import html
import re
import xml.etree.ElementTree as ET
from datetime import date, timedelta
import sqlite3

def goodreads_row_value(row: dict[str, str], *names: str) -> str:
    lowered = {k.lower().strip(): v for k, v in row.items()}
    for name in names:
        if name.lower() in lowered:
            return lowered[name.lower()].strip()
    return ""

def to_int(value: str):
    try:
        return int(value) if value else None
    except ValueError:
        return None

def to_float(value: str):
    try:
        return float(value) if value else None
    except ValueError:
        return None

def today_date_int() -> int:
    return (date.today() - date(1970, 1, 1)).days

def date_int_to_iso(value: int | str | None) -> str:
    if value in (None, ""):
        return ""
    return (date(1970, 1, 1) + timedelta(days=int(value))).isoformat()

def text_of(node: ET.Element, tag: str) -> str:
    child = node.find(tag)
    return (child.text or "").strip() if child is not None else ""

def clean_html_text(value: str) -> str:
    return re.sub(r"<[^>]+>", " ", html.unescape(value or "")).strip()

def row_dict(row: sqlite3.Row) -> dict:
    return {key: row[key] for key in row.keys()}
