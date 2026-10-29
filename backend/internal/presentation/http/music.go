package http

import (
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"math"
	"net/http"
	"strconv"
	"strings"
	"time"

	"personal-records/internal/application"
	"personal-records/internal/infrastructure/sqlite"
)

// Music endpoints. They follow the Cloudflare Worker's routes and JSON shapes so the
// Android app can talk to either server.

func (h *Handler) musicRoutes() {
	h.mux.HandleFunc("/api/webhooks/music", h.musicWebhook)
	h.mux.HandleFunc("/api/webhooks/scrobbler", h.musicWebhook)
	h.mux.HandleFunc("/webhooks/music", h.musicWebhook)
	h.mux.HandleFunc("/api/music", h.musicList)
	h.mux.HandleFunc("/api/music/stats", h.musicStats)
	h.mux.HandleFunc("/api/music/import", h.musicImport)
	h.mux.HandleFunc("/api/music/sync-lastfm", h.musicSyncLastFM)
	h.mux.HandleFunc("/api/music/sync-listenbrainz", h.musicSyncListenBrainz)
	h.mux.HandleFunc("/api/music/sync-statsfm", h.musicSyncStatsFM)
	h.mux.HandleFunc("/api/settings/music", h.musicSettings)
	h.mux.HandleFunc("/api/settings/plugins", h.pluginSettings)
}

func (h *Handler) musicWebhook(w http.ResponseWriter, r *http.Request) {
	switch r.Method {
	case http.MethodGet:
		h.writeJSON(w, http.StatusOK, h.services.WebScrobblerStatus(r.Context()))
	case http.MethodPost:
		raw, _ := io.ReadAll(io.LimitReader(r.Body, 5<<20))
		h.writeJSON(w, http.StatusOK, h.services.IngestWebScrobbler(r.Context(), raw, time.Now()))
	default:
		http.NotFound(w, r)
	}
}

func (h *Handler) musicList(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		http.NotFound(w, r)
		return
	}
	q := r.URL.Query()
	page := queryInt(r, "page", 1)
	if page < 1 {
		page = 1
	}
	limit := queryInt(r, "limit", 50)
	if limit < 1 {
		limit = 1
	}
	if limit > 200 {
		limit = 200
	}
	query := sqlite.MusicQuery{
		Search: strings.TrimSpace(q.Get("search")),
		Artist: strings.TrimSpace(q.Get("artist")),
		Album:  strings.TrimSpace(q.Get("album")),
		Source: strings.TrimSpace(q.Get("source")),
		Date:   strings.TrimSpace(q.Get("date")),
		Page:   page,
		Limit:  limit,
	}
	if query.Date == "" {
		query.FromUTS = parseUTS(q.Get("from"))
		query.ToUTS = parseUTS(q.Get("to"))
	}
	items, total, err := h.services.MusicScrobbles(r.Context(), query)
	if err != nil {
		h.writeJSON(w, http.StatusInternalServerError, map[string]any{"ok": false, "error": err.Error()})
		return
	}
	h.writeJSON(w, http.StatusOK, map[string]any{
		"page":       page,
		"limit":      limit,
		"total":      total,
		"totalPages": int(math.Ceil(float64(total) / float64(limit))),
		"count":      len(items),
		"items":      items,
	})
}

// parseUTS accepts a Unix timestamp or a date string ("2026-10-29", RFC 3339) and
// returns Unix seconds, or 0 when empty or unreadable.
func parseUTS(value string) int64 {
	value = strings.TrimSpace(value)
	if value == "" {
		return 0
	}
	if n, err := strconv.ParseFloat(value, 64); err == nil {
		return int64(n)
	}
	for _, layout := range []string{time.RFC3339Nano, time.RFC3339, "2006-01-02T15:04:05", "2006-01-02"} {
		if t, err := time.Parse(layout, value); err == nil {
			return t.Unix()
		}
	}
	return 0
}

func (h *Handler) musicStats(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		http.NotFound(w, r)
		return
	}
	stats, err := h.services.MusicStats(r.Context(), time.Now())
	if err != nil {
		h.writeJSON(w, http.StatusInternalServerError, map[string]any{"ok": false, "error": err.Error()})
		return
	}
	h.writeJSON(w, http.StatusOK, stats)
}

func (h *Handler) musicImport(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.NotFound(w, r)
		return
	}
	raw, _ := io.ReadAll(io.LimitReader(r.Body, 20<<20))
	result, err := h.services.ImportMusic(r.Context(), r.Header.Get("Content-Type"), raw, time.Now())
	h.musicResult(w, result, err)
}

func (h *Handler) musicSyncLastFM(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.NotFound(w, r)
		return
	}
	if allowed, remaining := h.checkSyncCooldown(r, "music_sync_runs", 90); !allowed {
		h.writeJSON(w, http.StatusTooManyRequests, map[string]any{
			"ok":                false,
			"error":             fmt.Sprintf("Last.fm senkronizasyonu bekleme süresinde (cooldown). Lütfen %d saniye sonra tekrar deneyin.", remaining),
			"remaining_seconds": remaining,
			"cooldown":          true,
		})
		return
	}
	result, err := h.services.SyncLastFM(r.Context(), readJSONMap(r))
	h.musicResult(w, result, err)
}

func (h *Handler) musicSyncListenBrainz(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.NotFound(w, r)
		return
	}
	result, err := h.services.SyncListenBrainz(r.Context(), readJSONMap(r))
	h.musicResult(w, result, err)
}

func (h *Handler) musicSyncStatsFM(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.NotFound(w, r)
		return
	}
	result, err := h.services.SyncStatsFM(r.Context(), readJSONMap(r), time.Now())
	h.musicResult(w, result, err)
}

func (h *Handler) musicSettings(w http.ResponseWriter, r *http.Request) {
	switch r.Method {
	case http.MethodGet:
		proto := r.Header.Get("X-Forwarded-Proto")
		if proto == "" {
			proto = "http"
			if r.TLS != nil {
				proto = "https"
			}
		}
		payload, err := h.services.MusicSettingsResponse(r.Context(), fmt.Sprintf("%s://%s/api/webhooks/music", proto, r.Host))
		if err != nil {
			h.writeJSON(w, http.StatusInternalServerError, map[string]any{"ok": false, "error": err.Error()})
			return
		}
		h.writeJSON(w, http.StatusOK, payload)
	case http.MethodPost:
		if err := h.services.SaveMusicSettings(r.Context(), readJSONMap(r)); err != nil {
			h.musicResult(w, nil, err)
			return
		}
		h.writeJSON(w, http.StatusOK, map[string]any{"ok": true, "message": "Müzik ayarları kaydedildi"})
	default:
		http.NotFound(w, r)
	}
}

func (h *Handler) pluginSettings(w http.ResponseWriter, r *http.Request) {
	switch r.Method {
	case http.MethodGet:
		plugins, err := h.services.PluginToggles(r.Context())
		if err != nil {
			h.writeJSON(w, http.StatusInternalServerError, map[string]any{"ok": false, "error": err.Error()})
			return
		}
		h.writeJSON(w, http.StatusOK, map[string]any{"ok": true, "plugins": plugins})
	case http.MethodPost:
		if err := h.services.SavePluginToggles(r.Context(), readJSONMap(r)); err != nil {
			h.writeJSON(w, http.StatusInternalServerError, map[string]any{"ok": false, "error": err.Error()})
			return
		}
		h.writeJSON(w, http.StatusOK, map[string]any{"ok": true, "message": "Eklenti ayarları başarıyla kaydedildi."})
	default:
		http.NotFound(w, r)
	}
}

// musicResult writes a service result, or the error with the status it carries.
func (h *Handler) musicResult(w http.ResponseWriter, result map[string]any, err error) {
	if err != nil {
		status := http.StatusInternalServerError
		var musicErr *application.MusicError
		if errors.As(err, &musicErr) {
			status = musicErr.Status
		}
		h.writeJSON(w, status, map[string]any{"ok": false, "error": err.Error()})
		return
	}
	h.writeJSON(w, http.StatusOK, result)
}

// readJSONMap decodes a JSON object body; an empty or invalid body gives an empty map.
func readJSONMap(r *http.Request) map[string]any {
	body := map[string]any{}
	_ = json.NewDecoder(io.LimitReader(r.Body, 1<<20)).Decode(&body)
	if body == nil {
		body = map[string]any{}
	}
	return body
}
