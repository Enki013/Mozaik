import base64
import hashlib
import hmac
import json
import secrets
import time
import urllib.parse
import urllib.request

from personal_records.config import (
    FATSECRET_API_BASE,
    FATSECRET_OAUTH1_ACCESS_TOKEN_URL,
    FATSECRET_OAUTH1_AUTHORIZE_URL,
    FATSECRET_OAUTH1_REQUEST_TOKEN_URL,
    FATSECRET_OAUTH2_TOKEN_URL,
    env,
)

def now_nonce() -> str:
    return secrets.token_urlsafe(18)

def percent(value: str) -> str:
    return urllib.parse.quote(str(value), safe="~-._")

def oauth1_parameters(method: str, url: str, params: dict[str, str], token_secret: str = "") -> dict[str, str]:
    consumer_secret = env("FATSECRET_CONSUMER_SECRET")
    oauth_params = {
        "oauth_consumer_key": env("FATSECRET_CONSUMER_KEY"),
        "oauth_nonce": now_nonce(),
        "oauth_signature_method": "HMAC-SHA1",
        "oauth_timestamp": str(int(time.time())),
        "oauth_version": "1.0",
    }
    for key, value in params.items():
        if key.startswith("oauth_"):
            oauth_params[key] = value
    all_params = {**params, **oauth_params}
    normalized = "&".join(
        f"{percent(k)}={percent(v)}" for k, v in sorted(all_params.items())
    )
    base = "&".join([method.upper(), percent(url), percent(normalized)])
    signing_key = f"{percent(consumer_secret)}&{percent(token_secret)}"
    signature = base64.b64encode(
        hmac.new(signing_key.encode(), base.encode(), hashlib.sha1).digest()
    ).decode()
    oauth_params["oauth_signature"] = signature
    return {**params, **oauth_params}

def oauth1_header(method: str, url: str, params: dict[str, str], token_secret: str = "") -> str:
    oauth_params = oauth1_parameters(method, url, params, token_secret)
    oauth_params = {k: v for k, v in oauth_params.items() if k.startswith("oauth_")}
    return "OAuth " + ", ".join(
        f'{percent(k)}="{percent(v)}"' for k, v in sorted(oauth_params.items())
    )

def http_form_request(method: str, url: str, form: dict[str, str], headers: dict[str, str]) -> bytes:
    data = urllib.parse.urlencode(form).encode()
    request_url = url
    request_data = data if method == "POST" else None
    if method == "GET" and form:
        request_url += "?" + urllib.parse.urlencode(form)
    req = urllib.request.Request(request_url, data=request_data, headers=headers, method=method)
    with urllib.request.urlopen(req, timeout=25) as resp:
        return resp.read()

def fatsecret_oauth2_token() -> dict:
    client_id = env("FATSECRET_CLIENT_ID")
    client_secret = env("FATSECRET_CLIENT_SECRET")
    if not client_id or not client_secret:
        raise RuntimeError("FATSECRET_CLIENT_ID ve FATSECRET_CLIENT_SECRET gerekli.")
    auth = base64.b64encode(f"{client_id}:{client_secret}".encode()).decode()
    body = http_form_request(
        "POST",
        FATSECRET_OAUTH2_TOKEN_URL,
        {"grant_type": "client_credentials", "scope": "basic"},
        {"Authorization": f"Basic {auth}", "Content-Type": "application/x-www-form-urlencoded"},
    )
    return json.loads(body.decode())

def fatsecret_public_search(query: str) -> dict:
    token = fatsecret_oauth2_token()["access_token"]
    form = {"method": "foods.search", "search_expression": query, "format": "json"}
    body = http_form_request(
        "POST",
        "https://platform.fatsecret.com/rest/server.api",
        form,
        {"Authorization": f"Bearer {token}", "Content-Type": "application/x-www-form-urlencoded"},
    )
    return json.loads(body.decode())

def fatsecret_signed_get(url: str, params: dict[str, str], token: str, token_secret: str) -> dict:
    auth_params = {**params, "oauth_token": token}
    signed_params = oauth1_parameters("GET", url, auth_params, token_secret)
    full_url = url + "?" + urllib.parse.urlencode(signed_params)
    req = urllib.request.Request(full_url, method="GET")
    with urllib.request.urlopen(req, timeout=25) as resp:
        return json.loads(resp.read().decode())

def parse_query_string(data: bytes) -> dict[str, str]:
    parsed = urllib.parse.parse_qs(data.decode())
    return {k: v[0] for k, v in parsed.items()}
