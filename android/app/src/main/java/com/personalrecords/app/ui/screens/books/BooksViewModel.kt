package com.personalrecords.app.ui.screens.books

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalrecords.app.core.common.Resource
import com.personalrecords.app.core.network.model.BookDto
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

data class BooksUiState(
    val isLoading: Boolean = true,
    val isFiltering: Boolean = false,
    val books: List<BookDto> = emptyList(),
    val filteredBooks: List<BookDto> = emptyList(),
    val readBooksCount: Int = 0,
    val toReadBooksCount: Int = 0,
    val avgRatingText: String = "0.0",
    val searchQuery: String = "",
    val selectedDateFilter: String? = null,
    val fromDateFilter: String? = null,
    val toDateFilter: String? = null,
    val selectedShelf: String = "all", // "all", "read", "to-read", "currently-reading"
    val sortBy: String = "rating_desc", // "rating_desc", "rating_asc", "title", "author", "date"
    val error: String? = null
)

private fun filterAndSortBooks(
    books: List<BookDto>,
    searchQuery: String,
    selectedShelf: String,
    selectedDateFilter: String?,
    fromDateFilter: String?,
    toDateFilter: String?,
    sortBy: String
): List<BookDto> {
    if (books.isEmpty()) return emptyList()
    val filtered = books.filter { book ->
        val matchesSearch = searchQuery.isBlank() ||
            book.title.contains(searchQuery, ignoreCase = true) ||
            book.author.contains(searchQuery, ignoreCase = true)
        val matchesShelf = selectedShelf == "all" ||
            book.exclusive_shelf.equals(selectedShelf, ignoreCase = true)
        val bookDate = book.date_read.trim().replace('/', '-')
        val matchesDate = when {
            selectedDateFilter != null -> bookDate.startsWith(selectedDateFilter)
            fromDateFilter != null && toDateFilter != null ->
                bookDate.isNotBlank() && bookDate >= fromDateFilter && bookDate <= toDateFilter
            fromDateFilter != null -> bookDate.isNotBlank() && bookDate >= fromDateFilter
            toDateFilter != null -> bookDate.isNotBlank() && bookDate <= toDateFilter
            else -> true
        }
        matchesSearch && matchesShelf && matchesDate
    }

    return when (sortBy) {
        "rating_desc" -> filtered.sortedByDescending { it.my_rating }
        "rating_asc" -> filtered.sortedBy { it.my_rating }
        "title" -> filtered.sortedBy { it.title.lowercase(Locale.getDefault()) }
        "date" -> filtered.sortedByDescending { it.date_read }
        else -> filtered
    }
}

class BooksViewModel(
    private val repository: PersonalRecordsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BooksUiState())
    val uiState: StateFlow<BooksUiState> = _uiState.asStateFlow()
    private var filterJob: Job? = null

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            when (val result = repository.getBooks()) {
                is Resource.Success -> {
                    val books = result.data
                    val snap = _uiState.value
                    val (filtered, readCount, toReadCount, avgText) = withContext(Dispatchers.Default) {
                        val list = filterAndSortBooks(
                            books = books,
                            searchQuery = snap.searchQuery,
                            selectedShelf = snap.selectedShelf,
                            selectedDateFilter = snap.selectedDateFilter,
                            fromDateFilter = snap.fromDateFilter,
                            toDateFilter = snap.toDateFilter,
                            sortBy = snap.sortBy
                        )
                        val read = books.count { it.exclusive_shelf.equals("read", ignoreCase = true) }
                        val toRead = books.count { it.exclusive_shelf.equals("to-read", ignoreCase = true) }
                        val rated = books.filter { it.my_rating > 0 }
                        val avg = if (rated.isNotEmpty()) {
                            String.format(Locale.US, "%.1f", rated.map { it.my_rating }.average())
                        } else "0.0"
                        Quadruple(list, read, toRead, avg)
                    }
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isFiltering = false,
                        books = books,
                        filteredBooks = filtered,
                        readBooksCount = readCount,
                        toReadBooksCount = toReadCount,
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

    fun setShelf(shelf: String) {
        if (_uiState.value.selectedShelf == shelf) return
        _uiState.value = _uiState.value.copy(selectedShelf = shelf, isFiltering = true)
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
                filterAndSortBooks(
                    books = snap.books,
                    searchQuery = snap.searchQuery,
                    selectedShelf = snap.selectedShelf,
                    selectedDateFilter = snap.selectedDateFilter,
                    fromDateFilter = snap.fromDateFilter,
                    toDateFilter = snap.toDateFilter,
                    sortBy = snap.sortBy
                )
            }
            _uiState.value = _uiState.value.copy(
                filteredBooks = filtered,
                isFiltering = false
            )
        }
    }
}

private data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)
