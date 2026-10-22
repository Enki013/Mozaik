package com.personalrecords.app.ui.screens.music

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalrecords.app.core.common.Resource
import com.personalrecords.app.core.network.model.MusicScrobbleDto
import com.personalrecords.app.core.network.model.MusicStatsDto
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
import java.util.Locale

@Immutable
data class MusicUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isFiltering: Boolean = false,
    val isSyncingLastFm: Boolean = false,
    val scrobbles: List<MusicScrobbleDto> = emptyList(),
    val filteredScrobbles: List<MusicScrobbleDto> = emptyList(),
    val stats: MusicStatsDto = MusicStatsDto(),
    val selectedFilter: String = "all", // "all", "lastfm", "web_scrobbler"
    val selectedArtist: String? = null,
    val selectedDateFilter: String? = null,
    val fromDateFilter: String? = null,
    val toDateFilter: String? = null,
    val sortBy: String = "date_desc", // "date_desc", "date_asc", "track", "artist"
    val searchQuery: String = "",
    val webhookUrl: String = "",
    val error: String? = null,
    val syncMessage: String? = null
)

private fun filterAndSortMusicScrobbles(
    scrobbles: List<MusicScrobbleDto>,
    selectedFilter: String,
    selectedArtist: String?,
    selectedDateFilter: String?,
    fromDateFilter: String?,
    toDateFilter: String?,
    sortBy: String,
    searchQuery: String
): List<MusicScrobbleDto> {
    if (scrobbles.isEmpty()) return emptyList()
    val filtered = scrobbles.filter { item ->
        val matchesSource = when (selectedFilter) {
            "lastfm" -> item.source.equals("lastfm", ignoreCase = true)
            "web_scrobbler" -> item.source.contains("scrobbl", ignoreCase = true)
            else -> true
        }
        val matchesArtist = selectedArtist.isNullOrBlank() || item.artist.equals(selectedArtist, ignoreCase = true)

        val itemDate = item.formattedDate
        val matchesDate = when {
            selectedDateFilter != null -> itemDate == selectedDateFilter
            fromDateFilter != null && toDateFilter != null ->
                itemDate.isNotBlank() && itemDate >= fromDateFilter && itemDate <= toDateFilter
            fromDateFilter != null -> itemDate.isNotBlank() && itemDate >= fromDateFilter
            toDateFilter != null -> itemDate.isNotBlank() && itemDate <= toDateFilter
            else -> true
        }

        val matchesSearch = searchQuery.isBlank() ||
            item.track.contains(searchQuery, ignoreCase = true) ||
            item.artist.contains(searchQuery, ignoreCase = true) ||
            item.album.contains(searchQuery, ignoreCase = true)

        matchesSource && matchesArtist && matchesDate && matchesSearch
    }

    return when (sortBy) {
        "date_asc" -> filtered.sortedBy { it.uts }
        "track" -> filtered.sortedBy { it.track.lowercase(Locale.getDefault()) }
        "artist" -> filtered.sortedBy { it.artist.lowercase(Locale.getDefault()) }
        else -> filtered.sortedByDescending { it.uts }
    }
}

class MusicViewModel(
    private val repository: PersonalRecordsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MusicUiState())
    val uiState: StateFlow<MusicUiState> = _uiState.asStateFlow()
    private var filterJob: Job? = null

    init {
        loadInitialData()
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            val cachedScrobbles = repository.cachedMusicScrobbles
            val cachedStats = repository.cachedMusicStats
            if (!cachedScrobbles.isNullOrEmpty()) {
                val snap = _uiState.value
                val filtered = withContext(Dispatchers.Default) {
                    filterAndSortMusicScrobbles(
                        scrobbles = cachedScrobbles,
                        selectedFilter = snap.selectedFilter,
                        selectedArtist = snap.selectedArtist,
                        selectedDateFilter = snap.selectedDateFilter,
                        fromDateFilter = snap.fromDateFilter,
                        toDateFilter = snap.toDateFilter,
                        sortBy = snap.sortBy,
                        searchQuery = snap.searchQuery
                    )
                }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        scrobbles = cachedScrobbles,
                        filteredScrobbles = filtered,
                        stats = cachedStats ?: MusicStatsDto(totalScrobbles = cachedScrobbles.size)
                    )
                }
            }
            delay(200L)
            fetchData(isRefresh = false)
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            fetchData(isRefresh = true)
        }
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = it.scrobbles.isEmpty()) }
            fetchData(isRefresh = false)
        }
    }

    private suspend fun fetchData(isRefresh: Boolean) {
        val scrobblesRes = repository.getMusicScrobbles(limit = 100)
        val statsRes = repository.getMusicStats()
        val settingsRes = repository.getMusicSettings()
        val webhookUrl = (settingsRes as? Resource.Success)?.data?.webhookUrl.orEmpty()

        when (scrobblesRes) {
            is Resource.Success -> {
                val items = scrobblesRes.data?.items ?: emptyList()
                val stats = (statsRes as? Resource.Success)?.data ?: MusicStatsDto(totalScrobbles = items.size)
                val snap = _uiState.value
                val filtered = withContext(Dispatchers.Default) {
                    filterAndSortMusicScrobbles(
                        scrobbles = items,
                        selectedFilter = snap.selectedFilter,
                        selectedArtist = snap.selectedArtist,
                        selectedDateFilter = snap.selectedDateFilter,
                        fromDateFilter = snap.fromDateFilter,
                        toDateFilter = snap.toDateFilter,
                        sortBy = snap.sortBy,
                        searchQuery = snap.searchQuery
                    )
                }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        scrobbles = items,
                        filteredScrobbles = filtered,
                        stats = stats,
                        webhookUrl = if (webhookUrl.isNotBlank()) webhookUrl else it.webhookUrl,
                        error = null
                    )
                }
            }
            is Resource.Error -> {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = if (it.scrobbles.isEmpty()) scrobblesRes.message else null
                    )
                }
            }
            else -> {}
        }
    }

    fun setFilter(filter: String) {
        if (_uiState.value.selectedFilter == filter) return
        _uiState.update { it.copy(selectedFilter = filter, isFiltering = true) }
        recomputeFilter(debounceMs = 0L)
    }

    fun setArtistFilter(artist: String?) {
        if (_uiState.value.selectedArtist == artist) return
        _uiState.update { it.copy(selectedArtist = artist, isFiltering = true) }
        recomputeFilter(debounceMs = 0L)
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

    fun setSortBy(sort: String) {
        if (_uiState.value.sortBy == sort) return
        _uiState.update { it.copy(sortBy = sort, isFiltering = true) }
        recomputeFilter(debounceMs = 0L)
    }

    fun setSearchQuery(query: String) {
        if (_uiState.value.searchQuery == query) return
        _uiState.update { it.copy(searchQuery = query, isFiltering = true) }
        recomputeFilter(debounceMs = if (query.isEmpty()) 0L else 150L)
    }

    private fun recomputeFilter(debounceMs: Long) {
        filterJob?.cancel()
        filterJob = viewModelScope.launch {
            if (debounceMs > 0L) delay(debounceMs)
            val snap = _uiState.value
            val filtered = withContext(Dispatchers.Default) {
                filterAndSortMusicScrobbles(
                    scrobbles = snap.scrobbles,
                    selectedFilter = snap.selectedFilter,
                    selectedArtist = snap.selectedArtist,
                    selectedDateFilter = snap.selectedDateFilter,
                    fromDateFilter = snap.fromDateFilter,
                    toDateFilter = snap.toDateFilter,
                    sortBy = snap.sortBy,
                    searchQuery = snap.searchQuery
                )
            }
            _uiState.update { it.copy(filteredScrobbles = filtered, isFiltering = false) }
        }
    }

    fun syncLastFm(allPages: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncingLastFm = true, error = null) }
            when (val res = repository.syncLastFm(allPages = allPages)) {
                is Resource.Success -> {
                    val count = res.data?.inserted ?: 0
                    val msg = if (count > 0) "$count yeni scrobble eklendi" else "Tüm parçalar güncel"
                    _uiState.update { it.copy(isSyncingLastFm = false, syncMessage = msg) }
                    fetchData(isRefresh = true)
                }
                is Resource.Error -> {
                    _uiState.update { it.copy(isSyncingLastFm = false, error = res.message) }
                }
                else -> {
                    _uiState.update { it.copy(isSyncingLastFm = false) }
                }
            }
        }
    }

    fun clearSyncMessage() {
        _uiState.update { it.copy(syncMessage = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
