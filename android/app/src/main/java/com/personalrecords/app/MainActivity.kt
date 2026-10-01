package com.personalrecords.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.personalrecords.app.core.theme.PersonalRecordsTheme
import com.personalrecords.app.ui.AppViewModelFactory
import com.personalrecords.app.ui.navigation.Screen
import com.personalrecords.app.ui.navigation.bottomNavScreens
import com.personalrecords.app.ui.navigation.recordsSubRoutes
import com.personalrecords.app.ui.screens.books.*
import com.personalrecords.app.ui.screens.movies.*
import com.personalrecords.app.ui.screens.notifications.*
import com.personalrecords.app.ui.screens.nutrition.*
import com.personalrecords.app.ui.screens.overview.*
import com.personalrecords.app.ui.screens.records.*
import com.personalrecords.app.ui.screens.settings.*
import com.personalrecords.app.ui.screens.timeline.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PersonalRecordsTheme {
                MainAppHost()
            }
        }
    }
}

@Composable
fun MainAppHost() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 3.dp
            ) {
                bottomNavScreens.forEach { screen ->
                    val selected = when (screen) {
                        Screen.Records -> currentDestination?.route in recordsSubRoutes
                        else -> currentDestination?.route == screen.route
                    }
                    NavigationBarItem(
                        icon = {
                            Icon(
                                if (selected) screen.selectedIcon else screen.icon,
                                contentDescription = screen.title,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        label = {
                            Text(
                                text = screen.navLabel,
                                style = MaterialTheme.typography.labelSmall,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        selected = selected,
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        onClick = {
                            if (selected) {
                                if (screen == Screen.Records && currentDestination?.route != Screen.Records.route) {
                                    val popped = navController.popBackStack(Screen.Records.route, inclusive = false)
                                    if (!popped) {
                                        navController.navigate(Screen.Records.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                }
                            } else {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->

        NavHost(
            navController = navController,
            startDestination = Screen.Overview.route,
            modifier = Modifier.padding(innerPadding),
            enterTransition = {
                androidx.compose.animation.fadeIn(
                    animationSpec = androidx.compose.animation.core.tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                )
            },
            exitTransition = {
                androidx.compose.animation.fadeOut(
                    animationSpec = androidx.compose.animation.core.tween(250, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                )
            },
            popEnterTransition = {
                androidx.compose.animation.fadeIn(
                    animationSpec = androidx.compose.animation.core.tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                )
            },
            popExitTransition = {
                androidx.compose.animation.fadeOut(
                    animationSpec = androidx.compose.animation.core.tween(250, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                )
            }
        ) {
            composable(Screen.Overview.route) {
                val vm: OverviewViewModel = viewModel(factory = AppViewModelFactory())
                OverviewScreen(
                    viewModel = vm,
                    onNavigateToTimeline = {
                        navController.navigate(Screen.Timeline.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToNutrition = { navController.navigate(Screen.Nutrition.route) },
                    onNavigateToBooks = { navController.navigate(Screen.Books.route) },
                    onNavigateToMovies = { navController.navigate(Screen.Movies.route) }
                )
            }

            composable(Screen.Timeline.route) {
                val vm: TimelineViewModel = viewModel(factory = AppViewModelFactory())
                TimelineScreen(
                    viewModel = vm
                )
            }

            composable(Screen.Records.route) {
                val vm: RecordsViewModel = viewModel(factory = AppViewModelFactory())
                RecordsScreen(
                    viewModel = vm,
                    onNavigateToNutrition = { navController.navigate(Screen.Nutrition.route) },
                    onNavigateToBooks = { navController.navigate(Screen.Books.route) },
                    onNavigateToMovies = { navController.navigate(Screen.Movies.route) },
                    onNavigateToNotifications = { navController.navigate(Screen.Notifications.route) }
                )
            }

            composable(Screen.Nutrition.route) {
                val vm: NutritionViewModel = viewModel(factory = AppViewModelFactory())
                NutritionScreen(
                    viewModel = vm,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.Books.route) {
                val vm: BooksViewModel = viewModel(factory = AppViewModelFactory())
                BooksScreen(
                    viewModel = vm,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.Movies.route) {
                val vm: MoviesViewModel = viewModel(factory = AppViewModelFactory())
                MoviesScreen(
                    viewModel = vm,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.Notifications.route) {
                val vm: NotificationsViewModel = viewModel(factory = AppViewModelFactory())
                NotificationsScreen(
                    viewModel = vm,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.Settings.route) {
                val vm: SettingsViewModel = viewModel(factory = AppViewModelFactory())
                SettingsScreen(
                    viewModel = vm
                )
            }
        }
    }
}
