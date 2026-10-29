# Mozaik: Cloudflare Worker and D1 server

This module runs the Mozaik server on **Cloudflare Workers** (with [Hono](https://hono.dev/)) and **Cloudflare D1** (serverless SQLite). It is free on the Cloudflare free tier and runs 24/7 without a server to maintain.

---

## Files

- `src/index.ts`: every REST API route, the Hono router and the cron sync logic.
- `src/html.ts`: the built-in web dashboard.
- `schema.sql`: D1 table definitions.
- `wrangler.toml`: Worker and D1 binding configuration. Start from `wrangler.toml.example`.
- `.dev.vars.example`: template for local secrets. Copy it to `.dev.vars`.
- `scripts/export_sqlite_to_sql.py`: turns a local `personal_records.sqlite3` file into a D1-compatible SQL dump.
- `scripts/migrate_sqlite_to_d1.sh`: imports that dump into D1, locally or remotely.

---

## Local development

Run the whole server on your computer with a local D1 database:

```bash
cd worker

# 1. Install dependencies
npm install

# 2. Create local secrets (set API_KEY at least)
cp .dev.vars.example .dev.vars

# 3. Create the local database
npm run db:init:local
# or import an existing Go/SQLite database instead:
npm run db:migrate:local

# 4. Start the local server
npm run dev
# or on a specific port, reachable from other devices on your network:
npx wrangler dev --port 8788 --ip 0.0.0.0
```

From the Android emulator, the local server is at `http://10.0.2.2:<port>`.

---

## Deploying to Cloudflare

### 1. Sign in

```bash
npx wrangler login
```

### 2. Create the D1 database

```bash
npx wrangler d1 create personal-records-db
```

Paste the `database_id` from the output into `worker/wrangler.toml`:

```toml
[[d1_databases]]
binding = "DB"
database_name = "personal-records-db"
database_id = "YOUR_D1_DATABASE_ID"
```

### 3. Create the tables, or import existing data

```bash
# New install
npm run db:init:remote

# Or move an existing Go/SQLite database to D1
npm run db:migrate:remote
```

### 4. Set secrets

```bash
npx wrangler secret put API_KEY

# Optional
npx wrangler secret put FATSECRET_CONSUMER_KEY
npx wrangler secret put FATSECRET_CONSUMER_SECRET
npx wrangler secret put LASTFM_API_KEY
npx wrangler secret put LASTFM_USERNAME
```

### 5. Deploy

```bash
npm run deploy
```

Cloudflare prints your live URL, for example `https://personal-records-api.<your-subdomain>.workers.dev`.

### 6. Point the app at it

In the Mozaik Android app:

1. Open **Settings**.
2. Enter the URL above in **Server URL** and your key in **API key**.
3. Tap **Save**, then **Test Connection**.

The app now works 24/7, even when your computer at home is off.

The cron trigger in `wrangler.toml` (`0 */6 * * *`) syncs your sources every 6 hours.

---

## Backups

Download everything in D1 as an SQL file at any time:

```bash
npx wrangler d1 export personal-records-db --remote --output=backup.sql
```

Turn the backup into a local SQLite database:

```bash
sqlite3 local.db < backup.sql
```

The app's **Settings → Data Export** screen can also download a JSON archive or the raw SQLite file from the live Worker.
