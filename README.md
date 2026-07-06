# Kisisel Kayitlar

FatSecret ve Goodreads veri alma prototipi.

## Calistirma

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

Eski Python dosyalari referans olarak duruyor; aktif local server Go entrypoint'i
olan `go run ./cmd/personal-records` ile calisir.
