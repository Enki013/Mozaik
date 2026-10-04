package com.personalrecords.app.ui.screens.books

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalrecords.app.core.common.Resource
import com.personalrecords.app.core.network.model.BookDto
import com.personalrecords.app.data.repository.PersonalRecordsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BooksUiState(
    val isLoading: Boolean = false,
    val books: List<BookDto> = emptyList(),
    val searchQuery: String = "",
    val selectedDateFilter: String? = null,
    val fromDateFilter: String? = null,
    val toDateFilter: String? = null,
    val selectedShelf: String = "all", // "all", "read", "to-read", "currently-reading"
    val sortBy: String = "rating_desc", // "rating_desc", "rating_asc", "title", "author", "date"
    val error: String? = null
)

class BooksViewModel(
    private val repository: PersonalRecordsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BooksUiState())
    val uiState: StateFlow<BooksUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = repository.getBooks()
            when (result) {
                is Resource.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        books = result.data
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

    fun setShelf(shelf: String) {
        _uiState.value = _uiState.value.copy(selectedShelf = shelf)
    }

    fun setSortBy(sort: String) {
        _uiState.value = _uiState.value.copy(sortBy = sort)
    }
}
