package com.personalrecords.app.core.integrations

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.ui.graphics.vector.ImageVector
import com.personalrecords.app.R

enum class IntegrationCategory(val labelRes: Int) {
    ALL(R.string.catalog_filter_all),
    MUSIC(R.string.catalog_filter_music),
    MEDIA(R.string.catalog_filter_media),
    HEALTH(R.string.catalog_filter_health),
    SYSTEM(R.string.catalog_filter_system)
}

enum class IntegrationType(val displayNameRes: Int, val displayName: String) {
    API_SYNC(R.string.integration_type_api_sync, "API Senkronizasyonu"),
    RSS_FEED(R.string.integration_type_rss_feed, "RSS Beslemesi"),
    WEBHOOK(R.string.integration_type_webhook, "Webhook Entegrasyonu"),
    OAUTH_BYOK(R.string.integration_type_oauth_byok, "OAuth 1.0 (BYOK)")
}

data class IntegrationPlugin(
    val id: String,
    val name: String,
    val category: IntegrationCategory,
    val type: IntegrationType,
    val icon: ImageVector,
    val badgeRes: Int = 0,
    val shortDescRes: Int = 0,
    val longDescRes: Int = 0,
    val badge: String = "",
    val shortDescription: String = "",
    val longDescription: String = "",
    val isEnabled: Boolean = false,
    val isConfigured: Boolean = false,
    val isInDevelopment: Boolean = false,
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
            badgeRes = R.string.plugin_badge_browser_extension,
            shortDescRes = R.string.plugin_desc_web_scrobbler,
            longDescRes = R.string.plugin_long_desc_web_scrobbler,
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
            badgeRes = R.string.plugin_badge_api_sync,
            shortDescRes = R.string.plugin_desc_lastfm,
            longDescRes = R.string.plugin_long_desc_lastfm,
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
            badgeRes = R.string.plugin_badge_rss_feed,
            shortDescRes = R.string.plugin_desc_goodreads,
            longDescRes = R.string.plugin_long_desc_goodreads,
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
            badgeRes = R.string.plugin_badge_rss_feed,
            shortDescRes = R.string.plugin_desc_letterboxd,
            longDescRes = R.string.plugin_long_desc_letterboxd,
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
            badgeRes = R.string.plugin_badge_oauth_byok,
            shortDescRes = R.string.plugin_desc_fatsecret,
            longDescRes = R.string.plugin_long_desc_fatsecret,
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
            badgeRes = R.string.plugin_badge_android_webhook,
            shortDescRes = R.string.plugin_desc_health_connect,
            longDescRes = R.string.plugin_long_desc_health_connect,
            badge = "Android Webhook",
            shortDescription = "Adım, uyku, nabız ve antrenman verilerini aktarın.",
            longDescription = "Açık kaynaklı Health Connect Webhook yardımcısı ile Android cihazınızdaki sağlık ve aktivite ölçümlerini sunucunuza iletir.",
            syncServiceKey = "health_connect",
            webhookPath = "/api/webhooks/health",
            officialUrl = "https://github.com/Enki013/health-connect-webhook",
            setupSteps = listOf(
                "Health Connect Webhook APK'sını kurun ve izinleri verin.",
                "Hedef Webhook URL'sini yapıştırın.",
                "Sunucunuzda API Anahtarı koruması aktifse, Header olarak 'X-Webhook-Secret' veya URL sonuna '?api_key=...' ekleyin.",
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
            badgeRes = R.string.plugin_badge_system_webhook,
            shortDescRes = R.string.plugin_desc_notification_relay,
            longDescRes = R.string.plugin_long_desc_notification_relay,
            badge = "Sistem Webhook",
            shortDescription = "Android bildirim akışını güvenli biçimde sunucuya aktarın.",
            longDescription = "Notification Relay uygulaması ile gelen cihaz bildirimlerini kategorize ederek zaman tünelinize kaydeder.",
            syncServiceKey = "notification_relay",
            webhookPath = "/api/webhooks/notification",
            setupSteps = listOf(
                "Notification Relay APK'sını kurun ve Bildirim Erişimi verin.",
                "Webhooks sekmesinde hedef URL'yi ekleyin.",
                "Sunucunuzda API Anahtarı koruması aktifse, Header olarak 'X-Webhook-Secret' veya URL sonuna '?api_key=...' ekleyin.",
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
            badgeRes = R.string.plugin_badge_in_development,
            shortDescRes = R.string.plugin_desc_spotify,
            longDescRes = R.string.plugin_long_desc_spotify,
            badge = "Geliştirme Aşamasında",
            shortDescription = "Doğrudan Spotify Connect ve anlık çalan şarkı senkronizasyonu.",
            longDescription = "Spotify Developer API anahtarlarıyla çalma listeleri ve anlık çalan şarkı senkronizasyonu.",
            isInDevelopment = true,
            syncServiceKey = "spotify",
            officialUrl = "https://developer.spotify.com",
            setupSteps = listOf(
                "Geliştirme aşamasında: Spotify Client ID ve Secret desteği yakında eklenecektir."
            ),
            tags = listOf("spotify", "music", "streaming", "api")
        ),

        // 9. ListenBrainz Plugin
        IntegrationPlugin(
            id = "listenbrainz",
            name = "ListenBrainz",
            category = IntegrationCategory.MUSIC,
            type = IntegrationType.API_SYNC,
            icon = Icons.Default.Headset,
            badgeRes = R.string.plugin_badge_listenbrainz,
            shortDescRes = R.string.plugin_desc_listenbrainz,
            longDescRes = R.string.plugin_long_desc_listenbrainz,
            badge = "MetaBrainz",
            shortDescription = "Açık kaynak müzik dinleme geçmişi ve profil senkronizasyonu.",
            longDescription = "MetaBrainz destekli açık kaynak müzik topluluğu ListenBrainz'den dinleme geçmişinizi ve istatistiklerinizi eşitler.",
            syncServiceKey = "listenbrainz",
            officialUrl = "https://listenbrainz.org",
            setupSteps = listOf(
                "Ayarlar sekmesinden ListenBrainz kullanıcı adınızı girin.",
                "İsteğe bağlı olarak ListenBrainz profilinizden User Token ekleyin.",
                "Eşitle butonuna basarak dinlemelerinizi çekin."
            ),
            tags = listOf("music", "scrobble", "listenbrainz", "metabrainz", "open-source")
        ),

        // 10. Stats.fm Plugin
        IntegrationPlugin(
            id = "statsfm",
            name = "Stats.fm",
            category = IntegrationCategory.MUSIC,
            type = IntegrationType.API_SYNC,
            icon = Icons.Default.Equalizer,
            badgeRes = R.string.plugin_badge_statsfm,
            shortDescRes = R.string.plugin_desc_statsfm,
            longDescRes = R.string.plugin_long_desc_statsfm,
            badge = "Spotistats",
            shortDescription = "Spotify dinlemeleri ve istatistik senkronizasyonu.",
            longDescription = "Herkese açık Stats.fm (Spotistats) profilinizden Spotify akışlarını ve dinleme analitiklerini aktarır.",
            syncServiceKey = "statsfm",
            officialUrl = "https://stats.fm",
            setupSteps = listOf(
                "Stats.fm uygulamasında Profil -> Gizlilik Ayarları'ndan 'Recently Played' akışını herkese açık yapın.",
                "Ayarlar sekmesinden Stats.fm kullanıcı adınızı girin.",
                "Eşitle butonuna basarak dinleme verilerinizi aktarın."
            ),
            tags = listOf("music", "spotify", "statsfm", "spotistats", "streams", "analytics")
        ),

        // 11. Trakt Plugin
        IntegrationPlugin(
            id = "trakt",
            name = "Trakt.tv",
            category = IntegrationCategory.MEDIA,
            type = IntegrationType.API_SYNC,
            icon = Icons.Default.Tv,
            badgeRes = R.string.plugin_badge_trakt,
            shortDescRes = R.string.plugin_desc_trakt,
            longDescRes = R.string.plugin_long_desc_trakt,
            badge = "Trakt.tv",
            shortDescription = "Film ve dizi izleme geçmişi, scrobble ve puanlama senkronizasyonu.",
            longDescription = "Trakt.tv API üzerinden izlenen filmler, dizi bölümleri, izleme listesi (watchlist) ve puanlama senkronizasyonu.",
            isInDevelopment = false,
            syncServiceKey = "trakt",
            officialUrl = "https://trakt.tv",
            setupSteps = listOf(
                "Ayarlar sekmesinden Trakt.tv kullanıcı adınızı girin.",
                "İsteğe bağlı olarak kendi Trakt API Client ID bilginizi ekleyin.",
                "Senkronizasyonu başlatarak film ve dizi kayıtlarınızı tek tıkla çekin."
            ),
            tags = listOf("trakt", "tv", "movies", "shows", "series", "dizi", "film", "cinema", "scrobble")
        ),

        // 12. MDBList Plugin
        IntegrationPlugin(
            id = "mdblist",
            name = "MDBList",
            category = IntegrationCategory.MEDIA,
            type = IntegrationType.API_SYNC,
            icon = Icons.Default.VideoLibrary,
            badgeRes = R.string.plugin_badge_mdblist,
            shortDescRes = R.string.plugin_desc_mdblist,
            longDescRes = R.string.plugin_long_desc_mdblist,
            badge = "MDBList",
            shortDescription = "Gelişmiş film/dizi listeleri ve çoklu platform puan agregatörü.",
            longDescription = "MDBList API ile özel küratör listeleri, IMDb/Trakt/TMDB/Rotten Tomatoes/Metacritic puanlama senkronizasyonu.",
            isInDevelopment = false,
            syncServiceKey = "mdblist",
            officialUrl = "https://mdblist.com",
            setupSteps = listOf(
                "mdblist.com/preferences sayfasından ücretsiz API anahtarınızı alın.",
                "Ayarlar sekmesindeki MDBList alanına API anahtarını yapıştırıp kaydedin.",
                "Filmleriniz için IMDb, Rotten Tomatoes, Metacritic ve Letterboxd puanları otomatik toplanır."
            ),
            tags = listOf("mdblist", "lists", "movies", "tv", "imdb", "trakt", "tmdb", "film", "dizi")
        ),

        // 13. Simkl Plugin
        IntegrationPlugin(
            id = "simkl",
            name = "Simkl",
            category = IntegrationCategory.MEDIA,
            type = IntegrationType.API_SYNC,
            icon = Icons.Default.Tv,
            badgeRes = R.string.plugin_badge_simkl,
            shortDescRes = R.string.plugin_desc_simkl,
            longDescRes = R.string.plugin_long_desc_simkl,
            badge = "Simkl",
            shortDescription = "Film, dizi ve anime izleme geçmişi, son izlenen bölüm ve puanlama takibi.",
            longDescription = "Simkl API üzerinden film, dizi ve anime izleme kayıtlarınızı (son izlenen bölüm, durum, puan ve posterler) tek merkezde toplayın.",
            isInDevelopment = false,
            syncServiceKey = "simkl",
            officialUrl = "https://simkl.com",
            setupSteps = listOf(
                "simkl.com/settings/developer/ adresinden Client ID ve Access Token alın.",
                "Ayarlar sekmesindeki Simkl alanına bilgilerinizi yapıştırıp kaydedin.",
                "Film, dizi ve anime geçmişinizi otomatik veya manuel olarak senkronize edin."
            ),
            tags = listOf("simkl", "anime", "movies", "shows", "series", "dizi", "film", "scrobble")
        )
    )
}
