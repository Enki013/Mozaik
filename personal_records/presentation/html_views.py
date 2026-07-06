import html
import json
import sqlite3

from personal_records.application.fatsecret_service import (
    fatsecret_daily_summary,
    fatsecret_recent_entries,
    get_fatsecret_access_token,
)
from personal_records.application.goodreads_service import (
    get_goodreads_sync_settings,
    goodreads_shelves,
    query_goodreads_books,
)
from personal_records.application.letterboxd_service import (
    get_letterboxd_sync_settings,
    query_letterboxd_films,
)
from personal_records.config import DB_PATH, env
from personal_records.infrastructure.database import db
from personal_records.shared import date_int_to_iso

def render_fatsecret_summary(rows: list[sqlite3.Row]) -> str:
    if not rows:
        return '<div class="empty">Henuz FatSecret yemek gunlugu kaydi yok.</div>'
    body = "<table><thead><tr><th>Tarih</th><th>Kayit</th><th>Kalori</th></tr></thead><tbody>"
    body += "".join(
        f"<tr><td>{html.escape(date_int_to_iso(r['date_int']))}</td><td>{r['entry_count']}</td><td>{round(r['calories'])}</td></tr>"
        for r in rows
    )
    body += "</tbody></table>"
    return body

def render_fatsecret_entries(rows: list[sqlite3.Row]) -> str:
    if not rows:
        return '<div class="empty">Henuz kaydedilmis ogun yok.</div>'
    body = "<table><thead><tr><th>Tarih</th><th>Ogun</th><th>Besin</th><th>Kalori</th><th>Karb</th><th>Protein</th><th>Yag</th></tr></thead><tbody>"
    body += "".join(
        "<tr>"
        f"<td>{html.escape(date_int_to_iso(r['date_int']))}</td>"
        f"<td>{html.escape(r['meal'] or '')}</td>"
        f"<td><strong>{html.escape(r['food_entry_name'] or '')}</strong><div class='muted'>{html.escape(r['food_entry_description'] or '')}</div></td>"
        f"<td>{html.escape(str(r['calories'] or ''))}</td>"
        f"<td>{html.escape(str(r['carbohydrate'] or ''))}</td>"
        f"<td>{html.escape(str(r['protein'] or ''))}</td>"
        f"<td>{html.escape(str(r['fat'] or ''))}</td>"
        "</tr>"
        for r in rows
    )
    body += "</tbody></table>"
    return body

def render_letterboxd_table(rows: list[sqlite3.Row]) -> str:
    if not rows:
        return '<div class="empty">Henuz Letterboxd filmi yok.</div>'
    body = "<table><thead><tr><th>Film</th><th>Yil</th><th>Puan</th><th>Izleme</th><th>Kaynak</th></tr></thead><tbody>"
    body += "".join(
        "<tr>"
        f"<td><strong>{html.escape(r['name'] or '')}</strong></td>"
        f"<td>{html.escape(str(r['year'] or ''))}</td>"
        f"<td>{html.escape(str(r['rating'] or ''))}</td>"
        f"<td>{html.escape(r['watched_date'] or r['diary_date'] or '')}</td>"
        f"<td>{html.escape(r['source'] or '')}</td>"
        "</tr>"
        for r in rows
    )
    body += "</tbody></table>"
    return body

def render_books_table(rows: list[sqlite3.Row]) -> str:
    if not rows:
        return '<div class="empty">Kayit bulunamadi.</div>'
    body = "<table><thead><tr><th>Kitap</th><th>Yazar</th><th>Raf</th><th>Puan</th><th>Okuma</th><th>Yil</th></tr></thead><tbody>"
    body += "".join(
        "<tr>"
        f"<td><strong>{html.escape(r['title'] or '')}</strong></td>"
        f"<td>{html.escape(r['author'] or '')}</td>"
        f"<td>{html.escape(r['exclusive_shelf'] or '')}</td>"
        f"<td>{html.escape(str(r['my_rating'] or ''))}</td>"
        f"<td>{html.escape(r['date_read'] or '')}</td>"
        f"<td>{html.escape(str(r['year_published'] or ''))}</td>"
        "</tr>"
        for r in rows
    )
    body += "</tbody></table>"
    return body

def html_page(stats: dict, search: str = "", shelf: str = "") -> str:
    fatsecret_ready = bool(env("FATSECRET_CLIENT_ID") and env("FATSECRET_CLIENT_SECRET"))
    oauth1_ready = bool(env("FATSECRET_CONSUMER_KEY") and env("FATSECRET_CONSUMER_SECRET"))
    fatsecret_connected = bool(get_fatsecret_access_token())
    shelves = goodreads_shelves()
    options = '<option value="">Tum raflar</option>' + "".join(
        f'<option value="{html.escape(name)}" {"selected" if name == shelf else ""}>{html.escape(name)}</option>'
        for name in shelves
    )
    books = query_goodreads_books(search=search, shelf=shelf)
    sync_settings = get_goodreads_sync_settings()
    letterboxd_settings = get_letterboxd_sync_settings()
    with db() as conn:
        last_sync = conn.execute(
            "select * from goodreads_sync_runs order by ran_at desc limit 1"
        ).fetchone()
        letterboxd_last_sync = conn.execute(
            "select * from letterboxd_sync_runs order by ran_at desc limit 1"
        ).fetchone()
    last_sync_text = "Henuz otomatik sync yok."
    if last_sync:
        last_sync_text = f"{last_sync['ran_at']} - {last_sync['status']} - {last_sync['inserted']} yeni, {last_sync['updated']} guncel"
    letterboxd_last_sync_text = "Henuz Letterboxd sync yok."
    if letterboxd_last_sync:
        letterboxd_last_sync_text = f"{letterboxd_last_sync['ran_at']} - {letterboxd_last_sync['status']} - {letterboxd_last_sync['inserted']} yeni, {letterboxd_last_sync['updated']} guncel"
    with db() as conn:
        fatsecret_last_sync = conn.execute(
            "select * from fatsecret_sync_runs order by ran_at desc limit 1"
        ).fetchone()
    fatsecret_last_sync_text = "Henuz FatSecret sync yok."
    if fatsecret_last_sync:
        fatsecret_last_sync_text = f"{fatsecret_last_sync['ran_at']} - {fatsecret_last_sync['status']} - {fatsecret_last_sync['inserted']} yeni, {fatsecret_last_sync['updated']} guncel"
    fatsecret_summary = fatsecret_daily_summary()
    fatsecret_entries = fatsecret_recent_entries()
    letterboxd_films = query_letterboxd_films()
    return f"""<!doctype html>
<html lang="tr">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Kisisel Kayitlar</title>
  <style>
    :root {{ color-scheme: light; font-family: Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; }}
    body {{ margin: 0; background: #f6f7f4; color: #1d2522; }}
    main {{ max-width: 1080px; margin: 0 auto; padding: 32px 20px 56px; }}
    header {{ display: flex; justify-content: space-between; align-items: end; gap: 16px; margin-bottom: 28px; }}
    h1 {{ font-size: 32px; margin: 0; letter-spacing: 0; }}
    h2 {{ font-size: 18px; margin: 0 0 14px; }}
    .grid {{ display: grid; grid-template-columns: repeat(auto-fit, minmax(280px, 1fr)); gap: 16px; }}
    .card {{ background: #fff; border: 1px solid #d9ded6; border-radius: 8px; padding: 18px; box-shadow: 0 1px 2px rgba(18, 29, 25, .05); }}
    .wide {{ grid-column: 1 / -1; }}
    .stat {{ font-size: 28px; font-weight: 700; }}
    .stats {{ display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 10px; margin-top: 14px; }}
    .mini {{ background: #f3f6f2; border: 1px solid #dfe5dc; border-radius: 6px; padding: 10px; }}
    .mini div:first-child {{ font-size: 20px; font-weight: 750; }}
    .muted {{ color: #62706b; font-size: 14px; }}
    form {{ display: grid; gap: 10px; margin-top: 10px; }}
    .inline {{ grid-template-columns: minmax(160px, 1fr) minmax(140px, 180px) auto; align-items: center; }}
    input[type="text"], input[type="file"], select {{ border: 1px solid #c8d0ca; border-radius: 6px; padding: 10px; font: inherit; background: #fff; min-width: 0; }}
    input[type="number"] {{ border: 1px solid #c8d0ca; border-radius: 6px; padding: 10px; font: inherit; background: #fff; min-width: 0; }}
    label {{ display: grid; gap: 6px; color: #53615c; font-size: 13px; }}
    label span {{ font-weight: 650; }}
    button, .button {{ border: 0; border-radius: 6px; padding: 10px 12px; background: #236c5d; color: white; font-weight: 650; cursor: pointer; text-decoration: none; display: inline-flex; justify-content: center; }}
    button.secondary, .button.secondary {{ background: #33423d; }}
    .actions {{ display: flex; gap: 8px; flex-wrap: wrap; margin-top: 10px; }}
    table {{ width: 100%; border-collapse: collapse; font-size: 14px; }}
    th, td {{ text-align: left; padding: 9px 8px; border-bottom: 1px solid #e5e9e4; vertical-align: top; }}
    th {{ color: #53615c; font-size: 12px; text-transform: uppercase; letter-spacing: .04em; }}
    .status {{ display: inline-block; border-radius: 999px; padding: 4px 8px; background: #e8eee9; color: #2d453d; font-size: 12px; }}
    .empty {{ color: #62706b; padding: 14px 0; }}
    @media (max-width: 700px) {{ .inline {{ grid-template-columns: 1fr; }} .stats {{ grid-template-columns: 1fr; }} }}
  </style>
</head>
<body>
  <main>
    <header>
      <div>
        <h1>Kisisel Kayitlar</h1>
        <div class="muted">FatSecret ve Goodreads veri alma prototipi</div>
      </div>
      <span class="status">SQLite: {html.escape(DB_PATH.name)}</span>
    </header>

    <section class="grid">
      <div class="card">
        <h2>Goodreads</h2>
        <div class="stat">{stats["goodreads_count"]}</div>
        <div class="muted">kitap kaydi</div>
        <div class="stats">
          <div class="mini"><div>{stats["read_count"]}</div><span class="muted">okundu</span></div>
          <div class="mini"><div>{stats["to_read_count"]}</div><span class="muted">okunacak</span></div>
          <div class="mini"><div>{stats["rated_count"]}</div><span class="muted">puanli</span></div>
        </div>
        <form method="post" action="/goodreads/import" enctype="multipart/form-data">
          <input type="file" name="file" accept=".csv,text/csv" required>
          <button>CSV import et</button>
        </form>
        <div class="actions">
          <a class="button secondary" href="/goodreads/export">JSON disa aktar</a>
        </div>
      </div>

      <div class="card">
        <h2>Goodreads otomatik sync</h2>
        <div class="muted">{html.escape(last_sync_text)}</div>
        <form method="post" action="/goodreads/settings">
          <label><span>Goodreads RSS/feed URL</span><input type="text" name="feed_url" value="{html.escape(sync_settings['feed_url'] or '')}" placeholder="https://www.goodreads.com/review/list_rss/..."></label>
          <label><span>Aralik dakika</span><input type="number" name="interval_minutes" min="15" value="{html.escape(str(sync_settings['interval_minutes']))}"></label>
          <label style="display:flex; align-items:center; gap:8px;"><input type="checkbox" name="enabled" {"checked" if sync_settings["enabled"] else ""}> Otomatik calissin</label>
          <button>Ayar kaydet</button>
        </form>
        <form method="post" action="/goodreads/sync">
          <button class="secondary">Simdi sync et</button>
        </form>
      </div>

      <div class="card">
        <h2>FatSecret yemek gunlugu</h2>
        <div class="muted">Hesap: {"bagli" if fatsecret_connected else "bagli degil"} · OAuth1: {"hazir" if oauth1_ready else "eksik"}</div>
        <div class="muted">{html.escape(fatsecret_last_sync_text)}</div>
        <a class="button secondary" href="/fatsecret/connect">FatSecret bagla</a>
        <form method="post" action="/fatsecret/sync">
          <input type="hidden" name="days" value="1">
          <button>Bugunu sync et</button>
        </form>
        <form method="post" action="/fatsecret/sync">
          <input type="hidden" name="days" value="30">
          <button class="secondary">Son 30 gunu sync et</button>
        </form>
      </div>

      <div class="card">
        <h2>Letterboxd</h2>
        <div class="stat">{stats["letterboxd_count"]}</div>
        <div class="muted">film kaydi · {stats["letterboxd_rated_count"]} puanli</div>
        <form method="post" action="/letterboxd/import" enctype="multipart/form-data">
          <input type="file" name="file" accept=".csv,text/csv" required>
          <button>CSV import et</button>
        </form>
      </div>

      <div class="card">
        <h2>Letterboxd otomatik sync</h2>
        <div class="muted">{html.escape(letterboxd_last_sync_text)}</div>
        <form method="post" action="/letterboxd/settings">
          <label><span>Letterboxd RSS URL</span><input type="text" name="feed_url" value="{html.escape(letterboxd_settings['feed_url'] or '')}" placeholder="https://letterboxd.com/kullanici/rss/"></label>
          <label><span>Aralik dakika</span><input type="number" name="interval_minutes" min="15" value="{html.escape(str(letterboxd_settings['interval_minutes']))}"></label>
          <label style="display:flex; align-items:center; gap:8px;"><input type="checkbox" name="enabled" {"checked" if letterboxd_settings["enabled"] else ""}> Otomatik calissin</label>
          <button>Ayar kaydet</button>
        </form>
        <form method="post" action="/letterboxd/sync">
          <button class="secondary">Simdi sync et</button>
        </form>
      </div>
    </section>

    <section class="card wide" style="margin-top:16px">
      <h2>FatSecret kalori ozeti</h2>
      {render_fatsecret_summary(fatsecret_summary)}
    </section>

    <section class="card wide" style="margin-top:16px">
      <h2>FatSecret ogun kayitlari</h2>
      {render_fatsecret_entries(fatsecret_entries)}
    </section>

    <section class="card wide" style="margin-top:16px">
      <h2>Goodreads kutuphanesi</h2>
      <form class="inline" method="get" action="/">
        <input type="text" name="q" value="{html.escape(search)}" placeholder="Kitap veya yazar ara">
        <select name="shelf">{options}</select>
        <button>Filtrele</button>
      </form>
      {render_books_table(books)}
    </section>

    <section class="card wide" style="margin-top:16px">
      <h2>Letterboxd filmleri</h2>
      {render_letterboxd_table(letterboxd_films)}
    </section>
  </main>
</body>
</html>"""

def multipart_file(body: bytes, content_type: str) -> str:
    marker = "boundary="
    if marker not in content_type:
        raise ValueError("multipart boundary bulunamadi")
    boundary = ("--" + content_type.split(marker, 1)[1]).encode()
    for part in body.split(boundary):
        if b'Content-Disposition:' not in part or b'name="file"' not in part:
            continue
        _, _, content = part.partition(b"\r\n\r\n")
        content = content.rsplit(b"\r\n", 1)[0]
        return content.decode("utf-8-sig")
    raise ValueError("file alani bulunamadi")
