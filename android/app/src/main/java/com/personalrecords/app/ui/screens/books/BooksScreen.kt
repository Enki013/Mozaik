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

    val booksColor = domainBooksColor()
    val ratingColor = domainRatingColor()
    val booksStatCards = remember(booksColor, ratingColor) {
        listOf(
            StatCardItem(
                id = "books_total",
                title = "Toplam Kitap",
                description = "Okuma günlüğündeki tüm kitaplar",
                icon = Icons.AutoMirrored.Filled.MenuBook,
                iconTint = booksColor
            ),
            StatCardItem(
                id = "books_to_read",
                title = "Okunacaklar",
                description = "Daha sonra okunacaklar listesi ve puan",
                icon = Icons.Default.Bookmark,
                iconTint = ratingColor
            )
        )
    }

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
    val configuration = LocalConfiguration.current
    val isWideScreen = configuration.screenWidthDp >= 600
    val isTwoPane = configuration.screenWidthDp >= 840
    val gridState = rememberLazyGridState()

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets.statusBars,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.title_books),
                        style = MaterialTheme.typography.titleLargeEmphasized
                    )
                },
                scrollBehavior = scrollBehavior,
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
            val listContent: @Composable () -> Unit = {
                LazyVerticalGrid(
                    columns = if (isWideScreen) GridCells.Adaptive(minSize = 340.dp) else GridCells.Fixed(1),
                    state = gridState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    contentPadding = PaddingValues(top = 10.dp, bottom = if (isWideScreen) 32.dp else 144.dp),
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

                    if (isTotalVisible || isToReadVisible) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (isTotalVisible) {
                                StatCardContainer(
                                    cardId = "books_total",
                                    isEditMode = isEditMode,
                                    onHideCard = {
                                        coroutineScope.launch {
                                            preferences.toggleStatCardVisibility("books_total", false)
                                        }
                                    },
                                    index = 0,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    MetricCard(
                                        title = stringResource(R.string.books_stats_total),
                                        value = "$totalBooks",
                                        badgeText = stringResource(R.string.books_badge_book),
                                        note = stringResource(R.string.books_stats_read, readBooks),
                                        valueIcon = Icons.AutoMirrored.Filled.MenuBook,
                                        valueIconTint = domainBooksColor(),
                                        badgeBg = domainBooksContainer(),
                                        badgeColor = domainBooksOnContainer(),
                                        onLongClick = { isEditMode = true }
                                    )
                                }
                            }
                            if (isToReadVisible) {
                                StatCardContainer(
                                    cardId = "books_to_read",
                                    isEditMode = isEditMode,
                                    onHideCard = {
                                        coroutineScope.launch {
                                            preferences.toggleStatCardVisibility("books_to_read", false)
                                        }
                                    },
                                    index = 1,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    MetricCard(
                                        title = stringResource(R.string.books_stats_to_read),
                                        value = "$toReadBooks",
                                        badgeText = stringResource(R.string.badge_list),
                                        note = stringResource(R.string.books_stats_avg_rating, avgRating),
                                        valueIcon = Icons.Default.Bookmark,
                                        valueIconTint = domainRatingColor(),
                                        noteIcon = Icons.Filled.Star,
                                        noteIconTint = domainRatingOnContainer(),
                                        badgeBg = domainRatingContainer(),
                                        badgeColor = domainRatingOnContainer(),
                                        onLongClick = { isEditMode = true }
                                    )
                                }
                            }
                        }
                    }
                }

                // Search Bar & Filter Header
                item(key = "books_sticky_header", span = { GridItemSpan(maxLineSpan) }, contentType = "sticky_header") {
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

                                ExpressiveConnectedButtonGroup(
                                    items = BookShelfKeys,
                                    selectedItem = state.selectedShelf,
                                    onItemSelected = { shelf ->
                                        if (state.selectedShelf != shelf) {
                                            viewModel.setShelf(shelf)
                                        }
                                    },
                                    labelProvider = { shelf ->
                                        when (shelf) {
                                            "read" -> stringResource(R.string.books_shelf_read)
                                            "to-read" -> stringResource(R.string.books_shelf_to_read)
                                            "currently-reading" -> stringResource(R.string.books_shelf_currently_reading)
                                            else -> stringResource(R.string.books_shelf_all)
                                        }
                                    },
                                    iconProvider = { shelf, _ ->
                                        when (shelf) {
                                            "read" -> Icons.Default.Check
                                            "to-read" -> Icons.Default.HourglassEmpty
                                            "currently-reading" -> Icons.AutoMirrored.Filled.MenuBook
                                            else -> null
                                        }
                                    }
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

                if ((state.isLoading || state.isFiltering) && state.filteredBooks.isEmpty()) {
                    if (!isRefreshing) {
                        item(key = "books_loading", span = { GridItemSpan(maxLineSpan) }, contentType = "loading") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                ContainedLoadingIndicator(size = 44.dp)
                            }
                        }
                    }
                } else if (state.filteredBooks.isEmpty()) {
                    item(key = "books_empty", span = { GridItemSpan(maxLineSpan) }, contentType = "empty") {
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
                        items = state.filteredBooks,
                        key = { _, book -> book.book_id.ifBlank { "${book.title}_${book.author}" } },
                        contentType = { _, _ -> "book_row" }
                    ) { index, book ->
                        if (isWideScreen) {
                            Surface(
                                modifier = expressiveItemModifier().fillMaxWidth(),
                                shape = M3Shapes.CornerMedium,
                                color = MaterialTheme.colorScheme.surfaceContainerLow
                            ) {
                                BookRowItem(
                                    book = book,
                                    onClick = { selectedBook = book }
                                )
                            }
                        } else {
                            LazyCardRowContainer(
                                index = index,
                                totalCount = state.filteredBooks.size,
                                modifier = expressiveItemModifier()
                            ) {
                                BookRowItem(
                                    book = book,
                                    onClick = { selectedBook = book }
                                )
                            }
                        }
                    }
                }

                item(key = "books_bottom_spacer", span = { GridItemSpan(maxLineSpan) }, contentType = "spacer") { Spacer(modifier = Modifier.height(20.dp)) }
            }
        }

        if (isTwoPane) {
            Row(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .weight(1.15f)
                        .fillMaxHeight()
                ) {
                    listContent()
                }
                VerticalDivider(
                    modifier = Modifier.fillMaxHeight(),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
                Box(
                    modifier = Modifier
                        .weight(0.85f)
                        .fillMaxHeight()
                        .padding(top = 10.dp, end = 12.dp, bottom = 32.dp)
                ) {
                    if (selectedBook != null) {
                        BookDetailPaneCard(
                            book = selectedBook!!,
                            onClose = { selectedBook = null }
                        )
                    } else {
                        DetailPlaceholderPane(
                            icon = Icons.AutoMirrored.Filled.MenuBook,
                            title = stringResource(R.string.adaptive_select_item_title),
                            subtitle = stringResource(R.string.adaptive_select_item_subtitle)
                        )
                    }
                }
            }
        } else {
            listContent()
        }
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
            cards = booksStatCards,
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

