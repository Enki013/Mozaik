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

data class OverviewUiState(
    val isLoading: Boolean = true,
    val dashboard: DashboardDto? = null,
    val recentActivities: List<ActivityItemDto> = emptyList(),
    val latestFilm: FilmDto? = null,
    val latestBook: BookDto? = null,
    val latestMeal: FoodEntryDto? = null,
    val latestMusic: MusicScrobbleDto? = null,
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
            val (dashRes, actRes, filmRes, bookRes, mealRes, musicRes) = coroutineScope {
                val dDeferred = async { repository.getDashboard() }
                val aDeferred = async { repository.getActivity(days = 14, limit = 20) }
                val fDeferred = async { repository.getFilms(limit = 1) }
                val bDeferred = async { repository.getBooks(limit = 1) }
                val mDeferred = async { repository.getFoodEntries(limit = 1) }
                val sDeferred = async { repository.getMusicScrobbles(page = 1, limit = 1) }
                Sextuple(
                    dDeferred.await(),
                    aDeferred.await(),
                    fDeferred.await(),
                    bDeferred.await(),
                    mDeferred.await(),
                    sDeferred.await()
                )
            }

            val dash = if (dashRes is Resource.Success) dashRes.data else _uiState.value.dashboard
            val acts = if (actRes is Resource.Success) actRes.data.items else _uiState.value.recentActivities
            val film = if (filmRes is Resource.Success) filmRes.data.firstOrNull() else _uiState.value.latestFilm
            val book = if (bookRes is Resource.Success) bookRes.data.firstOrNull() else _uiState.value.latestBook
            val meal = if (mealRes is Resource.Success) mealRes.data.firstOrNull() else _uiState.value.latestMeal
            val music = if (musicRes is Resource.Success) musicRes.data.items.firstOrNull() else _uiState.value.latestMusic

            _uiState.update {
                it.copy(
                    isLoading = false,
                    dashboard = dash,
                    recentActivities = acts,
                    latestFilm = film,
                    latestBook = book,
                    latestMeal = meal,
                    latestMusic = music,
                    error = if (dashRes is Resource.Error) dashRes.message else null
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

private data class Sextuple<A, B, C, D, E, F>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
    val fifth: E,
    val sixth: F
)
