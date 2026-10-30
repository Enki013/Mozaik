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
import androidx.compose.material.icons.automirrored.filled.Sort
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.personalrecords.app.PersonalRecordsApp
import com.personalrecords.app.R
import com.personalrecords.app.core.network.model.HealthConnectSummaryDto
import com.personalrecords.app.core.network.model.HealthRecordDto
import com.personalrecords.app.core.theme.*
import com.personalrecords.app.ui.components.*
import java.text.NumberFormat
import java.util.Locale
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

private val HealthCategoryKeys = listOf("all", "activity", "sleep", "vitals", "exercise", "body")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HealthConnectScreen(
    viewModel: HealthConnectViewModel,
    onNavigateBack: (() -> Unit)? = null
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val preferences = remember { PersonalRecordsApp.instance.preferences }
    val hiddenCardIds by preferences.hiddenStatsCards.collectAsStateWithLifecycle(initialValue = emptySet())
    val statCardOrder = rememberStatCardOrder(
        "health_connect",
        remember { listOf("health_steps", "health_hr", "health_sleep", "health_active") }
    )
    val orderedCardIds = statCardOrder.orderedIds
    val coroutineScope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
    var isEditMode by remember { mutableStateOf(false) }
    var showCustomizationSheet by remember { mutableStateOf(false) }
    var targetCardId by remember { mutableStateOf<String?>(null) }
    var selectedRecord by remember { mutableStateOf<HealthRecordDto?>(null) }
    val isTwoPane = currentAdaptiveLayout().showsDetailPane
    var showDatePicker by remember { mutableStateOf(false) }

    val healthColor = recordCategoryColors(RecordCategory.Health).accent
    val nutritionColor = recordCategoryColors(RecordCategory.Nutrition).accent
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val errorColor = MaterialTheme.colorScheme.error

    val healthStepsTitle = stringResource(R.string.stats_card_health_steps_title)
    val healthStepsDesc = stringResource(R.string.stats_card_health_steps_desc)
    val healthHrTitle = stringResource(R.string.stats_card_health_hr_title)
    val healthHrDesc = stringResource(R.string.stats_card_health_hr_desc)
    val healthSleepTitle = stringResource(R.string.stats_card_health_sleep_title)
    val healthSleepDesc = stringResource(R.string.stats_card_health_sleep_desc)
    val healthActiveTitle = stringResource(R.string.stats_card_health_active_title)
    val healthActiveDesc = stringResource(R.string.stats_card_health_active_desc)
    val healthStatCards = remember(healthColor, nutritionColor, tertiaryColor, errorColor, healthStepsTitle, healthStepsDesc, healthHrTitle, healthHrDesc, healthSleepTitle, healthSleepDesc, healthActiveTitle, healthActiveDesc) {
        listOf(
            StatCardItem(
                id = "health_steps",
                title = healthStepsTitle,
                description = healthStepsDesc,
                icon = Icons.AutoMirrored.Filled.DirectionsWalk,
                iconTint = healthColor
            ),
            StatCardItem(
                id = "health_hr",
                title = healthHrTitle,
                description = healthHrDesc,
                icon = Icons.Default.Favorite,
                iconTint = errorColor
            ),
            StatCardItem(
                id = "health_sleep",
                title = healthSleepTitle,
                description = healthSleepDesc,
                icon = Icons.Default.Bedtime,
                iconTint = tertiaryColor
            ),
            StatCardItem(
                id = "health_active",
                title = healthActiveTitle,
                description = healthActiveDesc,
                icon = Icons.Default.LocalFireDepartment,
                iconTint = nutritionColor
            )
        )
    }

    val orderedStatCards = remember(orderedCardIds, healthStatCards) {
        val map = healthStatCards.associateBy { it.id }
        orderedCardIds.mapNotNull { map[it] } + healthStatCards.filter { it.id !in orderedCardIds }
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
            ExpressivePageTopAppBar(
                title = stringResource(R.string.title_health_connect),
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
                        hasHiddenCards = healthStatCards.any { it.id in hiddenCardIds },
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
            val summary = state.summary
            val stepsFormatted = remember(summary.today_steps) {
                if (summary.today_steps > 0) {
                    NumberFormat.getIntegerInstance(Locale.getDefault()).format(summary.today_steps.toLong())
                } else "0"
            }
            val sleepPattern = stringResource(R.string.health_duration_hours_minutes)
            val sleepFormatted = remember(summary.last_sleep_seconds, sleepPattern) {
                val sec = summary.last_sleep_seconds.toLong()
                if (sec > 0L) {
                    val hrs = sec / 3600
                    val mins = (sec % 3600) / 60
                    String.format(sleepPattern, hrs, mins)
                } else "-"
            }
            val hrFormatted = remember(summary.latest_heart_rate_bpm) {
                if (summary.latest_heart_rate_bpm > 0) "${summary.latest_heart_rate_bpm.toInt()} bpm" else "-"
            }
            val activeKcalFormatted = remember(summary.today_active_calories) {
                if (summary.today_active_calories > 0) "${summary.today_active_calories.toInt()} kcal" else "0 kcal"
            }

            ExpressiveListDetailLayout(
                showDetailPane = isTwoPane,
                detailPane = {
                    val rec = selectedRecord
                    if (rec != null) {
                        HealthRecordDetailPaneCard(record = rec, onClose = { selectedRecord = null })
                    } else {
                        DetailPlaceholderPane(
                            icon = Icons.Default.Favorite,
                            title = stringResource(R.string.adaptive_select_item_title),
                            subtitle = stringResource(R.string.adaptive_select_item_subtitle)
                        )
                    }
                }
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = FilterGutter,
                        end = FilterGutter,
                        top = 10.dp,
                        bottom = 24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    ),
                    verticalArrangement = Arrangement.Top
                ) {
                    // Summary Metric Cards (Dynamic Chunked Grid)
                    item(key = "health_stats_grid", contentType = "stats_grid") {
                        val visibleCardIds = orderedCardIds.filter { it !in hiddenCardIds }

                        LaunchedEffect(visibleCardIds.isEmpty()) {
                            if (visibleCardIds.isEmpty()) {
                                isEditMode = false
                            }
                        }

                        StatCardGrid(
                            cardIds = visibleCardIds,
                            isEditMode = isEditMode,
                            onHideCard = { id ->
                                coroutineScope.launch { preferences.toggleStatCardVisibility(id, false) }
                            },
                            onReorder = { statCardOrder.setVisibleOrder(it) },
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) { cardId ->
                            HealthStatCardItemView(
                                cardId = cardId,
                                summary = summary,
                                stepsFormatted = stepsFormatted,
                                sleepFormatted = sleepFormatted,
                                hrFormatted = hrFormatted,
                                activeKcalFormatted = activeKcalFormatted,
                                onLongClick = { isEditMode = true }
                            )
                        }
                    }

                    state.error?.let { err ->
                        item(key = "health_error_banner", contentType = "error_banner") {
                            ExpressiveErrorBanner(
                                message = err,
                                onRetry = { viewModel.loadData() },
                                modifier = expressiveItemModifier().padding(bottom = 8.dp)
                            )
                        }
                    }

                    // Sticky Search Bar + Category Connected Button Group
                    stickyHeader(key = "health_sticky_header", contentType = "sticky_header") {
                        ExpressiveFilterHeader {
                            ExpressiveSearchField(
                                value = state.searchQuery,
                                onValueChange = { viewModel.setSearchQuery(it) },
                                placeholder = stringResource(R.string.health_search_placeholder),
                                clearContentDescription = stringResource(R.string.action_clear),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .filterHeaderItem()
                            )
                            ExpressiveFilterRow {
                                ExpressiveConnectedButtonGroup(
                                    items = HealthCategoryKeys,
                                    selectedItem = state.selectedCategory,
                                    onItemSelected = { key -> viewModel.setCategoryFilter(key) },
                                    labelProvider = { key ->
                                        when (key) {
                                            "activity" -> stringResource(R.string.health_cat_activity)
                                            "sleep" -> stringResource(R.string.health_cat_sleep)
                                            "vitals" -> stringResource(R.string.health_cat_vitals)
                                            "exercise" -> stringResource(R.string.health_cat_exercise)
                                            "body" -> stringResource(R.string.health_cat_body)
                                            else -> stringResource(R.string.filter_all)
                                        }
                                    },
                                    iconProvider = { key, _ ->
                                        when (key) {
                                            "activity" -> Icons.AutoMirrored.Filled.DirectionsWalk
                                            "sleep" -> Icons.Default.Bedtime
                                            "vitals" -> Icons.Default.MonitorHeart
                                            "exercise" -> Icons.Default.FitnessCenter
                                            "body" -> Icons.Default.MonitorWeight
                                            else -> null
                                        }
                                    }
                                )
                            }
                            val hasActiveDate = state.selectedDateFilter != null || state.fromDateFilter != null
                            val hasActiveFilters = state.searchQuery.isNotEmpty() || hasActiveDate || state.selectedCategory != "all"
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
                                ExpressiveFilterResultCount(count = state.filteredRecords.size)
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
                                    AppContainedLoadingIndicator(size = 44.dp)
                                }
                            }
                        }
                    } else if (state.filteredRecords.isEmpty() && state.error == null) {
                        item(key = "health_empty", contentType = "empty") {
                            if (state.records.isEmpty()) {
                                ExpressiveEmptyState(
                                    title = stringResource(R.string.health_empty_title),
                                    message = stringResource(R.string.health_empty_guide),
                                    icon = Icons.Default.Favorite,
                                    iconContainerColor = recordCategoryColors(RecordCategory.Health).container,
                                    iconContentColor = recordCategoryColors(RecordCategory.Health).onContainer
                                )
                            } else {
                                ExpressiveEmptyState(
                                    title = stringResource(R.string.health_search_empty),
                                    icon = Icons.Default.SearchOff
                                )
                            }
                        }
                    } else {
                        itemsIndexed(
                            items = state.filteredRecords,
                            key = { _, rec -> rec.id.ifBlank { "${rec.record_type}_${rec.start_time}_${rec.title}" } },
                            contentType = { _, _ -> "health_row" }
                        ) { index, rec ->
                            HealthRecordRowItem(
                                record = rec,
                                onClick = { selectedRecord = rec },
                                index = index,
                                count = state.filteredRecords.size,
                                addLeadingGap = true
                            )
                        }
                    }

                    item(key = "health_bottom_spacer", contentType = "spacer") {
                        Spacer(modifier = Modifier.height(20.dp))
                    }
                }
            }
        }
    }

    if (!isTwoPane) {
        selectedRecord?.let { rec ->
            HealthRecordDetailBottomSheet(
                record = rec,
                onDismiss = { selectedRecord = null }
            )
        }
    }

    if (showCustomizationSheet) {
        StatsCustomizationBottomSheet(
            sheetTitle = stringResource(R.string.stats_customization_title),
            cards = orderedStatCards,
            hiddenCardIds = hiddenCardIds,
            targetCardId = targetCardId,
            onToggleCard = { cardId, visible ->
                coroutineScope.launch {
                    preferences.toggleStatCardVisibility(cardId, visible)
                }
            },
            onReorder = { ids -> statCardOrder.setOrder(ids) },
            onResetDefault = {
                coroutineScope.launch {
                    preferences.resetHiddenStatsCards(healthStatCards.map { it.id })
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
        "activity" -> recordCategoryColors(RecordCategory.Nutrition).container to recordCategoryColors(RecordCategory.Nutrition).onContainer
        "sleep" -> recordCategoryColors(RecordCategory.Movies).container to recordCategoryColors(RecordCategory.Movies).onContainer
        "exercise" -> domainRatingContainer() to domainRatingOnContainer()
        "body" -> recordCategoryColors(RecordCategory.Books).container to recordCategoryColors(RecordCategory.Books).onContainer
        else -> recordCategoryColors(RecordCategory.Health).container to recordCategoryColors(RecordCategory.Health).onContainer
    }
    return Triple(icon, bg, fg)
}

@Composable
fun HealthRecordRowItem(
    record: HealthRecordDto,
    index: Int = 0,
    count: Int = 1,
    modifier: Modifier = Modifier,
    addLeadingGap: Boolean = false,
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

    
    ExpressiveListRow(
        index = index,
        count = count,
        modifier = modifier,
        addLeadingGap = addLeadingGap,
        onClick = onClick,
        verticalAlignment = Alignment.CenterVertically,
        leadingContent = {
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
        },
        supportingContent = {
            Column {
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
    ) {
        Text(
            text = record.title,
            style = MaterialTheme.typography.titleSmallEmphasized,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun HealthStatCardItemView(
    cardId: String,
    summary: HealthConnectSummaryDto,
    stepsFormatted: String,
    sleepFormatted: String,
    hrFormatted: String,
    activeKcalFormatted: String,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        when (cardId) {
            "health_steps" -> {
                MetricCard(
                    title = stringResource(R.string.health_metric_steps_title),
                    value = stepsFormatted,
                    badgeText = stringResource(R.string.health_metric_steps_badge),
                    note = summary.steps_date.ifBlank { stringResource(R.string.badge_daily) },
                    valueIcon = Icons.AutoMirrored.Filled.DirectionsWalk,
                    valueIconTint = recordCategoryColors(RecordCategory.Health).accent,
                    badgeBg = recordCategoryColors(RecordCategory.Health).container,
                    badgeColor = recordCategoryColors(RecordCategory.Health).onContainer,
                    onLongClick = onLongClick
                )
            }
            "health_hr" -> {
                MetricCard(
                    title = stringResource(R.string.health_metric_hr_title),
                    value = hrFormatted,
                    badgeText = stringResource(R.string.health_metric_hr_badge),
                    note = stringResource(R.string.health_metric_hr_note),
                    valueIcon = Icons.Default.Favorite,
                    valueIconTint = MaterialTheme.colorScheme.error,
                    badgeBg = MaterialTheme.colorScheme.errorContainer,
                    badgeColor = MaterialTheme.colorScheme.onErrorContainer,
                    onLongClick = onLongClick
                )
            }
            "health_sleep" -> {
                MetricCard(
                    title = stringResource(R.string.health_metric_sleep_title),
                    value = sleepFormatted,
                    badgeText = stringResource(R.string.health_metric_sleep_badge),
                    note = summary.sleep_date.ifBlank { stringResource(R.string.badge_daily) },
                    valueIcon = Icons.Default.Bedtime,
                    valueIconTint = MaterialTheme.colorScheme.tertiary,
                    badgeBg = MaterialTheme.colorScheme.tertiaryContainer,
                    badgeColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    onLongClick = onLongClick
                )
            }
            "health_active" -> {
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
                    valueIcon = Icons.Default.LocalFireDepartment,
                    valueIconTint = recordCategoryColors(RecordCategory.Nutrition).accent,
                    badgeBg = recordCategoryColors(RecordCategory.Nutrition).container,
                    badgeColor = recordCategoryColors(RecordCategory.Nutrition).onContainer,
                    onLongClick = onLongClick
                )
            }
        }
    }
}
