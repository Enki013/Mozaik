package application

import (
	"context"
	"encoding/csv"
	"encoding/json"
	"encoding/xml"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"regexp"
	"sort"
	"strconv"
	"strings"
	"time"

	"personal-records/internal/config"
	"personal-records/internal/infrastructure/sqlite"
	"personal-records/internal/integrations/fatsecret"
	"personal-records/internal/shared"
)

type FatSecretClient interface {
	SetCredentials(key, secret string)
	HasCredentials() bool
	ConsumerKey() string
	ConsumerSecret() string
	PublicSearch(query string) (map[string]any, error)
	RequestToken(callback string) (url.Values, error)
	AccessToken(oauthToken, verifier, tokenSecret string) (url.Values, error)
	SignedGet(endpoint string, params map[string]string, token, tokenSecret string) (map[string]any, error)
}

type Services struct {
	store     *sqlite.Store
	fatsecret FatSecretClient
}

type ActivityItem struct {
	ID        string         `json:"id"`
	Source    string         `json:"source"` // "fatsecret" | "goodreads" | "letterboxd"
	Type      string         `json:"type"`   // "meal" | "book" | "film"
	Date      string         `json:"date"`   // YYYY-MM-DD
	Time      string         `json:"time,omitempty"`
	Timestamp string         `json:"timestamp,omitempty"`
	Title     string         `json:"title"`
	Subtitle  string         `json:"subtitle"`
	ImageURL  string         `json:"image_url,omitempty"`
	Rewatch   bool           `json:"rewatch,omitempty"`
	Details   map[string]any `json:"details"`
}

type ActivityDayGroup struct {
	Date      string         `json:"date"`
	Label     string         `json:"label"`
	ItemCount int            `json:"item_count"`
	Summary   map[string]any `json:"summary"`
	Items     []ActivityItem `json:"items"`
}

type ActivityResponse struct {
	Days   int                `json:"days"`
	Total  int                `json:"total"`
	Items  []ActivityItem     `json:"items"`
	Groups []ActivityDayGroup `json:"groups"`
}

type Dashboard struct {
	Counts        map[string]int `json:"counts"`
	Today         map[string]any `json:"today"`
	Connections   map[string]any `json:"connections"`
	LastSync      map[string]any `json:"last_sync"`
	CalorieTarget int            `json:"calorie_target"`
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
			"notifications":          counts["notifications"],
			"health_records":         counts["health_records"],
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
			"health_connect": counts["health_records"] > 0,
		},
		LastSync: map[string]any{
			"fatsecret":  fatRun,
			"goodreads":  goodreadsRun,
			"letterboxd": letterboxdRun,
		},
		CalorieTarget: s.GetCalorieTarget(ctx),
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

func (s *Services) ActivityTimeline(ctx context.Context, days int, limit int) (ActivityResponse, error) {
	return s.ActivityTimelineRange(ctx, "", "", days, limit)
}

func (s *Services) ActivityTimelineRange(ctx context.Context, fromDate, toDate string, days int, limit int) (ActivityResponse, error) {
	if limit <= 0 {
		limit = 50
	}
	if limit > 1000 {
		limit = 1000
	}

	todayInt := shared.TodayDateInt()
	minDateInt := 0
	maxDateInt := 0
	minDateISO := strings.TrimSpace(fromDate)
	maxDateISO := strings.TrimSpace(toDate)

	if minDateISO != "" || maxDateISO != "" {
		if minDateISO != "" {
			minDateInt = shared.ISODateToDateInt(minDateISO)
		}
		if maxDateISO != "" {
			maxDateInt = shared.ISODateToDateInt(maxDateISO)
		}
	} else {
		if days < 0 {
			days = 7
		}
		if days > 3650 {
			days = 3650
		}
		if days > 0 {
			minDateInt = todayInt - days + 1
			minDateISO = shared.DateIntToISO(minDateInt)
		}
	}
	todayISO := shared.DateIntToISO(todayInt)
	yesterdayISO := shared.DateIntToISO(todayInt - 1)

	allItems := make([]ActivityItem, 0)

	// 1. FatSecret Food Entries
	var foodEntries []sqlite.FoodEntry
	var err error
	if minDateInt > 0 && maxDateInt > 0 {
		foodEntries, err = s.store.FoodEntriesBetween(ctx, minDateInt, maxDateInt, limit*3)
	} else if minDateInt > 0 {
		foodEntries, err = s.store.FoodEntriesSince(ctx, minDateInt, limit*3)
	} else {
		foodEntries, err = s.store.FoodEntries(ctx, limit*3)
	}
	if err == nil {
		for _, entry := range foodEntries {
			dateISO := shared.DateIntToISO(entry.DateInt)
			if minDateISO != "" && dateISO < minDateISO {
				continue
			}
			if maxDateISO != "" && dateISO > maxDateISO {
				continue
			}
			_, timePart := shared.NormalizeDate(entry.SyncedAt)
			item := ActivityItem{
				ID:        "fs_" + entry.FoodEntryID,
				Source:    "fatsecret",
				Type:      "meal",
				Date:      dateISO,
				Time:      timePart,
				Timestamp: entry.SyncedAt,
				Title:     entry.FoodEntryName,
				Subtitle:  entry.Meal,
				Details: map[string]any{
					"food_entry_id": entry.FoodEntryID,
					"meal":          entry.Meal,
					"description":   entry.FoodEntryDescription,
					"calories":      entry.Calories,
					"carbohydrate":  entry.Carbohydrate,
					"protein":       entry.Protein,
					"fat":           entry.Fat,
				},
			}
			allItems = append(allItems, item)
		}
	}

	// 2. Goodreads Books
	books, err := s.store.QueryGoodreadsBooks(ctx, "", "", limit*3)
	if err == nil {
		for _, book := range books {
			rawDate := book.DateRead
			if rawDate == "" {
				rawDate = book.DateAdded
			}
			dateISO, timePart := shared.NormalizeDate(rawDate)
			if dateISO == "" {
				dateISO, timePart = shared.NormalizeDate(book.ImportedAt)
			}
			if dateISO == "" || (minDateISO != "" && dateISO < minDateISO) || (maxDateISO != "" && dateISO > maxDateISO) {
				continue
			}
			item := ActivityItem{
				ID:        "gr_" + book.BookID,
				Source:    "goodreads",
				Type:      "book",
				Date:      dateISO,
				Time:      timePart,
				Timestamp: rawDate,
				Title:     book.Title,
				Subtitle:  book.Author,
				ImageURL:  book.ImageURL,
				Details: map[string]any{
					"book_id":         book.BookID,
					"author":          book.Author,
					"my_rating":       book.MyRating,
					"average_rating":  book.AverageRating,
					"exclusive_shelf": book.ExclusiveShelf,
					"bookshelves":     book.Bookshelves,
					"date_read":       book.DateRead,
					"date_added":      book.DateAdded,
					"year_published":  book.YearPublished,
					"image_url":       book.ImageURL,
					"my_review":       book.MyReview,
				},
			}
			allItems = append(allItems, item)
		}
	}

	// 3. Letterboxd Films
	films, err := s.store.QueryLetterboxdFilms(ctx, limit*3)
	if err == nil {
		for _, film := range films {
			rawDate := film.WatchedDate
			if rawDate == "" {
				rawDate = film.DiaryDate
			}
			dateISO, timePart := shared.NormalizeDate(rawDate)
			if dateISO == "" {
				dateISO, timePart = shared.NormalizeDate(film.ImportedAt)
			}
			if dateISO == "" || (minDateISO != "" && dateISO < minDateISO) || (maxDateISO != "" && dateISO > maxDateISO) {
				continue
			}
			subtitle := ""
			if film.Year > 0 {
				subtitle = fmt.Sprintf("%d", film.Year)
			}
			isRewatch := strings.EqualFold(film.Rewatch, "yes") || strings.EqualFold(film.Rewatch, "true")
			item := ActivityItem{
				ID:        "lb_" + film.FilmKey,
				Source:    "letterboxd",
				Type:      "film",
				Date:      dateISO,
				Time:      timePart,
				Timestamp: rawDate,
				Title:     film.Name,
				Subtitle:  subtitle,
				ImageURL:  film.ImageURL,
				Rewatch:   isRewatch,
				Details: map[string]any{
					"film_key":       film.FilmKey,
					"name":           film.Name,
					"year":           film.Year,
					"rating":         film.Rating,
					"watched_date":   film.WatchedDate,
					"diary_date":     film.DiaryDate,
					"letterboxd_uri": film.LetterboxdURI,
					"image_url":      film.ImageURL,
					"review":         film.Review,
					"rewatch":        film.Rewatch,
				},
			}
			allItems = append(allItems, item)
		}
	}

	// 4. Notifications
	notifs, err := s.store.QueryNotifications(ctx, limit*3, "")
	if err == nil {
		for _, n := range notifs {
			dateISO, timePart := shared.NormalizeDate(n.PostTime)
			if dateISO == "" || (minDateISO != "" && dateISO < minDateISO) || (maxDateISO != "" && dateISO > maxDateISO) {
				continue
			}
			title := n.Title
			if n.AppName != "" {
				if title != "" {
					title = n.AppName + ": " + title
				} else {
					title = n.AppName
				}
			}
			subtitle := n.Text
			if subtitle == "" {
				subtitle = n.BigText
			}
			item := ActivityItem{
				ID:        n.ID,
				Source:    "notification",
				Type:      "notification",
				Date:      dateISO,
				Time:      timePart,
				Timestamp: n.PostTime,
				Title:     title,
				Subtitle:  subtitle,
				Details: map[string]any{
					"id":               n.ID,
					"package_name":     n.PackageName,
					"app_name":         n.AppName,
					"category":         n.Category,
					"sub_text":         n.SubText,
					"big_text":         n.BigText,
					"notification_key": n.NotificationKey,
					"is_ongoing":       n.IsOngoing,
				},
			}
			allItems = append(allItems, item)
		}
	}

	// 5. Health Connect (Discrete sleep/exercise/weight + daily summary)
	healthRecords, err := s.store.QueryHealthRecords(ctx, "", "", "", minDateISO, maxDateISO, 500)
	if err == nil {
		type dailyAgg struct {
			steps    float64
			kcal     float64
			distM    float64
			bpmSum   float64
			bpmCount int
			lastTime string
		}
		dailyMap := map[string]*dailyAgg{}
		for _, hr := range healthRecords {
			if hr.DateISO == "" || (minDateISO != "" && hr.DateISO < minDateISO) || (maxDateISO != "" && hr.DateISO > maxDateISO) {
				continue
			}
			switch hr.RecordType {
			case "sleep", "exercise", "weight":
				_, timePart := shared.NormalizeDate(hr.StartTime)
				allItems = append(allItems, ActivityItem{
					ID:        hr.ID,
					Source:    "health_connect",
					Type:      "health",
					Date:      hr.DateISO,
					Time:      timePart,
					Timestamp: hr.EndTime,
					Title:     hr.Title,
					Subtitle:  hr.Subtitle,
					Details: map[string]any{
						"id":              hr.ID,
						"record_type":     hr.RecordType,
						"category":        hr.Category,
						"value_primary":   hr.ValuePrimary,
						"value_secondary": hr.ValueSecondary,
						"value_tertiary":  hr.ValueTertiary,
						"unit":            hr.Unit,
						"data_origin":     hr.DataOrigin,
					},
				})
			case "steps", "active_calories", "distance", "heart_rate", "resting_heart_rate":
				agg, ok := dailyMap[hr.DateISO]
				if !ok {
					agg = &dailyAgg{}
					dailyMap[hr.DateISO] = agg
				}
				if hr.EndTime > agg.lastTime {
					agg.lastTime = hr.EndTime
				}
				if hr.ValuePrimary != nil {
					switch hr.RecordType {
					case "steps":
						agg.steps += *hr.ValuePrimary
					case "active_calories":
						agg.kcal += *hr.ValuePrimary
					case "distance":
						agg.distM += *hr.ValuePrimary
					case "heart_rate", "resting_heart_rate":
						agg.bpmSum += *hr.ValuePrimary
						agg.bpmCount++
					}
				}
			}
		}
		for dISO, agg := range dailyMap {
			steps := int(agg.steps + 0.5)
			kcal := int(agg.kcal + 0.5)
			distKm := float64(int((agg.distM/1000.0)*10+0.5)) / 10.0
			avgBpm := 0
			if agg.bpmCount > 0 {
				avgBpm = int(agg.bpmSum/float64(agg.bpmCount) + 0.5)
			}
			if steps == 0 && kcal == 0 && distKm == 0 && avgBpm == 0 {
				continue
			}
			var parts []string
			if kcal > 0 {
				parts = append(parts, fmt.Sprintf("%d kcal aktif", kcal))
			}
			if distKm > 0 {
				parts = append(parts, fmt.Sprintf("%.1f km", distKm))
			}
			if avgBpm > 0 {
				parts = append(parts, fmt.Sprintf("Ort. %d bpm", avgBpm))
			}
			sub := strings.Join(parts, " · ")
			if sub == "" {
				sub = "Health Connect"
			}
			title := "Günlük Sağlık Özeti"
			if steps > 0 {
				title = fmt.Sprintf("Günlük Aktivite · %d adım", steps)
			}
			_, timePart := shared.NormalizeDate(agg.lastTime)
			allItems = append(allItems, ActivityItem{
				ID:        "hc_daily_" + dISO,
				Source:    "health_connect",
				Type:      "health",
				Date:      dISO,
				Time:      timePart,
				Timestamp: agg.lastTime,
				Title:     title,
				Subtitle:  sub,
				Details: map[string]any{
					"record_type":     "daily_summary",
					"category":        "activity",
					"steps":           steps,
					"active_calories": kcal,
					"distance_km":     distKm,
					"avg_bpm":         avgBpm,
				},
			})
		}
	}

	// Sort descending by Date, then Time, then Source, then ID
	sort.Slice(allItems, func(i, j int) bool {
		if allItems[i].Date != allItems[j].Date {
			return allItems[i].Date > allItems[j].Date
		}
		if allItems[i].Time != allItems[j].Time {
			return allItems[i].Time > allItems[j].Time
		}
		if allItems[i].Source != allItems[j].Source {
			return allItems[i].Source < allItems[j].Source
		}
		return allItems[i].ID > allItems[j].ID
	})

	if len(allItems) > limit {
		allItems = allItems[:limit]
	}

	// Group by date
	groupsMap := make(map[string]*ActivityDayGroup)
	var groupOrder []string

	for _, item := range allItems {
		g, exists := groupsMap[item.Date]
		if !exists {
			label := shared.FormatDateLabel(item.Date, todayISO, yesterdayISO)
			g = &ActivityDayGroup{
				Date:      item.Date,
				Label:     label,
				Summary:   map[string]any{
					"total_calories": 0.0,
					"total_protein":  0.0,
					"total_carbs":    0.0,
					"total_fat":      0.0,
					"meals_count":    0,
					"books_count":    0,
					"films_count":    0,
				},
				Items:     nil,
			}
			groupsMap[item.Date] = g
			groupOrder = append(groupOrder, item.Date)
		}
		g.Items = append(g.Items, item)
		g.ItemCount++

		switch item.Type {
		case "meal":
			if cal, ok := item.Details["calories"].(float64); ok {
				g.Summary["total_calories"] = g.Summary["total_calories"].(float64) + cal
			}
			if p, ok := item.Details["protein"].(float64); ok {
				g.Summary["total_protein"] = g.Summary["total_protein"].(float64) + p
			}
			if c, ok := item.Details["carbohydrate"].(float64); ok {
				g.Summary["total_carbs"] = g.Summary["total_carbs"].(float64) + c
			}
			if f, ok := item.Details["fat"].(float64); ok {
				g.Summary["total_fat"] = g.Summary["total_fat"].(float64) + f
			}
			g.Summary["meals_count"] = g.Summary["meals_count"].(int) + 1
		case "book":
			g.Summary["books_count"] = g.Summary["books_count"].(int) + 1
		case "film":
			g.Summary["films_count"] = g.Summary["films_count"].(int) + 1
		}
	}

	groups := make([]ActivityDayGroup, 0, len(groupOrder))
	for _, d := range groupOrder {
		groups = append(groups, *groupsMap[d])
	}

	return ActivityResponse{
		Days:   days,
		Total:  len(allItems),
		Items:  allItems,
		Groups: groups,
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
	settings.FeedURL = CleanGoodreadsFeedURL(settings.FeedURL)
	return s.store.SaveSettings(ctx, "goodreads_sync_settings", settings)
}

func CleanGoodreadsFeedURL(value string) string {
	value = strings.TrimSpace(value)
	if value == "" {
		return ""
	}
	re := regexp.MustCompile(`/:\s*(\d+)`)
	value = re.ReplaceAllString(value, "/$1")
	value = strings.ReplaceAll(value, " ", "")
	return value
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
	feedURL = CleanGoodreadsFeedURL(feedURL)
	shelf := shelfFromFeedURL(feedURL)
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, feedURL, nil)
	if err != nil {
		return sqlite.SyncResult{}, err
	}
	req.Header.Set("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36")
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		return sqlite.SyncResult{}, err
	}
	defer resp.Body.Close()
	if resp.StatusCode >= 400 {
		return sqlite.SyncResult{}, fmt.Errorf("Goodreads HTTP %d: %s", resp.StatusCode, http.StatusText(resp.StatusCode))
	}
	data, err := io.ReadAll(resp.Body)
	if err != nil {
		return sqlite.SyncResult{}, err
	}
	books, err := parseGoodreadsFeed(data, shelf)
	if err != nil {
		if strings.Contains(string(data), "<html") {
			return sqlite.SyncResult{}, fmt.Errorf("Goodreads XML yerine HTML döndürdü (URL veya profil erişimi geçersiz)")
		}
		return sqlite.SyncResult{}, fmt.Errorf("Goodreads XML okunamadı: %v", err)
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
		return sqlite.SyncResult{}, fmt.Errorf("Letterboxd HTTP %d: %s", resp.StatusCode, http.StatusText(resp.StatusCode))
	}
	data, err := io.ReadAll(resp.Body)
	if err != nil {
		return sqlite.SyncResult{}, err
	}
	films, err := parseLetterboxdFeed(data)
	if err != nil {
		if strings.Contains(string(data), "<html") {
			return sqlite.SyncResult{}, fmt.Errorf("Letterboxd XML yerine HTML döndürdü (kullanıcı adı veya URL geçersiz)")
		}
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

func (s *Services) ensureFatSecretCredentials(ctx context.Context) {
	if s.fatsecret == nil {
		return
	}
	key, _ := s.store.GetUserSetting(ctx, "fatsecret_consumer_key", "")
	sec, _ := s.store.GetUserSetting(ctx, "fatsecret_consumer_secret", "")
	if key != "" && sec != "" {
		s.fatsecret.SetCredentials(key, sec)
	}
}

func (s *Services) FatSecretSettings(ctx context.Context) (map[string]any, error) {
	s.ensureFatSecretCredentials(ctx)
	key, _ := s.store.GetUserSetting(ctx, "fatsecret_consumer_key", "")
	sec, _ := s.store.GetUserSetting(ctx, "fatsecret_consumer_secret", "")
	if key == "" && s.fatsecret != nil {
		key = s.fatsecret.ConsumerKey()
	}
	if sec == "" && s.fatsecret != nil {
		sec = s.fatsecret.ConsumerSecret()
	}
	hasKeys := key != "" && sec != ""
	isConnected := s.FatSecretConnected(ctx)

	maskedKey := ""
	if len(key) > 8 {
		maskedKey = key[:4] + "..." + key[len(key)-4:]
	} else if len(key) > 0 {
		maskedKey = "****"
	}

	return map[string]any{
		"has_keys":         hasKeys,
		"is_connected":     isConnected,
		"consumer_key":     maskedKey,
		"raw_consumer_key": key,
	}, nil
}

func (s *Services) SaveFatSecretCredentials(ctx context.Context, key, secret string) error {
	key = strings.TrimSpace(key)
	secret = strings.TrimSpace(secret)
	if err := s.store.SetUserSetting(ctx, "fatsecret_consumer_key", key); err != nil {
		return err
	}
	if err := s.store.SetUserSetting(ctx, "fatsecret_consumer_secret", secret); err != nil {
		return err
	}
	if s.fatsecret != nil {
		s.fatsecret.SetCredentials(key, secret)
	}
	return nil
}

func (s *Services) FatSecretConnected(ctx context.Context) bool {
	token, _ := s.store.FatSecretAccessToken(ctx)
	return token != nil
}

func (s *Services) FatSecretPublicSearch(query string) (map[string]any, error) {
	if s.fatsecret == nil {
		return nil, fmt.Errorf("FatSecret istemcisi yapilandirilmadi")
	}
	s.ensureFatSecretCredentials(ctxFallback())
	return s.fatsecret.PublicSearch(query)
}

func ctxFallback() context.Context {
	return context.Background()
}

func (s *Services) StartFatSecretConnect(ctx context.Context, callback string) (string, error) {
	if s.fatsecret == nil {
		return "", fmt.Errorf("FatSecret istemcisi yapilandirilmadi")
	}
	s.ensureFatSecretCredentials(ctx)
	if !s.fatsecret.HasCredentials() {
		return "", fmt.Errorf("FatSecret Consumer Key ve Secret tanimlanmamis")
	}
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
	if s.fatsecret == nil {
		return fmt.Errorf("FatSecret istemcisi yapilandirilmadi")
	}
	s.ensureFatSecretCredentials(ctx)
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
	if s.fatsecret == nil {
		return sqlite.SyncResult{}, fmt.Errorf("FatSecret istemcisi yapilandirilmadi")
	}
	s.ensureFatSecretCredentials(ctx)
	if days <= 0 {
		days = 1095 // Default to 3 years (~all-time)
	}
	if days > 1825 {
		days = 1825 // Cap at 5 years
	}
	today := shared.TodayDateInt()
	start := today - days + 1
	total := sqlite.SyncResult{}

	scannedDays, _ := s.store.FatSecretScannedDays(ctx)
	if scannedDays == nil {
		scannedDays = make(map[int]bool)
	}

	scannedCount := 0
	skippedCount := 0

	for i := 0; i < days; i++ {
		dateInt := start + i
		isRecent := dateInt >= today-1 // Today and yesterday can change, always sync them

		// If this past day was already scanned previously, skip it to save time and API quota
		if !isRecent && scannedDays[dateInt] {
			skippedCount++
			continue
		}

		result, err := s.syncFatSecretDate(ctx, dateInt)
		if err != nil {
			sourceName := fmt.Sprintf("food_entries:%dd", days)
			_ = s.store.RecordSyncRun(ctx, "fatsecret_sync_runs", sourceName, "error", total.Inserted, total.Updated, err.Error())
			return total, err
		}

		// Mark day as scanned in database
		_ = s.store.MarkFatSecretDayScanned(ctx, dateInt)
		scannedDays[dateInt] = true
		scannedCount++

		total.Inserted += result.Inserted
		total.Updated += result.Updated

		// If bulk syncing multiple unscanned days, throttle slightly (50ms) to avoid rate limits
		if days > 5 && i < days-1 {
			time.Sleep(50 * time.Millisecond)
		}
	}

	sourceName := fmt.Sprintf("food_entries:%dd", days)
	msg := ""
	if skippedCount > 0 {
		msg = fmt.Sprintf("%d gun tarandi (%d kayitli gun atlandi)", scannedCount, skippedCount)
	}
	total.Message = msg
	_ = s.store.RecordSyncRun(ctx, "fatsecret_sync_runs", sourceName, "ok", total.Inserted, total.Updated, msg)
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
		ImageURL:                shared.String(row, "Image URL", "image_url", "Book Large Image URL", "Book Image URL"),
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
	Title             string `xml:"title"`
	BookTitle         string `xml:"book_title"`
	Link              string `xml:"link"`
	Description       string `xml:"description"`
	UserReview        string `xml:"user_review"`
	AuthorName        string `xml:"author_name"`
	UserShelves       string `xml:"user_shelves"`
	UserRating        string `xml:"user_rating"`
	UserReadAt        string `xml:"user_read_at"`
	BookID            string `xml:"book_id"`
	ISBN              string `xml:"isbn"`
	ISBN13            string `xml:"isbn13"`
	Average           string `xml:"average_rating"`
	Published         string `xml:"book_published"`
	UserDateAdded     string `xml:"user_date_added"`
	PubDate           string `xml:"pubDate"`
	BookLargeImageURL string `xml:"book_large_image_url"`
	BookImageURL      string `xml:"book_image_url"`
	BookSmallImageURL string `xml:"book_small_image_url"`
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
		img := first(item.BookLargeImageURL, item.BookImageURL, item.BookSmallImageURL)
		if img == "" {
			imgMatch := regexp.MustCompile(`<img[^>]+src="([^">]+)"`).FindStringSubmatch(item.Description)
			if len(imgMatch) > 1 {
				img = imgMatch[1]
			}
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
			"Image URL":                 img,
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
		ImageURL:      shared.String(row, "Image URL", "image_url"),
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
	Title        string `xml:"title"`
	Link         string `xml:"link"`
	Description  string `xml:"description"`
	PubDate      string `xml:"pubDate"`
	WatchedDate  string `xml:"watchedDate"`
	Rewatch      string `xml:"rewatch"`
	FilmTitle    string `xml:"filmTitle"`
	FilmYear     string `xml:"filmYear"`
	MemberRating string `xml:"memberRating"`
}

func parseLetterboxdFeed(data []byte) ([]sqlite.LetterboxdFilm, error) {
	var feed letterboxdRSS
	if err := xml.Unmarshal(data, &feed); err != nil {
		return nil, err
	}
	films := make([]sqlite.LetterboxdFilm, 0, len(feed.Items))
	for _, item := range feed.Items {
		name, year := letterboxdTitleParts(item.Title)
		if item.FilmTitle != "" {
			name = item.FilmTitle
		}
		if item.FilmYear != "" {
			year = item.FilmYear
		}
		var img string
		imgMatch := regexp.MustCompile(`<img[^>]+src="([^">]+)"`).FindStringSubmatch(item.Description)
		if len(imgMatch) > 1 {
			img = imgMatch[1]
		}
		row := map[string]string{
			"Name":           name,
			"Year":           year,
			"Rating":         item.MemberRating,
			"Watched Date":   item.WatchedDate,
			"Date":           item.PubDate,
			"Rewatch":        item.Rewatch,
			"Letterboxd URI": item.Link,
			"Review":         shared.CleanHTML(item.Description),
			"Image URL":      img,
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

func (s *Services) GetCalorieTarget(ctx context.Context) int {
	val, err := s.store.GetUserSetting(ctx, "daily_calorie_target", "2200")
	if err != nil || val == "" {
		return 2200
	}
	target := shared.Int(val)
	if target <= 0 {
		return 2200
	}
	return target
}

func (s *Services) SetCalorieTarget(ctx context.Context, target int) error {
	if target < 500 {
		target = 500
	}
	if target > 10000 {
		target = 10000
	}
	return s.store.SetUserSetting(ctx, "daily_calorie_target", strconv.Itoa(target))
}

func (s *Services) RecentSyncRuns(ctx context.Context, limit int) ([]sqlite.SyncRun, error) {
	return s.store.RecentSyncRuns(ctx, limit)
}

func (s *Services) LastSyncRun(ctx context.Context, table string) (*sqlite.SyncRun, error) {
	return s.store.LastSyncRun(ctx, table)
}

func (s *Services) AllDataExport(ctx context.Context) (map[string]any, error) {
	return s.store.AllDataExport(ctx)
}

type NotificationWebhookItem struct {
	Package         string `json:"package"`
	PackageName     string `json:"packageName"`
	App             string `json:"app"`
	AppName         string `json:"appName"`
	Title           string `json:"title"`
	Text            string `json:"text"`
	SubText         string `json:"sub_text"`
	SubTextCamel    string `json:"subText"`
	BigText         string `json:"big_text"`
	BigTextCamel    string `json:"bigText"`
	Category        string `json:"category"`
	PostTime        string `json:"post_time"`
	PostedAt        string `json:"postedAt"`
	Key             string `json:"key"`
	NotificationKey string `json:"notificationKey"`
	Ongoing         bool   `json:"ongoing"`
}

func (s *Services) SaveNotificationWebhook(ctx context.Context, payload []byte) (int, int, error) {
	var items []NotificationWebhookItem
	if err := json.Unmarshal(payload, &items); err != nil {
		var single NotificationWebhookItem
		if err2 := json.Unmarshal(payload, &single); err2 != nil {
			return 0, 0, fmt.Errorf("gecersiz json: %w", err)
		}
		items = []NotificationWebhookItem{single}
	}

	inserted, updated := 0, 0
	for _, item := range items {
		pkg := item.Package
		if pkg == "" {
			pkg = item.PackageName
		}
		if pkg == "" {
			pkg = "unknown"
		}
		appName := item.App
		if appName == "" {
			appName = item.AppName
		}
		if appName == "" {
			appName = pkg
		}
		subText := item.SubText
		if subText == "" {
			subText = item.SubTextCamel
		}
		bigText := item.BigText
		if bigText == "" {
			bigText = item.BigTextCamel
		}
		postTime := item.PostTime
		if postTime == "" {
			postTime = item.PostedAt
		}
		if postTime == "" {
			postTime = time.Now().UTC().Format(time.RFC3339)
		}
		key := item.Key
		if key == "" {
			key = item.NotificationKey
		}

		basis := key
		if basis == "" {
			basis = pkg + "|" + item.Title + "|" + item.Text + "|" + postTime
		}
		id := "notif_" + shared.Hash("notif:", basis, 24)

		n := sqlite.Notification{
			ID:              id,
			PackageName:     pkg,
			AppName:         appName,
			Title:           item.Title,
			Text:            item.Text,
			SubText:         subText,
			BigText:         bigText,
			Category:        item.Category,
			PostTime:        postTime,
			NotificationKey: key,
			IsOngoing:       item.Ongoing,
			RawJSON:         string(payload),
		}

		status, err := s.store.UpsertNotification(ctx, n)
		if err != nil {
			return inserted, updated, err
		}
		if status == "updated" {
			updated++
		} else if status == "inserted" {
			inserted++
		}
	}
	return inserted, updated, nil
}

func (s *Services) Notifications(ctx context.Context, limit int, pkg string) ([]sqlite.Notification, error) {
	return s.store.QueryNotifications(ctx, limit, pkg)
}

func formatHealthOrigin(origin string) string {
	if origin == "" {
		return "Health Connect"
	}
	lower := strings.ToLower(origin)
	switch {
	case strings.Contains(lower, "shealth") || strings.Contains(lower, "samsung"):
		return "Samsung Health"
	case strings.Contains(lower, "google") && strings.Contains(lower, "fitness"):
		return "Google Fit"
	case strings.Contains(lower, "fitbit"):
		return "Fitbit"
	case strings.Contains(lower, "garmin"):
		return "Garmin Connect"
	case strings.Contains(lower, "xiaomi") || strings.Contains(lower, "mihealth") || strings.Contains(lower, "zepp"):
		return "Zepp / Mi Fitness"
	case strings.Contains(lower, "strava"):
		return "Strava"
	case strings.Contains(lower, "oura"):
		return "Oura"
	case strings.Contains(lower, "withings"):
		return "Withings"
	case strings.Contains(lower, "polar"):
		return "Polar"
	default:
		return origin
	}
}

func floatPtr(v float64) *float64 {
	return &v
}

func stringValue(v any) string {
	if v == nil {
		return ""
	}
	if s, ok := v.(string); ok {
		return strings.TrimSpace(s)
	}
	return strings.TrimSpace(fmt.Sprintf("%v", v))
}

func anyFloat(v any) float64 {
	if v == nil {
		return 0
	}
	switch n := v.(type) {
	case float64:
		return n
	case float32:
		return float64(n)
	case int:
		return float64(n)
	case int64:
		return float64(n)
	case string:
		return shared.Float(n)
	default:
		return shared.Float(fmt.Sprintf("%v", v))
	}
}

func mapFloatPtr(m map[string]any, key string) *float64 {
	v, ok := m[key]
	if !ok || v == nil {
		return nil
	}
	f := anyFloat(v)
	return &f
}

func (s *Services) SaveHealthConnectWebhook(ctx context.Context, payload []byte) (int, int, error) {
	var body map[string]any
	if err := json.Unmarshal(payload, &body); err != nil {
		return 0, 0, fmt.Errorf("gecersiz json: %w", err)
	}

	fallbackNow := time.Now().UTC().Format(time.RFC3339)
	if ts, ok := body["timestamp"].(string); ok && ts != "" {
		fallbackNow = ts
	}

	var records []sqlite.HealthRecord
	pushRecord := func(recordType, category, startTime, endTime string, v1, v2, v3 *float64, unit, title, subtitle string, rawItem map[string]any) {
		st := strings.TrimSpace(startTime)
		et := strings.TrimSpace(endTime)
		if st == "" {
			st = et
		}
		if et == "" {
			et = st
		}
		if st == "" {
			st = fallbackNow
			et = fallbackNow
		}
		dISO := et
		if len(dISO) >= 10 {
			dISO = dISO[:10]
		}
		origin := ""
		if meta, ok := rawItem["metadata"].(map[string]any); ok {
			origin = stringValue(meta["data_origin"])
		}
		basis := recordType + "|" + st + "|" + et + "|" + origin
		id := "hc_" + recordType + "_" + shared.Hash("hc:", basis, 20)
		originLabel := formatHealthOrigin(origin)
		finalSub := subtitle
		if finalSub != "" && origin != "" {
			finalSub = finalSub + " · " + originLabel
		} else if finalSub == "" {
			finalSub = originLabel
		}
		rawBytes, _ := json.Marshal(rawItem)
		records = append(records, sqlite.HealthRecord{
			ID:             id,
			RecordType:     recordType,
			Category:       category,
			DateISO:        dISO,
			StartTime:      st,
			EndTime:        et,
			ValuePrimary:   v1,
			ValueSecondary: v2,
			ValueTertiary:  v3,
			Unit:           unit,
			Title:          title,
			Subtitle:       finalSub,
			DataOrigin:     origin,
			RawJSON:        string(rawBytes),
		})
	}

	sliceItems := func(key string) []map[string]any {
		arr, ok := body[key].([]any)
		if !ok {
			return nil
		}
		out := make([]map[string]any, 0, len(arr))
		for _, el := range arr {
			if m, ok := el.(map[string]any); ok {
				out = append(out, m)
			}
		}
		return out
	}

	for _, it := range sliceItems("steps") {
		c := anyFloat(it["count"])
		pushRecord("steps", "activity", stringValue(it["start_time"]), stringValue(it["end_time"]), floatPtr(c), nil, nil, "steps", fmt.Sprintf("%d adım", int(c)), "Adım Sayısı", it)
	}
	for _, it := range sliceItems("distance") {
		m := anyFloat(it["meters"])
		pushRecord("distance", "activity", stringValue(it["start_time"]), stringValue(it["end_time"]), floatPtr(m), nil, nil, "m", fmt.Sprintf("%.2f km mesafe", m/1000.0), "Yürüme / Koşu Mesafesi", it)
	}
	for _, it := range sliceItems("active_calories") {
		cal := anyFloat(it["calories"])
		pushRecord("active_calories", "activity", stringValue(it["start_time"]), stringValue(it["end_time"]), floatPtr(cal), nil, nil, "kcal", fmt.Sprintf("%d kcal aktif", int(cal+0.5)), "Aktif Kalori", it)
	}
	for _, it := range sliceItems("total_calories") {
		cal := anyFloat(it["calories"])
		pushRecord("total_calories", "activity", stringValue(it["start_time"]), stringValue(it["end_time"]), floatPtr(cal), nil, nil, "kcal", fmt.Sprintf("%d kcal toplam", int(cal+0.5)), "Toplam Kalori", it)
	}
	for _, it := range sliceItems("exercise") {
		dur := anyFloat(it["duration_seconds"])
		dist := mapFloatPtr(it, "distance_meters")
		stp := mapFloatPtr(it, "steps")
		exTitle := first(stringValue(it["title"]), stringValue(it["type"]), "Antrenman")
		sub := fmt.Sprintf("%d dk", int(dur/60.0+0.5))
		if dist != nil && *dist > 0 {
			sub += fmt.Sprintf(" · %.2f km", *dist/1000.0)
		}
		pushRecord("exercise", "exercise", stringValue(it["start_time"]), stringValue(it["end_time"]), floatPtr(dur), dist, stp, "sec", "Antrenman: "+exTitle, sub, it)
	}
	for _, it := range sliceItems("sleep") {
		dur := anyFloat(it["duration_seconds"])
		stages, _ := it["stages"].([]any)
		startT := stringValue(it["session_end_time"])
		if len(stages) > 0 {
			if stMap, ok := stages[0].(map[string]any); ok {
				startT = first(stringValue(stMap["start_time"]), startT)
			}
		}
		hrs := int(dur) / 3600
		mins := (int(dur) % 3600) / 60
		sub := "Uyku Oturumu"
		var stCount *float64
		if len(stages) > 0 {
			c := float64(len(stages))
			stCount = &c
			sub = fmt.Sprintf("%d uyku evresi kaydedildi", len(stages))
		}
		pushRecord("sleep", "sleep", startT, stringValue(it["session_end_time"]), floatPtr(dur), stCount, nil, "sec", fmt.Sprintf("Uyku · %d sa %d dk", hrs, mins), sub, it)
	}
	for _, it := range sliceItems("heart_rate") {
		bpm := anyFloat(it["avg"])
		if bpm == 0 {
			bpm = anyFloat(it["bpm"])
		}
		minB := mapFloatPtr(it, "min")
		maxB := mapFloatPtr(it, "max")
		sub := "Kalp Atış Hızı"
		if minB != nil && maxB != nil {
			sub = fmt.Sprintf("Min %d · Max %d bpm", int(*minB+0.5), int(*maxB+0.5))
		}
		t := stringValue(it["time"])
		pushRecord("heart_rate", "vitals", t, t, floatPtr(bpm), minB, maxB, "bpm", fmt.Sprintf("%d bpm Nabız", int(bpm+0.5)), sub, it)
	}
	for _, it := range sliceItems("resting_heart_rate") {
		bpm := anyFloat(it["bpm"])
		t := stringValue(it["time"])
		pushRecord("resting_heart_rate", "vitals", t, t, floatPtr(bpm), nil, nil, "bpm", fmt.Sprintf("%d bpm Dinlenik Nabız", int(bpm+0.5)), "Dinlenik Kalp Atış Hızı", it)
	}
	for _, it := range sliceItems("heart_rate_variability") {
		hrv := anyFloat(it["avg"])
		if hrv == 0 {
			hrv = anyFloat(it["rmssd_millis"])
		}
		t := stringValue(it["time"])
		pushRecord("heart_rate_variability", "vitals", t, t, floatPtr(hrv), mapFloatPtr(it, "min"), mapFloatPtr(it, "max"), "ms", fmt.Sprintf("%d ms HRV", int(hrv+0.5)), "Kalp Atış Hızı Değişkenliği", it)
	}
	for _, it := range sliceItems("oxygen_saturation") {
		pct := anyFloat(it["avg"])
		if pct == 0 {
			pct = anyFloat(it["percentage"])
		}
		t := stringValue(it["time"])
		pushRecord("oxygen_saturation", "vitals", t, t, floatPtr(pct), mapFloatPtr(it, "min"), mapFloatPtr(it, "max"), "%", fmt.Sprintf("%%%.1f SpO₂", pct), "Kan Oksijen Doygunluğu", it)
	}
	for _, it := range sliceItems("respiratory_rate") {
		rate := anyFloat(it["avg"])
		if rate == 0 {
			rate = anyFloat(it["rate"])
		}
		t := stringValue(it["time"])
		pushRecord("respiratory_rate", "vitals", t, t, floatPtr(rate), mapFloatPtr(it, "min"), mapFloatPtr(it, "max"), "rpm", fmt.Sprintf("%.1f Solunum/dk", rate), "Solunum Hızı", it)
	}
	for _, it := range sliceItems("blood_pressure") {
		sys := anyFloat(it["systolic"])
		dia := anyFloat(it["diastolic"])
		t := stringValue(it["time"])
		pushRecord("blood_pressure", "vitals", t, t, floatPtr(sys), floatPtr(dia), nil, "mmHg", fmt.Sprintf("%d/%d mmHg", int(sys+0.5), int(dia+0.5)), "Kan Basıncı (Tansiyon)", it)
	}
	for _, it := range sliceItems("blood_glucose") {
		gl := anyFloat(it["mmol_per_liter"])
		t := stringValue(it["time"])
		pushRecord("blood_glucose", "vitals", t, t, floatPtr(gl), nil, nil, "mmol/L", fmt.Sprintf("%.1f mmol/L Kan Şekeri", gl), "Kan Glikozu", it)
	}
	for _, it := range sliceItems("body_temperature") {
		c := anyFloat(it["celsius"])
		t := stringValue(it["time"])
		pushRecord("body_temperature", "vitals", t, t, floatPtr(c), nil, nil, "°C", fmt.Sprintf("%.1f °C Vücut Sıcaklığı", c), "Ateş / Sıcaklık", it)
	}
	for _, it := range sliceItems("vo2_max") {
		v := anyFloat(it["ml_per_kg_per_min"])
		t := stringValue(it["time"])
		pushRecord("vo2_max", "vitals", t, t, floatPtr(v), nil, nil, "mL/kg/dk", fmt.Sprintf("%.1f VO₂ Max", v), "Maksimum Oksijen Tüketimi", it)
	}
	for _, it := range sliceItems("weight") {
		kg := anyFloat(it["kilograms"])
		t := stringValue(it["time"])
		pushRecord("weight", "body", t, t, floatPtr(kg), nil, nil, "kg", fmt.Sprintf("%.1f kg Ağırlık", kg), "Vücut Ağırlığı", it)
	}
	for _, it := range sliceItems("height") {
		m := anyFloat(it["meters"])
		t := stringValue(it["time"])
		pushRecord("height", "body", t, t, floatPtr(m), nil, nil, "m", fmt.Sprintf("%d cm Boy", int(m*100+0.5)), "Boy Ölçümü", it)
	}
	for _, it := range sliceItems("bmi") {
		v := anyFloat(it["value"])
		t := stringValue(it["time"])
		pushRecord("bmi", "body", t, t, floatPtr(v), mapFloatPtr(it, "weight_kg"), mapFloatPtr(it, "height_meters"), "kg/m²", fmt.Sprintf("%.1f VKİ (BMI)", v), "Vücut Kitle İndeksi", it)
	}
	for _, it := range sliceItems("body_fat") {
		pct := anyFloat(it["percentage"])
		t := stringValue(it["time"])
		pushRecord("body_fat", "body", t, t, floatPtr(pct), nil, nil, "%", fmt.Sprintf("%%%.1f Yağ Oranı", pct), "Vücut Kompozisyonu", it)
	}
	for _, it := range sliceItems("hydration") {
		l := anyFloat(it["liters"])
		pushRecord("hydration", "body", stringValue(it["start_time"]), stringValue(it["end_time"]), floatPtr(l), nil, nil, "L", fmt.Sprintf("%d mL Su", int(l*1000+0.5)), "Su Tüketimi", it)
	}

	return s.store.UpsertHealthRecords(ctx, records)
}

func (s *Services) HealthConnectRecords(ctx context.Context, category, recordType, date, fromDate, toDate string, limit int) ([]sqlite.HealthRecord, sqlite.HealthConnectSummary, error) {
	items, err := s.store.QueryHealthRecords(ctx, category, recordType, date, fromDate, toDate, limit)
	if err != nil {
		return nil, sqlite.HealthConnectSummary{}, err
	}
	sum, err := s.store.GetHealthConnectSummary(ctx)
	return items, sum, err
}


