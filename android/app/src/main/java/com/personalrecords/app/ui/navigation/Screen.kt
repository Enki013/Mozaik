package com.personalrecords.app.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector
import com.personalrecords.app.R
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
sealed class Screen(
    @Transient val route: String = "",
    @Transient @StringRes val titleRes: Int = 0,
    @Transient val icon: ImageVector = Icons.Default.Circle,
    @Transient val selectedIcon: ImageVector = icon,
    @Transient @StringRes val navLabelRes: Int = titleRes
) : NavKey {
    @Serializable
    data object Overview : Screen("overview", R.string.title_overview, Icons.Outlined.Dashboard, Icons.Filled.Dashboard, R.string.nav_overview)

    @Serializable
    data object Timeline : Screen("timeline", R.string.title_timeline, Icons.Outlined.Timeline, Icons.Filled.Timeline, R.string.nav_timeline)

    @Serializable
    data object Records : Screen("records", R.string.title_records, Icons.Outlined.Folder, Icons.Filled.Folder, R.string.nav_records)

    @Serializable
    data object Settings : Screen("settings", R.string.title_settings, Icons.Outlined.Settings, Icons.Filled.Settings, R.string.nav_settings)

    // Domain Detail Screens
    @Serializable
    data object Nutrition : Screen("nutrition", R.string.title_nutrition, Icons.Default.Restaurant, navLabelRes = R.string.nav_nutrition)

    @Serializable
    data object Books : Screen("books", R.string.title_books, Icons.AutoMirrored.Filled.MenuBook, navLabelRes = R.string.nav_books)

    @Serializable
    data object Movies : Screen("movies", R.string.title_movies, Icons.Default.Movie, navLabelRes = R.string.nav_movies)

    @Serializable
    data object Notifications : Screen("notifications", R.string.title_notifications, Icons.Default.Notifications, navLabelRes = R.string.nav_notifications)

    @Serializable
    data object HealthConnect : Screen("health_connect", R.string.title_health_connect, Icons.Default.Favorite, navLabelRes = R.string.nav_health_connect)

    @Serializable
    data object Music : Screen("music", R.string.title_music, Icons.Default.MusicNote, navLabelRes = R.string.nav_music)

    @Serializable
    data object Language : Screen("language", R.string.settings_language_screen_title, Icons.Default.Language, navLabelRes = R.string.settings_language_screen_title)
}

val bottomNavScreens = listOf(
    Screen.Overview,
    Screen.Timeline,
    Screen.Records,
    Screen.Settings
)

val topLevelScreens: Set<Screen> = setOf(
    Screen.Overview,
    Screen.Timeline,
    Screen.Records,
    Screen.Settings
)

val detailScreens = setOf(
    Screen.Nutrition,
    Screen.Books,
    Screen.Movies,
    Screen.Notifications,
    Screen.HealthConnect,
    Screen.Music,
    Screen.Language
)

val recordsSubScreens = setOf(
    Screen.Records,
    Screen.Nutrition,
    Screen.Books,
    Screen.Movies,
    Screen.Notifications,
    Screen.HealthConnect,
    Screen.Music
)

val recordsSubRoutes = setOf(
    Screen.Records.route,
    Screen.Nutrition.route,
    Screen.Books.route,
    Screen.Movies.route,
    Screen.Notifications.route,
    Screen.HealthConnect.route,
    Screen.Music.route
)

