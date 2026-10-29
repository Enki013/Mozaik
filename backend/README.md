# Mozaik: Go backend

The self-hosted **Go + SQLite** server for Mozaik. It follows Clean Architecture and serves the same REST API contract as the Cloudflare Worker in `worker/`, except for the Worker-only music features (see the [parity table](../docs/ARCHITECTURE.md#3-server-engine-parity)).

## Layout

- `cmd/personal-records/main.go`: entry point, dependency wiring and the background sync loop.
- `internal/application/`: business rules, the unified activity timeline, and RSS and OAuth sync services.
- `internal/infrastructure/sqlite/`: SQLite schema migrations and data access.
- `internal/integrations/fatsecret/`: FatSecret OAuth 1.0a signing and API client.
- `internal/presentation/http/`: REST API handlers and the built-in web dashboard.
- `internal/config/`: environment variable configuration (`.env`).

## Configuration

Copy `.env.example` to `.env` and set at least `API_KEY`.

| Variable | Purpose |
| :--- | :--- |
| `PORT` | HTTP port, default `8787` |
| `HOST` | Bind address, for example `0.0.0.0` in Docker |
| `DB_PATH` | Path to the SQLite database file |
| `APP_BASE_URL` | Public address of the server, used for the FatSecret OAuth callback |
| `API_KEY` | Key required by every protected route |
| `WEBHOOK_SECRET` | Optional separate secret for webhooks |
| `ALLOWED_ORIGINS` | Extra CORS origins, comma separated |
| `FATSECRET_CLIENT_ID`, `FATSECRET_CLIENT_SECRET` | Optional: public food database search (OAuth 2.0) |
| `FATSECRET_CONSUMER_KEY`, `FATSECRET_CONSUMER_SECRET` | Optional: your FatSecret diary (OAuth 1.0) |

## Running

### With Go

```bash
cd backend
cp .env.example .env
go run ./cmd/personal-records
```

### Tests

```bash
cd backend
go test ./...
```

### With Docker Compose (from the repository root)

The container does not read `backend/.env`. Put `API_KEY` and any other variables in a `.env` file in the repository root, which Docker Compose reads:

```bash
echo "API_KEY=$(openssl rand -hex 32)" > .env
docker compose up -d
```
