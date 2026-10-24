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
    val configuration = LocalConfiguration.current
    val isWideScreen = configuration.screenWidthDp >= 600
    val isTwoPane = configuration.screenWidthDp >= 840
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

    val notificationsStatCards = remember {
        listOf(
            StatCardItem(
                id = "notifications_total",
                title = "Toplam Bildirim",
                description = "Yakalanan tüm bildirimler ve uygulama sayısı",
                icon = Icons.Default.Notifications
            ),
            StatCardItem(
                id = "notifications_today",
                title = "Bugünkü Akış",
                description = "Bugün gelen bildirimlerin akışı",
                icon = Icons.Default.Today
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

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets.statusBars,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.title_notifications),
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
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
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
            val appList = state.appList
            val gridState = rememberLazyGridState()
            val listContent: @Composable () -> Unit = {
                LazyVerticalGrid(
                    columns = if (isWideScreen) GridCells.Adaptive(minSize = 340.dp) else GridCells.Fixed(1),
                    state = gridState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    contentPadding = PaddingValues(top = 10.dp, bottom = if (isWideScreen) 32.dp else 144.dp),
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

                    if (isTotalVisible || isTodayVisible) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (isTotalVisible) {
                                StatCardContainer(
                                    cardId = "notifications_total",
                                    isEditMode = isEditMode,
                                    onHideCard = {
                                        coroutineScope.launch {
                                            preferences.toggleStatCardVisibility("notifications_total", false)
                                        }
                                    },
                                    index = 0,
                                    modifier = Modifier.weight(1f)
                                ) {
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
                            }
                            if (isTodayVisible) {
                                StatCardContainer(
                                    cardId = "notifications_today",
                                    isEditMode = isEditMode,
                                    onHideCard = {
                                        coroutineScope.launch {
                                            preferences.toggleStatCardVisibility("notifications_today", false)
                                        }
                                    },
                                    index = 1,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    MetricCard(
                                        title = stringResource(R.string.notifications_stats_today),
                                        value = "$todayCount",
                                        badgeText = stringResource(R.string.badge_daily),
                                        note = stringResource(R.string.notifications_stats_active_stream),
                                        valueIcon = Icons.Default.Today,
                                        valueIconTint = MaterialTheme.colorScheme.secondary,
                                        badgeBg = MaterialTheme.colorScheme.secondaryContainer,
                                        badgeColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                        onLongClick = { isEditMode = true }
                                    )
                                }
                            }
                        }
                    }
                }

                // Search Bar & Filter Header
                item(key = "notifications_sticky_header", span = { GridItemSpan(maxLineSpan) }, contentType = "sticky_header") {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedTextField(
                                value = state.searchQuery,
                                onValueChange = { viewModel.setSearchQuery(it) },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = {
                                    Text(
                                        text = stringResource(R.string.notifications_search_placeholder),
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

                                val appGroupItems = remember(appList) { listOf<String?>(null) + appList }
                                ExpressiveConnectedButtonGroup(
                                    items = appGroupItems,
                                    selectedItem = state.selectedApp,
                                    onItemSelected = { appName ->
                                        if (state.selectedApp == appName && appName != null) {
                                            viewModel.setAppFilter(null)
                                        } else {
                                            viewModel.setAppFilter(appName)
                                        }
                                    },
                                    labelProvider = { appName ->
                                        appName ?: stringResource(R.string.filter_all)
                                    },
                                    iconProvider = { appName, _ ->
                                        if (appName == null) Icons.Default.Apps else Icons.Default.Notifications
                                    }
                                )

                                if (state.selectedApp != null || state.searchQuery.isNotEmpty() || hasActiveDate) {
                                    ExpressiveAssistChip(
                                        onClick = {
                                            viewModel.resetFilters()
                                        },
                                        label = {
                                            Text(
                                                text = stringResource(R.string.action_clear),
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
                                ContainedLoadingIndicator(size = 44.dp)
                            }
                        }
                    }
                } else if (state.filteredNotifications.isEmpty()) {
                    item(key = "notifications_empty", span = { GridItemSpan(maxLineSpan) }, contentType = "empty") {
                        if (state.notifications.isEmpty()) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                ),
                                shape = M3Shapes.CornerLarge
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Surface(
                                        shape = M3Shapes.CornerFull,
                                        color = MaterialTheme.colorScheme.tertiaryContainer,
                                        modifier = Modifier.size(56.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.Notifications,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = stringResource(R.string.notifications_empty),
                                        style = MaterialTheme.typography.titleMediumEmphasized
                                    )
                                    Text(
                                        text = stringResource(R.string.notifications_empty_guide),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(160.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.notifications_search_empty),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
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
                                shape = M3Shapes.CornerMedium,
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
                }
            }
        } else {
            listContent()
        }
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
            cards = notificationsStatCards,
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
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Notifications,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
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
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = notification.displayAppName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
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
