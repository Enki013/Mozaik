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
import com.personalrecords.app.core.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordsScreen(
    viewModel: RecordsViewModel,
    onNavigateToNutrition: () -> Unit = {},
    onNavigateToBooks: () -> Unit = {},
    onNavigateToMovies: () -> Unit = {},
    onNavigateToNotifications: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) }
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
            val counts = state.counts
            val booksCount = counts["goodreads_books"] ?: counts["books"] ?: 0
            val filmsCount = counts["letterboxd_films"] ?: counts["films"] ?: 0
            val foodsCount = counts["fatsecret_food_entries"] ?: counts["foods"] ?: 0
            val notifsCount = counts["notifications"] ?: 0

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {

                // Section 1: Sağlık & Yaşam
                item {
                    SectionTitle(title = "Sağlık & Yaşam")
                }
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
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
                                title = "Beslenme Günlüğü",
                                subtitle = "FatSecret · $foodsCount kayıtlı öğün",
                                onClick = onNavigateToNutrition
                            )
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                            RecordItemRow(
                                icon = Icons.Default.Favorite,
                                iconBg = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                                iconTint = MaterialTheme.colorScheme.error,
                                title = "Health Connect",
                                subtitle = "Adım, uyku, nabız ve antrenman",
                                badgeText = "Yakında",
                                onClick = {
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Health Connect webhook entegrasyonu yakında aktif olacaktır.")
                                    }
                                }
                            )
                        }
                    }
                }

                // Section 2: Kültür & Medya
                item {
                    SectionTitle(title = "Kültür & Medya")
                }
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        border = null
                    ) {
                        Column {
                            RecordItemRow(
                                icon = Icons.Default.Movie,
                                iconBg = domainCinemaContainer(),
                                iconTint = domainCinemaOnContainer(),
                                title = "Filmler & Sinema",
                                subtitle = "Letterboxd · $filmsCount film izlendi",
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
                                title = "Kütüphane & Kitaplar",
                                subtitle = "Goodreads · $booksCount kitap kayıtlı",
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
                                title = "Müzik & Ses",
                                subtitle = "Spotify ve podcast dinleme geçmişi",
                                badgeText = "Planlanan",
                                onClick = {
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Müzik ve Podcast entegrasyonu planlanan yol haritasındadır.")
                                    }
                                }
                            )
                        }
                    }
                }

                // Section 3: Sistem & Cihaz
                item {
                    SectionTitle(title = "Sistem & Cihaz")
                }
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
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
                            title = "Bildirim Günlüğü",
                            subtitle = "Webhook Relay · $notifsCount bildirim",
                            onClick = onNavigateToNotifications
                        )
                    }
                }

                item { Spacer(modifier = Modifier.height(20.dp)) }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 2.dp)
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
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Leading Icon Box
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(MaterialTheme.shapes.small)
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
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
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
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest
            ) {
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.5.dp),
                    fontWeight = FontWeight.Medium
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
