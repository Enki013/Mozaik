package com.personalrecords.app.ui.screens.notifications

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personalrecords.app.PersonalRecordsApp
import com.personalrecords.app.R
import com.personalrecords.app.core.network.model.NotificationDto
import com.personalrecords.app.core.theme.*
import com.personalrecords.app.ui.components.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun NotificationsScreen(
    viewModel: NotificationsViewModel,
    onNavigateBack: (() -> Unit)? = null
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val preferences = remember { PersonalRecordsApp.instance.preferences }
    val hiddenCardIds by preferences.hiddenStatsCards.collectAsStateWithLifecycle(initialValue = emptySet())
    val coroutineScope = rememberCoroutineScope()
    var isEditMode by remember { mutableStateOf(false) }
    var showCustomizationSheet by remember { mutableStateOf(false) }
    var targetCardId by remember { mutableStateOf<String?>(null) }
    var selectedNotification by remember { mutableStateOf<NotificationDto?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    val adaptiveLayout = currentAdaptiveLayout()
    val isWideScreen = adaptiveLayout.isWide
    val isTwoPane = adaptiveLayout.showsDetailPane
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

    val notificationsTotalTitle = stringResource(R.string.stats_card_notifications_total_title)
    val notificationsTotalDesc = stringResource(R.string.stats_card_notifications_total_desc)
    val notificationsTodayTitle = stringResource(R.string.stats_card_notifications_today_title)
    val notificationsTodayDesc = stringResource(R.string.stats_card_notifications_today_desc)
    val notificationsStatCards = remember(notificationsTotalTitle, notificationsTotalDesc, notificationsTodayTitle, notificationsTodayDesc) {
        listOf(
            StatCardItem(
                id = "notifications_total",
                title = notificationsTotalTitle,
                description = notificationsTotalDesc,
                icon = Icons.Default.Notifications
            ),
            StatCardItem(
                id = "notifications_today",
                title = notificationsTodayTitle,
                description = notificationsTodayDesc,
                icon = Icons.Default.Today
            )
        )
    }
    val statCardOrder = rememberStatCardOrder("notifications", notificationsStatCards.map { it.id })

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

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets.statusBars,
        topBar = {
            ExpressivePageTopAppBar(
                title = stringResource(R.string.title_notifications),
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
                        hasHiddenCards = notificationsStatCards.any { it.id in hiddenCardIds },
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
            val appList = state.appList
            val gridState = rememberLazyGridState()
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(if (isWideScreen) 8.dp else 0.dp)
                ) {
                // Stats Row
                item(key = "notifications_stats_row", span = { GridItemSpan(maxLineSpan) }, contentType = "stats_row") {
                    val total = state.notifications.size
                    val todayCount = state.todayCount
                    val appCount = appList.size

                    val isTotalVisible = "notifications_total" !in hiddenCardIds
                    val isTodayVisible = "notifications_today" !in hiddenCardIds

                    LaunchedEffect(isTotalVisible, isTodayVisible) {
                        if (!isTotalVisible && !isTodayVisible) {
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
                            "notifications_total" -> {
                                MetricCard(
                                    title = stringResource(R.string.notifications_stats_total),
                                    value = "$total",
                                    badgeText = stringResource(R.string.notifications_badge_notification),
                                    note = stringResource(R.string.notifications_stats_apps_count, appCount),
                                    valueIcon = Icons.Default.Notifications,
                                    valueIconTint = MaterialTheme.colorScheme.primary,
                                    badgeBg = MaterialTheme.colorScheme.primaryContainer,
                                    badgeColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    onLongClick = { isEditMode = true }
                                )
                            }
                            "notifications_today" -> {
                                MetricCard(
                                    title = stringResource(R.string.notifications_stats_today),
                                    value = "$todayCount",
                                    badgeText = stringResource(R.string.badge_daily),
                                    note = stringResource(R.string.notifications_stats_active_stream),
                                    valueIcon = Icons.Default.Today,
                                    valueIconTint = MaterialTheme.colorScheme.secondary,
                                    badgeBg = recordCategoryColors(RecordCategory.Notifications).container,
                                    badgeColor = recordCategoryColors(RecordCategory.Notifications).onContainer,
                                    onLongClick = { isEditMode = true }
                                )
                            }
                        }
                    }
                }

                state.error?.let { err ->
                    item(key = "notifications_error_banner", span = { GridItemSpan(maxLineSpan) }, contentType = "error_banner") {
                        ExpressiveErrorBanner(
                            message = err,
                            onRetry = { viewModel.loadData() },
                            modifier = expressiveItemModifier().padding(bottom = 8.dp)
                        )
                    }
                }

                // Search Bar & Filter Header
                stickyHeader(key = "notifications_sticky_header", contentType = "sticky_header") {
                    ExpressiveFilterHeader {
                        ExpressiveSearchField(
                            value = state.searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            placeholder = stringResource(R.string.notifications_search_placeholder),
                            clearContentDescription = stringResource(R.string.action_clear),
                            modifier = Modifier
                                .fillMaxWidth()
                                .filterHeaderItem()
                        )
                        val hasActiveDate = state.selectedDateFilter != null || state.fromDateFilter != null
                        val hasActiveFilters = state.searchQuery.isNotEmpty() || hasActiveDate || state.selectedApp != null
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
                                options = remember(appList) { listOf<String?>(null) + appList },
                                selected = state.selectedApp,
                                onSelect = { viewModel.setAppFilter(it) },
                                optionLabel = { app -> app ?: stringResource(R.string.filter_all) },
                                label = state.selectedApp ?: stringResource(R.string.filter_app),
                                leadingIcon = Icons.Default.Apps,
                                isActive = state.selectedApp != null
                            )
                            ExpressiveDropdownFilterChip(
                                options = listOf("date_desc", "date_asc"),
                                selected = state.sortBy,
                                onSelect = { viewModel.setSortBy(it) },
                                optionLabel = { key ->
                                    when (key) {
                                        "date_desc" -> stringResource(R.string.sort_newest)
                                        "date_asc" -> stringResource(R.string.sort_oldest)
                                        else -> stringResource(R.string.action_sort)
                                    }
                                },
                                leadingIcon = Icons.AutoMirrored.Filled.Sort,
                                isActive = false
                            )
                        }
                        if (hasActiveFilters) {
                            ExpressiveFilterResultCount(count = state.filteredNotifications.size)
                        }
                    }
                }

                // Loading or Empty State
                if ((state.isLoading || state.isFiltering) && state.filteredNotifications.isEmpty()) {
                    if (!isRefreshing) {
                        item(key = "notifications_loading", span = { GridItemSpan(maxLineSpan) }, contentType = "loading") {
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
                } else if (state.filteredNotifications.isEmpty() && state.error == null) {
                    item(key = "notifications_empty", span = { GridItemSpan(maxLineSpan) }, contentType = "empty") {
                        if (state.notifications.isEmpty()) {
                            ExpressiveEmptyState(
                                title = stringResource(R.string.notifications_empty),
                                message = stringResource(R.string.notifications_empty_guide),
                                icon = Icons.Default.Notifications,
                                iconContainerColor = recordCategoryColors(RecordCategory.Notifications).container,
                                iconContentColor = recordCategoryColors(RecordCategory.Notifications).onContainer
                            )
                        } else {
                            ExpressiveEmptyState(
                                title = stringResource(R.string.notifications_search_empty),
                                icon = Icons.Default.SearchOff
                            )
                        }
                    }
                } else {
                    itemsIndexed(
                        items = state.filteredNotifications,
                        key = { _, notif -> notif.id.ifBlank { "${notif.created_at}_${notif.app_name}_${notif.title}" } },
                        contentType = { _, _ -> "notification_row" }
                    ) { index, notif ->
                        if (isWideScreen) {
                            Surface(
                                modifier = expressiveItemModifier().fillMaxWidth(),
                                shape = M3Shapes.CornerLarge,
                                color = MaterialTheme.colorScheme.surfaceContainerLow
                            ) {
                                NotificationRowItem(
                                    notification = notif,
                                    onClick = { selectedNotification = notif }
                                )
                            }
                        } else {
                            LazyCardRowContainer(
                                index = index,
                                totalCount = state.filteredNotifications.size,
                                modifier = expressiveItemModifier()
                            ) {
                                NotificationRowItem(
                                    notification = notif,
                                    onClick = { selectedNotification = notif }
                                )
                            }
                        }
                    }
                }

                item(key = "notifications_bottom_spacer", span = { GridItemSpan(maxLineSpan) }, contentType = "spacer") { Spacer(modifier = Modifier.height(20.dp)) }
            }
        }

        ExpressiveListDetailLayout(
            showDetailPane = isTwoPane,
            detailPane = {
                if (selectedNotification != null) {
                    NotificationDetailPaneCard(
                        notif = selectedNotification!!,
                        onClose = { selectedNotification = null }
                    )
                } else {
                    DetailPlaceholderPane(
                        icon = Icons.Default.Notifications,
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
        selectedNotification?.let { notif ->
            NotificationDetailBottomSheet(
                notif = notif,
                onDismiss = { selectedNotification = null }
            )
        }
    }

    if (showCustomizationSheet) {
        StatsCustomizationBottomSheet(
            sheetTitle = stringResource(R.string.stats_customization_title),
            cards = statCardOrder.sort(notificationsStatCards),
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
                    preferences.resetHiddenStatsCards(notificationsStatCards.map { it.id })
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
fun NotificationRowItem(
    notification: NotificationDto,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .expressiveBounceClick(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // M3 Circular Notification Avatar Box (40dp)
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(M3Shapes.CornerFull)
                .background(recordCategoryColors(RecordCategory.Notifications).container),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Notifications,
                contentDescription = null,
                tint = recordCategoryColors(RecordCategory.Notifications).onContainer,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = notification.displayTitle,
                style = MaterialTheme.typography.titleSmallEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = notification.displayBody.ifBlank { notification.sub_text.ifBlank { stringResource(R.string.notifications_no_content) } },
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
                    color = recordCategoryColors(RecordCategory.Notifications).container
                ) {
                    Text(
                        text = notification.displayAppName,
                        style = MaterialTheme.typography.labelSmall,
                        color = recordCategoryColors(RecordCategory.Notifications).onContainer,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                        maxLines = 1,
                        softWrap = false
                    )
                }
                if (notification.category.isNotBlank()) {
                    Surface(
                        shape = M3Shapes.CornerFull,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest
                    ) {
                        Text(
                            text = notification.category,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
                Text(
                    text = "${notification.formattedDate} · ${notification.formattedTime}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
fun NotificationCard(
    notification: NotificationDto,
    onClick: () -> Unit
) {
    NotificationRowItem(notification = notification, onClick = onClick)
}
