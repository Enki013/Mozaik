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

def import_goodreads_csv(csv_text: str) -> dict[str, int]:
    reader = csv.DictReader(csv_text.splitlines())
    inserted = 0
    updated = 0
    with db() as conn:
        for row in reader:
            book_id = goodreads_row_value(row, "Book Id", "BookID")
            title = goodreads_row_value(row, "Title")
            if not book_id or not title:
                continue
            exists = conn.execute(
                "select 1 from goodreads_books where book_id = ?", (book_id,)
            ).fetchone()
            payload = (
                book_id,
                title,
                goodreads_row_value(row, "Author", "Author l-f"),
                goodreads_row_value(row, "ISBN"),
                goodreads_row_value(row, "ISBN13"),
                to_int(goodreads_row_value(row, "My Rating")),
                to_float(goodreads_row_value(row, "Average Rating")),
                goodreads_row_value(row, "Publisher"),
                goodreads_row_value(row, "Binding"),
                to_int(goodreads_row_value(row, "Number of Pages")),
                to_int(goodreads_row_value(row, "Year Published")),
                to_int(goodreads_row_value(row, "Original Publication Year")),
                goodreads_row_value(row, "Date Read"),
                goodreads_row_value(row, "Date Added"),
                goodreads_row_value(row, "Bookshelves"),
                goodreads_row_value(row, "Exclusive Shelf"),
                goodreads_row_value(row, "My Review"),
                json.dumps(row, ensure_ascii=False),
            )
            conn.execute(
                """
                insert into goodreads_books (
                  book_id, title, author, isbn, isbn13, my_rating, average_rating,
                  publisher, binding, number_of_pages, year_published,
                  original_publication_year, date_read, date_added, bookshelves,
                  exclusive_shelf, my_review, raw_json
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict(book_id) do update set
                  title=excluded.title,
                  author=excluded.author,
                  isbn=excluded.isbn,
                  isbn13=excluded.isbn13,
                  my_rating=excluded.my_rating,
                  average_rating=excluded.average_rating,
                  publisher=excluded.publisher,
                  binding=excluded.binding,
                  number_of_pages=excluded.number_of_pages,
                  year_published=excluded.year_published,
                  original_publication_year=excluded.original_publication_year,
                  date_read=excluded.date_read,
                  date_added=excluded.date_added,
                  bookshelves=excluded.bookshelves,
                  exclusive_shelf=excluded.exclusive_shelf,
                  my_review=excluded.my_review,
                  raw_json=excluded.raw_json,
                  imported_at=current_timestamp
                """,
                payload,
            )
            if exists:
                updated += 1
            else:
                inserted += 1
    return {"inserted": inserted, "updated": updated}

def synthetic_book_id(source: str, title: str, author: str) -> str:
    digest = hashlib.sha1(f"{source}|{title}|{author}".encode("utf-8")).hexdigest()
    return f"sync:{digest[:24]}"

def matching_goodreads_book_id(conn: sqlite3.Connection, row: dict[str, str], title: str, author: str) -> str:
    isbn = goodreads_row_value(row, "ISBN")
    isbn13 = goodreads_row_value(row, "ISBN13")
    if isbn or isbn13:
        match = conn.execute(
            """
            select book_id
            from goodreads_books
            where (? <> '' and isbn = ?)
               or (? <> '' and isbn13 = ?)
            limit 1
            """,
            (isbn, isbn, isbn13, isbn13),
        ).fetchone()
        if match:
            return match["book_id"]
    match = conn.execute(
        """
        select book_id
        from goodreads_books
        where lower(trim(title)) = lower(trim(?))
          and lower(trim(coalesce(author, ''))) = lower(trim(?))
        limit 1
        """,
        (title, author),
    ).fetchone()
    return match["book_id"] if match else ""

def upsert_goodreads_book(row: dict[str, str]) -> str:
    book_id = goodreads_row_value(row, "Book Id", "BookID")
    title = goodreads_row_value(row, "Title")
    author = goodreads_row_value(row, "Author", "Author l-f")
    if not title:
        return "skipped"
    with db() as conn:
        if not book_id:
            book_id = matching_goodreads_book_id(conn, row, title, author)
        if not book_id:
            book_id = synthetic_book_id(goodreads_row_value(row, "Source", "Link"), title, author)
        exists = conn.execute(
            "select 1 from goodreads_books where book_id = ?", (book_id,)
        ).fetchone()
        conn.execute(
            """
            insert into goodreads_books (
              book_id, title, author, isbn, isbn13, my_rating, average_rating,
              publisher, binding, number_of_pages, year_published,
              original_publication_year, date_read, date_added, bookshelves,
              exclusive_shelf, my_review, raw_json
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            on conflict(book_id) do update set
              title=excluded.title,
              author=excluded.author,
              isbn=excluded.isbn,
              isbn13=excluded.isbn13,
              my_rating=coalesce(excluded.my_rating, goodreads_books.my_rating),
              average_rating=coalesce(excluded.average_rating, goodreads_books.average_rating),
              publisher=coalesce(excluded.publisher, goodreads_books.publisher),
              binding=coalesce(excluded.binding, goodreads_books.binding),
              number_of_pages=coalesce(excluded.number_of_pages, goodreads_books.number_of_pages),
              year_published=coalesce(excluded.year_published, goodreads_books.year_published),
              original_publication_year=coalesce(excluded.original_publication_year, goodreads_books.original_publication_year),
              date_read=coalesce(nullif(excluded.date_read, ''), goodreads_books.date_read),
              date_added=coalesce(nullif(excluded.date_added, ''), goodreads_books.date_added),
              bookshelves=coalesce(nullif(excluded.bookshelves, ''), goodreads_books.bookshelves),
              exclusive_shelf=coalesce(nullif(excluded.exclusive_shelf, ''), goodreads_books.exclusive_shelf),
              my_review=coalesce(nullif(excluded.my_review, ''), goodreads_books.my_review),
              raw_json=excluded.raw_json,
              imported_at=current_timestamp
            """,
            (
                book_id,
                title,
                author,
                goodreads_row_value(row, "ISBN"),
                goodreads_row_value(row, "ISBN13"),
                to_int(goodreads_row_value(row, "My Rating")),
                to_float(goodreads_row_value(row, "Average Rating")),
                goodreads_row_value(row, "Publisher"),
                goodreads_row_value(row, "Binding"),
                to_int(goodreads_row_value(row, "Number of Pages")),
                to_int(goodreads_row_value(row, "Year Published")),
                to_int(goodreads_row_value(row, "Original Publication Year")),
                goodreads_row_value(row, "Date Read"),
                goodreads_row_value(row, "Date Added"),
                goodreads_row_value(row, "Bookshelves"),
                goodreads_row_value(row, "Exclusive Shelf"),
                goodreads_row_value(row, "My Review"),
                json.dumps(row, ensure_ascii=False),
            ),
        )
    return "updated" if exists else "inserted"

def clean_goodreads_shelves(value: str) -> str:
    text = clean_html_text(value)
    text = text.replace("bookshelves:", "")
    parts = re.split(r"[,|;/]\s*|\s{2,}", text)
    shelves = []
    for part in parts:
        shelf = part.strip().strip("#")
        if shelf and shelf not in shelves:
            shelves.append(shelf)
    return ", ".join(shelves)

def exclusive_shelf_from(bookshelves: str, fallback: str = "", rating: str = "", date_read: str = "") -> str:
    fallback = urllib.parse.unquote_plus(fallback or "").strip()
    if fallback and fallback != "#ALL#":
        return fallback
    shelves = [s.strip() for s in bookshelves.split(",") if s.strip()]
    for preferred in ("currently-reading", "read", "to-read"):
        if preferred in shelves:
            return preferred
    if date_read.strip() or to_int(rating):
        return "read"
    if fallback == "#ALL#":
        return "read"
    return shelves[0] if shelves else ""

def book_id_from_link(link: str) -> str:
    match = re.search(r"/book/show/(\d+)", link or "")
    return match.group(1) if match else ""

def shelf_from_feed_url(feed_url: str) -> str:
    parsed = urllib.parse.urlparse(feed_url)
    shelf = urllib.parse.parse_qs(parsed.query).get("shelf", [""])[0]
    return urllib.parse.unquote_plus(shelf).strip()

def parse_goodreads_feed(xml_text: str, default_shelf: str = "") -> list[dict[str, str]]:
    root = ET.fromstring(xml_text)
    items = root.findall("./channel/item")
    rows = []
    for item in items:
        title = text_of(item, "book_title") or text_of(item, "title")
        link = text_of(item, "link")
        description = text_of(item, "user_review") or text_of(item, "description")
        author = text_of(item, "author_name")
        if " by " in title:
            title, author = title.rsplit(" by ", 1)
        fallback_bookshelf = "" if default_shelf == "#ALL#" else default_shelf
        bookshelves = clean_goodreads_shelves(text_of(item, "user_shelves")) or fallback_bookshelf
        rating = text_of(item, "user_rating")
        date_read = text_of(item, "user_read_at")
        exclusive_shelf = exclusive_shelf_from(bookshelves, default_shelf, rating, date_read)
        rows.append(
            {
                "Book Id": text_of(item, "book_id") or book_id_from_link(link),
                "Title": html.unescape(title).strip(),
                "Author": html.unescape(author).strip(),
                "ISBN": text_of(item, "isbn"),
                "ISBN13": text_of(item, "isbn13"),
                "My Rating": rating,
                "Average Rating": text_of(item, "average_rating"),
                "Year Published": text_of(item, "book_published"),
                "Original Publication Year": text_of(item, "book_published"),
                "Date Read": date_read,
                "Date Added": text_of(item, "user_date_added") or text_of(item, "pubDate"),
                "Bookshelves": bookshelves,
                "Exclusive Shelf": exclusive_shelf,
                "My Review": clean_html_text(description),
                "Source": link,
            }
        )
    return rows

def sync_goodreads_feed(feed_url: str, shelf: str = "") -> dict[str, int]:
    if not feed_url.startswith(("https://", "http://")):
        raise RuntimeError("Goodreads feed URL http veya https ile baslamali.")
    if not shelf:
        shelf = shelf_from_feed_url(feed_url)
    req = urllib.request.Request(feed_url, headers={"User-Agent": "PersonalRecords/0.1"})
    with urllib.request.urlopen(req, timeout=30) as resp:
        xml_text = resp.read().decode("utf-8", errors="replace")
    inserted = 0
    updated = 0
    for row in parse_goodreads_feed(xml_text, shelf):
        status = upsert_goodreads_book(row)
        if status == "inserted":
            inserted += 1
        elif status == "updated":
            updated += 1
    return {"inserted": inserted, "updated": updated}

def get_goodreads_sync_settings() -> sqlite3.Row:
    with db() as conn:
        row = conn.execute("select * from goodreads_sync_settings where id = 1").fetchone()
        if row:
            return row
        conn.execute(
            "insert into goodreads_sync_settings (id, feed_url, interval_minutes, enabled) values (1, '', 360, 0)"
        )
        return conn.execute("select * from goodreads_sync_settings where id = 1").fetchone()

def save_goodreads_sync_settings(feed_url: str, interval_minutes: int, enabled: bool) -> None:
    interval_minutes = max(15, interval_minutes)
    with db() as conn:
        conn.execute(
            """
            insert into goodreads_sync_settings (id, feed_url, interval_minutes, enabled, updated_at)
            values (1, ?, ?, ?, current_timestamp)
            on conflict(id) do update set
              feed_url=excluded.feed_url,
              interval_minutes=excluded.interval_minutes,
              enabled=excluded.enabled,
              updated_at=current_timestamp
            """,
            (feed_url.strip(), interval_minutes, 1 if enabled else 0),
        )

def record_goodreads_sync_run(source: str, status: str, inserted: int = 0, updated: int = 0, message: str = "") -> None:
    with db() as conn:
        conn.execute(
            "insert into goodreads_sync_runs (source, status, inserted, updated, message) values (?, ?, ?, ?, ?)",
            (source, status, inserted, updated, message),
        )

def repair_goodreads_blank_rss_shelves() -> int:
    repaired = 0
    with db() as conn:
        rows = conn.execute(
            """
            select book_id, raw_json
            from goodreads_books
            where coalesce(exclusive_shelf, '') = ''
            """
        ).fetchall()
        for row in rows:
            try:
                data = json.loads(row["raw_json"])
            except json.JSONDecodeError:
                continue
            source = data.get("Source", "")
            if "goodreads.com/review/show/" not in source:
                continue
            conn.execute(
                """
                update goodreads_books
                set exclusive_shelf = 'read',
                    bookshelves = case when coalesce(bookshelves, '') = '' then 'read' else bookshelves end
                where book_id = ?
                """,
                (row["book_id"],),
            )
            repaired += 1
    return repaired

def run_configured_goodreads_sync() -> dict[str, int]:
    settings = get_goodreads_sync_settings()
    feed_url = settings["feed_url"] or ""
    if not feed_url:
        raise RuntimeError("Goodreads feed URL ayarlanmamis.")
    try:
        result = sync_goodreads_feed(feed_url)
        repaired = repair_goodreads_blank_rss_shelves()
        if repaired:
            result["updated"] += repaired
        record_goodreads_sync_run(feed_url, "ok", result["inserted"], result["updated"])
        return result
    except Exception as exc:
        record_goodreads_sync_run(feed_url, "error", message=str(exc))
        raise

def goodreads_shelves() -> list[str]:
    with db() as conn:
        rows = conn.execute(
            "select distinct exclusive_shelf from goodreads_books where coalesce(exclusive_shelf, '') <> '' order by exclusive_shelf"
        ).fetchall()
    return [row["exclusive_shelf"] for row in rows]

def query_goodreads_books(search: str = "", shelf: str = "", limit: int = 100) -> list[sqlite3.Row]:
    sql = """
      select book_id, title, author, my_rating, average_rating, exclusive_shelf,
             bookshelves, date_read, date_added, year_published
      from goodreads_books
      where 1 = 1
    """
    args: list[str | int] = []
    if search:
        sql += " and (lower(title) like ? or lower(author) like ?)"
        pattern = f"%{search.lower()}%"
        args.extend([pattern, pattern])
    if shelf:
        sql += " and exclusive_shelf = ?"
        args.append(shelf)
    sql += " order by coalesce(date_read, '') desc, coalesce(date_added, '') desc, title limit ?"
    args.append(limit)
    with db() as conn:
        return conn.execute(sql, args).fetchall()
