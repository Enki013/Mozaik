package com.personalrecords.app.ui.screens.health

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalrecords.app.core.common.Resource
import com.personalrecords.app.core.network.model.HealthConnectSummaryDto
import com.personalrecords.app.core.network.model.HealthRecordDto
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

@Immutable
data class HealthConnectUiState(
    val isLoading: Boolean = true,
    val isFiltering: Boolean = false,
    val records: List<HealthRecordDto> = emptyList(),
    val filteredRecords: List<HealthRecordDto> = emptyList(),
    val summary: HealthConnectSummaryDto = HealthConnectSummaryDto(),
    val selectedCategory: String = "all", // "all", "activity", "sleep", "vitals", "exercise", "body"
    val searchQuery: String = "",
    val selectedDateFilter: String? = null,
    val fromDateFilter: String? = null,
    val toDateFilter: String? = null,
    val error: String? = null
)

private fun filterHealthRecords(
    records: List<HealthRecordDto>,
    selectedCategory: String,
    searchQuery: String,
    selectedDateFilter: String?,
    fromDateFilter: String?,
    toDateFilter: String?
): List<HealthRecordDto> {
    if (records.isEmpty()) return emptyList()
    return records.filter { item ->
        val matchesCategory = selectedCategory == "all" || item.category.equals(selectedCategory, ignoreCase = true)
        val matchesSearch = searchQuery.isBlank() ||
            item.title.contains(searchQuery, ignoreCase = true) ||
            item.subtitle.contains(searchQuery, ignoreCase = true) ||
            item.record_type.contains(searchQuery, ignoreCase = true) ||
            item.data_origin.contains(searchQuery, ignoreCase = true)
        val recDate = item.formattedDate
        val matchesDate = when {
            selectedDateFilter != null -> recDate.startsWith(selectedDateFilter)
            fromDateFilter != null && toDateFilter != null ->
                recDate.isNotBlank() && recDate >= fromDateFilter && recDate <= toDateFilter
            fromDateFilter != null -> recDate.isNotBlank() && recDate >= fromDateFilter
            toDateFilter != null -> recDate.isNotBlank() && recDate <= toDateFilter
            else -> true
        }
        matchesCategory && matchesSearch && matchesDate
    }
}

private fun computeFallbackSummary(
    records: List<HealthRecordDto>,
    remoteSummary: HealthConnectSummaryDto?
): HealthConnectSummaryDto {
    if (remoteSummary != null && remoteSummary.total_records > 0) return remoteSummary
    if (records.isEmpty()) return HealthConnectSummaryDto()

    val latestStepsDate = records.firstOrNull { it.record_type == "steps" }?.formattedDate ?: ""
    val todaySteps = if (latestStepsDate.isNotBlank()) {
        records.asSequence()
            .filter { it.record_type == "steps" && it.formattedDate == latestStepsDate }
            .sumOf { it.value_primary ?: 0.0 }
    } else 0.0

    val lastSleep = records.firstOrNull { it.record_type == "sleep" }
    val latestHr = records.firstOrNull { it.record_type == "heart_rate" || it.record_type == "resting_heart_rate" }
    val latestWeight = records.firstOrNull { it.record_type == "weight" }

    val latestActiveDate = records.firstOrNull { it.record_type == "active_calories" }?.formattedDate ?: latestStepsDate
    val todayActiveCal = if (latestActiveDate.isNotBlank()) {
        records.asSequence()
            .filter { it.record_type == "active_calories" && it.formattedDate == latestActiveDate }
            .sumOf { it.value_primary ?: 0.0 }
    } else 0.0

    return HealthConnectSummaryDto(
        total_records = records.size,
        today_steps = todaySteps,
        steps_date = latestStepsDate,
        last_sleep_seconds = lastSleep?.value_primary ?: 0.0,
        sleep_date = lastSleep?.formattedDate ?: "",
        latest_heart_rate_bpm = latestHr?.value_primary ?: 0.0,
        today_active_calories = todayActiveCal,
        latest_weight_kg = latestWeight?.value_primary ?: 0.0
    )
}

class HealthConnectViewModel(
    private val repository: PersonalRecordsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HealthConnectUiState())
    val uiState: StateFlow<HealthConnectUiState> = _uiState.asStateFlow()
    private var filterJob: Job? = null

    init {
        loadInitialData()
    }

    private suspend fun applyHealthRecords(
        records: List<HealthRecordDto>,
        summary: HealthConnectSummaryDto?
    ) {
        val snap = _uiState.value
        val (filtered, resolvedSummary) = withContext(Dispatchers.Default) {
            val list = filterHealthRecords(
                records = records,
                selectedCategory = snap.selectedCategory,
                searchQuery = snap.searchQuery,
                selectedDateFilter = snap.selectedDateFilter,
                fromDateFilter = snap.fromDateFilter,
                toDateFilter = snap.toDateFilter
            )
            val sum = computeFallbackSummary(records, summary)
            list to sum
        }
        _uiState.update {
            it.copy(
                isLoading = false,
                isFiltering = false,
                records = records,
                filteredRecords = filtered,
                summary = resolvedSummary,
                error = null
            )
        }
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            val cached = repository.getCachedHealthRecords()
            if (cached.isNotEmpty()) {
                applyHealthRecords(cached, repository.cachedHealthSummary)
            }
            delay(340L)
            fetchRemoteRecords(showLoadingIfEmpty = cached.isEmpty())
        }
    }

    fun loadData() {
        viewModelScope.launch {
            fetchRemoteRecords(showLoadingIfEmpty = true)
        }
    }

    private suspend fun fetchRemoteRecords(showLoadingIfEmpty: Boolean) {
        if (showLoadingIfEmpty) {
            _uiState.update { it.copy(isLoading = true, error = null) }
        }
        when (val result = repository.getHealthConnectRecords(limit = 350)) {
            is Resource.Success -> {
                val resp = result.data
                if (resp.items != _uiState.value.records || resp.summary != _uiState.value.summary || _uiState.value.isLoading) {
                    applyHealthRecords(resp.items, resp.summary)
                }
            }
            is Resource.Error -> {
                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        isFiltering = false,
                        error = if (current.records.isEmpty()) result.message else null
                    )
                }
            }
            else -> Unit
        }
    }

    fun setCategoryFilter(category: String) {
        if (_uiState.value.selectedCategory == category) return
        _uiState.update { it.copy(selectedCategory = category, isFiltering = true) }
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
                selectedCategory = "all",
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
                filterHealthRecords(
                    records = snap.records,
                    selectedCategory = snap.selectedCategory,
                    searchQuery = snap.searchQuery,
                    selectedDateFilter = snap.selectedDateFilter,
                    fromDateFilter = snap.fromDateFilter,
                    toDateFilter = snap.toDateFilter
                )
            }
            _uiState.update {
                it.copy(
                    filteredRecords = filtered,
                    isFiltering = false
                )
            }
        }
    }
}
