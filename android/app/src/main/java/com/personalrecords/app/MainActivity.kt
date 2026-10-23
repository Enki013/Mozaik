package com.personalrecords.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.personalrecords.app.core.theme.M3ExpressiveMotion
import com.personalrecords.app.core.theme.M3Shapes
import com.personalrecords.app.core.theme.PersonalRecordsTheme
import com.personalrecords.app.core.theme.labelLargeEmphasized
import com.personalrecords.app.core.theme.labelSmallEmphasized
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

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isWideScreen = configuration.screenWidthDp >= 600

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

    val showFloatingNav = currentRoute !in setOf(Screen.IntegrationsCatalog, Screen.Language)

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = if (isWideScreen && showFloatingNav) 88.dp else 0.dp)
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
            visible = showFloatingNav,
            enter = fadeIn(animationSpec = M3ExpressiveMotion.fastSpatial()) + slideInVertically(
                initialOffsetY = { it },
                animationSpec = M3ExpressiveMotion.fastSpatial()
            ),
            exit = fadeOut(animationSpec = M3ExpressiveMotion.fastSpatial()) + slideOutVertically(
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
    val secondaryContainerColor = MaterialTheme.colorScheme.secondaryContainer

    val topLevelRoute = navigator.state.topLevelRoute
    val currentRoute = navigator.currentRoute()

    fun handleNavClick(screen: Screen, isSelected: Boolean) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)

        if (isSelected) {
            val currentStack = navigator.state.backStacks[screen]
            if (currentStack != null && currentStack.size > 1) {
                navigator.onReselect(screen)
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
            navigator.navigate(screen)
        }
    }

    if (isWideScreen) {
        // Floating NavRail — M3 Expressive Vertical Floating Toolbar (Overview, Timeline, Records, Settings)
        Box(
            modifier = modifier
                .fillMaxHeight()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(start = 12.dp, top = 16.dp, bottom = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
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
                                Screen.Records -> topLevelRoute == Screen.Records || currentRoute in recordsSubScreens
                                Screen.Settings -> topLevelRoute == Screen.Settings || currentRoute == Screen.Language
                                else -> topLevelRoute == screen && currentRoute !in recordsSubScreens && currentRoute != Screen.Language && currentRoute != Screen.IntegrationsCatalog
                            }

                            val interactionSource = remember { MutableInteractionSource() }
                            val isPressed by interactionSource.collectIsPressedAsState()

                            val selectionProgressState = animateFloatAsState(
                                targetValue = if (selected) 1f else 0f,
                                animationSpec = androidx.compose.animation.core.spring(
                                    dampingRatio = 0.85f,
                                    stiffness = 380f
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
                                animationSpec = M3ExpressiveMotion.defaultSpatial(),
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
        }
    } else {
        // Floating Bottom Bar — M3 Expressive Horizontal Floating Toolbar (Overview, Timeline, Records, Settings)
        Box(
            modifier = modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = M3Shapes.CornerFull,
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 3.dp,
                shadowElevation = 6.dp,
                modifier = Modifier.height(64.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    mainNavScreens.forEach { screen ->
                        key(screen) {
                            val selected = when (screen) {
                                Screen.Records -> topLevelRoute == Screen.Records || currentRoute in recordsSubScreens
                                Screen.Settings -> topLevelRoute == Screen.Settings || currentRoute == Screen.Language
                                else -> topLevelRoute == screen && currentRoute !in recordsSubScreens && currentRoute != Screen.Language && currentRoute != Screen.IntegrationsCatalog
                            }

                            val interactionSource = remember { MutableInteractionSource() }
                            val isPressed by interactionSource.collectIsPressedAsState()

                            val selectionProgressState = animateFloatAsState(
                                targetValue = if (selected) 1f else 0f,
                                animationSpec = androidx.compose.animation.core.spring(
                                    dampingRatio = 0.85f,
                                    stiffness = 380f
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
                                animationSpec = M3ExpressiveMotion.defaultSpatial(),
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
                                        val hPadPx = (10.dp.toPx() + (4.dp.toPx() * p)).roundToInt()
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
                                    Spacer(modifier = Modifier.width(6.dp))
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
        }
    }
}
