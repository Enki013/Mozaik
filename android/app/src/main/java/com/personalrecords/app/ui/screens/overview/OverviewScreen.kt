package com.personalrecords.app.ui.screens.overview

import androidx.activity.compose.ReportDrawnWhen

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.carousel.CarouselDefaults
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.personalrecords.app.R
import com.personalrecords.app.PersonalRecordsApp
import com.personalrecords.app.core.integrations.PluginRegistry
import com.personalrecords.app.core.theme.*
import com.personalrecords.app.ui.components.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(
    viewModel: OverviewViewModel,
    scrollToTopFlow: kotlinx.coroutines.flow.Flow<Unit>? = null,
    refreshFlow: kotlinx.coroutines.flow.Flow<Unit>? = null,
    onNavigateToTimeline: () -> Unit,
    onNavigateToNutrition: () -> Unit = {},
    onNavigateToBooks: () -> Unit = {},
    onNavigateToMovies: () -> Unit = {},
    onNavigateToMusic: () -> Unit = {},
    onNavigateToHealthConnect: () -> Unit = {},
    onNavigateToNotifications: () -> Unit = {},
    onNavigateToSettings: (() -> Unit)? = null,
    onNavigateToIntegrationsCatalog: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Tells the system when real content is on screen, so startup benchmarks can measure
    // time to full display (not just the first frame with a loading indicator).
    ReportDrawnWhen { state.dashboard != null || state.error != null }
    val preferences = remember { PersonalRecordsApp.instance.preferences }
    val hiddenStatsCards by preferences.hiddenStatsCards.collectAsStateWithLifecycle(initialValue = emptySet())
    val coroutineScope = rememberCoroutineScope()
    var showStatsCustomization by rememberSaveable { mutableStateOf(false) }
    val overviewStatCards = listOf(
        StatCardItem(
            id = "overview_nutrition",
            title = stringResource(R.string.title_nutrition),
            description = stringResource(R.string.overview_hero_status_nutrition),
            icon = Icons.Rounded.Restaurant,
            iconTint = recordCategoryColors(RecordCategory.Nutrition).accent
        ),
        StatCardItem(
            id = "overview_books",
            title = stringResource(R.string.title_books),
            description = stringResource(R.string.overview_hero_status_books),
            icon = Icons.AutoMirrored.Rounded.MenuBook,
            iconTint = recordCategoryColors(RecordCategory.Books).accent
        ),
        StatCardItem(
            id = "overview_movies",
            title = stringResource(R.string.title_movies),
            description = stringResource(R.string.overview_hero_status_movies),
            icon = Icons.Rounded.Movie,
            iconTint = recordCategoryColors(RecordCategory.Movies).accent
        ),
        StatCardItem(
            id = "overview_music",
            title = stringResource(R.string.title_music),
            description = stringResource(R.string.overview_hero_status_music),
            icon = Icons.Rounded.MusicNote,
            iconTint = recordCategoryColors(RecordCategory.Music).accent
        ),
        StatCardItem(
            id = "overview_health",
            title = stringResource(R.string.title_health_connect),
            description = stringResource(R.string.overview_hero_status_health),
            icon = Icons.Rounded.Favorite,
            iconTint = recordCategoryColors(RecordCategory.Health).accent
        ),
        StatCardItem(
            id = "overview_notifications",
            title = stringResource(R.string.title_notifications),
            description = stringResource(R.string.overview_hero_status_notifications),
            icon = Icons.Rounded.Notifications,
            iconTint = recordCategoryColors(RecordCategory.Notifications).accent
        )
    )
    val statCardOrder = rememberStatCardOrder("overview", overviewStatCards.map { it.id })
    val visibleOverviewCardCount = overviewStatCards.count { it.id !in hiddenStatsCards }
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedActivity by remember { mutableStateOf<com.personalrecords.app.core.network.model.ActivityItemDto?>(null) }
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
    // Keyed on the app locale: the saved language loads after the first frame, so a
    // key-less remember would keep the date in the system language.
    val appLocale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val formattedDate = remember(appLocale) {
        try {
            val formatter = DateTimeFormatter.ofPattern("EEEE, d MMMM", appLocale)
            LocalDate.now().format(formatter)
        } catch (_: Exception) {
            ""
        }
    }
    val statsPagerState = rememberCarouselState(
        initialItem = 0,
        itemCount = { visibleOverviewCardCount.coerceAtLeast(1) }
    )

    LaunchedEffect(visibleOverviewCardCount) {
        if (visibleOverviewCardCount > 0 && statsPagerState.currentItem >= visibleOverviewCardCount) {
            statsPagerState.scrollToItem(0)
        }
    }

    val messageContext = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(state.syncMessage) {
        state.syncMessage?.let {
            snackbarHostState.showSnackbar(it.resolve(messageContext))
            viewModel.clearSyncMessage()
        }
    }

    val listState = rememberLazyListState()
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            ExpressivePageTopAppBar(
                title = stringResource(R.string.app_name),
                subtitle = formattedDate.takeIf { it.isNotBlank() },
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = { showStatsCustomization = true }) {
                        BadgedBox(
                            badge = {
                                if (overviewStatCards.any { it.id in hiddenStatsCards }) {
                                    Badge(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(6.dp)
                                    )
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DashboardCustomize,
                                contentDescription = stringResource(R.string.stats_customization_title),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets.statusBars,
        snackbarHost = {
            SnackbarHost(
                snackbarHostState,
                modifier = Modifier
                    .padding(bottom = floatingNavSnackbarPadding())
            )
        }
    ) { innerPadding ->
        var isRefreshing by remember { mutableStateOf(false) }
        LaunchedEffect(state.isLoading) {
            if (!state.isLoading) isRefreshing = false
        }

        if (scrollToTopFlow != null) {
            LaunchedEffect(scrollToTopFlow) {
                scrollToTopFlow.collect {
                    listState.expressiveScrollToTop()
                }
            }
        }

        if (refreshFlow != null) {
            LaunchedEffect(refreshFlow) {
                refreshFlow.collect {
                    isRefreshing = true
                    viewModel.loadData()
                    if (!listState.isScrollInProgress) {
                        listState.expressiveScrollToTop()
                    }
                }
            }
        }

        ExpressivePullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { isRefreshing = true; viewModel.loadData() },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (state.isLoading && state.dashboard == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    AppContainedLoadingIndicator(size = 48.dp)
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = M3Spacing.Large),
                    contentPadding = PaddingValues(
                        top = 8.dp,
                        bottom = floatingBottomNavigationContentPadding()
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Server Connection Error Banner
                    if (state.error != null && state.dashboard == null) {
                        item(key = "overview_connection_error", contentType = "error_banner") {
                            ExpressiveErrorBanner(
                                title = stringResource(R.string.overview_connection_error_title),
                                message = stringResource(R.string.overview_connection_error_desc),
                                icon = Icons.Rounded.CloudOff,
                                onRetry = { viewModel.loadData() },
                                onClick = onNavigateToSettings,
                                modifier = expressiveItemModifier()
                            )
                        }
                    }

                    // Top Metrics: M3 Expressive Hero Stats Carousel
                    item(key = "overview_metrics", contentType = "metrics") {
                        val counts = state.dashboard?.counts ?: emptyMap()
                        val today = state.dashboard?.today ?: emptyMap()
                        val todayCalInt = (today["fatsecret_calories"]?.toString()?.replace("\"", "")?.toDoubleOrNull() ?: 0.0).toInt()
                        val todayEntriesInt = today["fatsecret_entry_count"]?.toString()?.replace("\"", "")?.toIntOrNull()
                            ?: today["entries_count"]?.toString()?.replace("\"", "")?.toIntOrNull() ?: 0
                        val targetCal = state.dashboard?.calorieTarget ?: 2000
                        val calProgress = (todayCalInt.toFloat() / targetCal).coerceIn(0f, 1f)
                        val remainingCal = targetCal - todayCalInt

                        val booksCount = counts["goodreads_books"] ?: counts["books"] ?: 0
                        val filmsCount = counts["letterboxd_films"] ?: counts["films"] ?: 0
                        val musicCount = counts["music_scrobbles"] ?: counts["music"] ?: 0
                        val todayMusicCount = today["music_scrobbles"]?.toString()?.replace("\"", "")?.toIntOrNull()
                            ?: today["music"]?.toString()?.replace("\"", "")?.toIntOrNull() ?: 0

                        val latestBookActivity = remember(state.recentActivities) {
                            state.recentActivities.firstOrNull { it.source == "goodreads" || it.type == "book" }
                        }
                        val latestFilmActivity = remember(state.recentActivities) {
                            state.recentActivities.firstOrNull { it.source == "letterboxd" || it.type == "film" }
                        }
                        val latestMusic = remember(state.recentActivities) {
                            state.recentActivities.firstOrNull { it.source == "music" || it.source == "lastfm" || it.type == "music" || it.type == "scrobble" }
                        }
                        val latestMeal = remember(state.recentActivities) {
                            state.recentActivities.firstOrNull { it.source == "fatsecret" || it.type == "meal" }
                        }

                        val resolvedFilmTitle = state.latestFilm?.displayTitle?.ifBlank { null }
                            ?: latestFilmActivity?.title
                            ?: stringResource(R.string.overview_hero_no_recent_film)
                        val resolvedFilmSubtitle = state.latestFilm?.let { f ->
                            if (f.safeYear > 0) f.safeYear.toString() else null
                        } ?: latestFilmActivity?.subtitle?.takeIf { it.isNotBlank() }
                        val resolvedFilmPoster = state.latestFilm?.image_url ?: latestFilmActivity?.image_url

                        val resolvedBookTitle = state.latestBook?.title?.ifBlank { null }
                            ?: latestBookActivity?.title
                            ?: stringResource(R.string.overview_hero_no_recent_book)
                        val resolvedBookAuthor = state.latestBook?.author?.ifBlank { null }
                            ?: latestBookActivity?.subtitle
                        val resolvedBookCover = state.latestBook?.image_url ?: latestBookActivity?.image_url
                        val resolvedMealTitle = state.latestMeal?.displayName?.ifBlank { null }
                            ?: latestMeal?.title
                            ?: stringResource(R.string.overview_hero_no_recent_meal)
                        val kcalUnit = stringResource(R.string.overview_hero_unit_kcal)
                        val resolvedMealSubtitle = state.latestMeal?.let { m ->
                            val parts = listOfNotNull(m.meal.ifBlank { null }, "${m.calories.toInt()} $kcalUnit")
                            if (parts.isNotEmpty()) parts.joinToString(" · ") else null
                        } ?: latestMeal?.subtitle?.takeIf { it.isNotBlank() }

                        val nutritionRemainingStr = if (remainingCal >= 0) {
                            stringResource(R.string.overview_hero_remaining, remainingCal)
                        } else {
                            stringResource(R.string.overview_hero_exceeded, -remainingCal)
                        }
                        val nutritionTargetStr = stringResource(R.string.overview_hero_target_context, targetCal)

                        val nutritionPills = listOf(
                            HeroMetricPillData(
                                value = "$remainingCal $kcalUnit",
                                label = stringResource(R.string.overview_hero_pill_remaining)
                            ),
                            HeroMetricPillData(
                                value = java.text.NumberFormat.getPercentInstance(appLocale).format(calProgress.toDouble()),
                                label = stringResource(R.string.overview_hero_pill_goal)
                            )
                        )

                        val nutritionSlide = HeroCarouselSlide(
                            id = "nutrition",
                            title = stringResource(R.string.title_nutrition),
                            provider = "FatSecret",
                            count = todayCalInt.toString(),
                            unitText = stringResource(R.string.overview_hero_unit_kcal),
                            secondaryText = "$nutritionRemainingStr · $nutritionTargetStr",
                            progress = calProgress,
                            progressLabel = stringResource(R.string.overview_hero_goal_pct, (calProgress * 100).toInt()),
                            metricPills = nutritionPills,
                            spotlight = HeroSpotlightData(
                                caption = stringResource(R.string.overview_hero_latest_added),
                                title = resolvedMealTitle,
                                subtitle = resolvedMealSubtitle,
                                fallbackIcon = Icons.Rounded.Restaurant
                            ),
                            containerColor = recordCategoryColors(RecordCategory.Nutrition).container,
                            contentColor = recordCategoryColors(RecordCategory.Nutrition).onContainer,
                            accentColor = recordCategoryColors(RecordCategory.Nutrition).accent,
                            onClick = onNavigateToNutrition
                        )

                        val booksReadingVal = if (state.booksStats.currentlyReadingCount > 0) "${state.booksStats.currentlyReadingCount}" else "0"
                        val booksMiddlePill = if (state.booksStats.totalPages > 0) {
                            val pagesStr = if (state.booksStats.totalPages >= 1000) {
                                String.format(appLocale, "%.1fk", state.booksStats.totalPages / 1000f)
                            } else {
                                state.booksStats.totalPages.toString()
                            }
                            HeroMetricPillData(
                                value = pagesStr,
                                label = stringResource(R.string.overview_hero_pill_pages)
                            )
                        } else {
                            HeroMetricPillData(
                                value = "—",
                                label = stringResource(R.string.overview_hero_pill_pages)
                            )
                        }
                        val booksRatingVal = if (state.booksStats.avgRating > 0.0) {
                            String.format(appLocale, "%.1f", state.booksStats.avgRating)
                        } else "—"

                        val booksPills = listOf(
                            HeroMetricPillData(
                                value = booksReadingVal,
                                label = stringResource(R.string.overview_hero_pill_reading)
                            ),
                            booksMiddlePill,
                            HeroMetricPillData(
                                value = booksRatingVal,
                                label = stringResource(R.string.overview_hero_pill_rating),
                                icon = Icons.Rounded.Star
                            )
                        )

                        val booksSlide = HeroCarouselSlide(
                            id = "books",
                            title = stringResource(R.string.title_books),
                            provider = "Goodreads",
                            count = booksCount.toString(),
                            unitText = stringResource(R.string.overview_hero_unit_books),
                            secondaryText = stringResource(
                                R.string.overview_hero_book_read_summary,
                                state.booksStats.readCount
                            ),
                            metricPills = booksPills,
                            spotlight = HeroSpotlightData(
                                caption = stringResource(R.string.overview_hero_latest_added),
                                title = resolvedBookTitle,
                                subtitle = resolvedBookAuthor,
                                imageUrl = resolvedBookCover,
                                fallbackIcon = Icons.AutoMirrored.Rounded.MenuBook
                            ),
                            containerColor = recordCategoryColors(RecordCategory.Books).container,
                            contentColor = recordCategoryColors(RecordCategory.Books).onContainer,
                            accentColor = recordCategoryColors(RecordCategory.Books).accent,
                            onClick = onNavigateToBooks
                        )

                        val filmsMonthVal = if (state.filmsStats.thisMonthCount > 0) "${state.filmsStats.thisMonthCount}" else "0"
                        val filmsRewatchVal = if (state.filmsStats.rewatchCount > 0) "${state.filmsStats.rewatchCount}" else "0"
                        val filmsRatingVal = if (state.filmsStats.avgRating > 0.0) {
                            String.format(appLocale, "%.1f", state.filmsStats.avgRating)
                        } else "—"

                        val moviesPills = listOf(
                            HeroMetricPillData(
                                value = filmsMonthVal,
                                label = stringResource(R.string.overview_hero_pill_this_month)
                            ),
                            HeroMetricPillData(
                                value = filmsRewatchVal,
                                label = stringResource(R.string.overview_hero_pill_rewatch)
                            ),
                            HeroMetricPillData(
                                value = filmsRatingVal,
                                label = stringResource(R.string.overview_hero_pill_rating),
                                icon = Icons.Rounded.Star
                            )
                        )

                        val moviesSlide = HeroCarouselSlide(
                            id = "movies",
                            title = stringResource(R.string.title_movies),
                            provider = "Letterboxd",
                            count = filmsCount.toString(),
                            unitText = stringResource(R.string.overview_hero_unit_films),
                            secondaryText = "",
                            metricPills = moviesPills,
                            spotlight = HeroSpotlightData(
                                caption = stringResource(R.string.overview_hero_latest_added),
                                title = resolvedFilmTitle,
                                subtitle = resolvedFilmSubtitle,
                                imageUrl = resolvedFilmPoster,
                                fallbackIcon = Icons.Rounded.Movie
                            ),
                            containerColor = recordCategoryColors(RecordCategory.Movies).container,
                            contentColor = recordCategoryColors(RecordCategory.Movies).onContainer,
                            accentColor = recordCategoryColors(RecordCategory.Movies).accent,
                            onClick = onNavigateToMovies
                        )

                        val resolvedMusicTitle = state.latestMusic?.track?.ifBlank { null }
                            ?: latestMusic?.title
                            ?: stringResource(R.string.overview_hero_no_recent_music)
                        val resolvedMusicSubtitle = state.latestMusic?.let { s ->
                            val parts = listOfNotNull(s.artist.ifBlank { null }, s.album.ifBlank { null })
                            if (parts.isNotEmpty()) parts.joinToString(" • ") else null
                        } ?: latestMusic?.subtitle?.takeIf { it.isNotBlank() }
                        val resolvedMusicImage = state.latestMusic?.imageUrl?.ifBlank { null }
                            ?: latestMusic?.image_url

                        val musicArtistsVal = if (state.musicStats.uniqueArtists > 0) "${state.musicStats.uniqueArtists}" else "—"
                        val musicAlbumsVal = if (state.musicStats.uniqueAlbums > 0) "${state.musicStats.uniqueAlbums}" else "—"
                        val musicWeekVal = if (state.musicStats.weekScrobbles > 0) "${state.musicStats.weekScrobbles}" else "—"

                        val musicPills = listOf(
                            HeroMetricPillData(
                                value = musicArtistsVal,
                                label = stringResource(R.string.overview_hero_pill_artists)
                            ),
                            HeroMetricPillData(
                                value = musicAlbumsVal,
                                label = stringResource(R.string.overview_hero_pill_albums)
                            ),
                            HeroMetricPillData(
                                value = musicWeekVal,
                                label = stringResource(R.string.overview_hero_pill_this_week)
                            )
                        )

                        val musicSlide = HeroCarouselSlide(
                            id = "music",
                            title = stringResource(R.string.title_music),
                            provider = "Last.fm",
                            count = musicCount.toString(),
                            unitText = stringResource(R.string.overview_hero_unit_tracks),
                            secondaryText = stringResource(R.string.overview_hero_today_music, todayMusicCount),
                            metricPills = musicPills,
                            spotlight = HeroSpotlightData(
                                caption = stringResource(R.string.overview_hero_latest_added),
                                title = resolvedMusicTitle,
                                subtitle = resolvedMusicSubtitle,
                                imageUrl = resolvedMusicImage,
                                fallbackIcon = Icons.Rounded.MusicNote
                            ),
                            containerColor = recordCategoryColors(RecordCategory.Music).container,
                            contentColor = recordCategoryColors(RecordCategory.Music).onContainer,
                            accentColor = recordCategoryColors(RecordCategory.Music).accent,
                            onClick = onNavigateToMusic
                        )

                        val health = state.healthSummary
                        val healthRecordCount = health.total_records.takeIf { it > 0 }
                            ?: state.dashboard?.counts?.get("health_records")
                            ?: 0
                        val healthSteps = health.today_steps.toInt().coerceAtLeast(0)
                        val healthHeartRate = health.latest_heart_rate_bpm.toInt().takeIf { it > 0 }?.toString() ?: "—"
                        val healthSleepHours = if (health.last_sleep_seconds > 0) {
                            String.format(appLocale, "%.1f h", health.last_sleep_seconds / 3600.0)
                        } else "—"
                        val healthActiveCalories = health.today_active_calories.toInt().takeIf { it > 0 }?.toString() ?: "—"
                        val healthRecord = state.latestHealthRecord
                        val healthSlide = HeroCarouselSlide(
                            id = "health",
                            title = stringResource(R.string.title_health_connect),
                            provider = "",
                            count = healthSteps.toString(),
                            unitText = stringResource(R.string.overview_hero_unit_steps),
                            secondaryText = stringResource(R.string.overview_health_records_summary, healthRecordCount),
                            metricPills = listOf(
                                HeroMetricPillData(healthHeartRate, stringResource(R.string.overview_hero_pill_heart_rate)),
                                HeroMetricPillData(healthSleepHours, stringResource(R.string.overview_hero_pill_sleep)),
                                HeroMetricPillData(healthActiveCalories, stringResource(R.string.overview_hero_pill_active_kcal))
                            ),
                            spotlight = HeroSpotlightData(
                                caption = stringResource(R.string.overview_hero_latest_added),
                                title = healthRecord?.title?.ifBlank { null }
                                    ?: stringResource(R.string.overview_health_no_recent_record),
                                subtitle = healthRecord?.formattedDate?.takeIf { it.isNotBlank() },
                                fallbackIcon = Icons.AutoMirrored.Rounded.DirectionsWalk
                            ),
                            containerColor = recordCategoryColors(RecordCategory.Health).container,
                            contentColor = recordCategoryColors(RecordCategory.Health).onContainer,
                            accentColor = recordCategoryColors(RecordCategory.Health).accent,
                            onClick = onNavigateToHealthConnect
                        )

                        val notificationStats = state.notificationsStats
                        val latestNotification = state.latestNotification
                        val notificationsSlide = HeroCarouselSlide(
                            id = "notifications",
                            title = stringResource(R.string.title_notifications),
                            provider = stringResource(R.string.overview_notifications_provider),
                            count = notificationStats.totalCount.toString(),
                            unitText = stringResource(R.string.overview_hero_unit_notifications),
                            secondaryText = "",
                            metricPills = listOf(
                                HeroMetricPillData(notificationStats.todayCount.toString(), stringResource(R.string.overview_hero_pill_today)),
                                HeroMetricPillData(notificationStats.appCount.toString(), stringResource(R.string.overview_hero_pill_apps))
                            ),
                            spotlight = HeroSpotlightData(
                                caption = stringResource(R.string.overview_hero_latest_added),
                                title = latestNotification?.displayTitle?.ifBlank { null }
                                    ?: stringResource(R.string.overview_notifications_no_recent),
                                subtitle = latestNotification?.let { notification ->
                                    listOf(notification.displayAppName, notification.displayBody)
                                        .filter { it.isNotBlank() }
                                        .joinToString(" · ")
                                        .takeIf { it.isNotBlank() }
                                },
                                fallbackIcon = Icons.Rounded.Apps
                            ),
                            containerColor = recordCategoryColors(RecordCategory.Notifications).container,
                            contentColor = recordCategoryColors(RecordCategory.Notifications).onContainer,
                            accentColor = recordCategoryColors(RecordCategory.Notifications).accent,
                            onClick = onNavigateToNotifications
                        )

                        val slides = listOf(nutritionSlide, booksSlide, moviesSlide, musicSlide, healthSlide, notificationsSlide)
                            .filterNot { slide -> "overview_${slide.id}" in hiddenStatsCards }
                            .sortedBy { slide -> statCardOrder.orderedIds.indexOf("overview_${slide.id}") }

                        Column(
                            modifier = expressiveItemModifier()
                        ) {
                            if (slides.isEmpty()) {
                                Surface(
                                    shape = MaterialTheme.shapes.extraLargeIncreased,
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.overview_stats_all_hidden),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(1f)
                                        )
                                        FilledTonalButton(
                                            onClick = { showStatsCustomization = true },
                                            shapes = expressiveButtonShapes()
                                        ) {
                                            Text(
                                                text = stringResource(R.string.overview_stats_show_cards),
                                                style = MaterialTheme.typography.labelMediumEmphasized
                                            )
                                        }
                                    }
                                }
                            } else {
                                // M3 multi-browse carousel: cards morph their width as they move
                                // between the hero slot and the small slot at the edge. Unlike the
                                // uncontained carousel, it composes the cards next to the visible
                                // ones ahead of time, so the next card never pops in.
                                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                                    // The carousel fits hero + spacing + small card into the width,
                                    // so ask for exactly that; otherwise it shrinks the hero card.
                                    val peek = if (slides.size > 1) HeroCardPeek + HeroCardSpacing else 0.dp
                                    val heroItemWidth = (maxWidth - peek).coerceAtMost(840.dp)
                                    HorizontalMultiBrowseCarousel(
                                        state = statsPagerState,
                                        preferredItemWidth = heroItemWidth,
                                        itemSpacing = HeroCardSpacing,
                                        minSmallItemWidth = HeroCardPeek,
                                        maxSmallItemWidth = HeroCardPeek,
                                        flingBehavior = CarouselDefaults.singleAdvanceFlingBehavior(statsPagerState),
                                        modifier = Modifier.fillMaxWidth()
                                    ) { itemIndex ->
                                        OverviewHeroCard(
                                            slide = slides[itemIndex],
                                            modifier = Modifier
                                                .maskClip(MaterialTheme.shapes.extraLargeIncreased)
                                                // The carousel centers its mask on the card, so a narrow
                                                // card would show its middle and reveal the title only
                                                // as it widens. Pin the card's leading edge to the mask
                                                // instead; the shift is zero once the card is full size.
                                                .graphicsLayer {
                                                    translationX = carouselItemDrawInfo.maskRect.left
                                                }
                                        )
                                    }
                                }

                                // M3 Expressive Morphing Pager Indicator
                                if (slides.size > 1) {
                                    val pageIndicatorDescription = stringResource(
                                        R.string.overview_stats_page_indicator,
                                        statsPagerState.currentItem + 1,
                                        slides.size
                                    )
                                    // The dots are not interactive, so the row is only as tall as a dot:
                                    // 12dp from the card, and the list's section spacing below.
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 12.dp)
                                            .height(6.dp)
                                            .semantics {
                                                contentDescription = pageIndicatorDescription
                                            },
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        slides.forEachIndexed { index, slide ->
                                            val isSelected = statsPagerState.currentItem == index
                                            val animatedWidth by animateDpAsState(
                                                targetValue = if (isSelected) 22.dp else 6.dp,
                                                animationSpec = M3ExpressiveMotion.fastSpatial(),
                                                label = "pager_ind_w"
                                            )
                                            val indicatorColor by animateColorAsState(
                                                targetValue = if (isSelected) slide.accentColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                                animationSpec = M3ExpressiveMotion.fastEffects(),
                                                label = "pager_ind_c"
                                            )

                                            Box(
                                                modifier = Modifier
                                                    .padding(horizontal = 2.dp)
                                                    .width(animatedWidth)
                                                    .height(6.dp)
                                                    .clip(CircleShape)
                                                    .background(indicatorColor)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Entegrasyonlar & Senkronizasyon (Grouped Containers + Sync All Hero Button)
                    item(key = "overview_integrations", contentType = "integrations") {
                        val activePlugins = remember(state.enabledPluginIds) {
                            PluginRegistry.defaultPlugins
                                .filter { state.enabledPluginIds.contains(it.id) }
                                .sortedBy { it.name.lowercase() }
                        }

                        Column(modifier = expressiveItemModifier().fillMaxWidth()) {
                            // Section Header with Title + "Sync All" Hero Action + Add Catalog Button
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.overview_integrations_title),
                                        style = MaterialTheme.typography.titleMediumEmphasized,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = stringResource(R.string.overview_integrations_subtitle),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    val isSyncingAll = state.isSyncing == "all"
                                    val syncAllAccessibilityLabel = stringResource(R.string.action_sync_all)
                                    FilledTonalButton(
                                        onClick = { viewModel.syncAll() },
                                        enabled = state.isSyncing == null,
                                        shapes = expressiveButtonShapes(),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        modifier = Modifier

                                            .semantics {
                                                contentDescription = syncAllAccessibilityLabel
                                            }
                                    ) {
                                        if (isSyncingAll) {
                                            ExpressiveLoadingIndicator(
                                                size = 14.dp,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Rounded.Sync,
                                                contentDescription = null,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = stringResource(R.string.overview_sync_all_short),
                                            style = MaterialTheme.typography.labelMediumEmphasized
                                        )
                                    }

                                    FilledTonalIconButton(
                                        onClick = onNavigateToIntegrationsCatalog,
                                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Add,
                                            contentDescription = stringResource(R.string.catalog_add_integration),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            if (activePlugins.isEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(18.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.catalog_empty_active),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        FilledTonalButton(
                                            onClick = onNavigateToIntegrationsCatalog,
                                            shapes = expressiveButtonShapes()
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Add,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = stringResource(R.string.catalog_add_integration),
                                                style = MaterialTheme.typography.labelMedium
                                            )
                                        }
                                    }
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                                    activePlugins.forEachIndexed { index, plugin ->
                                        val syncKey = plugin.syncServiceKey ?: plugin.id
                                        val isItemSyncing = state.isSyncing == syncKey || state.isSyncing == "all"
                                        val desc = if (plugin.shortDescRes != 0) stringResource(plugin.shortDescRes) else plugin.shortDescription
                                        ExpressiveListRow(
                                            index = index,
                                            count = activePlugins.size,
                                            addLeadingGap = false,
                                            onClick = {
                                                if (state.isSyncing == null) {
                                                    viewModel.syncService(syncKey)
                                                }
                                            },
                                            leadingContent = {
                                                Box(
                                                    modifier = Modifier
                                                        .size(40.dp)
                                                        .clip(MaterialTheme.shapes.medium)
                                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = plugin.icon,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            },
                                            trailingContent = {
                                                if (isItemSyncing) {
                                                    ExpressiveLoadingIndicator(
                                                        size = 16.dp,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                } else {
                                                    ExpressiveStatusBadge(
                                                        text = stringResource(R.string.plugin_status_active),
                                                        tone = StatusTone.Active
                                                    )
                                                }
                                            },
                                            supportingContent = if (desc.isNotBlank()) {
                                                { Text(text = desc, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                            } else null
                                        ) {
                                            Text(text = plugin.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Son Aktiviteler Grouped Section (Grouped Containers + See All Action)
                    item(key = "overview_recent_activities", contentType = "recent_activities") {
                        Column(modifier = expressiveItemModifier().fillMaxWidth()) {
                            // Header Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = stringResource(R.string.overview_recent_activities_title),
                                        style = MaterialTheme.typography.titleMediumEmphasized,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = stringResource(R.string.overview_recent_activities_subtitle),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                TextButton(
                                    onClick = onNavigateToTimeline,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.action_see_all),
                                            style = MaterialTheme.typography.labelLargeEmphasized,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Grouped Container List
                            if (state.isLoading && state.recentActivities.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AppContainedLoadingIndicator(size = 36.dp)
                                }
                            } else if (state.recentActivities.isEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(24.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = stringResource(R.string.overview_no_activities),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                                    val activitiesToShow = state.recentActivities.take(6)
                                    activitiesToShow.forEachIndexed { index, item ->
                                        key("${item.source}_${item.id.ifBlank { "${item.date}_${item.title}" }}") {
                                            ActivityRowItem(
                                                item = item,
                                                onClick = { selectedActivity = item },
                                                index = index,
                                                count = activitiesToShow.size
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item(key = "overview_bottom_spacer", contentType = "spacer") {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }

    if (showStatsCustomization) {
        StatsCustomizationBottomSheet(
            sheetTitle = stringResource(R.string.stats_customization_title),
            cards = statCardOrder.sort(overviewStatCards),
            onReorder = { ids -> statCardOrder.setOrder(ids) },
            hiddenCardIds = hiddenStatsCards,
            onToggleCard = { cardId, visible ->
                coroutineScope.launch {
                    preferences.toggleStatCardVisibility(cardId, visible)
                }
            },
            onResetDefault = {
                coroutineScope.launch {
                    preferences.resetHiddenStatsCards(overviewStatCards.map { it.id })
                    statCardOrder.reset()
                }
            },
            onDismissRequest = { showStatsCustomization = false }
        )
    }

    selectedActivity?.let { activity ->
        ActivityItemDetailBottomSheet(
            item = activity,
            onDismiss = { selectedActivity = null }
        )
    }
}

private fun overviewRecordCategory(category: String): RecordCategory = when (category) {
    "nutrition" -> RecordCategory.Nutrition
    "books" -> RecordCategory.Books
    "movies" -> RecordCategory.Movies
    "music" -> RecordCategory.Music
    "health" -> RecordCategory.Health
    else -> RecordCategory.Notifications
}

private data class HeroSpotlightData(
    val caption: String,
    val title: String,
    val subtitle: String? = null,
    val imageUrl: String? = null,
    val fallbackIcon: ImageVector
)

@Immutable
private data class HeroMetricPillData(
    val value: String,
    val label: String,
    val icon: ImageVector? = null
)

private data class HeroCarouselSlide(
    val id: String,
    val title: String,
    val provider: String,
    val count: String,
    val unitText: String,
    val secondaryText: String,
    val progress: Float? = null,
    val progressLabel: String? = null,
    val metricPills: List<HeroMetricPillData> = emptyList(),
    val spotlight: HeroSpotlightData? = null,
    val containerColor: Color,
    val contentColor: Color,
    val accentColor: Color,
    val onClick: () -> Unit
)

@Composable
private fun OverviewHeroCard(
    slide: HeroCarouselSlide,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = slide.onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 284.dp)
            .expressivePressScale(),
        shape = MaterialTheme.shapes.extraLargeIncreased,
        colors = CardDefaults.cardColors(
            containerColor = slide.containerColor,
            contentColor = slide.contentColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp)
                .heightIn(min = 244.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Editorial header: keep category and provider together without a duplicate action icon.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = slide.title,
                        style = MaterialTheme.typography.headlineSmallEmphasized,
                        color = slide.contentColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (slide.provider.isNotBlank()) {
                        Text(
                            text = slide.provider,
                            style = MaterialTheme.typography.labelMediumEmphasized,
                            color = slide.contentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

            }

            // Middle Body: Bento Split
            if (slide.id == "nutrition") {
                // Leafit-inspired Nutrition Hero
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // A smaller dial on narrow phones leaves room for the goal and percent.
                    val windowWidth = with(androidx.compose.ui.platform.LocalDensity.current) {
                        androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.width.toDp()
                    }
                    LeafitCalorieDial(
                        progress = slide.progress ?: 0f,
                        consumed = slide.count.toIntOrNull() ?: 0,
                        accentColor = slide.accentColor,
                        contentColor = slide.contentColor,
                        size = if (windowWidth < 400.dp) 80.dp else 96.dp
                    )

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // One line each: on narrow phones or large font sizes the goal is
                        // shortened instead of the percent wrapping into it.
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            val targetStr = slide.secondaryText.substringAfterLast("·").trim().removePrefix("/").trim()
                            Text(
                                text = targetStr.ifBlank { "${slide.count} ${stringResource(R.string.overview_hero_unit_kcal)}" },
                                style = MaterialTheme.typography.titleSmallEmphasized,
                                color = slide.contentColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (slide.progressLabel != null) {
                                Text(
                                    text = slide.progressLabel,
                                    style = MaterialTheme.typography.labelSmallEmphasized,
                                    color = slide.contentColor,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        val animatedProgress by animateFloatAsState(
                            targetValue = slide.progress ?: 0f,
                            animationSpec = M3ExpressiveMotion.defaultSpatial(),
                            label = "hero_progress"
                        )

                        LinearProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier.fillMaxWidth(),
                            color = slide.accentColor,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            strokeCap = StrokeCap.Round
                        )

                        if (slide.metricPills.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                slide.metricPills.take(2).forEach { pill ->
                                    HeroMetricPill(
                                        pill = pill,
                                        modifier = Modifier.weight(1f),
                                        contentColor = slide.contentColor,
                                        accentColor = slide.accentColor
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // Media Hero Bento: Left Cover Poster + Right Stats & Bento Docks
                // The top padding keeps the cover off the title when the card is full and the
                // column's SpaceBetween leaves no gap.
                BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    // On narrow cards (small phones) three stats beside the cover get about 40dp
                    // each, which breaks words such as "Rewatch". Put them in a full-width row
                    // under the cover instead.
                    val statsBelowCover = maxWidth < 300.dp
                    val metricPillsRow: @Composable () -> Unit = {
                        if (slide.metricPills.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                slide.metricPills.take(3).forEach { pill ->
                                    HeroMetricPill(
                                        pill = pill,
                                        modifier = Modifier.weight(1f),
                                        contentColor = slide.contentColor,
                                        accentColor = slide.accentColor
                                    )
                                }
                            }
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Left Hero Media Showcase
                            HeroMediaPoster(
                                category = slide.id,
                                spotlight = slide.spotlight,
                                isMusic = slide.id == "music",
                                iconOnly = slide.id == "health" || slide.id == "notifications",
                                contentColor = slide.contentColor,
                                accentColor = slide.accentColor
                            )

                            // Right Column: Hero Count + Bento Docks
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.Bottom,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = slide.count,
                                        style = MaterialTheme.typography.displayMediumEmphasized.copy(
                                            fontFeatureSettings = "tnum"
                                        ),
                                        color = slide.contentColor
                                    )
                                    Text(
                                        text = slide.unitText,
                                        style = MaterialTheme.typography.titleMediumEmphasized,
                                        color = slide.contentColor,
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    )
                                }

                                if (slide.secondaryText.isNotBlank()) {
                                    Text(
                                        text = slide.secondaryText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                if (!statsBelowCover) metricPillsRow()
                            }
                        }
                        if (statsBelowCover) metricPillsRow()
                    }
                }
            }

            // 3. Bottom Footer (The Nested Spotlight Island)
            if (slide.spotlight != null) {
                HeroSpotlightFooter(
                    spotlight = slide.spotlight,
                    accentColor = slide.accentColor,
                    contentColor = slide.contentColor
                )
            }
        }
    }
}

@Composable
private fun HeroMediaPoster(
    category: String,
    spotlight: HeroSpotlightData?,
    isMusic: Boolean,
    iconOnly: Boolean,
    contentColor: Color,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val width = 96.dp
    val hasArtwork = !spotlight?.imageUrl.isNullOrBlank() || isMusic
    val height = when {
        isMusic || iconOnly -> 96.dp
        else -> 116.dp
    }
    val shape = when {
        iconOnly -> CircleShape
        isMusic -> CircleShape
        else -> RoundedCornerShape(
            topStart = 38.dp,
            topEnd = 16.dp,
            bottomEnd = 38.dp,
            bottomStart = 16.dp
        )
    }

    Box(
        modifier = modifier
            .size(width = width, height = height)
            .then(
                if (iconOnly || !hasArtwork) Modifier
                else Modifier.shadow(elevation = 1.dp, shape = shape, spotColor = contentColor.copy(alpha = 0.16f))
            )
            .clip(shape)
    ) {
        if (!spotlight?.imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = spotlight.imageUrl,
                contentDescription = spotlight.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else if (iconOnly) {
            Icon(
                imageVector = spotlight?.fallbackIcon ?: Icons.Rounded.Apps,
                contentDescription = null,
                tint = recordCategoryColors(overviewRecordCategory(category)).onContainer,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(34.dp)
            )
        } else if (isMusic) {
            // Vinyl Record Art when album image is missing
            val vinylSurface = MaterialTheme.colorScheme.inverseSurface
            val vinylGroove = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.14f)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(vinylSurface),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val r = size.minDimension / 2f
                    drawCircle(vinylGroove, r * 0.88f, center, style = Stroke(1.5f))
                    drawCircle(vinylGroove, r * 0.72f, center, style = Stroke(1.5f))
                    drawCircle(vinylGroove, r * 0.56f, center, style = Stroke(1.5f))
                    drawCircle(accentColor.copy(alpha = 0.85f), r * 0.38f, center)
                    drawCircle(vinylSurface, r * 0.14f, center)
                }
            }
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = spotlight?.fallbackIcon ?: Icons.AutoMirrored.Rounded.MenuBook,
                    contentDescription = null,
                    tint = recordCategoryColors(overviewRecordCategory(category)).accent,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
private fun LeafitCalorieDial(
    progress: Float,
    consumed: Int,
    accentColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 96.dp
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = M3ExpressiveMotion.defaultSpatial(),
        label = "calorie_dial"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 8.dp.toPx()
            val arcSize = Size(this.size.width - strokeWidth, this.size.height - strokeWidth)
            val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)

            // Background arc (270 degrees)
            drawArc(
                color = contentColor.copy(alpha = 0.14f),
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Active Arc
            if (animatedProgress > 0.001f) {
                drawArc(
                    color = accentColor,
                    startAngle = 135f,
                    sweepAngle = 270f * animatedProgress,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = consumed.toString(),
                style = MaterialTheme.typography.titleLargeEmphasized.copy(fontFeatureSettings = "tnum"),
                color = contentColor
            )
            Text(
                text = stringResource(R.string.overview_hero_unit_kcal),
                style = MaterialTheme.typography.labelSmall,
                color = contentColor
            )
        }
    }
}

@Composable
private fun HeroMetricPill(
    pill: HeroMetricPillData,
    modifier: Modifier = Modifier,
    contentColor: Color,
    accentColor: Color
) {
    Column(
        modifier = modifier.padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (pill.icon != null) {
                Icon(
                    imageVector = pill.icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(14.dp)
                )
            }
            Text(
                text = pill.value,
                style = MaterialTheme.typography.titleSmallEmphasized.copy(
                    fontFeatureSettings = "tnum"
                ),
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = pill.label,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun HeroSpotlightFooter(
    spotlight: HeroSpotlightData,
    accentColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = spotlight.caption,
                style = MaterialTheme.typography.labelMediumEmphasized,
                color = accentColor,
                maxLines = 1,
                softWrap = false
            )

            Text(
                text = spotlight.title,
                style = MaterialTheme.typography.titleSmallEmphasized,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
        if (!spotlight.subtitle.isNullOrBlank()) {
            Text(
                text = spotlight.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 2.dp)
            )
        }
    }
}

@Composable
fun ActivityRowItem(
    item: com.personalrecords.app.core.network.model.ActivityItemDto,
    index: Int = 0,
    count: Int = 1,
    modifier: Modifier = Modifier,
    addLeadingGap: Boolean = false,
    onClick: () -> Unit
) {

    val fallbackIcon = when (item.source) {
        "fatsecret" -> Icons.Rounded.Restaurant
        "goodreads" -> Icons.AutoMirrored.Rounded.MenuBook
        "letterboxd" -> Icons.Rounded.Movie
        "notification" -> Icons.Rounded.Notifications
        "music" -> Icons.Rounded.MusicNote
        else -> Icons.Rounded.Bookmark
    }

    
    ExpressiveListRow(
        index = index,
        count = count,
        modifier = modifier,
        addLeadingGap = addLeadingGap,
        onClick = onClick,
        verticalAlignment = Alignment.CenterVertically,
        leadingContent = {
            when (item.source) {
                "notification" -> {
                    AppLetterAvatar(appName = notificationAppName(item), size = 38.dp)
                }
                "fatsecret" -> {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(recordCategoryColors(RecordCategory.Nutrition).container),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Restaurant,
                            contentDescription = null,
                            tint = recordCategoryColors(RecordCategory.Nutrition).onContainer,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                "music" -> {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(recordCategoryColors(RecordCategory.Music).container),
                        contentAlignment = Alignment.Center
                    ) {
                        if (item.image_url?.isNotBlank() == true) {
                            AsyncImage(
                                model = item.image_url,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.MusicNote,
                                contentDescription = null,
                                tint = recordCategoryColors(RecordCategory.Music).onContainer,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                else -> {
                    PosterThumbnail(
                        imageUrl = item.image_url,
                        fallbackIcon = fallbackIcon,
                        width = 34,
                        height = 48
                    )
                }
            }
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                val rating = item.details["rating"]?.toString()?.toDoubleOrNull()
                    ?: item.details["my_rating"]?.toString()?.toDoubleOrNull()
                if (rating != null && rating > 0) {
                    RatingBadge(rating = rating)
                }
                if (item.source == "fatsecret") {
                    val cal = item.details["calories"]?.toString()?.replace("\"", "")
                    if (cal != null && cal != "0" && cal != "null") {
                        Text(
                            text = "$cal ${stringResource(R.string.overview_hero_unit_kcal)}",
                            style = MaterialTheme.typography.labelSmallEmphasized,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        },
        supportingContent = {
            Column {
                Text(
                    text = "${item.subtitle} · ${item.date}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    ) {
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodyMediumEmphasized,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** How much of the next hero card shows at the carousel edge. */
private val HeroCardPeek = 32.dp

/** Gap between hero cards in the carousel. */
private val HeroCardSpacing = 8.dp
