# Setup guide

This guide walks you through setting up Mozaik from scratch and connecting your own data: FatSecret meals, Letterboxd films, Goodreads books, music, Health Connect and Android notifications.

For a shorter overview, see the [README](../README.md).

---

## How the pieces fit together

Mozaik has a **central server** that stores your data and an **Android app** that reads from it.

```text
[ Android notifications ] ──(webhook push)──┐
[ Health Connect ]        ──(webhook push)──┤
[ Web Scrobbler ]         ──(webhook push)──┤
[ Letterboxd (films) ]    ──(RSS polling)───┼──► [ SERVER ] ◄──► [ MOZAIK ANDROID APP ]
[ Goodreads (books) ]     ──(RSS polling)───┤    Cloudflare D1        Jetpack Compose,
[ FatSecret (nutrition) ] ──(OAuth 1.0)─────┤    or SQLite (Go)       Material 3 Expressive
[ Last.fm / Trakt / … ]   ──(API sync)──────┘
```

### Why separate companion apps?

The Mozaik app does not run a notification listener or a health sync service in the background. Instead:

- Small open-source companion apps ([Notification Relay Webhook](https://github.com/Enki013/notification-relay-webhook) and [Health Connect Webhook](https://github.com/mcnaveen/health-connect-webhook)) send data straight to your server.
- The server stores it in the database.
- The Mozaik app only displays, filters and summarizes the data, so it stays fast and light on battery.

### Two server options

| | Option A: Cloudflare Worker + D1 (recommended) | Option B: Docker / Go |
| :--- | :--- | :--- |
| **Cost** | Free (Cloudflare free tier) | Free on your own hardware |
| **How it runs** | Serverless | One container with Docker Compose |
| **Reachable from** | Anywhere, 24/7 | Your local network, or the internet through a VPN or reverse proxy |
| **Database** | Cloudflare D1 (SQLite) | Local `personal_records.sqlite3` file |
| **Setup time** | About 5 minutes | About 1 minute |
| **Music integrations** | Yes | Not yet (Last.fm, ListenBrainz, Stats.fm and Web Scrobbler are Worker-only) |

---

## Step 1: Get FatSecret API keys (optional, 2 minutes)

Skip this step if you do not use FatSecret.

1. Go to [platform.fatsecret.com](https://platform.fatsecret.com/) and create a free developer account.
2. Create a new application and choose the free **Basic** tier.
3. Note the two values you are given: **Consumer Key** and **Consumer Secret**.
4. You do not need to put them in the server code. You can enter them later in the Mozaik app's **Settings**.

---

## Step 2, option A: Cloudflare Worker + D1

Use this if you want a server that is always on, with nothing to maintain.

1. Install the dependencies:

   ```bash
   cd worker
   npm install
   ```

2. Sign in to Cloudflare:

   ```bash
   npx wrangler login
   ```

3. Create the database:

   ```bash
   npx wrangler d1 create personal-records-db
   ```

4. Copy `worker/wrangler.toml.example` to `worker/wrangler.toml` if it does not exist, and paste the `database_id` from the previous command into it.

5. Create the tables:

   ```bash
   npx wrangler d1 execute personal-records-db --remote --file=./schema.sql
   ```

6. Set an API key so only you can read your data:

   ```bash
   openssl rand -hex 32        # generates a strong key
   npx wrangler secret put API_KEY
   ```

7. Deploy:

   ```bash
   npx wrangler deploy
   ```

8. Copy the URL printed at the end, for example `https://personal-records-api.<your-subdomain>.workers.dev`.

More options (local development, migrating an existing SQLite database, backups) are in [worker/README.md](../worker/README.md).

---

## Step 2, option B: Docker Compose

Use this to run Mozaik on a Raspberry Pi, a home server, a VPS or your own computer.

1. Install **Docker** with **Docker Compose**.
2. Create a `.env` file **in the repository root** with your API key. Docker Compose reads it; the container does not read `backend/.env`.

   ```bash
   echo "API_KEY=$(openssl rand -hex 32)" > .env
   ```

3. Start the server from the repository root:

   ```bash
   docker compose up -d
   ```

4. Open `http://localhost:8787` in a browser to check that it works. Data is stored in `./data/personal_records.sqlite3`.
5. To connect from your phone on the same network, use your computer's local IP address, for example `http://192.168.1.50:8787`.

> [!TIP]
> **Local network and FatSecret:** if your phone connects to Docker over the local network and you want to link FatSecret, add `APP_BASE_URL=http://<your-local-ip>:8787` (for example `http://192.168.1.50:8787`) to the root `.env` file and run `docker compose up -d` again. FatSecret redirects back to this address after you sign in.

To run without Docker: `cd backend && cp .env.example .env && go run ./cmd/personal-records`. See [backend/README.md](../backend/README.md).

---

## Step 3: Connect the Android app

1. Download `app-release.apk` from the [latest release](https://github.com/Enki013/Mozaik/releases/latest) and install it.
2. Open the app and go to the **Settings** tab.

### A. Server connection

1. In **Server URL**, enter your server address:
   - Cloudflare: `https://personal-records-api.<your-subdomain>.workers.dev`
   - Docker or local Go: `http://192.168.1.xxx:8787`
2. In **API key**, enter the `API_KEY` you set on the server.
3. Tap **Save**, then **Test Connection**. A green **Connected** badge means everything works.

### B. FatSecret (bring your own key)

1. In the **FatSecret API (BYOK)** card, enter your **Consumer Key** and **Consumer Secret**, then tap **Save Keys**.
2. Tap **Connect with OAuth** and allow access in the browser page that opens.
3. Back in the app, the card shows **FatSecret Account Connected**.
4. Under **Sync Nutrition History**, tap **Last 30 Days** or **All Time** to import your past meals. The [FatSecret sync strategy](FATSECRET_SYNC_STRATEGY.md) explains why later syncs are fast.

---

## Step 4: Forward Android notifications (Notification Relay)

Use this to archive notifications such as bank alerts, delivery updates or reminders.

1. In **Settings**, find the **Notification Webhook Integration** card.
2. Tap **Copy URL**. The webhook URL (`https://…/api/webhooks/notification`) is prepared for you, with your API key attached.
3. Tap **Download APK** to install [Notification Relay Webhook](https://github.com/Enki013/notification-relay-webhook). The fork includes a fix that keeps it connected on HyperOS (Xiaomi).
4. Open Notification Relay and grant **Notification access**.
5. In its **Webhooks** tab, tap `+`, paste the URL and save.
6. Tap **Test**. Notifications now appear in Mozaik under **Records → Notifications** and in the **Timeline**.

> [!TIP]
> To forward only the notifications you care about, choose the apps you want in Notification Relay's filter settings.

---

## Step 5: Health Connect

1. In **Settings**, find the **Health Connect Webhook Integration** card and copy the webhook URL (`https://…/api/webhooks/health`).
2. Install [Health Connect Webhook](https://github.com/mcnaveen/health-connect-webhook/releases) and allow access to the data types you want to share (steps, sleep, heart rate, workouts, weight and more).
3. Paste the URL as the webhook target. If you copied it without the key, add the header `X-Webhook-Secret: <API_KEY>`.
4. Choose a sync interval and start the background sync. Data appears under **Records → Health Connect**.

---

## Step 6: Letterboxd and Goodreads

### Letterboxd (films)

1. In **Settings → Culture & Media Synchronization**, enter **just your username** in the Letterboxd field (for example `username`). Mozaik turns it into `https://letterboxd.com/username/rss/` for you.
2. Tap **Save**, then sync.
3. Your watched films and ratings are added to your library.
4. If you rate a film you logged earlier, the existing entry is updated instead of creating a duplicate.

### Goodreads (books)

1. Enter your numeric Goodreads user ID or your profile URL. You can find the ID in your profile address, for example `goodreads.com/user/show/12345678-name`.
2. Tap **Save**, then sync. Your shelves (read, currently reading, want to read) are imported from RSS.

---

## Step 7: Music, films and series (optional)

Turn services on in the **Integration catalog** (the `+` button next to **Integrations** on the Overview screen), then configure them in **Settings**:

- **Last.fm, ListenBrainz, Stats.fm:** enter your username and sync. These need the Cloudflare Worker server.
- **Web Scrobbler:** install the [browser extension](https://web-scrobbler.com/), enable its **Webhook** service and use `https://…/api/webhooks/music` with the header `X-API-Key: <API_KEY>`.
- **Trakt.tv:** enter your username. Public profiles need no password.
- **Simkl:** create a Client ID and Access Token at [simkl.com/settings/developer](https://simkl.com/settings/developer/).
- **MDBList:** add a free API key from [mdblist.com/preferences](https://mdblist.com/preferences) to show IMDb, Rotten Tomatoes, Metacritic and Letterboxd ratings on film details.

---

## How integrations are built

Every data source fits one of three plugin types:

1. **Webhook plugins (push):** external apps send JSON to `/api/webhooks/*`. Examples: Notification Relay, Health Connect Webhook, Web Scrobbler, and Home Assistant or Tasker automations.
2. **RSS plugins (pull):** the server reads public RSS feeds on a schedule and stores new items. Examples: Letterboxd and Goodreads.
3. **API plugins:** the server signs in to a service with your credentials or a token. Examples: FatSecret, Last.fm, Trakt.tv, Simkl and MDBList. Spotify and Steam are planned.

See [ARCHITECTURE.md](ARCHITECTURE.md) for the details.

---

## Backups

- **JSON:** **Settings → Data Export** saves all your records as JSON to your phone's Downloads folder.
- **SQLite:** the same screen downloads the raw database file.
- **Cloudflare:** `npx wrangler d1 export personal-records-db --remote --output=backup.sql` saves a full SQL dump.
