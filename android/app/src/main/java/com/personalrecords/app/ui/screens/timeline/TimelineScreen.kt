package com.personalrecords.app.ui.screens.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun TimelineScreen(
    viewModel: TimelineViewModel
) {
    val state by viewModel.uiState.collectAsState()
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply {
                                timeZone = TimeZone.getTimeZone("UTC")
                            }
                            val formatted = formatter.format(Date(millis))
                            viewModel.setSpecificDate(formatted)
                        }
                        showDatePicker = false
                    }
                ) {
                    Text(stringResource(R.string.action_ok), style = MaterialTheme.typography.labelLarge)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.action_cancel), style = MaterialTheme.typography.labelLarge)
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        var isRefreshing by remember { mutableStateOf(false) }
        LaunchedEffect(state.isLoading) {
            if (!state.isLoading) isRefreshing = false
        }

        androidx.compose.material3.pulltorefresh.PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                viewModel.loadData()
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val filteredGroups = remember(state.dayGroups, state.selectedSource, state.searchQuery) {
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

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                contentPadding = PaddingValues(top = 10.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // MD3 Search Bar (scrollable)
                item {
                    OutlinedTextField(
                        value = state.searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        modifier = Modifier.fillMaxWidth(),
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
                                IconButton(onClick = { viewModel.setSearchQuery("") }) {
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
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf(7 to "7G", 14 to "14G", 30 to "30G", 90 to "90G").forEach { (days, label) ->
                            val isSelected = state.selectedDays == days && state.selectedDate == null && state.fromDate == null
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.setDays(days) },
                                label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                                shape = MaterialTheme.shapes.small,
                                border = null,
                                colors = borderlessFilterChipColors()
                            )
                        }

                        AssistChip(
                            onClick = { showDatePicker = true },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.CalendarMonth,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = { Text(stringResource(R.string.nutrition_pick_date), style = MaterialTheme.typography.labelMedium) },
                            shape = MaterialTheme.shapes.small,
                            border = null,
                            colors = borderlessAssistChipColors()
                        )

                        AssistChip(
                            onClick = { viewModel.setToday() },
                            label = { Text(stringResource(R.string.timeline_today), style = MaterialTheme.typography.labelMedium) },
                            shape = MaterialTheme.shapes.small,
                            border = null,
                            colors = borderlessAssistChipColors()
                        )

                        if (state.selectedDate != null || state.fromDate != null || state.selectedDays != 14) {
                            AssistChip(
                                onClick = { viewModel.resetFilters() },
                                label = {
                                    Text(
                                        text = stringResource(R.string.timeline_filter_reset),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                },
                                shape = MaterialTheme.shapes.small,
                                border = null,
                                colors = borderlessAssistChipColors(
                                    labelColor = MaterialTheme.colorScheme.error
                                )
                            )
                        }
                    }
                }

                // Source Filter Chips Bar (Sticky Header)
                stickyHeader {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                Triple("all", stringResource(R.string.timeline_filter_all), null),
                                Triple("fatsecret", stringResource(R.string.timeline_filter_nutrition), Icons.Default.Restaurant),
                                Triple("goodreads", stringResource(R.string.timeline_filter_books), Icons.AutoMirrored.Filled.MenuBook),
                                Triple("letterboxd", stringResource(R.string.timeline_filter_movies), Icons.Default.Movie),
                                Triple("notification", stringResource(R.string.timeline_filter_notifications), Icons.Default.Notifications)
                            ).forEach { (key, label, icon) ->
                                val isSelected = state.selectedSource == key
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { viewModel.setSource(key) },
                                    leadingIcon = icon?.let {
                                        {
                                            Icon(
                                                imageVector = it,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    },
                                    label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                                    shape = MaterialTheme.shapes.small,
                                    border = null,
                                    colors = borderlessFilterChipColors()
                                )
                            }
                        }
                    }
                }

                // Active date indicator
                state.selectedDate?.let { date ->
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.padding(vertical = 4.dp)
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
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                if (state.isLoading && !isRefreshing) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(240.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                } else if (filteredGroups.isEmpty()) {
                    item {
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
                    items(filteredGroups) { group ->
                        DayGroupCard(group = group)
                    }
                }

                item { Spacer(modifier = Modifier.height(20.dp)) }
            }
        }
    }
}

@Composable
fun DayGroupCard(group: ActivityDayGroupDto) {
    val sections = remember(group.items) {
        val map = LinkedHashMap<String, MutableList<ActivityItemDto>>()
        group.items.forEach { item ->
            map.getOrPut(item.source) { mutableListOf() }.add(item)
        }
        map
    }

    val totalCal = group.summary["total_calories"]?.toString()?.replace("\"", "")
    val hasMultipleSections = sections.size > 1
    val singleSource = sections.keys.firstOrNull()
    val isOnlyFood = !hasMultipleSections && singleSource == "fatsecret"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = null
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            // Day Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = group.label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = group.date,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Header Badges:
                // Calories are ONLY shown if this day only contains food.
                // Mixed days show overall activity count.
                // Specific single domains (notifications, books, movies) show their own specific count badges.
                if (isOnlyFood && totalCal != null && totalCal != "0") {
                    Surface(
                        shape = CircleShape,
                        color = domainNutritionContainer()
                    ) {
                        Text(
                            text = "$totalCal kcal",
                            color = domainNutritionOnContainer(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                } else if (hasMultipleSections) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Text(
                            text = stringResource(R.string.timeline_items_count, group.items.size),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                } else if (singleSource != null && singleSource != "fatsecret") {
                    val (badgeText, badgeBg, badgeFg) = when (singleSource) {
                        "notification" -> Triple(
                            stringResource(R.string.timeline_count_notifications, group.items.size),
                            MaterialTheme.colorScheme.secondaryContainer,
                            MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        "letterboxd" -> Triple(
                            stringResource(R.string.timeline_count_films, group.items.size),
                            CinemaAccentContainer,
                            CinemaAccentOnContainer
                        )
                        "goodreads" -> Triple(
                            stringResource(R.string.timeline_count_books, group.items.size),
                            MaterialTheme.colorScheme.tertiaryContainer,
                            MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        else -> Triple(
                            stringResource(R.string.timeline_items_count, group.items.size),
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                            MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Surface(
                        shape = CircleShape,
                        color = badgeBg
                    ) {
                        Text(
                            text = badgeText,
                            color = badgeFg,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Sub-sections by domain
            var isFirstSection = true
            sections.forEach { (source, items) ->
                if (!isFirstSection) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                isFirstSection = false

                // Sub-section Header (shown when there are multiple domains on the same day)
                if (hasMultipleSections) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val (icon, title, color) = when (source) {
                            "fatsecret" -> Triple(Icons.Default.Restaurant, stringResource(R.string.timeline_filter_nutrition), MaterialTheme.colorScheme.primary)
                            "notification" -> Triple(Icons.Default.Notifications, stringResource(R.string.timeline_filter_notifications), MaterialTheme.colorScheme.secondary)
                            "letterboxd" -> Triple(Icons.Default.Movie, stringResource(R.string.timeline_filter_movies), CinemaAccent)
                            "goodreads" -> Triple(Icons.AutoMirrored.Filled.MenuBook, stringResource(R.string.timeline_filter_books), BooksAccent)
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
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = color
                            )
                        }

                        // Category specific summary badge
                        if (source == "fatsecret" && totalCal != null && totalCal != "0") {
                            Surface(
                                shape = CircleShape,
                                color = domainNutritionContainer()
                            ) {
                                Text(
                                    text = "$totalCal kcal",
                                    color = domainNutritionOnContainer(),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        } else if (source == "notification") {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = stringResource(R.string.timeline_count_notifications, items.size),
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        } else if (source == "letterboxd") {
                            Surface(
                                shape = CircleShape,
                                color = CinemaAccentContainer
                            ) {
                                Text(
                                    text = stringResource(R.string.timeline_count_films, items.size),
                                    color = CinemaAccentOnContainer,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        } else if (source == "goodreads") {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.tertiaryContainer
                            ) {
                                Text(
                                    text = stringResource(R.string.timeline_count_books, items.size),
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // Section Items
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items.forEach { item ->
                        TimelineItemCard(item = item)
                    }
                }
            }
        }
    }
}

@Composable
fun TimelineItemCard(item: ActivityItemDto) {
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
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Leading Icon / Thumbnail
        when (item.source) {
            "notification" -> {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
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
                        .size(36.dp)
                        .clip(MaterialTheme.shapes.small)
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
                    width = 32,
                    height = 46
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = item.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        // Right details (rating, rewatch, meal badge, calorie, time)
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            val rating = item.details["rating"]?.toString()?.toDoubleOrNull()
                ?: item.details["my_rating"]?.toString()?.toDoubleOrNull()
            if (rating != null && rating > 0) {
                RatingBadge(rating = rating)
            }
            if (item.rewatch) {
                Surface(
                    shape = CircleShape,
                    color = CinemaAccentContainer
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Repeat,
                            contentDescription = null,
                            tint = CinemaAccentOnContainer,
                            modifier = Modifier.size(10.dp)
                        )
                        Text(
                            text = "Tekrar",
                            color = CinemaAccentOnContainer,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
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
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
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
