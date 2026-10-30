package com.personalrecords.app.ui.screens.music

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.personalrecords.app.PersonalRecordsApp
import com.personalrecords.app.R
import com.personalrecords.app.core.network.model.MusicScrobbleDto
import com.personalrecords.app.core.theme.*
import com.personalrecords.app.ui.components.*
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MusicScreen(
    viewModel: MusicViewModel,
    onNavigateBack: (() -> Unit)? = null
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val preferences = remember { PersonalRecordsApp.instance.preferences }
    val hiddenCardIds by preferences.hiddenStatsCards.collectAsStateWithLifecycle(initialValue = emptySet())
    val coroutineScope = rememberCoroutineScope()
    var isEditMode by remember { mutableStateOf(false) }
    var showCustomizationSheet by remember { mutableStateOf(false) }
    var targetCardId by remember { mutableStateOf<String?>(null) }
    var selectedScrobble by remember { mutableStateOf<MusicScrobbleDto?>(null) }
    val isTwoPane = currentAdaptiveLayout().showsDetailPane
    var showDatePicker by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
    val snackbarHostState = remember { SnackbarHostState() }

    val musicColor = recordCategoryColors(RecordCategory.Music).accent
    val cinemaColor = recordCategoryColors(RecordCategory.Movies).accent
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val musicTotalTitle = stringResource(R.string.stats_card_music_total_title)
    val musicTotalDesc = stringResource(R.string.stats_card_music_total_desc)
    val musicTodayTitle = stringResource(R.string.stats_card_music_today_title)
    val musicTodayDesc = stringResource(R.string.stats_card_music_today_desc)
    val musicArtistsTitle = stringResource(R.string.stats_card_music_artists_title)
    val musicArtistsDesc = stringResource(R.string.stats_card_music_artists_desc)
    val musicAlbumsTitle = stringResource(R.string.stats_card_music_albums_title)
    val musicAlbumsDesc = stringResource(R.string.stats_card_music_albums_desc)
    val musicStatCards = remember(musicColor, cinemaColor, secondaryColor, tertiaryColor, musicTotalTitle, musicTotalDesc, musicTodayTitle, musicTodayDesc, musicArtistsTitle, musicArtistsDesc, musicAlbumsTitle, musicAlbumsDesc) {
        listOf(
            StatCardItem(
                id = "music_total",
                title = musicTotalTitle,
                description = musicTotalDesc,
                icon = Icons.Rounded.MusicNote,
                iconTint = musicColor
            ),
            StatCardItem(
                id = "music_today",
                title = musicTodayTitle,
                description = musicTodayDesc,
                icon = Icons.Rounded.Today,
                iconTint = secondaryColor
            ),
            StatCardItem(
                id = "music_artists",
                title = musicArtistsTitle,
                description = musicArtistsDesc,
                icon = Icons.Rounded.Person,
                iconTint = tertiaryColor
            ),
            StatCardItem(
                id = "music_albums",
                title = musicAlbumsTitle,
                description = musicAlbumsDesc,
                icon = Icons.Rounded.Album,
                iconTint = cinemaColor
            )
        )
    }
    val statCardOrder = rememberStatCardOrder("music", musicStatCards.map { it.id })

    val availableSources = remember(state.scrobbles) {
        val presentSources = state.scrobbles
            .map { it.source.lowercase(Locale.ROOT).trim() }
            .filter { it.isNotBlank() }
            .distinct()

        if (presentSources.size > 1) {
            listOf("all") + presentSources
        } else {
            emptyList()
        }
    }

    LaunchedEffect(availableSources) {
        if (availableSources.isNotEmpty() && state.selectedFilter != "all" && state.selectedFilter !in availableSources) {
            viewModel.setFilter("all")
        }
    }

    LaunchedEffect(state.syncMessage) {
        state.syncMessage?.let {
            snackbarHostState.showSnackbar(it.resolve(context))
            viewModel.clearSyncMessage()
        }
    }

    if (showDatePicker) {
        ExpressiveDateFilterDialog(
            initialStartDate = state.selectedDateFilter ?: state.fromDateFilter,
            initialEndDate = state.toDateFilter,
            onDismissRequest = { showDatePicker = false },
            onDateSelected = { date ->
                viewModel.setDateFilter(date)
            },
            onDateRangeSelected = { start, end ->
                viewModel.setDateRangeFilter(start, end)
            },
            onClear = {
                viewModel.clearDateFilter()
            }
        )
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets.statusBars,
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp)
            )
        },
        topBar = {
            ExpressivePageTopAppBar(
                title = stringResource(R.string.title_music),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    if (onNavigateBack != null) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.action_back)
                            )
                        }
                    }
                },
                actions = {
                    TopAppBarStatsAction(
                        isEditMode = isEditMode,
                        hasHiddenCards = musicStatCards.any { it.id in hiddenCardIds },
                        onToggleEditMode = { isEditMode = !isEditMode },
                        onOpenCustomization = {
                            targetCardId = null
                            showCustomizationSheet = true
                        }
                    )
                },
            )
        }
    ) { innerPadding ->
        ExpressivePullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = {
                viewModel.refresh()
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val showSnackbar: (String) -> Unit = { msg ->
                coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
            }
            ExpressiveListDetailLayout(
                showDetailPane = isTwoPane,
                detailPane = {
                    val scrobble = selectedScrobble
                    if (scrobble != null) {
                        MusicDetailPaneCard(
                            scrobble = scrobble,
                            onClose = { selectedScrobble = null },
                            onShowSnackbar = showSnackbar
                        )
                    } else {
                        DetailPlaceholderPane(
                            icon = Icons.Rounded.MusicNote,
                            title = stringResource(R.string.adaptive_select_item_title),
                            subtitle = stringResource(R.string.adaptive_select_item_subtitle)
                        )
                    }
                }
            ) {
                if (state.isLoading && state.scrobbles.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        AppContainedLoadingIndicator()
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = FilterGutter,
                            end = FilterGutter,
                            top = 8.dp,
                            bottom = 24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                        )
                    ) {
                        // 1. Metrics Grid
                        item(key = "music_metrics") {
                            MusicMetricsHeader(
                                totalScrobbles = state.stats.totalScrobbles,
                                todayScrobbles = state.stats.todayScrobbles,
                                uniqueArtists = state.stats.uniqueArtists,
                                uniqueAlbums = state.stats.uniqueAlbums,
                                hiddenCardIds = hiddenCardIds,
                                cardOrder = statCardOrder.orderedIds,
                                onReorderCards = { statCardOrder.setVisibleOrder(it) },
                                isEditMode = isEditMode,
                                onHideCard = { cardId ->
                                    coroutineScope.launch {
                                        preferences.toggleStatCardVisibility(cardId, false)
                                    }
                                },
                                onStartEditMode = { isEditMode = true },
                                onExitEditMode = { isEditMode = false },
                                onOpenCustomization = {
                                    targetCardId = null
                                    showCustomizationSheet = true
                                }
                            )
                        }


                        state.error?.let { err ->
                            item(key = "music_error_banner", contentType = "error_banner") {
                                ExpressiveErrorBanner(
                                    message = err,
                                    onRetry = { viewModel.loadData() },
                                    modifier = expressiveItemModifier().padding(bottom = 8.dp)
                                )
                            }
                        }

                        // 3. Search and Filters
                        stickyHeader(key = "search_and_filters", contentType = "sticky_header") {
                            ExpressiveFilterHeader {
                                ExpressiveSearchField(
                                    value = state.searchQuery,
                                    onValueChange = { viewModel.setSearchQuery(it) },
                                    placeholder = stringResource(R.string.music_search_hint),
                                    clearContentDescription = stringResource(R.string.action_clear),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .filterHeaderItem()
                                )
                                if (availableSources.size > 1) {
                                    ExpressiveFilterRow {
                                        ExpressiveConnectedButtonGroup(
                                            items = availableSources,
                                            selectedItem = if (state.selectedFilter in availableSources) state.selectedFilter else "all",
                                            onItemSelected = { key -> viewModel.setFilter(key) },
                                            labelProvider = { key ->
                                                when (key) {
                                                    "lastfm" -> stringResource(R.string.music_filter_lastfm)
                                                    "listenbrainz" -> stringResource(R.string.music_filter_listenbrainz)
                                                    "statsfm" -> stringResource(R.string.music_filter_statsfm)
                                                    "all" -> stringResource(R.string.filter_all)
                                                    else -> musicSourceLabel(key)
                                                }
                                            },
                                            iconProvider = { key, _ ->
                                                when (key) {

                                                    else -> null
                                                }
                                            }
                                        )
                                    }
                                }
                                val hasActiveDate = state.selectedDateFilter != null || state.fromDateFilter != null
                                val hasActiveFilters = state.searchQuery.isNotEmpty() || hasActiveDate || state.selectedFilter != "all" || state.selectedArtist != null
                                ExpressiveFilterRow {
                                    val artistCounts = remember(state.stats.topArtists) {
                                        state.stats.topArtists.take(20).associate { it.artist to it.count }
                                    }
                                    val artistOptions = remember(artistCounts, state.selectedArtist) {
                                        (listOf<String?>(null) + artistCounts.keys + listOfNotNull(state.selectedArtist)).distinct()
                                    }
                                    if (artistOptions.size > 1) {
                                        ExpressiveDropdownFilterChip(
                                            options = artistOptions,
                                            selected = state.selectedArtist,
                                            onSelect = { viewModel.setArtistFilter(it) },
                                            optionLabel = { artist ->
                                                when {
                                                    artist == null -> stringResource(R.string.filter_all)
                                                    artistCounts[artist] != null -> "$artist (${artistCounts[artist]})"
                                                    else -> artist
                                                }
                                            },
                                            label = state.selectedArtist ?: stringResource(R.string.filter_artist),
                                            leadingIcon = Icons.Rounded.Person,
                                            isActive = state.selectedArtist != null
                                        )
                                    }
                                    // Then sort, ahead of the clear chip so it keeps its place.
                                    ExpressiveDropdownFilterChip(
                                        options = listOf("date_desc", "date_asc", "track", "artist"),
                                        selected = state.sortBy,
                                        onSelect = { viewModel.setSortBy(it) },
                                        optionLabel = { key ->
                                            when (key) {
                                                "date_desc" -> stringResource(R.string.sort_newest)
                                                "date_asc" -> stringResource(R.string.sort_oldest)
                                                "track" -> stringResource(R.string.sort_track)
                                                "artist" -> stringResource(R.string.sort_artist)
                                                else -> stringResource(R.string.action_sort)
                                            }
                                        },
                                        leadingIcon = Icons.AutoMirrored.Rounded.Sort,
                                        isActive = false
                                    )
                                    if (hasActiveFilters) {
                                        ExpressiveClearFiltersChip(onClick = { viewModel.resetFilters() })
                                    }
                                    ExpressiveQuickDateFilters(
                                        selectedDate = state.selectedDateFilter,
                                        fromDate = state.fromDateFilter,
                                        toDate = state.toDateFilter,
                                        onPickDate = { showDatePicker = true },
                                        onClearDate = { viewModel.clearDateFilter() },
                                        onQuickRange = { range ->
                                            applyQuickRange(range, viewModel::setDateFilter, viewModel::setDateRangeFilter)
                                        }
                                    )
                                }
                                if (hasActiveFilters) {
                                    ExpressiveFilterResultCount(count = state.filteredScrobbles.size)
                                }
                            }
                        }

                        // 4. Loading or Empty State
                        if ((state.isLoading || state.isFiltering) && state.filteredScrobbles.isEmpty()) {
                            item(key = "music_loading") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(200.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AppContainedLoadingIndicator(size = 44.dp)
                                }
                            }
                        } else if (state.filteredScrobbles.isEmpty() && state.error == null) {
                            item(key = "empty_state") {
                                EmptyMusicView(
                                    onSyncClick = { viewModel.syncLastFm() },
                                    isSyncing = state.isSyncingLastFm
                                )
                            }
                        } else {
                            // 5. Scrobble Items List
                            itemsIndexed(
                                items = state.filteredScrobbles,
                                key = { _, scrobble -> scrobble.id },
                                contentType = { _, _ -> "scrobble_row" }
                            ) { index, scrobble ->
                                ScrobbleListItem(
                                    scrobble = scrobble,
                                    onClick = { selectedScrobble = scrobble },
                                    index = index,
                                    count = state.filteredScrobbles.size,
                                    addLeadingGap = true,
                                    modifier = expressiveItemModifier(),
                                    selected = if (isTwoPane) selectedScrobble == scrobble else null
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (!isTwoPane) {
        selectedScrobble?.let { scrobble ->
            MusicDetailBottomSheet(
                scrobble = scrobble,
                onDismiss = { selectedScrobble = null },
                onShowSnackbar = { msg ->
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(msg)
                    }
                }
            )
        }
    }

    if (showCustomizationSheet) {
        StatsCustomizationBottomSheet(
            sheetTitle = stringResource(R.string.stats_customization_title),
            cards = statCardOrder.sort(musicStatCards),
            onReorder = { ids -> statCardOrder.setOrder(ids) },
            hiddenCardIds = hiddenCardIds,
            targetCardId = targetCardId,
            onToggleCard = { cardId, visible ->
                coroutineScope.launch {
                    preferences.toggleStatCardVisibility(cardId, visible)
                }
            },
            onResetDefault = {
                coroutineScope.launch {
                    preferences.resetHiddenStatsCards(musicStatCards.map { it.id })
                    statCardOrder.reset()
                }
            },
            onDismissRequest = {
                showCustomizationSheet = false
                targetCardId = null
            }
        )





















































    }
}

@Composable
private fun MusicMetricsHeader(
    totalScrobbles: Int,
    todayScrobbles: Int,
    uniqueArtists: Int,
    uniqueAlbums: Int,
    hiddenCardIds: Set<String>,
    cardOrder: List<String>,
    onReorderCards: (List<String>) -> Unit,
    isEditMode: Boolean,
    onHideCard: (String) -> Unit,
    onStartEditMode: () -> Unit,
    onExitEditMode: () -> Unit,
    onOpenCustomization: () -> Unit
) {
    val isTotalVisible = "music_total" !in hiddenCardIds
    val isTodayVisible = "music_today" !in hiddenCardIds
    val isArtistsVisible = "music_artists" !in hiddenCardIds
    val isAlbumsVisible = "music_albums" !in hiddenCardIds

    val hasRow1 = isTotalVisible || isTodayVisible
    val hasRow2 = isArtistsVisible || isAlbumsVisible

    LaunchedEffect(isTotalVisible, isTodayVisible, isArtistsVisible, isAlbumsVisible) {
        if (!isTotalVisible && !isTodayVisible && !isArtistsVisible && !isAlbumsVisible) {
            onExitEditMode()
        }
    }

    StatCardGrid(
        cardIds = cardOrder.filter { it !in hiddenCardIds },
        isEditMode = isEditMode,
        onHideCard = onHideCard,
        onReorder = onReorderCards,
        modifier = Modifier.padding(vertical = 8.dp)
    ) { cardId ->
        when (cardId) {
            "music_total" -> {
                MetricCard(
                    title = stringResource(R.string.music_stats_total),
                    value = totalScrobbles.toString(),
                    badgeText = "",
                    note = "Scrobbles",
                    valueIcon = Icons.Rounded.MusicNote,
                    valueIconTint = recordCategoryColors(RecordCategory.Music).accent,
                    badgeBg = recordCategoryColors(RecordCategory.Music).container,
                    badgeColor = recordCategoryColors(RecordCategory.Music).onContainer,
                    onLongClick = onStartEditMode
                )
            }
            "music_today" -> {
                MetricCard(
                    title = stringResource(R.string.music_stats_today),
                    value = todayScrobbles.toString(),
                    badgeText = "",
                    note = "Tracks",
                    valueIcon = Icons.Rounded.Today,
                    valueIconTint = MaterialTheme.colorScheme.secondary,
                    badgeBg = MaterialTheme.colorScheme.secondaryContainer,
                    badgeColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    onLongClick = onStartEditMode
                )
            }
            "music_artists" -> {
                MetricCard(
                    title = stringResource(R.string.music_stats_artists),
                    value = uniqueArtists.toString(),
                    badgeText = "",
                    note = "Unique",
                    valueIcon = Icons.Rounded.Person,
                    valueIconTint = MaterialTheme.colorScheme.tertiary,
                    badgeBg = MaterialTheme.colorScheme.tertiaryContainer,
                    badgeColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    onLongClick = onStartEditMode
                )
            }
            "music_albums" -> {
                MetricCard(
                    title = stringResource(R.string.music_stats_albums),
                    value = uniqueAlbums.toString(),
                    badgeText = "",
                    note = "Unique",
                    valueIcon = Icons.Rounded.Album,
                    valueIconTint = recordCategoryColors(RecordCategory.Movies).accent,
                    badgeBg = recordCategoryColors(RecordCategory.Movies).container,
                    badgeColor = recordCategoryColors(RecordCategory.Movies).onContainer,
                    onLongClick = onStartEditMode
                )
            }
        }
    }
}

@Composable
private fun ScrobbleListItem(
    scrobble: MusicScrobbleDto,
    index: Int = 0,
    count: Int = 1,
    modifier: Modifier = Modifier,
    selected: Boolean? = null,
    addLeadingGap: Boolean = false,
    onClick: () -> Unit
) {

    // Same row anatomy as Books / Movies / Health: 12x10 padding, 40dp leading, titleSmall + bodySmall.
    val musicColors = recordCategoryColors(RecordCategory.Music)
    
    ExpressiveListRow(
        index = index,
        count = count,
        modifier = modifier,
        selected = selected,
        addLeadingGap = addLeadingGap,
        onClick = onClick,
        verticalAlignment = Alignment.CenterVertically,
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(musicColors.container),
                contentAlignment = Alignment.Center
            ) {
                if (scrobble.imageUrl.isNotBlank()) {
                    AsyncImage(
                        model = scrobble.imageUrl,
                        contentDescription = scrobble.track,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = musicColors.onContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        },
        trailingContent = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val sourceLabel = when {
                    scrobble.source.contains("lastfm", ignoreCase = true) -> "Last.fm"
                    scrobble.source.contains("listenbrainz", ignoreCase = true) -> "ListenBrainz"
                    scrobble.source.contains("statsfm", ignoreCase = true) -> "Stats.fm"
                    else -> musicSourceLabel(scrobble.source)
                }

                // Source is metadata, not a status: neutral tonal pill instead of brand/error colors.
                Surface(
                    shape = M3Shapes.CornerFull,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest
                ) {
                    Text(
                        text = sourceLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.5.dp)
                    )
                }

                if (scrobble.formattedTime.isNotBlank()) {
                    Text(
                        text = scrobble.formattedTime,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        supportingContent = {
            Column {
                Text(
                    text = scrobble.artist + if (scrobble.album.isNotBlank()) " • ${scrobble.album}" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    ) {
        Text(
            text = scrobble.track,
            style = MaterialTheme.typography.titleSmallEmphasized,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun EmptyMusicView(
    onSyncClick: () -> Unit,
    isSyncing: Boolean
) {
    val musicColors = recordCategoryColors(RecordCategory.Music)
    ExpressiveEmptyState(
        title = stringResource(R.string.music_empty_title),
        message = stringResource(R.string.music_empty_desc),
        icon = Icons.Rounded.MusicNote,
        iconContainerColor = musicColors.container,
        iconContentColor = musicColors.onContainer,
        action = {
            Button(
                onClick = onSyncClick,
                enabled = !isSyncing,
                shapes = expressiveButtonShapes()
            ) {
                Icon(
                    imageVector = Icons.Rounded.Sync,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isSyncing) stringResource(R.string.music_syncing) else stringResource(R.string.music_sync_lastfm),
                    style = MaterialTheme.typography.labelLargeEmphasized
                )
            }
        }
    )
}

@Composable
fun MusicDetailContent(
    scrobble: MusicScrobbleDto,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onShowSnackbar: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val notify = { message: String ->
        if (onShowSnackbar != null) {
            onShowSnackbar(message)
        } else {
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header Row: Album Art + Track Title
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                if (scrobble.imageUrl.isNotBlank()) {
                    AsyncImage(
                        model = scrobble.imageUrl,
                        contentDescription = scrobble.track,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = scrobble.track,
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = scrobble.artist,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        // Details List
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (scrobble.album.isNotBlank()) {
                DetailInfoRow(
                    label = stringResource(R.string.music_detail_album),
                    value = scrobble.album,
                    icon = Icons.Rounded.Album
                )
            }

            if (scrobble.formattedDuration.isNotBlank()) {
                DetailInfoRow(
                    label = stringResource(R.string.music_detail_duration),
                    value = scrobble.formattedDuration,
                    icon = Icons.Rounded.Timer
                )
            }

            val displaySource = when {
                scrobble.source.contains("lastfm", ignoreCase = true) -> "Last.fm"
                scrobble.source.contains("listenbrainz", ignoreCase = true) -> "ListenBrainz"
                scrobble.source.contains("statsfm", ignoreCase = true) -> "Stats.fm"
                else -> musicSourceLabel(scrobble.source)
            }
            DetailInfoRow(
                label = stringResource(R.string.music_detail_source),
                value = displaySource,
                icon = Icons.Rounded.Source
            )

            if (scrobble.formattedDate.isNotBlank()) {
                DetailInfoRow(
                    label = stringResource(R.string.music_detail_time),
                    value = "${scrobble.formattedDate}  ${scrobble.formattedTime}",
                    icon = Icons.Rounded.Schedule
                )
            }
        }

        // Action Buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("Track", "${scrobble.artist} - ${scrobble.track}")
                    clipboard.setPrimaryClip(clip)
                    // Without a snackbar host, Android 13+ already confirms the copy itself.
                    if (onShowSnackbar != null || android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
                        notify(context.getString(R.string.music_copied, "${scrobble.artist} - ${scrobble.track}"))
                    }
                },
                shapes = expressiveButtonShapes(),
                modifier = Modifier.weight(1f)
            ) {
                Icon(imageVector = Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.action_copy))
            }

            if (scrobble.originUrl.isNotBlank()) {
                Button(
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(scrobble.originUrl))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            notify(context.getString(R.string.music_open_failed))
                        }
                    },
                    shapes = expressiveButtonShapes(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(imageVector = Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.action_open))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicDetailBottomSheet(
    scrobble: MusicScrobbleDto,
    onDismiss: () -> Unit,
    onShowSnackbar: ((String) -> Unit)? = null
) {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        MusicDetailContent(
            scrobble = scrobble,
            onDismiss = onDismiss,
            onShowSnackbar = onShowSnackbar,
            modifier = Modifier.navigationBarsPadding()
        )
    }
}

@Composable
fun MusicDetailPaneCard(
    scrobble: MusicScrobbleDto,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onShowSnackbar: ((String) -> Unit)? = null
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 12.dp)
        ) {
            MusicDetailContent(
                scrobble = scrobble,
                onDismiss = onClose,
                onShowSnackbar = onShowSnackbar
            )
        }
    }
}

@Composable
private fun DetailInfoRow(
    label: String,
    value: String,
    icon: ImageVector
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(100.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMediumEmphasized,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

/** Display name for a scrobble source key such as "web_scrobbler". */
private fun musicSourceLabel(source: String): String = when {
    source.contains("scrobbler", ignoreCase = true) -> "Web Scrobbler"
    else -> source.replace('_', ' ').replaceFirstChar { it.uppercase() }
}
