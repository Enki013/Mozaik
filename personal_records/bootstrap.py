import threading
import time
from http.server import ThreadingHTTPServer

from personal_records.application.goodreads_service import (
    get_goodreads_sync_settings,
    run_configured_goodreads_sync,
)
from personal_records.application.letterboxd_service import (
    get_letterboxd_sync_settings,
    run_configured_letterboxd_sync,
)
from personal_records.config import PORT, load_dotenv
from personal_records.infrastructure.database import init_db
from personal_records.presentation.http_server import Handler


def sync_loop() -> None:
    while True:
        settings = get_goodreads_sync_settings()
        letterboxd_settings = get_letterboxd_sync_settings()
        interval = min(
            max(15, int(settings["interval_minutes"] or 360)),
            max(15, int(letterboxd_settings["interval_minutes"] or 360)),
        )
        if settings["enabled"] and settings["feed_url"]:
            try:
                run_configured_goodreads_sync()
            except Exception:
                pass
        if letterboxd_settings["enabled"] and letterboxd_settings["feed_url"]:
            try:
                run_configured_letterboxd_sync()
            except Exception:
                pass
        time.sleep(interval * 60)


def run_server() -> None:
    load_dotenv()
    init_db()
    threading.Thread(target=sync_loop, daemon=True).start()
    server = ThreadingHTTPServer(("127.0.0.1", PORT), Handler)
    print(f"Running at http://localhost:{PORT}")
    server.serve_forever()
