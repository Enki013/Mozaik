package com.personalrecords.app.ui.screens.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalrecords.app.core.common.Resource
import com.personalrecords.app.core.network.model.ActivityDayGroupDto
import com.personalrecords.app.core.network.model.ActivityItemDto
import com.personalrecords.app.data.repository.PersonalRecordsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

data class TimelineUiState(
    val isLoading: Boolean = false,
    val selectedDays: Int = 14,
    val selectedDate: String? = null,
    val fromDate: String? = null,
    val toDate: String? = null,
    val selectedSource: String = "all", // "all", "fatsecret", "goodreads", "letterboxd", "notification"
    val searchQuery: String = "",
    val dayGroups: List<ActivityDayGroupDto> = emptyList(),
    val error: String? = null
)

class TimelineViewModel(
    private val repository: PersonalRecordsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TimelineUiState())
    val uiState: StateFlow<TimelineUiState> = _uiState.asStateFlow()

    private var hasLoadedOnce = false

    fun ensureLoaded() {
        if (!hasLoadedOnce) {
            hasLoadedOnce = true
            loadData()
        }
    }

    fun loadData() {
        hasLoadedOnce = true
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
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        dayGroups = response.data.groups
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
        _uiState.value = _uiState.value.copy(selectedSource = source)
    }

    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }
}
