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
import androidx.navigation.NavDestination.Companion.hasRoute
import com.personalrecords.app.ui.navigation.Screen
import com.personalrecords.app.ui.navigation.bottomNavScreens
import com.personalrecords.app.ui.navigation.detailScreens
import com.personalrecords.app.ui.navigation.recordsSubScreens
import com.personalrecords.app.ui.screens.books.*
import com.personalrecords.app.ui.screens.health.*
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
    val nutritionVm: NutritionViewModel = viewModel(factory = AppViewModelFactory())
    val booksVm: BooksViewModel = viewModel(factory = AppViewModelFactory())
    val moviesVm: MoviesViewModel = viewModel(factory = AppViewModelFactory())
    val notificationsVm: NotificationsViewModel = viewModel(factory = AppViewModelFactory())
    val healthConnectVm: HealthConnectViewModel = viewModel(factory = AppViewModelFactory())

    val overviewScrollFlow = remember { MutableSharedFlow<Unit>(extraBufferCapacity = 1) }
    val overviewRefreshFlow = remember { MutableSharedFlow<Unit>(extraBufferCapacity = 1) }

    val timelineScrollFlow = remember { MutableSharedFlow<Unit>(extraBufferCapacity = 1) }
    val timelineRefreshFlow = remember { MutableSharedFlow<Unit>(extraBufferCapacity = 1) }

    val recordsScrollFlow = remember { MutableSharedFlow<Unit>(extraBufferCapacity = 1) }
    val recordsRefreshFlow = remember { MutableSharedFlow<Unit>(extraBufferCapacity = 1) }

    val settingsScrollFlow = remember { MutableSharedFlow<Unit>(extraBufferCapacity = 1) }
    val settingsRefreshFlow = remember { MutableSharedFlow<Unit>(extraBufferCapacity = 1) }

    var isRecordsWarm by remember { mutableStateOf(false) }
    var isTimelineWarm by remember { mutableStateOf(false) }
    var isSettingsWarm by remember { mutableStateOf(false) }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val destination = navBackStackEntry?.destination
    val isDetailRoute = destination != null && detailScreens.any { destination.hasRoute(it::class) }

    var lastActivePeerTab by remember { mutableStateOf<Screen>(Screen.Overview) }
    if (!isDetailRoute && destination != null) {
        when {
            destination.hasRoute<Screen.Overview>() -> lastActivePeerTab = Screen.Overview
            destination.hasRoute<Screen.Timeline>() -> lastActivePeerTab = Screen.Timeline
            destination.hasRoute<Screen.Records>() -> lastActivePeerTab = Screen.Records
            destination.hasRoute<Screen.Settings>() -> lastActivePeerTab = Screen.Settings
        }
    }

    LaunchedEffect(lastActivePeerTab) {
        when (lastActivePeerTab) {
            Screen.Timeline -> timelineVm.ensureLoaded()
            Screen.Records -> recordsVm.ensureLoaded()
            Screen.Settings -> settingsVm.ensureLoaded()
            else -> {}
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

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isWideScreen = configuration.screenWidthDp >= 600

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = if (isWideScreen) 88.dp else 0.dp)
        ) {
            // Persistent Peer Tabs Layer: keeps Overview, Timeline, Records, and Settings
            // warm in memory (measured once, unplaced when inactive so 0 GPU/touch/recomposition cost).
            // Keeping the active peer tab placed during detail route transitions prevents a Frame-1
            // layout invalidation and blank background flash while the detail screen slides/fades in.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .keepWarmTab(visible = lastActivePeerTab == Screen.Overview)
            ) {
            OverviewScreen(
                viewModel = overviewVm,
                scrollToTopFlow = overviewScrollFlow,
                refreshFlow = overviewRefreshFlow,
                onNavigateToTimeline = {
                    timelineVm.ensureLoaded()
                    navController.navigate(Screen.Timeline) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onNavigateToNutrition = { navController.navigate(Screen.Nutrition) },
                onNavigateToBooks = { navController.navigate(Screen.Books) },
                onNavigateToMovies = { navController.navigate(Screen.Movies) },
                onNavigateToSettings = { navController.navigate(Screen.Settings) }
            )
        }

        if (isTimelineWarm || lastActivePeerTab == Screen.Timeline) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .keepWarmTab(visible = lastActivePeerTab == Screen.Timeline)
            ) {
                TimelineScreen(
                    viewModel = timelineVm,
                    scrollToTopFlow = timelineScrollFlow,
                    refreshFlow = timelineRefreshFlow,
                    onNavigateToNutrition = { navController.navigate(Screen.Nutrition) },
                    onNavigateToBooks = { navController.navigate(Screen.Books) },
                    onNavigateToMovies = { navController.navigate(Screen.Movies) }
                )
            }
        }

        if (isRecordsWarm || lastActivePeerTab == Screen.Records) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .keepWarmTab(visible = lastActivePeerTab == Screen.Records)
            ) {
                RecordsScreen(
                    viewModel = recordsVm,
                    scrollToTopFlow = recordsScrollFlow,
                    refreshFlow = recordsRefreshFlow,
                    onNavigateToNutrition = { navController.navigate(Screen.Nutrition) },
                    onNavigateToBooks = { navController.navigate(Screen.Books) },
                    onNavigateToMovies = { navController.navigate(Screen.Movies) },
                    onNavigateToNotifications = { navController.navigate(Screen.Notifications) },
                    onNavigateToHealthConnect = { navController.navigate(Screen.HealthConnect) }
                )
            }
        }

        if (isSettingsWarm || lastActivePeerTab == Screen.Settings) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .keepWarmTab(visible = lastActivePeerTab == Screen.Settings)
            ) {
                SettingsScreen(
                    viewModel = settingsVm,
                    scrollToTopFlow = settingsScrollFlow,
                    refreshFlow = settingsRefreshFlow,
                    onNavigateToLanguage = { navController.navigate(Screen.Language) }
                )
            }
        }

        NavHost(
            navController = navController,
            startDestination = Screen.Overview,
            modifier = Modifier.fillMaxSize(),
            enterTransition = {
                val target = targetState.destination
                if (detailScreens.any { target.hasRoute(it::class) }) {
                    slideInHorizontally(
                        initialOffsetX = { fullWidth -> fullWidth },
                        animationSpec = androidx.compose.animation.core.spring(
                            dampingRatio = 0.85f,
                            stiffness = 380f,
                            visibilityThreshold = androidx.compose.ui.unit.IntOffset.VisibilityThreshold
                        )
                    )
                } else {
                    EnterTransition.None
                }
            },
            exitTransition = {
                val initial = initialState.destination
                val target = targetState.destination
                if (detailScreens.any { initial.hasRoute(it::class) } && detailScreens.any { target.hasRoute(it::class) }) {
                    slideOutHorizontally(
                        targetOffsetX = { fullWidth -> -(fullWidth * 0.25f).toInt() },
                        animationSpec = androidx.compose.animation.core.spring(
                            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                            stiffness = 400f,
                            visibilityThreshold = androidx.compose.ui.unit.IntOffset.VisibilityThreshold
                        )
                    )
                } else {
                    ExitTransition.None
                }
            },
            popEnterTransition = {
                val initial = initialState.destination
                val target = targetState.destination
                if (detailScreens.any { initial.hasRoute(it::class) } && detailScreens.any { target.hasRoute(it::class) }) {
                    slideInHorizontally(
                        initialOffsetX = { fullWidth -> -(fullWidth * 0.25f).toInt() },
                        animationSpec = androidx.compose.animation.core.spring(
                            dampingRatio = 0.85f,
                            stiffness = 380f,
                            visibilityThreshold = androidx.compose.ui.unit.IntOffset.VisibilityThreshold
                        )
                    )
                } else {
                    EnterTransition.None
                }
            },
            popExitTransition = {
                val initial = initialState.destination
                if (detailScreens.any { initial.hasRoute(it::class) }) {
                    slideOutHorizontally(
                        targetOffsetX = { fullWidth -> fullWidth },
                        animationSpec = androidx.compose.animation.core.spring(
                            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                            stiffness = 400f,
                            visibilityThreshold = androidx.compose.ui.unit.IntOffset.VisibilityThreshold
                        )
                    )
                } else {
                    ExitTransition.None
                }
            }
        ) {
            composable<Screen.Overview> {
                Spacer(modifier = Modifier.fillMaxSize())
            }

            composable<Screen.Timeline> {
                Spacer(modifier = Modifier.fillMaxSize())
            }

            composable<Screen.Records> {
                Spacer(modifier = Modifier.fillMaxSize())
            }

            composable<Screen.Settings> {
                Spacer(modifier = Modifier.fillMaxSize())
            }

            composable<Screen.Nutrition> {
                NutritionScreen(
                    viewModel = nutritionVm,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable<Screen.Books> {
                BooksScreen(
                    viewModel = booksVm,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable<Screen.Movies> {
                MoviesScreen(
                    viewModel = moviesVm,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable<Screen.Notifications> {
                NotificationsScreen(
                    viewModel = notificationsVm,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable<Screen.HealthConnect> {
                HealthConnectScreen(
                    viewModel = healthConnectVm,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable<Screen.Language> {
                val state by settingsVm.uiState.collectAsState()
                LanguageSelectionScreen(
                    currentLanguage = state.appLanguage,
                    onLanguageSelected = { lang -> settingsVm.setLanguage(lang) },
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
        }

        ExpressiveAdaptiveNavigation(
            navController = navController,
            onScrollToTop = { screen ->
                when (screen) {
                    Screen.Overview -> overviewScrollFlow.tryEmit(Unit)
                    Screen.Timeline -> timelineScrollFlow.tryEmit(Unit)
                    Screen.Records -> recordsScrollFlow.tryEmit(Unit)
                    Screen.Settings -> settingsScrollFlow.tryEmit(Unit)
                    else -> Unit
                }
            },
            onRefresh = { screen ->
                when (screen) {
                    Screen.Overview -> overviewRefreshFlow.tryEmit(Unit)
                    Screen.Timeline -> timelineRefreshFlow.tryEmit(Unit)
                    Screen.Records -> recordsRefreshFlow.tryEmit(Unit)
                    Screen.Settings -> settingsRefreshFlow.tryEmit(Unit)
                    else -> Unit
                }
            },
            isWideScreen = isWideScreen,
            modifier = if (isWideScreen) Modifier.align(Alignment.CenterStart) else Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun CompanionSettingsButton(
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val outerCorner by animateDpAsState(
        targetValue = if (isPressed) 20.dp else 32.dp,
        animationSpec = M3ExpressiveMotion.fastSpatial(),
        label = "settings_outer_corner"
    )
    val innerCornerState = animateDpAsState(
        targetValue = if (isPressed) 16.dp else 24.dp,
        animationSpec = M3ExpressiveMotion.fastSpatial(),
        label = "settings_inner_corner"
    )
    val indicatorAlphaState = animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = M3ExpressiveMotion.fastEffects(),
        label = "settings_indicator_alpha"
    )
    val indicatorScaleState = animateFloatAsState(
        targetValue = if (isSelected) 1f else 0.7f,
        animationSpec = M3ExpressiveMotion.fastSpatial(),
        label = "settings_indicator_scale"
    )
    val iconRotationState = animateFloatAsState(
        targetValue = if (isSelected) 60f else 0f,
        animationSpec = M3ExpressiveMotion.defaultSpatial(),
        label = "settings_icon_rotation"
    )
    val iconColor = if (isSelected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val secondaryContainerColor = MaterialTheme.colorScheme.secondaryContainer

    Surface(
        shape = RoundedCornerShape(outerCorner),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp,
        shadowElevation = 4.dp,
        modifier = modifier
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
                .semantics { this.selected = isSelected }
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
                                color = secondaryContainerColor,
                                cornerRadius = CornerRadius(r, r)
                            )
                        }
                    }
            )

            Icon(
                imageVector = if (isSelected) Screen.Settings.selectedIcon else Screen.Settings.icon,
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

@Composable
private fun ExpressiveAdaptiveNavigation(
    navController: NavHostController,
    onScrollToTop: (Screen) -> Unit,
    onRefresh: (Screen) -> Unit,
    isWideScreen: Boolean,
    modifier: Modifier = Modifier
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val destination = navBackStackEntry?.destination

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
            val dest = navBackStackEntry?.destination
            if (screen == Screen.Records && dest?.hasRoute<Screen.Records>() != true) {
                val popped = navController.popBackStack(Screen.Records, inclusive = false)
                if (!popped) {
                    navController.navigate(Screen.Records) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            } else if (screen == Screen.Settings && dest?.hasRoute<Screen.Language>() == true) {
                navController.popBackStack(Screen.Settings, inclusive = false)
            } else {
                if (activeTabTapJob?.isActive == true) {
                    activeTabTapJob?.cancel()
                    activeTabTapJob = null
                    onRefresh(screen)
                } else {
                    onScrollToTop(screen)
                    activeTabTapJob = coroutineScope.launch {
                        kotlinx.coroutines.delay(220L)
                        activeTabTapJob = null
                    }
                }
            }
        } else {
            navController.navigate(screen) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    val settingsSelected = destination?.let {
        it.hasRoute<Screen.Settings>() || it.hasRoute<Screen.Language>()
    } == true

    if (isWideScreen) {
        // Floating NavRail — M3 Expressive Vertical Floating Toolbar + Companion Action
        Box(
            modifier = modifier
                .fillMaxHeight()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(start = 12.dp, top = 16.dp, bottom = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 1. Primary Compact Floating Toolbar (Overview, Timeline, Records)
                Surface(
                    shape = M3Shapes.CornerFull,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 3.dp,
                    shadowElevation = 4.dp,
                    modifier = Modifier.width(64.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        mainNavScreens.forEach { screen ->
                            key(screen) {
                                val selected = when (screen) {
                                    Screen.Records -> destination?.let { dest -> recordsSubScreens.any { dest.hasRoute(it::class) } } == true
                                    else -> destination?.hasRoute(screen::class) == true
                                }

                                val interactionSource = remember { MutableInteractionSource() }
                                val isPressed by interactionSource.collectIsPressedAsState()

                                val selectionProgressState = animateFloatAsState(
                                    targetValue = if (selected) 1f else 0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = 600f
                                    ),
                                    label = "nav_rail_selection_progress"
                                )

                                val indicatorCornerState = animateDpAsState(
                                    targetValue = if (isPressed) 16.dp else 24.dp,
                                    animationSpec = M3ExpressiveMotion.fastSpatial(),
                                    label = "nav_rail_corner"
                                )

                                val iconScaleState = animateFloatAsState(
                                    targetValue = if (selected) 1.08f else 1.0f,
                                    animationSpec = M3ExpressiveMotion.fastSpatial(),
                                    label = "nav_rail_icon_scale"
                                )

                                val contentColor = if (selected) {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }

                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
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
                                        .semantics { this.selected = selected },
                                    contentAlignment = Alignment.Center
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
                                }
                            }
                        }
                    }
                }

                // 2. Companion Settings Button
                CompanionSettingsButton(
                    isSelected = settingsSelected,
                    onClick = { handleNavClick(Screen.Settings, settingsSelected) }
                )
            }
        }
    } else {
        // Floating Bottom Bar — M3 Expressive HorizontalFloatingToolbar + Companion Action
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
                            key(screen) {
                                val selected = when (screen) {
                                    Screen.Records -> destination?.let { dest -> recordsSubScreens.any { dest.hasRoute(it::class) } } == true
                                    else -> destination?.hasRoute(screen::class) == true
                                }

                                val interactionSource = remember { MutableInteractionSource() }
                                val isPressed by interactionSource.collectIsPressedAsState()

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

                // 2. Companion Settings Button
                CompanionSettingsButton(
                    isSelected = settingsSelected,
                    onClick = { handleNavClick(Screen.Settings, settingsSelected) }
                )
            }
        }
    }
}
