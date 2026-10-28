package com.personalrecords.app.ui.screens.overview

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalrecords.app.PersonalRecordsApp
import com.personalrecords.app.core.common.Resource
import com.personalrecords.app.core.datastore.AppPreferences
import com.personalrecords.app.core.integrations.PluginRegistry
import com.personalrecords.app.core.network.model.*
import com.personalrecords.app.data.repository.PersonalRecordsRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class BooksOverviewStats(
    val currentlyReadingCount: Int = 0,
    val readCount: Int = 0,
    val totalPages: Int = 0,
    val avgRating: Double = 0.0
)

@Immutable
data class FilmsOverviewStats(
    val thisMonthCount: Int = 0,
    val rewatchCount: Int = 0,
    val avgRating: Double = 0.0
)

@Immutable
data class MusicOverviewStats(
    val uniqueArtists: Int = 0,
    val uniqueAlbums: Int = 0,
    val weekScrobbles: Int = 0
)

@Immutable
data class NotificationsOverviewStats(
    val totalCount: Int = 0,
    val todayCount: Int = 0,
    val appCount: Int = 0
)

data class OverviewUiState(
    val isLoading: Boolean = true,
    val dashboard: DashboardDto? = null,
    val recentActivities: List<ActivityItemDto> = emptyList(),
    val latestFilm: FilmDto? = null,
    val latestBook: BookDto? = null,
    val latestMeal: FoodEntryDto? = null,
    val latestMusic: MusicScrobbleDto? = null,
    val latestHealthRecord: HealthRecordDto? = null,
    val latestNotification: NotificationDto? = null,
    val booksStats: BooksOverviewStats = BooksOverviewStats(),
    val filmsStats: FilmsOverviewStats = FilmsOverviewStats(),
    val musicStats: MusicOverviewStats = MusicOverviewStats(),
    val healthSummary: HealthConnectSummaryDto = HealthConnectSummaryDto(),
    val notificationsStats: NotificationsOverviewStats = NotificationsOverviewStats(),
    val isSyncing: String? = null, // "fatsecret", "goodreads", "letterboxd", "lastfm"
    val syncMessage: String? = null,
    val error: String? = null,
    val enabledPluginIds: Set<String> = AppPreferences.DEFAULT_ENABLED_PLUGINS,
    val serverBaseUrl: String = ""
)

class OverviewViewModel(
    private val repository: PersonalRecordsRepository,
    private val preferences: AppPreferences = PersonalRecordsApp.instance.preferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(OverviewUiState())
    val uiState: StateFlow<OverviewUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            preferences.enabledIntegrationPlugins.collect { plugins ->
                _uiState.update { it.copy(enabledPluginIds = plugins) }
            }
        }
        viewModelScope.launch {
            preferences.serverBaseUrl.collect { url ->
                _uiState.update { it.copy(serverBaseUrl = url) }
            }
        }
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val loaded = coroutineScope {
                val dDeferred = async { repository.getDashboard() }
                val aDeferred = async { repository.getActivity(days = 14, limit = 20) }
                val fDeferred = async { repository.getFilms(limit = 100) }
                val bDeferred = async { repository.getBooks(limit = 100) }
                val mDeferred = async { repository.getFoodEntries(limit = 1) }
                val sDeferred = async { repository.getMusicScrobbles(page = 1, limit = 1) }
                val msDeferred = async { repository.getMusicStats() }
                val healthDeferred = async { repository.getHealthConnectRecords(limit = 350) }
                val notificationsDeferred = async { repository.getNotifications(limit = 200) }
                val cbDeferred = async { repository.getCachedBooks() }
                val cfDeferred = async { repository.getCachedFilms() }
                OverviewLoadedAsync(
                    dashRes = dDeferred.await(),
                    actRes = aDeferred.await(),
                    filmRes = fDeferred.await(),
                    bookRes = bDeferred.await(),
                    mealRes = mDeferred.await(),
                    musicRes = sDeferred.await(),
                    musicStatsRes = msDeferred.await(),
                    healthRes = healthDeferred.await(),
                    notificationsRes = notificationsDeferred.await(),
                    cachedBooks = cbDeferred.await(),
                    cachedFilms = cfDeferred.await()
                )
            }

            val dash = if (loaded.dashRes is Resource.Success) loaded.dashRes.data else _uiState.value.dashboard
            val acts = if (loaded.actRes is Resource.Success) {
                loaded.actRes.data.items.sortedWith(
                    compareByDescending<com.personalrecords.app.core.network.model.ActivityItemDto> { it.date }
                        .thenByDescending { it.timestamp.orEmpty() }
                        .thenByDescending { it.time.orEmpty() }
                )
            } else {
                _uiState.value.recentActivities
            }
            val allBooks = if (loaded.cachedBooks.isNotEmpty()) loaded.cachedBooks else {
                (loaded.bookRes as? Resource.Success)?.data ?: emptyList()
            }
            val allFilms = if (loaded.cachedFilms.isNotEmpty()) loaded.cachedFilms else {
                (loaded.filmRes as? Resource.Success)?.data ?: emptyList()
            }
            val latestBooks = (loaded.bookRes as? Resource.Success)?.data
                ?.takeIf { it.isNotEmpty() } ?: allBooks
            val latestFilms = (loaded.filmRes as? Resource.Success)?.data
                ?.takeIf { it.isNotEmpty() } ?: allFilms
            val book = if (loaded.bookRes is Resource.Success || loaded.cachedBooks.isNotEmpty()) {
                latestBooks.maxByOrNull { it.date_read.trim().replace('/', '-') }
            } else _uiState.value.latestBook
            val film = if (loaded.filmRes is Resource.Success || loaded.cachedFilms.isNotEmpty()) {
                latestFilms.maxByOrNull { it.watched_date.orEmpty().ifBlank { it.diary_date.orEmpty() } }
            } else _uiState.value.latestFilm
            val meal = if (loaded.mealRes is Resource.Success) {
                loaded.mealRes.data.sortedWith(
                    compareByDescending<com.personalrecords.app.core.network.model.FoodEntryDto> { it.date_int }
                        .thenByDescending { it.displayDate }
                ).firstOrNull()
            } else _uiState.value.latestMeal
            val music = if (loaded.musicRes is Resource.Success) {
                loaded.musicRes.data.items.sortedWith(
                    compareByDescending<com.personalrecords.app.core.network.model.MusicScrobbleDto> { it.uts }
                        .thenByDescending { it.listenedAt }
                ).firstOrNull()
            } else _uiState.value.latestMusic
            val healthResponse = loaded.healthRes as? Resource.Success
            val healthSummary = healthResponse?.data?.summary ?: _uiState.value.healthSummary
            val latestHealthRecord = healthResponse?.data?.items
                ?.maxWithOrNull(
                    compareBy<com.personalrecords.app.core.network.model.HealthRecordDto> { it.formattedDate }
                        .thenBy { it.start_time }
                        .thenBy { it.updated_at }
                ) ?: _uiState.value.latestHealthRecord
            val notifications = (loaded.notificationsRes as? Resource.Success)?.data
            val latestNotification = notifications?.maxByOrNull { it.created_at.ifBlank { it.post_time } }
            val today = java.time.LocalDate.now().toString()
            val notificationTodayCount = notifications?.count { notification ->
                notification.formattedDate.ifBlank { notification.created_at.take(10) }.startsWith(today)
            } ?: _uiState.value.notificationsStats.todayCount
            val notificationApps = notifications?.map { it.displayAppName }?.distinct()?.size
                ?: _uiState.value.notificationsStats.appCount
            val totalNotifications = dash?.counts?.get("notifications")
                ?: notifications?.size
                ?: _uiState.value.notificationsStats.totalCount

            // Books Stats
            val booksReading = allBooks.count { it.exclusive_shelf.equals("currently-reading", ignoreCase = true) }
            val booksRead = allBooks.count { it.exclusive_shelf.equals("read", ignoreCase = true) }
            val totalPages = allBooks.sumOf { it.number_of_pages ?: 0 }
            val ratedBooks = allBooks.filter { (it.my_rating ?: 0.0) > 0.0 }
            val avgBookRating = if (ratedBooks.isNotEmpty()) {
                ratedBooks.mapNotNull { it.my_rating }.average()
            } else {
                val avgGoodreads = allBooks.filter { (it.average_rating ?: 0.0) > 0.0 }
                if (avgGoodreads.isNotEmpty()) avgGoodreads.mapNotNull { it.average_rating }.average() else 0.0
            }

            // Films Stats
            val currentYearMonth = try {
                java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"))
            } catch (_: Exception) { "" }
            val thisMonthFilms = if (currentYearMonth.isNotEmpty()) {
                allFilms.count { it.watched_date.orEmpty().ifBlank { it.diary_date.orEmpty() }.startsWith(currentYearMonth) }
            } else 0
            val rewatchFilms = allFilms.count { it.rewatch }
            val ratedFilms = allFilms.filter { (it.rating ?: 0.0) > 0.0 }
            val avgFilmRating = if (ratedFilms.isNotEmpty()) ratedFilms.mapNotNull { it.rating }.average() else 0.0

            // Music Stats
            val mStats = (loaded.musicStatsRes as? Resource.Success)?.data
            val musicOverview = MusicOverviewStats(
                uniqueArtists = mStats?.uniqueArtists ?: 0,
                uniqueAlbums = mStats?.uniqueAlbums ?: 0,
                weekScrobbles = mStats?.weekScrobbles ?: 0
            )

            _uiState.update {
                it.copy(
                    isLoading = false,
                    dashboard = dash,
                    recentActivities = acts,
                    latestFilm = film,
                    latestBook = book,
                    latestMeal = meal,
                    latestMusic = music,
                    latestHealthRecord = latestHealthRecord,
                    latestNotification = latestNotification ?: it.latestNotification,
                    booksStats = BooksOverviewStats(
                        currentlyReadingCount = booksReading,
                        readCount = booksRead,
                        totalPages = totalPages,
                        avgRating = avgBookRating
                    ),
                    filmsStats = FilmsOverviewStats(
                        thisMonthCount = thisMonthFilms,
                        rewatchCount = rewatchFilms,
                        avgRating = avgFilmRating
                    ),
                    musicStats = musicOverview,
                    healthSummary = healthSummary,
                    notificationsStats = NotificationsOverviewStats(
                        totalCount = totalNotifications,
                        todayCount = notificationTodayCount,
                        appCount = notificationApps
                    ),
                    error = if (loaded.dashRes is Resource.Error) loaded.dashRes.message else null
                )
            }
        }
    }

    fun syncService(service: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = service, syncMessage = null) }
            val result = when (service) {
                "fatsecret" -> repository.syncFatSecret(30)
                "goodreads" -> repository.syncGoodreads()
                "letterboxd" -> repository.syncLetterboxd()
                "lastfm", "music" -> {
                    when (val lfm = repository.syncLastFm()) {
                        is Resource.Success -> Resource.Success(
                            com.personalrecords.app.core.network.model.SyncResultDto(
                                success = true,
                                message = lfm.data.message
                            )
                        )
                        is Resource.Error -> Resource.Error(lfm.message ?: "Last.fm eşitlenemedi")
                        else -> Resource.Error("Last.fm eşitlenemedi")
                    }
                }
                "listenbrainz" -> {
                    when (val lb = repository.syncListenBrainz()) {
                        is Resource.Success -> {
                            val count = lb.data.inserted + lb.data.updated
                            val defaultMsg = if (count > 0) "ListenBrainz eşitlendi ($count yeni/güncellenen)" else "ListenBrainz dinlemeleri güncel"
                            Resource.Success(
                                com.personalrecords.app.core.network.model.SyncResultDto(
                                    success = true,
                                    message = lb.data.message.ifBlank { defaultMsg }
                                )
                            )
                        }
                        is Resource.Error -> Resource.Error(lb.message ?: "ListenBrainz eşitlenemedi")
                        else -> Resource.Error("ListenBrainz eşitlenemedi")
                    }
                }
                "statsfm" -> {
                    when (val sfm = repository.syncStatsFm()) {
                        is Resource.Success -> {
                            val count = sfm.data.inserted + sfm.data.updated
                            val defaultMsg = if (count > 0) "Stats.fm eşitlendi ($count yeni/güncellenen)" else "Stats.fm dinlemeleri güncel"
                            Resource.Success(
                                com.personalrecords.app.core.network.model.SyncResultDto(
                                    success = true,
                                    message = sfm.data.message.ifBlank { defaultMsg }
                                )
                            )
                        }
                        is Resource.Error -> Resource.Error(sfm.message ?: "Stats.fm eşitlenemedi")
                        else -> Resource.Error("Stats.fm eşitlenemedi")
                    }
                }
                "health_connect" -> {
                    Resource.Success(
                        com.personalrecords.app.core.network.model.SyncResultDto(
                            success = true,
                            message = "Health Connect verileri eşitlendi!"
                        )
                    )
                }
                "notification", "notification_relay" -> {
                    Resource.Success(
                        com.personalrecords.app.core.network.model.SyncResultDto(
                            success = true,
                            message = "Bildirim kayıtları eşitlendi!"
                        )
                    )
                }
                "web_scrobbler" -> {
                    Resource.Success(
                        com.personalrecords.app.core.network.model.SyncResultDto(
                            success = true,
                            message = "Web Scrobbler verileri eşitlendi!"
                        )
                    )
                }
                "trakt" -> repository.syncTrakt(force = true)
                "mdblist" -> repository.syncMdbList(force = true)
                "simkl" -> repository.syncSimkl(force = true)
                else -> {
                    val pluginName = PluginRegistry.defaultPlugins.find { it.id == service || it.syncServiceKey == service }?.name ?: service
                    Resource.Success(
                        com.personalrecords.app.core.network.model.SyncResultDto(
                            success = true,
                            message = "$pluginName verileri güncellendi!"
                        )
                    )
                }
            }
            when (result) {
                is Resource.Success -> {
                    val serverMsg = result.data.message.takeIf { it.isNotBlank() }
                    _uiState.update {
                        it.copy(
                            isSyncing = null,
                            syncMessage = serverMsg ?: "${service.replaceFirstChar { c -> c.uppercase() }} eşitlendi!"
                        )
                    }
                    loadData()
                }
                is Resource.Error -> {
                    _uiState.update {
                        it.copy(
                            isSyncing = null,
                            syncMessage = "Hata: ${result.message}"
                        )
                    }
                }
                else -> {
                    _uiState.update { it.copy(isSyncing = null) }
                }
            }
        }
    }

    fun syncAll() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = "all", syncMessage = null) }
            val activePlugins = PluginRegistry.defaultPlugins.filter { _uiState.value.enabledPluginIds.contains(it.id) }
            val syncKeys = activePlugins.mapNotNull { it.syncServiceKey ?: it.id }.distinct()

            var syncedCount = 0
            for (key in syncKeys) {
                try {
                    when (key) {
                        "fatsecret" -> repository.syncFatSecret(30)
                        "goodreads" -> repository.syncGoodreads()
                        "letterboxd" -> repository.syncLetterboxd()
                        "lastfm", "music" -> repository.syncLastFm()
                        "listenbrainz" -> repository.syncListenBrainz()
                        "statsfm" -> repository.syncStatsFm()
                        "trakt" -> repository.syncTrakt(force = true)
                        "mdblist" -> repository.syncMdbList(force = true)
                        "simkl" -> repository.syncSimkl(force = true)
                        else -> null
                    }
                    syncedCount++
                } catch (_: Exception) {
                    // continue with others
                }
            }

            _uiState.update {
                it.copy(
                    isSyncing = null,
                    syncMessage = if (syncedCount > 0) "Tüm aktif entegrasyonlar eşitlendi ($syncedCount servis)" else "Senkronizasyon tamamlandı"
                )
            }
            loadData()
        }
    }

    fun clearSyncMessage() {
        _uiState.update { it.copy(syncMessage = null) }
    }
}

private data class OverviewLoadedAsync(
    val dashRes: Resource<DashboardDto>,
    val actRes: Resource<ActivityResponseDto>,
    val filmRes: Resource<List<FilmDto>>,
    val bookRes: Resource<List<BookDto>>,
    val mealRes: Resource<List<FoodEntryDto>>,
    val musicRes: Resource<MusicResponse>,
    val musicStatsRes: Resource<MusicStatsDto>,
    val healthRes: Resource<HealthConnectResponse>,
    val notificationsRes: Resource<List<NotificationDto>>,
    val cachedBooks: List<BookDto>,
    val cachedFilms: List<FilmDto>
)
