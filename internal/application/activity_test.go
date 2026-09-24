package application_test

import (
	"context"
	"testing"
	"time"

	"personal-records/internal/application"
	"personal-records/internal/infrastructure/sqlite"
	"personal-records/internal/shared"
)

func TestActivityTimeline(t *testing.T) {
	ctx := context.Background()
	store, err := sqlite.Open(":memory:")
	if err != nil {
		t.Fatalf("sqlite.Open failed: %v", err)
	}
	defer store.Close()

	if err := store.Init(ctx); err != nil {
		t.Fatalf("store.Init failed: %v", err)
	}

	services := application.NewServices(store, nil)

	todayInt := shared.TodayDateInt()
	todayISO := shared.DateIntToISO(todayInt)
	yesterdayISO := shared.DateIntToISO(todayInt - 1)

	// 1. Insert food entry for today
	_, err = store.UpsertFoodEntry(ctx, sqlite.FoodEntry{
		FoodEntryID:          "101",
		DateInt:              todayInt,
		Meal:                 "Lunch",
		FoodEntryName:        "Grilled Chicken Bowl",
		FoodEntryDescription: "Chicken, Rice and Greens",
		Calories:             520,
		Carbohydrate:         45,
		Protein:              50,
		Fat:                  15,
		RawJSON:              "{}",
	})
	if err != nil {
		t.Fatalf("UpsertFoodEntry failed: %v", err)
	}

	// 2. Insert Goodreads book for today
	_, err = store.UpsertGoodreadsBook(ctx, sqlite.GoodreadsBook{
		BookID:         "201",
		Title:          "Dune",
		Author:         "Frank Herbert",
		DateRead:       todayISO,
		ExclusiveShelf: "read",
		MyRating:       5,
		NumberOfPages:  680,
		RawJSON:        "{}",
	})
	if err != nil {
		t.Fatalf("UpsertGoodreadsBook failed: %v", err)
	}

	// 3. Insert Letterboxd film for yesterday
	_, err = store.UpsertLetterboxdFilm(ctx, sqlite.LetterboxdFilm{
		FilmKey:     "301",
		Name:        "Oppenheimer",
		Year:        2023,
		Rating:      4.5,
		WatchedDate: yesterdayISO,
		Source:      "rss",
		RawJSON:     "{}",
	})
	if err != nil {
		t.Fatalf("UpsertLetterboxdFilm failed: %v", err)
	}

	// 4. Insert an older film (30 days ago) that shouldn't appear in 7-day query
	oldDateISO := shared.DateIntToISO(todayInt - 30)
	_, err = store.UpsertLetterboxdFilm(ctx, sqlite.LetterboxdFilm{
		FilmKey:     "302",
		Name:        "Interstellar",
		Year:        2014,
		Rating:      5,
		WatchedDate: oldDateISO,
		Source:      "rss",
		RawJSON:     "{}",
	})
	if err != nil {
		t.Fatalf("UpsertLetterboxdFilm failed: %v", err)
	}

	// Run ActivityTimeline for 7 days
	resp, err := services.ActivityTimeline(ctx, 7, 50)
	if err != nil {
		t.Fatalf("ActivityTimeline failed: %v", err)
	}

	if resp.Days != 7 {
		t.Errorf("resp.Days = %d, want 7", resp.Days)
	}

	// Should have exactly 3 items (today meal, today book, yesterday film)
	if resp.Total != 3 {
		t.Fatalf("resp.Total = %d, want 3", resp.Total)
	}
	if len(resp.Items) != 3 {
		t.Fatalf("len(resp.Items) = %d, want 3", len(resp.Items))
	}

	// Verify order: today items first, yesterday items second
	if resp.Items[0].Date != todayISO || resp.Items[1].Date != todayISO {
		t.Errorf("First two items should be today (%s), got %s and %s", todayISO, resp.Items[0].Date, resp.Items[1].Date)
	}
	if resp.Items[2].Date != yesterdayISO {
		t.Errorf("Third item should be yesterday (%s), got %s", yesterdayISO, resp.Items[2].Date)
	}

	// Verify groups
	if len(resp.Groups) != 2 {
		t.Fatalf("len(resp.Groups) = %d, want 2", len(resp.Groups))
	}

	todayGroup := resp.Groups[0]
	if todayGroup.Date != todayISO {
		t.Errorf("todayGroup.Date = %s, want %s", todayGroup.Date, todayISO)
	}
	if todayGroup.Label != "Bugün" {
		t.Errorf("todayGroup.Label = %s, want 'Bugün'", todayGroup.Label)
	}
	if todayGroup.ItemCount != 2 {
		t.Errorf("todayGroup.ItemCount = %d, want 2", todayGroup.ItemCount)
	}
	if cal, ok := todayGroup.Summary["total_calories"].(float64); !ok || cal != 520 {
		t.Errorf("todayGroup.Summary.total_calories = %v, want 520", todayGroup.Summary["total_calories"])
	}
	if bCount, ok := todayGroup.Summary["books_count"].(int); !ok || bCount != 1 {
		t.Errorf("todayGroup.Summary.books_count = %v, want 1", todayGroup.Summary["books_count"])
	}

	yesterdayGroup := resp.Groups[1]
	if yesterdayGroup.Date != yesterdayISO {
		t.Errorf("yesterdayGroup.Date = %s, want %s", yesterdayGroup.Date, yesterdayISO)
	}
	if yesterdayGroup.Label != "Dün" {
		t.Errorf("yesterdayGroup.Label = %s, want 'Dün'", yesterdayGroup.Label)
	}
	if yesterdayGroup.ItemCount != 1 {
		t.Errorf("yesterdayGroup.ItemCount = %d, want 1", yesterdayGroup.ItemCount)
	}
	if fCount, ok := yesterdayGroup.Summary["films_count"].(int); !ok || fCount != 1 {
		t.Errorf("yesterdayGroup.Summary.films_count = %v, want 1", yesterdayGroup.Summary["films_count"])
	}
}

func init() {
	_ = time.Local
}
