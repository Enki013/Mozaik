package com.personalrecords.app.ui.screens.timeline

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalrecords.app.core.common.Resource
import com.personalrecords.app.core.network.model.ActivityDayGroupDto
import com.personalrecords.app.core.network.model.ActivityItemDto
import com.personalrecords.app.data.repository.PersonalRecordsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

@Immutable
sealed interface TimelineFlatEntry {
    val key: String
    val contentType: String

    @Immutable
    data class DayHeader(
        val date: String,
        val label: String,
        val totalCal: String?,
        val hasMultipleSections: Boolean,
        val singleSource: String?,
        val totalItemCount: Int,
        val isLastInDay: Boolean
    ) : TimelineFlatEntry {
        override val key: String = "day_hdr_$date"
        override val contentType: String = "day_header"
    }

    @Immutable
    data class SectionHeader(
        val date: String,
        val source: String,
        val itemCount: Int,
        val totalCal: String?
    ) : TimelineFlatEntry {
        override val key: String = "sec_hdr_${date}_$source"
        override val contentType: String = "section_header"
    }

    @Immutable
    data class ActivityRow(
        val date: String,
        val item: ActivityItemDto,
        val indexInDay: Int,
        val isLastInDay: Boolean,
        val rating: Double?,
        val caloriesText: String?,
        val timeText: String?
    ) : TimelineFlatEntry {
        override val key: String = "act_${date}_${item.source}_${item.id}_$indexInDay"
        override val contentType: String = "activity_item"
    }
}

private fun computeTimelineFlatEntries(
    groups: List<ActivityDayGroupDto>,
    selectedSource: String,
    searchQuery: String
): List<TimelineFlatEntry> {
    if (groups.isEmpty()) return emptyList()
    val filteredGroups = if (selectedSource == "all" && searchQuery.isBlank()) {
        groups
    } else {
        groups.mapNotNull { group ->
            val filteredItems = group.items.filter { item ->
                val matchesSource = selectedSource == "all" || item.source == selectedSource
                val matchesSearch = searchQuery.isBlank() ||
                    item.title.contains(searchQuery, ignoreCase = true) ||
                    item.subtitle.contains(searchQuery, ignoreCase = true)
                matchesSource && matchesSearch
            }
            if (filteredItems.isNotEmpty()) group.copy(items = filteredItems) else null
        }
    }
    if (filteredGroups.isEmpty()) return emptyList()

    val result = ArrayList<TimelineFlatEntry>(filteredGroups.size * 6)
    for (group in filteredGroups) {
        if (group.items.isEmpty()) continue
        val sections = LinkedHashMap<String, MutableList<ActivityItemDto>>()
        for (item in group.items) {
            sections.getOrPut(item.source) { mutableListOf() }.add(item)
        }
        val totalCal = group.summary["total_calories"]?.toString()?.replace("\"", "")
        val hasMultipleSections = sections.size > 1
        val singleSource = sections.keys.firstOrNull()
        val totalCount = group.items.size

        result.add(
            TimelineFlatEntry.DayHeader(
                date = group.date,
                label = group.label,
                totalCal = totalCal,
                hasMultipleSections = hasMultipleSections,
                singleSource = singleSource,
                totalItemCount = totalCount,
                isLastInDay = false
            )
        )

        var indexInDay = 0
        for ((source, sectionItems) in sections) {
            if (hasMultipleSections) {
                result.add(
                    TimelineFlatEntry.SectionHeader(
                        date = group.date,
                        source = source,
                        itemCount = sectionItems.size,
                        totalCal = totalCal
                    )
                )
            }
            for (item in sectionItems) {
                val isLastInDay = indexInDay == totalCount - 1
                val rating = (item.details["rating"]?.toString()?.toDoubleOrNull()
                    ?: item.details["my_rating"]?.toString()?.toDoubleOrNull())
                    ?.takeIf { it > 0.0 }
                val calRaw = if (item.source == "fatsecret") {
                    item.details["calories"]?.toString()?.replace("\"", "")
                } else null
                val caloriesText = if (!calRaw.isNullOrBlank() && calRaw != "0" && calRaw != "null") {
                    "$calRaw kcal"
                } else null
                val timeText = if (item.source == "notification") {
                    item.time ?: item.details["post_time"]?.toString()?.replace("\"", "")?.let {
                        if (it.length >= 16) it.substring(11, 16) else null
                    }
                } else null

                result.add(
                    TimelineFlatEntry.ActivityRow(
                        date = group.date,
                        item = item,
                        indexInDay = indexInDay,
                        isLastInDay = isLastInDay,
                        rating = rating,
                        caloriesText = caloriesText,
                        timeText = timeText
                    )
                )
                indexInDay++
            }
        }
    }
    return result
}

@Immutable
data class TimelineUiState(
    val isLoading: Boolean = false,
    val selectedDays: Int = 14,
    val selectedDate: String? = null,
    val fromDate: String? = null,
    val toDate: String? = null,
    val selectedSource: String = "all", // "all", "fatsecret", "goodreads", "letterboxd", "notification"
    val searchQuery: String = "",
    val dayGroups: List<ActivityDayGroupDto> = emptyList(),
    val flatEntries: List<TimelineFlatEntry> = emptyList(),
    val error: String? = null
)

class TimelineViewModel(
    private val repository: PersonalRecordsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TimelineUiState())
    val uiState: StateFlow<TimelineUiState> = _uiState.asStateFlow()
    private var filterJob: Job? = null

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val currentState = _uiState.value
            val response = repository.getActivity(
                days = if (currentState.selectedDate == null && currentState.fromDate == null) currentState.selectedDays else null,
                from = currentState.fromDate,
                to = currentState.toDate,
                date = currentState.selectedDate,
                limit = 250
            )

            when (response) {
                is Resource.Success -> {
                    val groups = response.data.groups
                    val latest = _uiState.value
                    val flattened = withContext(Dispatchers.Default) {
                        computeTimelineFlatEntries(groups, latest.selectedSource, latest.searchQuery)
                    }
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        dayGroups = groups,
                        flatEntries = flattened
                    )
                }
                is Resource.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = response.message
                    )
                }
                else -> Unit
            }
        }
    }

    fun setDays(days: Int) {
        _uiState.value = _uiState.value.copy(
            selectedDays = days,
            selectedDate = null,
            fromDate = null,
            toDate = null
        )
        loadData()
    }

    fun setSpecificDate(date: String) {
        _uiState.value = _uiState.value.copy(
            selectedDate = date,
            fromDate = null,
            toDate = null
        )
        loadData()
    }

    fun setToday() {
        setSpecificDate(LocalDate.now().toString())
    }

    fun setDateRange(from: String, to: String) {
        _uiState.value = _uiState.value.copy(
            fromDate = from,
            toDate = to,
            selectedDate = null
        )
        loadData()
    }

    fun resetFilters() {
        _uiState.value = _uiState.value.copy(
            selectedDays = 14,
            selectedDate = null,
            fromDate = null,
            toDate = null,
            selectedSource = "all",
            searchQuery = ""
        )
        loadData()
    }

    fun setSource(source: String) {
        if (_uiState.value.selectedSource == source) return
        _uiState.value = _uiState.value.copy(selectedSource = source)
        recomputeFilter(debounceMs = 0L)
    }

    fun setSearchQuery(query: String) {
        if (_uiState.value.searchQuery == query) return
        _uiState.value = _uiState.value.copy(searchQuery = query)
        recomputeFilter(debounceMs = if (query.isEmpty()) 0L else 100L)
    }

    private fun recomputeFilter(debounceMs: Long) {
        filterJob?.cancel()
        filterJob = viewModelScope.launch {
            if (debounceMs > 0L) delay(debounceMs)
            val snap = _uiState.value
            val flattened = withContext(Dispatchers.Default) {
                computeTimelineFlatEntries(snap.dayGroups, snap.selectedSource, snap.searchQuery)
            }
            _uiState.value = _uiState.value.copy(flatEntries = flattened)
        }
    }
}
