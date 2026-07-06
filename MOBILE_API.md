# Mobile API

Local base URL:

```text
http://localhost:8787
```

## Read endpoints

```text
GET /api/health
GET /api/dashboard
GET /api/sync/status
GET /api/fatsecret/food-entries?limit=100
GET /api/fatsecret/daily-summary?limit=30
GET /api/goodreads/books?q=&shelf=&limit=100
GET /api/letterboxd/films?limit=100
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
