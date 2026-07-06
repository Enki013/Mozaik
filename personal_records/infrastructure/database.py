import sqlite3

from personal_records.config import DB_PATH

def db() -> sqlite3.Connection:
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    return conn

def init_db() -> None:
    with db() as conn:
        conn.executescript(
            """
            create table if not exists goodreads_books (
              book_id text primary key,
              title text not null,
              author text,
              isbn text,
              isbn13 text,
              my_rating integer,
              average_rating real,
              publisher text,
              binding text,
              number_of_pages integer,
              year_published integer,
              original_publication_year integer,
              date_read text,
              date_added text,
              bookshelves text,
              exclusive_shelf text,
              my_review text,
              raw_json text not null,
              imported_at text not null default current_timestamp
            );

            create table if not exists fatsecret_tokens (
              id integer primary key check (id = 1),
              oauth_token text not null,
              oauth_token_secret text not null,
              updated_at text not null default current_timestamp
            );

            create table if not exists fatsecret_food_entries (
              food_entry_id text primary key,
              date_int integer,
              meal text,
              food_entry_name text,
              food_entry_description text,
              calories real,
              carbohydrate real,
              protein real,
              fat real,
              raw_json text not null,
              synced_at text not null default current_timestamp
            );

            create table if not exists fatsecret_sync_runs (
              id integer primary key autoincrement,
              source text not null,
              status text not null,
              inserted integer not null default 0,
              updated integer not null default 0,
              message text,
              ran_at text not null default current_timestamp
            );

            create table if not exists oauth_temp_tokens (
              oauth_token text primary key,
              oauth_token_secret text not null,
              created_at integer not null
            );

            create table if not exists goodreads_sync_settings (
              id integer primary key check (id = 1),
              feed_url text,
              interval_minutes integer not null default 360,
              enabled integer not null default 0,
              updated_at text not null default current_timestamp
            );

            create table if not exists goodreads_sync_runs (
              id integer primary key autoincrement,
              source text not null,
              status text not null,
              inserted integer not null default 0,
              updated integer not null default 0,
              message text,
              ran_at text not null default current_timestamp
            );

            create table if not exists letterboxd_films (
              film_key text primary key,
              letterboxd_uri text,
              name text not null,
              year integer,
              rating real,
              watched_date text,
              diary_date text,
              rewatch text,
              tags text,
              review text,
              source text not null,
              raw_json text not null,
              imported_at text not null default current_timestamp
            );

            create table if not exists letterboxd_sync_settings (
              id integer primary key check (id = 1),
              feed_url text,
              interval_minutes integer not null default 360,
              enabled integer not null default 0,
              updated_at text not null default current_timestamp
            );

            create table if not exists letterboxd_sync_runs (
              id integer primary key autoincrement,
              source text not null,
              status text not null,
              inserted integer not null default 0,
              updated integer not null default 0,
              message text,
              ran_at text not null default current_timestamp
            );
            """
        )
