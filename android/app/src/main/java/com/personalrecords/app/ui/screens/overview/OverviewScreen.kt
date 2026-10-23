package com.personalrecords.app.ui.screens.overview

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp),
                    contentPadding = PaddingValues(
                        top = 8.dp,
                        bottom = 144.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    ),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
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
                        val todayCal = today["fatsecret_calories"]?.toString()?.replace("\"", "")
                            ?: today["calories"]?.toString()?.replace("\"", "") ?: "0"
                        val todayEntries = today["fatsecret_entry_count"]?.toString()?.replace("\"", "")
                            ?: today["entries_count"]?.toString()?.replace("\"", "") ?: "0"

                        val booksCount = counts["goodreads_books"] ?: counts["books"] ?: 0
                        val filmsCount = counts["letterboxd_films"] ?: counts["films"] ?: 0
                        val musicCount = counts["music_scrobbles"] ?: counts["music"] ?: 0

                        val latestBook = remember(state.recentActivities) {
                            state.recentActivities.firstOrNull { it.source == "goodreads" || it.type == "book" }
                        }
                        val latestFilm = remember(state.recentActivities) {
                            state.recentActivities.firstOrNull { it.source == "letterboxd" || it.type == "film" }
                        }
                        val latestMusic = remember(state.recentActivities) {
                            state.recentActivities.firstOrNull { it.source == "music" || it.source == "lastfm" || it.type == "music" || it.type == "scrobble" }
                        }

                        val nutritionSlide = HeroCarouselSlide(
                            id = "nutrition",
                            title = stringResource(R.string.overview_metric_today_calories),
                            badgeText = "FatSecret",
                            count = todayCal,
                            unitText = "kcal",
                            subtitle = stringResource(R.string.overview_metric_meals_count, todayEntries),
                            actionText = stringResource(R.string.title_nutrition),
                            icon = Icons.Default.Restaurant,
                            containerColor = domainNutritionContainer(),
                            contentColor = domainNutritionOnContainer(),
                            accentColor = domainNutritionColor(),
                            onClick = onNavigateToNutrition
                        )

                        val booksSlide = HeroCarouselSlide(
                            id = "books",
                            title = stringResource(R.string.overview_metric_library),
                            badgeText = "Goodreads",
                            count = booksCount.toString(),
                            unitText = stringResource(R.string.books_badge_book).lowercase(),
                            subtitle = latestBook?.let { "${it.title} · ${it.subtitle}" }
                                ?: stringResource(R.string.overview_metric_books_note),
                            actionText = stringResource(R.string.overview_metric_library),
                            icon = Icons.AutoMirrored.Filled.MenuBook,
                            containerColor = domainBooksContainer(),
                            contentColor = domainBooksOnContainer(),
                            accentColor = domainBooksColor(),
                            onClick = onNavigateToBooks
                        )

                        val moviesSlide = HeroCarouselSlide(
                            id = "movies",
                            title = stringResource(R.string.overview_metric_watched),
                            badgeText = "Letterboxd",
                            count = filmsCount.toString(),
                            unitText = "films",
                            subtitle = latestFilm?.let {
                                if (it.subtitle.isNotBlank()) "${it.title} (${it.subtitle})" else it.title
                            } ?: stringResource(R.string.overview_metric_films_note),
                            actionText = stringResource(R.string.overview_metric_watched),
                            icon = Icons.Default.Movie,
                            containerColor = domainCinemaContainer(),
                            contentColor = domainCinemaOnContainer(),
                            accentColor = domainCinemaColor(),
                            onClick = onNavigateToMovies
                        )

                        val musicSlide = HeroCarouselSlide(
                            id = "music",
                            title = stringResource(R.string.title_music),
                            badgeText = "Last.fm",
                            count = musicCount.toString(),
                            unitText = "tracks",
                            subtitle = latestMusic?.let { "${it.title} · ${it.subtitle}" }
                                ?: stringResource(R.string.overview_metric_music_note),
                            actionText = stringResource(R.string.title_music),
                            icon = Icons.Default.MusicNote,
                            containerColor = domainMusicContainer(),
                            contentColor = domainMusicOnContainer(),
                            accentColor = domainMusicColor(),
                            onClick = onNavigateToMusic
                        )

                        val slides = listOf(nutritionSlide, booksSlide, moviesSlide, musicSlide)
                        val coroutineScope = rememberCoroutineScope()

                        Column(
                            modifier = expressiveItemModifier(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            HorizontalPager(
                                state = statsPagerState,
                                modifier = Modifier.fillMaxWidth(),
                                pageSpacing = 12.dp
                            ) { pageIndex ->
                                val slide = slides[pageIndex]
                                OverviewHeroCard(slide = slide)
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
                                                    .padding(horizontal = 14.dp, vertical = 11.dp),
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

                                                FilledTonalIconButton(
                                                    onClick = { viewModel.syncService(syncKey) },
                                                    enabled = state.isSyncing == null,
                                                    modifier = Modifier.size(34.dp),
                                                    shape = RoundedCornerShape(10.dp),
                                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                                                        contentColor = MaterialTheme.colorScheme.primary
                                                    )
                                                ) {
                                                    if (isItemSyncing) {
                                                        ExpressiveLoadingIndicator(
                                                            size = 14.dp,
                                                            strokeWidth = 2.dp,
                                                            color = MaterialTheme.colorScheme.primary
                                                        )
                                                    } else {
                                                        Icon(
                                                            imageVector = Icons.Default.Sync,
                                                            contentDescription = "Sync ${plugin.name}",
                                                            modifier = Modifier.size(16.dp)
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
                                    state.recentActivities.forEachIndexed { index, item ->
                                        key("${item.source}_${item.id.ifBlank { "${item.date}_${item.title}" }}") {
                                            val shape = groupedContainerShape(index, state.recentActivities.size)
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
                        Spacer(modifier = Modifier.height(16.dp))
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

private data class HeroCarouselSlide(
    val id: String,
    val title: String,
    val badgeText: String,
    val count: String,
    val unitText: String,
    val subtitle: String,
    val actionText: String,
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
            .expressiveBounceClick(onClick = slide.onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = slide.containerColor,
            contentColor = slide.contentColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp)
        ) {
            // Top Row: Icon + Title & Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(slide.contentColor.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = slide.icon,
                            contentDescription = null,
                            tint = slide.contentColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = slide.title,
                        style = MaterialTheme.typography.titleMediumEmphasized,
                        color = slide.contentColor
                    )
                }

                Surface(
                    shape = M3Shapes.CornerFull,
                    color = slide.contentColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = slide.badgeText,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmallEmphasized,
                        color = slide.contentColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Display Numeral + Unit
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = slide.count,
                    style = MaterialTheme.typography.displaySmallEmphasized,
                    color = slide.contentColor
                )
                Text(
                    text = slide.unitText,
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    color = slide.contentColor.copy(alpha = 0.8f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Footer Row: Subtitle + Action Link
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = slide.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = slide.contentColor.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = slide.actionText,
                        style = MaterialTheme.typography.labelSmallEmphasized,
                        color = slide.contentColor
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = slide.contentColor,
                        modifier = Modifier.size(14.dp)
                    )
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
