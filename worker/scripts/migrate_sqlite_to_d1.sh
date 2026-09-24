#!/usr/bin/env bash
set -e

MODE="${1:-local}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORKER_DIR="$(dirname "$SCRIPT_DIR")"
ROOT_DIR="$(dirname "$WORKER_DIR")"

echo "==> Step 1: Exporting SQLite to SQL..."
python3 "$SCRIPT_DIR/export_sqlite_to_sql.py" "$ROOT_DIR/personal_records.sqlite3" "$WORKER_DIR/migration_data.sql"

echo "==> Step 2: Applying Schema to D1 ($MODE)..."
if [ "$MODE" = "remote" ]; then
  npx wrangler d1 execute personal-records-db --remote --file="$WORKER_DIR/schema.sql"
  echo "==> Step 3: Inserting Data to D1 (remote)..."
  npx wrangler d1 execute personal-records-db --remote --file="$WORKER_DIR/migration_data.sql"
else
  npx wrangler d1 execute personal-records-db --local --file="$WORKER_DIR/schema.sql"
  echo "==> Step 3: Inserting Data to D1 (local)..."
  npx wrangler d1 execute personal-records-db --local --file="$WORKER_DIR/migration_data.sql"
fi

echo "==> Migration to D1 ($MODE) completed successfully!"
