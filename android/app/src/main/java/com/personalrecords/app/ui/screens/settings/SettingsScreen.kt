package com.personalrecords.app.ui.screens.settings

import android.content.ContentValues
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personalrecords.app.R
import com.personalrecords.app.core.theme.*
import com.personalrecords.app.ui.components.*
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
import androidx.core.net.toUri

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    scrollToTopFlow: kotlinx.coroutines.flow.Flow<Unit>? = null,
    refreshFlow: kotlinx.coroutines.flow.Flow<Unit>? = null,
    onNavigateToLanguage: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showKeyEditor by remember { mutableStateOf(false) }
    var showLetterboxdEditor by remember { mutableStateOf(false) }
    var showGoodreadsEditor by remember { mutableStateOf(false) }
    var secretVisible by remember { mutableStateOf(false) }

    val clipboardManager = LocalClipboardManager.current

    fun triggerDownload(url: String, filename: String) {
        coroutineScope.launch {
            snackbarHostState.showSnackbar(context.getString(R.string.toast_downloading, filename))
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
                } catch (_: Exception) {
                    false
                }
            }

            if (success) {
                snackbarHostState.showSnackbar(context.getString(R.string.toast_downloaded, filename))
            } else {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, url.toUri())
                    context.startActivity(intent)
                    snackbarHostState.showSnackbar(context.getString(R.string.toast_download_browser))
                } catch (_: Exception) {
                    snackbarHostState.showSnackbar(context.getString(R.string.toast_download_failed))
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

    LaunchedEffect(state.letterboxdSaveMessage) {
        state.letterboxdSaveMessage?.let {
            snackbarHostState.showSnackbar(it)
            if (!it.startsWith("Hata") && !it.startsWith("Error")) {
                showLetterboxdEditor = false
            }
        }
    }

    LaunchedEffect(state.goodreadsSaveMessage) {
        state.goodreadsSaveMessage?.let {
            snackbarHostState.showSnackbar(it)
            if (!it.startsWith("Hata") && !it.startsWith("Error")) {
                showGoodreadsEditor = false
            }
        }
    }

    var isRefreshing by remember { mutableStateOf(false) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    LaunchedEffect(state.isTestingConnection) {
        if (!state.isTestingConnection) isRefreshing = false
    }

    LaunchedEffect(scrollToTopFlow) {
        scrollToTopFlow?.collect {
            listState.expressiveScrollToTop()
        }
    }

    LaunchedEffect(refreshFlow) {
        refreshFlow?.collect {
            isRefreshing = true
            viewModel.testConnection()
            viewModel.loadAllSettings()
            if (!listState.isScrollInProgress) {
                listState.expressiveScrollToTop()
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.statusBars,
        snackbarHost = { SnackbarHost(snackbarHostState, modifier = Modifier.navigationBarsPadding().padding(bottom = 96.dp)) }
    ) { innerPadding ->
        ExpressivePullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                viewModel.testConnection()
                viewModel.loadAllSettings()
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = 144.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // ==========================================
                // SECTION 0: Dil ve Bölge / Language & Region
                // ==========================================
                item {
                    SettingsSectionTitle(title = stringResource(R.string.settings_section_language))
                }
                item {
                    SettingsCard {
                        val currentLanguageLabel = when (state.appLanguage) {
                            "tr" -> "Türkçe"
                            "en" -> "English"
                            else -> stringResource(R.string.settings_language_system)
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(M3Shapes.CornerMedium)
                                .clickable { onNavigateToLanguage() }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            SettingsIconBox(
                                icon = Icons.Default.Language,
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.settings_language_title),
                                    style = MaterialTheme.typography.titleMediumEmphasized,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = currentLanguageLabel,
                                    style = MaterialTheme.typography.bodySmallEmphasized,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // ==========================================
                // SECTION 1: Sunucu ve Altyapı
                // ==========================================
                item {
                    SettingsSectionTitle(title = stringResource(R.string.settings_section_server))
                }
                item {
                    SettingsCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                SettingsIconBox(
                                    icon = Icons.Default.Dns,
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Column(modifier = Modifier.weight(1f, fill = false)) {
                                    Text(
                                        text = stringResource(R.string.settings_server_title),
                                        style = MaterialTheme.typography.titleMediumEmphasized,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = stringResource(R.string.settings_server_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            // Connection status chip
                            state.connectionSuccess?.let { ok ->
                                val statusBg = if (ok) domainNutritionContainer() else MaterialTheme.colorScheme.errorContainer
                                val statusFg = if (ok) domainNutritionOnContainer() else MaterialTheme.colorScheme.onErrorContainer
                                Surface(
                                    shape = M3Shapes.CornerFull,
                                    color = statusBg,
                                    modifier = Modifier.padding(start = 8.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .background(statusFg, M3Shapes.CornerFull)
                                        )
                                        Text(
                                            text = if (ok) stringResource(R.string.badge_connected) else stringResource(R.string.action_retry),
                                            color = statusFg,
                                            style = MaterialTheme.typography.labelSmallEmphasized,
                                            maxLines = 1,
                                            softWrap = false
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
                            label = { Text(stringResource(R.string.settings_server_url_label)) },
                            placeholder = { Text("https://personal-records-api.workers.dev") },
                            singleLine = true,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Dns,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            trailingIcon = if (state.serverUrl.isNotBlank()) {
                                {
                                    IconButton(onClick = { viewModel.updateServerUrlInput("") }) {
                                        Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.action_clear))
                                    }
                                }
                            } else null,
                            shape = M3Shapes.CornerMedium
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val saveBtnInteraction = remember { MutableInteractionSource() }
                            Button(
                                onClick = { viewModel.saveServerUrl() },
                                interactionSource = saveBtnInteraction,
                                shape = rememberExpressiveButtonShape(
                                    interactionSource = saveBtnInteraction,
                                    restingCorner = M3CornerRadius.Medium,
                                    pressedCorner = M3CornerRadius.ExtraSmall
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .expressivePressScale(saveBtnInteraction),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Text(
                                        text = stringResource(R.string.action_save),
                                        style = MaterialTheme.typography.labelLargeEmphasized,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            val testBtnInteraction = remember { MutableInteractionSource() }
                            OutlinedButton(
                                onClick = { viewModel.testConnection() },
                                enabled = !state.isTestingConnection,
                                interactionSource = testBtnInteraction,
                                shape = rememberExpressiveButtonShape(
                                    interactionSource = testBtnInteraction,
                                    restingCorner = M3CornerRadius.Medium,
                                    pressedCorner = M3CornerRadius.ExtraSmall
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .expressivePressScale(testBtnInteraction),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                            ) {
                                if (state.isTestingConnection) {
                                    ExpressiveLoadingIndicator(
                                        size = 16.dp,
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                } else {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Text(
                                            text = stringResource(R.string.action_test_connection),
                                            style = MaterialTheme.typography.labelLarge,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ==========================================
                // SECTION 2: Bildirim Webhook Entegrasyonu
                // ==========================================
                item {
                    SettingsSectionTitle(title = stringResource(R.string.settings_section_webhook))
                }
                item {
                    SettingsCard {
                        val cleanBaseUrl = state.serverUrl.trim().trimEnd('/')
                        val webhookUrl = if (cleanBaseUrl.isNotBlank()) "$cleanBaseUrl/api/webhooks/notification" else "https://.../api/webhooks/notification"

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                SettingsIconBox(
                                    icon = Icons.Default.Notifications,
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Column(modifier = Modifier.weight(1f, fill = false)) {
                                    Text(
                                        text = stringResource(R.string.settings_webhook_title),
                                        style = MaterialTheme.typography.titleMediumEmphasized,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = stringResource(R.string.settings_webhook_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = stringResource(R.string.settings_webhook_relay_summary),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Webhook URL display (nested inside 20dp SettingsCard -> 12dp CornerMedium)
                        Surface(
                            shape = M3Shapes.CornerMedium,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = stringResource(R.string.settings_target_webhook_url),
                                    style = MaterialTheme.typography.labelSmallEmphasized,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                SelectionContainer {
                                    Text(
                                        text = webhookUrl,
                                        style = MaterialTheme.typography.bodySmallEmphasized,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(webhookUrl))
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(context.getString(R.string.toast_webhook_copied))
                                    }
                                },
                                shape = M3Shapes.CornerMedium,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .expressivePressScale(remember { MutableInteractionSource() }),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = stringResource(R.string.action_copy_url),
                                        style = MaterialTheme.typography.labelLargeEmphasized,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            OutlinedButton(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW,
                                        "https://github.com/cobanov/notification-relay-webhook/releases".toUri())
                                    context.startActivity(intent)
                                },
                                shape = M3Shapes.CornerMedium,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .expressivePressScale(remember { MutableInteractionSource() }),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
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
                                    Text(
                                        text = stringResource(R.string.action_download_apk),
                                        style = MaterialTheme.typography.labelLarge,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Surface(
                            shape = M3Shapes.CornerMedium,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = stringResource(R.string.settings_setup_guide_title),
                                        style = MaterialTheme.typography.labelMediumEmphasized
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "${stringResource(R.string.settings_setup_step_1)}\n${stringResource(R.string.settings_setup_step_2)}\n${stringResource(R.string.settings_setup_step_3)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // ==========================================
                // SECTION 3: Beslenme & FatSecret BYOK
                // ==========================================
                item {
                    SettingsSectionTitle(title = stringResource(R.string.settings_section_fatsecret))
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
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                SettingsIconBox(
                                    icon = Icons.Default.Key,
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Column(modifier = Modifier.weight(1f, fill = false)) {
                                    Text(
                                        text = stringResource(R.string.settings_fatsecret_title),
                                        style = MaterialTheme.typography.titleMediumEmphasized,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = stringResource(R.string.settings_fatsecret_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            if (state.isConnected) {
                                Surface(
                                    shape = M3Shapes.CornerFull,
                                    color = domainNutritionContainer(),
                                    modifier = Modifier.padding(start = 8.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .background(domainNutritionOnContainer(), M3Shapes.CornerFull)
                                        )
                                        Text(
                                            text = stringResource(R.string.badge_connected),
                                            color = domainNutritionOnContainer(),
                                            style = MaterialTheme.typography.labelSmallEmphasized,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            } else if (state.hasKeys) {
                                Surface(
                                    shape = M3Shapes.CornerFull,
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                    modifier = Modifier.padding(start = 8.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.badge_key_ready),
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        style = MaterialTheme.typography.labelSmallEmphasized,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }

                        // Configured Summary Box (nested inside 20dp SettingsCard -> 12dp CornerMedium)
                        if (state.isConnected) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                shape = M3Shapes.CornerMedium,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.65f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = domainNutritionColor(),
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = stringResource(R.string.settings_fatsecret_connected_banner),
                                                style = MaterialTheme.typography.titleSmallEmphasized
                                            )
                                            if (state.maskedKey.isNotBlank()) {
                                                Text(
                                                    text = stringResource(R.string.badge_active_key, state.maskedKey),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                    // Nested inside 12dp summary Surface -> 8dp CornerSmall
                                    FilledTonalButton(
                                        onClick = { showKeyEditor = !showKeyEditor },
                                        shape = M3Shapes.CornerSmall,
                                        modifier = Modifier
                                            .height(34.dp)
                                            .expressivePressScale(remember { MutableInteractionSource() }),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (showKeyEditor) Icons.Default.ExpandLess else Icons.Default.Edit,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = if (showKeyEditor) stringResource(R.string.action_cancel) else stringResource(R.string.action_edit),
                                                style = MaterialTheme.typography.labelMedium,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Key Inputs (Spatial spring for height + Effects spring for opacity)
                        AnimatedVisibility(
                            visible = !state.isConnected || showKeyEditor,
                            enter = expandVertically(
                                animationSpec = M3ExpressiveMotion.defaultSpatial()
                            ) + fadeIn(animationSpec = M3ExpressiveMotion.defaultEffects()),
                            exit = shrinkVertically(
                                animationSpec = M3ExpressiveMotion.defaultSpatial()
                            ) + fadeOut(animationSpec = M3ExpressiveMotion.fastEffects())
                        ) {
                            Surface(
                                shape = M3Shapes.CornerMedium,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.65f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    OutlinedTextField(
                                        value = state.consumerKey,
                                        onValueChange = { viewModel.updateConsumerKey(it) },
                                        modifier = Modifier.fillMaxWidth(),
                                        label = { Text(stringResource(R.string.settings_consumer_key_label)) },
                                        placeholder = { Text(stringResource(R.string.settings_consumer_key_label)) },
                                        singleLine = true,
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.VpnKey,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        },
                                        trailingIcon = if (state.consumerKey.isNotBlank()) {
                                            {
                                                IconButton(onClick = { viewModel.updateConsumerKey("") }) {
                                                    Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.action_clear))
                                                }
                                            }
                                        } else null,
                                        shape = M3Shapes.CornerSmall
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))

                                    OutlinedTextField(
                                        value = state.consumerSecret,
                                        onValueChange = { viewModel.updateConsumerSecret(it) },
                                        modifier = Modifier.fillMaxWidth(),
                                        label = { Text(stringResource(R.string.settings_consumer_secret_label)) },
                                        placeholder = { Text(stringResource(R.string.settings_consumer_secret_label)) },
                                        singleLine = true,
                                        visualTransformation = if (secretVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Lock,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        },
                                        trailingIcon = {
                                            IconButton(onClick = { secretVisible = !secretVisible }) {
                                                Icon(
                                                    imageVector = if (secretVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        },
                                        shape = M3Shapes.CornerSmall
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))

                                    Button(
                                        onClick = {
                                            viewModel.saveFatSecretKeys()
                                            showKeyEditor = false
                                        },
                                        enabled = !state.isSavingKeys && state.consumerKey.isNotBlank() && state.consumerSecret.isNotBlank(),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(42.dp)
                                            .expressivePressScale(remember { MutableInteractionSource() }),
                                        shape = M3Shapes.CornerSmall,
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                                    ) {
                                        if (state.isSavingKeys) {
                                            ExpressiveLoadingIndicator(
                                                size = 16.dp,
                                                strokeWidth = 2.dp,
                                                color = MaterialTheme.colorScheme.onPrimary
                                            )
                                        } else {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Text(
                                                    text = stringResource(R.string.settings_btn_save_keys),
                                                    style = MaterialTheme.typography.labelLargeEmphasized,
                                                    maxLines = 1,
                                                    softWrap = false,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }

                                    if (state.hasKeys && !state.isConnected) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        FilledTonalButton(
                                            onClick = {
                                                val baseUrl = state.serverUrl.trim().trimEnd('/')
                                                if (baseUrl.isBlank()) {
                                                    coroutineScope.launch {
                                                        snackbarHostState.showSnackbar(context.getString(R.string.settings_save_server_first))
                                                    }
                                                } else {
                                                    val intent = Intent(
                                                        Intent.ACTION_VIEW,
                                                        "$baseUrl/fatsecret/connect".toUri()
                                                    )
                                                    context.startActivity(intent)
                                                }
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(42.dp)
                                                .expressivePressScale(remember { MutableInteractionSource() }),
                                            shape = M3Shapes.CornerSmall,
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
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
                                                Text(
                                                    text = stringResource(R.string.settings_btn_connect_oauth),
                                                    style = MaterialTheme.typography.labelLarge,
                                                    maxLines = 1,
                                                    softWrap = false,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))
                                    Surface(
                                        shape = M3Shapes.CornerSmall,
                                        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.8f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                                Text(
                                                    text = stringResource(R.string.settings_fatsecret_help_title),
                                                    style = MaterialTheme.typography.labelMediumEmphasized
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "${stringResource(R.string.settings_fatsecret_help_step_1)}\n${stringResource(R.string.settings_fatsecret_help_step_2)}\n${stringResource(R.string.settings_fatsecret_help_step_3)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            TextButton(
                                                onClick = {
                                                    val intent = Intent(
                                                        Intent.ACTION_VIEW,
                                                        "https://platform.fatsecret.com/".toUri()
                                                    )
                                                    context.startActivity(intent)
                                                },
                                                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Text(
                                                        text = "platform.fatsecret.com",
                                                        style = MaterialTheme.typography.labelSmall
                                                    )
                                                    Icon(
                                                        imageVector = Icons.Default.ArrowOutward,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Divider before sync section
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
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
                                text = stringResource(R.string.settings_fatsecret_sync_title),
                                style = MaterialTheme.typography.titleSmallEmphasized
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.settings_fatsecret_sync_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilledTonalButton(
                                    onClick = { viewModel.syncFatSecret(1) },
                                    enabled = state.isSyncing == null,
                                    shape = M3Shapes.CornerMedium,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp)
                                        .expressivePressScale(remember { MutableInteractionSource() }),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.settings_sync_today),
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                FilledTonalButton(
                                    onClick = { viewModel.syncFatSecret(30) },
                                    enabled = state.isSyncing == null,
                                    shape = M3Shapes.CornerMedium,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp)
                                        .expressivePressScale(remember { MutableInteractionSource() }),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.settings_sync_30d),
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilledTonalButton(
                                    onClick = { viewModel.syncFatSecret(365) },
                                    enabled = state.isSyncing == null,
                                    shape = M3Shapes.CornerMedium,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp)
                                        .expressivePressScale(remember { MutableInteractionSource() }),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.settings_sync_1y),
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                FilledTonalButton(
                                    onClick = { viewModel.syncFatSecret(1095) },
                                    enabled = state.isSyncing == null,
                                    shape = M3Shapes.CornerMedium,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp)
                                        .expressivePressScale(remember { MutableInteractionSource() }),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.settings_sync_all),
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                // ==========================================
                // SECTION 4: Medya & Kültür Senkronizasyonu
                // ==========================================
                item {
                    SettingsSectionTitle(title = stringResource(R.string.settings_section_media))
                }
                item {
                    SettingsCard {
                        // Letterboxd Card Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                SettingsIconBox(
                                    icon = Icons.Default.Movie,
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Column(modifier = Modifier.weight(1f, fill = false)) {
                                    Text(
                                        text = "Letterboxd",
                                        style = MaterialTheme.typography.titleMediumEmphasized,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = stringResource(R.string.settings_letterboxd_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            if (state.letterboxdUrl.isNotBlank()) {
                                Surface(
                                    shape = M3Shapes.CornerFull,
                                    color = domainNutritionContainer(),
                                    modifier = Modifier.padding(start = 8.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .background(domainNutritionOnContainer(), M3Shapes.CornerFull)
                                        )
                                        Text(
                                            text = stringResource(R.string.badge_connected),
                                            color = domainNutritionOnContainer(),
                                            style = MaterialTheme.typography.labelSmallEmphasized,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }
                        }

                        // Configured Summary Box (nested inside 20dp SettingsCard -> 12dp CornerMedium)
                        if (state.letterboxdUrl.isNotBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                shape = M3Shapes.CornerMedium,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.65f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = domainNutritionColor(),
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            val username = remember(state.letterboxdUrl) {
                                                extractLetterboxdUsername(state.letterboxdUrl)
                                            }
                                            Text(
                                                text = "@$username",
                                                style = MaterialTheme.typography.titleSmallEmphasized
                                            )
                                            Text(
                                                text = state.letterboxdUrl,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    // Nested inside 12dp summary Surface -> 8dp CornerSmall
                                    FilledTonalButton(
                                        onClick = { showLetterboxdEditor = !showLetterboxdEditor },
                                        shape = M3Shapes.CornerSmall,
                                        modifier = Modifier
                                            .height(34.dp)
                                            .expressivePressScale(remember { MutableInteractionSource() }),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (showLetterboxdEditor) Icons.Default.ExpandLess else Icons.Default.Edit,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = if (showLetterboxdEditor) stringResource(R.string.action_cancel) else stringResource(R.string.action_edit),
                                                style = MaterialTheme.typography.labelMedium,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Editor / Initial Setup Inputs (Spatial spring for height + Effects spring for opacity)
                        AnimatedVisibility(
                            visible = state.letterboxdUrl.isBlank() || showLetterboxdEditor,
                            enter = expandVertically(
                                animationSpec = M3ExpressiveMotion.defaultSpatial()
                            ) + fadeIn(animationSpec = M3ExpressiveMotion.defaultEffects()),
                            exit = shrinkVertically(
                                animationSpec = M3ExpressiveMotion.defaultSpatial()
                            ) + fadeOut(animationSpec = M3ExpressiveMotion.fastEffects())
                        ) {
                            Surface(
                                shape = M3Shapes.CornerMedium,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.65f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    OutlinedTextField(
                                        value = state.letterboxdUrl,
                                        onValueChange = { viewModel.updateLetterboxdUrl(it) },
                                        modifier = Modifier.fillMaxWidth(),
                                        label = { Text(stringResource(R.string.settings_letterboxd_user_label)) },
                                        placeholder = { Text(stringResource(R.string.settings_letterboxd_user_hint)) },
                                        singleLine = true,
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.AlternateEmail,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        },
                                        trailingIcon = if (state.letterboxdUrl.isNotBlank()) {
                                            {
                                                IconButton(onClick = { viewModel.updateLetterboxdUrl("") }) {
                                                    Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.action_clear))
                                                }
                                            }
                                        } else null,
                                        shape = M3Shapes.CornerSmall,
                                        supportingText = {
                                            Text(stringResource(R.string.settings_letterboxd_support))
                                        }
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Button(
                                        onClick = {
                                            viewModel.saveLetterboxdSettings()
                                            showLetterboxdEditor = false
                                        },
                                        enabled = !state.isSavingLetterboxd && state.letterboxdUrl.isNotBlank(),
                                        shape = M3Shapes.CornerSmall,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(42.dp)
                                            .expressivePressScale(remember { MutableInteractionSource() }),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                                    ) {
                                        if (state.isSavingLetterboxd) {
                                            ExpressiveLoadingIndicator(
                                                size = 16.dp,
                                                strokeWidth = 2.dp,
                                                color = MaterialTheme.colorScheme.onPrimary
                                            )
                                        } else {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Text(
                                                    text = stringResource(R.string.action_save),
                                                    style = MaterialTheme.typography.labelLargeEmphasized,
                                                    maxLines = 1,
                                                    softWrap = false,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (state.letterboxdUrl.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            FilledTonalButton(
                                onClick = { viewModel.syncLetterboxd() },
                                enabled = state.isSyncing == null,
                                shape = M3Shapes.CornerMedium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(42.dp)
                                    .expressivePressScale(remember { MutableInteractionSource() }),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                            ) {
                                if (state.isSyncing == "letterboxd") {
                                    ExpressiveLoadingIndicator(
                                        size = 16.dp,
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.action_syncing),
                                        style = MaterialTheme.typography.labelLarge,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                } else {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Sync,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = stringResource(R.string.action_sync),
                                            style = MaterialTheme.typography.labelLarge,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    SettingsCard {
                        // Goodreads Card Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                SettingsIconBox(
                                    icon = Icons.AutoMirrored.Filled.MenuBook,
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Column(modifier = Modifier.weight(1f, fill = false)) {
                                    Text(
                                        text = "Goodreads",
                                        style = MaterialTheme.typography.titleMediumEmphasized,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = stringResource(R.string.settings_goodreads_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            if (state.goodreadsUrl.isNotBlank()) {
                                Surface(
                                    shape = M3Shapes.CornerFull,
                                    color = domainNutritionContainer(),
                                    modifier = Modifier.padding(start = 8.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .background(domainNutritionOnContainer(), M3Shapes.CornerFull)
                                        )
                                        Text(
                                            text = stringResource(R.string.badge_connected),
                                            color = domainNutritionOnContainer(),
                                            style = MaterialTheme.typography.labelSmallEmphasized,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }
                        }

                        // Configured Summary Box (nested inside 20dp SettingsCard -> 12dp CornerMedium)
                        if (state.goodreadsUrl.isNotBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                shape = M3Shapes.CornerMedium,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.65f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = domainNutritionColor(),
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            val goodreadsId = remember(state.goodreadsUrl) {
                                                extractGoodreadsId(state.goodreadsUrl)
                                            }
                                            Text(
                                                text = "ID: $goodreadsId",
                                                style = MaterialTheme.typography.titleSmallEmphasized
                                            )
                                            Text(
                                                text = state.goodreadsUrl,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    // Nested inside 12dp summary Surface -> 8dp CornerSmall
                                    FilledTonalButton(
                                        onClick = { showGoodreadsEditor = !showGoodreadsEditor },
                                        shape = M3Shapes.CornerSmall,
                                        modifier = Modifier
                                            .height(34.dp)
                                            .expressivePressScale(remember { MutableInteractionSource() }),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (showGoodreadsEditor) Icons.Default.ExpandLess else Icons.Default.Edit,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = if (showGoodreadsEditor) stringResource(R.string.action_cancel) else stringResource(R.string.action_edit),
                                                style = MaterialTheme.typography.labelMedium,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Editor / Initial Setup Inputs (Spatial spring for height + Effects spring for opacity)
                        AnimatedVisibility(
                            visible = state.goodreadsUrl.isBlank() || showGoodreadsEditor,
                            enter = expandVertically(
                                animationSpec = M3ExpressiveMotion.defaultSpatial()
                            ) + fadeIn(animationSpec = M3ExpressiveMotion.defaultEffects()),
                            exit = shrinkVertically(
                                animationSpec = M3ExpressiveMotion.defaultSpatial()
                            ) + fadeOut(animationSpec = M3ExpressiveMotion.fastEffects())
                        ) {
                            Surface(
                                shape = M3Shapes.CornerMedium,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.65f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    OutlinedTextField(
                                        value = state.goodreadsUrl,
                                        onValueChange = { viewModel.updateGoodreadsUrl(it) },
                                        modifier = Modifier.fillMaxWidth(),
                                        label = { Text(stringResource(R.string.settings_goodreads_user_label)) },
                                        placeholder = { Text(stringResource(R.string.settings_goodreads_user_hint)) },
                                        singleLine = true,
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Numbers,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        },
                                        trailingIcon = if (state.goodreadsUrl.isNotBlank()) {
                                            {
                                                IconButton(onClick = { viewModel.updateGoodreadsUrl("") }) {
                                                    Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.action_clear))
                                                }
                                            }
                                        } else null,
                                        shape = M3Shapes.CornerSmall,
                                        supportingText = {
                                            Text(stringResource(R.string.settings_goodreads_support))
                                        }
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Button(
                                        onClick = {
                                            viewModel.saveGoodreadsSettings()
                                            showGoodreadsEditor = false
                                        },
                                        enabled = !state.isSavingGoodreads && state.goodreadsUrl.isNotBlank(),
                                        shape = M3Shapes.CornerSmall,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(42.dp)
                                            .expressivePressScale(remember { MutableInteractionSource() }),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                                    ) {
                                        if (state.isSavingGoodreads) {
                                            ExpressiveLoadingIndicator(
                                                size = 16.dp,
                                                strokeWidth = 2.dp,
                                                color = MaterialTheme.colorScheme.onPrimary
                                            )
                                        } else {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Text(
                                                    text = stringResource(R.string.action_save),
                                                    style = MaterialTheme.typography.labelLargeEmphasized,
                                                    maxLines = 1,
                                                    softWrap = false,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (state.goodreadsUrl.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            FilledTonalButton(
                                onClick = { viewModel.syncGoodreads() },
                                enabled = state.isSyncing == null,
                                shape = M3Shapes.CornerMedium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(42.dp)
                                    .expressivePressScale(remember { MutableInteractionSource() }),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                            ) {
                                if (state.isSyncing == "goodreads") {
                                    ExpressiveLoadingIndicator(
                                        size = 16.dp,
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.action_syncing),
                                        style = MaterialTheme.typography.labelLarge,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                } else {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Sync,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = stringResource(R.string.action_sync),
                                            style = MaterialTheme.typography.labelLarge,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ==========================================
                // SECTION 5: Veri Egemenliği & Yedekleme
                // ==========================================
                item {
                    SettingsSectionTitle(title = stringResource(R.string.settings_section_export))
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
                                    text = stringResource(R.string.settings_export_title),
                                    style = MaterialTheme.typography.titleMediumEmphasized
                                )
                                Text(
                                    text = stringResource(R.string.settings_export_desc),
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
                                            snackbarHostState.showSnackbar(context.getString(R.string.settings_save_server_first))
                                        }
                                    } else {
                                        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                                        triggerDownload("$baseUrl/api/export/all", "personal_records_$timestamp.json")
                                    }
                                },
                                shape = M3Shapes.CornerMedium,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .expressivePressScale(remember { MutableInteractionSource() }),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
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
                                    Text(
                                        text = stringResource(R.string.settings_btn_export_json),
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            FilledTonalButton(
                                onClick = {
                                    val baseUrl = state.serverUrl.trim().trimEnd('/')
                                    if (baseUrl.isBlank()) {
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar(context.getString(R.string.settings_save_server_first))
                                        }
                                    } else {
                                        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                                        triggerDownload("$baseUrl/api/export/db", "personal_records_$timestamp.sqlite3")
                                    }
                                },
                                shape = M3Shapes.CornerMedium,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .expressivePressScale(remember { MutableInteractionSource() }),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
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
                                    Text(
                                        text = stringResource(R.string.settings_btn_export_sqlite),
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
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
                                text = stringResource(R.string.settings_export_note),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // ==========================================
                // SECTION 6: Uygulama Bilgisi (Simple Text Footer)
                // ==========================================
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp, bottom = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "${stringResource(R.string.app_name)} • ${stringResource(R.string.app_version_info)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                        Text(
                            text = stringResource(R.string.app_description),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
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
        style = MaterialTheme.typography.labelLargeEmphasized,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp)
    )
}

@Composable
private fun SettingsCard(
    content: @Composable ColumnScope.() -> Unit
) {
    // Outer card uses M3 Expressive CornerLargeIncreased (20dp); nested children use 12dp/8dp per optical roundness rule
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = M3Shapes.CornerLargeIncreased,
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
    // Nested inside 20dp SettingsCard (with 14dp padding) -> 12dp CornerMedium
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(M3Shapes.CornerMedium)
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


private data class LanguageOption(
    val code: String,
    val labelRes: Int? = null,
    val rawLabel: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSelectionScreen(
    currentLanguage: String,
    onLanguageSelected: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    val languages = remember {
        listOf(
            LanguageOption(code = "system", labelRes = R.string.settings_language_system),
            LanguageOption(code = "en", rawLabel = "English"),
            LanguageOption(code = "tr", rawLabel = "Türkçe")
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.statusBars,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_language_screen_title),
                        style = MaterialTheme.typography.titleLargeEmphasized
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                actions = {},
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp),
            contentPadding = PaddingValues(top = 10.dp, bottom = 144.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                SettingsCard {
                    languages.forEachIndexed { index, item ->
                        val isSelected = currentLanguage == item.code
                        val label = item.labelRes?.let { stringResource(it) } ?: item.rawLabel.orEmpty()

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(M3Shapes.CornerSmall)
                                .clickable { onLanguageSelected(item.code) }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = label,
                                style = if (isSelected) MaterialTheme.typography.bodyLargeEmphasized else MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .background(MaterialTheme.colorScheme.primary, M3Shapes.CornerFull),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                        if (index < languages.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun extractLetterboxdUsername(url: String): String {
    val trimmed = url.trim()
    val pattern = Regex("""letterboxd\.com/([^/]+)""", RegexOption.IGNORE_CASE)
    val match = pattern.find(trimmed)
    if (match != null) {
        val user = match.groupValues[1]
        if (user.isNotBlank() && user != "rss") {
            return user
        }
    }
    return trimmed.removePrefix("@").removeSuffix("/rss").removeSuffix("/rss/").trim('/')
}

private fun extractGoodreadsId(url: String): String {
    val trimmed = url.trim()
    val pattern = Regex("""(?:list_rss|user/show)/([0-9]+)""", RegexOption.IGNORE_CASE)
    val match = pattern.find(trimmed)
    if (match != null) {
        return match.groupValues[1]
    }
    return trimmed
}

