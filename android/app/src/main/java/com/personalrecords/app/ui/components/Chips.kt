package com.personalrecords.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.graphics.vector.ImageVector
import com.personalrecords.app.core.theme.M3ComponentSize
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import com.personalrecords.app.core.theme.*

// Chip colors, filter and assist chips, and the connected button group.

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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun expressiveButtonShapes() = ButtonDefaults.shapes()

/** App-level color treatment around the official M3 expressive filter chip. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
    // M3 filter chips mark selection with a checkmark when they have no icon of their own.
    val resolvedLeadingIcon = leadingIcon ?: if (selected) {
        {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                modifier = Modifier.size(FilterChipDefaults.IconSize)
            )
        }
    } else null
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = label,
        modifier = modifier.heightIn(min = M3ComponentSize.FilterControlHeight),
        shapes = FilterChipDefaults.shapes(),
        border = null,
        colors = colors,
        leadingIcon = resolvedLeadingIcon,
        trailingIcon = trailingIcon
    )
}

/** Keeps the app's tonal AssistChip colors while using the official M3 component behavior. */
@Composable
fun ExpressiveAssistChip(
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    colors: ChipColors = borderlessAssistChipColors()
) {
    AssistChip(
        onClick = onClick,
        label = label,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        border = null,
        colors = colors,
        modifier = modifier.heightIn(min = M3ComponentSize.FilterControlHeight)
    )
}

/** Single-select group composed from the official expressive M3 ToggleButton APIs. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> ExpressiveConnectedButtonGroup(
    items: List<T>,
    selectedItem: T,
    onItemSelected: (T) -> Unit,
    labelProvider: @Composable (T) -> String,
    modifier: Modifier = Modifier,
    fillWidth: Boolean = false,
    iconProvider: (@Composable (T, Boolean) -> ImageVector?)? = null
) {
    val buttonSize = ToggleButtonSize.Small
    val buttonColors = ToggleButtonDefaults.colors(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        checkedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
        checkedContentColor = MaterialTheme.colorScheme.onSecondaryContainer
    )

    // Single choice: exposed to screen readers as a radio group, not as independent toggles.
    Row(
        modifier = modifier.selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEachIndexed { index, item ->
            val selected = item == selectedItem
            val itemModifier = if (fillWidth) {
                Modifier.weight(1f)
            } else {
                Modifier
            }
            val shapes = when {
                items.size == 1 -> ToggleButtonDefaults.shapesFor(buttonSize)
                index == 0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                index == items.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
            }
            val icon = iconProvider?.invoke(item, selected)
            val label = labelProvider(item)

            // Expose each option to screen readers as one radio item (label + selected state)
            // instead of the toggle button's own checkbox-style semantics.
            Box(
                modifier = itemModifier.clearAndSetSemantics {
                    role = Role.RadioButton
                    this.selected = selected
                    contentDescription = label
                    onClick(label = label) {
                        if (!selected) onItemSelected(item)
                        true
                    }
                }
            ) {
                ToggleButton(
                    checked = selected,
                    onCheckedChange = { checked -> if (checked) onItemSelected(item) },
                    buttonSize = buttonSize,
                    shapes = shapes,
                    colors = buttonColors,
                    modifier = if (fillWidth) Modifier.fillMaxWidth() else Modifier,
                    icon = icon?.let { imageVector ->
                        { Icon(imageVector = imageVector, contentDescription = null) }
                    }
                ) {
                    Text(
                        text = label,
                        // Same weight in both states so the label width does not jump on selection.
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}
