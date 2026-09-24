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

func TestISODateToDateInt(t *testing.T) {
	if got, want := ISODateToDateInt("2026-07-06"), 20640; got != want {
		t.Fatalf("ISODateToDateInt() = %d, want %d", got, want)
	}
}

func TestNormalizeDate(t *testing.T) {
	tests := []struct {
		input    string
		wantDate string
	}{
		{"2026-09-16", "2026-09-16"},
		{"2026/09/16", "2026-09-16"},
		{"2026/9/5", "2026-09-05"},
		{"Tue, 16 Sep 2026 12:30:00 +0000", "2026-09-16"},
		{"2026-09-16T12:30:00Z", "2026-09-16"},
		{"2026-09-16 12:30:00", "2026-09-16"},
		{"", ""},
	}
	for _, tc := range tests {
		date, _ := NormalizeDate(tc.input)
		if date != tc.wantDate {
			t.Errorf("NormalizeDate(%q) date = %q, want %q", tc.input, date, tc.wantDate)
		}
	}
}

func TestFormatDateLabel(t *testing.T) {
	if got := FormatDateLabel("2026-09-16", "2026-09-16", "2026-09-15"); got != "Bugün" {
		t.Errorf("FormatDateLabel today = %q, want 'Bugün'", got)
	}
	if got := FormatDateLabel("2026-09-15", "2026-09-16", "2026-09-15"); got != "Dün" {
		t.Errorf("FormatDateLabel yesterday = %q, want 'Dün'", got)
	}
	if got := FormatDateLabel("2026-09-14", "2026-09-16", "2026-09-15"); got != "14 Eylül" {
		t.Errorf("FormatDateLabel other = %q, want '14 Eylül'", got)
	}
}

