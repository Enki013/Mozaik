# FatSecret Senkronizasyon Stratejisi ve Mimari Kararlar

Bu doküman, FatSecret beslenme verilerinin yerel SQLite veritabanına aktarılmasında uygulanan **Akıllı Tüm Zamanlar (Smart All-Time Sync)** mimarisini ve alınan teknik kararları gelecekte hatırlamak ve referans almak üzere açıklar.

---

## 1. Problem ve API Kısıtları

1. **Toplu İndirme Endpoint'inin Olmaması:**
   * Goodreads (CSV export) veya Letterboxd (RSS/CSV) tüm veriyi tek seferde verebilirken, FatSecret Platform API'sinde tek tıkla *"kullanıcının tüm geçmişini getir"* diyen tekil bir endpoint **bulunmamaktadır**.
   * FatSecret'ın detaylı besin endpoint'i (`/food-entries/v2`), her çağrıda sadece tek bir günü (`date=X` - 1970'ten bu yana geçen gün sayısı) sorgulamaya izin verir.
2. **Kotayı ve Süreyi Koruma Zorunluluğu:**
   * 1 yıllık geçmiş 365 HTTP isteği; 3 yıllık geçmiş ~1095 HTTP isteği demektir.
   * Her senkronizasyonda bu günlerin tümü baştan sona tekrar sorgulanırsa:
     * FatSecret API kotası tükenir (Rate limit / 429).
     * İşlem dakikalarca sürer ve HTTP zaman aşımı (timeout) riski oluşur.

---

## 2. Uygulanan Akıllı Mimari Çözümü

Sistemimiz **3 katmanlı akıllı önbellekleme ve senkronizasyon** yöntemiyle çalışır:

### A. Taranan Günlerin Kalıcı Olarak Kaydedilmesi (`fatsecret_scanned_days`)
* SQLite veritabanına `fatsecret_scanned_days (date_int INTEGER PRIMARY KEY, scanned_at TEXT)` tablosu eklenmiştir.
* Bir gün FatSecret'tan sorgulandığında (ister 0 yemek çıksın, ister 10 yemek çıksın), o gün veritabanında `taranmış (scanned)` olarak işaretlenir.

### B. Akıllı Atlatma Kuralı (Smart Skip):
* **Geçmiş Günler (2 günden eski):** Eğer bir geçmiş gün `fatsecret_scanned_days` tablosunda kayıtlıysa, **FatSecret API'sine hiç istek atılmaz (0 ms, 0 kota harcaması).** Doğrudan atlanır.
* **Yakın Günler (Bugün ve Dün):** Kullanıcı geriye dönük öğün ekleyebileceği veya porsiyon güncelleyebileceği için `Bugün` ve `Dün` her zaman güncel tutulur.
* **Nezaket Gecikmesi (Polite Throttle):** Eksik geçmiş taranırken ardışık istekler arasına 50 ms bekleme konularak FatSecret sunucularında hız sınırına (rate limit) takılma engellenir.

### C. Arka Plan Rutin Eşitleme (`main.go` - syncLoop)
* Kullanıcı web arayüzünde hiçbir butona basmasa dahi, arka plan döngüsü her 30-60 dakikada bir otomatik olarak **sadece son 3 günü (Bugün, Dün, Önceki Gün)** sessizce eşitler.
* Bu işlem sadece 3 HTTP isteği sürer ve ~1 saniyede tamamlanır.

---

## 3. Kullanıcı Seçenekleri

Arayüzde ve mobil API'de şu seçenekler sunulur:
1. **Bugün (`days=1`):** Anlık bugünün öğünlerini tazeler.
2. **Son 30 Gün (`days=30`):** Son 1 ayı hızlıca tarar.
3. **Son 1 Yıl (`days=365`):** Son 1 yıldaki eksik günleri tamamlar.
4. **Tüm Zamanlar (`days=1095` / 3 Yıl):** Hesabın açıldığı günden bu yana tüm eksik günleri tespit edip SQLite'a kalıcı olarak kopyalar. Zaten taranmış günler anında atlandığı için sonraki tıklamalarda saniyeler içinde tamamlanır.

---

## 4. Mobil (Android Kotlin) İstemcisi İçin Notlar

* Mobil uygulama açılışında sadece `POST /api/sync/fatsecret` `{ "days": 3 }` tetikleyebilir.
* Mobil arayüzdeki kalori grafikleri ve geçmiş listeleri, harici API'ye bağımlı olmadan yerel backend'in `/api/activity` ve `/api/fatsecret/food-entries` endpoint'lerinden anında (offline hızında) beslenir.
