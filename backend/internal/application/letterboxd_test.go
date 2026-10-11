package application_test

import (
	"context"
	"testing"

	"personal-records/internal/application"
	"personal-records/internal/infrastructure/sqlite"
)

func TestLetterboxdFilmDeduplicationAndRatingUpdate(t *testing.T) {
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

	// 1. Initial watch: user watched Inception, logged with no rating from watched.csv
	csvData := `Date,Name,Year,Letterboxd URI
2026-07-01,Inception,2010,https://boxd.it/1sz2`

	res, err := services.ImportLetterboxdCSV(ctx, csvData)
	if err != nil {
		t.Fatalf("ImportLetterboxdCSV failed: %v", err)
	}
	if res.Inserted != 1 {
		t.Fatalf("Expected 1 inserted, got %d", res.Inserted)
	}

	// Verify it exists in store
	films, err := store.QueryLetterboxdFilms(ctx, 10)
	if err != nil || len(films) != 1 {
		t.Fatalf("Expected 1 film in store, got %d (err: %v)", len(films), err)
	}
	if films[0].Rating != 0 {
		t.Errorf("Expected 0 rating, got %v", films[0].Rating)
	}
	origKey := films[0].FilmKey

	// 2. Later: user gives rating to Inception (e.g. 4.5 stars) via diary or RSS
	// Notice: different date, different URI format, different source
	csvDiary := `Date,Name,Year,Letterboxd URI,Rating,Rewatch,Watched Date
2026-09-20,Inception,2010,https://letterboxd.com/enesben_/film/inception/,4.5,Yes,2026-09-19`

	res2, err := services.ImportLetterboxdCSV(ctx, csvDiary)
	if err != nil {
		t.Fatalf("ImportLetterboxdCSV second pass failed: %v", err)
	}
	if res2.Updated != 1 {
		t.Errorf("Expected 1 updated, got %d (inserted: %d)", res2.Updated, res2.Inserted)
	}

	// 3. Verify that total film count is STILL 1 and rating is updated
	filmsAfter, err := store.QueryLetterboxdFilms(ctx, 10)
	if err != nil || len(filmsAfter) != 1 {
		t.Fatalf("Expected 1 film in store after rating, got %d (err: %v)", len(filmsAfter), err)
	}
	if filmsAfter[0].FilmKey != origKey {
		t.Errorf("FilmKey changed: orig=%s, new=%s", origKey, filmsAfter[0].FilmKey)
	}
	if filmsAfter[0].Rating != 4.5 {
		t.Errorf("Expected rating 4.5, got %v", filmsAfter[0].Rating)
	}
}
