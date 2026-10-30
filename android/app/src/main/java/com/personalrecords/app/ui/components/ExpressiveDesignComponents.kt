package com.personalrecords.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.VerticalDragHandle
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import com.personalrecords.app.R
import com.personalrecords.app.core.theme.currentAdaptiveLayout
import com.personalrecords.app.core.theme.M3NavigationMetrics
import com.personalrecords.app.core.theme.M3Shapes
import com.personalrecords.app.core.theme.recordCategoryColors
import com.personalrecords.app.core.theme.RecordCategory
import com.personalrecords.app.core.theme.StatusTone
import com.personalrecords.app.core.theme.statusColors

@Composable
fun floatingBottomNavigationContentPadding(): androidx.compose.ui.unit.Dp {
    val navigationInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val isCompactWidth = !currentAdaptiveLayout().isWide
    if (!isCompactWidth) {
        return M3NavigationMetrics.WideBottomContentSpacing + navigationInset
    }

    val floatingBarTopClearance = navigationInset +
        M3NavigationMetrics.FloatingBarBottomOffset +
        M3NavigationMetrics.FloatingBarHeight
    return floatingBarTopClearance + M3NavigationMetrics.BottomContentClearance
}

/**
 * Bottom padding for snackbars on top-level tabs: just above the floating navigation bar
 * on phones, and just above the system bar when the navigation is a side rail.
 */
@Composable
fun floatingNavSnackbarPadding(): androidx.compose.ui.unit.Dp {
    val navigationInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    if (currentAdaptiveLayout().isWide) return navigationInset + 16.dp
    return navigationInset +
        M3NavigationMetrics.FloatingBarBottomOffset +
        M3NavigationMetrics.FloatingBarHeight +
        12.dp
}

/**
 * Small page top app bar, used on every screen. It keeps the `surface` color while content
 * scrolls, so the bar and the sticky filter header under it never change tone.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressivePageTopAppBar(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior? = null
) {
    val colors = TopAppBarDefaults.topAppBarColors(
        containerColor = MaterialTheme.colorScheme.surface,
        scrolledContainerColor = MaterialTheme.colorScheme.surface
    )
    val titleContent: @Composable () -> Unit = {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLargeEmphasized,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
    val subtitleText = subtitle?.takeIf { it.isNotBlank() }
    if (subtitleText != null) {
        TopAppBar(
            title = titleContent,
            subtitle = { Text(text = subtitleText, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            modifier = modifier,
            navigationIcon = navigationIcon,
            actions = actions,
            colors = colors,
            scrollBehavior = scrollBehavior
        )
    } else {
        TopAppBar(
            title = titleContent,
            modifier = modifier,
            navigationIcon = navigationIcon,
            actions = actions,
            colors = colors,
            scrollBehavior = scrollBehavior
        )
    }
}

@Composable
fun ExpressiveSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    clearContentDescription: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        placeholder = {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
        },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(
                        imageVector = Icons.Rounded.Clear,
                        contentDescription = clearContentDescription,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        },
        shape = M3Shapes.CornerFull,
        // Filled M3 search look at rest (surfaceContainerHigh, no outline). Focus must stay
        // visible, as on every M3 text field: a primary outline and a primary search icon.
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = Color.Transparent,
            focusedLeadingIconColor = MaterialTheme.colorScheme.primary,
            unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        singleLine = true
    )
}

@Composable
fun ExpressiveStatusBadge(
    text: String,
    tone: StatusTone,
    modifier: Modifier = Modifier
) {
    val colors = statusColors(tone)
    Surface(
        modifier = modifier,
        shape = M3Shapes.CornerFull,
        color = colors.container,
        contentColor = colors.onContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(colors.indicator, CircleShape)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmallEmphasized,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

/**
 * Round avatar with the first letter of an app name, used for notification rows so apps can be
 * told apart at a glance. Each app always gets the same tonal color pair.
 */
@Composable
fun AppLetterAvatar(
    appName: String,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 40.dp
) {
    val scheme = MaterialTheme.colorScheme
    val palette = listOf(
        scheme.primaryContainer to scheme.onPrimaryContainer,
        scheme.secondaryContainer to scheme.onSecondaryContainer,
        scheme.tertiaryContainer to scheme.onTertiaryContainer,
        recordCategoryColors(RecordCategory.Nutrition).let { it.container to it.onContainer },
        recordCategoryColors(RecordCategory.Books).let { it.container to it.onContainer },
        recordCategoryColors(RecordCategory.Movies).let { it.container to it.onContainer },
        recordCategoryColors(RecordCategory.Music).let { it.container to it.onContainer },
        recordCategoryColors(RecordCategory.Health).let { it.container to it.onContainer }
    )
    val key = appName.trim().lowercase()
    val (container, content) = palette[Math.floorMod(key.hashCode(), palette.size)]
    val letter = appName.trim().firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "?"
    Box(
        modifier = modifier
            .size(size)
            .background(container, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = letter,
            style = MaterialTheme.typography.titleMediumEmphasized,
            color = content,
            maxLines = 1
        )
    }
}

/** App name of a notification activity item: the stored app name, or the "App: title" prefix. */
fun notificationAppName(item: com.personalrecords.app.core.network.model.ActivityItemDto): String {
    val stored = (item.details["app_name"] as? kotlinx.serialization.json.JsonPrimitive)
        ?.takeIf { it.isString }?.content.orEmpty()
    return stored.ifBlank { item.title.substringBefore(':').trim() }
}

/** Subheader above a segmented list group: titleSmall in primary, aligned with row content. */
@Composable
fun ExpressiveListSubheader(
    title: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmallEmphasized,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .padding(start = 16.dp, top = 4.dp, bottom = 6.dp)
            .semantics { heading() }
    )
}

/**
 * One row of an M3 Expressive segmented list, built on [SegmentedListItem]: grouped corners
 * from [ListItemDefaults.segmentedShapes], a shape change and state layer on press, focus and
 * screen-reader semantics, and the standard 16dp list padding.
 *
 * In a lazy list keep [addLeadingGap] on, so each row after the first adds the segment gap
 * itself. Pass `count = 1` for a standalone row (for example in a grid).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveListRow(
    index: Int,
    count: Int,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    /** Non-null only where the row can show a selection, e.g. the item open in a detail pane. */
    selected: Boolean? = null,
    addLeadingGap: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    /** Rows with a poster or avatar read better centered; text-only rows keep the M3 default. */
    verticalAlignment: Alignment.Vertical = ListItemDefaults.verticalAlignment(),
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    overlineContent: (@Composable () -> Unit)? = null,
    supportingContent: (@Composable () -> Unit)? = null,
    headlineContent: @Composable () -> Unit
) {
    val rowModifier = modifier
        .then(
            if (addLeadingGap && index > 0) Modifier.padding(top = ListItemDefaults.SegmentedGap)
            else Modifier
        )
        .fillMaxWidth()
    val shapes = ListItemDefaults.segmentedShapes(index = index, count = count)
    val colors = ListItemDefaults.segmentedColors(containerColor = containerColor)
    if (onClick != null && selected != null) {
        SegmentedListItem(
            selected = selected,
            onClick = onClick,
            shapes = shapes,
            colors = colors,
            modifier = rowModifier,
            leadingContent = leadingContent,
            trailingContent = trailingContent,
            overlineContent = overlineContent,
            supportingContent = supportingContent,
            verticalAlignment = verticalAlignment,
            content = headlineContent
        )
    } else if (onClick != null) {
        SegmentedListItem(
            onClick = onClick,
            onLongClick = onLongClick,
            shapes = shapes,
            colors = colors,
            modifier = rowModifier,
            leadingContent = leadingContent,
            trailingContent = trailingContent,
            overlineContent = overlineContent,
            supportingContent = supportingContent,
            verticalAlignment = verticalAlignment,
            content = headlineContent
        )
    } else {
        SegmentedListItem(
            shapes = shapes,
            colors = colors,
            modifier = rowModifier,
            leadingContent = leadingContent,
            trailingContent = trailingContent,
            overlineContent = overlineContent,
            supportingContent = supportingContent,
            verticalAlignment = verticalAlignment,
            content = headlineContent
        )
    }
}

/**
 * Shared error banner for every screen. Shakes once when the message changes and is
 * announced by screen readers.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveErrorBanner(
    message: String,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.common_error_title),
    icon: ImageVector = Icons.Rounded.ErrorOutline,
    onRetry: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    Surface(
        shape = M3Shapes.CornerLarge,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = modifier
            .fillMaxWidth()
            .expressiveShake(trigger = message)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmallEmphasized
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (onRetry != null) {
                TextButton(
                    onClick = onRetry,
                    shapes = ButtonDefaults.shapes(),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Text(
                        text = stringResource(R.string.action_retry),
                        style = MaterialTheme.typography.labelLargeEmphasized
                    )
                }
            }
        }
    }
}

/**
 * Shared empty state: tonal icon disc, emphasized title, optional guide text and action.
 * Used for "no data yet", "no results" and empty detail panes so every screen reads the same.
 */
@Composable
fun ExpressiveEmptyState(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    icon: ImageVector = Icons.Rounded.Inbox,
    iconContainerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    iconContentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    action: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(iconContainerColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconContentColor,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMediumEmphasized,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        if (!message.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        if (action != null) {
            Spacer(modifier = Modifier.height(20.dp))
            action()
        }
    }
}

/**
 * List-detail scaffold shared by every list screen. On Expanded widths the detail pane sits
 * beside the list; otherwise only the list is shown and the screen opens a bottom sheet.
 */
@Composable
fun ExpressiveListDetailLayout(
    showDetailPane: Boolean,
    detailPane: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    list: @Composable () -> Unit
) {
    if (!showDetailPane) {
        Box(modifier = modifier.fillMaxSize()) { list() }
        return
    }
    // The user can resize the panes with the M3 drag handle between them.
    var listFraction by rememberSaveable { mutableFloatStateOf(0.575f) }
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val totalWidthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val dragState = rememberDraggableState { delta ->
            listFraction = (listFraction + delta / totalWidthPx).coerceIn(0.35f, 0.7f)
        }
        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(listFraction)
                    .fillMaxHeight()
            ) {
                list()
            }
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(24.dp),
                contentAlignment = Alignment.Center
            ) {
                VerticalDragHandle(
                    modifier = Modifier.draggable(
                        state = dragState,
                        orientation = Orientation.Horizontal
                    )
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f - listFraction)
                    .fillMaxHeight()
                    .padding(top = 10.dp, end = 12.dp, bottom = 32.dp)
            ) {
                detailPane()
            }
        }
    }
}
