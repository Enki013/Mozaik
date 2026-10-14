package com.personalrecords.app.ui.screens.movies

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.personalrecords.app.R
import com.personalrecords.app.core.network.model.FilmDto
import com.personalrecords.app.core.theme.*
import com.personalrecords.app.ui.components.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun MoviesScreen(
    viewModel: MoviesViewModel,
    onNavigateBack: (() -> Unit)? = null
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showDatePicker by remember { mutableStateOf(false) }
    var selectedFilm by remember { mutableStateOf<FilmDto?>(null) }

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
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.statusBars,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.title_movies),
                        style = MaterialTheme.typography.titleLargeEmphasized
                    )
                },
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
            onRefresh = { isRefreshing = true; viewModel.loadData() },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                contentPadding = PaddingValues(top = 10.dp, bottom = 144.dp),
                verticalArrangement = Arrangement.Top
            ) {

                // Stats Row
                item(key = "movies_stats_row", contentType = "stats_row") {
                    val totalFilms = state.films.size
                    val ratedFilms = state.ratedFilmsCount
                    val rewatchFilms = state.rewatchFilmsCount
                    val avgRating = state.avgRatingText

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        MetricCard(
                            title = stringResource(R.string.movies_stats_total),
                            value = "$totalFilms",
                            badgeText = stringResource(R.string.movies_badge_letterboxd),
                            note = stringResource(R.string.movies_stats_rated, ratedFilms),
                            modifier = Modifier.weight(1f),
                            badgeBg = domainCinemaContainer(),
                            badgeColor = domainCinemaOnContainer()
                        )
                        MetricCard(
                            title = stringResource(R.string.movies_stats_avg_rating),
                            value = avgRating,
                            valueIcon = Icons.Filled.Star,
                            valueIconTint = domainRatingOnContainer(),
                            badgeText = stringResource(R.string.badge_avg),
                            note = stringResource(R.string.movies_stats_rewatch, rewatchFilms),
                            modifier = Modifier.weight(1f),
                            badgeBg = domainRatingContainer(),
                            badgeColor = domainRatingOnContainer()
                        )
                    }
                }

                // Error banner if any
                state.error?.let { err ->
                    item(key = "movies_error_banner", contentType = "error_banner") {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = M3Shapes.CornerSmall,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Hata: $err",
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { viewModel.loadData() }) {
                                    Text(
                                        text = stringResource(R.string.action_retry),
                                        style = MaterialTheme.typography.labelMediumEmphasized,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }

                // Search Bar & Filter Header (Sticky Header)
                stickyHeader(key = "movies_sticky_header", contentType = "sticky_header") {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = state.searchQuery,
                                onValueChange = { viewModel.setSearchQuery(it) },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = {
                                    Text(
                                        text = stringResource(R.string.movies_search_placeholder),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Search,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                trailingIcon = {
                                    if (state.searchQuery.isNotEmpty()) {
                                        IconButton(onClick = {
                                            viewModel.setSearchQuery("")
                                        }) {
                                            Icon(
                                                Icons.Default.Clear,
                                                contentDescription = stringResource(R.string.action_clear),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                },
                                shape = M3Shapes.CornerFull,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                ),
                                singleLine = true
                            )

                            val rewatchFilms = state.rewatchFilmsCount
                            val rewatchLabel = stringResource(R.string.movies_filter_rewatches)
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
                                ExpressiveFilterChip(
                                    selected = state.onlyRewatches,
                                    onClick = {
                                        viewModel.toggleRewatches()
                                    },
                                    label = {
                                        Text(
                                            text = "$rewatchLabel ($rewatchFilms)",
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Repeat,
                                            contentDescription = null,
                                            modifier = Modifier.size(FilterChipDefaults.IconSize)
                                        )
                                    }
                                )

                                val (sortLabel, sortIcon) = when (state.sortBy) {
                                    "rating_desc" -> stringResource(R.string.sort_rating_desc) to Icons.Default.ArrowDownward
                                    "rating_asc" -> stringResource(R.string.sort_rating_asc) to Icons.Default.ArrowUpward
                                    "year" -> stringResource(R.string.sort_release_year) to Icons.Default.CalendarMonth
                                    "date" -> stringResource(R.string.sort_date_watched) to Icons.Default.Schedule
                                    "title" -> stringResource(R.string.sort_movie_title) to Icons.Default.SortByAlpha
                                    else -> stringResource(R.string.action_sort) to Icons.AutoMirrored.Filled.Sort
                                }
                                ExpressiveAssistChip(
                                    onClick = {
                                        val nextSort = when (state.sortBy) {
                                            "rating_desc" -> "rating_asc"
                                            "rating_asc" -> "year"
                                            "year" -> "date"
                                            "date" -> "title"
                                            else -> "rating_desc"
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

                if ((state.isLoading || state.isFiltering) && state.filteredFilms.isEmpty()) {
                    if (!isRefreshing) {
                        item(key = "movies_loading", contentType = "loading") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                ContainedLoadingIndicator(size = 44.dp)
                            }
                        }
                    }
                } else if (state.filteredFilms.isEmpty()) {
                    item(key = "movies_empty", contentType = "empty") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.movies_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    itemsIndexed(
                        items = state.filteredFilms,
                        key = { _, film -> film.film_key.ifBlank { "${film.displayTitle}_${film.year}_${film.safeWatchedDate}" } },
                        contentType = { _, _ -> "movie_row" }
                    ) { index, film ->
                        LazyCardRowContainer(
                            index = index,
                            totalCount = state.filteredFilms.size
                        ) {
                            FilmRowItem(
                                film = film,
                                onClick = { selectedFilm = film }
                            )
                        }
                    }
                }

                item(key = "movies_bottom_spacer", contentType = "spacer") { Spacer(modifier = Modifier.height(20.dp)) }
            }
        }
    }

    selectedFilm?.let { film ->
        FilmDetailBottomSheet(
            film = film,
            onDismiss = { selectedFilm = null }
        )
    }
}

@Composable
fun FilmRowItem(
    film: FilmDto,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .expressiveBounceClick(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PosterThumbnail(imageUrl = film.image_url, fallbackIcon = Icons.Default.Movie, width = 36, height = 52)
        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = film.displayTitle,
                style = MaterialTheme.typography.titleSmallEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (film.safeYear > 0) {
                    Text(
                        text = "${film.safeYear}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        maxLines = 1,
                        softWrap = false
                    )
                }
                if (film.safeWatchedDate.isNotBlank()) {
                    Text(
                        text = "· ${film.safeWatchedDate}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        maxLines = 1,
                        softWrap = false
                    )
                }
                if (film.rewatch) {
                    Surface(
                        shape = M3Shapes.CornerFull,
                        color = domainCinemaContainer()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp),
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Repeat,
                                contentDescription = null,
                                modifier = Modifier.size(10.dp),
                                tint = domainCinemaOnContainer()
                            )
                            Text(
                                text = stringResource(R.string.movies_rewatch_badge),
                                style = MaterialTheme.typography.labelSmallEmphasized,
                                color = domainCinemaOnContainer(),
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }

            if (film.safeReview.isNotBlank()) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "“${film.safeReview.take(120)}${if (film.safeReview.length > 120) "..." else ""}”",
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (film.safeRating > 0) {
            Spacer(modifier = Modifier.width(8.dp))
            RatingBadge(rating = film.safeRating)
        }
    }
}

@Composable
fun FilmCard(film: FilmDto) {
    FilmRowItem(film = film)
}

