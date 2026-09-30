package com.personalrecords.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(
    val route: String,
    val title: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector = icon,
    val navLabel: String = title
) {
    object Overview : Screen("overview", "Genel Bakış", Icons.Outlined.Dashboard, Icons.Filled.Dashboard, "Özet")
    object Timeline : Screen("timeline", "Zaman Çizelgesi", Icons.Outlined.Timeline, Icons.Filled.Timeline, "Zaman")
    object Records : Screen("records", "Kayıtlar", Icons.Outlined.Folder, Icons.Filled.Folder, "Kayıtlar")
    object Settings : Screen("settings", "Ayarlar", Icons.Outlined.Settings, Icons.Filled.Settings, "Ayarlar")

    // Domain Detail Screens
    object Nutrition : Screen("nutrition", "Beslenme", Icons.Default.Restaurant, navLabel = "Beslenme")
    object Books : Screen("books", "Kitaplar", Icons.AutoMirrored.Filled.MenuBook, navLabel = "Kitaplar")
    object Movies : Screen("movies", "Filmler", Icons.Default.Movie, navLabel = "Filmler")
    object Notifications : Screen("notifications", "Bildirimler", Icons.Default.Notifications, navLabel = "Bildirimler")
}

val bottomNavScreens = listOf(
    Screen.Overview,
    Screen.Timeline,
    Screen.Records,
    Screen.Settings
)
