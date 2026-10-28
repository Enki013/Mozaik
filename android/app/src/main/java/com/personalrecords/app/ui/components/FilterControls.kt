package com.personalrecords.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterListOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.personalrecords.app.R

/** Horizontal gutter every list screen uses; filter rows bleed past it to the screen edge. */
val FilterGutter: Dp = 12.dp

/**
 * Lets a child draw [amount] past its parent's horizontal padding on both sides.
 * Used so sticky filter headers and chip rows run edge to edge while list items keep the gutter.
 */
fun Modifier.bleedHorizontally(amount: Dp): Modifier = layout { measurable, constraints ->
    val extra = amount.roundToPx()
    val looseMax = if (constraints.hasBoundedWidth) constraints.maxWidth + extra * 2 else constraints.maxWidth
    val placeable = measurable.measure(
        constraints.copy(minWidth = (constraints.minWidth + extra * 2).coerceAtMost(looseMax), maxWidth = looseMax)
    )
    val width = (placeable.width - extra * 2).coerceAtLeast(0)
    layout(width, placeable.height) { placeable.place(-extra, 0) }
}

/**
 * Sticky header shell shared by every list screen: opaque surface that spans the full width,
 * content padded back to the list gutter. Put the search field and [ExpressiveFilterRow]s inside.
 */
@Composable
fun ExpressiveFilterHeader(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier
            .bleedHorizontally(FilterGutter)
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }
}

/** Padding for non-scrolling children (search field, result count) inside [ExpressiveFilterHeader]. */
fun Modifier.filterHeaderItem(): Modifier = padding(horizontal = FilterGutter)

/** One horizontally scrolling line of filter controls that scrolls under the screen edges. */
@Composable
fun ExpressiveFilterRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = FilterGutter),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

/**
 * Date filter chip used on every screen. When a date is active it shows the range and a
 * clear button with its own label and a full-height touch area.
 */
@Composable
fun ExpressiveDateFilterChip(
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clearDescription = stringResource(R.string.filter_clear_date)
    ExpressiveFilterChip(
        selected = isActive,
        onClick = onClick,
        modifier = modifier,
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.CalendarMonth,
                contentDescription = null,
                modifier = Modifier.size(FilterChipDefaults.IconSize)
            )
        },
        trailingIcon = if (isActive) {
            {
                Box(
                    modifier = Modifier
                        .height(M3FilterControlHeight)
                        .width(32.dp)
                        .clip(CircleShape)
                        .clickable(role = Role.Button, onClick = onClear)
                        .semantics { contentDescription = clearDescription },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier.size(FilterChipDefaults.IconSize)
                    )
                }
            }
        } else null,
        label = { Text(text = label, style = MaterialTheme.typography.labelLarge) }
    )
}

/**
 * Filter chip that opens a menu of options: used for sorting and for filters with many or
 * dynamic values (apps, artists). The current value is the label; the menu marks it with a check.
 */
@Composable
fun <T> ExpressiveDropdownFilterChip(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    optionLabel: @Composable (T) -> String,
    leadingIcon: ImageVector,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    label: String = optionLabel(selected)
) {
    var expanded by remember { mutableStateOf(false) }
    // Resolve labels here, not inside the menu: the menu is a separate window and would
    // otherwise fall back to the system language instead of the in-app language.
    val optionLabels = options.map { optionLabel(it) }
    Box(modifier = modifier) {
        // A menu trigger, not a toggle: an assist chip (button role) tinted when a value is active.
        ExpressiveAssistChip(
            onClick = { expanded = true },
            colors = if (isActive) {
                borderlessAssistChipColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    labelColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            } else {
                borderlessAssistChipColors()
            },
            leadingIcon = {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    modifier = Modifier.size(FilterChipDefaults.IconSize)
                )
            },
            trailingIcon = {
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(FilterChipDefaults.IconSize)
                )
            },
            label = { Text(text = label, style = MaterialTheme.typography.labelLarge, maxLines = 1) }
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = MaterialTheme.shapes.large
        ) {
            options.forEachIndexed { index, option ->
                val isSelected = option == selected
                DropdownMenuItem(
                    text = {
                        Text(
                            text = optionLabels[index],
                            style = MaterialTheme.typography.bodyLarge
                        )
                    },
                    trailingIcon = if (isSelected) {
                        { Icon(imageVector = Icons.Default.Check, contentDescription = null) }
                    } else null,
                    onClick = {
                        expanded = false
                        if (!isSelected) onSelect(option)
                    }
                )
            }
        }
    }
}

/** "Clear" chip shown first in the chip row whenever any filter is active. */
@Composable
fun ExpressiveClearFiltersChip(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ExpressiveAssistChip(
        onClick = onClick,
        modifier = modifier,
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.FilterListOff,
                contentDescription = null,
                modifier = Modifier.size(FilterChipDefaults.IconSize)
            )
        },
        label = { Text(text = stringResource(R.string.action_clear), style = MaterialTheme.typography.labelLarge) }
    )
}

/** "23 results" line under the filter rows, shown while any filter or search is active. */
@Composable
fun ExpressiveFilterResultCount(
    count: Int,
    modifier: Modifier = Modifier
) {
    Text(
        text = pluralStringResource(R.plurals.filter_result_count, count, count),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.filterHeaderItem()
    )
}

private val M3FilterControlHeight = com.personalrecords.app.core.theme.M3ComponentSize.FilterControlHeight
