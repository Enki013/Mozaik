package com.personalrecords.app.ui.components

import android.annotation.SuppressLint
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import com.personalrecords.app.core.theme.*

/**
 * Metric card for the stats row of list screens. Leave [badgeText] blank unless the badge adds
 * information the title does not already give (a target, a percentage).
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
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current
    val cardModifier = if (onClick != null || onLongClick != null) {
        modifier.fillMaxWidth().expressiveBounceCombinedClick(
            onClick = onClick,
            onLongClick = onLongClick?.let { action ->
                {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    action()
                }
            }
        )
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
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMediumEmphasized,
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
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
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
                        modifier = Modifier.size(20.dp)
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
                        modifier = Modifier.size(14.dp)
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
    fallbackIcon: ImageVector = Icons.Rounded.BrokenImage,
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
            color = ratingColors().container,
            modifier = modifier
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Star,
                    contentDescription = null,
                    tint = ratingColors().onContainer,
                    modifier = Modifier.size(10.dp)
                )
                Text(
                    text = if (rating % 1.0 == 0.0) rating.toInt().toString() else String.format("%.1f", rating),
                    style = MaterialTheme.typography.labelSmallEmphasized,
                    color = ratingColors().onContainer,
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
