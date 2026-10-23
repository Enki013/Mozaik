package com.personalrecords.app.ui.screens.music

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
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
import com.personalrecords.app.R
import com.personalrecords.app.core.network.model.MusicScrobbleDto
import com.personalrecords.app.core.theme.*
import com.personalrecords.app.ui.components.*
import kotlinx.coroutines.launch

private val MusicSourceKeys = listOf("all", "lastfm", "listenbrainz", "statsfm")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicScreen(
    viewModel: MusicViewModel,
    onNavigateBack: (() -> Unit)? = null
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedScrobble by remember { mutableStateOf<MusicScrobbleDto?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.syncMessage) {
        state.syncMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSyncMessage()
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
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
                    .padding(bottom = 96.dp)
            )
        },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.title_music),
                        style = MaterialTheme.typography.titleLargeEmphasized
                    )
                },
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    if (onNavigateBack != null) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_back)
                            )
                        }
                    }
                },
                actions = {},
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        var isRefreshing by remember { mutableStateOf(false) }
        LaunchedEffect(state.isLoading) {
            if (!state.isLoading) isRefreshing = false
        }

        ExpressivePullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                viewModel.refresh()
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (state.isLoading && state.scrobbles.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    ContainedLoadingIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 144.dp)
                ) {
                    // 1. Metrics Grid
                    item(key = "music_metrics") {
                        MusicMetricsHeader(
                            totalScrobbles = state.stats.totalScrobbles,
                            todayScrobbles = state.stats.todayScrobbles,
                            uniqueArtists = state.stats.uniqueArtists,
                            uniqueAlbums = state.stats.uniqueAlbums
                        )
                    }

                    // 2. Top Artists mini scroll if available
                    if (state.stats.topArtists.isNotEmpty()) {
                        item(key = "top_artists") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp, bottom = 4.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.music_top_artists),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState())
                                        .padding(horizontal = 16.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    state.stats.topArtists.take(8).forEach { artist ->
                                        val isSelected = state.selectedArtist == artist.artist
                                        ExpressiveFilterChip(
                                            selected = isSelected,
                                            onClick = {
                                                viewModel.setArtistFilter(if (isSelected) null else artist.artist)
                                            },
                                            label = {
                                                Text(
                                                    text = "${artist.artist} (${artist.count})",
                                                    style = MaterialTheme.typography.labelMedium
                                                )
                                            },
                                            leadingIcon = {
                                                if (artist.imageUrl.isNotBlank()) {
                                                    AsyncImage(
                                                        model = artist.imageUrl,
                                                        contentDescription = null,
                                                        modifier = Modifier
                                                            .size(18.dp)
                                                            .clip(CircleShape),
                                                        contentScale = ContentScale.Crop
                                                    )
                                                } else {
                                                    Icon(
                                                        imageVector = Icons.Default.Person,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 3. Search and Filters
                    item(key = "search_and_filters") {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = state.searchQuery,
                                    onValueChange = { viewModel.setSearchQuery(it) },
                                    placeholder = {
                                        Text(
                                            text = stringResource(R.string.music_search_hint),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Search,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    },
                                    trailingIcon = {
                                        if (state.searchQuery.isNotBlank()) {
                                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                                Icon(
                                                    imageVector = Icons.Default.Clear,
                                                    contentDescription = stringResource(R.string.action_clear),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    },
                                    singleLine = true,
                                    shape = M3Shapes.CornerFull,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                // Filter Chips Row
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val hasActiveDate = state.selectedDateFilter != null || state.fromDateFilter != null
                                    val dateLabel = formatDateRangeChipLabel(
                                        singleDate = state.selectedDateFilter,
                                        fromDate = state.fromDateFilter,
                                        toDate = state.toDateFilter,
                                        defaultText = stringResource(R.string.filter_pick_date)
                                    )

                                    ExpressiveFilterChip(
                                        selected = hasActiveDate,
                                        onClick = { showDatePicker = true },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = if (hasActiveDate) Icons.Default.DateRange else Icons.Default.CalendarMonth,
                                                contentDescription = null,
                                                modifier = Modifier.size(FilterChipDefaults.IconSize)
                                            )
                                        },
                                        trailingIcon = if (hasActiveDate) {
                                            {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = null,
                                                    modifier = Modifier
                                                        .size(14.dp)
                                                        .clickable {
                                                            viewModel.clearDateFilter()
                                                        }
                                                )
                                            }
                                        } else null,
                                        label = {
                                            Text(
                                                text = dateLabel,
                                                style = MaterialTheme.typography.labelMedium
                                            )
                                        }
                                    )

                                    ExpressiveConnectedButtonGroup(
                                        items = MusicSourceKeys,
                                        selectedItem = state.selectedFilter,
                                        onItemSelected = { source ->
                                            if (state.selectedFilter != source) {
                                                viewModel.setFilter(source)
                                            }
                                        },
                                        labelProvider = { source ->
                                            when (source) {
                                                "lastfm" -> stringResource(R.string.music_filter_lastfm)
                                                "listenbrainz" -> stringResource(R.string.music_filter_listenbrainz)
                                                "statsfm" -> stringResource(R.string.music_filter_statsfm)
                                                else -> stringResource(R.string.music_filter_all)
                                            }
                                        },
                                        iconProvider = { source, _ ->
                                            when (source) {
                                                "lastfm" -> Icons.Default.CloudSync
                                                "listenbrainz" -> Icons.Default.GraphicEq
                                                "statsfm" -> Icons.Default.Equalizer
                                                else -> Icons.Default.LibraryMusic
                                            }
                                        }
                                    )

                                    if (state.selectedArtist != null) {
                                        ExpressiveFilterChip(
                                            selected = true,
                                            onClick = { viewModel.setArtistFilter(null) },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.Person,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(FilterChipDefaults.IconSize)
                                                )
                                            },
                                            trailingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            },
                                            label = {
                                                Text(
                                                    text = state.selectedArtist ?: "",
                                                    style = MaterialTheme.typography.labelMedium
                                                )
                                            }
                                        )
                                    }

                                    val (sortLabel, sortIcon) = when (state.sortBy) {
                                        "date_asc" -> stringResource(R.string.sort_oldest) to Icons.Default.ArrowUpward
                                        "track" -> stringResource(R.string.sort_track) to Icons.Default.SortByAlpha
                                        "artist" -> stringResource(R.string.sort_artist) to Icons.Default.Person
                                        else -> stringResource(R.string.sort_newest) to Icons.Default.ArrowDownward
                                    }
                                    ExpressiveAssistChip(
                                        onClick = {
                                            val nextSort = when (state.sortBy) {
                                                "date_desc" -> "date_asc"
                                                "date_asc" -> "track"
                                                "track" -> "artist"
                                                else -> "date_desc"
                                            }
                                            viewModel.setSortBy(nextSort)
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = sortIcon,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        label = {
                                            Text(
                                                text = sortLabel,
                                                style = MaterialTheme.typography.labelMediumEmphasized,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        },
                                        colors = borderlessAssistChipColors()
                                    )
                                }
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
                                ContainedLoadingIndicator(size = 44.dp)
                            }
                        }
                    } else if (state.filteredScrobbles.isEmpty()) {
                        item(key = "empty_state") {
                            EmptyMusicView(
                                onSyncClick = { viewModel.syncLastFm() },
                                isSyncing = state.isSyncingLastFm
                            )
                        }
                    } else {
                        // 5. Scrobble Items List
                        items(
                            items = state.filteredScrobbles,
                            key = { it.id }
                        ) { scrobble ->
                            ScrobbleListItem(
                                scrobble = scrobble,
                                onClick = { selectedScrobble = scrobble }
                            )
                        }
                    }
                }
            }
        }
    }

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

@Composable
private fun MusicMetricsHeader(
    totalScrobbles: Int,
    todayScrobbles: Int,
    uniqueArtists: Int,
    uniqueAlbums: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        MetricCard(
            title = stringResource(R.string.music_stats_total),
            value = "$totalScrobbles",
            badgeText = stringResource(R.string.music_filter_lastfm),
            note = stringResource(R.string.music_stats_artists_count, uniqueArtists),
            modifier = Modifier.weight(1f),
            badgeBg = domainMusicContainer(),
            badgeColor = domainMusicOnContainer()
        )
        MetricCard(
            title = stringResource(R.string.music_stats_today),
            value = "$todayScrobbles",
            badgeText = stringResource(R.string.music_stats_today),
            note = stringResource(R.string.music_stats_albums_count, uniqueAlbums),
            modifier = Modifier.weight(1f),
            badgeBg = domainRatingContainer(),
            badgeColor = domainRatingOnContainer()
        )
    }
}

@Composable
private fun ScrobbleListItem(
    scrobble: MusicScrobbleDto,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .expressiveBounceClick(onClick = onClick),
        shape = M3Shapes.CornerLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Album Art Thumbnail
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(10.dp))
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
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Track, Artist, Album Info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = scrobble.track,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = scrobble.artist + if (scrobble.album.isNotBlank()) " • ${scrobble.album}" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Source & Time Badge
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val (sourceLabel, sourceColor) = when {
                    scrobble.source.contains("lastfm", ignoreCase = true) -> "Last.fm" to MaterialTheme.colorScheme.error
                    scrobble.source.contains("listenbrainz", ignoreCase = true) -> "ListenBrainz" to domainBooksColor()
                    scrobble.source.contains("statsfm", ignoreCase = true) -> "Stats.fm" to domainNutritionColor()
                    else -> scrobble.source.replaceFirstChar { it.uppercase() } to MaterialTheme.colorScheme.primary
                }

                Surface(
                    shape = M3Shapes.CornerFull,
                    color = sourceColor.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = sourceLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = sourceColor,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                if (scrobble.formattedTime.isNotBlank()) {
                    Text(
                        text = scrobble.formattedTime,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyMusicView(
    onSyncClick: () -> Unit,
    isSyncing: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        shape = M3Shapes.CornerExtraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(32.dp)
                )
            }

            Text(
                text = stringResource(R.string.music_empty_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = stringResource(R.string.music_empty_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Button(
                onClick = onSyncClick,
                enabled = !isSyncing,
                shape = M3Shapes.CornerFull,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Sync,
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
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicDetailBottomSheet(
    scrobble: MusicScrobbleDto,
    onDismiss: () -> Unit,
    onShowSnackbar: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val notify = { message: String ->
        if (onShowSnackbar != null) {
            onShowSnackbar(message)
        } else {
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = M3Shapes.CornerExtraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .navigationBarsPadding(),
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
                            imageVector = Icons.Default.MusicNote,
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
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
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
                        icon = Icons.Default.Album
                    )
                }

                if (scrobble.formattedDuration.isNotBlank()) {
                    DetailInfoRow(
                        label = stringResource(R.string.music_detail_duration),
                        value = scrobble.formattedDuration,
                        icon = Icons.Default.Timer
                    )
                }

                val displaySource = when {
                    scrobble.source.contains("lastfm", ignoreCase = true) -> "Last.fm"
                    scrobble.source.contains("listenbrainz", ignoreCase = true) -> "ListenBrainz"
                    scrobble.source.contains("statsfm", ignoreCase = true) -> "Stats.fm"
                    else -> scrobble.source.replaceFirstChar { it.uppercase() }
                }
                DetailInfoRow(
                    label = stringResource(R.string.music_detail_source),
                    value = displaySource,
                    icon = Icons.Default.Source
                )

                if (scrobble.formattedDate.isNotBlank()) {
                    DetailInfoRow(
                        label = stringResource(R.string.music_detail_time),
                        value = "${scrobble.formattedDate}  ${scrobble.formattedTime}",
                        icon = Icons.Default.Schedule
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
                        notify(context.getString(R.string.music_copied, "${scrobble.artist} - ${scrobble.track}"))
                    },
                    shape = M3Shapes.CornerFull,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
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
                        shape = M3Shapes.CornerFull,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(imageVector = Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.action_open))
                    }
                }
            }
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
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}
