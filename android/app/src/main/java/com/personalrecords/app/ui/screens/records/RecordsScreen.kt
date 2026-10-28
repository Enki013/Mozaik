package com.personalrecords.app.ui.screens.records

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.personalrecords.app.R
import com.personalrecords.app.core.theme.*
import com.personalrecords.app.ui.components.*
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordsScreen(
    viewModel: RecordsViewModel,
    scrollToTopFlow: kotlinx.coroutines.flow.Flow<Unit>? = null,
    refreshFlow: kotlinx.coroutines.flow.Flow<Unit>? = null,
    onNavigateToNutrition: () -> Unit = {},
    onNavigateToBooks: () -> Unit = {},
    onNavigateToMovies: () -> Unit = {},
    onNavigateToNotifications: () -> Unit = {},
    onNavigateToHealthConnect: () -> Unit = {},
    onNavigateToMusic: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    // Short page: only collapse the bar when the list can actually scroll.
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(
        state = rememberTopAppBarState(),
        canScroll = { listState.canScrollForward || listState.canScrollBackward }
    )
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            ExpressivePageTopAppBar(
                title = stringResource(R.string.title_records),
                scrollBehavior = scrollBehavior
            )
        },
        contentWindowInsets = WindowInsets.statusBars
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
            val counts = state.counts
            val booksCount = counts["goodreads_books"] ?: counts["books"] ?: 0
            val filmsCount = counts["total_films"] ?: counts["films"] ?: (counts["letterboxd_films"] ?: 0)
            val foodsCount = counts["fatsecret_food_entries"] ?: counts["foods"] ?: 0
            val notifsCount = counts["notifications"] ?: 0
            val healthCount = counts["health_records"] ?: 0
            val musicCount = counts["music_scrobbles"] ?: counts["music"] ?: 0

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                contentPadding = PaddingValues(
                    top = 8.dp,
                    bottom = floatingBottomNavigationContentPadding()
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {

                state.error?.let { err ->
                    item(key = "records_error_banner", contentType = "error_banner") {
                        ExpressiveErrorBanner(
                            message = err,
                            onRetry = { viewModel.loadData() },
                            modifier = expressiveItemModifier()
                        )
                    }
                }

                // Section 1: Sağlık & Yaşam
                item(key = "records_header_health", contentType = "section_header") {
                    SectionTitle(
                        title = stringResource(R.string.records_section_health),
                        modifier = expressiveItemModifier()
                    )
                }
                item(key = "records_card_health", contentType = "section_card") {
                    RecordsGroup(
                        modifier = expressiveItemModifier(),
                        rows = listOf(
                            RecordRowSpec(
                                category = RecordCategory.Nutrition,
                                icon = Icons.Default.Restaurant,
                                title = stringResource(R.string.records_item_nutrition_title),
                                subtitle = if (foodsCount > 0) {
                                    stringResource(R.string.records_item_nutrition_sub, foodsCount)
                                } else {
                                    stringResource(R.string.records_item_nutrition_empty)
                                },
                                onClick = onNavigateToNutrition
                            ),
                            RecordRowSpec(
                                category = RecordCategory.Health,
                                icon = Icons.Default.Favorite,
                                title = stringResource(R.string.records_item_healthconnect_title),
                                subtitle = if (healthCount > 0) {
                                    stringResource(R.string.records_item_healthconnect_count, healthCount)
                                } else {
                                    stringResource(R.string.records_item_healthconnect_sub)
                                },
                                onClick = onNavigateToHealthConnect
                            )
                        )
                    )
                }

                // Section 2: Kültür & Medya
                item(key = "records_header_media", contentType = "section_header") {
                    SectionTitle(
                        title = stringResource(R.string.records_section_media),
                        modifier = expressiveItemModifier()
                    )
                }
                item(key = "records_card_media", contentType = "section_card") {
                    RecordsGroup(
                        modifier = expressiveItemModifier(),
                        rows = listOf(
                            RecordRowSpec(
                                category = RecordCategory.Movies,
                                icon = Icons.Default.Movie,
                                title = stringResource(R.string.records_item_movies_title),
                                subtitle = if (filmsCount > 0) {
                                    stringResource(R.string.records_item_movies_sub, filmsCount)
                                } else {
                                    stringResource(R.string.records_item_movies_empty)
                                },
                                onClick = onNavigateToMovies
                            ),
                            RecordRowSpec(
                                category = RecordCategory.Books,
                                icon = Icons.AutoMirrored.Filled.MenuBook,
                                title = stringResource(R.string.records_item_books_title),
                                subtitle = if (booksCount > 0) {
                                    stringResource(R.string.records_item_books_sub, booksCount)
                                } else {
                                    stringResource(R.string.records_item_books_empty)
                                },
                                onClick = onNavigateToBooks
                            ),
                            RecordRowSpec(
                                category = RecordCategory.Music,
                                icon = Icons.Default.MusicNote,
                                title = stringResource(R.string.records_item_music_title),
                                subtitle = if (musicCount > 0) {
                                    stringResource(R.string.records_item_music_count, musicCount)
                                } else {
                                    stringResource(R.string.records_item_music_sub)
                                },
                                onClick = onNavigateToMusic
                            )
                        )
                    )
                }

                // Section 3: Sistem & Cihaz
                item(key = "records_header_system", contentType = "section_header") {
                    SectionTitle(
                        title = stringResource(R.string.records_section_system),
                        modifier = expressiveItemModifier()
                    )
                }
                item(key = "records_card_system", contentType = "section_card") {
                    RecordsGroup(
                        modifier = expressiveItemModifier(),
                        rows = listOf(
                            RecordRowSpec(
                                category = RecordCategory.Notifications,
                                icon = Icons.Default.Notifications,
                                title = stringResource(R.string.records_item_notifications_title),
                                subtitle = if (notifsCount > 0) {
                                    stringResource(R.string.records_item_notifications_sub, notifsCount)
                                } else {
                                    stringResource(R.string.records_item_notifications_empty)
                                },
                                onClick = onNavigateToNotifications
                            )
                        )
                    )
                }

                item(key = "records_bottom_spacer", contentType = "spacer") { Spacer(modifier = Modifier.height(20.dp)) }
            }
        }
    }
}

@Composable
private fun SectionTitle(
    title: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLargeEmphasized,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 4.dp, top = 6.dp, bottom = 2.dp)
    )
}

private data class RecordRowSpec(
    val category: RecordCategory,
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val onClick: () -> Unit
)

/** One segmented group of navigation rows. */
@Composable
private fun RecordsGroup(
    rows: List<RecordRowSpec>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(M3ListMetrics.SegmentGap)
    ) {
        rows.forEachIndexed { index, row ->
            val colors = recordCategoryColors(row.category)
            GroupedListItemSurface(index = index, itemCount = rows.size) {
                RecordItemRow(
                    icon = row.icon,
                    iconBg = colors.container,
                    iconTint = colors.onContainer,
                    title = row.title,
                    subtitle = row.subtitle,
                    onClick = row.onClick
                )
            }
        }
    }
}

@Composable
private fun RecordItemRow(
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    title: String,
    subtitle: String,
    badgeText: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .expressiveBounceClick(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Leading icon box: same 40dp / 8dp anatomy as the other list rows
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(M3Shapes.CornerSmall)
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Center Content
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmallEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Trailing Badge or Chevron
        if (badgeText != null) {
            Surface(
                shape = M3Shapes.CornerFull,
                color = MaterialTheme.colorScheme.surfaceContainerHighest
            ) {
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.5.dp)
                )
            }
        } else {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}
