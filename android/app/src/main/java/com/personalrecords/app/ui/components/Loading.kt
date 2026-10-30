package com.personalrecords.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.ContainedLoadingIndicator as Material3ContainedLoadingIndicator
import androidx.compose.material3.LoadingIndicator as Material3LoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import com.personalrecords.app.core.theme.*

// Loading indicators and pull-to-refresh.

/**
 * Size and color adapter for Material 3's official shape-morphing loading indicator.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveLoadingIndicator(
    modifier: Modifier = Modifier,
    size: Dp = LoadingIndicatorDefaults.IndicatorSize,
    color: Color = MaterialTheme.colorScheme.primary,
    strokeWidth: Dp = 2.dp,
    progress: (() -> Float)? = null
) {
    val indicatorModifier = modifier.size(size)
    if (progress == null) {
        Material3LoadingIndicator(modifier = indicatorModifier, color = color)
    } else {
        Material3LoadingIndicator(
            progress = progress,
            modifier = indicatorModifier,
            color = color
        )
    }
}

/** Size and palette adapter for Material 3's official contained loading indicator. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppContainedLoadingIndicator(
    modifier: Modifier = Modifier,
    size: Dp = LoadingIndicatorDefaults.ContainerWidth,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    indicatorColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    progress: (() -> Float)? = null
) {
    val indicatorModifier = modifier.size(size)
    if (progress == null) {
        Material3ContainedLoadingIndicator(
            modifier = indicatorModifier,
            containerColor = containerColor,
            indicatorColor = indicatorColor
        )
    } else {
        Material3ContainedLoadingIndicator(
            progress = progress,
            modifier = indicatorModifier,
            containerColor = containerColor,
            indicatorColor = indicatorColor
        )
    }
}

/**
 * MD3 Expressive Segmented Linear Progress Indicator.
 *
 * Visualizes multi-category or nutrient distribution (such as Carbs, Protein, Fat)
 * as discrete, capsule-shaped pills with fully rounded caps (Material 3 CornerFull)
 * separated by a minimal spacing (`gap`).
 *
 * For determinate progress tracking, an inactive track with rounded caps is rendered for the
 * remaining progress, separated from the active segments by `trackGap`.
 */
@Composable
fun SegmentedLinearProgressIndicator(
    progress: () -> Float,
    segments: List<Pair<Float, Color>>?,
    modifier: Modifier = Modifier,
    barHeight: Dp = 8.dp,
    gap: Dp = 3.dp,
    trackGap: Dp = 4.dp,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    singleColor: Color = MaterialTheme.colorScheme.primary,
    drawStopIndicator: Boolean = false
) {
    val activeSegments = remember(segments) {
        segments?.filter { it.first > 0f }.orEmpty()
    }
    val totalWeight = remember(activeSegments) {
        activeSegments.sumOf { it.first.toDouble() }.toFloat()
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(barHeight)
    ) {
        val p = progress().coerceIn(0f, 1f)
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        val gapPx = gap.toPx()
        val trackGapPx = trackGap.toPx()
        val activeWidth = (w * p).coerceIn(0f, w)

        // 1. Draw Active Segment(s)
        if (p > 0.005f && activeWidth > 0f) {
            if (activeSegments.isNotEmpty() && totalWeight > 0f) {
                val n = activeSegments.size
                val totalGaps = if (n > 1) (n - 1) * gapPx else 0f
                val effectiveGap = if (activeWidth > totalGaps + n * 2f) {
                    gapPx
                } else {
                    (activeWidth / (n * 3f)).coerceAtLeast(0f)
                }
                val effectiveTotalGaps = if (n > 1) (n - 1) * effectiveGap else 0f
                val availableSpan = (activeWidth - effectiveTotalGaps).coerceAtLeast(0f)

                var curX = 0f
                activeSegments.forEach { (weight, color) ->
                    val segFraction = weight / totalWeight
                    val segWidth = (availableSpan * segFraction).coerceAtLeast(0f)

                    if (segWidth > 0f) {
                        val rx = (h / 2f).coerceAtMost(segWidth / 2f)
                        val ry = h / 2f
                        drawRoundRect(
                            color = color,
                            topLeft = Offset(curX, 0f),
                            size = Size(segWidth, h),
                            cornerRadius = CornerRadius(rx, ry)
                        )
                    }
                    curX += segWidth + effectiveGap
                }
            } else {
                // Fallback: single active pill
                val rx = (h / 2f).coerceAtMost(activeWidth / 2f)
                val ry = h / 2f
                drawRoundRect(
                    color = singleColor,
                    topLeft = Offset(0f, 0f),
                    size = Size(activeWidth, h),
                    cornerRadius = CornerRadius(rx, ry)
                )
            }
        }

        // 2. Draw Inactive Track
        val trackStartX = if (p > 0.005f) {
            (activeWidth + trackGapPx)
        } else {
            0f
        }
        val trackEndX = w
        val trackWidth = trackEndX - trackStartX
        if (trackWidth > 1f) {
            val rx = (h / 2f).coerceAtMost(trackWidth / 2f)
            val ry = h / 2f
            drawRoundRect(
                color = trackColor,
                topLeft = Offset(trackStartX, 0f),
                size = Size(trackWidth, h),
                cornerRadius = CornerRadius(rx, ry)
            )
            if (drawStopIndicator && trackWidth >= h) {
                drawCircle(
                    color = singleColor,
                    radius = h / 2f,
                    center = Offset(w - h / 2f, h / 2f)
                )
            }
        }
    }
}

/**
 * MD3 Expressive Pull-To-Refresh Indicator (`PullToRefreshWithLoadingIndicatorSample` from `PullToRefreshSamples.kt`).
 *
 * Uses Material 3's official shape-morphing pull-to-refresh indicator while keeping the app's
 * threshold haptic and color treatment.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
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
    LaunchedEffect(isPastThreshold) {
        if (isPastThreshold) {
            haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
        }
    }

    PullToRefreshDefaults.LoadingIndicator(
        state = state,
        isRefreshing = isRefreshing,
        modifier = modifier,
        containerColor = if (isRefreshing || isPastThreshold) {
            containerColor
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        color = if (isRefreshing || isPastThreshold) {
            color
        } else {
            MaterialTheme.colorScheme.primary
        },
        maxDistance = threshold
    )
}

/**
 * MD3 Expressive PullToRefreshBox.
 *
 * Drop-in container supporting swipe-to-refresh with the Material 3 Expressive
 * official shape-morphing loading indicator and threshold haptic.
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
