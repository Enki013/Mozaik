package http_test

import (
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"personal-records/internal/application"
	"personal-records/internal/config"
	"personal-records/internal/infrastructure/sqlite"
	httpserver "personal-records/internal/presentation/http"
	"personal-records/internal/shared"
)

func setupTestServer(t *testing.T) (*sqlite.Store, *application.Services, http.Handler) {
	t.Helper()
	tempDir := t.TempDir()
	dbPath := filepath.Join(tempDir, "test.sqlite3")

	store, err := sqlite.Open(dbPath)
	if err != nil {
		t.Fatalf("sqlite.Open failed: %v", err)
	}

	ctx := context.Background()
	if err := store.Init(ctx); err != nil {
		t.Fatalf("store.Init failed: %v", err)
	}

	cfg := config.Config{
		Port:   "8787",
		DBPath: dbPath,
	}

	services := application.NewServices(store, nil)
	handler := httpserver.NewHandler(cfg, services)

	return store, services, handler
}

func TestActivityEndpointEmpty(t *testing.T) {
	_, _, handler := setupTestServer(t)

	req := httptest.NewRequest(http.MethodGet, "/api/activity", nil)
	rec := httptest.NewRecorder()

	handler.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("expected status 200, got %d: %s", rec.Code, rec.Body.String())
	}

	corsHeader := rec.Header().Get("Access-Control-Allow-Origin")
	if corsHeader != "*" {
		t.Errorf("expected CORS header '*', got %q", corsHeader)
	}

	var resp application.ActivityResponse
	if err := json.Unmarshal(rec.Body.Bytes(), &resp); err != nil {
		t.Fatalf("failed to decode json response: %v", err)
	}

	if resp.Days != 7 {
		t.Errorf("expected default days=7, got %d", resp.Days)
	}
	if resp.Total != 0 {
		t.Errorf("expected total=0 for empty db, got %d", resp.Total)
	}
	if len(resp.Items) != 0 {
		t.Errorf("expected 0 items, got %d", len(resp.Items))
	}
	if len(resp.Groups) != 0 {
		t.Errorf("expected 0 groups, got %d", len(resp.Groups))
	}
}

func TestActivityEndpointWithData(t *testing.T) {
	store, services, handler := setupTestServer(t)
	ctx := context.Background()

	todayInt := shared.TodayDateInt()
	todayISO := shared.DateIntToISO(todayInt)
	yesterdayISO := shared.DateIntToISO(todayInt - 1)

	// 1. Add meal
	_, err := store.UpsertFoodEntry(ctx, sqlite.FoodEntry{
		FoodEntryID:          "fs_101",
		DateInt:              todayInt,
		Meal:                 "Dinner",
		FoodEntryName:        "Steak and Sweet Potato",
		FoodEntryDescription: "Grilled sirloin with baked sweet potato",
		Calories:             680,
		Carbohydrate:         42,
		Protein:              54,
		Fat:                  22,
		RawJSON:              "{}",
	})
	if err != nil {
		t.Fatalf("UpsertFoodEntry failed: %v", err)
	}

	// 2. Add Goodreads book via CSV import
	sampleCSVPath := filepath.Join("..", "..", "work", "sample_goodreads.csv")
	if csvBytes, err := os.ReadFile(sampleCSVPath); err == nil {
		_, err = services.ImportGoodreadsCSV(ctx, string(csvBytes))
		if err != nil {
			t.Fatalf("ImportGoodreadsCSV failed: %v", err)
		}
	} else {
		// Manual fallback if sample CSV file is moved
		_, _ = store.UpsertGoodreadsBook(ctx, sqlite.GoodreadsBook{
			BookID:         "1",
			Title:          "The Pragmatic Programmer",
			Author:         "Andrew Hunt",
			DateRead:       todayISO,
			ExclusiveShelf: "read",
			MyRating:       5,
		})
	}

	// 3. Add Letterboxd film for yesterday
	_, err = store.UpsertLetterboxdFilm(ctx, sqlite.LetterboxdFilm{
		FilmKey:     "lb_202",
		Name:        "Blade Runner 2049",
		Year:        2017,
		Rating:      5.0,
		WatchedDate: yesterdayISO,
		Source:      "rss",
		RawJSON:     "{}",
	})
	if err != nil {
		t.Fatalf("UpsertLetterboxdFilm failed: %v", err)
	}

	// Request /api/activity?days=30&limit=20
	req := httptest.NewRequest(http.MethodGet, "/api/activity?days=30&limit=20", nil)
	rec := httptest.NewRecorder()

	handler.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("status = %d, want 200: %s", rec.Code, rec.Body.String())
	}

	var resp application.ActivityResponse
	if err := json.Unmarshal(rec.Body.Bytes(), &resp); err != nil {
		t.Fatalf("failed to decode JSON: %v", err)
	}

	if resp.Days != 30 {
		t.Errorf("resp.Days = %d, want 30", resp.Days)
	}

	if resp.Total == 0 {
		t.Fatalf("resp.Total should be > 0")
	}

	// Check that items contain our meal and film
	foundMeal := false
	foundFilm := false
	for _, item := range resp.Items {
		if item.Source == "fatsecret" && item.Title == "Steak and Sweet Potato" {
			foundMeal = true
			if item.Date != todayISO {
				t.Errorf("meal date = %s, want %s", item.Date, todayISO)
			}
			if item.Type != "meal" {
				t.Errorf("meal type = %s, want 'meal'", item.Type)
			}
			if cal, ok := item.Details["calories"].(float64); !ok || cal != 680 {
				t.Errorf("meal calories = %v, want 680", item.Details["calories"])
			}
		}
		if item.Source == "letterboxd" && item.Title == "Blade Runner 2049" {
			foundFilm = true
			if item.Date != yesterdayISO {
				t.Errorf("film date = %s, want %s", item.Date, yesterdayISO)
			}
			if item.Type != "film" {
				t.Errorf("film type = %s, want 'film'", item.Type)
			}
		}
	}

	if !foundMeal {
		t.Errorf("expected meal item not found in response items")
	}
	if !foundFilm {
		t.Errorf("expected film item not found in response items")
	}

	// Verify group labels
	foundTodayGroup := false
	foundYesterdayGroup := false
	for _, g := range resp.Groups {
		if g.Date == todayISO {
			foundTodayGroup = true
			if g.Label != "Bugün" {
				t.Errorf("today group label = %q, want 'Bugün'", g.Label)
			}
			if cal, ok := g.Summary["total_calories"].(float64); !ok || cal < 680 {
				t.Errorf("today summary calories = %v, want >= 680", g.Summary["total_calories"])
			}
		}
		if g.Date == yesterdayISO {
			foundYesterdayGroup = true
			if g.Label != "Dün" {
				t.Errorf("yesterday group label = %q, want 'Dün'", g.Label)
			}
			if films, ok := g.Summary["films_count"].(float64); !ok || films < 1 {
				t.Errorf("yesterday summary films = %v, want >= 1", g.Summary["films_count"])
			}
		}
	}

	if !foundTodayGroup {
		t.Errorf("today group not found")
	}
	if !foundYesterdayGroup {
		t.Errorf("yesterday group not found")
	}
}

func TestActivityOptionsCors(t *testing.T) {
	_, _, handler := setupTestServer(t)

	req := httptest.NewRequest(http.MethodOptions, "/api/activity", nil)
	rec := httptest.NewRecorder()

	handler.ServeHTTP(rec, req)

	if rec.Code != http.StatusNoContent {
		t.Errorf("expected OPTIONS status 204, got %d", rec.Code)
	}
	if rec.Header().Get("Access-Control-Allow-Origin") != "*" {
		t.Errorf("expected CORS origin '*'")
	}
}

func TestFatSecretSettingsEndpoints(t *testing.T) {
	_, _, handler := setupTestServer(t)

	// 1. Initial GET - should not have keys
	req := httptest.NewRequest(http.MethodGet, "/api/settings/fatsecret", nil)
	rec := httptest.NewRecorder()
	handler.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d: %s", rec.Code, rec.Body.String())
	}
	var res map[string]any
	_ = json.NewDecoder(rec.Body).Decode(&res)
	if res["has_keys"] == true {
		t.Errorf("expected has_keys false, got true")
	}

	// 2. POST - Save keys
	body := `{"consumer_key":"my_key_12345678","consumer_secret":"my_secret_abcdef"}`
	req = httptest.NewRequest(http.MethodPost, "/api/settings/fatsecret", strings.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	rec = httptest.NewRecorder()
	handler.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("expected 200 on post, got %d: %s", rec.Code, rec.Body.String())
	}

	// 3. GET again - should have keys and masked key
	req = httptest.NewRequest(http.MethodGet, "/api/settings/fatsecret", nil)
	rec = httptest.NewRecorder()
	handler.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d: %s", rec.Code, rec.Body.String())
	}
	var res2 map[string]any
	_ = json.NewDecoder(rec.Body).Decode(&res2)
	if res2["has_keys"] != true {
		t.Errorf("expected has_keys true, got %v", res2["has_keys"])
	}
	if res2["consumer_key"] != "my_k...5678" {
		t.Errorf("expected masked key 'my_k...5678', got %v", res2["consumer_key"])
	}
}

func TestNotificationWebhookAndAPI(t *testing.T) {
	_, _, handler := setupTestServer(t)

	now := time.Now().UTC().Format(time.RFC3339)
	payload := fmt.Sprintf(`{
		"package": "com.whatsapp",
		"appName": "WhatsApp",
		"title": "Ahmet",
		"text": "Toplantı saat 15:00",
		"category": "msg",
		"post_time": "%s",
		"key": "msg_key_123"
	}`, now)
	req := httptest.NewRequest(http.MethodPost, "/api/webhooks/notification", strings.NewReader(payload))
	req.Header.Set("Content-Type", "application/json")
	rec := httptest.NewRecorder()
	handler.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("expected 200 on webhook post, got %d: %s", rec.Code, rec.Body.String())
	}
	var res map[string]any
	_ = json.NewDecoder(rec.Body).Decode(&res)
	if res["success"] != true {
		t.Errorf("expected success true, got %v", res["success"])
	}
	if res["inserted"] != float64(1) {
		t.Errorf("expected 1 inserted, got %v", res["inserted"])
	}

	// 2. Post same notification again - should update, not duplicate
	req2 := httptest.NewRequest(http.MethodPost, "/api/webhooks/notification", strings.NewReader(payload))
	req2.Header.Set("Content-Type", "application/json")
	rec2 := httptest.NewRecorder()
	handler.ServeHTTP(rec2, req2)

	if rec2.Code != http.StatusOK {
		t.Fatalf("expected 200 on second post, got %d: %s", rec2.Code, rec2.Body.String())
	}
	var res2 map[string]any
	_ = json.NewDecoder(rec2.Body).Decode(&res2)
	if res2["updated"] != float64(1) {
		t.Errorf("expected 1 updated, got %v", res2["updated"])
	}

	// 3. GET /api/notifications
	reqGet := httptest.NewRequest(http.MethodGet, "/api/notifications", nil)
	recGet := httptest.NewRecorder()
	handler.ServeHTTP(recGet, reqGet)

	if recGet.Code != http.StatusOK {
		t.Fatalf("expected 200 on get notifications, got %d: %s", recGet.Code, recGet.Body.String())
	}
	var notifList map[string]any
	_ = json.NewDecoder(recGet.Body).Decode(&notifList)
	if notifList["count"] != float64(1) {
		t.Errorf("expected count 1, got %v", notifList["count"])
	}

	// 4. GET /api/activity
	reqAct := httptest.NewRequest(http.MethodGet, "/api/activity", nil)
	recAct := httptest.NewRecorder()
	handler.ServeHTTP(recAct, reqAct)

	if recAct.Code != http.StatusOK {
		t.Fatalf("expected 200 on activity, got %d: %s", recAct.Code, recAct.Body.String())
	}
	var actRes application.ActivityResponse
	_ = json.NewDecoder(recAct.Body).Decode(&actRes)
	if len(actRes.Items) != 1 {
		t.Fatalf("expected 1 activity item, got %d", len(actRes.Items))
	}
	if actRes.Items[0].Source != "notification" {
		t.Errorf("expected item source 'notification', got %s", actRes.Items[0].Source)
	}
	if actRes.Items[0].Title != "WhatsApp: Ahmet" {
		t.Errorf("expected title 'WhatsApp: Ahmet', got %s", actRes.Items[0].Title)
	}
}

func TestSettingsAPILetterboxdAndGoodreads(t *testing.T) {
	_, _, handler := setupTestServer(t)

	// 1. Post username to /api/settings/letterboxd - should auto-expand to RSS URL
	lbPayload := `{"feed_url": "enesben_", "interval_minutes": 180, "enabled": true}`
	reqLb := httptest.NewRequest(http.MethodPost, "/api/settings/letterboxd", strings.NewReader(lbPayload))
	reqLb.Header.Set("Content-Type", "application/json")
	recLb := httptest.NewRecorder()
	handler.ServeHTTP(recLb, reqLb)

	if recLb.Code != http.StatusOK {
		t.Fatalf("expected 200 on post letterboxd settings, got %d: %s", recLb.Code, recLb.Body.String())
	}
	var lbRes map[string]any
	_ = json.NewDecoder(recLb.Body).Decode(&lbRes)
	if lbRes["feed_url"] != "https://letterboxd.com/enesben_/rss/" {
		t.Errorf("expected expanded rss url, got %v", lbRes["feed_url"])
	}

	// 2. GET /api/settings/letterboxd
	reqLbGet := httptest.NewRequest(http.MethodGet, "/api/settings/letterboxd", nil)
	recLbGet := httptest.NewRecorder()
	handler.ServeHTTP(recLbGet, reqLbGet)

	if recLbGet.Code != http.StatusOK {
		t.Fatalf("expected 200 on get letterboxd settings, got %d", recLbGet.Code)
	}
	var lbGetRes map[string]any
	_ = json.NewDecoder(recLbGet.Body).Decode(&lbGetRes)
	if lbGetRes["feed_url"] != "https://letterboxd.com/enesben_/rss/" {
		t.Errorf("expected stored rss url, got %v", lbGetRes["feed_url"])
	}

	// 3. Post full url to /api/settings/goodreads
	grPayload := `{"feed_url": "https://www.goodreads.com/review/list_rss/12345?shelf=read", "interval_minutes": 360, "enabled": true}`
	reqGr := httptest.NewRequest(http.MethodPost, "/api/settings/goodreads", strings.NewReader(grPayload))
	reqGr.Header.Set("Content-Type", "application/json")
	recGr := httptest.NewRecorder()
	handler.ServeHTTP(recGr, reqGr)

	if recGr.Code != http.StatusOK {
		t.Fatalf("expected 200 on post goodreads settings, got %d: %s", recGr.Code, recGr.Body.String())
	}

	// 4. GET /api/settings/goodreads
	reqGrGet := httptest.NewRequest(http.MethodGet, "/api/settings/goodreads", nil)
	recGrGet := httptest.NewRecorder()
	handler.ServeHTTP(recGrGet, reqGrGet)

	if recGrGet.Code != http.StatusOK {
		t.Fatalf("expected 200 on get goodreads settings, got %d", recGrGet.Code)
	}
	var grGetRes map[string]any
	_ = json.NewDecoder(recGrGet.Body).Decode(&grGetRes)
	if grGetRes["feed_url"] != "https://www.goodreads.com/review/list_rss/12345?shelf=read" {
		t.Errorf("expected stored goodreads rss url, got %v", grGetRes["feed_url"])
	}
}

func init() {
	_ = time.Local
}


