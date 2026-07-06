package shared

import (
	"testing"
	"time"
)

func TestDateIntUsesCalendarDays(t *testing.T) {
	day := time.Date(2026, 7, 6, 12, 0, 0, 0, time.Local)
	if got, want := DateInt(day), 20640; got != want {
		t.Fatalf("DateInt() = %d, want %d", got, want)
	}
}

func TestDateIntToISO(t *testing.T) {
	if got, want := DateIntToISO(20640), "2026-07-06"; got != want {
		t.Fatalf("DateIntToISO() = %q, want %q", got, want)
	}
}
