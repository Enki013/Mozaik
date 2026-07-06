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
	return fmt.Sprintf(`<!doctype html>
<html lang="tr">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Kisisel Kayitlar</title>
  <style>
    :root { color-scheme: light; font-family: Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; }
    body { margin: 0; background: #f6f7f4; color: #1d2522; }
    main { max-width: 1080px; margin: 0 auto; padding: 32px 20px 56px; }
    header { display: flex; justify-content: space-between; align-items: end; gap: 16px; margin-bottom: 28px; }
    h1 { font-size: 32px; margin: 0; letter-spacing: 0; }
    h2 { font-size: 18px; margin: 0 0 14px; }
    .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(280px, 1fr)); gap: 16px; }
    .card { background: #fff; border: 1px solid #d9ded6; border-radius: 8px; padding: 18px; box-shadow: 0 1px 2px rgba(18, 29, 25, .05); }
    .wide { grid-column: 1 / -1; }
    .stat { font-size: 28px; font-weight: 700; }
    .stats { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 10px; margin-top: 14px; }
    .mini { background: #f3f6f2; border: 1px solid #dfe5dc; border-radius: 6px; padding: 10px; }
    .mini div:first-child { font-size: 20px; font-weight: 750; }
    .muted { color: #62706b; font-size: 14px; }
    form { display: grid; gap: 10px; margin-top: 10px; }
    .inline { grid-template-columns: minmax(160px, 1fr) minmax(140px, 180px) auto; align-items: center; }
    input[type="text"], input[type="file"], select, input[type="number"] { border: 1px solid #c8d0ca; border-radius: 6px; padding: 10px; font: inherit; background: #fff; min-width: 0; }
    label { display: grid; gap: 6px; color: #53615c; font-size: 13px; }
    label span { font-weight: 650; }
    button, .button { border: 0; border-radius: 6px; padding: 10px 12px; background: #236c5d; color: white; font-weight: 650; cursor: pointer; text-decoration: none; display: inline-flex; justify-content: center; }
    button.secondary, .button.secondary { background: #33423d; }
    .actions { display: flex; gap: 8px; flex-wrap: wrap; margin-top: 10px; }
    table { width: 100%%; border-collapse: collapse; font-size: 14px; }
    th, td { text-align: left; padding: 9px 8px; border-bottom: 1px solid #e5e9e4; vertical-align: top; }
    th { color: #53615c; font-size: 12px; text-transform: uppercase; letter-spacing: .04em; }
    .status { display: inline-block; border-radius: 999px; padding: 4px 8px; background: #e8eee9; color: #2d453d; font-size: 12px; }
    .empty { color: #62706b; padding: 14px 0; }
    @media (max-width: 700px) { .inline { grid-template-columns: 1fr; } .stats { grid-template-columns: 1fr; } }
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
    <section class="grid">
      <div class="card">
        <h2>Goodreads</h2>
        <div class="stat">%d</div>
        <div class="muted">kitap kaydi</div>
        <div class="stats">
          <div class="mini"><div>%d</div><span class="muted">okundu</span></div>
          <div class="mini"><div>%d</div><span class="muted">okunacak</span></div>
          <div class="mini"><div>%d</div><span class="muted">puanli</span></div>
        </div>
        <form method="post" action="/goodreads/import" enctype="multipart/form-data">
          <input type="file" name="file" accept=".csv,text/csv" required>
          <button>CSV import et</button>
        </form>
        <div class="actions"><a class="button secondary" href="/goodreads/export">JSON disa aktar</a></div>
      </div>
      <div class="card">
        <h2>Goodreads otomatik sync</h2>
        <form method="post" action="/goodreads/settings">
          <label><span>Goodreads RSS/feed URL</span><input type="text" name="feed_url" value="%s" placeholder="https://www.goodreads.com/review/list_rss/..."></label>
          <label><span>Aralik dakika</span><input type="number" name="interval_minutes" min="15" value="%d"></label>
          <label style="display:flex; align-items:center; gap:8px;"><input type="checkbox" name="enabled" %s> Otomatik calissin</label>
          <button>Ayar kaydet</button>
        </form>
        <form method="post" action="/goodreads/sync"><button class="secondary">Simdi sync et</button></form>
      </div>
      <div class="card">
        <h2>FatSecret yemek gunlugu</h2>
        <div class="muted">Hesap: %s · OAuth1: %s</div>
        <a class="button secondary" href="/fatsecret/connect">FatSecret bagla</a>
        <form method="post" action="/fatsecret/sync"><input type="hidden" name="days" value="1"><button>Bugunu sync et</button></form>
        <form method="post" action="/fatsecret/sync"><input type="hidden" name="days" value="30"><button class="secondary">Son 30 gunu sync et</button></form>
      </div>
      <div class="card">
        <h2>Letterboxd</h2>
        <div class="stat">%d</div>
        <div class="muted">film kaydi · %d puanli</div>
        <form method="post" action="/letterboxd/import" enctype="multipart/form-data">
          <input type="file" name="file" accept=".csv,text/csv" required>
          <button>CSV import et</button>
        </form>
      </div>
      <div class="card">
        <h2>Letterboxd otomatik sync</h2>
        <form method="post" action="/letterboxd/settings">
          <label><span>Letterboxd RSS URL</span><input type="text" name="feed_url" value="%s" placeholder="https://letterboxd.com/kullanici/rss/"></label>
          <label><span>Aralik dakika</span><input type="number" name="interval_minutes" min="15" value="%d"></label>
          <label style="display:flex; align-items:center; gap:8px;"><input type="checkbox" name="enabled" %s> Otomatik calissin</label>
          <button>Ayar kaydet</button>
        </form>
        <form method="post" action="/letterboxd/sync"><button class="secondary">Simdi sync et</button></form>
      </div>
    </section>
    <section class="card wide" style="margin-top:16px"><h2>FatSecret kalori ozeti</h2>%s</section>
    <section class="card wide" style="margin-top:16px"><h2>FatSecret ogun kayitlari</h2>%s</section>
    <section class="card wide" style="margin-top:16px">
      <h2>Goodreads kutuphanesi</h2>
      <form class="inline" method="get" action="/">
        <input type="text" name="q" value="%s" placeholder="Kitap veya yazar ara">
        <select name="shelf">%s</select>
        <button>Filtrele</button>
      </form>
      %s
    </section>
    <section class="card wide" style="margin-top:16px"><h2>Letterboxd filmleri</h2>%s</section>
  </main>
</body>
</html>`,
		esc(data.DBName),
		data.Counts["goodreads_books"], data.Counts["read_count"], data.Counts["to_read_count"], data.Counts["rated_count"],
		esc(data.GoodreadsSettings.FeedURL), data.GoodreadsSettings.IntervalMinutes, checked(data.GoodreadsSettings.Enabled),
		connected(data.FatSecretConnected), ready(data.OAuthReady),
		data.Counts["letterboxd_films"], data.Counts["letterboxd_rated_count"],
		esc(data.LetterboxdSettings.FeedURL), data.LetterboxdSettings.IntervalMinutes, checked(data.LetterboxdSettings.Enabled),
		renderFoodSummary(data.FoodSummary),
		renderFoodEntries(data.FoodEntries),
		esc(data.Search), renderShelfOptions(data.Shelves, data.SelectedShelf), renderBooks(data.Books),
		renderFilms(data.LetterboxdFilms),
	)
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

func rating(value float64) string {
	if value == 0 {
		return ""
	}
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
