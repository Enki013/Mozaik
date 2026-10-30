package com.personalrecords.app.core.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

enum class RecordCategory {
    Nutrition,
    Books,
    Movies,
    Music,
    Health,
    Notifications
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
    val BottomFadeHeight = 116.dp
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
@Composable
fun recordCategoryColors(category: RecordCategory): CategoryColorTokens {
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
            indicator = if (isSystemInDarkTheme()) StatusSuccessDark else StatusSuccess,
            container = if (isSystemInDarkTheme()) StatusSuccessDarkContainer else StatusSuccessContainer,
            onContainer = if (isSystemInDarkTheme()) StatusSuccessDarkOnContainer else StatusSuccessOnContainer
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
