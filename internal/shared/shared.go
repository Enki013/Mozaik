package shared

import (
	"crypto/sha1"
	"encoding/hex"
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
	start := time.Date(1970, 1, 1, 0, 0, 0, 0, time.Local)
	now := time.Now()
	today := time.Date(now.Year(), now.Month(), now.Day(), 0, 0, 0, 0, time.Local)
	return int(today.Sub(start).Hours() / 24)
}

func DateIntToISO(value int) string {
	if value <= 0 {
		return ""
	}
	return time.Date(1970, 1, 1, 0, 0, 0, 0, time.Local).AddDate(0, 0, value).Format("2006-01-02")
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
