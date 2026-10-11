package com.personalrecords.app.ui.screens.books

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.personalrecords.app.R
import com.personalrecords.app.core.network.model.BookDto
import com.personalrecords.app.core.theme.*
import com.personalrecords.app.ui.components.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun BooksScreen(
    viewModel: BooksViewModel,
    onNavigateBack: (() -> Unit)? = null
) {
    val state by viewModel.uiState.collectAsState()
    var showDatePicker by remember { mutableStateOf(false) }
    var selectedBook by remember { mutableStateOf<BookDto?>(null) }
    var filteredBooks by remember { mutableStateOf<List<BookDto>>(emptyList()) }
    var isFiltering by remember { mutableStateOf(false) }

    LaunchedEffect(
        state.books,
        state.searchQuery,
        state.selectedDateFilter,
        state.fromDateFilter,
        state.toDateFilter,
        state.selectedShelf,
        state.sortBy
    ) {
        if (state.books.isEmpty()) {
            filteredBooks = emptyList()
            isFiltering = false
            return@LaunchedEffect
        }
        if (isFiltering) {
            delay(160)
        }
        val computed = withContext(Dispatchers.Default) {
            var list = state.books.filter { book ->
                val matchesSearch = state.searchQuery.isBlank() ||
                    book.title.contains(state.searchQuery, ignoreCase = true) ||
                    book.author.contains(state.searchQuery, ignoreCase = true)
                val matchesShelf = state.selectedShelf == "all" ||
                    book.exclusive_shelf.equals(state.selectedShelf, ignoreCase = true)
                val bookDate = book.date_read.trim().replace('/', '-')
                val matchesDate = when {
                    state.selectedDateFilter != null -> bookDate.startsWith(state.selectedDateFilter!!)
                    state.fromDateFilter != null && state.toDateFilter != null ->
                        bookDate.isNotBlank() && bookDate >= state.fromDateFilter!! && bookDate <= state.toDateFilter!!
                    state.fromDateFilter != null -> bookDate.isNotBlank() && bookDate >= state.fromDateFilter!!
                    state.toDateFilter != null -> bookDate.isNotBlank() && bookDate <= state.toDateFilter!!
                    else -> true
                }
                matchesSearch && matchesShelf && matchesDate
            }

            list = when (state.sortBy) {
                "rating_desc" -> list.sortedByDescending { it.my_rating }
                "rating_asc" -> list.sortedBy { it.my_rating }
                "title" -> list.sortedBy { it.title.lowercase() }
                "date" -> list.sortedByDescending { it.date_read }
                else -> list
            }
            list
        }
        filteredBooks = computed
        isFiltering = false
    }

    if (showDatePicker) {
        ExpressiveDateFilterDialog(
            initialStartDate = state.selectedDateFilter ?: state.fromDateFilter,
            initialEndDate = state.toDateFilter,
            onDismissRequest = { showDatePicker = false },
            onDateSelected = { date ->
                isFiltering = true
                viewModel.setDateFilter(date)
            },
            onDateRangeSelected = { start, end ->
                isFiltering = true
                viewModel.setDateRangeFilter(start, end)
            },
            onClear = {
                isFiltering = true
                viewModel.clearDateFilter()
            }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.statusBars,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.title_books),
                        style = MaterialTheme.typography.titleLargeEmphasized
                    )
                },
                navigationIcon = {
                    if (onNavigateBack != null) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_back)
                            )
                        }
                    }
                },
                actions = {},
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        var isRefreshing by remember { mutableStateOf(false) }
        LaunchedEffect(state.isLoading) {
            if (!state.isLoading) isRefreshing = false
        }

        ExpressivePullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { isRefreshing = true; viewModel.loadData() },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                contentPadding = PaddingValues(top = 10.dp, bottom = 144.dp),
                verticalArrangement = Arrangement.Top
            ) {

                // Stats Row
                item {
                    val totalBooks = state.books.size
                    val readBooks = state.books.count { it.exclusive_shelf.equals("read", ignoreCase = true) }
                    val toReadBooks = state.books.count { it.exclusive_shelf.equals("to-read", ignoreCase = true) }
                    val ratedCount = state.books.count { it.my_rating > 0 }
                    val avgRating = if (ratedCount > 0) {
                        String.format("%.1f", state.books.filter { it.my_rating > 0 }.map { it.my_rating }.average())
                    } else "0.0"

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        MetricCard(
                            title = stringResource(R.string.books_stats_total),
                            value = "$totalBooks",
                            badgeText = stringResource(R.string.books_badge_book),
                            note = stringResource(R.string.books_stats_read, readBooks),
                            modifier = Modifier.weight(1f),
                            badgeBg = domainBooksContainer(),
                            badgeColor = domainBooksOnContainer()
                        )
                        MetricCard(
                            title = stringResource(R.string.books_stats_to_read),
                            value = "$toReadBooks",
                            badgeText = stringResource(R.string.badge_list),
                            note = stringResource(R.string.books_stats_avg_rating, avgRating),
                            noteIcon = Icons.Filled.Star,
                            noteIconTint = domainRatingOnContainer(),
                            modifier = Modifier.weight(1f),
                            badgeBg = domainRatingContainer(),
                            badgeColor = domainRatingOnContainer()
                        )
                    }
                }

                // Search Bar & Filter Header (Sticky Header)
                stickyHeader {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = state.searchQuery,
                                onValueChange = { viewModel.setSearchQuery(it) },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = {
                                    Text(
                                        text = stringResource(R.string.books_search_placeholder),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Search,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                trailingIcon = {
                                    if (state.searchQuery.isNotEmpty()) {
                                        IconButton(onClick = {
                                            isFiltering = true
                                            viewModel.setSearchQuery("")
                                        }) {
                                            Icon(
                                                Icons.Default.Clear,
                                                contentDescription = stringResource(R.string.action_clear),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                },
                                shape = M3Shapes.CornerFull,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                ),
                                singleLine = true
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val hasActiveDate = state.selectedDateFilter != null || state.fromDateFilter != null
                                val dateLabel = formatDateRangeChipLabel(
                                    singleDate = state.selectedDateFilter,
                                    fromDate = state.fromDateFilter,
                                    toDate = state.toDateFilter,
                                    defaultText = stringResource(R.string.filter_pick_date)
                                )

                                ExpressiveFilterChip(
                                    selected = hasActiveDate,
                                    onClick = { showDatePicker = true },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (hasActiveDate) Icons.Default.DateRange else Icons.Default.CalendarMonth,
                                            contentDescription = null,
                                            modifier = Modifier.size(FilterChipDefaults.IconSize)
                                        )
                                    },
                                    trailingIcon = if (hasActiveDate) {
                                        {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = null,
                                                modifier = Modifier
                                                    .size(14.dp)
                                                    .clickable {
                                                        isFiltering = true
                                                        viewModel.clearDateFilter()
                                                    }
                                            )
                                        }
                                    } else null,
                                    label = {
                                        Text(
                                            text = dateLabel,
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                )

                                val shelfItems = listOf(
                                    Triple("all", stringResource(R.string.books_shelf_all), null),
                                    Triple("read", stringResource(R.string.books_shelf_read), Icons.Default.Check),
                                    Triple("to-read", stringResource(R.string.books_shelf_to_read), Icons.Default.HourglassEmpty),
                                    Triple("currently-reading", stringResource(R.string.books_shelf_currently_reading), Icons.AutoMirrored.Filled.MenuBook)
                                )
                                val currentShelfItem = shelfItems.firstOrNull { it.first == state.selectedShelf } ?: shelfItems.first()
                                ExpressiveConnectedButtonGroup(
                                    items = shelfItems,
                                    selectedItem = currentShelfItem,
                                    onItemSelected = { (shelf, _, _) ->
                                        if (state.selectedShelf != shelf) {
                                            isFiltering = true
                                            viewModel.setShelf(shelf)
                                        }
                                    },
                                    labelProvider = { it.second },
                                    iconProvider = { item, _ -> item.third }
                                )

                                val (sortLabel, sortIcon) = when (state.sortBy) {
                                    "rating_desc" -> stringResource(R.string.sort_rating_desc) to Icons.Default.ArrowDownward
                                    "rating_asc" -> stringResource(R.string.sort_rating_asc) to Icons.Default.ArrowUpward
                                    "title" -> stringResource(R.string.sort_title) to Icons.Default.SortByAlpha
                                    "date" -> stringResource(R.string.sort_date_read) to Icons.Default.CalendarMonth
                                    else -> stringResource(R.string.action_sort) to Icons.AutoMirrored.Filled.Sort
                                }
                                ExpressiveAssistChip(
                                    onClick = {
                                        val nextSort = when (state.sortBy) {
                                            "rating_desc" -> "rating_asc"
                                            "rating_asc" -> "title"
                                            "title" -> "date"
                                            else -> "rating_desc"
                                        }
                                        isFiltering = true
                                        viewModel.setSortBy(nextSort)
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = sortIcon,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = sortLabel,
                                            style = MaterialTheme.typography.labelMediumEmphasized,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    },
                                    colors = borderlessAssistChipColors()
                                )
                            }
                        }
                    }
                }

                if ((state.isLoading && state.books.isEmpty()) || isFiltering) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            ContainedLoadingIndicator(size = 44.dp)
                        }
                    }
                } else if (filteredBooks.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.books_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    itemsIndexed(
                        items = filteredBooks,
                        key = { index, book -> "${book.book_id}_${book.title}_$index" }
                    ) { index, book ->
                        LazyCardRowContainer(
                            index = index,
                            totalCount = filteredBooks.size
                        ) {
                            BookRowItem(
                                book = book,
                                onClick = { selectedBook = book }
                            )
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(20.dp)) }
            }
        }
    }

    selectedBook?.let { book ->
        BookDetailBottomSheet(
            book = book,
            onDismiss = { selectedBook = null }
        )
    }
}

@Composable
fun BookRowItem(
    book: BookDto,
    onClick: () -> Unit = {}
) {
    val shelfLabel = when (book.exclusive_shelf.lowercase()) {
        "read" -> stringResource(R.string.books_shelf_read)
        "to-read" -> stringResource(R.string.books_shelf_to_read)
        "currently-reading" -> stringResource(R.string.books_shelf_currently_reading)
        else -> book.exclusive_shelf
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .expressiveBounceClick(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PosterThumbnail(imageUrl = book.image_url, fallbackIcon = Icons.AutoMirrored.Filled.MenuBook, width = 36, height = 52)
        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = book.title,
                style = MaterialTheme.typography.titleSmallEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = book.author,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = M3Shapes.CornerFull,
                    color = domainBooksContainer()
                ) {
                    Text(
                        text = shelfLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = domainBooksOnContainer(),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.5.dp),
                        maxLines = 1,
                        softWrap = false
                    )
                }
                if (book.number_of_pages > 0) {
                    Text(
                        text = stringResource(R.string.books_pages, book.number_of_pages),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        if (book.my_rating > 0) {
            Spacer(modifier = Modifier.width(8.dp))
            RatingBadge(rating = book.my_rating)
        }
    }
}

@Composable
fun BookCard(book: BookDto) {
    BookRowItem(book = book)
}

