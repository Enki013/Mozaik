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
import com.personalrecords.app.core.theme.labelLargeEmphasized
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

        // Floating Bottom Bar — M3 Expressive HorizontalFloatingToolbar + Companion Action
        // Specs: 64dp container height, CornerFull capsule shape, 8dp leading/trailing padding,
        // 4dp spacing between items, 8dp gap to companion Settings button.
        val haptic = LocalHapticFeedback.current
        val mainNavScreens = remember {
            listOf(Screen.Overview, Screen.Timeline, Screen.Records)
        }

        fun handleNavClick(screen: Screen, isSelected: Boolean) {
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

            if (isSelected) {
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
                } else if (screen == Screen.Settings && currentDestination?.route == Screen.Language.route) {
                    navController.popBackStack(Screen.Settings.route, inclusive = false)
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

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 1. Primary Compact Floating Toolbar — M3 Expressive Vibrant Scheme
                Surface(
                    shape = M3Shapes.CornerFull,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    tonalElevation = 0.dp,
                    shadowElevation = 4.dp,
                    modifier = Modifier.height(64.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        mainNavScreens.forEach { screen ->
                            val selected = when (screen) {
                                Screen.Records -> currentDestination?.route in recordsSubRoutes
                                else -> currentDestination?.route == screen.route
                            }

                            val interactionSource = remember { MutableInteractionSource() }
                            val isPressed by interactionSource.collectIsPressedAsState()

                            val horizontalPadding by animateDpAsState(
                                targetValue = if (selected) 16.dp else 12.dp,
                                animationSpec = M3ExpressiveMotion.defaultSpatial(),
                                label = "nav_item_h_pad"
                            )

                            val indicatorCorner by animateDpAsState(
                                targetValue = if (isPressed) 16.dp else 24.dp,
                                animationSpec = M3ExpressiveMotion.fastSpatial(),
                                label = "nav_item_corner"
                            )

                            val indicatorAlpha by animateFloatAsState(
                                targetValue = if (selected) 1f else 0f,
                                animationSpec = M3ExpressiveMotion.fastEffects(),
                                label = "nav_item_bg_alpha"
                            )

                            val iconScale by animateFloatAsState(
                                targetValue = if (selected) 1.08f else 1.0f,
                                animationSpec = M3ExpressiveMotion.fastSpatial(),
                                label = "nav_item_icon_scale"
                            )

                            val contentColor by animateColorAsState(
                                targetValue = if (selected) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                },
                                animationSpec = M3ExpressiveMotion.fastEffects(),
                                label = "nav_item_content_color"
                            )

                            Row(
                                modifier = Modifier
                                    .height(48.dp)
                                    .expressivePressScale(interactionSource, scaleDown = 0.94f)
                                    .background(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = indicatorAlpha),
                                        shape = RoundedCornerShape(indicatorCorner)
                                    )
                                    .clickable(
                                        interactionSource = interactionSource,
                                        indication = null,
                                        role = Role.Tab,
                                        onClick = { handleNavClick(screen, selected) }
                                    )
                                    .semantics { this.selected = selected }
                                    .padding(horizontal = horizontalPadding),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = if (selected) screen.selectedIcon else screen.icon,
                                    contentDescription = stringResource(screen.titleRes),
                                    tint = contentColor,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .graphicsLayer {
                                            scaleX = iconScale
                                            scaleY = iconScale
                                        }
                                )

                                AnimatedVisibility(
                                    visible = selected,
                                    enter = expandHorizontally(
                                        animationSpec = M3ExpressiveMotion.defaultSpatial(),
                                        expandFrom = Alignment.Start
                                    ) + fadeIn(animationSpec = M3ExpressiveMotion.fastEffects()),
                                    exit = shrinkHorizontally(
                                        animationSpec = M3ExpressiveMotion.defaultSpatial(),
                                        shrinkTowards = Alignment.Start
                                    ) + fadeOut(animationSpec = M3ExpressiveMotion.fastEffects())
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = stringResource(screen.navLabelRes),
                                            style = MaterialTheme.typography.labelLargeEmphasized,
                                            color = contentColor,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Clip
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. Companion Settings Button (8dp separated, M3 Expressive TertiaryContainer pairing)
                val settingsSelected = currentDestination?.route == Screen.Settings.route ||
                    currentDestination?.route == Screen.Language.route
                val settingsInteractionSource = remember { MutableInteractionSource() }
                val isSettingsPressed by settingsInteractionSource.collectIsPressedAsState()

                val settingsOuterCorner by animateDpAsState(
                    targetValue = if (isSettingsPressed) 20.dp else 32.dp,
                    animationSpec = M3ExpressiveMotion.fastSpatial(),
                    label = "settings_outer_corner"
                )
                val settingsInnerCorner by animateDpAsState(
                    targetValue = if (isSettingsPressed) 16.dp else 24.dp,
                    animationSpec = M3ExpressiveMotion.fastSpatial(),
                    label = "settings_inner_corner"
                )
                val settingsIndicatorAlpha by animateFloatAsState(
                    targetValue = if (settingsSelected) 1f else 0f,
                    animationSpec = M3ExpressiveMotion.fastEffects(),
                    label = "settings_indicator_alpha"
                )
                val settingsIndicatorScale by animateFloatAsState(
                    targetValue = if (settingsSelected) 1f else 0.7f,
                    animationSpec = M3ExpressiveMotion.fastSpatial(),
                    label = "settings_indicator_scale"
                )
                val settingsIconRotation by animateFloatAsState(
                    targetValue = if (settingsSelected) 60f else 0f,
                    animationSpec = M3ExpressiveMotion.defaultSpatial(),
                    label = "settings_icon_rotation"
                )
                val settingsIconColor by animateColorAsState(
                    targetValue = if (settingsSelected) {
                        MaterialTheme.colorScheme.onTertiary
                    } else {
                        MaterialTheme.colorScheme.onTertiaryContainer
                    },
                    animationSpec = M3ExpressiveMotion.fastEffects(),
                    label = "settings_icon_color"
                )

                Surface(
                    shape = RoundedCornerShape(settingsOuterCorner),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    tonalElevation = 0.dp,
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .size(64.dp)
                        .expressivePressScale(settingsInteractionSource, scaleDown = 0.92f)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                interactionSource = settingsInteractionSource,
                                indication = null,
                                role = Role.Tab,
                                onClick = { handleNavClick(Screen.Settings, settingsSelected) }
                            )
                            .semantics { this.selected = settingsSelected }
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (settingsIndicatorAlpha > 0.005f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        scaleX = settingsIndicatorScale
                                        scaleY = settingsIndicatorScale
                                        alpha = settingsIndicatorAlpha
                                    }
                                    .background(
                                        color = MaterialTheme.colorScheme.tertiary,
                                        shape = RoundedCornerShape(settingsInnerCorner)
                                    )
                            )
                        }

                        Icon(
                            imageVector = if (settingsSelected) Screen.Settings.selectedIcon else Screen.Settings.icon,
                            contentDescription = stringResource(Screen.Settings.titleRes),
                            tint = settingsIconColor,
                            modifier = Modifier
                                .size(24.dp)
                                .graphicsLayer {
                                    rotationZ = settingsIconRotation
                                }
                        )
                    }
                }
            }
        }
    }
}
