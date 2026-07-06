import sqlite3

from personal_records.application.fatsecret_service import (
    fatsecret_daily_summary,
    fatsecret_recent_entries,
    get_fatsecret_access_token,
)
from personal_records.application.goodreads_service import (
    get_goodreads_sync_settings,
    query_goodreads_books,
)
from personal_records.application.letterboxd_service import (
    get_letterboxd_sync_settings,
    query_letterboxd_films,
)
from personal_records.infrastructure.database import db
from personal_records.shared import date_int_to_iso, row_dict, today_date_int

def api_fatsecret_entries(limit: int = 100) -> list[dict]:
    rows = fatsecret_recent_entries(limit)
    result = []
    for row in rows:
        item = row_dict(row)
        item["date"] = date_int_to_iso(item.get("date_int"))
        result.append(item)
    return result

def api_fatsecret_summary(limit: int = 30) -> list[dict]:
    rows = fatsecret_daily_summary(limit)
    return [
        {
            "date_int": row["date_int"],
            "date": date_int_to_iso(row["date_int"]),
            "entry_count": row["entry_count"],
            "calories": row["calories"],
        }
        for row in rows
    ]

def api_goodreads_books(search: str = "", shelf: str = "", limit: int = 100) -> list[dict]:
    return [row_dict(row) for row in query_goodreads_books(search, shelf, limit)]

def api_letterboxd_films(limit: int = 100) -> list[dict]:
    return [row_dict(row) for row in query_letterboxd_films(limit)]

def api_dashboard_summary() -> dict:
    with db() as conn:
        goodreads_count = conn.execute("select count(*) c from goodreads_books").fetchone()["c"]
        letterboxd_count = conn.execute("select count(*) c from letterboxd_films").fetchone()["c"]
        fatsecret_entry_count = conn.execute("select count(*) c from fatsecret_food_entries").fetchone()["c"]
        today = today_date_int()
        today_food = conn.execute(
            """
            select count(*) entry_count, coalesce(sum(calories), 0) calories
            from fatsecret_food_entries
            where date_int = ?
            """,
            (today,),
        ).fetchone()
        last_fatsecret_sync = conn.execute(
            "select * from fatsecret_sync_runs order by ran_at desc limit 1"
        ).fetchone()
        last_goodreads_sync = conn.execute(
            "select * from goodreads_sync_runs order by ran_at desc limit 1"
        ).fetchone()
        last_letterboxd_sync = conn.execute(
            "select * from letterboxd_sync_runs order by ran_at desc limit 1"
        ).fetchone()
    return {
        "counts": {
            "goodreads_books": goodreads_count,
            "letterboxd_films": letterboxd_count,
            "fatsecret_food_entries": fatsecret_entry_count,
        },
        "today": {
            "date": date_int_to_iso(today),
            "fatsecret_entry_count": today_food["entry_count"],
            "fatsecret_calories": today_food["calories"],
        },
        "connections": {
            "fatsecret": bool(get_fatsecret_access_token()),
            "goodreads_rss": bool((get_goodreads_sync_settings()["feed_url"] or "").strip()),
            "letterboxd_rss": bool((get_letterboxd_sync_settings()["feed_url"] or "").strip()),
        },
        "last_sync": {
            "fatsecret": row_dict(last_fatsecret_sync) if last_fatsecret_sync else None,
            "goodreads": row_dict(last_goodreads_sync) if last_goodreads_sync else None,
            "letterboxd": row_dict(last_letterboxd_sync) if last_letterboxd_sync else None,
        },
    }
