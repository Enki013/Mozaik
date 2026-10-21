import { Hono } from "hono";
import { cors } from "hono/cors";

export interface Env {
  DB: D1Database;
  FATSECRET_CONSUMER_KEY?: string;
  FATSECRET_CONSUMER_SECRET?: string;
  LASTFM_API_KEY?: string;
  LASTFM_USERNAME?: string;
}

const app = new Hono<{ Bindings: Env }>();

app.use("*", cors({
  origin: "*",
  allowMethods: ["GET", "POST", "PUT", "DELETE", "OPTIONS"],
  allowHeaders: ["Content-Type", "Authorization"],
}));

// Date helpers
function dateIntToISO(dateInt: number): string {
  if (!dateInt || dateInt <= 0) return "";
  const d = new Date(dateInt * 86400000);
  return d.toISOString().split("T")[0];
}

function isoToDateInt(iso: string): number {
  const d = new Date(iso + "T00:00:00Z");
  return Math.floor(d.getTime() / 86400000);
}

import { renderDashboardHtml } from "./html";
import {
  syncFatSecretFoods,
  getTurkeyDateInt,
  getFatSecretCredentials,
  startFatSecretConnect,
  finishFatSecretConnect
} from "./fatsecret";

function todayDateInt(): number {
  return getTurkeyDateInt();
}

// 0. Web Dashboard
app.get("/", (c) => {
  return c.html(renderDashboardHtml());
});

// 1. Health
app.get("/api/health", async (c) => {
  try {
    await c.env.DB.prepare("SELECT 1").first();
    return c.json({ ok: true, d1: true });
  } catch (e: any) {
    return c.json({ ok: false, error: e.message }, 500);
  }
});

async function ensureHealthTable(db: D1Database) {
  await db.prepare(`
    CREATE TABLE IF NOT EXISTS health_records (
      id TEXT PRIMARY KEY,
      record_type TEXT NOT NULL,
      category TEXT NOT NULL,
      date_iso TEXT NOT NULL,
      start_time TEXT NOT NULL,
      end_time TEXT NOT NULL,
      value_primary REAL,
      value_secondary REAL,
      value_tertiary REAL,
      unit TEXT,
      title TEXT,
      subtitle TEXT,
      data_origin TEXT,
      raw_json TEXT NOT NULL,
      synced_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
    )
  `).run();
}

async function ensureMusicTables(db: D1Database) {
  await db.prepare(`
    CREATE TABLE IF NOT EXISTS music_scrobbles (
      id TEXT PRIMARY KEY,
      track TEXT NOT NULL,
      artist TEXT NOT NULL,
      album TEXT,
      album_artist TEXT,
      duration INTEGER,
      listened_at TEXT NOT NULL,
      uts INTEGER NOT NULL,
      source TEXT NOT NULL,
      origin_url TEXT,
      image_url TEXT,
      raw_json TEXT NOT NULL,
      created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
    )
  `).run();
  await db.prepare("CREATE INDEX IF NOT EXISTS idx_music_scrobbles_uts ON music_scrobbles(uts DESC)").run();
  await db.prepare("CREATE INDEX IF NOT EXISTS idx_music_scrobbles_artist ON music_scrobbles(artist)").run();
  await db.prepare("CREATE UNIQUE INDEX IF NOT EXISTS idx_music_scrobbles_unique ON music_scrobbles(artist, track, uts)").run();

  await db.prepare(`
    CREATE TABLE IF NOT EXISTS music_sync_settings (
      id INTEGER PRIMARY KEY CHECK (id = 1),
      lastfm_username TEXT,
      lastfm_api_key TEXT,
      interval_minutes INTEGER NOT NULL DEFAULT 360,
      enabled INTEGER NOT NULL DEFAULT 1,
      updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
    )
  `).run();

  await db.prepare(`
    CREATE TABLE IF NOT EXISTS music_sync_runs (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      source TEXT NOT NULL,
      status TEXT NOT NULL,
      inserted INTEGER NOT NULL DEFAULT 0,
      updated INTEGER NOT NULL DEFAULT 0,
      message TEXT,
      ran_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
    )
  `).run();
}

// 2. Dashboard
app.get("/api/dashboard", async (c) => {
  const db = c.env.DB;
  await ensureHealthTable(db);
  await ensureMusicTables(db);

  const [booksCount, filmsCount, foodsCount, notifCount, healthCount, musicCount] = await Promise.all([
    db.prepare("SELECT count(*) as c FROM goodreads_books").first<any>(),
    db.prepare("SELECT count(*) as c FROM letterboxd_films").first<any>(),
    db.prepare("SELECT count(*) as c FROM fatsecret_food_entries").first<any>(),
    db.prepare("SELECT count(*) as c FROM notifications").first<any>(),
    db.prepare("SELECT count(*) as c FROM health_records").first<any>(),
    db.prepare("SELECT count(*) as c FROM music_scrobbles").first<any>(),
  ]);

  const today = todayDateInt();
  const todayIso = dateIntToISO(today);
  const startOfTodayUts = Math.floor(new Date(todayIso + "T00:00:00+03:00").getTime() / 1000);
  const [todayFood, todayMusic] = await Promise.all([
    db
      .prepare("SELECT count(*) as count, coalesce(sum(calories), 0) as calories FROM fatsecret_food_entries WHERE date_int = ?")
      .bind(today)
      .first<any>(),
    db
      .prepare("SELECT count(*) as count FROM music_scrobbles WHERE uts >= ?")
      .bind(startOfTodayUts)
      .first<any>(),
  ]);

  const token = await db.prepare("SELECT id FROM fatsecret_tokens WHERE id = 1").first();
  const grSettings = await db.prepare("SELECT feed_url FROM goodreads_sync_settings WHERE id = 1").first<any>();
  const lbSettings = await db.prepare("SELECT feed_url FROM letterboxd_sync_settings WHERE id = 1").first<any>();
  const musicSettings = await db.prepare("SELECT lastfm_username FROM music_sync_settings WHERE id = 1").first<any>();

  const [fatRun, grRun, lbRun, musicRun] = await Promise.all([
    db.prepare("SELECT * FROM fatsecret_sync_runs ORDER BY id DESC LIMIT 1").first(),
    db.prepare("SELECT * FROM goodreads_sync_runs ORDER BY id DESC LIMIT 1").first(),
    db.prepare("SELECT * FROM letterboxd_sync_runs ORDER BY id DESC LIMIT 1").first(),
    db.prepare("SELECT * FROM music_sync_runs ORDER BY id DESC LIMIT 1").first(),
  ]);

  const calorieTargetRow = await db
    .prepare("SELECT value FROM user_settings WHERE key = 'daily_calorie_target'")
    .first<any>();
  const calorieTarget = calorieTargetRow ? parseInt(calorieTargetRow.value, 10) || 2200 : 2200;

  return c.json({
    counts: {
      goodreads_books: booksCount?.c || 0,
      letterboxd_films: filmsCount?.c || 0,
      fatsecret_food_entries: foodsCount?.c || 0,
      notifications: notifCount?.c || 0,
      health_records: healthCount?.c || 0,
      music_scrobbles: musicCount?.c || 0,
    },
    today: {
      date: todayIso,
      fatsecret_entry_count: todayFood?.count || 0,
      fatsecret_calories: Math.round((todayFood?.calories || 0) * 100) / 100,
      music_scrobbles: todayMusic?.count || 0,
    },
    connections: {
      fatsecret: !!token,
      goodreads_rss: !!(grSettings?.feed_url),
      letterboxd_rss: !!(lbSettings?.feed_url),
      health_connect: (healthCount?.c || 0) > 0,
      music: (musicCount?.c || 0) > 0 || !!(musicSettings?.lastfm_username),
    },
    last_sync: {
      fatsecret: fatRun || null,
      goodreads: grRun || null,
      letterboxd: lbRun || null,
      music: musicRun || null,
    },
    calorie_target: calorieTarget,
  });
});

// 3. Activity Timeline
app.get("/api/activity", async (c) => {
  const db = c.env.DB;
  const daysParam = c.req.query("days");
  const days = daysParam ? parseInt(daysParam, 10) : 7;
  const fromDate = c.req.query("from")?.trim();
  const toDate = c.req.query("to")?.trim();
  const singleDate = c.req.query("date")?.trim();
  const limit = Math.min(Math.max(parseInt(c.req.query("limit") || "50", 10), 1), 1000);

  const todayInt = todayDateInt();
  let minDateISO = "";
  let maxDateISO = "";

  if (singleDate) {
    minDateISO = singleDate;
    maxDateISO = singleDate;
  } else if (fromDate || toDate) {
    if (fromDate) minDateISO = fromDate;
    if (toDate) maxDateISO = toDate;
  } else if (days > 0) {
    minDateISO = dateIntToISO(todayInt - days + 1);
  }

  const allItems: any[] = [];

  // FatSecret entries
  const foods = await db
    .prepare("SELECT * FROM fatsecret_food_entries ORDER BY date_int DESC, food_entry_id DESC LIMIT ?")
    .bind(limit * 3)
    .all<any>();

  for (const f of foods.results) {
    const dISO = dateIntToISO(f.date_int);
    if (minDateISO && dISO < minDateISO) continue;
    if (maxDateISO && dISO > maxDateISO) continue;

    const timePart = f.synced_at ? f.synced_at.substring(11, 16) : "";
    allItems.push({
      id: "fs_" + f.food_entry_id,
      source: "fatsecret",
      type: "meal",
      date: dISO,
      time: timePart,
      timestamp: f.synced_at || "",
      title: f.food_entry_name,
      subtitle: f.meal,
      details: {
        food_entry_id: f.food_entry_id,
        meal: f.meal,
        description: f.food_entry_description,
        calories: f.calories,
        carbohydrate: f.carbohydrate,
        protein: f.protein,
        fat: f.fat,
      },
    });
  }

  // Goodreads books
  const books = await db
    .prepare("SELECT * FROM goodreads_books WHERE coalesce(date_read, '') <> '' OR coalesce(date_added, '') <> '' ORDER BY imported_at DESC LIMIT ?")
    .bind(limit * 3)
    .all<any>();

  for (const b of books.results) {
    let dISO = "";
    if (b.date_read) {
      const parsed = new Date(b.date_read);
      if (!isNaN(parsed.getTime())) dISO = parsed.toISOString().split("T")[0];
    }
    if (!dISO && b.date_added) {
      const parsed = new Date(b.date_added);
      if (!isNaN(parsed.getTime())) dISO = parsed.toISOString().split("T")[0];
    }
    if (!dISO && b.imported_at) {
      dISO = b.imported_at.substring(0, 10);
    }
    if (!dISO) continue;

    if (minDateISO && dISO < minDateISO) continue;
    if (maxDateISO && dISO > maxDateISO) continue;

    allItems.push({
      id: "gr_" + b.book_id,
      source: "goodreads",
      type: "book",
      date: dISO,
      time: "",
      timestamp: b.imported_at || "",
      title: b.title,
      subtitle: b.author,
      details: {
        book_id: b.book_id,
        author: b.author,
        my_rating: b.my_rating,
        average_rating: b.average_rating,
        number_of_pages: b.number_of_pages,
        exclusive_shelf: b.exclusive_shelf,
        image_url: b.image_url,
      },
    });
  }

  // Letterboxd films
  const films = await db
    .prepare("SELECT * FROM letterboxd_films ORDER BY coalesce(watched_date, diary_date, imported_at) DESC LIMIT ?")
    .bind(limit * 3)
    .all<any>();

  for (const flm of films.results) {
    let dISO = flm.watched_date || "";
    if (!dISO && flm.diary_date) {
      const parsed = new Date(flm.diary_date);
      if (!isNaN(parsed.getTime())) dISO = parsed.toISOString().split("T")[0];
    }
    if (!dISO && flm.imported_at) {
      dISO = flm.imported_at.substring(0, 10);
    }
    if (!dISO) continue;

    if (minDateISO && dISO < minDateISO) continue;
    if (maxDateISO && dISO > maxDateISO) continue;

    allItems.push({
      id: "lb_" + flm.film_key,
      source: "letterboxd",
      type: "film",
      date: dISO,
      time: "",
      timestamp: flm.imported_at || "",
      title: flm.name,
      subtitle: flm.year ? flm.year.toString() : "",
      details: {
        film_key: flm.film_key,
        year: flm.year,
        rating: flm.rating,
        rewatch: flm.rewatch,
        review: flm.review,
        image_url: flm.image_url,
        letterboxd_uri: flm.letterboxd_uri,
      },
    });
  }

  // Notifications
  const notifs = await db
    .prepare("SELECT * FROM notifications ORDER BY post_time DESC LIMIT ?")
    .bind(limit)
    .all<any>();

  for (const n of notifs.results) {
    const dISO = n.post_time ? n.post_time.substring(0, 10) : "";
    if (!dISO) continue;
    if (minDateISO && dISO < minDateISO) continue;
    if (maxDateISO && dISO > maxDateISO) continue;

    allItems.push({
      id: n.id,
      source: "notification",
      type: "notification",
      date: dISO,
      time: n.post_time.length >= 16 ? n.post_time.substring(11, 16) : "",
      timestamp: n.post_time,
      title: n.title ? `${n.app_name ? n.app_name + ": " : ""}${n.title}` : (n.app_name || n.package_name),
      subtitle: n.text || n.big_text || n.sub_text || "",
      details: {
        id: n.id,
        package_name: n.package_name,
        app_name: n.app_name,
        category: n.category,
        sub_text: n.sub_text,
        big_text: n.big_text,
        notification_key: n.notification_key,
        is_ongoing: n.is_ongoing === 1,
      },
    });
  }

  // Health Connect (Smart Timeline: Discrete Sleep/Exercise/Weight + Daily Activity Summary)
  await ensureHealthTable(db);
  const discreteHealth = await db
    .prepare("SELECT * FROM health_records WHERE record_type IN ('sleep', 'exercise', 'weight') ORDER BY date_iso DESC, start_time DESC LIMIT ?")
    .bind(limit)
    .all<any>();

  for (const h of discreteHealth.results) {
    const dISO = h.date_iso || "";
    if (!dISO) continue;
    if (minDateISO && dISO < minDateISO) continue;
    if (maxDateISO && dISO > maxDateISO) continue;

    allItems.push({
      id: h.id,
      source: "health_connect",
      type: "health",
      date: dISO,
      time: h.start_time && h.start_time.length >= 16 ? h.start_time.substring(11, 16) : "",
      timestamp: h.end_time || h.start_time || h.synced_at || "",
      title: h.title || h.record_type,
      subtitle: h.subtitle || "",
      details: {
        id: h.id,
        record_type: h.record_type,
        category: h.category,
        value_primary: h.value_primary,
        value_secondary: h.value_secondary,
        value_tertiary: h.value_tertiary,
        unit: h.unit,
        data_origin: h.data_origin,
      },
    });
  }

  const dailyHealthAgg = await db
    .prepare(`
      SELECT
        date_iso,
        coalesce(sum(CASE WHEN record_type = 'steps' THEN value_primary ELSE 0 END), 0) as total_steps,
        coalesce(sum(CASE WHEN record_type = 'active_calories' THEN value_primary ELSE 0 END), 0) as active_kcal,
        coalesce(sum(CASE WHEN record_type = 'distance' THEN value_primary ELSE 0 END), 0) as distance_m,
        coalesce(avg(CASE WHEN record_type IN ('heart_rate', 'resting_heart_rate') THEN value_primary ELSE NULL END), 0) as avg_bpm,
        max(end_time) as last_time
      FROM health_records
      WHERE record_type IN ('steps', 'active_calories', 'distance', 'heart_rate', 'resting_heart_rate')
      GROUP BY date_iso
      ORDER BY date_iso DESC
      LIMIT 60
    `)
    .all<any>();

  for (const d of dailyHealthAgg.results) {
    const dISO = d.date_iso || "";
    if (!dISO) continue;
    if (minDateISO && dISO < minDateISO) continue;
    if (maxDateISO && dISO > maxDateISO) continue;

    const steps = Math.round(Number(d.total_steps) || 0);
    const kcal = Math.round(Number(d.active_kcal) || 0);
    const distKm = Math.round(((Number(d.distance_m) || 0) / 1000) * 10) / 10;
    const avgBpm = Math.round(Number(d.avg_bpm) || 0);
    if (steps === 0 && kcal === 0 && distKm === 0 && avgBpm === 0) continue;

    const parts: string[] = [];
    if (kcal > 0) parts.push(`${kcal} kcal aktif`);
    if (distKm > 0) parts.push(`${distKm} km`);
    if (avgBpm > 0) parts.push(`Ort. ${avgBpm} bpm`);

    allItems.push({
      id: "hc_daily_" + dISO,
      source: "health_connect",
      type: "health",
      date: dISO,
      time: d.last_time && d.last_time.length >= 16 ? d.last_time.substring(11, 16) : "",
      timestamp: d.last_time || (dISO + "T23:59:00Z"),
      title: steps > 0 ? `Günlük Aktivite · ${steps.toLocaleString("tr-TR")} adım` : `Günlük Sağlık Özeti`,
      subtitle: parts.join(" · ") || "Health Connect",
      details: {
        record_type: "daily_summary",
        category: "activity",
        steps,
        active_calories: kcal,
        distance_km: distKm,
        avg_bpm: avgBpm,
      },
    });
  }

  // Music scrobbles
  await ensureMusicTables(db);
  const musicEntries = await db
    .prepare("SELECT * FROM music_scrobbles ORDER BY uts DESC LIMIT ?")
    .bind(limit * 3)
    .all<any>();

  for (const m of musicEntries.results) {
    const dISO = m.listened_at ? m.listened_at.substring(0, 10) : "";
    if (!dISO) continue;
    if (minDateISO && dISO < minDateISO) continue;
    if (maxDateISO && dISO > maxDateISO) continue;

    allItems.push({
      id: "music_" + m.id,
      source: "music",
      type: "music",
      date: dISO,
      time: m.listened_at && m.listened_at.length >= 16 ? m.listened_at.substring(11, 16) : "",
      timestamp: m.listened_at || "",
      title: m.track,
      subtitle: m.artist + (m.album ? ` • ${m.album}` : ""),
      details: {
        id: m.id,
        track: m.track,
        artist: m.artist,
        album: m.album,
        album_artist: m.album_artist,
        duration: m.duration,
        image_url: m.image_url,
        origin_url: m.origin_url,
        source: m.source,
        uts: m.uts,
      },
    });
  }

  // Sort all items descending by date and timestamp
  allItems.sort((a, b) => {
    if (a.date !== b.date) return b.date.localeCompare(a.date);
    return (b.timestamp || "").localeCompare(a.timestamp || "");
  });

  const slicedItems = allItems.slice(0, limit);

  // Group by date
  const groupsMap = new Map<string, any>();
  const todayISO = dateIntToISO(todayInt);
  const yesterdayISO = dateIntToISO(todayInt - 1);

  for (const item of slicedItems) {
    if (!groupsMap.has(item.date)) {
      let label = item.date;
      if (item.date === todayISO) label = "Bugün";
      else if (item.date === yesterdayISO) label = "Dün";

      groupsMap.set(item.date, {
        date: item.date,
        label,
        item_count: 0,
        summary: {
          total_calories: 0,
          total_protein: 0,
          total_carbs: 0,
          total_fat: 0,
          meals_count: 0,
          books_count: 0,
          films_count: 0,
        },
        items: [],
      });
    }

    const g = groupsMap.get(item.date);
    g.item_count++;
    g.items.push(item);

    if (item.type === "meal" && item.details) {
      g.summary.meals_count++;
      g.summary.total_calories += Number(item.details.calories) || 0;
      g.summary.total_protein += Number(item.details.protein) || 0;
      g.summary.total_carbs += Number(item.details.carbohydrate) || 0;
      g.summary.total_fat += Number(item.details.fat) || 0;
    } else if (item.type === "book") {
      g.summary.books_count++;
    } else if (item.type === "film") {
      g.summary.films_count++;
    }
  }

  // Round macro numbers
  for (const g of groupsMap.values()) {
    g.summary.total_calories = Math.round(g.summary.total_calories * 100) / 100;
    g.summary.total_protein = Math.round(g.summary.total_protein * 100) / 100;
    g.summary.total_carbs = Math.round(g.summary.total_carbs * 100) / 100;
    g.summary.total_fat = Math.round(g.summary.total_fat * 100) / 100;
  }

  return c.json({
    days,
    total: slicedItems.length,
    items: slicedItems,
    groups: Array.from(groupsMap.values()),
  });
});

// 4. FatSecret Food Entries
app.get("/api/fatsecret/food-entries", async (c) => {
  const limit = parseInt(c.req.query("limit") || "100", 10);
  const rows = await c.env.DB
    .prepare("SELECT * FROM fatsecret_food_entries ORDER BY date_int DESC, food_entry_id DESC LIMIT ?")
    .bind(limit)
    .all<any>();

  const items = rows.results.map((r) => ({
    ...r,
    date: dateIntToISO(r.date_int),
  }));

  return c.json({ items });
});

// 5. FatSecret Daily Summary
app.get("/api/fatsecret/daily-summary", async (c) => {
  const limit = parseInt(c.req.query("limit") || "30", 10);
  const rows = await c.env.DB
    .prepare(`
      SELECT date_int, count(*) as entry_count, coalesce(sum(calories), 0) as calories
      FROM fatsecret_food_entries
      GROUP BY date_int
      ORDER BY date_int DESC
      LIMIT ?
    `)
    .bind(limit)
    .all<any>();

  const items = rows.results.map((r) => ({
    date_int: r.date_int,
    date: dateIntToISO(r.date_int),
    entry_count: r.entry_count,
    calories: Math.round(r.calories * 100) / 100,
  }));

  return c.json({ items });
});

// 6. Goodreads Books
app.get("/api/goodreads/books", async (c) => {
  const q = c.req.query("q")?.trim();
  const shelf = c.req.query("shelf")?.trim();
  const limit = parseInt(c.req.query("limit") || "100", 10);

  let sql = "SELECT * FROM goodreads_books WHERE 1=1";
  const params: any[] = [];

  if (q) {
    sql += " AND (title LIKE ? OR author LIKE ?)";
    params.push(`%${q}%`, `%${q}%`);
  }
  if (shelf) {
    sql += " AND exclusive_shelf = ?";
    params.push(shelf);
  }

  sql += " ORDER BY coalesce(date_read, date_added, imported_at) DESC LIMIT ?";
  params.push(limit);

  const rows = await c.env.DB.prepare(sql).bind(...params).all<any>();
  return c.json({ items: rows.results });
});

// 7. Letterboxd Films
app.get("/api/letterboxd/films", async (c) => {
  const limit = parseInt(c.req.query("limit") || "100", 10);
  const rows = await c.env.DB
    .prepare("SELECT * FROM letterboxd_films ORDER BY coalesce(watched_date, diary_date, imported_at) DESC LIMIT ?")
    .bind(limit)
    .all<any>();

  return c.json({ items: rows.results });
});

// 8. Calorie Target
app.get("/api/settings/calorie-target", async (c) => {
  const row = await c.env.DB
    .prepare("SELECT value FROM user_settings WHERE key = 'daily_calorie_target'")
    .first<any>();
  const target = row ? parseInt(row.value, 10) || 2200 : 2200;
  return c.json({ target, calorie_target: target, success: true });
});

const updateCalorieTargetHandler = async (c: any) => {
  let target = 0;
  if (c.req.query("target")) {
    target = parseInt(c.req.query("target"), 10);
  } else {
    try {
      const body = await c.req.json();
      target = parseInt(body.target || body.calorie_target, 10);
    } catch (_) {}
  }

  if (target <= 0) target = 2200;

  await c.env.DB
    .prepare("INSERT OR REPLACE INTO user_settings (key, value, updated_at) VALUES ('daily_calorie_target', ?, current_timestamp)")
    .bind(target.toString())
    .run();

  return c.json({ target, calorie_target: target, success: true });
};

app.post("/settings/calorie-target", updateCalorieTargetHandler);
app.post("/api/settings/calorie-target", updateCalorieTargetHandler);

// 8.1. FatSecret Credentials Settings
app.get("/api/settings/fatsecret", async (c) => {
  const creds = await getFatSecretCredentials(c.env.DB, c.env);
  const tokenRow = await c.env.DB
    .prepare("SELECT oauth_token FROM fatsecret_tokens WHERE id = 1")
    .first<{ oauth_token: string }>();

  const maskedKey = creds.consumerKey
    ? (creds.consumerKey.length > 8
        ? creds.consumerKey.substring(0, 4) + "..." + creds.consumerKey.substring(creds.consumerKey.length - 4)
        : creds.consumerKey)
    : "";

  return c.json({
    has_keys: !!(creds.consumerKey && creds.consumerSecret),
    is_connected: !!tokenRow?.oauth_token,
    consumer_key: maskedKey,
    raw_consumer_key: creds.consumerKey || "",
  });
});

app.post("/api/settings/fatsecret", async (c) => {
  let body: any = {};
  try {
    body = await c.req.json();
  } catch (_) {}

  const key = String(body.consumer_key || "").trim();
  const secret = String(body.consumer_secret || "").trim();

  if (!key || !secret) {
    return c.json({ success: false, message: "Consumer Key ve Secret boş olamaz" }, 400);
  }

  await c.env.DB
    .prepare("INSERT OR REPLACE INTO user_settings (key, value, updated_at) VALUES ('fatsecret_consumer_key', ?, current_timestamp)")
    .bind(key)
    .run();

  await c.env.DB
    .prepare("INSERT OR REPLACE INTO user_settings (key, value, updated_at) VALUES ('fatsecret_consumer_secret', ?, current_timestamp)")
    .bind(secret)
    .run();

  return c.json({ success: true, message: "FatSecret anahtarları başarıyla kaydedildi" });
});

// 8b. Goodreads Sync Settings
app.get("/api/settings/goodreads", async (c) => {
  const row = await c.env.DB
    .prepare("SELECT feed_url, interval_minutes, enabled FROM goodreads_sync_settings WHERE id = 1")
    .first<any>();
  return c.json({
    feed_url: row?.feed_url || "",
    interval_minutes: row?.interval_minutes || 360,
    enabled: !!(row?.enabled)
  });
});

app.post("/api/settings/goodreads", async (c) => {
  let body: any = {};
  try {
    body = await c.req.json();
  } catch {
    body = await c.req.parseBody();
  }
  let feedUrl = String(body.feed_url || body.feedUrl || "").trim();
  const intervalMinutes = parseInt(String(body.interval_minutes || body.intervalMinutes || "360"), 10) || 360;
  const enabled = body.enabled === true || body.enabled === "true" || body.enabled === "1" || body.enabled === 1 ? 1 : 0;

  if (feedUrl && !feedUrl.startsWith("http://") && !feedUrl.startsWith("https://")) {
    const id = feedUrl.replace(/^@/, "").replace(/\/+$/, "");
    feedUrl = `https://www.goodreads.com/review/list_rss/${id}`;
  } else if (feedUrl.includes("goodreads.com/user/show/")) {
    const id = feedUrl.split("goodreads.com/user/show/")[1].split("?")[0].replace(/\/+$/, "");
    feedUrl = `https://www.goodreads.com/review/list_rss/${id}`;
  }

  await c.env.DB.prepare(`
    INSERT INTO goodreads_sync_settings (id, feed_url, interval_minutes, enabled, updated_at)
    VALUES (1, ?, ?, ?, CURRENT_TIMESTAMP)
    ON CONFLICT(id) DO UPDATE SET
      feed_url=excluded.feed_url,
      interval_minutes=excluded.interval_minutes,
      enabled=excluded.enabled,
      updated_at=CURRENT_TIMESTAMP
  `).bind(feedUrl, intervalMinutes, enabled).run();

  return c.json({ success: true, feed_url: feedUrl, message: "Goodreads ayarları kaydedildi" });
});

// 8c. Letterboxd Sync Settings
app.get("/api/settings/letterboxd", async (c) => {
  const row = await c.env.DB
    .prepare("SELECT feed_url, interval_minutes, enabled FROM letterboxd_sync_settings WHERE id = 1")
    .first<any>();
  return c.json({
    feed_url: row?.feed_url || "",
    interval_minutes: row?.interval_minutes || 360,
    enabled: !!(row?.enabled)
  });
});

app.post("/api/settings/letterboxd", async (c) => {
  let body: any = {};
  try {
    body = await c.req.json();
  } catch {
    body = await c.req.parseBody();
  }
  let feedUrl = String(body.feed_url || body.feedUrl || "").trim();
  const intervalMinutes = parseInt(String(body.interval_minutes || body.intervalMinutes || "360"), 10) || 360;
  const enabled = body.enabled === true || body.enabled === "true" || body.enabled === "1" || body.enabled === 1 ? 1 : 0;

  if (feedUrl && !feedUrl.startsWith("http://") && !feedUrl.startsWith("https://")) {
    const username = feedUrl.replace(/^@/, "").replace(/\/+$/, "");
    feedUrl = `https://letterboxd.com/${username}/rss/`;
  }

  await c.env.DB.prepare(`
    INSERT INTO letterboxd_sync_settings (id, feed_url, interval_minutes, enabled, updated_at)
    VALUES (1, ?, ?, ?, CURRENT_TIMESTAMP)
    ON CONFLICT(id) DO UPDATE SET
      feed_url=excluded.feed_url,
      interval_minutes=excluded.interval_minutes,
      enabled=excluded.enabled,
      updated_at=CURRENT_TIMESTAMP
  `).bind(feedUrl, intervalMinutes, enabled).run();

  return c.json({ success: true, feed_url: feedUrl, message: "Letterboxd ayarları kaydedildi" });
});

// 9. Sync Status
app.get("/api/sync/status", async (c) => {
  const [fatRun, grRun, lbRun] = await Promise.all([
    c.env.DB.prepare("SELECT * FROM fatsecret_sync_runs ORDER BY id DESC LIMIT 1").first(),
    c.env.DB.prepare("SELECT * FROM goodreads_sync_runs ORDER BY id DESC LIMIT 1").first(),
    c.env.DB.prepare("SELECT * FROM letterboxd_sync_runs ORDER BY id DESC LIMIT 1").first(),
  ]);

  return c.json({
    fatsecret: fatRun || null,
    goodreads: grRun || null,
    letterboxd: lbRun || null,
  });
});

// 10. Sync Goodreads RSS
app.post("/api/sync/goodreads", async (c) => {
  const settings = await c.env.DB
    .prepare("SELECT feed_url FROM goodreads_sync_settings WHERE id = 1")
    .first<any>();

  let feedUrl = String(settings?.feed_url || "").trim();
  if (!feedUrl) {
    return c.json({ inserted: 0, updated: 0, message: "Goodreads RSS URL kayitli degil" }, 400);
  }

  if (!feedUrl.startsWith("http://") && !feedUrl.startsWith("https://")) {
    const id = feedUrl.replace(/^@/, "").replace(/\/+$/, "");
    feedUrl = `https://www.goodreads.com/review/list_rss/${id}`;
  } else if (feedUrl.includes("goodreads.com/user/show/")) {
    const id = feedUrl.split("goodreads.com/user/show/")[1].split("?")[0].replace(/\/+$/, "");
    feedUrl = `https://www.goodreads.com/review/list_rss/${id}`;
  }

  try {
    const resp = await fetch(feedUrl, {
      headers: { "User-Agent": "Mozilla/5.0 (PersonalRecords-Worker)" },
    });
    if (!resp.ok) throw new Error(`HTTP ${resp.status}: ${resp.statusText}`);
    const xml = await resp.text();

    // Simple XML parse for <item> tags
    const itemRegex = /<item>([\s\S]*?)<\/item>/g;
    let match;
    let inserted = 0;
    let updated = 0;

    while ((match = itemRegex.exec(xml)) !== null) {
      const itemContent = match[1];
      const title = extractTag(itemContent, "title");
      const bookId = extractTag(itemContent, "book_id") || extractTag(itemContent, "guid") || hashStr(title);
      const author = extractTag(itemContent, "author_name");
      const userRating = parseInt(extractTag(itemContent, "user_rating") || "0", 10);
      const avgRating = parseFloat(extractTag(itemContent, "average_rating") || "0");
      const bookPublished = parseInt(extractTag(itemContent, "book_published") || "0", 10);
      const userReadAt = extractTag(itemContent, "user_read_at");
      const userDateAdded = extractTag(itemContent, "user_date_added");
      const userShelves = extractTag(itemContent, "user_shelves");
      const bookLargeImageUrl = extractTag(itemContent, "book_large_image_url") || extractTag(itemContent, "book_image_url");

      const existing = await c.env.DB.prepare("SELECT book_id FROM goodreads_books WHERE book_id = ?").bind(bookId).first();

      await c.env.DB.prepare(`
        INSERT OR REPLACE INTO goodreads_books (
          book_id, title, author, my_rating, average_rating, year_published,
          date_read, date_added, bookshelves, exclusive_shelf, image_url, raw_json, imported_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, current_timestamp)
      `).bind(
        bookId,
        title,
        author,
        userRating,
        avgRating,
        bookPublished,
        userReadAt,
        userDateAdded,
        userShelves,
        userShelves ? userShelves.split(",")[0].trim() : "read",
        bookLargeImageUrl,
        JSON.stringify({ title, author, bookId })
      ).run();

      if (existing) updated++;
      else inserted++;
    }

    await c.env.DB.prepare(`
      INSERT INTO goodreads_sync_runs (source, status, inserted, updated, message, ran_at)
      VALUES (?, 'ok', ?, ?, '', current_timestamp)
    `).bind(settings.feed_url, inserted, updated).run();

    return c.json({ inserted, updated, message: "Senkronizasyon tamamlandı" });
  } catch (e: any) {
    await c.env.DB.prepare(`
      INSERT INTO goodreads_sync_runs (source, status, inserted, updated, message, ran_at)
      VALUES (?, 'error', 0, 0, ?, current_timestamp)
    `).bind(settings.feed_url, e.message).run();
    return c.json({ inserted: 0, updated: 0, message: e.message }, 500);
  }
});

// 11. Sync Letterboxd RSS
app.post("/api/sync/letterboxd", async (c) => {
  const settings = await c.env.DB
    .prepare("SELECT feed_url FROM letterboxd_sync_settings WHERE id = 1")
    .first<any>();

  if (!settings?.feed_url) {
    return c.json({ inserted: 0, updated: 0, message: "Letterboxd RSS URL kayitli degil" }, 400);
  }

  try {
    const resp = await fetch(settings.feed_url, {
      headers: { "User-Agent": "Mozilla/5.0 (PersonalRecords-Worker)" },
    });
    if (!resp.ok) throw new Error(`HTTP ${resp.status}: ${resp.statusText}`);
    const xml = await resp.text();

    const itemRegex = /<item>([\s\S]*?)<\/item>/g;
    let match;
    let inserted = 0;
    let updated = 0;

    while ((match = itemRegex.exec(xml)) !== null) {
      const itemContent = match[1];
      const name = extractTag(itemContent, "letterboxd:filmTitle") || extractTag(itemContent, "title");
      const year = parseInt(extractTag(itemContent, "letterboxd:filmYear") || "0", 10);
      const uri = extractTag(itemContent, "link");
      const rating = parseFloat(extractTag(itemContent, "letterboxd:memberRating") || "0");
      const watchedDate = extractTag(itemContent, "letterboxd:watchedDate");
      const pubDate = extractTag(itemContent, "pubDate");
      const rewatch = extractTag(itemContent, "letterboxd:rewatch");

      // Extract image URL from description or CDATA
      let imageUrl = "";
      const imgMatch = itemContent.match(/<img[^>]+src="([^">]+)"/);
      if (imgMatch) imageUrl = imgMatch[1];

      // Try to find existing film by URI slug or name+year to prevent duplicates
      let filmKey = "";

      // 1. Match by Letterboxd URI film slug
      if (uri) {
        const slugMatch = uri.match(/\/film\/([^/]+)/);
        if (slugMatch) {
          const slug = slugMatch[1];
          const bySlug = await c.env.DB
            .prepare("SELECT film_key FROM letterboxd_films WHERE letterboxd_uri LIKE '%/film/' || ? || '/%' LIMIT 1")
            .bind(slug)
            .first<any>();
          if (bySlug?.film_key) filmKey = bySlug.film_key;
        }
      }

      // 2. Match by name + year
      if (!filmKey && name) {
        const byName = await c.env.DB
          .prepare("SELECT film_key FROM letterboxd_films WHERE lower(trim(name)) = lower(trim(?)) AND coalesce(year, -1) = ? LIMIT 1")
          .bind(name, year || -1)
          .first<any>();
        if (byName?.film_key) filmKey = byName.film_key;
      }

      // 3. Fallback: generate new key from guid or uri
      if (!filmKey) {
        filmKey = extractTag(itemContent, "guid") || uri || hashStr(name + year);
      }

      const existing = await c.env.DB.prepare("SELECT film_key FROM letterboxd_films WHERE film_key = ?").bind(filmKey).first();

      await c.env.DB.prepare(`
        INSERT INTO letterboxd_films (
          film_key, letterboxd_uri, name, year, rating, watched_date,
          diary_date, rewatch, image_url, source, raw_json, imported_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'rss', ?, current_timestamp)
        ON CONFLICT(film_key) DO UPDATE SET
          letterboxd_uri=coalesce(nullif(excluded.letterboxd_uri, ''), letterboxd_films.letterboxd_uri),
          name=excluded.name,
          year=coalesce(excluded.year, letterboxd_films.year),
          rating=CASE WHEN excluded.rating > 0 THEN excluded.rating ELSE letterboxd_films.rating END,
          watched_date=coalesce(nullif(excluded.watched_date, ''), letterboxd_films.watched_date),
          diary_date=coalesce(nullif(excluded.diary_date, ''), letterboxd_films.diary_date),
          rewatch=coalesce(nullif(excluded.rewatch, ''), letterboxd_films.rewatch),
          image_url=coalesce(nullif(excluded.image_url, ''), letterboxd_films.image_url),
          source=excluded.source,
          raw_json=excluded.raw_json,
          imported_at=current_timestamp
      `).bind(
        filmKey,
        uri,
        name,
        year,
        rating,
        watchedDate,
        pubDate,
        rewatch,
        imageUrl,
        JSON.stringify({ name, year, uri })
      ).run();

      if (existing) updated++;
      else inserted++;
    }

    await c.env.DB.prepare(`
      INSERT INTO letterboxd_sync_runs (source, status, inserted, updated, message, ran_at)
      VALUES (?, 'ok', ?, ?, '', current_timestamp)
    `).bind(settings.feed_url, inserted, updated).run();

    return c.json({ inserted, updated, message: "Senkronizasyon tamamlandı" });
  } catch (e: any) {
    await c.env.DB.prepare(`
      INSERT INTO letterboxd_sync_runs (source, status, inserted, updated, message, ran_at)
      VALUES (?, 'error', 0, 0, ?, current_timestamp)
    `).bind(settings.feed_url, e.message).run();
    return c.json({ inserted: 0, updated: 0, message: e.message }, 500);
  }
});

// 11b. Letterboxd CSV Import (ratings.csv / diary.csv / watched.csv)
app.get("/letterboxd/import", (c) => {
  return c.html(`<!DOCTYPE html>
<html lang="tr">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Letterboxd CSV İçe Aktar</title>
  <style>
    body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; background: #0b0f19; color: #f3f4f6; display: flex; align-items: center; justify-content: center; min-height: 100vh; margin: 0; padding: 20px; }
    .card { background: #161e2e; border: 1px solid #2e384d; border-radius: 16px; padding: 32px; max-width: 480px; width: 100%; box-shadow: 0 10px 25px rgba(0,0,0,0.5); }
    h1 { font-size: 20px; margin-bottom: 8px; color: #f59e0b; display: flex; align-items: center; gap: 8px; }
    p { color: #9ca3af; font-size: 14px; line-height: 1.5; margin-bottom: 20px; }
    .dropzone { border: 2px dashed #3b82f6; border-radius: 12px; padding: 24px; text-align: center; background: rgba(59,130,246,0.05); margin-bottom: 16px; }
    input[type=file] { margin: 12px 0; color: #9ca3af; font-size: 14px; }
    button { width: 100%; background: #3b82f6; color: white; border: none; padding: 12px; border-radius: 8px; font-weight: 600; cursor: pointer; }
    button:hover { background: #2563eb; }
    .info { font-size: 12px; color: #6b7280; margin-top: 16px; border-top: 1px solid #2e384d; padding-top: 12px; }
  </style>
</head>
<body>
  <div class="card">
    <h1>🎬 Letterboxd CSV İçe Aktar</h1>
    <p>Letterboxd hesabınızdan indirdiğiniz <code>ratings.csv</code> veya <code>diary.csv</code> dosyasını seçerek tüm geçmiş film puanlarınızı veritabanına aktarın.</p>
    <form method="post" action="/api/letterboxd/import" enctype="multipart/form-data">
      <div class="dropzone">
        <input type="file" name="file" accept=".csv" required>
      </div>
      <button type="submit">Puanları ve Filmleri İçe Aktar</button>
    </form>
    <div class="info">
      📌 <b>Dosyayı nasıl alırsınız?</b><br>
      Letterboxd web sitesinde profilinize gidin: <b>Settings &gt; Data &gt; Export Your Data</b> butonuna tıklayın. İnen zip dosyasının içindeki <code>ratings.csv</code> dosyasını buraya yükleyin.
    </div>
  </div>
</body>
</html>`);
});

app.post("/api/letterboxd/import", async (c) => {
  let csvText = "";
  const contentType = c.req.header("content-type") || "";
  if (contentType.includes("multipart/form-data")) {
    const body = await c.req.parseBody();
    const file = body["file"];
    if (file instanceof File) {
      csvText = await file.text();
    } else if (typeof file === "string") {
      csvText = file;
    }
  } else {
    csvText = await c.req.text();
  }

  if (!csvText || !csvText.trim()) {
    return c.json({ success: false, message: "CSV içeriği boş" }, 400);
  }

  const lines = csvText.replace(/^\uFEFF/, "").split(/\r?\n/).filter(l => l.trim().length > 0);
  if (lines.length < 2) {
    return c.json({ success: false, message: "Geçersiz CSV (en az başlık ve 1 satır olmalı)" }, 400);
  }

  const parseCSVLine = (line: string): string[] => {
    const res: string[] = [];
    let cur = "";
    let inQuotes = false;
    for (let i = 0; i < line.length; i++) {
      const ch = line[i];
      if (ch === '"') {
        if (inQuotes && line[i + 1] === '"') {
          cur += '"';
          i++;
        } else {
          inQuotes = !inQuotes;
        }
      } else if (ch === ',' && !inQuotes) {
        res.push(cur.trim());
        cur = "";
      } else {
        cur += ch;
      }
    }
    res.push(cur.trim());
    return res;
  };

  const headers = parseCSVLine(lines[0]).map(h => h.trim().toLowerCase());
  const nameIdx = headers.findIndex(h => h === "name" || h === "title");
  const yearIdx = headers.findIndex(h => h === "year");
  const ratingIdx = headers.findIndex(h => h === "rating");
  const uriIdx = headers.findIndex(h => h.includes("uri") || h === "link");
  const dateIdx = headers.findIndex(h => h.includes("watched date") || h === "date");
  const rewatchIdx = headers.findIndex(h => h === "rewatch");

  if (nameIdx === -1) {
    return c.json({ success: false, message: "CSV içinde 'Name' veya 'Title' sütunu bulunamadı" }, 400);
  }

  let inserted = 0;
  let updated = 0;

  for (let i = 1; i < lines.length; i++) {
    const cols = parseCSVLine(lines[i]);
    const name = cols[nameIdx] || "";
    if (!name) continue;

    const year = yearIdx !== -1 ? parseInt(cols[yearIdx] || "0", 10) : 0;
    const rating = ratingIdx !== -1 ? parseFloat(cols[ratingIdx] || "0") : 0;
    const uri = uriIdx !== -1 ? cols[uriIdx] : "";
    const dateStr = dateIdx !== -1 ? cols[dateIdx] : "";
    const rewatch = rewatchIdx !== -1 ? cols[rewatchIdx] : "";

    let filmKey = "";
    if (uri) {
      const slugMatch = uri.match(/\/film\/([^/]+)/);
      if (slugMatch) {
        const slug = slugMatch[1];
        const bySlug = await c.env.DB
          .prepare("SELECT film_key FROM letterboxd_films WHERE letterboxd_uri LIKE '%/film/' || ? || '/%' LIMIT 1")
          .bind(slug)
          .first<any>();
        if (bySlug?.film_key) filmKey = bySlug.film_key;
      }
    }

    if (!filmKey) {
      const byName = await c.env.DB
        .prepare("SELECT film_key FROM letterboxd_films WHERE lower(trim(name)) = lower(trim(?)) AND coalesce(year, -1) = ? LIMIT 1")
        .bind(name, year || -1)
        .first<any>();
      if (byName?.film_key) filmKey = byName.film_key;
    }

    if (!filmKey) {
      filmKey = uri || hashStr(name + year);
    }

    const existing = await c.env.DB.prepare("SELECT film_key FROM letterboxd_films WHERE film_key = ?").bind(filmKey).first();

    await c.env.DB.prepare(`
      INSERT INTO letterboxd_films (
        film_key, letterboxd_uri, name, year, rating, watched_date,
        diary_date, rewatch, source, raw_json, imported_at
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'csv', ?, current_timestamp)
      ON CONFLICT(film_key) DO UPDATE SET
        letterboxd_uri=coalesce(nullif(excluded.letterboxd_uri, ''), letterboxd_films.letterboxd_uri),
        name=excluded.name,
        year=coalesce(excluded.year, letterboxd_films.year),
        rating=CASE WHEN excluded.rating > 0 THEN excluded.rating ELSE letterboxd_films.rating END,
        watched_date=coalesce(nullif(excluded.watched_date, ''), letterboxd_films.watched_date),
        diary_date=coalesce(nullif(excluded.diary_date, ''), letterboxd_films.diary_date),
        rewatch=coalesce(nullif(excluded.rewatch, ''), letterboxd_films.rewatch),
        source=excluded.source,
        imported_at=current_timestamp
    `).bind(
      filmKey,
      uri,
      name,
      year,
      rating,
      dateStr,
      dateStr,
      rewatch,
      JSON.stringify({ name, year, uri, rating })
    ).run();

    if (existing) updated++;
    else inserted++;
  }

  if (contentType.includes("multipart/form-data")) {
    return c.html(`<!DOCTYPE html>
<html lang="tr">
<head>
  <meta charset="utf-8">
  <title>İçe Aktarma Tamamlandı</title>
  <style>
    body { font-family: sans-serif; background: #0b0f19; color: #f3f4f6; display: flex; align-items: center; justify-content: center; min-height: 100vh; margin: 0; }
    .card { background: #161e2e; border: 1px solid #2e384d; border-radius: 16px; padding: 32px; text-align: center; max-width: 440px; }
    h1 { color: #10b981; }
    a { display: inline-block; margin-top: 16px; color: #3b82f6; text-decoration: none; font-weight: 600; }
  </style>
</head>
<body>
  <div class="card">
    <h1>🎉 İçe Aktarma Başarılı!</h1>
    <p><b>${updated}</b> film güncellendi, <b>${inserted}</b> yeni film eklendi.</p>
    <a href="/letterboxd/import">← Yeni Dosya Yükle</a>
  </div>
</body>
</html>`);
  }

  return c.json({ success: true, inserted, updated, total: lines.length - 1, message: `${updated} film güncellendi, ${inserted} film eklendi.` });
});

// 12. Sync FatSecret
app.post("/api/sync/fatsecret", async (c) => {
  let days = 3;
  try {
    const body = await c.req.json();
    if (body.days) days = parseInt(body.days, 10);
  } catch (_) {
    if (c.req.query("days")) days = parseInt(c.req.query("days")!, 10);
  }

  const creds = await getFatSecretCredentials(c.env.DB, c.env);

  if (!creds.consumerKey || !creds.consumerSecret) {
    return c.json({
      inserted: 0,
      updated: 0,
      message: "FatSecret API anahtarları tanımlı değil (Ayarlar ekranından girebilirsiniz)",
    }, 400);
  }

  try {
    const result = await syncFatSecretFoods(c.env.DB, creds.consumerKey, creds.consumerSecret, days);
    return c.json(result);
  } catch (e: any) {
    return c.json({ inserted: 0, updated: 0, message: e.message }, 500);
  }
});

// 12.1. FatSecret OAuth Web Connect & Callback
app.get("/fatsecret/connect", async (c) => {
  const creds = await getFatSecretCredentials(c.env.DB, c.env);
  if (!creds.consumerKey || !creds.consumerSecret) {
    return c.html(`
      <!DOCTYPE html>
      <html><head><meta charset="utf-8"><title>FatSecret Anahtarları Eksik</title></head>
      <body style="font-family:sans-serif;padding:2rem;text-align:center;">
        <h2>FatSecret API Anahtarları Bulunamadı</h2>
        <p>Lütfen önce mobil uygulamadan veya ayarlardan Consumer Key ve Secret bilgilerinizi giriniz.</p>
      </body></html>
    `, 400);
  }

  const origin = new URL(c.req.url).origin;
  const callbackUrl = `${origin}/fatsecret/callback`;

  try {
    const authUrl = await startFatSecretConnect(c.env.DB, creds.consumerKey, creds.consumerSecret, callbackUrl);
    return c.redirect(authUrl);
  } catch (err: any) {
    return c.html(`
      <!DOCTYPE html>
      <html><head><meta charset="utf-8"><title>Hata</title></head>
      <body style="font-family:sans-serif;padding:2rem;text-align:center;">
        <h2>Bağlantı Başlatılamadı</h2>
        <p style="color:red;">${err.message}</p>
      </body></html>
    `, 500);
  }
});

app.get("/fatsecret/callback", async (c) => {
  const oauthToken = c.req.query("oauth_token");
  const oauthVerifier = c.req.query("oauth_verifier");

  if (!oauthToken || !oauthVerifier) {
    return c.html(`
      <!DOCTYPE html>
      <html><head><meta charset="utf-8"><title>Eksik Parametre</title></head>
      <body style="font-family:sans-serif;padding:2rem;text-align:center;">
        <h2>Yetkilendirme Başarısız</h2>
        <p>FatSecret geri dönüş parametreleri eksik geldi.</p>
      </body></html>
    `, 400);
  }

  const creds = await getFatSecretCredentials(c.env.DB, c.env);
  try {
    await finishFatSecretConnect(c.env.DB, creds.consumerKey, creds.consumerSecret, oauthToken, oauthVerifier);
    return c.html(`
      <!DOCTYPE html>
      <html>
      <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>FatSecret Bağlandı</title>
        <style>
          body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; background: #f8fafc; display: flex; align-items: center; justify-content: center; min-height: 100vh; margin: 0; padding: 20px; box-sizing: border-box; }
          .card { background: white; padding: 40px 30px; border-radius: 20px; box-shadow: 0 10px 25px rgba(0,0,0,0.05); text-align: center; max-width: 420px; width: 100%; }
          .icon { width: 64px; height: 64px; background: #e0f2fe; color: #0284c7; border-radius: 50%; display: flex; align-items: center; justify-content: center; margin: 0 auto 20px; font-size: 32px; }
          h2 { margin: 0 0 10px; color: #0f172a; }
          p { color: #64748b; line-height: 1.5; margin: 0 0 25px; }
          .btn { display: inline-block; background: #0284c7; color: white; padding: 12px 24px; border-radius: 12px; text-decoration: none; font-weight: 600; cursor: pointer; }
        </style>
      </head>
      <body>
        <div class="card">
          <div class="icon">✓</div>
          <h2>FatSecret Bağlandı!</h2>
          <p>Hesabınız başarıyla eşleştirildi. Artık bu sekmeyi kapatıp uygulamaya dönebilirsiniz.</p>
          <a href="javascript:window.close();" class="btn">Pencereyi Kapat</a>
        </div>
      </body>
      </html>
    `);
  } catch (err: any) {
    return c.html(`
      <!DOCTYPE html>
      <html><head><meta charset="utf-8"><title>Hata</title></head>
      <body style="font-family:sans-serif;padding:2rem;text-align:center;">
        <h2>Yetkilendirme Hatası</h2>
        <p style="color:red;">${err.message}</p>
      </body></html>
    `, 500);
  }
});

// 13. Export All (JSON)
app.get("/api/export/all", async (c) => {
  await ensureHealthTable(c.env.DB);
  const [books, films, foods, healthRecords, summary] = await Promise.all([
    c.env.DB.prepare("SELECT * FROM goodreads_books LIMIT 10000").all(),
    c.env.DB.prepare("SELECT * FROM letterboxd_films LIMIT 10000").all(),
    c.env.DB.prepare("SELECT * FROM fatsecret_food_entries LIMIT 10000").all(),
    c.env.DB.prepare("SELECT * FROM health_records ORDER BY date_iso DESC, start_time DESC LIMIT 10000").all(),
    c.env.DB.prepare(`
      SELECT date_int, count(*) as entry_count, coalesce(sum(calories), 0) as calories
      FROM fatsecret_food_entries
      GROUP BY date_int
      ORDER BY date_int DESC
      LIMIT 365
    `).all(),
  ]);

  const payload = {
    version: "1.0",
    app: "Personal Data Hub (D1)",
    exported_at: new Date().toISOString(),
    goodreads_books: books.results,
    letterboxd_films: films.results,
    fatsecret_foods: foods.results,
    health_records: healthRecords.results,
    daily_summary: summary.results.map((r: any) => ({
      date_int: r.date_int,
      date: dateIntToISO(r.date_int),
      entry_count: r.entry_count,
      calories: r.calories,
    })),
  };

  const filename = `personal_records_backup_${new Date().toISOString().split("T")[0]}.json`;
  return new Response(JSON.stringify(payload, null, 2), {
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      "Content-Disposition": `attachment; filename="${filename}"`,
    },
  });
});

// 14. Export DB / SQL
const exportSqlHandler = async (c: any) => {
  await ensureHealthTable(c.env.DB);
  const tables = [
    "user_settings",
    "goodreads_sync_settings",
    "letterboxd_sync_settings",
    "fatsecret_tokens",
    "goodreads_books",
    "letterboxd_films",
    "fatsecret_food_entries",
    "notifications",
    "health_records",
  ];

  let sqlDump = "-- Cloudflare D1 SQL Dump\n-- Exported At: " + new Date().toISOString() + "\n\n";

  for (const t of tables) {
    const rows = await c.env.DB.prepare(`SELECT * FROM ${t}`).all();
    const results = (rows.results || []) as any[];
    if (results.length === 0) continue;
    sqlDump += `-- Table: ${t} (${results.length} rows)\n`;
    for (const r of results) {
      const keys = Object.keys(r);
      const cols = keys.map((k) => `"${k}"`).join(", ");
      const vals = keys
        .map((k) => {
          const v = r[k];
          if (v === null || v === undefined) return "NULL";
          if (typeof v === "number") return v.toString();
          return `'${v.toString().replace(/'/g, "''")}'`;
        })
        .join(", ");
      sqlDump += `INSERT OR REPLACE INTO ${t} (${cols}) VALUES (${vals});\n`;
    }
    sqlDump += "\n";
  }

  const filename = `personal_records_${new Date().toISOString().replace(/[:.]/g, "-")}.sql`;
  return new Response(sqlDump, {
    headers: {
      "Content-Type": "application/sql; charset=utf-8",
      "Content-Disposition": `attachment; filename="${filename}"`,
    },
  });
};

app.get("/api/export/sql", exportSqlHandler);
app.get("/api/export/db", exportSqlHandler);

// 13. Notification Relay Webhook (cobanov/notification-relay-webhook)
const handleNotificationWebhook = async (c: any) => {
  // Optional Secret Header Validation
  const expectedSecret = (c.env as any).WEBHOOK_SECRET;
  if (expectedSecret) {
    const clientSecret = c.req.header("X-Webhook-Secret") || c.req.header("Authorization")?.replace(/^Bearer\s+/i, "");
    if (clientSecret !== expectedSecret) {
      return c.json({ success: false, error: "Yetkisiz istek: Webhook Secret geçersiz" }, 401);
    }
  }

  let body: any;
  try {
    body = await c.req.json();
  } catch (e: any) {
    return c.json({ success: false, error: "Geçersiz JSON gövdesi: " + e.message }, 400);
  }

  const items = Array.isArray(body) ? body : [body];
  let inserted = 0;
  let updated = 0;

  for (const item of items) {
    const packageName = item.package || item.packageName || "unknown";
    const appName = item.app || item.appName || packageName;
    const title = item.title || "";
    const text = item.text || "";
    const subText = item.sub_text || item.subText || "";
    const bigText = item.big_text || item.bigText || "";
    const category = item.category || "";
    const postTime = item.post_time || item.postedAt || new Date().toISOString();
    const notifKey = item.key || item.notificationKey || "";
    const isOngoing = item.ongoing ? 1 : 0;

    // Deterministic ID for idempotency & deduplication
    const idBasis = notifKey || (packageName + "|" + title + "|" + text + "|" + postTime);
    const id = "notif_" + hashStr(idBasis);

    const existing = await c.env.DB
      .prepare("SELECT id FROM notifications WHERE id = ?")
      .bind(id)
      .first();

    await c.env.DB.prepare(`
      INSERT INTO notifications (
        id, package_name, app_name, title, text, sub_text, big_text,
        category, post_time, notification_key, is_ongoing, raw_json, created_at
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
      ON CONFLICT(id) DO UPDATE SET
        text=excluded.text,
        sub_text=excluded.sub_text,
        big_text=excluded.big_text,
        raw_json=excluded.raw_json
    `).bind(
      id,
      packageName,
      appName,
      title,
      text,
      subText,
      bigText,
      category,
      postTime,
      notifKey,
      isOngoing,
      JSON.stringify(item)
    ).run();

    if (existing) updated++;
    else inserted++;
  }

  return c.json({
    success: true,
    inserted,
    updated,
    total: items.length,
    message: `${items.length} bildirim başarıyla işlendi (${inserted} yeni, ${updated} güncellendi).`
  });
};

app.post("/api/webhooks/notification", handleNotificationWebhook);
app.post("/api/webhooks/notifications", handleNotificationWebhook);

// 14. Query Notifications API
app.get("/api/notifications", async (c) => {
  const limit = Math.min(parseInt(c.req.query("limit") || "50", 10), 200);
  const pkg = c.req.query("package");

  let query = "SELECT * FROM notifications";
  const params: any[] = [];
  if (pkg) {
    query += " WHERE package_name = ?";
    params.push(pkg);
  }
  query += " ORDER BY post_time DESC LIMIT ?";
  params.push(limit);

  const stmt = c.env.DB.prepare(query);
  const result = params.length === 2 ? await stmt.bind(params[0], params[1]).all<any>() : await stmt.bind(params[0]).all<any>();

  return c.json({
    count: result.results.length,
    items: result.results,
  });
});

// 15. Health Connect Webhook (mcnaveen/health-connect-webhook compatible)
interface NormalizedHealthRecord {
  id: string;
  record_type: string;
  category: string;
  date_iso: string;
  start_time: string;
  end_time: string;
  value_primary: number | null;
  value_secondary: number | null;
  value_tertiary: number | null;
  unit: string;
  title: string;
  subtitle: string;
  data_origin: string;
  raw_json: string;
}

function formatOriginLabel(origin: string): string {
  if (!origin) return "Health Connect";
  const lower = origin.toLowerCase();
  if (lower.includes("shealth") || lower.includes("samsung")) return "Samsung Health";
  if (lower.includes("google") && lower.includes("fitness")) return "Google Fit";
  if (lower.includes("fitbit")) return "Fitbit";
  if (lower.includes("garmin")) return "Garmin Connect";
  if (lower.includes("xiaomi") || lower.includes("mihealth") || lower.includes("zepp")) return "Zepp / Mi Fitness";
  if (lower.includes("strava")) return "Strava";
  if (lower.includes("oura")) return "Oura";
  if (lower.includes("withings")) return "Withings";
  if (lower.includes("polar")) return "Polar";
  return origin;
}

function parseHealthConnectPayload(body: any): NormalizedHealthRecord[] {
  const out: NormalizedHealthRecord[] = [];
  if (!body || typeof body !== "object") return out;
  const fallbackNow = typeof body.timestamp === "string" && body.timestamp ? body.timestamp : new Date().toISOString();

  const pushRecord = (
    recordType: string,
    category: string,
    startTime: string,
    endTime: string,
    val1: number | null,
    val2: number | null,
    val3: number | null,
    unit: string,
    title: string,
    subtitle: string,
    rawItem: any
  ) => {
    const st = startTime || endTime || fallbackNow;
    const et = endTime || startTime || fallbackNow;
    const dISO = (et || st).substring(0, 10);
    const origin = String(rawItem?.metadata?.data_origin || "");
    const basis = `${recordType}|${st}|${et}|${origin}`;
    const id = `hc_${recordType}_${hashStr(basis)}`;
    const originName = formatOriginLabel(origin);
    const finalSub = subtitle ? (origin ? `${subtitle} · ${originName}` : subtitle) : originName;

    out.push({
      id,
      record_type: recordType,
      category,
      date_iso: dISO,
      start_time: st,
      end_time: et,
      value_primary: val1 !== undefined && val1 !== null && !Number.isNaN(Number(val1)) ? Number(val1) : null,
      value_secondary: val2 !== undefined && val2 !== null && !Number.isNaN(Number(val2)) ? Number(val2) : null,
      value_tertiary: val3 !== undefined && val3 !== null && !Number.isNaN(Number(val3)) ? Number(val3) : null,
      unit,
      title,
      subtitle: finalSub,
      data_origin: origin,
      raw_json: JSON.stringify(rawItem),
    });
  };

  if (Array.isArray(body.steps)) {
    for (const it of body.steps) {
      const c = Number(it.count) || 0;
      pushRecord("steps", "activity", it.start_time, it.end_time, c, null, null, "steps", `${c.toLocaleString("tr-TR")} adım`, "Adım Sayısı", it);
    }
  }
  if (Array.isArray(body.distance)) {
    for (const it of body.distance) {
      const m = Number(it.meters) || 0;
      pushRecord("distance", "activity", it.start_time, it.end_time, m, null, null, "m", `${(m / 1000).toFixed(2)} km mesafe`, "Yürüme / Koşu Mesafesi", it);
    }
  }
  if (Array.isArray(body.active_calories)) {
    for (const it of body.active_calories) {
      const cal = Number(it.calories) || 0;
      pushRecord("active_calories", "activity", it.start_time, it.end_time, cal, null, null, "kcal", `${Math.round(cal)} kcal aktif`, "Aktif Kalori", it);
    }
  }
  if (Array.isArray(body.total_calories)) {
    for (const it of body.total_calories) {
      const cal = Number(it.calories) || 0;
      pushRecord("total_calories", "activity", it.start_time, it.end_time, cal, null, null, "kcal", `${Math.round(cal)} kcal toplam`, "Toplam Kalori", it);
    }
  }
  if (Array.isArray(body.exercise)) {
    for (const it of body.exercise) {
      const dur = Number(it.duration_seconds) || 0;
      const dist = it.distance_meters != null ? Number(it.distance_meters) : null;
      const stp = it.steps != null ? Number(it.steps) : null;
      const mins = Math.round(dur / 60);
      const exTitle = it.title || it.type || "Antrenman";
      const subParts = [`${mins} dk`];
      if (dist && dist > 0) subParts.push(`${(dist / 1000).toFixed(2)} km`);
      if (stp && stp > 0) subParts.push(`${stp} adım`);
      pushRecord("exercise", "exercise", it.start_time, it.end_time, dur, dist, stp, "sec", `Antrenman: ${exTitle}`, subParts.join(" · "), it);
    }
  }
  if (Array.isArray(body.sleep)) {
    for (const it of body.sleep) {
      const dur = Number(it.duration_seconds) || 0;
      const stages = Array.isArray(it.stages) ? it.stages : [];
      const startT = stages.length > 0 && stages[0].start_time ? stages[0].start_time : it.session_end_time;
      const hrs = Math.floor(dur / 3600);
      const mins = Math.round((dur % 3600) / 60);
      const sub = stages.length > 0 ? `${stages.length} uyku evresi kaydedildi` : "Uyku Oturumu";
      pushRecord("sleep", "sleep", startT, it.session_end_time, dur, stages.length, null, "sec", `Uyku · ${hrs} sa ${mins} dk`, sub, it);
    }
  }
  if (Array.isArray(body.heart_rate)) {
    for (const it of body.heart_rate) {
      const bpm = Number(it.avg ?? it.bpm) || 0;
      const minBpm = it.min != null ? Number(it.min) : null;
      const maxBpm = it.max != null ? Number(it.max) : null;
      const sub = minBpm != null && maxBpm != null ? `Min ${Math.round(minBpm)} · Max ${Math.round(maxBpm)} bpm` : "Kalp Atış Hızı";
      pushRecord("heart_rate", "vitals", it.time, it.time, bpm, minBpm, maxBpm, "bpm", `${Math.round(bpm)} bpm Nabız`, sub, it);
    }
  }
  if (Array.isArray(body.resting_heart_rate)) {
    for (const it of body.resting_heart_rate) {
      const bpm = Number(it.bpm) || 0;
      pushRecord("resting_heart_rate", "vitals", it.time, it.time, bpm, null, null, "bpm", `${Math.round(bpm)} bpm Dinlenik Nabız`, "Dinlenik Kalp Atış Hızı", it);
    }
  }
  if (Array.isArray(body.heart_rate_variability)) {
    for (const it of body.heart_rate_variability) {
      const hrv = Number(it.avg ?? it.rmssd_millis) || 0;
      const minH = it.min != null ? Number(it.min) : null;
      const maxH = it.max != null ? Number(it.max) : null;
      pushRecord("heart_rate_variability", "vitals", it.time, it.time, hrv, minH, maxH, "ms", `${Math.round(hrv)} ms HRV`, "Kalp Atış Hızı Değişkenliği (RMSSD)", it);
    }
  }
  if (Array.isArray(body.oxygen_saturation)) {
    for (const it of body.oxygen_saturation) {
      const pct = Number(it.avg ?? it.percentage) || 0;
      const minP = it.min != null ? Number(it.min) : null;
      const maxP = it.max != null ? Number(it.max) : null;
      pushRecord("oxygen_saturation", "vitals", it.time, it.time, pct, minP, maxP, "%", `%${pct.toFixed(1)} SpO₂`, "Kan Oksijen Doygunluğu", it);
    }
  }
  if (Array.isArray(body.respiratory_rate)) {
    for (const it of body.respiratory_rate) {
      const rate = Number(it.avg ?? it.rate) || 0;
      pushRecord("respiratory_rate", "vitals", it.time, it.time, rate, it.min ?? null, it.max ?? null, "rpm", `${rate.toFixed(1)} Solunum/dk`, "Solunum Hızı", it);
    }
  }
  if (Array.isArray(body.blood_pressure)) {
    for (const it of body.blood_pressure) {
      const sys = Number(it.systolic) || 0;
      const dia = Number(it.diastolic) || 0;
      pushRecord("blood_pressure", "vitals", it.time, it.time, sys, dia, null, "mmHg", `${Math.round(sys)}/${Math.round(dia)} mmHg`, "Kan Basıncı (Tansiyon)", it);
    }
  }
  if (Array.isArray(body.blood_glucose)) {
    for (const it of body.blood_glucose) {
      const gl = Number(it.mmol_per_liter) || 0;
      pushRecord("blood_glucose", "vitals", it.time, it.time, gl, null, null, "mmol/L", `${gl.toFixed(1)} mmol/L Kan Şekeri`, "Kan Glikozu", it);
    }
  }
  if (Array.isArray(body.body_temperature)) {
    for (const it of body.body_temperature) {
      const c = Number(it.celsius) || 0;
      pushRecord("body_temperature", "vitals", it.time, it.time, c, null, null, "°C", `${c.toFixed(1)} °C Vücut Sıcaklığı`, "Ateş / Sıcaklık", it);
    }
  }
  if (Array.isArray(body.skin_temperature)) {
    for (const it of body.skin_temperature) {
      const d = Number(it.avg_delta_celsius ?? it.delta_celsius) || 0;
      pushRecord("skin_temperature", "vitals", it.time, it.time, d, it.baseline_celsius ?? null, null, "Δ°C", `${d >= 0 ? "+" : ""}${d.toFixed(2)} °C Cilt Sıcaklığı`, "Bazal Değişim", it);
    }
  }
  if (Array.isArray(body.vo2_max)) {
    for (const it of body.vo2_max) {
      const v = Number(it.ml_per_kg_per_min) || 0;
      pushRecord("vo2_max", "vitals", it.time, it.time, v, null, null, "mL/kg/dk", `${v.toFixed(1)} VO₂ Max`, "Maksimum Oksijen Tüketimi", it);
    }
  }
  if (Array.isArray(body.weight)) {
    for (const it of body.weight) {
      const kg = Number(it.kilograms) || 0;
      pushRecord("weight", "body", it.time, it.time, kg, null, null, "kg", `${kg.toFixed(1)} kg Ağırlık`, "Vücut Ağırlığı", it);
    }
  }
  if (Array.isArray(body.height)) {
    for (const it of body.height) {
      const m = Number(it.meters) || 0;
      pushRecord("height", "body", it.time, it.time, m, null, null, "m", `${Math.round(m * 100)} cm Boy`, "Boy Ölçümü", it);
    }
  }
  if (Array.isArray(body.bmi)) {
    for (const it of body.bmi) {
      const v = Number(it.value) || 0;
      pushRecord("bmi", "body", it.time, it.time, v, it.weight_kg ?? null, it.height_meters ?? null, "kg/m²", `${v.toFixed(1)} VKİ (BMI)`, "Vücut Kitle İndeksi", it);
    }
  }
  if (Array.isArray(body.body_fat)) {
    for (const it of body.body_fat) {
      const pct = Number(it.percentage) || 0;
      pushRecord("body_fat", "body", it.time, it.time, pct, null, null, "%", `%${pct.toFixed(1)} Yağ Oranı`, "Vücut Kompozisyonu", it);
    }
  }
  if (Array.isArray(body.lean_body_mass)) {
    for (const it of body.lean_body_mass) {
      const kg = Number(it.kilograms) || 0;
      pushRecord("lean_body_mass", "body", it.time, it.time, kg, null, null, "kg", `${kg.toFixed(1)} kg Yağsız Kütle`, "Vücut Kompozisyonu", it);
    }
  }
  if (Array.isArray(body.body_water_mass)) {
    for (const it of body.body_water_mass) {
      const kg = Number(it.kilograms) || 0;
      pushRecord("body_water_mass", "body", it.time, it.time, kg, null, null, "kg", `${kg.toFixed(1)} kg Vücut Suyu`, "Vücut Kompozisyonu", it);
    }
  }
  if (Array.isArray(body.bone_mass)) {
    for (const it of body.bone_mass) {
      const kg = Number(it.kilograms) || 0;
      pushRecord("bone_mass", "body", it.time, it.time, kg, null, null, "kg", `${kg.toFixed(2)} kg Kemik Kütlesi`, "Vücut Kompozisyonu", it);
    }
  }
  if (Array.isArray(body.basal_metabolic_rate)) {
    for (const it of body.basal_metabolic_rate) {
      const w = Number(it.watts) || 0;
      pushRecord("basal_metabolic_rate", "body", it.time, it.time, w, null, null, "W", `${Math.round(w)} W BMR`, "Bazal Metabolizma Hızı", it);
    }
  }
  if (Array.isArray(body.hydration)) {
    for (const it of body.hydration) {
      const l = Number(it.liters) || 0;
      pushRecord("hydration", "body", it.start_time, it.end_time, l, null, null, "L", `${Math.round(l * 1000)} mL Su`, "Su Tüketimi", it);
    }
  }
  if (Array.isArray(body.nutrition)) {
    for (const it of body.nutrition) {
      const cal = Number(it.calories) || 0;
      pushRecord("nutrition", "body", it.start_time, it.end_time, cal, it.protein_grams ?? null, it.carbs_grams ?? null, "kcal", it.name || "Besin Kaydı", `${Math.round(cal)} kcal`, it);
    }
  }

  return out;
}

const handleHealthWebhook = async (c: any) => {
  const expectedSecret = (c.env as any).WEBHOOK_SECRET;
  if (expectedSecret) {
    const clientSecret = c.req.header("X-Webhook-Secret") || c.req.header("x-api-key") || c.req.header("Authorization")?.replace(/^Bearer\s+/i, "");
    if (clientSecret !== expectedSecret) {
      return c.json({ success: false, error: "Yetkisiz istek: Webhook Secret geçersiz" }, 401);
    }
  }

  let body: any;
  try {
    body = await c.req.json();
  } catch (e: any) {
    return c.json({ success: false, error: "Geçersiz JSON gövdesi: " + e.message }, 400);
  }

  await ensureHealthTable(c.env.DB);
  const records = parseHealthConnectPayload(body);

  if (records.length === 0) {
    return c.json({
      success: true,
      inserted: 0,
      updated: 0,
      total: 0,
      message: "Health Connect webhook alındı (yeni kayıt yok)."
    });
  }

  const upsertSql = `
    INSERT INTO health_records (
      id, record_type, category, date_iso, start_time, end_time,
      value_primary, value_secondary, value_tertiary, unit, title, subtitle,
      data_origin, raw_json, synced_at
    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
    ON CONFLICT(id) DO UPDATE SET
      value_primary=excluded.value_primary,
      value_secondary=excluded.value_secondary,
      value_tertiary=excluded.value_tertiary,
      title=excluded.title,
      subtitle=excluded.subtitle,
      raw_json=excluded.raw_json,
      synced_at=CURRENT_TIMESTAMP
  `;

  // Batch in chunks of 50 for D1 performance
  const chunkSize = 50;
  for (let i = 0; i < records.length; i += chunkSize) {
    const chunk = records.slice(i, i + chunkSize);
    const stmts = chunk.map((r) =>
      c.env.DB.prepare(upsertSql).bind(
        r.id,
        r.record_type,
        r.category,
        r.date_iso,
        r.start_time,
        r.end_time,
        r.value_primary,
        r.value_secondary,
        r.value_tertiary,
        r.unit,
        r.title,
        r.subtitle,
        r.data_origin,
        r.raw_json
      )
    );
    await c.env.DB.batch(stmts);
  }

  return c.json({
    success: true,
    inserted: records.length,
    updated: 0,
    total: records.length,
    message: `${records.length} Health Connect kaydı başarıyla işlendi.`
  });
};

const handleHealthWebhookStatus = async (c: any) => {
  await ensureHealthTable(c.env.DB);
  const row = (await c.env.DB.prepare("SELECT count(*) as c FROM health_records").first()) as any;
  return c.json({
    ok: true,
    service: "health-connect-webhook",
    status: "ready",
    accepted_methods: ["POST"],
    total_records: row?.c || 0,
    message: "Health Connect Webhook uç noktası aktif. Verileri POST metodu ile gönderebilirsiniz."
  });
};

app.get("/api/webhooks/health", handleHealthWebhookStatus);
app.get("/api/webhooks/health-connect", handleHealthWebhookStatus);
app.get("/webhooks/health", handleHealthWebhookStatus);
app.get("/webhooks/health-connect", handleHealthWebhookStatus);

app.post("/api/webhooks/health", handleHealthWebhook);
app.post("/api/webhooks/health-connect", handleHealthWebhook);
app.post("/webhooks/health", handleHealthWebhook);
app.post("/webhooks/health-connect", handleHealthWebhook);

// 16. Query Health Connect Records & Summary API
app.get("/api/health-connect/records", async (c) => {
  const db = c.env.DB;
  await ensureHealthTable(db);

  const limit = Math.min(parseInt(c.req.query("limit") || "150", 10), 500);
  const category = c.req.query("category")?.trim();
  const recordType = c.req.query("type")?.trim();
  const date = c.req.query("date")?.trim();
  const fromDate = c.req.query("from")?.trim();
  const toDate = c.req.query("to")?.trim();

  let sql = "SELECT * FROM health_records WHERE 1=1";
  const params: any[] = [];

  if (category) {
    sql += " AND category = ?";
    params.push(category);
  }
  if (recordType) {
    sql += " AND record_type = ?";
    params.push(recordType);
  }
  if (date) {
    sql += " AND date_iso = ?";
    params.push(date);
  } else {
    if (fromDate) {
      sql += " AND date_iso >= ?";
      params.push(fromDate);
    }
    if (toDate) {
      sql += " AND date_iso <= ?";
      params.push(toDate);
    }
  }

  sql += " ORDER BY date_iso DESC, start_time DESC LIMIT ?";
  params.push(limit);

  const [rows, totalRow, latestStepsDay, latestSleep, latestHr, latestWeight] = await Promise.all([
    db.prepare(sql).bind(...params).all<any>(),
    db.prepare("SELECT count(*) as c FROM health_records").first<any>(),
    db.prepare(`
      SELECT date_iso, coalesce(sum(value_primary), 0) as steps
      FROM health_records
      WHERE record_type = 'steps'
      GROUP BY date_iso
      ORDER BY date_iso DESC
      LIMIT 1
    `).first<any>(),
    db.prepare("SELECT value_primary, date_iso FROM health_records WHERE record_type = 'sleep' ORDER BY date_iso DESC, end_time DESC LIMIT 1").first<any>(),
    db.prepare("SELECT value_primary, date_iso FROM health_records WHERE record_type IN ('heart_rate', 'resting_heart_rate') ORDER BY date_iso DESC, end_time DESC LIMIT 1").first<any>(),
    db.prepare("SELECT value_primary, date_iso FROM health_records WHERE record_type = 'weight' ORDER BY date_iso DESC, end_time DESC LIMIT 1").first<any>(),
  ]);

  const activeCaloriesDay = await db.prepare(`
    SELECT date_iso, coalesce(sum(value_primary), 0) as kcal
    FROM health_records
    WHERE record_type = 'active_calories'
    GROUP BY date_iso
    ORDER BY date_iso DESC
    LIMIT 1
  `).first<any>();

  return c.json({
    count: rows.results.length,
    items: rows.results,
    summary: {
      total_records: totalRow?.c || 0,
      today_steps: Math.round(Number(latestStepsDay?.steps) || 0),
      steps_date: latestStepsDay?.date_iso || "",
      last_sleep_seconds: Math.round(Number(latestSleep?.value_primary) || 0),
      sleep_date: latestSleep?.date_iso || "",
      latest_heart_rate_bpm: Math.round(Number(latestHr?.value_primary) || 0),
      today_active_calories: Math.round(Number(activeCaloriesDay?.kcal) || 0),
      latest_weight_kg: Number(latestWeight?.value_primary) || 0,
    },
  });
});

// ==========================================
// 17. MUSIC & AUDIO (Web Scrobbler & Last.fm)
// ==========================================

function extractLastFmImage(images: any): string {
  if (!images || !Array.isArray(images) || images.length === 0) return "";
  const order = ["extralarge", "large", "medium", "small"];
  for (const size of order) {
    const found = images.find((img: any) => img.size === size && img["#text"]);
    if (found && found["#text"]) return found["#text"].trim();
  }
  const last = images[images.length - 1];
  return last && last["#text"] ? last["#text"].trim() : "";
}

async function getLastFmCredentials(db: D1Database, env: Env): Promise<{ username: string; apiKey: string }> {
  await ensureMusicTables(db);
  const row = (await db.prepare("SELECT lastfm_username, lastfm_api_key FROM music_sync_settings WHERE id = 1").first()) as any;
  const username = (row?.lastfm_username || env.LASTFM_USERNAME || "").trim();
  const apiKey = (row?.lastfm_api_key || env.LASTFM_API_KEY || "").trim();
  return { username, apiKey };
}

// Web Scrobbler Webhook Handler
const handleMusicWebhook = async (c: any) => {
  const db = c.env.DB;
  await ensureMusicTables(db);

  let body: any = null;
  try {
    body = await c.req.json();
  } catch (e) {
    return c.json({ ok: false, error: "Invalid JSON body" }, 200);
  }

  if (!body) {
    return c.json({ ok: false, error: "Empty request body" }, 200);
  }

  // Web Scrobbler sends eventName: "scrobble" or "nowplaying"
  const eventName = body.eventName || body.event || "scrobble";
  if (eventName === "nowplaying") {
    // Return 200 OK without saving to prevent spamming
    return c.json({ ok: true, status: "nowplaying_ignored" }, 200);
  }

  const rawItems = Array.isArray(body) ? body : (Array.isArray(body.items) ? body.items : [body]);
  let inserted = 0;
  let updated = 0;

  for (const item of rawItems) {
    const song = item.data?.song || item.song;
    const artist = (song?.processed?.artist || song?.parsed?.artist || item.artist || "").trim();
    const track = (song?.processed?.track || song?.parsed?.track || item.track || item.title || "").trim();
    const album = (song?.processed?.album || song?.parsed?.album || item.album || "").trim();
    const albumArtist = (song?.processed?.albumArtist || song?.parsed?.albumArtist || item.albumArtist || item.album_artist || "").trim();
    const duration = Number(song?.parsed?.duration || song?.duration || item.duration || 0);
    const imageUrl = (song?.parsed?.trackArt || item.trackArt || item.image_url || item.imageUrl || "").trim();
    const originUrl = (item.originUrl || song?.parsed?.originUrl || item.origin_url || "").trim();

    let uts = Number(item.uts || item.timestamp || 0);
    if (!uts || uts <= 0) {
      uts = Math.floor(Date.now() / 1000);
    }
    const listenedAt = item.listened_at || new Date(uts * 1000).toISOString();
    const source = item.source || "web_scrobbler";

    if (!artist || !track) {
      continue;
    }

    const id = (source === "lastfm" ? "lfm_" : "ws_") + hashStr(`${artist}:${track}:${uts}`);
    const rawJson = JSON.stringify(item);

    const existing = await db
      .prepare("SELECT id FROM music_scrobbles WHERE artist = ? AND track = ? AND uts = ?")
      .bind(artist, track, uts)
      .first();

    await db.prepare(`
      INSERT INTO music_scrobbles (id, track, artist, album, album_artist, duration, listened_at, uts, source, origin_url, image_url, raw_json)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      ON CONFLICT(artist, track, uts) DO UPDATE SET
        album = CASE WHEN excluded.album != '' THEN excluded.album ELSE music_scrobbles.album END,
        album_artist = CASE WHEN excluded.album_artist != '' THEN excluded.album_artist ELSE music_scrobbles.album_artist END,
        duration = CASE WHEN excluded.duration > 0 THEN excluded.duration ELSE music_scrobbles.duration END,
        image_url = CASE WHEN excluded.image_url != '' THEN excluded.image_url ELSE music_scrobbles.image_url END,
        origin_url = CASE WHEN excluded.origin_url != '' THEN excluded.origin_url ELSE music_scrobbles.origin_url END
    `).bind(
      id, track, artist, album, albumArtist, duration, listenedAt, uts, source, originUrl, imageUrl, rawJson
    ).run();

    if (existing) updated++;
    else inserted++;
  }

  return c.json({
    ok: true,
    status: "success",
    inserted,
    updated,
    total: rawItems.length,
    message: `${rawItems.length} scrobble başarıyla işlendi (${inserted} yeni, ${updated} güncellendi).`
  }, 200);
};

const handleMusicWebhookStatus = async (c: any) => {
  const db = c.env.DB;
  await ensureMusicTables(db);
  const row = (await db.prepare("SELECT count(*) as c FROM music_scrobbles").first()) as any;
  return c.json({
    ok: true,
    service: "music-webhook",
    status: "ready",
    accepted_methods: ["POST"],
    total_records: row?.c || 0,
    instructions: "Web Scrobbler extension -> Options -> Accounts -> Webhook -> URL: [host]/api/webhooks/music",
  });
};

app.get("/api/webhooks/music", handleMusicWebhookStatus);
app.get("/api/webhooks/scrobbler", handleMusicWebhookStatus);
app.get("/webhooks/music", handleMusicWebhookStatus);

app.post("/api/webhooks/music", handleMusicWebhook);
app.post("/api/webhooks/scrobbler", handleMusicWebhook);
app.post("/webhooks/music", handleMusicWebhook);

// 18. Query Music Scrobbles API
app.get("/api/music", async (c) => {
  const db = c.env.DB;
  await ensureMusicTables(db);

  const page = Math.max(parseInt(c.req.query("page") || "1", 10), 1);
  const limit = Math.min(Math.max(parseInt(c.req.query("limit") || "50", 10), 1), 200);
  const offset = (page - 1) * limit;

  const search = c.req.query("search")?.trim();
  const artist = c.req.query("artist")?.trim();
  const album = c.req.query("album")?.trim();
  const source = c.req.query("source")?.trim();
  const date = c.req.query("date")?.trim();
  const from = c.req.query("from")?.trim();
  const to = c.req.query("to")?.trim();

  let whereClauses: string[] = ["1=1"];
  const params: any[] = [];

  if (search) {
    whereClauses.push("(track LIKE ? OR artist LIKE ? OR album LIKE ?)");
    params.push(`%${search}%`, `%${search}%`, `%${search}%`);
  }
  if (artist) {
    whereClauses.push("artist = ?");
    params.push(artist);
  }
  if (album) {
    whereClauses.push("album = ?");
    params.push(album);
  }
  if (source) {
    whereClauses.push("source = ?");
    params.push(source);
  }
  if (date) {
    whereClauses.push("listened_at LIKE ?");
    params.push(`${date}%`);
  } else {
    if (from) {
      const fromUts = isNaN(Number(from)) ? Math.floor(new Date(from).getTime() / 1000) : Number(from);
      whereClauses.push("uts >= ?");
      params.push(fromUts);
    }
    if (to) {
      const toUts = isNaN(Number(to)) ? Math.floor(new Date(to).getTime() / 1000) : Number(to);
      whereClauses.push("uts <= ?");
      params.push(toUts);
    }
  }

  const whereSql = whereClauses.join(" AND ");

  const [countRow, rows] = await Promise.all([
    db.prepare(`SELECT count(*) as total FROM music_scrobbles WHERE ${whereSql}`).bind(...params).first<any>(),
    db.prepare(`SELECT * FROM music_scrobbles WHERE ${whereSql} ORDER BY uts DESC LIMIT ? OFFSET ?`).bind(...params, limit, offset).all<any>(),
  ]);

  const total = countRow?.total || 0;
  return c.json({
    page,
    limit,
    total,
    totalPages: Math.ceil(total / limit),
    count: rows.results.length,
    items: rows.results,
  });
});

// 19. Query Music Stats API
app.get("/api/music/stats", async (c) => {
  const db = c.env.DB;
  await ensureMusicTables(db);

  const nowUts = Math.floor(Date.now() / 1000);
  const todayDate = dateIntToISO(todayDateInt());
  const startOfTodayUts = Math.floor(new Date(todayDate + "T00:00:00+03:00").getTime() / 1000);
  const startOfWeekUts = nowUts - (7 * 86400);
  const startOfMonthUts = nowUts - (30 * 86400);

  const [
    totalRow,
    todayRow,
    weekRow,
    monthRow,
    uniqueArtistsRow,
    uniqueAlbumsRow,
    topArtistsRows,
    topAlbumsRows,
    recentRows,
    lastSyncRow
  ] = await Promise.all([
    db.prepare("SELECT count(*) as c FROM music_scrobbles").first<any>(),
    db.prepare("SELECT count(*) as c FROM music_scrobbles WHERE uts >= ?").bind(startOfTodayUts).first<any>(),
    db.prepare("SELECT count(*) as c FROM music_scrobbles WHERE uts >= ?").bind(startOfWeekUts).first<any>(),
    db.prepare("SELECT count(*) as c FROM music_scrobbles WHERE uts >= ?").bind(startOfMonthUts).first<any>(),
    db.prepare("SELECT count(DISTINCT artist) as c FROM music_scrobbles").first<any>(),
    db.prepare("SELECT count(DISTINCT album) as c FROM music_scrobbles WHERE album != ''").first<any>(),
    db.prepare(`
      SELECT artist, count(*) as count, max(image_url) as image_url
      FROM music_scrobbles
      GROUP BY artist
      ORDER BY count DESC
      LIMIT 10
    `).all<any>(),
    db.prepare(`
      SELECT album, artist, count(*) as count, max(image_url) as image_url
      FROM music_scrobbles
      WHERE album != ''
      GROUP BY album, artist
      ORDER BY count DESC
      LIMIT 10
    `).all<any>(),
    db.prepare("SELECT * FROM music_scrobbles ORDER BY uts DESC LIMIT 5").all<any>(),
    db.prepare("SELECT * FROM music_sync_runs ORDER BY id DESC LIMIT 1").first<any>(),
  ]);

  return c.json({
    total_scrobbles: totalRow?.c || 0,
    today_scrobbles: todayRow?.c || 0,
    week_scrobbles: weekRow?.c || 0,
    month_scrobbles: monthRow?.c || 0,
    unique_artists: uniqueArtistsRow?.c || 0,
    unique_albums: uniqueAlbumsRow?.c || 0,
    top_artists: topArtistsRows.results,
    top_albums: topAlbumsRows.results,
    recent_scrobbles: recentRows.results,
    last_sync: lastSyncRow || null,
  });
});

// 20. Music Settings API
app.get("/api/settings/music", async (c) => {
  const db = c.env.DB;
  await ensureMusicTables(db);
  const row = (await db.prepare("SELECT * FROM music_sync_settings WHERE id = 1").first()) as any;
  const username = row?.lastfm_username || c.env.LASTFM_USERNAME || "";
  const hasApiKey = !!(row?.lastfm_api_key || c.env.LASTFM_API_KEY);

  const host = c.req.header("host") || "your-worker.workers.dev";
  const proto = c.req.header("x-forwarded-proto") || "https";
  const webhookUrl = `${proto}://${host}/api/webhooks/music`;

  return c.json({
    lastfm_username: username,
    lastfm_api_key_set: hasApiKey,
    interval_minutes: row?.interval_minutes || 360,
    enabled: row?.enabled !== 0,
    webhook_url: webhookUrl,
  });
});

app.post("/api/settings/music", async (c) => {
  const db = c.env.DB;
  await ensureMusicTables(db);
  const body = await c.req.json().catch(() => ({}));

  const username = typeof body.lastfm_username === "string" ? body.lastfm_username.trim() : "";
  const apiKey = typeof body.lastfm_api_key === "string" ? body.lastfm_api_key.trim() : "";
  const interval = typeof body.interval_minutes === "number" ? body.interval_minutes : 360;
  const enabled = body.enabled === false ? 0 : 1;

  const existing = (await db.prepare("SELECT * FROM music_sync_settings WHERE id = 1").first()) as any;

  if (existing) {
    const finalApiKey = apiKey !== "" ? apiKey : existing.lastfm_api_key;
    await db.prepare(`
      UPDATE music_sync_settings
      SET lastfm_username = ?, lastfm_api_key = ?, interval_minutes = ?, enabled = ?, updated_at = CURRENT_TIMESTAMP
      WHERE id = 1
    `).bind(username, finalApiKey, interval, enabled).run();
  } else {
    await db.prepare(`
      INSERT INTO music_sync_settings (id, lastfm_username, lastfm_api_key, interval_minutes, enabled)
      VALUES (1, ?, ?, ?, ?)
    `).bind(username, apiKey, interval, enabled).run();
  }

  return c.json({ ok: true, message: "Müzik ayarları kaydedildi" });
});

// 21. Last.fm Sync API
app.post("/api/music/sync-lastfm", async (c) => {
  const db = c.env.DB;
  await ensureMusicTables(db);

  let body: any = {};
  try {
    body = await c.req.json();
  } catch (e) {
    // empty body is fine
  }

  const creds = await getLastFmCredentials(db, c.env);
  const username = (body.username || creds.username).trim();
  const apiKey = (body.apiKey || creds.apiKey).trim();

  if (!username) {
    return c.json({ ok: false, error: "Last.fm kullanıcı adı tanımlanmamış." }, 400);
  }
  if (!apiKey) {
    return c.json({ ok: false, error: "Last.fm API Key tanımlanmamış. Ayarlardan Last.fm API Key girin." }, 400);
  }

  const limit = Math.min(Math.max(parseInt(body.limit || "200", 10), 10), 200);
  const page = Math.max(parseInt(body.page || "1", 10), 1);
  const maxPages = body.allPages ? Math.min(parseInt(body.maxPages || "10", 10), 20) : 1;
  const incremental = body.incremental !== false && !body.page;

  let fromUts: number | null = null;
  if (body.fromUts) {
    fromUts = Number(body.fromUts);
  } else if (incremental) {
    // Look for latest UTS from lastfm to only fetch new scrobbles
    const maxRow = (await db.prepare("SELECT max(uts) as max_uts FROM music_scrobbles WHERE source = 'lastfm'").first()) as any;
    if (maxRow?.max_uts) {
      fromUts = Number(maxRow.max_uts) + 1;
    }
  }

  let totalInserted = 0;
  let totalUpdated = 0;
  let totalProcessed = 0;
  let totalPagesReported = 1;

  try {
    for (let p = page; p < page + maxPages; p++) {
      const url = new URL("https://ws.audioscrobbler.com/2.0/");
      url.searchParams.set("method", "user.getrecenttracks");
      url.searchParams.set("user", username);
      url.searchParams.set("api_key", apiKey);
      url.searchParams.set("format", "json");
      url.searchParams.set("limit", limit.toString());
      url.searchParams.set("page", p.toString());
      url.searchParams.set("extended", "1");
      if (fromUts) {
        url.searchParams.set("from", fromUts.toString());
      }

      const res = await fetch(url.toString(), {
        headers: { "User-Agent": "PersonalRecordsApp/1.0" },
      });

      if (!res.ok) {
        const txt = await res.text();
        throw new Error(`Last.fm API Hatası: ${res.status} ${txt}`);
      }

      const data: any = await res.json();
      if (data.error) {
        throw new Error(`Last.fm API Hatası (${data.error}): ${data.message}`);
      }

      const recentTracks = data.recenttracks;
      if (!recentTracks) break;

      totalPagesReported = parseInt(recentTracks["@attr"]?.totalPages || "1", 10);
      const rawTracks = recentTracks.track;
      const tracks = Array.isArray(rawTracks) ? rawTracks : (rawTracks ? [rawTracks] : []);

      if (tracks.length === 0) break;

      for (const t of tracks) {
        // Skip current playback (nowplaying="true" does not have date)
        if (t["@attr"]?.nowplaying === "true") continue;

        const uts = parseInt(t.date?.uts, 10);
        if (!uts || isNaN(uts)) continue;

        const artist = typeof t.artist === "object" ? (t.artist.name || t.artist["#text"] || "") : (t.artist || "");
        const track = (t.name || "").trim();
        const album = typeof t.album === "object" ? (t.album["#text"] || t.album.name || "") : (t.album || "");
        const albumArtist = "";
        const duration = 0;
        const imageUrl = extractLastFmImage(t.image);
        const originUrl = t.url || "";
        const listenedAt = new Date(uts * 1000).toISOString();
        const source = "lastfm";
        const id = "lfm_" + hashStr(`${artist}:${track}:${uts}`);
        const rawJson = JSON.stringify(t);

        if (!artist || !track) continue;

        const existing = await db
          .prepare("SELECT id FROM music_scrobbles WHERE artist = ? AND track = ? AND uts = ?")
          .bind(artist, track, uts)
          .first();

        await db.prepare(`
          INSERT INTO music_scrobbles (id, track, artist, album, album_artist, duration, listened_at, uts, source, origin_url, image_url, raw_json)
          VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
          ON CONFLICT(artist, track, uts) DO UPDATE SET
            album = CASE WHEN excluded.album != '' THEN excluded.album ELSE music_scrobbles.album END,
            image_url = CASE WHEN excluded.image_url != '' THEN excluded.image_url ELSE music_scrobbles.image_url END,
            origin_url = CASE WHEN excluded.origin_url != '' THEN excluded.origin_url ELSE music_scrobbles.origin_url END
        `).bind(
          id, track, artist, album, albumArtist, duration, listenedAt, uts, source, originUrl, imageUrl, rawJson
        ).run();

        if (existing) totalUpdated++;
        else totalInserted++;
        totalProcessed++;
      }

      if (p >= totalPagesReported) break;
    }

    await db.prepare(`
      INSERT INTO music_sync_runs (source, status, inserted, updated, message)
      VALUES ('lastfm', 'success', ?, ?, ?)
    `).bind(
      totalInserted,
      totalUpdated,
      `Last.fm senkronizasyonu tamamlandı: ${totalInserted} yeni, ${totalUpdated} güncellendi.`
    ).run();

    return c.json({
      ok: true,
      inserted: totalInserted,
      updated: totalUpdated,
      totalProcessed,
      totalPages: totalPagesReported,
      message: `Last.fm senkronizasyonu başarılı: ${totalInserted} yeni scrobble eklendi.`
    });
  } catch (err: any) {
    await db.prepare(`
      INSERT INTO music_sync_runs (source, status, inserted, updated, message)
      VALUES ('lastfm', 'error', ?, ?, ?)
    `).bind(totalInserted, totalUpdated, err.message).run();

    return c.json({ ok: false, error: err.message }, 500);
  }
});

// 22. Music Import API (CSV or JSON)
app.post("/api/music/import", async (c) => {
  const db = c.env.DB;
  await ensureMusicTables(db);

  const contentType = c.req.header("content-type") || "";
  let items: any[] = [];

  if (contentType.includes("application/json")) {
    const body = await c.req.json().catch(() => null);
    if (Array.isArray(body)) {
      items = body;
    } else if (body && Array.isArray(body.items)) {
      items = body.items;
    }
  } else {
    // CSV parsing
    const text = await c.req.text();
    const lines = text.split(/\r?\n/).filter((l) => l.trim().length > 0);
    if (lines.length > 0) {
      const headerLine = lines[0].toLowerCase();
      const hasHeader = headerLine.includes("artist") || headerLine.includes("track") || headerLine.includes("title");
      const startIdx = hasHeader ? 1 : 0;

      for (let i = startIdx; i < lines.length; i++) {
        const parts = lines[i].split(",").map((p) => p.trim().replace(/^"|"$/g, ""));
        if (parts.length >= 3) {
          let artist = "";
          let track = "";
          let album = "";
          let timestampStr = "";

          if (parts.length >= 4) {
            artist = parts[0];
            album = parts[1];
            track = parts[2];
            timestampStr = parts[3];
          } else {
            artist = parts[0];
            track = parts[1];
            timestampStr = parts[2];
          }

          let uts = Number(timestampStr);
          if (!uts || isNaN(uts)) {
            const parsed = new Date(timestampStr);
            if (!isNaN(parsed.getTime())) {
              uts = Math.floor(parsed.getTime() / 1000);
            } else {
              uts = Math.floor(Date.now() / 1000) - (lines.length - i) * 180;
            }
          }

          items.push({
            artist,
            track,
            album,
            uts,
            source: "csv_import",
          });
        }
      }
    }
  }

  if (items.length === 0) {
    return c.json({ ok: false, error: "İçe aktarılacak scrobble verisi bulunamadı." }, 400);
  }

  let inserted = 0;
  let updated = 0;

  for (const item of items) {
    const artist = (item.artist || "").trim();
    const track = (item.track || item.title || "").trim();
    const album = (item.album || "").trim();
    const albumArtist = (item.album_artist || item.albumArtist || "").trim();
    const duration = Number(item.duration || 0);
    const imageUrl = (item.image_url || item.imageUrl || "").trim();
    const originUrl = (item.origin_url || item.originUrl || "").trim();

    let uts = Number(item.uts || item.timestamp || 0);
    if (!uts || uts <= 0) {
      uts = Math.floor(Date.now() / 1000);
    }
    const listenedAt = item.listened_at || new Date(uts * 1000).toISOString();
    const source = item.source || "import";

    if (!artist || !track) continue;

    const id = "imp_" + hashStr(`${artist}:${track}:${uts}`);
    const rawJson = JSON.stringify(item);

    const existing = await db
      .prepare("SELECT id FROM music_scrobbles WHERE artist = ? AND track = ? AND uts = ?")
      .bind(artist, track, uts)
      .first();

    await db.prepare(`
      INSERT INTO music_scrobbles (id, track, artist, album, album_artist, duration, listened_at, uts, source, origin_url, image_url, raw_json)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      ON CONFLICT(artist, track, uts) DO UPDATE SET
        album = CASE WHEN excluded.album != '' THEN excluded.album ELSE music_scrobbles.album END,
        image_url = CASE WHEN excluded.image_url != '' THEN excluded.image_url ELSE music_scrobbles.image_url END
    `).bind(
      id, track, artist, album, albumArtist, duration, listenedAt, uts, source, originUrl, imageUrl, rawJson
    ).run();

    if (existing) updated++;
    else inserted++;
  }

  return c.json({
    ok: true,
    total: items.length,
    inserted,
    updated,
    message: `${items.length} scrobble başarıyla içe aktarıldı (${inserted} yeni, ${updated} güncellendi).`
  });
});


// Simple Tag Extractor helper
function extractTag(xml: string, tag: string): string {
  const regex = new RegExp(`<${tag}[^>]*>(?:<!\\[CDATA\\[([\\s\\S]*?)\\]\\]>|([\\s\\S]*?))<\\/${tag}>`, "i");
  const match = xml.match(regex);
  if (!match) return "";
  const val = match[1] !== undefined ? match[1] : match[2];
  return val.trim();
}

function hashStr(str: string): string {
  let hash = 0;
  for (let i = 0; i < str.length; i++) {
    hash = (hash << 5) - hash + str.charCodeAt(i);
    hash |= 0;
  }
  return Math.abs(hash).toString(16);
}

export default {
  fetch: app.fetch,
  async scheduled(event: ScheduledEvent, env: Env, ctx: ExecutionContext) {
    // Scheduled Cron Trigger: Trigger Goodreads and Letterboxd RSS Sync
    try {
      const grReq = new Request("http://localhost/api/sync/goodreads", { method: "POST" });
      await app.fetch(grReq, env, ctx);
      const lbReq = new Request("http://localhost/api/sync/letterboxd", { method: "POST" });
      await app.fetch(lbReq, env, ctx);
      const creds = await getFatSecretCredentials(env.DB, env);
      if (creds.consumerKey && creds.consumerSecret) {
        const fsReq = new Request("http://localhost/api/sync/fatsecret", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ days: 3 }),
        });
        await app.fetch(fsReq, env, ctx);
      }
      const musicCreds = await getLastFmCredentials(env.DB, env);
      if (musicCreds.username && musicCreds.apiKey) {
        const musicReq = new Request("http://localhost/api/music/sync-lastfm", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ incremental: true }),
        });
        await app.fetch(musicReq, env, ctx);
      }
    } catch (err) {
      console.error("Scheduled sync error:", err);
    }
  },
};
