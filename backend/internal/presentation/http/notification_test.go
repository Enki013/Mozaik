package http_test

import (
	"context"
	"net/http"
	"testing"
)

// WhatsApp reuses one notification key per chat. Each message must get its own row,
// while ongoing notifications with a reused key update in place.
func TestNotificationWebhookReusedKeys(t *testing.T) {
	store, _, h := setupTestServer(t)
	post := func(body string) map[string]any {
		t.Helper()
		code, res := doJSON(t, h, http.MethodPost, "/api/webhooks/notification", body, nil)
		if code != http.StatusOK {
			t.Fatalf("webhook: %d %v", code, res)
		}
		return res
	}
	count := func() int {
		t.Helper()
		items, err := store.QueryNotifications(context.Background(), 100, "")
		if err != nil {
			t.Fatal(err)
		}
		return len(items)
	}

	// Two different messages in the same chat (same key) give two rows.
	post(`{"package":"com.whatsapp","title":"Alex","text":"Hi","key":"0|com.whatsapp|1|chat","post_time":"2026-10-29T10:00:00Z"}`)
	res := post(`{"package":"com.whatsapp","title":"Alex","text":"Are you there?","key":"0|com.whatsapp|1|chat","post_time":"2026-10-29T10:01:00Z"}`)
	if res["inserted"] != 1.0 || count() != 2 {
		t.Fatalf("expected 2 rows for 2 messages, got %d (%v)", count(), res)
	}

	// The same message delivered again updates the existing row.
	res = post(`{"package":"com.whatsapp","title":"Alex","text":"Hi","key":"0|com.whatsapp|1|chat","post_time":"2026-10-29T10:05:00Z"}`)
	if res["updated"] != 1.0 || count() != 2 {
		t.Fatalf("a repeat must not add a row, got %d (%v)", count(), res)
	}
	items, _ := store.QueryNotifications(context.Background(), 100, "")
	for _, n := range items {
		if n.Text == "Hi" && n.PostTime != "2026-10-29T10:00:00Z" {
			t.Fatalf("a repeat of a normal notification must keep its first time, got %s", n.PostTime)
		}
		if n.Text == "Hi" && (n.RawJSON == "" || n.RawJSON[0] != '{') {
			t.Fatalf("raw_json should hold the single item, got %q", n.RawJSON)
		}
	}

	// Group summaries are skipped, in both spellings, including inside a batch.
	res = post(`[{"package":"com.whatsapp","title":"WhatsApp","text":"3 new messages","key":"g","group_summary":true},
		{"package":"com.whatsapp","title":"WhatsApp","text":"4 new messages","key":"g2","groupSummary":true}]`)
	if res["skipped"] != 2.0 || res["inserted"] != 0.0 || res["total"] != 2.0 || count() != 2 {
		t.Fatalf("group summaries must be skipped, got %d rows (%v)", count(), res)
	}

	// An ongoing notification with the same key updates one row, with the new title and time.
	post(`{"package":"com.spotify.music","title":"Song A","text":"Artist","key":"player","ongoing":true,"post_time":"2026-10-29T11:00:00Z"}`)
	post(`{"package":"com.spotify.music","title":"Song B","text":"Artist","key":"player","ongoing":true,"post_time":"2026-10-29T11:04:00Z"}`)
	spotify, err := store.QueryNotifications(context.Background(), 100, "com.spotify.music")
	if err != nil {
		t.Fatal(err)
	}
	if len(spotify) != 1 || spotify[0].Title != "Song B" || spotify[0].PostTime != "2026-10-29T11:04:00Z" {
		t.Fatalf("ongoing notification should update in place, got %+v", spotify)
	}
}
