package http_test

import (
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"personal-records/internal/infrastructure/sqlite"
)

func doJSON(t *testing.T, h http.Handler, method, target, body string, headers map[string]string) (int, map[string]any) {
	t.Helper()
	var req *http.Request
	if body == "" {
		req = httptest.NewRequest(method, target, nil)
	} else {
		req = httptest.NewRequest(method, target, strings.NewReader(body))
		req.Header.Set("Content-Type", "application/json")
	}
	for k, v := range headers {
		req.Header.Set(k, v)
	}
	rec := httptest.NewRecorder()
	h.ServeHTTP(rec, req)
	out := map[string]any{}
	if err := json.Unmarshal(rec.Body.Bytes(), &out); err != nil {
		t.Fatalf("%s %s: invalid JSON %q", method, target, rec.Body.String())
	}
	return rec.Code, out
}

func seedScrobbles(t *testing.T, store *sqlite.Store) {
	t.Helper()
	rows := []sqlite.MusicScrobble{
		{ID: "1", Track: "Reckoner", Artist: "Radiohead", Album: "In Rainbows", ListenedAt: "2025-10-29T12:00:00.000Z", UTS: 1761739200, Source: "lastfm"},
		{ID: "2", Track: "Nude", Artist: "Radiohead", Album: "In Rainbows", ListenedAt: "2025-10-29T11:00:00.000Z", UTS: 1761735600, Source: "web_scrobbler"},
		{ID: "3", Track: "Dreams", Artist: "Fleetwood Mac", Album: "Rumours", ListenedAt: "2025-10-28T10:00:00.000Z", UTS: 1761645600, Source: "lastfm"},
	}
	for _, r := range rows {
		r.RawJSON = "{}"
		if _, err := store.UpsertMusicScrobble(context.Background(), r); err != nil {
			t.Fatal(err)
		}
	}
}

func TestMusicListEndpoint(t *testing.T) {
	store, _, h := setupTestServer(t)
	seedScrobbles(t, store)

	code, body := doJSON(t, h, http.MethodGet, "/api/music?limit=2", "", nil)
	if code != 200 || body["total"] != 3.0 || body["count"] != 2.0 || body["totalPages"] != 2.0 || body["page"] != 1.0 {
		t.Fatalf("paging: %d %v", code, body)
	}
	first := body["items"].([]any)[0].(map[string]any)
	for _, key := range []string{"id", "track", "artist", "album", "album_artist", "duration", "listened_at", "uts", "source", "origin_url", "image_url", "raw_json", "created_at"} {
		if _, ok := first[key]; !ok {
			t.Errorf("item is missing %q", key)
		}
	}
	if first["track"] != "Reckoner" || first["album_artist"] != "" {
		t.Fatalf("unexpected first item: %v", first)
	}

	cases := map[string]float64{
		"/api/music?artist=Radiohead":                        2,
		"/api/music?source=lastfm":                           2,
		"/api/music?search=dream":                            1,
		"/api/music?album=Rumours":                           1,
		"/api/music?date=2025-10-29":                         2,
		"/api/music?from=1761700000":                         2,
		"/api/music?from=2025-10-29&to=2025-10-29T11:30:00Z": 1,
		"/api/music?page=2&limit=2":                          3,
	}
	for target, want := range cases {
		_, body := doJSON(t, h, http.MethodGet, target, "", nil)
		if body["total"] != want {
			t.Errorf("%s: total %v, want %v", target, body["total"], want)
		}
	}
	_, body = doJSON(t, h, http.MethodGet, "/api/music?limit=999", "", nil)
	if body["limit"] != 200.0 {
		t.Errorf("limit should be capped at 200, got %v", body["limit"])
	}
}

func TestMusicStatsEndpoint(t *testing.T) {
	store, _, h := setupTestServer(t)
	seedScrobbles(t, store)
	code, body := doJSON(t, h, http.MethodGet, "/api/music/stats", "", nil)
	if code != 200 || body["total_scrobbles"] != 3.0 || body["unique_artists"] != 2.0 || body["unique_albums"] != 2.0 {
		t.Fatalf("stats: %d %v", code, body)
	}
	top := body["top_artists"].([]any)[0].(map[string]any)
	if top["artist"] != "Radiohead" || top["count"] != 2.0 {
		t.Fatalf("top artist: %v", top)
	}
	for _, key := range []string{"today_scrobbles", "week_scrobbles", "month_scrobbles", "top_albums", "recent_scrobbles", "last_sync"} {
		if _, ok := body[key]; !ok {
			t.Errorf("stats missing %q", key)
		}
	}
}

func TestMusicSettingsAndPluginsEndpoints(t *testing.T) {
	_, _, h := setupTestServer(t)
	t.Setenv("LASTFM_API_KEY", "")
	t.Setenv("LASTFM_USERNAME", "")

	code, body := doJSON(t, h, http.MethodPost, "/api/settings/music", `{"lastfm_api_key":"too-short"}`, nil)
	if code != http.StatusBadRequest || body["ok"] != false {
		t.Fatalf("invalid key: %d %v", code, body)
	}
	code, body = doJSON(t, h, http.MethodPost, "/api/settings/music", `{"lastfm_username":"alice","statsfm_username":"sfm","web_scrobbler_enabled":true}`, nil)
	if code != 200 || body["ok"] != true {
		t.Fatalf("save: %d %v", code, body)
	}
	_, body = doJSON(t, h, http.MethodGet, "/api/settings/music", "", map[string]string{"X-Forwarded-Proto": "https"})
	if body["lastfm_username"] != "alice" || body["statsfm_username"] != "sfm" || body["web_scrobbler_enabled"] != true ||
		body["interval_minutes"] != 360.0 || body["enabled"] != true || body["webhook_url"] != "https://example.com/api/webhooks/music" {
		t.Fatalf("settings: %v", body)
	}

	_, body = doJSON(t, h, http.MethodGet, "/api/settings/plugins", "", nil)
	if body["ok"] != true || body["plugins"].(map[string]any)["web_scrobbler"] != true {
		t.Fatalf("plugins after settings save: %v", body)
	}
	code, body = doJSON(t, h, http.MethodPost, "/api/settings/plugins", `{"plugin_id":"lastfm","enabled":true}`, nil)
	if code != 200 || body["ok"] != true {
		t.Fatalf("toggle: %d %v", code, body)
	}
	_, body = doJSON(t, h, http.MethodGet, "/api/settings/plugins", "", nil)
	if body["plugins"].(map[string]any)["lastfm"] != true {
		t.Fatalf("lastfm should be enabled: %v", body)
	}
}

func TestMusicWebhookEndpointAuthAndAliases(t *testing.T) {
	_, _, h := setupTestServer(t)
	t.Setenv("API_KEY", "secret-key")
	t.Setenv("WEBHOOK_SECRET", "")

	if code, _ := doJSON(t, h, http.MethodPost, "/api/webhooks/music", `{"artist":"A","track":"B"}`, nil); code != http.StatusUnauthorized {
		t.Fatalf("webhook without key should be 401, got %d", code)
	}
	auth := map[string]string{"X-API-Key": "secret-key"}
	if code, _ := doJSON(t, h, http.MethodPost, "/api/settings/plugins", `{"plugin_id":"web_scrobbler","enabled":true}`, auth); code != 200 {
		t.Fatalf("enable plugin: %d", code)
	}
	for i, target := range []string{"/api/webhooks/music", "/api/webhooks/scrobbler", "/webhooks/music"} {
		code, body := doJSON(t, h, http.MethodPost, target, fmt.Sprintf(`{"artist":"A","track":"T%d","uts":%d}`, i, 1761739200+i), auth)
		if code != 200 || body["inserted"] != 1.0 {
			t.Fatalf("%s: %d %v", target, code, body)
		}
	}
	_, body := doJSON(t, h, http.MethodGet, "/api/webhooks/music?api_key=secret-key", "", nil)
	if body["status"] != "ready" || body["total_records"] != 3.0 {
		t.Fatalf("status: %v", body)
	}
}

func TestMusicSyncCooldown(t *testing.T) {
	store, _, h := setupTestServer(t)
	if err := store.RecordSyncRun(context.Background(), "music_sync_runs", "lastfm", "success", 0, 0, "x"); err != nil {
		t.Fatal(err)
	}
	code, body := doJSON(t, h, http.MethodPost, "/api/music/sync-lastfm", "", nil)
	if code != http.StatusTooManyRequests || body["cooldown"] != true {
		t.Fatalf("expected cooldown, got %d %v", code, body)
	}
	// force=true skips the cooldown and reaches validation (no username configured).
	t.Setenv("LASTFM_USERNAME", "")
	code, body = doJSON(t, h, http.MethodPost, "/api/music/sync-lastfm?force=true", "", nil)
	if code != http.StatusBadRequest || body["ok"] != false {
		t.Fatalf("expected a validation error, got %d %v", code, body)
	}
	code, _ = doJSON(t, h, http.MethodPost, "/api/music/sync-listenbrainz", "", nil)
	if code != http.StatusBadRequest {
		t.Fatalf("listenbrainz without username should be 400, got %d", code)
	}
	code, _ = doJSON(t, h, http.MethodPost, "/api/music/sync-statsfm", "", nil)
	if code != http.StatusBadRequest {
		t.Fatalf("statsfm without username should be 400, got %d", code)
	}
}

func TestMusicImportEndpoint(t *testing.T) {
	_, _, h := setupTestServer(t)
	req := httptest.NewRequest(http.MethodPost, "/api/music/import", strings.NewReader("artist,track,timestamp\nRadiohead,Reckoner,1761739200\n"))
	req.Header.Set("Content-Type", "text/csv")
	rec := httptest.NewRecorder()
	h.ServeHTTP(rec, req)
	var body map[string]any
	_ = json.Unmarshal(rec.Body.Bytes(), &body)
	if rec.Code != 200 || body["inserted"] != 1.0 {
		t.Fatalf("import: %d %s", rec.Code, rec.Body.String())
	}
}
