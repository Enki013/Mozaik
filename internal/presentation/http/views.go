package http

import (
	"fmt"
	"html"
	"strings"

	"personal-records/internal/application"
	"personal-records/internal/infrastructure/sqlite"
)

type pageData struct {
	DBName             string
	Counts             map[string]int
	Dashboard          application.Dashboard
	Overview           application.OverviewStats
	Books              []sqlite.GoodreadsBook
	Shelves            []string
	SelectedShelf      string
	Search             string
	GoodreadsSettings  sqlite.SyncSettings
	LetterboxdSettings sqlite.SyncSettings
	LetterboxdFilms    []sqlite.LetterboxdFilm
	FoodSummary        []sqlite.DailySummary
	FoodEntries        []sqlite.FoodEntry
	FatSecretConnected bool
	OAuthReady         bool
}

func renderHome(data pageData) string {
	var b strings.Builder
	b.WriteString(fmt.Sprintf(`<!doctype html>
<html lang="tr">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Kisisel Kayitlar</title>
  <style>
    :root { color-scheme: light; font-family: Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; }
    * { box-sizing: border-box; }
    body { margin: 0; background: #f5f6f3; color: #1f2925; }
    main { max-width: 1240px; margin: 0 auto; padding: 28px 20px 56px; }
    header { display: flex; justify-content: space-between; align-items: flex-end; gap: 18px; margin-bottom: 22px; }
    h1 { font-size: 30px; margin: 0; letter-spacing: 0; }
    h2 { font-size: 18px; margin: 0; letter-spacing: 0; }
    h3 { font-size: 14px; margin: 0; color: #44524d; letter-spacing: 0; }
    form { display: grid; gap: 10px; }
    label { display: grid; gap: 6px; color: #53615c; font-size: 13px; }
    label span { font-weight: 650; }
    input[type="text"], input[type="file"], select, input[type="number"] { width: 100%%; border: 1px solid #c8d0ca; border-radius: 6px; padding: 10px; font: inherit; background: #fff; min-width: 0; }
    button, .button { border: 0; border-radius: 6px; padding: 10px 12px; background: #236c5d; color: white; font-weight: 650; cursor: pointer; text-decoration: none; display: inline-flex; justify-content: center; align-items: center; min-height: 40px; }
    button.secondary, .button.secondary { background: #34433f; }
    table { width: 100%%; border-collapse: collapse; font-size: 14px; }
    th, td { text-align: left; padding: 10px 8px; border-bottom: 1px solid #e5e9e4; vertical-align: top; }
    th { color: #53615c; font-size: 12px; text-transform: uppercase; letter-spacing: .04em; }
    .muted { color: #64726d; font-size: 14px; }
    .status { display: inline-flex; align-items: center; border-radius: 999px; padding: 4px 8px; background: #e6ece8; color: #2d453d; font-size: 12px; white-space: nowrap; }
    .status.ok { background: #dfeee7; color: #155d43; }
    .status.warn { background: #f3ead6; color: #72531a; }
    .summary-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; margin-bottom: 18px; }
    .metric { background: #fff; border: 1px solid #d9ded6; border-radius: 8px; padding: 16px; min-width: 0; }
    .metric .label { color: #66736e; font-size: 13px; font-weight: 650; }
    .metric .value { font-size: 28px; line-height: 1.15; font-weight: 780; margin-top: 6px; overflow-wrap: anywhere; }
    .metric .note { color: #66736e; font-size: 13px; margin-top: 4px; }
    .layout { display: grid; grid-template-columns: minmax(280px, 340px) 1fr; gap: 16px; align-items: start; }
    .panel { background: #fff; border: 1px solid #d9ded6; border-radius: 8px; padding: 18px; box-shadow: 0 1px 2px rgba(18, 29, 25, .05); min-width: 0; }
    .section { margin-top: 16px; }
    .section-head { display: flex; justify-content: space-between; align-items: flex-start; gap: 12px; margin-bottom: 14px; }
    .section-title { display: grid; gap: 4px; }
    .stat-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 10px; margin-bottom: 12px; }
    .stat { border: 1px solid #e2e7e1; border-radius: 7px; padding: 11px; background: #fafbf9; min-width: 0; }
    .stat b { display: block; font-size: 19px; margin-bottom: 3px; overflow-wrap: anywhere; }
    .stat span { color: #66736e; font-size: 12px; }
    .connection-list { display: grid; gap: 12px; }
    .connection { border-bottom: 1px solid #e8ece7; padding-bottom: 12px; display: grid; gap: 8px; }
    .connection:last-child { border-bottom: 0; padding-bottom: 0; }
    .connection-top { display: flex; justify-content: space-between; align-items: center; gap: 8px; }
    .sync-line { color: #66736e; font-size: 13px; line-height: 1.35; }
    .compact-form { display: grid; gap: 8px; }
    .button-row { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px; }
    .inline { display: grid; grid-template-columns: minmax(180px, 1fr) minmax(140px, 190px) auto; align-items: center; gap: 10px; }
    .management { margin-top: 16px; }
    .management summary { cursor: pointer; font-weight: 750; font-size: 16px; }
    .management-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; margin-top: 14px; }
    .management-card { border: 1px solid #e2e7e1; border-radius: 7px; padding: 14px; background: #fafbf9; display: grid; gap: 12px; align-content: start; min-width: 0; }
    .table-wrap { overflow-x: auto; }
    .empty { color: #64726d; padding: 14px 0; }
    @media (max-width: 980px) { .summary-grid, .stat-grid, .management-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } .layout { grid-template-columns: 1fr; } }
    @media (max-width: 640px) { main { padding: 20px 14px 44px; } header { align-items: flex-start; flex-direction: column; } .summary-grid, .stat-grid, .button-row, .inline, .management-grid { grid-template-columns: 1fr; } .metric .value { font-size: 24px; } }
  </style>
</head>
<body>
  <main>
    <header>
      <div>
        <h1>Kisisel Kayitlar</h1>
        <div class="muted">Go backend · FatSecret, Goodreads, Letterboxd</div>
      </div>
      <span class="status">SQLite: %s</span>
    </header>
`, esc(data.DBName)))
	b.WriteString(renderSummaryGrid(data))
	b.WriteString(`<div class="layout">`)
	b.WriteString(renderConnections(data))
	b.WriteString(`<div>`)
	b.WriteString(renderFatSecretDashboard(data))
	b.WriteString(renderGoodreadsDashboard(data))
	b.WriteString(renderLetterboxdDashboard(data))
	b.WriteString(`</div></div>`)
	b.WriteString(renderDataManagement(data))
	b.WriteString(`
  </main>
</body>
</html>`)
	return b.String()
}

func renderSummaryGrid(data pageData) string {
	todayDate := fmt.Sprint(data.Dashboard.Today["date"])
	return `<section class="summary-grid">` +
		metricCard("Bugun kalori", ratingZero(data.Overview.FatSecret.TodayCalories), fmt.Sprintf("%d yemek · %s", data.Overview.FatSecret.TodayEntries, todayDate)) +
		metricCard("Kitap", number(data.Overview.Goodreads.Total), fmt.Sprintf("%d okundu · %d okunacak", data.Overview.Goodreads.Read, data.Overview.Goodreads.ToRead)) +
		metricCard("Film", number(data.Overview.Letterboxd.Total), fmt.Sprintf("%d puanli · ort. %s", data.Overview.Letterboxd.Rated, rating(data.Overview.Letterboxd.AverageRating))) +
		metricCard("Yemek gunlugu", number(data.Overview.FatSecret.Entries), fmt.Sprintf("%d gun · ort. %s kalori", data.Overview.FatSecret.LoggedDays, rating(data.Overview.FatSecret.AverageCalories))) +
		`</section>`
}

func metricCard(label, value, note string) string {
	return fmt.Sprintf(`<div class="metric"><div class="label">%s</div><div class="value">%s</div><div class="note">%s</div></div>`, esc(label), emptyDash(value), esc(note))
}

func renderConnections(data pageData) string {
	var b strings.Builder
	b.WriteString(`<aside class="panel"><div class="section-head"><div class="section-title"><h2>Baglantilar</h2><div class="muted">Hesap durumu ve anlik sync</div></div></div><div class="connection-list">`)
	b.WriteString(fmt.Sprintf(`<div class="connection"><div class="connection-top"><h3>FatSecret</h3>%s</div><div class="sync-line">%s</div><div class="button-row"><a class="button secondary" href="/fatsecret/connect">Hesabi bagla</a><form method="post" action="/fatsecret/sync"><input type="hidden" name="days" value="1"><button>Bugunu sync et</button></form></div><form method="post" action="/fatsecret/sync"><input type="hidden" name="days" value="30"><button class="secondary">Son 30 gunu sync et</button></form></div>`,
		statusBadge(data.FatSecretConnected && data.OAuthReady),
		syncRunText(data.Dashboard.LastSync["fatsecret"]),
	))
	b.WriteString(fmt.Sprintf(`<div class="connection"><div class="connection-top"><h3>Goodreads RSS</h3>%s</div><div class="sync-line">%s</div><form method="post" action="/goodreads/sync"><button>Simdi sync et</button></form><div class="muted">Feed ayarlari alttaki veri yonetimi bolumunde.</div></div>`,
		statusBadge(data.GoodreadsSettings.FeedURL != ""),
		syncRunText(data.Dashboard.LastSync["goodreads"]),
	))
	b.WriteString(fmt.Sprintf(`<div class="connection"><div class="connection-top"><h3>Letterboxd RSS</h3>%s</div><div class="sync-line">%s</div><form method="post" action="/letterboxd/sync"><button>Simdi sync et</button></form><div class="muted">Feed ayarlari alttaki veri yonetimi bolumunde.</div></div>`,
		statusBadge(data.LetterboxdSettings.FeedURL != ""),
		syncRunText(data.Dashboard.LastSync["letterboxd"]),
	))
	b.WriteString(`</div></aside>`)
	return b.String()
}

func renderFatSecretDashboard(data pageData) string {
	stats := data.Overview.FatSecret
	return fmt.Sprintf(`<section class="panel"><div class="section-head"><div class="section-title"><h2>FatSecret</h2><div class="muted">Kalori, makro ve gunluk yemek ozeti</div></div><form method="post" action="/fatsecret/sync"><input type="hidden" name="days" value="1"><button>Bugunu sync et</button></form></div><div class="stat-grid">%s%s%s%s%s%s%s%s</div><div class="section"><h3>Gunluk kalori ozeti</h3>%s</div><div class="section"><h3>Son ogun kayitlari</h3>%s</div></section>`,
		statBlock("Bugun", fmt.Sprintf("%s kalori", ratingZero(stats.TodayCalories)), fmt.Sprintf("%d kayit", stats.TodayEntries)),
		statBlock("Toplam kalori", rating(stats.TotalCalories), fmt.Sprintf("%d gun", stats.LoggedDays)),
		statBlock("Gun ortalamasi", rating(stats.AverageCalories), "kalori"),
		statBlock("Son kayit", emptyDash(stats.LastLoggedDate), "FatSecret"),
		statBlock("Karbonhidrat", rating(stats.TotalCarbohydrate), "toplam g"),
		statBlock("Protein", rating(stats.TotalProtein), "toplam g"),
		statBlock("Yag", rating(stats.TotalFat), "toplam g"),
		statBlock("Yemek kaydi", number(stats.Entries), "tum zamanlar"),
		renderFoodSummary(data.FoodSummary),
		renderFoodEntries(data.FoodEntries),
	)
}

func renderGoodreadsDashboard(data pageData) string {
	stats := data.Overview.Goodreads
	return fmt.Sprintf(`<section class="panel section"><div class="section-head"><div class="section-title"><h2>Goodreads</h2><div class="muted">Kutuphane, raf ve puan istatistikleri</div></div></div><div class="stat-grid">%s%s%s%s%s%s%s%s</div><div class="section"><h3>Kutuphanede ara</h3><form class="inline" method="get" action="/"><input type="text" name="q" value="%s" placeholder="Kitap veya yazar ara"><select name="shelf">%s</select><button>Filtrele</button></form>%s</div></section>`,
		statBlock("Toplam", number(stats.Total), "kitap"),
		statBlock("Okundu", number(stats.Read), "read"),
		statBlock("Okunacak", number(stats.ToRead), "to-read"),
		statBlock("Okunuyor", number(stats.CurrentlyReading), "currently-reading"),
		statBlock("Puanli", number(stats.Rated), fmt.Sprintf("ort. %s", rating(stats.AverageMyRating))),
		statBlock("Yazar", number(stats.Authors), "benzersiz"),
		statBlock("Sayfa", number(stats.TotalPages), "toplam"),
		statBlock("Son okuma", emptyDash(stats.LastRead), fmt.Sprintf("eklenen %s", emptyDash(stats.LastAdded))),
		esc(data.Search),
		renderShelfOptions(data.Shelves, data.SelectedShelf),
		renderBooks(data.Books),
	)
}

func renderLetterboxdDashboard(data pageData) string {
	stats := data.Overview.Letterboxd
	yearRange := ""
	if stats.OldestYear > 0 || stats.NewestYear > 0 {
		yearRange = fmt.Sprintf("%s-%s", number(stats.OldestYear), number(stats.NewestYear))
	}
	return fmt.Sprintf(`<section class="panel section"><div class="section-head"><div class="section-title"><h2>Letterboxd</h2><div class="muted">Film, puan ve kaynak ozeti</div></div></div><div class="stat-grid">%s%s%s%s%s%s</div><div class="section"><h3>Son filmler</h3>%s</div></section>`,
		statBlock("Toplam", number(stats.Total), "film"),
		statBlock("Puanli", number(stats.Rated), fmt.Sprintf("ort. %s", rating(stats.AverageRating))),
		statBlock("Yil araligi", emptyDash(yearRange), "film yili"),
		statBlock("RSS", number(stats.RSSCount), "otomatik"),
		statBlock("CSV", number(stats.CSVCount), "manuel import"),
		statBlock("Son izleme", emptyDash(stats.LastWatched), "feed/import"),
		renderFilms(data.LetterboxdFilms),
	)
}

func renderDataManagement(data pageData) string {
	return fmt.Sprintf(`<details class="panel management"><summary>Veri yonetimi ve ayarlar</summary><div class="management-grid"><div class="management-card"><h3>Goodreads import/export</h3><form method="post" action="/goodreads/import" enctype="multipart/form-data"><input type="file" name="file" accept=".csv,text/csv" required><button>CSV import et</button></form><a class="button secondary" href="/goodreads/export">JSON disa aktar</a></div><div class="management-card"><h3>Goodreads RSS ayarlari</h3><form class="compact-form" method="post" action="/goodreads/settings"><label><span>Feed URL</span><input type="text" name="feed_url" value="%s" placeholder="https://www.goodreads.com/review/list_rss/..."></label><label><span>Aralik dakika</span><input type="number" name="interval_minutes" min="15" value="%d"></label><label style="display:flex; align-items:center; gap:8px;"><input type="checkbox" name="enabled" %s> Otomatik calissin</label><button>Ayar kaydet</button></form></div><div class="management-card"><h3>Letterboxd import</h3><form method="post" action="/letterboxd/import" enctype="multipart/form-data"><input type="file" name="file" accept=".csv,text/csv" required><button>CSV import et</button></form></div><div class="management-card"><h3>Letterboxd RSS ayarlari</h3><form class="compact-form" method="post" action="/letterboxd/settings"><label><span>Feed URL</span><input type="text" name="feed_url" value="%s" placeholder="https://letterboxd.com/kullanici/rss/"></label><label><span>Aralik dakika</span><input type="number" name="interval_minutes" min="15" value="%d"></label><label style="display:flex; align-items:center; gap:8px;"><input type="checkbox" name="enabled" %s> Otomatik calissin</label><button>Ayar kaydet</button></form></div><div class="management-card"><h3>FatSecret hesap</h3><div class="sync-line">Hesap: %s · OAuth1: %s</div><a class="button secondary" href="/fatsecret/connect">FatSecret hesabi bagla</a></div></div></details>`,
		esc(data.GoodreadsSettings.FeedURL),
		data.GoodreadsSettings.IntervalMinutes,
		checked(data.GoodreadsSettings.Enabled),
		esc(data.LetterboxdSettings.FeedURL),
		data.LetterboxdSettings.IntervalMinutes,
		checked(data.LetterboxdSettings.Enabled),
		connected(data.FatSecretConnected),
		ready(data.OAuthReady),
	)
}

func statBlock(label, value, note string) string {
	return fmt.Sprintf(`<div class="stat"><b>%s</b><span>%s</span><div class="muted">%s</div></div>`, esc(emptyDash(value)), esc(label), esc(note))
}

func statusBadge(ok bool) string {
	if ok {
		return `<span class="status ok">Bagli</span>`
	}
	return `<span class="status warn">Eksik</span>`
}

func syncRunText(value any) string {
	run, ok := value.(*sqlite.SyncRun)
	if !ok || run == nil {
		return "Henuz sync kaydi yok."
	}
	detail := fmt.Sprintf("%s · %s · %d yeni, %d guncel", run.RanAt, run.Status, run.Inserted, run.Updated)
	if run.Message != "" {
		detail += " · " + run.Message
	}
	return esc(detail)
}

func renderShelfOptions(shelves []string, selected string) string {
	var b strings.Builder
	b.WriteString(`<option value="">Tum raflar</option>`)
	for _, shelf := range shelves {
		attr := ""
		if shelf == selected {
			attr = " selected"
		}
		b.WriteString(fmt.Sprintf(`<option value="%s"%s>%s</option>`, esc(shelf), attr, esc(shelf)))
	}
	return b.String()
}

func renderBooks(rows []sqlite.GoodreadsBook) string {
	if len(rows) == 0 {
		return `<div class="empty">Kayit bulunamadi.</div>`
	}
	var b strings.Builder
	b.WriteString(`<table><thead><tr><th>Kitap</th><th>Yazar</th><th>Raf</th><th>Puan</th><th>Okuma</th><th>Yil</th></tr></thead><tbody>`)
	for _, r := range rows {
		b.WriteString(fmt.Sprintf(`<tr><td><strong>%s</strong></td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>`,
			esc(r.Title), esc(r.Author), esc(r.ExclusiveShelf), number(r.MyRating), esc(r.DateRead), number(r.YearPublished)))
	}
	b.WriteString(`</tbody></table>`)
	return b.String()
}

func renderFilms(rows []sqlite.LetterboxdFilm) string {
	if len(rows) == 0 {
		return `<div class="empty">Henuz Letterboxd filmi yok.</div>`
	}
	var b strings.Builder
	b.WriteString(`<table><thead><tr><th>Film</th><th>Yil</th><th>Puan</th><th>Izleme</th><th>Kaynak</th></tr></thead><tbody>`)
	for _, r := range rows {
		b.WriteString(fmt.Sprintf(`<tr><td><strong>%s</strong></td><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>`,
			esc(r.Name), number(r.Year), rating(r.Rating), esc(first(r.WatchedDate, r.DiaryDate)), esc(r.Source)))
	}
	b.WriteString(`</tbody></table>`)
	return b.String()
}

func renderFoodSummary(rows []sqlite.DailySummary) string {
	if len(rows) == 0 {
		return `<div class="empty">Henuz FatSecret yemek gunlugu kaydi yok.</div>`
	}
	var b strings.Builder
	b.WriteString(`<table><thead><tr><th>Tarih</th><th>Kayit</th><th>Kalori</th></tr></thead><tbody>`)
	for _, r := range rows {
		b.WriteString(fmt.Sprintf(`<tr><td>%s</td><td>%d</td><td>%.0f</td></tr>`, esc(r.Date), r.EntryCount, r.Calories))
	}
	b.WriteString(`</tbody></table>`)
	return b.String()
}

func renderFoodEntries(rows []sqlite.FoodEntry) string {
	if len(rows) == 0 {
		return `<div class="empty">Henuz kaydedilmis ogun yok.</div>`
	}
	var b strings.Builder
	b.WriteString(`<table><thead><tr><th>Tarih</th><th>Ogun</th><th>Besin</th><th>Kalori</th><th>Karb</th><th>Protein</th><th>Yag</th></tr></thead><tbody>`)
	for _, r := range rows {
		b.WriteString(fmt.Sprintf(`<tr><td>%s</td><td>%s</td><td><strong>%s</strong><div class="muted">%s</div></td><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>`,
			esc(r.Date), esc(r.Meal), esc(r.FoodEntryName), esc(r.FoodEntryDescription), rating(r.Calories), rating(r.Carbohydrate), rating(r.Protein), rating(r.Fat)))
	}
	b.WriteString(`</tbody></table>`)
	return b.String()
}

func esc(value string) string {
	return html.EscapeString(value)
}

func checked(value bool) string {
	if value {
		return "checked"
	}
	return ""
}

func connected(value bool) string {
	if value {
		return "bagli"
	}
	return "bagli degil"
}

func ready(value bool) string {
	if value {
		return "hazir"
	}
	return "eksik"
}

func number(value int) string {
	if value == 0 {
		return ""
	}
	return fmt.Sprint(value)
}

func emptyDash(value string) string {
	if strings.TrimSpace(value) == "" {
		return "-"
	}
	return value
}

func rating(value float64) string {
	if value == 0 {
		return ""
	}
	return ratingZero(value)
}

func ratingZero(value float64) string {
	if value == float64(int(value)) {
		return fmt.Sprintf("%.0f", value)
	}
	return fmt.Sprintf("%.1f", value)
}

func first(values ...string) string {
	for _, value := range values {
		if value != "" {
			return value
		}
	}
	return ""
}
