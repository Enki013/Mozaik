package com.personalrecords.app.ui.screens.books

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.personalrecords.app.PersonalRecordsApp
import com.personalrecords.app.R
import com.personalrecords.app.core.network.model.BookDto
import com.personalrecords.app.core.theme.*
import com.personalrecords.app.ui.components.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

private val BookShelfKeys = listOf("all", "read", "to-read", "currently-reading")

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun BooksScreen(
    viewModel: BooksViewModel,
    onNavigateBack: (() -> Unit)? = null
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val preferences = remember { PersonalRecordsApp.instance.preferences }
    val hiddenCardIds by preferences.hiddenStatsCards.collectAsStateWithLifecycle(initialValue = emptySet())
    val coroutineScope = rememberCoroutineScope()
    var isEditMode by remember { mutableStateOf(false) }
    var showCustomizationSheet by remember { mutableStateOf(false) }
    var targetCardId by remember { mutableStateOf<String?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var selectedBook by remember { mutableStateOf<BookDto?>(null) }

    val booksColor = recordCategoryColors(RecordCategory.Books).accent
    val ratingColor = domainRatingColor()
    val booksTotalTitle = stringResource(R.string.stats_card_books_total_title)
    val booksTotalDesc = stringResource(R.string.stats_card_books_total_desc)
    val booksToReadTitle = stringResource(R.string.stats_card_books_to_read_title)
    val booksToReadDesc = stringResource(R.string.stats_card_books_to_read_desc)
    val booksStatCards = remember(booksColor, ratingColor, booksTotalTitle, booksTotalDesc, booksToReadTitle, booksToReadDesc) {
        listOf(
            StatCardItem(
                id = "books_total",
                title = booksTotalTitle,
                description = booksTotalDesc,
                icon = Icons.AutoMirrored.Rounded.MenuBook,
                iconTint = booksColor
            ),
            StatCardItem(
                id = "books_to_read",
                title = booksToReadTitle,
                description = booksToReadDesc,
                icon = Icons.Rounded.Bookmark,
                iconTint = ratingColor
            )
        )
    }
    val statCardOrder = rememberStatCardOrder("books", booksStatCards.map { it.id })

    if (showDatePicker) {
        ExpressiveDateFilterDialog(
            initialStartDate = state.selectedDateFilter ?: state.fromDateFilter,
            initialEndDate = state.toDateFilter,
            onDismissRequest = { showDatePicker = false },
            onDateSelected = { date ->
                viewModel.setDateFilter(date)
            },
            onDateRangeSelected = { start, end ->
                viewModel.setDateRangeFilter(start, end)
            },
            onClear = {
                viewModel.clearDateFilter()
            }
        )
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
    val adaptiveLayout = currentAdaptiveLayout()
    val isWideScreen = adaptiveLayout.isWide
    val isTwoPane = adaptiveLayout.showsDetailPane
    val gridState = rememberLazyGridState()

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets.statusBars,
        topBar = {
            ExpressivePageTopAppBar(
                title = stringResource(R.string.title_books),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    if (onNavigateBack != null) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.action_back)
                            )
                        }
                    }
                },
                actions = {
                    TopAppBarStatsAction(
                        isEditMode = isEditMode,
                        hasHiddenCards = booksStatCards.any { it.id in hiddenCardIds },
                        onToggleEditMode = { isEditMode = !isEditMode },
                        onOpenCustomization = {
                            targetCardId = null
                            showCustomizationSheet = true
                        }
                    )
                },
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
            val listContent: @Composable () -> Unit = {
                LazyVerticalGrid(
                    columns = if (isWideScreen) GridCells.Adaptive(minSize = 340.dp) else GridCells.Fixed(1),
                    state = gridState,
                    modifier = Modifier
                        .fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = FilterGutter,
                        end = FilterGutter,
                        top = 10.dp,
                        bottom = 24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    ),
                horizontalArrangement = if (isWideScreen) Arrangement.spacedBy(8.dp) else Arrangement.Start,
                verticalArrangement = if (isWideScreen) Arrangement.spacedBy(8.dp) else Arrangement.Top
            ) {
                // Stats Row
                item(key = "books_stats_row", span = { GridItemSpan(maxLineSpan) }, contentType = "stats_row") {
                    val totalBooks = state.books.size
                    val readBooks = state.readBooksCount
                    val toReadBooks = state.toReadBooksCount
                    val avgRating = state.avgRatingText

                    val isTotalVisible = "books_total" !in hiddenCardIds
                    val isToReadVisible = "books_to_read" !in hiddenCardIds

                    LaunchedEffect(isTotalVisible, isToReadVisible) {
                        if (!isTotalVisible && !isToReadVisible) {
                            isEditMode = false
                        }
                    }

                    StatCardGrid(
                        cardIds = statCardOrder.orderedIds.filter { it !in hiddenCardIds },
                        isEditMode = isEditMode,
                        onHideCard = { id ->
                            coroutineScope.launch { preferences.toggleStatCardVisibility(id, false) }
                        },
                        onReorder = { statCardOrder.setVisibleOrder(it) },
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) { cardId ->
                        when (cardId) {
                            "books_total" -> {
                                MetricCard(
                                    title = stringResource(R.string.books_stats_total),
                                    value = "$totalBooks",
                                    badgeText = "",
                                    note = stringResource(R.string.books_stats_read, readBooks),
                                    valueIcon = Icons.AutoMirrored.Rounded.MenuBook,
                                    valueIconTint = recordCategoryColors(RecordCategory.Books).accent,
                                    badgeBg = recordCategoryColors(RecordCategory.Books).container,
                                    badgeColor = recordCategoryColors(RecordCategory.Books).onContainer,
                                    onLongClick = { isEditMode = true }
                                )
                            }
                            "books_to_read" -> {
                                MetricCard(
                                    title = stringResource(R.string.books_stats_to_read),
                                    value = "$toReadBooks",
                                    badgeText = "",
                                    note = stringResource(R.string.books_stats_avg_rating, avgRating),
                                    valueIcon = Icons.Rounded.Bookmark,
                                    valueIconTint = domainRatingColor(),
                                    noteIcon = Icons.Rounded.Star,
                                    noteIconTint = domainRatingOnContainer(),
                                    badgeBg = domainRatingContainer(),
                                    badgeColor = domainRatingOnContainer(),
                                    onLongClick = { isEditMode = true }
                                )
                            }
                        }
                    }
                }

                state.error?.let { err ->
                    item(key = "books_error_banner", span = { GridItemSpan(maxLineSpan) }, contentType = "error_banner") {
                        ExpressiveErrorBanner(
                            message = err,
                            onRetry = { viewModel.loadData() },
                            modifier = expressiveItemModifier().padding(bottom = 8.dp)
                        )
                    }
                }

                // Search Bar & Filter Header
                stickyHeader(key = "books_sticky_header", contentType = "sticky_header") {
                    ExpressiveFilterHeader {
                        ExpressiveSearchField(
                            value = state.searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            placeholder = stringResource(R.string.books_search_placeholder),
                            clearContentDescription = stringResource(R.string.action_clear),
                            modifier = Modifier
                                .fillMaxWidth()
                                .filterHeaderItem()
                        )
                        val hasActiveDate = state.selectedDateFilter != null || state.fromDateFilter != null
                        val hasActiveFilters = state.searchQuery.isNotEmpty() || hasActiveDate || state.selectedShelf != "all"
                        ExpressiveFilterRow {
                            if (hasActiveFilters) {
                                ExpressiveClearFiltersChip(onClick = { viewModel.resetFilters() })
                            }
                            ExpressiveQuickDateFilters(
                                selectedDate = state.selectedDateFilter,
                                fromDate = state.fromDateFilter,
                                toDate = state.toDateFilter,
                                onPickDate = { showDatePicker = true },
                                onClearDate = { viewModel.clearDateFilter() },
                                onQuickRange = { range ->
                                    applyQuickRange(range, viewModel::setDateFilter, viewModel::setDateRangeFilter)
                                }
                            )
                            ExpressiveDropdownFilterChip(
                                options = listOf("date", "rating_desc", "rating_asc", "title"),
                                selected = state.sortBy,
                                onSelect = { viewModel.setSortBy(it) },
                                optionLabel = { key ->
                                    when (key) {
                                        "date" -> stringResource(R.string.sort_date_read)
                                        "rating_desc" -> stringResource(R.string.sort_rating_desc)
                                        "rating_asc" -> stringResource(R.string.sort_rating_asc)
                                        "title" -> stringResource(R.string.sort_title)
                                        else -> stringResource(R.string.action_sort)
                                    }
                                },
                                leadingIcon = Icons.AutoMirrored.Rounded.Sort,
                                isActive = false
                            )
                            ExpressiveConnectedButtonGroup(
                                items = BookShelfKeys,
                                selectedItem = state.selectedShelf,
                                onItemSelected = { key -> viewModel.setShelf(key) },
                                labelProvider = { key ->
                                    when (key) {
                                        "read" -> stringResource(R.string.books_shelf_read)
                                        "to-read" -> stringResource(R.string.books_shelf_to_read)
                                        "currently-reading" -> stringResource(R.string.books_shelf_currently_reading)
                                        else -> stringResource(R.string.filter_all)
                                    }
                                },
                                iconProvider = { key, _ ->
                                    when (key) {
                                        "read" -> Icons.AutoMirrored.Rounded.LibraryBooks
                                        "to-read" -> Icons.Rounded.HourglassEmpty
                                        "currently-reading" -> Icons.AutoMirrored.Rounded.MenuBook
                                        else -> null
                                    }
                                }
                            )
                        }
                        if (hasActiveFilters) {
                            ExpressiveFilterResultCount(count = state.filteredBooks.size)
                        }
                    }
                }

                if ((state.isLoading || state.isFiltering) && state.filteredBooks.isEmpty()) {
                    if (!isRefreshing) {
                        item(key = "books_loading", span = { GridItemSpan(maxLineSpan) }, contentType = "loading") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                AppContainedLoadingIndicator(size = 44.dp)
                            }
                        }
                    }
                } else if (state.filteredBooks.isEmpty() && state.error == null) {
                    item(key = "books_empty", span = { GridItemSpan(maxLineSpan) }, contentType = "empty") {
                        ExpressiveEmptyState(
                            title = stringResource(R.string.books_empty),
                            icon = Icons.AutoMirrored.Rounded.MenuBook,
                            iconContainerColor = recordCategoryColors(RecordCategory.Books).container,
                            iconContentColor = recordCategoryColors(RecordCategory.Books).onContainer
                        )
                    }
                } else {
                    itemsIndexed(
                        items = state.filteredBooks,
                        key = { _, book -> book.book_id.ifBlank { "${book.title}_${book.author}" } },
                        contentType = { _, _ -> "book_row" }
                    ) { index, book ->
                        BookRowItem(
                            book = book,
                            // Grid cells on wide screens are standalone rows; phones get one segmented group.
                            index = if (isWideScreen) 0 else index,
                            count = if (isWideScreen) 1 else state.filteredBooks.size,
                            addLeadingGap = !isWideScreen,
                            modifier = expressiveItemModifier(),
                            onClick = { selectedBook = book },
                            selected = if (isTwoPane) selectedBook == book else null
                        )
                    }
                }

                item(key = "books_bottom_spacer", span = { GridItemSpan(maxLineSpan) }, contentType = "spacer") { Spacer(modifier = Modifier.height(20.dp)) }
            }
        }

        ExpressiveListDetailLayout(
            showDetailPane = isTwoPane,
            detailPane = {
                if (selectedBook != null) {
                    BookDetailPaneCard(
                        book = selectedBook!!,
                        onClose = { selectedBook = null }
                    )
                } else {
                    DetailPlaceholderPane(
                        icon = Icons.AutoMirrored.Rounded.MenuBook,
                        title = stringResource(R.string.adaptive_select_item_title),
                        subtitle = stringResource(R.string.adaptive_select_item_subtitle)
                    )
                }
            },
            list = listContent
        )
    }
}

    if (!isTwoPane) {
        selectedBook?.let { book ->
            BookDetailBottomSheet(
                book = book,
                onDismiss = { selectedBook = null }
            )
        }
    }

    if (showCustomizationSheet) {
        StatsCustomizationBottomSheet(
            sheetTitle = stringResource(R.string.stats_customization_title),
            cards = statCardOrder.sort(booksStatCards),
            onReorder = { ids -> statCardOrder.setOrder(ids) },
            hiddenCardIds = hiddenCardIds,
            targetCardId = targetCardId,
            onToggleCard = { cardId, visible ->
                coroutineScope.launch {
                    preferences.toggleStatCardVisibility(cardId, visible)
                }
            },
            onResetDefault = {
                coroutineScope.launch {
                    preferences.resetHiddenStatsCards(booksStatCards.map { it.id })
                    statCardOrder.reset()
                }
            },
            onDismissRequest = {
                showCustomizationSheet = false
                targetCardId = null
            }
        )
    }
}

@Composable
fun BookRowItem(
    book: BookDto,
    index: Int = 0,
    count: Int = 1,
    modifier: Modifier = Modifier,
    selected: Boolean? = null,
    addLeadingGap: Boolean = false,
    onClick: () -> Unit = {}
) {
    val shelfLabel = when (book.exclusive_shelf.lowercase()) {
        "read" -> stringResource(R.string.books_shelf_read)
        "to-read" -> stringResource(R.string.books_shelf_to_read)
        "currently-reading" -> stringResource(R.string.books_shelf_currently_reading)
        else -> book.exclusive_shelf
    }

    ExpressiveListRow(
        index = index,
        count = count,
        modifier = modifier,
        selected = selected,
        addLeadingGap = addLeadingGap,
        onClick = onClick,
        verticalAlignment = Alignment.CenterVertically,
        leadingContent = {
            PosterThumbnail(imageUrl = book.image_url, fallbackIcon = Icons.AutoMirrored.Rounded.MenuBook, width = 36, height = 52)
        },
        trailingContent = if (book.my_rating > 0) {
            { RatingBadge(rating = book.my_rating) }
        } else null,
        supportingContent = {
            Column {
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
                        color = recordCategoryColors(RecordCategory.Books).container
                    ) {
                        Text(
                            text = shelfLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = recordCategoryColors(RecordCategory.Books).onContainer,
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
        }
    ) {
        Text(
            text = book.title,
            style = MaterialTheme.typography.titleSmallEmphasized,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun BookCard(book: BookDto) {
    BookRowItem(book = book)
}

