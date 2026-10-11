package com.personalrecords.app.ui.screens.records

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalrecords.app.core.common.Resource
import com.personalrecords.app.data.repository.PersonalRecordsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val result = repository.getDashboard()) {
                is Resource.Success -> {
                    val dashboard = result.data
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
