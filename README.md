# Kişisel Kayıtlar (Personal Records)

Kişisel beslenme (FatSecret), film günlüğü (Letterboxd) ve kitap okuma (Goodreads) kayıtlarınızı tek bir yerde toplayan, veri egemenliğinizi koruyan açık kaynaklı kişisel takip platformu.

> 📖 **Hızlı Başlangıç & Kurulum Rehberi:** Sıfırdan ücretsiz Cloudflare D1 veya yerel Go kurulumu ve Android uygulaması yapılandırması için **[Kurulum ve Kullanım Kılavuzu (SETUP_GUIDE.md)](SETUP_GUIDE.md)** belgesine göz atın.

## Mimariler

1. **Cloudflare Worker + D1 (Bulut & Sıfır Maliyet):** `worker/` dizininde yer alan, 7/24 ücretsiz çalışan serverless API.
2. **Go Backend + SQLite (Yerel / Kendi Sunucun):** `cmd/personal-records` ile çalışan Clean Architecture Go uygulaması.
3. **Android Jetpack Compose Mobil Uygulama:** `android/` dizininde, her iki backend ile de dinamik olarak çalışan yerel mobil uygulama.

## Hızlı Çalıştırma (Go Yerel Sunucu)

```bash
cp .env.example .env
go run ./cmd/personal-records
```

Sonra:

```text
http://localhost:8787
```

Mobil uygulama icin JSON API endpointleri [MOBILE_API.md](MOBILE_API.md)
dosyasinda listelenir.

## Goodreads

Goodreads API artik pratik bir sync yolu sunmadigi icin ilk yol CSV import.

Goodreads export dosyasini alip web arayuzunden import et:

```text
Goodreads -> My Books -> Import and export -> Export Library
```

Import edilen kayitlar `personal_records.sqlite3` icindeki `goodreads_books`
tablosuna yazilir.

Web arayuzunde:

- toplam, okundu, okunacak ve puanli kitap sayilari gorunur
- kitap/yazar aramasi yapilir
- `read`, `to-read` gibi Goodreads raflariyla filtrelenir
- `/goodreads/export` uzerinden import edilen kutuphane JSON olarak disari alinir
- Goodreads RSS/feed URL girilerek belirli araliklarla otomatik sync yapilir

Otomatik sync icin Goodreads'te ilgili shelf/RSS feed URL'sini bulup
`Goodreads otomatik sync` kartina yapistir. Araligi dakika olarak belirle,
`Otomatik calissin` kutusunu isaretle ve kaydet. `Simdi sync et` butonu ayni
ayari hemen dener.

## FatSecret

Iki ayri yol var:

- `FATSECRET_CLIENT_ID` ve `FATSECRET_CLIENT_SECRET`: public besin arama icin OAuth 2.0.
- `FATSECRET_CONSUMER_KEY` ve `FATSECRET_CONSUMER_SECRET`: hesaba baglanip kisisel gunluklere ulasmak icin OAuth 1.0 3-legged.

FatSecret uygulama bilgilerinden sonra `.env` dosyasini doldurup server'i yeniden
baslat.

## Go mimarisi

Uygulama Go'ya tasindi ve clean architecture katmanlari `internal/` altinda:

- `internal/application`: sync/import/query use-case'leri
- `internal/infrastructure/sqlite`: SQLite schema ve repository islemleri
- `internal/integrations/fatsecret`: FatSecret OAuth/API istemcisi
- `internal/presentation/http`: HTML ve JSON API handler'lari
- `internal/config`: `.env`, port ve path ayarlari

Aktif local server Go entrypoint'i olan `go run ./cmd/personal-records` ile calisir.
