package com.personalrecords.app.ui.screens.health

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.personalrecords.app.R
import com.personalrecords.app.core.network.model.HealthRecordDto
import com.personalrecords.app.core.theme.*
import com.personalrecords.app.ui.components.*
import java.text.NumberFormat
import java.util.Locale
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay

private val HealthCategoryKeys = listOf("all", "activity", "sleep", "vitals", "exercise", "body")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HealthConnectScreen(
    viewModel: HealthConnectViewModel,
    onNavigateBack: (() -> Unit)? = null
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedRecord by remember { mutableStateOf<HealthRecordDto?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }

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
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.statusBars,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.title_health_connect),
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
        var isTransitionSettled by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            delay(260L)
            isTransitionSettled = true
        }

        if (!isTransitionSettled) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                ContainedLoadingIndicator(size = 48.dp)
            }
        } else {
            ExpressivePullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { isRefreshing = true; viewModel.loadData() },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
            val summary = state.summary
            val stepsFormatted = remember(summary.today_steps) {
                if (summary.today_steps > 0) {
                    NumberFormat.getIntegerInstance(Locale.getDefault()).format(summary.today_steps.toLong())
                } else "0"
            }
            val sleepFormatted = remember(summary.last_sleep_seconds) {
                val sec = summary.last_sleep_seconds.toLong()
                if (sec > 0L) {
                    val hrs = sec / 3600
                    val mins = (sec % 3600) / 60
                    "${hrs}s ${mins}d"
                } else "-"
            }
            val hrFormatted = remember(summary.latest_heart_rate_bpm) {
                if (summary.latest_heart_rate_bpm > 0) "${summary.latest_heart_rate_bpm.toInt()} bpm" else "-"
            }
            val activeKcalFormatted = remember(summary.today_active_calories) {
                if (summary.today_active_calories > 0) "${summary.today_active_calories.toInt()} kcal" else "0 kcal"
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                contentPadding = PaddingValues(top = 10.dp, bottom = 144.dp),
                verticalArrangement = Arrangement.Top
            ) {
                // Summary Metric Cards (2x2 Grid)
                item(key = "health_stats_grid", contentType = "stats_grid") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            MetricCard(
                                title = stringResource(R.string.health_metric_steps_title),
                                value = stepsFormatted,
                                badgeText = stringResource(R.string.health_metric_steps_badge),
                                note = summary.steps_date.ifBlank { stringResource(R.string.badge_daily) },
                                modifier = Modifier.weight(1f)
                            )
                            MetricCard(
                                title = stringResource(R.string.health_metric_sleep_title),
                                value = sleepFormatted,
                                badgeText = stringResource(R.string.health_metric_sleep_badge),
                                note = summary.sleep_date.ifBlank { stringResource(R.string.badge_daily) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            MetricCard(
                                title = stringResource(R.string.health_metric_hr_title),
                                value = hrFormatted,
                                badgeText = stringResource(R.string.health_metric_hr_badge),
                                note = stringResource(R.string.health_metric_hr_note),
                                modifier = Modifier.weight(1f)
                            )
                            val fourthNote = if (summary.latest_weight_kg > 0) {
                                "${String.format(Locale.US, "%.1f", summary.latest_weight_kg)} kg · ${stringResource(R.string.health_metric_records_note, summary.total_records)}"
                            } else {
                                stringResource(R.string.health_metric_records_note, summary.total_records)
                            }
                            MetricCard(
                                title = stringResource(R.string.health_metric_active_title),
                                value = activeKcalFormatted,
                                badgeText = stringResource(R.string.health_metric_active_badge),
                                note = fourthNote,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Sticky Search Bar + Category Connected Button Group
                stickyHeader(key = "health_sticky_header", contentType = "sticky_header") {
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
                                        text = stringResource(R.string.health_search_placeholder),
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
                                                    .clickable { viewModel.clearDateFilter() }
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
                                    items = HealthCategoryKeys,
                                    selectedItem = state.selectedCategory,
                                    onItemSelected = { key ->
                                        if (state.selectedCategory == key && key != "all") {
                                            viewModel.setCategoryFilter("all")
                                        } else {
                                            viewModel.setCategoryFilter(key)
                                        }
                                    },
                                    labelProvider = { key ->
                                        when (key) {
                                            "activity" -> stringResource(R.string.health_cat_activity)
                                            "sleep" -> stringResource(R.string.health_cat_sleep)
                                            "vitals" -> stringResource(R.string.health_cat_vitals)
                                            "exercise" -> stringResource(R.string.health_cat_exercise)
                                            "body" -> stringResource(R.string.health_cat_body)
                                            else -> stringResource(R.string.health_cat_all)
                                        }
                                    },
                                    iconProvider = { key, _ ->
                                        when (key) {
                                            "activity" -> Icons.AutoMirrored.Filled.DirectionsWalk
                                            "sleep" -> Icons.Default.Bedtime
                                            "vitals" -> Icons.Default.MonitorHeart
                                            "exercise" -> Icons.Default.FitnessCenter
                                            "body" -> Icons.Default.MonitorWeight
                                            else -> Icons.Default.Apps
                                        }
                                    }
                                )

                                if (state.selectedCategory != "all" || state.searchQuery.isNotEmpty() || hasActiveDate) {
                                    ExpressiveAssistChip(
                                        onClick = { viewModel.resetFilters() },
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

                // Loading or Empty or Items
                if ((state.isLoading || state.isFiltering) && state.filteredRecords.isEmpty()) {
                    if (!isRefreshing) {
                        item(key = "health_loading", contentType = "loading") {
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
                } else if (state.filteredRecords.isEmpty()) {
                    item(key = "health_empty", contentType = "empty") {
                        if (state.records.isEmpty()) {
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
                                        color = domainHealthContainer(),
                                        modifier = Modifier.size(56.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.Favorite,
                                                contentDescription = null,
                                                tint = domainHealthOnContainer(),
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = stringResource(R.string.health_empty_title),
                                        style = MaterialTheme.typography.titleMediumEmphasized
                                    )
                                    Text(
                                        text = stringResource(R.string.health_empty_guide),
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
                                    text = stringResource(R.string.health_search_empty),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    itemsIndexed(
                        items = state.filteredRecords,
                        key = { _, rec -> rec.id.ifBlank { "${rec.record_type}_${rec.start_time}_${rec.title}" } },
                        contentType = { _, _ -> "health_row" }
                    ) { index, rec ->
                        LazyCardRowContainer(
                            index = index,
                            totalCount = state.filteredRecords.size
                        ) {
                            HealthRecordRowItem(
                                record = rec,
                                onClick = { selectedRecord = rec }
                            )
                        }
                    }
                }

                item(key = "health_bottom_spacer", contentType = "spacer") {
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }
        }
    }

    selectedRecord?.let { rec ->
        HealthRecordDetailBottomSheet(
            record = rec,
            onDismiss = { selectedRecord = null }
        )
    }
}

@Composable
fun healthRecordVisuals(recordType: String, category: String): Triple<ImageVector, Color, Color> {
    val icon = when (recordType) {
        "steps" -> Icons.AutoMirrored.Filled.DirectionsWalk
        "distance", "elevation_gained", "floors_climbed" -> Icons.Default.Route
        "active_calories", "total_calories", "basal_metabolic_rate" -> Icons.Default.LocalFireDepartment
        "sleep" -> Icons.Default.Bedtime
        "exercise", "speed", "power", "vo2_max" -> Icons.AutoMirrored.Filled.DirectionsRun
        "heart_rate", "resting_heart_rate", "heart_rate_variability" -> Icons.Default.MonitorHeart
        "oxygen_saturation", "respiratory_rate" -> Icons.Default.Air
        "blood_pressure", "blood_glucose", "body_temperature", "skin_temperature" -> Icons.Default.Thermostat
        "weight", "bmi", "body_fat", "lean_body_mass", "bone_mass", "height" -> Icons.Default.MonitorWeight
        "hydration", "body_water_mass" -> Icons.Default.WaterDrop
        else -> Icons.Default.Favorite
    }

    val (bg, fg) = when (category) {
        "activity" -> domainNutritionContainer() to domainNutritionOnContainer()
        "sleep" -> domainCinemaContainer() to domainCinemaOnContainer()
        "exercise" -> domainRatingContainer() to domainRatingOnContainer()
        "body" -> domainBooksContainer() to domainBooksOnContainer()
        else -> domainHealthContainer() to domainHealthOnContainer()
    }
    return Triple(icon, bg, fg)
}

@Composable
fun HealthRecordRowItem(
    record: HealthRecordDto,
    onClick: () -> Unit
) {
    val (icon, iconBg, iconTint) = healthRecordVisuals(record.record_type, record.category)
    val categoryLabel = when (record.category) {
        "activity" -> stringResource(R.string.health_cat_activity)
        "sleep" -> stringResource(R.string.health_cat_sleep)
        "vitals" -> stringResource(R.string.health_cat_vitals)
        "exercise" -> stringResource(R.string.health_cat_exercise)
        "body" -> stringResource(R.string.health_cat_body)
        else -> stringResource(R.string.title_health_connect)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .expressiveBounceClick(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(M3Shapes.CornerSmall)
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = record.title,
                style = MaterialTheme.typography.titleSmallEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (record.subtitle.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = record.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = M3Shapes.CornerFull,
                    color = iconBg
                ) {
                    Text(
                        text = categoryLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = iconTint,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                        maxLines = 1,
                        softWrap = false
                    )
                }
                val dateTimeStr = listOf(record.formattedDate, record.formattedTime)
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
                if (dateTimeStr.isNotBlank()) {
                    Text(
                        text = dateTimeStr,
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
