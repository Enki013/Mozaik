package com.personalrecords.app.ui.screens.records

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalrecords.app.core.common.Resource
import com.personalrecords.app.data.repository.PersonalRecordsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class RecordsUiState(
    val isLoading: Boolean = true,
    val counts: Map<String, Int> = emptyMap(),
    val todayCalories: String = "0",
    val todayEntries: String = "0",
    val error: String? = null
) {
    val totalRecords: Int
        get() = counts.values.sum()
}

class RecordsViewModel(
    private val repository: PersonalRecordsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecordsUiState())
    val uiState: StateFlow<RecordsUiState> = _uiState.asStateFlow()
    private var hasInitialized = false

    fun ensureLoaded() {
        if (hasInitialized) return
        hasInitialized = true
        val cached = repository.cachedDashboard
        if (cached != null) {
            applyDashboard(cached)
        } else {
            loadData()
        }
    }

    private fun applyDashboard(dashboard: com.personalrecords.app.core.network.model.DashboardDto) {
        val counts = dashboard.counts
        val today = dashboard.today
        val cal = today["fatsecret_calories"]?.toString()?.replace("\"", "")
            ?: today["calories"]?.toString()?.replace("\"", "") ?: "0"
        val entries = today["fatsecret_entry_count"]?.toString()?.replace("\"", "")
            ?: today["entries_count"]?.toString()?.replace("\"", "") ?: "0"

        _uiState.update {
            it.copy(
                isLoading = false,
                counts = counts,
                todayCalories = cal,
                todayEntries = entries,
                error = null
            )
        }
    }

    fun loadData() {
        hasInitialized = true
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val result = repository.getDashboard()) {
                is Resource.Success -> applyDashboard(result.data)
                is Resource.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = result.message
                        )
                    }
                }
                is Resource.Loading -> Unit
            }
        }
    }
}
