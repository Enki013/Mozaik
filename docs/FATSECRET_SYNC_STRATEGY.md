# FatSecret sync strategy

This document explains how Mozaik imports FatSecret nutrition data with its **smart all-time sync**, and why it was designed this way.

---

## 1. The problem: API limits

1. **No bulk export endpoint**
   - Goodreads and Letterboxd can return a whole history at once (CSV export or RSS). The FatSecret Platform API has **no** single endpoint that returns a user's full history.
   - The detailed food diary endpoint (`/food-entries/v2`) returns **one day per call**. The day is passed as `date=X`, the number of days since 1970-01-01.
2. **Quota and time**
   - One year of history means 365 HTTP requests. Three years means about 1,095.
   - Re-reading every day on each sync would:
     - use up the FatSecret API quota (rate limit, `429`), and
     - take minutes, with a real risk of HTTP timeouts.

---

## 2. The solution

Sync works in three layers.

### A. Remember scanned days (`fatsecret_scanned_days`)

- The database has a `fatsecret_scanned_days (date_int INTEGER PRIMARY KEY, scanned_at TEXT)` table.
- Whenever a day is fetched from FatSecret, it is marked as scanned, whether it had zero meals or ten.

### B. Smart skip

- **Older days (more than 2 days ago):** if the day is already in `fatsecret_scanned_days`, **no request is sent**. It costs no time and no quota.
- **Recent days (today and yesterday):** always fetched again, because you may still add meals or change portions.
- **Polite throttle:** while filling gaps in older history, the sync waits 50 ms between requests to stay under FatSecret's rate limit.

### C. Background sync

- Without any action from you, a background job refreshes **only the last 3 days** (today, yesterday and the day before):
  - Go backend: the `syncLoop` in `main.go`, every 30 to 60 minutes.
  - Cloudflare Worker: the cron trigger, every 6 hours.
- This takes 3 requests and finishes in about a second.

---

## 3. Sync options

The app and the API offer these ranges:

1. **Today (`days=1`):** refreshes today's meals.
2. **Last 30 days (`days=30`):** a quick scan of the last month.
3. **Last year (`days=365`):** fills any missing days in the past year.
4. **All time (`days=1095`, 3 years):** finds every missing day and stores it permanently. Because scanned days are skipped, running it again finishes in seconds.

---

## 4. Notes for the Android client

- On launch, the app only needs to call `POST /api/sync/fatsecret` with `{ "days": 3 }`.
- Calorie charts and history lists load from the server's `/api/activity` and `/api/fatsecret/food-entries` endpoints, so they do not depend on the FatSecret API being reachable. The app also keeps a local Room cache for offline browsing.
