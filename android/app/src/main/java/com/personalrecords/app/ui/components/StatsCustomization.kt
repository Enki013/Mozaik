package com.personalrecords.app.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.ui.zIndex
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.personalrecords.app.R
import com.personalrecords.app.core.theme.*

/**
 * Edit-mode marker for reorderable stat cards: an outline around the card, without the looping
 * iOS-style wobble. Dragging still lifts the card (see [StatCardGrid]).
 */
@Composable
fun Modifier.statCardEditOutline(enabled: Boolean): Modifier {
    val alpha by animateFloatAsState(
        targetValue = if (enabled) 1f else 0f,
        animationSpec = M3ExpressiveMotion.fastEffects(),
        label = "edit_outline_alpha"
    )
    val color = MaterialTheme.colorScheme.outline
    val shape = MaterialTheme.shapes.medium
    return this.drawWithContent {
        drawContent()
        if (alpha > 0.01f) {
            val outline = shape.createOutline(size, layoutDirection, this)
            drawOutline(outline, color = color.copy(alpha = alpha), style = Stroke(width = 2.dp.toPx()))
        }
    }
}

/**
 * Wrapper container for customizable stat cards in Edit Mode.
 * - In Edit Mode: Shakes with spring physics and renders an animated hide badge on top-right.
 * - Long-pressing any card triggers Edit Mode with haptic feedback.
 */
@Composable
fun StatCardContainer(
    cardId: String,
    isEditMode: Boolean,
    onHideCard: () -> Unit,
    modifier: Modifier = Modifier,
    index: Int = 0,
    content: @Composable () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    key(cardId) {
        Box(
            modifier = modifier.statCardEditOutline(enabled = isEditMode)
        ) {
            content()

            AnimatedVisibility(
                visible = isEditMode,
                enter = fadeIn(animationSpec = M3ExpressiveMotion.fastEffects()) +
                        scaleIn(initialScale = 0.4f, animationSpec = M3ExpressiveMotion.fastSpatial()),
                exit = fadeOut(animationSpec = M3ExpressiveMotion.fastEffects()) +
                        scaleOut(targetScale = 0.4f, animationSpec = M3ExpressiveMotion.fastSpatial()),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 4.dp, end = 4.dp)
            ) {
                FilledTonalIconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                        onHideCard()
                    },
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    modifier = Modifier.size(IconButtonDefaults.extraSmallContainerSize())
                ) {
                    Icon(
                        imageVector = Icons.Rounded.VisibilityOff,
                        contentDescription = stringResource(R.string.stats_hide_this_card),
                        modifier = Modifier.size(IconButtonDefaults.extraSmallIconSize)
                    )
                }
            }
        }
    }
}

/**
 * TopAppBar Action for stat cards:
 * - When in Edit Mode: Displays a "Done" pill button with Check icon.
 * - When NOT in Edit Mode: Displays a customize icon button (with optional dot badge if cards are hidden).
 */
@Composable
fun TopAppBarStatsAction(
    isEditMode: Boolean,
    hasHiddenCards: Boolean,
    onToggleEditMode: () -> Unit,
    onOpenCustomization: () -> Unit
) {
    val effectsSpec = M3ExpressiveMotion.fastEffects<Float>()
    val spatialSpec = M3ExpressiveMotion.fastSpatial<Float>()

    AnimatedContent(
        targetState = isEditMode,
        transitionSpec = {
            (fadeIn(animationSpec = effectsSpec) +
             scaleIn(initialScale = 0.8f, animationSpec = spatialSpec))
                .togetherWith(
                    fadeOut(animationSpec = effectsSpec) +
                    scaleOut(targetScale = 0.8f, animationSpec = spatialSpec)
                )
        },
        label = "top_bar_stats_action"
    ) { inEditMode ->
        if (inEditMode) {
            FilledTonalButton(
                onClick = onToggleEditMode,
                modifier = Modifier,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                shapes = expressiveButtonShapes(),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.action_done),
                    style = MaterialTheme.typography.labelMediumEmphasized
                )
            }
        } else {
            IconButton(onClick = onOpenCustomization) {
                BadgedBox(
                    badge = {
                        if (hasHiddenCards) {
                            Badge(
                                containerColor = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(6.dp)
                            )
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DashboardCustomize,
                        contentDescription = stringResource(R.string.stats_customization_title),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Definition of a customizable stat card on a screen.
 */
data class StatCardItem(
    val id: String,
    val title: String,
    val description: String = "",
    val icon: ImageVector? = null,
    val iconTint: Color = Color.Unspecified
)

/**
 * M3 Expressive Bottom Sheet for customizing / hiding / showing stat cards.
 * Follows Google Material 3 Expressive guidelines and physics-based motion.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsCustomizationBottomSheet(
    sheetTitle: String = stringResource(R.string.stats_customization_title),
    cards: List<StatCardItem>,
    hiddenCardIds: Set<String>,
    targetCardId: String? = null,
    onToggleCard: (cardId: String, visible: Boolean) -> Unit,
    onReorder: ((orderedIds: List<String>) -> Unit)? = null,
    onResetDefault: () -> Unit,
    onDismissRequest: () -> Unit
) {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header: Icon + Title + Subtitle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = sheetTitle,
                        style = MaterialTheme.typography.titleMediumEmphasized,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.stats_customization_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Quick Action: "Hide this card" if user long-pressed a visible card
            val targetCard = remember(targetCardId, cards) {
                cards.firstOrNull { it.id == targetCardId }
            }
            if (targetCard != null && targetCard.id !in hiddenCardIds) {
                FilledTonalButton(
                    onClick = {
                        onToggleCard(targetCard.id, false)
                        onDismissRequest()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shapes = expressiveButtonShapes(),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.VisibilityOff,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.stats_hide_card_action, targetCard.title),
                        style = MaterialTheme.typography.labelLargeEmphasized
                    )
                }
            }

            // Drag the handle to reorder; tap a row (or its switch) to show or hide the card.
            ReorderableStatCardList(
                cards = cards,
                hiddenCardIds = hiddenCardIds,
                onToggleCard = onToggleCard,
                onReorder = onReorder
            )

            // Bottom Reset & Close Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Resetting drops the user's order and hidden cards, so it reads as destructive.
                TextButton(
                    onClick = onResetDefault,
                    shapes = expressiveButtonShapes(),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(text = stringResource(R.string.stats_reset_default))
                }

                Button(
                    onClick = onDismissRequest,
                    shapes = expressiveButtonShapes()
                ) {
                    Text(text = stringResource(R.string.action_close))
                }
            }
        }
    }
}

/**
 * Segmented list of stat cards that can be reordered by dragging the handle.
 * The dragged row lifts and follows the finger, neighbours spring out of the way, and the
 * new order is saved once when the finger lifts. Screen readers get move up/down actions.
 */
@Composable
private fun ReorderableStatCardList(
    cards: List<StatCardItem>,
    hiddenCardIds: Set<String>,
    onToggleCard: (cardId: String, visible: Boolean) -> Unit,
    onReorder: ((orderedIds: List<String>) -> Unit)?
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val reducedMotion = LocalReducedMotion.current
    val settleSpec = M3ExpressiveMotion.defaultSpatial<Float>()
    val gapPx = with(LocalDensity.current) { ListItemDefaults.SegmentedGap.toPx() }
    val cardsById = remember(cards) { cards.associateBy { it.id } }

    // One local list for the whole lifetime: the drag handlers are created once and must always
    // see the list that is on screen. It follows the saved order whenever no drag is running.
    val savedIds = cards.map { it.id }
    val currentSavedIds by rememberUpdatedState(savedIds)
    val currentOnReorder by rememberUpdatedState(onReorder)
    val order = remember { mutableStateListOf(*savedIds.toTypedArray()) }
    var draggingId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(savedIds) {
        if (draggingId == null && order.toList() != savedIds) {
            order.clear()
            order.addAll(savedIds)
        }
    }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val heights = remember { mutableStateMapOf<String, Int>() }
    val shifts = remember { mutableStateMapOf<String, Animatable<Float, AnimationVector1D>>() }

    fun animateShift(id: String, from: Float) {
        val anim = shifts.getOrPut(id) { Animatable(0f) }
        scope.launch {
            if (reducedMotion) {
                anim.snapTo(0f)
            } else {
                anim.snapTo(anim.value + from)
                anim.animateTo(0f, settleSpec)
            }
        }
    }

    fun commit() {
        val newOrder = order.toList()
        if (newOrder != currentSavedIds) currentOnReorder?.invoke(newOrder)
    }

    fun rowHeight(id: String): Float = (heights[id] ?: 0) + gapPx

    fun dragBy(dy: Float) {
        val id = draggingId ?: return
        dragOffset += dy
        val index = order.indexOf(id)
        if (dragOffset > 0 && index < order.lastIndex) {
            val next = order[index + 1]
            if (dragOffset > rowHeight(next) / 2) {
                order.removeAt(index)
                order.add(index + 1, id)
                dragOffset -= rowHeight(next)
                animateShift(next, rowHeight(id))
                haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
            }
        } else if (dragOffset < 0 && index > 0) {
            val previous = order[index - 1]
            if (-dragOffset > rowHeight(previous) / 2) {
                order.removeAt(index)
                order.add(index - 1, id)
                dragOffset += rowHeight(previous)
                animateShift(previous, -rowHeight(id))
                haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
            }
        }
    }

    fun endDrag() {
        val id = draggingId ?: return
        // Let the lifted row glide into its slot instead of jumping.
        animateShift(id, dragOffset)
        dragOffset = 0f
        draggingId = null
        commit()
    }

    fun moveBy(id: String, delta: Int): Boolean {
        val index = order.indexOf(id)
        val target = index + delta
        if (index == -1 || target !in order.indices) return false
        order.add(target, order.removeAt(index))
        commit()
        return true
    }

    val moveUpLabel = stringResource(R.string.action_move_up)
    val moveDownLabel = stringResource(R.string.action_move_down)
    val dragHandleLabel = stringResource(R.string.stats_drag_to_reorder)

    Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        order.forEachIndexed { index, id ->
            val card = cardsById[id] ?: return@forEachIndexed
            key(id) {
                val isDragging = id == draggingId
                val isVisible = id !in hiddenCardIds
                val lift by animateDpAsState(
                    targetValue = if (isDragging) 6.dp else 0.dp,
                    animationSpec = M3ExpressiveMotion.fastSpatial(),
                    label = "stat_row_lift"
                )
                Surface(
                    shape = if (isDragging) MaterialTheme.shapes.large else M3Shapes.groupedListItem(index, order.size),
                    color = if (isDragging) MaterialTheme.colorScheme.surfaceContainerHighest
                            else MaterialTheme.colorScheme.surfaceContainerHigh,
                    shadowElevation = lift,
                    modifier = Modifier
                        .fillMaxWidth()
                        .zIndex(if (isDragging) 1f else 0f)
                        .graphicsLayer {
                            translationY = if (isDragging) dragOffset else (shifts[id]?.value ?: 0f)
                        }
                        .onSizeChanged { heights[id] = it.height }
                        .semantics {
                            customActions = buildList {
                                if (index > 0) add(CustomAccessibilityAction(moveUpLabel) { moveBy(id, -1) })
                                if (index < order.lastIndex) add(CustomAccessibilityAction(moveDownLabel) { moveBy(id, 1) })
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggleCard(id, !isVisible) }
                            .padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Drag handle: a full 48dp target; dragging starts right away.
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .pointerInput(id) {
                                    detectDragGestures(
                                        onDragStart = {
                                            draggingId = id
                                            dragOffset = 0f
                                            haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                                        },
                                        onDragEnd = { endDrag() },
                                        onDragCancel = { endDrag() },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            dragBy(amount.y)
                                        }
                                    )
                                }
                                .semantics { contentDescription = dragHandleLabel },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DragIndicator,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (card.icon != null) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(MaterialTheme.shapes.small)
                                    .background(
                                        if (card.iconTint != Color.Unspecified) card.iconTint.copy(alpha = 0.15f)
                                        else MaterialTheme.colorScheme.primaryContainer
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = card.icon,
                                    contentDescription = null,
                                    tint = if (card.iconTint != Color.Unspecified) card.iconTint else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = card.title,
                                style = MaterialTheme.typography.bodyMediumEmphasized,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (card.description.isNotBlank()) {
                                Text(
                                    text = card.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Switch(
                            checked = isVisible,
                            onCheckedChange = { onToggleCard(id, it) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Saved order of a screen's stat cards plus the actions the customization sheet needs.
 * Every screen uses this, so cards are reordered the same way everywhere.
 */
@Stable
class StatCardOrder internal constructor(
    val orderedIds: List<String>,
    private val onSetOrder: (List<String>) -> Unit,
    private val onReset: () -> Unit
) {
    fun setOrder(ids: List<String>) = onSetOrder(ids)

    /** Saves a new order for the visible cards, keeping hidden cards in their slots. */
    fun setVisibleOrder(visible: List<String>) {
        val visibleSet = visible.toSet()
        val next = visible.iterator()
        onSetOrder(orderedIds.map { if (it in visibleSet && next.hasNext()) next.next() else it })
    }
    fun reset() = onReset()

    /** The given cards sorted into the saved order. */
    fun sort(cards: List<StatCardItem>): List<StatCardItem> {
        val byId = cards.associateBy { it.id }
        return orderedIds.mapNotNull { byId[it] }
    }
}

@Composable
fun rememberStatCardOrder(screenKey: String, defaultOrder: List<String>): StatCardOrder {
    val preferences = remember { com.personalrecords.app.PersonalRecordsApp.instance.preferences }
    val scope = rememberCoroutineScope()
    val stored by preferences.getStatsCardsOrder(screenKey, defaultOrder)
        .collectAsState(initial = defaultOrder)
    // Drop ids that no longer exist and append cards added after the order was saved.
    val resolved = remember(stored, defaultOrder) {
        stored.filter { it in defaultOrder } + defaultOrder.filter { it !in stored }
    }
    return remember(resolved, screenKey) {
        StatCardOrder(
            orderedIds = resolved,
            onSetOrder = { ids -> scope.launch { preferences.setStatsCardsOrder(screenKey, ids) } },
            onReset = { scope.launch { preferences.resetStatsCardsOrder(screenKey) } }
        )
    }
}

/**
 * Two-column grid of customizable stat cards in the given order. A lone card on the last row
 * takes the full width, and so does every card in [fullWidthIds] (larger cards such as charts).
 *
 * In edit mode a card can be dragged: it follows the finger, swaps with the card it is held
 * over, and the other cards glide to their new slots. The new order is saved when the finger
 * lifts. Outside edit mode the grid is static and taps/long-presses go to the cards.
 */
@Composable
fun StatCardGrid(
    cardIds: List<String>,
    isEditMode: Boolean,
    onHideCard: (String) -> Unit,
    modifier: Modifier = Modifier,
    onReorder: ((visibleOrder: List<String>) -> Unit)? = null,
    fullWidthIds: Set<String> = emptySet(),
    card: @Composable (cardId: String) -> Unit
) {
    if (cardIds.isEmpty()) return
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val reducedMotion = LocalReducedMotion.current
    val settleSpec = M3ExpressiveMotion.defaultSpatial<Offset>()

    // Same rule as the sheet: one list, kept in sync with the saved order between drags.
    val currentCardIds by rememberUpdatedState(cardIds)
    val currentOnReorder by rememberUpdatedState(onReorder)
    val order = remember { mutableStateListOf(*cardIds.toTypedArray()) }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(cardIds) {
        if (draggingId == null && order.toList() != cardIds) {
            order.clear()
            order.addAll(cardIds)
        }
    }
    var gridCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    // Card slots relative to the grid, so list scrolling never looks like movement.
    val slots = remember { mutableMapOf<String, Rect>() }
    // After a swap the slots are stale until the next layout; without this guard a fast drag
    // would swap the same pair back on the very next pointer event.
    val awaitingLayout = remember { booleanArrayOf(false) }
    val shifts = remember { mutableStateMapOf<String, Animatable<Offset, AnimationVector2D>>() }

    fun shiftFrom(id: String, delta: Offset) {
        if (delta == Offset.Zero) return
        val anim = shifts.getOrPut(id) { Animatable(Offset.Zero, Offset.VectorConverter) }
        scope.launch {
            if (reducedMotion) {
                anim.snapTo(Offset.Zero)
            } else {
                anim.snapTo(anim.value + delta)
                anim.animateTo(Offset.Zero, settleSpec)
            }
        }
    }

    fun onSlotPlaced(id: String, rect: Rect) {
        val old = slots[id]
        slots[id] = rect
        if (old == null || old.topLeft == rect.topLeft) return
        val moved = old.topLeft - rect.topLeft
        if (id == draggingId) {
            // Keep the lifted card under the finger after its slot moved.
            dragOffset += moved
            awaitingLayout[0] = false
        } else {
            shiftFrom(id, moved)
        }
    }

    fun dragBy(delta: Offset) {
        val id = draggingId ?: return
        dragOffset += delta
        if (awaitingLayout[0]) return
        val home = slots[id] ?: return
        val pointer = home.center + dragOffset
        val target = order.firstOrNull { it != id && slots[it]?.contains(pointer) == true } ?: return
        val from = order.indexOf(id)
        val to = order.indexOf(target)
        order.add(to, order.removeAt(from))
        awaitingLayout[0] = true
        haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    }

    fun endDrag() {
        val id = draggingId ?: return
        shiftFrom(id, dragOffset)
        dragOffset = Offset.Zero
        draggingId = null
        awaitingLayout[0] = false
        if (order.toList() != currentCardIds) currentOnReorder?.invoke(order.toList())
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { gridCoords = it },
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        statCardRows(order, fullWidthIds).forEachIndexed { rowIndex, rowIds ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                rowIds.forEachIndexed { colIndex, cardId ->
                    key(cardId) {
                        val isDragging = cardId == draggingId
                        val lift by animateFloatAsState(
                            targetValue = if (isDragging) 1.04f else 1f,
                            animationSpec = M3ExpressiveMotion.fastSpatial(),
                            label = "stat_card_lift"
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .zIndex(if (isDragging) 1f else 0f)
                                // Measured before the drag translation, so this is the card's slot.
                                .onGloballyPositioned { coords ->
                                    val grid = gridCoords ?: return@onGloballyPositioned
                                    if (!grid.isAttached || !coords.isAttached) return@onGloballyPositioned
                                    val topLeft = grid.localPositionOf(coords, Offset.Zero)
                                    onSlotPlaced(cardId, Rect(topLeft, coords.size.toSize()))
                                }
                                .graphicsLayer {
                                    val offset = if (isDragging) dragOffset else (shifts[cardId]?.value ?: Offset.Zero)
                                    translationX = offset.x
                                    translationY = offset.y
                                    scaleX = lift
                                    scaleY = lift
                                }
                                .then(
                                    if (isEditMode && onReorder != null) {
                                        Modifier.pointerInput(cardId) {
                                            detectDragGestures(
                                                onDragStart = {
                                                    draggingId = cardId
                                                    dragOffset = Offset.Zero
                                                    haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                                                },
                                                onDragEnd = { endDrag() },
                                                onDragCancel = { endDrag() },
                                                onDrag = { change, amount ->
                                                    change.consume()
                                                    dragBy(amount)
                                                }
                                            )
                                        }
                                    } else Modifier
                                )
                        ) {
                            StatCardContainer(
                                cardId = cardId,
                                isEditMode = isEditMode && !isDragging,
                                onHideCard = { onHideCard(cardId) },
                                index = rowIndex * 2 + colIndex
                            ) {
                                card(cardId)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Splits cards into rows of two; a card in [fullWidthIds] gets a row of its own. */
private fun statCardRows(order: List<String>, fullWidthIds: Set<String>): List<List<String>> {
    if (fullWidthIds.isEmpty()) return order.chunked(2)
    val rows = mutableListOf<List<String>>()
    val pending = mutableListOf<String>()
    for (id in order) {
        if (id in fullWidthIds) {
            if (pending.isNotEmpty()) {
                rows += pending.toList()
                pending.clear()
            }
            rows += listOf(id)
        } else {
            pending += id
            if (pending.size == 2) {
                rows += pending.toList()
                pending.clear()
            }
        }
    }
    if (pending.isNotEmpty()) rows += pending.toList()
    return rows
}
