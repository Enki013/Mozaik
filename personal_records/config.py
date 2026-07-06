from pathlib import Path
import os

ROOT = Path(__file__).resolve().parent.parent
DB_PATH = ROOT / "personal_records.sqlite3"
PORT = int(os.getenv("PORT", "8787"))

FATSECRET_OAUTH2_TOKEN_URL = "https://oauth.fatsecret.com/connect/token"
FATSECRET_API_BASE = "https://platform.fatsecret.com/rest"
FATSECRET_OAUTH1_REQUEST_TOKEN_URL = "https://authentication.fatsecret.com/oauth/request_token"
FATSECRET_OAUTH1_AUTHORIZE_URL = "https://authentication.fatsecret.com/oauth/authorize"
FATSECRET_OAUTH1_ACCESS_TOKEN_URL = "https://authentication.fatsecret.com/oauth/access_token"

def load_dotenv() -> None:
    env_path = ROOT / ".env"
    if not env_path.exists():
        return
    for raw_line in env_path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        os.environ.setdefault(key.strip(), value.strip().strip('"').strip("'"))

def env(name: str) -> str:
    return os.getenv(name, "").strip()
