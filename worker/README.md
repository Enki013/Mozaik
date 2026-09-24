# Personal Records - Cloudflare Worker & D1 Backend

Bu modül, Personal Records uygulamasının **Cloudflare Worker (Hono)** ve **Cloudflare D1 (Serverless SQLite)** altyapısında 7/24 ücretsiz ve sunucusuz çalışmasını sağlar.

---

## Dizin Yapısı

- `src/index.ts`: Tüm REST API uç noktaları, Hono yönlendiricisi ve Cron trigger senkronizasyon mantığı.
- `schema.sql`: D1 veritabanı tablo şemaları.
- `wrangler.toml`: Cloudflare Worker ve D1 bağlama yapılandırması.
- `scripts/export_sqlite_to_sql.py`: Yerel `personal_records.sqlite3` dosyasından D1 uyumlu SQL dump üreten betik.
- `scripts/migrate_sqlite_to_d1.sh`: Verileri D1'e (yerel veya uzak) aktaran ana göç betiği.

---

## Yerel Geliştirme (Local Emulation)

Tüm sistemi bilgisayarınızda yerel D1 ile çalıştırmak için:

```bash
cd worker

# 1. Bağımlılıkları yükleyin
npm install

# 2. Yerel D1 veritabanını oluşturun ve verileri aktarın
npm run db:migrate:local

# 3. Yerel Worker sunucusunu başlatın
npm run dev
# veya belirli bir portta çalıştırmak için:
npx wrangler dev --port 8788 --ip 0.0.0.0
```

---

## Cloudflare Bulutuna Canlı Dağıtım (Remote Deployment)

Sistemi 7/24 dünyanın her yerinden erişilebilir yapmak için şu adımları izleyin:

### 1. Cloudflare Girişi Yapın
Terminalden Cloudflare hesabınıza giriş yapın:
```bash
npx wrangler login
```

### 2. Uzak D1 Veritabanını Oluşturun
```bash
npx wrangler d1 create personal-records-db
```
Bu komutun çıktısında yer alan `database_id` değerini `worker/wrangler.toml` dosyasındaki `database_id` alanına yapıştırın:
```toml
[[d1_databases]]
binding = "DB"
database_name = "personal-records-db"
database_id = "BURAYA_DATABASE_ID_YAZILACAK"
```

### 3. Mevcut Verilerinizi Cloudflare'e Yükleyin (Migration)
```bash
npm run db:migrate:remote
```

### 4. Worker API'yi Canlıya Alın
```bash
npm run deploy
```
İşlem tamamlandığında Cloudflare size bir canlı URL verecektir (örn. `https://personal-records-api.<kullanici_adiniz>.workers.dev`).

### 5. Mobil Uygulamada URL'yi Güncelleyin
Android uygulamanızı açın:
1. **Ayarlar** ekranına gidin.
2. **Sunucu URL** kutusuna yukarıdaki canlı Cloudflare adresinizi girin:
   ```text
   https://personal-records-api.<kullanici_adiniz>.workers.dev
   ```
3. **Kaydet** ve **Bağlantıyı Sına** butonlarına basın.

Artık uygulamanız evde bilgisayarınız açık olmasa bile 7/24 kesintisiz çalışacaktır!

---

## D1'den Verileri Geri Yedekleme (Export)

D1'deki verilerinizi istediğiniz zaman bilgisayarınıza SQL dosyası olarak indirebilirsiniz:

```bash
npx wrangler d1 export personal-records-db --remote --output=backup.sql
```

Bu yedeği yerel SQLite veritabanına dönüştürmek için:
```bash
sqlite3 local.db < backup.sql
```
Ayrıca mobil uygulamadaki Ayarlar > "Veri Egemenliği" altındaki "JSON İndir" ve "SQLite (.db) İndir" butonları da canlı Cloudflare Worker üzerinden anlık yedek indirmeye devam eder.
