package com.personalrecords.app.ui.screens.movies

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.personalrecords.app.core.network.model.FilmDto
import com.personalrecords.app.core.theme.*
import com.personalrecords.app.ui.components.*

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun MoviesScreen(
    viewModel: MoviesViewModel,
    onNavigateBack: (() -> Unit)? = null
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Filmler",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    if (onNavigateBack != null) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Geri"
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

        androidx.compose.material3.pulltorefresh.PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { isRefreshing = true; viewModel.loadData() },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val filteredFilms = remember(state.films, state.searchQuery, state.onlyRewatches, state.sortBy) {
                var list = state.films.filter { film ->
                    val matchesSearch = state.searchQuery.isBlank() ||
                        film.displayTitle.contains(state.searchQuery, ignoreCase = true)
                    val matchesRewatch = !state.onlyRewatches || film.rewatch
                    matchesSearch && matchesRewatch
                }

                list = when (state.sortBy) {
                    "rating_desc" -> list.sortedByDescending { it.safeRating }
                    "rating_asc" -> list.sortedBy { it.safeRating }
                    "year" -> list.sortedByDescending { it.safeYear }
                    "date" -> list.sortedByDescending { it.safeWatchedDate }
                    "title" -> list.sortedBy { it.displayTitle.lowercase() }
                    else -> list
                }
                list
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                contentPadding = PaddingValues(top = 10.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {

                // Stats Row
                item {
                    val totalFilms = state.films.size
                    val ratedFilms = state.films.count { it.safeRating > 0 }
                    val rewatchFilms = state.films.count { it.rewatch }
                    val avgRating = if (ratedFilms > 0) {
                        String.format("%.1f", state.films.filter { it.safeRating > 0 }.map { it.safeRating }.average())
                    } else "0.0"

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        MetricCard(
                            title = "Toplam Film",
                            value = "$totalFilms",
                            badgeText = "Letterboxd",
                            note = "$ratedFilms puanlanan",
                            modifier = Modifier.weight(1f),
                            badgeBg = domainCinemaContainer(),
                            badgeColor = domainCinemaOnContainer()
                        )
                        MetricCard(
                            title = "Ortalama Puan",
                            value = avgRating,
                            valueIcon = Icons.Filled.Star,
                            valueIconTint = domainRatingOnContainer(),
                            badgeText = "Ort.",
                            note = "$rewatchFilms tekrar izleme",
                            modifier = Modifier.weight(1f),
                            badgeBg = domainRatingContainer(),
                            badgeColor = domainRatingOnContainer()
                        )
                    }
                }

                // Error banner if any
                state.error?.let { err ->
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth()
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
                                        text = "Tekrar Dene",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                // Search Bar & Filter Header
                item {
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
                                    text = "Film adı ara...",
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
                                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                        Icon(
                                            Icons.Default.Clear,
                                            contentDescription = "Temizle",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            },
                            shape = CircleShape,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                            ),
                            singleLine = true
                        )

                        val rewatchFilms = state.films.count { it.rewatch }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilterChip(
                                selected = state.onlyRewatches,
                                onClick = { viewModel.toggleRewatches() },
                                label = {
                                    Text(
                                        text = "Tekrar İzlenenler ($rewatchFilms)",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Repeat,
                                        contentDescription = null,
                                        modifier = Modifier.size(FilterChipDefaults.IconSize)
                                    )
                                },
                                shape = MaterialTheme.shapes.small,
                                border = null,
                                colors = borderlessFilterChipColors()
                            )

                            val (sortLabel, sortIcon) = when (state.sortBy) {
                                "rating_desc" -> "Puan (Azalan)" to Icons.Default.ArrowDownward
                                "rating_asc" -> "Puan (Artan)" to Icons.Default.ArrowUpward
                                "year" -> "Vizyon Yılı" to Icons.Default.CalendarMonth
                                "date" -> "İzleme Tarihi" to Icons.Default.Schedule
                                "title" -> "Film Adı" to Icons.Default.SortByAlpha
                                else -> "Sırala" to Icons.AutoMirrored.Filled.Sort
                            }
                            AssistChip(
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
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                },
                                shape = MaterialTheme.shapes.small,
                                border = null,
                                colors = borderlessAssistChipColors()
                            )
                        }
                    }
                }

                if (state.isLoading && state.films.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    }
                } else if (filteredFilms.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Uygun film bulunamadı.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    items(filteredFilms) { film ->
                        FilmCard(film = film)
                    }
                }

                item { Spacer(modifier = Modifier.height(20.dp)) }
            }
        }
    }
}

@Composable
fun FilmCard(film: FilmDto) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = null
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PosterThumbnail(imageUrl = film.image_url, fallbackIcon = Icons.Default.Movie, width = 36, height = 52)
            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = film.displayTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
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
                            shape = CircleShape,
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
                                    text = "Tekrar",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = domainCinemaOnContainer(),
                                    fontWeight = FontWeight.Bold,
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
}

