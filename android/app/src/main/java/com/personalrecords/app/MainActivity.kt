package com.personalrecords.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.personalrecords.app.core.theme.M3ExpressiveMotion
import com.personalrecords.app.core.theme.M3NavigationMetrics
import com.personalrecords.app.core.theme.M3Shapes
import com.personalrecords.app.core.theme.PersonalRecordsTheme
import com.personalrecords.app.ui.AppViewModelFactory
import com.personalrecords.app.ui.navigation.Navigator
import com.personalrecords.app.ui.navigation.Screen
import com.personalrecords.app.ui.navigation.bottomNavScreens
import com.personalrecords.app.ui.navigation.detailScreens
import com.personalrecords.app.ui.navigation.recordsSubScreens
import com.personalrecords.app.ui.navigation.rememberAppNavigationState
import com.personalrecords.app.ui.navigation.topLevelScreens
import com.personalrecords.app.ui.screens.books.*
import com.personalrecords.app.ui.screens.catalog.*
import com.personalrecords.app.ui.screens.health.*
import com.personalrecords.app.ui.screens.movies.*
import com.personalrecords.app.ui.screens.music.*
import com.personalrecords.app.ui.screens.notifications.*
import com.personalrecords.app.ui.screens.nutrition.*
import com.personalrecords.app.ui.screens.overview.*
import com.personalrecords.app.ui.screens.records.*
import com.personalrecords.app.ui.screens.settings.*
import com.personalrecords.app.ui.screens.timeline.*
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
        val locale = if (language == "system") {
            android.content.res.Resources.getSystem().configuration.locales[0]
        } else {
            java.util.Locale.forLanguageTag(language)
        }
        java.util.Locale.setDefault(locale)
        val config = android.content.res.Configuration(context.resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
        // Menus, bottom sheets and dialogs open in their own windows and read the activity's
        // resources directly, not these composition locals. Update the activity resources too,
        // otherwise they fall back to the system language.
        @Suppress("DEPRECATION")
        context.resources.updateConfiguration(config, context.resources.displayMetrics)
        context.createConfigurationContext(config)
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

private fun Any?.toScreen(): Screen? {
    return when (this) {
        is Screen -> this
        is String -> when {
            this == "Overview" || this.contains("Overview") -> Screen.Overview
            this == "Timeline" || this.contains("Timeline") -> Screen.Timeline
            this == "Records" || this.contains("Records") -> Screen.Records
            this == "Settings" || this.contains("Settings") -> Screen.Settings
            this == "Nutrition" || this.contains("Nutrition") -> Screen.Nutrition
            this == "Books" || this.contains("Books") -> Screen.Books
            this == "Movies" || this.contains("Movies") -> Screen.Movies
            this == "Notifications" || this.contains("Notifications") -> Screen.Notifications
            this == "HealthConnect" || this.contains("HealthConnect") -> Screen.HealthConnect
            this == "Music" || this.contains("Music") -> Screen.Music
            this == "Language" || this.contains("Language") -> Screen.Language
            this == "IntegrationsCatalog" || this.contains("IntegrationsCatalog") -> Screen.IntegrationsCatalog
            else -> null
        }
        else -> null
    }
}

@Composable
fun MainAppHost() {
    val activity = LocalContext.current as? ComponentActivity
    val navigationState = rememberAppNavigationState(
        startRoute = Screen.Overview,
        topLevelRoutes = topLevelScreens
    )
    val navigator = remember { Navigator(navigationState) }

    val overviewVm: OverviewViewModel = viewModel(factory = AppViewModelFactory())
    val timelineVm: TimelineViewModel = viewModel(factory = AppViewModelFactory())
    val recordsVm: RecordsViewModel = viewModel(factory = AppViewModelFactory())
    val settingsVm: SettingsViewModel = viewModel(factory = AppViewModelFactory())
    val nutritionVm: NutritionViewModel = viewModel(factory = AppViewModelFactory())
    val booksVm: BooksViewModel = viewModel(factory = AppViewModelFactory())
    val moviesVm: MoviesViewModel = viewModel(factory = AppViewModelFactory())
    val notificationsVm: NotificationsViewModel = viewModel(factory = AppViewModelFactory())
    val healthConnectVm: HealthConnectViewModel = viewModel(factory = AppViewModelFactory())
    val musicVm: MusicViewModel = viewModel(factory = AppViewModelFactory())
    val catalogVm: IntegrationsCatalogViewModel = viewModel(factory = AppViewModelFactory())

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

    val topLevelRoute = navigationState.topLevelRoute
    val currentRoute = navigator.currentRoute()
    val isDetailRoute = currentRoute in detailScreens

    var lastActivePeerTab by remember { mutableStateOf<Screen>(Screen.Overview) }
    if (!isDetailRoute) {
        when (topLevelRoute) {
            Screen.Overview -> lastActivePeerTab = Screen.Overview
            Screen.Timeline -> lastActivePeerTab = Screen.Timeline
            Screen.Records -> lastActivePeerTab = Screen.Records
            Screen.Settings -> lastActivePeerTab = Screen.Settings
            else -> {}
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

    val isWideScreen = com.personalrecords.app.core.theme.currentAdaptiveLayout().isWide
    val navHideThresholdPx = with(androidx.compose.ui.platform.LocalDensity.current) { 16.dp.toPx() }
    val bottomNavVisible = remember { mutableStateOf(true) }
    val accumulatedScroll = remember { mutableFloatStateOf(0f) }
    val bottomNavScrollConnection = remember(navHideThresholdPx, isWideScreen) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                val scrollDelta = consumed.y + available.y
                if (isWideScreen || scrollDelta == 0f) return Offset.Zero

                accumulatedScroll.floatValue += scrollDelta
                when {
                    accumulatedScroll.floatValue <= -navHideThresholdPx -> {
                        bottomNavVisible.value = false
                        accumulatedScroll.floatValue = 0f
                    }
                    accumulatedScroll.floatValue >= navHideThresholdPx -> {
                        bottomNavVisible.value = true
                        accumulatedScroll.floatValue = 0f
                    }
                }
                return Offset.Zero
            }
        }
    }

    LaunchedEffect(topLevelRoute, currentRoute) {
        bottomNavVisible.value = true
        accumulatedScroll.floatValue = 0f
    }

    val entryProvider = entryProvider<androidx.navigation3.runtime.NavKey> {
        entry<Screen.Overview> {
            Spacer(modifier = Modifier.fillMaxSize())
        }

        entry<Screen.Timeline> {
            Spacer(modifier = Modifier.fillMaxSize())
        }

        entry<Screen.Records> {
            Spacer(modifier = Modifier.fillMaxSize())
        }

        entry<Screen.Settings> {
            Spacer(modifier = Modifier.fillMaxSize())
        }

        entry<Screen.Nutrition> {
            NutritionScreen(
                viewModel = nutritionVm,
                onNavigateBack = { navigator.goBack() }
            )
        }

        entry<Screen.Books> {
            BooksScreen(
                viewModel = booksVm,
                onNavigateBack = { navigator.goBack() }
            )
        }

        entry<Screen.Movies> {
            MoviesScreen(
                viewModel = moviesVm,
                onNavigateBack = { navigator.goBack() }
            )
        }

        entry<Screen.Notifications> {
            NotificationsScreen(
                viewModel = notificationsVm,
                onNavigateBack = { navigator.goBack() }
            )
        }

        entry<Screen.HealthConnect> {
            HealthConnectScreen(
                viewModel = healthConnectVm,
                onNavigateBack = { navigator.goBack() }
            )
        }

        entry<Screen.Music> {
            MusicScreen(
                viewModel = musicVm,
                onNavigateBack = { navigator.goBack() }
            )
        }

        entry<Screen.Language> {
            val state by settingsVm.uiState.collectAsState()
            LanguageSelectionScreen(
                currentLanguage = state.appLanguage,
                onLanguageSelected = { lang -> settingsVm.setLanguage(lang) },
                onNavigateBack = { navigator.goBack() }
            )
        }

        entry<Screen.IntegrationsCatalog> {
            IntegrationsCatalogScreen(
                viewModel = catalogVm,
                onNavigateBack = { navigator.goBack() },
                onNavigateToSettings = { navigator.navigate(Screen.Settings) }
            )
        }
    }

    BackHandler(enabled = navigator.canGoBack()) {
        navigator.goBack()
    }

    // Phones hide the bar on detail screens and while scrolling down; the rail stays on every
    // screen so wide layouts keep one-tap access to the top-level tabs.
    val showFloatingNav = isWideScreen || (!isDetailRoute && bottomNavVisible.value)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(bottomNavScrollConnection)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = if (isWideScreen) M3NavigationMetrics.RailContentStartPadding else 0.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = 1200.dp)
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
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
                        onNavigateToTimeline = { navigator.navigate(Screen.Timeline) },
                        onNavigateToNutrition = { navigator.navigate(Screen.Nutrition) },
                        onNavigateToBooks = { navigator.navigate(Screen.Books) },
                        onNavigateToMovies = { navigator.navigate(Screen.Movies) },
                        onNavigateToMusic = { navigator.navigate(Screen.Music) },
                        onNavigateToHealthConnect = { navigator.navigate(Screen.HealthConnect) },
                        onNavigateToNotifications = { navigator.navigate(Screen.Notifications) },
                        onNavigateToSettings = { navigator.navigate(Screen.Settings) },
                        onNavigateToIntegrationsCatalog = { navigator.navigate(Screen.IntegrationsCatalog) }
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
                            onNavigateToNutrition = { navigator.navigate(Screen.Nutrition) },
                            onNavigateToBooks = { navigator.navigate(Screen.Books) },
                            onNavigateToMovies = { navigator.navigate(Screen.Movies) }
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
                            onNavigateToNutrition = { navigator.navigate(Screen.Nutrition) },
                            onNavigateToBooks = { navigator.navigate(Screen.Books) },
                            onNavigateToMovies = { navigator.navigate(Screen.Movies) },
                            onNavigateToNotifications = { navigator.navigate(Screen.Notifications) },
                            onNavigateToHealthConnect = { navigator.navigate(Screen.HealthConnect) },
                            onNavigateToMusic = { navigator.navigate(Screen.Music) }
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
                            onNavigateToLanguage = { navigator.navigate(Screen.Language) },
                            onNavigateToIntegrationsCatalog = { navigator.navigate(Screen.IntegrationsCatalog) }
                        )
                    }
                }
            }

            NavDisplay(
                entries = navigationState.toDecoratedEntries(entryProvider),
                onBack = {
                    if (!navigator.goBack()) {
                        activity?.finish()
                    }
                },
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    val initialKey = initialState.key.toScreen()
                        ?: initialState.entries.lastOrNull()?.contentKey.toScreen()
                    val targetKey = targetState.key.toScreen()
                        ?: targetState.entries.lastOrNull()?.contentKey.toScreen()
                    val isTargetDetail = targetKey in detailScreens
                    val isInitialDetail = initialKey in detailScreens

                    if (isTargetDetail && !isInitialDetail) {
                        // Opening detail screen from root tab: detail slides in from right over warm root tab
                        (slideInHorizontally(
                            initialOffsetX = { fullWidth -> fullWidth },
                            animationSpec = androidx.compose.animation.core.spring(
                                dampingRatio = 0.85f,
                                stiffness = 380f,
                                visibilityThreshold = androidx.compose.ui.unit.IntOffset.VisibilityThreshold
                            )
                        ) togetherWith ExitTransition.None).apply {
                            targetContentZIndex = 1f
                        }
                    } else if (isTargetDetail && isInitialDetail) {
                        // Navigating between detail screens
                        (slideInHorizontally(
                            initialOffsetX = { fullWidth -> fullWidth },
                            animationSpec = androidx.compose.animation.core.spring(
                                dampingRatio = 0.85f,
                                stiffness = 380f,
                                visibilityThreshold = androidx.compose.ui.unit.IntOffset.VisibilityThreshold
                            )
                        ) togetherWith slideOutHorizontally(
                            targetOffsetX = { fullWidth -> -(fullWidth * 0.25f).toInt() },
                            animationSpec = androidx.compose.animation.core.spring(
                                dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                                stiffness = 400f,
                                visibilityThreshold = androidx.compose.ui.unit.IntOffset.VisibilityThreshold
                            )
                        )).apply {
                            targetContentZIndex = 1f
                        }
                    } else {
                        // Top-level tab switch (Overview <-> Timeline <-> Records <-> Settings)
                        EnterTransition.None togetherWith ExitTransition.None
                    }
                },
                popTransitionSpec = {
                    val initialKey = initialState.key.toScreen()
                        ?: initialState.entries.lastOrNull()?.contentKey.toScreen()
                    val targetKey = targetState.key.toScreen()
                        ?: targetState.entries.lastOrNull()?.contentKey.toScreen()
                    val isInitialDetail = initialKey in detailScreens
                    val isTargetDetail = targetKey in detailScreens

                    if (isInitialDetail && !isTargetDetail) {
                        // Popping detail screen back to root tab: detail slides out to right, revealing warm root tab underneath
                        (EnterTransition.None togetherWith slideOutHorizontally(
                            targetOffsetX = { fullWidth -> fullWidth },
                            animationSpec = androidx.compose.animation.core.spring(
                                dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                                stiffness = 400f,
                                visibilityThreshold = androidx.compose.ui.unit.IntOffset.VisibilityThreshold
                            )
                        )).apply {
                            targetContentZIndex = -1f
                        }
                    } else if (isInitialDetail && isTargetDetail) {
                        // Popping between detail screens
                        (slideInHorizontally(
                            initialOffsetX = { fullWidth -> -(fullWidth * 0.25f).toInt() },
                            animationSpec = androidx.compose.animation.core.spring(
                                dampingRatio = 0.85f,
                                stiffness = 380f,
                                visibilityThreshold = androidx.compose.ui.unit.IntOffset.VisibilityThreshold
                            )
                        ) togetherWith slideOutHorizontally(
                            targetOffsetX = { fullWidth -> fullWidth },
                            animationSpec = androidx.compose.animation.core.spring(
                                dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                                stiffness = 400f,
                                visibilityThreshold = androidx.compose.ui.unit.IntOffset.VisibilityThreshold
                            )
                        )).apply {
                            targetContentZIndex = -1f
                        }
                    } else {
                        EnterTransition.None togetherWith ExitTransition.None
                    }
                },
                predictivePopTransitionSpec = { _ ->
                    (EnterTransition.None togetherWith slideOutHorizontally(
                        targetOffsetX = { fullWidth -> fullWidth },
                        animationSpec = androidx.compose.animation.core.spring(
                            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                            stiffness = 400f,
                            visibilityThreshold = androidx.compose.ui.unit.IntOffset.VisibilityThreshold
                        )
                    )).apply {
                        targetContentZIndex = -1f
                    }
                }
            )
        }

        AnimatedVisibility(
            visible = showFloatingNav && !isWideScreen,
            enter = fadeIn(animationSpec = M3ExpressiveMotion.fastEffects()),
            exit = fadeOut(animationSpec = M3ExpressiveMotion.fastEffects()),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(M3NavigationMetrics.BottomFadeHeight)
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(
                                androidx.compose.ui.graphics.Color.Transparent,
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                                MaterialTheme.colorScheme.surface
                            )
                        )
                    )
            )
        }

        AnimatedVisibility(
            visible = showFloatingNav,
            enter = fadeIn(animationSpec = M3ExpressiveMotion.fastEffects()) + slideInVertically(
                initialOffsetY = { it },
                animationSpec = M3ExpressiveMotion.fastSpatial()
            ),
            exit = fadeOut(animationSpec = M3ExpressiveMotion.fastEffects()) + slideOutVertically(
                targetOffsetY = { it },
                animationSpec = M3ExpressiveMotion.fastSpatial()
            ),
            modifier = if (isWideScreen) Modifier.align(Alignment.CenterStart) else Modifier.align(Alignment.BottomCenter)
        ) {
            ExpressiveAdaptiveNavigation(
                navigator = navigator,
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
                isWideScreen = isWideScreen
            )
        }
    }
}

@Composable
private fun ExpressiveAdaptiveNavigation(
    navigator: Navigator,
    onScrollToTop: (Screen) -> Unit,
    onRefresh: (Screen) -> Unit,
    isWideScreen: Boolean,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var activeTabTapJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val haptic = LocalHapticFeedback.current
    val mainNavScreens = remember {
        listOf(Screen.Overview, Screen.Timeline, Screen.Records, Screen.Settings)
    }
    val topLevelRoute = navigator.state.topLevelRoute
    val currentRoute = navigator.currentRoute()
    // Bumped on a double tap of the active tab (refresh), so its icon bounces again.
    val refreshBounces = remember { mutableStateMapOf<Screen, Int>() }

    fun isSelected(screen: Screen): Boolean = when (screen) {
        Screen.Records -> topLevelRoute == Screen.Records || currentRoute in recordsSubScreens
        Screen.Settings -> topLevelRoute == Screen.Settings || currentRoute == Screen.Language
        else -> topLevelRoute == screen && currentRoute !in recordsSubScreens && currentRoute != Screen.Language
    }

    fun handleNavClick(screen: Screen, isSelected: Boolean) {
        haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)

        if (isSelected) {
            val currentStack = navigator.state.backStacks[screen]
            if (currentStack != null && currentStack.size > 1) {
                navigator.onReselect(screen)
            } else {
                if (activeTabTapJob?.isActive == true) {
                    activeTabTapJob?.cancel()
                    activeTabTapJob = null
                    refreshBounces[screen] = (refreshBounces[screen] ?: 0) + 1
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
            navigator.navigate(screen)
        }
    }

    // Selected tab: primary tonal pair, clearly stronger than the bar container.
    // Labels sit outside the indicator, so they use the regular surface text roles.
    val scheme = MaterialTheme.colorScheme
    val barItemColors = ShortNavigationBarItemDefaults.colors(
        selectedIconColor = scheme.onPrimaryContainer,
        selectedTextColorTopIconPosition = scheme.onSurface,
        selectedIndicatorColor = scheme.primaryContainer,
        unselectedIconColor = scheme.onSurfaceVariant,
        unselectedTextColor = scheme.onSurfaceVariant
    )
    val railItemColors = WideNavigationRailItemDefaults.colors(
        selectedIconColor = scheme.onPrimaryContainer,
        selectedTextColorTopIconPosition = scheme.onSurface,
        selectedIndicatorColor = scheme.primaryContainer,
        unselectedIconColor = scheme.onSurfaceVariant,
        unselectedTextColor = scheme.onSurfaceVariant
    )

    // Floating containers keep the app's pill look; the items are the official M3 navigation
    // items, so ripple, focus indicator, tab semantics and always-visible labels come built in.
    if (isWideScreen) {
        Box(
            modifier = modifier
                .fillMaxHeight()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(
                    start = M3NavigationMetrics.FloatingRailStartOffset,
                    top = 16.dp,
                    bottom = 16.dp
                ),
            contentAlignment = Alignment.CenterStart
        ) {
            Surface(
                shape = M3Shapes.CornerFull,
                // Same container tone as the phone bar so both forms read as one component.
                color = scheme.surfaceContainer,
                shadowElevation = 3.dp,
                modifier = Modifier
                    .width(M3NavigationMetrics.FloatingRailWidth)
                    .selectableGroup()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    mainNavScreens.forEach { screen ->
                        key(screen) {
                            val selected = isSelected(screen)
                            WideNavigationRailItem(
                                selected = selected,
                                onClick = { handleNavClick(screen, selected) },
                                icon = {
                                    BouncingNavIcon(
                                        screen = screen,
                                        selected = selected,
                                        bounceTrigger = refreshBounces[screen] ?: 0
                                    )
                                },
                                label = {
                                    Text(
                                        text = stringResource(screen.navLabelRes),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                railExpanded = false,
                                iconPosition = NavigationItemIconPosition.Top,
                                colors = railItemColors
                            )
                        }
                    }
                }
            }
        }
    } else {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = M3NavigationMetrics.FloatingBarBottomOffset),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = M3Shapes.CornerFull,
                // Darkest container tone: a greyish bar that keeps the theme's subtle tint and
                // stands apart from the page in both themes, like Google's floating toolbars.
                // (tonalElevation only tints the plain `surface` color, so it is not used.)
                color = scheme.surfaceContainer,
                shadowElevation = 3.dp,
                modifier = Modifier
                    .widthIn(max = M3NavigationMetrics.FloatingBarMaxWidth)
                    .fillMaxWidth()
                    .height(M3NavigationMetrics.FloatingBarHeight)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp)
                        .selectableGroup(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    mainNavScreens.forEach { screen ->
                        key(screen) {
                            val selected = isSelected(screen)
                            ShortNavigationBarItem(
                                selected = selected,
                                onClick = { handleNavClick(screen, selected) },
                                icon = {
                                    BouncingNavIcon(
                                        screen = screen,
                                        selected = selected,
                                        bounceTrigger = refreshBounces[screen] ?: 0
                                    )
                                },
                                label = {
                                    Text(
                                        text = stringResource(screen.navLabelRes),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                iconPosition = NavigationItemIconPosition.Top,
                                colors = barItemColors,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Navigation icon that lifts while its tab is selected: it grows to [SelectedIconScale] and moves
 * up by [SelectedIconLift] on a medium-bouncy spring, and stays there. This is the app's original
 * bottom bar animation (commit 73dc674). Each time [bounceTrigger] grows (a double tap that
 * refreshes the active tab) the lift plays again from rest.
 * With the system "remove animations" setting on, the icon jumps straight to its end state.
 */
@Composable
private fun BouncingNavIcon(
    screen: Screen,
    selected: Boolean,
    bounceTrigger: Int
) {
    val reducedMotion = M3ExpressiveMotion.isReducedMotion()
    // 0 = resting, 1 = lifted. Scale and offset share one spring, as in the original.
    val lift = remember { Animatable(if (selected) 1f else 0f) }
    val liftSpring = remember {
        spring<Float>(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        )
    }

    LaunchedEffect(selected) {
        val target = if (selected) 1f else 0f
        if (reducedMotion) lift.snapTo(target) else lift.animateTo(target, liftSpring)
    }

    // 0 means no double tap yet; each later value is one refresh tap.
    LaunchedEffect(bounceTrigger) {
        if (bounceTrigger > 0 && selected && !reducedMotion) {
            lift.snapTo(0f)
            lift.animateTo(1f, liftSpring)
        }
    }

    Icon(
        imageVector = if (selected) screen.selectedIcon else screen.icon,
        contentDescription = null,
        modifier = Modifier.graphicsLayer {
            val progress = lift.value
            val iconScale = 1f + (SelectedIconScale - 1f) * progress
            scaleX = iconScale
            scaleY = iconScale
            translationY = -SelectedIconLift.toPx() * progress
        }
    )
}

private const val SelectedIconScale = 1.15f
private val SelectedIconLift = 2.dp
