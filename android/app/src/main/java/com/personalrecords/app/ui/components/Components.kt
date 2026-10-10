package com.personalrecords.app.ui.components

import android.annotation.SuppressLint
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import androidx.compose.animation.Crossfade
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
import com.personalrecords.app.core.network.model.NotificationDto
import com.personalrecords.app.core.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import com.personalrecords.app.core.theme.*

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
        if (!imageUrl.isNullOrBlank()) {
            SubcomposeAsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        ExpressiveLoadingIndicator(
                            size = 16.dp,
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                error = {
                    Icon(
                        imageVector = fallbackIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size((minOf(width, height) / 2).coerceAtLeast(18).dp)
                    )
                }
            )
        } else {
            Icon(
                imageVector = fallbackIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size((minOf(width, height) / 2).coerceAtLeast(18).dp)
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
 * MD3 Expressive Filter Chip.
 *
 * Morphs corner curvature (`CornerSmall` 8dp -> `CornerLargeIncreased` 20dp) and applies a
 * fast spatial spring scale pop on selection, accompanied by subtle tactile haptic feedback.
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
    val animatedCorner by animateDpAsState(
        targetValue = if (selected) M3CornerRadius.LargeIncreased else M3CornerRadius.Small,
        animationSpec = M3ExpressiveMotion.fastSpatial(),
        label = "chip_corner"
    )
    val animatedScale by animateFloatAsState(
        targetValue = if (selected) 1.04f else 1f,
        animationSpec = M3ExpressiveMotion.fastSpatial(),
        label = "chip_scale"
    )

    FilterChip(
        selected = selected,
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        label = label,
        shape = RoundedCornerShape(animatedCorner),
        modifier = modifier.graphicsLayer {
            scaleX = animatedScale
            scaleY = animatedScale
        },
        border = null,
        colors = colors,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon
    )
}

/**
 * MD3 Expressive Shape-Morphing Loading Indicator.
 *
 * Pixel-faithful implementation of Google's official Material 3 Expressive loading indicator:
 * Organically morphs between a 12-point floral sunburst, a rounded circle, a 4-lobed clover/squircle,
 * and a 5-point organic flower with continuous spring-like motion and dynamic rotation.
 */
@Composable
fun ExpressiveLoadingIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    @Suppress("UNUSED_PARAMETER") strokeWidth: Dp = (size / 10).coerceIn(2.dp, 4.dp),
    color: Color = MaterialTheme.colorScheme.primary,
    @Suppress("UNUSED_PARAMETER") secondaryColor: Color = MaterialTheme.colorScheme.tertiary,
    @Suppress("UNUSED_PARAMETER") trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest
) {
    val infiniteTransition = rememberInfiniteTransition(label = "expressive_morph_loading")

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val cycleProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "cycleProgress"
    )

    val breathingScale by infiniteTransition.animateFloat(
        initialValue = 0.93f,
        targetValue = 1.07f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathingScale"
    )

    // Calculate current morphing parameters based on cycleProgress (0..3)
    val segment = cycleProgress.toInt().coerceIn(0, 2)
    val t = cycleProgress - segment
    val sinT = kotlin.math.sin(t * Math.PI).toFloat() // 0 at circle endpoints, peak in middle
    val (nLobes, maxAmp) = when (segment) {
        0 -> 12f to 0.16f // 12-point floral sunburst
        1 -> 4f to 0.32f  // 4-lobed clover / squircle
        else -> 5f to 0.22f // 5-point organic flower
    }
    val amplitude = maxAmp * sinT

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = breathingScale
                scaleY = breathingScale
                rotationZ = rotation
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding((size * 0.08f).coerceAtLeast(1.dp))) {
            val canvasW = this.size.width
            val canvasH = this.size.height
            val cx = canvasW / 2f
            val cy = canvasH / 2f
            val baseRadius = (minOf(canvasW, canvasH) / 2f) * 0.88f

            val path = Path()
            val points = 96
            for (i in 0 until points) {
                val theta = (i.toFloat() / points) * 2f * Math.PI.toFloat()
                val r = baseRadius * (1f + amplitude * kotlin.math.cos(nLobes * theta))
                val x = cx + r * kotlin.math.cos(theta)
                val y = cy + r * kotlin.math.sin(theta)
                if (i == 0) {
                    path.moveTo(x, y)
                } else {
                    path.lineTo(x, y)
                }
            }
            path.close()

            drawPath(
                path = path,
                color = color
            )
        }
    }
}

/**
 * MD3 Expressive Pull-To-Refresh Indicator.
 *
 * During drag gestures, the indicator smoothly blooms an organic 12-point floral star that
 * scales, rotates with gesture physics, and triggers a tactile haptic click when crossing
 * the activation threshold.
 *
 * During refreshing, it smoothly morphs into Google's official Material 3 Expressive
 * shape-morphing animation with spring-like continuous motion and breathing pulse.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpressivePullToRefreshIndicator(
    state: PullToRefreshState,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    color: Color = MaterialTheme.colorScheme.primary,
    threshold: Dp = PullToRefreshDefaults.PositionalThreshold,
) {
    val haptic = LocalHapticFeedback.current
    var hasHapticTriggered by remember { mutableStateOf(false) }

    LaunchedEffect(state.distanceFraction) {
        if (state.distanceFraction >= 1f && !hasHapticTriggered) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            hasHapticTriggered = true
        } else if (state.distanceFraction < 0.2f) {
            hasHapticTriggered = false
        }
    }

    val animatedCorner by animateDpAsState(
        targetValue = if (isRefreshing) M3CornerRadius.LargeIncreased else M3CornerRadius.Medium,
        animationSpec = M3ExpressiveMotion.defaultSpatial(),
        label = "ptr_corner"
    )

    Box(
        modifier = modifier
            .pullToRefreshIndicator(
                state = state,
                isRefreshing = isRefreshing,
                containerColor = containerColor,
                threshold = threshold,
                shape = RoundedCornerShape(animatedCorner),
                elevation = 6.dp
            ),
        contentAlignment = Alignment.Center
    ) {
        // Opacity transition uses M3 Expressive Effects spring (damping 1.0, no overshoot)
        Crossfade(
            targetState = isRefreshing,
            animationSpec = M3ExpressiveMotion.defaultEffects(),
            label = "ptr_crossfade"
        ) { refreshing ->
            if (refreshing) {
                ExpressiveLoadingIndicator(
                    size = 24.dp,
                    color = color
                )
            } else {
                val fraction = state.distanceFraction.coerceIn(0f, 1.3f)
                val pullScale = (fraction * 0.85f + 0.15f).coerceIn(0.2f, 1.15f)
                val pullRotation = fraction * 270f
                val amplitude = 0.24f * fraction.coerceAtMost(1f)

                Canvas(
                    modifier = Modifier
                        .size(24.dp)
                        .graphicsLayer {
                            scaleX = pullScale
                            scaleY = pullScale
                            rotationZ = pullRotation
                        }
                ) {
                    val w = size.width
                    val h = size.height
                    val cx = w / 2f
                    val cy = h / 2f
                    val baseRadius = (minOf(w, h) / 2f) * 0.82f

                    val path = Path()
                    val points = 72
                    val nLobes = 12f
                    for (i in 0 until points) {
                        val theta = (i.toFloat() / points) * 2f * Math.PI.toFloat()
                        val r = baseRadius * (1f + amplitude * kotlin.math.cos(nLobes * theta))
                        val x = cx + r * kotlin.math.cos(theta)
                        val y = cy + r * kotlin.math.sin(theta)
                        if (i == 0) {
                            path.moveTo(x, y)
                        } else {
                            path.lineTo(x, y)
                        }
                    }
                    path.close()

                    drawPath(
                        path = path,
                        color = color
                    )
                }
            }
        }
    }
}

/**
 * MD3 Expressive PullToRefreshBox.
 *
 * Drop-in container supporting swipe-to-refresh with the Material 3 Expressive
 * blooming flower drag gesture, haptic threshold pop, and shape-morphing loader.
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
    val maxShiftPx = with(density) { 108.dp.toPx() }

    val targetTranslation = when {
        isRefreshing -> maxShiftPx
        else -> (state.distanceFraction * maxShiftPx).coerceAtLeast(0f)
    }

    val animatedContentTranslationY by animateFloatAsState(
        targetValue = targetTranslation,
        animationSpec = M3ExpressiveMotion.defaultSpatial(),
        label = "ptr_content_shift"
    )

    Box(
        modifier = modifier.pullToRefresh(
            state = state,
            isRefreshing = isRefreshing,
            onRefresh = onRefresh
        ),
        contentAlignment = contentAlignment
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = animatedContentTranslationY
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
 * component scale compression and bounce recovery.
 */
fun Modifier.expressiveBounceClick(
    scaleDown: Float = 0.96f,
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) scaleDown else 1f,
        animationSpec = M3ExpressiveMotion.fastSpatial(),
        label = "expressive_bounce"
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = ripple(),
            enabled = enabled,
            onClick = onClick
        )
}

/**
 * MD3 Expressive spring press scale modifier for components that already manage their own interaction source.
 * Uses M3 Expressive Fast Spatial spring (`damping = 0.6f, stiffness = 800f`).
 */
fun Modifier.expressivePressScale(
    interactionSource: InteractionSource,
    scaleDown: Float = 0.96f
): Modifier = composed {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) scaleDown else 1f,
        animationSpec = M3ExpressiveMotion.fastSpatial(),
        label = "expressive_press_scale"
    )
    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * MD3 Expressive spring scroll to top extension for [LazyListState].
 * Smoothly scrolls the list to the top with an M3 Expressive Slow Spatial spring (`damping = 0.8f, stiffness = 200f`).
 */
suspend fun LazyListState.expressiveScrollToTop() {
    if (firstVisibleItemIndex > 8) {
        scrollToItem(8)
    }
    val visibleItems = layoutInfo.visibleItemsInfo
    val firstVisible = firstVisibleItemIndex
    val firstOffset = firstVisibleItemScrollOffset

    val averageItemHeight = if (visibleItems.isNotEmpty()) visibleItems.sumOf { it.size }.toFloat() / visibleItems.size else 200f
    val totalDistance = (firstVisible * averageItemHeight) + firstOffset

    if (totalDistance > 0f) {
        animateScrollBy(
            value = -totalDistance,
            animationSpec = M3ExpressiveMotion.slowSpatial()
        )
    }
    scrollToItem(0, 0)
}

enum class DateSelectionMode {
    SINGLE,
    RANGE
}

fun formatMillisToIsoDate(millis: Long): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    return formatter.format(Date(millis))
}

fun parseIsoDateToMillis(dateStr: String?): Long? {
    if (dateStr.isNullOrBlank()) return null
    return try {
        val clean = dateStr.trim().replace('/', '-')
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        formatter.parse(clean)?.time
    } catch (e: Exception) {
        null
    }
}

fun formatIsoToReadableDate(dateStr: String): String {
    return try {
        val clean = dateStr.trim().replace('/', '-')
        val inputFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val date = inputFormatter.parse(clean) ?: return dateStr
        val outputFormatter = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
        outputFormatter.format(date)
    } catch (e: Exception) {
        dateStr
    }
}

fun formatIsoToShortDate(dateStr: String): String {
    return try {
        val clean = dateStr.trim().replace('/', '-')
        val inputFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val date = inputFormatter.parse(clean) ?: return dateStr
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
                // Segmented mode toggle
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp)
                ) {
                    SegmentedButton(
                        selected = selectionMode == DateSelectionMode.SINGLE,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            selectionMode = DateSelectionMode.SINGLE
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) {
                        Text(
                            text = stringResource(R.string.date_picker_single_day),
                            style = if (selectionMode == DateSelectionMode.SINGLE) MaterialTheme.typography.labelMediumEmphasized else MaterialTheme.typography.labelMedium
                        )
                    }
                    SegmentedButton(
                        selected = selectionMode == DateSelectionMode.RANGE,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            selectionMode = DateSelectionMode.RANGE
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) {
                        Text(
                            text = stringResource(R.string.date_picker_date_range),
                            style = if (selectionMode == DateSelectionMode.RANGE) MaterialTheme.typography.labelMediumEmphasized else MaterialTheme.typography.labelMedium
                        )
                    }
                }

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodEntryDetailBottomSheet(
    item: FoodEntryDto,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val mealLabel = localizedMealName(item.meal)
    val mealIcon = mealIconFor(item.meal)

    val carbKcal = item.carbohydrate * 4
    val protKcal = item.protein * 4
    val fatKcal = item.fat * 9
    val totalMacroKcal = carbKcal + protKcal + fatKcal
    val carbPct = if (totalMacroKcal > 0) (carbKcal / totalMacroKcal).toFloat() else 0.33f
    val protPct = if (totalMacroKcal > 0) (protKcal / totalMacroKcal).toFloat() else 0.33f
    val fatPct = if (totalMacroKcal > 0) (fatKcal / totalMacroKcal).toFloat() else 0.34f

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilmDetailBottomSheet(
    film: FilmDto,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val watchedDate = film.safeWatchedDate.ifBlank { film.diary_date ?: "" }

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailBottomSheet(
    book: BookDto,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val shelfLabel = when (book.exclusive_shelf.lowercase()) {
        "read" -> stringResource(R.string.books_shelf_read)
        "to-read" -> stringResource(R.string.books_shelf_to_read)
        "currently-reading" -> stringResource(R.string.books_shelf_currently_reading)
        else -> book.exclusive_shelf.ifBlank { stringResource(R.string.books_badge_book) }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationDetailBottomSheet(
    notif: NotificationDto,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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
        index == 0 -> RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
        index == totalCount - 1 -> RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
        else -> RoundedCornerShape(0.dp)
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column {
            if (showDivider) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
            content()
        }
    }
}




