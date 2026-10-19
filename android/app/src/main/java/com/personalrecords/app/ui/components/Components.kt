package com.personalrecords.app.ui.components

import android.annotation.SuppressLint
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import com.personalrecords.app.core.theme.LocalReducedMotion
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.compose.ui.graphics.lerp
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.geometry.Offset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.pullToRefreshIndicator
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import com.personalrecords.app.R
import com.personalrecords.app.core.network.model.ActivityItemDto
import com.personalrecords.app.core.network.model.BookDto
import com.personalrecords.app.core.network.model.FilmDto
import com.personalrecords.app.core.network.model.FoodEntryDto
import com.personalrecords.app.core.network.model.HealthRecordDto
import com.personalrecords.app.core.network.model.NotificationDto
import com.personalrecords.app.core.theme.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * MD3 Elevated Metric Card for dashboard overviews and key statistics.
 */
@Composable
fun MetricCard(
    title: String,
    value: String,
    badgeText: String,
    note: String,
    modifier: Modifier = Modifier,
    badgeBg: Color = MaterialTheme.colorScheme.secondaryContainer,
    badgeColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    valueIcon: ImageVector? = null,
    valueIconTint: Color = MaterialTheme.colorScheme.primary,
    noteIcon: ImageVector? = null,
    noteIconTint: Color? = null,
    onClick: (() -> Unit)? = null
) {
    val cardModifier = if (onClick != null) {
        modifier.fillMaxWidth().expressiveBounceClick(onClick = onClick)
    } else {
        modifier.fillMaxWidth()
    }
    Card(
        modifier = cardModifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = null
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmallEmphasized,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (badgeText.isNotBlank()) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Surface(
                        shape = M3Shapes.CornerFull,
                        color = badgeBg
                    ) {
                        Text(
                            text = badgeText,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = badgeColor,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (valueIcon != null) {
                    Icon(
                        imageVector = valueIcon,
                        contentDescription = null,
                        tint = valueIconTint,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineSmallEmphasized,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (noteIcon != null) {
                    Icon(
                        imageVector = noteIcon,
                        contentDescription = null,
                        tint = noteIconTint ?: MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(11.dp)
                    )
                }
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * MD3 Poster / Cover Thumbnail with subtle outline and tonal fallback.
 */
@Composable
fun PosterThumbnail(
    imageUrl: String?,
    fallbackIcon: ImageVector = Icons.Default.BrokenImage,
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier,
    width: Int = 36,
    height: Int = 52
) {
    Box(
        modifier = modifier
            .size(width.dp, height.dp)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = fallbackIcon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size((minOf(width, height) / 2).coerceAtLeast(18).dp)
        )
        if (!imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * MD3 Gold Star Rating Badge formatted as a pill.
 */
@SuppressLint("DefaultLocale")
@Composable
fun RatingBadge(
    rating: Double,
    modifier: Modifier = Modifier
) {
    if (rating > 0) {
        Surface(
            shape = M3Shapes.CornerFull,
            color = domainRatingContainer(),
            modifier = modifier
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = null,
                    tint = domainRatingOnContainer(),
                    modifier = Modifier.size(10.dp)
                )
                Text(
                    text = if (rating % 1.0 == 0.0) rating.toInt().toString() else String.format("%.1f", rating),
                    style = MaterialTheme.typography.labelSmallEmphasized,
                    color = domainRatingOnContainer(),
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

/**
 * MD3 Section Header with optional action or view-all text.
 */
//@Composable
//fun SectionHeader(
//    title: String,
//    modifier: Modifier = Modifier,
//    actionText: String? = null,
//    onActionClick: (() -> Unit)? = null
//) {
//    Row(
//        modifier = modifier
//            .fillMaxWidth()
//            .padding(vertical = 4.dp),
//        horizontalArrangement = Arrangement.SpaceBetween,
//        verticalAlignment = Alignment.CenterVertically
//    ) {
//        Text(
//            text = title,
//            style = MaterialTheme.typography.titleMedium,
//            fontWeight = FontWeight.SemiBold,
//            color = MaterialTheme.colorScheme.onSurface
//        )
//        if (actionText != null && onActionClick != null) {
//            TextButton(
//                onClick = onActionClick,
//                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
//            ) {
//                Text(
//                    text = actionText,
//                    style = MaterialTheme.typography.labelLarge,
//                    color = MaterialTheme.colorScheme.primary
//                )
//            }
//        }
//    }
//}

/**
 * MD3 Nutritional Macronutrient Pill.
 */
//@Composable
//fun MacroPill(
//    name: String,
//    amount: String,
//    containerColor: Color,
//    contentColor: Color,
//    modifier: Modifier = Modifier
//) {
//    Surface(
//        shape = CircleShape,
//        color = containerColor,
//        modifier = modifier
//    ) {
//        Row(
//            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.5.dp),
//            verticalAlignment = Alignment.CenterVertically,
//            horizontalArrangement = Arrangement.spacedBy(4.dp)
//        ) {
//            Text(
//                text = name,
//                style = MaterialTheme.typography.labelSmall,
//                fontWeight = FontWeight.Normal,
//                color = contentColor.copy(alpha = 0.8f)
//            )
//            Text(
//                text = amount,
//                style = MaterialTheme.typography.labelMedium,
//                fontWeight = FontWeight.Bold,
//                color = contentColor
//            )
//        }
//    }
//}

/**
 * Borderless FilterChip colors with clean M3 tonal container background.
 */
@Composable
fun borderlessFilterChipColors() = FilterChipDefaults.filterChipColors(
    containerColor = MaterialTheme.colorScheme.surfaceContainer,
    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
    selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer
)

/**
 * Borderless AssistChip colors with clean M3 tonal container background.
 */
@Composable
fun borderlessAssistChipColors(
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) = AssistChipDefaults.assistChipColors(
    containerColor = containerColor,
    labelColor = labelColor,
    leadingIconContentColor = labelColor
)

/**
 * MD3 Expressive Interactive Button Shape helper (`ButtonDefaults.shapes()`).
 *
 * Morphs button corner radius between [restingCorner] and [pressedCorner] (sharper squircle)
 * while pressed using the M3 Expressive Fast Spatial spring (`damping = 0.6f, stiffness = 800f`).
 */
@Composable
fun rememberExpressiveButtonShape(
    interactionSource: InteractionSource,
    restingCorner: Dp = M3CornerRadius.Medium,
    pressedCorner: Dp = M3CornerRadius.ExtraSmall
): RoundedCornerShape {
    val isPressed by interactionSource.collectIsPressedAsState()
    val corner by animateDpAsState(
        targetValue = if (isPressed) pressedCorner else restingCorner,
        animationSpec = M3ExpressiveMotion.fastSpatial(),
        label = "expressive_btn_corner"
    )
    return RoundedCornerShape(corner)
}

/**
 * MD3 Expressive Filter Chip (`FilterChipDefaults.shapes()`).
 *
 * Implements authentic M3 Expressive 3-state corner morphing from `ChipSamples.kt`:
 * - Unselected resting: `CornerSmall` (8dp)
 * - Pressed (`isPressed`): `CornerExtraSmall` (4dp sharp squircle)
 * - Selected (`selected`): `CornerFull` (24dp full pill)
 * Accompanied by Fast Spatial spring scale physics and optional animated checkmark when no custom [leadingIcon] is supplied.
 */
@Composable
fun ExpressiveFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    colors: SelectableChipColors = borderlessFilterChipColors()
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val targetCorner = when {
        isPressed -> M3CornerRadius.ExtraSmall
        selected -> M3CornerRadius.Full
        else -> M3CornerRadius.Small
    }
    val animatedCorner by animateDpAsState(
        targetValue = targetCorner,
        animationSpec = M3ExpressiveMotion.fastSpatial(),
        label = "chip_corner_morph"
    )
    val animatedScaleState = animateFloatAsState(
        targetValue = when {
            isPressed -> 0.95f
            selected -> 1.03f
            else -> 1f
        },
        animationSpec = M3ExpressiveMotion.fastSpatial(),
        label = "chip_scale_morph"
    )

    FilterChip(
        selected = selected,
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        label = label,
        shape = RoundedCornerShape(animatedCorner),
        interactionSource = interactionSource,
        modifier = modifier.graphicsLayer {
            val s = animatedScaleState.value
            scaleX = s
            scaleY = s
        },
        border = null,
        colors = colors,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon
    )
}

/**
 * MD3 Expressive Assist Chip (`AssistChipDefaults` with interactive press corner morphing).
 *
 * Morphs corner curvature from `CornerSmall` (8dp) to `CornerExtraSmall` (4dp) on press
 * with M3 Expressive Fast Spatial spring bounce and tactile feedback.
 */
@Composable
fun ExpressiveAssistChip(
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    colors: ChipColors = borderlessAssistChipColors()
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val animatedCorner by animateDpAsState(
        targetValue = if (isPressed) M3CornerRadius.ExtraSmall else M3CornerRadius.Small,
        animationSpec = M3ExpressiveMotion.fastSpatial(),
        label = "assist_chip_corner"
    )
    val animatedScaleState = animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = M3ExpressiveMotion.fastSpatial(),
        label = "assist_chip_scale"
    )

    AssistChip(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        label = label,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(animatedCorner),
        border = null,
        colors = colors,
        modifier = modifier.graphicsLayer {
            val s = animatedScaleState.value
            scaleX = s
            scaleY = s
        }
    )
}

/**
 * MD3 Expressive Connected Button Group (`SingleSelectConnectedButtonGroupSample` from `ButtonGroupSamples.kt`).
 *
 * Implements `ButtonGroupDefaults.connectedLeadingButtonShapes()`, `connectedMiddleButtonShapes()`,
 * and `connectedTrailingButtonShapes()`:
 * - Outer group ends have `CornerFull` (24dp)
 * - Inner adjacent edges have `CornerSmall` (8dp) when unselected
 * - Selected button (`checked = true`) morphs all 4 corners to `CornerFull` (24dp pill)
 * - Pressed button (`isPressed = true`) morphs inner corners to `CornerExtraSmall` (4dp)
 */
@Composable
fun <T> ExpressiveConnectedButtonGroup(
    items: List<T>,
    selectedItem: T,
    onItemSelected: (T) -> Unit,
    labelProvider: @Composable (T) -> String,
    modifier: Modifier = Modifier,
    fillWidth: Boolean = false,
    iconProvider: (@Composable (T, Boolean) -> ImageVector?)? = null
) {
    val haptic = LocalHapticFeedback.current
    val selectedBg = MaterialTheme.colorScheme.secondaryContainer
    val unselectedBg = MaterialTheme.colorScheme.surfaceContainer
    val selectedFg = MaterialTheme.colorScheme.onSecondaryContainer
    val unselectedFg = MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEachIndexed { index, item ->
            val selected = item == selectedItem
            val interactionSource = remember { MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()

            val outerStart = index == 0
            val outerEnd = index == items.lastIndex

            val selectionProgress by animateFloatAsState(
                targetValue = if (selected) 1f else 0f,
                animationSpec = M3ExpressiveMotion.fastEffects(),
                label = "connected_selection"
            )

            val baseStartCorner = if (outerStart) 20f else if (isPressed) 4f else 8f
            val baseEndCorner = if (outerEnd) 20f else if (isPressed) 4f else 8f
            val startCorner = (baseStartCorner + (20f - baseStartCorner) * selectionProgress).dp
            val endCorner = (baseEndCorner + (20f - baseEndCorner) * selectionProgress).dp
            val horizPad = (11f + 3f * selectionProgress).dp
            val containerColor = lerp(unselectedBg, selectedBg, selectionProgress)
            val contentColor = lerp(unselectedFg, selectedFg, selectionProgress)

            val shape = RoundedCornerShape(
                topStart = startCorner,
                bottomStart = startCorner,
                topEnd = endCorner,
                bottomEnd = endCorner
            )

            val itemModifier = if (fillWidth) {
                Modifier.weight(1f + 0.12f * selectionProgress)
            } else {
                Modifier
            }

            Surface(
                shape = shape,
                color = containerColor,
                contentColor = contentColor,
                modifier = itemModifier
                    .height(32.dp)
                    .graphicsLayer {
                        val pressScale = if (isPressed) 0.95f else 1f
                        scaleX = pressScale
                        scaleY = pressScale
                    }
                    .clip(shape)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = ripple(),
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onItemSelected(item)
                        }
                    )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = horizPad),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    val icon = iconProvider?.invoke(item, selected)
                    if (icon != null) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                    }
                    Text(
                        text = labelProvider(item),
                        style = if (selected) MaterialTheme.typography.labelMediumEmphasized else MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

/**
 * Evaluates the polar radius (normalized around `1.0f`) for each of the 7 official
 * Material 3 Expressive `LoadingIndicatorDefaults.IndeterminateIndicatorPolygons` shapes
 * from `MaterialShapes`:
 * 0 -> `SoftBurst` (10-pointed soft starburst)
 * 1 -> `Cookie9Sided` (9-sided scalloped cookie)
 * 2 -> `Pentagon` (rounded 5-sided regular polygon)
 * 3 -> `Pill` (tilted stadium / capsule)
 * 4 -> `Sunny` (8-pointed warm sun star)
 * 5 -> `Cookie4Sided` (4-lobed clover / squircle)
 * 6 -> `Cookie7Sided` (7-sided scalloped flower)
 */
private fun m3ExpressivePolygonRadius(shapeIndex: Int, theta: Float): Float {
    return when (shapeIndex % 7) {
        0 -> {
            // SoftBurst: 10-pointed soft starburst
            1f + 0.14f * kotlin.math.cos(10f * theta) - 0.03f * kotlin.math.cos(20f * theta)
        }
        1 -> {
            // Cookie9Sided: 9-sided rounded cookie
            1f + 0.13f * kotlin.math.cos(9f * theta) + 0.02f * kotlin.math.cos(18f * theta)
        }
        2 -> {
            // Pentagon: rounded 5-sided regular pentagon
            1f + 0.15f * kotlin.math.cos(5f * theta) - 0.035f * kotlin.math.cos(10f * theta)
        }
        3 -> {
            // Pill: stadium / elongated 2-lobe capsule
            val c = kotlin.math.cos(theta)
            val s = kotlin.math.sin(theta)
            val rx = 1.18f
            val ry = 0.78f
            (rx * ry) / kotlin.math.sqrt((ry * c) * (ry * c) + (rx * s) * (rx * s))
        }
        4 -> {
            // Sunny: 8-pointed sunny star
            1f + 0.17f * kotlin.math.cos(8f * theta) - 0.025f * kotlin.math.cos(16f * theta)
        }
        5 -> {
            // Cookie4Sided: 4-lobed clover / cookie
            1f + 0.22f * kotlin.math.cos(4f * theta) - 0.03f * kotlin.math.cos(8f * theta)
        }
        else -> {
            // Cookie7Sided: 7-sided scalloped flower
            1f + 0.15f * kotlin.math.cos(7f * theta) + 0.02f * kotlin.math.cos(14f * theta)
        }
    }
}

/**
 * Spring-like morph curve for M3 Expressive polygon-to-polygon transitions:
 * Quickly snaps from 0 -> 1 with slight spatial spring overshoot (~1.08) in the first 68%
 * of each step, then settles cleanly to 1.0 so each polygon silhouette is crisp and legible.
 */
private fun m3ExpressiveStepSpringProgress(rawT: Float): Float {
    val t = rawT.coerceIn(0f, 1f)
    return if (t < 0.68f) {
        val u = t / 0.68f
        val base = 1f - (1f - u) * (1f - u) * (1f - u)
        val overshoot = 0.09f * kotlin.math.sin(u * Math.PI.toFloat())
        base + overshoot
    } else {
        val v = (t - 0.68f) / 0.32f
        1f + 0.03f * kotlin.math.sin(v * Math.PI.toFloat()) * (1f - v)
    }
}

/**
 * MD3 Expressive Shape-Morphing Loading Indicator (`LoadingIndicator` & `ContainedLoadingIndicator`
 * from `LoadingIndicatorSamples.kt` + `MaterialShapesSamples.kt`).
 *
 * Defer all animation state reads and path construction to the Draw/GraphicsLayer phases so
 * zero recompositions occur while the indicator is animating.
 */
@Composable
fun ExpressiveLoadingIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    @Suppress("UNUSED_PARAMETER") strokeWidth: Dp = (size / 10).coerceIn(2.dp, 4.dp),
    color: Color = MaterialTheme.colorScheme.primary,
    @Suppress("UNUSED_PARAMETER") secondaryColor: Color = MaterialTheme.colorScheme.tertiary,
    @Suppress("UNUSED_PARAMETER") trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    contained: Boolean = false,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    progress: (() -> Float)? = null
) {
    val activePolygonColor = if (contained && color == MaterialTheme.colorScheme.primary) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        color
    }

    val infiniteTransition = if (progress == null) {
        rememberInfiniteTransition(label = "m3_expressive_loading")
    } else {
        null
    }
    val rawCycleState = infiniteTransition?.animateFloat(
        initialValue = 0f,
        targetValue = 7f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4550, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "polygon_cycle"
    )
    val continuousSpinState = infiniteTransition?.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4550, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "continuous_spin"
    )

    val reusablePath = remember { Path() }

    Box(
        modifier = modifier
            .size(size)
            .then(
                if (contained) {
                    Modifier
                        .clip(CircleShape)
                        .background(containerColor)
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        val innerSize = if (contained) size * 0.74f else size
        Canvas(
            modifier = Modifier
                .size(innerSize)
                .padding((innerSize * 0.08f).coerceAtLeast(1.dp))
                .graphicsLayer {
                    val morphScale: Float
                    val totalRotationDeg: Float
                    if (progress != null) {
                        val p = progress().coerceIn(0f, 1f)
                        totalRotationDeg = p * 360f
                        morphScale = (0.35f + 0.65f * LinearOutSlowInEasing.transform(p)).coerceIn(0.25f, 1f)
                    } else {
                        val rawCycle = rawCycleState?.value ?: 0f
                        val continuousSpin = continuousSpinState?.value ?: 0f
                        val stepIndex = rawCycle.toInt().coerceIn(0, 6)
                        val localT = rawCycle - stepIndex
                        val springAlpha = m3ExpressiveStepSpringProgress(localT)
                        totalRotationDeg = (stepIndex + springAlpha.coerceIn(0f, 1.1f)) * 51.42857f + continuousSpin * 0.5f
                        morphScale = 1f + 0.06f * kotlin.math.sin(localT.coerceIn(0f, 1f) * Math.PI.toFloat())
                    }
                    scaleX = morphScale
                    scaleY = morphScale
                    rotationZ = totalRotationDeg
                }
        ) {
            val shapeStepProgress = if (progress != null) {
                progress().coerceIn(0f, 1f) * 6f
            } else {
                val rawCycle = rawCycleState?.value ?: 0f
                val stepIndex = rawCycle.toInt().coerceIn(0, 6)
                val localT = rawCycle - stepIndex
                stepIndex + m3ExpressiveStepSpringProgress(localT)
            }

            val fromIndex = shapeStepProgress.toInt().coerceAtLeast(0) % 7
            val toIndex = (fromIndex + 1) % 7
            val morphFraction = shapeStepProgress - shapeStepProgress.toInt()

            val canvasW = this.size.width
            val canvasH = this.size.height
            val cx = canvasW / 2f
            val cy = canvasH / 2f
            val baseRadius = (minOf(canvasW, canvasH) / 2f) * 0.84f

            reusablePath.rewind()
            val points = 96
            for (i in 0 until points) {
                val theta = (i.toFloat() / points) * 2f * Math.PI.toFloat()
                val rFrom = m3ExpressivePolygonRadius(fromIndex, theta)
                val rTo = m3ExpressivePolygonRadius(toIndex, theta)
                val rNorm = rFrom + (rTo - rFrom) * morphFraction
                val r = baseRadius * rNorm
                val x = cx + r * kotlin.math.cos(theta)
                val y = cy + r * kotlin.math.sin(theta)
                if (i == 0) {
                    reusablePath.moveTo(x, y)
                } else {
                    reusablePath.lineTo(x, y)
                }
            }
            reusablePath.close()

            drawPath(
                path = reusablePath,
                color = activePolygonColor
            )
        }
    }
}

/**
 * MD3 Expressive Contained Loading Indicator (`ContainedLoadingIndicatorSample` from `LoadingIndicatorSamples.kt`).
 *
 * Wraps the 7-polygon morphing [ExpressiveLoadingIndicator] inside a circular `primaryContainer` disc
 * with `onPrimaryContainer` polygon color for high-contrast list and screen loading states.
 */
@Composable
fun ContainedLoadingIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    indicatorColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    progress: (() -> Float)? = null
) {
    ExpressiveLoadingIndicator(
        modifier = modifier,
        size = size,
        color = indicatorColor,
        contained = true,
        containerColor = containerColor,
        progress = progress
    )
}

/**
 * MD3 Expressive Wavy Linear Progress Indicator (`LinearWavyProgressIndicatorSample` from `ProgressIndicatorSamples.kt`).
 *
 * Draws an animated sinusoidal wave along the active progress segment with rounded stroke caps,
 * a clean separation gap (`trackGap`), and a stop indicator dot at the end of the inactive track.
 */
@Composable
fun LinearWavyProgressIndicator(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    segments: List<Pair<Float, Color>>? = null,
    wavelength: Dp = 24.dp,
    amplitude: Dp = 2.8.dp,
    strokeWidth: Dp = 4.dp,
    trackGap: Dp = 6.dp,
    animateWave: Boolean = false
) {
    val phaseState = if (animateWave) {
        val infiniteTransition = rememberInfiniteTransition(label = "wavy_progress_phase")
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = (2f * Math.PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1100, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "wave_phase"
        )
    } else {
        null
    }

    val wavePath = remember { Path() }
    val activeSegments = remember(segments) {
        segments?.filter { it.first > 0f }.orEmpty()
    }
    val totalWeight = remember(activeSegments) {
        activeSegments.sumOf { it.first.toDouble() }.toFloat()
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(14.dp)
    ) {
        val p = progress().coerceIn(0f, 1f)
        // Smoothly ramp wave amplitude to 0 at 0% and 100% per M3 Expressive ProgressIndicatorDefaults
        val edgeEnvelope = when {
            p <= 0.01f || p >= 0.99f -> 0f
            p < 0.08f -> (p - 0.01f) / 0.07f
            p > 0.92f -> (0.99f - p) / 0.07f
            else -> 1f
        }

        val w = size.width
        val h = size.height
        val cy = h / 2f
        val strokePx = strokeWidth.toPx()
        val halfStroke = strokePx / 2f
        val gapPx = trackGap.toPx()
        val waveLenPx = wavelength.toPx().coerceAtLeast(8f)
        val ampPx = amplitude.toPx() * edgeEnvelope
        val phase = phaseState?.value ?: 0f

        val activeEndX = (w * p).coerceIn(0f, w)

        // 1. Active Wavy Segment
        if (p > 0.005f) {
            val startX = halfStroke
            val endX = (activeEndX - halfStroke).coerceAtLeast(startX)
            wavePath.rewind()
            val steps = ((endX - startX) / 2f).toInt().coerceAtLeast(2)
            for (i in 0..steps) {
                val x = startX + (endX - startX) * (i.toFloat() / steps)
                val angle = (x / waveLenPx) * (2f * Math.PI.toFloat()) + phase
                val y = cy + ampPx * kotlin.math.sin(angle)
                if (i == 0) {
                    wavePath.moveTo(x, y)
                } else {
                    wavePath.lineTo(x, y)
                }
            }

            val strokeStyle = Stroke(width = strokePx, cap = StrokeCap.Round)
            if (activeSegments.isNotEmpty() && totalWeight > 0f) {
                var cumulative = 0f
                val span = endX - startX
                activeSegments.forEachIndexed { index, (weight, segColor) ->
                    val segFraction = weight / totalWeight
                    val segStartX = startX + span * cumulative
                    cumulative += segFraction
                    val segEndX = startX + span * cumulative

                    val clipLeft = if (index == 0) 0f else segStartX
                    val clipRight = if (index == activeSegments.lastIndex) w else segEndX

                    clipRect(left = clipLeft, top = 0f, right = clipRight, bottom = h) {
                        drawPath(
                            path = wavePath,
                            color = segColor,
                            style = strokeStyle
                        )
                    }
                }
            } else {
                drawPath(
                    path = wavePath,
                    color = color,
                    style = strokeStyle
                )
            }
        }

        // 2. Inactive Track + Stop Indicator Dot
        val trackStartX = if (p > 0.005f) {
            (activeEndX + gapPx + halfStroke)
        } else {
            halfStroke
        }
        val trackEndX = w - halfStroke
        if (trackStartX < trackEndX) {
            drawLine(
                color = trackColor,
                start = Offset(trackStartX, cy),
                end = Offset(trackEndX, cy),
                strokeWidth = strokePx,
                cap = StrokeCap.Round
            )
            // M3 Expressive stop indicator dot at end of track
            drawCircle(
                color = color,
                radius = halfStroke,
                center = Offset(trackEndX, cy)
            )
        }
    }
}

/**
 * MD3 Expressive Pull-To-Refresh Indicator (`PullToRefreshWithLoadingIndicatorSample` from `PullToRefreshSamples.kt`).
 *
 * Uses a circular container (`CircleShape`) that scales with `LinearOutSlowInEasing` during pull,
 * transitions to `primaryContainer` / `onPrimaryContainer` at threshold and during refresh, and
 * morphs determinately through the 7 M3 Expressive polygons as the user drags down.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpressivePullToRefreshIndicator(
    state: PullToRefreshState,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    color: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    threshold: Dp = PullToRefreshDefaults.PositionalThreshold,
) {
    val haptic = LocalHapticFeedback.current
    val isPastThreshold by remember(state) {
        derivedStateOf { state.distanceFraction >= 1f }
    }
    val isDragging by remember(state) {
        derivedStateOf { state.distanceFraction > 0.005f }
    }

    LaunchedEffect(isPastThreshold) {
        if (isPastThreshold) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    val activeContainerColor by animateColorAsState(
        targetValue = if (isRefreshing || isPastThreshold) {
            containerColor
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        animationSpec = M3ExpressiveMotion.fastEffects(),
        label = "ptr_container_color"
    )

    val activeIndicatorColor by animateColorAsState(
        targetValue = if (isRefreshing || isPastThreshold) {
            color
        } else {
            MaterialTheme.colorScheme.primary
        },
        animationSpec = M3ExpressiveMotion.fastEffects(),
        label = "ptr_indicator_color"
    )

    Box(
        modifier = modifier
            .pullToRefreshIndicator(
                state = state,
                isRefreshing = isRefreshing,
                containerColor = activeContainerColor,
                threshold = threshold,
                shape = CircleShape,
                elevation = PullToRefreshDefaults.Elevation
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isRefreshing) {
            ExpressiveLoadingIndicator(
                size = 28.dp,
                color = activeIndicatorColor
            )
        } else if (isDragging) {
            ExpressiveLoadingIndicator(
                size = 28.dp,
                color = activeIndicatorColor,
                progress = { state.distanceFraction }
            )
        }
    }
}

/**
 * MD3 Expressive PullToRefreshBox.
 *
 * Drop-in container supporting swipe-to-refresh with the Material 3 Expressive
 * 7-polygon determinate drag morph, haptic threshold pop, and ContainedLoadingIndicator refresh state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpressivePullToRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    state: PullToRefreshState = rememberPullToRefreshState(),
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit
) {
    val density = LocalDensity.current
    val maxShiftPx = with(density) { 96.dp.toPx() }
    val currentRefreshing by rememberUpdatedState(isRefreshing)

    val pullLockConnection = remember(state) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // While refreshing or while the pull-to-refresh displacement/animation is active,
                // prevent the inner LazyColumn from scrolling upward independently of the animation.
                if (currentRefreshing || state.isAnimating || state.distanceFraction > 0.001f) {
                    if (available.y < 0f || currentRefreshing || state.isAnimating) {
                        return Offset(0f, available.y)
                    }
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (currentRefreshing || state.isAnimating || state.distanceFraction > 0.001f) {
                    return available
                }
                return Velocity.Zero
            }
        }
    }

    Box(
        modifier = modifier
            .clipToBounds()
            .pullToRefresh(
                state = state,
                isRefreshing = isRefreshing,
                onRefresh = onRefresh
            )
            .nestedScroll(pullLockConnection),
        contentAlignment = contentAlignment
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = (state.distanceFraction * maxShiftPx).coerceAtLeast(0f)
                },
            content = content
        )

        ExpressivePullToRefreshIndicator(
            state = state,
            isRefreshing = isRefreshing,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}

/**
 * MD3 Expressive spring bounce click modifier.
 *
 * Uses M3 Expressive Fast Spatial spring (`damping = 0.6f, stiffness = 800f`) for small/interactive
 * component scale compression and bounce recovery via [ExpressivePressScaleNode] without `Modifier.composed`.
 */
fun Modifier.expressiveBounceClick(
    scaleDown: Float = 0.96f,
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier = this
    .expressivePressScale(
        interactionSource = null,
        scaleDown = scaleDown,
        enabled = enabled
    )
    .clickable(
        enabled = enabled,
        onClick = onClick
    )

/**
 * MD3 Expressive spring press scale modifier implemented via [ModifierNodeElement] and [Modifier.Node]
 * to avoid `Modifier.composed` subcomposition overhead on lazy list items.
 * Uses M3 Expressive Fast Spatial spring (`damping = 0.6f, stiffness = 800f`).
 */
fun Modifier.expressivePressScale(
    interactionSource: InteractionSource? = null,
    scaleDown: Float = 0.96f,
    enabled: Boolean = true
): Modifier = this then ExpressivePressScaleElement(
    interactionSource = interactionSource,
    scaleDown = scaleDown,
    enabled = enabled
)

private data class ExpressivePressScaleElement(
    val interactionSource: InteractionSource?,
    val scaleDown: Float,
    val enabled: Boolean
) : ModifierNodeElement<ExpressivePressScaleNode>() {
    override fun create(): ExpressivePressScaleNode =
        ExpressivePressScaleNode(scaleDown, enabled)

    override fun update(node: ExpressivePressScaleNode) {
        node.updateNode(scaleDown, enabled)
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "expressivePressScale"
        properties["scaleDown"] = scaleDown
        properties["enabled"] = enabled
    }
}

private class ExpressivePressScaleNode(
    private var scaleDown: Float,
    private var enabled: Boolean
) : Modifier.Node(), PointerInputModifierNode, DrawModifierNode {
    private var scaleAnimatable: Animatable<Float, AnimationVector1D>? = null
    private var animJob: Job? = null
    private var isPointerPressed: Boolean = false

    fun updateNode(
        newScaleDown: Float,
        newEnabled: Boolean
    ) {
        scaleDown = newScaleDown
        enabled = newEnabled
        if (!enabled && isPointerPressed) {
            isPointerPressed = false
            animateToScale(1f)
        }
    }

    override fun onPointerEvent(
        pointerEvent: PointerEvent,
        pass: PointerEventPass,
        bounds: IntSize
    ) {
        if (pass != PointerEventPass.Main || !enabled) return
        when (pointerEvent.type) {
            PointerEventType.Press -> {
                if (!isPointerPressed && pointerEvent.changes.any { it.pressed && !it.isConsumed }) {
                    isPointerPressed = true
                    animateToScale(scaleDown)
                }
            }
            PointerEventType.Move -> {
                if (isPointerPressed && pointerEvent.changes.any { it.isConsumed || !it.pressed }) {
                    isPointerPressed = false
                    animateToScale(1f)
                }
            }
            PointerEventType.Release, PointerEventType.Exit -> {
                if (isPointerPressed && pointerEvent.changes.none { it.pressed }) {
                    isPointerPressed = false
                    animateToScale(1f)
                }
            }
        }
    }

    override fun onCancelPointerInput() {
        if (isPointerPressed) {
            isPointerPressed = false
            animateToScale(1f)
        }
    }

    override fun onDetach() {
        isPointerPressed = false
        animJob?.cancel()
        animJob = null
        scaleAnimatable = null
    }

    private fun animateToScale(target: Float) {
        animJob?.cancel()
        val anim = scaleAnimatable ?: Animatable(1f).also { scaleAnimatable = it }
        if (anim.value == target && !anim.isRunning) return
        animJob = coroutineScope.launch {
            anim.animateTo(
                targetValue = target,
                animationSpec = M3ExpressiveMotion.fastSpatial()
            ) {
                invalidateDraw()
            }
            invalidateDraw()
        }
    }

    override fun ContentDrawScope.draw() {
        val currentScale = scaleAnimatable?.value ?: 1f
        if (currentScale != 1f) {
            scale(scaleX = currentScale, scaleY = currentScale, pivot = center) {
                this@draw.drawContent()
            }
        } else {
            drawContent()
        }
    }
}

/**
 * MD3 Expressive Error Shake Modifier.
 *
 * Simulates physical error feedback via damped horizontal oscillation (±24px, ~320ms)
 * combined with system haptic feedback.
 * When Reduced Motion is enabled, respects accessibility by replacing spatial translation with an opacity pulse.
 */
@Composable
fun Modifier.expressiveShake(
    trigger: Any?,
    amplitudePx: Float = 24f,
    enabled: Boolean = true
): Modifier {
    if (!enabled) return this
    val haptic = LocalHapticFeedback.current
    val isReducedMotion = LocalReducedMotion.current
    val animOffset = remember { Animatable(0f) }
    val animAlpha = remember { Animatable(1f) }

    LaunchedEffect(trigger) {
        if (trigger != null && trigger != false && trigger != "") {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            if (isReducedMotion) {
                // Non-spatial fallback: subtle opacity pulse
                animAlpha.snapTo(1f)
                animAlpha.animateTo(
                    targetValue = 0.5f,
                    animationSpec = tween(durationMillis = 100, easing = LinearEasing)
                )
                animAlpha.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 150, easing = LinearEasing)
                )
            } else {
                animOffset.snapTo(0f)
                animOffset.animateTo(
                    targetValue = 0f,
                    animationSpec = keyframes {
                        durationMillis = 320
                        0f at 0
                        -amplitudePx at 50 using FastOutLinearInEasing
                        amplitudePx * 0.7f at 110 using FastOutSlowInEasing
                        -amplitudePx * 0.45f at 170 using FastOutSlowInEasing
                        amplitudePx * 0.2f at 230 using FastOutSlowInEasing
                        -amplitudePx * 0.08f at 280 using FastOutSlowInEasing
                        0f at 320 using LinearOutSlowInEasing
                    }
                )
            }
        }
    }

    return this.graphicsLayer {
        if (isReducedMotion) {
            alpha = animAlpha.value
        } else {
            translationX = animOffset.value
        }
    }
}

/**
 * MD3 Expressive Success Pop Modifier.
 *
 * Implements a celebratory scale pop (1.0f -> 1.12f -> 1.0f) using
 * [M3ExpressiveMotion.fastSpatial] overshoot spring.
 * Enhances dopamine loop upon sync completion or action confirmation.
 * Respects Reduced Motion by skipping spatial scale.
 */
@Composable
fun Modifier.expressiveSuccessPop(
    trigger: Any?,
    maxScale: Float = 1.12f,
    enabled: Boolean = true
): Modifier {
    if (!enabled) return this
    val haptic = LocalHapticFeedback.current
    val isReducedMotion = LocalReducedMotion.current
    val scaleAnim = remember { Animatable(1f) }

    LaunchedEffect(trigger) {
        if (trigger != null && trigger != false && trigger != "") {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            if (isReducedMotion) {
                scaleAnim.snapTo(1f)
            } else {
                scaleAnim.snapTo(1f)
                scaleAnim.animateTo(
                    targetValue = maxScale,
                    animationSpec = tween(durationMillis = 90, easing = FastOutSlowInEasing)
                )
                scaleAnim.animateTo(
                    targetValue = 1f,
                    animationSpec = M3ExpressiveMotion.fastSpatial()
                )
            }
        }
    }

    return this.graphicsLayer {
        if (!isReducedMotion) {
            val s = scaleAnim.value
            scaleX = s
            scaleY = s
        }
    }
}

/**
 * MD3 Expressive physics for lazy list item placement and appearance.
 */
fun LazyItemScope.expressiveItemModifier(
    modifier: Modifier = Modifier
): Modifier = modifier.animateItem(
    fadeInSpec = M3ExpressiveMotion.fastEffects(),
    placementSpec = M3ExpressiveMotion.defaultSpatial()
)

/**
 * MD3 Expressive physics for lazy grid item placement and appearance.
 */
fun LazyGridItemScope.expressiveItemModifier(
    modifier: Modifier = Modifier
): Modifier = modifier.animateItem(
    fadeInSpec = M3ExpressiveMotion.fastEffects(),
    placementSpec = M3ExpressiveMotion.defaultSpatial()
)

/**
 * MD3 Expressive spring scroll to top extension for [LazyListState].
 * Smoothly scrolls the list to the exact top (`index = 0, offset = 0`) using a live
 * critically-damped spring that dynamically tracks item heights as they enter the viewport,
 * preventing premature stopping, downward bounce-back, or un-animated jumps at the end.
 */
suspend fun LazyListState.expressiveScrollToTop() {
    if (firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset == 0) return

    if (firstVisibleItemIndex > 8) {
        scrollToItem(8)
    }

    scroll {
        val omega = 15f // Natural frequency (rad/s) for smooth M3 Expressive deceleration
        var velocity = 0f
        var lastFrameNanos = 0L
        var estimatedItemSize = (
            layoutInfo.visibleItemsInfo
                .map { it.size }
                .average()
                .takeIf { !it.isNaN() && it > 0.0 }
                ?.toFloat() ?: 240f
            ).coerceAtLeast(240f) + layoutInfo.mainAxisItemSpacing

        fun computeStep(x0: Float, dt: Float): Float {
            if (x0 <= 1.5f) return x0.coerceAtLeast(0f)
            val maxCriticalVelocity = omega * x0 * 1.15f
            if (velocity > maxCriticalVelocity) {
                velocity = maxCriticalVelocity
            }
            val expTerm = kotlin.math.exp(-omega * dt)
            val nextX = (x0 + (omega * x0 - velocity) * dt) * expTerm
            velocity = (velocity + omega * (omega * x0 - velocity) * dt) * expTerm
            return (x0 - nextX).coerceIn(1.5f, x0)
        }

        while (canScrollBackward && (firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset > 0)) {
            val dt = withFrameNanos { frameNanos ->
                val deltaSec = if (lastFrameNanos == 0L) {
                    1f / 60f
                } else {
                    ((frameNanos - lastFrameNanos) / 1_000_000_000f).coerceIn(1f / 240f, 0.05f)
                }
                lastFrameNanos = frameNanos
                deltaSec
            }

            if (!canScrollBackward || (firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset == 0)) {
                break
            }

            val visible = layoutInfo.visibleItemsInfo
            if (visible.isNotEmpty()) {
                val currentAvg = visible.sumOf { it.size }.toFloat() / visible.size + layoutInfo.mainAxisItemSpacing
                estimatedItemSize = maxOf(estimatedItemSize * 0.7f + currentAvg * 0.3f, 180f)
            }

            val x0 = if (firstVisibleItemIndex == 0) {
                firstVisibleItemScrollOffset.toFloat()
            } else {
                firstVisibleItemScrollOffset.toFloat() + (firstVisibleItemIndex * estimatedItemSize)
            }

            if (x0 <= 0.5f) break

            val step = computeStep(x0, dt)
            val boundaryDist = firstVisibleItemScrollOffset.toFloat()

            // If crossing from item k > 0 into item k - 1, cross the boundary first so LazyColumn
            // synchronously measures item k - 1 and we use its true height for the remainder of the frame.
            if (firstVisibleItemIndex > 0 && step > boundaryDist + 1f) {
                val crossStep = boundaryDist + 1f
                val consumed1 = -scrollBy(-crossStep)
                if (consumed1 < 0.5f && !canScrollBackward) break

                val remainingRatio = ((step - crossStep) / step).coerceIn(0f, 1f)
                val dtRem = dt * remainingRatio
                if (dtRem > 0.0005f && (firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset > 0)) {
                    val xAfter = if (firstVisibleItemIndex == 0) {
                        firstVisibleItemScrollOffset.toFloat()
                    } else {
                        firstVisibleItemScrollOffset.toFloat() + (firstVisibleItemIndex * estimatedItemSize)
                    }
                    if (xAfter > 0.5f) {
                        val step2 = computeStep(xAfter, dtRem)
                        val consumed2 = -scrollBy(-step2)
                        if (consumed2 < 0.5f && !canScrollBackward) break
                    }
                }
            } else {
                val consumed = -scrollBy(-step)
                if (consumed < 0.5f && !canScrollBackward) break
            }
        }

        if (firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset > 0) {
            scrollBy(-firstVisibleItemScrollOffset.toFloat())
        }
    }
}

enum class DateSelectionMode {
    SINGLE,
    RANGE
}

private val utcIsoFormatter = object : ThreadLocal<SimpleDateFormat>() {
    override fun initialValue(): SimpleDateFormat =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
}

private val localIsoParser = object : ThreadLocal<SimpleDateFormat>() {
    override fun initialValue(): SimpleDateFormat =
        SimpleDateFormat("yyyy-MM-dd", Locale.US)
}

fun formatMillisToIsoDate(millis: Long): String {
    return utcIsoFormatter.get()!!.format(Date(millis))
}

fun parseIsoDateToMillis(dateStr: String?): Long? {
    if (dateStr.isNullOrBlank()) return null
    return try {
        val clean = dateStr.trim().replace('/', '-')
        utcIsoFormatter.get()!!.parse(clean)?.time
    } catch (e: Exception) {
        null
    }
}

fun formatIsoToReadableDate(dateStr: String): String {
    return try {
        val clean = dateStr.trim().replace('/', '-')
        val date = localIsoParser.get()!!.parse(clean) ?: return dateStr
        val outputFormatter = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
        outputFormatter.format(date)
    } catch (e: Exception) {
        dateStr
    }
}

fun formatIsoToShortDate(dateStr: String): String {
    return try {
        val clean = dateStr.trim().replace('/', '-')
        val date = localIsoParser.get()!!.parse(clean) ?: return dateStr
        val outputFormatter = SimpleDateFormat("d MMM", Locale.getDefault())
        outputFormatter.format(date)
    } catch (e: Exception) {
        dateStr
    }
}

fun formatDateRangeChipLabel(
    singleDate: String?,
    fromDate: String?,
    toDate: String?,
    defaultText: String
): String {
    return when {
        !singleDate.isNullOrBlank() -> formatIsoToReadableDate(singleDate)
        !fromDate.isNullOrBlank() && !toDate.isNullOrBlank() -> {
            "${formatIsoToShortDate(fromDate)} – ${formatIsoToShortDate(toDate)}"
        }
        !fromDate.isNullOrBlank() -> "${formatIsoToShortDate(fromDate)} →"
        !toDate.isNullOrBlank() -> "→ ${formatIsoToShortDate(toDate)}"
        else -> defaultText
    }
}

/**
 * Material 3 Expressive Date & Date Range Picker Dialog.
 * Allows switching seamlessly between single day selection and date range selection with spring transitions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpressiveDateFilterDialog(
    initialStartDate: String? = null,
    initialEndDate: String? = null,
    onDismissRequest: () -> Unit,
    onDateSelected: (String) -> Unit,
    onDateRangeSelected: (String, String) -> Unit,
    onClear: () -> Unit
) {
    val initialStartMillis = remember(initialStartDate) { parseIsoDateToMillis(initialStartDate) }
    val initialEndMillis = remember(initialEndDate) { parseIsoDateToMillis(initialEndDate) }

    var selectionMode by remember {
        mutableStateOf(
            if (!initialEndDate.isNullOrBlank()) DateSelectionMode.RANGE
            else DateSelectionMode.SINGLE
        )
    }

    val singleDatePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialStartMillis
    )

    val dateRangePickerState = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initialStartMillis,
        initialSelectedEndDateMillis = initialEndMillis
    )

    val haptic = LocalHapticFeedback.current
    val pickerColors = DatePickerDefaults.colors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    )

    BasicAlertDialog(
        onDismissRequest = onDismissRequest,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .requiredWidth(360.dp)
                .wrapContentHeight(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = DatePickerDefaults.TonalElevation
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // M3 Expressive Connected Button Group mode toggle
                ExpressiveConnectedButtonGroup(
                    items = listOf(DateSelectionMode.SINGLE, DateSelectionMode.RANGE),
                    selectedItem = selectionMode,
                    onItemSelected = { selectionMode = it },
                    fillWidth = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    labelProvider = { mode ->
                        if (mode == DateSelectionMode.SINGLE) {
                            stringResource(R.string.date_picker_single_day)
                        } else {
                            stringResource(R.string.date_picker_date_range)
                        }
                    }
                )

                // Opacity crossfade must use an Effects spring (damping 1.0, no overshoot) per motion.md
                Crossfade(
                    targetState = selectionMode,
                    animationSpec = M3ExpressiveMotion.defaultEffects(),
                    label = "date_picker_mode_fade"
                ) { mode ->
                    if (mode == DateSelectionMode.SINGLE) {
                        val selectedIso = singleDatePickerState.selectedDateMillis?.let { formatMillisToIsoDate(it) }
                        val headlineText = if (selectedIso != null) {
                            formatIsoToReadableDate(selectedIso)
                        } else {
                            stringResource(R.string.filter_pick_date)
                        }
                        DatePicker(
                            state = singleDatePickerState,
                            title = null,
                            headline = {
                                Text(
                                    text = headlineText,
                                    style = MaterialTheme.typography.headlineSmallEmphasized,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(
                                        start = 24.dp,
                                        end = 16.dp,
                                        top = 8.dp,
                                        bottom = 12.dp
                                    )
                                )
                            },
                            showModeToggle = false,
                            colors = pickerColors
                        )
                    } else {
                        val startIso = dateRangePickerState.selectedStartDateMillis?.let { formatMillisToIsoDate(it) }
                        val endIso = dateRangePickerState.selectedEndDateMillis?.let { formatMillisToIsoDate(it) }
                        val rangeHeadline = formatDateRangeChipLabel(
                            singleDate = null,
                            fromDate = startIso,
                            toDate = endIso,
                            defaultText = stringResource(R.string.date_picker_date_range)
                        )
                        DateRangePicker(
                            state = dateRangePickerState,
                            title = null,
                            headline = {
                                Text(
                                    text = rangeHeadline,
                                    style = MaterialTheme.typography.titleLargeEmphasized,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(
                                        start = 24.dp,
                                        end = 16.dp,
                                        top = 8.dp,
                                        bottom = 12.dp
                                    )
                                )
                            },
                            showModeToggle = false,
                            colors = pickerColors,
                            modifier = Modifier.height(420.dp)
                        )
                    }
                }

                // Dialog Actions Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 10.dp, top = 2.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val hasAnySelection = !initialStartDate.isNullOrBlank() ||
                        !initialEndDate.isNullOrBlank() ||
                        singleDatePickerState.selectedDateMillis != null ||
                        dateRangePickerState.selectedStartDateMillis != null

                    if (hasAnySelection) {
                        TextButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onClear()
                                onDismissRequest()
                            }
                        ) {
                            Text(
                                text = stringResource(R.string.date_picker_clear),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    TextButton(onClick = onDismissRequest) {
                        Text(
                            text = stringResource(R.string.action_cancel),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    TextButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            if (selectionMode == DateSelectionMode.SINGLE) {
                                singleDatePickerState.selectedDateMillis?.let { millis ->
                                    onDateSelected(formatMillisToIsoDate(millis))
                                }
                            } else {
                                val startMillis = dateRangePickerState.selectedStartDateMillis
                                val endMillis = dateRangePickerState.selectedEndDateMillis
                                if (startMillis != null && endMillis != null) {
                                    onDateRangeSelected(
                                        formatMillisToIsoDate(startMillis),
                                        formatMillisToIsoDate(endMillis)
                                    )
                                } else if (startMillis != null) {
                                    onDateSelected(formatMillisToIsoDate(startMillis))
                                }
                            }
                            onDismissRequest()
                        }
                    ) {
                        Text(
                            text = stringResource(R.string.date_picker_apply),
                            style = MaterialTheme.typography.labelLargeEmphasized
                        )
                    }
                }
            }
        }
    }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, text.trim())
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, context.getString(R.string.action_copied), Toast.LENGTH_SHORT).show()
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
        "breakfast" -> Icons.Default.BakeryDining
        "lunch" -> Icons.Default.LunchDining
        "dinner" -> Icons.Default.DinnerDining
        else -> Icons.Default.Restaurant
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
        if (onClose != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.title_nutrition),
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.action_close),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
            // Header row: Meal badge pill + Date
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = M3Shapes.CornerFull,
                    color = domainNutritionContainer()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = mealIcon,
                            contentDescription = null,
                            tint = domainNutritionOnContainer(),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = mealLabel,
                            style = MaterialTheme.typography.labelMediumEmphasized,
                            color = domainNutritionOnContainer()
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
                shape = M3Shapes.CornerMedium,
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
                            text = "${item.calories.toInt()} kcal",
                            style = MaterialTheme.typography.headlineSmallEmphasized,
                            color = domainNutritionColor()
                        )
                    }

                    // 3-segment macro bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(M3Shapes.CornerFull)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .weight(carbPct.coerceAtLeast(0.01f))
                                .background(domainBooksColor())
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .weight(protPct.coerceAtLeast(0.01f))
                                .background(domainNutritionColor())
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .weight(fatPct.coerceAtLeast(0.01f))
                                .background(domainRatingColor())
                        )
                    }

                    // 3 Macro columns
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MacroDetailStat(
                            color = domainBooksColor(),
                            label = stringResource(R.string.nutrition_label_carb),
                            grams = item.carbohydrate,
                            pct = (carbPct * 100).toInt()
                        )
                        MacroDetailStat(
                            color = domainNutritionColor(),
                            label = stringResource(R.string.nutrition_label_protein),
                            grams = item.protein,
                            pct = (protPct * 100).toInt()
                        )
                        MacroDetailStat(
                            color = domainRatingColor(),
                            label = stringResource(R.string.nutrition_label_fat),
                            grams = item.fat,
                            pct = (fatPct * 100).toInt()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

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
                            append("\n${item.calories.toInt()} kcal · ")
                            append("K:${item.carbohydrate.toInt()}g P:${item.protein.toInt()}g Y:${item.fat.toInt()}g")
                        }
                        copyToClipboard(context, item.displayName, copyStr)
                    },
                    shape = M3Shapes.CornerSmall,
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
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
                        shape = M3Shapes.CornerSmall,
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp),
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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = M3Shapes.CornerExtraLarge,
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
fun FoodEntryDetailPaneCard(
    item: FoodEntryDto,
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        shape = M3Shapes.CornerLarge,
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

@Composable
fun FilmDetailContent(
    film: FilmDto,
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val watchedDate = film.safeWatchedDate.ifBlank { film.diary_date ?: "" }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (onClose != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.movies_badge_letterboxd),
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.action_close),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Top Row: Poster + Title & Badges
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            PosterThumbnail(
                imageUrl = film.image_url,
                fallbackIcon = Icons.Default.Movie,
                width = 72,
                height = 104
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Badges
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = M3Shapes.CornerFull,
                        color = domainCinemaContainer()
                    ) {
                        Text(
                            text = stringResource(R.string.movies_badge_letterboxd),
                            style = MaterialTheme.typography.labelSmallEmphasized,
                            color = domainCinemaOnContainer(),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.5.dp)
                        )
                    }
                    if (film.safeYear > 0) {
                        Surface(
                            shape = M3Shapes.CornerFull,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest
                        ) {
                            Text(
                                text = "${film.safeYear}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                            )
                        }
                    }
                    if (film.rewatch) {
                        Surface(
                            shape = M3Shapes.CornerFull,
                            color = domainCinemaContainer()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp),
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Repeat,
                                    contentDescription = null,
                                    tint = domainCinemaOnContainer(),
                                    modifier = Modifier.size(11.dp)
                                )
                                Text(
                                    text = stringResource(R.string.movies_rewatch_badge),
                                    style = MaterialTheme.typography.labelSmallEmphasized,
                                    color = domainCinemaOnContainer()
                                )
                            }
                        }
                    }
                }

                Text(
                    text = film.displayTitle,
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (film.safeRating > 0) {
                        RatingBadge(rating = film.safeRating)
                    }
                    if (watchedDate.isNotBlank()) {
                        Text(
                            text = watchedDate,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Full Review Box if available
        if (film.safeReview.isNotBlank()) {
            Surface(
                shape = M3Shapes.CornerMedium,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(14.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "“${film.safeReview}”",
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Actions: Copy & Close
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    val copyStr = buildString {
                        append(film.displayTitle)
                        if (film.safeYear > 0) append(" (${film.safeYear})")
                        if (film.safeRating > 0) append(" · ★ ${film.safeRating}")
                        if (film.safeReview.isNotBlank()) append("\n\n${film.safeReview}")
                    }
                    copyToClipboard(context, film.displayTitle, copyStr)
                },
                shape = M3Shapes.CornerSmall,
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
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
                    shape = M3Shapes.CornerSmall,
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp),
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
fun FilmDetailBottomSheet(
    film: FilmDto,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = M3Shapes.CornerExtraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        FilmDetailContent(
            film = film,
            onClose = onDismiss,
            modifier = Modifier.navigationBarsPadding()
        )
    }
}

@Composable
fun FilmDetailPaneCard(
    film: FilmDto,
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        shape = M3Shapes.CornerLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            FilmDetailContent(
                film = film,
                onClose = onClose
            )
        }
    }
}

@Composable
fun BookDetailContent(
    book: BookDto,
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val shelfLabel = when (book.exclusive_shelf.lowercase()) {
        "read" -> stringResource(R.string.books_shelf_read)
        "to-read" -> stringResource(R.string.books_shelf_to_read)
        "currently-reading" -> stringResource(R.string.books_shelf_currently_reading)
        else -> book.exclusive_shelf.ifBlank { stringResource(R.string.books_badge_book) }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Top Row: Cover + Title, Author, Badges
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            PosterThumbnail(
                imageUrl = book.image_url,
                fallbackIcon = Icons.AutoMirrored.Filled.MenuBook,
                width = 72,
                height = 104
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = M3Shapes.CornerFull,
                        color = domainBooksContainer()
                    ) {
                        Text(
                            text = shelfLabel,
                            style = MaterialTheme.typography.labelSmallEmphasized,
                            color = domainBooksOnContainer(),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.5.dp)
                        )
                    }
                    if (book.year_published > 0) {
                        Surface(
                            shape = M3Shapes.CornerFull,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest
                        ) {
                            Text(
                                text = "${book.year_published}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                            )
                        }
                    }
                }

                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (book.author.isNotBlank()) {
                    Text(
                        text = book.author,
                        style = MaterialTheme.typography.bodyMediumEmphasized,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (book.my_rating > 0) {
                        RatingBadge(rating = book.my_rating)
                    }
                    if (book.average_rating > 0) {
                        Text(
                            text = "${stringResource(R.string.badge_avg)} ★ ${String.format(Locale.US, "%.2f", book.average_rating)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Metadata surface if pages or date_read exist
        if (book.number_of_pages > 0 || book.date_read.isNotBlank()) {
            Surface(
                shape = M3Shapes.CornerMedium,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (book.number_of_pages > 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                contentDescription = null,
                                tint = domainBooksColor(),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = stringResource(R.string.books_pages, book.number_of_pages),
                                style = MaterialTheme.typography.bodySmallEmphasized,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    if (book.date_read.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = book.date_read,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Actions: Copy & Close
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    val copyStr = buildString {
                        append(book.title)
                        if (book.author.isNotBlank()) append(" — ${book.author}")
                        if (book.my_rating > 0) append(" (★ ${book.my_rating})")
                    }
                    copyToClipboard(context, book.title, copyStr)
                },
                shape = M3Shapes.CornerSmall,
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
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
                    shape = M3Shapes.CornerSmall,
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp),
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
fun BookDetailBottomSheet(
    book: BookDto,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = M3Shapes.CornerExtraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        BookDetailContent(
            book = book,
            onClose = onDismiss,
            modifier = Modifier.navigationBarsPadding()
        )
    }
}

@Composable
fun BookDetailPaneCard(
    book: BookDto,
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        shape = M3Shapes.CornerLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            BookDetailContent(
                book = book,
                onClose = onClose
            )
        }
    }
}

@Composable
fun NotificationDetailContent(
    notif: NotificationDto,
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header row: App Name pill, Category pill, Date/Time
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = M3Shapes.CornerFull,
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Text(
                    text = notif.displayAppName,
                    style = MaterialTheme.typography.labelMediumEmphasized,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
            if (notif.category.isNotBlank()) {
                Surface(
                    shape = M3Shapes.CornerFull,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest
                ) {
                    Text(
                        text = notif.category,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            val dateTimeText = listOf(notif.formattedDate, notif.formattedTime)
                .filter { it.isNotBlank() }
                .joinToString(" · ")
            if (dateTimeText.isNotBlank()) {
                Text(
                    text = dateTimeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Title
        Text(
            text = notif.displayTitle,
            style = MaterialTheme.typography.titleMediumEmphasized,
            color = MaterialTheme.colorScheme.onSurface
        )

        // Message Body
        if (notif.displayBody.isNotBlank()) {
            Text(
                text = notif.displayBody,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Subtext if distinct
        if (notif.sub_text.isNotBlank() && notif.sub_text != notif.displayBody) {
            Text(
                text = notif.sub_text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Package name info
        if (notif.package_name.isNotBlank()) {
            Text(
                text = "Paket: ${notif.package_name}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Actions: Copy & Close
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    val textToCopy = "${notif.displayTitle}\n${notif.displayBody}".trim()
                    copyToClipboard(context, "Bildirim", textToCopy)
                },
                shape = M3Shapes.CornerSmall,
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
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
                    shape = M3Shapes.CornerSmall,
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp),
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
fun NotificationDetailBottomSheet(
    notif: NotificationDto,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = M3Shapes.CornerExtraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        NotificationDetailContent(
            notif = notif,
            onClose = onDismiss,
            modifier = Modifier.navigationBarsPadding()
        )
    }
}

@Composable
fun NotificationDetailPaneCard(
    notif: NotificationDto,
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        shape = M3Shapes.CornerLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            NotificationDetailContent(
                notif = notif,
                onClose = onClose
            )
        }
    }
}

@Composable
fun DetailPlaceholderPane(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        shape = M3Shapes.CornerLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = M3Shapes.CornerFull,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.size(72.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthRecordDetailBottomSheet(
    record: HealthRecordDto,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val categoryLabel = when (record.category) {
        "activity" -> stringResource(R.string.health_cat_activity)
        "sleep" -> stringResource(R.string.health_cat_sleep)
        "vitals" -> stringResource(R.string.health_cat_vitals)
        "exercise" -> stringResource(R.string.health_cat_exercise)
        "body" -> stringResource(R.string.health_cat_body)
        else -> stringResource(R.string.title_health_connect)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = M3Shapes.CornerExtraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = M3Shapes.CornerFull,
                    color = domainHealthContainer()
                ) {
                    Text(
                        text = categoryLabel,
                        style = MaterialTheme.typography.labelMediumEmphasized,
                        color = domainHealthOnContainer(),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                if (record.record_type.isNotBlank()) {
                    Surface(
                        shape = M3Shapes.CornerFull,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest
                    ) {
                        Text(
                            text = record.record_type.replace('_', ' '),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                val dateTimeText = listOf(record.formattedDate, record.formattedTime)
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
                if (dateTimeText.isNotBlank()) {
                    Text(
                        text = dateTimeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = record.title,
                style = MaterialTheme.typography.titleLargeEmphasized,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (record.subtitle.isNotBlank()) {
                Text(
                    text = record.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (record.start_time.isNotBlank() || record.data_origin.isNotBlank()) {
                Surface(
                    shape = M3Shapes.CornerMedium,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (record.start_time.isNotBlank()) {
                            val startFormatted = record.start_time.replace('T', ' ').take(19)
                            val endFormatted = record.end_time.replace('T', ' ').take(19)
                            val windowStr = if (endFormatted.isNotBlank() && endFormatted != startFormatted) {
                                "$startFormatted → $endFormatted"
                            } else {
                                startFormatted
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.health_detail_time_range),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = windowStr,
                                    style = MaterialTheme.typography.labelSmallEmphasized,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        if (record.data_origin.isNotBlank()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.health_detail_source),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = record.data_origin,
                                    style = MaterialTheme.typography.labelSmallEmphasized,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val textToCopy = "${record.title}\n${record.subtitle}\n${record.formattedDate} ${record.formattedTime}".trim()
                        copyToClipboard(context, "Health Connect", textToCopy)
                    },
                    shape = M3Shapes.CornerSmall,
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
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

                Button(
                    onClick = onDismiss,
                    shape = M3Shapes.CornerSmall,
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp),
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

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

private fun Map<String, kotlinx.serialization.json.JsonElement>.str(key: String): String {
    return this[key]?.toString()?.removeSurrounding("\"")?.takeIf { it != "null" } ?: ""
}

private fun Map<String, kotlinx.serialization.json.JsonElement>.dbl(key: String): Double {
    return str(key).toDoubleOrNull() ?: 0.0
}

private fun Map<String, kotlinx.serialization.json.JsonElement>.intVal(key: String): Int {
    return str(key).toIntOrNull() ?: 0
}

@Composable
fun ActivityItemDetailBottomSheet(
    item: ActivityItemDto,
    onDismiss: () -> Unit
) {
    val d = item.details
    when (item.source) {
        "fatsecret" -> {
            val food = FoodEntryDto(
                food_entry_id = d.str("food_entry_id").ifBlank { item.id },
                date = item.date,
                meal = d.str("meal").ifBlank { item.subtitle },
                food_entry_name = item.title,
                food_entry_description = d.str("description").ifBlank { item.subtitle },
                calories = d.dbl("calories"),
                carbohydrate = d.dbl("carbohydrate"),
                protein = d.dbl("protein"),
                fat = d.dbl("fat")
            )
            FoodEntryDetailBottomSheet(item = food, onDismiss = onDismiss)
        }
        "letterboxd" -> {
            val film = FilmDto(
                film_key = d.str("film_key").ifBlank { item.id },
                name = item.title,
                year = d.intVal("year").takeIf { it > 0 } ?: item.subtitle.toIntOrNull(),
                rating = d.dbl("rating").takeIf { it > 0 },
                rewatch = item.rewatch || d.str("rewatch").equals("true", ignoreCase = true) || d.str("rewatch") == "1",
                review = d.str("review"),
                image_url = item.image_url ?: d.str("image_url").ifBlank { null },
                watched_date = item.date
            )
            FilmDetailBottomSheet(film = film, onDismiss = onDismiss)
        }
        "goodreads" -> {
            val book = BookDto(
                book_id = d.str("book_id").ifBlank { item.id },
                title = item.title,
                author = d.str("author").ifBlank { item.subtitle },
                my_rating = d.dbl("my_rating").takeIf { it > 0 } ?: d.dbl("rating"),
                average_rating = d.dbl("average_rating"),
                number_of_pages = d.intVal("number_of_pages"),
                exclusive_shelf = d.str("exclusive_shelf"),
                image_url = item.image_url ?: d.str("image_url").ifBlank { null },
                date_read = item.date
            )
            BookDetailBottomSheet(book = book, onDismiss = onDismiss)
        }
        "health_connect" -> {
            val rec = HealthRecordDto(
                id = d.str("id").ifBlank { item.id },
                record_type = d.str("record_type").ifBlank { item.type },
                category = d.str("category").ifBlank { "activity" },
                date_iso = item.date,
                start_time = d.str("start_time").ifBlank { item.timestamp ?: item.date },
                end_time = d.str("end_time").ifBlank { item.timestamp ?: item.date },
                value_primary = d.dbl("value_primary").takeIf { it > 0 },
                value_secondary = d.dbl("value_secondary").takeIf { it > 0 },
                unit = d.str("unit"),
                title = item.title,
                subtitle = item.subtitle,
                data_origin = d.str("data_origin")
            )
            HealthRecordDetailBottomSheet(record = rec, onDismiss = onDismiss)
        }
        else -> {
            val postTime = item.timestamp?.takeIf { it.length >= 16 }
                ?: if (!item.time.isNullOrBlank()) "${item.date}T${item.time}" else item.date
            val notif = NotificationDto(
                id = item.id,
                package_name = d.str("package_name"),
                app_name = d.str("app_name"),
                title = item.title,
                text = item.subtitle,
                sub_text = d.str("sub_text"),
                big_text = d.str("big_text"),
                category = d.str("category"),
                post_time = postTime
            )
            NotificationDetailBottomSheet(notif = notif, onDismiss = onDismiss)
        }
    }
}


private val LazyCardTopShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
private val LazyCardBottomShape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)

@Composable
fun LazyCardRowContainer(
    index: Int,
    totalCount: Int,
    modifier: Modifier = Modifier,
    showDivider: Boolean = index > 0,
    content: @Composable () -> Unit
) {
    val shape = when {
        totalCount <= 1 -> M3Shapes.CornerLarge
        index == 0 -> LazyCardTopShape
        index == totalCount - 1 -> LazyCardBottomShape
        else -> RectangleShape
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        if (showDivider) {
            Column {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                content()
            }
        } else {
            content()
        }
    }
}




