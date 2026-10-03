package com.personalrecords.app.ui.screens.nutrition

import androidx.compose.animation.core.animateFloatAsState
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
                            viewModel.setDateFilter(formatted)
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

    if (showTargetDialog) {
        var tempTarget by remember { mutableStateOf(state.calorieTarget?.toString() ?: "") }
        AlertDialog(
            onDismissRequest = { showTargetDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.nutrition_target_dialog_title),
                    style = MaterialTheme.typography.headlineSmall
                )
            },
            text = {
                OutlinedTextField(
                    value = tempTarget,
                    onValueChange = { tempTarget = it },
                    label = { Text(stringResource(R.string.nutrition_target_dialog_label)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small
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
                    Text(stringResource(R.string.action_save), style = MaterialTheme.typography.labelLarge)
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
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.title_nutrition),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
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
        val filteredFoods = remember(state.foodEntries, state.searchQuery, state.selectedDateFilter, state.selectedMeal, state.onlyHighProtein) {
            state.foodEntries.filter { item ->
                val matchesSearch = state.searchQuery.isBlank() ||
                    item.displayName.contains(state.searchQuery, ignoreCase = true) ||
                    item.displayAmount.contains(state.searchQuery, ignoreCase = true)
                val matchesDate = state.selectedDateFilter == null || item.displayDate == state.selectedDateFilter
                val matchesMeal = state.selectedMeal == "all" || item.meal.equals(state.selectedMeal, ignoreCase = true)
                val matchesProtein = !state.onlyHighProtein || item.protein >= 20.0
                matchesSearch && matchesDate && matchesMeal && matchesProtein
            }
        }

        var isRefreshing by remember { mutableStateOf(false) }
        LaunchedEffect(state.isLoading) {
            if (!state.isLoading) isRefreshing = false
        }

        androidx.compose.material3.pulltorefresh.PullToRefreshBox(
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
                contentPadding = PaddingValues(top = 10.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
            // Calorie Budget Card (Card MD3)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
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
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            FilledTonalButton(
                                onClick = { showTargetDialog = true },
                                shape = MaterialTheme.shapes.small,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(30.dp)
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
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        val target = state.calorieTarget
                        val progressPct = if (target != null && target > 0) {
                            (state.todayCalories / target).toFloat().coerceIn(0f, 1f)
                        } else 0f
                        val animatedProgress by animateFloatAsState(targetValue = progressPct, label = "prog")

                        val barColor = when {
                            progressPct >= 1f -> MaterialTheme.colorScheme.error
                            progressPct > 0.85f -> domainRatingColor()
                            else -> domainNutritionColor()
                        }

                        // MD3 Linear Progress Indicator
                        LinearProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(CircleShape),
                            color = barColor,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
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
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = if (rem >= 0) domainNutritionColor() else MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            // Macro Breakdown Card (MD3 Card)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    border = null
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = stringResource(R.string.nutrition_macro_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
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
                                .clip(CircleShape)
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
                            MacroLegendItem(color = carbColor, label = stringResource(R.string.nutrition_label_carb), grams = state.totalCarb.toInt(), pct = (carbPct * 100).toInt())
                            MacroLegendItem(color = protColor, label = stringResource(R.string.nutrition_label_protein), grams = state.totalProtein.toInt(), pct = (protPct * 100).toInt())
                            MacroLegendItem(color = fatColor, label = stringResource(R.string.nutrition_label_fat), grams = state.totalFat.toInt(), pct = (fatPct * 100).toInt())
                        }
                    }
                }
            }

            // Food Entries Log Header & Filters (Sticky Header)
            stickyHeader {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
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

                        // Date & Meal filter chips row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Date picker chip
                            AssistChip(
                                onClick = { showDatePicker = true },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.CalendarMonth,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                label = {
                                    Text(
                                        text = state.selectedDateFilter ?: stringResource(R.string.nutrition_pick_date),
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                },
                                shape = MaterialTheme.shapes.small,
                                border = null,
                                colors = borderlessAssistChipColors()
                            )

                            // Meals
                            listOf(
                                Triple("all", stringResource(R.string.nutrition_filter_all_meals), null),
                                Triple("Breakfast", stringResource(R.string.nutrition_filter_breakfast), Icons.Default.BakeryDining),
                                Triple("Lunch", stringResource(R.string.nutrition_filter_lunch), Icons.Default.LunchDining),
                                Triple("Dinner", stringResource(R.string.nutrition_filter_dinner), Icons.Default.DinnerDining),
                                Triple("Other", stringResource(R.string.nutrition_filter_snack), Icons.Default.Fastfood)
                            ).forEach { (mealKey, label, icon) ->
                                FilterChip(
                                    selected = state.selectedMeal == mealKey,
                                    onClick = { viewModel.setMeal(mealKey) },
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

                            // High protein
                            FilterChip(
                                selected = state.onlyHighProtein,
                                onClick = { viewModel.toggleHighProtein() },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.FitnessCenter,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                label = { Text(">20g Protein", style = MaterialTheme.typography.labelMedium) },
                                shape = MaterialTheme.shapes.small,
                                border = null,
                                colors = borderlessFilterChipColors()
                            )

                            // Clear filter button
                            if (state.selectedDateFilter != null || state.selectedMeal != "all" || state.onlyHighProtein || state.searchQuery.isNotEmpty()) {
                                AssistChip(
                                    onClick = { viewModel.resetFilters() },
                                    label = {
                                        Text(
                                            text = stringResource(R.string.action_clear),
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
                }
            }



            // Food Items List
            if (state.isLoading && state.foodEntries.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            } else if (filteredFoods.isEmpty()) {
                item {
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
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        border = null
                    ) {
                        Column {
                            filteredFoods.forEachIndexed { index, food ->
                                if (index > 0) {
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                                        modifier = Modifier.padding(horizontal = 12.dp)
                                    )
                                }
                                FoodEntryRowItem(item = food)
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
          }
        }
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
                .clip(CircleShape)
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
fun FoodEntryRowItem(item: FoodEntryDto) {
    val mealIcon = getMealIcon(item.meal)
    val mealLabel = formatMealName(item.meal)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // M3 Meal Avatar Container (40dp)
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(MaterialTheme.shapes.small)
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
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Surface(
                    shape = CircleShape,
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
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
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

