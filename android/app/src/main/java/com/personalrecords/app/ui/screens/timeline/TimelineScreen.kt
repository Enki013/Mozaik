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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.personalrecords.app.R
import com.personalrecords.app.core.network.model.ActivityDayGroupDto
import com.personalrecords.app.core.network.model.ActivityItemDto
import com.personalrecords.app.core.theme.*
import com.personalrecords.app.ui.components.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private sealed interface TimelineFlatEntry {
    val key: String
    val contentType: String

    data class DayHeader(
        val date: String,
        val label: String,
        val totalCal: String?,
        val hasMultipleSections: Boolean,
        val singleSource: String?,
        val totalItemCount: Int,
        val isLastInDay: Boolean
    ) : TimelineFlatEntry {
        override val key: String = "day_hdr_$date"
        override val contentType: String = "day_header"
    }

    data class SectionHeader(
        val date: String,
        val source: String,
        val itemCount: Int,
        val totalCal: String?
    ) : TimelineFlatEntry {
        override val key: String = "sec_hdr_${date}_$source"
        override val contentType: String = "section_header"
    }

    data class ActivityRow(
        val date: String,
        val item: ActivityItemDto,
        val indexInDay: Int,
        val isLastInDay: Boolean
    ) : TimelineFlatEntry {
        override val key: String = "act_${date}_${item.source}_${item.id}_$indexInDay"
        override val contentType: String = "activity_item"
    }
}

private fun buildTimelineFlatEntries(groups: List<ActivityDayGroupDto>): List<TimelineFlatEntry> {
    if (groups.isEmpty()) return emptyList()
    val result = ArrayList<TimelineFlatEntry>(groups.size * 6)
    for (group in groups) {
        if (group.items.isEmpty()) continue
        val sections = LinkedHashMap<String, MutableList<ActivityItemDto>>()
        for (item in group.items) {
            sections.getOrPut(item.source) { mutableListOf() }.add(item)
        }
        val totalCal = group.summary["total_calories"]?.toString()?.replace("\"", "")
        val hasMultipleSections = sections.size > 1
        val singleSource = sections.keys.firstOrNull()
        val totalCount = group.items.size

        result.add(
            TimelineFlatEntry.DayHeader(
                date = group.date,
                label = group.label,
                totalCal = totalCal,
                hasMultipleSections = hasMultipleSections,
                singleSource = singleSource,
                totalItemCount = totalCount,
                isLastInDay = false
            )
        )

        var indexInDay = 0
        for ((source, sectionItems) in sections) {
            if (hasMultipleSections) {
                result.add(
                    TimelineFlatEntry.SectionHeader(
                        date = group.date,
                        source = source,
                        itemCount = sectionItems.size,
                        totalCal = totalCal
                    )
                )
            }
            for (item in sectionItems) {
                val isLastInDay = indexInDay == totalCount - 1
                result.add(
                    TimelineFlatEntry.ActivityRow(
                        date = group.date,
                        item = item,
                        indexInDay = indexInDay,
                        isLastInDay = isLastInDay
                    )
                )
                indexInDay++
            }
        }
    }
    return result
}

private val DayCardTopShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
private val DayCardMiddleShape = RoundedCornerShape(0.dp)
private val DayCardBottomShape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun TimelineScreen(
    viewModel: TimelineViewModel,
    scrollToTopFlow: kotlinx.coroutines.flow.Flow<Unit>? = null,
    refreshFlow: kotlinx.coroutines.flow.Flow<Unit>? = null,
    onNavigateToNutrition: () -> Unit = {},
    onNavigateToBooks: () -> Unit = {},
    onNavigateToMovies: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    var showDatePicker by remember { mutableStateOf(false) }
    var selectedActivity by remember { mutableStateOf<ActivityItemDto?>(null) }
    var flatEntries by remember { mutableStateOf<List<TimelineFlatEntry>>(emptyList()) }
    var isFiltering by remember { mutableStateOf(false) }
    var isNavTransitionSettled by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(220L)
        isNavTransitionSettled = true
    }

    LaunchedEffect(state.dayGroups, state.selectedSource, state.searchQuery) {
        if (state.dayGroups.isEmpty()) {
            flatEntries = emptyList()
            isFiltering = false
            return@LaunchedEffect
        }
        if (isFiltering) {
            delay(160L)
        }
        val computed = withContext(Dispatchers.Default) {
            val filteredGroups = if (state.selectedSource == "all" && state.searchQuery.isBlank()) {
                state.dayGroups
            } else {
                state.dayGroups.mapNotNull { group ->
                    val filteredItems = group.items.filter { item ->
                        val matchesSource = state.selectedSource == "all" || item.source == state.selectedSource
                        val matchesSearch = state.searchQuery.isBlank() ||
                            item.title.contains(state.searchQuery, ignoreCase = true) ||
                            item.subtitle.contains(state.searchQuery, ignoreCase = true)
                        matchesSource && matchesSearch
                    }
                    if (filteredItems.isNotEmpty()) group.copy(items = filteredItems) else null
                }
            }
            buildTimelineFlatEntries(filteredGroups)
        }
        flatEntries = computed
        isFiltering = false
    }

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

        LaunchedEffect(scrollToTopFlow) {
            scrollToTopFlow?.collect {
                listState.expressiveScrollToTop()
            }
        }

        LaunchedEffect(refreshFlow) {
            refreshFlow?.collect {
                listState.expressiveScrollToTop()
                isRefreshing = true
                viewModel.loadData()
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
                            items = listOf(7, 14, 30, 90),
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
                            val sourceItems = listOf(
                                Triple("all", stringResource(R.string.timeline_filter_all), null),
                                Triple("fatsecret", stringResource(R.string.timeline_filter_nutrition), Icons.Default.Restaurant),
                                Triple("goodreads", stringResource(R.string.timeline_filter_books), Icons.AutoMirrored.Filled.MenuBook),
                                Triple("letterboxd", stringResource(R.string.timeline_filter_movies), Icons.Default.Movie),
                                Triple("notification", stringResource(R.string.timeline_filter_notifications), Icons.Default.Notifications)
                            )
                            val currentSourceItem = sourceItems.firstOrNull { it.first == state.selectedSource } ?: sourceItems.first()
                            ExpressiveConnectedButtonGroup(
                                items = sourceItems,
                                selectedItem = currentSourceItem,
                                onItemSelected = { (key, _, _) ->
                                    if (state.selectedSource != key) {
                                        isFiltering = true
                                        viewModel.setSource(key)
                                    }
                                },
                                labelProvider = { it.second },
                                iconProvider = { item, _ -> item.third }
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

                if ((state.isLoading && !isRefreshing) || !isNavTransitionSettled || isFiltering) {
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
                } else if (flatEntries.isEmpty()) {
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
                        items = flatEntries,
                        key = { entry -> entry.key },
                        contentType = { entry -> entry.contentType }
                    ) { entry ->
                        when (entry) {
                            is TimelineFlatEntry.DayHeader -> {
                                TimelineDayHeaderItem(entry = entry)
                            }
                            is TimelineFlatEntry.SectionHeader -> {
                                TimelineSectionHeaderItem(entry = entry)
                            }
                            is TimelineFlatEntry.ActivityRow -> {
                                TimelineActivityRowItem(
                                    entry = entry,
                                    onItemClick = { selectedActivity = it }
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
    entry: TimelineFlatEntry.DayHeader
) {
    val isOnlyFood = !entry.hasMultipleSections && entry.singleSource == "fatsecret"
    val shape = if (entry.isLastInDay) M3Shapes.CornerLarge else DayCardTopShape

    Surface(
        modifier = Modifier
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
                val (badgeText, badgeBg, badgeFg) = when (entry.singleSource) {
                    "notification" -> Triple(
                        stringResource(R.string.timeline_count_notifications, entry.totalItemCount),
                        MaterialTheme.colorScheme.secondaryContainer,
                        MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    "letterboxd" -> Triple(
                        stringResource(R.string.timeline_count_films, entry.totalItemCount),
                        domainCinemaContainer(),
                        domainCinemaOnContainer()
                    )
                    "goodreads" -> Triple(
                        stringResource(R.string.timeline_count_books, entry.totalItemCount),
                        domainBooksContainer(),
                        domainBooksOnContainer()
                    )
                    else -> Triple(
                        stringResource(R.string.timeline_items_count, entry.totalItemCount),
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                        MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
    entry: TimelineFlatEntry.SectionHeader
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
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
                val (icon, title, color) = when (entry.source) {
                    "fatsecret" -> Triple(Icons.Default.Restaurant, stringResource(R.string.timeline_filter_nutrition), domainNutritionColor())
                    "notification" -> Triple(Icons.Default.Notifications, stringResource(R.string.timeline_filter_notifications), MaterialTheme.colorScheme.secondary)
                    "letterboxd" -> Triple(Icons.Default.Movie, stringResource(R.string.timeline_filter_movies), domainCinemaColor())
                    "goodreads" -> Triple(Icons.AutoMirrored.Filled.MenuBook, stringResource(R.string.timeline_filter_books), domainBooksColor())
                    else -> Triple(Icons.Default.Bookmark, stringResource(R.string.common_other), MaterialTheme.colorScheme.onSurfaceVariant)
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
    onItemClick: (ActivityItemDto) -> Unit
) {
    val shape = if (entry.isLastInDay) DayCardBottomShape else DayCardMiddleShape

    Surface(
        modifier = Modifier
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
                onClick = { onItemClick(entry.item) }
            )
        }
    }
}

@Composable
fun TimelineItemCard(
    item: ActivityItemDto,
    onClick: () -> Unit = {}
) {
    val fallbackIcon = when (item.source) {
        "fatsecret" -> Icons.Default.Restaurant
        "goodreads" -> Icons.AutoMirrored.Filled.MenuBook
        "letterboxd" -> Icons.Default.Movie
        "notification" -> Icons.Default.Notifications
        else -> Icons.Default.Bookmark
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .expressiveBounceClick(onClick = onClick)
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
            val rating = item.details["rating"]?.toString()?.toDoubleOrNull()
                ?: item.details["my_rating"]?.toString()?.toDoubleOrNull()
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
            val cal = item.details["calories"]?.toString()?.replace("\"", "")
            if (item.source == "fatsecret" && cal != null && cal != "0" && cal != "null") {
                Text(
                    text = "$cal kcal",
                    style = MaterialTheme.typography.labelSmallEmphasized,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    softWrap = false
                )
            }
            // For notification, show post time if available
            if (item.source == "notification") {
                val timeStr = item.time ?: item.details["post_time"]?.toString()?.replace("\"", "")?.let {
                    if (it.length >= 16) it.substring(11, 16) else null
                }
                if (!timeStr.isNullOrBlank()) {
                    Text(
                        text = timeStr,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}
