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
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
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

    val nutritionColor = domainNutritionColor()
    val booksColor = domainBooksColor()
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val nutritionStatCards = remember(nutritionColor, booksColor, secondaryColor) {
        listOf(
            StatCardItem(
                id = "nutrition_calories",
                title = "Bugünkü Kalori",
                description = "Tüketilen kalori ve kalan hedef",
                icon = Icons.Default.LocalFireDepartment,
                iconTint = nutritionColor
            ),
            StatCardItem(
                id = "nutrition_meals",
                title = "Öğün Kayıtları",
                description = "Bugünkü öğün sayısı ve toplam kayıt",
                icon = Icons.Default.Restaurant,
                iconTint = secondaryColor
            ),
            StatCardItem(
                id = "nutrition_protein",
                title = "Protein",
                description = "Günlük protein gramı ve yüzdesi",
                icon = Icons.Default.FitnessCenter,
                iconTint = nutritionColor
            ),
            StatCardItem(
                id = "nutrition_carbfat",
                title = "Karbonhidrat & Yağ",
                description = "Karb ve yağ dengesi ve yüzdeleri",
                icon = Icons.Default.PieChart,
                iconTint = booksColor
            )
        )
    }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(360L) // Wait for NavHost slide-in transition to finish before animating gauge
        animationStarted = true
    }
    val configuration = LocalConfiguration.current
    val isWideScreen = configuration.screenWidthDp >= 600
    val isTwoPane = configuration.screenWidthDp >= 840
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

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets.statusBars,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.title_nutrition),
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
                        hasHiddenCards = nutritionStatCards.any { it.id in hiddenCardIds },
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
            // Stats Summary: M3 Elevated Metric Cards with Value Icons (Music & Health pattern)
            item(key = "nutrition_stats", span = { GridItemSpan(maxLineSpan) }, contentType = "stats") {
                val target = state.calorieTarget
                val rem = if (target != null) (target - state.todayCalories).toInt() else 0
                val targetBadge = target?.let { "/ $it kcal" } ?: stringResource(R.string.badge_daily)
                val remNote = target?.let {
                    if (rem >= 0) stringResource(R.string.nutrition_target_remaining, rem)
                    else stringResource(R.string.nutrition_target_exceeded, -rem)
                } ?: stringResource(R.string.overview_metric_today_calories)

                val todayTotalMacroGrams = state.todayCarb + state.todayProtein + state.todayFat
                val protPct = if (todayTotalMacroGrams > 0) ((state.todayProtein / todayTotalMacroGrams) * 100).toInt() else 0
                val carbPct = if (todayTotalMacroGrams > 0) ((state.todayCarb / todayTotalMacroGrams) * 100).toInt() else 0
                val fatPct = if (todayTotalMacroGrams > 0) ((state.todayFat / todayTotalMacroGrams) * 100).toInt() else 0

                val isCaloriesVisible = "nutrition_calories" !in hiddenCardIds
                val isMealsVisible = "nutrition_meals" !in hiddenCardIds
                val isProteinVisible = "nutrition_protein" !in hiddenCardIds
                val isCarbFatVisible = "nutrition_carbfat" !in hiddenCardIds

                val hasRow1 = isCaloriesVisible || isMealsVisible
                val hasRow2 = isProteinVisible || isCarbFatVisible

                LaunchedEffect(isCaloriesVisible, isMealsVisible, isProteinVisible, isCarbFatVisible) {
                    if (!isCaloriesVisible && !isMealsVisible && !isProteinVisible && !isCarbFatVisible) {
                        isEditMode = false
                    }
                }

                if (hasRow1 || hasRow2) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (hasRow1) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (isCaloriesVisible) {
                                    StatCardContainer(
                                        cardId = "nutrition_calories",
                                        isEditMode = isEditMode,
                                        onHideCard = {
                                            coroutineScope.launch {
                                                preferences.toggleStatCardVisibility("nutrition_calories", false)
                                            }
                                        },
                                        index = 0,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        MetricCard(
                                            title = stringResource(R.string.overview_metric_today_calories),
                                            value = "${state.todayCalories.toInt()} kcal",
                                            badgeText = targetBadge,
                                            note = remNote,
                                            valueIcon = Icons.Default.LocalFireDepartment,
                                            valueIconTint = domainNutritionColor(),
                                            badgeBg = domainNutritionContainer(),
                                            badgeColor = domainNutritionOnContainer(),
                                            onLongClick = { isEditMode = true }
                                        )
                                    }
                                }
                                if (isMealsVisible) {
                                    StatCardContainer(
                                        cardId = "nutrition_meals",
                                        isEditMode = isEditMode,
                                        onHideCard = {
                                            coroutineScope.launch {
                                                preferences.toggleStatCardVisibility("nutrition_meals", false)
                                            }
                                        },
                                        index = 1,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        MetricCard(
                                            title = stringResource(R.string.records_item_nutrition_title),
                                            value = "${state.todayEntriesCount}",
                                            badgeText = stringResource(R.string.nutrition_filter_all_meals),
                                            note = stringResource(R.string.overview_metric_meals_count, state.foodEntries.size),
                                            valueIcon = Icons.Default.Restaurant,
                                            valueIconTint = MaterialTheme.colorScheme.secondary,
                                            badgeBg = MaterialTheme.colorScheme.secondaryContainer,
                                            badgeColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                            onLongClick = { isEditMode = true }
                                        )
                                    }
                                }
                            }
                        }
                        if (hasRow2) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (isProteinVisible) {
                                    StatCardContainer(
                                        cardId = "nutrition_protein",
                                        isEditMode = isEditMode,
                                        onHideCard = {
                                            coroutineScope.launch {
                                                preferences.toggleStatCardVisibility("nutrition_protein", false)
                                            }
                                        },
                                        index = 2,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        MetricCard(
                                            title = stringResource(R.string.nutrition_label_protein),
                                            value = "${state.todayProtein.toInt()}g",
                                            badgeText = "$protPct%",
                                            note = stringResource(R.string.badge_daily) + " protein",
                                            valueIcon = Icons.Default.FitnessCenter,
                                            valueIconTint = domainNutritionColor(),
                                            badgeBg = domainNutritionContainer(),
                                            badgeColor = domainNutritionOnContainer(),
                                            onLongClick = { isEditMode = true }
                                        )
                                    }
                                }
                                if (isCarbFatVisible) {
                                    StatCardContainer(
                                        cardId = "nutrition_carbfat",
                                        isEditMode = isEditMode,
                                        onHideCard = {
                                            coroutineScope.launch {
                                                preferences.toggleStatCardVisibility("nutrition_carbfat", false)
                                            }
                                        },
                                        index = 3,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        MetricCard(
                                            title = "${stringResource(R.string.nutrition_label_carb)} / ${stringResource(R.string.nutrition_label_fat)}",
                                            value = "${state.todayCarb.toInt()}g · ${state.todayFat.toInt()}g",
                                            badgeText = "$carbPct% / $fatPct%",
                                            note = "Karb / Yağ dengesi",
                                            valueIcon = Icons.Default.PieChart,
                                            valueIconTint = domainBooksColor(),
                                            badgeBg = domainBooksContainer(),
                                            badgeColor = domainBooksOnContainer(),
                                            onLongClick = { isEditMode = true }
                                        )
                                    }
                                }
                            }
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
                                    imageVector = Icons.Default.LocalFireDepartment,
                                    contentDescription = null,
                                    tint = domainNutritionColor(),
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
                                shape = rememberExpressiveButtonShape(
                                    interactionSource = targetBtnInteraction,
                                    restingCorner = M3CornerRadius.Full,
                                    pressedCorner = M3CornerRadius.ExtraSmall
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier
                                    .height(30.dp)
                                    .expressivePressScale(targetBtnInteraction)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
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

                        val carbColor = domainBooksColor()
                        val protColor = domainNutritionColor()
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
                                icon = Icons.Default.Grain
                            )
                            MacroLegendItem(
                                color = protColor,
                                label = stringResource(R.string.nutrition_label_protein_short),
                                grams = state.todayProtein.toInt(),
                                pct = todayProtPct,
                                icon = Icons.Default.FitnessCenter
                            )
                            MacroLegendItem(
                                color = fatColor,
                                label = stringResource(R.string.nutrition_label_fat_short),
                                grams = state.todayFat.toInt(),
                                pct = todayFatPct,
                                icon = Icons.Default.Opacity
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
                                imageVector = Icons.Default.PieChart,
                                contentDescription = null,
                                tint = domainNutritionColor(),
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

                        val carbColor = domainBooksColor()
                        val protColor = domainNutritionColor()
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
                            MacroLegendItem(color = carbColor, label = stringResource(R.string.nutrition_label_carb_short), grams = state.totalCarb.toInt(), pct = (carbPct * 100).toInt(), icon = Icons.Default.Grain)
                            MacroLegendItem(color = protColor, label = stringResource(R.string.nutrition_label_protein_short), grams = state.totalProtein.toInt(), pct = (protPct * 100).toInt(), icon = Icons.Default.FitnessCenter)
                            MacroLegendItem(color = fatColor, label = stringResource(R.string.nutrition_label_fat_short), grams = state.totalFat.toInt(), pct = (fatPct * 100).toInt(), icon = Icons.Default.Opacity)
                        }
                    }
                }
            }

            // Food Entries Log Header & Filters
            item(key = "nutrition_sticky_header", span = { GridItemSpan(maxLineSpan) }, contentType = "sticky_header") {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Search input
                        OutlinedTextField(
                            value = state.searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = {
                                Text(
                                    text = stringResource(R.string.nutrition_search_placeholder),
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

                        // Date & Meal filter chips row
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
                                defaultText = stringResource(R.string.nutrition_pick_date)
                            )

                            ExpressiveFilterChip(
                                selected = hasActiveDate,
                                onClick = { showDatePicker = true },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (hasActiveDate) Icons.Default.DateRange else Icons.Default.CalendarMonth,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
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

                            // Meals (M3 Expressive Connected Button Group)
                            ExpressiveConnectedButtonGroup(
                                items = NutritionMealKeys,
                                selectedItem = state.selectedMeal,
                                onItemSelected = { mealKey ->
                                    if (state.selectedMeal != mealKey) {
                                        viewModel.setMeal(mealKey)
                                    }
                                },
                                labelProvider = { mealKey ->
                                    when (mealKey) {
                                        "Breakfast" -> stringResource(R.string.nutrition_filter_breakfast)
                                        "Lunch" -> stringResource(R.string.nutrition_filter_lunch)
                                        "Dinner" -> stringResource(R.string.nutrition_filter_dinner)
                                        "Other" -> stringResource(R.string.nutrition_filter_snack)
                                        else -> stringResource(R.string.nutrition_filter_all_meals)
                                    }
                                },
                                iconProvider = { mealKey, _ ->
                                    when (mealKey) {
                                        "Breakfast" -> Icons.Default.BakeryDining
                                        "Lunch" -> Icons.Default.LunchDining
                                        "Dinner" -> Icons.Default.DinnerDining
                                        "Other" -> Icons.Default.Fastfood
                                        else -> null
                                    }
                                }
                            )

                            // High protein
                            ExpressiveFilterChip(
                                selected = state.onlyHighProtein,
                                onClick = {
                                    viewModel.toggleHighProtein()
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.FitnessCenter,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                label = { Text(">20g Protein", style = MaterialTheme.typography.labelMedium) }
                            )

                            // Clear filter button
                            if (state.selectedDateFilter != null || state.fromDateFilter != null || state.selectedMeal != "all" || state.onlyHighProtein || state.searchQuery.isNotEmpty()) {
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
                            ContainedLoadingIndicator(size = 44.dp)
                        }
                    }
                }
            } else if (state.filteredFoods.isEmpty()) {
                item(key = "nutrition_empty", span = { GridItemSpan(maxLineSpan) }, contentType = "empty") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.nutrition_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                itemsIndexed(
                    items = state.filteredFoods,
                    key = { _, food -> food.food_entry_id.ifBlank { "${food.displayName}_${food.displayDate}_${food.meal}_${food.calories}" } },
                    contentType = { _, _ -> "food_row" }
                ) { index, food ->
                    if (isWideScreen) {
                        Surface(
                            modifier = expressiveItemModifier().fillMaxWidth(),
                            shape = M3Shapes.CornerMedium,
                            color = MaterialTheme.colorScheme.surfaceContainerLow
                        ) {
                            FoodEntryRowItem(
                                item = food,
                                onClick = { selectedFood = food }
                            )
                        }
                    } else {
                        LazyCardRowContainer(
                            index = index,
                            totalCount = state.filteredFoods.size,
                            modifier = expressiveItemModifier()
                        ) {
                            FoodEntryRowItem(
                                item = food,
                                onClick = { selectedFood = food }
                            )
                        }
                    }
                }
            }

            item(key = "nutrition_bottom_spacer", span = { GridItemSpan(maxLineSpan) }, contentType = "spacer") { Spacer(modifier = Modifier.height(24.dp)) }
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
                    if (selectedFood != null) {
                        Modifier.FoodEntryDetailPaneCard(
                            item = selectedFood!!
                        ) { selectedFood = null }
                    } else {
                        DetailPlaceholderPane(
                            icon = Icons.Default.Restaurant,
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
            cards = nutritionStatCards,
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
        "breakfast" -> Icons.Default.BakeryDining
        "lunch" -> Icons.Default.LunchDining
        "dinner" -> Icons.Default.DinnerDining
        else -> Icons.Default.Restaurant
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
    onClick: () -> Unit = {}
) {
    val mealIcon = getMealIcon(item.meal)
    val mealLabel = formatMealName(item.meal)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .expressiveBounceClick(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // M3 Meal Avatar Container (40dp, optical roundness: 8.dp CornerSmall inside 16.dp CornerLarge card)
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(M3Shapes.CornerSmall)
                .background(domainNutritionContainer()),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = mealIcon,
                contentDescription = null,
                tint = domainNutritionOnContainer(),
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
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
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${item.displayAmount} · ${item.displayDate}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "${item.calories.toInt()} kcal",
                style = MaterialTheme.typography.titleSmallEmphasized,
                color = domainNutritionColor(),
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
    }
}

@Composable
fun FoodEntryCard(item: FoodEntryDto) {
    FoodEntryRowItem(item = item)
}

