package com.personalrecords.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.personalrecords.app.core.theme.M3ExpressiveMotion
import com.personalrecords.app.core.theme.M3Shapes
import com.personalrecords.app.core.theme.PersonalRecordsTheme
import com.personalrecords.app.core.theme.labelSmallEmphasized
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.personalrecords.app.ui.components.expressivePressScale
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val preferences = remember { PersonalRecordsApp.instance.preferences }
            val language by preferences.appLanguage.collectAsState(initial = "system")

            ProvideLocalizedApp(language = language) {
                PersonalRecordsTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        MainAppHost()
                    }
                }
            }
        }
    }
}

@Composable
fun ProvideLocalizedApp(
    language: String,
    content: @Composable () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val localizedContext = remember(language, context) {
        if (language == "system") {
            context
        } else {
            val locale = java.util.Locale(language)
            java.util.Locale.setDefault(locale)
            val config = android.content.res.Configuration(context.resources.configuration).apply {
                setLocale(locale)
                setLayoutDirection(locale)
            }
            context.createConfigurationContext(config)
        }
    }

    CompositionLocalProvider(
        androidx.compose.ui.platform.LocalContext provides localizedContext,
        androidx.compose.ui.platform.LocalConfiguration provides localizedContext.resources.configuration
    ) {
        content()
    }
}

@Composable
fun MainAppHost() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val overviewScrollFlow = remember { MutableSharedFlow<Unit>(extraBufferCapacity = 1) }
    val overviewRefreshFlow = remember { MutableSharedFlow<Unit>(extraBufferCapacity = 1) }

    val timelineScrollFlow = remember { MutableSharedFlow<Unit>(extraBufferCapacity = 1) }
    val timelineRefreshFlow = remember { MutableSharedFlow<Unit>(extraBufferCapacity = 1) }

    val recordsScrollFlow = remember { MutableSharedFlow<Unit>(extraBufferCapacity = 1) }
    val recordsRefreshFlow = remember { MutableSharedFlow<Unit>(extraBufferCapacity = 1) }

    val settingsScrollFlow = remember { MutableSharedFlow<Unit>(extraBufferCapacity = 1) }
    val settingsRefreshFlow = remember { MutableSharedFlow<Unit>(extraBufferCapacity = 1) }

    val coroutineScope = rememberCoroutineScope()
    var activeTabTapJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    val detailSubRoutes = setOf(
        Screen.Nutrition.route,
        Screen.Books.route,
        Screen.Movies.route,
        Screen.Notifications.route,
        Screen.Language.route
    )

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = Screen.Overview.route,
            modifier = Modifier.fillMaxSize(),
            enterTransition = {
                val target = targetState.destination.route
                if (target in detailSubRoutes) {
                    slideInHorizontally(
                        initialOffsetX = { (it * 0.18f).toInt() },
                        animationSpec = M3ExpressiveMotion.defaultSpatial()
                    ) + fadeIn(animationSpec = M3ExpressiveMotion.defaultEffects())
                } else {
                    fadeIn(animationSpec = M3ExpressiveMotion.defaultEffects()) + scaleIn(
                        initialScale = 0.98f,
                        animationSpec = M3ExpressiveMotion.defaultSpatial()
                    )
                }
            },
            exitTransition = {
                val target = targetState.destination.route
                if (target in detailSubRoutes) {
                    ExitTransition.None
                } else {
                    fadeOut(animationSpec = M3ExpressiveMotion.fastEffects())
                }
            },
            popEnterTransition = {
                val initial = initialState.destination.route
                if (initial in detailSubRoutes) {
                    fadeIn(animationSpec = M3ExpressiveMotion.defaultEffects())
                } else {
                    fadeIn(animationSpec = M3ExpressiveMotion.defaultEffects()) + scaleIn(
                        initialScale = 0.98f,
                        animationSpec = M3ExpressiveMotion.defaultSpatial()
                    )
                }
            },
            popExitTransition = {
                val initial = initialState.destination.route
                if (initial in detailSubRoutes) {
                    scaleOut(
                        targetScale = 0.94f,
                        animationSpec = M3ExpressiveMotion.fastSpatial()
                    ) + fadeOut(animationSpec = M3ExpressiveMotion.fastEffects())
                } else {
                    fadeOut(animationSpec = M3ExpressiveMotion.fastEffects())
                }
            }
        ) {
            composable(Screen.Overview.route) {
                val vm: OverviewViewModel = viewModel(factory = AppViewModelFactory())
                OverviewScreen(
                    viewModel = vm,
                    scrollToTopFlow = overviewScrollFlow,
                    refreshFlow = overviewRefreshFlow,
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
                    viewModel = vm,
                    scrollToTopFlow = timelineScrollFlow,
                    refreshFlow = timelineRefreshFlow,
                    onNavigateToNutrition = { navController.navigate(Screen.Nutrition.route) },
                    onNavigateToBooks = { navController.navigate(Screen.Books.route) },
                    onNavigateToMovies = { navController.navigate(Screen.Movies.route) }
                )
            }

            composable(Screen.Records.route) {
                val vm: RecordsViewModel = viewModel(factory = AppViewModelFactory())
                RecordsScreen(
                    viewModel = vm,
                    scrollToTopFlow = recordsScrollFlow,
                    refreshFlow = recordsRefreshFlow,
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
                    viewModel = vm,
                    scrollToTopFlow = settingsScrollFlow,
                    refreshFlow = settingsRefreshFlow,
                    onNavigateToLanguage = { navController.navigate(Screen.Language.route) }
                )
            }

            composable(Screen.Language.route) {
                val vm: SettingsViewModel = viewModel(factory = AppViewModelFactory())
                val state by vm.uiState.collectAsState()
                LanguageSelectionScreen(
                    currentLanguage = state.appLanguage,
                    onLanguageSelected = { lang -> vm.setLanguage(lang) },
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }

        // Floating Bottom Bar — M3 Expressive Floating Navigation Bar
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = M3Shapes.CornerExtraLarge,
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 2.dp,
                shadowElevation = 0.dp,
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .padding(horizontal = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val haptic = LocalHapticFeedback.current
                    bottomNavScreens.forEach { screen ->
                        val selected = when (screen) {
                            Screen.Records -> currentDestination?.route in recordsSubRoutes
                            Screen.Settings -> currentDestination?.route == Screen.Settings.route || currentDestination?.route == Screen.Language.route
                            else -> currentDestination?.route == screen.route
                        }

                        // Spatial spring (fast: 0.6 damping / 800 stiffness) for icon bounce & pill geometry
                        val iconScale by animateFloatAsState(
                            targetValue = if (selected) 1.12f else 1.0f,
                            animationSpec = M3ExpressiveMotion.fastSpatial(),
                            label = "nav_icon_bounce"
                        )

                        // Effects spring (damping 1.0, no overshoot) for indicator opacity
                        val indicatorAlpha by animateFloatAsState(
                            targetValue = if (selected) 1f else 0f,
                            animationSpec = M3ExpressiveMotion.fastEffects(),
                            label = "nav_indicator_alpha"
                        )
                        val indicatorScaleX by animateFloatAsState(
                            targetValue = if (selected) 1f else 0.6f,
                            animationSpec = M3ExpressiveMotion.fastSpatial(),
                            label = "nav_indicator_scale_x"
                        )
                        val indicatorScaleY by animateFloatAsState(
                            targetValue = if (selected) 1f else 0.75f,
                            animationSpec = M3ExpressiveMotion.fastSpatial(),
                            label = "nav_indicator_scale_y"
                        )

                        // Effects spring (damping 1.0, no overshoot) for color transitions
                        val iconColor by animateColorAsState(
                            targetValue = if (selected) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            },
                            animationSpec = M3ExpressiveMotion.fastEffects(),
                            label = "nav_icon_color"
                        )

                        val textColor by animateColorAsState(
                            targetValue = if (selected) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            },
                            animationSpec = M3ExpressiveMotion.fastEffects(),
                            label = "nav_text_color"
                        )

                        val interactionSource = remember { MutableInteractionSource() }
                        val isNavPressed by interactionSource.collectIsPressedAsState()
                        val indicatorCorner by animateDpAsState(
                            targetValue = if (isNavPressed) 8.dp else 15.dp,
                            animationSpec = M3ExpressiveMotion.fastSpatial(),
                            label = "nav_indicator_corner"
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = null,
                                    role = Role.Tab,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)

                                        fun triggerScrollToTop(route: String) {
                                            when (route) {
                                                Screen.Overview.route -> overviewScrollFlow.tryEmit(Unit)
                                                Screen.Timeline.route -> timelineScrollFlow.tryEmit(Unit)
                                                Screen.Records.route -> recordsScrollFlow.tryEmit(Unit)
                                                Screen.Settings.route -> settingsScrollFlow.tryEmit(Unit)
                                            }
                                        }

                                        fun triggerRefresh(route: String) {
                                            when (route) {
                                                Screen.Overview.route -> overviewRefreshFlow.tryEmit(Unit)
                                                Screen.Timeline.route -> timelineRefreshFlow.tryEmit(Unit)
                                                Screen.Records.route -> recordsRefreshFlow.tryEmit(Unit)
                                                Screen.Settings.route -> settingsRefreshFlow.tryEmit(Unit)
                                            }
                                        }

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
                                            } else {
                                                if (activeTabTapJob?.isActive == true) {
                                                    activeTabTapJob?.cancel()
                                                    activeTabTapJob = null
                                                    triggerRefresh(screen.route)
                                                } else {
                                                    triggerScrollToTop(screen.route)
                                                    activeTabTapJob = coroutineScope.launch {
                                                        kotlinx.coroutines.delay(220L)
                                                        activeTabTapJob = null
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
                                .semantics { this.selected = selected },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                modifier = Modifier
                                    .expressivePressScale(interactionSource, scaleDown = 0.94f),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                // M3 Expressive Active Indicator Pill & Centered Icon
                                Box(
                                    modifier = Modifier.size(width = 56.dp, height = 30.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (indicatorAlpha > 0.005f) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .graphicsLayer {
                                                    scaleX = indicatorScaleX
                                                    scaleY = indicatorScaleY
                                                    alpha = indicatorAlpha
                                                }
                                                .background(
                                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                                    shape = RoundedCornerShape(indicatorCorner)
                                                )
                                        )
                                    }

                                    Icon(
                                        imageVector = if (selected) screen.selectedIcon else screen.icon,
                                        contentDescription = stringResource(screen.titleRes),
                                        tint = iconColor,
                                        modifier = Modifier
                                            .size(24.dp)
                                            .graphicsLayer {
                                                scaleX = iconScale
                                                scaleY = iconScale
                                            }
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = stringResource(screen.navLabelRes),
                                    style = if (selected) {
                                        MaterialTheme.typography.labelSmallEmphasized.copy(lineHeight = 14.sp)
                                    } else {
                                        MaterialTheme.typography.labelSmall.copy(lineHeight = 14.sp)
                                    },
                                    color = textColor,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
