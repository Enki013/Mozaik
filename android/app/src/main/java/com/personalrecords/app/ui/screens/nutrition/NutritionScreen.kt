package com.personalrecords.app.ui.screens.nutrition

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.personalrecords.app.PersonalRecordsApp
import com.personalrecords.app.R
import com.personalrecords.app.core.network.model.FoodEntryDto
import com.personalrecords.app.core.theme.*
import com.personalrecords.app.ui.components.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

private val NutritionMealKeys = listOf("all", "Breakfast", "Lunch", "Dinner", "Other")

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun NutritionScreen(
    viewModel: NutritionViewModel,
    onNavigateBack: (() -> Unit)? = null
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val preferences = remember { PersonalRecordsApp.instance.preferences }
    val hiddenCardIds by preferences.hiddenStatsCards.collectAsStateWithLifecycle(initialValue = emptySet())
    val coroutineScope = rememberCoroutineScope()
    var isEditMode by remember { mutableStateOf(false) }
    var showCustomizationSheet by remember { mutableStateOf(false) }
    var targetCardId by remember { mutableStateOf<String?>(null) }
    var showTargetDialog by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var selectedFood by remember { mutableStateOf<FoodEntryDto?>(null) }
    var animationStarted by remember { mutableStateOf(false) }

    val nutritionColor = recordCategoryColors(RecordCategory.Nutrition).accent
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val nutritionCaloriesTitle = stringResource(R.string.stats_card_nutrition_calories_title)
    val nutritionCaloriesDesc = stringResource(R.string.stats_card_nutrition_calories_desc)
    val nutritionMealsTitle = stringResource(R.string.stats_card_nutrition_meals_title)
    val nutritionMealsDesc = stringResource(R.string.stats_card_nutrition_meals_desc)
    val nutritionStatCards = remember(nutritionColor, secondaryColor, nutritionCaloriesTitle, nutritionCaloriesDesc, nutritionMealsTitle, nutritionMealsDesc) {
        listOf(
            StatCardItem(
                id = "nutrition_calories",
                title = nutritionCaloriesTitle,
                description = nutritionCaloriesDesc,
                icon = Icons.Rounded.LocalFireDepartment,
                iconTint = nutritionColor
            ),
            StatCardItem(
                id = "nutrition_meals",
                title = nutritionMealsTitle,
                description = nutritionMealsDesc,
                icon = Icons.Rounded.Restaurant,
                iconTint = secondaryColor
            )
        )
    }
    val statCardOrder = rememberStatCardOrder("nutrition", nutritionStatCards.map { it.id })

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(360L) // Wait for NavHost slide-in transition to finish before animating gauge
        animationStarted = true
    }
    val adaptiveLayout = currentAdaptiveLayout()
    val isWideScreen = adaptiveLayout.isWide
    val isTwoPane = adaptiveLayout.showsDetailPane
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

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

    if (showTargetDialog) {
        var tempTarget by remember { mutableStateOf(state.calorieTarget?.toString() ?: "") }
        AlertDialog(
            onDismissRequest = { showTargetDialog = false },
            shape = M3Shapes.CornerExtraLarge,
            title = {
                Text(
                    text = stringResource(R.string.nutrition_target_dialog_title),
                    style = MaterialTheme.typography.headlineSmallEmphasized
                )
            },
            text = {
                OutlinedTextField(
                    value = tempTarget,
                    onValueChange = { tempTarget = it },
                    label = { Text(stringResource(R.string.nutrition_target_dialog_label)) },
                    singleLine = true,
                    shape = M3Shapes.CornerSmall
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val num = tempTarget.toIntOrNull()
                        if (num != null && num > 0) {
                            viewModel.updateCalorieTarget(num)
                        }
                        showTargetDialog = false
                    }
                ) {
                    Text(stringResource(R.string.action_save), style = MaterialTheme.typography.labelLargeEmphasized)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTargetDialog = false }) {
                    Text(stringResource(R.string.action_cancel), style = MaterialTheme.typography.labelLarge)
                }
            }
        )
    }

    val gridState = rememberLazyGridState()
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets.statusBars,
        topBar = {
            ExpressivePageTopAppBar(
                title = stringResource(R.string.title_nutrition),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    if (onNavigateBack != null) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.action_back)
                            )
                        }
                    }
                },
                actions = {
                    TopAppBarStatsAction(
                        isEditMode = isEditMode,
                        hasHiddenCards = nutritionStatCards.any { it.id in hiddenCardIds },
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
            // Errors first, so a failed sync is seen before the numbers it affects.
            state.error?.let { err ->
                item(key = "nutrition_error_banner", span = { GridItemSpan(maxLineSpan) }, contentType = "error_banner") {
                    ExpressiveErrorBanner(
                        message = err,
                        onRetry = { viewModel.loadData() },
                        modifier = expressiveItemModifier().padding(bottom = 8.dp)
                    )
                }
            }

            // Stats Summary: M3 Elevated Metric Cards with Value Icons (Music & Health pattern)
            item(key = "nutrition_stats", span = { GridItemSpan(maxLineSpan) }, contentType = "stats") {
                val target = state.calorieTarget
                val rem = if (target != null) (target - state.todayCalories).toInt() else 0
                val targetBadge = target?.let { "/ $it kcal" } ?: stringResource(R.string.badge_daily)
                val remNote = target?.let {
                    if (rem >= 0) stringResource(R.string.nutrition_target_remaining, rem)
                    else stringResource(R.string.nutrition_target_exceeded, -rem)
                } ?: stringResource(R.string.overview_metric_today_calories)


                val isCaloriesVisible = "nutrition_calories" !in hiddenCardIds
                val isMealsVisible = "nutrition_meals" !in hiddenCardIds


                LaunchedEffect(isCaloriesVisible, isMealsVisible) {
                    if (!isCaloriesVisible && !isMealsVisible) {
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
                        "nutrition_calories" -> {
                            MetricCard(
                                title = stringResource(R.string.overview_metric_today_calories),
                                value = "${state.todayCalories.toInt()} kcal",
                                badgeText = targetBadge,
                                note = remNote,
                                valueIcon = Icons.Rounded.LocalFireDepartment,
                                valueIconTint = recordCategoryColors(RecordCategory.Nutrition).accent,
                                badgeBg = recordCategoryColors(RecordCategory.Nutrition).container,
                                badgeColor = recordCategoryColors(RecordCategory.Nutrition).onContainer,
                                onLongClick = { isEditMode = true }
                            )
                        }
                        "nutrition_meals" -> {
                            MetricCard(
                                title = stringResource(R.string.records_item_nutrition_title),
                                value = "${state.todayEntriesCount}",
                                badgeText = "",
                                note = stringResource(R.string.overview_metric_meals_count, state.foodEntries.size),
                                valueIcon = Icons.Rounded.Restaurant,
                                valueIconTint = MaterialTheme.colorScheme.secondary,
                                badgeBg = MaterialTheme.colorScheme.secondaryContainer,
                                badgeColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                onLongClick = { isEditMode = true }
                            )
                        }
                    }
                }
            }

            // Calorie Budget Card (Card MD3)
            item(key = "nutrition_budget_card", span = { GridItemSpan(if (isWideScreen) 1 else maxLineSpan) }, contentType = "budget_card") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    shape = M3Shapes.CornerLarge,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    border = null
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.LocalFireDepartment,
                                    contentDescription = null,
                                    tint = recordCategoryColors(RecordCategory.Nutrition).accent,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = stringResource(R.string.nutrition_calorie_target_title),
                                    style = MaterialTheme.typography.titleMediumEmphasized,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            val targetBtnInteraction = remember { MutableInteractionSource() }
                            FilledTonalButton(
                                onClick = { showTargetDialog = true },
                                interactionSource = targetBtnInteraction,
                                shapes = expressiveButtonShapes(),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier

                                    .expressivePressScale(targetBtnInteraction)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Settings,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = state.calorieTarget?.let { "$it kcal" } ?: "---",
                                        style = MaterialTheme.typography.labelMediumEmphasized
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        val target = state.calorieTarget
                        val progressPct = if (target != null && target > 0) {
                            (state.todayCalories / target).toFloat().coerceIn(0f, 1f)
                        } else 0f
                        val animatedProgressState = animateFloatAsState(
                            targetValue = if (animationStarted) progressPct else 0f,
                            animationSpec = M3ExpressiveMotion.defaultSpatial(),
                            label = "prog"
                        )

                        val todayCarbKcal = state.todayCarb * 4
                        val todayProtKcal = state.todayProtein * 4
                        val todayFatKcal = state.todayFat * 9
                        val todayTotalMacroKcal = todayCarbKcal + todayProtKcal + todayFatKcal

                        val carbColor = recordCategoryColors(RecordCategory.Books).accent
                        val protColor = recordCategoryColors(RecordCategory.Nutrition).accent
                        val fatColor = domainRatingColor()

                        val macroSegments = if (todayTotalMacroKcal > 0) {
                            listOf(
                                (todayCarbKcal / todayTotalMacroKcal).toFloat() to carbColor,
                                (todayProtKcal / todayTotalMacroKcal).toFloat() to protColor,
                                (todayFatKcal / todayTotalMacroKcal).toFloat() to fatColor
                            )
                        } else null

                        val barColor = when {
                            progressPct >= 1f -> MaterialTheme.colorScheme.error
                            progressPct > 0.85f -> fatColor
                            else -> protColor
                        }

                        // MD3 Expressive Segmented Linear Progress Indicator
                        SegmentedLinearProgressIndicator(
                            progress = { animatedProgressState.value },
                            segments = macroSegments,
                            modifier = Modifier.fillMaxWidth(),
                            barHeight = 8.dp,
                            gap = 3.dp,
                            trackGap = 4.dp,
                            singleColor = barColor,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val rem = if (target != null) (target - state.todayCalories).toInt() else 0
                            val remText = target?.let {
                                if (rem >= 0) stringResource(R.string.nutrition_target_remaining, rem)
                                else stringResource(R.string.nutrition_target_exceeded, -rem)
                            } ?: ""
                            val consumedText = target?.let {
                                stringResource(R.string.nutrition_consumed_today, state.todayCalories.toInt(), it, (progressPct * 100).toInt())
                            } ?: "${state.todayCalories.toInt()} kcal"
                            Text(
                                text = consumedText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = remText,
                                style = MaterialTheme.typography.bodySmallEmphasized,
                                color = if (rem >= 0) protColor else MaterialTheme.colorScheme.error
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        val todayCarbPct = if (todayTotalMacroKcal > 0) ((todayCarbKcal / todayTotalMacroKcal) * 100).toInt() else 0
                        val todayProtPct = if (todayTotalMacroKcal > 0) ((todayProtKcal / todayTotalMacroKcal) * 100).toInt() else 0
                        val todayFatPct = if (todayTotalMacroKcal > 0) ((todayFatKcal / todayTotalMacroKcal) * 100).toInt() else 0

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            MacroLegendItem(
                                color = carbColor,
                                label = stringResource(R.string.nutrition_label_carb_short),
                                grams = state.todayCarb.toInt(),
                                pct = todayCarbPct,
                                icon = Icons.Rounded.Grain
                            )
                            MacroLegendItem(
                                color = protColor,
                                label = stringResource(R.string.nutrition_label_protein_short),
                                grams = state.todayProtein.toInt(),
                                pct = todayProtPct,
                                icon = Icons.Rounded.FitnessCenter
                            )
                            MacroLegendItem(
                                color = fatColor,
                                label = stringResource(R.string.nutrition_label_fat_short),
                                grams = state.todayFat.toInt(),
                                pct = todayFatPct,
                                icon = Icons.Rounded.Opacity
                            )
                        }
                    }
                }
            }

            // Macro Breakdown Card (MD3 Card)
            item(key = "nutrition_macro_card", span = { GridItemSpan(if (isWideScreen) 1 else maxLineSpan) }, contentType = "macro_card") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    shape = M3Shapes.CornerLarge,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    border = null
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PieChart,
                                contentDescription = null,
                                tint = recordCategoryColors(RecordCategory.Nutrition).accent,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = stringResource(R.string.nutrition_macro_title),
                                style = MaterialTheme.typography.titleMediumEmphasized,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = stringResource(R.string.nutrition_macro_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        val carbKcal = state.totalCarb * 4
                        val protKcal = state.totalProtein * 4
                        val fatKcal = state.totalFat * 9
                        val totalKcal = carbKcal + protKcal + fatKcal

                        val carbPct = if (totalKcal > 0) (carbKcal / totalKcal).toFloat() else 0.33f
                        val protPct = if (totalKcal > 0) (protKcal / totalKcal).toFloat() else 0.33f
                        val fatPct = if (totalKcal > 0) (fatKcal / totalKcal).toFloat() else 0.34f

                        val carbColor = recordCategoryColors(RecordCategory.Books).accent
                        val protColor = recordCategoryColors(RecordCategory.Nutrition).accent
                        val fatColor = domainRatingColor()

                        val breakdownSegments = if (totalKcal > 0) {
                            listOf(
                                carbPct to carbColor,
                                protPct to protColor,
                                fatPct to fatColor
                            )
                        } else null

                        val animatedBreakdownProgress = animateFloatAsState(
                            targetValue = if (animationStarted) 1f else 0f,
                            animationSpec = M3ExpressiveMotion.defaultSpatial(),
                            label = "breakdown_prog"
                        )

                        // MD3 Expressive Segmented Linear Progress Indicator
                        SegmentedLinearProgressIndicator(
                            progress = { animatedBreakdownProgress.value },
                            segments = breakdownSegments,
                            modifier = Modifier.fillMaxWidth(),
                            barHeight = 8.dp,
                            gap = 3.dp,
                            trackGap = 4.dp,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            MacroLegendItem(color = carbColor, label = stringResource(R.string.nutrition_label_carb_short), grams = state.totalCarb.toInt(), pct = (carbPct * 100).toInt(), icon = Icons.Rounded.Grain)
                            MacroLegendItem(color = protColor, label = stringResource(R.string.nutrition_label_protein_short), grams = state.totalProtein.toInt(), pct = (protPct * 100).toInt(), icon = Icons.Rounded.FitnessCenter)
                            MacroLegendItem(color = fatColor, label = stringResource(R.string.nutrition_label_fat_short), grams = state.totalFat.toInt(), pct = (fatPct * 100).toInt(), icon = Icons.Rounded.Opacity)
                        }
                    }
                }
            }


            // Food Entries Log Header & Filters
            stickyHeader(key = "nutrition_sticky_header", contentType = "sticky_header") {
                ExpressiveFilterHeader {
                    ExpressiveSearchField(
                        value = state.searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        placeholder = stringResource(R.string.nutrition_search_placeholder),
                        clearContentDescription = stringResource(R.string.action_clear),
                        modifier = Modifier
                            .fillMaxWidth()
                            .filterHeaderItem()
                    )
                    ExpressiveFilterRow {
                        ExpressiveConnectedButtonGroup(
                            items = NutritionMealKeys,
                            selectedItem = state.selectedMeal,
                            onItemSelected = { key -> viewModel.setMeal(key) },
                            labelProvider = { key ->
                                when (key) {
                                    "Breakfast" -> stringResource(R.string.nutrition_filter_breakfast)
                                    "Lunch" -> stringResource(R.string.nutrition_filter_lunch)
                                    "Dinner" -> stringResource(R.string.nutrition_filter_dinner)
                                    "Other" -> stringResource(R.string.nutrition_filter_snack)
                                    else -> stringResource(R.string.filter_all)
                                }
                            },
                            iconProvider = { key, _ ->
                                when (key) {
                                    "Breakfast" -> Icons.Rounded.BakeryDining
                                    "Lunch" -> Icons.Rounded.LunchDining
                                    "Dinner" -> Icons.Rounded.DinnerDining
                                    "Other" -> Icons.Rounded.Fastfood
                                    else -> null
                                }
                            }
                        )
                    }
                    val hasActiveDate = state.selectedDateFilter != null || state.fromDateFilter != null
                    val hasActiveFilters = state.searchQuery.isNotEmpty() || hasActiveDate || state.selectedMeal != "all"
                    ExpressiveFilterRow {
                        // Sort first, so it keeps its place when the clear chip appears.
                        ExpressiveDropdownFilterChip(
                            options = listOf("date_desc", "date_asc", "calories_desc", "protein_desc"),
                            selected = state.sortBy,
                            onSelect = { viewModel.setSortBy(it) },
                            optionLabel = { key ->
                                when (key) {
                                    "date_desc" -> stringResource(R.string.sort_newest)
                                    "date_asc" -> stringResource(R.string.sort_oldest)
                                    "calories_desc" -> stringResource(R.string.sort_calories)
                                    "protein_desc" -> stringResource(R.string.sort_protein)
                                    else -> stringResource(R.string.action_sort)
                                }
                            },
                            leadingIcon = Icons.AutoMirrored.Rounded.Sort,
                            isActive = false
                        )
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
                    }
                    if (hasActiveFilters) {
                        ExpressiveFilterResultCount(count = state.filteredFoods.size)
                    }
                }
            }

            // Food Items List
            if ((state.isLoading || state.isFiltering) && state.filteredFoods.isEmpty()) {
                if (!isRefreshing) {
                    item(key = "nutrition_loading", span = { GridItemSpan(maxLineSpan) }, contentType = "loading") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            AppContainedLoadingIndicator(size = 44.dp)
                        }
                    }
                }
            } else if (state.filteredFoods.isEmpty() && state.error == null) {
                item(key = "nutrition_empty", span = { GridItemSpan(maxLineSpan) }, contentType = "empty") {
                    ExpressiveEmptyState(
                        title = stringResource(R.string.nutrition_empty),
                        icon = Icons.Rounded.Restaurant,
                        iconContainerColor = recordCategoryColors(RecordCategory.Nutrition).container,
                        iconContentColor = recordCategoryColors(RecordCategory.Nutrition).onContainer
                    )
                }
            } else {
                itemsIndexed(
                    items = state.filteredFoods,
                    key = { _, food -> food.food_entry_id.ifBlank { "${food.displayName}_${food.displayDate}_${food.meal}_${food.calories}" } },
                    contentType = { _, _ -> "food_row" }
                ) { index, food ->
                    FoodEntryRowItem(
                        item = food,
                        onClick = { selectedFood = food },
                        // Grid cells on wide screens are standalone rows; phones get one segmented group.
                        index = if (isWideScreen) 0 else index,
                        count = if (isWideScreen) 1 else state.filteredFoods.size,
                        addLeadingGap = !isWideScreen,
                        modifier = expressiveItemModifier(),
                        selected = if (isTwoPane) selectedFood == food else null
                    )
                }
            }

            item(key = "nutrition_bottom_spacer", span = { GridItemSpan(maxLineSpan) }, contentType = "spacer") { Spacer(modifier = Modifier.height(24.dp)) }
          }
        }

        ExpressiveListDetailLayout(
            showDetailPane = isTwoPane,
            detailPane = {
                if (selectedFood != null) {
                    Modifier.FoodEntryDetailPaneCard(
                        item = selectedFood!!
                    ) { selectedFood = null }
                } else {
                    DetailPlaceholderPane(
                        icon = Icons.Rounded.Restaurant,
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
        selectedFood?.let { food ->
            FoodEntryDetailBottomSheet(
                item = food,
                onDismiss = { selectedFood = null }
            )
        }
    }

    if (showCustomizationSheet) {
        StatsCustomizationBottomSheet(
            sheetTitle = stringResource(R.string.stats_customization_title),
            cards = statCardOrder.sort(nutritionStatCards),
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
                    preferences.resetHiddenStatsCards(nutritionStatCards.map { it.id })
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
private fun formatMealName(meal: String): String {
    return when (meal.trim().lowercase()) {
        "breakfast" -> stringResource(R.string.nutrition_filter_breakfast)
        "lunch" -> stringResource(R.string.nutrition_filter_lunch)
        "dinner" -> stringResource(R.string.nutrition_filter_dinner)
        "snack", "other" -> stringResource(R.string.nutrition_filter_snack)
        else -> meal
    }
}

private fun getMealIcon(meal: String): androidx.compose.ui.graphics.vector.ImageVector {
    return when (meal.trim().lowercase()) {
        "breakfast" -> Icons.Rounded.BakeryDining
        "lunch" -> Icons.Rounded.LunchDining
        "dinner" -> Icons.Rounded.DinnerDining
        else -> Icons.Rounded.Restaurant
    }
}

@Composable
fun MacroLegendItem(
    color: Color,
    label: String,
    grams: Int,
    pct: Int,
    icon: ImageVector? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(13.dp)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(M3Shapes.CornerFull)
                    .background(color)
            )
        }
        Text(
            text = stringResource(R.string.nutrition_macro_chip_format, label, grams, pct),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
fun FoodEntryRowItem(
    item: FoodEntryDto,
    index: Int = 0,
    count: Int = 1,
    modifier: Modifier = Modifier,
    selected: Boolean? = null,
    addLeadingGap: Boolean = false,
    onClick: () -> Unit = {}
) {

    val mealIcon = getMealIcon(item.meal)
    val mealLabel = formatMealName(item.meal)

    
    ExpressiveListRow(
        index = index,
        count = count,
        modifier = modifier,
        selected = selected,
        addLeadingGap = addLeadingGap,
        onClick = onClick,
        verticalAlignment = Alignment.CenterVertically,
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(M3Shapes.CornerSmall)
                    .background(recordCategoryColors(RecordCategory.Nutrition).container),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = mealIcon,
                    contentDescription = null,
                    tint = recordCategoryColors(RecordCategory.Nutrition).onContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${item.calories.toInt()} kcal",
                    style = MaterialTheme.typography.titleSmallEmphasized,
                    color = recordCategoryColors(RecordCategory.Nutrition).accent,
                    maxLines = 1,
                    softWrap = false
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.nutrition_macro_short, item.carbohydrate.toInt(), item.protein.toInt(), item.fat.toInt()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false
                )
            }
        },
        supportingContent = {
            Column {
                Text(
                    text = "${item.displayAmount} · ${item.displayDate}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = item.displayName,
                style = MaterialTheme.typography.bodyMediumEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Surface(
                shape = M3Shapes.CornerFull,
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Text(
                    text = mealLabel,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
fun FoodEntryCard(item: FoodEntryDto) {
    FoodEntryRowItem(item = item)
}

