package main

import (
	"context"
	"fmt"
	"log"
	"net/http"
	"time"

	"personal-records/internal/application"
	"personal-records/internal/config"
	"personal-records/internal/infrastructure/sqlite"
	"personal-records/internal/integrations/fatsecret"
	httpserver "personal-records/internal/presentation/http"
)

func main() {
	cfg := config.Load()
	if err := cfg.LoadDotEnv(); err != nil {
		log.Fatal(err)
	}

	store, err := sqlite.Open(cfg.DBPath)
	if err != nil {
		log.Fatal(err)
	}
	defer store.Close()

	if err := store.Init(context.Background()); err != nil {
		log.Fatal(err)
	}

	services := application.NewServices(store, fatsecret.NewClient(cfg))
	go syncLoop(context.Background(), services)

	handler := httpserver.NewHandler(cfg, services)
	addr := "127.0.0.1:" + cfg.Port
	fmt.Printf("Running at http://localhost:%s\n", cfg.Port)
	log.Fatal(http.ListenAndServe(addr, handler))
}

func syncLoop(ctx context.Context, services *application.Services) {
	for {
		goodreads, _ := services.GoodreadsSettings(ctx)
		letterboxd, _ := services.LetterboxdSettings(ctx)
		interval := minInterval(goodreads.IntervalMinutes, letterboxd.IntervalMinutes)

		if goodreads.Enabled && goodreads.FeedURL != "" {
			_, _ = services.RunConfiguredGoodreadsSync(ctx)
		}
		if letterboxd.Enabled && letterboxd.FeedURL != "" {
			_, _ = services.RunConfiguredLetterboxdSync(ctx)
		}

		timer := time.NewTimer(time.Duration(interval) * time.Minute)
		select {
		case <-ctx.Done():
			timer.Stop()
			return
		case <-timer.C:
		}
	}
}

func minInterval(a, b int) int {
	if a < 15 {
		a = 360
	}
	if b < 15 {
		b = 360
	}
	if a < b {
		return a
	}
	return b
}
