import { Hono } from "hono";
import { cors } from "hono/cors";

export interface Env {
  DB: D1Database;
  FATSECRET_CONSUMER_KEY?: string;
  FATSECRET_CONSUMER_SECRET?: string;
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

// 2. Dashboard
app.get("/api/dashboard", async (c) => {
  const db = c.env.DB;

  const [booksCount, filmsCount, foodsCount, notifCount] = await Promise.all([
    db.prepare("SELECT count(*) as c FROM goodreads_books").first<any>(),
    db.prepare("SELECT count(*) as c FROM letterboxd_films").first<any>(),
    db.prepare("SELECT count(*) as c FROM fatsecret_food_entries").first<any>(),
    db.prepare("SELECT count(*) as c FROM notifications").first<any>(),
  ]);

  const today = todayDateInt();
  const todayFood = await db
    .prepare("SELECT count(*) as count, coalesce(sum(calories), 0) as calories FROM fatsecret_food_entries WHERE date_int = ?")
    .bind(today)
    .first<any>();

  const token = await db.prepare("SELECT id FROM fatsecret_tokens WHERE id = 1").first();
  const grSettings = await db.prepare("SELECT feed_url FROM goodreads_sync_settings WHERE id = 1").first<any>();
  const lbSettings = await db.prepare("SELECT feed_url FROM letterboxd_sync_settings WHERE id = 1").first<any>();

  const [fatRun, grRun, lbRun] = await Promise.all([
    db.prepare("SELECT * FROM fatsecret_sync_runs ORDER BY id DESC LIMIT 1").first(),
    db.prepare("SELECT * FROM goodreads_sync_runs ORDER BY id DESC LIMIT 1").first(),
    db.prepare("SELECT * FROM letterboxd_sync_runs ORDER BY id DESC LIMIT 1").first(),
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
    },
    today: {
      date: dateIntToISO(today),
      fatsecret_entry_count: todayFood?.count || 0,
      fatsecret_calories: Math.round((todayFood?.calories || 0) * 100) / 100,
    },
    connections: {
      fatsecret: !!token,
      goodreads_rss: !!(grSettings?.feed_url),
      letterboxd_rss: !!(lbSettings?.feed_url),
    },
    last_sync: {
      fatsecret: fatRun || null,
      goodreads: grRun || null,
      letterboxd: lbRun || null,
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

  if (!settings?.feed_url) {
    return c.json({ inserted: 0, updated: 0, message: "Goodreads RSS URL kayitli degil" }, 400);
  }

  try {
    const resp = await fetch(settings.feed_url, {
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
  const [books, films, foods, summary] = await Promise.all([
    c.env.DB.prepare("SELECT * FROM goodreads_books LIMIT 10000").all(),
    c.env.DB.prepare("SELECT * FROM letterboxd_films LIMIT 10000").all(),
    c.env.DB.prepare("SELECT * FROM fatsecret_food_entries LIMIT 10000").all(),
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
  const tables = [
    "user_settings",
    "goodreads_sync_settings",
    "letterboxd_sync_settings",
    "fatsecret_tokens",
    "goodreads_books",
    "letterboxd_films",
    "fatsecret_food_entries",
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
      .first<any>();

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
    } catch (err) {
      console.error("Scheduled sync error:", err);
    }
  },
};
