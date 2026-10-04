package com.personalrecords.app.ui.screens.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalrecords.app.core.common.Resource
import com.personalrecords.app.core.network.model.NotificationDto
import com.personalrecords.app.data.repository.PersonalRecordsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class NotificationsUiState(
    val isLoading: Boolean = false,
    val notifications: List<NotificationDto> = emptyList(),
    val selectedApp: String? = null,
    val searchQuery: String = "",
    val selectedDateFilter: String? = null,
    val fromDateFilter: String? = null,
    val toDateFilter: String? = null,
    val error: String? = null
)

class NotificationsViewModel(
    private val repository: PersonalRecordsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationsUiState())
    val uiState: StateFlow<NotificationsUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = repository.getNotifications(limit = 200)
            when (result) {
                is Resource.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        notifications = result.data
                    )
                }
                is Resource.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = result.message
                    )
                }
                else -> Unit
            }
        }
    }

    fun setAppFilter(app: String?) {
        _uiState.value = _uiState.value.copy(selectedApp = app)
    }

    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun setDateFilter(date: String?) {
        _uiState.value = _uiState.value.copy(
            selectedDateFilter = date,
            fromDateFilter = null,
            toDateFilter = null
        )
    }

    fun setDateRangeFilter(from: String?, to: String?) {
        _uiState.value = _uiState.value.copy(
            selectedDateFilter = null,
            fromDateFilter = from,
            toDateFilter = to
        )
    }

    fun clearDateFilter() {
        _uiState.value = _uiState.value.copy(
            selectedDateFilter = null,
            fromDateFilter = null,
            toDateFilter = null
        )
    }
}
