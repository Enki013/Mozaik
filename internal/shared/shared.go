package shared

import (
	"crypto/sha1"
	"encoding/hex"
	"fmt"
	"html"
	"regexp"
	"strconv"
	"strings"
	"time"
)

func String(row map[string]string, names ...string) string {
	for _, name := range names {
		if value := strings.TrimSpace(row[name]); value != "" {
			return value
		}
	}
	return ""
}

func Int(value string) int {
	i, _ := strconv.Atoi(strings.TrimSpace(value))
	return i
}

func Float(value string) float64 {
	value = strings.TrimSpace(strings.ReplaceAll(value, ",", "."))
	f, _ := strconv.ParseFloat(value, 64)
	return f
}

func TodayDateInt() int {
	now := time.Now()
	return DateInt(now)
}

func DateInt(day time.Time) int {
	epoch := time.Date(1970, 1, 1, 0, 0, 0, 0, time.UTC)
	local := day.In(time.Local)
	today := time.Date(local.Year(), local.Month(), local.Day(), 0, 0, 0, 0, time.UTC)
	return int(today.Sub(epoch).Hours() / 24)
}

func DateIntToISO(value int) string {
	if value <= 0 {
		return ""
	}
	return time.Date(1970, 1, 1, 0, 0, 0, 0, time.UTC).AddDate(0, 0, value).Format("2006-01-02")
}

func ISODateToDateInt(iso string) int {
	iso = strings.TrimSpace(iso)
	if len(iso) >= 10 {
		iso = iso[:10]
	}
	t, err := time.Parse("2006-01-02", iso)
	if err != nil {
		return 0
	}
	return DateInt(t)
}

func CleanHTML(value string) string {
	re := regexp.MustCompile(`<[^>]+>`)
	return strings.TrimSpace(re.ReplaceAllString(html.UnescapeString(value), " "))
}

func Hash(prefix, value string, length int) string {
	sum := sha1.Sum([]byte(value))
	digest := hex.EncodeToString(sum[:])
	if length > len(digest) {
		length = len(digest)
	}
	return prefix + digest[:length]
}

func NormalizeDate(raw string) (string, string) {
	raw = strings.TrimSpace(raw)
	if raw == "" {
		return "", ""
	}
	formats := []string{
		time.RFC3339,
		time.RFC1123Z,
		time.RFC1123,
		time.RFC822Z,
		time.RFC822,
		"2006-01-02 15:04:05",
		"2006-01-02T15:04:05",
		"2006/01/02 15:04:05",
		"2006/01/02",
		"2006-01-02",
		"02 Jan 2006",
		"02 January 2006",
	}
	for _, layout := range formats {
		if t, err := time.Parse(layout, raw); err == nil {
			timePart := t.Format("15:04")
			if timePart == "00:00" && !strings.Contains(raw, ":") {
				timePart = ""
			}
			return t.Format("2006-01-02"), timePart
		}
	}
	re := regexp.MustCompile(`^(\d{4})[-/](\d{1,2})[-/](\d{1,2})`)
	if m := re.FindStringSubmatch(raw); len(m) == 4 {
		y, _ := strconv.Atoi(m[1])
		mon, _ := strconv.Atoi(m[2])
		d, _ := strconv.Atoi(m[3])
		return fmt.Sprintf("%04d-%02d-%02d", y, mon, d), ""
	}
	return "", ""
}

func FormatDateLabel(isoDate string, todayISO string, yesterdayISO string) string {
	if isoDate == "" {
		return ""
	}
	if isoDate == todayISO {
		return "Bugün"
	}
	if isoDate == yesterdayISO {
		return "Dün"
	}
	t, err := time.Parse("2006-01-02", isoDate)
	if err != nil {
		return isoDate
	}
	months := map[time.Month]string{
		time.January:   "Ocak",
		time.February:  "Şubat",
		time.March:     "Mart",
		time.April:     "Nisan",
		time.May:       "Mayıs",
		time.June:      "Haziran",
		time.July:      "Temmuz",
		time.August:    "Ağustos",
		time.September: "Eylül",
		time.October:   "Ekim",
		time.November:  "Kasım",
		time.December:  "Aralık",
	}
	m := months[t.Month()]
	if m == "" {
		m = t.Month().String()
	}
	if t.Year() == time.Now().Year() {
		return fmt.Sprintf("%d %s", t.Day(), m)
	}
	return fmt.Sprintf("%d %s %d", t.Day(), m, t.Year())
}

