package sqlite

import (
	"context"
	"database/sql"
	"encoding/json"
	"fmt"
	"strings"
	"time"

	_ "github.com/mattn/go-sqlite3"
)

type Store struct {
	db *sql.DB
}

type SyncResult struct {
	Inserted int    `json:"inserted"`
	Updated  int    `json:"updated"`
	Message  string `json:"message,omitempty"`
}

type SyncSettings struct {
	FeedURL         string `json:"feed_url"`
	IntervalMinutes int    `json:"interval_minutes"`
	Enabled         bool   `json:"enabled"`
}

type GoodreadsBook struct {
	BookID                  string  `json:"book_id"`
	Title                   string  `json:"title"`
	Author                  string  `json:"author"`
	ISBN                    string  `json:"isbn,omitempty"`
	ISBN13                  string  `json:"isbn13,omitempty"`
	MyRating                int     `json:"my_rating"`
	AverageRating           float64 `json:"average_rating"`
	Publisher               string  `json:"publisher,omitempty"`
	Binding                 string  `json:"binding,omitempty"`
	NumberOfPages           int     `json:"number_of_pages,omitempty"`
	YearPublished           int     `json:"year_published"`
	OriginalPublicationYear int     `json:"original_publication_year,omitempty"`
	DateRead                string  `json:"date_read"`
	DateAdded               string  `json:"date_added"`
	Bookshelves             string  `json:"bookshelves"`
	ExclusiveShelf          string  `json:"exclusive_shelf"`
	MyReview                string  `json:"my_review,omitempty"`
	ImageURL                string  `json:"image_url,omitempty"`
	RawJSON                 string  `json:"raw_json,omitempty"`
	ImportedAt              string  `json:"imported_at,omitempty"`
}

type LetterboxdFilm struct {
	FilmKey       string  `json:"film_key"`
	LetterboxdURI string  `json:"letterboxd_uri"`
	Name          string  `json:"name"`
	Year          int     `json:"year"`
	Rating        float64 `json:"rating"`
	WatchedDate   string  `json:"watched_date"`
	DiaryDate     string  `json:"diary_date"`
	Rewatch       string  `json:"rewatch"`
	Tags          string  `json:"tags"`
	Review        string  `json:"review"`
	ImageURL      string  `json:"image_url,omitempty"`
	Source        string  `json:"source"`
	RawJSON       string  `json:"raw_json,omitempty"`
	ImportedAt    string  `json:"imported_at,omitempty"`
}

type FoodEntry struct {
	FoodEntryID          string  `json:"food_entry_id"`
	DateInt              int     `json:"date_int"`
	Date                 string  `json:"date,omitempty"`
	Meal                 string  `json:"meal"`
	FoodEntryName        string  `json:"food_entry_name"`
	FoodEntryDescription string  `json:"food_entry_description"`
	Calories             float64 `json:"calories"`
	Carbohydrate         float64 `json:"carbohydrate"`
	Protein              float64 `json:"protein"`
	Fat                  float64 `json:"fat"`
	RawJSON              string  `json:"raw_json,omitempty"`
	SyncedAt             string  `json:"synced_at,omitempty"`
}

type DailySummary struct {
	DateInt    int     `json:"date_int"`
	Date       string  `json:"date"`
	EntryCount int     `json:"entry_count"`
	Calories   float64 `json:"calories"`
}

type GoodreadsStats struct {
	Total             int     `json:"total"`
	Read              int     `json:"read"`
	ToRead            int     `json:"to_read"`
	CurrentlyReading  int     `json:"currently_reading"`
	Rated             int     `json:"rated"`
	Authors           int     `json:"authors"`
	TotalPages        int     `json:"total_pages"`
	AverageMyRating   float64 `json:"average_my_rating"`
	AverageBookRating float64 `json:"average_book_rating"`
	LastRead          string  `json:"last_read"`
	LastAdded         string  `json:"last_added"`
}

type LetterboxdStats struct {
	Total         int     `json:"total"`
	Rated         int     `json:"rated"`
	AverageRating float64 `json:"average_rating"`
	OldestYear    int     `json:"oldest_year"`
	NewestYear    int     `json:"newest_year"`
	RSSCount      int     `json:"rss_count"`
	CSVCount      int     `json:"csv_count"`
	LastWatched   string  `json:"last_watched"`
}

type FatSecretStats struct {
	Entries           int     `json:"entries"`
	LoggedDays        int     `json:"logged_days"`
	TotalCalories     float64 `json:"total_calories"`
	AverageCalories   float64 `json:"average_calories"`
	TodayEntries      int     `json:"today_entries"`
	TodayCalories     float64 `json:"today_calories"`
	TotalCarbohydrate float64 `json:"total_carbohydrate"`
	TotalProtein      float64 `json:"total_protein"`
	TotalFat          float64 `json:"total_fat"`
	LastLoggedDate    string  `json:"last_logged_date"`
}

type SyncRun struct {
	ID       int    `json:"id"`
	Source   string `json:"source"`
	Status   string `json:"status"`
	Inserted int    `json:"inserted"`
	Updated  int    `json:"updated"`
	Message  string `json:"message"`
	RanAt    string `json:"ran_at"`
}

type OAuthToken struct {
	Token  string
	Secret string
}

func Open(path string) (*Store, error) {
	db, err := sql.Open("sqlite3", path+"?_busy_timeout=5000")
	if err != nil {
		return nil, err
	}
	return &Store{db: db}, nil
}

func (s *Store) Close() error {
	return s.db.Close()
}

func (s *Store) Init(ctx context.Context) error {
	if _, err := s.db.ExecContext(ctx, schema); err != nil {
		return err
	}
	_, _ = s.db.ExecContext(ctx, "insert or ignore into fatsecret_scanned_days (date_int) select distinct date_int from fatsecret_food_entries")
	_, _ = s.db.ExecContext(ctx, "alter table goodreads_books add column image_url text")
	_, _ = s.db.ExecContext(ctx, "alter table letterboxd_films add column image_url text")
	_, _ = s.db.ExecContext(ctx, "create table if not exists user_settings (key text primary key, value text not null, updated_at text not null default current_timestamp)")
	_, _ = s.db.ExecContext(ctx, "insert or ignore into user_settings (key, value) values ('daily_calorie_target', '2200')")
	return nil
}

func (s *Store) RawDB() *sql.DB {
	return s.db
}

func (s *Store) Counts(ctx context.Context) (map[string]int, error) {
	queries := map[string]string{
		"goodreads_books":        "select count(*) from goodreads_books",
		"letterboxd_films":       "select count(*) from letterboxd_films",
		"fatsecret_food_entries": "select count(*) from fatsecret_food_entries",
		"read_count":             "select count(*) from goodreads_books where exclusive_shelf = 'read'",
		"to_read_count":          "select count(*) from goodreads_books where exclusive_shelf = 'to-read'",
		"rated_count":            "select count(*) from goodreads_books where coalesce(my_rating, 0) > 0",
		"letterboxd_rated_count": "select count(*) from letterboxd_films where coalesce(rating, 0) > 0",
	}
	result := map[string]int{}
	for key, query := range queries {
		var count int
		if err := s.db.QueryRowContext(ctx, query).Scan(&count); err != nil {
			return nil, err
		}
		result[key] = count
	}
	return result, nil
}

func scanString(value sql.NullString) string {
	if value.Valid {
		return value.String
	}
	return ""
}

func scanInt(value sql.NullInt64) int {
	if value.Valid {
		return int(value.Int64)
	}
	return 0
}

func scanFloat(value sql.NullFloat64) float64 {
	if value.Valid {
		return value.Float64
	}
	return 0
}

func scanDateInt(value sql.NullInt64) string {
	if !value.Valid || value.Int64 <= 0 {
		return ""
	}
	return time.Date(1970, 1, 1, 0, 0, 0, 0, time.UTC).AddDate(0, 0, int(value.Int64)).Format("2006-01-02")
}

func (s *Store) GoodreadsShelves(ctx context.Context) ([]string, error) {
	rows, err := s.db.QueryContext(ctx, "select distinct exclusive_shelf from goodreads_books where coalesce(exclusive_shelf, '') <> '' order by exclusive_shelf")
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var shelves []string
	for rows.Next() {
		var shelf string
		if err := rows.Scan(&shelf); err != nil {
			return nil, err
		}
		shelves = append(shelves, shelf)
	}
	return shelves, rows.Err()
}

func (s *Store) GoodreadsStats(ctx context.Context) (GoodreadsStats, error) {
	var stats GoodreadsStats
	var averageMyRating, averageBookRating sql.NullFloat64
	var lastRead, lastAdded sql.NullString
	err := s.db.QueryRowContext(ctx, `
select
  count(*),
  sum(case when exclusive_shelf = 'read' then 1 else 0 end),
  sum(case when exclusive_shelf = 'to-read' then 1 else 0 end),
  sum(case when exclusive_shelf = 'currently-reading' then 1 else 0 end),
  sum(case when coalesce(my_rating, 0) > 0 then 1 else 0 end),
  count(distinct nullif(author, '')),
  coalesce(sum(coalesce(number_of_pages, 0)), 0),
  avg(case when coalesce(my_rating, 0) > 0 then my_rating end),
  avg(case when coalesce(average_rating, 0) > 0 then average_rating end),
  max(nullif(date_read, '')),
  max(nullif(date_added, ''))
from goodreads_books`).Scan(
		&stats.Total,
		&stats.Read,
		&stats.ToRead,
		&stats.CurrentlyReading,
		&stats.Rated,
		&stats.Authors,
		&stats.TotalPages,
		&averageMyRating,
		&averageBookRating,
		&lastRead,
		&lastAdded,
	)
	stats.AverageMyRating = scanFloat(averageMyRating)
	stats.AverageBookRating = scanFloat(averageBookRating)
	stats.LastRead = scanString(lastRead)
	stats.LastAdded = scanString(lastAdded)
	return stats, err
}

func (s *Store) LetterboxdStats(ctx context.Context) (LetterboxdStats, error) {
	var stats LetterboxdStats
	var averageRating sql.NullFloat64
	var oldestYear, newestYear sql.NullInt64
	var lastWatched sql.NullString
	err := s.db.QueryRowContext(ctx, `
select
  count(*),
  sum(case when coalesce(rating, 0) > 0 then 1 else 0 end),
  avg(case when coalesce(rating, 0) > 0 then rating end),
  min(case when coalesce(year, 0) > 0 then year end),
  max(case when coalesce(year, 0) > 0 then year end),
  sum(case when source = 'rss' then 1 else 0 end),
  sum(case when source = 'csv' then 1 else 0 end),
  max(nullif(coalesce(watched_date, diary_date), ''))
from letterboxd_films`).Scan(
		&stats.Total,
		&stats.Rated,
		&averageRating,
		&oldestYear,
		&newestYear,
		&stats.RSSCount,
		&stats.CSVCount,
		&lastWatched,
	)
	stats.AverageRating = scanFloat(averageRating)
	stats.OldestYear = scanInt(oldestYear)
	stats.NewestYear = scanInt(newestYear)
	stats.LastWatched = scanString(lastWatched)
	return stats, err
}

func (s *Store) FatSecretStats(ctx context.Context, today int) (FatSecretStats, error) {
	var stats FatSecretStats
	var lastLogged sql.NullInt64
	err := s.db.QueryRowContext(ctx, `
select
  count(*),
  count(distinct date_int),
  coalesce(sum(calories), 0),
  coalesce(sum(carbohydrate), 0),
  coalesce(sum(protein), 0),
  coalesce(sum(fat), 0),
  max(date_int)
from fatsecret_food_entries`).Scan(
		&stats.Entries,
		&stats.LoggedDays,
		&stats.TotalCalories,
		&stats.TotalCarbohydrate,
		&stats.TotalProtein,
		&stats.TotalFat,
		&lastLogged,
	)
	if err != nil {
		return stats, err
	}
	if stats.LoggedDays > 0 {
		stats.AverageCalories = stats.TotalCalories / float64(stats.LoggedDays)
	}
	stats.LastLoggedDate = scanDateInt(lastLogged)
	stats.TodayEntries, stats.TodayCalories, err = s.TodayFood(ctx, today)
	return stats, err
}

func (s *Store) QueryGoodreadsBooks(ctx context.Context, search, shelf string, limit int) ([]GoodreadsBook, error) {
	if limit <= 0 || limit > 1000 {
		limit = 100
	}
	query := `select book_id, title, author, my_rating, average_rating, exclusive_shelf, bookshelves, date_read, date_added, year_published, coalesce(image_url, '')
from goodreads_books where 1 = 1`
	args := []any{}
	if search != "" {
		query += " and (lower(title) like ? or lower(author) like ?)"
		pattern := "%" + strings.ToLower(search) + "%"
		args = append(args, pattern, pattern)
	}
	if shelf != "" {
		query += " and exclusive_shelf = ?"
		args = append(args, shelf)
	}
	query += " order by coalesce(date_read, '') desc, coalesce(date_added, '') desc, title limit ?"
	args = append(args, limit)

	rows, err := s.db.QueryContext(ctx, query, args...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var books []GoodreadsBook
	for rows.Next() {
		var b GoodreadsBook
		var author, shelfValue, shelves, dateRead, dateAdded, img sql.NullString
		var myRating, year sql.NullInt64
		var avg sql.NullFloat64
		if err := rows.Scan(&b.BookID, &b.Title, &author, &myRating, &avg, &shelfValue, &shelves, &dateRead, &dateAdded, &year, &img); err != nil {
			return nil, err
		}
		b.Author = scanString(author)
		b.MyRating = scanInt(myRating)
		b.AverageRating = scanFloat(avg)
		b.ExclusiveShelf = scanString(shelfValue)
		b.Bookshelves = scanString(shelves)
		b.DateRead = scanString(dateRead)
		b.DateAdded = scanString(dateAdded)
		b.YearPublished = scanInt(year)
		b.ImageURL = scanString(img)
		books = append(books, b)
	}
	return books, rows.Err()
}

func (s *Store) UpsertGoodreadsBook(ctx context.Context, b GoodreadsBook) (string, error) {
	if b.BookID == "" || b.Title == "" {
		return "skipped", nil
	}
	var exists int
	_ = s.db.QueryRowContext(ctx, "select 1 from goodreads_books where book_id = ?", b.BookID).Scan(&exists)
	_, err := s.db.ExecContext(ctx, `
insert into goodreads_books (
  book_id, title, author, isbn, isbn13, my_rating, average_rating, publisher, binding,
  number_of_pages, year_published, original_publication_year, date_read, date_added,
  bookshelves, exclusive_shelf, my_review, raw_json, image_url
) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
on conflict(book_id) do update set
  title=excluded.title,
  author=excluded.author,
  isbn=excluded.isbn,
  isbn13=excluded.isbn13,
  my_rating=coalesce(excluded.my_rating, goodreads_books.my_rating),
  average_rating=coalesce(excluded.average_rating, goodreads_books.average_rating),
  publisher=coalesce(nullif(excluded.publisher, ''), goodreads_books.publisher),
  binding=coalesce(nullif(excluded.binding, ''), goodreads_books.binding),
  number_of_pages=coalesce(excluded.number_of_pages, goodreads_books.number_of_pages),
  year_published=coalesce(excluded.year_published, goodreads_books.year_published),
  original_publication_year=coalesce(excluded.original_publication_year, goodreads_books.original_publication_year),
  date_read=coalesce(nullif(excluded.date_read, ''), goodreads_books.date_read),
  date_added=coalesce(nullif(excluded.date_added, ''), goodreads_books.date_added),
  bookshelves=coalesce(nullif(excluded.bookshelves, ''), goodreads_books.bookshelves),
  exclusive_shelf=coalesce(nullif(excluded.exclusive_shelf, ''), goodreads_books.exclusive_shelf),
  my_review=coalesce(nullif(excluded.my_review, ''), goodreads_books.my_review),
  image_url=coalesce(nullif(excluded.image_url, ''), goodreads_books.image_url),
  raw_json=excluded.raw_json,
  imported_at=current_timestamp`,
		b.BookID, b.Title, b.Author, b.ISBN, b.ISBN13, nullInt(b.MyRating), nullFloat(b.AverageRating),
		b.Publisher, b.Binding, nullInt(b.NumberOfPages), nullInt(b.YearPublished), nullInt(b.OriginalPublicationYear),
		b.DateRead, b.DateAdded, b.Bookshelves, b.ExclusiveShelf, b.MyReview, b.RawJSON, b.ImageURL)
	if err != nil {
		return "", err
	}
	if exists == 1 {
		return "updated", nil
	}
	return "inserted", nil
}

func (s *Store) MatchingGoodreadsBookID(ctx context.Context, b GoodreadsBook) (string, error) {
	if b.ISBN != "" || b.ISBN13 != "" {
		var id string
		err := s.db.QueryRowContext(ctx, `select book_id from goodreads_books
where (? <> '' and isbn = ?) or (? <> '' and isbn13 = ?) limit 1`, b.ISBN, b.ISBN, b.ISBN13, b.ISBN13).Scan(&id)
		if err == nil {
			return id, nil
		}
		if err != sql.ErrNoRows {
			return "", err
		}
	}
	var id string
	err := s.db.QueryRowContext(ctx, `select book_id from goodreads_books
where lower(trim(title)) = lower(trim(?)) and lower(trim(coalesce(author, ''))) = lower(trim(?)) limit 1`, b.Title, b.Author).Scan(&id)
	if err == sql.ErrNoRows {
		return "", nil
	}
	return id, err
}

func (s *Store) ExportGoodreads(ctx context.Context) ([]GoodreadsBook, error) {
	rows, err := s.db.QueryContext(ctx, `select book_id, title, author, isbn, isbn13, my_rating, average_rating, publisher, binding,
number_of_pages, year_published, original_publication_year, date_read, date_added, bookshelves, exclusive_shelf, my_review, raw_json, imported_at
from goodreads_books order by title`)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var books []GoodreadsBook
	for rows.Next() {
		var b GoodreadsBook
		var author, isbn, isbn13, publisher, binding, dateRead, dateAdded, shelves, shelf, review, raw, imported sql.NullString
		var myRating, pages, year, originalYear sql.NullInt64
		var avg sql.NullFloat64
		if err := rows.Scan(&b.BookID, &b.Title, &author, &isbn, &isbn13, &myRating, &avg, &publisher, &binding, &pages, &year, &originalYear, &dateRead, &dateAdded, &shelves, &shelf, &review, &raw, &imported); err != nil {
			return nil, err
		}
		b.Author = scanString(author)
		b.ISBN = scanString(isbn)
		b.ISBN13 = scanString(isbn13)
		b.MyRating = scanInt(myRating)
		b.AverageRating = scanFloat(avg)
		b.Publisher = scanString(publisher)
		b.Binding = scanString(binding)
		b.NumberOfPages = scanInt(pages)
		b.YearPublished = scanInt(year)
		b.OriginalPublicationYear = scanInt(originalYear)
		b.DateRead = scanString(dateRead)
		b.DateAdded = scanString(dateAdded)
		b.Bookshelves = scanString(shelves)
		b.ExclusiveShelf = scanString(shelf)
		b.MyReview = scanString(review)
		b.RawJSON = scanString(raw)
		b.ImportedAt = scanString(imported)
		books = append(books, b)
	}
	return books, rows.Err()
}

func (s *Store) QueryLetterboxdFilms(ctx context.Context, limit int) ([]LetterboxdFilm, error) {
	if limit <= 0 || limit > 1000 {
		limit = 100
	}
	rows, err := s.db.QueryContext(ctx, `select film_key, letterboxd_uri, name, year, rating, watched_date,
diary_date, rewatch, tags, review, source, raw_json, imported_at, coalesce(image_url, '')
from letterboxd_films
order by coalesce(watched_date, diary_date, imported_at) desc, name
limit ?`, limit)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var films []LetterboxdFilm
	for rows.Next() {
		var f LetterboxdFilm
		var uri, watched, diary, rewatch, tags, review, raw, imported, img sql.NullString
		var year sql.NullInt64
		var rating sql.NullFloat64
		if err := rows.Scan(&f.FilmKey, &uri, &f.Name, &year, &rating, &watched, &diary, &rewatch, &tags, &review, &f.Source, &raw, &imported, &img); err != nil {
			return nil, err
		}
		f.LetterboxdURI = scanString(uri)
		f.Year = scanInt(year)
		f.Rating = scanFloat(rating)
		f.WatchedDate = scanString(watched)
		f.DiaryDate = scanString(diary)
		f.Rewatch = scanString(rewatch)
		f.Tags = scanString(tags)
		f.Review = scanString(review)
		f.ImageURL = scanString(img)
		f.RawJSON = scanString(raw)
		f.ImportedAt = scanString(imported)
		films = append(films, f)
	}
	return films, rows.Err()
}

func (s *Store) MatchingLetterboxdFilmKey(ctx context.Context, f LetterboxdFilm) (string, error) {
	// First try: match by Letterboxd URI film slug (most precise)
	// e.g. /film/inception/ from both /enes/film/inception/ and /enes/film/inception/1/
	if f.LetterboxdURI != "" {
		slug := letterboxdFilmSlug(f.LetterboxdURI)
		if slug != "" {
			var key string
			err := s.db.QueryRowContext(ctx, `select film_key
from letterboxd_films
where letterboxd_uri like '%/film/' || ? || '/%'
limit 1`, slug).Scan(&key)
			if err == nil && key != "" {
				return key, nil
			}
		}
	}

	// Second try: match by name + year (ignoring dates, which change when rating)
	var key string
	err := s.db.QueryRowContext(ctx, `select film_key
from letterboxd_films
where lower(trim(name)) = lower(trim(?))
  and coalesce(year, -1) = coalesce(?, -1)
limit 1`, f.Name, nullInt(f.Year)).Scan(&key)
	if err == sql.ErrNoRows {
		return "", nil
	}
	return key, err
}

// letterboxdFilmSlug extracts the film slug from a Letterboxd URL.
// e.g. "https://letterboxd.com/enes/film/inception/" -> "inception"
//      "https://letterboxd.com/enes/film/inception/1/" -> "inception"
func letterboxdFilmSlug(uri string) string {
	parts := strings.Split(strings.Trim(uri, "/"), "/")
	for i, p := range parts {
		if p == "film" && i+1 < len(parts) {
			return parts[i+1]
		}
	}
	return ""
}

func (s *Store) UpsertLetterboxdFilm(ctx context.Context, f LetterboxdFilm) (string, error) {
	if f.FilmKey == "" || f.Name == "" {
		return "skipped", nil
	}
	var exists int
	_ = s.db.QueryRowContext(ctx, "select 1 from letterboxd_films where film_key = ?", f.FilmKey).Scan(&exists)
	_, err := s.db.ExecContext(ctx, `
insert into letterboxd_films (
  film_key, letterboxd_uri, name, year, rating, watched_date, diary_date,
  rewatch, tags, review, source, raw_json, image_url
) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
on conflict(film_key) do update set
  letterboxd_uri=coalesce(nullif(excluded.letterboxd_uri, ''), letterboxd_films.letterboxd_uri),
  name=excluded.name,
  year=coalesce(excluded.year, letterboxd_films.year),
  rating=coalesce(excluded.rating, letterboxd_films.rating),
  watched_date=coalesce(nullif(excluded.watched_date, ''), letterboxd_films.watched_date),
  diary_date=coalesce(nullif(excluded.diary_date, ''), letterboxd_films.diary_date),
  rewatch=coalesce(nullif(excluded.rewatch, ''), letterboxd_films.rewatch),
  tags=coalesce(nullif(excluded.tags, ''), letterboxd_films.tags),
  review=coalesce(nullif(excluded.review, ''), letterboxd_films.review),
  image_url=coalesce(nullif(excluded.image_url, ''), letterboxd_films.image_url),
  source=excluded.source,
  raw_json=excluded.raw_json,
  imported_at=current_timestamp`,
		f.FilmKey, f.LetterboxdURI, f.Name, nullInt(f.Year), nullFloat(f.Rating), f.WatchedDate, f.DiaryDate,
		f.Rewatch, f.Tags, f.Review, f.Source, f.RawJSON, f.ImageURL)
	if err != nil {
		return "", err
	}
	if exists == 1 {
		return "updated", nil
	}
	return "inserted", nil
}

func (s *Store) FatSecretAccessToken(ctx context.Context) (*OAuthToken, error) {
	var token OAuthToken
	err := s.db.QueryRowContext(ctx, "select oauth_token, oauth_token_secret from fatsecret_tokens where id = 1").Scan(&token.Token, &token.Secret)
	if err == sql.ErrNoRows {
		return nil, nil
	}
	return &token, err
}

func (s *Store) SaveFatSecretAccessToken(ctx context.Context, token OAuthToken) error {
	_, err := s.db.ExecContext(ctx, `insert or replace into fatsecret_tokens (id, oauth_token, oauth_token_secret, updated_at)
values (1, ?, ?, current_timestamp)`, token.Token, token.Secret)
	return err
}

func (s *Store) SaveTempOAuthToken(ctx context.Context, token OAuthToken, createdAt int64) error {
	_, err := s.db.ExecContext(ctx, `insert or replace into oauth_temp_tokens (oauth_token, oauth_token_secret, created_at)
values (?, ?, ?)`, token.Token, token.Secret, createdAt)
	return err
}

func (s *Store) TempOAuthSecret(ctx context.Context, token string) (string, error) {
	var secret string
	err := s.db.QueryRowContext(ctx, "select oauth_token_secret from oauth_temp_tokens where oauth_token = ?", token).Scan(&secret)
	return secret, err
}

func (s *Store) UpsertFoodEntry(ctx context.Context, entry FoodEntry) (string, error) {
	if entry.FoodEntryID == "" {
		return "skipped", nil
	}
	var exists int
	_ = s.db.QueryRowContext(ctx, "select 1 from fatsecret_food_entries where food_entry_id = ?", entry.FoodEntryID).Scan(&exists)
	_, err := s.db.ExecContext(ctx, `
insert into fatsecret_food_entries (
  food_entry_id, date_int, meal, food_entry_name, food_entry_description,
  calories, carbohydrate, protein, fat, raw_json
) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
on conflict(food_entry_id) do update set
  date_int=excluded.date_int,
  meal=excluded.meal,
  food_entry_name=excluded.food_entry_name,
  food_entry_description=excluded.food_entry_description,
  calories=excluded.calories,
  carbohydrate=excluded.carbohydrate,
  protein=excluded.protein,
  fat=excluded.fat,
  raw_json=excluded.raw_json,
  synced_at=current_timestamp`,
		entry.FoodEntryID, entry.DateInt, entry.Meal, entry.FoodEntryName, entry.FoodEntryDescription,
		entry.Calories, entry.Carbohydrate, entry.Protein, entry.Fat, entry.RawJSON)
	if err != nil {
		return "", err
	}
	if exists == 1 {
		return "updated", nil
	}
	return "inserted", nil
}

func (s *Store) FoodEntries(ctx context.Context, limit int) ([]FoodEntry, error) {
	if limit <= 0 || limit > 1000 {
		limit = 100
	}
	rows, err := s.db.QueryContext(ctx, `select food_entry_id, date_int, meal, food_entry_name, food_entry_description,
calories, carbohydrate, protein, fat, raw_json, synced_at
from fatsecret_food_entries
order by date_int desc, synced_at desc
limit ?`, limit)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var entries []FoodEntry
	for rows.Next() {
		var e FoodEntry
		var meal, name, description, raw, synced sql.NullString
		var dateInt sql.NullInt64
		var calories, carb, protein, fat sql.NullFloat64
		if err := rows.Scan(&e.FoodEntryID, &dateInt, &meal, &name, &description, &calories, &carb, &protein, &fat, &raw, &synced); err != nil {
			return nil, err
		}
		e.DateInt = scanInt(dateInt)
		e.Meal = scanString(meal)
		e.FoodEntryName = scanString(name)
		e.FoodEntryDescription = scanString(description)
		e.Calories = scanFloat(calories)
		e.Carbohydrate = scanFloat(carb)
		e.Protein = scanFloat(protein)
		e.Fat = scanFloat(fat)
		e.RawJSON = scanString(raw)
		e.SyncedAt = scanString(synced)
		entries = append(entries, e)
	}
	return entries, rows.Err()
}

func (s *Store) FatSecretScannedDays(ctx context.Context) (map[int]bool, error) {
	rows, err := s.db.QueryContext(ctx, "select date_int from fatsecret_scanned_days")
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	result := make(map[int]bool)
	for rows.Next() {
		var d int
		if err := rows.Scan(&d); err == nil {
			result[d] = true
		}
	}
	return result, nil
}

func (s *Store) MarkFatSecretDayScanned(ctx context.Context, dateInt int) error {
	_, err := s.db.ExecContext(ctx, "insert or replace into fatsecret_scanned_days (date_int, scanned_at) values (?, current_timestamp)", dateInt)
	return err
}

func (s *Store) FoodEntriesSince(ctx context.Context, minDateInt int, limit int) ([]FoodEntry, error) {
	if limit <= 0 || limit > 1000 {
		limit = 100
	}
	rows, err := s.db.QueryContext(ctx, `select food_entry_id, date_int, meal, food_entry_name, food_entry_description,
calories, carbohydrate, protein, fat, raw_json, synced_at
from fatsecret_food_entries
where date_int >= ?
order by date_int desc, synced_at desc
limit ?`, minDateInt, limit)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var entries []FoodEntry
	for rows.Next() {
		var e FoodEntry
		var meal, name, description, raw, synced sql.NullString
		var dateInt sql.NullInt64
		var calories, carb, protein, fat sql.NullFloat64
		if err := rows.Scan(&e.FoodEntryID, &dateInt, &meal, &name, &description, &calories, &carb, &protein, &fat, &raw, &synced); err != nil {
			return nil, err
		}
		e.DateInt = scanInt(dateInt)
		e.Meal = scanString(meal)
		e.FoodEntryName = scanString(name)
		e.FoodEntryDescription = scanString(description)
		e.Calories = scanFloat(calories)
		e.Carbohydrate = scanFloat(carb)
		e.Protein = scanFloat(protein)
		e.Fat = scanFloat(fat)
		e.RawJSON = scanString(raw)
		e.SyncedAt = scanString(synced)
		entries = append(entries, e)
	}
	return entries, rows.Err()
}

func (s *Store) FoodEntriesBetween(ctx context.Context, minDateInt, maxDateInt int, limit int) ([]FoodEntry, error) {
	if limit <= 0 || limit > 1000 {
		limit = 100
	}
	rows, err := s.db.QueryContext(ctx, `select food_entry_id, date_int, meal, food_entry_name, food_entry_description,
calories, carbohydrate, protein, fat, raw_json, synced_at
from fatsecret_food_entries
where date_int >= ? and date_int <= ?
order by date_int desc, synced_at desc
limit ?`, minDateInt, maxDateInt, limit)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var entries []FoodEntry
	for rows.Next() {
		var e FoodEntry
		var meal, name, description, raw, synced sql.NullString
		var dateInt sql.NullInt64
		var calories, carb, protein, fat sql.NullFloat64
		if err := rows.Scan(&e.FoodEntryID, &dateInt, &meal, &name, &description, &calories, &carb, &protein, &fat, &raw, &synced); err != nil {
			return nil, err
		}
		e.DateInt = scanInt(dateInt)
		e.Meal = scanString(meal)
		e.FoodEntryName = scanString(name)
		e.FoodEntryDescription = scanString(description)
		e.Calories = scanFloat(calories)
		e.Carbohydrate = scanFloat(carb)
		e.Protein = scanFloat(protein)
		e.Fat = scanFloat(fat)
		e.RawJSON = scanString(raw)
		e.SyncedAt = scanString(synced)
		entries = append(entries, e)
	}
	return entries, rows.Err()
}

func (s *Store) FatSecretDailySummary(ctx context.Context, limit int) ([]DailySummary, error) {
	if limit <= 0 || limit > 1000 {
		limit = 30
	}
	rows, err := s.db.QueryContext(ctx, `select date_int, count(*) entry_count, coalesce(sum(calories), 0) calories
from fatsecret_food_entries
group by date_int
order by date_int desc
limit ?`, limit)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var summaries []DailySummary
	for rows.Next() {
		var s DailySummary
		if err := rows.Scan(&s.DateInt, &s.EntryCount, &s.Calories); err != nil {
			return nil, err
		}
		summaries = append(summaries, s)
	}
	return summaries, rows.Err()
}

func (s *Store) TodayFood(ctx context.Context, dateInt int) (int, float64, error) {
	var count int
	var calories float64
	err := s.db.QueryRowContext(ctx, `select count(*) entry_count, coalesce(sum(calories), 0)
from fatsecret_food_entries where date_int = ?`, dateInt).Scan(&count, &calories)
	return count, calories, err
}

func (s *Store) GetSettings(ctx context.Context, table string) (SyncSettings, error) {
	if table != "goodreads_sync_settings" && table != "letterboxd_sync_settings" {
		return SyncSettings{}, fmt.Errorf("invalid settings table")
	}
	var settings SyncSettings
	var enabled int
	err := s.db.QueryRowContext(ctx, "select coalesce(feed_url, ''), interval_minutes, enabled from "+table+" where id = 1").Scan(&settings.FeedURL, &settings.IntervalMinutes, &enabled)
	if err == sql.ErrNoRows {
		_, err = s.db.ExecContext(ctx, "insert into "+table+" (id, feed_url, interval_minutes, enabled) values (1, '', 360, 0)")
		if err != nil {
			return settings, err
		}
		settings.IntervalMinutes = 360
		return settings, nil
	}
	settings.Enabled = enabled == 1
	return settings, err
}

func (s *Store) SaveSettings(ctx context.Context, table string, settings SyncSettings) error {
	if table != "goodreads_sync_settings" && table != "letterboxd_sync_settings" {
		return fmt.Errorf("invalid settings table")
	}
	if settings.IntervalMinutes < 15 {
		settings.IntervalMinutes = 15
	}
	enabled := 0
	if settings.Enabled {
		enabled = 1
	}
	_, err := s.db.ExecContext(ctx, `insert into `+table+` (id, feed_url, interval_minutes, enabled, updated_at)
values (1, ?, ?, ?, current_timestamp)
on conflict(id) do update set feed_url=excluded.feed_url, interval_minutes=excluded.interval_minutes, enabled=excluded.enabled, updated_at=current_timestamp`,
		strings.TrimSpace(settings.FeedURL), settings.IntervalMinutes, enabled)
	return err
}

func (s *Store) RecordSyncRun(ctx context.Context, table, source, status string, inserted, updated int, message string) error {
	switch table {
	case "goodreads_sync_runs", "letterboxd_sync_runs", "fatsecret_sync_runs":
	default:
		return fmt.Errorf("invalid sync table")
	}
	_, err := s.db.ExecContext(ctx, "insert into "+table+" (source, status, inserted, updated, message) values (?, ?, ?, ?, ?)", source, status, inserted, updated, message)
	return err
}

func (s *Store) LastSyncRun(ctx context.Context, table string) (*SyncRun, error) {
	switch table {
	case "goodreads_sync_runs", "letterboxd_sync_runs", "fatsecret_sync_runs":
	default:
		return nil, fmt.Errorf("invalid sync table")
	}
	row := s.db.QueryRowContext(ctx, "select id, source, status, inserted, updated, coalesce(message, ''), ran_at from "+table+" order by ran_at desc limit 1")
	var run SyncRun
	err := row.Scan(&run.ID, &run.Source, &run.Status, &run.Inserted, &run.Updated, &run.Message, &run.RanAt)
	if err == sql.ErrNoRows {
		return nil, nil
	}
	return &run, err
}

func (s *Store) GetUserSetting(ctx context.Context, key, defaultValue string) (string, error) {
	var val string
	err := s.db.QueryRowContext(ctx, "select value from user_settings where key = ?", key).Scan(&val)
	if err == sql.ErrNoRows {
		return defaultValue, nil
	}
	if err != nil {
		return defaultValue, err
	}
	return val, nil
}

func (s *Store) SetUserSetting(ctx context.Context, key, value string) error {
	_, err := s.db.ExecContext(ctx, `insert into user_settings (key, value, updated_at) values (?, ?, current_timestamp)
on conflict(key) do update set value=excluded.value, updated_at=current_timestamp`, key, value)
	return err
}

func (s *Store) RecentSyncRuns(ctx context.Context, limit int) ([]SyncRun, error) {
	if limit <= 0 || limit > 100 {
		limit = 10
	}
	query := `
select id, source, status, inserted, updated, coalesce(message, ''), ran_at
from (
  select id, 'FatSecret (' || source || ')' as source, status, inserted, updated, message, ran_at from fatsecret_sync_runs
  union all
  select id, 'Goodreads (' || source || ')' as source, status, inserted, updated, message, ran_at from goodreads_sync_runs
  union all
  select id, 'Letterboxd (' || source || ')' as source, status, inserted, updated, message, ran_at from letterboxd_sync_runs
)
order by ran_at desc
limit ?`
	rows, err := s.db.QueryContext(ctx, query, limit)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var runs []SyncRun
	for rows.Next() {
		var r SyncRun
		if err := rows.Scan(&r.ID, &r.Source, &r.Status, &r.Inserted, &r.Updated, &r.Message, &r.RanAt); err != nil {
			return nil, err
		}
		runs = append(runs, r)
	}
	return runs, rows.Err()
}

func (s *Store) AllDataExport(ctx context.Context) (map[string]any, error) {
	books, err := s.QueryGoodreadsBooks(ctx, "", "", 10000)
	if err != nil {
		return nil, err
	}
	films, err := s.QueryLetterboxdFilms(ctx, 10000)
	if err != nil {
		return nil, err
	}
	foods, err := s.FoodEntries(ctx, 10000)
	if err != nil {
		return nil, err
	}
	summary, err := s.FatSecretDailySummary(ctx, 365)
	if err != nil {
		return nil, err
	}
	export := map[string]any{
		"version":         "1.0",
		"app":             "Personal Data Hub",
		"exported_at":     time.Now().UTC().Format(time.RFC3339),
		"goodreads_books": books,
		"letterboxd_films": films,
		"fatsecret_foods": foods,
		"daily_summary":   summary,
	}
	return export, nil
}

func nullInt(value int) any {
	if value == 0 {
		return nil
	}
	return value
}

func nullFloat(value float64) any {
	if value == 0 {
		return nil
	}
	return value
}

func RawJSON(value any) string {
	data, _ := json.Marshal(value)
	return string(data)
}

const schema = `
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
  image_url text,
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
create table if not exists fatsecret_scanned_days (
  date_int integer primary key,
  scanned_at text not null default current_timestamp
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
  image_url text,
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
create table if not exists user_settings (
  key text primary key,
  value text not null,
  updated_at text not null default current_timestamp
);`
