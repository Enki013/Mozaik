package com.personalrecords.app.ui.screens.movies

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalrecords.app.core.common.Resource
import com.personalrecords.app.core.network.model.FilmDto
import com.personalrecords.app.data.repository.PersonalRecordsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@Immutable
data class MoviesUiState(
    val isLoading: Boolean = true,
    val isFiltering: Boolean = false,
    val films: List<FilmDto> = emptyList(),
    val filteredFilms: List<FilmDto> = emptyList(),
    val ratedFilmsCount: Int = 0,
    val rewatchFilmsCount: Int = 0,
    val avgRatingText: String = "0.0",
    val searchQuery: String = "",
    val selectedDateFilter: String? = null,
    val fromDateFilter: String? = null,
    val toDateFilter: String? = null,
    val onlyRewatches: Boolean = false,
    val sortBy: String = "rating_desc", // "rating_desc", "rating_asc", "year", "date", "title"
    val error: String? = null
)

private fun filterAndSortFilms(
    films: List<FilmDto>,
    query: String,
    singleDate: String?,
    fromDate: String?,
    toDate: String?,
    rewatchesOnly: Boolean,
    sort: String
): List<FilmDto> {
    if (films.isEmpty()) return emptyList()
    val filtered = films.filter { film ->
        val matchesSearch = query.isBlank() ||
            film.displayTitle.contains(query, ignoreCase = true)
        val matchesRewatch = !rewatchesOnly || film.rewatch
        val filmDate = film.safeWatchedDate.ifBlank { film.diary_date ?: "" }
        val matchesDate = when {
            singleDate != null -> filmDate.startsWith(singleDate)
            fromDate != null && toDate != null ->
                filmDate.isNotBlank() && filmDate >= fromDate && filmDate <= toDate
            fromDate != null -> filmDate.isNotBlank() && filmDate >= fromDate
            toDate != null -> filmDate.isNotBlank() && filmDate <= toDate
            else -> true
        }
        matchesSearch && matchesRewatch && matchesDate
    }

    return when (sort) {
        "rating_desc" -> filtered.sortedByDescending { it.safeRating }
        "rating_asc" -> filtered.sortedBy { it.safeRating }
        "year" -> filtered.sortedByDescending { it.safeYear }
        "date" -> filtered.sortedByDescending { it.safeWatchedDate }
        "title" -> filtered.sortedBy { it.displayTitle.lowercase(Locale.getDefault()) }
        else -> filtered
    }
}

class MoviesViewModel(
    private val repository: PersonalRecordsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MoviesUiState())
    val uiState: StateFlow<MoviesUiState> = _uiState.asStateFlow()
    private var filterJob: Job? = null

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            when (val result = repository.getFilms()) {
                is Resource.Success -> {
                    val films = result.data
                    val snap = _uiState.value
                    val (filtered, ratedCount, rewatchCount, avgText) = withContext(Dispatchers.Default) {
                        val list = filterAndSortFilms(
                            films = films,
                            query = snap.searchQuery,
                            singleDate = snap.selectedDateFilter,
                            fromDate = snap.fromDateFilter,
                            toDate = snap.toDateFilter,
                            rewatchesOnly = snap.onlyRewatches,
                            sort = snap.sortBy
                        )
                        val rated = films.filter { it.safeRating > 0 }
                        val rewatches = films.count { it.rewatch }
                        val avg = if (rated.isNotEmpty()) {
                            String.format(Locale.US, "%.1f", rated.map { it.safeRating }.average())
                        } else "0.0"
                        MoviesStatsResult(list, rated.size, rewatches, avg)
                    }
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isFiltering = false,
                        films = films,
                        filteredFilms = filtered,
                        ratedFilmsCount = ratedCount,
                        rewatchFilmsCount = rewatchCount,
                        avgRatingText = avgText
                    )
                }
                is Resource.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isFiltering = false,
                        error = result.message
                    )
                }
                else -> Unit
            }
        }
    }

    fun setSearchQuery(query: String) {
        if (_uiState.value.searchQuery == query) return
        _uiState.value = _uiState.value.copy(searchQuery = query, isFiltering = true)
        recomputeFilter(debounceMs = if (query.isEmpty()) 0L else 100L)
    }

    fun setDateFilter(date: String?) {
        _uiState.value = _uiState.value.copy(
            selectedDateFilter = date,
            fromDateFilter = null,
            toDateFilter = null,
            isFiltering = true
        )
        recomputeFilter(debounceMs = 0L)
    }

    fun setDateRangeFilter(from: String?, to: String?) {
        _uiState.value = _uiState.value.copy(
            selectedDateFilter = null,
            fromDateFilter = from,
            toDateFilter = to,
            isFiltering = true
        )
        recomputeFilter(debounceMs = 0L)
    }

    fun clearDateFilter() {
        _uiState.value = _uiState.value.copy(
            selectedDateFilter = null,
            fromDateFilter = null,
            toDateFilter = null,
            isFiltering = true
        )
        recomputeFilter(debounceMs = 0L)
    }

    fun toggleRewatches() {
        _uiState.value = _uiState.value.copy(
            onlyRewatches = !_uiState.value.onlyRewatches,
            isFiltering = true
        )
        recomputeFilter(debounceMs = 0L)
    }

    fun setSortBy(sort: String) {
        if (_uiState.value.sortBy == sort) return
        _uiState.value = _uiState.value.copy(sortBy = sort, isFiltering = true)
        recomputeFilter(debounceMs = 0L)
    }

    private fun recomputeFilter(debounceMs: Long) {
        filterJob?.cancel()
        filterJob = viewModelScope.launch {
            if (debounceMs > 0L) delay(debounceMs)
            val snap = _uiState.value
            val filtered = withContext(Dispatchers.Default) {
                filterAndSortFilms(
                    films = snap.films,
                    query = snap.searchQuery,
                    singleDate = snap.selectedDateFilter,
                    fromDate = snap.fromDateFilter,
                    toDate = snap.toDateFilter,
                    rewatchesOnly = snap.onlyRewatches,
                    sort = snap.sortBy
                )
            }
            _uiState.value = _uiState.value.copy(
                filteredFilms = filtered,
                isFiltering = false
            )
        }
    }
}

private data class MoviesStatsResult(
    val filtered: List<FilmDto>,
    val ratedCount: Int,
    val rewatchCount: Int,
    val avgText: String
)
