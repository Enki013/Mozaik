-- Cloudflare D1 Database Schema for Personal Records

CREATE TABLE IF NOT EXISTS goodreads_books (
  book_id TEXT PRIMARY KEY,
  title TEXT NOT NULL,
  author TEXT,
  isbn TEXT,
  isbn13 TEXT,
  my_rating INTEGER,
  average_rating REAL,
  publisher TEXT,
  binding TEXT,
  number_of_pages INTEGER,
  year_published INTEGER,
  original_publication_year INTEGER,
  date_read TEXT,
  date_added TEXT,
  bookshelves TEXT,
  exclusive_shelf TEXT,
  my_review TEXT,
  image_url TEXT,
  raw_json TEXT NOT NULL,
  imported_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS fatsecret_tokens (
  id INTEGER PRIMARY KEY CHECK (id = 1),
  oauth_token TEXT NOT NULL,
  oauth_token_secret TEXT NOT NULL,
  updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS fatsecret_food_entries (
  food_entry_id TEXT PRIMARY KEY,
  date_int INTEGER,
  meal TEXT,
  food_entry_name TEXT,
  food_entry_description TEXT,
  calories REAL,
  carbohydrate REAL,
  protein REAL,
  fat REAL,
  raw_json TEXT NOT NULL,
  synced_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS fatsecret_sync_runs (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  source TEXT NOT NULL,
  status TEXT NOT NULL,
  inserted INTEGER NOT NULL DEFAULT 0,
  updated INTEGER NOT NULL DEFAULT 0,
  message TEXT,
  ran_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS fatsecret_scanned_days (
  date_int INTEGER PRIMARY KEY,
  scanned_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS oauth_temp_tokens (
  oauth_token TEXT PRIMARY KEY,
  oauth_token_secret TEXT NOT NULL,
  created_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS goodreads_sync_settings (
  id INTEGER PRIMARY KEY CHECK (id = 1),
  feed_url TEXT,
  interval_minutes INTEGER NOT NULL DEFAULT 360,
  enabled INTEGER NOT NULL DEFAULT 0,
  updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS goodreads_sync_runs (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  source TEXT NOT NULL,
  status TEXT NOT NULL,
  inserted INTEGER NOT NULL DEFAULT 0,
  updated INTEGER NOT NULL DEFAULT 0,
  message TEXT,
  ran_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS letterboxd_films (
  film_key TEXT PRIMARY KEY,
  letterboxd_uri TEXT,
  name TEXT NOT NULL,
  year INTEGER,
  rating REAL,
  watched_date TEXT,
  diary_date TEXT,
  rewatch TEXT,
  tags TEXT,
  review TEXT,
  image_url TEXT,
  source TEXT NOT NULL,
  raw_json TEXT NOT NULL,
  imported_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS letterboxd_sync_settings (
  id INTEGER PRIMARY KEY CHECK (id = 1),
  feed_url TEXT,
  interval_minutes INTEGER NOT NULL DEFAULT 360,
  enabled INTEGER NOT NULL DEFAULT 0,
  updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS letterboxd_sync_runs (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  source TEXT NOT NULL,
  status TEXT NOT NULL,
  inserted INTEGER NOT NULL DEFAULT 0,
  updated INTEGER NOT NULL DEFAULT 0,
  message TEXT,
  ran_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS user_settings (
  key TEXT PRIMARY KEY,
  value TEXT NOT NULL,
  updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT OR IGNORE INTO user_settings (key, value) VALUES ('daily_calorie_target', '2200');

CREATE TABLE IF NOT EXISTS notifications (
  id TEXT PRIMARY KEY,
  package_name TEXT NOT NULL,
  app_name TEXT,
  title TEXT,
  text TEXT,
  sub_text TEXT,
  big_text TEXT,
  category TEXT,
  post_time TEXT NOT NULL,
  notification_key TEXT,
  is_ongoing INTEGER DEFAULT 0,
  raw_json TEXT NOT NULL,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_notifications_post_time ON notifications(post_time DESC);
CREATE INDEX IF NOT EXISTS idx_notifications_package ON notifications(package_name);

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
);
CREATE INDEX IF NOT EXISTS idx_health_records_date ON health_records(date_iso DESC, start_time DESC);
CREATE INDEX IF NOT EXISTS idx_health_records_type ON health_records(record_type);
CREATE INDEX IF NOT EXISTS idx_health_records_category ON health_records(category);

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
);
CREATE INDEX IF NOT EXISTS idx_music_scrobbles_uts ON music_scrobbles(uts DESC);
CREATE INDEX IF NOT EXISTS idx_music_scrobbles_artist ON music_scrobbles(artist);
CREATE UNIQUE INDEX IF NOT EXISTS idx_music_scrobbles_unique ON music_scrobbles(artist, track, uts);

CREATE TABLE IF NOT EXISTS music_sync_settings (
  id INTEGER PRIMARY KEY CHECK (id = 1),
  lastfm_username TEXT,
  lastfm_api_key TEXT,
  listenbrainz_username TEXT,
  listenbrainz_token TEXT,
  statsfm_username TEXT,
  interval_minutes INTEGER NOT NULL DEFAULT 360,
  enabled INTEGER NOT NULL DEFAULT 1,
  web_scrobbler_enabled INTEGER NOT NULL DEFAULT 0,
  updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS music_sync_runs (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  source TEXT NOT NULL,
  status TEXT NOT NULL,
  inserted INTEGER NOT NULL DEFAULT 0,
  updated INTEGER NOT NULL DEFAULT 0,
  message TEXT,
  ran_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Trakt.tv Integration Tables
CREATE TABLE IF NOT EXISTS trakt_sync_settings (
  id INTEGER PRIMARY KEY CHECK (id = 1),
  username TEXT,
  client_id TEXT,
  sync_movies INTEGER NOT NULL DEFAULT 1,
  sync_episodes INTEGER NOT NULL DEFAULT 1,
  sync_ratings INTEGER NOT NULL DEFAULT 1,
  interval_minutes INTEGER NOT NULL DEFAULT 360,
  enabled INTEGER NOT NULL DEFAULT 1,
  updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS trakt_history (
  history_id TEXT PRIMARY KEY,
  media_type TEXT NOT NULL,          -- 'movie' veya 'episode'
  title TEXT NOT NULL,               -- Film adı veya Dizi adı
  year INTEGER,
  season INTEGER,                    -- Dizi ise sezon
  episode_number INTEGER,            -- Dizi ise bölüm no
  episode_title TEXT,                -- Bölüm adı
  watched_at TEXT NOT NULL,          -- ISO 8601 UTC
  trakt_id INTEGER,
  imdb_id TEXT,
  tmdb_id INTEGER,
  user_rating INTEGER,               -- 1 - 10
  image_url TEXT,
  raw_json TEXT NOT NULL,
  imported_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_trakt_history_watched ON trakt_history(watched_at DESC);
CREATE INDEX IF NOT EXISTS idx_trakt_history_type ON trakt_history(media_type);

CREATE TABLE IF NOT EXISTS trakt_sync_runs (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  source TEXT NOT NULL,
  status TEXT NOT NULL,
  inserted INTEGER NOT NULL DEFAULT 0,
  updated INTEGER NOT NULL DEFAULT 0,
  message TEXT,
  ran_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- MDBList Integration Tables
CREATE TABLE IF NOT EXISTS mdblist_settings (
  id INTEGER PRIMARY KEY CHECK (id = 1),
  api_key TEXT,
  sync_ratings_enabled INTEGER NOT NULL DEFAULT 1,
  sync_lists_enabled INTEGER NOT NULL DEFAULT 0,
  updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS mdblist_ratings_cache (
  id TEXT PRIMARY KEY,               -- imdb_id veya 'tmdb_' + tmdb_id
  imdb_id TEXT,
  tmdb_id INTEGER,
  media_type TEXT NOT NULL,          -- 'movie' veya 'show'
  title TEXT NOT NULL,
  year INTEGER,
  score_imdb REAL,                   -- IMDb puanı (örn: 8.4)
  score_tmdb INTEGER,                -- TMDb % puanı (örn: 82)
  score_trakt INTEGER,               -- Trakt % puanı (örn: 80)
  score_letterboxd REAL,             -- Letterboxd puanı (örn: 4.1)
  score_tomatoes INTEGER,            -- Rotten Tomatoes Tomatometer % (örn: 94)
  score_audience INTEGER,            -- Rotten Tomatoes Audience % (örn: 91)
  score_metacritic INTEGER,          -- Metacritic Metascore (örn: 84)
  certification TEXT,                -- PG-13, R vb.
  poster TEXT,                       -- Afiş görseli URL
  description TEXT,                  -- Film / dizi konusu / özeti
  user_rating REAL,                  -- Kullanıcının verdiği puan (5 yıldız ölçeğinde)
  rated_at TEXT,                     -- Puanlama / izleme tarihi
  ratings_json TEXT,                 -- Ham puan dökümü JSON
  updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_mdblist_ratings_imdb ON mdblist_ratings_cache(imdb_id);
CREATE INDEX IF NOT EXISTS idx_mdblist_ratings_tmdb ON mdblist_ratings_cache(tmdb_id);

CREATE TABLE IF NOT EXISTS mdblist_sync_runs (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  source TEXT NOT NULL,
  status TEXT NOT NULL,
  inserted INTEGER NOT NULL DEFAULT 0,
  updated INTEGER NOT NULL DEFAULT 0,
  message TEXT,
  ran_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Simkl Integration Tables
CREATE TABLE IF NOT EXISTS simkl_sync_settings (
  id INTEGER PRIMARY KEY CHECK (id = 1),
  client_id TEXT,
  access_token TEXT,
  user_id TEXT,
  sync_movies INTEGER NOT NULL DEFAULT 1,
  sync_shows INTEGER NOT NULL DEFAULT 1,
  sync_anime INTEGER NOT NULL DEFAULT 1,
  sync_ratings INTEGER NOT NULL DEFAULT 1,
  interval_minutes INTEGER NOT NULL DEFAULT 360,
  enabled INTEGER NOT NULL DEFAULT 1,
  updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS simkl_history (
  id TEXT PRIMARY KEY,               -- 'simkl_movie_1234', 'simkl_show_5678', 'simkl_anime_9101'
  simkl_id INTEGER NOT NULL,
  item_type TEXT NOT NULL,          -- 'movie', 'show', 'anime'
  title TEXT NOT NULL,
  year INTEGER,
  last_ep_watched TEXT,             -- Dizi/Anime için 'S01E12'
  status TEXT NOT NULL DEFAULT 'completed', -- 'completed', 'watching' vb.
  user_rating INTEGER,              -- 1 - 10
  poster_url TEXT,
  imdb_id TEXT,
  tmdb_id INTEGER,
  mal_id TEXT,
  raw_json TEXT NOT NULL,
  watched_at TEXT NOT NULL,         -- ISO 8601 UTC
  imported_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_simkl_history_watched ON simkl_history(watched_at DESC);
CREATE INDEX IF NOT EXISTS idx_simkl_history_type ON simkl_history(item_type);

CREATE TABLE IF NOT EXISTS simkl_sync_runs (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  source TEXT NOT NULL,
  status TEXT NOT NULL,
  inserted INTEGER NOT NULL DEFAULT 0,
  updated INTEGER NOT NULL DEFAULT 0,
  message TEXT,
  ran_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);



