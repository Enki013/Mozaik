package application_test

import (
	"context"
	"errors"
	"net/http"
	"net/http/httptest"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"personal-records/internal/application"
	"personal-records/internal/infrastructure/sqlite"
)

const testLastFMKey = "0123456789abcdef0123456789abcdef"

func newMusicServices(t *testing.T) (*application.Services, *sqlite.Store) {
	t.Helper()
	t.Setenv("LASTFM_API_KEY", "")
	t.Setenv("LASTFM_USERNAME", "")
	store, err := sqlite.Open(filepath.Join(t.TempDir(), "music.sqlite3"))
	if err != nil {
		t.Fatalf("open: %v", err)
	}
	t.Cleanup(func() { store.Close() })
	if err := store.Init(context.Background()); err != nil {
		t.Fatalf("init: %v", err)
	}
	return application.NewServices(store, nil), store
}

func musicStatus(t *testing.T, err error) int {
	t.Helper()
	var me *application.MusicError
	if !errors.As(err, &me) {
		t.Fatalf("expected MusicError, got %v", err)
	}
	return me.Status
}

func allScrobbles(t *testing.T, s *application.Services) []sqlite.MusicScrobble {
	t.Helper()
	items, _, err := s.MusicScrobbles(context.Background(), sqlite.MusicQuery{Limit: 200})
	if err != nil {
		t.Fatalf("query: %v", err)
	}
	return items
}

func TestWebScrobblerWebhook(t *testing.T) {
	ctx := context.Background()
	s, store := newMusicServices(t)
	now := time.Unix(1761739200, 0)

	// Disabled by default: the scrobble is not stored and a paused run is recorded.
	res := s.IngestWebScrobbler(ctx, []byte(`{"eventName":"scrobble","data":{"song":{"parsed":{"artist":"A","track":"B"}}}}`), now)
	if res["status"] != "paused" {
		t.Fatalf("expected paused, got %v", res)
	}
	if run, _ := store.LastSyncRun(ctx, "music_sync_runs"); run == nil || run.Status != "paused" {
		t.Fatalf("expected a paused sync run, got %+v", run)
	}

	if err := s.SavePluginToggles(ctx, map[string]any{"plugin_id": "web_scrobbler", "enabled": true}); err != nil {
		t.Fatal(err)
	}

	if res := s.IngestWebScrobbler(ctx, []byte(`{"eventName":"nowplaying","data":{}}`), now); res["status"] != "nowplaying_ignored" {
		t.Fatalf("nowplaying should be ignored, got %v", res)
	}
	if res := s.IngestWebScrobbler(ctx, []byte(`not json`), now); res["ok"] != false {
		t.Fatalf("invalid JSON should fail, got %v", res)
	}

	payload := `{"eventName":"scrobble","data":{"song":{
		"parsed":{"artist":"Radiohead","track":"Reckoner","album":"","duration":290,"trackArt":"https://img/x.jpg","originUrl":"https://yt/1"},
		"processed":{"artist":"Radiohead","track":"Reckoner","album":"In Rainbows"}}}}`
	res = s.IngestWebScrobbler(ctx, []byte(payload), now)
	if res["inserted"] != 1 || res["updated"] != 0 {
		t.Fatalf("expected 1 inserted, got %v", res)
	}
	res = s.IngestWebScrobbler(ctx, []byte(payload), now)
	if res["inserted"] != 0 || res["updated"] != 1 {
		t.Fatalf("expected the repeat to update, got %v", res)
	}
	// Items without artist or track are skipped.
	res = s.IngestWebScrobbler(ctx, []byte(`[{"artist":"Only artist","uts":1}]`), now)
	if res["inserted"] != 0 || res["total"] != 1 {
		t.Fatalf("expected the item to be skipped, got %v", res)
	}

	items := allScrobbles(t, s)
	if len(items) != 1 {
		t.Fatalf("expected 1 scrobble, got %d", len(items))
	}
	m := items[0]
	if m.ID != "ws_2bc26aaa" || m.Album != "In Rainbows" || m.Duration != 290 || m.ImageURL != "https://img/x.jpg" ||
		m.OriginURL != "https://yt/1" || m.Source != "web_scrobbler" || m.UTS != 1761739200 ||
		m.ListenedAt != "2025-10-29T12:00:00.000Z" {
		t.Fatalf("unexpected scrobble: %+v", m)
	}

	status := s.WebScrobblerStatus(ctx)
	if status["status"] != "ready" || status["total_records"] != 1 {
		t.Fatalf("unexpected status: %v", status)
	}
}

func TestMusicSettingsSaveSemantics(t *testing.T) {
	ctx := context.Background()
	s, store := newMusicServices(t)

	err := s.SaveMusicSettings(ctx, map[string]any{"lastfm_api_key": "https://example.com/hook"})
	if musicStatus(t, err) != http.StatusBadRequest {
		t.Fatal("a URL must be rejected as a Last.fm key")
	}
	if musicStatus(t, s.SaveMusicSettings(ctx, map[string]any{"lastfm_api_key": "short"})) != http.StatusBadRequest {
		t.Fatal("a short key must be rejected")
	}

	if err := s.SaveMusicSettings(ctx, map[string]any{
		"lastfm_username": " alice ", "lastfm_api_key": testLastFMKey,
		"listenbrainz_username": "alice_lb", "listenbrainz_token": "tok",
		"statsfm_username": "alice_sfm", "web_scrobbler_enabled": true,
	}); err != nil {
		t.Fatal(err)
	}
	got, _ := store.GetMusicSettings(ctx)
	if got.LastFMUsername != "alice" || got.LastFMAPIKey != testLastFMKey || got.ListenBrainzToken != "tok" ||
		got.StatsFMUsername != "alice_sfm" || !got.WebScrobblerEnabled {
		t.Fatalf("unexpected settings: %+v", got)
	}

	// Fields missing from the body keep their values.
	if err := s.SaveMusicSettings(ctx, map[string]any{"statsfm_username": "bob"}); err != nil {
		t.Fatal(err)
	}
	got, _ = store.GetMusicSettings(ctx)
	if got.LastFMUsername != "alice" || got.LastFMAPIKey != testLastFMKey || got.ListenBrainzUsername != "alice_lb" ||
		got.ListenBrainzToken != "tok" || got.StatsFMUsername != "bob" || !got.WebScrobblerEnabled {
		t.Fatalf("partial update changed other fields: %+v", got)
	}

	// Empty strings clear the secrets.
	if err := s.SaveMusicSettings(ctx, map[string]any{"lastfm_api_key": "", "listenbrainz_token": ""}); err != nil {
		t.Fatal(err)
	}
	got, _ = store.GetMusicSettings(ctx)
	if got.LastFMAPIKey != "" || got.ListenBrainzToken != "" {
		t.Fatalf("secrets were not cleared: %+v", got)
	}

	resp, err := s.MusicSettingsResponse(ctx, "http://host/api/webhooks/music")
	if err != nil {
		t.Fatal(err)
	}
	if resp["lastfm_username"] != "alice" || resp["lastfm_api_key_set"] != false || resp["web_scrobbler_enabled"] != true ||
		resp["webhook_url"] != "http://host/api/webhooks/music" {
		t.Fatalf("unexpected response: %v", resp)
	}
	if _, leaked := resp["lastfm_api_key"]; leaked {
		t.Fatal("the API key must not be returned")
	}
}

func TestPluginToggles(t *testing.T) {
	ctx := context.Background()
	s, store := newMusicServices(t)
	if err := s.SaveMusicSettings(ctx, map[string]any{"lastfm_username": "alice"}); err != nil {
		t.Fatal(err)
	}
	if err := s.SavePluginToggles(ctx, map[string]any{"enabled_plugins": []any{"lastfm", "web_scrobbler"}}); err != nil {
		t.Fatal(err)
	}
	plugins, _ := s.PluginToggles(ctx)
	if !plugins["lastfm"] || !plugins["web_scrobbler"] || plugins["goodreads"] {
		t.Fatalf("unexpected plugins: %v", plugins)
	}
	if len(plugins) != 9 {
		t.Fatalf("a full sync should store all 9 known plugins, got %d", len(plugins))
	}
	if settings, _ := store.GetMusicSettings(ctx); !settings.WebScrobblerEnabled {
		t.Fatal("the web scrobbler column should follow the toggle")
	}
	if err := s.SavePluginToggles(ctx, map[string]any{"plugin_id": "web_scrobbler", "enabled": false}); err != nil {
		t.Fatal(err)
	}
	if s.WebScrobblerEnabled(ctx) {
		t.Fatal("web scrobbler should be off")
	}
}

func TestImportMusic(t *testing.T) {
	ctx := context.Background()
	s, _ := newMusicServices(t)
	now := time.Unix(1761739200, 0)

	csv := "artist,album,track,timestamp\nRadiohead,In Rainbows,Reckoner,1761739200\n\"Daft Punk\",RAM,Get Lucky,2025-10-29T12:00:00Z\n"
	res, err := s.ImportMusic(ctx, "text/csv", []byte(csv), now)
	if err != nil || res["inserted"] != 2 {
		t.Fatalf("csv import: %v %v", res, err)
	}
	res, err = s.ImportMusic(ctx, "application/json", []byte(`{"items":[{"artist":"Radiohead","track":"Reckoner","uts":1761739200,"album":"In Rainbows"}]}`), now)
	if err != nil || res["updated"] != 1 {
		t.Fatalf("json import should update the CSV row: %v %v", res, err)
	}
	if _, err := s.ImportMusic(ctx, "application/json", []byte(`{}`), now); musicStatus(t, err) != http.StatusBadRequest {
		t.Fatal("an empty import should be a 400")
	}
	items := allScrobbles(t, s)
	if len(items) != 2 || items[0].Source != "csv_import" {
		t.Fatalf("unexpected rows: %+v", items)
	}
}

func TestSyncLastFM(t *testing.T) {
	ctx := context.Background()
	s, store := newMusicServices(t)
	var gotFrom []string
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		q := r.URL.Query()
		if q.Get("method") != "user.getrecenttracks" || q.Get("user") != "alice" || q.Get("api_key") != testLastFMKey || q.Get("extended") != "1" {
			t.Errorf("unexpected query: %s", r.URL.RawQuery)
		}
		gotFrom = append(gotFrom, q.Get("from"))
		w.Write([]byte(`{"recenttracks":{"@attr":{"totalPages":"1"},"track":[
			{"@attr":{"nowplaying":"true"},"name":"Now","artist":{"name":"X"}},
			{"name":"Reckoner","artist":{"name":"Radiohead"},"album":{"#text":"In Rainbows"},"url":"https://last.fm/r",
			 "date":{"uts":"1761739200"},"image":[{"size":"small","#text":"s.jpg"},{"size":"extralarge","#text":"xl.jpg"}]},
			{"name":"Dreams","artist":{"#text":"Fleetwood Mac"},"date":{"uts":"1761500000"}}
		]}}`))
	}))
	defer srv.Close()
	s.SetMusicHTTP(srv.Client(), application.MusicEndpoints{LastFM: srv.URL + "/2.0/"})

	if _, err := s.SyncLastFM(ctx, map[string]any{}); musicStatus(t, err) != http.StatusBadRequest {
		t.Fatal("missing username must be a 400")
	}
	if err := s.SaveMusicSettings(ctx, map[string]any{"lastfm_username": "alice", "lastfm_api_key": testLastFMKey}); err != nil {
		t.Fatal(err)
	}
	res, err := s.SyncLastFM(ctx, map[string]any{})
	if err != nil || res["inserted"] != 2 || res["totalProcessed"] != 2 {
		t.Fatalf("first sync: %v %v", res, err)
	}
	// The second sync is incremental: it asks only for tracks after the newest one.
	if _, err := s.SyncLastFM(ctx, map[string]any{}); err != nil {
		t.Fatal(err)
	}
	if len(gotFrom) != 2 || gotFrom[0] != "" || gotFrom[1] != "1761739201" {
		t.Fatalf("unexpected from params: %v", gotFrom)
	}
	items := allScrobbles(t, s)
	if len(items) != 2 || items[0].ID != "lfm_2bc26aaa" || items[0].ImageURL != "xl.jpg" || items[0].Album != "In Rainbows" ||
		items[1].ID != "lfm_2f332a8a" || items[1].Artist != "Fleetwood Mac" {
		t.Fatalf("unexpected rows: %+v", items)
	}
	if run, _ := store.LastSyncRun(ctx, "music_sync_runs"); run == nil || run.Source != "lastfm" || run.Status != "success" {
		t.Fatalf("unexpected run: %+v", run)
	}
}

func TestSyncLastFMInvalidKey(t *testing.T) {
	ctx := context.Background()
	s, _ := newMusicServices(t)
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Write([]byte(`{"error":10,"message":"Invalid API key - You must be granted a valid key by last.fm"}`))
	}))
	defer srv.Close()
	s.SetMusicHTTP(srv.Client(), application.MusicEndpoints{LastFM: srv.URL})
	_ = s.SaveMusicSettings(ctx, map[string]any{"lastfm_username": "alice", "lastfm_api_key": testLastFMKey})
	_, err := s.SyncLastFM(ctx, map[string]any{})
	if musicStatus(t, err) != http.StatusBadRequest || !strings.Contains(err.Error(), "Hata 10") {
		t.Fatalf("expected an invalid key error, got %v", err)
	}
}

func TestSyncListenBrainz(t *testing.T) {
	ctx := context.Background()
	s, _ := newMusicServices(t)
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/1/user/alice%20lb/listens" && r.URL.EscapedPath() != "/1/user/alice%20lb/listens" {
			t.Errorf("unexpected path %s", r.URL.EscapedPath())
		}
		if r.Header.Get("Authorization") != "Token tok" {
			t.Errorf("missing token header")
		}
		w.Write([]byte(`{"payload":{"listens":[
			{"listened_at":1761739200,"track_metadata":{"artist_name":"Radiohead","track_name":"Reckoner","release_name":"In Rainbows",
			 "mbid_mapping":{"release_mbid":"abc"},"additional_info":{"spotify_id":"sp1"}}},
			{"listened_at":0,"track_metadata":{"artist_name":"Skip","track_name":"Me"}}
		]}}`))
	}))
	defer srv.Close()
	s.SetMusicHTTP(srv.Client(), application.MusicEndpoints{ListenBrainz: srv.URL})

	if _, err := s.SyncListenBrainz(ctx, map[string]any{}); musicStatus(t, err) != http.StatusBadRequest {
		t.Fatal("missing username must be a 400")
	}
	_ = s.SaveMusicSettings(ctx, map[string]any{"listenbrainz_username": "alice lb", "listenbrainz_token": "tok"})
	res, err := s.SyncListenBrainz(ctx, map[string]any{})
	if err != nil || res["inserted"] != 1 {
		t.Fatalf("sync: %v %v", res, err)
	}
	m := allScrobbles(t, s)[0]
	if m.ID != "lb_2bc26aaa" || m.ImageURL != "https://coverartarchive.org/release/abc/front-250" ||
		m.OriginURL != "https://open.spotify.com/track/sp1" || m.Source != "listenbrainz" {
		t.Fatalf("unexpected row: %+v", m)
	}
}

func TestSyncStatsFM(t *testing.T) {
	ctx := context.Background()
	s, _ := newMusicServices(t)
	private := false
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch {
		case private:
			w.WriteHeader(http.StatusForbidden)
		case strings.HasSuffix(r.URL.Path, "/streams"):
			w.Write([]byte(`{"items":[{"endTime":"2025-10-29T12:00:00.000Z","playedMs":200000,
				"track":{"name":"Reckoner","durationMs":290000,"artists":[{"name":"Radiohead"}],"albums":[{"name":"In Rainbows","image":"img.jpg"}]}}]}`))
		default:
			t.Errorf("unexpected path %s", r.URL.Path)
		}
	}))
	defer srv.Close()
	s.SetMusicHTTP(srv.Client(), application.MusicEndpoints{StatsFM: srv.URL})
	_ = s.SaveMusicSettings(ctx, map[string]any{"statsfm_username": "alice"})

	res, err := s.SyncStatsFM(ctx, map[string]any{}, time.Unix(1761800000, 0))
	if err != nil || res["inserted"] != 1 {
		t.Fatalf("sync: %v %v", res, err)
	}
	m := allScrobbles(t, s)[0]
	if m.ID != "sfm_2bc26aaa" || m.UTS != 1761739200 || m.Duration != 200 || m.Album != "In Rainbows" || m.ImageURL != "img.jpg" {
		t.Fatalf("unexpected row: %+v", m)
	}

	private = true
	_, err = s.SyncStatsFM(ctx, map[string]any{}, time.Now())
	if musicStatus(t, err) != http.StatusInternalServerError || !strings.Contains(err.Error(), "gizli") {
		t.Fatalf("expected the private-profile error, got %v", err)
	}
}

func TestMusicInActivityAndDashboard(t *testing.T) {
	ctx := context.Background()
	s, store := newMusicServices(t)
	now := time.Now()
	uts := now.Unix()
	listened := now.UTC().Format("2006-01-02T15:04:05.000Z")
	if _, err := store.UpsertMusicScrobble(ctx, sqlite.MusicScrobble{
		ID: "lfm_1", Track: "Reckoner", Artist: "Radiohead", Album: "In Rainbows", ListenedAt: listened, UTS: uts, Source: "lastfm", RawJSON: "{}",
	}); err != nil {
		t.Fatal(err)
	}

	activity, err := s.ActivityTimeline(ctx, 0, 50)
	if err != nil {
		t.Fatal(err)
	}
	var found *application.ActivityItem
	for i := range activity.Items {
		if activity.Items[i].Source == "music" {
			found = &activity.Items[i]
		}
	}
	if found == nil || found.ID != "music_lfm_1" || found.Subtitle != "Radiohead • In Rainbows" ||
		found.Date != listened[:10] || found.Time != listened[11:16] || found.Details["uts"] != uts {
		t.Fatalf("unexpected music activity item: %+v", found)
	}

	dash, err := s.Dashboard(ctx)
	if err != nil {
		t.Fatal(err)
	}
	if dash.Counts["music_scrobbles"] != 1 || dash.Today["music_scrobbles"] != 1 || dash.Connections["music"] != true {
		t.Fatalf("unexpected dashboard: %+v", dash)
	}
	if _, ok := dash.LastSync["music"]; !ok {
		t.Fatal("dashboard last_sync should include music")
	}

	stats, err := s.MusicStats(ctx, now)
	if err != nil || stats.TotalScrobbles != 1 || stats.TodayScrobbles != 1 || len(stats.TopArtists) != 1 || len(stats.RecentScrobbles) != 1 {
		t.Fatalf("unexpected stats: %+v %v", stats, err)
	}
}
