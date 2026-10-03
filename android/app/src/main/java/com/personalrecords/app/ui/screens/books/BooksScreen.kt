package com.personalrecords.app.ui.screens.books

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.personalrecords.app.R
import com.personalrecords.app.core.network.model.BookDto
import com.personalrecords.app.core.theme.*
import com.personalrecords.app.ui.components.*

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun BooksScreen(
    viewModel: BooksViewModel,
    onNavigateBack: (() -> Unit)? = null
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.title_books),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
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

        androidx.compose.material3.pulltorefresh.PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { isRefreshing = true; viewModel.loadData() },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val filteredBooks = remember(state.books, state.searchQuery, state.selectedShelf, state.sortBy) {
                var list = state.books.filter { book ->
                    val matchesSearch = state.searchQuery.isBlank() ||
                        book.title.contains(state.searchQuery, ignoreCase = true) ||
                        book.author.contains(state.searchQuery, ignoreCase = true)
                    val matchesShelf = state.selectedShelf == "all" ||
                        book.exclusive_shelf.equals(state.selectedShelf, ignoreCase = true)
                    matchesSearch && matchesShelf
                }

                list = when (state.sortBy) {
                    "rating_desc" -> list.sortedByDescending { it.my_rating }
                    "rating_asc" -> list.sortedBy { it.my_rating }
                    "title" -> list.sortedBy { it.title.lowercase() }
                    "date" -> list.sortedByDescending { it.date_read }
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
                    val totalBooks = state.books.size
                    val readBooks = state.books.count { it.exclusive_shelf.equals("read", ignoreCase = true) }
                    val toReadBooks = state.books.count { it.exclusive_shelf.equals("to-read", ignoreCase = true) }
                    val ratedCount = state.books.count { it.my_rating > 0 }
                    val avgRating = if (ratedCount > 0) {
                        String.format("%.1f", state.books.filter { it.my_rating > 0 }.map { it.my_rating }.average())
                    } else "0.0"

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        MetricCard(
                            title = stringResource(R.string.books_stats_total),
                            value = "$totalBooks",
                            badgeText = stringResource(R.string.books_badge_book),
                            note = stringResource(R.string.books_stats_read, readBooks),
                            modifier = Modifier.weight(1f),
                            badgeBg = domainBooksContainer(),
                            badgeColor = domainBooksOnContainer()
                        )
                        MetricCard(
                            title = stringResource(R.string.books_stats_to_read),
                            value = "$toReadBooks",
                            badgeText = stringResource(R.string.badge_list),
                            note = stringResource(R.string.books_stats_avg_rating, avgRating),
                            noteIcon = Icons.Filled.Star,
                            noteIconTint = domainRatingOnContainer(),
                            modifier = Modifier.weight(1f),
                            badgeBg = domainRatingContainer(),
                            badgeColor = domainRatingOnContainer()
                        )
                    }
                }

                // Search Bar & Filter Header (Sticky Header)
                stickyHeader {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
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
                                        text = stringResource(R.string.books_search_placeholder),
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
                                                contentDescription = stringResource(R.string.action_clear),
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

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                listOf(
                                    Triple("all", stringResource(R.string.books_shelf_all), null),
                                    Triple("read", stringResource(R.string.books_shelf_read), Icons.Default.Check),
                                    Triple("to-read", stringResource(R.string.books_shelf_to_read), Icons.Default.HourglassEmpty),
                                    Triple("currently-reading", stringResource(R.string.books_shelf_currently_reading), Icons.AutoMirrored.Filled.MenuBook)
                                ).forEach { (shelf, label, icon) ->
                                    FilterChip(
                                        selected = state.selectedShelf == shelf,
                                        onClick = { viewModel.setShelf(shelf) },
                                        leadingIcon = icon?.let {
                                            {
                                                Icon(
                                                    imageVector = it,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        },
                                        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                                        shape = MaterialTheme.shapes.small,
                                        border = null,
                                        colors = borderlessFilterChipColors()
                                    )
                                }

                                val (sortLabel, sortIcon) = when (state.sortBy) {
                                    "rating_desc" -> stringResource(R.string.sort_rating_desc) to Icons.Default.ArrowDownward
                                    "rating_asc" -> stringResource(R.string.sort_rating_asc) to Icons.Default.ArrowUpward
                                    "title" -> stringResource(R.string.sort_title) to Icons.Default.SortByAlpha
                                    "date" -> stringResource(R.string.sort_date_read) to Icons.Default.CalendarMonth
                                    else -> stringResource(R.string.action_sort) to Icons.AutoMirrored.Filled.Sort
                                }
                                AssistChip(
                                    onClick = {
                                        val nextSort = when (state.sortBy) {
                                            "rating_desc" -> "rating_asc"
                                            "rating_asc" -> "title"
                                            "title" -> "date"
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
                }

                if (state.isLoading && state.books.isEmpty()) {
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
                } else if (filteredBooks.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.books_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    items(filteredBooks) { book ->
                        BookCard(book = book)
                    }
                }

                item { Spacer(modifier = Modifier.height(20.dp)) }
            }
        }
    }
}

@Composable
fun BookCard(book: BookDto) {
    val shelfLabel = when (book.exclusive_shelf.lowercase()) {
        "read" -> "Okundu"
        "to-read" -> "Okunacak"
        "currently-reading" -> "Okunuyor"
        else -> book.exclusive_shelf
    }

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
            PosterThumbnail(imageUrl = book.image_url, fallbackIcon = Icons.AutoMirrored.Filled.MenuBook, width = 36, height = 52)
            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = book.author,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = domainBooksContainer()
                    ) {
                        Text(
                            text = shelfLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = domainBooksOnContainer(),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.5.dp),
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                    if (book.number_of_pages > 0) {
                        Text(
                            text = stringResource(R.string.books_pages, book.number_of_pages),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            if (book.my_rating > 0) {
                Spacer(modifier = Modifier.width(8.dp))
                RatingBadge(rating = book.my_rating)
            }
        }
    }
}

