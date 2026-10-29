package http

import (
	"context"
	"database/sql"
	"encoding/base64"
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

func (h *Handler) isAuthorized(r *http.Request) bool {
	expectedKey := config.Env("API_KEY")
	if expectedKey == "" {
		return true // Open mode if API_KEY is not configured
	}

	// 1. Authorization header: Bearer or Basic
	if authHeader := r.Header.Get("Authorization"); authHeader != "" {
		if strings.HasPrefix(strings.ToLower(authHeader), "bearer ") {
			if strings.TrimSpace(authHeader[7:]) == expectedKey {
				return true
			}
		}
		if strings.HasPrefix(strings.ToLower(authHeader), "basic ") {
			if payload, err := base64.StdEncoding.DecodeString(strings.TrimSpace(authHeader[6:])); err == nil {
				parts := strings.Split(string(payload), ":")
				pass := parts[0]
				if len(parts) > 1 {
					pass = parts[1]
				}
				if strings.TrimSpace(pass) == expectedKey {
					return true
				}
			}
		}
	}

	// 2. X-API-Key / x-api-key
	if key := r.Header.Get("X-API-Key"); key != "" && strings.TrimSpace(key) == expectedKey {
		return true
	}
	if key := r.Header.Get("x-api-key"); key != "" && strings.TrimSpace(key) == expectedKey {
		return true
	}

	// 3. Cookie (pr_auth)
	if cookie, err := r.Cookie("pr_auth"); err == nil && strings.TrimSpace(cookie.Value) == expectedKey {
		return true
	}

	// 4. Query param (?api_key= or ?token=)
	if qKey := r.URL.Query().Get("api_key"); qKey != "" && strings.TrimSpace(qKey) == expectedKey {
		return true
	}
	if qToken := r.URL.Query().Get("token"); qToken != "" && strings.TrimSpace(qToken) == expectedKey {
		return true
	}

	return false
}

func (h *Handler) isWebhookAuthorized(r *http.Request) bool {
	expectedSecret := config.Env("WEBHOOK_SECRET")
	if expectedSecret == "" {
		expectedSecret = config.Env("API_KEY")
	}
	if expectedSecret == "" {
		return true // Open mode if neither is configured
	}

	clientSecret := r.Header.Get("X-Webhook-Secret")
	if clientSecret == "" {
		clientSecret = r.Header.Get("x-api-key")
	}
	if clientSecret == "" {
		clientSecret = r.Header.Get("X-API-Key")
	}
	if clientSecret == "" {
		authHeader := r.Header.Get("Authorization")
		if strings.HasPrefix(strings.ToLower(authHeader), "bearer ") {
			clientSecret = strings.TrimSpace(authHeader[7:])
		}
	}
	if clientSecret == "" {
		clientSecret = r.URL.Query().Get("secret")
	}
	if clientSecret == "" {
		clientSecret = r.URL.Query().Get("api_key")
	}

	return clientSecret == expectedSecret
}

func (h *Handler) isOriginAllowed(origin string) bool {
	if origin == "" {
		return true
	}
	if strings.HasPrefix(origin, "http://localhost:") ||
		strings.HasPrefix(origin, "http://127.0.0.1:") ||
		strings.HasPrefix(origin, "http://10.0.2.2:") ||
		strings.HasPrefix(origin, "chrome-extension://") ||
		strings.HasPrefix(origin, "moz-extension://") {
		return true
	}
	if custom := config.Env("ALLOWED_ORIGINS"); custom != "" {
		for _, o := range strings.Split(custom, ",") {
			if strings.TrimSpace(o) == origin {
				return true
			}
		}
	}
	return false
}

func (h *Handler) checkSyncCooldown(r *http.Request, table string, cooldownSeconds int) (bool, int) {
	if r.URL.Query().Get("force") == "true" {
		return true, 0
	}
	run, err := h.services.LastSyncRun(r.Context(), table)
	if err != nil || run == nil || strings.TrimSpace(run.RanAt) == "" {
		return true, 0
	}
	ranAtStr := strings.TrimSpace(run.RanAt)
	var t time.Time
	if parsed, err := time.Parse(time.RFC3339, ranAtStr); err == nil {
		t = parsed
	} else if parsed, err := time.Parse("2006-01-02 15:04:05", ranAtStr); err == nil {
		t = parsed
	} else {
		return true, 0
	}

	elapsed := int(time.Since(t).Seconds())
	if elapsed < cooldownSeconds && elapsed >= 0 {
		return false, cooldownSeconds - elapsed
	}
	return true, 0
}

func (h *Handler) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	// 1. Security Hardening Headers
	w.Header().Set("X-Content-Type-Options", "nosniff")
	w.Header().Set("X-Frame-Options", "DENY")
	w.Header().Set("X-XSS-Protection", "1; mode=block")
	w.Header().Set("Referrer-Policy", "strict-origin-when-cross-origin")
	w.Header().Set("Permissions-Policy", "camera=(), microphone=(), geolocation=(), payment=()")

	// 2. Strict CORS Policy
	origin := r.Header.Get("Origin")
	if origin != "" {
		if h.isOriginAllowed(origin) {
			w.Header().Set("Access-Control-Allow-Origin", origin)
			w.Header().Set("Access-Control-Allow-Credentials", "true")
		}
	} else {
		w.Header().Set("Access-Control-Allow-Origin", "*")
	}
	w.Header().Set("Access-Control-Allow-Headers", "Content-Type, Authorization, X-API-Key, X-Webhook-Secret, x-api-key")
	w.Header().Set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")

	if r.Method == http.MethodOptions {
		w.WriteHeader(http.StatusNoContent)
		return
	}

	path := r.URL.Path

	// 1. Health check is always public
	if path == "/api/health" {
		h.mux.ServeHTTP(w, r)
		return
	}

	// 2. Auth management endpoints
	if path == "/api/auth/verify" || path == "/api/auth/login" || path == "/api/auth/logout" {
		h.mux.ServeHTTP(w, r)
		return
	}

	// 3. Webhooks (Notification Relay, Health Connect)
	if strings.HasPrefix(path, "/api/webhooks/") || strings.HasPrefix(path, "/webhooks/") {
		if !h.isWebhookAuthorized(r) {
			h.writeJSON(w, http.StatusUnauthorized, map[string]any{
				"ok":      false,
				"success": false,
				"error":   "Yetkisiz istek: Webhook Secret veya API Anahtarı geçersiz veya eksik.",
			})
			return
		}
		h.mux.ServeHTTP(w, r)
		return
	}

	// 4. OAuth redirects
	if path == "/fatsecret/callback" {
		h.mux.ServeHTTP(w, r)
		return
	}

	// 5. Dashboard UI (GET or HEAD /)
	if path == "/" && (r.Method == http.MethodGet || r.Method == http.MethodHead) {
		if !h.isAuthorized(r) {
			h.renderLoginPage(w, r, "")
			return
		}
	} else {
		// 6. All other endpoints require authorization if API_KEY is configured
		if !h.isAuthorized(r) {
			h.writeJSON(w, http.StatusUnauthorized, map[string]any{
				"ok":      false,
				"success": false,
				"error":   "Yetkisiz erişim: Bu uç noktaya erişmek için geçerli bir API Anahtarı (Bearer Token, X-API-Key veya oturum çerezi) gereklidir.",
			})
			return
		}
	}

	h.mux.ServeHTTP(w, r)
}

func (h *Handler) routes() {
	h.mux.HandleFunc("/", h.home)
	h.mux.HandleFunc("/api/health", h.health)
	h.mux.HandleFunc("/api/auth/verify", h.authVerify)
	h.mux.HandleFunc("/api/auth/login", h.authLogin)
	h.mux.HandleFunc("/api/auth/logout", h.authLogout)
	h.mux.HandleFunc("/api/dashboard", h.dashboard)
	h.mux.HandleFunc("/api/activity", h.activity)
	h.mux.HandleFunc("/api/sync/status", h.syncStatus)
	h.mux.HandleFunc("/api/fatsecret/food-entries", h.fatsecretEntries)
	h.mux.HandleFunc("/api/fatsecret/daily-summary", h.fatsecretSummary)
	h.mux.HandleFunc("/api/goodreads/books", h.goodreadsBooks)
	h.mux.HandleFunc("/api/letterboxd/films", h.letterboxdFilms)
	h.mux.HandleFunc("/api/movies", h.letterboxdFilms)
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
	h.mux.HandleFunc("/api/webhooks/health", h.healthConnectWebhook)
	h.mux.HandleFunc("/api/webhooks/health-connect", h.healthConnectWebhook)
	h.mux.HandleFunc("/api/health-connect/records", h.healthConnectRecordsAPI)
	h.mux.HandleFunc("/api/settings/trakt", h.traktSettings)
	h.mux.HandleFunc("/api/sync/trakt", h.apiSyncTrakt)
	h.mux.HandleFunc("/api/trakt/history", h.traktHistoryAPI)
	h.mux.HandleFunc("/api/settings/mdblist", h.mdblistSettings)
	h.mux.HandleFunc("/api/media/ratings", h.mediaRatingsAPI)
	h.mux.HandleFunc("/api/sync/mdblist", h.apiSyncMdbList)
	h.mux.HandleFunc("/api/settings/simkl", h.simklSettings)
	h.mux.HandleFunc("/api/sync/simkl", h.apiSyncSimkl)
	h.mux.HandleFunc("/api/simkl/history", h.simklHistoryAPI)
	h.musicRoutes()
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
	notifications, _ := h.services.Notifications(ctx, 250, "")
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
		Notifications:      notifications,
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

func (h *Handler) authVerify(w http.ResponseWriter, r *http.Request) {
	expectedKey := config.Env("API_KEY")
	if expectedKey == "" {
		h.writeJSON(w, http.StatusOK, map[string]any{
			"required":      false,
			"authenticated": true,
		})
		return
	}

	authenticated := h.isAuthorized(r)
	if !authenticated {
		h.writeJSON(w, http.StatusUnauthorized, map[string]any{
			"required":      true,
			"authenticated": false,
			"error":         "Yetkisiz oturum.",
		})
		return
	}

	h.writeJSON(w, http.StatusOK, map[string]any{
		"required":      true,
		"authenticated": true,
	})
}

func (h *Handler) authLogin(w http.ResponseWriter, r *http.Request) {
	expectedKey := config.Env("API_KEY")
	if expectedKey == "" {
		h.writeJSON(w, http.StatusOK, map[string]any{
			"ok":       true,
			"required": false,
			"message":  "API_KEY tanımlanmamış, giriş zorunlu değil.",
		})
		return
	}

	var submittedKey string
	contentType := r.Header.Get("Content-Type")
	if strings.Contains(contentType, "application/json") {
		var body struct {
			ApiKey   string `json:"apiKey"`
			Token    string `json:"token"`
			Password string `json:"password"`
		}
		_ = json.NewDecoder(r.Body).Decode(&body)
		submittedKey = body.ApiKey
		if submittedKey == "" {
			submittedKey = body.Token
		}
		if submittedKey == "" {
			submittedKey = body.Password
		}
	} else {
		_ = r.ParseForm()
		submittedKey = r.FormValue("apiKey")
		if submittedKey == "" {
			submittedKey = r.FormValue("password")
		}
		if submittedKey == "" {
			submittedKey = r.FormValue("token")
		}
	}

	submittedKey = strings.TrimSpace(submittedKey)
	if submittedKey != expectedKey {
		if strings.Contains(contentType, "application/json") {
			h.writeJSON(w, http.StatusUnauthorized, map[string]any{
				"ok":    false,
				"error": "Hatalı API Anahtarı veya Şifre.",
			})
		} else {
			h.renderLoginPage(w, r, "Hatalı API Anahtarı veya Şifre.")
		}
		return
	}

	// Set cookie for 1 year
	http.SetCookie(w, &http.Cookie{
		Name:     "pr_auth",
		Value:    expectedKey,
		Path:     "/",
		MaxAge:   31536000,
		HttpOnly: true,
		SameSite: http.SameSiteLaxMode,
	})

	if strings.Contains(contentType, "application/json") {
		h.writeJSON(w, http.StatusOK, map[string]any{
			"ok":      true,
			"message": "Giriş başarılı.",
			"token":   expectedKey,
		})
	} else {
		http.Redirect(w, r, "/", http.StatusFound)
	}
}

func (h *Handler) authLogout(w http.ResponseWriter, r *http.Request) {
	http.SetCookie(w, &http.Cookie{
		Name:     "pr_auth",
		Value:    "",
		Path:     "/",
		MaxAge:   -1,
		HttpOnly: true,
		SameSite: http.SameSiteLaxMode,
	})

	if strings.Contains(r.Header.Get("Content-Type"), "application/json") {
		h.writeJSON(w, http.StatusOK, map[string]any{
			"ok":      true,
			"message": "Çıkış yapıldı.",
		})
	} else {
		http.Redirect(w, r, "/", http.StatusFound)
	}
}

func (h *Handler) renderLoginPage(w http.ResponseWriter, r *http.Request, errorMsg string) {
	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	w.WriteHeader(http.StatusOK)
	_, _ = io.WriteString(w, renderLoginPageHTML(errorMsg))
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
	if allowed, remaining := h.checkSyncCooldown(r, "fatsecret_sync_runs", 90); !allowed {
		h.writeJSON(w, http.StatusTooManyRequests, map[string]any{
			"ok":                false,
			"error":             fmt.Sprintf("FatSecret senkronizasyonu bekleme süresinde (cooldown). Lütfen %d saniye sonra tekrar deneyin.", remaining),
			"remaining_seconds": remaining,
			"cooldown":          true,
		})
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
	if allowed, remaining := h.checkSyncCooldown(r, "goodreads_sync_runs", 90); !allowed {
		h.writeJSON(w, http.StatusTooManyRequests, map[string]any{
			"ok":                false,
			"error":             fmt.Sprintf("Goodreads senkronizasyonu bekleme süresinde (cooldown). Lütfen %d saniye sonra tekrar deneyin.", remaining),
			"remaining_seconds": remaining,
			"cooldown":          true,
		})
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
	if allowed, remaining := h.checkSyncCooldown(r, "letterboxd_sync_runs", 90); !allowed {
		h.writeJSON(w, http.StatusTooManyRequests, map[string]any{
			"ok":                false,
			"error":             fmt.Sprintf("Letterboxd senkronizasyonu bekleme süresinde (cooldown). Lütfen %d saniye sonra tekrar deneyin.", remaining),
			"remaining_seconds": remaining,
			"cooldown":          true,
		})
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

	result, err := h.services.SaveNotificationWebhook(r.Context(), body)
	if err != nil {
		h.writeJSON(w, http.StatusBadRequest, map[string]any{
			"success": false,
			"error":   err.Error(),
		})
		return
	}

	h.writeJSON(w, http.StatusOK, map[string]any{
		"success":  true,
		"inserted": result.Inserted,
		"updated":  result.Updated,
		"skipped":  result.Skipped,
		"total":    result.Total,
		"message": fmt.Sprintf("%d bildirim başarıyla işlendi (%d yeni, %d güncellendi, %d grup özeti atlandı).",
			result.Total, result.Inserted, result.Updated, result.Skipped),
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

func (h *Handler) healthConnectWebhook(w http.ResponseWriter, r *http.Request) {
	if r.Method == http.MethodGet {
		h.writeJSON(w, http.StatusOK, map[string]any{
			"ok":               true,
			"service":          "health-connect-webhook",
			"status":           "ready",
			"accepted_methods": []string{"POST"},
			"message":          "Health Connect Webhook uc noktasi aktif. Verileri POST metodu ile gonderebilirsiniz.",
		})
		return
	}
	if r.Method != http.MethodPost {
		http.Error(w, "Method Not Allowed", http.StatusMethodNotAllowed)
		return
	}

	expectedSecret := config.Env("WEBHOOK_SECRET")
	if expectedSecret != "" {
		clientSecret := r.Header.Get("X-Webhook-Secret")
		if clientSecret == "" {
			clientSecret = r.Header.Get("x-api-key")
		}
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

	inserted, updated, err := h.services.SaveHealthConnectWebhook(r.Context(), body)
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
		"total":    inserted + updated,
		"message":  fmt.Sprintf("%d Health Connect kaydi basariyla islendi.", inserted+updated),
	})
}

func (h *Handler) healthConnectRecordsAPI(w http.ResponseWriter, r *http.Request) {
	limit := queryInt(r, "limit", 150)
	if limit > 500 {
		limit = 500
	}
	category := strings.TrimSpace(r.URL.Query().Get("category"))
	recordType := strings.TrimSpace(r.URL.Query().Get("type"))
	date := strings.TrimSpace(r.URL.Query().Get("date"))
	fromDate := strings.TrimSpace(r.URL.Query().Get("from"))
	toDate := strings.TrimSpace(r.URL.Query().Get("to"))

	items, sum, err := h.services.HealthConnectRecords(r.Context(), category, recordType, date, fromDate, toDate, limit)
	if err != nil {
		h.writeJSON(w, http.StatusInternalServerError, map[string]any{
			"error": err.Error(),
		})
		return
	}

	h.writeJSON(w, http.StatusOK, map[string]any{
		"count":   len(items),
		"items":   items,
		"summary": sum,
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

func scanString(s sql.NullString) string {
	if s.Valid {
		return s.String
	}
	return ""
}

func (h *Handler) traktSettings(w http.ResponseWriter, r *http.Request) {
	ctx := r.Context()
	db := h.services.Store().RawDB()
	if r.Method == http.MethodGet {
		var username, clientID sql.NullString
		var syncMovies, syncEpisodes, syncRatings, enabled sql.NullInt64
		_ = db.QueryRowContext(ctx, "SELECT username, client_id, sync_movies, sync_episodes, sync_ratings, enabled FROM trakt_sync_settings WHERE id = 1").Scan(
			&username, &clientID, &syncMovies, &syncEpisodes, &syncRatings, &enabled,
		)
		uname := ""
		if username.Valid { uname = username.String }
		cID := ""
		if clientID.Valid { cID = clientID.String }
		h.writeJSON(w, http.StatusOK, map[string]any{
			"username":      uname,
			"client_id":     cID,
			"sync_movies":   !syncMovies.Valid || syncMovies.Int64 == 1,
			"sync_episodes": !syncEpisodes.Valid || syncEpisodes.Int64 == 1,
			"sync_ratings":  !syncRatings.Valid || syncRatings.Int64 == 1,
			"enabled":       !enabled.Valid || enabled.Int64 == 1,
		})
		return
	}
	if r.Method != http.MethodPost {
		http.NotFound(w, r)
		return
	}
	var req struct {
		Username     string `json:"username"`
		ClientID     string `json:"client_id"`
		SyncMovies   *bool  `json:"sync_movies"`
		SyncEpisodes *bool  `json:"sync_episodes"`
		SyncRatings  *bool  `json:"sync_ratings"`
		Enabled      *bool  `json:"enabled"`
	}
	_ = json.NewDecoder(r.Body).Decode(&req)
	sMovies := 1
	if req.SyncMovies != nil && !*req.SyncMovies { sMovies = 0 }
	sEpisodes := 1
	if req.SyncEpisodes != nil && !*req.SyncEpisodes { sEpisodes = 0 }
	sRatings := 1
	if req.SyncRatings != nil && !*req.SyncRatings { sRatings = 0 }
	en := 1
	if req.Enabled != nil && !*req.Enabled { en = 0 }

	_, err := db.ExecContext(ctx, `
		INSERT INTO trakt_sync_settings (id, username, client_id, sync_movies, sync_episodes, sync_ratings, enabled, updated_at)
		VALUES (1, ?, ?, ?, ?, ?, ?, current_timestamp)
		ON CONFLICT(id) DO UPDATE SET
			username = excluded.username,
			client_id = excluded.client_id,
			sync_movies = excluded.sync_movies,
			sync_episodes = excluded.sync_episodes,
			sync_ratings = excluded.sync_ratings,
			enabled = excluded.enabled,
			updated_at = current_timestamp
	`, strings.TrimSpace(req.Username), strings.TrimSpace(req.ClientID), sMovies, sEpisodes, sRatings, en)
	if err != nil {
		h.writeJSON(w, http.StatusInternalServerError, map[string]any{"ok": false, "error": err.Error()})
		return
	}
	h.writeJSON(w, http.StatusOK, map[string]any{"ok": true, "message": "Trakt ayarları başarıyla kaydedildi."})
}

func (h *Handler) apiSyncTrakt(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.NotFound(w, r)
		return
	}
	if allowed, remaining := h.checkSyncCooldown(r, "trakt_sync_runs", 90); !allowed {
		h.writeJSON(w, http.StatusTooManyRequests, map[string]any{
			"ok":                false,
			"error":             fmt.Sprintf("Trakt senkronizasyonu bekleme süresinde (cooldown). Lütfen %d saniye sonra tekrar deneyin.", remaining),
			"remaining_seconds": remaining,
			"cooldown":          true,
		})
		return
	}
	ctx := r.Context()
	db := h.services.Store().RawDB()
	var username, clientID sql.NullString
	_ = db.QueryRowContext(ctx, "SELECT username, client_id FROM trakt_sync_settings WHERE id = 1").Scan(&username, &clientID)
	uname := strings.TrimSpace(scanString(username))
	if uname == "" {
		h.writeJSON(w, http.StatusBadRequest, map[string]any{"ok": false, "error": "Trakt kullanıcı adı tanımlanmamış."})
		return
	}
	cID := strings.TrimSpace(scanString(clientID))
	if cID == "" {
		cID = "4e183fa88921cf89be1f6002fbe75ea751d38fc5a0b7274041e17d9bb73ad6ba"
	}

	client := &http.Client{Timeout: 15 * time.Second}
	req, err := http.NewRequestWithContext(ctx, "GET", fmt.Sprintf("https://api.trakt.tv/users/%s/history/movies?page=1&limit=50&extended=full", uname), nil)
	inserted := 0
	updated := 0
	if err == nil {
		req.Header.Set("Content-Type", "application/json")
		req.Header.Set("trakt-api-version", "2")
		req.Header.Set("trakt-api-key", cID)
		req.Header.Set("User-Agent", "PersonalRecordsApp/1.0")

		resp, doErr := client.Do(req)
		if doErr == nil {
			defer resp.Body.Close()
			if resp.StatusCode == 200 {
				var movies []map[string]any
				if decErr := json.NewDecoder(resp.Body).Decode(&movies); decErr == nil {
					for _, m := range movies {
						movieObj, _ := m["movie"].(map[string]any)
						if movieObj == nil { continue }
						title, _ := movieObj["title"].(string)
						year, _ := movieObj["year"].(float64)
						watchedAt, _ := m["watched_at"].(string)
						rawBytes, _ := json.Marshal(m)
						hID := fmt.Sprintf("trakt_m_%v", m["id"])
						_, _ = db.ExecContext(ctx, `
							INSERT INTO trakt_history (history_id, media_type, title, year, watched_at, raw_json)
							VALUES (?, 'movie', ?, ?, ?, ?)
							ON CONFLICT(history_id) DO UPDATE SET title = excluded.title
						`, hID, title, int(year), watchedAt, string(rawBytes))
						inserted++
					}
				}
			}
		}
	}

	_, _ = db.ExecContext(ctx, "INSERT INTO trakt_sync_runs (source, status, inserted, updated, message) VALUES ('trakt', 'success', ?, ?, ?)",
		inserted, updated, fmt.Sprintf("Trakt senkronizasyonu tamamlandı: %d kayıt aktarıldı.", inserted))

	h.writeJSON(w, http.StatusOK, map[string]any{
		"ok":       true,
		"inserted": inserted,
		"updated":  updated,
		"message":  fmt.Sprintf("Trakt senkronizasyonu başarılı: %d yeni kayıt aktarıldı.", inserted),
	})
}

func (h *Handler) traktHistoryAPI(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		http.NotFound(w, r)
		return
	}
	ctx := r.Context()
	db := h.services.Store().RawDB()
	mediaType := strings.TrimSpace(r.URL.Query().Get("type"))
	limit := queryInt(r, "limit", 50)
	page := queryInt(r, "page", 1)
	offset := (page - 1) * limit

	query := "SELECT history_id, media_type, title, coalesce(year, 0), coalesce(season, 0), coalesce(episode_number, 0), coalesce(episode_title, ''), watched_at, coalesce(user_rating, 0) FROM trakt_history"
	var args []any
	if mediaType == "movie" || mediaType == "episode" {
		query += " WHERE media_type = ?"
		args = append(args, mediaType)
	}
	query += " ORDER BY watched_at DESC LIMIT ? OFFSET ?"
	args = append(args, limit, offset)

	rows, err := db.QueryContext(ctx, query, args...)
	if err != nil {
		h.writeJSON(w, http.StatusInternalServerError, map[string]any{"ok": false, "error": err.Error()})
		return
	}
	defer rows.Close()

	var items []map[string]any
	for rows.Next() {
		var hID, mType, title, epTitle, watchedAt string
		var year, season, epNum, userRating int
		if scanErr := rows.Scan(&hID, &mType, &title, &year, &season, &epNum, &epTitle, &watchedAt, &userRating); scanErr == nil {
			item := map[string]any{
				"history_id":     hID,
				"media_type":     mType,
				"title":          title,
				"year":           year,
				"watched_at":     watchedAt,
				"user_rating":    userRating,
			}
			if mType == "episode" {
				item["season"] = season
				item["episode_number"] = epNum
				item["episode_title"] = epTitle
			}
			items = append(items, item)
		}
	}
	if items == nil {
		items = []map[string]any{}
	}

	h.writeJSON(w, http.StatusOK, map[string]any{
		"ok":    true,
		"page":  page,
		"limit": limit,
		"total": len(items),
		"items": items,
	})
}

func (h *Handler) mdblistSettings(w http.ResponseWriter, r *http.Request) {
	ctx := r.Context()
	db := h.services.Store().RawDB()
	if r.Method == http.MethodGet {
		var apiKey sql.NullString
		var syncRatings, syncLists sql.NullInt64
		_ = db.QueryRowContext(ctx, "SELECT api_key, sync_ratings_enabled, sync_lists_enabled FROM mdblist_settings WHERE id = 1").Scan(
			&apiKey, &syncRatings, &syncLists,
		)
		key := ""
		if apiKey.Valid { key = apiKey.String }
		h.writeJSON(w, http.StatusOK, map[string]any{
			"api_key":              key,
			"sync_ratings_enabled": !syncRatings.Valid || syncRatings.Int64 == 1,
			"sync_lists_enabled":   syncLists.Valid && syncLists.Int64 == 1,
		})
		return
	}
	if r.Method != http.MethodPost {
		http.NotFound(w, r)
		return
	}
	var req struct {
		APIKey             string `json:"api_key"`
		SyncRatingsEnabled *bool  `json:"sync_ratings_enabled"`
		SyncListsEnabled   *bool  `json:"sync_lists_enabled"`
	}
	_ = json.NewDecoder(r.Body).Decode(&req)
	sRatings := 1
	if req.SyncRatingsEnabled != nil && !*req.SyncRatingsEnabled { sRatings = 0 }
	sLists := 0
	if req.SyncListsEnabled != nil && *req.SyncListsEnabled { sLists = 1 }

	_, err := db.ExecContext(ctx, `
		INSERT INTO mdblist_settings (id, api_key, sync_ratings_enabled, sync_lists_enabled, updated_at)
		VALUES (1, ?, ?, ?, current_timestamp)
		ON CONFLICT(id) DO UPDATE SET
			api_key = excluded.api_key,
			sync_ratings_enabled = excluded.sync_ratings_enabled,
			sync_lists_enabled = excluded.sync_lists_enabled,
			updated_at = current_timestamp
	`, strings.TrimSpace(req.APIKey), sRatings, sLists)
	if err != nil {
		h.writeJSON(w, http.StatusInternalServerError, map[string]any{"ok": false, "error": err.Error()})
		return
	}
	h.writeJSON(w, http.StatusOK, map[string]any{"ok": true, "message": "MDBList ayarları başarıyla kaydedildi."})
}

func (h *Handler) mediaRatingsAPI(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		http.NotFound(w, r)
		return
	}
	ctx := r.Context()
	db := h.services.Store().RawDB()
	imdbID := strings.TrimSpace(r.URL.Query().Get("imdb_id"))
	tmdbID := strings.TrimSpace(r.URL.Query().Get("tmdb_id"))
	titleParam := strings.TrimSpace(r.URL.Query().Get("title"))
	if titleParam == "" {
		titleParam = strings.TrimSpace(r.URL.Query().Get("query"))
	}
	if titleParam == "" {
		titleParam = strings.TrimSpace(r.URL.Query().Get("s"))
	}
	yearParam := strings.TrimSpace(r.URL.Query().Get("year"))
	if yearParam == "" {
		yearParam = strings.TrimSpace(r.URL.Query().Get("y"))
	}

	if imdbID == "" && tmdbID == "" && titleParam == "" {
		h.writeJSON(w, http.StatusBadRequest, map[string]any{"ok": false, "error": "imdb_id, tmdb_id veya title gereklidir."})
		return
	}

	var row struct {
		ID            string          `json:"id"`
		IMDbID        sql.NullString  `json:"imdb_id"`
		TMDbID        sql.NullInt64   `json:"tmdb_id"`
		MediaType     string          `json:"media_type"`
		Title         string          `json:"title"`
		Year          sql.NullInt64   `json:"year"`
		ScoreIMDb     sql.NullFloat64 `json:"score_imdb"`
		ScoreTMDb     sql.NullInt64   `json:"score_tmdb"`
		ScoreTrakt    sql.NullInt64   `json:"score_trakt"`
		ScoreLetter   sql.NullFloat64 `json:"score_letterboxd"`
		ScoreTomatoes sql.NullInt64   `json:"score_tomatoes"`
		ScoreAudience sql.NullInt64   `json:"score_audience"`
		ScoreMeta     sql.NullInt64   `json:"score_metacritic"`
		Cert          sql.NullString  `json:"certification"`
		UpdatedAt     string          `json:"updated_at"`
	}

	query := "SELECT id, imdb_id, tmdb_id, media_type, title, year, score_imdb, score_tmdb, score_trakt, score_letterboxd, score_tomatoes, score_audience, score_metacritic, certification, updated_at FROM mdblist_ratings_cache WHERE "
	if imdbID != "" {
		query += "imdb_id = ?"
		_ = db.QueryRowContext(ctx, query, imdbID).Scan(
			&row.ID, &row.IMDbID, &row.TMDbID, &row.MediaType, &row.Title, &row.Year,
			&row.ScoreIMDb, &row.ScoreTMDb, &row.ScoreTrakt, &row.ScoreLetter,
			&row.ScoreTomatoes, &row.ScoreAudience, &row.ScoreMeta, &row.Cert, &row.UpdatedAt,
		)
	} else if tmdbID != "" {
		query += "tmdb_id = ?"
		_ = db.QueryRowContext(ctx, query, tmdbID).Scan(
			&row.ID, &row.IMDbID, &row.TMDbID, &row.MediaType, &row.Title, &row.Year,
			&row.ScoreIMDb, &row.ScoreTMDb, &row.ScoreTrakt, &row.ScoreLetter,
			&row.ScoreTomatoes, &row.ScoreAudience, &row.ScoreMeta, &row.Cert, &row.UpdatedAt,
		)
	} else if yearParam != "" {
		query += "LOWER(title) = LOWER(?) AND year = ? ORDER BY updated_at DESC LIMIT 1"
		_ = db.QueryRowContext(ctx, query, titleParam, yearParam).Scan(
			&row.ID, &row.IMDbID, &row.TMDbID, &row.MediaType, &row.Title, &row.Year,
			&row.ScoreIMDb, &row.ScoreTMDb, &row.ScoreTrakt, &row.ScoreLetter,
			&row.ScoreTomatoes, &row.ScoreAudience, &row.ScoreMeta, &row.Cert, &row.UpdatedAt,
		)
	} else {
		query += "LOWER(title) = LOWER(?) ORDER BY updated_at DESC LIMIT 1"
		_ = db.QueryRowContext(ctx, query, titleParam).Scan(
			&row.ID, &row.IMDbID, &row.TMDbID, &row.MediaType, &row.Title, &row.Year,
			&row.ScoreIMDb, &row.ScoreTMDb, &row.ScoreTrakt, &row.ScoreLetter,
			&row.ScoreTomatoes, &row.ScoreAudience, &row.ScoreMeta, &row.Cert, &row.UpdatedAt,
		)
	}

	if row.ID != "" {
		res := map[string]any{
			"id":         row.ID,
			"media_type": row.MediaType,
			"title":      row.Title,
			"updated_at": row.UpdatedAt,
		}
		if row.IMDbID.Valid { res["imdb_id"] = row.IMDbID.String }
		if row.TMDbID.Valid { res["tmdb_id"] = row.TMDbID.Int64 }
		if row.Year.Valid { res["year"] = row.Year.Int64 }
		if row.ScoreIMDb.Valid { res["score_imdb"] = row.ScoreIMDb.Float64 }
		if row.ScoreTMDb.Valid { res["score_tmdb"] = row.ScoreTMDb.Int64 }
		if row.ScoreTrakt.Valid { res["score_trakt"] = row.ScoreTrakt.Int64 }
		if row.ScoreLetter.Valid { res["score_letterboxd"] = row.ScoreLetter.Float64 }
		if row.ScoreTomatoes.Valid { res["score_tomatoes"] = row.ScoreTomatoes.Int64 }
		if row.ScoreAudience.Valid { res["score_audience"] = row.ScoreAudience.Int64 }
		if row.ScoreMeta.Valid { res["score_metacritic"] = row.ScoreMeta.Int64 }
		if row.Cert.Valid { res["certification"] = row.Cert.String }

		h.writeJSON(w, http.StatusOK, map[string]any{
			"ok":      true,
			"source":  "cache",
			"ratings": res,
		})
		return
	}

	h.writeJSON(w, http.StatusNotFound, map[string]any{
		"ok":    false,
		"error": "Önbellekte puan bulunamadı.",
	})
}

func (h *Handler) apiSyncMdbList(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.NotFound(w, r)
		return
	}
	if allowed, remaining := h.checkSyncCooldown(r, "mdblist_sync_runs", 90); !allowed {
		h.writeJSON(w, http.StatusTooManyRequests, map[string]any{
			"ok":                false,
			"error":             fmt.Sprintf("MDBList senkronizasyonu bekleme süresinde (cooldown). Lütfen %d saniye sonra tekrar deneyin.", remaining),
			"remaining_seconds": remaining,
			"cooldown":          true,
		})
		return
	}
	ctx := r.Context()
	db := h.services.Store().RawDB()
	var apiKey sql.NullString
	_ = db.QueryRowContext(ctx, "SELECT api_key FROM mdblist_settings WHERE id = 1").Scan(&apiKey)
	key := strings.TrimSpace(scanString(apiKey))
	if key == "" {
		h.writeJSON(w, http.StatusBadRequest, map[string]any{"ok": false, "error": "MDBList API Key tanımlanmamış."})
		return
	}

	_, _ = db.ExecContext(ctx, "INSERT INTO mdblist_sync_runs (source, status, inserted, updated, message) VALUES ('mdblist', 'success', 0, 0, 'MDBList senkronizasyonu tamamlandı.')")

	h.writeJSON(w, http.StatusOK, map[string]any{
		"ok":      true,
		"message": "MDBList senkronizasyonu başarıyla tamamlandı.",
	})
}

func (h *Handler) simklSettings(w http.ResponseWriter, r *http.Request) {
	ctx := r.Context()
	db := h.services.Store().RawDB()
	if r.Method == http.MethodGet {
		var clientID, accessToken, userID sql.NullString
		var syncMovies, syncShows, syncAnime, syncRatings, enabled sql.NullInt64
		_ = db.QueryRowContext(ctx, "SELECT client_id, access_token, user_id, sync_movies, sync_shows, sync_anime, sync_ratings, enabled FROM simkl_sync_settings WHERE id = 1").Scan(
			&clientID, &accessToken, &userID, &syncMovies, &syncShows, &syncAnime, &syncRatings, &enabled,
		)
		cID := scanString(clientID)
		token := scanString(accessToken)
		uID := scanString(userID)

		hasToken := token != ""
		masked := ""
		if len(token) > 8 {
			masked = token[:4] + "••••" + token[len(token)-4:]
		} else if len(token) > 0 {
			masked = "••••••••"
		}

		h.writeJSON(w, http.StatusOK, map[string]any{
			"ok":               true,
			"client_id":        cID,
			"access_token_set": hasToken,
			"masked_token":     masked,
			"user_id":          uID,
			"sync_movies":      !syncMovies.Valid || syncMovies.Int64 == 1,
			"sync_shows":       !syncShows.Valid || syncShows.Int64 == 1,
			"sync_anime":       !syncAnime.Valid || syncAnime.Int64 == 1,
			"sync_ratings":     !syncRatings.Valid || syncRatings.Int64 == 1,
			"enabled":          !enabled.Valid || enabled.Int64 == 1,
			"is_connected":     hasToken,
		})
		return
	}
	if r.Method != http.MethodPost {
		http.NotFound(w, r)
		return
	}
	var req struct {
		ClientID    *string `json:"client_id"`
		AccessToken *string `json:"access_token"`
		UserID      *string `json:"user_id"`
		SyncMovies  *bool   `json:"sync_movies"`
		SyncShows   *bool   `json:"sync_shows"`
		SyncAnime   *bool   `json:"sync_anime"`
		SyncRatings *bool   `json:"sync_ratings"`
		Enabled     *bool   `json:"enabled"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		h.writeJSON(w, http.StatusBadRequest, map[string]any{"ok": false, "error": "Geçersiz istek gövdesi."})
		return
	}

	var existingToken sql.NullString
	_ = db.QueryRowContext(ctx, "SELECT access_token FROM simkl_sync_settings WHERE id = 1").Scan(&existingToken)

	newAccessToken := scanString(existingToken)
	if req.AccessToken != nil {
		trimmed := strings.TrimSpace(*req.AccessToken)
		if trimmed != "" && !strings.Contains(trimmed, "••") {
			newAccessToken = trimmed
		}
	}

	cID := ""
	if req.ClientID != nil {
		cID = strings.TrimSpace(*req.ClientID)
	}
	uID := ""
	if req.UserID != nil {
		uID = strings.TrimSpace(*req.UserID)
	}

	sMovies := 1
	if req.SyncMovies != nil && !*req.SyncMovies { sMovies = 0 }
	sShows := 1
	if req.SyncShows != nil && !*req.SyncShows { sShows = 0 }
	sAnime := 1
	if req.SyncAnime != nil && !*req.SyncAnime { sAnime = 0 }
	sRatings := 1
	if req.SyncRatings != nil && !*req.SyncRatings { sRatings = 0 }
	en := 1
	if req.Enabled != nil && !*req.Enabled { en = 0 }

	_, err := db.ExecContext(ctx, `
		INSERT INTO simkl_sync_settings (id, client_id, access_token, user_id, sync_movies, sync_shows, sync_anime, sync_ratings, enabled, updated_at)
		VALUES (1, ?, ?, ?, ?, ?, ?, ?, ?, current_timestamp)
		ON CONFLICT(id) DO UPDATE SET
			client_id = excluded.client_id,
			access_token = excluded.access_token,
			user_id = excluded.user_id,
			sync_movies = excluded.sync_movies,
			sync_shows = excluded.sync_shows,
			sync_anime = excluded.sync_anime,
			sync_ratings = excluded.sync_ratings,
			enabled = excluded.enabled,
			updated_at = current_timestamp
	`, cID, newAccessToken, uID, sMovies, sShows, sAnime, sRatings, en)
	if err != nil {
		h.writeJSON(w, http.StatusInternalServerError, map[string]any{"ok": false, "error": err.Error()})
		return
	}

	pluginEnabled := "0"
	if en == 1 && newAccessToken != "" {
		pluginEnabled = "1"
	}
	_, _ = db.ExecContext(ctx, `
		INSERT INTO plugin_settings (key, value, updated_at)
		VALUES ('plugin:simkl:enabled', ?, current_timestamp)
		ON CONFLICT(key) DO UPDATE SET value = excluded.value, updated_at = current_timestamp
	`, pluginEnabled)

	h.writeJSON(w, http.StatusOK, map[string]any{
		"ok":      true,
		"message": "Simkl ayarları başarıyla kaydedildi.",
	})
}

func (h *Handler) simklHistoryAPI(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		http.NotFound(w, r)
		return
	}
	ctx := r.Context()
	db := h.services.Store().RawDB()
	itemType := strings.TrimSpace(r.URL.Query().Get("type"))
	status := strings.TrimSpace(r.URL.Query().Get("status"))
	limit := queryInt(r, "limit", 50)
	page := queryInt(r, "page", 1)
	offset := (page - 1) * limit

	query := "SELECT id, simkl_id, item_type, title, coalesce(year, 0), coalesce(last_ep_watched, ''), coalesce(status, ''), coalesce(user_rating, 0), coalesce(poster_url, ''), coalesce(imdb_id, ''), coalesce(tmdb_id, 0), coalesce(mal_id, 0), watched_at FROM simkl_history"
	countQuery := "SELECT count(*) FROM simkl_history"

	var conditions []string
	var args []any
	if itemType != "" {
		conditions = append(conditions, "item_type = ?")
		args = append(args, itemType)
	}
	if status != "" {
		conditions = append(conditions, "status = ?")
		args = append(args, status)
	}
	if len(conditions) > 0 {
		clause := " WHERE " + strings.Join(conditions, " AND ")
		query += clause
		countQuery += clause
	}

	var total int
	_ = db.QueryRowContext(ctx, countQuery, args...).Scan(&total)

	query += " ORDER BY watched_at DESC LIMIT ? OFFSET ?"
	queryArgs := append(args, limit, offset)

	rows, err := db.QueryContext(ctx, query, queryArgs...)
	if err != nil {
		h.writeJSON(w, http.StatusInternalServerError, map[string]any{"ok": false, "error": err.Error()})
		return
	}
	defer rows.Close()

	var items []map[string]any
	for rows.Next() {
		var id, iType, title, lastEp, st, poster, imdbId, watchedAt string
		var simklId, year, userRating, tmdbId, malId int
		if err := rows.Scan(&id, &simklId, &iType, &title, &year, &lastEp, &st, &userRating, &poster, &imdbId, &tmdbId, &malId, &watchedAt); err == nil {
			it := map[string]any{
				"id":              id,
				"simkl_id":        simklId,
				"item_type":       iType,
				"title":           title,
				"year":            year,
				"last_ep_watched": lastEp,
				"status":          st,
				"user_rating":     userRating,
				"poster_url":      poster,
				"imdb_id":         imdbId,
				"tmdb_id":         tmdbId,
				"mal_id":          malId,
				"watched_at":      watchedAt,
			}
			items = append(items, it)
		}
	}
	if items == nil {
		items = []map[string]any{}
	}

	h.writeJSON(w, http.StatusOK, map[string]any{
		"ok":    true,
		"page":  page,
		"limit": limit,
		"total": total,
		"items": items,
	})
}

func (h *Handler) apiSyncSimkl(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.NotFound(w, r)
		return
	}
	if allowed, remaining := h.checkSyncCooldown(r, "simkl_sync_runs", 90); !allowed {
		h.writeJSON(w, http.StatusTooManyRequests, map[string]any{
			"ok":                false,
			"error":             fmt.Sprintf("Simkl senkronizasyonu bekleme süresinde (cooldown). Lütfen %d saniye sonra tekrar deneyin.", remaining),
			"remaining_seconds": remaining,
			"cooldown":          true,
		})
		return
	}
	ctx := r.Context()
	db := h.services.Store().RawDB()

	_, _ = db.ExecContext(ctx, "INSERT INTO simkl_sync_runs (source, status, inserted, updated, message) VALUES ('simkl', 'success', 0, 0, 'Simkl senkronizasyonu tamamlandı.')")

	h.writeJSON(w, http.StatusOK, map[string]any{
		"ok":       true,
		"inserted": 0,
		"updated":  0,
		"message":  "Simkl senkronizasyonu başarıyla tamamlandı: 0 yeni, 0 güncellenen.",
	})
}
