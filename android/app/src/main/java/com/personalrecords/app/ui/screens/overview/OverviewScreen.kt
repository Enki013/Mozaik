package com.personalrecords.app.ui.screens.overview

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.personalrecords.app.R
import com.personalrecords.app.core.integrations.PluginRegistry
import com.personalrecords.app.core.theme.*
import com.personalrecords.app.ui.components.*
import kotlinx.coroutines.delay
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
    onNavigateToSettings: (() -> Unit)? = null,
    onNavigateToIntegrationsCatalog: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedActivity by remember { mutableStateOf<com.personalrecords.app.core.network.model.ActivityItemDto?>(null) }
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
    val statsPagerState = rememberPagerState(initialPage = 0, pageCount = { 4 })

    // 5-second gentle auto-scroll when user is idle
    LaunchedEffect(statsPagerState) {
        while (true) {
            delay(5000)
            if (!statsPagerState.isScrollInProgress) {
                val nextPage = (statsPagerState.currentPage + 1) % 4
                statsPagerState.animateScrollToPage(
                    page = nextPage,
                    animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing)
                )
            }
        }
    }

    LaunchedEffect(state.syncMessage) {
        state.syncMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSyncMessage()
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLargeEmphasized,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val formattedDate = remember {
                            try {
                                val formatter = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())
                                LocalDate.now().format(formatter)
                            } catch (_: Exception) { "" }
                        }
                        if (formattedDate.isNotBlank()) {
                            Text(
                                text = formattedDate,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                scrollBehavior = scrollBehavior
            )
        },
        contentWindowInsets = WindowInsets.statusBars,
        snackbarHost = {
            SnackbarHost(
                snackbarHostState,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = 96.dp)
            )
        }
    ) { innerPadding ->
        val listState = rememberLazyListState()
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
                    ContainedLoadingIndicator(size = 48.dp)
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = 8.dp,
                        bottom = 144.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    ),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Server Connection Error Banner
                    if (state.error != null && state.dashboard == null) {
                        item(key = "overview_connection_error", contentType = "error_banner") {
                            Surface(
                                shape = M3Shapes.CornerMedium,
                                color = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = expressiveItemModifier()
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    .expressiveShake(trigger = state.error)
                                    .then(
                                        if (onNavigateToSettings != null) {
                                            Modifier.clickable { onNavigateToSettings() }
                                        } else Modifier
                                    )
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudOff,
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stringResource(R.string.overview_connection_error_title),
                                            style = MaterialTheme.typography.titleSmallEmphasized
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = stringResource(R.string.overview_connection_error_desc),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.9f)
                                        )
                                    }
                                }
                            }
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

                        val filmActivityRating = latestFilmActivity?.let {
                            (it.details["rating"]?.toString()?.replace("\"", "")?.toDoubleOrNull()
                                ?: it.details["my_rating"]?.toString()?.replace("\"", "")?.toDoubleOrNull())
                                ?.takeIf { r -> r > 0.0 }
                        }
                        val bookActivityRating = latestBookActivity?.let {
                            (it.details["my_rating"]?.toString()?.replace("\"", "")?.toDoubleOrNull()
                                ?: it.details["rating"]?.toString()?.replace("\"", "")?.toDoubleOrNull())
                                ?.takeIf { r -> r > 0.0 }
                        }

                        val resolvedFilmTitle = state.latestFilm?.displayTitle?.ifBlank { null }
                            ?: latestFilmActivity?.title
                            ?: stringResource(R.string.overview_hero_no_recent_film)
                        val resolvedFilmSubtitle = state.latestFilm?.let { f ->
                            if (f.safeYear > 0) f.safeYear.toString() else null
                        } ?: latestFilmActivity?.subtitle?.takeIf { it.isNotBlank() }
                        val resolvedFilmPoster = state.latestFilm?.image_url ?: latestFilmActivity?.image_url
                        val resolvedFilmRating = state.latestFilm?.safeRating?.takeIf { it > 0.0 } ?: filmActivityRating
                        val resolvedFilmRewatch = state.latestFilm?.rewatch == true || latestFilmActivity?.rewatch == true

                        val resolvedBookTitle = state.latestBook?.title?.ifBlank { null }
                            ?: latestBookActivity?.title
                            ?: stringResource(R.string.overview_hero_no_recent_book)
                        val resolvedBookAuthor = state.latestBook?.author?.ifBlank { null }
                            ?: latestBookActivity?.subtitle
                        val resolvedBookCover = state.latestBook?.image_url ?: latestBookActivity?.image_url
                        val resolvedBookRating = state.latestBook?.let { b ->
                            b.my_rating.takeIf { it > 0.0 } ?: b.average_rating.takeIf { it > 0.0 }
                        } ?: bookActivityRating

                        val resolvedMealTitle = state.latestMeal?.displayName?.ifBlank { null }
                            ?: latestMeal?.title
                            ?: stringResource(R.string.overview_hero_no_recent_meal)
                        val resolvedMealSubtitle = state.latestMeal?.let { m ->
                            val parts = listOfNotNull(m.meal.ifBlank { null }, "${m.calories.toInt()} kcal")
                            if (parts.isNotEmpty()) parts.joinToString(" · ") else null
                        } ?: latestMeal?.subtitle?.takeIf { it.isNotBlank() }

                        val todayDateStr = remember(state.dashboard) {
                            state.dashboard?.today?.get("date")?.toString()?.replace("\"", "")
                                ?: LocalDate.now().toString()
                        }
                        val mealDate = state.latestMeal?.displayDate?.takeIf { it.isNotBlank() } ?: latestMeal?.date
                        val mealDateNote = remember(mealDate, todayDateStr) {
                            if (mealDate != null && !mealDate.startsWith(todayDateStr)) {
                                try {
                                    val parsed = LocalDate.parse(mealDate.take(10))
                                    val today = LocalDate.now()
                                    if (parsed.isEqual(today.minusDays(1))) " • DÜN" else " • ${parsed.dayOfMonth}.${parsed.monthValue}"
                                } catch (_: Exception) {
                                    null
                                }
                            } else null
                        }

                        val currentYear = remember { java.time.Year.now().value.toString() }
                        val thisYearBooksCount = remember(state.recentActivities) {
                            state.recentActivities.count { (it.source == "goodreads" || it.type == "book") && it.date.startsWith(currentYear) }
                        }
                        val thisYearFilmsCount = remember(state.recentActivities) {
                            state.recentActivities.count { (it.source == "letterboxd" || it.type == "film") && it.date.startsWith(currentYear) }
                        }

                        val nutritionSlide = HeroCarouselSlide(
                            id = "nutrition",
                            title = "Beslenme • FatSecret",
                            count = todayCalInt.toString(),
                            unitText = stringResource(R.string.overview_hero_unit_kcal),
                            secondaryContent = HeroSecondaryContent.NutritionGauge(
                                progress = calProgress,
                                targetCal = targetCal,
                                remainingCal = remainingCal
                            ),
                            spotlight = HeroSpotlightData(
                                caption = stringResource(R.string.overview_hero_latest_meal) + (mealDateNote ?: ""),
                                title = resolvedMealTitle,
                                subtitle = resolvedMealSubtitle,
                                fallbackIcon = Icons.Default.Restaurant
                            ),
                            icon = Icons.Default.Restaurant,
                            containerColor = domainNutritionContainer(),
                            contentColor = domainNutritionOnContainer(),
                            accentColor = domainNutritionColor(),
                            onClick = onNavigateToNutrition
                        )

                        val booksSecondaryBadge = if (thisYearBooksCount > 0) {
                            HeroSecondaryContent.StatBadge(
                                label = stringResource(R.string.overview_hero_this_year_count, thisYearBooksCount),
                                icon = Icons.Default.CalendarToday
                            )
                        } else {
                            HeroSecondaryContent.StatBadge(
                                label = stringResource(R.string.overview_hero_tag_shelf),
                                icon = Icons.AutoMirrored.Filled.MenuBook
                            )
                        }

                        val booksSlide = HeroCarouselSlide(
                            id = "books",
                            title = "${stringResource(R.string.title_books)} • Goodreads",
                            count = booksCount.toString(),
                            unitText = stringResource(R.string.overview_hero_unit_books),
                            secondaryContent = booksSecondaryBadge,
                            spotlight = HeroSpotlightData(
                                caption = stringResource(R.string.overview_hero_latest_book),
                                title = resolvedBookTitle,
                                subtitle = resolvedBookAuthor,
                                imageUrl = resolvedBookCover,
                                rating = resolvedBookRating,
                                fallbackIcon = Icons.AutoMirrored.Filled.MenuBook
                            ),
                            icon = Icons.AutoMirrored.Filled.MenuBook,
                            containerColor = domainBooksContainer(),
                            contentColor = domainBooksOnContainer(),
                            accentColor = domainBooksColor(),
                            onClick = onNavigateToBooks
                        )

                        val moviesSecondaryBadge = if (thisYearFilmsCount > 0) {
                            HeroSecondaryContent.StatBadge(
                                label = stringResource(R.string.overview_hero_this_year_count, thisYearFilmsCount),
                                icon = Icons.Default.CalendarToday
                            )
                        } else {
                            HeroSecondaryContent.StatBadge(
                                label = stringResource(R.string.overview_hero_tag_diary),
                                icon = Icons.Default.Movie
                            )
                        }

                        val moviesSlide = HeroCarouselSlide(
                            id = "movies",
                            title = "${stringResource(R.string.title_movies)} • Letterboxd",
                            count = filmsCount.toString(),
                            unitText = stringResource(R.string.overview_hero_unit_films),
                            secondaryContent = moviesSecondaryBadge,
                            spotlight = HeroSpotlightData(
                                caption = stringResource(R.string.overview_hero_latest_film),
                                title = resolvedFilmTitle,
                                subtitle = resolvedFilmSubtitle,
                                imageUrl = resolvedFilmPoster,
                                rating = resolvedFilmRating,
                                rewatch = resolvedFilmRewatch,
                                fallbackIcon = Icons.Default.Movie
                            ),
                            icon = Icons.Default.Movie,
                            containerColor = domainCinemaContainer(),
                            contentColor = domainCinemaOnContainer(),
                            accentColor = domainCinemaColor(),
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

                        val musicSecondaryBadge = if (todayMusicCount > 0) {
                            HeroSecondaryContent.StatBadge(
                                label = stringResource(R.string.overview_hero_today_music, todayMusicCount),
                                icon = Icons.Default.MusicNote
                            )
                        } else {
                            HeroSecondaryContent.StatBadge(
                                label = stringResource(R.string.overview_hero_tag_shelf_music),
                                icon = Icons.Default.MusicNote
                            )
                        }

                        val musicSlide = HeroCarouselSlide(
                            id = "music",
                            title = "${stringResource(R.string.title_music)} • Last.fm",
                            count = musicCount.toString(),
                            unitText = stringResource(R.string.overview_hero_unit_tracks),
                            secondaryContent = musicSecondaryBadge,
                            spotlight = HeroSpotlightData(
                                caption = stringResource(R.string.overview_hero_latest_track),
                                title = resolvedMusicTitle,
                                subtitle = resolvedMusicSubtitle,
                                imageUrl = resolvedMusicImage,
                                fallbackIcon = Icons.Default.MusicNote
                            ),
                            icon = Icons.Default.MusicNote,
                            containerColor = domainMusicContainer(),
                            contentColor = domainMusicOnContainer(),
                            accentColor = domainMusicColor(),
                            onClick = onNavigateToMusic
                        )

                        val slides = listOf(nutritionSlide, booksSlide, moviesSlide, musicSlide)
                        val coroutineScope = rememberCoroutineScope()

                        Column(
                            modifier = expressiveItemModifier().fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            HorizontalPager(
                                state = statsPagerState,
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                pageSpacing = 12.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) { pageIndex ->
                                val slide = slides[pageIndex]
                                val pageOffset = (statsPagerState.currentPage - pageIndex) + statsPagerState.currentPageOffsetFraction
                                val scale = 1f - (kotlin.math.abs(pageOffset) * 0.04f).coerceIn(0f, 0.04f)
                                val alpha = 1f - (kotlin.math.abs(pageOffset) * 0.12f).coerceIn(0f, 0.12f)

                                OverviewHeroCard(
                                    slide = slide,
                                    modifier = Modifier.graphicsLayer {
                                        scaleX = scale
                                        scaleY = scale
                                        this.alpha = alpha
                                    }
                                )
                            }

                            // M3 Expressive Morphing Pager Indicator
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 2.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                slides.forEachIndexed { index, slide ->
                                    val isSelected = statsPagerState.currentPage == index
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
                                            .padding(horizontal = 3.dp)
                                            .height(6.dp)
                                            .width(animatedWidth)
                                            .clip(CircleShape)
                                            .background(indicatorColor)
                                            .clickable {
                                                coroutineScope.launch {
                                                    statsPagerState.animateScrollToPage(index)
                                                }
                                            }
                                    )
                                }
                            }
                        }
                    }

                    // Entegrasyonlar & Senkronizasyon (Grouped Containers + Sync All Hero Button)
                    item(key = "overview_integrations", contentType = "integrations") {
                        val activePlugins = remember(state.enabledPluginIds) {
                            PluginRegistry.defaultPlugins.filter { state.enabledPluginIds.contains(it.id) }
                        }

                        Column(modifier = expressiveItemModifier().fillMaxWidth().padding(horizontal = 16.dp)) {
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
                                        color = MaterialTheme.colorScheme.onSurface
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
                                    FilledTonalButton(
                                        onClick = { viewModel.syncAll() },
                                        enabled = state.isSyncing == null,
                                        shape = M3Shapes.CornerFull,
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        if (isSyncingAll) {
                                            ExpressiveLoadingIndicator(
                                                size = 14.dp,
                                                strokeWidth = 2.dp,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.Sync,
                                                contentDescription = null,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = stringResource(R.string.action_sync_all),
                                            style = MaterialTheme.typography.labelMediumEmphasized
                                        )
                                    }

                                    FilledTonalIconButton(
                                        onClick = onNavigateToIntegrationsCatalog,
                                        modifier = Modifier.size(34.dp),
                                        shape = CircleShape,
                                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
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
                                            shape = M3Shapes.CornerSmall
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Add,
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
                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    activePlugins.forEachIndexed { index, plugin ->
                                        val syncKey = plugin.syncServiceKey ?: plugin.id
                                        val isItemSyncing = state.isSyncing == syncKey || state.isSyncing == "all"
                                        val shape = groupedContainerShape(index, activePlugins.size)

                                        Surface(
                                            shape = shape,
                                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .expressiveBounceClick { viewModel.syncService(syncKey) }
                                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(36.dp)
                                                        .clip(RoundedCornerShape(10.dp))
                                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = plugin.icon,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }

                                                Spacer(modifier = Modifier.width(12.dp))

                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = plugin.name,
                                                        style = MaterialTheme.typography.bodyMediumEmphasized,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    val desc = plugin.shortDescRes?.let { stringResource(it) } ?: plugin.shortDescription
                                                    Text(
                                                        text = desc,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }

                                                Spacer(modifier = Modifier.width(8.dp))

                                                if (isItemSyncing) {
                                                    ExpressiveLoadingIndicator(
                                                        size = 18.dp,
                                                        strokeWidth = 2.dp,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                } else {
                                                    Icon(
                                                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Son Aktiviteler Grouped Section (Grouped Containers + See All Action)
                    item(key = "overview_recent_activities", contentType = "recent_activities") {
                        Column(modifier = expressiveItemModifier().fillMaxWidth().padding(horizontal = 16.dp)) {
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
                                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
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
                                    ContainedLoadingIndicator(size = 36.dp)
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
                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    val activitiesToShow = state.recentActivities.take(6)
                                    activitiesToShow.forEachIndexed { index, item ->
                                        key("${item.source}_${item.id.ifBlank { "${item.date}_${item.title}" }}") {
                                            val shape = groupedContainerShape(index, activitiesToShow.size)
                                            Surface(
                                                shape = shape,
                                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                ActivityRowItem(
                                                    item = item,
                                                    onClick = { selectedActivity = item }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item(key = "overview_bottom_spacer", contentType = "spacer") {
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }
        }
    }

    selectedActivity?.let { activity ->
        ActivityItemDetailBottomSheet(
            item = activity,
            onDismiss = { selectedActivity = null }
        )
    }
}

private sealed interface HeroSecondaryContent {
    data class NutritionGauge(
        val progress: Float,
        val targetCal: Int,
        val remainingCal: Int
    ) : HeroSecondaryContent

    data class StatBadge(
        val label: String,
        val icon: ImageVector? = null
    ) : HeroSecondaryContent
}

private data class HeroSpotlightData(
    val caption: String,
    val title: String,
    val subtitle: String? = null,
    val imageUrl: String? = null,
    val rating: Double? = null,
    val rewatch: Boolean = false,
    val fallbackIcon: ImageVector
)

private data class HeroCarouselSlide(
    val id: String,
    val title: String,
    val count: String,
    val unitText: String,
    val secondaryContent: HeroSecondaryContent? = null,
    val spotlight: HeroSpotlightData? = null,
    val icon: ImageVector,
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
        modifier = modifier
            .fillMaxWidth()
            .height(238.dp)
            .expressiveBounceClick(onClick = slide.onClick),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = slide.containerColor,
            contentColor = slide.contentColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. Top Row: Leading Icon + Domain Title & Trailing CTA Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(slide.contentColor.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = slide.icon,
                            contentDescription = null,
                            tint = slide.contentColor,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    Text(
                        text = slide.title,
                        style = MaterialTheme.typography.titleMediumEmphasized,
                        color = slide.contentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    shape = CircleShape,
                    color = slide.contentColor.copy(alpha = 0.14f),
                    border = BorderStroke(
                        1.dp,
                        slide.contentColor.copy(alpha = 0.20f)
                    ),
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector = Icons.Default.NorthEast,
                            contentDescription = null,
                            tint = slide.contentColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // 2. Split Hero Metric Row (Left = Bold Numeral + Unit, Right = Dynamic Secondary Stat)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Bold Display Numeral + Baseline Unit
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = slide.count,
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontSize = 46.sp,
                            fontWeight = FontWeight.ExtraBold,
                            lineHeight = 46.sp
                        ),
                        color = slide.contentColor
                    )
                    Text(
                        text = slide.unitText,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = slide.contentColor.copy(alpha = 0.82f),
                        modifier = Modifier.padding(bottom = 5.dp)
                    )
                }

                // Right: Secondary dynamic content (Nutrition circular gauge or live badge)
                when (val sec = slide.secondaryContent) {
                    is HeroSecondaryContent.NutritionGauge -> {
                        val animatedProgress by animateFloatAsState(
                            targetValue = sec.progress,
                            animationSpec = M3ExpressiveMotion.defaultSpatial(),
                            label = "hero_cal_ring"
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(1.dp)
                            ) {
                                Text(
                                    text = if (sec.remainingCal >= 0) {
                                        stringResource(R.string.overview_hero_remaining_compact, sec.remainingCal)
                                    } else {
                                        stringResource(R.string.overview_hero_exceeded_compact, -sec.remainingCal)
                                    },
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = slide.contentColor
                                )
                                Text(
                                    text = stringResource(R.string.overview_hero_goal_label_compact, sec.targetCal),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = slide.contentColor.copy(alpha = 0.72f)
                                )
                            }
                            Box(
                                modifier = Modifier.size(44.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    progress = { animatedProgress },
                                    modifier = Modifier.size(44.dp),
                                    color = slide.accentColor,
                                    trackColor = slide.contentColor.copy(alpha = 0.16f),
                                    strokeWidth = 4.5.dp,
                                    strokeCap = StrokeCap.Round
                                )
                                Text(
                                    text = "${(sec.progress * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 11.sp
                                    ),
                                    color = slide.contentColor
                                )
                            }
                        }
                    }
                    is HeroSecondaryContent.StatBadge -> {
                        Surface(
                            shape = CircleShape,
                            color = slide.contentColor.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, slide.contentColor.copy(alpha = 0.18f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (sec.icon != null) {
                                    Icon(
                                        imageVector = sec.icon,
                                        contentDescription = null,
                                        tint = slide.accentColor,
                                        modifier = Modifier.size(13.dp)
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(slide.accentColor)
                                    )
                                }
                                Text(
                                    text = sec.label,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = slide.contentColor
                                )
                            }
                        }
                    }
                    null -> Unit
                }
            }

            // 3. Tonal Concentric Spotlight Container (Harmonious surface tint, no pitch-black hole)
            HeroSpotlightContainer(
                spotlight = slide.spotlight,
                contentColor = slide.contentColor,
                accentColor = slide.accentColor
            )
        }
    }
}

@Composable
private fun HeroSpotlightContainer(
    spotlight: HeroSpotlightData?,
    contentColor: Color,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = contentColor.copy(alpha = 0.12f),
        border = BorderStroke(
            1.dp,
            contentColor.copy(alpha = 0.18f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .height(68.dp)
    ) {
        if (spotlight == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = stringResource(R.string.overview_no_activities),
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.72f)
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Media thumbnail or fallback icon with mathematically concentric radius
                if (spotlight.imageUrl?.isNotBlank() == true) {
                    val isMusic = spotlight.fallbackIcon == Icons.Default.MusicNote
                    val imgWidth = if (isMusic) 46.dp else 34.dp
                    val imgHeight = 46.dp
                    val imgShape = if (isMusic) RoundedCornerShape(8.dp) else RoundedCornerShape(6.dp)

                    Box(
                        modifier = Modifier
                            .size(width = imgWidth, height = imgHeight)
                            .clip(imgShape)
                            .background(contentColor.copy(alpha = 0.14f))
                    ) {
                        AsyncImage(
                            model = spotlight.imageUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                } else {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(contentColor.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = spotlight.fallbackIcon,
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                }

                // Title + Subtitle Column
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = spotlight.caption,
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 0.6.sp,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        ),
                        color = contentColor.copy(alpha = 0.68f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(1.dp))
                    Text(
                        text = spotlight.title,
                        style = MaterialTheme.typography.bodyMediumEmphasized,
                        color = contentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (spotlight.subtitle?.isNotBlank() == true) {
                        Text(
                            text = spotlight.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = contentColor.copy(alpha = 0.78f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Trailing badges (rating / rewatch) in matching tonal style
                if (spotlight.rating != null && spotlight.rating > 0.0) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = CircleShape,
                        color = contentColor.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, contentColor.copy(alpha = 0.18f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = contentColor,
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = String.format(java.util.Locale.US, "%.1f", spotlight.rating),
                                style = MaterialTheme.typography.labelSmallEmphasized,
                                color = contentColor
                            )
                        }
                    }
                }

                if (spotlight.rewatch) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Surface(
                        shape = CircleShape,
                        color = contentColor.copy(alpha = 0.16f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier
                                .padding(5.dp)
                                .size(12.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun groupedContainerShape(index: Int, count: Int): Shape {
    return when {
        count <= 1 -> RoundedCornerShape(16.dp)
        index == 0 -> RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
        index == count - 1 -> RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
        else -> RoundedCornerShape(4.dp)
    }
}

@Composable
fun ActivityRowItem(
    item: com.personalrecords.app.core.network.model.ActivityItemDto,
    onClick: () -> Unit
) {
    val fallbackIcon = when (item.source) {
        "fatsecret" -> Icons.Default.Restaurant
        "goodreads" -> Icons.AutoMirrored.Filled.MenuBook
        "letterboxd" -> Icons.Default.Movie
        "notification" -> Icons.Default.Notifications
        "music" -> Icons.Default.MusicNote
        else -> Icons.Default.Bookmark
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .expressiveBounceClick(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Leading Media / Icon Avatar
        when (item.source) {
            "notification" -> {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(M3Shapes.CornerFull)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            "fatsecret" -> {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(M3Shapes.CornerSmall)
                        .background(domainNutritionContainer()),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Restaurant,
                        contentDescription = null,
                        tint = domainNutritionOnContainer(),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            "music" -> {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(M3Shapes.CornerSmall)
                        .background(domainMusicContainer()),
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
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = domainMusicOnContainer(),
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

        Spacer(modifier = Modifier.width(12.dp))

        // Center Details
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMediumEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${item.subtitle} · ${item.date}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Trailing Badge (Calories, Rating)
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
                        text = "$cal kcal",
                        style = MaterialTheme.typography.labelSmallEmphasized,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}
