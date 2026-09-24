package com.personalrecords.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(
    val route: String,
    val title: String,
    val icon: ImageVector,
    val navLabel: String = title
) {
    object Overview : Screen("overview", "Genel Bakış", Icons.Default.Dashboard, "Genel")
    object Timeline : Screen("timeline", "Zaman Çizelgesi", Icons.Default.Timeline, "Zaman")
    object Nutrition : Screen("nutrition", "Beslenme", Icons.Default.Restaurant, "Beslenme")
    object Books : Screen("books", "Kitaplar", Icons.AutoMirrored.Filled.MenuBook, "Kitaplar")
    object Movies : Screen("movies", "Filmler", Icons.Default.Movie, "Filmler")
    object Notifications : Screen("notifications", "Bildirimler", Icons.Default.Notifications, "Bildirimler")
    object Settings : Screen("settings", "Ayarlar", Icons.Default.Settings, "Ayarlar")
}

val bottomNavScreens = listOf(
    Screen.Overview,
    Screen.Timeline,
    Screen.Nutrition,
    Screen.Books,
    Screen.Movies
)
