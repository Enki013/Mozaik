package com.personalrecords.app.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.personalrecords.app.R
import com.personalrecords.app.core.theme.*

/**
 * Modifier providing a playful M3 physical shake/jiggle effect when edit mode is active.
 * Applies subtle rotation and micro-translation with slight phase offsets.
 */
@Composable
fun Modifier.expressiveShake(enabled: Boolean, index: Int = 0): Modifier {
    if (!enabled) return this
    val infiniteTransition = rememberInfiniteTransition(label = "expressive_shake_$index")
    val rotation by infiniteTransition.animateFloat(
        initialValue = -1.4f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (index % 2 == 0) 130 else 160,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shake_rotation_$index"
    )
    val translationY by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (index % 2 == 0) 150 else 180,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shake_translation_$index"
    )
    return this.graphicsLayer {
        rotationZ = rotation
        this.translationY = translationY
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
    Box(
        modifier = modifier.expressiveShake(enabled = isEditMode, index = index)
    ) {
        content()

        AnimatedVisibility(
            visible = isEditMode,
            enter = fadeIn(animationSpec = M3ExpressiveMotion.fastSpatial()) +
                    scaleIn(initialScale = 0.4f, animationSpec = M3ExpressiveMotion.fastSpatial()),
            exit = fadeOut(animationSpec = M3ExpressiveMotion.fastEffects()) +
                    scaleOut(targetScale = 0.4f, animationSpec = M3ExpressiveMotion.fastEffects()),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 4.dp, end = 4.dp)
        ) {
            Surface(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onHideCard()
                },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                tonalElevation = 3.dp,
                shadowElevation = 4.dp,
                modifier = Modifier.size(28.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = Icons.Default.VisibilityOff,
                        contentDescription = stringResource(R.string.stats_hide_this_card),
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }
}

/**
 * TopAppBar Action for stat cards:
 * - When in Edit Mode: Displays a "Bitti" (Done) pill button with Check icon.
 * - When NOT in Edit Mode: Displays a customize icon button (with optional dot badge if cards are hidden).
 */
@Composable
fun TopAppBarStatsAction(
    isEditMode: Boolean,
    hasHiddenCards: Boolean,
    onToggleEditMode: () -> Unit,
    onOpenCustomization: () -> Unit
) {
    AnimatedContent(
        targetState = isEditMode,
        transitionSpec = {
            (fadeIn(animationSpec = M3ExpressiveMotion.fastSpatial()) +
             scaleIn(initialScale = 0.8f, animationSpec = M3ExpressiveMotion.fastSpatial()))
                .togetherWith(
                    fadeOut(animationSpec = M3ExpressiveMotion.fastEffects()) +
                    scaleOut(targetScale = 0.8f, animationSpec = M3ExpressiveMotion.fastEffects())
                )
        },
        label = "top_bar_stats_action"
    ) { inEditMode ->
        if (inEditMode) {
            FilledTonalButton(
                onClick = onToggleEditMode,
                modifier = Modifier.height(34.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                shape = M3Shapes.CornerFull,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
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
                        imageVector = Icons.Default.DashboardCustomize,
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
    onMoveCard: ((cardId: String, direction: Int) -> Unit)? = null,
    onResetDefault: () -> Unit,
    onDismissRequest: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = M3Shapes.CornerExtraLarge,
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
                        .clip(M3Shapes.CornerMedium)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
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
                    shape = M3Shapes.CornerMedium,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.VisibilityOff,
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

            // Interactive Switches List
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = M3Shapes.CornerLarge,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    cards.forEachIndexed { index, card ->
                        val isVisible = card.id !in hiddenCardIds
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggleCard(card.id, !isVisible) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                if (card.icon != null) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(M3Shapes.CornerSmall)
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
                                Column {
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
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (onMoveCard != null) {
                                    IconButton(
                                        onClick = { onMoveCard(card.id, -1) },
                                        enabled = index > 0,
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowUp,
                                            contentDescription = stringResource(R.string.action_move_up),
                                            tint = if (index > 0) MaterialTheme.colorScheme.onSurfaceVariant
                                                   else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { onMoveCard(card.id, 1) },
                                        enabled = index < cards.lastIndex,
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowDown,
                                            contentDescription = stringResource(R.string.action_move_down),
                                            tint = if (index < cards.lastIndex) MaterialTheme.colorScheme.onSurfaceVariant
                                                   else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                Switch(
                                    checked = isVisible,
                                    onCheckedChange = { onToggleCard(card.id, it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                                        checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                                        uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                        uncheckedBorderColor = MaterialTheme.colorScheme.outline
                                    )
                                )
                            }
                        }

                        if (index < cards.lastIndex) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }
                }
            }

            // Bottom Reset & Close Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onResetDefault,
                    shape = M3Shapes.CornerFull
                ) {
                    Text(
                        text = stringResource(R.string.stats_reset_default),
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                Button(
                    onClick = onDismissRequest,
                    shape = M3Shapes.CornerFull
                ) {
                    Text(
                        text = stringResource(R.string.action_close),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}
