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
    onNavigateToHealthConnect: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.statusBars,
        snackbarHost = { SnackbarHost(snackbarHostState, modifier = Modifier.navigationBarsPadding().padding(bottom = 96.dp)) }
    ) { innerPadding ->
        val listState = androidx.compose.foundation.lazy.rememberLazyListState()
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
            val filmsCount = counts["letterboxd_films"] ?: counts["films"] ?: 0
            val foodsCount = counts["fatsecret_food_entries"] ?: counts["foods"] ?: 0
            val notifsCount = counts["notifications"] ?: 0
            val healthCount = counts["health_records"] ?: 0

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 144.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {

                // Section 1: Sağlık & Yaşam
                item(key = "records_header_health", contentType = "section_header") {
                    SectionTitle(
                        title = stringResource(R.string.records_section_health),
                        modifier = expressiveItemModifier()
                    )
                }
                item(key = "records_card_health", contentType = "section_card") {
                    Card(
                        modifier = expressiveItemModifier().fillMaxWidth(),
                        shape = M3Shapes.CornerLarge,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        border = null
                    ) {
                        Column {
                            RecordItemRow(
                                icon = Icons.Default.Restaurant,
                                iconBg = domainNutritionContainer(),
                                iconTint = domainNutritionOnContainer(),
                                title = stringResource(R.string.records_item_nutrition_title),
                                subtitle = stringResource(R.string.records_item_nutrition_sub, foodsCount),
                                onClick = onNavigateToNutrition
                            )
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                            val healthSub = if (healthCount > 0) {
                                stringResource(R.string.records_item_healthconnect_count, healthCount)
                            } else {
                                stringResource(R.string.records_item_healthconnect_sub)
                            }
                            RecordItemRow(
                                icon = Icons.Default.Favorite,
                                iconBg = domainHealthContainer(),
                                iconTint = domainHealthOnContainer(),
                                title = stringResource(R.string.records_item_healthconnect_title),
                                subtitle = healthSub,
                                onClick = onNavigateToHealthConnect
                            )
                        }
                    }
                }

                // Section 2: Kültür & Medya
                item(key = "records_header_media", contentType = "section_header") {
                    SectionTitle(
                        title = stringResource(R.string.records_section_media),
                        modifier = expressiveItemModifier()
                    )
                }
                item(key = "records_card_media", contentType = "section_card") {
                    Card(
                        modifier = expressiveItemModifier().fillMaxWidth(),
                        shape = M3Shapes.CornerLarge,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        border = null
                    ) {
                        val musicToast = stringResource(R.string.records_toast_music)
                        Column {
                            RecordItemRow(
                                icon = Icons.Default.Movie,
                                iconBg = domainCinemaContainer(),
                                iconTint = domainCinemaOnContainer(),
                                title = stringResource(R.string.records_item_movies_title),
                                subtitle = stringResource(R.string.records_item_movies_sub, filmsCount),
                                onClick = onNavigateToMovies
                            )
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                            RecordItemRow(
                                icon = Icons.AutoMirrored.Filled.MenuBook,
                                iconBg = domainBooksContainer(),
                                iconTint = domainBooksOnContainer(),
                                title = stringResource(R.string.records_item_books_title),
                                subtitle = stringResource(R.string.records_item_books_sub, booksCount),
                                onClick = onNavigateToBooks
                            )
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                            RecordItemRow(
                                icon = Icons.Default.Headphones,
                                iconBg = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                                iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
                                title = stringResource(R.string.records_item_music_title),
                                subtitle = stringResource(R.string.records_item_music_sub),
                                badgeText = stringResource(R.string.badge_planned),
                                onClick = {
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(musicToast)
                                    }
                                }
                            )
                        }
                    }
                }

                // Section 3: Sistem & Cihaz
                item(key = "records_header_system", contentType = "section_header") {
                    SectionTitle(
                        title = stringResource(R.string.records_section_system),
                        modifier = expressiveItemModifier()
                    )
                }
                item(key = "records_card_system", contentType = "section_card") {
                    Card(
                        modifier = expressiveItemModifier().fillMaxWidth(),
                        shape = M3Shapes.CornerLarge,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        border = null
                    ) {
                        RecordItemRow(
                            icon = Icons.Default.Notifications,
                            iconBg = MaterialTheme.colorScheme.secondaryContainer,
                            iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                            title = stringResource(R.string.records_item_notifications_title),
                            subtitle = stringResource(R.string.records_item_notifications_sub, notifsCount),
                            onClick = onNavigateToNotifications
                        )
                    }
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
        // Leading Icon Box (optical roundness: 8.dp CornerSmall inside 16.dp CornerLarge card)
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(M3Shapes.CornerSmall)
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(22.dp)
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
