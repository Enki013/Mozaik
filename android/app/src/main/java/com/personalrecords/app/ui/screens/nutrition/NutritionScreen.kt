package com.personalrecords.app.ui.screens.nutrition

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.graphics.Color

import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personalrecords.app.R
import com.personalrecords.app.core.network.model.FoodEntryDto
import com.personalrecords.app.core.theme.*
import com.personalrecords.app.ui.components.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun NutritionScreen(
    viewModel: NutritionViewModel,
    onNavigateBack: (() -> Unit)? = null
) {
    val state by viewModel.uiState.collectAsState()
    var showTargetDialog by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var selectedFood by remember { mutableStateOf<FoodEntryDto?>(null) }
    var filteredFoods by remember { mutableStateOf<List<FoodEntryDto>>(emptyList()) }
    var isFiltering by remember { mutableStateOf(false) }

    LaunchedEffect(
        state.foodEntries,
        state.searchQuery,
        state.selectedDateFilter,
        state.fromDateFilter,
        state.toDateFilter,
        state.selectedMeal,
        state.onlyHighProtein
    ) {
        if (state.foodEntries.isEmpty()) {
            filteredFoods = emptyList()
            isFiltering = false
            return@LaunchedEffect
        }
        val entries = state.foodEntries
        val query = state.searchQuery
        val singleDate = state.selectedDateFilter
        val fromDate = state.fromDateFilter
        val toDate = state.toDateFilter
        val meal = state.selectedMeal
        val highProt = state.onlyHighProtein

        if (isFiltering) {
            delay(160)
        }
        val result = withContext(Dispatchers.Default) {
            entries.filter { item ->
                val matchesSearch = query.isBlank() ||
                    item.displayName.contains(query, ignoreCase = true) ||
                    item.displayAmount.contains(query, ignoreCase = true)
                val matchesDate = when {
                    singleDate != null -> item.displayDate == singleDate
                    fromDate != null && toDate != null -> item.displayDate in fromDate..toDate
                    fromDate != null -> item.displayDate >= fromDate
                    toDate != null -> item.displayDate <= toDate
                    else -> true
                }
                val matchesMeal = meal == "all" || item.meal.equals(meal, ignoreCase = true)
                val matchesProtein = !highProt || item.protein >= 20.0
                matchesSearch && matchesDate && matchesMeal && matchesProtein
            }
        }
        filteredFoods = result
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
        modifier = Modifier.fillMaxSize(),
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
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                contentPadding = PaddingValues(top = 10.dp, bottom = 144.dp),
                verticalArrangement = Arrangement.Top
            ) {
            // Calorie Budget Card (Card MD3)
            item {
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
                                    imageVector = Icons.Default.Adjust,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
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
                        val animatedProgress by animateFloatAsState(
                            targetValue = progressPct,
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

                        // MD3 Expressive Wavy Linear Progress Indicator
                        LinearWavyProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier.fillMaxWidth(),
                            color = barColor,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            segments = macroSegments
                        )
                        Spacer(modifier = Modifier.height(6.dp))

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
                                pct = todayCarbPct
                            )
                            MacroLegendItem(
                                color = protColor,
                                label = stringResource(R.string.nutrition_label_protein_short),
                                grams = state.todayProtein.toInt(),
                                pct = todayProtPct
                            )
                            MacroLegendItem(
                                color = fatColor,
                                label = stringResource(R.string.nutrition_label_fat_short),
                                grams = state.todayFat.toInt(),
                                pct = todayFatPct
                            )
                        }
                    }
                }
            }

            // Macro Breakdown Card (MD3 Card)
            item {
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
                        Text(
                            text = stringResource(R.string.nutrition_macro_title),
                            style = MaterialTheme.typography.titleMediumEmphasized,
                            color = MaterialTheme.colorScheme.onSurface
                        )
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

                        // Multi-segment Bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(M3Shapes.CornerFull)
                        ) {
                            Box(modifier = Modifier.fillMaxHeight().weight(carbPct.coerceAtLeast(0.01f)).background(carbColor))
                            Box(modifier = Modifier.fillMaxHeight().weight(protPct.coerceAtLeast(0.01f)).background(protColor))
                            Box(modifier = Modifier.fillMaxHeight().weight(fatPct.coerceAtLeast(0.01f)).background(fatColor))
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            MacroLegendItem(color = carbColor, label = stringResource(R.string.nutrition_label_carb_short), grams = state.totalCarb.toInt(), pct = (carbPct * 100).toInt())
                            MacroLegendItem(color = protColor, label = stringResource(R.string.nutrition_label_protein_short), grams = state.totalProtein.toInt(), pct = (protPct * 100).toInt())
                            MacroLegendItem(color = fatColor, label = stringResource(R.string.nutrition_label_fat_short), grams = state.totalFat.toInt(), pct = (fatPct * 100).toInt())
                        }
                    }
                }
            }

            // Food Entries Log Header & Filters (Sticky Header)
            stickyHeader {
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

                            // Meals (M3 Expressive Connected Button Group)
                            val mealItems = listOf(
                                Triple("all", stringResource(R.string.nutrition_filter_all_meals), null),
                                Triple("Breakfast", stringResource(R.string.nutrition_filter_breakfast), Icons.Default.BakeryDining),
                                Triple("Lunch", stringResource(R.string.nutrition_filter_lunch), Icons.Default.LunchDining),
                                Triple("Dinner", stringResource(R.string.nutrition_filter_dinner), Icons.Default.DinnerDining),
                                Triple("Other", stringResource(R.string.nutrition_filter_snack), Icons.Default.Fastfood)
                            )
                            val currentMealItem = mealItems.firstOrNull { it.first == state.selectedMeal } ?: mealItems.first()
                            ExpressiveConnectedButtonGroup(
                                items = mealItems,
                                selectedItem = currentMealItem,
                                onItemSelected = { (mealKey, _, _) ->
                                    if (state.selectedMeal != mealKey) {
                                        isFiltering = true
                                        viewModel.setMeal(mealKey)
                                    }
                                },
                                labelProvider = { it.second },
                                iconProvider = { item, _ -> item.third }
                            )

                            // High protein
                            ExpressiveFilterChip(
                                selected = state.onlyHighProtein,
                                onClick = {
                                    isFiltering = true
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
                            if (state.selectedDateFilter != null || state.selectedMeal != "all" || state.onlyHighProtein || state.searchQuery.isNotEmpty()) {
                                ExpressiveAssistChip(
                                    onClick = {
                                        isFiltering = true
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
            if ((state.isLoading && state.foodEntries.isEmpty()) || isFiltering) {
                item(key = "nutrition_loading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        ContainedLoadingIndicator(size = 44.dp)
                    }
                }
            } else if (filteredFoods.isEmpty()) {
                item(key = "nutrition_empty") {
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
                    items = filteredFoods,
                    key = { index, food -> "${food.food_entry_id}_$index" }
                ) { index, food ->
                    LazyCardRowContainer(
                        index = index,
                        totalCount = filteredFoods.size
                    ) {
                        FoodEntryRowItem(
                            item = food,
                            onClick = { selectedFood = food }
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
          }
        }
    }

    selectedFood?.let { food ->
        FoodEntryDetailBottomSheet(
            item = food,
            onDismiss = { selectedFood = null }
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
fun MacroLegendItem(color: Color, label: String, grams: Int, pct: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(M3Shapes.CornerFull)
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
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

