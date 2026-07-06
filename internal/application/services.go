package application

import (
	"context"
	"encoding/csv"
	"encoding/xml"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"regexp"
	"strings"
	"time"

	"personal-records/internal/config"
	"personal-records/internal/infrastructure/sqlite"
	"personal-records/internal/integrations/fatsecret"
	"personal-records/internal/shared"
)

type FatSecretClient interface {
	PublicSearch(query string) (map[string]any, error)
	RequestToken(callback string) (url.Values, error)
	AccessToken(oauthToken, verifier, tokenSecret string) (url.Values, error)
	SignedGet(endpoint string, params map[string]string, token, tokenSecret string) (map[string]any, error)
}

type Services struct {
	store     *sqlite.Store
	fatsecret FatSecretClient
}

type Dashboard struct {
	Counts      map[string]int `json:"counts"`
	Today       map[string]any `json:"today"`
	Connections map[string]any `json:"connections"`
	LastSync    map[string]any `json:"last_sync"`
}

type OverviewStats struct {
	Goodreads  sqlite.GoodreadsStats  `json:"goodreads"`
	Letterboxd sqlite.LetterboxdStats `json:"letterboxd"`
	FatSecret  sqlite.FatSecretStats  `json:"fatsecret"`
}

func NewServices(store *sqlite.Store, fatsecret *fatsecret.Client) *Services {
	return &Services{store: store, fatsecret: fatsecret}
}

func (s *Services) Store() *sqlite.Store {
	return s.store
}

func (s *Services) Dashboard(ctx context.Context) (Dashboard, error) {
	counts, err := s.store.Counts(ctx)
	if err != nil {
		return Dashboard{}, err
	}
	today := shared.TodayDateInt()
	todayCount, todayCalories, err := s.store.TodayFood(ctx, today)
	if err != nil {
		return Dashboard{}, err
	}
	token, _ := s.store.FatSecretAccessToken(ctx)
	goodreads, _ := s.GoodreadsSettings(ctx)
	letterboxd, _ := s.LetterboxdSettings(ctx)
	fatRun, _ := s.store.LastSyncRun(ctx, "fatsecret_sync_runs")
	goodreadsRun, _ := s.store.LastSyncRun(ctx, "goodreads_sync_runs")
	letterboxdRun, _ := s.store.LastSyncRun(ctx, "letterboxd_sync_runs")
	return Dashboard{
		Counts: map[string]int{
			"goodreads_books":        counts["goodreads_books"],
			"letterboxd_films":       counts["letterboxd_films"],
			"fatsecret_food_entries": counts["fatsecret_food_entries"],
		},
		Today: map[string]any{
			"date":                  shared.DateIntToISO(today),
			"fatsecret_entry_count": todayCount,
			"fatsecret_calories":    todayCalories,
		},
		Connections: map[string]any{
			"fatsecret":      token != nil,
			"goodreads_rss":  goodreads.FeedURL != "",
			"letterboxd_rss": letterboxd.FeedURL != "",
		},
		LastSync: map[string]any{
			"fatsecret":  fatRun,
			"goodreads":  goodreadsRun,
			"letterboxd": letterboxdRun,
		},
	}, nil
}

func (s *Services) Counts(ctx context.Context) (map[string]int, error) {
	return s.store.Counts(ctx)
}

func (s *Services) OverviewStats(ctx context.Context) (OverviewStats, error) {
	goodreads, err := s.store.GoodreadsStats(ctx)
	if err != nil {
		return OverviewStats{}, err
	}
	letterboxd, err := s.store.LetterboxdStats(ctx)
	if err != nil {
		return OverviewStats{}, err
	}
	fatsecret, err := s.store.FatSecretStats(ctx, shared.TodayDateInt())
	if err != nil {
		return OverviewStats{}, err
	}
	return OverviewStats{
		Goodreads:  goodreads,
		Letterboxd: letterboxd,
		FatSecret:  fatsecret,
	}, nil
}

func (s *Services) GoodreadsBooks(ctx context.Context, search, shelf string, limit int) ([]sqlite.GoodreadsBook, error) {
	return s.store.QueryGoodreadsBooks(ctx, search, shelf, limit)
}

func (s *Services) GoodreadsShelves(ctx context.Context) ([]string, error) {
	return s.store.GoodreadsShelves(ctx)
}

func (s *Services) ExportGoodreads(ctx context.Context) ([]sqlite.GoodreadsBook, error) {
	return s.store.ExportGoodreads(ctx)
}

func (s *Services) ImportGoodreadsCSV(ctx context.Context, text string) (sqlite.SyncResult, error) {
	reader := csv.NewReader(strings.NewReader(strings.TrimPrefix(text, "\ufeff")))
	reader.FieldsPerRecord = -1
	headers, err := reader.Read()
	if err != nil {
		if err == io.EOF {
			return sqlite.SyncResult{}, nil
		}
		return sqlite.SyncResult{}, err
	}
	result := sqlite.SyncResult{}
	for {
		record, err := reader.Read()
		if err == io.EOF {
			break
		}
		if err != nil {
			return result, err
		}
		row := csvRow(headers, record)
		book := goodreadsBookFromRow(row)
		if book.BookID == "" || book.Title == "" {
			continue
		}
		status, err := s.store.UpsertGoodreadsBook(ctx, book)
		if err != nil {
			return result, err
		}
		countStatus(&result, status)
	}
	return result, nil
}

func (s *Services) GoodreadsSettings(ctx context.Context) (sqlite.SyncSettings, error) {
	return s.store.GetSettings(ctx, "goodreads_sync_settings")
}

func (s *Services) SaveGoodreadsSettings(ctx context.Context, settings sqlite.SyncSettings) error {
	return s.store.SaveSettings(ctx, "goodreads_sync_settings", settings)
}

func (s *Services) RunConfiguredGoodreadsSync(ctx context.Context) (sqlite.SyncResult, error) {
	settings, err := s.GoodreadsSettings(ctx)
	if err != nil {
		return sqlite.SyncResult{}, err
	}
	if settings.FeedURL == "" {
		return sqlite.SyncResult{}, fmt.Errorf("Goodreads RSS URL kayitli degil")
	}
	result, err := s.SyncGoodreadsFeed(ctx, settings.FeedURL)
	if err != nil {
		_ = s.store.RecordSyncRun(ctx, "goodreads_sync_runs", settings.FeedURL, "error", 0, 0, err.Error())
		return result, err
	}
	_ = s.store.RecordSyncRun(ctx, "goodreads_sync_runs", settings.FeedURL, "ok", result.Inserted, result.Updated, "")
	return result, nil
}

func (s *Services) SyncGoodreadsFeed(ctx context.Context, feedURL string) (sqlite.SyncResult, error) {
	shelf := shelfFromFeedURL(feedURL)
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, feedURL, nil)
	if err != nil {
		return sqlite.SyncResult{}, err
	}
	req.Header.Set("User-Agent", "PersonalRecords/0.1")
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		return sqlite.SyncResult{}, err
	}
	defer resp.Body.Close()
	if resp.StatusCode >= 400 {
		body, _ := io.ReadAll(resp.Body)
		return sqlite.SyncResult{}, fmt.Errorf("Goodreads HTTP %d: %s", resp.StatusCode, string(body))
	}
	data, err := io.ReadAll(resp.Body)
	if err != nil {
		return sqlite.SyncResult{}, err
	}
	books, err := parseGoodreadsFeed(data, shelf)
	if err != nil {
		return sqlite.SyncResult{}, err
	}
	result := sqlite.SyncResult{}
	for _, book := range books {
		if book.BookID == "" {
			if id, err := s.store.MatchingGoodreadsBookID(ctx, book); err == nil && id != "" {
				book.BookID = id
			}
		}
		if book.BookID == "" {
			book.BookID = shared.Hash("sync:", book.RawJSON+"|"+book.Title+"|"+book.Author, 24)
		}
		status, err := s.store.UpsertGoodreadsBook(ctx, book)
		if err != nil {
			return result, err
		}
		countStatus(&result, status)
	}
	return result, nil
}

func (s *Services) LetterboxdFilms(ctx context.Context, limit int) ([]sqlite.LetterboxdFilm, error) {
	return s.store.QueryLetterboxdFilms(ctx, limit)
}

func (s *Services) ImportLetterboxdCSV(ctx context.Context, text string) (sqlite.SyncResult, error) {
	reader := csv.NewReader(strings.NewReader(strings.TrimPrefix(text, "\ufeff")))
	reader.FieldsPerRecord = -1
	headers, err := reader.Read()
	if err != nil {
		if err == io.EOF {
			return sqlite.SyncResult{}, nil
		}
		return sqlite.SyncResult{}, err
	}
	result := sqlite.SyncResult{}
	for {
		record, err := reader.Read()
		if err == io.EOF {
			break
		}
		if err != nil {
			return result, err
		}
		film := letterboxdFilmFromRow(csvRow(headers, record), "csv")
		status, err := s.upsertLetterboxdFilm(ctx, film)
		if err != nil {
			return result, err
		}
		countStatus(&result, status)
	}
	return result, nil
}

func (s *Services) LetterboxdSettings(ctx context.Context) (sqlite.SyncSettings, error) {
	return s.store.GetSettings(ctx, "letterboxd_sync_settings")
}

func (s *Services) SaveLetterboxdSettings(ctx context.Context, settings sqlite.SyncSettings) error {
	settings.FeedURL = LetterboxdOfficialFeedURL(settings.FeedURL)
	return s.store.SaveSettings(ctx, "letterboxd_sync_settings", settings)
}

func (s *Services) RunConfiguredLetterboxdSync(ctx context.Context) (sqlite.SyncResult, error) {
	settings, err := s.LetterboxdSettings(ctx)
	if err != nil {
		return sqlite.SyncResult{}, err
	}
	if settings.FeedURL == "" {
		return sqlite.SyncResult{}, fmt.Errorf("Letterboxd RSS URL kayitli degil")
	}
	result, err := s.SyncLetterboxdFeed(ctx, settings.FeedURL)
	if err != nil {
		_ = s.store.RecordSyncRun(ctx, "letterboxd_sync_runs", settings.FeedURL, "error", 0, 0, err.Error())
		return result, err
	}
	_ = s.store.RecordSyncRun(ctx, "letterboxd_sync_runs", settings.FeedURL, "ok", result.Inserted, result.Updated, "")
	return result, nil
}

func (s *Services) SyncLetterboxdFeed(ctx context.Context, feedURL string) (sqlite.SyncResult, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, feedURL, nil)
	if err != nil {
		return sqlite.SyncResult{}, err
	}
	req.Header.Set("User-Agent", "PersonalRecords/0.1")
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		return sqlite.SyncResult{}, err
	}
	defer resp.Body.Close()
	if resp.StatusCode >= 400 {
		body, _ := io.ReadAll(resp.Body)
		return sqlite.SyncResult{}, fmt.Errorf("Letterboxd HTTP %d: %s", resp.StatusCode, string(body))
	}
	data, err := io.ReadAll(resp.Body)
	if err != nil {
		return sqlite.SyncResult{}, err
	}
	films, err := parseLetterboxdFeed(data)
	if err != nil {
		return sqlite.SyncResult{}, err
	}
	result := sqlite.SyncResult{}
	for _, film := range films {
		status, err := s.upsertLetterboxdFilm(ctx, film)
		if err != nil {
			return result, err
		}
		countStatus(&result, status)
	}
	return result, nil
}

func (s *Services) upsertLetterboxdFilm(ctx context.Context, film sqlite.LetterboxdFilm) (string, error) {
	if film.Name == "" {
		return "skipped", nil
	}
	if key, err := s.store.MatchingLetterboxdFilmKey(ctx, film); err == nil && key != "" {
		film.FilmKey = key
	}
	if film.FilmKey == "" {
		basis := film.LetterboxdURI
		if basis == "" {
			basis = film.Name + "|" + fmt.Sprint(film.Year) + "|" + film.WatchedDate + "|" + film.DiaryDate + "|" + film.Source
		}
		film.FilmKey = shared.Hash("lb:", basis, 28)
	}
	return s.store.UpsertLetterboxdFilm(ctx, film)
}

func (s *Services) FatSecretConnected(ctx context.Context) bool {
	token, _ := s.store.FatSecretAccessToken(ctx)
	return token != nil
}

func (s *Services) FatSecretPublicSearch(query string) (map[string]any, error) {
	return s.fatsecret.PublicSearch(query)
}

func (s *Services) StartFatSecretConnect(ctx context.Context, callback string) (string, error) {
	values, err := s.fatsecret.RequestToken(callback)
	if err != nil {
		return "", err
	}
	token := values.Get("oauth_token")
	secret := values.Get("oauth_token_secret")
	if token == "" || secret == "" {
		return "", fmt.Errorf("FatSecret request token eksik dondu")
	}
	if err := s.store.SaveTempOAuthToken(ctx, sqlite.OAuthToken{Token: token, Secret: secret}, sharedNowUnix()); err != nil {
		return "", err
	}
	return config.FatSecretAuthorizeURL + "?" + url.Values{"oauth_token": {token}}.Encode(), nil
}

func (s *Services) FinishFatSecretConnect(ctx context.Context, oauthToken, verifier string) error {
	secret, err := s.store.TempOAuthSecret(ctx, oauthToken)
	if err != nil {
		return fmt.Errorf("Gecici OAuth token bulunamadi")
	}
	values, err := s.fatsecret.AccessToken(oauthToken, verifier, secret)
	if err != nil {
		return err
	}
	token := values.Get("oauth_token")
	tokenSecret := values.Get("oauth_token_secret")
	if token == "" || tokenSecret == "" {
		return fmt.Errorf("FatSecret access token eksik dondu")
	}
	return s.store.SaveFatSecretAccessToken(ctx, sqlite.OAuthToken{Token: token, Secret: tokenSecret})
}

func (s *Services) SyncFatSecretFoodEntries(ctx context.Context, days int) (sqlite.SyncResult, error) {
	if days < 1 {
		days = 1
	}
	if days > 366 {
		days = 366
	}
	start := shared.TodayDateInt() - days + 1
	total := sqlite.SyncResult{}
	for i := 0; i < days; i++ {
		result, err := s.syncFatSecretDate(ctx, start+i)
		if err != nil {
			_ = s.store.RecordSyncRun(ctx, "fatsecret_sync_runs", fmt.Sprintf("food_entries:%dd", days), "error", 0, 0, err.Error())
			return total, err
		}
		total.Inserted += result.Inserted
		total.Updated += result.Updated
	}
	_ = s.store.RecordSyncRun(ctx, "fatsecret_sync_runs", fmt.Sprintf("food_entries:%dd", days), "ok", total.Inserted, total.Updated, "")
	return total, nil
}

func (s *Services) syncFatSecretDate(ctx context.Context, dateInt int) (sqlite.SyncResult, error) {
	token, err := s.store.FatSecretAccessToken(ctx)
	if err != nil {
		return sqlite.SyncResult{}, err
	}
	if token == nil {
		return sqlite.SyncResult{}, fmt.Errorf("Once FatSecret hesabini baglaman gerekiyor")
	}
	payload, err := s.fatsecret.SignedGet(config.FatSecretAPIBase+"/food-entries/v2", map[string]string{"date": fmt.Sprint(dateInt), "format": "json"}, token.Token, token.Secret)
	if err != nil {
		return sqlite.SyncResult{}, err
	}
	result := sqlite.SyncResult{}
	for _, entry := range normalizeFoodEntries(payload) {
		food := foodEntryFromMap(entry)
		status, err := s.store.UpsertFoodEntry(ctx, food)
		if err != nil {
			return result, err
		}
		countStatus(&result, status)
	}
	return result, nil
}

func (s *Services) FoodEntries(ctx context.Context, limit int) ([]sqlite.FoodEntry, error) {
	entries, err := s.store.FoodEntries(ctx, limit)
	if err != nil {
		return nil, err
	}
	for i := range entries {
		entries[i].Date = shared.DateIntToISO(entries[i].DateInt)
	}
	return entries, nil
}

func (s *Services) FatSecretDailySummary(ctx context.Context, limit int) ([]sqlite.DailySummary, error) {
	summaries, err := s.store.FatSecretDailySummary(ctx, limit)
	if err != nil {
		return nil, err
	}
	for i := range summaries {
		summaries[i].Date = shared.DateIntToISO(summaries[i].DateInt)
	}
	return summaries, nil
}

func csvRow(headers, record []string) map[string]string {
	row := map[string]string{}
	for i, header := range headers {
		if i < len(record) {
			row[strings.TrimSpace(header)] = strings.TrimSpace(record[i])
		}
	}
	return row
}

func goodreadsBookFromRow(row map[string]string) sqlite.GoodreadsBook {
	book := sqlite.GoodreadsBook{
		BookID:                  shared.String(row, "Book Id", "BookID"),
		Title:                   shared.String(row, "Title"),
		Author:                  shared.String(row, "Author", "Author l-f"),
		ISBN:                    shared.String(row, "ISBN"),
		ISBN13:                  shared.String(row, "ISBN13"),
		MyRating:                shared.Int(shared.String(row, "My Rating")),
		AverageRating:           shared.Float(shared.String(row, "Average Rating")),
		Publisher:               shared.String(row, "Publisher"),
		Binding:                 shared.String(row, "Binding"),
		NumberOfPages:           shared.Int(shared.String(row, "Number of Pages")),
		YearPublished:           shared.Int(shared.String(row, "Year Published")),
		OriginalPublicationYear: shared.Int(shared.String(row, "Original Publication Year")),
		DateRead:                shared.String(row, "Date Read"),
		DateAdded:               shared.String(row, "Date Added"),
		Bookshelves:             shared.String(row, "Bookshelves"),
		ExclusiveShelf:          shared.String(row, "Exclusive Shelf"),
		MyReview:                shared.String(row, "My Review"),
		RawJSON:                 sqlite.RawJSON(row),
	}
	if book.BookID == "" {
		book.BookID = shared.Hash("sync:", shared.String(row, "Source", "Link")+"|"+book.Title+"|"+book.Author, 24)
	}
	return book
}

type goodreadsRSS struct {
	Items []goodreadsItem `xml:"channel>item"`
}

type goodreadsItem struct {
	Title         string `xml:"title"`
	BookTitle     string `xml:"book_title"`
	Link          string `xml:"link"`
	Description   string `xml:"description"`
	UserReview    string `xml:"user_review"`
	AuthorName    string `xml:"author_name"`
	UserShelves   string `xml:"user_shelves"`
	UserRating    string `xml:"user_rating"`
	UserReadAt    string `xml:"user_read_at"`
	BookID        string `xml:"book_id"`
	ISBN          string `xml:"isbn"`
	ISBN13        string `xml:"isbn13"`
	Average       string `xml:"average_rating"`
	Published     string `xml:"book_published"`
	UserDateAdded string `xml:"user_date_added"`
	PubDate       string `xml:"pubDate"`
}

func parseGoodreadsFeed(data []byte, defaultShelf string) ([]sqlite.GoodreadsBook, error) {
	var feed goodreadsRSS
	if err := xml.Unmarshal(data, &feed); err != nil {
		return nil, err
	}
	books := make([]sqlite.GoodreadsBook, 0, len(feed.Items))
	for _, item := range feed.Items {
		title := first(item.BookTitle, item.Title)
		author := item.AuthorName
		if strings.Contains(title, " by ") {
			parts := strings.Split(title, " by ")
			title = strings.Join(parts[:len(parts)-1], " by ")
			author = parts[len(parts)-1]
		}
		fallback := defaultShelf
		if fallback == "#ALL#" {
			fallback = ""
		}
		bookshelves := cleanGoodreadsShelves(item.UserShelves)
		if bookshelves == "" {
			bookshelves = fallback
		}
		row := map[string]string{
			"Book Id":                   first(item.BookID, bookIDFromLink(item.Link)),
			"Title":                     strings.TrimSpace(title),
			"Author":                    strings.TrimSpace(author),
			"ISBN":                      item.ISBN,
			"ISBN13":                    item.ISBN13,
			"My Rating":                 item.UserRating,
			"Average Rating":            item.Average,
			"Year Published":            item.Published,
			"Original Publication Year": item.Published,
			"Date Read":                 item.UserReadAt,
			"Date Added":                first(item.UserDateAdded, item.PubDate),
			"Bookshelves":               bookshelves,
			"Exclusive Shelf":           exclusiveShelf(bookshelves, defaultShelf, item.UserRating, item.UserReadAt),
			"My Review":                 shared.CleanHTML(first(item.UserReview, item.Description)),
			"Source":                    item.Link,
		}
		books = append(books, goodreadsBookFromRow(row))
	}
	return books, nil
}

func cleanGoodreadsShelves(value string) string {
	value = strings.ReplaceAll(shared.CleanHTML(value), "bookshelves:", "")
	re := regexp.MustCompile(`[,|;/]\s*|\s{2,}`)
	seen := map[string]bool{}
	var shelves []string
	for _, part := range re.Split(value, -1) {
		shelf := strings.Trim(strings.TrimSpace(part), "#")
		if shelf != "" && !seen[shelf] {
			seen[shelf] = true
			shelves = append(shelves, shelf)
		}
	}
	return strings.Join(shelves, ", ")
}

func exclusiveShelf(bookshelves, fallback, rating, dateRead string) string {
	fallback, _ = url.QueryUnescape(fallback)
	fallback = strings.TrimSpace(fallback)
	if fallback != "" && fallback != "#ALL#" {
		return fallback
	}
	for _, preferred := range []string{"currently-reading", "read", "to-read"} {
		for _, shelf := range strings.Split(bookshelves, ",") {
			if strings.TrimSpace(shelf) == preferred {
				return preferred
			}
		}
	}
	if strings.TrimSpace(dateRead) != "" || shared.Int(rating) > 0 || fallback == "#ALL#" {
		return "read"
	}
	if bookshelves != "" {
		return strings.TrimSpace(strings.Split(bookshelves, ",")[0])
	}
	return ""
}

func bookIDFromLink(link string) string {
	re := regexp.MustCompile(`/book/show/(\d+)`)
	match := re.FindStringSubmatch(link)
	if len(match) > 1 {
		return match[1]
	}
	return ""
}

func shelfFromFeedURL(feedURL string) string {
	parsed, err := url.Parse(feedURL)
	if err != nil {
		return ""
	}
	return parsed.Query().Get("shelf")
}

func letterboxdFilmFromRow(row map[string]string, source string) sqlite.LetterboxdFilm {
	film := sqlite.LetterboxdFilm{
		LetterboxdURI: shared.String(row, "Letterboxd URI", "URI", "Link"),
		Name:          shared.String(row, "Name", "Title"),
		Year:          shared.Int(shared.String(row, "Year")),
		Rating:        shared.Float(shared.String(row, "Rating")),
		WatchedDate:   shared.String(row, "Watched Date"),
		DiaryDate:     shared.String(row, "Date"),
		Rewatch:       shared.String(row, "Rewatch"),
		Tags:          shared.String(row, "Tags"),
		Review:        shared.String(row, "Review", "Description"),
		Source:        source,
		RawJSON:       sqlite.RawJSON(row),
	}
	if film.Source == "" {
		film.Source = shared.String(row, "Source")
	}
	basis := film.LetterboxdURI
	if basis == "" {
		basis = film.Name + "|" + fmt.Sprint(film.Year) + "|" + film.WatchedDate + "|" + film.DiaryDate + "|" + film.Source
	}
	film.FilmKey = shared.Hash("lb:", basis, 28)
	return film
}

type letterboxdRSS struct {
	Items []letterboxdItem `xml:"channel>item"`
}

type letterboxdItem struct {
	Title       string `xml:"title"`
	Link        string `xml:"link"`
	Description string `xml:"description"`
	PubDate     string `xml:"pubDate"`
}

func parseLetterboxdFeed(data []byte) ([]sqlite.LetterboxdFilm, error) {
	var feed letterboxdRSS
	if err := xml.Unmarshal(data, &feed); err != nil {
		return nil, err
	}
	films := make([]sqlite.LetterboxdFilm, 0, len(feed.Items))
	for _, item := range feed.Items {
		name, year := letterboxdTitleParts(item.Title)
		row := map[string]string{
			"Name":           name,
			"Year":           year,
			"Date":           item.PubDate,
			"Letterboxd URI": item.Link,
			"Review":         shared.CleanHTML(item.Description),
			"Source":         item.Link,
		}
		films = append(films, letterboxdFilmFromRow(row, "rss"))
	}
	return films, nil
}

func letterboxdTitleParts(title string) (string, string) {
	title = strings.TrimSpace(title)
	title = regexp.MustCompile(`\s+-\s+★.*$`).ReplaceAllString(title, "")
	match := regexp.MustCompile(`^(.+),\s*(\d{4})$`).FindStringSubmatch(title)
	if len(match) == 3 {
		return strings.TrimSpace(match[1]), match[2]
	}
	return title, ""
}

func LetterboxdOfficialFeedURL(value string) string {
	value = strings.TrimSpace(value)
	if value == "" {
		return ""
	}
	parsed, err := url.Parse(value)
	if err != nil || !strings.Contains(parsed.Host, "letterboxd.com") {
		return value
	}
	path := strings.Trim(parsed.Path, "/")
	if strings.HasSuffix(path, "rss") {
		if parsed.Scheme == "" {
			parsed.Scheme = "https"
		}
		parsed.Path = "/" + path + "/"
		parsed.RawQuery = ""
		return parsed.String()
	}
	username := strings.Split(path, "/")[0]
	if username == "" {
		return value
	}
	return "https://letterboxd.com/" + username + "/rss/"
}

func normalizeFoodEntries(payload map[string]any) []map[string]any {
	container, ok := payload["food_entries"].(map[string]any)
	if !ok {
		return nil
	}
	raw := container["food_entry"]
	switch entries := raw.(type) {
	case []any:
		result := make([]map[string]any, 0, len(entries))
		for _, entry := range entries {
			if m, ok := entry.(map[string]any); ok {
				result = append(result, m)
			}
		}
		return result
	case map[string]any:
		return []map[string]any{entries}
	default:
		return nil
	}
}

func foodEntryFromMap(entry map[string]any) sqlite.FoodEntry {
	return sqlite.FoodEntry{
		FoodEntryID:          anyString(entry["food_entry_id"]),
		DateInt:              shared.Int(anyString(entry["date_int"])),
		Meal:                 anyString(entry["meal"]),
		FoodEntryName:        anyString(entry["food_entry_name"]),
		FoodEntryDescription: anyString(entry["food_entry_description"]),
		Calories:             shared.Float(anyString(entry["calories"])),
		Carbohydrate:         shared.Float(anyString(entry["carbohydrate"])),
		Protein:              shared.Float(anyString(entry["protein"])),
		Fat:                  shared.Float(anyString(entry["fat"])),
		RawJSON:              sqlite.RawJSON(entry),
	}
}

func anyString(value any) string {
	switch v := value.(type) {
	case nil:
		return ""
	case string:
		return strings.TrimSpace(v)
	case float64:
		return fmt.Sprintf("%.0f", v)
	case int:
		return fmt.Sprint(v)
	default:
		return strings.TrimSpace(fmt.Sprint(v))
	}
}

func first(values ...string) string {
	for _, value := range values {
		if strings.TrimSpace(value) != "" {
			return strings.TrimSpace(value)
		}
	}
	return ""
}

func countStatus(result *sqlite.SyncResult, status string) {
	switch status {
	case "inserted":
		result.Inserted++
	case "updated":
		result.Updated++
	}
}

func sharedNowUnix() int64 {
	return time.Now().Unix()
}
