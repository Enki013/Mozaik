package com.personalrecords.app.core.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ===================================================
// Material Design 3 & M3 Expressive Shape Scale (10 Steps)
// Source: .agent/skills/m3-expressive/references/tokens.md
// 0 / 4 / 8 / 12 / 16 / 20 / 28 / 32 / 48 / full
// ===================================================

object M3CornerRadius {
    val None: Dp = 0.dp
    val ExtraSmall: Dp = 4.dp
    val Small: Dp = 8.dp
    val Medium: Dp = 12.dp
    val Large: Dp = 16.dp
    /** Added in M3 Expressive (May 2025) */
    val LargeIncreased: Dp = 20.dp
    val ExtraLarge: Dp = 28.dp
    /** Added in M3 Expressive (May 2025) */
    val ExtraLargeIncreased: Dp = 32.dp
    /** Added in M3 Expressive (May 2025) */
    val ExtraExtraLarge: Dp = 48.dp
}

object M3Shapes {
    val CornerNone: Shape = RectangleShape
    val CornerExtraSmall = RoundedCornerShape(M3CornerRadius.ExtraSmall)
    val CornerSmall = RoundedCornerShape(M3CornerRadius.Small)
    val CornerMedium = RoundedCornerShape(M3CornerRadius.Medium)
    val CornerLarge = RoundedCornerShape(M3CornerRadius.Large)
    /** Added in M3 Expressive (20dp) */
    val CornerLargeIncreased = RoundedCornerShape(M3CornerRadius.LargeIncreased)
    val CornerExtraLarge = RoundedCornerShape(M3CornerRadius.ExtraLarge)
    /** Added in M3 Expressive (32dp) */
    val CornerExtraLargeIncreased = RoundedCornerShape(M3CornerRadius.ExtraLargeIncreased)
    /** Added in M3 Expressive (48dp) */
    val CornerExtraExtraLarge = RoundedCornerShape(M3CornerRadius.ExtraExtraLarge)
    val CornerFull: Shape = CircleShape

    /**
     * Optical roundness rule from M3 Expressive (tokens.md):
     * `inner radius = outer radius - padding`
     * Never reuse the parent container's corner radius on a nested child.
     */
    fun nestedRadius(outerRadius: Dp, padding: Dp, minRadius: Dp = M3CornerRadius.ExtraSmall): Dp =
        (outerRadius - padding).coerceAtLeast(minRadius)

    fun nestedShape(outerRadius: Dp, padding: Dp, minRadius: Dp = M3CornerRadius.ExtraSmall): RoundedCornerShape =
        RoundedCornerShape(nestedRadius(outerRadius, padding, minRadius))

    fun groupedListItem(index: Int, count: Int): RoundedCornerShape {
        val outer = M3CornerRadius.Large
        val inner = M3CornerRadius.ExtraSmall
        return when {
            count <= 1 -> RoundedCornerShape(outer)
            index == 0 -> RoundedCornerShape(
                topStart = outer,
                topEnd = outer,
                bottomEnd = inner,
                bottomStart = inner
            )
            index == count - 1 -> RoundedCornerShape(
                topStart = inner,
                topEnd = inner,
                bottomEnd = outer,
                bottomStart = outer
            )
            else -> RoundedCornerShape(inner)
        }
    }
}

val Shapes = Shapes(
    extraSmall = M3Shapes.CornerExtraSmall,
    small = M3Shapes.CornerSmall,
    medium = M3Shapes.CornerMedium,
    large = M3Shapes.CornerLarge,
    extraLarge = M3Shapes.CornerExtraLarge,
    largeIncreased = M3Shapes.CornerLargeIncreased,
    extraLargeIncreased = M3Shapes.CornerExtraLargeIncreased,
    extraExtraLarge = M3Shapes.CornerExtraExtraLarge
)
