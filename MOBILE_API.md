# Mobile API

Local base URL:

```text
http://localhost:8787
```

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
```

### Unified Activity Endpoint (`GET /api/activity`)

Birleşik zaman çizelgesi (Activity Timeline) için Kotlin istemcisinin kullanacağı ana uç nokta:

- **Query Parametreleri:**
  - `days`: Kaç günlük geçmiş listelensin? (Varsayılan: `7`, Maks: `365`).
  - `limit`: Toplam maksimum kayıt sayısı (Varsayılan: `50`, Maks: `500`).
- **Örnek Yanıt Şeması (JSON):**
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


## Sync endpoints

```text
POST /api/sync/fatsecret
Content-Type: application/json

{"days": 30}
```

```text
POST /api/sync/goodreads
POST /api/sync/letterboxd
```

## Notes

- Secrets stay on the backend. Mobile clients should never store FatSecret keys.
- CSV import remains in the web UI for now.
- This is the first API layer. The next migration step is moving this surface to
  FastAPI with the same JSON contracts.
