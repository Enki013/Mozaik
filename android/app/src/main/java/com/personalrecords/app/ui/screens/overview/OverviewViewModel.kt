package com.personalrecords.app.ui.screens.overview

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalrecords.app.PersonalRecordsApp
import com.personalrecords.app.core.common.Resource
import com.personalrecords.app.core.datastore.AppPreferences
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
            val (dashRes, actRes) = coroutineScope {
                val dDeferred = async { repository.getDashboard() }
                val aDeferred = async { repository.getActivity(days = 14, limit = 6) }
                dDeferred.await() to aDeferred.await()
            }

            val dash = if (dashRes is Resource.Success) dashRes.data else _uiState.value.dashboard
            val acts = if (actRes is Resource.Success) actRes.data.items else _uiState.value.recentActivities

            _uiState.update {
                it.copy(
                    isLoading = false,
                    dashboard = dash,
                    recentActivities = acts,
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
                else -> Resource.Error("Bilinmeyen servis")
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

    fun clearSyncMessage() {
        _uiState.update { it.copy(syncMessage = null) }
    }
}
