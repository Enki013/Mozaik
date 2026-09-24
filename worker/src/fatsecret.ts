import crypto from "node:crypto";

function percentEncode(str: string): string {
  return encodeURIComponent(str).replace(/[!'()*]/g, (c) => `%${c.charCodeAt(0).toString(16).toUpperCase()}`);
}

export function oauth1Sign(
  method: string,
  url: string,
  params: Record<string, string>,
  consumerKey: string,
  consumerSecret: string,
  token: string,
  tokenSecret: string
): Record<string, string> {
  const oauth: Record<string, string> = {
    oauth_consumer_key: consumerKey,
    oauth_nonce: Array.from(crypto.getRandomValues(new Uint8Array(16))).map((b) => b.toString(16).padStart(2, "0")).join(""),
    oauth_signature_method: "HMAC-SHA1",
    oauth_timestamp: Math.floor(Date.now() / 1000).toString(),
    oauth_version: "1.0",
    ...params,
  };
  if (token) {
    oauth.oauth_token = token;
  }

  const sortedKeys = Object.keys(oauth).sort();
  const paramString = sortedKeys
    .map((k) => `${percentEncode(k)}=${percentEncode(oauth[k])}`)
    .join("&");

  const baseString = [
    method.toUpperCase(),
    percentEncode(url),
    percentEncode(paramString),
  ].join("&");

  const signingKey = `${percentEncode(consumerSecret)}&${percentEncode(tokenSecret || "")}`;
  const signature = crypto
    .createHmac("sha1", signingKey)
    .update(baseString)
    .digest("base64");

  return { ...oauth, oauth_signature: signature };
}

export function getTurkeyDateInt(): number {
  const now = new Date();
  // Turkey is permanently UTC+3 (no DST changes)
  const tr = new Date(now.getTime() + 3 * 3600 * 1000);
  return Math.floor(Date.UTC(tr.getUTCFullYear(), tr.getUTCMonth(), tr.getUTCDate()) / 86400000);
}

export async function getFatSecretCredentials(
  db: D1Database,
  env?: { FATSECRET_CONSUMER_KEY?: string; FATSECRET_CONSUMER_SECRET?: string }
): Promise<{ consumerKey: string; consumerSecret: string }> {
  let key = env?.FATSECRET_CONSUMER_KEY?.trim() || "";
  let secret = env?.FATSECRET_CONSUMER_SECRET?.trim() || "";

  if (!key || !secret) {
    const rows = await db
      .prepare("SELECT key, value FROM user_settings WHERE key IN ('fatsecret_consumer_key', 'fatsecret_consumer_secret')")
      .all<{ key: string; value: string }>();

    for (const r of rows.results || []) {
      if (r.key === "fatsecret_consumer_key" && !key) key = r.value?.trim() || "";
      if (r.key === "fatsecret_consumer_secret" && !secret) secret = r.value?.trim() || "";
    }
  }

  return { consumerKey: key, consumerSecret: secret };
}

export async function startFatSecretConnect(
  db: D1Database,
  consumerKey: string,
  consumerSecret: string,
  callbackUrl: string
): Promise<string> {
  const requestTokenUrl = "https://authentication.fatsecret.com/oauth/request_token";
  const params: Record<string, string> = {
    oauth_callback: callbackUrl,
  };

  const signed = oauth1Sign(
    "POST",
    requestTokenUrl,
    params,
    consumerKey,
    consumerSecret,
    "",
    ""
  );

  const formBody = new URLSearchParams(signed).toString();
  const resp = await fetch(requestTokenUrl, {
    method: "POST",
    headers: {
      "Content-Type": "application/x-www-form-urlencoded",
      "User-Agent": "PersonalRecords/1.0",
    },
    body: formBody,
  });

  const text = await resp.text();
  if (!resp.ok) {
    throw new Error(`FatSecret request token failed (HTTP ${resp.status}): ${text}`);
  }

  const values = new URLSearchParams(text);
  const oauthToken = values.get("oauth_token");
  const oauthTokenSecret = values.get("oauth_token_secret");

  if (!oauthToken || !oauthTokenSecret) {
    throw new Error("FatSecret response missing oauth_token or oauth_token_secret: " + text);
  }

  const nowSec = Math.floor(Date.now() / 1000);
  await db
    .prepare("INSERT OR REPLACE INTO oauth_temp_tokens (oauth_token, oauth_token_secret, created_at) VALUES (?, ?, ?)")
    .bind(oauthToken, oauthTokenSecret, nowSec)
    .run();

  return `https://authentication.fatsecret.com/oauth/authorize?oauth_token=${encodeURIComponent(oauthToken)}`;
}

export async function finishFatSecretConnect(
  db: D1Database,
  consumerKey: string,
  consumerSecret: string,
  oauthToken: string,
  oauthVerifier: string
): Promise<void> {
  const temp = await db
    .prepare("SELECT oauth_token_secret FROM oauth_temp_tokens WHERE oauth_token = ?")
    .bind(oauthToken)
    .first<{ oauth_token_secret: string }>();

  if (!temp?.oauth_token_secret) {
    throw new Error("Geçici OAuth token bulunamadı veya süresi doldu");
  }

  const accessTokenUrl = "https://authentication.fatsecret.com/oauth/access_token";
  const params: Record<string, string> = {
    oauth_verifier: oauthVerifier,
  };

  const signed = oauth1Sign(
    "GET",
    accessTokenUrl,
    params,
    consumerKey,
    consumerSecret,
    oauthToken,
    temp.oauth_token_secret
  );

  const queryUrl = `${accessTokenUrl}?${new URLSearchParams(signed).toString()}`;
  const resp = await fetch(queryUrl, {
    method: "GET",
    headers: {
      "User-Agent": "PersonalRecords/1.0",
    },
  });

  const text = await resp.text();
  if (!resp.ok) {
    throw new Error(`FatSecret access token failed (HTTP ${resp.status}): ${text}`);
  }

  const values = new URLSearchParams(text);
  const accessToken = values.get("oauth_token");
  const accessSecret = values.get("oauth_token_secret");

  if (!accessToken || !accessSecret) {
    throw new Error("FatSecret response missing access token: " + text);
  }

  await db
    .prepare("INSERT OR REPLACE INTO fatsecret_tokens (id, oauth_token, oauth_token_secret, updated_at) VALUES (1, ?, ?, current_timestamp)")
    .bind(accessToken, accessSecret)
    .run();

  await db
    .prepare("DELETE FROM oauth_temp_tokens WHERE oauth_token = ?")
    .bind(oauthToken)
    .run();
}

export async function syncFatSecretFoods(
  db: D1Database,
  consumerKey: string,
  consumerSecret: string,
  days: number = 3
): Promise<{ inserted: number; updated: number; message: string }> {
  if (!consumerKey || !consumerSecret) {
    return { inserted: 0, updated: 0, message: "FATSECRET_CONSUMER_KEY veya SECRET tanımlı değil" };
  }

  const tokenRow = await db
    .prepare("SELECT oauth_token, oauth_token_secret FROM fatsecret_tokens WHERE id = 1")
    .first<{ oauth_token: string; oauth_token_secret: string }>();

  if (!tokenRow?.oauth_token || !tokenRow?.oauth_token_secret) {
    return { inserted: 0, updated: 0, message: "FatSecret hesabı bağlı değil (token bulunamadı)" };
  }

  if (days <= 0) days = 3;
  if (days > 1825) days = 1825; // Cap at 5 years

  // Use Turkey timezone dateInt to accurately match user and FatSecret dates
  const today = getTurkeyDateInt();
  const start = today - days + 1;

  // Retrieve existing scanned days to skip past days already scanned
  const scannedRows = await db
    .prepare("SELECT date_int FROM fatsecret_scanned_days")
    .all<{ date_int: number }>();
  const scannedDays = new Set<number>((scannedRows.results || []).map((r) => r.date_int));

  let totalInserted = 0;
  let totalUpdated = 0;
  let scannedCount = 0;
  let skippedCount = 0;

  const endpoint = "https://platform.fatsecret.com/rest/food-entries/v2";

  // Scan in reverse order: today and yesterday first, then back into history
  for (let d = today; d >= start; d--) {
    const isRecent = d >= today - 1; // Today and yesterday can change, always sync them
    if (!isRecent && scannedDays.has(d)) {
      skippedCount++;
      continue;
    }

    // Safety guard against Cloudflare Worker subrequest limit (Free tier limit is 50 subrequests)
    if (scannedCount >= 40) {
      console.log("Reached batch limit of 40 subrequests, finishing current sync");
      break;
    }

    const params: Record<string, string> = {
      date: d.toString(),
      format: "json",
    };

    const signed = oauth1Sign(
      "GET",
      endpoint,
      params,
      consumerKey,
      consumerSecret,
      tokenRow.oauth_token,
      tokenRow.oauth_token_secret
    );

    const queryUrl = `${endpoint}?${new URLSearchParams(signed).toString()}`;

    try {
      const resp = await fetch(queryUrl, {
        headers: { "User-Agent": "Mozilla/5.0 (PersonalRecords-Worker)" },
      });

      if (!resp.ok) {
        console.error(`FatSecret sync failed for dateInt ${d}: ${resp.status}`);
        continue;
      }

      const data = await resp.json<any>();
      const rawEntries = data?.food_entries?.food_entry;
      const entries = Array.isArray(rawEntries) ? rawEntries : rawEntries ? [rawEntries] : [];

      for (const entry of entries) {
        const id = String(entry.food_entry_id);
        const name = String(entry.food_entry_name || "");
        const meal = String(entry.meal || "");
        const desc = String(entry.food_entry_description || "");
        const cals = parseFloat(entry.calories) || 0;
        const carbs = parseFloat(entry.carbohydrate) || 0;
        const prot = parseFloat(entry.protein) || 0;
        const fat = parseFloat(entry.fat) || 0;
        const entryDateInt = parseInt(entry.date_int, 10) || d;

        const existing = await db
          .prepare("SELECT food_entry_id FROM fatsecret_food_entries WHERE food_entry_id = ?")
          .bind(id)
          .first();

        await db
          .prepare(`
            INSERT OR REPLACE INTO fatsecret_food_entries (
              food_entry_id, date_int, meal, food_entry_name, food_entry_description,
              calories, carbohydrate, protein, fat, raw_json, synced_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, current_timestamp)
          `)
          .bind(id, entryDateInt, meal, name, desc, cals, carbs, prot, fat, JSON.stringify(entry))
          .run();

        if (existing) totalUpdated++;
        else totalInserted++;
      }

      await db
        .prepare("INSERT OR REPLACE INTO fatsecret_scanned_days (date_int, scanned_at) VALUES (?, current_timestamp)")
        .bind(d)
        .run();

      scannedDays.add(d);
      scannedCount++;
    } catch (err) {
      console.error(`Error syncing FatSecret for day ${d}:`, err);
    }
  }

  let msg = `${scannedCount} gün tarandı (${totalInserted} yeni, ${totalUpdated} güncellendi)`;
  if (skippedCount > 0) {
    msg += ` [${skippedCount} kayıtlı gün atlandı]`;
  }

  await db
    .prepare("INSERT INTO fatsecret_sync_runs (source, status, inserted, updated, message, ran_at) VALUES (?, 'ok', ?, ?, ?, current_timestamp)")
    .bind(`food_entries:${days}d`, totalInserted, totalUpdated, msg)
    .run();

  return { inserted: totalInserted, updated: totalUpdated, message: msg };
}
