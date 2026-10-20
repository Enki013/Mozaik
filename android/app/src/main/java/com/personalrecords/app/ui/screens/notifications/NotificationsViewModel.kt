package com.personalrecords.app.ui.screens.notifications

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalrecords.app.core.common.Resource
import com.personalrecords.app.core.network.model.NotificationDto
import com.personalrecords.app.data.repository.PersonalRecordsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

@Immutable
data class NotificationsUiState(
    val isLoading: Boolean = true,
    val isFiltering: Boolean = false,
    val notifications: List<NotificationDto> = emptyList(),
    val filteredNotifications: List<NotificationDto> = emptyList(),
    val appList: List<String> = emptyList(),
    val todayCount: Int = 0,
    val selectedApp: String? = null,
    val searchQuery: String = "",
    val selectedDateFilter: String? = null,
    val fromDateFilter: String? = null,
    val toDateFilter: String? = null,
    val error: String? = null
)

private fun filterNotifications(
    notifications: List<NotificationDto>,
    selectedApp: String?,
    searchQuery: String,
    selectedDateFilter: String?,
    fromDateFilter: String?,
    toDateFilter: String?
): List<NotificationDto> {
    if (notifications.isEmpty()) return emptyList()
    return notifications.filter { item ->
        val matchesApp = selectedApp == null || item.displayAppName == selectedApp
        val matchesSearch = searchQuery.isBlank() ||
            item.displayTitle.contains(searchQuery, ignoreCase = true) ||
            item.displayBody.contains(searchQuery, ignoreCase = true) ||
            item.displayAppName.contains(searchQuery, ignoreCase = true)
        val notifDate = item.formattedDate.ifBlank { item.created_at.take(10) }
        val matchesDate = when {
            selectedDateFilter != null -> notifDate.startsWith(selectedDateFilter)
            fromDateFilter != null && toDateFilter != null ->
                notifDate.isNotBlank() && notifDate >= fromDateFilter && notifDate <= toDateFilter
            fromDateFilter != null -> notifDate.isNotBlank() && notifDate >= fromDateFilter
            toDateFilter != null -> notifDate.isNotBlank() && notifDate <= toDateFilter
            else -> true
        }
        matchesApp && matchesSearch && matchesDate
    }
}

class NotificationsViewModel(
    private val repository: PersonalRecordsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationsUiState())
    val uiState: StateFlow<NotificationsUiState> = _uiState.asStateFlow()
    private var filterJob: Job? = null

    init {
        loadInitialData()
    }

    private suspend fun applyNotificationsList(notifs: List<NotificationDto>) {
        val snap = _uiState.value
        val (filtered, apps, todayCnt) = withContext(Dispatchers.Default) {
            val list = filterNotifications(
                notifications = notifs,
                selectedApp = snap.selectedApp,
                searchQuery = snap.searchQuery,
                selectedDateFilter = snap.selectedDateFilter,
                fromDateFilter = snap.fromDateFilter,
                toDateFilter = snap.toDateFilter
            )
            val distinctApps = notifs.map { it.displayAppName }.distinct().sorted()
            val todayISO = LocalDate.now().toString()
            val today = notifs.count { it.formattedDate == todayISO }
            Triple(list, distinctApps, today)
        }
        _uiState.update {
            it.copy(
                isLoading = false,
                isFiltering = false,
                notifications = notifs,
                filteredNotifications = filtered,
                appList = apps,
                todayCount = todayCnt,
                error = null
            )
        }
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            val cached = repository.cachedNotifications ?: emptyList()
            if (cached.isNotEmpty()) {
                applyNotificationsList(cached)
            }
            delay(340L)
            fetchRemoteNotifications(showLoadingIfEmpty = cached.isEmpty())
        }
    }

    fun loadData() {
        viewModelScope.launch {
            fetchRemoteNotifications(showLoadingIfEmpty = true)
        }
    }

    private suspend fun fetchRemoteNotifications(showLoadingIfEmpty: Boolean) {
        if (showLoadingIfEmpty) {
            _uiState.update { it.copy(isLoading = true, error = null) }
        }
        when (val result = repository.getNotifications(limit = 200)) {
            is Resource.Success -> {
                val notifs = result.data
                if (notifs != _uiState.value.notifications || _uiState.value.isLoading) {
                    applyNotificationsList(notifs)
                }
            }
            is Resource.Error -> {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isFiltering = false,
                        error = if (it.notifications.isEmpty()) result.message else null
                    )
                }
            }
            else -> Unit
        }
    }

    fun setAppFilter(app: String?) {
        if (_uiState.value.selectedApp == app) return
        _uiState.update { it.copy(selectedApp = app, isFiltering = true) }
        recomputeFilter(debounceMs = 0L)
    }

    fun setSearchQuery(query: String) {
        if (_uiState.value.searchQuery == query) return
        _uiState.update { it.copy(searchQuery = query, isFiltering = true) }
        recomputeFilter(debounceMs = if (query.isEmpty()) 0L else 100L)
    }

    fun setDateFilter(date: String?) {
        _uiState.update {
            it.copy(
                selectedDateFilter = date,
                fromDateFilter = null,
                toDateFilter = null,
                isFiltering = true
            )
        }
        recomputeFilter(debounceMs = 0L)
    }

    fun setDateRangeFilter(from: String?, to: String?) {
        _uiState.update {
            it.copy(
                selectedDateFilter = null,
                fromDateFilter = from,
                toDateFilter = to,
                isFiltering = true
            )
        }
        recomputeFilter(debounceMs = 0L)
    }

    fun clearDateFilter() {
        _uiState.update {
            it.copy(
                selectedDateFilter = null,
                fromDateFilter = null,
                toDateFilter = null,
                isFiltering = true
            )
        }
        recomputeFilter(debounceMs = 0L)
    }

    fun resetFilters() {
        _uiState.update {
            it.copy(
                selectedApp = null,
                searchQuery = "",
                selectedDateFilter = null,
                fromDateFilter = null,
                toDateFilter = null,
                isFiltering = true
            )
        }
        recomputeFilter(debounceMs = 0L)
    }

    private fun recomputeFilter(debounceMs: Long) {
        filterJob?.cancel()
        filterJob = viewModelScope.launch {
            if (debounceMs > 0L) delay(debounceMs)
            val snap = _uiState.value
            val filtered = withContext(Dispatchers.Default) {
                filterNotifications(
                    notifications = snap.notifications,
                    selectedApp = snap.selectedApp,
                    searchQuery = snap.searchQuery,
                    selectedDateFilter = snap.selectedDateFilter,
                    fromDateFilter = snap.fromDateFilter,
                    toDateFilter = snap.toDateFilter
                )
            }
            _uiState.update {
                it.copy(
                    filteredNotifications = filtered,
                    isFiltering = false
                )
            }
        }
    }
}
