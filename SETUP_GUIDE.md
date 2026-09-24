# 🚀 Kişisel Kayıtlar (Personal Records) - Kurulum ve Kullanım Rehberi

Bu rehber, **Kişisel Kayıtlar** sistemini sıfırdan kurup kendi verilerinizle (FatSecret beslenme günlüğü, Letterboxd filmleri, Goodreads kitapları) kullanabilmeniz için hazırlanmıştır.

---

## 🏛️ Mimari Seçenekleri

Uygulama iki farklı arka yüz (backend) desteğine sahiptir. İkisi de Android uygulamasıyla %100 uyumludur:

| Özellik | Seçenek A: Cloudflare Worker + D1 (Önerilen) | Seçenek B: Go Backend + SQLite |
| :--- | :--- | :--- |
| **Maliyet** | **%100 Ücretsiz** (Cloudflare Free Tier) | Kendi cihazınızda ücretsiz |
| **Çalışma Şekli** | Sunucusuz (Serverless) Bulut | Yerel bilgisayar, Raspberry Pi, VPS veya Docker |
| **Erişilebilirlik** | 7/24 dünyanın her yerinden açık | Yerel ağda veya port yönlendirmeyle |
| **Kurulum Süresi** | ~5 dakika | ~3 dakika |

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
6. Bu iki değeri bir yere not edin. *(Sunucu konfigürasyonuyla uğraşmanıza gerek yok; bu anahtarları doğrudan Android uygulamasından gireceksiniz!)*

---

## ☁️ 2. Adım (Seçenek A): Cloudflare Worker & D1 Bulut Kurulumu

Eğer 7/24 çalışan, sıfır maliyetli ve kesintisiz bir bulut altyapısı istiyorsanız bu seçeneği tercih edin.

### Gereksinimler
- Bilgisayarınızda [Node.js](https://nodejs.org/) kurulu olmalı.
- Ücretsiz bir [Cloudflare](https://dash.cloudflare.com/) hesabı.

### Kurulum Adımları

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
   *Terminalde size bir `database_id` verilecektir.*

4. `worker/wrangler.jsonc` dosyasını açın ve `database_id` alanına aldığınız ID'yi yapıştırın:
   ```json
   "d1_databases": [
     {
       "binding": "DB",
       "database_name": "personal-records-db",
       "database_id": "BURAYA_DATABASE_ID_YAPISTIRIN"
     }
   ]
   ```

5. Veritabanı tablolarını oluşturun:
   ```bash
   npx wrangler d1 execute personal-records-db --remote --file=./schema.sql
   ```

6. Worker'ınızı buluta yükleyin:
   ```bash
   npx wrangler deploy
   ```

7. Deploy bittiğinde terminalde çıkan URL'yi kopyalayın (Örn: `https://personal-records-api.kullaniciadi.workers.dev`).

---

## 🖥️ 2. Adım (Seçenek B): Yerel Go Backend Kurulumu

Kendi bilgisayarınızda veya ev sunucunuzda çalıştırmak isterseniz:

1. Bilgisayarınızda [Go](https://go.dev/) kurulu olmalı (1.22+).
2. Ana dizinde `.env` dosyasını hazırlayın:
   ```bash
   cp .env.example .env
   ```
3. Sunucuyu başlatın:
   ```bash
   go run ./cmd/personal-records
   ```
4. Sunucunuz `http://localhost:8787` adresinde çalışacaktır. Mobil uygulamadan erişmek için yerel ağdaki Wi-Fi IP adresinizi kullanabilirsiniz (Örn: `http://192.168.1.50:8787`).

---

## 📱 3. Adım: Android Mobil Uygulamayı Bağlama

1. `app-debug.apk` dosyasını Android telefonunuza yükleyin.
2. Uygulamayı açın ve sağ üst köşedeki **Ayarlar** (çark) simgesine dokunun.

### A. Sunucu Bağlantısı
1. **Sunucu URL** alanına:
   - Cloudflare Worker kullanıyorsanız: `https://personal-records-api.xxxx.workers.dev`
   - Go backend kullanıyorsanız: `http://192.168.1.xxx:8787`
2. **Kaydet** butonuna basın, ardından **Bağlantıyı Sına** butonuna dokunun. Ekranda yeşil **"Bağlı"** rozetini gördüğünüzde bağlantı hazırdır.

### B. FatSecret Bağlantısı
1. **FatSecret API Anahtarları** kartındaki alanlara:
   - 1. Adımda aldığınız `Consumer Key` ve `Consumer Secret` değerlerini yazın.
   - **Anahtarları Kaydet** butonuna basın.
2. Ekranda beliren yeşil **"FatSecret ile Bağlan (OAuth)"** butonuna tıklayın.
3. Telefonunuzun tarayıcısında FatSecret giriş ve izin sayfası açılacaktır. Hesabınıza giriş yapıp **İzin Ver / Allow** seçeneğini onaylayın.
4. "Bağlantı Başarılı" sayfasını görüp uygulamaya geri döndüğünüzde ekranda **"OAuth ✓"** rozeti çıkacaktır.

### C. İlk Eşitleme
- **FatSecret Senkronizasyonu** kartından **"Tüm Zamanlar"** veya **"Son 30 Gün"** butonuna basarak tüm geçmiş yemeklerinizi ve kalorilerinizi tek tıkla cihazınıza çekin!

---

## 📚 4. Adım: Letterboxd ve Goodreads Entegrasyonu

### Letterboxd (Filmler)
1. Uygulamadaki Ayarlar menüsünden veya API üzerinden Letterboxd RSS bağlantınızı tanımlayabilirsiniz:
   - `https://letterboxd.com/KULLANICI_ADINIZ/rss/`
2. **Letterboxd Sync** butonuna tıkladığınızda tüm izlediğiniz ve puanladığınız filmler günlüğünüze eklenir.
3. *Not: Önceden izlediğiniz bir filme sonradan puan verdiğinizde sistem akıllıca mevcut filmi günceller, mükerrer veya hatalı "tekrar izlendi" kaydı oluşturmaz.*

### Goodreads (Kitaplar)
1. Goodreads profilinizden kitap listenizin RSS besleme linkini alın.
2. **Goodreads Sync** butonuna basarak okuduğunuz ve okumakta olduğunuz kitapları anında eşitleyin.

---

## 🛡️ Veri Egemenliği ve Yedekleme

Verileriniz tamamen size aittir:
- **JSON Dışa Aktar:** Ayarlar ekranındaki "JSON İndir" butonuna tıklayarak beslenme, film ve kitap verilerinizi tek bir JSON dosyasında cihazınızın `Downloads` klasörüne indirebilirsiniz.
- **SQLite (.db) İndir:** "SQLite (.db) İndir" butonuna tıklayarak doğrudan ham veritabanınızı alıp istediğiniz yerde saklayabilirsiniz.
