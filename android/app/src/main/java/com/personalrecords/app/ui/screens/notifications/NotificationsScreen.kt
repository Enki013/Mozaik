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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personalrecords.app.R
import com.personalrecords.app.core.network.model.NotificationDto
import com.personalrecords.app.core.theme.*
import com.personalrecords.app.ui.components.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun NotificationsScreen(
    viewModel: NotificationsViewModel,
    onNavigateBack: (() -> Unit)? = null
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var selectedNotification by remember { mutableStateOf<NotificationDto?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var filteredList by remember { mutableStateOf<List<NotificationDto>>(emptyList()) }
    var isFiltering by remember { mutableStateOf(false) }

    LaunchedEffect(
        state.notifications,
        state.selectedApp,
        state.searchQuery,
        state.selectedDateFilter,
        state.fromDateFilter,
        state.toDateFilter
    ) {
        if (state.notifications.isEmpty()) {
            filteredList = emptyList()
            isFiltering = false
            return@LaunchedEffect
        }
        if (isFiltering) {
            delay(160)
        }
        val computed = withContext(Dispatchers.Default) {
            state.notifications.filter { item ->
                val matchesApp = state.selectedApp == null || item.displayAppName == state.selectedApp
                val matchesSearch = state.searchQuery.isBlank() ||
                    item.displayTitle.contains(state.searchQuery, ignoreCase = true) ||
                    item.displayBody.contains(state.searchQuery, ignoreCase = true) ||
                    item.displayAppName.contains(state.searchQuery, ignoreCase = true)
                val notifDate = item.formattedDate.ifBlank { item.created_at.take(10) }
                val matchesDate = when {
                    state.selectedDateFilter != null -> notifDate.startsWith(state.selectedDateFilter!!)
                    state.fromDateFilter != null && state.toDateFilter != null ->
                        notifDate.isNotBlank() && notifDate >= state.fromDateFilter!! && notifDate <= state.toDateFilter!!
                    state.fromDateFilter != null -> notifDate.isNotBlank() && notifDate >= state.fromDateFilter!!
                    state.toDateFilter != null -> notifDate.isNotBlank() && notifDate <= state.toDateFilter!!
                    else -> true
                }
                matchesApp && matchesSearch && matchesDate
            }
        }
        filteredList = computed
        isFiltering = false
    }

    if (showDatePicker) {
        ExpressiveDateFilterDialog(
            initialStartDate = state.selectedDateFilter ?: state.fromDateFilter,
            initialEndDate = state.toDateFilter,
            onDismissRequest = { showDatePicker = false },
            onDateSelected = { date ->
                isFiltering = true
                viewModel.setDateFilter(date)
            },
            onDateRangeSelected = { start, end ->
                isFiltering = true
                viewModel.setDateRangeFilter(start, end)
            },
            onClear = {
                isFiltering = true
                viewModel.clearDateFilter()
            }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
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
                actions = {},
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
            val appList = remember(state.notifications) {
                state.notifications
                    .map { it.displayAppName }
                    .distinct()
                    .sorted()
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                contentPadding = PaddingValues(top = 10.dp, bottom = 144.dp),
                verticalArrangement = Arrangement.Top
            ) {
                // Stats Row
                item {
                    val total = state.notifications.size
                    val todayISO = remember { LocalDate.now().toString() }
                    val todayCount = state.notifications.count { it.formattedDate == todayISO }
                    val appCount = appList.size

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        MetricCard(
                            title = stringResource(R.string.notifications_stats_total),
                            value = "$total",
                            badgeText = stringResource(R.string.notifications_badge_notification),
                            note = stringResource(R.string.notifications_stats_apps_count, appCount),
                            modifier = Modifier.weight(1f)
                        )
                        MetricCard(
                            title = stringResource(R.string.notifications_stats_today),
                            value = "$todayCount",
                            badgeText = stringResource(R.string.badge_daily),
                            note = stringResource(R.string.notifications_stats_active_stream),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Search Bar & Filter Header (Sticky Header)
                stickyHeader {
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
                                                        isFiltering = true
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

                                ExpressiveFilterChip(
                                    selected = state.selectedApp == null,
                                    onClick = {
                                        if (state.selectedApp != null) {
                                            isFiltering = true
                                            viewModel.setAppFilter(null)
                                        }
                                    },
                                    label = { Text(stringResource(R.string.filter_all), style = MaterialTheme.typography.labelMedium) }
                                )

                                appList.forEach { appName ->
                                    val isSelected = state.selectedApp == appName
                                    ExpressiveFilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            isFiltering = true
                                            viewModel.setAppFilter(if (isSelected) null else appName)
                                        },
                                        label = { Text(appName, style = MaterialTheme.typography.labelMedium) }
                                    )
                                }

                                if (state.selectedApp != null || state.searchQuery.isNotEmpty() || hasActiveDate) {
                                    ExpressiveAssistChip(
                                        onClick = {
                                            isFiltering = true
                                            viewModel.setAppFilter(null)
                                            viewModel.setSearchQuery("")
                                            viewModel.clearDateFilter()
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
                if ((state.isLoading && state.notifications.isEmpty()) || isFiltering) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            ContainedLoadingIndicator(size = 44.dp)
                        }
                    }
                } else if (filteredList.isEmpty()) {
                    item {
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
                        items = filteredList,
                        key = { index, notif -> "${notif.id}_${notif.created_at}_$index" }
                    ) { index, notif ->
                        LazyCardRowContainer(
                            index = index,
                            totalCount = filteredList.size
                        ) {
                            NotificationRowItem(
                                notification = notif,
                                onClick = { selectedNotification = notif }
                            )
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(20.dp)) }
            }
        }
    }

    selectedNotification?.let { notif ->
        NotificationDetailBottomSheet(
            notif = notif,
            onDismiss = { selectedNotification = null }
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
