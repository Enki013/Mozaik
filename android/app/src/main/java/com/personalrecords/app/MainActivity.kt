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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import kotlin.math.roundToInt
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

private fun Modifier.keepWarmTab(visible: Boolean): Modifier = this.layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(placeable.width, placeable.height) {
        if (visible) {
            placeable.place(0, 0)
        }
    }
}

@Composable
fun MainAppHost() {
    val navController = rememberNavController()

    val overviewVm: OverviewViewModel = viewModel(factory = AppViewModelFactory())
    val timelineVm: TimelineViewModel = viewModel(factory = AppViewModelFactory())
    val recordsVm: RecordsViewModel = viewModel(factory = AppViewModelFactory())
    val settingsVm: SettingsViewModel = viewModel(factory = AppViewModelFactory())

    val overviewScrollFlow = remember { MutableSharedFlow<Unit>(extraBufferCapacity = 1) }
    val overviewRefreshFlow = remember { MutableSharedFlow<Unit>(extraBufferCapacity = 1) }

    val timelineScrollFlow = remember { MutableSharedFlow<Unit>(extraBufferCapacity = 1) }
    val timelineRefreshFlow = remember { MutableSharedFlow<Unit>(extraBufferCapacity = 1) }

    val recordsScrollFlow = remember { MutableSharedFlow<Unit>(extraBufferCapacity = 1) }
    val recordsRefreshFlow = remember { MutableSharedFlow<Unit>(extraBufferCapacity = 1) }

    val settingsScrollFlow = remember { MutableSharedFlow<Unit>(extraBufferCapacity = 1) }
    val settingsRefreshFlow = remember { MutableSharedFlow<Unit>(extraBufferCapacity = 1) }

    val detailSubRoutes = remember {
        setOf(
            Screen.Nutrition.route,
            Screen.Books.route,
            Screen.Movies.route,
            Screen.Notifications.route,
            Screen.HealthConnect.route,
            Screen.Language.route
        )
    }

    var isRecordsWarm by remember { mutableStateOf(false) }
    var isTimelineWarm by remember { mutableStateOf(false) }
    var isSettingsWarm by remember { mutableStateOf(false) }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: Screen.Overview.route
    val isDetailRoute = currentRoute in detailSubRoutes

    var lastActivePeerTab by remember { mutableStateOf(Screen.Overview.route) }
    if (!isDetailRoute) {
        lastActivePeerTab = currentRoute
    }

    LaunchedEffect(lastActivePeerTab) {
        when (lastActivePeerTab) {
            Screen.Timeline.route -> timelineVm.ensureLoaded()
            Screen.Records.route -> recordsVm.ensureLoaded()
            Screen.Settings.route -> settingsVm.ensureLoaded()
        }
    }

    LaunchedEffect(Unit) {
        // Allow initial startup and OverviewScreen animations to settle first
        kotlinx.coroutines.delay(550L)
        PersonalRecordsApp.instance.repository.prewarmRoomCaches()
        recordsVm.ensureLoaded()
        kotlinx.coroutines.delay(220L)
        isRecordsWarm = true
        kotlinx.coroutines.delay(250L)
        timelineVm.ensureLoaded()
        kotlinx.coroutines.delay(450L)
        isTimelineWarm = true
        kotlinx.coroutines.delay(300L)
        settingsVm.ensureLoaded()
        isSettingsWarm = true
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Persistent Peer Tabs Layer: keeps Overview, Timeline, Records, and Settings
        // warm in memory (measured once, unplaced when inactive so 0 GPU/touch/recomposition cost).
        // Keeping the active peer tab placed during detail route transitions prevents a Frame-1
        // layout invalidation and blank background flash while the detail screen slides/fades in.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .keepWarmTab(visible = lastActivePeerTab == Screen.Overview.route)
        ) {
            OverviewScreen(
                viewModel = overviewVm,
                scrollToTopFlow = overviewScrollFlow,
                refreshFlow = overviewRefreshFlow,
                onNavigateToTimeline = {
                    timelineVm.ensureLoaded()
                    navController.navigate(Screen.Timeline.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onNavigateToNutrition = { navController.navigate(Screen.Nutrition.route) },
                onNavigateToBooks = { navController.navigate(Screen.Books.route) },
                onNavigateToMovies = { navController.navigate(Screen.Movies.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
            )
        }

        if (isTimelineWarm || lastActivePeerTab == Screen.Timeline.route) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .keepWarmTab(visible = lastActivePeerTab == Screen.Timeline.route)
            ) {
                TimelineScreen(
                    viewModel = timelineVm,
                    scrollToTopFlow = timelineScrollFlow,
                    refreshFlow = timelineRefreshFlow,
                    onNavigateToNutrition = { navController.navigate(Screen.Nutrition.route) },
                    onNavigateToBooks = { navController.navigate(Screen.Books.route) },
                    onNavigateToMovies = { navController.navigate(Screen.Movies.route) }
                )
            }
        }

        if (isRecordsWarm || lastActivePeerTab == Screen.Records.route) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .keepWarmTab(visible = lastActivePeerTab == Screen.Records.route)
            ) {
                RecordsScreen(
                    viewModel = recordsVm,
                    scrollToTopFlow = recordsScrollFlow,
                    refreshFlow = recordsRefreshFlow,
                    onNavigateToNutrition = { navController.navigate(Screen.Nutrition.route) },
                    onNavigateToBooks = { navController.navigate(Screen.Books.route) },
                    onNavigateToMovies = { navController.navigate(Screen.Movies.route) },
                    onNavigateToNotifications = { navController.navigate(Screen.Notifications.route) },
                    onNavigateToHealthConnect = { navController.navigate(Screen.HealthConnect.route) }
                )
            }
        }

        if (isSettingsWarm || lastActivePeerTab == Screen.Settings.route) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .keepWarmTab(visible = lastActivePeerTab == Screen.Settings.route)
            ) {
                SettingsScreen(
                    viewModel = settingsVm,
                    scrollToTopFlow = settingsScrollFlow,
                    refreshFlow = settingsRefreshFlow,
                    onNavigateToLanguage = { navController.navigate(Screen.Language.route) }
                )
            }
        }

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
                    EnterTransition.None
                }
            },
            exitTransition = {
                ExitTransition.None
            },
            popEnterTransition = {
                EnterTransition.None
            },
            popExitTransition = {
                val initial = initialState.destination.route
                if (initial in detailSubRoutes) {
                    scaleOut(
                        targetScale = 0.94f,
                        animationSpec = M3ExpressiveMotion.fastSpatial()
                    ) + fadeOut(animationSpec = M3ExpressiveMotion.fastEffects())
                } else {
                    ExitTransition.None
                }
            }
        ) {
            composable(Screen.Overview.route) {
                Spacer(modifier = Modifier.fillMaxSize())
            }

            composable(Screen.Timeline.route) {
                Spacer(modifier = Modifier.fillMaxSize())
            }

            composable(Screen.Records.route) {
                Spacer(modifier = Modifier.fillMaxSize())
            }

            composable(Screen.Settings.route) {
                Spacer(modifier = Modifier.fillMaxSize())
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

            composable(Screen.HealthConnect.route) {
                val vm: com.personalrecords.app.ui.screens.health.HealthConnectViewModel = viewModel(factory = AppViewModelFactory())
                com.personalrecords.app.ui.screens.health.HealthConnectScreen(
                    viewModel = vm,
                    onNavigateBack = { navController.popBackStack() }
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

        ExpressiveFloatingBottomBar(
            navController = navController,
            onScrollToTop = { route ->
                when (route) {
                    Screen.Overview.route -> overviewScrollFlow.tryEmit(Unit)
                    Screen.Timeline.route -> timelineScrollFlow.tryEmit(Unit)
                    Screen.Records.route -> recordsScrollFlow.tryEmit(Unit)
                    Screen.Settings.route -> settingsScrollFlow.tryEmit(Unit)
                }
            },
            onRefresh = { route ->
                when (route) {
                    Screen.Overview.route -> overviewRefreshFlow.tryEmit(Unit)
                    Screen.Timeline.route -> timelineRefreshFlow.tryEmit(Unit)
                    Screen.Records.route -> recordsRefreshFlow.tryEmit(Unit)
                    Screen.Settings.route -> settingsRefreshFlow.tryEmit(Unit)
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun ExpressiveFloatingBottomBar(
    navController: NavHostController,
    onScrollToTop: (String) -> Unit,
    onRefresh: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val backStackRoute = navBackStackEntry?.destination?.route
    var optimisticRoute by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(backStackRoute) {
        if (backStackRoute != null) {
            optimisticRoute = backStackRoute
        }
    }

    val currentRoute = optimisticRoute ?: backStackRoute

    val coroutineScope = rememberCoroutineScope()
    var activeTabTapJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val haptic = LocalHapticFeedback.current
    val mainNavScreens = remember {
        listOf(Screen.Overview, Screen.Timeline, Screen.Records)
    }
    val secondaryContainerColor = MaterialTheme.colorScheme.secondaryContainer

    fun handleNavClick(screen: Screen, isSelected: Boolean) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)

        if (isSelected) {
            if (screen == Screen.Records && backStackRoute != Screen.Records.route) {
                optimisticRoute = Screen.Records.route
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
            } else if (screen == Screen.Settings && backStackRoute == Screen.Language.route) {
                optimisticRoute = Screen.Settings.route
                navController.popBackStack(Screen.Settings.route, inclusive = false)
            } else {
                if (activeTabTapJob?.isActive == true) {
                    activeTabTapJob?.cancel()
                    activeTabTapJob = null
                    onRefresh(screen.route)
                } else {
                    onScrollToTop(screen.route)
                    activeTabTapJob = coroutineScope.launch {
                        kotlinx.coroutines.delay(220L)
                        activeTabTapJob = null
                    }
                }
            }
        } else {
            optimisticRoute = screen.route
            navController.navigate(screen.route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    // Floating Bottom Bar — M3 Expressive HorizontalFloatingToolbar + Companion Action
    // Specs: 64dp container height, CornerFull capsule shape, 8dp leading/trailing padding,
    // 4dp spacing between items, 8dp gap to companion Settings button.
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 1. Primary Compact Floating Toolbar (Overview, Timeline, Records)
            Surface(
                shape = M3Shapes.CornerFull,
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 3.dp,
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
                        key(screen.route) {
                            val selected = when (screen) {
                                Screen.Records -> currentRoute in recordsSubRoutes
                                else -> currentRoute == screen.route
                            }

                            val interactionSource = remember { MutableInteractionSource() }
                            val isPressed by interactionSource.collectIsPressedAsState()

                            // Single non-overshooting progress state read ONLY in layout/draw phases (zero recomposition per frame)
                            val selectionProgressState = animateFloatAsState(
                                targetValue = if (selected) 1f else 0f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = 600f
                                ),
                                label = "nav_item_selection_progress"
                            )

                            val indicatorCornerState = animateDpAsState(
                                targetValue = if (isPressed) 16.dp else 24.dp,
                                animationSpec = M3ExpressiveMotion.fastSpatial(),
                                label = "nav_item_corner"
                            )

                            val iconScaleState = animateFloatAsState(
                                targetValue = if (selected) 1.08f else 1.0f,
                                animationSpec = M3ExpressiveMotion.fastSpatial(),
                                label = "nav_item_icon_scale"
                            )

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
                                        val alpha = selectionProgressState.value.coerceIn(0f, 1f)
                                        if (alpha > 0.005f) {
                                            val r = indicatorCornerState.value.toPx()
                                            drawRoundRect(
                                                color = secondaryContainerColor.copy(alpha = alpha),
                                                cornerRadius = CornerRadius(r, r)
                                            )
                                        }
                                    }
                                    .clickable(
                                        interactionSource = interactionSource,
                                        indication = null,
                                        role = Role.Tab,
                                        onClick = { handleNavClick(screen, selected) }
                                    )
                                    .semantics { this.selected = selected }
                                    .layout { measurable, constraints ->
                                        val p = selectionProgressState.value.coerceIn(0f, 1f)
                                        val hPadPx = (12.dp.toPx() + (4.dp.toPx() * p)).roundToInt()
                                        val placeable = measurable.measure(constraints)
                                        layout(placeable.width + hPadPx * 2, placeable.height) {
                                            placeable.placeRelative(hPadPx, 0)
                                        }
                                    },
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

                                // Always-composed label measured once and expanded in the Layout phase (no AnimatedVisibility mount/unmount jump)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .graphicsLayer {
                                            val p = selectionProgressState.value
                                            alpha = ((p - 0.15f) / 0.85f).coerceIn(0f, 1f)
                                        }
                                        .layout { measurable, constraints ->
                                            val p = selectionProgressState.value.coerceIn(0f, 1f)
                                            val placeable = measurable.measure(constraints)
                                            val currentWidth = (placeable.width * p).roundToInt()
                                            layout(currentWidth, placeable.height) {
                                                if (currentWidth > 0) {
                                                    placeable.placeRelative(0, 0)
                                                }
                                            }
                                        }
                                ) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(screen.navLabelRes),
                                        style = MaterialTheme.typography.labelLargeEmphasized,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
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

            // 2. Companion Settings Button (8dp separated per M3 Expressive FloatingToolbar spec)
            val settingsSelected = currentRoute == Screen.Settings.route ||
                currentRoute == Screen.Language.route
            val settingsInteractionSource = remember { MutableInteractionSource() }
            val isSettingsPressed by settingsInteractionSource.collectIsPressedAsState()

            val settingsOuterCorner by animateDpAsState(
                targetValue = if (isSettingsPressed) 20.dp else 32.dp,
                animationSpec = M3ExpressiveMotion.fastSpatial(),
                label = "settings_outer_corner"
            )
            val settingsInnerCornerState = animateDpAsState(
                targetValue = if (isSettingsPressed) 16.dp else 24.dp,
                animationSpec = M3ExpressiveMotion.fastSpatial(),
                label = "settings_inner_corner"
            )
            val settingsIndicatorAlphaState = animateFloatAsState(
                targetValue = if (settingsSelected) 1f else 0f,
                animationSpec = M3ExpressiveMotion.fastEffects(),
                label = "settings_indicator_alpha"
            )
            val settingsIndicatorScaleState = animateFloatAsState(
                targetValue = if (settingsSelected) 1f else 0.7f,
                animationSpec = M3ExpressiveMotion.fastSpatial(),
                label = "settings_indicator_scale"
            )
            val settingsIconRotationState = animateFloatAsState(
                targetValue = if (settingsSelected) 60f else 0f,
                animationSpec = M3ExpressiveMotion.defaultSpatial(),
                label = "settings_icon_rotation"
            )
            val settingsIconColor = if (settingsSelected) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }

            Surface(
                shape = RoundedCornerShape(settingsOuterCorner),
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 3.dp,
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
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val s = settingsIndicatorScaleState.value
                                scaleX = s
                                scaleY = s
                                alpha = settingsIndicatorAlphaState.value
                            }
                            .drawBehind {
                                if (settingsIndicatorAlphaState.value > 0.005f) {
                                    val r = settingsInnerCornerState.value.toPx()
                                    drawRoundRect(
                                        color = secondaryContainerColor,
                                        cornerRadius = CornerRadius(r, r)
                                    )
                                }
                            }
                    )

                    Icon(
                        imageVector = if (settingsSelected) Screen.Settings.selectedIcon else Screen.Settings.icon,
                        contentDescription = stringResource(Screen.Settings.titleRes),
                        tint = settingsIconColor,
                        modifier = Modifier
                            .size(24.dp)
                            .graphicsLayer {
                                rotationZ = settingsIconRotationState.value
                            }
                    )
                }
            }
        }
    }
}
