package application

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strconv"
	"strings"
	"time"
	"unicode/utf16"

	"personal-records/internal/config"
	"personal-records/internal/infrastructure/sqlite"
)

// Music integrations: Last.fm, ListenBrainz, Stats.fm sync, the Web Scrobbler webhook
// and CSV/JSON import. Behaviour and JSON shapes follow the Cloudflare Worker
// (worker/src/index.ts) so the Android app works the same against either server.

const musicUserAgent = "PersonalRecordsApp/1.0 (https://github.com/Enki013/Mozaik)"

// MusicEndpoints are the base URLs of the external music APIs. Tests point them at
// local servers.
type MusicEndpoints struct {
	LastFM       string // full method URL, e.g. https://ws.audioscrobbler.com/2.0/
	ListenBrainz string // e.g. https://api.listenbrainz.org
	StatsFM      string // e.g. https://api.stats.fm
}

var DefaultMusicEndpoints = MusicEndpoints{
	LastFM:       "https://ws.audioscrobbler.com/2.0/",
	ListenBrainz: "https://api.listenbrainz.org",
	StatsFM:      "https://api.stats.fm",
}

// MusicError carries the HTTP status the handler should answer with.
type MusicError struct {
	Status  int
	Message string
}

func (e *MusicError) Error() string { return e.Message }

// SetMusicHTTP replaces the HTTP client and API base URLs used for music sync.
func (s *Services) SetMusicHTTP(client *http.Client, endpoints MusicEndpoints) {
	s.musicHTTP = client
	s.musicEndpoints = endpoints
}

func (s *Services) musicClient() *http.Client {
	if s.musicHTTP != nil {
		return s.musicHTTP
	}
	return &http.Client{Timeout: 20 * time.Second}
}

func (s *Services) endpoints() MusicEndpoints {
	e := s.musicEndpoints
	if e.LastFM == "" {
		e.LastFM = DefaultMusicEndpoints.LastFM
	}
	if e.ListenBrainz == "" {
		e.ListenBrainz = DefaultMusicEndpoints.ListenBrainz
	}
	if e.StatsFM == "" {
		e.StatsFM = DefaultMusicEndpoints.StatsFM
	}
	return e
}

// ---------- Settings ----------

// LastFMCredentials returns the saved Last.fm username and key, falling back to the
// LASTFM_USERNAME and LASTFM_API_KEY environment variables.
func (s *Services) LastFMCredentials(ctx context.Context) (string, string) {
	settings, _ := s.store.GetMusicSettings(ctx)
	username := strings.TrimSpace(firstNonEmpty(settings.LastFMUsername, config.Env("LASTFM_USERNAME")))
	apiKey := strings.TrimSpace(firstNonEmpty(settings.LastFMAPIKey, config.Env("LASTFM_API_KEY")))
	return username, apiKey
}

// WebScrobblerEnabled reports whether Web Scrobbler scrobbles are accepted. The plugin
// toggle wins; without it the legacy settings column decides.
func (s *Services) WebScrobblerEnabled(ctx context.Context) bool {
	if enabled, set, err := s.store.PluginToggle(ctx, "web_scrobbler"); err == nil && set {
		return enabled
	}
	settings, err := s.store.GetMusicSettings(ctx)
	if err != nil || !settings.Exists {
		return false
	}
	return settings.WebScrobblerEnabled && settings.Enabled
}

// MusicSettingsResponse builds GET /api/settings/music. Secrets are reported only as
// "set" flags.
func (s *Services) MusicSettingsResponse(ctx context.Context, webhookURL string) (map[string]any, error) {
	settings, err := s.store.GetMusicSettings(ctx)
	if err != nil {
		return nil, err
	}
	webScrobbler := false
	if enabled, set, _ := s.store.PluginToggle(ctx, "web_scrobbler"); set {
		webScrobbler = enabled
	} else if settings.Exists {
		webScrobbler = settings.WebScrobblerEnabled
	}
	return map[string]any{
		"lastfm_username":        firstNonEmpty(settings.LastFMUsername, config.Env("LASTFM_USERNAME")),
		"lastfm_api_key_set":     settings.LastFMAPIKey != "" || config.Env("LASTFM_API_KEY") != "",
		"listenbrainz_username":  settings.ListenBrainzUsername,
		"listenbrainz_token_set": settings.ListenBrainzToken != "",
		"statsfm_username":       settings.StatsFMUsername,
		"interval_minutes":       settings.IntervalMinutes,
		"enabled":                settings.Enabled,
		"web_scrobbler_enabled":  webScrobbler,
		"webhook_url":            webhookURL,
	}, nil
}

// SaveMusicSettings applies POST /api/settings/music. Fields that are missing from
// the body keep their saved values; an empty key or token clears it.
func (s *Services) SaveMusicSettings(ctx context.Context, body map[string]any) error {
	username := trimmedString(body["lastfm_username"])
	apiKey := trimmedString(body["lastfm_api_key"])
	clearAPIKey := body["clear_api_key"] == true || isEmptyString(body["lastfm_api_key"])

	lbUsername, hasLBUsername := optionalString(body["listenbrainz_username"])
	lbToken, hasLBToken := optionalString(body["listenbrainz_token"])
	clearLBToken := body["clear_listenbrainz_token"] == true || isEmptyString(body["listenbrainz_token"])
	sfmUsername, hasSFMUsername := optionalString(body["statsfm_username"])

	interval := 360
	if v, ok := body["interval_minutes"].(float64); ok {
		interval = int(v)
	}
	enabled := body["enabled"] != false

	if apiKey != "" && !clearAPIKey {
		if strings.HasPrefix(apiKey, "http://") || strings.HasPrefix(apiKey, "https://") {
			return &MusicError{Status: http.StatusBadRequest, Message: "Geçersiz Last.fm API Key. Webhook URL'si değil, Last.fm API Key (32 karakterlik kod) girmelisiniz."}
		}
		if len(apiKey) != 32 {
			return &MusicError{Status: http.StatusBadRequest, Message: fmt.Sprintf("Geçersiz Last.fm API Key uzunluğu (%d karakter). Last.fm API Key tam olarak 32 karakter olmalıdır.", len(apiKey))}
		}
	}

	existing, err := s.store.GetMusicSettings(ctx)
	if err != nil {
		return err
	}
	webScrobbler := existing.Exists && existing.WebScrobblerEnabled
	if v, ok := body["web_scrobbler_enabled"]; ok && v != nil {
		webScrobbler = v == true || v == float64(1) || v == "true"
	}

	next := sqlite.MusicSettings{IntervalMinutes: interval, Enabled: enabled, WebScrobblerEnabled: webScrobbler}
	if existing.Exists {
		next.LastFMUsername = username
		if username == "" {
			if _, present := body["lastfm_username"]; !present {
				next.LastFMUsername = existing.LastFMUsername
			}
		}
		switch {
		case clearAPIKey:
			next.LastFMAPIKey = ""
		case apiKey != "":
			next.LastFMAPIKey = apiKey
		default:
			next.LastFMAPIKey = existing.LastFMAPIKey
		}
		next.ListenBrainzUsername = existing.ListenBrainzUsername
		if hasLBUsername {
			next.ListenBrainzUsername = lbUsername
		}
		switch {
		case clearLBToken:
			next.ListenBrainzToken = ""
		case hasLBToken:
			next.ListenBrainzToken = lbToken
		default:
			next.ListenBrainzToken = existing.ListenBrainzToken
		}
		next.StatsFMUsername = existing.StatsFMUsername
		if hasSFMUsername {
			next.StatsFMUsername = sfmUsername
		}
	} else {
		next.LastFMUsername = username
		if !clearAPIKey {
			next.LastFMAPIKey = apiKey
		}
		next.ListenBrainzUsername = lbUsername
		if !clearLBToken {
			next.ListenBrainzToken = lbToken
		}
		next.StatsFMUsername = sfmUsername
	}

	if err := s.store.SaveMusicSettings(ctx, next); err != nil {
		return err
	}
	return s.store.SetPluginToggle(ctx, "web_scrobbler", webScrobbler)
}

// knownPlugins are reset by a full "enabled_plugins" sync, as in the Worker.
var knownPlugins = []string{
	"web_scrobbler", "lastfm", "fatsecret", "goodreads", "letterboxd",
	"listenbrainz", "statsfm", "notification_relay", "health_connect",
}

func (s *Services) PluginToggles(ctx context.Context) (map[string]bool, error) {
	return s.store.PluginToggles(ctx)
}

// SavePluginToggles applies POST /api/settings/plugins: either one plugin
// ({"plugin_id", "enabled"}) or the full list of enabled plugins ({"enabled_plugins"}).
func (s *Services) SavePluginToggles(ctx context.Context, body map[string]any) error {
	id := trimmedString(body["plugin_id"])
	if id == "" {
		id = trimmedString(body["id"])
	}
	if id != "" {
		v := body["enabled"]
		enabled := v == true || v == "true" || v == float64(1)
		if err := s.store.SetPluginToggle(ctx, id, enabled); err != nil {
			return err
		}
		if id == "web_scrobbler" {
			if err := s.store.SetWebScrobblerFlag(ctx, enabled); err != nil {
				return err
			}
		}
	}
	if list, ok := body["enabled_plugins"].([]any); ok {
		enabledSet := map[string]bool{}
		for _, v := range list {
			if id, ok := v.(string); ok {
				enabledSet[id] = true
			}
		}
		for _, pid := range knownPlugins {
			if err := s.store.SetPluginToggle(ctx, pid, enabledSet[pid]); err != nil {
				return err
			}
		}
		if err := s.store.SetWebScrobblerFlag(ctx, enabledSet["web_scrobbler"]); err != nil {
			return err
		}
	}
	return nil
}

// ---------- Queries ----------

func (s *Services) MusicScrobbles(ctx context.Context, q sqlite.MusicQuery) ([]sqlite.MusicScrobble, int, error) {
	return s.store.QueryMusicScrobbles(ctx, q)
}

// MusicStats counts scrobbles for today (since local midnight), the last 7 days and
// the last 30 days.
func (s *Services) MusicStats(ctx context.Context, now time.Time) (sqlite.MusicStats, error) {
	local := now.In(time.Local)
	midnight := time.Date(local.Year(), local.Month(), local.Day(), 0, 0, 0, 0, time.Local)
	return s.store.MusicStats(ctx, midnight.Unix(), now.Unix()-7*86400, now.Unix()-30*86400)
}

// ---------- Web Scrobbler webhook ----------

// IngestWebScrobbler handles POST /api/webhooks/music. It always answers with HTTP 200,
// like the Worker, so the extension does not retry endlessly.
func (s *Services) IngestWebScrobbler(ctx context.Context, raw []byte, now time.Time) map[string]any {
	if !s.WebScrobblerEnabled(ctx) {
		msg := "Web Scrobbler entegrasyonu devre dışı bırakıldığı için scrobble kaydı duraklatıldı."
		_ = s.store.RecordSyncRun(ctx, "music_sync_runs", "web_scrobbler", "paused", 0, 0, msg)
		return map[string]any{"ok": true, "status": "paused", "message": msg}
	}

	var body any
	if err := json.Unmarshal(raw, &body); err != nil {
		return map[string]any{"ok": false, "error": "Invalid JSON body"}
	}
	if body == nil {
		return map[string]any{"ok": false, "error": "Empty request body"}
	}

	event := firstNonEmpty(asString(jsGet(body, "eventName")), asString(jsGet(body, "event")), "scrobble")
	if event == "nowplaying" {
		return map[string]any{"ok": true, "status": "nowplaying_ignored"}
	}

	items := rawItems(body)
	inserted, updated := 0, 0
	for _, item := range items {
		song := jsGet(item, "data", "song")
		if song == nil {
			song = jsGet(item, "song")
		}
		pick := func(field string, itemKeys ...string) string {
			values := []any{jsGet(song, "processed", field), jsGet(song, "parsed", field)}
			for _, k := range itemKeys {
				values = append(values, jsGet(item, k))
			}
			return strings.TrimSpace(firstTruthyString(values...))
		}
		artist := pick("artist", "artist")
		track := pick("track", "track", "title")
		album := pick("album", "album")
		albumArtist := pick("albumArtist", "albumArtist", "album_artist")
		duration := int(jsNumber(firstTruthy(jsGet(song, "parsed", "duration"), jsGet(song, "duration"), jsGet(item, "duration"))))
		imageURL := strings.TrimSpace(firstTruthyString(jsGet(song, "parsed", "trackArt"), jsGet(item, "trackArt"), jsGet(item, "image_url"), jsGet(item, "imageUrl")))
		originURL := strings.TrimSpace(firstTruthyString(jsGet(item, "originUrl"), jsGet(song, "parsed", "originUrl"), jsGet(item, "origin_url")))

		uts := int64(jsNumber(firstTruthy(jsGet(item, "uts"), jsGet(item, "timestamp"))))
		if uts <= 0 {
			uts = now.Unix()
		}
		listenedAt := firstTruthyString(jsGet(item, "listened_at"))
		if listenedAt == "" {
			listenedAt = isoMillis(uts)
		}
		source := firstTruthyString(jsGet(item, "source"))
		if source == "" {
			source = "web_scrobbler"
		}
		if artist == "" || track == "" {
			continue
		}
		prefix := "ws_"
		if source == "lastfm" {
			prefix = "lfm_"
		}
		isNew, err := s.store.UpsertMusicScrobble(ctx, sqlite.MusicScrobble{
			ID: prefix + jsHash(fmt.Sprintf("%s:%s:%d", artist, track, uts)), Track: track, Artist: artist,
			Album: album, AlbumArtist: albumArtist, Duration: duration, ListenedAt: listenedAt, UTS: uts,
			Source: source, OriginURL: originURL, ImageURL: imageURL, RawJSON: mustJSON(item),
		})
		if err != nil {
			continue
		}
		if isNew {
			inserted++
		} else {
			updated++
		}
	}
	return map[string]any{
		"ok":       true,
		"status":   "success",
		"inserted": inserted,
		"updated":  updated,
		"total":    len(items),
		"message":  fmt.Sprintf("%d scrobble başarıyla işlendi (%d yeni, %d güncellendi).", len(items), inserted, updated),
	}
}

// WebScrobblerStatus answers GET /api/webhooks/music.
func (s *Services) WebScrobblerStatus(ctx context.Context) map[string]any {
	enabled := s.WebScrobblerEnabled(ctx)
	_, count, _ := s.store.QueryMusicScrobbles(ctx, sqlite.MusicQuery{Limit: 1})
	status := "paused"
	if enabled {
		status = "ready"
	}
	return map[string]any{
		"ok":               true,
		"service":          "music-webhook",
		"status":           status,
		"enabled":          enabled,
		"accepted_methods": []string{"POST"},
		"total_records":    count,
		"instructions":     "Web Scrobbler extension -> Options -> Accounts -> Webhook -> URL: [host]/api/webhooks/music",
	}
}

// ---------- Import ----------

// ImportMusic handles POST /api/music/import with a JSON array ({"items": [...]}) or CSV
// ("artist,album,track,timestamp" or "artist,track,timestamp").
func (s *Services) ImportMusic(ctx context.Context, contentType string, raw []byte, now time.Time) (map[string]any, error) {
	var items []any
	if strings.Contains(contentType, "application/json") {
		var body any
		if json.Unmarshal(raw, &body) == nil {
			items = rawItemsStrict(body)
		}
	} else {
		items = parseMusicCSV(string(raw), now)
	}
	if len(items) == 0 {
		return nil, &MusicError{Status: http.StatusBadRequest, Message: "İçe aktarılacak scrobble verisi bulunamadı."}
	}

	inserted, updated := 0, 0
	for _, item := range items {
		artist := strings.TrimSpace(asString(jsGet(item, "artist")))
		track := strings.TrimSpace(firstTruthyString(jsGet(item, "track"), jsGet(item, "title")))
		uts := int64(jsNumber(firstTruthy(jsGet(item, "uts"), jsGet(item, "timestamp"))))
		if uts <= 0 {
			uts = now.Unix()
		}
		if artist == "" || track == "" {
			continue
		}
		listenedAt := firstTruthyString(jsGet(item, "listened_at"))
		if listenedAt == "" {
			listenedAt = isoMillis(uts)
		}
		source := firstTruthyString(jsGet(item, "source"))
		if source == "" {
			source = "import"
		}
		isNew, err := s.store.UpsertMusicScrobble(ctx, sqlite.MusicScrobble{
			ID: "imp_" + jsHash(fmt.Sprintf("%s:%s:%d", artist, track, uts)), Track: track, Artist: artist,
			Album:       strings.TrimSpace(asString(jsGet(item, "album"))),
			AlbumArtist: strings.TrimSpace(firstTruthyString(jsGet(item, "album_artist"), jsGet(item, "albumArtist"))),
			Duration:    int(jsNumber(jsGet(item, "duration"))),
			ListenedAt:  listenedAt, UTS: uts, Source: source,
			OriginURL: strings.TrimSpace(firstTruthyString(jsGet(item, "origin_url"), jsGet(item, "originUrl"))),
			ImageURL:  strings.TrimSpace(firstTruthyString(jsGet(item, "image_url"), jsGet(item, "imageUrl"))),
			RawJSON:   mustJSON(item),
		})
		if err != nil {
			return nil, err
		}
		if isNew {
			inserted++
		} else {
			updated++
		}
	}
	return map[string]any{
		"ok":       true,
		"total":    len(items),
		"inserted": inserted,
		"updated":  updated,
		"message":  fmt.Sprintf("%d scrobble içe aktarıldı (%d yeni, %d güncellendi).", len(items), inserted, updated),
	}, nil
}

func parseMusicCSV(text string, now time.Time) []any {
	var lines []string
	for _, l := range strings.Split(strings.ReplaceAll(text, "\r\n", "\n"), "\n") {
		if strings.TrimSpace(l) != "" {
			lines = append(lines, l)
		}
	}
	if len(lines) == 0 {
		return nil
	}
	header := strings.ToLower(lines[0])
	start := 0
	if strings.Contains(header, "artist") || strings.Contains(header, "track") || strings.Contains(header, "title") {
		start = 1
	}
	var items []any
	for i := start; i < len(lines); i++ {
		parts := strings.Split(lines[i], ",")
		for j := range parts {
			parts[j] = strings.Trim(strings.TrimSpace(parts[j]), `"`)
		}
		if len(parts) < 3 {
			continue
		}
		var artist, album, track, ts string
		if len(parts) >= 4 {
			artist, album, track, ts = parts[0], parts[1], parts[2], parts[3]
		} else {
			artist, track, ts = parts[0], parts[1], parts[2]
		}
		uts, err := strconv.ParseFloat(ts, 64)
		if err != nil || uts == 0 {
			if t, ok := parseTimeLoose(ts); ok {
				uts = float64(t.Unix())
			} else {
				uts = float64(now.Unix() - int64(len(lines)-i)*180)
			}
		}
		items = append(items, map[string]any{"artist": artist, "track": track, "album": album, "uts": uts, "source": "csv_import"})
	}
	return items
}

// ---------- Last.fm ----------

// SyncLastFM imports recent tracks from Last.fm. By default it only fetches tracks
// newer than the latest stored Last.fm scrobble.
func (s *Services) SyncLastFM(ctx context.Context, body map[string]any) (map[string]any, error) {
	savedUser, savedKey := s.LastFMCredentials(ctx)
	username := strings.TrimSpace(firstNonEmpty(asString(body["username"]), savedUser))
	apiKey := strings.TrimSpace(firstNonEmpty(asString(body["apiKey"]), savedKey))
	if username == "" {
		return nil, &MusicError{Status: http.StatusBadRequest, Message: "Last.fm kullanıcı adı tanımlanmamış."}
	}
	if apiKey == "" {
		return nil, &MusicError{Status: http.StatusBadRequest, Message: "Last.fm API Key tanımlanmamış. Ayarlardan Last.fm API Key girin."}
	}
	if strings.HasPrefix(apiKey, "http://") || strings.HasPrefix(apiKey, "https://") || len(apiKey) != 32 {
		return nil, &MusicError{Status: http.StatusBadRequest, Message: "Kayıtlı Last.fm API Key geçersiz (Webhook URL girilmiş olabilir). Lütfen Ayarlar > Last.fm bölümünden 32 haneli geçerli API Key girin."}
	}

	limit := clampInt(intOr(body["limit"], 200), 10, 200)
	page := intOr(body["page"], 1)
	if page < 1 {
		page = 1
	}
	maxPages := 1
	if truthy(body["allPages"]) {
		maxPages = intOr(body["maxPages"], 10)
		if maxPages > 20 {
			maxPages = 20
		}
	}
	incremental := body["incremental"] != false && !truthy(body["page"])

	var fromUTS int64
	if truthy(body["fromUts"]) {
		fromUTS = int64(jsNumber(body["fromUts"]))
	} else if incremental {
		if maxUTS, _ := s.store.MaxMusicUTS(ctx, "lastfm"); maxUTS > 0 {
			fromUTS = maxUTS + 1
		}
	}

	inserted, updated, processed, totalPages := 0, 0, 0, 1
	fail := func(err error) (map[string]any, error) {
		_ = s.store.RecordSyncRun(ctx, "music_sync_runs", "lastfm", "error", inserted, updated, err.Error())
		msg := err.Error()
		if strings.Contains(msg, "Invalid API key") || strings.Contains(msg, "error: 10") || strings.Contains(msg, "(10)") ||
			strings.Contains(msg, "403") || strings.Contains(msg, "401") {
			return nil, &MusicError{Status: http.StatusBadRequest, Message: "Geçersiz Last.fm API Anahtarı (Hata 10). Lütfen Ayarlar > Last.fm bölümünden geçerli 32 haneli API Key girin."}
		}
		return nil, &MusicError{Status: http.StatusInternalServerError, Message: msg}
	}

	for p := page; p < page+maxPages; p++ {
		params := url.Values{
			"method":   {"user.getrecenttracks"},
			"user":     {username},
			"api_key":  {apiKey},
			"format":   {"json"},
			"limit":    {strconv.Itoa(limit)},
			"page":     {strconv.Itoa(p)},
			"extended": {"1"},
		}
		if fromUTS > 0 {
			params.Set("from", strconv.FormatInt(fromUTS, 10))
		}
		data, status, err := s.getJSON(ctx, s.endpoints().LastFM+"?"+params.Encode(), nil)
		if err != nil {
			return fail(err)
		}
		if status < 200 || status > 299 {
			return fail(fmt.Errorf("Last.fm API Hatası: %d %s", status, compactJSON(data)))
		}
		if code := jsGet(data, "error"); code != nil {
			return fail(fmt.Errorf("Last.fm API Hatası (%v): %s", jsNumberString(code), asString(jsGet(data, "message"))))
		}
		recent := jsGet(data, "recenttracks")
		if recent == nil {
			break
		}
		totalPages = intOr(jsGet(recent, "@attr", "totalPages"), 1)
		tracks := asList(jsGet(recent, "track"))
		if len(tracks) == 0 {
			break
		}
		for _, t := range tracks {
			if asString(jsGet(t, "@attr", "nowplaying")) == "true" {
				continue
			}
			uts := int64(jsNumber(jsGet(t, "date", "uts")))
			if uts <= 0 {
				continue
			}
			artist := ""
			if a := jsGet(t, "artist"); a != nil {
				if _, isObj := a.(map[string]any); isObj {
					artist = firstTruthyString(jsGet(a, "name"), jsGet(a, "#text"))
				} else {
					artist = asString(a)
				}
			}
			track := strings.TrimSpace(asString(jsGet(t, "name")))
			album := ""
			if a := jsGet(t, "album"); a != nil {
				if _, isObj := a.(map[string]any); isObj {
					album = firstTruthyString(jsGet(a, "#text"), jsGet(a, "name"))
				} else {
					album = asString(a)
				}
			}
			if artist == "" || track == "" {
				continue
			}
			isNew, err := s.store.UpsertMusicScrobble(ctx, sqlite.MusicScrobble{
				ID: "lfm_" + jsHash(fmt.Sprintf("%s:%s:%d", artist, track, uts)), Track: track, Artist: artist,
				Album: album, ListenedAt: isoMillis(uts), UTS: uts, Source: "lastfm",
				OriginURL: asString(jsGet(t, "url")), ImageURL: lastFMImage(jsGet(t, "image")), RawJSON: mustJSON(t),
			})
			if err != nil {
				return fail(err)
			}
			if isNew {
				inserted++
			} else {
				updated++
			}
			processed++
		}
		if p >= totalPages {
			break
		}
	}

	_ = s.store.RecordSyncRun(ctx, "music_sync_runs", "lastfm", "success", inserted, updated,
		fmt.Sprintf("Last.fm senkronizasyonu tamamlandı: %d yeni, %d güncellendi.", inserted, updated))
	return map[string]any{
		"ok":             true,
		"inserted":       inserted,
		"updated":        updated,
		"totalProcessed": processed,
		"totalPages":     totalPages,
		"message":        fmt.Sprintf("Last.fm senkronizasyonu başarılı: %d yeni scrobble eklendi.", inserted),
	}, nil
}

func lastFMImage(images any) string {
	list, ok := images.([]any)
	if !ok || len(list) == 0 {
		return ""
	}
	for _, size := range []string{"extralarge", "large", "medium", "small"} {
		for _, img := range list {
			if asString(jsGet(img, "size")) == size {
				if text := strings.TrimSpace(asString(jsGet(img, "#text"))); text != "" {
					return text
				}
			}
		}
	}
	return strings.TrimSpace(asString(jsGet(list[len(list)-1], "#text")))
}

// ---------- ListenBrainz ----------

// SyncListenBrainz imports listens for a public ListenBrainz user. A saved token is
// sent when present.
func (s *Services) SyncListenBrainz(ctx context.Context, body map[string]any) (map[string]any, error) {
	settings, _ := s.store.GetMusicSettings(ctx)
	username := strings.TrimSpace(firstNonEmpty(asString(body["username"]), settings.ListenBrainzUsername))
	if username == "" {
		return nil, &MusicError{Status: http.StatusBadRequest, Message: "ListenBrainz kullanıcı adı tanımlanmamış. Ayarlardan kullanıcı adınızı girin."}
	}
	count := clampInt(intOr(body["count"], 100), 10, 100)
	var minTS int64
	if truthy(body["minTs"]) {
		minTS = int64(jsNumber(body["minTs"]))
	} else if maxUTS, _ := s.store.MaxMusicUTS(ctx, "listenbrainz"); maxUTS > 0 {
		minTS = maxUTS
	}

	inserted, updated, processed := 0, 0, 0
	fail := func(err error) (map[string]any, error) {
		_ = s.store.RecordSyncRun(ctx, "music_sync_runs", "listenbrainz", "error", inserted, updated, err.Error())
		return nil, &MusicError{Status: http.StatusInternalServerError, Message: err.Error()}
	}

	params := url.Values{"count": {strconv.Itoa(count)}}
	if minTS > 0 {
		params.Set("min_ts", strconv.FormatInt(minTS, 10))
	}
	headers := map[string]string{}
	if token := strings.TrimSpace(settings.ListenBrainzToken); token != "" {
		headers["Authorization"] = "Token " + token
	}
	endpoint := fmt.Sprintf("%s/1/user/%s/listens?%s", s.endpoints().ListenBrainz, url.PathEscape(username), params.Encode())
	data, status, err := s.getJSON(ctx, endpoint, headers)
	if err != nil {
		return fail(err)
	}
	if status < 200 || status > 299 {
		return fail(fmt.Errorf("ListenBrainz API Hatası (%d): %s", status, compactJSON(data)))
	}

	for _, l := range asList(jsGet(data, "payload", "listens")) {
		uts := int64(jsNumber(jsGet(l, "listened_at")))
		if uts <= 0 {
			continue
		}
		meta := jsGet(l, "track_metadata")
		artist := strings.TrimSpace(asString(jsGet(meta, "artist_name")))
		track := strings.TrimSpace(asString(jsGet(meta, "track_name")))
		if artist == "" || track == "" {
			continue
		}
		imageURL := ""
		if mbid := firstTruthyString(jsGet(meta, "mbid_mapping", "release_mbid"), jsGet(meta, "mbid_mapping", "recording_mbid")); mbid != "" {
			imageURL = "https://coverartarchive.org/release/" + mbid + "/front-250"
		}
		originURL := asString(jsGet(meta, "additional_info", "origin_url"))
		if originURL == "" {
			if spotify := asString(jsGet(meta, "additional_info", "spotify_id")); spotify != "" {
				originURL = "https://open.spotify.com/track/" + spotify
			}
		}
		isNew, err := s.store.UpsertMusicScrobble(ctx, sqlite.MusicScrobble{
			ID: "lb_" + jsHash(fmt.Sprintf("%s:%s:%d", artist, track, uts)), Track: track, Artist: artist,
			Album: strings.TrimSpace(asString(jsGet(meta, "release_name"))), ListenedAt: isoMillis(uts), UTS: uts,
			Source: "listenbrainz", OriginURL: originURL, ImageURL: imageURL, RawJSON: mustJSON(l),
		})
		if err != nil {
			return fail(err)
		}
		if isNew {
			inserted++
		} else {
			updated++
		}
		processed++
	}

	_ = s.store.RecordSyncRun(ctx, "music_sync_runs", "listenbrainz", "success", inserted, updated,
		fmt.Sprintf("ListenBrainz senkronizasyonu tamamlandı: %d yeni, %d güncellendi.", inserted, updated))
	return map[string]any{
		"ok":             true,
		"inserted":       inserted,
		"updated":        updated,
		"totalProcessed": processed,
		"message":        fmt.Sprintf("ListenBrainz senkronizasyonu başarılı: %d yeni dinleme eklendi.", inserted),
	}, nil
}

// ---------- Stats.fm ----------

// SyncStatsFM imports recent streams for a public Stats.fm profile. When the profile
// hides its streams, it falls back to the weekly top tracks.
func (s *Services) SyncStatsFM(ctx context.Context, body map[string]any, now time.Time) (map[string]any, error) {
	settings, _ := s.store.GetMusicSettings(ctx)
	username := strings.TrimSpace(firstNonEmpty(asString(body["username"]), settings.StatsFMUsername))
	if username == "" {
		return nil, &MusicError{Status: http.StatusBadRequest, Message: "Stats.fm kullanıcı adı tanımlanmamış. Ayarlardan Stats.fm kullanıcı adınızı girin."}
	}

	inserted, updated, processed := 0, 0, 0
	fail := func(err error) (map[string]any, error) {
		_ = s.store.RecordSyncRun(ctx, "music_sync_runs", "statsfm", "error", inserted, updated, err.Error())
		return nil, &MusicError{Status: http.StatusInternalServerError, Message: err.Error()}
	}
	base := s.endpoints().StatsFM + "/api/v1/users/" + url.PathEscape(username)

	data, status, err := s.getJSON(ctx, base+"/streams?limit=100", nil)
	if err != nil {
		return fail(err)
	}
	switch {
	case status == http.StatusNotFound:
		return fail(fmt.Errorf("Stats.fm kullanıcısı '%s' bulunamadı. Lütfen kullanıcı adınızı kontrol edin.", username))
	case status == http.StatusForbidden:
		return fail(fmt.Errorf("Stats.fm dinleme geçmişiniz gizli. Stats.fm uygulamasında Profil > Gizlilik Ayarları'ndan 'Recently Played' akışını herkese açık yapın."))
	case status < 200 || status > 299:
		return fail(fmt.Errorf("Stats.fm API Hatası (%d): %s", status, compactJSON(data)))
	}

	store := func(item any, uts int64, durationMs float64) error {
		t := jsGet(item, "track")
		if t == nil {
			t = item
		}
		artist := firstTruthyString(jsGet(t, "artists", "0", "name"), jsGet(item, "artistName"))
		track := firstTruthyString(jsGet(t, "name"), jsGet(item, "trackName"))
		if artist == "" || track == "" {
			return nil
		}
		isNew, err := s.store.UpsertMusicScrobble(ctx, sqlite.MusicScrobble{
			ID: "sfm_" + jsHash(fmt.Sprintf("%s:%s:%d", artist, track, uts)), Track: track, Artist: artist,
			Album:    firstTruthyString(jsGet(t, "albums", "0", "name"), jsGet(item, "albumName")),
			ImageURL: asString(jsGet(t, "albums", "0", "image")), Duration: int(durationMs / 1000),
			ListenedAt: isoMillis(uts), UTS: uts, Source: "statsfm", RawJSON: mustJSON(item),
		})
		if err != nil {
			return err
		}
		if isNew {
			inserted++
		} else {
			updated++
		}
		processed++
		return nil
	}

	items := asList(jsGet(data, "items"))
	if len(items) == 0 {
		items = asList(data)
	}
	if len(items) == 0 {
		top, topStatus, err := s.getJSON(ctx, base+"/top/tracks?range=weeks", nil)
		if err == nil && topStatus >= 200 && topStatus <= 299 {
			for _, it := range asList(jsGet(top, "items")) {
				t := jsGet(it, "track")
				if t == nil {
					t = it
				}
				if err := store(it, now.Unix(), jsNumber(jsGet(t, "durationMs"))); err != nil {
					return fail(err)
				}
			}
		}
	} else {
		for _, item := range items {
			t := jsGet(item, "track")
			if t == nil {
				t = item
			}
			uts := now.Unix()
			if ts, ok := parseTimeAny(firstTruthy(jsGet(item, "endTime"), jsGet(item, "playedAt"), jsGet(item, "timestamp"))); ok && ts.Unix() > 0 {
				uts = ts.Unix()
			}
			duration := jsNumber(firstTruthy(jsGet(item, "playedMs"), jsGet(t, "durationMs")))
			if err := store(item, uts, duration); err != nil {
				return fail(err)
			}
		}
	}

	_ = s.store.RecordSyncRun(ctx, "music_sync_runs", "statsfm", "success", inserted, updated,
		fmt.Sprintf("Stats.fm senkronizasyonu tamamlandı: %d yeni, %d güncellendi.", inserted, updated))
	return map[string]any{
		"ok":             true,
		"inserted":       inserted,
		"updated":        updated,
		"totalProcessed": processed,
		"message":        fmt.Sprintf("Stats.fm senkronizasyonu başarılı: %d yeni dinleme aktarıldı.", inserted),
	}, nil
}

// RunConfiguredLastFMSync is called by the background sync loop. It does nothing
// unless Last.fm is configured with a valid key.
func (s *Services) RunConfiguredLastFMSync(ctx context.Context) {
	settings, _ := s.store.GetMusicSettings(ctx)
	if settings.Exists && !settings.Enabled {
		return
	}
	username, apiKey := s.LastFMCredentials(ctx)
	if username == "" || len(apiKey) != 32 {
		return
	}
	_, _ = s.SyncLastFM(ctx, map[string]any{})
}

// ---------- helpers ----------

func (s *Services) getJSON(ctx context.Context, endpoint string, headers map[string]string) (any, int, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, endpoint, nil)
	if err != nil {
		return nil, 0, err
	}
	req.Header.Set("User-Agent", musicUserAgent)
	req.Header.Set("Accept", "application/json")
	for k, v := range headers {
		req.Header.Set(k, v)
	}
	resp, err := s.musicClient().Do(req)
	if err != nil {
		return nil, 0, err
	}
	defer resp.Body.Close()
	raw, err := io.ReadAll(io.LimitReader(resp.Body, 20<<20))
	if err != nil {
		return nil, resp.StatusCode, err
	}
	var data any
	if len(raw) > 0 && json.Unmarshal(raw, &data) != nil {
		data = strings.TrimSpace(string(raw))
	}
	return data, resp.StatusCode, nil
}

// jsHash reproduces the Worker's hashStr: a 32-bit "times 31" string hash over UTF-16
// code units, printed as the hex of its absolute value. Matching IDs keep data moved
// between the two engines free of duplicates.
func jsHash(s string) string {
	var h int32
	for _, unit := range utf16.Encode([]rune(s)) {
		h = (h << 5) - h + int32(unit)
	}
	v := int64(h)
	if v < 0 {
		v = -v
	}
	return strconv.FormatInt(v, 16)
}

// isoMillis formats like JavaScript's Date.toISOString(): UTC with milliseconds.
func isoMillis(uts int64) string {
	return time.Unix(uts, 0).UTC().Format("2006-01-02T15:04:05.000Z")
}

// jsGet walks nested JSON objects and arrays ("0" indexes an array), returning nil
// when any step is missing.
func jsGet(v any, path ...string) any {
	for _, key := range path {
		switch node := v.(type) {
		case map[string]any:
			v = node[key]
		case []any:
			i, err := strconv.Atoi(key)
			if err != nil || i < 0 || i >= len(node) {
				return nil
			}
			v = node[i]
		default:
			return nil
		}
	}
	return v
}

func truthy(v any) bool {
	switch x := v.(type) {
	case nil:
		return false
	case bool:
		return x
	case string:
		return x != ""
	case float64:
		return x != 0
	default:
		return true
	}
}

func firstTruthy(values ...any) any {
	for _, v := range values {
		if truthy(v) {
			return v
		}
	}
	return nil
}

func firstTruthyString(values ...any) string {
	for _, v := range values {
		if s := asString(v); s != "" {
			return s
		}
	}
	return ""
}

func firstNonEmpty(values ...string) string {
	for _, v := range values {
		if v != "" {
			return v
		}
	}
	return ""
}

func asString(v any) string {
	switch x := v.(type) {
	case string:
		return x
	case float64:
		return strconv.FormatFloat(x, 'f', -1, 64)
	default:
		return ""
	}
}

func jsNumber(v any) float64 {
	switch x := v.(type) {
	case float64:
		return x
	case bool:
		if x {
			return 1
		}
		return 0
	case string:
		f, err := strconv.ParseFloat(strings.TrimSpace(x), 64)
		if err != nil {
			return 0
		}
		return f
	default:
		return 0
	}
}

func jsNumberString(v any) string {
	if f, ok := v.(float64); ok {
		return strconv.FormatFloat(f, 'f', -1, 64)
	}
	return asString(v)
}

func intOr(v any, fallback int) int {
	if !truthy(v) {
		return fallback
	}
	n := jsNumber(v)
	if n == 0 {
		return fallback
	}
	return int(n)
}

func clampInt(v, lo, hi int) int {
	if v < lo {
		return lo
	}
	if v > hi {
		return hi
	}
	return v
}

func asList(v any) []any {
	switch x := v.(type) {
	case []any:
		return x
	case map[string]any:
		return []any{x}
	default:
		return nil
	}
}

// rawItems mirrors the Worker: an array body, a body with "items", or a single object.
func rawItems(body any) []any {
	if list, ok := body.([]any); ok {
		return list
	}
	if list, ok := jsGet(body, "items").([]any); ok {
		return list
	}
	return []any{body}
}

// rawItemsStrict accepts only an array or {"items": [...]}, as the import endpoint does.
func rawItemsStrict(body any) []any {
	if list, ok := body.([]any); ok {
		return list
	}
	if list, ok := jsGet(body, "items").([]any); ok {
		return list
	}
	return nil
}

func trimmedString(v any) string {
	s, _ := v.(string)
	return strings.TrimSpace(s)
}

func optionalString(v any) (string, bool) {
	s, ok := v.(string)
	return strings.TrimSpace(s), ok
}

func isEmptyString(v any) bool {
	s, ok := v.(string)
	return ok && s == ""
}

func mustJSON(v any) string {
	data, err := json.Marshal(v)
	if err != nil {
		return "{}"
	}
	return string(data)
}

func compactJSON(v any) string {
	if s, ok := v.(string); ok {
		return s
	}
	return mustJSON(v)
}

func parseTimeLoose(s string) (time.Time, bool) {
	s = strings.TrimSpace(s)
	for _, layout := range []string{time.RFC3339Nano, time.RFC3339, "2006-01-02 15:04:05", "2006-01-02T15:04:05", "2006-01-02 15:04", "02 Jan 2006, 15:04", "2006-01-02"} {
		if t, err := time.Parse(layout, s); err == nil {
			return t, true
		}
	}
	return time.Time{}, false
}

// parseTimeAny accepts an ISO date string or a millisecond timestamp, like new Date(value).
func parseTimeAny(v any) (time.Time, bool) {
	switch x := v.(type) {
	case string:
		return parseTimeLoose(x)
	case float64:
		return time.UnixMilli(int64(x)), true
	default:
		return time.Time{}, false
	}
}
