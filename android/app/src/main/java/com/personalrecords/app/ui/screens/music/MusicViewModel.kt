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

@Immutable
data class MusicUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isSyncingLastFm: Boolean = false,
    val scrobbles: List<MusicScrobbleDto> = emptyList(),
    val filteredScrobbles: List<MusicScrobbleDto> = emptyList(),
    val stats: MusicStatsDto = MusicStatsDto(),
    val selectedFilter: String = "all", // "all", "lastfm", "web_scrobbler"
    val searchQuery: String = "",
    val error: String? = null,
    val syncMessage: String? = null
)

private fun filterMusicScrobbles(
    scrobbles: List<MusicScrobbleDto>,
    selectedFilter: String,
    searchQuery: String
): List<MusicScrobbleDto> {
    if (scrobbles.isEmpty()) return emptyList()
    return scrobbles.filter { item ->
        val matchesSource = when (selectedFilter) {
            "lastfm" -> item.source.equals("lastfm", ignoreCase = true)
            "web_scrobbler" -> item.source.contains("scrobbl", ignoreCase = true)
            else -> true
        }
        val matchesSearch = searchQuery.isBlank() ||
            item.track.contains(searchQuery, ignoreCase = true) ||
            item.artist.contains(searchQuery, ignoreCase = true) ||
            item.album.contains(searchQuery, ignoreCase = true)
        matchesSource && matchesSearch
    }
}

class MusicViewModel(
    private val repository: PersonalRecordsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MusicUiState())
    val uiState: StateFlow<MusicUiState> = _uiState.asStateFlow()
    private var searchJob: Job? = null

    init {
        loadInitialData()
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            val cachedScrobbles = repository.cachedMusicScrobbles
            val cachedStats = repository.cachedMusicStats
            if (!cachedScrobbles.isNullOrEmpty()) {
                val filtered = withContext(Dispatchers.Default) {
                    filterMusicScrobbles(cachedScrobbles, _uiState.value.selectedFilter, _uiState.value.searchQuery)
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

        when (scrobblesRes) {
            is Resource.Success -> {
                val items = scrobblesRes.data?.items ?: emptyList()
                val stats = (statsRes as? Resource.Success)?.data ?: MusicStatsDto(totalScrobbles = items.size)
                val filtered = withContext(Dispatchers.Default) {
                    filterMusicScrobbles(items, _uiState.value.selectedFilter, _uiState.value.searchQuery)
                }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        scrobbles = items,
                        filteredScrobbles = filtered,
                        stats = stats,
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
        _uiState.update { current ->
            val filtered = filterMusicScrobbles(current.scrobbles, filter, current.searchQuery)
            current.copy(selectedFilter = filter, filteredScrobbles = filtered)
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(150L)
            val filtered = withContext(Dispatchers.Default) {
                filterMusicScrobbles(_uiState.value.scrobbles, _uiState.value.selectedFilter, query)
            }
            _uiState.update { it.copy(filteredScrobbles = filtered) }
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
