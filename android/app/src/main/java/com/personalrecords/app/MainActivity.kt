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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
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
    val currentRoute = navBackStackEntry?.destination?.route

    // Hoist top-level tab ViewModels to Activity scope so switching tabs is instantaneous
    // and never destroys/re-fetches state when navigating between main destinations.
    val factory = remember { AppViewModelFactory() }
    val overviewVm: OverviewViewModel = viewModel(factory = factory)
    val timelineVm: TimelineViewModel = viewModel(factory = factory)
    val recordsVm: RecordsViewModel = viewModel(factory = factory)
    val settingsVm: SettingsViewModel = viewModel(factory = factory)

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

    val detailSubRoutes = remember {
        setOf(
            Screen.Nutrition.route,
            Screen.Books.route,
            Screen.Movies.route,
            Screen.Notifications.route,
            Screen.Language.route
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = Screen.Overview.route,
            modifier = Modifier.fillMaxSize(),
            enterTransition = {
                val target = targetState.destination.route
                if (target in detailSubRoutes) {
                    slideInHorizontally(
                        initialOffsetX = { (it * 0.14f).toInt() },
                        animationSpec = M3ExpressiveMotion.fastSpatial()
                    ) + fadeIn(animationSpec = M3ExpressiveMotion.fastEffects())
                } else {
                    fadeIn(animationSpec = M3ExpressiveMotion.fastEffects())
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
                fadeIn(animationSpec = M3ExpressiveMotion.fastEffects())
            },
            popExitTransition = {
                val initial = initialState.destination.route
                if (initial in detailSubRoutes) {
                    slideOutHorizontally(
                        targetOffsetX = { (it * 0.14f).toInt() },
                        animationSpec = M3ExpressiveMotion.fastSpatial()
                    ) + fadeOut(animationSpec = M3ExpressiveMotion.fastEffects())
                } else {
                    fadeOut(animationSpec = M3ExpressiveMotion.fastEffects())
                }
            }
        ) {
            composable(Screen.Overview.route) {
                OverviewScreen(
                    viewModel = overviewVm,
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
                TimelineScreen(
                    viewModel = timelineVm,
                    scrollToTopFlow = timelineScrollFlow,
                    refreshFlow = timelineRefreshFlow,
                    onNavigateToNutrition = { navController.navigate(Screen.Nutrition.route) },
                    onNavigateToBooks = { navController.navigate(Screen.Books.route) },
                    onNavigateToMovies = { navController.navigate(Screen.Movies.route) }
                )
            }

            composable(Screen.Records.route) {
                RecordsScreen(
                    viewModel = recordsVm,
                    scrollToTopFlow = recordsScrollFlow,
                    refreshFlow = recordsRefreshFlow,
                    onNavigateToNutrition = { navController.navigate(Screen.Nutrition.route) },
                    onNavigateToBooks = { navController.navigate(Screen.Books.route) },
                    onNavigateToMovies = { navController.navigate(Screen.Movies.route) },
                    onNavigateToNotifications = { navController.navigate(Screen.Notifications.route) }
                )
            }

            composable(Screen.Nutrition.route) {
                val vm: NutritionViewModel = viewModel(factory = factory)
                NutritionScreen(
                    viewModel = vm,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.Books.route) {
                val vm: BooksViewModel = viewModel(factory = factory)
                BooksScreen(
                    viewModel = vm,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.Movies.route) {
                val vm: MoviesViewModel = viewModel(factory = factory)
                MoviesScreen(
                    viewModel = vm,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.Notifications.route) {
                val vm: NotificationsViewModel = viewModel(factory = factory)
                NotificationsScreen(
                    viewModel = vm,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.Settings.route) {
                SettingsScreen(
                    viewModel = settingsVm,
                    scrollToTopFlow = settingsScrollFlow,
                    refreshFlow = settingsRefreshFlow,
                    onNavigateToLanguage = { navController.navigate(Screen.Language.route) }
                )
            }

            composable(Screen.Language.route) {
                val state by settingsVm.uiState.collectAsState()
                LanguageSelectionScreen(
                    currentLanguage = state.appLanguage,
                    onLanguageSelected = { lang -> settingsVm.setLanguage(lang) },
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }

        val haptic = LocalHapticFeedback.current
        ExpressiveFloatingBottomBar(
            currentRoute = currentRoute,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            onTabClick = { screen, isSelected ->
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
                    if (screen == Screen.Records && currentRoute != Screen.Records.route) {
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
                    } else if (screen == Screen.Settings && currentRoute == Screen.Language.route) {
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
        )
    }
}

/**
 * Isolated M3 Expressive Floating Toolbar + Companion Settings Button.
 * Uses draw-phase (`drawBehind`) and layout/layer-phase (`graphicsLayer`) state reads
 * so animations never trigger recomposition of [MainAppHost] or [NavHost].
 */
@Composable
private fun ExpressiveFloatingBottomBar(
    currentRoute: String?,
    onTabClick: (Screen, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val mainNavScreens = remember {
        listOf(Screen.Overview, Screen.Timeline, Screen.Records)
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 1. Primary Compact Floating Toolbar (Overview, Timeline, Records)
            // M3 Expressive: no drop shadow (shadowElevation = 0.dp), color fill creates separation
            Surface(
                shape = M3Shapes.CornerFull,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
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
                            Screen.Records -> currentRoute in recordsSubRoutes
                            else -> currentRoute == screen.route
                        }
                        FloatingToolbarTabItem(
                            screen = screen,
                            selected = selected,
                            onClick = { onTabClick(screen, selected) }
                        )
                    }
                }
            }

            // 2. Companion Settings Button (8dp separated per M3 Expressive FloatingToolbar spec)
            val settingsSelected = currentRoute == Screen.Settings.route ||
                currentRoute == Screen.Language.route
            FloatingSettingsOrbiter(
                selected = settingsSelected,
                onClick = { onTabClick(Screen.Settings, settingsSelected) }
            )
        }
    }
}

@Composable
private fun FloatingToolbarTabItem(
    screen: Screen,
    selected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val indicatorCornerState = animateDpAsState(
        targetValue = if (isPressed) 16.dp else 24.dp,
        animationSpec = M3ExpressiveMotion.fastSpatial(),
        label = "nav_item_corner"
    )
    val indicatorAlphaState = animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = M3ExpressiveMotion.fastEffects(),
        label = "nav_item_bg_alpha"
    )
    val iconScaleState = animateFloatAsState(
        targetValue = if (selected) 1.08f else 1.0f,
        animationSpec = M3ExpressiveMotion.fastSpatial(),
        label = "nav_item_icon_scale"
    )

    val activeContainerColor = MaterialTheme.colorScheme.secondaryContainer
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier
            .height(48.dp)
            .expressivePressScale(interactionSource, scaleDown = 0.94f)
            .drawBehind {
                val alpha = indicatorAlphaState.value
                if (alpha > 0.005f) {
                    val r = indicatorCornerState.value.toPx()
                    drawRoundRect(
                        color = activeContainerColor.copy(alpha = alpha),
                        cornerRadius = CornerRadius(r, r)
                    )
                }
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Tab,
                onClick = onClick
            )
            .semantics { this.selected = selected }
            .padding(horizontal = if (selected) 16.dp else 12.dp),
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
                    val s = iconScaleState.value
                    scaleX = s
                    scaleY = s
                }
        )

        AnimatedVisibility(
            visible = selected,
            enter = expandHorizontally(
                animationSpec = M3ExpressiveMotion.fastSpatial(),
                expandFrom = Alignment.Start
            ) + fadeIn(animationSpec = M3ExpressiveMotion.fastEffects()),
            exit = shrinkHorizontally(
                animationSpec = M3ExpressiveMotion.fastSpatial(),
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

@Composable
private fun FloatingSettingsOrbiter(
    selected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val innerCornerState = animateDpAsState(
        targetValue = if (isPressed) 16.dp else 24.dp,
        animationSpec = M3ExpressiveMotion.fastSpatial(),
        label = "settings_inner_corner"
    )
    val indicatorAlphaState = animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = M3ExpressiveMotion.fastEffects(),
        label = "settings_indicator_alpha"
    )
    val indicatorScaleState = animateFloatAsState(
        targetValue = if (selected) 1f else 0.7f,
        animationSpec = M3ExpressiveMotion.fastSpatial(),
        label = "settings_indicator_scale"
    )
    val iconRotationState = animateFloatAsState(
        targetValue = if (selected) 60f else 0f,
        animationSpec = M3ExpressiveMotion.fastSpatial(),
        label = "settings_icon_rotation"
    )

    val activeContainerColor = MaterialTheme.colorScheme.secondaryContainer
    val iconColor = if (selected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        shape = M3Shapes.CornerFull,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = Modifier
            .size(64.dp)
            .expressivePressScale(interactionSource, scaleDown = 0.92f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Tab,
                    onClick = onClick
                )
                .semantics { this.selected = selected }
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val s = indicatorScaleState.value
                        scaleX = s
                        scaleY = s
                        alpha = indicatorAlphaState.value
                    }
                    .drawBehind {
                        if (indicatorAlphaState.value > 0.005f) {
                            val r = innerCornerState.value.toPx()
                            drawRoundRect(
                                color = activeContainerColor,
                                cornerRadius = CornerRadius(r, r)
                            )
                        }
                    }
            )

            Icon(
                imageVector = if (selected) Screen.Settings.selectedIcon else Screen.Settings.icon,
                contentDescription = stringResource(Screen.Settings.titleRes),
                tint = iconColor,
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer {
                        rotationZ = iconRotationState.value
                    }
            )
        }
    }
}
