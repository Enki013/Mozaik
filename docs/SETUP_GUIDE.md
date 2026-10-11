# 🚀 Kişisel Kayıtlar (Personal Records) - Kurulum ve Kullanım Rehberi

Bu rehber, **Kişisel Kayıtlar** sistemini sıfırdan kurup kendi verilerinizle (FatSecret beslenme günlüğü, Letterboxd filmleri, Goodreads kitapları ve Android bildirimleri) kullanabilmeniz için hazırlanmıştır.

---

## 🏛️ Hibrit Mimari ve Çalışma Prensibi

Kişisel Kayıtlar, **merkezi bir veri havuzu (hub)** ve bu havuzu dinleyen bir **Android istemcisi (dashboard)** mimarisine sahiptir:

```
[ Android Bildirimleri ] ──(Webhook Push)──┐
[ Home Assistant / Tasker ] ──(Webhook)───┤
[ Letterboxd (Filmler) ] ──(RSS Polling)──┼──► [ SUNUCU / VERİ HAVUZU ] ◄──► [ ANDROID İSTEMCİ ]
[ Goodreads (Kitaplar) ] ──(RSS Polling)──┤    (Cloudflare D1 VEYA             (Jetpack Compose M3
[ FatSecret (Beslenme) ] ──(OAuth 1.0)───┘     Docker SQLite)                  Okuyucu & Yönetim)
```

### 1. Neden Harici Bildirim Servisi?
Android uygulamasının kendisi arka planda sürekli bildirim dinleyen hantal bir servis çalıştırmaz. Bunun yerine:
- Telefonunuza hafif ve açık kaynaklı **Notification Relay Webhook** kurulur.
- Bildirimler doğrudan sunucunuzun `/api/webhooks/notification` adresine iletilir ve veritabanına kaydedilir.
- Android uygulaması yalnızca bu verileri görüntüleyen, filtreleyen ve istatistik çıkaran hızlı ve pil dostu bir istemci olarak çalışır.

### 2. İki Farklı Sunucu Seçeneği (100% Uyumlu)

| Özellik | Seçenek A: Cloudflare Worker + D1 (Önerilen) | Seçenek B: Docker / Yerel Go Backend |
| :--- | :--- | :--- |
| **Maliyet** | **%100 Ücretsiz** (Cloudflare Free Tier) | Kendi sunucunuzda/bilgisayarınızda ücretsiz |
| **Çalışma Şekli** | Sunucusuz (Serverless Bulut) | Docker Compose ile tek komutla konteyner |
| **Erişilebilirlik** | 7/24 dünyanın her yerinden açık | Yerel ağda, VPN veya port yönlendirmeyle |
| **Veritabanı** | Cloudflare D1 (Dağıtık SQLite) | Yerel `personal_records.sqlite3` dosyası |
| **Kurulum Süresi** | ~4 dakika | ~1 dakika (Docker) |

---

## 🔑 1. Adım: FatSecret API Anahtarlarını Alma (BYOK - 2 Dakika)

FatSecret beslenme verilerinizi otomatik çekebilmek için ücretsiz geliştirici anahtarı almanız gerekir:

1. [platform.fatsecret.com](https://platform.fatsecret.com/) adresine gidin.
2. Sağ üstten **Create Account** diyerek ücretsiz bir geliştirici hesabı açın.
3. Hesabınıza giriş yaptıktan sonra **My App** veya **Create Application** butonuna tıklayın.
4. Uygulama türü olarak **Basic** (Ücretsiz) seçeneğini işaretleyin.
5. Uygulama oluştuktan sonra size iki değer verilecektir:
   - `Consumer Key`
   - `Consumer Secret`
6. Bu iki değeri not edin. *(Sunucu koduna yazmanıza gerek yok; Android uygulamasındaki Ayarlar ekranından doğrudan gireceksiniz!)*

---

## 🐳 2. Adım (Seçenek A): Docker Compose ile Kendi Sunucunu Kur (En Kolay Yerel Kurulum)

Eğer kendi sunucunuzda (Raspberry Pi, ev sunucusu, VPS veya yerel PC) çalıştırmak istiyorsanız:

1. Bilgisayarınızda **Docker** ve **Docker Compose** kurulu olsun.
2. Proje ana dizininde tek komutla sunucuyu başlatın:
   ```bash
   docker compose up -d
   ```
3. Sunucunuz hazır! Veritabanınız `./data/personal_records.sqlite3` konumunda güvenle saklanır.
4. Tarayıcınızdan `http://localhost:8787` adresini açarak çalıştığını doğrulayabilirsiniz.
5. Mobil uygulamadan erişmek için yerel ağdaki IP adresinizi kullanabilirsiniz (Örn: `http://192.168.1.50:8787`).

*(Docker kullanmadan doğrudan Go ile çalıştırmak isterseniz: `cd backend && cp .env.example .env && go run ./cmd/personal-records`)*

---

## ☁️ 2. Adım (Seçenek B): Cloudflare Worker & D1 Bulut Kurulumu (7/24 Ücretsiz)

Eğer elektrik faturası ve sunucu yönetimi olmadan 7/24 çalışan bir altyapı istiyorsanız:

1. Terminali açın ve `worker` klasörüne girin:
   ```bash
   cd worker
   npm install
   ```
2. Cloudflare hesabınıza giriş yapın:
   ```bash
   npx wrangler login
   ```
3. D1 veritabanınızı oluşturun:
   ```bash
   npx wrangler d1 create personal-records-db
   ```
4. `worker/wrangler.jsonc` dosyasındaki `database_id` alanına terminaldeki ID'yi yapıştırın.
5. Tabloları oluşturun:
   ```bash
   npx wrangler d1 execute personal-records-db --remote --file=./schema.sql
   ```
6. Buluta yükleyin:
   ```bash
   npx wrangler deploy
   ```
7. Terminalde verilen URL'yi kopyalayın (Örn: `https://personal-records-api.kullaniciadi.workers.dev`).

---

## 📱 3. Adım: Android Mobil Uygulamayı Bağlama

1. GitHub Releases sayfasından `app-release.apk` dosyasını telefonunuza indirin ve kurun.
2. Uygulamayı açın ve alttaki gezinti çubuğundan **Ayarlar** sekmesine geçin.

### A. Sunucu Bağlantısı
1. **Sunucu URL** alanına sunucu adresinizi yazın:
   - Cloudflare için: `https://personal-records-api.xxxx.workers.dev`
   - Docker / Yerel için: `http://192.168.1.xxx:8787`
2. **Kaydet** butonuna basın ve ardından **Bağlantıyı Sına** butonuna dokunun. Yeşil **"Bağlı"** rozeti belirdiğinde bağlantı hazırdır.

### B. FatSecret Bağlantısı (BYOK)
1. **FatSecret API (BYOK)** kartına `Consumer Key` ve `Consumer Secret` değerlerinizi yazıp **Anahtarları Kaydet** butonuna dokunun.
2. Beliren **"FatSecret ile Bağlan (OAuth)"** butonuna tıklayın. Tarayıcınızda açılan sayfada izin verin.
3. Uygulamaya döndüğünüzde ekranda yeşil **"OAuth ✓"** rozetini göreceksiniz.
4. "Son 30 Gün" veya "Tüm Zamanlar" butonuna dokunarak geçmiş besin kayıtlarınızı çekin.

---

## 🔔 4. Adım: Android Bildirimlerini İletme (Notification Relay)

Telefonunuza gelen bildirimleri (banka harcamaları, sağlık uygulaması uyarıları, kargo takibi vb.) sunucunuza aktarmak için:

1. **Ayarlar** sekmesindeki **Bildirim Webhook Entegrasyonu** kartına bakın.
2. Oradaki **URL'yi Kopyala** butonuna dokunun (Hedef URL otomatik hazırlanır: `https://.../api/webhooks/notification`).
3. **APK İndir** butonuna dokunarak açık kaynaklı **[Notification Relay Webhook](https://github.com/cobanov/notification-relay-webhook)** uygulamasını kurun.
4. Notification Relay uygulamasını açıp **Bildirim Erişimi** iznini onaylayın.
5. **Webhooks** sekmesinde `+` simgesine dokunun ve kopyaladığınız Webhook URL'sini yapıştırıp kaydedin.
6. **Test** butonuna basın. Başarılı olduğunda bildirimleriniz otomatik olarak Kişisel Kayıtlar veritabanına ve **Zaman Çizelgesi (Timeline)** akışına düşecektir!

---

## 🎬 5. Adım: Letterboxd ve Goodreads Entegrasyonu

### Letterboxd (Filmler)
1. **Ayarlar -> Kültür & Medya Senkronizasyonu** kartındaki Letterboxd alanına **sadece kullanıcı adınızı** yazın (Örn: `enesben_`).
   - *Not: Tam URL yazmakla uğraşmanıza gerek yoktur, sistem otomatik olarak `https://letterboxd.com/enesben_/rss/` formatına dönüştürür.*
2. **Kaydet** butonuna dokunun.
3. **Senkronize Et** butonuna bastığınızda izlediğiniz filmler ve puanlarınız kütüphanenize eklenir.
4. *Akıllı Eşleştirme:* Daha önce izlediğiniz bir filme sonradan puan verdiğinizde sistem mevcut filmi günceller, gereksiz mükerrer kayıt oluşturmaz.

### Goodreads (Kitaplar)
1. Goodreads profilinizden aldığınız kitap listenizin RSS besleme linkini yapıştırın.
2. **Kaydet** ve **Senkronize Et** butonuna basarak kitaplarınızı eşitleyin.

---

## 🧩 6. Adım: Modüler Eklenti (Plugin) Sistemi

Kişisel Kayıtlar, gelecekte eklenecek yeni veri kaynaklarını 3 ana eklenti kategorisinde kabul eder:

1. **Webhook Eklentileri (Push):**
   - Sunucunun `/api/webhooks/notification` ve benzeri uç noktalarına JSON verisi fırlatan harici istemciler.
   - Örnekler: Notification Relay, Home Assistant otomasyonları, Tasker tetikleyicileri.
2. **RSS Eklentileri (Pull / Polling):**
   - Halka açık RSS/Atom beslemelerini periyodik olarak tarayıp veritabanına kaydeden yapılar.
   - Örnekler: Letterboxd, Goodreads, blog veya haber beslemeleri.
3. **OTT / OAuth Eklentileri (API Entegrasyonları):**
   - İki taraflı token takası ile kullanıcı verilerine erişen servisler.
   - Örnekler: FatSecret, yakında planlanan Spotify dinleme geçmişi ve Steam kütüphanesi.

---

## 🛡️ Veri Egemenliği ve Yedekleme

- **JSON İndir:** Ayarlar ekranından tek dokunuşla tüm verilerinizi JSON olarak telefonunuzun `Downloads` klasörüne aktarabilirsiniz.
- **SQLite (.db) İndir:** Ham veritabanı dosyasını doğrudan cihazınıza indirebilirsiniz.
