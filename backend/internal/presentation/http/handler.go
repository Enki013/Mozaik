package http

import (
	"context"
	"encoding/json"
	"fmt"
	"html"
	"io"
	"net/http"
	"os"
	"strconv"
	"strings"
	"time"

	"personal-records/internal/application"
	"personal-records/internal/config"
	"personal-records/internal/infrastructure/sqlite"
	"personal-records/internal/shared"
)

type Handler struct {
	cfg      config.Config
	services *application.Services
	mux      *http.ServeMux
}

func NewHandler(cfg config.Config, services *application.Services) http.Handler {
	h := &Handler{cfg: cfg, services: services, mux: http.NewServeMux()}
	h.routes()
	return h
}

func (h *Handler) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Access-Control-Allow-Origin", "*")
	w.Header().Set("Access-Control-Allow-Headers", "Content-Type")
	w.Header().Set("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
	if r.Method == http.MethodOptions {
		w.WriteHeader(http.StatusNoContent)
		return
	}
	h.mux.ServeHTTP(w, r)
}

func (h *Handler) routes() {
	h.mux.HandleFunc("/", h.home)
	h.mux.HandleFunc("/api/health", h.health)
	h.mux.HandleFunc("/api/dashboard", h.dashboard)
	h.mux.HandleFunc("/api/activity", h.activity)
	h.mux.HandleFunc("/api/sync/status", h.syncStatus)
	h.mux.HandleFunc("/api/fatsecret/food-entries", h.fatsecretEntries)
	h.mux.HandleFunc("/api/fatsecret/daily-summary", h.fatsecretSummary)
	h.mux.HandleFunc("/api/goodreads/books", h.goodreadsBooks)
	h.mux.HandleFunc("/api/letterboxd/films", h.letterboxdFilms)
	h.mux.HandleFunc("/api/sync/fatsecret", h.apiSyncFatSecret)
	h.mux.HandleFunc("/api/sync/goodreads", h.apiSyncGoodreads)
	h.mux.HandleFunc("/api/sync/letterboxd", h.apiSyncLetterboxd)
	h.mux.HandleFunc("/api/export/all", h.exportAll)
	h.mux.HandleFunc("/api/export/db", h.exportDB)
	h.mux.HandleFunc("/goodreads/export", h.goodreadsExport)
	h.mux.HandleFunc("/goodreads/import", h.goodreadsImport)
	h.mux.HandleFunc("/goodreads/settings", h.goodreadsSettings)
	h.mux.HandleFunc("/api/settings/goodreads", h.goodreadsSettings)
	h.mux.HandleFunc("/goodreads/sync", h.goodreadsSync)
	h.mux.HandleFunc("/letterboxd/export", h.letterboxdExport)
	h.mux.HandleFunc("/letterboxd/import", h.letterboxdImport)
	h.mux.HandleFunc("/letterboxd/settings", h.letterboxdSettings)
	h.mux.HandleFunc("/api/settings/letterboxd", h.letterboxdSettings)
	h.mux.HandleFunc("/letterboxd/sync", h.letterboxdSync)
	h.mux.HandleFunc("/fatsecret/export", h.fatsecretExport)
	h.mux.HandleFunc("/fatsecret/search", h.fatsecretSearch)
	h.mux.HandleFunc("/fatsecret/connect", h.fatsecretConnect)
	h.mux.HandleFunc("/fatsecret/callback", h.fatsecretCallback)
	h.mux.HandleFunc("/fatsecret/sync", h.fatsecretSync)
	h.mux.HandleFunc("/settings/calorie-target", h.updateCalorieTarget)
	h.mux.HandleFunc("/api/settings/calorie-target", h.updateCalorieTarget)
	h.mux.HandleFunc("/api/settings/fatsecret", h.fatsecretSettings)
	h.mux.HandleFunc("/settings/fatsecret", h.fatsecretSettings)
	h.mux.HandleFunc("/api/webhooks/notification", h.notificationWebhook)
	h.mux.HandleFunc("/api/webhooks/notifications", h.notificationWebhook)
	h.mux.HandleFunc("/api/notifications", h.notificationsAPI)
}

func (h *Handler) home(w http.ResponseWriter, r *http.Request) {
	if r.URL.Path != "/" {
		http.NotFound(w, r)
		return
	}
	ctx := r.Context()
	counts, err := h.services.Counts(ctx)
	if err != nil {
		h.htmlError(w, err)
		return
	}
	activityDays := 30
	if d := r.URL.Query().Get("days"); d != "" {
		if val := shared.Int(d); val > 0 {
			activityDays = val
		}
	}
	fromDate := strings.TrimSpace(r.URL.Query().Get("from"))
	toDate := strings.TrimSpace(r.URL.Query().Get("to"))
	if singleDate := strings.TrimSpace(r.URL.Query().Get("date")); singleDate != "" {
		fromDate = singleDate
		toDate = singleDate
	}

	books, _ := h.services.GoodreadsBooks(ctx, r.URL.Query().Get("q"), r.URL.Query().Get("shelf"), 250)
	shelves, _ := h.services.GoodreadsShelves(ctx)
	goodreadsSettings, _ := h.services.GoodreadsSettings(ctx)
	letterboxdSettings, _ := h.services.LetterboxdSettings(ctx)
	letterboxd, _ := h.services.LetterboxdFilms(ctx, 250)
	foodSummary, _ := h.services.FatSecretDailySummary(ctx, 30)
	foodEntries, _ := h.services.FoodEntries(ctx, 500)
	dashboard, _ := h.services.Dashboard(ctx)
	overview, _ := h.services.OverviewStats(ctx)
	activity, _ := h.services.ActivityTimelineRange(ctx, fromDate, toDate, activityDays, 250)
	calorieTarget := h.services.GetCalorieTarget(ctx)
	recentSyncRuns, _ := h.services.RecentSyncRuns(ctx, 10)

	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	_, _ = io.WriteString(w, renderHome(pageData{
		DBName:             "personal_records.sqlite3",
		Counts:             counts,
		Dashboard:          dashboard,
		Activity:           activity,
		ActivityDays:       activityDays,
		ActivityFromDate:   fromDate,
		ActivityToDate:     toDate,
		Books:              books,
		Shelves:            shelves,
		SelectedShelf:      r.URL.Query().Get("shelf"),
		Search:             r.URL.Query().Get("q"),
		GoodreadsSettings:  goodreadsSettings,
		LetterboxdSettings: letterboxdSettings,
		LetterboxdFilms:    letterboxd,
		FoodSummary:        foodSummary,
		FoodEntries:        foodEntries,
		FatSecretConnected: h.services.FatSecretConnected(ctx),
		OAuthReady:         config.Env("FATSECRET_CONSUMER_KEY") != "" && config.Env("FATSECRET_CONSUMER_SECRET") != "",
		Overview:           overview,
		CalorieTarget:      calorieTarget,
		RecentSyncRuns:     recentSyncRuns,
	}))
}

func (h *Handler) health(w http.ResponseWriter, r *http.Request) {
	h.writeJSON(w, http.StatusOK, map[string]any{"ok": true})
}

func (h *Handler) dashboard(w http.ResponseWriter, r *http.Request) {
	payload, err := h.services.Dashboard(r.Context())
	h.jsonOrError(w, payload, err)
}

func (h *Handler) activity(w http.ResponseWriter, r *http.Request) {
	days := 7
	if r.URL.Query().Has("days") {
		days = queryInt(r, "days", 0)
	}
	fromDate := strings.TrimSpace(r.URL.Query().Get("from"))
	toDate := strings.TrimSpace(r.URL.Query().Get("to"))
	if singleDate := strings.TrimSpace(r.URL.Query().Get("date")); singleDate != "" {
		fromDate = singleDate
		toDate = singleDate
	}
	limit := queryInt(r, "limit", 50)
	payload, err := h.services.ActivityTimelineRange(r.Context(), fromDate, toDate, days, limit)
	h.jsonOrError(w, payload, err)
}

func (h *Handler) syncStatus(w http.ResponseWriter, r *http.Request) {
	payload, err := h.services.Dashboard(r.Context())
	if err != nil {
		h.jsonError(w, err)
		return
	}
	h.writeJSON(w, http.StatusOK, payload.LastSync)
}

func (h *Handler) fatsecretEntries(w http.ResponseWriter, r *http.Request) {
	items, err := h.services.FoodEntries(r.Context(), queryInt(r, "limit", 100))
	h.jsonOrError(w, map[string]any{"items": items}, err)
}

func (h *Handler) fatsecretSummary(w http.ResponseWriter, r *http.Request) {
	items, err := h.services.FatSecretDailySummary(r.Context(), queryInt(r, "limit", 30))
	h.jsonOrError(w, map[string]any{"items": items}, err)
}

func (h *Handler) goodreadsBooks(w http.ResponseWriter, r *http.Request) {
	items, err := h.services.GoodreadsBooks(r.Context(), r.URL.Query().Get("q"), r.URL.Query().Get("shelf"), queryInt(r, "limit", 100))
	h.jsonOrError(w, map[string]any{"items": items}, err)
}

func (h *Handler) letterboxdFilms(w http.ResponseWriter, r *http.Request) {
	items, err := h.services.LetterboxdFilms(r.Context(), queryInt(r, "limit", 100))
	h.jsonOrError(w, map[string]any{"items": items}, err)
}

func (h *Handler) apiSyncFatSecret(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.NotFound(w, r)
		return
	}
	var body struct {
		Days int `json:"days"`
	}
	_ = json.NewDecoder(r.Body).Decode(&body)
	result, err := h.services.SyncFatSecretFoodEntries(r.Context(), body.Days)
	h.jsonOrError(w, result, err)
}

func (h *Handler) apiSyncGoodreads(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.NotFound(w, r)
		return
	}
	result, err := h.services.RunConfiguredGoodreadsSync(r.Context())
	h.jsonOrError(w, result, err)
}

func (h *Handler) apiSyncLetterboxd(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.NotFound(w, r)
		return
	}
	result, err := h.services.RunConfiguredLetterboxdSync(r.Context())
	h.jsonOrError(w, result, err)
}

func (h *Handler) goodreadsExport(w http.ResponseWriter, r *http.Request) {
	books, err := h.services.ExportGoodreads(r.Context())
	h.jsonOrError(w, books, err)
}

func (h *Handler) exportAll(w http.ResponseWriter, r *http.Request) {
	data, err := h.services.AllDataExport(r.Context())
	if err != nil {
		h.jsonOrError(w, nil, err)
		return
	}
	filename := fmt.Sprintf("personal_records_backup_%s.json", time.Now().Format("2006-01-02"))
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.Header().Set("Content-Disposition", fmt.Sprintf("attachment; filename=%q", filename))
	_ = json.NewEncoder(w).Encode(data)
}

func (h *Handler) exportDB(w http.ResponseWriter, r *http.Request) {
	dbPath := "personal_records.sqlite3"
	f, err := os.Open(dbPath)
	if err != nil {
		http.Error(w, "Veritabanı dosyası bulunamadı: "+err.Error(), http.StatusInternalServerError)
		return
	}
	defer f.Close()
	w.Header().Set("Content-Type", "application/octet-stream")
	w.Header().Set("Content-Disposition", "attachment; filename=\"personal_records.sqlite3\"")
	_, _ = io.Copy(w, f)
}

func (h *Handler) letterboxdExport(w http.ResponseWriter, r *http.Request) {
	films, err := h.services.LetterboxdFilms(r.Context(), 10000)
	if err != nil {
		h.jsonOrError(w, nil, err)
		return
	}
	filename := fmt.Sprintf("letterboxd_films_%s.json", time.Now().Format("2006-01-02"))
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.Header().Set("Content-Disposition", fmt.Sprintf("attachment; filename=%q", filename))
	_ = json.NewEncoder(w).Encode(films)
}

func (h *Handler) fatsecretExport(w http.ResponseWriter, r *http.Request) {
	foods, err := h.services.FoodEntries(r.Context(), 10000)
	if err != nil {
		h.jsonOrError(w, nil, err)
		return
	}
	filename := fmt.Sprintf("fatsecret_foods_%s.json", time.Now().Format("2006-01-02"))
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.Header().Set("Content-Disposition", fmt.Sprintf("attachment; filename=%q", filename))
	_ = json.NewEncoder(w).Encode(foods)
}

func (h *Handler) updateCalorieTarget(w http.ResponseWriter, r *http.Request) {
	if r.Method == http.MethodGet {
		target := h.services.GetCalorieTarget(r.Context())
		h.writeJSON(w, http.StatusOK, map[string]any{
			"success":        true,
			"target":         target,
			"calorie_target": target,
		})
		return
	}
	if r.Method != http.MethodPost {
		http.NotFound(w, r)
		return
	}
	_ = r.ParseForm()
	target := shared.Int(r.FormValue("target"))
	if target <= 0 {
		var body struct {
			Target int `json:"target"`
		}
		if json.NewDecoder(r.Body).Decode(&body) == nil && body.Target > 0 {
			target = body.Target
		}
	}
	if target <= 0 {
		target = 2200
	}
	err := h.services.SetCalorieTarget(r.Context(), target)
	if strings.HasPrefix(r.URL.Path, "/api/") || isJSONRequest(r) {
		if err != nil {
			h.writeJSON(w, http.StatusInternalServerError, map[string]any{"success": false, "error": err.Error()})
		} else {
			h.writeJSON(w, http.StatusOK, map[string]any{
				"success":        true,
				"message":        fmt.Sprintf("Günlük kalori hedefi %d kcal olarak güncellendi.", target),
				"target":         target,
				"calorie_target": target,
			})
		}
		return
	}
	http.Redirect(w, r, "/#overview", http.StatusSeeOther)
}

func (h *Handler) goodreadsImport(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.NotFound(w, r)
		return
	}
	text, err := uploadedText(r)
	if err != nil {
		h.htmlError(w, err)
		return
	}
	result, err := h.services.ImportGoodreadsCSV(r.Context(), text)
	h.importMessage(w, r, "Goodreads", result, err)
}

func (h *Handler) goodreadsSettings(w http.ResponseWriter, r *http.Request) {
	if r.Method == http.MethodGet {
		settings, err := h.services.GoodreadsSettings(r.Context())
		if err != nil {
			h.writeJSON(w, http.StatusInternalServerError, map[string]any{"success": false, "error": err.Error()})
			return
		}
		h.writeJSON(w, http.StatusOK, settings)
		return
	}
	if r.Method != http.MethodPost {
		http.NotFound(w, r)
		return
	}
	var req struct {
		FeedURL         string `json:"feed_url"`
		IntervalMinutes int    `json:"interval_minutes"`
		Enabled         *bool  `json:"enabled"`
	}
	_ = r.ParseForm()
	req.FeedURL = r.FormValue("feed_url")
	if im := formInt(r, "interval_minutes", 0); im > 0 {
		req.IntervalMinutes = im
	}
	if r.FormValue("enabled") != "" {
		enabled := true
		req.Enabled = &enabled
	}
	if req.FeedURL == "" {
		_ = json.NewDecoder(r.Body).Decode(&req)
	}
	if req.IntervalMinutes <= 0 {
		req.IntervalMinutes = 360
	}
	isEnabled := true
	if req.Enabled != nil {
		isEnabled = *req.Enabled
	}

	err := h.services.SaveGoodreadsSettings(r.Context(), sqlite.SyncSettings{
		FeedURL:         strings.TrimSpace(req.FeedURL),
		IntervalMinutes: req.IntervalMinutes,
		Enabled:         isEnabled,
	})
	if err != nil {
		if strings.HasPrefix(r.URL.Path, "/api/") || r.Header.Get("Accept") == "application/json" {
			h.writeJSON(w, http.StatusInternalServerError, map[string]any{"success": false, "error": err.Error()})
			return
		}
		h.htmlError(w, err)
		return
	}
	if strings.HasPrefix(r.URL.Path, "/api/") || r.Header.Get("Accept") == "application/json" {
		h.writeJSON(w, http.StatusOK, map[string]any{
			"success":  true,
			"feed_url": strings.TrimSpace(req.FeedURL),
			"message":  "Goodreads ayarları kaydedildi",
		})
		return
	}
	http.Redirect(w, r, "/", http.StatusSeeOther)
}

func (h *Handler) goodreadsSync(w http.ResponseWriter, r *http.Request) {
	result, err := h.services.RunConfiguredGoodreadsSync(r.Context())
	h.syncMessage(w, r, "Goodreads", result, err)
}

func (h *Handler) letterboxdImport(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.NotFound(w, r)
		return
	}
	text, err := uploadedText(r)
	if err != nil {
		h.importMessage(w, r, "Letterboxd", sqlite.SyncResult{}, err)
		return
	}
	result, err := h.services.ImportLetterboxdCSV(r.Context(), text)
	h.importMessage(w, r, "Letterboxd", result, err)
}

func (h *Handler) letterboxdSettings(w http.ResponseWriter, r *http.Request) {
	if r.Method == http.MethodGet {
		settings, err := h.services.LetterboxdSettings(r.Context())
		if err != nil {
			h.writeJSON(w, http.StatusInternalServerError, map[string]any{"success": false, "error": err.Error()})
			return
		}
		h.writeJSON(w, http.StatusOK, settings)
		return
	}
	if r.Method != http.MethodPost {
		http.NotFound(w, r)
		return
	}
	var req struct {
		FeedURL         string `json:"feed_url"`
		IntervalMinutes int    `json:"interval_minutes"`
		Enabled         *bool  `json:"enabled"`
	}
	_ = r.ParseForm()
	req.FeedURL = r.FormValue("feed_url")
	if im := formInt(r, "interval_minutes", 0); im > 0 {
		req.IntervalMinutes = im
	}
	if r.FormValue("enabled") != "" {
		enabled := true
		req.Enabled = &enabled
	}
	if req.FeedURL == "" {
		_ = json.NewDecoder(r.Body).Decode(&req)
	}

	feedURL := strings.TrimSpace(req.FeedURL)
	if feedURL != "" && !strings.HasPrefix(feedURL, "http://") && !strings.HasPrefix(feedURL, "https://") {
		username := strings.TrimPrefix(feedURL, "@")
		username = strings.Trim(username, "/")
		feedURL = fmt.Sprintf("https://letterboxd.com/%s/rss/", username)
	}

	if req.IntervalMinutes <= 0 {
		req.IntervalMinutes = 360
	}
	isEnabled := true
	if req.Enabled != nil {
		isEnabled = *req.Enabled
	}

	err := h.services.SaveLetterboxdSettings(r.Context(), sqlite.SyncSettings{
		FeedURL:         feedURL,
		IntervalMinutes: req.IntervalMinutes,
		Enabled:         isEnabled,
	})
	if err != nil {
		if strings.HasPrefix(r.URL.Path, "/api/") || r.Header.Get("Accept") == "application/json" {
			h.writeJSON(w, http.StatusInternalServerError, map[string]any{"success": false, "error": err.Error()})
			return
		}
		h.htmlError(w, err)
		return
	}
	if strings.HasPrefix(r.URL.Path, "/api/") || r.Header.Get("Accept") == "application/json" {
		h.writeJSON(w, http.StatusOK, map[string]any{
			"success":  true,
			"feed_url": feedURL,
			"message":  "Letterboxd ayarları kaydedildi",
		})
		return
	}
	http.Redirect(w, r, "/", http.StatusSeeOther)
}

func (h *Handler) letterboxdSync(w http.ResponseWriter, r *http.Request) {
	result, err := h.services.RunConfiguredLetterboxdSync(r.Context())
	h.syncMessage(w, r, "Letterboxd", result, err)
}

func (h *Handler) fatsecretSearch(w http.ResponseWriter, r *http.Request) {
	result, err := h.services.FatSecretPublicSearch(r.URL.Query().Get("q"))
	if err != nil {
		h.htmlError(w, err)
		return
	}
	h.writeJSON(w, http.StatusOK, result)
}

func (h *Handler) fatsecretSettings(w http.ResponseWriter, r *http.Request) {
	if r.Method == http.MethodGet {
		settings, err := h.services.FatSecretSettings(r.Context())
		if err != nil {
			h.writeJSON(w, http.StatusInternalServerError, map[string]any{"success": false, "error": err.Error()})
			return
		}
		h.writeJSON(w, http.StatusOK, settings)
		return
	}
	if r.Method != http.MethodPost {
		http.NotFound(w, r)
		return
	}
	var req struct {
		ConsumerKey    string `json:"consumer_key"`
		ConsumerSecret string `json:"consumer_secret"`
	}
	_ = r.ParseForm()
	req.ConsumerKey = r.FormValue("consumer_key")
	req.ConsumerSecret = r.FormValue("consumer_secret")
	if req.ConsumerKey == "" || req.ConsumerSecret == "" {
		_ = json.NewDecoder(r.Body).Decode(&req)
	}
	if req.ConsumerKey == "" || req.ConsumerSecret == "" {
		h.writeJSON(w, http.StatusBadRequest, map[string]any{
			"success": false,
			"error":   "consumer_key ve consumer_secret zorunludur",
		})
		return
	}
	if err := h.services.SaveFatSecretCredentials(r.Context(), req.ConsumerKey, req.ConsumerSecret); err != nil {
		h.writeJSON(w, http.StatusInternalServerError, map[string]any{"success": false, "error": err.Error()})
		return
	}
	h.writeJSON(w, http.StatusOK, map[string]any{
		"success": true,
		"message": "FatSecret anahtarları başarıyla kaydedildi.",
	})
}

func (h *Handler) fatsecretConnect(w http.ResponseWriter, r *http.Request) {
	settings, err := h.services.FatSecretSettings(r.Context())
	if err != nil || !settings["has_keys"].(bool) {
		h.htmlError(w, fmt.Errorf("FatSecret API anahtarları eksik. Lütfen Ayarlar sayfasından veya .env dosyasından FATSECRET_CONSUMER_KEY ve FATSECRET_CONSUMER_SECRET alanlarını doldurun."))
		return
	}
	base := config.Env("APP_BASE_URL")
	if base == "" {
		host := r.Host
		proto := "http"
		if r.TLS != nil || r.Header.Get("X-Forwarded-Proto") == "https" {
			proto = "https"
		}
		if host != "" {
			base = proto + "://" + host
		} else {
			base = "http://localhost:" + h.cfg.Port
		}
	}
	redirectURL, err := h.services.StartFatSecretConnect(r.Context(), strings.TrimRight(base, "/")+"/fatsecret/callback")
	if err != nil {
		h.htmlError(w, err)
		return
	}
	http.Redirect(w, r, redirectURL, http.StatusSeeOther)
}

func (h *Handler) fatsecretCallback(w http.ResponseWriter, r *http.Request) {
	oauthToken := r.URL.Query().Get("oauth_token")
	verifier := r.URL.Query().Get("oauth_verifier")
	if oauthToken == "" || verifier == "" {
		h.htmlError(w, fmt.Errorf("FatSecret callback eksik oauth_token veya oauth_verifier ile geldi"))
		return
	}
	if err := h.services.FinishFatSecretConnect(r.Context(), oauthToken, verifier); err != nil {
		h.htmlError(w, err)
		return
	}
	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	_, _ = fmt.Fprint(w, `<!DOCTYPE html>
<html lang="tr">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>FatSecret Bağlantısı Başarılı</title>
  <style>
    body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; background: #0b0f19; color: #f3f4f6; display: flex; align-items: center; justify-content: center; min-height: 100vh; margin: 0; padding: 20px; }
    .card { background: #161e2e; border: 1px solid #2e384d; border-radius: 16px; padding: 32px; max-width: 440px; text-align: center; box-shadow: 0 10px 25px rgba(0,0,0,0.5); }
    .icon { font-size: 54px; margin-bottom: 16px; }
    h1 { font-size: 22px; margin-bottom: 12px; color: #10b981; }
    p { color: #9ca3af; font-size: 15px; line-height: 1.5; margin-bottom: 24px; }
    .btn { display: inline-block; background: #3b82f6; color: white; padding: 12px 24px; border-radius: 8px; text-decoration: none; font-weight: 600; cursor: pointer; border: none; }
  </style>
</head>
<body>
  <div class="card">
    <div class="icon">🎉</div>
    <h1>FatSecret Başarıyla Bağlandı!</h1>
    <p>Kişisel kayıtlar sisteminiz FatSecret hesabınızla başarıyla eşleştirildi. Bu pencereyi kapatıp uygulamaya dönebilirsiniz.</p>
    <button class="btn" onclick="window.close()">Pencereyi Kapat</button>
  </div>
</body>
</html>`)
}

func (h *Handler) fatsecretSync(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.NotFound(w, r)
		return
	}
	_ = r.ParseForm()
	result, err := h.services.SyncFatSecretFoodEntries(r.Context(), formInt(r, "days", 1))
	h.syncMessage(w, r, "FatSecret yemek gunlugu", result, err)
}

func (h *Handler) notificationWebhook(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "Method Not Allowed", http.StatusMethodNotAllowed)
		return
	}

	expectedSecret := config.Env("WEBHOOK_SECRET")
	if expectedSecret != "" {
		clientSecret := r.Header.Get("X-Webhook-Secret")
		if clientSecret == "" {
			authHeader := r.Header.Get("Authorization")
			if strings.HasPrefix(strings.ToLower(authHeader), "bearer ") {
				clientSecret = strings.TrimSpace(authHeader[7:])
			}
		}
		if clientSecret != expectedSecret {
			h.writeJSON(w, http.StatusUnauthorized, map[string]any{
				"success": false,
				"error":   "Yetkisiz istek: Webhook Secret gecersiz",
			})
			return
		}
	}

	body, err := io.ReadAll(r.Body)
	if err != nil {
		h.writeJSON(w, http.StatusBadRequest, map[string]any{
			"success": false,
			"error":   "Govde okunamadi",
		})
		return
	}

	inserted, updated, err := h.services.SaveNotificationWebhook(r.Context(), body)
	if err != nil {
		h.writeJSON(w, http.StatusBadRequest, map[string]any{
			"success": false,
			"error":   err.Error(),
		})
		return
	}

	h.writeJSON(w, http.StatusOK, map[string]any{
		"success":  true,
		"inserted": inserted,
		"updated":  updated,
		"message":  fmt.Sprintf("%d bildirim basariyla islendi (%d yeni, %d guncellendi).", inserted+updated, inserted, updated),
	})
}

func (h *Handler) notificationsAPI(w http.ResponseWriter, r *http.Request) {
	limit := 50
	if l := r.URL.Query().Get("limit"); l != "" {
		if parsed, err := strconv.Atoi(l); err == nil && parsed > 0 {
			limit = parsed
		}
	}
	if limit > 200 {
		limit = 200
	}
	pkg := r.URL.Query().Get("package")

	notifs, err := h.services.Notifications(r.Context(), limit, pkg)
	if err != nil {
		h.writeJSON(w, http.StatusInternalServerError, map[string]any{
			"error": err.Error(),
		})
		return
	}

	h.writeJSON(w, http.StatusOK, map[string]any{
		"count": len(notifs),
		"items": notifs,
	})
}

func (h *Handler) syncMessage(w http.ResponseWriter, r *http.Request, name string, result sqlite.SyncResult, err error) {
	if isJSONRequest(r) {
		if err != nil {
			h.writeJSON(w, http.StatusOK, map[string]any{
				"success": false,
				"name":    name,
				"error":   err.Error(),
			})
			return
		}
		msg := fmt.Sprintf("%s sync tamam: %d yeni, %d guncel.", name, result.Inserted, result.Updated)
		if result.Message != "" {
			msg = fmt.Sprintf("%s sync tamam: %s · %d yeni, %d guncel.", name, result.Message, result.Inserted, result.Updated)
		}
		h.writeJSON(w, http.StatusOK, map[string]any{
			"success":  true,
			"name":     name,
			"inserted": result.Inserted,
			"updated":  result.Updated,
			"message":  msg,
		})
		return
	}
	if err != nil {
		h.htmlError(w, err)
		return
	}
	msg := fmt.Sprintf("%s sync tamam: %d yeni, %d guncel.", name, result.Inserted, result.Updated)
	if result.Message != "" {
		msg = fmt.Sprintf("%s sync tamam: %s · %d yeni, %d guncel.", name, result.Message, result.Inserted, result.Updated)
	}
	h.message(w, msg)
}

func (h *Handler) importMessage(w http.ResponseWriter, r *http.Request, name string, result sqlite.SyncResult, err error) {
	if isJSONRequest(r) {
		if err != nil {
			h.writeJSON(w, http.StatusOK, map[string]any{
				"success": false,
				"name":    name,
				"error":   err.Error(),
			})
			return
		}
		h.writeJSON(w, http.StatusOK, map[string]any{
			"success":  true,
			"name":     name,
			"inserted": result.Inserted,
			"updated":  result.Updated,
			"message":  fmt.Sprintf("%s import tamam: %d yeni, %d guncel.", name, result.Inserted, result.Updated),
		})
		return
	}
	if err != nil {
		h.htmlError(w, err)
		return
	}
	h.message(w, fmt.Sprintf("%s import tamam: %d yeni, %d guncel.", name, result.Inserted, result.Updated))
}

func isJSONRequest(r *http.Request) bool {
	accept := r.Header.Get("Accept")
	return strings.Contains(accept, "application/json") || r.Header.Get("X-Requested-With") == "XMLHttpRequest"
}

func (h *Handler) jsonOrError(w http.ResponseWriter, payload any, err error) {
	if err != nil {
		h.jsonError(w, err)
		return
	}
	h.writeJSON(w, http.StatusOK, payload)
}

func (h *Handler) writeJSON(w http.ResponseWriter, status int, payload any) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.WriteHeader(status)
	encoder := json.NewEncoder(w)
	encoder.SetIndent("", "  ")
	_ = encoder.Encode(payload)
}

func (h *Handler) jsonError(w http.ResponseWriter, err error) {
	h.writeJSON(w, http.StatusInternalServerError, map[string]string{"error": err.Error()})
}

func (h *Handler) htmlError(w http.ResponseWriter, err error) {
	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	w.WriteHeader(http.StatusInternalServerError)
	_, _ = fmt.Fprintf(w, "<h1>Hata</h1><pre>%s</pre><p><a href='/'>Geri don</a></p>", html.EscapeString(err.Error()))
}

func (h *Handler) message(w http.ResponseWriter, message string) {
	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	_, _ = fmt.Fprintf(w, "<p>%s</p><p><a href='/'>Geri don</a></p>", html.EscapeString(message))
}

func uploadedText(r *http.Request) (string, error) {
	if err := r.ParseMultipartForm(16 << 20); err != nil {
		return "", err
	}
	file, _, err := r.FormFile("file")
	if err != nil {
		return "", err
	}
	defer file.Close()
	data, err := io.ReadAll(file)
	return string(data), err
}

func queryInt(r *http.Request, name string, fallback int) int {
	value, err := strconv.Atoi(r.URL.Query().Get(name))
	if err != nil || value <= 0 {
		return fallback
	}
	return value
}

func formInt(r *http.Request, name string, fallback int) int {
	value, err := strconv.Atoi(r.FormValue(name))
	if err != nil || value <= 0 {
		return fallback
	}
	return value
}

func background(ctx context.Context) context.Context {
	if ctx != nil {
		return ctx
	}
	return context.Background()
}
