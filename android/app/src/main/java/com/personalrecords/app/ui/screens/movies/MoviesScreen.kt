package com.personalrecords.app.ui.screens.movies

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.personalrecords.app.PersonalRecordsApp
import com.personalrecords.app.R
import com.personalrecords.app.core.network.model.FilmDto
import com.personalrecords.app.core.theme.*
import com.personalrecords.app.ui.components.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun MoviesScreen(
    viewModel: MoviesViewModel,
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
    var selectedFilm by remember { mutableStateOf<FilmDto?>(null) }

    val cinemaColor = recordCategoryColors(RecordCategory.Movies).accent
    val ratingColor = ratingColors().accent
    val totalTitle = stringResource(R.string.movies_stats_total)
    val totalDesc = stringResource(R.string.movies_stats_total_desc)
    val avgTitle = stringResource(R.string.movies_stats_avg_rating)
    val avgDesc = stringResource(R.string.movies_stats_avg_rating_desc)
    val moviesStatCards = remember(cinemaColor, ratingColor, totalTitle, totalDesc, avgTitle, avgDesc) {
        listOf(
            StatCardItem(
                id = "movies_total",
                title = totalTitle,
                description = totalDesc,
                icon = Icons.Rounded.Movie,
                iconTint = cinemaColor
            ),
            StatCardItem(
                id = "movies_avg_rating",
                title = avgTitle,
                description = avgDesc,
                icon = Icons.Rounded.Star,
                iconTint = ratingColor
            )
        )
    }
    val statCardOrder = rememberStatCardOrder("movies", moviesStatCards.map { it.id })

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
                title = stringResource(R.string.title_movies),
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
                        hasHiddenCards = moviesStatCards.any { it.id in hiddenCardIds },
                        onToggleEditMode = { isEditMode = !isEditMode },
                        onOpenCustomization = {
                            targetCardId = null
                            showCustomizationSheet = true
                        }
                    )
                }
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
                item(key = "movies_stats_row", span = { GridItemSpan(maxLineSpan) }, contentType = "stats_row") {
                    val totalFilms = state.films.size
                    val ratedFilms = state.ratedFilmsCount
                    val rewatchFilms = state.rewatchFilmsCount
                    val avgRating = state.avgRatingText

                    val isTotalVisible = "movies_total" !in hiddenCardIds
                    val isAvgVisible = "movies_avg_rating" !in hiddenCardIds

                    LaunchedEffect(isTotalVisible, isAvgVisible) {
                        if (!isTotalVisible && !isAvgVisible) {
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
                            "movies_total" -> {
                                MetricCard(
                                    title = stringResource(R.string.movies_stats_total),
                                    value = "$totalFilms",
                                    badgeText = "",
                                    note = stringResource(R.string.movies_stats_rated, ratedFilms),
                                    valueIcon = Icons.Rounded.Movie,
                                    valueIconTint = recordCategoryColors(RecordCategory.Movies).accent,
                                    badgeBg = recordCategoryColors(RecordCategory.Movies).container,
                                    badgeColor = recordCategoryColors(RecordCategory.Movies).onContainer,
                                    onLongClick = { isEditMode = true }
                                )
                            }
                            "movies_avg_rating" -> {
                                MetricCard(
                                    title = stringResource(R.string.movies_stats_avg_rating),
                                    value = avgRating,
                                    valueIcon = Icons.Rounded.Star,
                                    valueIconTint = ratingColors().onContainer,
                                    badgeText = "",
                                    note = stringResource(R.string.movies_stats_rewatch, rewatchFilms),
                                    badgeBg = ratingColors().container,
                                    badgeColor = ratingColors().onContainer,
                                    onLongClick = { isEditMode = true }
                                )
                            }
                        }
                    }
                }

                state.error?.let { err ->
                    item(key = "movies_error_banner", span = { GridItemSpan(maxLineSpan) }, contentType = "error_banner") {
                        ExpressiveErrorBanner(
                            message = err,
                            onRetry = { viewModel.loadData() },
                            modifier = expressiveItemModifier().padding(bottom = 8.dp)
                        )
                    }
                }

                // Search Bar & Filter Header
                stickyHeader(key = "movies_sticky_header", contentType = "sticky_header") {
                    ExpressiveFilterHeader {
                        ExpressiveSearchField(
                            value = state.searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            placeholder = stringResource(R.string.movies_search_placeholder),
                            clearContentDescription = stringResource(R.string.action_clear),
                            modifier = Modifier
                                .fillMaxWidth()
                                .filterHeaderItem()
                        )
                        ExpressiveFilterRow {
                            ExpressiveConnectedButtonGroup(
                                items = listOf("all", "letterboxd", "mdblist"),
                                selectedItem = when {
                                    state.onlyLetterboxd -> "letterboxd"
                                    state.onlyMdbList -> "mdblist"
                                    else -> "all"
                                },
                                onItemSelected = { key -> viewModel.setSource(key) },
                                labelProvider = { key ->
                                    when (key) {
                                        "letterboxd" -> stringResource(R.string.movies_badge_letterboxd)
                                        "mdblist" -> stringResource(R.string.movies_filter_mdblist)
                                        else -> stringResource(R.string.filter_all)
                                    }
                                },
                                iconProvider = { key, _ ->
                                    when (key) {
                                        "letterboxd" -> Icons.Rounded.Movie
                                        "mdblist" -> Icons.Rounded.VideoLibrary
                                        else -> null
                                    }
                                }
                            )
                        }
                        val hasActiveDate = state.selectedDateFilter != null || state.fromDateFilter != null
                        val hasActiveFilters = state.searchQuery.isNotEmpty() || hasActiveDate || state.onlyRewatches || state.onlyLetterboxd || state.onlyMdbList
                        ExpressiveFilterRow {
                            ExpressiveFilterChip(
                                selected = state.onlyRewatches,
                                onClick = { viewModel.toggleRewatches() },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.Repeat,
                                        contentDescription = null,
                                        modifier = Modifier.size(FilterChipDefaults.IconSize)
                                    )
                                },
                                label = { Text(text = stringResource(R.string.movies_filter_rewatches), style = MaterialTheme.typography.labelLarge) }
                            )
                            // Then sort, ahead of the clear chip so it keeps its place.
                            ExpressiveDropdownFilterChip(
                                options = listOf("date", "rating_desc", "rating_asc", "year", "title"),
                                selected = state.sortBy,
                                onSelect = { viewModel.setSortBy(it) },
                                optionLabel = { key ->
                                    when (key) {
                                        "date" -> stringResource(R.string.sort_date_watched)
                                        "rating_desc" -> stringResource(R.string.sort_rating_desc)
                                        "rating_asc" -> stringResource(R.string.sort_rating_asc)
                                        "year" -> stringResource(R.string.sort_release_year)
                                        "title" -> stringResource(R.string.sort_movie_title)
                                        else -> stringResource(R.string.action_sort)
                                    }
                                },
                                leadingIcon = Icons.AutoMirrored.Rounded.Sort,
                                isActive = false
                            )
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
                        }
                        if (hasActiveFilters) {
                            ExpressiveFilterResultCount(count = state.filteredFilms.size)
                        }
                    }
                }

                if ((state.isLoading || state.isFiltering) && state.filteredFilms.isEmpty()) {
                    if (!isRefreshing) {
                        item(key = "movies_loading", span = { GridItemSpan(maxLineSpan) }, contentType = "loading") {
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
                } else if (state.filteredFilms.isEmpty() && state.error == null) {
                    item(key = "movies_empty", span = { GridItemSpan(maxLineSpan) }, contentType = "empty") {
                        ExpressiveEmptyState(
                            title = stringResource(R.string.movies_empty),
                            icon = Icons.Rounded.Movie,
                            iconContainerColor = recordCategoryColors(RecordCategory.Movies).container,
                            iconContentColor = recordCategoryColors(RecordCategory.Movies).onContainer
                        )
                    }
                } else {
                    itemsIndexed(
                        items = state.filteredFilms,
                        key = { _, film -> film.film_key.ifBlank { "${film.displayTitle}_${film.year}_${film.safeWatchedDate}" } },
                        contentType = { _, _ -> "movie_row" }
                    ) { index, film ->
                        FilmRowItem(
                            film = film,
                            onClick = { selectedFilm = film },
                            // Grid cells on wide screens are standalone rows; phones get one segmented group.
                            index = if (isWideScreen) 0 else index,
                            count = if (isWideScreen) 1 else state.filteredFilms.size,
                            addLeadingGap = !isWideScreen,
                            modifier = expressiveItemModifier(),
                            selected = if (isTwoPane) selectedFilm == film else null
                        )
                    }
                }

                item(key = "movies_bottom_spacer", span = { GridItemSpan(maxLineSpan) }, contentType = "spacer") { Spacer(modifier = Modifier.height(20.dp)) }
            }
        }

        ExpressiveListDetailLayout(
            showDetailPane = isTwoPane,
            detailPane = {
                if (selectedFilm != null) {
                    FilmDetailPaneCard(
                        film = selectedFilm!!,
                        onClose = { selectedFilm = null }
                    )
                } else {
                    DetailPlaceholderPane(
                        icon = Icons.Rounded.Movie,
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
        selectedFilm?.let { film ->
            FilmDetailBottomSheet(
                film = film,
                onDismiss = { selectedFilm = null }
            )
        }
    }

    if (showCustomizationSheet) {
        StatsCustomizationBottomSheet(
            sheetTitle = stringResource(R.string.stats_customization_title),
            cards = statCardOrder.sort(moviesStatCards),
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
                    preferences.resetHiddenStatsCards(moviesStatCards.map { it.id })
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
fun FilmRowItem(
    film: FilmDto,
    index: Int = 0,
    count: Int = 1,
    modifier: Modifier = Modifier,
    selected: Boolean? = null,
    addLeadingGap: Boolean = false,
    onClick: () -> Unit = {}
) {
    ExpressiveListRow(
        index = index,
        count = count,
        modifier = modifier,
        selected = selected,
        addLeadingGap = addLeadingGap,
        onClick = onClick,
        verticalAlignment = Alignment.CenterVertically,
        leadingContent = {
            PosterThumbnail(imageUrl = film.image_url, fallbackIcon = Icons.Rounded.Movie, width = 36, height = 52)
        },
        trailingContent = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (film.safeRating > 0) {
                    RatingBadge(rating = film.safeRating)
                }

                val isMdbList = film.source.equals("mdblist", ignoreCase = true)
                val sourceLabel = if (isMdbList) "MDBList" else stringResource(R.string.movies_badge_letterboxd)
                val sourceColor = if (isMdbList) MaterialTheme.colorScheme.tertiary else recordCategoryColors(RecordCategory.Movies).accent

                Surface(
                    shape = M3Shapes.CornerFull,
                    color = sourceColor.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = sourceLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = sourceColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        },
        supportingContent = {
            Column {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (film.isShow) {
                        Surface(
                            shape = M3Shapes.CornerFull,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest
                        ) {
                            Text(
                                text = stringResource(R.string.movies_type_show),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.5.dp),
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                    val metaText = buildString {
                        if (film.safeYear > 0) append(film.safeYear)
                        if (film.safeWatchedDate.isNotBlank()) {
                            if (isNotEmpty()) append(" · ")
                            append(film.safeWatchedDate)
                        }
                    }
                    if (metaText.isNotBlank()) {
                        Text(
                            text = metaText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                    if (film.rewatch) {
                        Surface(
                            shape = M3Shapes.CornerFull,
                            color = recordCategoryColors(RecordCategory.Movies).container
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp),
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Repeat,
                                    contentDescription = null,
                                    modifier = Modifier.size(10.dp),
                                    tint = recordCategoryColors(RecordCategory.Movies).onContainer
                                )
                                Text(
                                    text = stringResource(R.string.movies_rewatch_badge),
                                    style = MaterialTheme.typography.labelSmallEmphasized,
                                    color = recordCategoryColors(RecordCategory.Movies).onContainer,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
                if (film.safeReview.isNotBlank()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "“${film.safeReview.take(120)}${if (film.safeReview.length > 120) "..." else ""}”",
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    ) {
        Text(
            text = film.displayTitle,
            style = MaterialTheme.typography.titleSmallEmphasized,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

