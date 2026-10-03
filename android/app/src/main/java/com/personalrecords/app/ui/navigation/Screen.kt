package com.personalrecords.app.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector
import com.personalrecords.app.R

sealed class Screen(
    val route: String,
    @StringRes val titleRes: Int,
    val icon: ImageVector,
    val selectedIcon: ImageVector = icon,
    @StringRes val navLabelRes: Int = titleRes
) {
    object Overview : Screen("overview", R.string.title_overview, Icons.Outlined.Dashboard, Icons.Filled.Dashboard, R.string.nav_overview)
    object Timeline : Screen("timeline", R.string.title_timeline, Icons.Outlined.Timeline, Icons.Filled.Timeline, R.string.nav_timeline)
    object Records : Screen("records", R.string.title_records, Icons.Outlined.Folder, Icons.Filled.Folder, R.string.nav_records)
    object Settings : Screen("settings", R.string.title_settings, Icons.Outlined.Settings, Icons.Filled.Settings, R.string.nav_settings)

    // Domain Detail Screens
    object Nutrition : Screen("nutrition", R.string.title_nutrition, Icons.Default.Restaurant, navLabelRes = R.string.nav_nutrition)
    object Books : Screen("books", R.string.title_books, Icons.AutoMirrored.Filled.MenuBook, navLabelRes = R.string.nav_books)
    object Movies : Screen("movies", R.string.title_movies, Icons.Default.Movie, navLabelRes = R.string.nav_movies)
    object Notifications : Screen("notifications", R.string.title_notifications, Icons.Default.Notifications, navLabelRes = R.string.nav_notifications)
}

val bottomNavScreens = listOf(
    Screen.Overview,
    Screen.Timeline,
    Screen.Records,
    Screen.Settings
)

val recordsSubRoutes = setOf(
    Screen.Records.route,
    Screen.Nutrition.route,
    Screen.Books.route,
    Screen.Movies.route,
    Screen.Notifications.route
)
