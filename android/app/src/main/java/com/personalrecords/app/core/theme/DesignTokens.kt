package com.personalrecords.app.core.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp

enum class RecordCategory {
    Nutrition,
    Books,
    Movies,
    Music,
    Health,
    Notifications
}

/**
 * 4dp-based spacing scale. [Large] is the M3 compact-window margin (phones), [ExtraLarge] the
 * medium-window margin and the gap between page sections.
 */
object M3Spacing {
    val ExtraSmall = 4.dp
    val Small = 8.dp
    val Medium = 12.dp
    val Large = 16.dp
    val ExtraLarge = 24.dp
}

object M3ComponentSize {
    val FilterControlHeight = 40.dp
}

/** Segmented (grouped) list metrics shared by every list screen. */
object M3ListMetrics {
    /** Gap between segments of one group; corners come from [M3Shapes.groupedListItem]. */
    val SegmentGap = 2.dp
}

object M3NavigationMetrics {
    val FloatingBarHeight = 64.dp
    val FloatingBarBottomOffset = 12.dp
    val BottomContentClearance = 16.dp
    val WideBottomContentSpacing = 24.dp
}

@Immutable
data class CategoryColorTokens(
    val accent: Color,
    val container: Color,
    val onContainer: Color
)

/**
 * Single source for category colors. Screens should call this instead of the
 * `domainXxx()` helpers in Theme.kt so every page maps a category the same way.
 */
/**
 * M3 color harmonization: turns this color's hue toward [target]'s hue by half the gap, at most
 * 15 degrees, so fixed brand colors sit well next to the current (often wallpaper-based) scheme.
 * Approximated in HSV; greys are left alone.
 */
fun Color.harmonizeWith(target: Color): Color {
    val source = FloatArray(3)
    val goal = FloatArray(3)
    android.graphics.Color.colorToHSV(toArgb(), source)
    android.graphics.Color.colorToHSV(target.toArgb(), goal)
    if (source[1] < 0.05f || goal[1] < 0.05f) return this
    val gap = ((goal[0] - source[0] + 540f) % 360f) - 180f
    val rotation = minOf(kotlin.math.abs(gap) * 0.5f, 15f) * kotlin.math.sign(gap)
    source[0] = (source[0] + rotation + 360f) % 360f
    return Color(android.graphics.Color.HSVToColor((alpha * 255).toInt(), source))
}

private fun CategoryColorTokens.harmonizedWith(target: Color) = CategoryColorTokens(
    accent = accent.harmonizeWith(target),
    container = container.harmonizeWith(target),
    onContainer = onContainer.harmonizeWith(target)
)

@Composable
fun recordCategoryColors(category: RecordCategory): CategoryColorTokens {
    val scheme = MaterialTheme.colorScheme
    val fixed = fixedCategoryColors(category)
    // Notifications already use scheme roles; brand categories are harmonized with primary.
    return if (category == RecordCategory.Notifications) fixed else fixed.harmonizedWith(scheme.primary)
}

@Composable
private fun fixedCategoryColors(category: RecordCategory): CategoryColorTokens {
    val scheme = MaterialTheme.colorScheme
    return when (category) {
        RecordCategory.Nutrition -> CategoryColorTokens(
            accent = domainNutritionColor(),
            container = domainNutritionContainer(),
            onContainer = domainNutritionOnContainer()
        )
        RecordCategory.Books -> CategoryColorTokens(
            accent = domainBooksColor(),
            container = domainBooksContainer(),
            onContainer = domainBooksOnContainer()
        )
        RecordCategory.Movies -> CategoryColorTokens(
            accent = domainCinemaColor(),
            container = domainCinemaContainer(),
            onContainer = domainCinemaOnContainer()
        )
        RecordCategory.Music -> CategoryColorTokens(
            accent = domainMusicColor(),
            container = domainMusicContainer(),
            onContainer = domainMusicOnContainer()
        )
        RecordCategory.Health -> CategoryColorTokens(
            accent = domainHealthColor(),
            container = domainHealthContainer(),
            onContainer = domainHealthOnContainer()
        )
        // Secondary tonal role: matches how notifications have always been drawn in lists.
        RecordCategory.Notifications -> CategoryColorTokens(
            accent = scheme.secondary,
            container = scheme.secondaryContainer,
            onContainer = scheme.onSecondaryContainer
        )
    }
}

enum class StatusTone {
    Active,
    Paused,
    Warning,
    Error,
    Info
}

@Immutable
data class StatusColorTokens(
    val indicator: Color,
    val container: Color,
    val onContainer: Color
)

@Composable
fun statusColors(tone: StatusTone): StatusColorTokens {
    val scheme = MaterialTheme.colorScheme
    return when (tone) {
        StatusTone.Active -> StatusColorTokens(
            indicator = if (LocalDarkTheme.current) StatusSuccessDark else StatusSuccess,
            container = if (LocalDarkTheme.current) StatusSuccessDarkContainer else StatusSuccessContainer,
            onContainer = if (LocalDarkTheme.current) StatusSuccessDarkOnContainer else StatusSuccessOnContainer
        )
        StatusTone.Paused -> StatusColorTokens(
            indicator = scheme.outline,
            container = scheme.surfaceContainerHigh,
            onContainer = scheme.onSurfaceVariant
        )
        StatusTone.Warning -> StatusColorTokens(
            indicator = scheme.secondary,
            container = scheme.secondaryContainer,
            onContainer = scheme.onSecondaryContainer
        )
        StatusTone.Error -> StatusColorTokens(
            indicator = scheme.error,
            container = scheme.errorContainer,
            onContainer = scheme.onErrorContainer
        )
        StatusTone.Info -> StatusColorTokens(
            indicator = scheme.primary,
            container = scheme.primaryContainer,
            onContainer = scheme.onPrimaryContainer
        )
    }
}
