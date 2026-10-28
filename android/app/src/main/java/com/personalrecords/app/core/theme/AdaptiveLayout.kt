package com.personalrecords.app.core.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Material 3 window width classes (Compact < 600dp, Medium < 840dp, Expanded and up). */
enum class WindowWidthClass { Compact, Medium, Expanded }

@Immutable
data class AdaptiveLayout(val widthClass: WindowWidthClass) {
    /** Medium and wider: lists switch to a multi-column grid and the nav becomes a rail. */
    val isWide: Boolean get() = widthClass != WindowWidthClass.Compact

    /** Expanded and wider: list-detail screens show the detail pane beside the list. */
    val showsDetailPane: Boolean get() = widthClass == WindowWidthClass.Expanded

    companion object {
        val MediumMinWidth: Dp = 600.dp
        val ExpandedMinWidth: Dp = 840.dp

        fun fromWidth(width: Dp): AdaptiveLayout = AdaptiveLayout(
            when {
                width >= ExpandedMinWidth -> WindowWidthClass.Expanded
                width >= MediumMinWidth -> WindowWidthClass.Medium
                else -> WindowWidthClass.Compact
            }
        )
    }
}

/**
 * Single place every screen reads its width class from. Uses the window size,
 * so split-screen and freeform windows are classified correctly.
 */
@Composable
fun currentAdaptiveLayout(): AdaptiveLayout {
    val widthPx = LocalWindowInfo.current.containerSize.width
    val width = with(LocalDensity.current) { widthPx.toDp() }
    return AdaptiveLayout.fromWidth(width)
}
