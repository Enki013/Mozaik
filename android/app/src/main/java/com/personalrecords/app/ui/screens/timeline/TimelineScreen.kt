package com.personalrecords.app.ui.screens.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.personalrecords.app.R
import com.personalrecords.app.core.network.model.ActivityItemDto
import com.personalrecords.app.core.theme.*
import com.personalrecords.app.ui.components.*

// Each day is one segmented group: header on top, sections and rows below, 2dp apart.
private val DayCardTopShape = M3Shapes.groupedListItem(index = 0, count = 2)
private val DayCardMiddleShape = M3Shapes.groupedListItem(index = 1, count = 3)
private val TimelineSourceKeys = listOf("all", "fatsecret", "health_connect", "music", "goodreads", "letterboxd", "notification")

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun TimelineScreen(
    viewModel: TimelineViewModel,
    scrollToTopFlow: kotlinx.coroutines.flow.Flow<Unit>? = null,
    refreshFlow: kotlinx.coroutines.flow.Flow<Unit>? = null,
    @Suppress("UNUSED_PARAMETER") onNavigateToNutrition: () -> Unit = {},
    @Suppress("UNUSED_PARAMETER") onNavigateToBooks: () -> Unit = {},
    @Suppress("UNUSED_PARAMETER") onNavigateToMovies: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showDatePicker by remember { mutableStateOf(false) }
    var selectedActivity by remember { mutableStateOf<ActivityItemDto?>(null) }
    val onActivityClick = remember { { item: ActivityItemDto -> selectedActivity = item } }

    if (showDatePicker) {
        ExpressiveDateFilterDialog(
            initialStartDate = state.selectedDate ?: state.fromDate,
            initialEndDate = state.toDate,
            onDismissRequest = { showDatePicker = false },
            onDateSelected = { date ->
                viewModel.setSpecificDate(date)
            },
            onDateRangeSelected = { start, end ->
                viewModel.setDateRange(start, end)
            },
            onClear = {
                viewModel.resetFilters()
            }
        )
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            ExpressivePageTopAppBar(
                title = stringResource(R.string.title_timeline),
                scrollBehavior = scrollBehavior
            )
        },
        contentWindowInsets = WindowInsets.statusBars
    ) { innerPadding ->
        var isRefreshing by remember { mutableStateOf(false) }
        LaunchedEffect(state.isLoading) {
            if (!state.isLoading) isRefreshing = false
        }

        if (scrollToTopFlow != null) {
            LaunchedEffect(scrollToTopFlow) {
                scrollToTopFlow.collect {
                    listState.expressiveScrollToTop()
                }
            }
        }

        if (refreshFlow != null) {
            LaunchedEffect(refreshFlow) {
                refreshFlow.collect {
                    isRefreshing = true
                    viewModel.loadData()
                    if (!listState.isScrollInProgress) {
                        listState.expressiveScrollToTop()
                    }
                }
            }
        }

        ExpressivePullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                viewModel.loadData()
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize(),
                contentPadding = PaddingValues(
                    start = FilterGutter,
                    end = FilterGutter,
                    top = 10.dp,
                    bottom = floatingBottomNavigationContentPadding()
                ),
                verticalArrangement = Arrangement.Top
            ) {
                state.error?.let { err ->
                    item(key = "timeline_error_banner", contentType = "error_banner") {
                        ExpressiveErrorBanner(
                            message = err,
                            onRetry = { viewModel.loadData() },
                            modifier = expressiveItemModifier().padding(bottom = 8.dp)
                        )
                    }
                }

                // Layer 1: Search stays pinned until the date controls reach it.
                // Same header layout as every list screen: search, single-choice source group, chip row.
                stickyHeader(key = "timeline_filters", contentType = "sticky_header") {
                    val todayIso = java.time.LocalDate.now().toString()
                    val isToday = state.selectedDate == todayIso && state.fromDate == null
                    val hasCustomDate = !isToday && (state.selectedDate != null || state.fromDate != null)
                    val hasActiveFilters = isToday || hasCustomDate || state.selectedDays != TimelineDefaultDays ||
                        state.selectedSource != "all" || state.searchQuery.isNotEmpty()
                    ExpressiveFilterHeader {
                        ExpressiveSearchField(
                            value = state.searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            placeholder = stringResource(R.string.timeline_search_placeholder),
                            clearContentDescription = stringResource(R.string.action_clear),
                            modifier = Modifier
                                .fillMaxWidth()
                                .filterHeaderItem()
                        )
                        ExpressiveFilterRow {
                            if (hasActiveFilters) {
                                ExpressiveClearFiltersChip(onClick = { viewModel.resetFilters() })
                            }
                            ExpressiveQuickDateFilters(
                                selectedDate = state.selectedDate,
                                fromDate = state.fromDate,
                                toDate = state.toDate,
                                selectedQuickRange = when {
                                    isToday -> 0
                                    hasCustomDate -> null
                                    else -> state.selectedDays
                                },
                                defaultQuickRange = TimelineDefaultDays,
                                onPickDate = { showDatePicker = true },
                                // Clearing a range or date returns to the default window, not a full reset.
                                onClearDate = { viewModel.setDays(TimelineDefaultDays) },
                                onQuickRange = { range ->
                                    if (range == 0) viewModel.setToday() else viewModel.setDays(range)
                                }
                            )
                            ExpressiveConnectedButtonGroup(
                                items = TimelineSourceKeys,
                                selectedItem = state.selectedSource,
                                onItemSelected = { key -> viewModel.setSource(key) },
                                labelProvider = { key ->
                                    when (key) {
                                        "fatsecret" -> stringResource(R.string.timeline_filter_nutrition)
                                        "health_connect" -> stringResource(R.string.timeline_filter_health)
                                        "music" -> stringResource(R.string.timeline_filter_music)
                                        "goodreads" -> stringResource(R.string.timeline_filter_books)
                                        "letterboxd" -> stringResource(R.string.timeline_filter_movies)
                                        "notification" -> stringResource(R.string.timeline_filter_notifications)
                                        else -> stringResource(R.string.timeline_filter_all)
                                    }
                                },
                                iconProvider = { key, _ ->
                                    when (key) {
                                        "fatsecret" -> Icons.Rounded.Restaurant
                                        "health_connect" -> Icons.Rounded.Favorite
                                        "music" -> Icons.Rounded.MusicNote
                                        "goodreads" -> Icons.AutoMirrored.Rounded.MenuBook
                                        "letterboxd" -> Icons.Rounded.Movie
                                        "notification" -> Icons.Rounded.Notifications
                                        else -> null
                                    }
                                }
                            )
                        }
                    }
                }

                // Active date indicator
                state.selectedDate?.let { date ->
                    item(key = "timeline_selected_date_badge", contentType = "selected_date") {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = M3Shapes.CornerSmall,
                            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CalendarMonth,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = stringResource(R.string.timeline_selected_date, date),
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    style = MaterialTheme.typography.labelMediumEmphasized
                                )
                            }
                        }
                    }
                }

                if ((state.isLoading || state.isFiltering) && state.flatEntries.isEmpty()) {
                    if (!isRefreshing) {
                        item(key = "loading_indicator", contentType = "loading") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(240.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                AppContainedLoadingIndicator(size = 44.dp)
                            }
                        }
                    }
                } else if (state.flatEntries.isEmpty() && state.error == null) {
                    item(key = "empty_state", contentType = "empty") {
                        ExpressiveEmptyState(
                            title = stringResource(R.string.timeline_empty_title),
                            message = stringResource(R.string.timeline_empty_desc),
                            icon = Icons.Rounded.SearchOff
                        )
                    }
                } else {
                    items(
                        items = state.flatEntries,
                        key = { entry -> entry.key },
                        contentType = { entry -> entry.contentType }
                    ) { entry ->
                        when (entry) {
                            is TimelineFlatEntry.DayHeader -> {
                                TimelineDayHeaderItem(
                                    entry = entry,
                                    modifier = expressiveItemModifier()
                                )
                            }
                            is TimelineFlatEntry.SectionHeader -> {
                                TimelineSectionHeaderItem(
                                    entry = entry,
                                    modifier = expressiveItemModifier()
                                )
                            }
                            is TimelineFlatEntry.ActivityRow -> {
                                TimelineActivityRowItem(
                                    entry = entry,
                                    onItemClick = onActivityClick,
                                    modifier = expressiveItemModifier()
                                )
                            }
                        }
                    }
                }

                item(key = "timeline_bottom_spacer", contentType = "spacer") {
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }
    }

    selectedActivity?.let { activity ->
        ActivityItemDetailBottomSheet(
            item = activity,
            onDismiss = { selectedActivity = null }
        )
    }
}

@Composable
private fun TimelineDayHeaderItem(
    entry: TimelineFlatEntry.DayHeader,
    modifier: Modifier = Modifier
) {
    val isOnlyFood = !entry.hasMultipleSections && entry.singleSource == "fatsecret"
    val shape = if (entry.isLastInDay) M3Shapes.CornerLarge else DayCardTopShape

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(if (entry.isLastInDay) Modifier.padding(bottom = 8.dp) else Modifier),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Build the label here: the server sends a Turkish "Bugün"/"Dün" label.
            val dayLabel = when (entry.date) {
                java.time.LocalDate.now().toString() -> stringResource(R.string.timeline_today)
                java.time.LocalDate.now().minusDays(1).toString() -> stringResource(R.string.timeline_yesterday)
                else -> ""
            }
            Column {
                Text(
                    text = dayLabel.ifBlank { entry.date },
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (dayLabel.isNotBlank()) {
                    Text(
                        text = entry.date,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (isOnlyFood && entry.totalCal != null && entry.totalCal != "0") {
                Surface(
                    shape = M3Shapes.CornerFull,
                    color = recordCategoryColors(RecordCategory.Nutrition).container
                ) {
                    Text(
                        text = "${entry.totalCal} kcal",
                        color = recordCategoryColors(RecordCategory.Nutrition).onContainer,
                        style = MaterialTheme.typography.labelSmallEmphasized,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            } else if (entry.hasMultipleSections) {
                Surface(
                    shape = M3Shapes.CornerFull,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Text(
                        text = stringResource(R.string.timeline_items_count, entry.totalItemCount),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            } else if (entry.singleSource != null && entry.singleSource != "fatsecret") {
                val badgeText: String
                val badgeBg: androidx.compose.ui.graphics.Color
                val badgeFg: androidx.compose.ui.graphics.Color
                when (entry.singleSource) {
                    "health_connect" -> {
                        badgeText = stringResource(R.string.timeline_count_health, entry.totalItemCount)
                        badgeBg = recordCategoryColors(RecordCategory.Health).container
                        badgeFg = recordCategoryColors(RecordCategory.Health).onContainer
                    }
                    "notification" -> {
                        badgeText = stringResource(R.string.timeline_count_notifications, entry.totalItemCount)
                        badgeBg = recordCategoryColors(RecordCategory.Notifications).container
                        badgeFg = recordCategoryColors(RecordCategory.Notifications).onContainer
                    }
                    "music" -> {
                        badgeText = stringResource(R.string.timeline_count_music, entry.totalItemCount)
                        badgeBg = recordCategoryColors(RecordCategory.Music).container
                        badgeFg = recordCategoryColors(RecordCategory.Music).onContainer
                    }
                    "letterboxd" -> {
                        badgeText = stringResource(R.string.timeline_count_films, entry.totalItemCount)
                        badgeBg = recordCategoryColors(RecordCategory.Movies).container
                        badgeFg = recordCategoryColors(RecordCategory.Movies).onContainer
                    }
                    "goodreads" -> {
                        badgeText = stringResource(R.string.timeline_count_books, entry.totalItemCount)
                        badgeBg = recordCategoryColors(RecordCategory.Books).container
                        badgeFg = recordCategoryColors(RecordCategory.Books).onContainer
                    }
                    else -> {
                        badgeText = stringResource(R.string.timeline_items_count, entry.totalItemCount)
                        badgeBg = MaterialTheme.colorScheme.surfaceContainerHigh
                        badgeFg = MaterialTheme.colorScheme.onSurfaceVariant
                    }
                }
                Surface(
                    shape = M3Shapes.CornerFull,
                    color = badgeBg
                ) {
                    Text(
                        text = badgeText,
                        color = badgeFg,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TimelineSectionHeaderItem(
    entry: TimelineFlatEntry.SectionHeader,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .padding(top = M3ListMetrics.SegmentGap)
            .fillMaxWidth(),
        shape = DayCardMiddleShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val icon: androidx.compose.ui.graphics.vector.ImageVector
                val title: String
                val color: androidx.compose.ui.graphics.Color
                when (entry.source) {
                    "fatsecret" -> {
                        icon = Icons.Rounded.Restaurant
                        title = stringResource(R.string.timeline_filter_nutrition)
                        color = recordCategoryColors(RecordCategory.Nutrition).accent
                    }
                    "health_connect" -> {
                        icon = Icons.Rounded.Favorite
                        title = stringResource(R.string.title_health_connect)
                        color = recordCategoryColors(RecordCategory.Health).accent
                    }
                    "notification" -> {
                        icon = Icons.Rounded.Notifications
                        title = stringResource(R.string.timeline_filter_notifications)
                        color = recordCategoryColors(RecordCategory.Notifications).accent
                    }
                    "music" -> {
                        icon = Icons.Rounded.MusicNote
                        title = stringResource(R.string.timeline_filter_music)
                        color = recordCategoryColors(RecordCategory.Music).accent
                    }
                    "letterboxd" -> {
                        icon = Icons.Rounded.Movie
                        title = stringResource(R.string.timeline_filter_movies)
                        color = recordCategoryColors(RecordCategory.Movies).accent
                    }
                    "goodreads" -> {
                        icon = Icons.AutoMirrored.Rounded.MenuBook
                        title = stringResource(R.string.timeline_filter_books)
                        color = recordCategoryColors(RecordCategory.Books).accent
                    }
                    else -> {
                        icon = Icons.Rounded.Bookmark
                        title = stringResource(R.string.common_other)
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelMediumEmphasized,
                        color = color
                    )
                }

                if (entry.source == "fatsecret" && entry.totalCal != null && entry.totalCal != "0") {
                    Surface(
                        shape = M3Shapes.CornerFull,
                        color = recordCategoryColors(RecordCategory.Nutrition).container
                    ) {
                        Text(
                            text = "${entry.totalCal} kcal",
                            color = recordCategoryColors(RecordCategory.Nutrition).onContainer,
                            style = MaterialTheme.typography.labelSmallEmphasized,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                } else if (entry.source == "health_connect") {
                    Surface(
                        shape = M3Shapes.CornerFull,
                        color = recordCategoryColors(RecordCategory.Health).container
                    ) {
                        Text(
                            text = stringResource(R.string.timeline_count_health, entry.itemCount),
                            color = recordCategoryColors(RecordCategory.Health).onContainer,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                } else if (entry.source == "notification") {
                    Surface(
                        shape = M3Shapes.CornerFull,
                        color = recordCategoryColors(RecordCategory.Notifications).container
                    ) {
                        Text(
                            text = stringResource(R.string.timeline_count_notifications, entry.itemCount),
                            color = recordCategoryColors(RecordCategory.Notifications).onContainer,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                } else if (entry.source == "letterboxd") {
                    Surface(
                        shape = M3Shapes.CornerFull,
                        color = recordCategoryColors(RecordCategory.Movies).container
                    ) {
                        Text(
                            text = stringResource(R.string.timeline_count_films, entry.itemCount),
                            color = recordCategoryColors(RecordCategory.Movies).onContainer,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                } else if (entry.source == "goodreads") {
                    Surface(
                        shape = M3Shapes.CornerFull,
                        color = recordCategoryColors(RecordCategory.Books).container
                    ) {
                        Text(
                            text = stringResource(R.string.timeline_count_books, entry.itemCount),
                            color = recordCategoryColors(RecordCategory.Books).onContainer,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                } else if (entry.source == "music") {
                    Surface(
                        shape = M3Shapes.CornerFull,
                        color = recordCategoryColors(RecordCategory.Music).container
                    ) {
                        Text(
                            text = stringResource(R.string.timeline_count_music, entry.itemCount),
                            color = recordCategoryColors(RecordCategory.Music).onContainer,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineActivityRowItem(
    entry: TimelineFlatEntry.ActivityRow,
    onItemClick: (ActivityItemDto) -> Unit,
    modifier: Modifier = Modifier
) {
    // Each day is one segmented group: the day header is the first segment, so an activity is a
    // middle segment, or the bottom one when it closes the day.
    TimelineItemCard(
        item = entry.item,
        rating = entry.rating,
        caloriesText = entry.caloriesText,
        timeText = entry.timeText,
        onClick = { onItemClick(entry.item) },
        index = 1,
        count = if (entry.isLastInDay) 2 else 3,
        addLeadingGap = true,
        modifier = modifier.then(if (entry.isLastInDay) Modifier.padding(bottom = 8.dp) else Modifier)
    )
}

@Composable
fun TimelineItemCard(
    item: ActivityItemDto,
    index: Int = 0,
    count: Int = 1,
    modifier: Modifier = Modifier,
    addLeadingGap: Boolean = false,
    rating: Double? = null,
    caloriesText: String? = null,
    timeText: String? = null,
    onClick: () -> Unit = {}
) {

    val fallbackIcon = when (item.source) {
        "fatsecret" -> Icons.Rounded.Restaurant
        "health_connect" -> Icons.Rounded.Favorite
        "music" -> Icons.Rounded.MusicNote
        "goodreads" -> Icons.AutoMirrored.Rounded.MenuBook
        "letterboxd" -> Icons.Rounded.Movie
        "notification" -> Icons.Rounded.Notifications
        else -> Icons.Rounded.Bookmark
    }

    
    ExpressiveListRow(
        index = index,
        count = count,
        modifier = modifier,
        addLeadingGap = addLeadingGap,
        onClick = onClick,
        verticalAlignment = Alignment.CenterVertically,
        leadingContent = {
            when (item.source) {
                "notification" -> {
                    AppLetterAvatar(appName = notificationAppName(item), size = 38.dp)
                }
                "fatsecret" -> {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(M3Shapes.CornerSmall)
                            .background(recordCategoryColors(RecordCategory.Nutrition).container),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Restaurant,
                            contentDescription = null,
                            tint = recordCategoryColors(RecordCategory.Nutrition).onContainer,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                "health_connect" -> {
                    val hcIcon = when (item.type) {
                        "sleep" -> Icons.Rounded.Bedtime
                        "exercise" -> Icons.AutoMirrored.Rounded.DirectionsRun
                        "weight" -> Icons.Rounded.MonitorWeight
                        else -> Icons.Rounded.Favorite
                    }
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(M3Shapes.CornerSmall)
                            .background(recordCategoryColors(RecordCategory.Health).container),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = hcIcon,
                            contentDescription = null,
                            tint = recordCategoryColors(RecordCategory.Health).onContainer,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                "music" -> {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(M3Shapes.CornerSmall)
                            .background(recordCategoryColors(RecordCategory.Music).container),
                        contentAlignment = Alignment.Center
                    ) {
                        if (item.image_url?.isNotBlank() == true) {
                            AsyncImage(
                                model = item.image_url,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.MusicNote,
                                contentDescription = null,
                                tint = recordCategoryColors(RecordCategory.Music).onContainer,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                else -> {
                    PosterThumbnail(
                        imageUrl = item.image_url,
                        fallbackIcon = fallbackIcon,
                        width = 34,
                        height = 48
                    )
                }
            }
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                if (rating != null && rating > 0) {
                    RatingBadge(rating = rating)
                }
                if (item.rewatch) {
                    Surface(
                        shape = M3Shapes.CornerFull,
                        color = recordCategoryColors(RecordCategory.Movies).container
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Repeat,
                                contentDescription = null,
                                tint = recordCategoryColors(RecordCategory.Movies).onContainer,
                                modifier = Modifier.size(10.dp)
                            )
                            Text(
                                text = "Tekrar",
                                color = recordCategoryColors(RecordCategory.Movies).onContainer,
                                style = MaterialTheme.typography.labelSmallEmphasized,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
                if (!caloriesText.isNullOrBlank()) {
                    Text(
                        text = caloriesText,
                        style = MaterialTheme.typography.labelSmallEmphasized,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        softWrap = false
                    )
                }
                if (!timeText.isNullOrBlank()) {
                    Text(
                        text = timeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        },
        supportingContent = {
            Column {
                if (item.subtitle.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = item.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    ) {
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodyMediumEmphasized,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
