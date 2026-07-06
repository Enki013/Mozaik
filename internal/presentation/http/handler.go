package http

import (
	"context"
	"encoding/json"
	"fmt"
	"html"
	"io"
	"net/http"
	"strconv"
	"strings"

	"personal-records/internal/application"
	"personal-records/internal/config"
	"personal-records/internal/infrastructure/sqlite"
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
	h.mux.HandleFunc("/api/sync/status", h.syncStatus)
	h.mux.HandleFunc("/api/fatsecret/food-entries", h.fatsecretEntries)
	h.mux.HandleFunc("/api/fatsecret/daily-summary", h.fatsecretSummary)
	h.mux.HandleFunc("/api/goodreads/books", h.goodreadsBooks)
	h.mux.HandleFunc("/api/letterboxd/films", h.letterboxdFilms)
	h.mux.HandleFunc("/api/sync/fatsecret", h.apiSyncFatSecret)
	h.mux.HandleFunc("/api/sync/goodreads", h.apiSyncGoodreads)
	h.mux.HandleFunc("/api/sync/letterboxd", h.apiSyncLetterboxd)
	h.mux.HandleFunc("/goodreads/export", h.goodreadsExport)
	h.mux.HandleFunc("/goodreads/import", h.goodreadsImport)
	h.mux.HandleFunc("/goodreads/settings", h.goodreadsSettings)
	h.mux.HandleFunc("/goodreads/sync", h.goodreadsSync)
	h.mux.HandleFunc("/letterboxd/import", h.letterboxdImport)
	h.mux.HandleFunc("/letterboxd/settings", h.letterboxdSettings)
	h.mux.HandleFunc("/letterboxd/sync", h.letterboxdSync)
	h.mux.HandleFunc("/fatsecret/search", h.fatsecretSearch)
	h.mux.HandleFunc("/fatsecret/connect", h.fatsecretConnect)
	h.mux.HandleFunc("/fatsecret/callback", h.fatsecretCallback)
	h.mux.HandleFunc("/fatsecret/sync", h.fatsecretSync)
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
	books, _ := h.services.GoodreadsBooks(ctx, r.URL.Query().Get("q"), r.URL.Query().Get("shelf"), 100)
	shelves, _ := h.services.GoodreadsShelves(ctx)
	goodreadsSettings, _ := h.services.GoodreadsSettings(ctx)
	letterboxdSettings, _ := h.services.LetterboxdSettings(ctx)
	letterboxd, _ := h.services.LetterboxdFilms(ctx, 80)
	foodSummary, _ := h.services.FatSecretDailySummary(ctx, 14)
	foodEntries, _ := h.services.FoodEntries(ctx, 30)
	dashboard, _ := h.services.Dashboard(ctx)

	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	_, _ = io.WriteString(w, renderHome(pageData{
		DBName:             "personal_records.sqlite3",
		Counts:             counts,
		Dashboard:          dashboard,
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
	}))
}

func (h *Handler) health(w http.ResponseWriter, r *http.Request) {
	h.writeJSON(w, http.StatusOK, map[string]any{"ok": true})
}

func (h *Handler) dashboard(w http.ResponseWriter, r *http.Request) {
	payload, err := h.services.Dashboard(r.Context())
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
	if err != nil {
		h.htmlError(w, err)
		return
	}
	h.message(w, fmt.Sprintf("Goodreads import tamam: %d yeni, %d guncel.", result.Inserted, result.Updated))
}

func (h *Handler) goodreadsSettings(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.NotFound(w, r)
		return
	}
	_ = r.ParseForm()
	err := h.services.SaveGoodreadsSettings(r.Context(), sqlite.SyncSettings{
		FeedURL:         r.FormValue("feed_url"),
		IntervalMinutes: formInt(r, "interval_minutes", 360),
		Enabled:         r.FormValue("enabled") != "",
	})
	if err != nil {
		h.htmlError(w, err)
		return
	}
	http.Redirect(w, r, "/", http.StatusSeeOther)
}

func (h *Handler) goodreadsSync(w http.ResponseWriter, r *http.Request) {
	result, err := h.services.RunConfiguredGoodreadsSync(r.Context())
	h.syncMessage(w, "Goodreads", result, err)
}

func (h *Handler) letterboxdImport(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.NotFound(w, r)
		return
	}
	text, err := uploadedText(r)
	if err != nil {
		h.htmlError(w, err)
		return
	}
	result, err := h.services.ImportLetterboxdCSV(r.Context(), text)
	if err != nil {
		h.htmlError(w, err)
		return
	}
	h.message(w, fmt.Sprintf("Letterboxd import tamam: %d yeni, %d guncel.", result.Inserted, result.Updated))
}

func (h *Handler) letterboxdSettings(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.NotFound(w, r)
		return
	}
	_ = r.ParseForm()
	err := h.services.SaveLetterboxdSettings(r.Context(), sqlite.SyncSettings{
		FeedURL:         r.FormValue("feed_url"),
		IntervalMinutes: formInt(r, "interval_minutes", 360),
		Enabled:         r.FormValue("enabled") != "",
	})
	if err != nil {
		h.htmlError(w, err)
		return
	}
	http.Redirect(w, r, "/", http.StatusSeeOther)
}

func (h *Handler) letterboxdSync(w http.ResponseWriter, r *http.Request) {
	result, err := h.services.RunConfiguredLetterboxdSync(r.Context())
	h.syncMessage(w, "Letterboxd", result, err)
}

func (h *Handler) fatsecretSearch(w http.ResponseWriter, r *http.Request) {
	result, err := h.services.FatSecretPublicSearch(r.URL.Query().Get("q"))
	if err != nil {
		h.htmlError(w, err)
		return
	}
	h.writeJSON(w, http.StatusOK, result)
}

func (h *Handler) fatsecretConnect(w http.ResponseWriter, r *http.Request) {
	base := config.Env("APP_BASE_URL")
	if base == "" {
		base = "http://localhost:" + h.cfg.Port
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
	h.message(w, "FatSecret baglandi.")
}

func (h *Handler) fatsecretSync(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.NotFound(w, r)
		return
	}
	_ = r.ParseForm()
	result, err := h.services.SyncFatSecretFoodEntries(r.Context(), formInt(r, "days", 1))
	h.syncMessage(w, "FatSecret yemek gunlugu", result, err)
}

func (h *Handler) syncMessage(w http.ResponseWriter, name string, result sqlite.SyncResult, err error) {
	if err != nil {
		h.htmlError(w, err)
		return
	}
	h.message(w, fmt.Sprintf("%s sync tamam: %d yeni, %d guncel.", name, result.Inserted, result.Updated))
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
