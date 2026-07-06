import html
import json
import time
import urllib.parse
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler

from personal_records.application.fatsecret_service import sync_fatsecret_food_entries
from personal_records.application.goodreads_service import (
    import_goodreads_csv,
    run_configured_goodreads_sync,
    save_goodreads_sync_settings,
)
from personal_records.application.letterboxd_service import (
    import_letterboxd_csv,
    run_configured_letterboxd_sync,
    save_letterboxd_sync_settings,
)
from personal_records.config import (
    FATSECRET_OAUTH1_ACCESS_TOKEN_URL,
    FATSECRET_OAUTH1_AUTHORIZE_URL,
    FATSECRET_OAUTH1_REQUEST_TOKEN_URL,
    PORT,
    env,
)
from personal_records.infrastructure.database import db
from personal_records.integrations.fatsecret_client import (
    fatsecret_public_search,
    http_form_request,
    oauth1_parameters,
    parse_query_string,
)
from personal_records.presentation.api import (
    api_dashboard_summary,
    api_fatsecret_entries,
    api_fatsecret_summary,
    api_goodreads_books,
    api_letterboxd_films,
)
from personal_records.presentation.html_views import html_page, multipart_file
from personal_records.shared import to_int

class Handler(BaseHTTPRequestHandler):
    def send(self, status: int, body: str | bytes, content_type: str = "text/html; charset=utf-8") -> None:
        payload = body.encode() if isinstance(body, str) else body
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    def send_json(self, status: int, payload: dict | list) -> None:
        self.send(
            status,
            json.dumps(payload, ensure_ascii=False, indent=2),
            "application/json; charset=utf-8",
        )

    def redirect(self, location: str) -> None:
        self.send_response(HTTPStatus.SEE_OTHER)
        self.send_header("Location", location)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.end_headers()

    def do_OPTIONS(self) -> None:
        self.send_response(HTTPStatus.NO_CONTENT)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.end_headers()

    def stats(self) -> dict:
        with db() as conn:
            goodreads_count = conn.execute("select count(*) c from goodreads_books").fetchone()["c"]
            read_count = conn.execute("select count(*) c from goodreads_books where exclusive_shelf = 'read'").fetchone()["c"]
            to_read_count = conn.execute("select count(*) c from goodreads_books where exclusive_shelf = 'to-read'").fetchone()["c"]
            rated_count = conn.execute("select count(*) c from goodreads_books where coalesce(my_rating, 0) > 0").fetchone()["c"]
            letterboxd_count = conn.execute("select count(*) c from letterboxd_films").fetchone()["c"]
            letterboxd_rated_count = conn.execute("select count(*) c from letterboxd_films where coalesce(rating, 0) > 0").fetchone()["c"]
        return {
            "goodreads_count": goodreads_count,
            "read_count": read_count,
            "to_read_count": to_read_count,
            "rated_count": rated_count,
            "letterboxd_count": letterboxd_count,
            "letterboxd_rated_count": letterboxd_rated_count,
        }

    def do_GET(self) -> None:
        parsed = urllib.parse.urlparse(self.path)
        query = urllib.parse.parse_qs(parsed.query)
        try:
            if parsed.path == "/api/health":
                self.send_json(200, {"ok": True})
            elif parsed.path == "/api/dashboard":
                self.send_json(200, api_dashboard_summary())
            elif parsed.path == "/api/fatsecret/food-entries":
                limit = to_int(query.get("limit", ["100"])[0]) or 100
                self.send_json(200, {"items": api_fatsecret_entries(limit)})
            elif parsed.path == "/api/fatsecret/daily-summary":
                limit = to_int(query.get("limit", ["30"])[0]) or 30
                self.send_json(200, {"items": api_fatsecret_summary(limit)})
            elif parsed.path == "/api/goodreads/books":
                limit = to_int(query.get("limit", ["100"])[0]) or 100
                self.send_json(
                    200,
                    {
                        "items": api_goodreads_books(
                            query.get("q", [""])[0],
                            query.get("shelf", [""])[0],
                            limit,
                        )
                    },
                )
            elif parsed.path == "/api/letterboxd/films":
                limit = to_int(query.get("limit", ["100"])[0]) or 100
                self.send_json(200, {"items": api_letterboxd_films(limit)})
            elif parsed.path == "/api/sync/status":
                self.send_json(200, api_dashboard_summary()["last_sync"])
            elif parsed.path == "/":
                self.send(200, html_page(self.stats(), query.get("q", [""])[0], query.get("shelf", [""])[0]))
            elif parsed.path == "/goodreads/export":
                with db() as conn:
                    rows = conn.execute("select * from goodreads_books order by title").fetchall()
                payload = json.dumps([dict(row) for row in rows], ensure_ascii=False, indent=2)
                self.send(200, payload, "application/json; charset=utf-8")
            elif parsed.path == "/fatsecret/search":
                result = fatsecret_public_search(query.get("q", ["toast"])[0])
                self.send(200, "<pre>" + html.escape(json.dumps(result, indent=2, ensure_ascii=False)) + "</pre>")
            elif parsed.path == "/fatsecret/connect":
                if not env("FATSECRET_CONSUMER_KEY") or not env("FATSECRET_CONSUMER_SECRET"):
                    raise RuntimeError("FATSECRET_CONSUMER_KEY ve FATSECRET_CONSUMER_SECRET gerekli.")
                callback = env("APP_BASE_URL") or f"http://localhost:{PORT}"
                params = {"oauth_callback": callback.rstrip("/") + "/fatsecret/callback"}
                signed_params = oauth1_parameters("POST", FATSECRET_OAUTH1_REQUEST_TOKEN_URL, params)
                body = http_form_request(
                    "POST",
                    FATSECRET_OAUTH1_REQUEST_TOKEN_URL,
                    signed_params,
                    {"Content-Type": "application/x-www-form-urlencoded"},
                )
                token_data = parse_query_string(body)
                with db() as conn:
                    conn.execute(
                        "insert or replace into oauth_temp_tokens (oauth_token, oauth_token_secret, created_at) values (?, ?, ?)",
                        (token_data["oauth_token"], token_data["oauth_token_secret"], int(time.time())),
                    )
                self.redirect(FATSECRET_OAUTH1_AUTHORIZE_URL + "?" + urllib.parse.urlencode({"oauth_token": token_data["oauth_token"]}))
            elif parsed.path == "/fatsecret/callback":
                oauth_token = query.get("oauth_token", [""])[0]
                verifier = query.get("oauth_verifier", [""])[0]
                if not oauth_token or not verifier:
                    raise RuntimeError("FatSecret callback eksik oauth_token veya oauth_verifier ile geldi.")
                with db() as conn:
                    temp = conn.execute(
                        "select oauth_token_secret from oauth_temp_tokens where oauth_token = ?", (oauth_token,)
                    ).fetchone()
                if not temp:
                    raise RuntimeError("Gecici OAuth token bulunamadi.")
                params = {"oauth_token": oauth_token, "oauth_verifier": verifier}
                signed_params = oauth1_parameters("GET", FATSECRET_OAUTH1_ACCESS_TOKEN_URL, params, temp["oauth_token_secret"])
                body = http_form_request(
                    "GET",
                    FATSECRET_OAUTH1_ACCESS_TOKEN_URL,
                    signed_params,
                    {},
                )
                token_data = parse_query_string(body)
                with db() as conn:
                    conn.execute(
                        "insert or replace into fatsecret_tokens (id, oauth_token, oauth_token_secret, updated_at) values (1, ?, ?, current_timestamp)",
                        (token_data["oauth_token"], token_data["oauth_token_secret"]),
                    )
                self.send(200, "<p>FatSecret baglandi. Artik diary sync endpointleri eklenebilir.</p><p><a href='/'>Geri don</a></p>")
            else:
                self.send(404, "Not found", "text/plain; charset=utf-8")
        except Exception as exc:
            if parsed.path.startswith("/api/"):
                self.send_json(500, {"error": str(exc)})
            else:
                self.send(500, f"<h1>Hata</h1><pre>{html.escape(str(exc))}</pre><p><a href='/'>Geri don</a></p>")

    def do_POST(self) -> None:
        try:
            parsed = urllib.parse.urlparse(self.path)
            length = int(self.headers.get("Content-Length", "0"))
            body = self.rfile.read(length)
            if parsed.path == "/api/sync/fatsecret":
                data = json.loads(body.decode() or "{}")
                days = to_int(str(data.get("days", "1"))) or 1
                self.send_json(200, sync_fatsecret_food_entries(days))
            elif parsed.path == "/api/sync/goodreads":
                self.send_json(200, run_configured_goodreads_sync())
            elif parsed.path == "/api/sync/letterboxd":
                self.send_json(200, run_configured_letterboxd_sync())
            elif parsed.path == "/goodreads/import":
                text = multipart_file(body, self.headers.get("Content-Type", ""))
                result = import_goodreads_csv(text)
                self.send(
                    200,
                    f"<p>Goodreads import tamam: {result['inserted']} yeni, {result['updated']} guncel.</p><p><a href='/'>Geri don</a></p>",
                )
            elif parsed.path == "/goodreads/settings":
                data = urllib.parse.parse_qs(body.decode())
                save_goodreads_sync_settings(
                    data.get("feed_url", [""])[0],
                    to_int(data.get("interval_minutes", ["360"])[0]) or 360,
                    "enabled" in data,
                )
                self.redirect("/")
            elif parsed.path == "/goodreads/sync":
                result = run_configured_goodreads_sync()
                self.send(
                    200,
                    f"<p>Goodreads sync tamam: {result['inserted']} yeni, {result['updated']} guncel.</p><p><a href='/'>Geri don</a></p>",
                )
            elif parsed.path == "/fatsecret/sync":
                data = urllib.parse.parse_qs(body.decode())
                days = to_int(data.get("days", ["1"])[0]) or 1
                result = sync_fatsecret_food_entries(days)
                self.send(
                    200,
                    f"<p>FatSecret yemek gunlugu sync tamam: {result['inserted']} yeni, {result['updated']} guncel.</p><p><a href='/'>Geri don</a></p>",
                )
            elif parsed.path == "/letterboxd/import":
                text = multipart_file(body, self.headers.get("Content-Type", ""))
                result = import_letterboxd_csv(text)
                self.send(
                    200,
                    f"<p>Letterboxd import tamam: {result['inserted']} yeni, {result['updated']} guncel.</p><p><a href='/'>Geri don</a></p>",
                )
            elif parsed.path == "/letterboxd/settings":
                data = urllib.parse.parse_qs(body.decode())
                save_letterboxd_sync_settings(
                    data.get("feed_url", [""])[0],
                    to_int(data.get("interval_minutes", ["360"])[0]) or 360,
                    "enabled" in data,
                )
                self.redirect("/")
            elif parsed.path == "/letterboxd/sync":
                result = run_configured_letterboxd_sync()
                self.send(
                    200,
                    f"<p>Letterboxd sync tamam: {result['inserted']} yeni, {result['updated']} guncel.</p><p><a href='/'>Geri don</a></p>",
                )
            else:
                self.send(404, "Not found", "text/plain; charset=utf-8")
        except Exception as exc:
            if parsed.path.startswith("/api/"):
                self.send_json(500, {"error": str(exc)})
            else:
                self.send(500, f"<h1>Hata</h1><pre>{html.escape(str(exc))}</pre><p><a href='/'>Geri don</a></p>")
