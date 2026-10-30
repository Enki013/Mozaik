package com.personalrecords.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import com.personalrecords.app.core.theme.LocalReducedMotion
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.IntSize
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.onClick
import com.personalrecords.app.core.theme.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

// Press, bounce, shake and lazy-item motion modifiers.

/**
 * MD3 Expressive spring bounce click modifier.
 *
 * Uses the M3 Expressive fast spatial spring for small component scale compression and recovery
 * via [ExpressivePressScaleNode] without `Modifier.composed`.
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
 * MD3 Expressive spring bounce combined click modifier supporting both onClick and onLongClick.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.expressiveBounceCombinedClick(
    scaleDown: Float = 0.96f,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
): Modifier = this
    .expressivePressScale(
        interactionSource = null,
        scaleDown = scaleDown,
        enabled = enabled
    )
    .combinedClickable(
        enabled = enabled,
        onClick = { onClick?.invoke() },
        onLongClick = onLongClick
    )

/**
 * MD3 Expressive spring press scale modifier implemented via [ModifierNodeElement] and [Modifier.Node]
 * to avoid `Modifier.composed` subcomposition overhead on lazy list items.
 * Uses the M3 Expressive fast spatial spring; skipped when reduced motion is on.
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

/** M3 Expressive fast spatial spring, the motion token for small component state changes. */
private val PressScaleSpec = androidx.compose.material3.MotionScheme.expressive().fastSpatialSpec<Float>()

private class ExpressivePressScaleNode(
    private var scaleDown: Float,
    private var enabled: Boolean
) : Modifier.Node(), PointerInputModifierNode, DrawModifierNode,
    androidx.compose.ui.node.CompositionLocalConsumerModifierNode {
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
        // No press scale when the system "remove animations" setting is on.
        if (pass != PointerEventPass.Main || !enabled || currentValueOf(LocalReducedMotion)) return
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
                animationSpec = PressScaleSpec
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
            haptic.performHapticFeedback(HapticFeedbackType.Reject)
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
                        amplitudePx at 50 using FastOutLinearInEasing
                        amplitudePx * 0.7f at 110 using FastOutSlowInEasing
                        amplitudePx * 0.45f at 170 using FastOutSlowInEasing
                        amplitudePx * 0.2f at 230 using FastOutSlowInEasing
                        amplitudePx * 0.08f at 280 using FastOutSlowInEasing
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
 * MD3 Expressive physics for lazy list item placement and appearance.
 * fadeInSpec is disabled (null) on initial list entry to prevent heavy concurrent alpha animations
 * during screen navigation transitions, preserving pure 120 FPS hardware translation.
 */
@Composable
fun LazyItemScope.expressiveItemModifier(
    modifier: Modifier = Modifier
): Modifier = modifier.animateItem(
    fadeInSpec = null,
    placementSpec = M3ExpressiveMotion.defaultSpatial()
)

/**
 * MD3 Expressive physics for lazy grid item placement and appearance.
 * fadeInSpec is disabled (null) on initial list entry to prevent heavy concurrent alpha animations
 * during screen navigation transitions, preserving pure 120 FPS hardware translation.
 */
@Composable
fun LazyGridItemScope.expressiveItemModifier(
    modifier: Modifier = Modifier
): Modifier = modifier.animateItem(
    fadeInSpec = null,
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
