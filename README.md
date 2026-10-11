# Kişisel Kayıtlar (Personal Records)

Kişisel beslenme (FatSecret), film günlüğü (Letterboxd), kitap okuma (Goodreads) ve Android bildirimlerinizi tek bir merkezi havuzda toplayan, veri egemenliğinizi koruyan açık kaynaklı kişisel takip platformu.

> 📖 **Hızlı Başlangıç & Kurulum Rehberi:** Docker Compose veya Cloudflare D1 kurulumu, Notification Relay Webhook entegrasyonu ve Android yapılandırması için **[Kurulum ve Kullanım Kılavuzu (docs/SETUP_GUIDE.md)](docs/SETUP_GUIDE.md)** belgesine göz atın.  
> 🏛️ **Sistem Mimarisi & Eklenti Tasarımı:** Eklenti modeli ve çift motorlu sunucu yapısı için **[Mimari Belgesi (docs/ARCHITECTURE.md)](docs/ARCHITECTURE.md)** belgesine bakın.

---

## 📂 Proje Dizin Yapısı (Monorepo)

```text
personal-records/
├── android/                  # Android İstemci Uygulaması (Kotlin, Jetpack Compose, Material 3 Expressive)
├── backend/                  # Öz-Barındırılan Go Sunucusu (Clean Architecture, SQLite, Docker)
│   ├── cmd/personal-records/ # Uygulama giriş noktası (main.go)
│   ├── internal/             # Katmanlı iş mantığı (application, infrastructure, integrations, presentation)
│   └── Dockerfile            # Çok aşamalı (multi-stage) Go konteyner imajı
├── worker/                   # Sunucusuz Bulut Motoru (Cloudflare Worker, Hono, Cloudflare D1)
│   ├── src/                  # TypeScript API ve Cron senkronizasyon işleyicileri
│   ├── scripts/              # SQLite -> D1 veri taşıma betikleri
│   └── schema.sql            # D1 SQLite veritabanı şeması
├── docs/                     # Mimari, Kurulum ve API Belgeleri
│   ├── ARCHITECTURE.md       # Sistem ve eklenti mimarisi
│   ├── SETUP_GUIDE.md        # Uçtan uca kurulum kılavuzu
│   ├── MOBILE_API.md         # REST API sözleşmesi
│   └── FATSECRET_SYNC_STRATEGY.md
└── docker-compose.yml        # Tek komutla yerel sunucu kurulumu
```

---

## 🏛️ Hibrit Mimari ve Eklenti Sistemi

Kişisel Kayıtlar, **veri toplama motoru (Hub)** ile **kullanıcı arayüzünü (Android)** birbirinden ayırır:

- **1. Webhook Eklentileri (Push):** Harici **Notification Relay Webhook** APK'sı, Tasker veya Home Assistant bildirim ve sensör verilerini doğrudan sunucunun `/api/webhooks/notification` uç noktasına iletir.
- **2. RSS Eklentileri (Pull):** Letterboxd ve Goodreads gibi halka açık RSS beslemeleri sunucu tarafından taranır. Letterboxd için sadece kullanıcı adı (örn: `enesben_`) yazılması yeterlidir.
- **3. OTT / OAuth Eklentileri (API):** FatSecret BYOK (Kendi Anahtarını Getir) entegrasyonu ile beslenme ve kalori geçmişi çekilir. (Yakında: Spotify ve Steam).
- **4. Android İstemcisi:** Jetpack Compose Material 3 Expressive arayüzü ile telefonunuzda pil tüketmeden verilerinizi görüntüleyen, arama ve filtreleme sunan modern bir dashboard'dur.

---

## 🚀 Hızlı Başlangıç

### Seçenek 1: Docker Compose (Önerilen Yerel Kurulum)

Hiçbir bağımlılık kurmadan tek komutla ayağa kaldırın:

```bash
docker compose up -d
```

- Sunucunuz `http://localhost:8787` adresinde çalışır.
- Veritabanınız `./data/personal_records.sqlite3` dizininde kalıcı olarak saklanır.

### Seçenek 2: Cloudflare Worker + D1 (7/24 Kesintisiz & Ücretsiz Bulut)

```bash
cd worker
npm install
npx wrangler login
npx wrangler d1 create personal-records-db
npx wrangler d1 execute personal-records-db --remote --file=./schema.sql
npx wrangler deploy
```

Deploy sonrası terminaldeki URL'yi (Örn: `https://personal-records-api.xxxx.workers.dev`) Android uygulamanıza yapıştırın.

### Seçenek 3: Yerel Go Backend

```bash
cd backend
cp .env.example .env
go run ./cmd/personal-records
```

---

## 📱 Android Uygulaması

- En son sürüm APK dosyasını [GitHub Releases](https://github.com/Enki013/personal-records/releases) sayfasından indirin veya `android/` dizininde `./gradlew assembleDebug` ile derleyin.
- **Ayarlar** sekmesinden sunucu adresinizi kaydedin.
- **Bildirim Webhook Entegrasyonu** kartından webhook URL'nizi tek tıkla kopyalayın ve Notification Relay uygulamasına yapıştırın.
- Letterboxd kullanıcı adınızı ve FatSecret API anahtarlarınızı doğrudan uygulama içinden tanımlayın.

---

## 🛡️ Veri Egemenliği ve Yedekleme

- **JSON Dışa Aktar:** Ayarlar menüsünden tüm verilerinizi tek bir JSON dosyası olarak indirin.
- **SQLite (.db) İndir:** Ham veritabanınızı doğrudan cihazınıza yedekleyin.
