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
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personalrecords.app.R
import com.personalrecords.app.core.network.model.ActivityItemDto
import com.personalrecords.app.core.theme.*
import com.personalrecords.app.ui.components.*

private val DayCardTopShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
private val DayCardMiddleShape = androidx.compose.ui.graphics.RectangleShape
private val DayCardBottomShape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
private val TimelineDayOptions = listOf(7, 14, 30, 90)
private val TimelineSourceKeys = listOf("all", "fatsecret", "health_connect", "goodreads", "letterboxd", "notification")

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

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.statusBars
    ) { innerPadding ->
        val listState = androidx.compose.foundation.lazy.rememberLazyListState()
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
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                contentPadding = PaddingValues(top = 10.dp, bottom = 144.dp),
                verticalArrangement = Arrangement.Top
            ) {
                // MD3 Search Bar (scrollable)
                item(key = "timeline_search_bar", contentType = "search_bar") {
                    OutlinedTextField(
                        value = state.searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        placeholder = {
                            Text(
                                text = stringResource(R.string.timeline_search_placeholder),
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
                        shape = CircleShape,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        ),
                        singleLine = true
                    )
                }

                // Date Range & Quick Chips Bar (scrollable)
                item(key = "timeline_date_chips", contentType = "date_chips") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val activeDayOption = if (state.selectedDate == null && state.fromDate == null) state.selectedDays else -1
                        ExpressiveConnectedButtonGroup(
                            items = TimelineDayOptions,
                            selectedItem = activeDayOption,
                            onItemSelected = { days -> viewModel.setDays(days) },
                            labelProvider = { days -> "${days}G" }
                        )

                        val hasCustomDate = state.selectedDate != null || state.fromDate != null
                        val dateLabel = formatDateRangeChipLabel(
                            singleDate = state.selectedDate,
                            fromDate = state.fromDate,
                            toDate = state.toDate,
                            defaultText = stringResource(R.string.nutrition_pick_date)
                        )

                        ExpressiveFilterChip(
                            selected = hasCustomDate,
                            onClick = { showDatePicker = true },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (hasCustomDate) Icons.Default.DateRange else Icons.Default.CalendarMonth,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            trailingIcon = if (hasCustomDate) {
                                {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clickable { viewModel.resetFilters() }
                                    )
                                }
                            } else null,
                            label = { Text(dateLabel, style = MaterialTheme.typography.labelMedium) }
                        )

                        ExpressiveAssistChip(
                            onClick = { viewModel.setToday() },
                            label = { Text(stringResource(R.string.timeline_today), style = MaterialTheme.typography.labelMedium) },
                            colors = borderlessAssistChipColors()
                        )

                        if (state.selectedDate != null || state.fromDate != null || state.selectedDays != 14) {
                            ExpressiveAssistChip(
                                onClick = { viewModel.resetFilters() },
                                label = {
                                    Text(
                                        text = stringResource(R.string.timeline_filter_reset),
                                        style = MaterialTheme.typography.labelMediumEmphasized,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                },
                                colors = borderlessAssistChipColors(
                                    labelColor = MaterialTheme.colorScheme.error
                                )
                            )
                        }
                    }
                }

                // Source Filter Chips Bar (Sticky Header)
                stickyHeader(key = "timeline_source_sticky_header", contentType = "sticky_header") {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ExpressiveConnectedButtonGroup(
                                items = TimelineSourceKeys,
                                selectedItem = state.selectedSource,
                                onItemSelected = { key ->
                                    if (state.selectedSource != key) {
                                        viewModel.setSource(key)
                                    }
                                },
                                labelProvider = { key ->
                                    when (key) {
                                        "fatsecret" -> stringResource(R.string.timeline_filter_nutrition)
                                        "health_connect" -> stringResource(R.string.timeline_filter_health)
                                        "goodreads" -> stringResource(R.string.timeline_filter_books)
                                        "letterboxd" -> stringResource(R.string.timeline_filter_movies)
                                        "notification" -> stringResource(R.string.timeline_filter_notifications)
                                        else -> stringResource(R.string.timeline_filter_all)
                                    }
                                },
                                iconProvider = { key, _ ->
                                    when (key) {
                                        "fatsecret" -> Icons.Default.Restaurant
                                        "health_connect" -> Icons.Default.Favorite
                                        "goodreads" -> Icons.AutoMirrored.Filled.MenuBook
                                        "letterboxd" -> Icons.Default.Movie
                                        "notification" -> Icons.Default.Notifications
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
                                    imageVector = Icons.Default.CalendarMonth,
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
                                ContainedLoadingIndicator(size = 44.dp)
                            }
                        }
                    }
                } else if (state.flatEntries.isEmpty()) {
                    item(key = "empty_state", contentType = "empty") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.timeline_empty_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
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
            Column {
                Text(
                    text = entry.label.ifBlank { entry.date },
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (entry.label.isNotBlank() && entry.label != entry.date) {
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
                    color = domainNutritionContainer()
                ) {
                    Text(
                        text = "${entry.totalCal} kcal",
                        color = domainNutritionOnContainer(),
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
                        badgeBg = domainHealthContainer()
                        badgeFg = domainHealthOnContainer()
                    }
                    "notification" -> {
                        badgeText = stringResource(R.string.timeline_count_notifications, entry.totalItemCount)
                        badgeBg = MaterialTheme.colorScheme.secondaryContainer
                        badgeFg = MaterialTheme.colorScheme.onSecondaryContainer
                    }
                    "letterboxd" -> {
                        badgeText = stringResource(R.string.timeline_count_films, entry.totalItemCount)
                        badgeBg = domainCinemaContainer()
                        badgeFg = domainCinemaOnContainer()
                    }
                    "goodreads" -> {
                        badgeText = stringResource(R.string.timeline_count_books, entry.totalItemCount)
                        badgeBg = domainBooksContainer()
                        badgeFg = domainBooksOnContainer()
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
        modifier = modifier.fillMaxWidth(),
        shape = DayCardMiddleShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                modifier = Modifier.padding(horizontal = 12.dp)
            )
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
                        icon = Icons.Default.Restaurant
                        title = stringResource(R.string.timeline_filter_nutrition)
                        color = domainNutritionColor()
                    }
                    "health_connect" -> {
                        icon = Icons.Default.Favorite
                        title = stringResource(R.string.title_health_connect)
                        color = domainHealthColor()
                    }
                    "notification" -> {
                        icon = Icons.Default.Notifications
                        title = stringResource(R.string.timeline_filter_notifications)
                        color = MaterialTheme.colorScheme.secondary
                    }
                    "letterboxd" -> {
                        icon = Icons.Default.Movie
                        title = stringResource(R.string.timeline_filter_movies)
                        color = domainCinemaColor()
                    }
                    "goodreads" -> {
                        icon = Icons.AutoMirrored.Filled.MenuBook
                        title = stringResource(R.string.timeline_filter_books)
                        color = domainBooksColor()
                    }
                    else -> {
                        icon = Icons.Default.Bookmark
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
                        color = domainNutritionContainer()
                    ) {
                        Text(
                            text = "${entry.totalCal} kcal",
                            color = domainNutritionOnContainer(),
                            style = MaterialTheme.typography.labelSmallEmphasized,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                } else if (entry.source == "health_connect") {
                    Surface(
                        shape = M3Shapes.CornerFull,
                        color = domainHealthContainer()
                    ) {
                        Text(
                            text = stringResource(R.string.timeline_count_health, entry.itemCount),
                            color = domainHealthOnContainer(),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                } else if (entry.source == "notification") {
                    Surface(
                        shape = M3Shapes.CornerFull,
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = stringResource(R.string.timeline_count_notifications, entry.itemCount),
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                } else if (entry.source == "letterboxd") {
                    Surface(
                        shape = M3Shapes.CornerFull,
                        color = domainCinemaContainer()
                    ) {
                        Text(
                            text = stringResource(R.string.timeline_count_films, entry.itemCount),
                            color = domainCinemaOnContainer(),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                } else if (entry.source == "goodreads") {
                    Surface(
                        shape = M3Shapes.CornerFull,
                        color = domainBooksContainer()
                    ) {
                        Text(
                            text = stringResource(R.string.timeline_count_books, entry.itemCount),
                            color = domainBooksOnContainer(),
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
    val shape = if (entry.isLastInDay) DayCardBottomShape else DayCardMiddleShape

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(if (entry.isLastInDay) Modifier.padding(bottom = 8.dp) else Modifier),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                modifier = Modifier.padding(horizontal = 12.dp)
            )
            TimelineItemCard(
                item = entry.item,
                rating = entry.rating,
                caloriesText = entry.caloriesText,
                timeText = entry.timeText,
                onClick = { onItemClick(entry.item) }
            )
        }
    }
}

@Composable
fun TimelineItemCard(
    item: ActivityItemDto,
    rating: Double? = null,
    caloriesText: String? = null,
    timeText: String? = null,
    onClick: () -> Unit = {}
) {
    val fallbackIcon = when (item.source) {
        "fatsecret" -> Icons.Default.Restaurant
        "health_connect" -> Icons.Default.Favorite
        "goodreads" -> Icons.AutoMirrored.Filled.MenuBook
        "letterboxd" -> Icons.Default.Movie
        "notification" -> Icons.Default.Notifications
        else -> Icons.Default.Bookmark
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Leading Icon / Thumbnail (optical roundness: 8.dp CornerSmall inside 16.dp CornerLarge card)
        when (item.source) {
            "notification" -> {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(M3Shapes.CornerFull)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            "fatsecret" -> {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(M3Shapes.CornerSmall)
                        .background(domainNutritionContainer()),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Restaurant,
                        contentDescription = null,
                        tint = domainNutritionOnContainer(),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            "health_connect" -> {
                val hcIcon = when (item.type) {
                    "sleep" -> Icons.Default.Bedtime
                    "exercise" -> Icons.AutoMirrored.Filled.DirectionsRun
                    "weight" -> Icons.Default.MonitorWeight
                    else -> Icons.Default.Favorite
                }
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(M3Shapes.CornerSmall)
                        .background(domainHealthContainer()),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = hcIcon,
                        contentDescription = null,
                        tint = domainHealthOnContainer(),
                        modifier = Modifier.size(18.dp)
                    )
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

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMediumEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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

        Spacer(modifier = Modifier.width(8.dp))

        // Right details (rating, rewatch, calorie, time)
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            if (rating != null && rating > 0) {
                RatingBadge(rating = rating)
            }
            if (item.rewatch) {
                Surface(
                    shape = M3Shapes.CornerFull,
                    color = domainCinemaContainer()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Repeat,
                            contentDescription = null,
                            tint = domainCinemaOnContainer(),
                            modifier = Modifier.size(10.dp)
                        )
                        Text(
                            text = "Tekrar",
                            color = domainCinemaOnContainer(),
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
    }
}
