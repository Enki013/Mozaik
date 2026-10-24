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
import androidx.compose.ui.tooling.preview.Preview
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
                        val resolvedMealSubtitle = state.latestMeal?.meal?.ifBlank { null }
                            ?: latestMeal?.subtitle?.takeIf { it.isNotBlank() }
                            ?: "Öğün"
                        val resolvedMealBadge = state.latestMeal?.let { "${it.calories.toInt()} kcal" }
                            ?: latestMeal?.details?.get("calories")?.let { "$it kcal" }

                        val nutritionMainValue = if (todayCalInt > 0) todayCalInt.toString() else (state.latestMeal?.calories?.toInt()?.takeIf { it > 0 }?.toString() ?: "0")
                        val nutritionProgress = if (todayCalInt > 0) calProgress else ((state.latestMeal?.calories?.toFloat() ?: 0f) / targetCal).coerceIn(0f, 1f)

                        val nutritionSlide = CompactHeroSlide(
                            id = "nutrition",
                            categoryTitle = "Beslenme • FatSecret",
                            mainValue = nutritionMainValue,
                            unit = stringResource(R.string.overview_hero_unit_kcal),
                            goalProgress = nutritionProgress,
                            goalLabel = "/ $targetCal",
                            recentTitle = resolvedMealTitle,
                            recentSubtitle = resolvedMealSubtitle,
                            recentBadge = resolvedMealBadge,
                            hasRatingStar = false,
                            imageUrl = null,
                            cardColor = domainNutritionContainer(),
                            onCardColor = domainNutritionOnContainer(),
                            icon = Icons.Default.Restaurant,
                            onClick = onNavigateToNutrition
                        )

                        val booksSlide = CompactHeroSlide(
                            id = "books",
                            categoryTitle = "${stringResource(R.string.title_books)} • Goodreads",
                            mainValue = booksCount.toString(),
                            unit = stringResource(R.string.overview_hero_unit_books),
                            recentTitle = resolvedBookTitle,
                            recentSubtitle = resolvedBookAuthor ?: "",
                            recentBadge = resolvedBookRating?.let { String.format(Locale.US, "%.1f", it) },
                            hasRatingStar = resolvedBookRating != null && resolvedBookRating > 0.0,
                            imageUrl = resolvedBookCover,
                            cardColor = domainBooksContainer(),
                            onCardColor = domainBooksOnContainer(),
                            icon = Icons.AutoMirrored.Filled.MenuBook,
                            onClick = onNavigateToBooks
                        )

                        val moviesSlide = CompactHeroSlide(
                            id = "movies",
                            categoryTitle = "${stringResource(R.string.title_movies)} • Letterboxd",
                            mainValue = filmsCount.toString(),
                            unit = stringResource(R.string.overview_hero_unit_films),
                            recentTitle = resolvedFilmTitle,
                            recentSubtitle = resolvedFilmSubtitle ?: "",
                            recentBadge = resolvedFilmRating?.let { String.format(Locale.US, "%.1f", it) },
                            hasRatingStar = resolvedFilmRating != null && resolvedFilmRating > 0.0,
                            imageUrl = resolvedFilmPoster,
                            cardColor = domainCinemaContainer(),
                            onCardColor = domainCinemaOnContainer(),
                            icon = Icons.Default.Movie,
                            onClick = onNavigateToMovies
                        )

                        val resolvedMusicTitle = state.latestMusic?.track?.ifBlank { null }
                            ?: latestMusic?.title
                            ?: stringResource(R.string.overview_hero_no_recent_music)
                        val resolvedMusicSubtitle = state.latestMusic?.let { s ->
                            val parts = listOfNotNull(s.artist.ifBlank { null }, s.album.ifBlank { null })
                            if (parts.isNotEmpty()) parts.joinToString(" • ") else null
                        } ?: latestMusic?.subtitle?.takeIf { it.isNotBlank() } ?: ""
                        val resolvedMusicImage = state.latestMusic?.imageUrl?.ifBlank { null }
                            ?: latestMusic?.image_url

                        val musicSlide = CompactHeroSlide(
                            id = "music",
                            categoryTitle = "${stringResource(R.string.title_music)} • Last.fm",
                            mainValue = musicCount.toString(),
                            unit = stringResource(R.string.overview_hero_unit_tracks),
                            recentTitle = resolvedMusicTitle,
                            recentSubtitle = resolvedMusicSubtitle,
                            recentBadge = if (todayMusicCount > 0) "+$todayMusicCount" else null,
                            hasRatingStar = false,
                            imageUrl = resolvedMusicImage,
                            cardColor = domainMusicContainer(),
                            onCardColor = domainMusicOnContainer(),
                            icon = Icons.Default.MusicNote,
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

                                CompactExpressiveStatsCard(
                                    categoryTitle = slide.categoryTitle,
                                    mainValue = slide.mainValue,
                                    unit = slide.unit,
                                    recentTitle = slide.recentTitle,
                                    recentSubtitle = slide.recentSubtitle,
                                    recentBadge = slide.recentBadge,
                                    hasRatingStar = slide.hasRatingStar,
                                    imageUrl = slide.imageUrl,
                                    goalProgress = slide.goalProgress,
                                    goalLabel = slide.goalLabel,
                                    cardColor = slide.cardColor,
                                    onCardColor = slide.onCardColor,
                                    icon = slide.icon,
                                    onClick = slide.onClick,
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
                                        targetValue = if (isSelected) slide.cardColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
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

private data class CompactHeroSlide(
    val id: String,
    val categoryTitle: String,
    val mainValue: String,
    val unit: String,
    val recentTitle: String,
    val recentSubtitle: String,
    val recentBadge: String? = null,
    val hasRatingStar: Boolean = false,
    val imageUrl: String? = null,
    val goalProgress: Float? = null,
    val goalLabel: String? = null,
    val cardColor: Color,
    val onCardColor: Color = Color.White,
    val icon: ImageVector,
    val onClick: () -> Unit
)

@Composable
fun CompactExpressiveStatsCard(
    categoryTitle: String,
    mainValue: String,
    unit: String,
    recentTitle: String,
    recentSubtitle: String,
    recentBadge: String? = null,
    hasRatingStar: Boolean = false,
    imageUrl: String? = null,
    goalProgress: Float? = null,
    goalLabel: String? = null,
    cardColor: Color,
    onCardColor: Color = Color.White,
    icon: ImageVector,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Saf siyah (#000000) yerine M3 Expressive tonal katman renkleri
    val tonalSurface = Color.Black.copy(alpha = 0.22f)
    val tonalHighlight = Color.White.copy(alpha = 0.14f)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(156.dp)
            .expressiveBounceClick(onClick = onClick),
        shape = RoundedCornerShape(26.dp),
        color = cardColor,
        contentColor = onCardColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. ÜST BLOK: Sol tarafta Kategori + Hero Rakam, Sağda Aksiyon & Progress
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // Sol Sütun: Başlık ve Büyük İstatistik Sıkı Hiyerarşi
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = onCardColor.copy(alpha = 0.85f)
                        )
                        Text(
                            text = categoryTitle,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = onCardColor.copy(alpha = 0.85f)
                            )
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = mainValue,
                            fontSize = 42.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-1.5).sp,
                            lineHeight = 42.sp
                        )
                        Text(
                            text = unit,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Medium,
                                color = onCardColor.copy(alpha = 0.8f)
                            ),
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                }

                // Sağ Sütun: Ok Butonu ve (Varsa) Kompakt Kalori Hedef Barı
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(tonalSurface),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowOutward,
                            contentDescription = "Aç",
                            modifier = Modifier.size(16.dp),
                            tint = onCardColor
                        )
                    }

                    if (goalProgress != null && goalLabel != null) {
                        val animatedProgress by animateFloatAsState(
                            targetValue = goalProgress,
                            animationSpec = M3ExpressiveMotion.defaultSpatial(),
                            label = "compact_goal_progress"
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(tonalSurface)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            LinearProgressIndicator(
                                progress = { animatedProgress },
                                modifier = Modifier
                                    .width(44.dp)
                                    .height(6.dp),
                                color = Color(0xFF81C784),
                                trackColor = tonalHighlight,
                                strokeCap = StrokeCap.Round
                            )
                            Text(
                                text = goalLabel,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = onCardColor.copy(alpha = 0.9f)
                            )
                        }
                    }
                }
            }

            // 2. ALT BLOK: "SON EKLENEN" yazısı olmayan, 52.dp yüksekliğinde kompakt medya kapsülü
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp), // 26.dp dış köşe - 12.dp = 14.dp eşmerkezli iç köşe
                color = tonalSurface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        // Kompakt Kapak / İkon (36x36.dp)
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(tonalHighlight),
                            contentAlignment = Alignment.Center
                        ) {
                            if (imageUrl?.isNotBlank() == true) {
                                AsyncImage(
                                    model = imageUrl,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = onCardColor
                                )
                            }
                        }

                        // Sadece Başlık ve Alt Başlık (2 satır, maksimum sadelik)
                        Column(verticalArrangement = Arrangement.Center) {
                            Text(
                                text = recentTitle,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    lineHeight = 16.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (recentSubtitle.isNotBlank()) {
                                Text(
                                    text = recentSubtitle,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = onCardColor.copy(alpha = 0.72f)
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // Sağ Rozet (★ 5.0 veya 444 kcal)
                    if (recentBadge != null) {
                        Surface(
                            shape = CircleShape,
                            color = tonalHighlight
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                if (hasRatingStar || recentBadge.contains("5.0")) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = null,
                                        modifier = Modifier.size(11.dp),
                                        tint = Color(0xFFFFD54F)
                                    )
                                }
                                Text(
                                    text = recentBadge,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = onCardColor
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121316, widthDp = 412)
@Composable
fun CompactExpressivePreview() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF121316))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Kitaplar (Goodreads kütüphane arşivi yazısı kaldırıldı)
        CompactExpressiveStatsCard(
            categoryTitle = "Kitaplar • Goodreads",
            mainValue = "36",
            unit = "kitap",
            recentTitle = "Dune (Dune, #1)",
            recentSubtitle = "Frank Herbert",
            recentBadge = "5.0",
            cardColor = Color(0xFF00569E),
            icon = Icons.AutoMirrored.Filled.MenuBook
        )

        // 2. Filmler (Letterboxd izleme günlüğü yazısı kaldırıldı)
        CompactExpressiveStatsCard(
            categoryTitle = "Filmler • Letterboxd",
            mainValue = "16",
            unit = "film",
            recentTitle = "Obsession",
            recentSubtitle = "2025",
            recentBadge = "5.0",
            cardColor = Color(0xFF4527A0),
            icon = Icons.Default.Movie
        )

        // 3. Müzik (Dinleme ve scrobble arşivi yazısı kaldırıldı)
        CompactExpressiveStatsCard(
            categoryTitle = "Müzik & Ses • Last.fm",
            mainValue = "60",
            unit = "parça",
            recentTitle = "yastıgımda saclar",
            recentSubtitle = "Mavi & Nazli Mcfian • VERITAS II",
            cardColor = Color(0xFFB71C1C),
            icon = Icons.Default.MusicNote
        )

        // 4. Beslenme (Tam genişlikli bar yerine sağda kompakt hedef kapsülü)
        CompactExpressiveStatsCard(
            categoryTitle = "Beslenme • FatSecret",
            mainValue = "444",
            unit = "kcal",
            goalProgress = 0.44f,
            goalLabel = "/ 1000",
            recentTitle = "Biber Dolması",
            recentSubtitle = "Lunch",
            recentBadge = "444 kcal",
            cardColor = Color(0xFF1E6829),
            icon = Icons.Default.Restaurant
        )
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
