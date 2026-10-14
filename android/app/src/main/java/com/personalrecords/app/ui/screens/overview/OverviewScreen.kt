package com.personalrecords.app.ui.screens.overview

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.personalrecords.app.R
import com.personalrecords.app.core.theme.*
import com.personalrecords.app.ui.components.*

import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun OverviewScreen(
    viewModel: OverviewViewModel,
    scrollToTopFlow: kotlinx.coroutines.flow.Flow<Unit>? = null,
    refreshFlow: kotlinx.coroutines.flow.Flow<Unit>? = null,
    onNavigateToTimeline: () -> Unit,
    onNavigateToNutrition: () -> Unit = {},
    onNavigateToBooks: () -> Unit = {},
    onNavigateToMovies: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedActivity by remember { mutableStateOf<com.personalrecords.app.core.network.model.ActivityItemDto?>(null) }

    LaunchedEffect(state.syncMessage) {
        state.syncMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSyncMessage()
        }
    }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.statusBars,
        snackbarHost = { SnackbarHost(snackbarHostState, modifier = Modifier.navigationBarsPadding().padding(bottom = 96.dp)) }
    ) { innerPadding ->
        val listState = rememberLazyListState()
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
            onRefresh = { isRefreshing = true; viewModel.loadData() },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (state.isLoading && state.dashboard == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    ContainedLoadingIndicator(size = 48.dp)
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 144.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                // Top 4 Metrics Grid
                item(key = "overview_metrics", contentType = "metrics") {
                    val counts = state.dashboard?.counts ?: emptyMap()
                    val today = state.dashboard?.today ?: emptyMap()
                    val todayCal = today["fatsecret_calories"]?.toString()?.replace("\"", "")
                        ?: today["calories"]?.toString()?.replace("\"", "") ?: "0"
                    val todayEntries = today["fatsecret_entry_count"]?.toString()?.replace("\"", "")
                        ?: today["entries_count"]?.toString()?.replace("\"", "") ?: "0"

                    val booksCount = counts["goodreads_books"] ?: counts["books"] ?: 0
                    val filmsCount = counts["letterboxd_films"] ?: counts["films"] ?: 0
                    val foodsCount = counts["fatsecret_food_entries"] ?: counts["foods"] ?: 0

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            MetricCard(
                                title = stringResource(R.string.overview_metric_today_calories),
                                value = stringResource(R.string.overview_metric_kcal, todayCal),
                                badgeText = "FatSecret",
                                note = stringResource(R.string.overview_metric_meals_count, todayEntries),
                                modifier = Modifier.weight(1f),
                                badgeBg = domainNutritionContainer(),
                                badgeColor = domainNutritionOnContainer(),
                                onClick = onNavigateToNutrition
                            )
                            MetricCard(
                                title = stringResource(R.string.overview_metric_library),
                                value = stringResource(R.string.overview_metric_books_count, booksCount),
                                badgeText = "Goodreads",
                                note = stringResource(R.string.overview_metric_books_note),
                                modifier = Modifier.weight(1f),
                                badgeBg = domainBooksContainer(),
                                badgeColor = domainBooksOnContainer(),
                                onClick = onNavigateToBooks
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            MetricCard(
                                title = stringResource(R.string.overview_metric_watched),
                                value = stringResource(R.string.overview_metric_films_count, filmsCount),
                                badgeText = "Letterboxd",
                                note = stringResource(R.string.overview_metric_films_note),
                                modifier = Modifier.weight(1f),
                                badgeBg = domainCinemaContainer(),
                                badgeColor = domainCinemaOnContainer(),
                                onClick = onNavigateToMovies
                            )
                            MetricCard(
                                title = stringResource(R.string.overview_metric_food_log),
                                value = stringResource(R.string.overview_metric_foods_count, foodsCount),
                                badgeText = stringResource(R.string.badge_data),
                                note = stringResource(R.string.overview_metric_foods_note),
                                modifier = Modifier.weight(1f),
                                badgeBg = domainNutritionContainer(),
                                badgeColor = domainNutritionOnContainer(),
                                onClick = onNavigateToNutrition
                            )
                        }
                    }
                }

                // Entegrasyonlar Card
                item(key = "overview_integrations", contentType = "integrations") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = M3Shapes.CornerLarge,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        border = null
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = stringResource(R.string.overview_integrations_title),
                                style = MaterialTheme.typography.titleMediumEmphasized,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.overview_integrations_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            // FatSecret Row
                            SyncRow(
                                title = "FatSecret",
                                subtitle = stringResource(R.string.overview_integration_fatsecret_desc),
                                isSyncing = state.isSyncing == "fatsecret",
                                onSync = { viewModel.syncService("fatsecret") }
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 8.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
                            )

                            // Goodreads Row
                            SyncRow(
                                title = "Goodreads RSS",
                                subtitle = stringResource(R.string.overview_integration_goodreads_desc),
                                isSyncing = state.isSyncing == "goodreads",
                                onSync = { viewModel.syncService("goodreads") }
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 8.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
                            )

                            // Letterboxd Row
                            SyncRow(
                                title = "Letterboxd RSS",
                                subtitle = stringResource(R.string.overview_integration_letterboxd_desc),
                                isSyncing = state.isSyncing == "letterboxd",
                                onSync = { viewModel.syncService("letterboxd") }
                            )
                        }
                    }
                }

                // Son Aktiviteler Grouped Card (M3 Pattern aligned with RecordsScreen)
                item(key = "overview_recent_activities", contentType = "recent_activities") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = M3Shapes.CornerLarge,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        border = null
                    ) {
                        Column {
                            // Header Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = stringResource(R.string.overview_recent_activities_title),
                                        style = MaterialTheme.typography.titleMediumEmphasized,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = stringResource(R.string.overview_recent_activities_subtitle),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                TextButton(
                                    onClick = onNavigateToTimeline,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.action_see_all),
                                            style = MaterialTheme.typography.labelLargeEmphasized,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )

                            // Items List inside the Card
                            if (state.isLoading && state.recentActivities.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    ContainedLoadingIndicator(size = 36.dp)
                                }
                            } else if (state.recentActivities.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = stringResource(R.string.overview_no_activities),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                state.recentActivities.forEachIndexed { index, item ->
                                    key("${item.source}_${item.id.ifBlank { "${item.date}_${item.title}" }}") {
                                        if (index > 0) {
                                            HorizontalDivider(
                                                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                                                modifier = Modifier.padding(horizontal = 12.dp)
                                            )
                                        }
                                        ActivityRowItem(
                                            item = item,
                                            onClick = { selectedActivity = item }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item(key = "overview_bottom_spacer", contentType = "spacer") { Spacer(modifier = Modifier.height(16.dp)) }
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
fun SyncRow(
    title: String,
    subtitle: String,
    isSyncing: Boolean,
    onSync: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMediumEmphasized,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        val syncBtnInteraction = remember { MutableInteractionSource() }
        FilledTonalButton(
            onClick = onSync,
            enabled = !isSyncing,
            interactionSource = syncBtnInteraction,
            shape = rememberExpressiveButtonShape(
                interactionSource = syncBtnInteraction,
                restingCorner = M3CornerRadius.Full,
                pressedCorner = M3CornerRadius.ExtraSmall
            ),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            modifier = Modifier
                .height(32.dp)
                .expressivePressScale(syncBtnInteraction)
        ) {
            if (isSyncing) {
                ExpressiveLoadingIndicator(
                    size = 15.dp,
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            } else {
                Icon(
                    Icons.Default.Sync,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.action_sync),
                    style = MaterialTheme.typography.labelMediumEmphasized
                )
            }
        }
    }
}

@Composable
fun ActivityRowItem(
    item: com.personalrecords.app.core.network.model.ActivityItemDto,
    onClick: () -> Unit
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
        // Leading Media / Icon Avatar (optical roundness: 8.dp CornerSmall inside 16.dp CornerLarge card)
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

        // Center Details
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMediumEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${item.subtitle} · ${item.date}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Trailing Badge (Calories, Rating)
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            val rating = item.details["rating"]?.toString()?.toDoubleOrNull()
                ?: item.details["my_rating"]?.toString()?.toDoubleOrNull()
            if (rating != null && rating > 0) {
                RatingBadge(rating = rating)
            }
            if (item.source == "fatsecret") {
                val cal = item.details["calories"]?.toString()?.replace("\"", "")
                if (cal != null && cal != "0" && cal != "null") {
                    Text(
                        text = "$cal kcal",
                        style = MaterialTheme.typography.labelSmallEmphasized,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}
