package com.personalrecords.app.ui.screens.overview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.personalrecords.app.core.theme.*
import com.personalrecords.app.ui.components.*

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun OverviewScreen(
    viewModel: OverviewViewModel,
    onNavigateToTimeline: () -> Unit,
    onNavigateToSettings: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.syncMessage) {
        state.syncMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSyncMessage()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Personal Records",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Ayarlar",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        val listState = rememberLazyListState()
        var unlockProgress by remember { mutableStateOf(0f) }
        val stickyReleaseLock = remember {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    if (available.y > 0 && listState.firstVisibleItemIndex <= 1) {
                        unlockProgress += available.y
                        if (unlockProgress < 150f) {
                            return available
                        }
                    } else if (available.y < 0) {
                        unlockProgress = 0f
                    }
                    return Offset.Zero
                }
            }
        }

        androidx.compose.material3.pulltorefresh.PullToRefreshBox(
            isRefreshing = state.isLoading && state.dashboard != null,
            onRefresh = { viewModel.loadData() },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (state.isLoading && state.dashboard == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(stickyReleaseLock)
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                // Top 4 Metrics Grid
                item {
                    val counts = state.dashboard?.counts ?: emptyMap()
                    val today = state.dashboard?.today ?: emptyMap()
                    val todayCal = today["fatsecret_calories"]?.toString()?.replace("\"", "")
                        ?: today["calories"]?.toString()?.replace("\"", "") ?: "0"
                    val todayEntries = today["fatsecret_entry_count"]?.toString()?.replace("\"", "")
                        ?: today["entries_count"]?.toString()?.replace("\"", "") ?: "0"

                    val booksCount = counts["goodreads_books"] ?: counts["books"] ?: 0
                    val filmsCount = counts["letterboxd_films"] ?: counts["films"] ?: 0
                    val foodsCount = counts["fatsecret_food_entries"] ?: counts["foods"] ?: 0

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            MetricCard(
                                title = "Bugün Alınan",
                                value = "$todayCal kcal",
                                badgeText = "FatSecret",
                                note = "$todayEntries öğün kaydı",
                                modifier = Modifier.weight(1f),
                                badgeBg = domainNutritionContainer(),
                                badgeColor = domainNutritionOnContainer()
                            )
                            MetricCard(
                                title = "Kütüphane",
                                value = "$booksCount kitap",
                                badgeText = "Goodreads",
                                note = "Toplam kayıtlı",
                                modifier = Modifier.weight(1f),
                                badgeBg = domainBooksContainer(),
                                badgeColor = domainBooksOnContainer()
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            MetricCard(
                                title = "İzlenen",
                                value = "$filmsCount film",
                                badgeText = "Letterboxd",
                                note = "Toplam film",
                                modifier = Modifier.weight(1f),
                                badgeBg = domainCinemaContainer(),
                                badgeColor = domainCinemaOnContainer()
                            )
                            MetricCard(
                                title = "Yemek Günlüğü",
                                value = "$foodsCount kayıt",
                                badgeText = "Besin",
                                note = "Tüm öğünler",
                                modifier = Modifier.weight(1f),
                                badgeBg = domainNutritionContainer(),
                                badgeColor = domainNutritionOnContainer()
                            )
                        }
                    }
                }

                // Sync & Recent Activities Header (Sticky)
                stickyHeader {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Entegrasyonlar Card
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium,
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                                border = null
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "Entegrasyonlar & Senkronizasyon",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Tek tıkla anlık veri eşitlemesi",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))

                                    // FatSecret Row
                                    SyncRow(
                                        title = "FatSecret",
                                        subtitle = "Beslenme günlüğü",
                                        isSyncing = state.isSyncing == "fatsecret",
                                        onSync = { viewModel.syncService("fatsecret") }
                                    )
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 6.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                    )

                                    // Goodreads Row
                                    SyncRow(
                                        title = "Goodreads RSS",
                                        subtitle = "Kitaplar & Raflar",
                                        isSyncing = state.isSyncing == "goodreads",
                                        onSync = { viewModel.syncService("goodreads") }
                                    )
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 6.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                    )

                                    // Letterboxd Row
                                    SyncRow(
                                        title = "Letterboxd RSS",
                                        subtitle = "Filmler & Puanlar",
                                        isSyncing = state.isSyncing == "letterboxd",
                                        onSync = { viewModel.syncService("letterboxd") }
                                    )
                                }
                            }

                            // Son Aktiviteler Header Card
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
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "Son Aktiviteler",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Öğün, kitap ve film akışı",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    TextButton(onClick = onNavigateToTimeline) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = "Tümü",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.SemiBold
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
                            }
                        }
                    }
                }

                // Recent Activities Items List
                if (state.recentActivities.isEmpty()) {
                    item {
                        Text(
                            text = "Henüz bir aktivite kaydı yok.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }
                } else {
                    items(state.recentActivities) { item ->
                        ActivityRowItem(item = item)
                    }
                }

                item { Spacer(modifier = Modifier.height(12.dp)) }
            }
        }
      }
    }
}

@Composable
fun SyncRow(
    title: String,
    subtitle: String,
    isSyncing: Boolean,
    onSync: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        FilledTonalButton(
            onClick = onSync,
            enabled = !isSyncing,
            shape = MaterialTheme.shapes.small,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            modifier = Modifier.height(32.dp)
        ) {
            if (isSyncing) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
            } else {
                Icon(
                    Icons.Default.Sync,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Sync Et",
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

@Composable
fun ActivityRowItem(item: com.personalrecords.app.core.network.model.ActivityItemDto) {
    val fallbackIcon = when (item.source) {
        "fatsecret" -> Icons.Default.Restaurant
        "goodreads" -> Icons.AutoMirrored.Filled.MenuBook
        "letterboxd" -> Icons.Default.Movie
        else -> Icons.Default.Bookmark
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = MaterialTheme.shapes.small
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PosterThumbnail(
            imageUrl = item.image_url,
            fallbackIcon = fallbackIcon,
            width = 32,
            height = 46
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
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
    }
}
