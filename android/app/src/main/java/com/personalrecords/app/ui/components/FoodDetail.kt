package com.personalrecords.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import com.personalrecords.app.R
import com.personalrecords.app.core.network.model.FoodEntryDto
import com.personalrecords.app.core.theme.*
import java.util.Date
import java.util.Locale

// Food entry detail: sheet and side-pane content.

internal fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, text.trim())
    clipboard.setPrimaryClip(clip)
    // Android 13+ shows its own copy confirmation; a toast there just lands under the floating bar.
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, context.getString(R.string.action_copied), Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun localizedMealName(meal: String): String {
    return when (meal.trim().lowercase()) {
        "breakfast" -> stringResource(R.string.nutrition_filter_breakfast)
        "lunch" -> stringResource(R.string.nutrition_filter_lunch)
        "dinner" -> stringResource(R.string.nutrition_filter_dinner)
        "snack", "other" -> stringResource(R.string.nutrition_filter_snack)
        else -> meal.ifBlank { stringResource(R.string.nutrition_filter_all_meals) }
    }
}

private fun mealIconFor(meal: String): ImageVector {
    return when (meal.trim().lowercase()) {
        "breakfast" -> Icons.Rounded.BakeryDining
        "lunch" -> Icons.Rounded.LunchDining
        "dinner" -> Icons.Rounded.DinnerDining
        else -> Icons.Rounded.Restaurant
    }
}

@Composable
fun FoodEntryDetailContent(
    item: FoodEntryDto,
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val mealLabel = localizedMealName(item.meal)
    val mealIcon = mealIconFor(item.meal)

    val carbKcal = item.carbohydrate * 4
    val protKcal = item.protein * 4
    val fatKcal = item.fat * 9
    val totalMacroKcal = carbKcal + protKcal + fatKcal
    val carbPct = if (totalMacroKcal > 0) (carbKcal / totalMacroKcal).toFloat() else 0.33f
    val protPct = if (totalMacroKcal > 0) (protKcal / totalMacroKcal).toFloat() else 0.33f
    val fatPct = if (totalMacroKcal > 0) (fatKcal / totalMacroKcal).toFloat() else 0.34f

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header row: Meal badge pill + Date
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = M3Shapes.CornerFull,
                    color = recordCategoryColors(RecordCategory.Nutrition).container
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = mealIcon,
                            contentDescription = null,
                            tint = recordCategoryColors(RecordCategory.Nutrition).onContainer,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = mealLabel,
                            style = MaterialTheme.typography.labelMediumEmphasized,
                            color = recordCategoryColors(RecordCategory.Nutrition).onContainer
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                if (item.displayDate.isNotBlank()) {
                    Text(
                        text = item.displayDate,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Food Name & Portion
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = item.displayName,
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (item.displayAmount.isNotBlank()) {
                    Text(
                        text = item.displayAmount,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Calorie & Macro Summary Card (Optical roundness: 12.dp CornerMedium inside 28.dp sheet)
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.nutrition_macro_title),
                            style = MaterialTheme.typography.labelLargeEmphasized,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(R.string.calories_value, item.calories.toInt().toString()),
                            style = MaterialTheme.typography.headlineSmallEmphasized,
                            color = recordCategoryColors(RecordCategory.Nutrition).accent
                        )
                    }

                    val carbColor = recordCategoryColors(RecordCategory.Books).accent
                    val protColor = recordCategoryColors(RecordCategory.Nutrition).accent
                    val fatColor = ratingColors().accent

                    val segments = if (totalMacroKcal > 0) {
                        listOf(
                            carbPct to carbColor,
                            protPct to protColor,
                            fatPct to fatColor
                        )
                    } else null

                    var sheetAnimStarted by remember { mutableStateOf(false) }
                    LaunchedEffect(item) {
                        sheetAnimStarted = true
                    }
                    val animatedProgress = animateFloatAsState(
                        targetValue = if (sheetAnimStarted) 1f else 0f,
                        animationSpec = M3ExpressiveMotion.defaultSpatial(),
                        label = "food_detail_macro_prog"
                    )

                    // MD3 Expressive Segmented Linear Progress Indicator
                    SegmentedLinearProgressIndicator(
                        progress = { animatedProgress.value },
                        segments = segments,
                        modifier = Modifier.fillMaxWidth(),
                        barHeight = 8.dp,
                        gap = 3.dp,
                        trackGap = 4.dp,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )

                    // 3 Macro columns
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MacroDetailStat(
                            color = recordCategoryColors(RecordCategory.Books).accent,
                            label = stringResource(R.string.nutrition_label_carb),
                            grams = item.carbohydrate,
                            pct = (carbPct * 100).toInt()
                        )
                        MacroDetailStat(
                            color = recordCategoryColors(RecordCategory.Nutrition).accent,
                            label = stringResource(R.string.nutrition_label_protein),
                            grams = item.protein,
                            pct = (protPct * 100).toInt()
                        )
                        MacroDetailStat(
                            color = ratingColors().accent,
                            label = stringResource(R.string.nutrition_label_fat),
                            grams = item.fat,
                            pct = (fatPct * 100).toInt()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Labels for the copied text, resolved while composing (not in the click handler).
            val copyCalories = stringResource(R.string.calories_value, item.calories.toInt().toString())
            val copyCarb = stringResource(R.string.nutrition_label_carb)
            val copyProtein = stringResource(R.string.nutrition_label_protein)
            val copyFat = stringResource(R.string.nutrition_label_fat)
            // Actions: Copy & Close
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val copyStr = buildString {
                            append(item.displayName)
                            if (item.displayAmount.isNotBlank()) append(" (${item.displayAmount})")
                            append("\n$copyCalories · ")
                            append("$copyCarb ${item.carbohydrate.toInt()}g · $copyProtein ${item.protein.toInt()}g · $copyFat ${item.fat.toInt()}g")
                        }
                        copyToClipboard(context, item.displayName, copyStr)
                    },
                    shapes = expressiveButtonShapes(),
                    modifier = Modifier
                        .weight(1f)
                        ,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.notifications_copy_text),
                        style = MaterialTheme.typography.labelLargeEmphasized,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (onClose != null) {
                    Button(
                        onClick = onClose,
                        shapes = expressiveButtonShapes(),
                        modifier = Modifier
                            .weight(1f)
                            ,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.action_close),
                            style = MaterialTheme.typography.labelLargeEmphasized,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodEntryDetailBottomSheet(
    item: FoodEntryDto,
    onDismiss: () -> Unit
) {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        FoodEntryDetailContent(
            item = item,
            onClose = onDismiss,
            modifier = Modifier.navigationBarsPadding()
        )
    }
}

@Composable
fun Modifier.FoodEntryDetailPaneCard(
    item: FoodEntryDto,
    onClose: (() -> Unit)? = null
) {
    Surface(
        modifier = fillMaxSize(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            FoodEntryDetailContent(
                item = item,
                onClose = onClose
            )
        }
    }
}

@Composable
private fun MacroDetailStat(color: Color, label: String, grams: Double, pct: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(M3Shapes.CornerFull)
                    .background(color)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = "${String.format(Locale.US, "%.1f", grams)}g",
            style = MaterialTheme.typography.titleSmallEmphasized,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "%$pct",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
