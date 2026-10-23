package com.personalrecords.app.core.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val LocalReducedMotion = staticCompositionLocalOf { false }

// ==========================================
// Material Design 3 Color Schemes
// ==========================================

private val DarkColorScheme = darkColorScheme(
    primary = md_theme_dark_primary,
    onPrimary = md_theme_dark_onPrimary,
    primaryContainer = md_theme_dark_primaryContainer,
    onPrimaryContainer = md_theme_dark_onPrimaryContainer,
    secondary = md_theme_dark_secondary,
    onSecondary = md_theme_dark_onSecondary,
    secondaryContainer = md_theme_dark_secondaryContainer,
    onSecondaryContainer = md_theme_dark_onSecondaryContainer,
    tertiary = md_theme_dark_tertiary,
    onTertiary = md_theme_dark_onTertiary,
    tertiaryContainer = md_theme_dark_tertiaryContainer,
    onTertiaryContainer = md_theme_dark_onTertiaryContainer,
    error = md_theme_dark_error,
    onError = md_theme_dark_onError,
    errorContainer = md_theme_dark_errorContainer,
    onErrorContainer = md_theme_dark_onErrorContainer,
    background = md_theme_dark_background,
    onBackground = md_theme_dark_onBackground,
    surface = md_theme_dark_surface,
    onSurface = md_theme_dark_onSurface,
    surfaceVariant = md_theme_dark_surfaceVariant,
    onSurfaceVariant = md_theme_dark_onSurfaceVariant,
    outline = md_theme_dark_outline,
    outlineVariant = md_theme_dark_outlineVariant,
    scrim = md_theme_dark_scrim,
    inverseSurface = md_theme_dark_inverseSurface,
    inverseOnSurface = md_theme_dark_inverseOnSurface,
    inversePrimary = md_theme_dark_inversePrimary,
    surfaceContainerLowest = md_theme_dark_surfaceContainerLowest,
    surfaceContainerLow = md_theme_dark_surfaceContainerLow,
    surfaceContainer = md_theme_dark_surfaceContainer,
    surfaceContainerHigh = md_theme_dark_surfaceContainerHigh,
    surfaceContainerHighest = md_theme_dark_surfaceContainerHighest
)

private val LightColorScheme = lightColorScheme(
    primary = md_theme_light_primary,
    onPrimary = md_theme_light_onPrimary,
    primaryContainer = md_theme_light_primaryContainer,
    onPrimaryContainer = md_theme_light_onPrimaryContainer,
    secondary = md_theme_light_secondary,
    onSecondary = md_theme_light_onSecondary,
    secondaryContainer = md_theme_light_secondaryContainer,
    onSecondaryContainer = md_theme_light_onSecondaryContainer,
    tertiary = md_theme_light_tertiary,
    onTertiary = md_theme_light_onTertiary,
    tertiaryContainer = md_theme_light_tertiaryContainer,
    onTertiaryContainer = md_theme_light_onTertiaryContainer,
    error = md_theme_light_error,
    onError = md_theme_light_onError,
    errorContainer = md_theme_light_errorContainer,
    onErrorContainer = md_theme_light_onErrorContainer,
    background = md_theme_light_background,
    onBackground = md_theme_light_onBackground,
    surface = md_theme_light_surface,
    onSurface = md_theme_light_onSurface,
    surfaceVariant = md_theme_light_surfaceVariant,
    onSurfaceVariant = md_theme_light_onSurfaceVariant,
    outline = md_theme_light_outline,
    outlineVariant = md_theme_light_outlineVariant,
    scrim = md_theme_light_scrim,
    inverseSurface = md_theme_light_inverseSurface,
    inverseOnSurface = md_theme_light_inverseOnSurface,
    inversePrimary = md_theme_light_inversePrimary,
    surfaceContainerLowest = md_theme_light_surfaceContainerLowest,
    surfaceContainerLow = md_theme_light_surfaceContainerLow,
    surfaceContainer = md_theme_light_surfaceContainer,
    surfaceContainerHigh = md_theme_light_surfaceContainerHigh,
    surfaceContainerHighest = md_theme_light_surfaceContainerHighest
)

@Composable
fun PersonalRecordsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    val context = LocalContext.current
    val isReducedMotion = remember(context) {
        try {
            android.provider.Settings.Global.getFloat(
                context.contentResolver,
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) == 0f
        } catch (_: Exception) {
            false
        }
    }

    CompositionLocalProvider(
        LocalReducedMotion provides isReducedMotion
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = Shapes,
            content = content
        )
    }
}

// ==========================================
// Dynamic Domain Accent Color Helpers
// Adapts domain-specific badges and pills to Dark/Light themes per MD3
// ==========================================

@Composable
fun domainNutritionContainer(): androidx.compose.ui.graphics.Color =
    if (isSystemInDarkTheme()) NutritionGreenDarkContainer else NutritionGreenContainer

@Composable
fun domainNutritionOnContainer(): androidx.compose.ui.graphics.Color =
    if (isSystemInDarkTheme()) NutritionGreenDarkOnContainer else NutritionGreenOnContainer

@Composable
fun domainBooksContainer(): androidx.compose.ui.graphics.Color =
    if (isSystemInDarkTheme()) BooksAccentDarkContainer else BooksAccentContainer

@Composable
fun domainBooksOnContainer(): androidx.compose.ui.graphics.Color =
    if (isSystemInDarkTheme()) BooksAccentDarkOnContainer else BooksAccentOnContainer

@Composable
fun domainCinemaContainer(): androidx.compose.ui.graphics.Color =
    if (isSystemInDarkTheme()) CinemaAccentDarkContainer else CinemaAccentContainer

@Composable
fun domainCinemaOnContainer(): androidx.compose.ui.graphics.Color =
    if (isSystemInDarkTheme()) CinemaAccentDarkOnContainer else CinemaAccentOnContainer

@Composable
fun domainRatingContainer(): androidx.compose.ui.graphics.Color =
    if (isSystemInDarkTheme()) RatingGoldDarkContainer else RatingGoldContainer

@Composable
fun domainRatingOnContainer(): androidx.compose.ui.graphics.Color =
    if (isSystemInDarkTheme()) RatingGoldDarkOnContainer else RatingGoldOnContainer

@Composable
fun domainNutritionColor(): androidx.compose.ui.graphics.Color =
    if (isSystemInDarkTheme()) NutritionGreenDark else NutritionGreen

@Composable
fun domainBooksColor(): androidx.compose.ui.graphics.Color =
    if (isSystemInDarkTheme()) BooksAccentDark else BooksAccent

@Composable
fun domainCinemaColor(): androidx.compose.ui.graphics.Color =
    if (isSystemInDarkTheme()) CinemaAccentDark else CinemaAccent

@Composable
fun domainRatingColor(): androidx.compose.ui.graphics.Color =
    if (isSystemInDarkTheme()) RatingGoldDark else RatingGold

@Composable
fun domainHealthContainer(): androidx.compose.ui.graphics.Color =
    if (isSystemInDarkTheme()) HealthCrimsonDarkContainer else HealthCrimsonContainer

@Composable
fun domainHealthOnContainer(): androidx.compose.ui.graphics.Color =
    if (isSystemInDarkTheme()) HealthCrimsonDarkOnContainer else HealthCrimsonOnContainer

@Composable
fun domainHealthColor(): androidx.compose.ui.graphics.Color =
    if (isSystemInDarkTheme()) HealthCrimsonDark else HealthCrimson

@Composable
fun domainMusicContainer(): androidx.compose.ui.graphics.Color =
    if (isSystemInDarkTheme()) MusicAccentDarkContainer else MusicAccentContainer

@Composable
fun domainMusicOnContainer(): androidx.compose.ui.graphics.Color =
    if (isSystemInDarkTheme()) MusicAccentDarkOnContainer else MusicAccentOnContainer

@Composable
fun domainMusicColor(): androidx.compose.ui.graphics.Color =
    if (isSystemInDarkTheme()) MusicAccentDark else MusicAccent

// ==========================================
// M3 Expressive Motion Physics Tokens
// Source: .agent/skills/m3-expressive/references/tokens.md
// Spatial = position, size, rotation, corner radius (bounces)
// Effects = opacity, color (damping 1.0, never overshoots)
// ==========================================

object M3ExpressiveMotion {
    // --- Expressive Scheme (Material's recommended default) ---
    const val EXPRESSIVE_DEFAULT_SPATIAL_DAMPING = 0.8f
    const val EXPRESSIVE_DEFAULT_SPATIAL_STIFFNESS = 380f

    const val EXPRESSIVE_FAST_SPATIAL_DAMPING = 0.6f
    const val EXPRESSIVE_FAST_SPATIAL_STIFFNESS = 800f

    const val EXPRESSIVE_SLOW_SPATIAL_DAMPING = 0.8f
    const val EXPRESSIVE_SLOW_SPATIAL_STIFFNESS = 200f

    // --- Effects Springs (Identical in Expressive & Standard; never overshoot) ---
    const val EFFECTS_DAMPING = 1.0f
    const val DEFAULT_EFFECTS_STIFFNESS = 1600f
    const val FAST_EFFECTS_STIFFNESS = 3800f
    const val SLOW_EFFECTS_STIFFNESS = 800f

    // --- Standard Scheme (Utilitarian fallback) ---
    const val STANDARD_DEFAULT_SPATIAL_DAMPING = 0.9f
    const val STANDARD_DEFAULT_SPATIAL_STIFFNESS = 700f
    const val STANDARD_FAST_SPATIAL_DAMPING = 0.9f
    const val STANDARD_FAST_SPATIAL_STIFFNESS = 1400f
    const val STANDARD_SLOW_SPATIAL_DAMPING = 0.9f
    const val STANDARD_SLOW_SPATIAL_STIFFNESS = 300f

    fun <T> defaultSpatial(visibilityThreshold: T? = null): androidx.compose.animation.core.SpringSpec<T> =
        androidx.compose.animation.core.spring(
            dampingRatio = EXPRESSIVE_DEFAULT_SPATIAL_DAMPING,
            stiffness = EXPRESSIVE_DEFAULT_SPATIAL_STIFFNESS,
            visibilityThreshold = visibilityThreshold
        )

    fun <T> fastSpatial(visibilityThreshold: T? = null): androidx.compose.animation.core.SpringSpec<T> =
        androidx.compose.animation.core.spring(
            dampingRatio = EXPRESSIVE_FAST_SPATIAL_DAMPING,
            stiffness = EXPRESSIVE_FAST_SPATIAL_STIFFNESS,
            visibilityThreshold = visibilityThreshold
        )

    fun <T> slowSpatial(visibilityThreshold: T? = null): androidx.compose.animation.core.SpringSpec<T> =
        androidx.compose.animation.core.spring(
            dampingRatio = EXPRESSIVE_SLOW_SPATIAL_DAMPING,
            stiffness = EXPRESSIVE_SLOW_SPATIAL_STIFFNESS,
            visibilityThreshold = visibilityThreshold
        )

    fun <T> defaultEffects(visibilityThreshold: T? = null): androidx.compose.animation.core.SpringSpec<T> =
        androidx.compose.animation.core.spring(
            dampingRatio = EFFECTS_DAMPING,
            stiffness = DEFAULT_EFFECTS_STIFFNESS,
            visibilityThreshold = visibilityThreshold
        )

    fun <T> fastEffects(visibilityThreshold: T? = null): androidx.compose.animation.core.SpringSpec<T> =
        androidx.compose.animation.core.spring(
            dampingRatio = EFFECTS_DAMPING,
            stiffness = FAST_EFFECTS_STIFFNESS,
            visibilityThreshold = visibilityThreshold
        )

    fun <T> slowEffects(visibilityThreshold: T? = null): androidx.compose.animation.core.SpringSpec<T> =
        androidx.compose.animation.core.spring(
            dampingRatio = EFFECTS_DAMPING,
            stiffness = SLOW_EFFECTS_STIFFNESS,
            visibilityThreshold = visibilityThreshold
        )

    @Composable
    fun isReducedMotion(): Boolean = LocalReducedMotion.current
}


