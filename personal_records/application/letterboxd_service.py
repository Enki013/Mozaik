import csv
import hashlib
import html
import json
import re
import sqlite3
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET

from personal_records.infrastructure.database import db
from personal_records.shared import clean_html_text, goodreads_row_value, text_of, to_float, to_int

def letterboxd_film_key(row: dict[str, str]) -> str:
    uri = goodreads_row_value(row, "Letterboxd URI", "URI", "Link")
    name = goodreads_row_value(row, "Name", "Title")
    year = goodreads_row_value(row, "Year")
    watched_date = goodreads_row_value(row, "Watched Date", "Date")
    source = goodreads_row_value(row, "Source")
    basis = uri or f"{name}|{year}|{watched_date}|{source}"
    digest = hashlib.sha1(basis.encode("utf-8")).hexdigest()
    return f"lb:{digest[:28]}"

def matching_letterboxd_film_key(conn: sqlite3.Connection, row: dict[str, str], name: str) -> str:
    year = to_int(goodreads_row_value(row, "Year"))
    watched_date = goodreads_row_value(row, "Watched Date")
    diary_date = goodreads_row_value(row, "Date")
    match = conn.execute(
        """
        select film_key
        from letterboxd_films
        where lower(trim(name)) = lower(trim(?))
          and coalesce(year, -1) = coalesce(?, -1)
          and coalesce(watched_date, '') = coalesce(?, '')
          and coalesce(diary_date, '') = coalesce(?, '')
        limit 1
        """,
        (name, year, watched_date, diary_date),
    ).fetchone()
    return match["film_key"] if match else ""

def upsert_letterboxd_film(row: dict[str, str], source: str = "csv") -> str:
    name = goodreads_row_value(row, "Name", "Title")
    if not name:
        return "skipped"
    with db() as conn:
        key = matching_letterboxd_film_key(conn, row, name) or letterboxd_film_key(row)
        exists = conn.execute(
            "select 1 from letterboxd_films where film_key = ?", (key,)
        ).fetchone()
        conn.execute(
            """
            insert into letterboxd_films (
              film_key, letterboxd_uri, name, year, rating, watched_date,
              diary_date, rewatch, tags, review, source, raw_json
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            on conflict(film_key) do update set
              letterboxd_uri=coalesce(nullif(excluded.letterboxd_uri, ''), letterboxd_films.letterboxd_uri),
              name=excluded.name,
              year=coalesce(excluded.year, letterboxd_films.year),
              rating=coalesce(excluded.rating, letterboxd_films.rating),
              watched_date=coalesce(nullif(excluded.watched_date, ''), letterboxd_films.watched_date),
              diary_date=coalesce(nullif(excluded.diary_date, ''), letterboxd_films.diary_date),
              rewatch=coalesce(nullif(excluded.rewatch, ''), letterboxd_films.rewatch),
              tags=coalesce(nullif(excluded.tags, ''), letterboxd_films.tags),
              review=coalesce(nullif(excluded.review, ''), letterboxd_films.review),
              source=excluded.source,
              raw_json=excluded.raw_json,
              imported_at=current_timestamp
            """,
            (
                key,
                goodreads_row_value(row, "Letterboxd URI", "URI", "Link"),
                name,
                to_int(goodreads_row_value(row, "Year")),
                to_float(goodreads_row_value(row, "Rating")),
                goodreads_row_value(row, "Watched Date"),
                goodreads_row_value(row, "Date"),
                goodreads_row_value(row, "Rewatch"),
                goodreads_row_value(row, "Tags"),
                goodreads_row_value(row, "Review", "Description"),
                source,
                json.dumps(row, ensure_ascii=False),
            ),
        )
    return "updated" if exists else "inserted"

def import_letterboxd_csv(csv_text: str) -> dict[str, int]:
    reader = csv.DictReader(csv_text.splitlines())
    inserted = 0
    updated = 0
    for row in reader:
        status = upsert_letterboxd_film(row, "csv")
        if status == "inserted":
            inserted += 1
        elif status == "updated":
            updated += 1
    return {"inserted": inserted, "updated": updated}

def letterboxd_title_parts(title: str) -> tuple[str, str]:
    title = html.unescape(title or "").strip()
    title = re.sub(r"\s+-\s+★.*$", "", title).strip()
    match = re.match(r"(.+),\s*(\d{4})$", title)
    if match:
        return match.group(1).strip(), match.group(2)
    return title, ""

def parse_letterboxd_feed(xml_text: str) -> list[dict[str, str]]:
    root = ET.fromstring(xml_text)
    rows = []
    for item in root.findall("./channel/item"):
        item_title = text_of(item, "title")
        name, year = letterboxd_title_parts(item_title)
        link = text_of(item, "link")
        description = clean_html_text(text_of(item, "description"))
        rows.append(
            {
                "Name": name,
                "Year": year,
                "Date": text_of(item, "pubDate"),
                "Letterboxd URI": link,
                "Review": description,
                "Source": link,
            }
        )
    return rows

def sync_letterboxd_feed(feed_url: str) -> dict[str, int]:
    if not feed_url.startswith(("https://", "http://")):
        raise RuntimeError("Letterboxd RSS URL http veya https ile baslamali.")
    req = urllib.request.Request(feed_url, headers={"User-Agent": "PersonalRecords/0.1"})
    with urllib.request.urlopen(req, timeout=30) as resp:
        xml_text = resp.read().decode("utf-8", errors="replace")
    inserted = 0
    updated = 0
    for row in parse_letterboxd_feed(xml_text):
        status = upsert_letterboxd_film(row, "rss")
        if status == "inserted":
            inserted += 1
        elif status == "updated":
            updated += 1
    return {"inserted": inserted, "updated": updated}

def letterboxd_official_feed_url(value: str) -> str:
    value = value.strip()
    if not value:
        return ""
    parsed = urllib.parse.urlparse(value)
    if "letterboxd.com" not in parsed.netloc:
        return value
    path = parsed.path.strip("/")
    if path.endswith("rss"):
        return urllib.parse.urlunparse((parsed.scheme or "https", parsed.netloc, "/" + path + "/", "", "", ""))
    username = path.split("/", 1)[0]
    return f"https://letterboxd.com/{username}/rss/" if username else value

def get_letterboxd_sync_settings() -> sqlite3.Row:
    with db() as conn:
        row = conn.execute("select * from letterboxd_sync_settings where id = 1").fetchone()
        if row:
            return row
        conn.execute(
            "insert into letterboxd_sync_settings (id, feed_url, interval_minutes, enabled) values (1, '', 360, 0)"
        )
        return conn.execute("select * from letterboxd_sync_settings where id = 1").fetchone()

def save_letterboxd_sync_settings(feed_url: str, interval_minutes: int, enabled: bool) -> None:
    interval_minutes = max(15, interval_minutes)
    feed_url = letterboxd_official_feed_url(feed_url)
    with db() as conn:
        conn.execute(
            """
            insert into letterboxd_sync_settings (id, feed_url, interval_minutes, enabled, updated_at)
            values (1, ?, ?, ?, current_timestamp)
            on conflict(id) do update set
              feed_url=excluded.feed_url,
              interval_minutes=excluded.interval_minutes,
              enabled=excluded.enabled,
              updated_at=current_timestamp
            """,
            (feed_url, interval_minutes, 1 if enabled else 0),
        )

def record_letterboxd_sync_run(source: str, status: str, inserted: int = 0, updated: int = 0, message: str = "") -> None:
    with db() as conn:
        conn.execute(
            "insert into letterboxd_sync_runs (source, status, inserted, updated, message) values (?, ?, ?, ?, ?)",
            (source, status, inserted, updated, message),
        )

def run_configured_letterboxd_sync() -> dict[str, int]:
    settings = get_letterboxd_sync_settings()
    feed_url = settings["feed_url"] or ""
    if not feed_url:
        raise RuntimeError("Letterboxd RSS URL ayarlanmamis.")
    try:
        result = sync_letterboxd_feed(feed_url)
        record_letterboxd_sync_run(feed_url, "ok", result["inserted"], result["updated"])
        return result
    except Exception as exc:
        record_letterboxd_sync_run(feed_url, "error", message=str(exc))
        raise

def query_letterboxd_films(limit: int = 80) -> list[sqlite3.Row]:
    with db() as conn:
        return conn.execute(
            """
            select *
            from letterboxd_films
            order by coalesce(watched_date, diary_date, imported_at) desc, name
            limit ?
            """,
            (limit,),
        ).fetchall()
