#!/usr/bin/env python3
import sqlite3
import sys
import os

def escape_sql_value(val):
    if val is None:
        return "NULL"
    if isinstance(val, (int, float)):
        return str(val)
    # text
    s = str(val).replace("'", "''")
    return f"'{s}'"

def dump_sqlite_to_d1(db_path, output_sql_path):
    if not os.path.exists(db_path):
        print(f"Error: {db_path} does not exist", file=sys.stderr)
        sys.exit(1)

    con = sqlite3.connect(db_path)
    cur = con.cursor()

    tables = [
        "user_settings",
        "goodreads_sync_settings",
        "letterboxd_sync_settings",
        "fatsecret_tokens",
        "fatsecret_scanned_days",
        "goodreads_books",
        "letterboxd_films",
        "fatsecret_food_entries",
    ]

    with open(output_sql_path, "w", encoding="utf-8") as f:
        f.write("-- D1 Migration data generated from SQLite\n")
        total_rows = 0
        for table in tables:
            cur.execute(f"PRAGMA table_info({table})")
            cols = [row[1] for row in cur.fetchall()]
            if not cols:
                continue
            cols_joined = ", ".join(f'"{c}"' for c in cols)

            cur.execute(f"SELECT {cols_joined} FROM {table}")
            rows = cur.fetchall()
            if not rows:
                continue

            f.write(f"\n-- Table: {table} ({len(rows)} rows)\n")
            for row in rows:
                vals = ", ".join(escape_sql_value(v) for v in row)
                f.write(f"INSERT OR REPLACE INTO {table} ({cols_joined}) VALUES ({vals});\n")
            total_rows += len(rows)

    con.close()
    print(f"Successfully generated {output_sql_path} with {total_rows} rows from {len(tables)} tables.")

if __name__ == "__main__":
    db = sys.argv[1] if len(sys.argv) > 1 else "../personal_records.sqlite3"
    out = sys.argv[2] if len(sys.argv) > 2 else "migration_data.sql"
    dump_sqlite_to_d1(db, out)
