# 🏛️ Architecture & System Design

**Kişisel Kayıtlar (Personal Records)** is an open-source, sovereign personal telemetry and lifelogging data hub. It decouples **data ingestion and storage** from **client-side consumption**, ensuring battery efficiency, privacy, and full data ownership.

---

## 1. High-Level System Architecture

```mermaid
flowchart TD
    subgraph Data Sources [Data Ingestion Layer]
        NR["📱 Notification Relay APK<br/>(Push Notifications)"]
        HA["🏠 Home Assistant / Tasker<br/>(Telemetry & Sensors)"]
        LB["🎬 Letterboxd<br/>(Public RSS Feed)"]
        GR["📚 Goodreads<br/>(Public RSS Feed)"]
        FS["🥗 FatSecret API<br/>(OAuth 1.0 / BYOK)"]
    end

    subgraph Hub [Hybrid Server Engine (Parity)]
        direction TB
        subgraph Option A [Cloudflare Workers]
            W_API["Serverless Edge API"]
            W_D1[("Cloudflare D1<br/>Distributed SQLite")]
            W_API <--> W_D1
        end
        subgraph Option B [Self-Hosted Docker / Go]
            G_API["Go HTTP Server<br/>Clean Architecture"]
            G_SQLITE[("Persistent SQLite<br/>/data/personal_records.sqlite3")]
            G_API <--> G_SQLITE
        end
    end

    subgraph Client [Android Mobile App]
        APP["Android 14+ Jetpack Compose<br/>Material 3 Expressive UI<br/>Room Offline Cache"]
    end

    NR -->|"POST /api/webhooks/notification"| Hub
    HA -->|"POST /api/webhooks/notification"| Hub
    Hub -->|"GET RSS XML"| LB
    Hub -->|"GET RSS XML"| GR
    Hub <-->|"OAuth 1.0 3-legged"| FS

    Hub <===>|"REST JSON API (/api/activity, /api/dashboard...)"| APP
```

---

## 2. Ingestion Plugin Architecture

The server acts as a centralized personal records collector with three distinct plugin contracts:

### A. Webhook Plugins (Push)
- **Concept:** External daemons, relay apps, or automation triggers push real-time events to the hub over HTTP POST.
- **Contract:** Accepts JSON payloads at `/api/webhooks/notification` (single item or batch array) with deterministic IDs (`notif_<hash>`) for idempotency.
- **Implementations:**
  - **Notification Relay Webhook APK:** Lightweight open-source Android listener that captures device notifications and forwards them to the hub.
  - **Tasker / Home Assistant:** Can emit custom webhooks to the same endpoint.

### B. RSS Plugins (Pull / Polling)
- **Concept:** Periodic cron workers or on-demand sync calls fetch XML feeds, parse elements, and execute smart upsert queries.
- **Contract:** Registered under `/api/sync/<service>` with settings saved in `*_sync_settings` tables.
- **Implementations:**
  - **Letterboxd Plugin:** Syncs diary dates, ratings, and rewatches. Auto-formats simple usernames (e.g. `enesben_` -> `https://letterboxd.com/enesben_/rss/`). Detects prior watches to prevent duplicate or phantom "rewatch" records.
  - **Goodreads Plugin:** Fetches shelves, ratings, and read dates.

### C. OTT / OAuth Plugins (Bidirectional API)
- **Concept:** 3-legged authentication with external service providers where users Bring Your Own Key (BYOK).
- **Contract:** Direct token exchange endpoints (`/fatsecret/connect`, `/fatsecret/callback`) and credential management (`/api/settings/fatsecret`).
- **Implementations:**
  - **FatSecret BYOK:** Allows users to input their own `Consumer Key` and `Consumer Secret` via the mobile app, authenticate in their phone's browser, and sync nutrition logs.
  - **Trakt.tv (Planned):** Movies & TV shows watch history, episode tracking, scrobble, and ratings via Trakt API.
  - **MDBList (Planned):** Smart curated lists, multi-source rating aggregation (IMDb, Trakt, TMDB, Letterboxd), and watchlist filter syncing.
  - **Future Plugins:** Spotify listening history, Steam library & achievements.

---

## 3. Dual-Engine Server Parity

Both backend implementations expose identical REST endpoints and data structures:

| Endpoint | Purpose | Supported by |
| :--- | :--- | :--- |
| `GET /api/health` | Health & database connectivity check | Worker D1 & Go Docker |
| `GET /api/dashboard` | Aggregated counter metrics & sync statuses | Worker D1 & Go Docker |
| `GET /api/activity` | Unified chronological timeline feed | Worker D1 & Go Docker |
| `POST /api/webhooks/notification` | Ingestion endpoint for Notification Relay | Worker D1 & Go Docker |
| `GET /api/settings/letterboxd` | Retrieve Letterboxd sync configuration | Worker D1 & Go Docker |
| `POST /api/settings/letterboxd` | Save Letterboxd username / RSS URL | Worker D1 & Go Docker |
| `GET /api/settings/goodreads` | Retrieve Goodreads sync configuration | Worker D1 & Go Docker |
| `POST /api/settings/goodreads` | Save Goodreads RSS URL | Worker D1 & Go Docker |
| `GET/POST /api/settings/fatsecret` | Retrieve & store BYOK credentials | Worker D1 & Go Docker |
| `GET /api/export/all` | Export entire database as JSON | Worker D1 & Go Docker |
| `GET /api/export/db` | Download SQLite database binary | Worker D1 & Go Docker |

---

## 4. Go Backend Clean Architecture

The self-hosted Go backend (`backend/cmd/personal-records`) follows Clean Architecture:

- `backend/internal/application/`: Business rules, activity timeline merging, and sync coordinators.
- `backend/internal/infrastructure/sqlite/`: SQLite schema migration, store connection, directory guarantee, and repositories.
- `backend/internal/integrations/fatsecret/`: OAuth 1.0a signing and HTTP client.
- `backend/internal/presentation/http/`: REST JSON API handlers and HTML views.
- `backend/internal/config/`: Environment variable parsing (`PORT`, `HOST`, `DB_PATH`, `APP_BASE_URL`).

---

## 5. Android Client Design

- **Role:** Strictly a central reader, inspector, and manager. It does **not** run an in-app persistent notification listener service, eliminating background battery drain.
- **UI:** Jetpack Compose with Material 3 Expressive guidelines, dynamic sticky search headers, and domain-colored surface containers.
- **Offline First:** Room database caches activities, food entries, books, and films for offline browsing.
