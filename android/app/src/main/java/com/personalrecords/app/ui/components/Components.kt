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
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (badgeText.isNotBlank()) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Surface(
                        shape = CircleShape,
                        color = badgeBg
                    ) {
                        Text(
                            text = badgeText,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
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
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
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
            shape = CircleShape,
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
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
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
 * Morphs corner curvature (8dp squircle to 20dp pill) and applies a playful spring scale pop
 * on selection, accompanied by subtle tactile haptic feedback.
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
        targetValue = if (selected) 20.dp else 8.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "chip_corner"
    )
    val animatedScale by animateFloatAsState(
        targetValue = if (selected) 1.04f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
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
        targetValue = if (isRefreshing) 20.dp else 14.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
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
        Crossfade(
            targetState = isRefreshing,
            animationSpec = tween(durationMillis = 250),
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
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = if (isRefreshing || state.isAnimating) Spring.StiffnessMediumLow else Spring.StiffnessMedium
        ),
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
 * Applies a tactile compression scale down on press (e.g. 0.96f) and a playful spring bounce
 * return upon release using Material 3 Expressive spring physics.
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
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
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
 */
fun Modifier.expressivePressScale(
    interactionSource: InteractionSource,
    scaleDown: Float = 0.96f
): Modifier = composed {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) scaleDown else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "expressive_press_scale"
    )
    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * MD3 Expressive spring scroll to top extension for [LazyListState].
 * Smoothly scrolls the list to the top with Material 3 Expressive spring physics.
 */
suspend fun LazyListState.expressiveScrollToTop() {
    if (firstVisibleItemIndex > 2) {
        scrollToItem(2)
    }
    if (firstVisibleItemIndex > 0) {
        scrollToItem(0, 400)
    }
    val item0Info = layoutInfo.visibleItemsInfo.firstOrNull { it.index == 0 }
    val offsetToScroll = if (item0Info != null) {
        -item0Info.offset.toFloat()
    } else {
        firstVisibleItemScrollOffset.toFloat()
    }

    if (offsetToScroll > 0f) {
        animateScrollBy(
            value = -offsetToScroll,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    }
    scrollToItem(0, 0)
}

