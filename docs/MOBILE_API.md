# Mobile API

The REST API the Mozaik Android app uses. Both server engines serve it; the [parity table](ARCHITECTURE.md#3-server-engine-parity) lists the one endpoint (the SQL dump) that only the Cloudflare Worker has.

Base URL:

```text
https://personal-records-api.<your-subdomain>.workers.dev   # Cloudflare Worker
http://localhost:8787                                       # Go backend / Docker
```

## Authentication

Every `/api/*` route except `GET /api/health` needs your API key. Send it with one of:

```text
Authorization: Bearer <API_KEY>
X-API-Key: <API_KEY>
```

Webhooks also accept `X-Webhook-Secret: <API_KEY>` or a `?api_key=<API_KEY>` query string, for apps that cannot set headers. `GET /api/auth/verify` checks a key without returning data.

## Read endpoints

```text
GET /api/health
GET /api/dashboard
GET /api/activity?days=7&limit=50
GET /api/sync/status
GET /api/fatsecret/food-entries?limit=100
GET /api/fatsecret/daily-summary?limit=30
GET /api/goodreads/books?q=&shelf=&limit=100
GET /api/letterboxd/films?limit=100
GET /api/trakt/history
GET /api/simkl/history
GET /api/media/ratings
GET /api/health-connect/records
GET /api/notifications?limit=50&package=com.whatsapp
GET /api/music?page=1&limit=50&search=&artist=&album=&source=&date=&from=&to=
GET /api/music/stats
```

### Unified activity feed (`GET /api/activity`)

The main endpoint behind the app's **Timeline**.

- **Query parameters**
  - `days`: how many days of history to include. Default `7`.
  - `limit`: maximum number of items. Default `50`, maximum `1000` on the Worker.
- **Example response**

  ```json
  {
    "days": 7,
    "total": 3,
    "items": [
      {
        "id": "fs_101",
        "source": "fatsecret",
        "type": "meal",
        "date": "2026-09-16",
        "time": "12:30",
        "timestamp": "2026-09-16 12:30:00",
        "title": "Grilled Chicken Bowl",
        "subtitle": "Lunch",
        "details": {
          "food_entry_id": "101",
          "meal": "Lunch",
          "calories": 520.0,
          "protein": 50.0,
          "carbohydrate": 45.0,
          "fat": 15.0
        }
      },
      {
        "id": "gr_201",
        "source": "goodreads",
        "type": "book",
        "date": "2026-09-16",
        "title": "Dune",
        "subtitle": "Frank Herbert",
        "details": {
          "book_id": "201",
          "author": "Frank Herbert",
          "my_rating": 5,
          "number_of_pages": 680,
          "exclusive_shelf": "read"
        }
      },
      {
        "id": "lb_301",
        "source": "letterboxd",
        "type": "film",
        "date": "2026-09-15",
        "title": "Oppenheimer",
        "subtitle": "2023",
        "details": {
          "film_key": "301",
          "year": 2023,
          "rating": 4.5
        }
      }
    ],
    "groups": [
      {
        "date": "2026-09-16",
        "label": "Bugün",
        "item_count": 2,
        "summary": {
          "total_calories": 520.0,
          "total_protein": 50.0,
          "total_carbs": 45.0,
          "total_fat": 15.0,
          "meals_count": 1,
          "books_count": 1,
          "films_count": 0
        },
        "items": [...]
      }
    ]
  }
  ```

> [!NOTE]
> `groups[].label` is always Turkish (`Bugün` for today, `Dün` for yesterday, otherwise the date). Clients should build their own localized label from `groups[].date`, as the Android app does.

## Sync endpoints

```text
POST /api/sync/fatsecret
Content-Type: application/json

{"days": 30}
```

```text
POST /api/sync/goodreads
POST /api/sync/letterboxd
POST /api/sync/trakt
POST /api/sync/simkl
POST /api/sync/mdblist
POST /api/music/sync-lastfm
POST /api/music/sync-listenbrainz
POST /api/music/sync-statsfm
```

Each source has a 90-second cooldown. A call inside the window returns `429 Too Many Requests` with the remaining seconds. Add `?force=true` to bypass it. See [FATSECRET_SYNC_STRATEGY.md](FATSECRET_SYNC_STRATEGY.md) for how the `days` range works.

## Webhooks

### Notifications

Receives Android notifications. Compatible with [Notification Relay Webhook](https://github.com/Enki013/notification-relay-webhook).

```text
POST /api/webhooks/notification
Content-Type: application/json
X-API-Key: <API_KEY>

{
  "package": "com.whatsapp",
  "appName": "WhatsApp",
  "title": "Alex",
  "text": "Meeting at 3 pm",
  "category": "msg",
  "post_time": "2026-09-25T15:00:00Z",
  "key": "msg_123"
}
```

The endpoint accepts a single object or an array. IDs are derived from the content, so sending the same notification twice does not create a duplicate.

List stored notifications:

```text
GET /api/notifications?limit=50&package=com.whatsapp
```

### Health Connect

Receives data from [Health Connect Webhook](https://github.com/mcnaveen/health-connect-webhook). The payload has one array per data type, such as `steps`, `distance`, `active_calories`, `sleep`, `heart_rate`, `resting_heart_rate`, `exercise` and `weight`.

```text
POST /api/webhooks/health
GET  /api/webhooks/health            # endpoint status and record count
```

### Music

Receives scrobbles from the [Web Scrobbler](https://web-scrobbler.com/) browser extension.

```text
POST /api/webhooks/music
GET  /api/webhooks/music             # endpoint status
```

## Export

```text
GET /api/export/all                  # full JSON archive
GET /api/export/db                   # raw SQLite database
GET /api/export/sql                  # SQL dump, Worker only
```

## Notes

- Third-party secrets stay on the server, or are entered once in the app's settings (bring your own key).
- Letterboxd CSV exports can be imported from the web dashboard at `/letterboxd/import`.
