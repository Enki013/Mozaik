import json
import sqlite3

from personal_records.infrastructure.database import db
from personal_records.integrations.fatsecret_client import fatsecret_signed_get
from personal_records.shared import date_int_to_iso, to_float, today_date_int

def get_fatsecret_access_token() -> sqlite3.Row | None:
    with db() as conn:
        return conn.execute("select * from fatsecret_tokens where id = 1").fetchone()

def normalize_food_entries(payload: dict) -> list[dict]:
    if not payload:
        return []
    food_entries = payload.get("food_entries") or {}
    entries = food_entries.get("food_entry", [])
    if isinstance(entries, dict):
        return [entries]
    return entries if isinstance(entries, list) else []

def upsert_fatsecret_food_entry(entry: dict) -> str:
    entry_id = str(entry.get("food_entry_id", "")).strip()
    if not entry_id:
        return "skipped"
    with db() as conn:
        exists = conn.execute(
            "select 1 from fatsecret_food_entries where food_entry_id = ?",
            (entry_id,),
        ).fetchone()
        conn.execute(
            """
            insert into fatsecret_food_entries (
              food_entry_id, date_int, meal, food_entry_name,
              food_entry_description, calories, carbohydrate, protein, fat, raw_json
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            on conflict(food_entry_id) do update set
              date_int=excluded.date_int,
              meal=excluded.meal,
              food_entry_name=excluded.food_entry_name,
              food_entry_description=excluded.food_entry_description,
              calories=excluded.calories,
              carbohydrate=excluded.carbohydrate,
              protein=excluded.protein,
              fat=excluded.fat,
              raw_json=excluded.raw_json,
              synced_at=current_timestamp
            """,
            (
                entry_id,
                to_int(str(entry.get("date_int", ""))),
                str(entry.get("meal", "")),
                str(entry.get("food_entry_name", "")),
                str(entry.get("food_entry_description", "")),
                to_float(str(entry.get("calories", ""))),
                to_float(str(entry.get("carbohydrate", ""))),
                to_float(str(entry.get("protein", ""))),
                to_float(str(entry.get("fat", ""))),
                json.dumps(entry, ensure_ascii=False),
            ),
        )
    return "updated" if exists else "inserted"

def sync_fatsecret_food_entries_for_date(date_int: int) -> dict[str, int]:
    token = get_fatsecret_access_token()
    if not token:
        raise RuntimeError("Once FatSecret hesabini baglaman gerekiyor.")
    payload = fatsecret_signed_get(
        "https://platform.fatsecret.com/rest/food-entries/v2",
        {"date": str(date_int), "format": "json"},
        token["oauth_token"],
        token["oauth_token_secret"],
    )
    inserted = 0
    updated = 0
    for entry in normalize_food_entries(payload):
        status = upsert_fatsecret_food_entry(entry)
        if status == "inserted":
            inserted += 1
        elif status == "updated":
            updated += 1
    return {"inserted": inserted, "updated": updated}

def record_fatsecret_sync_run(source: str, status: str, inserted: int = 0, updated: int = 0, message: str = "") -> None:
    with db() as conn:
        conn.execute(
            "insert into fatsecret_sync_runs (source, status, inserted, updated, message) values (?, ?, ?, ?, ?)",
            (source, status, inserted, updated, message),
        )

def sync_fatsecret_food_entries(days: int = 1) -> dict[str, int]:
    days = max(1, min(days, 366))
    start = today_date_int() - days + 1
    total = {"inserted": 0, "updated": 0}
    try:
        for offset in range(days):
            result = sync_fatsecret_food_entries_for_date(start + offset)
            total["inserted"] += result["inserted"]
            total["updated"] += result["updated"]
        record_fatsecret_sync_run(f"food_entries:{days}d", "ok", total["inserted"], total["updated"])
        return total
    except Exception as exc:
        record_fatsecret_sync_run(f"food_entries:{days}d", "error", message=str(exc))
        raise

def fatsecret_daily_summary(limit: int = 14) -> list[sqlite3.Row]:
    with db() as conn:
        return conn.execute(
            """
            select date_int, count(*) entry_count, coalesce(sum(calories), 0) calories
            from fatsecret_food_entries
            group by date_int
            order by date_int desc
            limit ?
            """,
            (limit,),
        ).fetchall()

def fatsecret_recent_entries(limit: int = 30) -> list[sqlite3.Row]:
    with db() as conn:
        return conn.execute(
            """
            select *
            from fatsecret_food_entries
            order by date_int desc, synced_at desc
            limit ?
            """,
            (limit,),
        ).fetchall()
