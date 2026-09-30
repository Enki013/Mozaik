package com.personalrecords.app.ui.screens.settings

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.personalrecords.app.core.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: (() -> Unit)? = null
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showKeyEditor by remember { mutableStateOf(false) }

    fun triggerDownload(url: String, filename: String) {
        coroutineScope.launch {
            snackbarHostState.showSnackbar("İndiriliyor: $filename...")
            val success = withContext(Dispatchers.IO) {
                try {
                    val client = OkHttpClient.Builder()
                        .connectTimeout(15, TimeUnit.SECONDS)
                        .readTimeout(60, TimeUnit.SECONDS)
                        .build()
                    val request = Request.Builder().url(url).build()
                    val response = client.newCall(request).execute()
                    if (!response.isSuccessful) return@withContext false
                    val body = response.body ?: return@withContext false

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val contentValues = ContentValues().apply {
                            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                            put(
                                MediaStore.MediaColumns.MIME_TYPE,
                                if (filename.endsWith(".json")) "application/json" else "application/octet-stream"
                            )
                            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                        }
                        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                        if (uri != null) {
                            context.contentResolver.openOutputStream(uri)?.use { out ->
                                body.byteStream().copyTo(out)
                            }
                            true
                        } else false
                    } else {
                        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                        val file = File(downloadsDir, filename)
                        FileOutputStream(file).use { out ->
                            body.byteStream().copyTo(out)
                        }
                        true
                    }
                } catch (e: Exception) {
                    false
                }
            }

            if (success) {
                snackbarHostState.showSnackbar("İndirildi: $filename (Downloads klasörüne kaydedildi)")
            } else {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    context.startActivity(intent)
                    snackbarHostState.showSnackbar("Tarayıcıda indirme başlatılıyor...")
                } catch (e: Exception) {
                    snackbarHostState.showSnackbar("İndirme başarısız oldu.")
                }
            }
        }
    }

    LaunchedEffect(state.syncStatusMessage) {
        state.syncStatusMessage?.let { snackbarHostState.showSnackbar(it) }
    }

    LaunchedEffect(state.keySaveMessage) {
        state.keySaveMessage?.let { snackbarHostState.showSnackbar(it) }
    }

    var isRefreshing by remember { mutableStateOf(false) }
    LaunchedEffect(state.isTestingConnection) {
        if (!state.isTestingConnection) isRefreshing = false
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Ayarlar",
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
                actions = {
                    IconButton(
                        onClick = {
                            viewModel.testConnection()
                            viewModel.loadFatSecretSettings()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Yenile"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        androidx.compose.material3.pulltorefresh.PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                viewModel.testConnection()
                viewModel.loadFatSecretSettings()
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // ==========================================
                // SECTION 1: Sunucu ve Altyapı
                // ==========================================
                item {
                    SettingsSectionTitle(title = "Sunucu & Altyapı")
                }
                item {
                    SettingsCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                SettingsIconBox(
                                    icon = Icons.Default.Dns,
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Column {
                                    Text(
                                        text = "API Sunucusu",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Cloudflare Worker veya yerel sunucu",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // Connection status chip
                            state.connectionSuccess?.let { ok ->
                                val statusBg = if (ok) domainNutritionContainer() else MaterialTheme.colorScheme.errorContainer
                                val statusFg = if (ok) domainNutritionOnContainer() else MaterialTheme.colorScheme.onErrorContainer
                                Surface(
                                    shape = CircleShape,
                                    color = statusBg
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .background(statusFg, CircleShape)
                                        )
                                        Text(
                                            text = if (ok) "Bağlı" else "Hata",
                                            color = statusFg,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = state.serverUrl,
                            onValueChange = { viewModel.updateServerUrlInput(it) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Sunucu URL") },
                            placeholder = { Text("https://personal-records-api.workers.dev") },
                            singleLine = true,
                            shape = MaterialTheme.shapes.small
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { viewModel.saveServerUrl() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Kaydet", style = MaterialTheme.typography.labelLarge)
                            }

                            OutlinedButton(
                                onClick = { viewModel.testConnection() },
                                enabled = !state.isTestingConnection,
                                modifier = Modifier.weight(1f)
                            ) {
                                if (state.isTestingConnection) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                } else {
                                    Text("Bağlantıyı Sına", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }
                }

                // ==========================================
                // SECTION 2: Beslenme & FatSecret BYOK
                // ==========================================
                item {
                    SettingsSectionTitle(title = "Beslenme Entegrasyonu (FatSecret)")
                }
                item {
                    SettingsCard {
                        // Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                SettingsIconBox(
                                    icon = Icons.Default.Key,
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Column {
                                    Text(
                                        text = "FatSecret API (BYOK)",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Kendi API anahtarınız ile güvenli bağlantı",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (state.isConnected) {
                                Surface(
                                    shape = CircleShape,
                                    color = domainNutritionContainer()
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .background(domainNutritionOnContainer(), CircleShape)
                                        )
                                        Text(
                                            text = "OAuth ✓",
                                            color = domainNutritionOnContainer(),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            } else if (state.hasKeys) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.tertiaryContainer
                                ) {
                                    Text(
                                        text = "Anahtar ✓",
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }

                        // Connected status banner
                        if (state.isConnected) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = domainNutritionContainer(),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = domainNutritionOnContainer(),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "FatSecret Hesabı Başarıyla Bağlandı",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = domainNutritionOnContainer()
                                        )
                                        if (state.maskedKey.isNotBlank()) {
                                            Text(
                                                text = "Aktif Anahtar: ${state.maskedKey}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = domainNutritionOnContainer().copy(alpha = 0.85f)
                                            )
                                        }
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(
                                    onClick = { showKeyEditor = !showKeyEditor },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                ) {
                                    Text(
                                        text = if (showKeyEditor) "Düzenlemeyi Kapat" else "Anahtarları Değiştir",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        }

                        // Key Inputs (shown if not connected OR user tapped edit)
                        AnimatedVisibility(visible = !state.isConnected || showKeyEditor) {
                            Column {
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = state.consumerKey,
                                    onValueChange = { viewModel.updateConsumerKey(it) },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("Consumer Key") },
                                    placeholder = { Text("FatSecret Consumer Key") },
                                    singleLine = true,
                                    shape = MaterialTheme.shapes.small
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedTextField(
                                    value = state.consumerSecret,
                                    onValueChange = { viewModel.updateConsumerSecret(it) },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("Consumer Secret") },
                                    placeholder = { Text("FatSecret Consumer Secret") },
                                    singleLine = true,
                                    shape = MaterialTheme.shapes.small
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                Button(
                                    onClick = { viewModel.saveFatSecretKeys() },
                                    enabled = !state.isSavingKeys && state.consumerKey.isNotBlank() && state.consumerSecret.isNotBlank(),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    if (state.isSavingKeys) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                    } else {
                                        Text("Anahtarları Kaydet", style = MaterialTheme.typography.labelLarge)
                                    }
                                }

                                if (state.hasKeys && !state.isConnected) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    FilledTonalButton(
                                        onClick = {
                                            val baseUrl = state.serverUrl.trim().trimEnd('/')
                                            if (baseUrl.isBlank()) {
                                                coroutineScope.launch {
                                                    snackbarHostState.showSnackbar("Önce sunucu adresini kaydedin.")
                                                }
                                            } else {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("$baseUrl/fatsecret/connect"))
                                                context.startActivity(intent)
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.OpenInBrowser,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text("FatSecret ile Bağlan (OAuth)", style = MaterialTheme.typography.labelLarge)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.HelpOutline,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = "API anahtarı nasıl alınır?",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "1. platform.fatsecret.com'a gidin\n2. Ücretsiz hesap ve Application oluşturun\n3. Consumer Key ve Secret değerlerini kopyalayın",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        TextButton(
                                            onClick = {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://platform.fatsecret.com/"))
                                                context.startActivity(intent)
                                            },
                                            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)
                                        ) {
                                            Text(
                                                text = "platform.fatsecret.com →",
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Divider before sync section
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                            modifier = Modifier.padding(vertical = 12.dp)
                        )

                        // Sync Section
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Restaurant,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Besin Geçmişini Senkronize Et",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "FatSecret sunucusundan geçmiş öğün kayıtlarını çekin:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilledTonalButton(
                                    onClick = { viewModel.syncFatSecret(1) },
                                    enabled = state.isSyncing == null,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Bugün (1G)", style = MaterialTheme.typography.labelMedium)
                                }
                                FilledTonalButton(
                                    onClick = { viewModel.syncFatSecret(30) },
                                    enabled = state.isSyncing == null,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Son 30 Gün", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilledTonalButton(
                                    onClick = { viewModel.syncFatSecret(365) },
                                    enabled = state.isSyncing == null,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Son 1 Yıl", style = MaterialTheme.typography.labelMedium)
                                }
                                FilledTonalButton(
                                    onClick = { viewModel.syncFatSecret(1095) },
                                    enabled = state.isSyncing == null,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Tüm Zamanlar", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }

                // ==========================================
                // SECTION 3: Medya Senkronizasyonu
                // ==========================================
                item {
                    SettingsSectionTitle(title = "Kültür & Medya Senkronizasyonu")
                }
                item {
                    SettingsCard {
                        // Goodreads row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                SettingsIconBox(
                                    icon = Icons.AutoMirrored.Filled.MenuBook,
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Column {
                                    Text(
                                        text = "Goodreads",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Kitaplar ve okuma listeleri (RSS)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            FilledTonalButton(
                                onClick = { viewModel.syncGoodreads() },
                                enabled = state.isSyncing == null
                            ) {
                                if (state.isSyncing == "goodreads") {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                } else {
                                    Text("Senkronize Et", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }

                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                            modifier = Modifier.padding(vertical = 10.dp)
                        )

                        // Letterboxd row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                SettingsIconBox(
                                    icon = Icons.Default.Movie,
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Column {
                                    Text(
                                        text = "Letterboxd",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "İzlenen film günlüğü (RSS)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            FilledTonalButton(
                                onClick = { viewModel.syncLetterboxd() },
                                enabled = state.isSyncing == null
                            ) {
                                if (state.isSyncing == "letterboxd") {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                } else {
                                    Text("Senkronize Et", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }

                // ==========================================
                // SECTION 4: Veri Egemenliği & Yedekleme
                // ==========================================
                item {
                    SettingsSectionTitle(title = "Veri Egemenliği & Yedekleme")
                }
                item {
                    SettingsCard {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            SettingsIconBox(
                                icon = Icons.Default.Storage,
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Column {
                                Text(
                                    text = "Veri Dışa Aktarma",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Tüm kayıtları cihazınıza indirin ve saklayın",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilledTonalButton(
                                onClick = {
                                    val baseUrl = state.serverUrl.trim().trimEnd('/')
                                    if (baseUrl.isBlank()) {
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("Lütfen önce sunucu adresini kaydedin.")
                                        }
                                    } else {
                                        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                                        triggerDownload("$baseUrl/api/export/all", "personal_records_$timestamp.json")
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text("JSON İndir", style = MaterialTheme.typography.labelMedium)
                                }
                            }

                            FilledTonalButton(
                                onClick = {
                                    val baseUrl = state.serverUrl.trim().trimEnd('/')
                                    if (baseUrl.isBlank()) {
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("Lütfen önce sunucu adresini kaydedin.")
                                        }
                                    } else {
                                        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                                        triggerDownload("$baseUrl/api/export/db", "personal_records_$timestamp.sqlite3")
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text("SQLite (.db)", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Dosyalar Downloads klasörünüze kaydedilir.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // ==========================================
                // SECTION 5: Uygulama Bilgisi
                // ==========================================
                item {
                    SettingsSectionTitle(title = "Uygulama Bilgisi")
                }
                item {
                    SettingsCard {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            SettingsIconBox(
                                icon = Icons.Default.Tune,
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                            Column {
                                Text(
                                    text = "Personal Records",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "v1.0.0 · Material 3 Expressive Design",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Kişisel veri egemenliği & yaşam günlüğü paneli",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp)
    )
}

@Composable
private fun SettingsCard(
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = null
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            content = content
        )
    }
}

@Composable
private fun SettingsIconBox(
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(MaterialTheme.shapes.small)
            .background(containerColor),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(20.dp)
        )
    }
}
