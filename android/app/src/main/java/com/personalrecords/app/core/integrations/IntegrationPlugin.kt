package com.personalrecords.app.core.integrations

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.ui.graphics.vector.ImageVector
import com.personalrecords.app.R

enum class IntegrationCategory(val labelRes: Int) {
    ALL(R.string.catalog_filter_all),
    MUSIC(R.string.catalog_filter_music),
    MEDIA(R.string.catalog_filter_media),
    HEALTH(R.string.catalog_filter_health),
    SYSTEM(R.string.catalog_filter_system)
}

enum class IntegrationType(val displayName: String) {
    API_SYNC("API Senkronizasyonu"),
    RSS_FEED("RSS Beslemesi"),
    WEBHOOK("Webhook Entegrasyonu"),
    OAUTH_BYOK("OAuth 1.0 (BYOK)")
}

data class IntegrationPlugin(
    val id: String,
    val name: String,
    val category: IntegrationCategory,
    val type: IntegrationType,
    val icon: ImageVector,
    val badge: String,
    val shortDescription: String,
    val longDescription: String,
    val isEnabled: Boolean = false,
    val isConfigured: Boolean = false,
    val syncServiceKey: String? = null,
    val webhookPath: String? = null,
    val officialUrl: String? = null,
    val setupSteps: List<String> = emptyList(),
    val tags: List<String> = emptyList()
)

object PluginRegistry {
    val defaultPlugins = listOf(
        // 1. Web Scrobbler Plugin
        IntegrationPlugin(
            id = "web_scrobbler",
            name = "Web Scrobbler",
            category = IntegrationCategory.MUSIC,
            type = IntegrationType.WEBHOOK,
            icon = Icons.Default.Extension,
            badge = "Tarayıcı Eklentisi",
            shortDescription = "Chrome, Firefox ve Edge tarayıcınızdan dinlenen müzikleri anında sunucunuza aktarın.",
            longDescription = "YouTube, Spotify Web, SoundCloud, Bandcamp ve 300'den fazla web müzik çalarını destekleyen açık kaynaklı Web Scrobbler eklentisi. Dinlemelerinizi doğrudan sunucunuzun webhook adresine iletir.",
            syncServiceKey = "web_scrobbler",
            webhookPath = "/api/webhooks/music",
            officialUrl = "https://web-scrobbler.com/",
            setupSteps = listOf(
                "Web Scrobbler eklenti ayarlarında Accounts -> Webhook bölümünü açın.",
                "Hedef Webhook URL'sini yapıştırın ve kaydedin.",
                "Tarayıcınızda müzik dinlemeye başlayın; dinlemeler anında veritabanınıza işlenir."
            ),
            tags = listOf("music", "scrobbler", "scrobble", "youtube", "spotify", "browser", "extension")
        ),

        // 2. Last.fm Plugin
        IntegrationPlugin(
            id = "lastfm",
            name = "Last.fm",
            category = IntegrationCategory.MUSIC,
            type = IntegrationType.API_SYNC,
            icon = Icons.Default.MusicNote,
            badge = "API Senkronizasyonu",
            shortDescription = "Müzik dinleme geçmişi ve profil senkronizasyonu.",
            longDescription = "Last.fm hesabınızdaki dinleme geçmişini düzenli aralıklarla sunucunuza çeker ve müzik istatistiklerinizi günceller.",
            syncServiceKey = "lastfm",
            officialUrl = "https://www.last.fm",
            setupSteps = listOf(
                "Ayarlar sekmesinden Last.fm kullanıcı adınızı girin.",
                "İsteğe bağlı olarak 32 haneli Last.fm API Key ekleyin.",
                "Tek tıkla veya otomatik arka plan senkronizasyonu ile dinlemeleri çekin."
            ),
            tags = listOf("music", "scrobble", "lastfm", "api")
        ),

        // 3. Goodreads Plugin
        IntegrationPlugin(
            id = "goodreads",
            name = "Goodreads RSS",
            category = IntegrationCategory.MEDIA,
            type = IntegrationType.RSS_FEED,
            icon = Icons.AutoMirrored.Filled.MenuBook,
            badge = "RSS Beslemesi",
            shortDescription = "Kitap okuma günlüğü ve raflar senkronizasyonu.",
            longDescription = "Goodreads profilinizin herkese açık RSS beslemesini kullanarak okuduğunuz ve okumayı planladığınız kitapları eşitler.",
            syncServiceKey = "goodreads",
            officialUrl = "https://www.goodreads.com",
            setupSteps = listOf(
                "Goodreads profilinizin RSS bağlantısını kopyalayın.",
                "Ayarlar sekmesindeki Goodreads alanına yapıştırın.",
                "Senkronize et butonuna basarak kitaplarınızı getirin."
            ),
            tags = listOf("books", "reading", "goodreads", "rss", "kitap")
        ),

        // 4. Letterboxd Plugin
        IntegrationPlugin(
            id = "letterboxd",
            name = "Letterboxd RSS",
            category = IntegrationCategory.MEDIA,
            type = IntegrationType.RSS_FEED,
            icon = Icons.Default.Movie,
            badge = "RSS Beslemesi",
            shortDescription = "Film izleme günlüğü ve puanlar senkronizasyonu.",
            longDescription = "Letterboxd profilinizin RSS beslemesini okuyarak izlediğiniz filmleri, puanları ve günlük kayıtlarını sunucunuza aktarır.",
            syncServiceKey = "letterboxd",
            officialUrl = "https://letterboxd.com",
            setupSteps = listOf(
                "Letterboxd profilinizin RSS adresini bulun (letterboxd.com/kullanici/rss).",
                "Ayarlar bölümünde ilgili kutuya yapıştırıp kaydedin.",
                "Senkronizasyon ile tüm izleme geçmişini aktarın."
            ),
            tags = listOf("movies", "cinema", "letterboxd", "film", "rss")
        ),

        // 5. FatSecret Plugin
        IntegrationPlugin(
            id = "fatsecret",
            name = "FatSecret",
            category = IntegrationCategory.HEALTH,
            type = IntegrationType.OAUTH_BYOK,
            icon = Icons.Default.Restaurant,
            badge = "OAuth 1.0 (BYOK)",
            shortDescription = "Günlük kalori, makro ve öğün günlüğü entegrasyonu.",
            longDescription = "Kendi FatSecret Platform API anahtarlarınız (BYOK) ile güvenli bağlantı kurarak besin tüketim verilerinizi eşitler.",
            syncServiceKey = "fatsecret",
            officialUrl = "https://platform.fatsecret.com",
            setupSteps = listOf(
                "FatSecret Developer Portal'dan Consumer Key ve Secret edinin.",
                "Ayarlar sekmesine bu anahtarları kaydedin.",
                "OAuth ile hesabınızı bağlayın ve günlük öğünlerinizi çekin."
            ),
            tags = listOf("nutrition", "calories", "macros", "fatsecret", "diet", "yemek")
        ),

        // 6. Health Connect Webhook Plugin
        IntegrationPlugin(
            id = "health_connect",
            name = "Health Connect Webhook",
            category = IntegrationCategory.HEALTH,
            type = IntegrationType.WEBHOOK,
            icon = Icons.Default.Favorite,
            badge = "Android Webhook",
            shortDescription = "Adım, uyku, nabız ve antrenman verilerini aktarın.",
            longDescription = "Açık kaynaklı Health Connect Webhook yardımcısı ile Android cihazınızdaki sağlık ve aktivite ölçümlerini sunucunuza iletir.",
            syncServiceKey = "health_connect",
            webhookPath = "/api/webhooks/health",
            officialUrl = "https://github.com/Enki013/health-connect-webhook",
            setupSteps = listOf(
                "Health Connect Webhook APK'sını kurun ve izinleri verin.",
                "Hedef Webhook URL'sini yapıştırın.",
                "Eşitleme sıklığını seçip arka plan aktarımını başlatın."
            ),
            tags = listOf("health", "steps", "heart", "sleep", "android", "fitness", "sağlık")
        ),

        // 7. Notification Relay Plugin
        IntegrationPlugin(
            id = "notification_relay",
            name = "Notification Relay",
            category = IntegrationCategory.SYSTEM,
            type = IntegrationType.WEBHOOK,
            icon = Icons.Default.Notifications,
            badge = "Sistem Webhook",
            shortDescription = "Android bildirim akışını güvenli biçimde sunucuya aktarın.",
            longDescription = "Notification Relay uygulaması ile gelen cihaz bildirimlerini kategorize ederek zaman tünelinize kaydeder.",
            syncServiceKey = "notification_relay",
            webhookPath = "/api/webhooks/notification",
            setupSteps = listOf(
                "Notification Relay APK'sını kurun ve Bildirim Erişimi verin.",
                "Webhooks sekmesinde hedef URL'yi ekleyin.",
                "Test bildirimini gönderip kaydı doğrulayın."
            ),
            tags = listOf("notifications", "system", "relay", "webhook", "android", "bildirim")
        ),

        // 8. Spotify Direct API Plugin (Coming Soon)
        IntegrationPlugin(
            id = "spotify",
            name = "Spotify Direct API",
            category = IntegrationCategory.MUSIC,
            type = IntegrationType.API_SYNC,
            icon = Icons.Default.Podcasts,
            badge = "Geliştirme Aşamasında",
            shortDescription = "Doğrudan Spotify Connect ve anlık çalan şarkı senkronizasyonu.",
            longDescription = "Spotify Developer API anahtarlarıyla çalma listeleri ve anlık çalan şarkı senkronizasyonu.",
            syncServiceKey = "spotify",
            officialUrl = "https://developer.spotify.com",
            setupSteps = listOf(
                "Geliştirme aşamasında: Spotify Client ID ve Secret desteği yakında eklenecektir."
            ),
            tags = listOf("spotify", "music", "streaming", "api")
        )
    )
}
