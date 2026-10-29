package sqlite

import (
	"context"
	"database/sql"
	"strings"
)

// MusicScrobble is one played track. JSON names match the Cloudflare Worker so the
// Android app can read either server.
type MusicScrobble struct {
	ID          string `json:"id"`
	Track       string `json:"track"`
	Artist      string `json:"artist"`
	Album       string `json:"album"`
	AlbumArtist string `json:"album_artist"`
	Duration    int    `json:"duration"`
	ListenedAt  string `json:"listened_at"`
	UTS         int64  `json:"uts"`
	Source      string `json:"source"`
	OriginURL   string `json:"origin_url"`
	ImageURL    string `json:"image_url"`
	RawJSON     string `json:"raw_json"`
	CreatedAt   string `json:"created_at"`
}

// MusicQuery filters /api/music. Zero values mean "no filter".
type MusicQuery struct {
	Search  string
	Artist  string
	Album   string
	Source  string
	Date    string // YYYY-MM-DD prefix of listened_at
	FromUTS int64
	ToUTS   int64
	Page    int
	Limit   int
}

type TopArtist struct {
	Artist   string `json:"artist"`
	Count    int    `json:"count"`
	ImageURL string `json:"image_url"`
}

type TopAlbum struct {
	Album    string `json:"album"`
	Artist   string `json:"artist"`
	Count    int    `json:"count"`
	ImageURL string `json:"image_url"`
}

type MusicStats struct {
	TotalScrobbles  int             `json:"total_scrobbles"`
	TodayScrobbles  int             `json:"today_scrobbles"`
	WeekScrobbles   int             `json:"week_scrobbles"`
	MonthScrobbles  int             `json:"month_scrobbles"`
	UniqueArtists   int             `json:"unique_artists"`
	UniqueAlbums    int             `json:"unique_albums"`
	TopArtists      []TopArtist     `json:"top_artists"`
	TopAlbums       []TopAlbum      `json:"top_albums"`
	RecentScrobbles []MusicScrobble `json:"recent_scrobbles"`
	LastSync        *SyncRun        `json:"last_sync"`
}

// MusicSettings is the single row of music_sync_settings. Exists is false when the
// row has never been saved.
type MusicSettings struct {
	Exists               bool
	LastFMUsername       string
	LastFMAPIKey         string
	ListenBrainzUsername string
	ListenBrainzToken    string
	StatsFMUsername      string
	IntervalMinutes      int
	Enabled              bool
	WebScrobblerEnabled  bool
}

const musicSchema = `
create table if not exists music_scrobbles (
  id text primary key,
  track text not null,
  artist text not null,
  album text,
  album_artist text,
  duration integer,
  listened_at text not null,
  uts integer not null,
  source text not null,
  origin_url text,
  image_url text,
  raw_json text not null,
  created_at text not null default current_timestamp
);
create index if not exists idx_music_scrobbles_uts on music_scrobbles(uts desc);
create index if not exists idx_music_scrobbles_artist on music_scrobbles(artist);
create unique index if not exists idx_music_scrobbles_unique on music_scrobbles(artist, track, uts);
create table if not exists music_sync_settings (
  id integer primary key check (id = 1),
  lastfm_username text,
  lastfm_api_key text,
  listenbrainz_username text,
  listenbrainz_token text,
  statsfm_username text,
  interval_minutes integer not null default 360,
  enabled integer not null default 1,
  web_scrobbler_enabled integer not null default 0,
  updated_at text not null default current_timestamp
);
create table if not exists music_sync_runs (
  id integer primary key autoincrement,
  source text not null,
  status text not null,
  inserted integer not null default 0,
  updated integer not null default 0,
  message text,
  ran_at text not null default current_timestamp
);`

func (s *Store) initMusic(ctx context.Context) error {
	_, err := s.db.ExecContext(ctx, musicSchema)
	return err
}

const musicColumns = `id, track, artist, coalesce(album, ''), coalesce(album_artist, ''), coalesce(duration, 0),
listened_at, uts, source, coalesce(origin_url, ''), coalesce(image_url, ''), raw_json, created_at`

func scanMusicScrobble(row interface{ Scan(...any) error }) (MusicScrobble, error) {
	var m MusicScrobble
	err := row.Scan(&m.ID, &m.Track, &m.Artist, &m.Album, &m.AlbumArtist, &m.Duration,
		&m.ListenedAt, &m.UTS, &m.Source, &m.OriginURL, &m.ImageURL, &m.RawJSON, &m.CreatedAt)
	return m, err
}

// UpsertMusicScrobble stores a scrobble. Scrobbles are unique by (artist, track, uts);
// a repeat fills in album, artwork and links that were missing. It reports whether
// a new row was created.
func (s *Store) UpsertMusicScrobble(ctx context.Context, m MusicScrobble) (bool, error) {
	var existing string
	err := s.db.QueryRowContext(ctx, "select id from music_scrobbles where artist = ? and track = ? and uts = ?",
		m.Artist, m.Track, m.UTS).Scan(&existing)
	if err != nil && err != sql.ErrNoRows {
		return false, err
	}
	_, err = s.db.ExecContext(ctx, `
insert into music_scrobbles (id, track, artist, album, album_artist, duration, listened_at, uts, source, origin_url, image_url, raw_json)
values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
on conflict(artist, track, uts) do update set
  album = case when excluded.album != '' then excluded.album else music_scrobbles.album end,
  album_artist = case when excluded.album_artist != '' then excluded.album_artist else music_scrobbles.album_artist end,
  duration = case when excluded.duration > 0 then excluded.duration else music_scrobbles.duration end,
  image_url = case when excluded.image_url != '' then excluded.image_url else music_scrobbles.image_url end,
  origin_url = case when excluded.origin_url != '' then excluded.origin_url else music_scrobbles.origin_url end`,
		m.ID, m.Track, m.Artist, m.Album, m.AlbumArtist, m.Duration, m.ListenedAt, m.UTS, m.Source, m.OriginURL, m.ImageURL, m.RawJSON)
	if err != nil {
		return false, err
	}
	return existing == "", nil
}

// QueryMusicScrobbles returns one page of scrobbles, newest first, and the total match count.
func (s *Store) QueryMusicScrobbles(ctx context.Context, q MusicQuery) ([]MusicScrobble, int, error) {
	where := []string{"1=1"}
	var args []any
	if q.Search != "" {
		where = append(where, "(track like ? or artist like ? or album like ?)")
		like := "%" + q.Search + "%"
		args = append(args, like, like, like)
	}
	if q.Artist != "" {
		where = append(where, "artist = ?")
		args = append(args, q.Artist)
	}
	if q.Album != "" {
		where = append(where, "album = ?")
		args = append(args, q.Album)
	}
	if q.Source != "" {
		where = append(where, "source = ?")
		args = append(args, q.Source)
	}
	if q.Date != "" {
		where = append(where, "listened_at like ?")
		args = append(args, q.Date+"%")
	} else {
		if q.FromUTS > 0 {
			where = append(where, "uts >= ?")
			args = append(args, q.FromUTS)
		}
		if q.ToUTS > 0 {
			where = append(where, "uts <= ?")
			args = append(args, q.ToUTS)
		}
	}
	whereSQL := strings.Join(where, " and ")

	var total int
	if err := s.db.QueryRowContext(ctx, "select count(*) from music_scrobbles where "+whereSQL, args...).Scan(&total); err != nil {
		return nil, 0, err
	}
	limit := q.Limit
	if limit <= 0 {
		limit = 50
	}
	page := q.Page
	if page < 1 {
		page = 1
	}
	rows, err := s.db.QueryContext(ctx, "select "+musicColumns+" from music_scrobbles where "+whereSQL+" order by uts desc limit ? offset ?",
		append(args, limit, (page-1)*limit)...)
	if err != nil {
		return nil, 0, err
	}
	defer rows.Close()
	items := []MusicScrobble{}
	for rows.Next() {
		m, err := scanMusicScrobble(rows)
		if err != nil {
			return nil, 0, err
		}
		items = append(items, m)
	}
	return items, total, rows.Err()
}

// RecentMusicScrobbles returns the newest scrobbles, used by the activity timeline.
func (s *Store) RecentMusicScrobbles(ctx context.Context, limit int) ([]MusicScrobble, error) {
	items, _, err := s.QueryMusicScrobbles(ctx, MusicQuery{Limit: limit, Page: 1})
	return items, err
}

func (s *Store) countMusic(ctx context.Context, query string, args ...any) int {
	var n int
	_ = s.db.QueryRowContext(ctx, query, args...).Scan(&n)
	return n
}

// MusicStats builds the /api/music/stats payload. The windows are passed in as Unix
// timestamps so the caller decides what "today" means.
func (s *Store) MusicStats(ctx context.Context, startOfTodayUTS, startOfWeekUTS, startOfMonthUTS int64) (MusicStats, error) {
	stats := MusicStats{
		TotalScrobbles: s.countMusic(ctx, "select count(*) from music_scrobbles"),
		TodayScrobbles: s.countMusic(ctx, "select count(*) from music_scrobbles where uts >= ?", startOfTodayUTS),
		WeekScrobbles:  s.countMusic(ctx, "select count(*) from music_scrobbles where uts >= ?", startOfWeekUTS),
		MonthScrobbles: s.countMusic(ctx, "select count(*) from music_scrobbles where uts >= ?", startOfMonthUTS),
		UniqueArtists:  s.countMusic(ctx, "select count(distinct artist) from music_scrobbles"),
		UniqueAlbums:   s.countMusic(ctx, "select count(distinct album) from music_scrobbles where album != ''"),
		TopArtists:     []TopArtist{},
		TopAlbums:      []TopAlbum{},
	}

	rows, err := s.db.QueryContext(ctx, `select artist, count(*) as c, coalesce(max(image_url), '')
from music_scrobbles group by artist order by c desc limit 10`)
	if err != nil {
		return stats, err
	}
	for rows.Next() {
		var a TopArtist
		if err := rows.Scan(&a.Artist, &a.Count, &a.ImageURL); err != nil {
			rows.Close()
			return stats, err
		}
		stats.TopArtists = append(stats.TopArtists, a)
	}
	rows.Close()

	rows, err = s.db.QueryContext(ctx, `select album, artist, count(*) as c, coalesce(max(image_url), '')
from music_scrobbles where album != '' group by album, artist order by c desc limit 10`)
	if err != nil {
		return stats, err
	}
	for rows.Next() {
		var a TopAlbum
		if err := rows.Scan(&a.Album, &a.Artist, &a.Count, &a.ImageURL); err != nil {
			rows.Close()
			return stats, err
		}
		stats.TopAlbums = append(stats.TopAlbums, a)
	}
	rows.Close()

	if stats.RecentScrobbles, err = s.RecentMusicScrobbles(ctx, 5); err != nil {
		return stats, err
	}
	stats.LastSync, err = s.LastSyncRun(ctx, "music_sync_runs")
	return stats, err
}

// MaxMusicUTS returns the newest scrobble time for a source, or 0 when there is none.
func (s *Store) MaxMusicUTS(ctx context.Context, source string) (int64, error) {
	var v sql.NullInt64
	err := s.db.QueryRowContext(ctx, "select max(uts) from music_scrobbles where source = ?", source).Scan(&v)
	if err != nil {
		return 0, err
	}
	return v.Int64, nil
}

func (s *Store) GetMusicSettings(ctx context.Context) (MusicSettings, error) {
	var lfmUser, lfmKey, lbUser, lbToken, sfmUser sql.NullString
	var interval, enabled, ws sql.NullInt64
	err := s.db.QueryRowContext(ctx, `select lastfm_username, lastfm_api_key, listenbrainz_username, listenbrainz_token,
statsfm_username, interval_minutes, enabled, web_scrobbler_enabled from music_sync_settings where id = 1`).
		Scan(&lfmUser, &lfmKey, &lbUser, &lbToken, &sfmUser, &interval, &enabled, &ws)
	if err == sql.ErrNoRows {
		return MusicSettings{IntervalMinutes: 360, Enabled: true}, nil
	}
	if err != nil {
		return MusicSettings{}, err
	}
	settings := MusicSettings{
		Exists:               true,
		LastFMUsername:       scanString(lfmUser),
		LastFMAPIKey:         scanString(lfmKey),
		ListenBrainzUsername: scanString(lbUser),
		ListenBrainzToken:    scanString(lbToken),
		StatsFMUsername:      scanString(sfmUser),
		IntervalMinutes:      int(interval.Int64),
		Enabled:              !enabled.Valid || enabled.Int64 != 0,
		WebScrobblerEnabled:  ws.Valid && ws.Int64 == 1,
	}
	if settings.IntervalMinutes <= 0 {
		settings.IntervalMinutes = 360
	}
	return settings, nil
}

func (s *Store) SaveMusicSettings(ctx context.Context, m MusicSettings) error {
	_, err := s.db.ExecContext(ctx, `
insert into music_sync_settings (id, lastfm_username, lastfm_api_key, listenbrainz_username, listenbrainz_token,
  statsfm_username, interval_minutes, enabled, web_scrobbler_enabled, updated_at)
values (1, ?, ?, ?, ?, ?, ?, ?, ?, current_timestamp)
on conflict(id) do update set
  lastfm_username = excluded.lastfm_username,
  lastfm_api_key = excluded.lastfm_api_key,
  listenbrainz_username = excluded.listenbrainz_username,
  listenbrainz_token = excluded.listenbrainz_token,
  statsfm_username = excluded.statsfm_username,
  interval_minutes = excluded.interval_minutes,
  enabled = excluded.enabled,
  web_scrobbler_enabled = excluded.web_scrobbler_enabled,
  updated_at = current_timestamp`,
		m.LastFMUsername, m.LastFMAPIKey, m.ListenBrainzUsername, m.ListenBrainzToken, m.StatsFMUsername,
		m.IntervalMinutes, boolInt(m.Enabled), boolInt(m.WebScrobblerEnabled))
	return err
}

// SetWebScrobblerFlag updates only the legacy web_scrobbler_enabled column, like the Worker does
// when the plugin is toggled. It is a no-op when the settings row does not exist yet.
func (s *Store) SetWebScrobblerFlag(ctx context.Context, enabled bool) error {
	_, err := s.db.ExecContext(ctx, "update music_sync_settings set web_scrobbler_enabled = ?, updated_at = current_timestamp where id = 1", boolInt(enabled))
	return err
}

// PluginToggles returns every "plugin:<id>:enabled" user setting.
func (s *Store) PluginToggles(ctx context.Context) (map[string]bool, error) {
	rows, err := s.db.QueryContext(ctx, "select key, value from user_settings where key like 'plugin:%:enabled'")
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	plugins := map[string]bool{}
	for rows.Next() {
		var key, value string
		if err := rows.Scan(&key, &value); err != nil {
			return nil, err
		}
		id := strings.TrimSuffix(strings.TrimPrefix(key, "plugin:"), ":enabled")
		plugins[id] = value == "true" || value == "1"
	}
	return plugins, rows.Err()
}

// PluginToggle returns a plugin's stored state and whether it was ever set.
func (s *Store) PluginToggle(ctx context.Context, id string) (enabled bool, set bool, err error) {
	var value string
	err = s.db.QueryRowContext(ctx, "select value from user_settings where key = ?", "plugin:"+id+":enabled").Scan(&value)
	if err == sql.ErrNoRows {
		return false, false, nil
	}
	if err != nil {
		return false, false, err
	}
	return value == "true" || value == "1", true, nil
}

func (s *Store) SetPluginToggle(ctx context.Context, id string, enabled bool) error {
	value := "0"
	if enabled {
		value = "1"
	}
	return s.SetUserSetting(ctx, "plugin:"+id+":enabled", value)
}

func boolInt(v bool) int {
	if v {
		return 1
	}
	return 0
}
