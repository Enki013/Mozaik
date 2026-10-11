package com.personalrecords.app.ui.screens.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalrecords.app.core.common.Resource
import com.personalrecords.app.core.network.model.*
import com.personalrecords.app.data.repository.PersonalRecordsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class OverviewUiState(
    val isLoading: Boolean = false,
    val dashboard: DashboardDto? = null,
    val recentActivities: List<ActivityItemDto> = emptyList(),
    val isSyncing: String? = null, // "fatsecret", "goodreads", "letterboxd"
    val syncMessage: String? = null,
    val error: String? = null
)

class OverviewViewModel(
    private val repository: PersonalRecordsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OverviewUiState())
    val uiState: StateFlow<OverviewUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val (dashRes, actRes) = withContext(Dispatchers.IO) {
                coroutineScope {
                    val dashDeferred = async { repository.getDashboard() }
                    val actDeferred = async { repository.getActivity(days = 14, limit = 6) }
                    dashDeferred.await() to actDeferred.await()
                }
            }

            val dash = if (dashRes is Resource.Success) dashRes.data else null
            val acts = if (actRes is Resource.Success) actRes.data.items else emptyList()

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                dashboard = dash,
                recentActivities = acts,
                error = if (dashRes is Resource.Error) dashRes.message else null
            )
        }
    }

    fun syncService(service: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSyncing = service, syncMessage = null)
            val result = when (service) {
                "fatsecret" -> repository.syncFatSecret(30)
                "goodreads" -> repository.syncGoodreads()
                "letterboxd" -> repository.syncLetterboxd()
                else -> Resource.Error("Bilinmeyen servis")
            }
            when (result) {
                is Resource.Success -> {
                    val serverMsg = result.data.message?.takeIf { it.isNotBlank() }
                    _uiState.value = _uiState.value.copy(
                        isSyncing = null,
                        syncMessage = serverMsg ?: "${service.replaceFirstChar { it.uppercase() }} eşitlendi!"
                    )
                    loadData()
                }
                is Resource.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isSyncing = null,
                        syncMessage = "Hata: ${result.message}"
                    )
                }
                else -> {
                    _uiState.value = _uiState.value.copy(isSyncing = null)
                }
            }
        }
    }

    fun clearSyncMessage() {
        _uiState.value = _uiState.value.copy(syncMessage = null)
    }
}
