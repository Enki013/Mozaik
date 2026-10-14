# Kişisel Kayıtlar (Personal Records) - Geliştirme Yol Haritası (Roadmap)

Bu belge, projenin tamamlanan mimari ve performans aşamalarını ve gelecekte planlanan geliştirme fazlarını detaylandırmaktadır.

---

## ✅ Aşama 1: Production-Readiness & Jetpack Compose Performance (Tamamlandı)

- [x] **Katı MVI & Atomik Durum Yönetimi:**
  - Tüm 9 ViewModel (`Overview`, `Records`, `Timeline`, `Books`, `Movies`, `Nutrition`, `Notifications`, `HealthConnect`, `Settings`) doğrudan değer atamaları yerine atomik `_uiState.update { ... }` yapısına geçirildi.
  - Recomposition optimizasyonu için tüm `UiState` sınıfları `@Immutable` ile işaretlendi (Strong Skipping uyumu).
  - 9 ekranın tamamında Flow dinlemeleri `collectAsStateWithLifecycle()` ile yaşam döngüsüne duyarlı hale getirildi (arka planda pil ve CPU tüketimini durdurma).
- [x] **LazyLayout & Scroll Performansı:**
  - `BooksScreen`, `MoviesScreen`, `NutritionScreen`, `NotificationsScreen`, `HealthConnectScreen` ve `OverviewScreen` üzerindeki index bağımlı key antipattern'ı (`_$index`) kaldırıldı.
  - Key'ler saf domain benzersiz kimliklerine (`book_id`, `film_key`, `food_entry_id`, `id`) bağlandı; gereksiz item yıkımı ve scroll takılmaları önlendi.
- [x] **Ağ & Threading Optimizasyonu:**
  - `NetworkClient` içinde her istekte diskten Datastore okuyan bloklayıcı `runBlocking` kaldırıldı; in-memory `AtomicReference` önbellek kuruldu.
  - Release derlemelerinde `HttpLoggingInterceptor` devre dışı bırakıldı (`BuildConfig.DEBUG` kontrolü).
- [x] **Güvenlik & Google Play Politikaları:**
  - `network_security_config.xml` ile production'da HTTPS zorunlu kılındı, yerel emülatör loopback adresleri beyaz listeye alındı.
  - `AndroidManifest.xml`'den blanket `android:usesCleartextTraffic="true"` bayrağı kaldırıldı.
  - Android 12+ (`data_extraction_rules.xml`) ve Android 11- (`full_backup_content.xml`) kuralları tanımlanarak kullanıcı API anahtarları ve ayarlarının bulut yedeklemelerine sızması engellendi.
- [x] **Health Connect Entegrasyonu:**
  - Cloudflare Worker (`schema.sql` ve `src/index.ts`), Go Backend ve Android uygulamasına uçtan uca Health Connect webhook & sync desteği eklendi.
- [x] **Derleme & Release Doğrulaması:**
  - `lintRelease` 0 hata ile başarıyla tamamlandı.
  - R8 Full Mode optimizasyonu ile 2.45 MB'lık release APK üretildi.

---

## 🚀 Aşama 2: Compose Adaptive & Çoklu Form Faktör Desteği (Planlanan - Sıradaki Faz)

> **Referans Skill:** [`.agents/skills/compose-adaptive/`](../.agents/skills/compose-adaptive/SKILL.md)  
> **Hedef:** Telefon, tablet, katlanabilir cihazlar (foldables), masaüstü ve yatay (landscape) modda kusursuz uyum sağlayan arayüz.

### 1. Pencere Boyutu Sınıflandırması (Window Size Classes)
- `androidx.compose.material3.adaptive:adaptive` kütüphanesi entegre edilecek.
- Ekran genişliği `Compact` (< 600dp), `Medium` (600dp - 840dp) ve `Expanded` (> 840dp) olarak dinamik sınıflandırılacak.

### 2. Dinamik Navigasyon (Adaptive Navigation)
- **Telefon Dikey (Compact):** Mevcut floating / edge-to-edge alt çubuk (`NavigationBar`).
- **Tablet / Katlanabilir / Landscape (Medium & Expanded):** Ekranın sol kenarına yerleşen `NavigationRail`. Böylece geniş ekranlarda dikey alan kaybolmayacak ve tablet ergonomisi sağlanacak.

### 3. Adaptif Liste ve Grid Dizilimi (Multi-Column Layouts)
- **Geniş Ekranlarda Tek Sütun Sorunu:** Tabletlerde tek sütunlu kartlar ekran boyunca aşırı uzamakta.
- **Çözüm:** `BooksScreen`, `MoviesScreen`, `NutritionScreen`, `NotificationsScreen` ve `HealthConnectScreen` listeleri:
  - Telefonlarda 1 sütunlu kart yapısını koruyacak,
  - `Medium` ve `Expanded` pencerelerde `LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 340.dp))` ile otomatik 2 veya 3 sütunlu adaptif kart ızgarasına dönüşecek.

### 4. İki Panelli Düzen (List-Detail Scaffold)
- Geniş ekranlarda bir kitaba, filme veya besine tıklandığında `ModalBottomSheet` açmak yerine:
  - Sol tarafta liste (`Pane 1`),
  - Sağ tarafta kalıcı detay ve düzenleme paneli (`Pane 2`) yan yana gösterilecek.

### 5. Akıllı Scroll ile App Bar Daraltma
- Uzun kaydırma gerektiren listelerde `TopAppBarDefaults.enterAlwaysScrollBehavior()` kullanılarak kaydırma esnasında üst başlık gizlenecek, kullanıcıya maksimum okuma alanı tanınacak.

---

## 🔮 Aşama 3: İleri Entegrasyonlar & Arka Plan Senkronizasyonu (Gelecek Vizyon)

- [ ] **WorkManager ile Arka Plan Health Connect Senkronizasyonu:**
  - Health Connect verilerini kullanıcı uygulamayı açmasa bile arka planda periyodik olarak sunucuya aktaran worker servisi.
- [ ] **Yeni Servis Eklentileri:**
  - Spotify (Dinleme geçmişi eklentisi).
  - Steam / Oyun kütüphanesi eklentisi.
- [ ] **Widget Desteği:**
  - Günlük kalori, adım ve son aktiviteleri ana ekranda gösteren Jetpack Glance widget'ları.
