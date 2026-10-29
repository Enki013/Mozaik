package com.personalrecords.app.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.personalrecords.app.R
import com.personalrecords.app.core.theme.M3ListMetrics
import com.personalrecords.app.core.theme.currentAdaptiveLayout
import com.personalrecords.app.core.theme.M3NavigationMetrics
import com.personalrecords.app.core.theme.M3Shapes
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
    return maxOf(M3NavigationMetrics.BottomFadeHeight, floatingBarTopClearance) +
        M3NavigationMetrics.BottomContentClearance
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpressivePageTopAppBar(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior? = null
) {
    val appBarContainerColor = MaterialTheme.colorScheme.surface
    TopAppBar(
        modifier = modifier,
        title = {
            Column(
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        navigationIcon = navigationIcon,
        actions = actions,
        scrollBehavior = scrollBehavior,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = appBarContainerColor,
            scrolledContainerColor = appBarContainerColor
        )
    )
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
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = clearContentDescription,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        },
        shape = M3Shapes.CornerFull,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
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
 * One segment of an M3 Expressive segmented list: 16dp outer corners, 4dp inner corners.
 * Segments are separated by [M3ListMetrics.SegmentGap] instead of dividers.
 *
 * In a lazy list, set [addLeadingGap] so each segment after the first adds the gap itself.
 * In a plain Column, leave it off and use `Arrangement.spacedBy(M3ListMetrics.SegmentGap)`.
 */
@Composable
fun GroupedListItemSurface(
    index: Int,
    itemCount: Int,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    addLeadingGap: Boolean = false,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier
            .then(
                if (addLeadingGap && index > 0) Modifier.padding(top = M3ListMetrics.SegmentGap)
                else Modifier
            )
            .fillMaxWidth(),
        shape = M3Shapes.groupedListItem(index, itemCount),
        color = containerColor,
        content = content
    )
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
    icon: ImageVector = Icons.Default.ErrorOutline,
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
    icon: ImageVector = Icons.Default.Inbox,
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
    Row(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(1.15f)
                .fillMaxHeight()
        ) {
            list()
        }
        VerticalDivider(
            modifier = Modifier.fillMaxHeight(),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
        Box(
            modifier = Modifier
                .weight(0.85f)
                .fillMaxHeight()
                .padding(top = 10.dp, end = 12.dp, bottom = 32.dp)
        ) {
            detailPane()
        }
    }
}
