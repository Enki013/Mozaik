package com.personalrecords.app.ui.screens.settings

import android.content.ContentValues
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import com.personalrecords.app.ui.screens.catalog.recordCategory
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.role
import androidx.compose.foundation.lazy.LazyListScope
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.runtime.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
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
import com.personalrecords.app.core.integrations.IntegrationPlugin
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    scrollToTopFlow: kotlinx.coroutines.flow.Flow<Unit>? = null,
    refreshFlow: kotlinx.coroutines.flow.Flow<Unit>? = null,
    onNavigateToLanguage: () -> Unit = {},
    onNavigateToIntegrationsCatalog: () -> Unit = {},
    /** Opens the page of the server ([SERVER_PAGE_ID]) or of one integration (its plugin id). */
    onOpenSettingsPage: (String) -> Unit = {},
    /**
     * null: the main settings list. Otherwise this instance is a settings page showing only the
     * server form or one integration's settings.
     */
    pageId: String? = null,
    onNavigateBack: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showKeyEditor by remember { mutableStateOf(false) }
    var showLetterboxdEditor by remember { mutableStateOf(false) }
    var showGoodreadsEditor by remember { mutableStateOf(false) }
    var showLastFmEditor by remember { mutableStateOf(false) }
    var showListenBrainzEditor by remember { mutableStateOf(false) }
    var showStatsFmEditor by remember { mutableStateOf(false) }
    var showTraktEditor by remember { mutableStateOf(false) }
    var traktUsernameInput by remember(state.traktUsername) { mutableStateOf(state.traktUsername) }
    var traktClientIdInput by remember(state.traktClientId) { mutableStateOf(state.traktClientId) }
    var traktSyncMoviesInput by remember(state.traktSyncMovies) { mutableStateOf(state.traktSyncMovies) }
    var traktSyncEpisodesInput by remember(state.traktSyncEpisodes) { mutableStateOf(state.traktSyncEpisodes) }
    var traktSyncRatingsInput by remember(state.traktSyncRatings) { mutableStateOf(state.traktSyncRatings) }

    var showMdbListEditor by remember { mutableStateOf(false) }
    var mdblistApiKeyInput by remember(state.mdblistApiKey) { mutableStateOf(state.mdblistApiKey) }
    var mdblistApiKeyVisible by remember { mutableStateOf(false) }
    var mdblistSyncRatingsInput by remember(state.mdblistSyncRatings) { mutableStateOf(state.mdblistSyncRatings) }
    var mdblistSyncListsInput by remember(state.mdblistSyncLists) { mutableStateOf(state.mdblistSyncLists) }

    var showSimklEditor by remember { mutableStateOf(false) }
    var simklClientIdInput by remember(state.simklClientId) { mutableStateOf(state.simklClientId) }
    var simklAccessTokenInput by remember { mutableStateOf("") }
    var simklAccessTokenVisible by remember { mutableStateOf(false) }
    var simklSyncMoviesInput by remember(state.simklSyncMovies) { mutableStateOf(state.simklSyncMovies) }
    var simklSyncShowsInput by remember(state.simklSyncShows) { mutableStateOf(state.simklSyncShows) }
    var simklSyncAnimeInput by remember(state.simklSyncAnime) { mutableStateOf(state.simklSyncAnime) }
    var simklSyncRatingsInput by remember(state.simklSyncRatings) { mutableStateOf(state.simklSyncRatings) }

    var secretVisible by remember { mutableStateOf(false) }
    var lastfmKeyVisible by remember { mutableStateOf(false) }
    var lastfmKeyCleared by remember(showLastFmEditor) { mutableStateOf(false) }
    var listenBrainzTokenVisible by remember { mutableStateOf(false) }
    var listenBrainzTokenCleared by remember(showListenBrainzEditor) { mutableStateOf(false) }

    val clipboardManager = LocalClipboardManager.current

    val otherActivePlugins = remember(state.enabledPluginIds) {
        com.personalrecords.app.core.integrations.PluginRegistry.defaultPlugins.filter { plugin ->
            (plugin.id in state.enabledPluginIds) &&
                plugin.id !in setOf("notification", "notification_relay", "health_connect", "fatsecret", "letterboxd", "goodreads", "lastfm", "listenbrainz", "statsfm", "web_scrobbler", "trakt", "mdblist", "simkl")
        }
    }

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
        state.syncStatusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSyncStatusMessage()
        }
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

    LaunchedEffect(state.musicSaveMessage) {
        state.musicSaveMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMusicSaveMessage()
            if (!it.startsWith("Hata") && !it.startsWith("Error")) {
                showLastFmEditor = false
            }
        }
    }

    LaunchedEffect(state.listenbrainzSaveMessage) {
        state.listenbrainzSaveMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearListenbrainzSaveMessage()
            if (!it.startsWith("Hata") && !it.startsWith("Error")) {
                showListenBrainzEditor = false
            }
        }
    }

    LaunchedEffect(state.statsfmSaveMessage) {
        state.statsfmSaveMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatsfmSaveMessage()
            if (!it.startsWith("Hata") && !it.startsWith("Error")) {
                showStatsFmEditor = false
            }
        }
    }

    LaunchedEffect(state.traktSaveMessage) {
        state.traktSaveMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearTraktSaveMessage()
            if (!it.startsWith("Hata") && !it.startsWith("Error")) {
                showTraktEditor = false
            }
        }
    }

    LaunchedEffect(state.mdblistSaveMessage) {
        state.mdblistSaveMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMdbListSaveMessage()
            if (!it.startsWith("Hata") && !it.startsWith("Error")) {
                showMdbListEditor = false
            }
        }
    }

    LaunchedEffect(state.simklSaveMessage) {
        state.simklSaveMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSimklSaveMessage()
            if (!it.startsWith("Hata") && !it.startsWith("Error")) {
                showSimklEditor = false
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

    val enabledPlugins = remember(state.enabledPluginIds) {
        com.personalrecords.app.core.integrations.PluginRegistry.defaultPlugins.filter { it.id in state.enabledPluginIds }
    }
    val pageTitle = when (pageId) {
        null -> stringResource(R.string.title_settings)
        SERVER_PAGE_ID -> stringResource(R.string.settings_server_title)
        else -> com.personalrecords.app.core.integrations.PluginRegistry.defaultPlugins
            .firstOrNull { it.id == pageId }?.name ?: stringResource(R.string.title_settings)
    }
    val scrollBehavior = if (pageId == null) {
        TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
    } else {
        TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    }
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            ExpressivePageTopAppBar(
                title = pageTitle,
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    if (pageId != null) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_back)
                            )
                        }
                    }
                }
            )
        },
        contentWindowInsets = WindowInsets.statusBars,
        snackbarHost = { SnackbarHost(snackbarHostState, modifier = Modifier.padding(bottom = floatingNavSnackbarPadding())) }
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
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = floatingBottomNavigationContentPadding()),
                verticalArrangement = Arrangement.spacedBy(if (pageId == null) 16.dp else 8.dp)
            ) {
                if (pageId == null) {
                    settingsMainPage(
                        state = state,
                        enabledPlugins = enabledPlugins,
                        onNavigateToLanguage = onNavigateToLanguage,
                        onOpenSettingsPage = onOpenSettingsPage,
                        onNavigateToIntegrationsCatalog = onNavigateToIntegrationsCatalog,
                        onThemeModeChange = { viewModel.setThemeMode(it) },
                        onDynamicColorChange = { viewModel.setDynamicColor(it) },
                        onExport = { path, extension ->
                            val baseUrl = state.serverUrl.trim().trimEnd('/')
                            if (baseUrl.isBlank()) {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(context.getString(R.string.settings_save_server_first))
                                }
                            } else {
                                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                                triggerDownload("$baseUrl$path", "personal_records_$timestamp.$extension")
                            }
                        }
                    )
                }

                // ==========================================
                // Server page
                // ==========================================
                if (pageId == SERVER_PAGE_ID) {
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
                                val statusBg = if (ok) statusColors(StatusTone.Active).container else MaterialTheme.colorScheme.errorContainer
                                val statusFg = if (ok) statusColors(StatusTone.Active).onContainer else MaterialTheme.colorScheme.onErrorContainer
                                Surface(
                                    shape = M3Shapes.CornerFull,
                                    color = statusBg,
                                    modifier = Modifier
                                        .padding(start = 8.dp)
                                        .expressiveShake(trigger = if (!ok) state.connectionSuccess else null)
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

                        var apiKeyVisible by remember { mutableStateOf(false) }

                        OutlinedTextField(
                            value = state.serverApiKey,
                            onValueChange = { viewModel.updateServerApiKeyInput(it) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.settings_server_api_key_label)) },
                            placeholder = { Text(stringResource(R.string.settings_server_api_key_hint)) },
                            supportingText = { Text(stringResource(R.string.settings_server_api_key_support)) },
                            singleLine = true,
                            visualTransformation = if (apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Key,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            trailingIcon = {
                                IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                                    Icon(
                                        imageVector = if (apiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = stringResource(if (apiKeyVisible) R.string.action_hide_key else R.string.action_show_key)
                                    )
                                }
                            },
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
                                shapes = expressiveButtonShapes(),
                                modifier = Modifier
                                    .weight(1f),
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
                                shapes = expressiveButtonShapes(),
                                modifier = Modifier
                                    .weight(1f),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                            ) {
                                if (state.isTestingConnection) {
                                    ExpressiveLoadingIndicator(
                                        size = 16.dp,
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

                        state.authStatusMessage?.let { msg ->
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (state.connectionSuccess == true) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.error
                                },
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    }
                }
                }

                // 1. Notification Webhook
                if (pageId == "notification" || pageId == "notification_relay") {
                    item(key = "config_notification") {
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
                                shapes = expressiveButtonShapes(),
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(webhookUrl))
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(context.getString(R.string.toast_webhook_copied))
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp),
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
                                shapes = expressiveButtonShapes(),
                                onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW,
                                        "https://github.com/cobanov/notification-relay-webhook/releases".toUri())
                                    context.startActivity(intent)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp),
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

                        if (state.serverApiKey.isNotBlank()) {
                            val authWebhookUrl = "$webhookUrl?api_key=${state.serverApiKey}"
                            Spacer(modifier = Modifier.height(8.dp))
                            WebhookAuthCard(
                                onCopy = {
                                    clipboardManager.setText(AnnotatedString(authWebhookUrl))
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(context.getString(R.string.toast_webhook_auth_copied))
                                    }
                                }
                            )
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
                }

                // 2. Health Connect Webhook
                if (pageId == "health_connect") {
                    item(key = "config_health_connect") {
                        SettingsCard {
                        val cleanBaseUrl = state.serverUrl.trim().trimEnd('/')
                        val healthWebhookUrl = if (cleanBaseUrl.isNotBlank()) "$cleanBaseUrl/api/webhooks/health" else "https://.../api/webhooks/health"

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
                                    icon = Icons.Default.Favorite,
                                    containerColor = recordCategoryColors(RecordCategory.Health).container,
                                    contentColor = recordCategoryColors(RecordCategory.Health).onContainer
                                )
                                Column(modifier = Modifier.weight(1f, fill = false)) {
                                    Text(
                                        text = stringResource(R.string.settings_health_webhook_title),
                                        style = MaterialTheme.typography.titleMediumEmphasized,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = stringResource(R.string.settings_health_webhook_desc),
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
                            text = stringResource(R.string.settings_health_webhook_summary),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(10.dp))

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
                                        text = healthWebhookUrl,
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
                                shapes = expressiveButtonShapes(),
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(healthWebhookUrl))
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(context.getString(R.string.toast_webhook_copied))
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp),
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
                                shapes = expressiveButtonShapes(),
                                onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW,
                                        "https://github.com/mcnaveen/health-connect-webhook/releases".toUri())
                                    context.startActivity(intent)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp),
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

                        if (state.serverApiKey.isNotBlank()) {
                            val authWebhookUrl = "$healthWebhookUrl?api_key=${state.serverApiKey}"
                            Spacer(modifier = Modifier.height(8.dp))
                            WebhookAuthCard(
                                onCopy = {
                                    clipboardManager.setText(AnnotatedString(authWebhookUrl))
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(context.getString(R.string.toast_webhook_auth_copied))
                                    }
                                }
                            )
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
                                    text = "${stringResource(R.string.settings_health_step_1)}\n${stringResource(R.string.settings_health_step_2)}\n${stringResource(R.string.settings_health_step_3)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                }

                // 3. FatSecret BYOK
                if (pageId == "fatsecret") {
                    item(key = "config_fatsecret") {
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
                                ExpressiveStatusBadge(
                                    text = stringResource(R.string.badge_connected),
                                    tone = StatusTone.Active,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
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
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
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
                                            tint = statusColors(StatusTone.Active).indicator,
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
                                        shapes = expressiveButtonShapes(),
                                        onClick = { showKeyEditor = !showKeyEditor },
                                        modifier = Modifier
                                            .heightIn(min = ButtonDefaults.MinHeight),
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
                                                style = MaterialTheme.typography.labelLarge,
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
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
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
                                        shapes = expressiveButtonShapes(),
                                        onClick = {
                                            viewModel.saveFatSecretKeys()
                                            showKeyEditor = false
                                        },
                                        enabled = !state.isSavingKeys && state.consumerKey.isNotBlank() && state.consumerSecret.isNotBlank(),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(42.dp),
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
                                            shapes = expressiveButtonShapes(),
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
                                                .height(42.dp),
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
                                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
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
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
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
                                    shapes = expressiveButtonShapes(),
                                    onClick = { viewModel.syncFatSecret(1) },
                                    enabled = state.isSyncing == null,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp),
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
                                    shapes = expressiveButtonShapes(),
                                    onClick = { viewModel.syncFatSecret(30) },
                                    enabled = state.isSyncing == null,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp),
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
                                    shapes = expressiveButtonShapes(),
                                    onClick = { viewModel.syncFatSecret(365) },
                                    enabled = state.isSyncing == null,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp),
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
                                    shapes = expressiveButtonShapes(),
                                    onClick = { viewModel.syncFatSecret(1095) },
                                    enabled = state.isSyncing == null,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp),
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
                }

                // 4. Letterboxd RSS
                if (pageId == "letterboxd") {
                    item(key = "config_letterboxd") {
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
                                ExpressiveStatusBadge(
                                    text = stringResource(R.string.badge_connected),
                                    tone = StatusTone.Active,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }

                        // Configured Summary Box (nested inside 20dp SettingsCard -> 12dp CornerMedium)
                        if (state.letterboxdUrl.isNotBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                shape = M3Shapes.CornerMedium,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
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
                                            tint = statusColors(StatusTone.Active).indicator,
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
                                        shapes = expressiveButtonShapes(),
                                        onClick = { showLetterboxdEditor = !showLetterboxdEditor },
                                        modifier = Modifier
                                            .heightIn(min = ButtonDefaults.MinHeight),
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
                                                style = MaterialTheme.typography.labelLarge,
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
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
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
                                        shapes = expressiveButtonShapes(),
                                        onClick = {
                                            viewModel.saveLetterboxdSettings()
                                            showLetterboxdEditor = false
                                        },
                                        enabled = !state.isSavingLetterboxd && state.letterboxdUrl.isNotBlank(),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(42.dp),
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
                                shapes = expressiveButtonShapes(),
                                onClick = { viewModel.syncLetterboxd() },
                                enabled = state.isSyncing == null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(42.dp),
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
                }

                // 5. Goodreads RSS
                if (pageId == "goodreads") {
                    item(key = "config_goodreads") {
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
                                ExpressiveStatusBadge(
                                    text = stringResource(R.string.badge_connected),
                                    tone = StatusTone.Active,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }

                        // Configured Summary Box (nested inside 20dp SettingsCard -> 12dp CornerMedium)
                        if (state.goodreadsUrl.isNotBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                shape = M3Shapes.CornerMedium,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
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
                                            tint = statusColors(StatusTone.Active).indicator,
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
                                        shapes = expressiveButtonShapes(),
                                        onClick = { showGoodreadsEditor = !showGoodreadsEditor },
                                        modifier = Modifier
                                            .heightIn(min = ButtonDefaults.MinHeight),
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
                                                style = MaterialTheme.typography.labelLarge,
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
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
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
                                        shapes = expressiveButtonShapes(),
                                        onClick = {
                                            viewModel.saveGoodreadsSettings()
                                            showGoodreadsEditor = false
                                        },
                                        enabled = !state.isSavingGoodreads && state.goodreadsUrl.isNotBlank(),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(42.dp),
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
                                shapes = expressiveButtonShapes(),
                                onClick = { viewModel.syncGoodreads() },
                                enabled = state.isSyncing == null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(42.dp),
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
                }

                // 5b. Trakt.tv API
                if (pageId == "trakt") {
                    item(key = "config_trakt") {
                        SettingsCard {
                            // Trakt Card Header
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
                                        icon = Icons.Default.Tv,
                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Column(modifier = Modifier.weight(1f, fill = false)) {
                                        Text(
                                            text = stringResource(R.string.settings_trakt_title),
                                            style = MaterialTheme.typography.titleMediumEmphasized,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = stringResource(R.string.settings_trakt_desc),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                if (state.traktUsername.isNotBlank()) {
                                    ExpressiveStatusBadge(
                                        text = stringResource(R.string.badge_connected),
                                        tone = StatusTone.Active,
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                }
                            }

                            // Configured Summary Box
                            if (state.traktUsername.isNotBlank()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Surface(
                                    shape = M3Shapes.CornerMedium,
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
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
                                                tint = statusColors(StatusTone.Active).indicator,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "@${state.traktUsername}",
                                                    style = MaterialTheme.typography.titleSmallEmphasized
                                                )
                                                Text(
                                                    text = buildString {
                                                        val items = mutableListOf<String>()
                                                        if (state.traktSyncMovies) items.add("Filmler")
                                                        if (state.traktSyncEpisodes) items.add("Diziler")
                                                        if (state.traktSyncRatings) items.add("Puanlar")
                                                        append(items.joinToString(" · "))
                                                    },
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }

                                        FilledTonalButton(
                                            shapes = expressiveButtonShapes(),
                                            onClick = { showTraktEditor = !showTraktEditor },
                                            modifier = Modifier
                                                .heightIn(min = ButtonDefaults.MinHeight),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (showTraktEditor) Icons.Default.ExpandLess else Icons.Default.Edit,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Text(
                                                    text = if (showTraktEditor) stringResource(R.string.action_cancel) else stringResource(R.string.action_edit),
                                                    style = MaterialTheme.typography.labelLarge,
                                                    maxLines = 1,
                                                    softWrap = false
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Editor / Initial Setup Inputs
                            AnimatedVisibility(
                                visible = state.traktUsername.isBlank() || showTraktEditor,
                                enter = expandVertically(animationSpec = M3ExpressiveMotion.defaultSpatial()) + fadeIn(animationSpec = M3ExpressiveMotion.defaultEffects()),
                                exit = shrinkVertically(animationSpec = M3ExpressiveMotion.defaultSpatial()) + fadeOut(animationSpec = M3ExpressiveMotion.fastEffects())
                            ) {
                                Surface(
                                    shape = M3Shapes.CornerMedium,
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = traktUsernameInput,
                                            onValueChange = { traktUsernameInput = it },
                                            modifier = Modifier.fillMaxWidth(),
                                            label = { Text(stringResource(R.string.settings_trakt_user_label)) },
                                            placeholder = { Text(stringResource(R.string.settings_trakt_user_hint)) },
                                            singleLine = true,
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.Person,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            },
                                            shape = M3Shapes.CornerSmall
                                        )

                                        OutlinedTextField(
                                            value = traktClientIdInput,
                                            onValueChange = { traktClientIdInput = it },
                                            modifier = Modifier.fillMaxWidth(),
                                            label = { Text(stringResource(R.string.settings_trakt_client_id_label)) },
                                            placeholder = { Text(stringResource(R.string.settings_trakt_client_id_hint)) },
                                            singleLine = true,
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.Key,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            },
                                            shape = M3Shapes.CornerSmall,
                                            supportingText = {
                                                Text(stringResource(R.string.settings_trakt_hint))
                                            }
                                        )

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.clickable { traktSyncMoviesInput = !traktSyncMoviesInput }
                                            ) {
                                                Checkbox(
                                                    checked = traktSyncMoviesInput,
                                                    onCheckedChange = { traktSyncMoviesInput = it }
                                                )
                                                Text(
                                                    text = stringResource(R.string.settings_trakt_sync_movies),
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.clickable { traktSyncEpisodesInput = !traktSyncEpisodesInput }
                                            ) {
                                                Checkbox(
                                                    checked = traktSyncEpisodesInput,
                                                    onCheckedChange = { traktSyncEpisodesInput = it }
                                                )
                                                Text(
                                                    text = stringResource(R.string.settings_trakt_sync_episodes),
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }
                                        }

                                        Button(
                                            shapes = expressiveButtonShapes(),
                                            onClick = {
                                                viewModel.saveTraktSettings(
                                                    username = traktUsernameInput,
                                                    clientId = traktClientIdInput,
                                                    syncMovies = traktSyncMoviesInput,
                                                    syncEpisodes = traktSyncEpisodesInput,
                                                    syncRatings = traktSyncRatingsInput
                                                )
                                                showTraktEditor = false
                                            },
                                            enabled = !state.isSavingTrakt && traktUsernameInput.isNotBlank(),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(42.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                                        ) {
                                            if (state.isSavingTrakt) {
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

                            if (state.traktUsername.isNotBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                FilledTonalButton(
                                    shapes = expressiveButtonShapes(),
                                    onClick = { viewModel.syncTrakt() },
                                    enabled = state.isSyncing == null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(42.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                                ) {
                                    if (state.isSyncing == "trakt") {
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
                                                text = stringResource(R.string.settings_trakt_sync),
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
                }

                // 5c. MDBList API
                if (pageId == "mdblist") {
                    item(key = "config_mdblist") {
                        SettingsCard {
                            // MDBList Card Header
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
                                        icon = Icons.Default.VideoLibrary,
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Column(modifier = Modifier.weight(1f, fill = false)) {
                                        Text(
                                            text = stringResource(R.string.settings_mdblist_title),
                                            style = MaterialTheme.typography.titleMediumEmphasized,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = stringResource(R.string.settings_mdblist_desc),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                if (state.mdblistApiKey.isNotBlank()) {
                                    ExpressiveStatusBadge(
                                        text = stringResource(R.string.badge_connected),
                                        tone = StatusTone.Active,
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                }
                            }

                            // Configured Summary Box
                            if (state.mdblistApiKey.isNotBlank()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Surface(
                                    shape = M3Shapes.CornerMedium,
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
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
                                                tint = statusColors(StatusTone.Active).indicator,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "API Key: ••••••••${state.mdblistApiKey.takeLast(4)}",
                                                    style = MaterialTheme.typography.titleSmallEmphasized
                                                )
                                                Text(
                                                    text = "IMDb, RT, Metacritic, Letterboxd, Trakt",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }

                                        FilledTonalButton(
                                            shapes = expressiveButtonShapes(),
                                            onClick = { showMdbListEditor = !showMdbListEditor },
                                            modifier = Modifier
                                                .heightIn(min = ButtonDefaults.MinHeight),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (showMdbListEditor) Icons.Default.ExpandLess else Icons.Default.Edit,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Text(
                                                    text = if (showMdbListEditor) stringResource(R.string.action_cancel) else stringResource(R.string.action_edit),
                                                    style = MaterialTheme.typography.labelLarge,
                                                    maxLines = 1,
                                                    softWrap = false
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Editor / Initial Setup Inputs
                            AnimatedVisibility(
                                visible = state.mdblistApiKey.isBlank() || showMdbListEditor,
                                enter = expandVertically(animationSpec = M3ExpressiveMotion.defaultSpatial()) + fadeIn(animationSpec = M3ExpressiveMotion.defaultEffects()),
                                exit = shrinkVertically(animationSpec = M3ExpressiveMotion.defaultSpatial()) + fadeOut(animationSpec = M3ExpressiveMotion.fastEffects())
                            ) {
                                Surface(
                                    shape = M3Shapes.CornerMedium,
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = mdblistApiKeyInput,
                                            onValueChange = { mdblistApiKeyInput = it },
                                            modifier = Modifier.fillMaxWidth(),
                                            label = { Text(stringResource(R.string.settings_mdblist_apikey_label)) },
                                            placeholder = { Text(stringResource(R.string.settings_mdblist_apikey_hint)) },
                                            singleLine = true,
                                            visualTransformation = if (mdblistApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.VpnKey,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            },
                                            trailingIcon = {
                                                IconButton(onClick = { mdblistApiKeyVisible = !mdblistApiKeyVisible }) {
                                                    Icon(
                                                        imageVector = if (mdblistApiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                        contentDescription = null
                                                    )
                                                }
                                            },
                                            shape = M3Shapes.CornerSmall,
                                            supportingText = {
                                                Text(stringResource(R.string.settings_mdblist_hint))
                                            }
                                        )

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.clickable { mdblistSyncRatingsInput = !mdblistSyncRatingsInput }
                                        ) {
                                            Checkbox(
                                                checked = mdblistSyncRatingsInput,
                                                onCheckedChange = { mdblistSyncRatingsInput = it }
                                            )
                                            Text(
                                                text = stringResource(R.string.settings_mdblist_sync_ratings),
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }

                                        Button(
                                            shapes = expressiveButtonShapes(),
                                            onClick = {
                                                viewModel.saveMdbListSettings(
                                                    apiKey = mdblistApiKeyInput,
                                                    syncRatings = mdblistSyncRatingsInput,
                                                    syncLists = mdblistSyncListsInput
                                                )
                                                showMdbListEditor = false
                                            },
                                            enabled = !state.isSavingMdbList && mdblistApiKeyInput.isNotBlank(),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(42.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                                        ) {
                                            if (state.isSavingMdbList) {
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

                            if (state.mdblistApiKey.isNotBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                FilledTonalButton(
                                    shapes = expressiveButtonShapes(),
                                    onClick = { viewModel.syncMdbList() },
                                    enabled = state.isSyncing == null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(42.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                                ) {
                                    if (state.isSyncing == "mdblist") {
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
                                                text = stringResource(R.string.settings_mdblist_sync),
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
                }

                // 5d. Simkl API
                if (pageId == "simkl") {
                    item(key = "config_simkl") {
                        SettingsCard {
                            // Simkl Card Header
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
                                        icon = Icons.Default.Tv,
                                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                    Column(modifier = Modifier.weight(1f, fill = false)) {
                                        Text(
                                            text = stringResource(R.string.settings_simkl_title),
                                            style = MaterialTheme.typography.titleMediumEmphasized,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = stringResource(R.string.settings_simkl_desc),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                if (state.isSimklConnected) {
                                    ExpressiveStatusBadge(
                                        text = stringResource(R.string.badge_connected),
                                        tone = StatusTone.Active,
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                }
                            }

                            // Connected info display
                            if (state.isSimklConnected && !showSimklEditor) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Surface(
                                    shape = M3Shapes.CornerMedium,
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.weight(1f, fill = false)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Column {
                                                Text(
                                                    text = if (state.simklMaskedToken.isNotBlank()) "Token: ${state.simklMaskedToken}" else "Simkl Hesabı Bağlı",
                                                    style = MaterialTheme.typography.titleSmallEmphasized
                                                )
                                                val syncTypes = listOfNotNull(
                                                    if (state.simklSyncMovies) "Film" else null,
                                                    if (state.simklSyncShows) "Dizi" else null,
                                                    if (state.simklSyncAnime) "Anime" else null,
                                                    if (state.simklSyncRatings) "Puan" else null
                                                ).joinToString(", ")
                                                Text(
                                                    text = "Eşitlenen: ${syncTypes.ifEmpty { "Yok" }}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }

                                        FilledTonalButton(
                                            shapes = expressiveButtonShapes(),
                                            onClick = { showSimklEditor = !showSimklEditor },
                                            modifier = Modifier
                                                .heightIn(min = ButtonDefaults.MinHeight),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (showSimklEditor) Icons.Default.ExpandLess else Icons.Default.Edit,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Text(
                                                    text = if (showSimklEditor) stringResource(R.string.action_cancel) else stringResource(R.string.action_edit),
                                                    style = MaterialTheme.typography.labelLarge,
                                                    maxLines = 1,
                                                    softWrap = false
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Editor / Initial Setup Inputs
                            AnimatedVisibility(
                                visible = !state.isSimklConnected || showSimklEditor,
                                enter = expandVertically(animationSpec = M3ExpressiveMotion.defaultSpatial()) + fadeIn(animationSpec = M3ExpressiveMotion.defaultEffects()),
                                exit = shrinkVertically(animationSpec = M3ExpressiveMotion.defaultSpatial()) + fadeOut(animationSpec = M3ExpressiveMotion.fastEffects())
                            ) {
                                Surface(
                                    shape = M3Shapes.CornerMedium,
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = simklAccessTokenInput,
                                            onValueChange = { simklAccessTokenInput = it },
                                            modifier = Modifier.fillMaxWidth(),
                                            label = { Text(stringResource(R.string.settings_simkl_token_label)) },
                                            placeholder = { Text(if (state.simklMaskedToken.isNotBlank()) state.simklMaskedToken else stringResource(R.string.settings_simkl_token_hint)) },
                                            singleLine = true,
                                            visualTransformation = if (simklAccessTokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.VpnKey,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            },
                                            trailingIcon = {
                                                IconButton(onClick = { simklAccessTokenVisible = !simklAccessTokenVisible }) {
                                                    Icon(
                                                        imageVector = if (simklAccessTokenVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                        contentDescription = null
                                                    )
                                                }
                                            },
                                            shape = M3Shapes.CornerSmall,
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                            )
                                        )

                                        OutlinedTextField(
                                            value = simklClientIdInput,
                                            onValueChange = { simklClientIdInput = it },
                                            modifier = Modifier.fillMaxWidth(),
                                            label = { Text(stringResource(R.string.settings_simkl_client_id_label)) },
                                            placeholder = { Text(stringResource(R.string.settings_simkl_client_id_hint)) },
                                            singleLine = true,
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.Settings,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            },
                                            shape = M3Shapes.CornerSmall,
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                            )
                                        )

                                        // Sync checkboxes
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Checkbox(
                                                    checked = simklSyncMoviesInput,
                                                    onCheckedChange = { simklSyncMoviesInput = it }
                                                )
                                                Text(
                                                    text = stringResource(R.string.settings_simkl_sync_movies),
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Checkbox(
                                                    checked = simklSyncShowsInput,
                                                    onCheckedChange = { simklSyncShowsInput = it }
                                                )
                                                Text(
                                                    text = stringResource(R.string.settings_simkl_sync_shows),
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Checkbox(
                                                    checked = simklSyncAnimeInput,
                                                    onCheckedChange = { simklSyncAnimeInput = it }
                                                )
                                                Text(
                                                    text = stringResource(R.string.settings_simkl_sync_anime),
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Checkbox(
                                                    checked = simklSyncRatingsInput,
                                                    onCheckedChange = { simklSyncRatingsInput = it }
                                                )
                                                Text(
                                                    text = stringResource(R.string.settings_simkl_sync_ratings),
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }
                                        }

                                        Text(
                                            text = stringResource(R.string.settings_simkl_hint),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        Button(
                                            shapes = expressiveButtonShapes(),
                                            onClick = {
                                                viewModel.saveSimklSettings(
                                                    clientId = simklClientIdInput,
                                                    accessToken = simklAccessTokenInput,
                                                    syncMovies = simklSyncMoviesInput,
                                                    syncShows = simklSyncShowsInput,
                                                    syncAnime = simklSyncAnimeInput,
                                                    syncRatings = simklSyncRatingsInput
                                                )
                                                showSimklEditor = false
                                            },
                                            enabled = !state.isSavingSimkl && (simklAccessTokenInput.isNotBlank() || state.isSimklConnected),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(42.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                                        ) {
                                            if (state.isSavingSimkl) {
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

                            if (state.isSimklConnected) {
                                Spacer(modifier = Modifier.height(8.dp))
                                FilledTonalButton(
                                    shapes = expressiveButtonShapes(),
                                    onClick = { viewModel.syncSimkl() },
                                    enabled = state.isSyncing == null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(42.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                                ) {
                                    if (state.isSyncing == "simkl") {
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
                                                text = stringResource(R.string.settings_simkl_sync),
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
                }

                // 6. Last.fm API
                if (pageId == "lastfm") {
                    item(key = "config_lastfm") {
                        SettingsCard {
                        // Last.fm Card Header
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
                                    icon = Icons.Default.MusicNote,
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Column(modifier = Modifier.weight(1f, fill = false)) {
                                    Text(
                                        text = stringResource(R.string.settings_lastfm_title),
                                        style = MaterialTheme.typography.titleMediumEmphasized,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = stringResource(R.string.settings_lastfm_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            if (state.lastfmUsername.isNotBlank()) {
                                ExpressiveStatusBadge(
                                    text = stringResource(R.string.badge_connected),
                                    tone = StatusTone.Active,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }

                        // Configured Summary Box (nested inside 20dp SettingsCard -> 12dp CornerMedium)
                        if (state.lastfmUsername.isNotBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                shape = M3Shapes.CornerMedium,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
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
                                            tint = statusColors(StatusTone.Active).indicator,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "@${state.lastfmUsername}",
                                                style = MaterialTheme.typography.titleSmallEmphasized
                                            )
                                            Text(
                                                text = stringResource(R.string.settings_lastfm_connected_banner),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    // Nested inside 12dp summary Surface -> 8dp CornerSmall
                                    FilledTonalButton(
                                        shapes = expressiveButtonShapes(),
                                        onClick = { showLastFmEditor = !showLastFmEditor },
                                        modifier = Modifier
                                            .heightIn(min = ButtonDefaults.MinHeight),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (showLastFmEditor) Icons.Default.ExpandLess else Icons.Default.Edit,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = if (showLastFmEditor) stringResource(R.string.action_cancel) else stringResource(R.string.action_edit),
                                                style = MaterialTheme.typography.labelLarge,
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
                            visible = state.lastfmUsername.isBlank() || showLastFmEditor,
                            enter = expandVertically(
                                animationSpec = M3ExpressiveMotion.defaultSpatial()
                            ) + fadeIn(animationSpec = M3ExpressiveMotion.defaultEffects()),
                            exit = shrinkVertically(
                                animationSpec = M3ExpressiveMotion.defaultSpatial()
                            ) + fadeOut(animationSpec = M3ExpressiveMotion.fastEffects())
                        ) {
                            Surface(
                                shape = M3Shapes.CornerMedium,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    OutlinedTextField(
                                        value = state.lastfmUsername,
                                        onValueChange = { viewModel.updateLastfmUsername(it) },
                                        modifier = Modifier.fillMaxWidth(),
                                        label = { Text(stringResource(R.string.settings_lastfm_user_label)) },
                                        placeholder = { Text(stringResource(R.string.settings_lastfm_user_hint)) },
                                        singleLine = true,
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.AlternateEmail,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        },
                                        trailingIcon = if (state.lastfmUsername.isNotBlank()) {
                                            {
                                                IconButton(onClick = { viewModel.updateLastfmUsername("") }) {
                                                    Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.action_clear))
                                                }
                                            }
                                        } else null,
                                        shape = M3Shapes.CornerSmall,
                                        supportingText = {
                                            Text(stringResource(R.string.settings_lastfm_support))
                                        }
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    val isKeyPreConfigured = state.lastfmApiKeySet && !lastfmKeyCleared && state.lastfmApiKey.isBlank()
                                    val displayKey = if (isKeyPreConfigured) "********************************" else state.lastfmApiKey

                                    OutlinedTextField(
                                        value = displayKey,
                                        onValueChange = { input ->
                                            if (isKeyPreConfigured) {
                                                lastfmKeyCleared = true
                                                val clean = input.replace("*", "").replace("•", "")
                                                viewModel.updateLastfmApiKey(clean)
                                            } else {
                                                viewModel.updateLastfmApiKey(input)
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        label = { Text(stringResource(R.string.settings_lastfm_key_label)) },
                                        placeholder = { Text(stringResource(R.string.settings_lastfm_key_hint)) },
                                        singleLine = true,
                                        visualTransformation = if (lastfmKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Key,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        },
                                        trailingIcon = {
                                            if (displayKey.isNotBlank()) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    IconButton(onClick = { lastfmKeyVisible = !lastfmKeyVisible }) {
                                                        Icon(
                                                            imageVector = if (lastfmKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                    IconButton(
                                                        onClick = {
                                                            lastfmKeyCleared = true
                                                            viewModel.updateLastfmApiKey("")
                                                        }
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Clear,
                                                            contentDescription = stringResource(R.string.action_clear),
                                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                            }
                                        },
                                        shape = M3Shapes.CornerSmall
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Button(
                                        shapes = expressiveButtonShapes(),
                                        onClick = {
                                            viewModel.saveMusicSettings()
                                            showLastFmEditor = false
                                        },
                                        enabled = !state.isSavingMusicSettings && state.lastfmUsername.isNotBlank(),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(42.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                                    ) {
                                        if (state.isSavingMusicSettings) {
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

                        if (state.lastfmUsername.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            FilledTonalButton(
                                shapes = expressiveButtonShapes(),
                                onClick = { viewModel.syncLastFm() },
                                enabled = state.isSyncing == null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(42.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                            ) {
                                if (state.isSyncing == "lastfm") {
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
                }

                // 7. ListenBrainz API
                if (pageId == "listenbrainz") {
                    item(key = "config_listenbrainz") {
                        SettingsCard {
                            // ListenBrainz Card Header
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
                                        icon = Icons.Default.Headset,
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Column(modifier = Modifier.weight(1f, fill = false)) {
                                        Text(
                                            text = stringResource(R.string.settings_listenbrainz_title),
                                            style = MaterialTheme.typography.titleMediumEmphasized,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = stringResource(R.string.settings_listenbrainz_desc),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                if (state.listenbrainzUsername.isNotBlank()) {
                                    ExpressiveStatusBadge(
                                        text = stringResource(R.string.badge_connected),
                                        tone = StatusTone.Active,
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                }
                            }

                            // Configured Summary Box
                            if (state.listenbrainzUsername.isNotBlank()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Surface(
                                    shape = M3Shapes.CornerMedium,
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
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
                                                tint = statusColors(StatusTone.Active).indicator,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "@${state.listenbrainzUsername}",
                                                    style = MaterialTheme.typography.titleSmallEmphasized
                                                )
                                                Text(
                                                    text = if (state.listenbrainzTokenSet) {
                                                        stringResource(R.string.settings_listenbrainz_token_set)
                                                    } else {
                                                        stringResource(R.string.settings_listenbrainz_hint)
                                                    },
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                        FilledTonalButton(
                                            shapes = expressiveButtonShapes(),
                                            onClick = { showListenBrainzEditor = !showListenBrainzEditor },
                                            modifier = Modifier
                                                .heightIn(min = ButtonDefaults.MinHeight),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (showListenBrainzEditor) Icons.Default.ExpandLess else Icons.Default.Edit,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Text(
                                                    text = if (showListenBrainzEditor) stringResource(R.string.action_cancel) else stringResource(R.string.action_edit),
                                                    style = MaterialTheme.typography.labelLarge,
                                                    maxLines = 1,
                                                    softWrap = false
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Editor / Initial Setup Inputs
                            AnimatedVisibility(
                                visible = state.listenbrainzUsername.isBlank() || showListenBrainzEditor,
                                enter = expandVertically(
                                    animationSpec = M3ExpressiveMotion.defaultSpatial()
                                ) + fadeIn(animationSpec = M3ExpressiveMotion.defaultEffects()),
                                exit = shrinkVertically(
                                    animationSpec = M3ExpressiveMotion.defaultSpatial()
                                ) + fadeOut(animationSpec = M3ExpressiveMotion.fastEffects())
                            ) {
                                Surface(
                                    shape = M3Shapes.CornerMedium,
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        OutlinedTextField(
                                            value = state.listenbrainzUsername,
                                            onValueChange = { viewModel.updateListenBrainzUsername(it) },
                                            modifier = Modifier.fillMaxWidth(),
                                            label = { Text(stringResource(R.string.settings_listenbrainz_username)) },
                                            placeholder = { Text("username") },
                                            singleLine = true,
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.AlternateEmail,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            },
                                            trailingIcon = if (state.listenbrainzUsername.isNotBlank()) {
                                                {
                                                    IconButton(onClick = { viewModel.updateListenBrainzUsername("") }) {
                                                        Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.action_clear))
                                                    }
                                                }
                                            } else null,
                                            shape = M3Shapes.CornerSmall,
                                            supportingText = {
                                                Text(stringResource(R.string.settings_listenbrainz_hint))
                                            }
                                        )

                                        Spacer(modifier = Modifier.height(8.dp))

                                        val isTokenPreConfigured = state.listenbrainzTokenSet && !listenBrainzTokenCleared && state.listenbrainzToken.isBlank()
                                        val displayToken = if (isTokenPreConfigured) "********************************" else state.listenbrainzToken

                                        OutlinedTextField(
                                            value = displayToken,
                                            onValueChange = { input ->
                                                if (isTokenPreConfigured) {
                                                    listenBrainzTokenCleared = true
                                                    val clean = input.replace("*", "").replace("•", "")
                                                    viewModel.updateListenBrainzToken(clean)
                                                } else {
                                                    viewModel.updateListenBrainzToken(input)
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            label = { Text(stringResource(R.string.settings_listenbrainz_token)) },
                                            placeholder = { Text("Token (optional)") },
                                            singleLine = true,
                                            visualTransformation = if (listenBrainzTokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.Key,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            },
                                            trailingIcon = {
                                                if (displayToken.isNotBlank()) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        IconButton(onClick = { listenBrainzTokenVisible = !listenBrainzTokenVisible }) {
                                                            Icon(
                                                                imageVector = if (listenBrainzTokenVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                                contentDescription = null,
                                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                        IconButton(
                                                            onClick = {
                                                                listenBrainzTokenCleared = true
                                                                viewModel.updateListenBrainzToken("")
                                                            }
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Clear,
                                                                contentDescription = stringResource(R.string.action_clear),
                                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                    }
                                                }
                                            },
                                            shape = M3Shapes.CornerSmall
                                        )

                                        Spacer(modifier = Modifier.height(8.dp))

                                        Button(
                                            shapes = expressiveButtonShapes(),
                                            onClick = {
                                                viewModel.saveListenBrainzSettings(clearToken = listenBrainzTokenCleared && state.listenbrainzToken.isBlank() && state.listenbrainzTokenSet)
                                                showListenBrainzEditor = false
                                            },
                                            enabled = !state.isSavingListenBrainz && state.listenbrainzUsername.isNotBlank(),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(42.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                                        ) {
                                            if (state.isSavingListenBrainz) {
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

                            if (state.listenbrainzUsername.isNotBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                FilledTonalButton(
                                    shapes = expressiveButtonShapes(),
                                    onClick = { viewModel.syncListenBrainz() },
                                    enabled = state.isSyncing == null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(42.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                                ) {
                                    if (state.isSyncing == "listenbrainz") {
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
                                                text = stringResource(R.string.settings_listenbrainz_sync),
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
                }

                // 8. Stats.fm API
                if (pageId == "statsfm") {
                    item(key = "config_statsfm") {
                        SettingsCard {
                            // Stats.fm Card Header
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
                                        icon = Icons.Default.Equalizer,
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Column(modifier = Modifier.weight(1f, fill = false)) {
                                        Text(
                                            text = stringResource(R.string.settings_statsfm_title),
                                            style = MaterialTheme.typography.titleMediumEmphasized,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = stringResource(R.string.settings_statsfm_desc),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                if (state.statsfmUsername.isNotBlank()) {
                                    ExpressiveStatusBadge(
                                        text = stringResource(R.string.badge_connected),
                                        tone = StatusTone.Active,
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                }
                            }

                            // Configured Summary Box
                            if (state.statsfmUsername.isNotBlank()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Surface(
                                    shape = M3Shapes.CornerMedium,
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
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
                                                tint = statusColors(StatusTone.Active).indicator,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "@${state.statsfmUsername}",
                                                    style = MaterialTheme.typography.titleSmallEmphasized
                                                )
                                                Text(
                                                    text = stringResource(R.string.settings_statsfm_public_hint),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                        FilledTonalButton(
                                            shapes = expressiveButtonShapes(),
                                            onClick = { showStatsFmEditor = !showStatsFmEditor },
                                            modifier = Modifier
                                                .heightIn(min = ButtonDefaults.MinHeight),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (showStatsFmEditor) Icons.Default.ExpandLess else Icons.Default.Edit,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Text(
                                                    text = if (showStatsFmEditor) stringResource(R.string.action_cancel) else stringResource(R.string.action_edit),
                                                    style = MaterialTheme.typography.labelLarge,
                                                    maxLines = 1,
                                                    softWrap = false
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Editor / Initial Setup Inputs
                            AnimatedVisibility(
                                visible = state.statsfmUsername.isBlank() || showStatsFmEditor,
                                enter = expandVertically(
                                    animationSpec = M3ExpressiveMotion.defaultSpatial()
                                ) + fadeIn(animationSpec = M3ExpressiveMotion.defaultEffects()),
                                exit = shrinkVertically(
                                    animationSpec = M3ExpressiveMotion.defaultSpatial()
                                ) + fadeOut(animationSpec = M3ExpressiveMotion.fastEffects())
                            ) {
                                Surface(
                                    shape = M3Shapes.CornerMedium,
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        OutlinedTextField(
                                            value = state.statsfmUsername,
                                            onValueChange = { viewModel.updateStatsFmUsername(it) },
                                            modifier = Modifier.fillMaxWidth(),
                                            label = { Text(stringResource(R.string.settings_statsfm_username)) },
                                            placeholder = { Text("username or custom ID") },
                                            singleLine = true,
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.AlternateEmail,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            },
                                            trailingIcon = if (state.statsfmUsername.isNotBlank()) {
                                                {
                                                    IconButton(onClick = { viewModel.updateStatsFmUsername("") }) {
                                                        Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.action_clear))
                                                    }
                                                }
                                            } else null,
                                            shape = M3Shapes.CornerSmall,
                                            supportingText = {
                                                Text(stringResource(R.string.settings_statsfm_public_hint))
                                            }
                                        )

                                        Spacer(modifier = Modifier.height(8.dp))

                                        Button(
                                            shapes = expressiveButtonShapes(),
                                            onClick = {
                                                viewModel.saveStatsFmSettings()
                                                showStatsFmEditor = false
                                            },
                                            enabled = !state.isSavingStatsFm && state.statsfmUsername.isNotBlank(),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(42.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                                        ) {
                                            if (state.isSavingStatsFm) {
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

                            if (state.statsfmUsername.isNotBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                FilledTonalButton(
                                    shapes = expressiveButtonShapes(),
                                    onClick = { viewModel.syncStatsFm() },
                                    enabled = state.isSyncing == null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(42.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                                ) {
                                    if (state.isSyncing == "statsfm") {
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
                                                text = stringResource(R.string.settings_statsfm_sync),
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
                }

                // 9. Web Scrobbler Brand Integration Card
                if (pageId == "web_scrobbler") {
                    item(key = "config_web_scrobbler") {
                        SettingsCard {
                        val cleanBaseUrl = state.serverUrl.trim().trimEnd('/')
                        val musicWebhookUrl = state.musicWebhookUrl.ifBlank {
                            if (cleanBaseUrl.isNotBlank()) "$cleanBaseUrl/api/webhooks/music" else "https://.../api/webhooks/music"
                        }

                        // Web Scrobbler Brand Header
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
                                    icon = Icons.Default.Extension,
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Column(modifier = Modifier.weight(1f, fill = false)) {
                                    Text(
                                        text = stringResource(R.string.settings_webscrobbler_title),
                                        style = MaterialTheme.typography.titleMediumEmphasized,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = stringResource(R.string.settings_webscrobbler_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Surface(
                                shape = M3Shapes.CornerFull,
                                color = MaterialTheme.colorScheme.tertiaryContainer,
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
                                            .background(MaterialTheme.colorScheme.onTertiaryContainer, M3Shapes.CornerFull)
                                    )
                                    Text(
                                        text = stringResource(R.string.badge_brand_integration),
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        style = MaterialTheme.typography.labelSmallEmphasized,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = stringResource(R.string.settings_webscrobbler_summary),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Web Scrobbler Status & Pause/Resume Switch Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = if (state.webScrobblerEnabled) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceContainerHighest,
                                    shape = M3Shapes.CornerMedium
                                )
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(
                                            if (state.webScrobblerEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                            CircleShape
                                        )
                                )
                                Column {
                                    Text(
                                        text = if (state.webScrobblerEnabled) "Veri Kaydı Aktif" else "Veri Kaydı Duraklatıldı",
                                        style = MaterialTheme.typography.labelMediumEmphasized,
                                        color = if (state.webScrobblerEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                    )
                                    Text(
                                        text = if (state.webScrobblerEnabled) "Gelen dinlemeler veritabanına işlenir" else "Eklenti pasifken gelen veriler kaydedilmez",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Switch(
                                checked = state.webScrobblerEnabled,
                                onCheckedChange = { viewModel.toggleWebScrobbler(it) }
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

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
                                        text = musicWebhookUrl,
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
                                shapes = expressiveButtonShapes(),
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(musicWebhookUrl))
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(context.getString(R.string.toast_webhook_copied))
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp),
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
                                shapes = expressiveButtonShapes(),
                                onClick = {
                                    val intent = Intent(
                                        Intent.ACTION_VIEW,
                                        "https://web-scrobbler.com/".toUri()
                                    )
                                    context.startActivity(intent)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.OpenInBrowser,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Web Scrobbler",
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
                                    text = "${stringResource(R.string.settings_webscrobbler_step_1)}\n${stringResource(R.string.settings_webscrobbler_step_2)}\n${stringResource(R.string.settings_webscrobbler_step_3)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                }

                // 8. Other Active Plugins (Dynamic catalog plugins like Spotify or future plugins)
                items(
                    items = otherActivePlugins.filter { it.id == pageId },
                    key = { "config_${it.id}" }
                ) { plugin ->
                    GenericPluginSettingsCard(
                        plugin = plugin,
                        serverUrl = state.serverUrl,
                        onOpenCatalog = onNavigateToIntegrationsCatalog,
                        onShowToast = { msg ->
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(msg)
                            }
                        }
                    )
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}

/** Page id of the server form; integration pages use their plugin id. */
const val SERVER_PAGE_ID = "server"

/**
 * Main settings list: one segmented group per section. Every row that has more settings
 * behind it opens its own page through [onOpenSettingsPage].
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun LazyListScope.settingsMainPage(
    state: SettingsUiState,
    enabledPlugins: List<IntegrationPlugin>,
    onNavigateToLanguage: () -> Unit,
    onOpenSettingsPage: (String) -> Unit,
    onNavigateToIntegrationsCatalog: () -> Unit,
    onThemeModeChange: (String) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onExport: (path: String, extension: String) -> Unit
) {
    // Language
    item(key = "section_language") {
        SettingsGroup(title = stringResource(R.string.settings_section_language)) {
            val currentLanguageLabel = when (state.appLanguage) {
                "tr" -> "Türkçe"
                "en" -> "English"
                else -> stringResource(R.string.settings_language_system)
            }
            ExpressiveListRow(
                index = 0,
                count = 1,
                addLeadingGap = false,
                onClick = onNavigateToLanguage,
                leadingContent = {
                    SettingsIconBox(
                        icon = Icons.Default.Language,
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                },
                trailingContent = { SettingsChevron() },
                supportingContent = { Text(currentLanguageLabel) }
            ) {
                Text(stringResource(R.string.settings_language_title))
            }
        }
    }

    // Appearance
    item(key = "section_appearance") {
        val supportsDynamicColor = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
        val rowCount = if (supportsDynamicColor) 2 else 1
        SettingsGroup(title = stringResource(R.string.settings_section_appearance)) {
            ExpressiveListRow(
                index = 0,
                count = rowCount,
                addLeadingGap = false,
                leadingContent = {
                    SettingsIconBox(
                        icon = Icons.Default.DarkMode,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                },
                supportingContent = {
                    ThemeModeButtonGroup(
                        selected = state.themeMode,
                        onSelect = onThemeModeChange,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            ) {
                Text(stringResource(R.string.settings_theme_title))
            }
            if (supportsDynamicColor) {
                SegmentedListItem(
                    checked = state.dynamicColor,
                    onCheckedChange = onDynamicColorChange,
                    shapes = ListItemDefaults.segmentedShapes(index = 1, count = rowCount),
                    // The switch already shows the state; keep the row neutral instead of the
                    // selected-row highlight that checked list items get by default.
                    colors = ListItemDefaults.segmentedColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        selectedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    leadingContent = {
                        SettingsIconBox(
                            icon = Icons.Default.Palette,
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    },
                    trailingContent = {
                        // The row is the toggle; the switch only shows its state.
                        Switch(checked = state.dynamicColor, onCheckedChange = null)
                    },
                    supportingContent = { Text(stringResource(R.string.settings_dynamic_color_desc)) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.settings_dynamic_color_title))
                }
            }
        }
    }

    // Server
    item(key = "section_server") {
        SettingsGroup(title = stringResource(R.string.settings_section_server)) {
            ExpressiveListRow(
                index = 0,
                count = 1,
                addLeadingGap = false,
                onClick = { onOpenSettingsPage(SERVER_PAGE_ID) },
                leadingContent = {
                    SettingsIconBox(
                        icon = Icons.Default.Dns,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                },
                trailingContent = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        state.connectionSuccess?.let { ok ->
                            ConnectionStatusChip(connected = ok)
                        }
                        SettingsChevron()
                    }
                },
                supportingContent = {
                    Text(
                        text = state.serverUrl.trim().ifBlank { stringResource(R.string.settings_server_not_configured) },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            ) {
                Text(stringResource(R.string.settings_server_title))
            }
        }
    }

    // Integrations: one row per enabled plugin, then a row into the catalog.
    item(key = "section_integrations") {
        val count = enabledPlugins.size + 1
        SettingsGroup(title = stringResource(R.string.settings_section_integrations)) {
            enabledPlugins.forEachIndexed { index, plugin ->
                val colors = recordCategoryColors(plugin.recordCategory())
                val description = if (plugin.shortDescRes != 0) stringResource(plugin.shortDescRes) else plugin.shortDescription
                ExpressiveListRow(
                    index = index,
                    count = count,
                    addLeadingGap = false,
                    onClick = { onOpenSettingsPage(plugin.id) },
                    leadingContent = {
                        SettingsIconBox(
                            icon = plugin.icon,
                            containerColor = colors.container,
                            contentColor = colors.onContainer
                        )
                    },
                    trailingContent = { SettingsChevron() },
                    supportingContent = if (description.isNotBlank()) {
                        { Text(description, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    } else null
                ) {
                    Text(plugin.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            ExpressiveListRow(
                index = enabledPlugins.size,
                count = count,
                addLeadingGap = false,
                onClick = onNavigateToIntegrationsCatalog,
                leadingContent = {
                    SettingsIconBox(
                        icon = Icons.Default.Add,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingContent = { SettingsChevron() },
                supportingContent = {
                    Text(
                        stringResource(
                            if (enabledPlugins.isEmpty()) R.string.settings_integrations_empty_desc
                            else R.string.settings_integrations_add_desc
                        )
                    )
                }
            ) {
                Text(stringResource(R.string.settings_integrations_add))
            }
        }
    }

    // Data export
    item(key = "section_export") {
        SettingsGroup(title = stringResource(R.string.settings_section_export)) {
            ExpressiveListRow(
                index = 0,
                count = 2,
                addLeadingGap = false,
                onClick = { onExport("/api/export/all", "json") },
                leadingContent = {
                    SettingsIconBox(
                        icon = Icons.Default.Download,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                },
                supportingContent = { Text(stringResource(R.string.settings_export_desc)) }
            ) {
                Text(stringResource(R.string.settings_btn_export_json))
            }
            ExpressiveListRow(
                index = 1,
                count = 2,
                addLeadingGap = false,
                onClick = { onExport("/api/export/db", "sqlite3") },
                leadingContent = {
                    SettingsIconBox(
                        icon = Icons.Default.Storage,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                },
                supportingContent = { Text(stringResource(R.string.settings_export_note)) }
            ) {
                Text(stringResource(R.string.settings_btn_export_sqlite))
            }
        }
    }

    // App info footer
    item(key = "app_info") {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = "${stringResource(R.string.app_name)} • ${stringResource(R.string.app_version_info)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.app_description),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Section of the main settings list: a subheader over a segmented group of rows. */
@Composable
private fun SettingsGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        SettingsSectionTitle(title = title)
        content()
    }
}

@Composable
private fun SettingsChevron() {
    Icon(
        imageVector = Icons.Default.ChevronRight,
        contentDescription = null
    )
}

@Composable
private fun ConnectionStatusChip(connected: Boolean) {
    val statusBg = if (connected) statusColors(StatusTone.Active).container else MaterialTheme.colorScheme.errorContainer
    val statusFg = if (connected) statusColors(StatusTone.Active).onContainer else MaterialTheme.colorScheme.onErrorContainer
    Surface(shape = M3Shapes.CornerFull, color = statusBg) {
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
                text = stringResource(if (connected) R.string.badge_connected else R.string.badge_not_connected),
                color = statusFg,
                style = MaterialTheme.typography.labelSmallEmphasized,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

/** System / Light / Dark as an M3 Expressive connected button group. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ThemeModeButtonGroup(
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val options = listOf(
        "system" to R.string.settings_theme_system,
        "light" to R.string.settings_theme_light,
        "dark" to R.string.settings_theme_dark
    )
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
    ) {
        options.forEachIndexed { index, (mode, labelRes) ->
            ToggleButton(
                checked = selected == mode,
                onCheckedChange = { onSelect(mode) },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                modifier = Modifier
                    .weight(1f)
                    .semantics { role = Role.RadioButton }
            ) {
                Text(stringResource(labelRes), maxLines = 1, softWrap = false)
            }
        }
    }
}

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmallEmphasized,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(start = 16.dp, top = 4.dp, bottom = 6.dp)
            .semantics { heading() }
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
            ExpressivePageTopAppBar(
                title = stringResource(R.string.settings_language_screen_title),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp)
                .selectableGroup(),
            contentPadding = PaddingValues(
                top = 10.dp,
                bottom = 24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            )
        ) {
            itemsIndexed(languages, key = { _, item -> item.code }) { index, item ->
                val isSelected = currentLanguage == item.code
                val label = item.labelRes?.let { stringResource(it) } ?: item.rawLabel.orEmpty()
                GroupedListItemSurface(
                    index = index,
                    itemCount = languages.size,
                    addLeadingGap = true
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = isSelected,
                                onClick = { onLanguageSelected(item.code) },
                                role = Role.RadioButton
                            )
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = label,
                            style = if (isSelected) MaterialTheme.typography.bodyLargeEmphasized else MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        // onClick = null: the whole row handles selection and semantics.
                        RadioButton(selected = isSelected, onClick = null)
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

@Composable
private fun GenericPluginSettingsCard(
    plugin: IntegrationPlugin,
    serverUrl: String,
    onOpenCatalog: () -> Unit,
    onShowToast: (String) -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val cleanBaseUrl = serverUrl.trim().trimEnd('/')
    val webhookUrl = remember(cleanBaseUrl, plugin.webhookPath) {
        if (!plugin.webhookPath.isNullOrBlank()) {
            if (cleanBaseUrl.isNotBlank()) "$cleanBaseUrl${plugin.webhookPath}" else "https://...${plugin.webhookPath}"
        } else null
    }

    SettingsCard {
        // Plugin Header
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
                    icon = plugin.icon,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        text = plugin.name,
                        style = MaterialTheme.typography.titleMediumEmphasized,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = plugin.shortDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Surface(
                shape = M3Shapes.CornerFull,
                color = MaterialTheme.colorScheme.secondaryContainer,
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
                            .background(MaterialTheme.colorScheme.onSecondaryContainer, M3Shapes.CornerFull)
                    )
                    Text(
                        text = plugin.badge.ifBlank { plugin.type.displayName },
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        style = MaterialTheme.typography.labelSmallEmphasized,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = plugin.longDescription,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Webhook Box if available
        if (webhookUrl != null) {
            Spacer(modifier = Modifier.height(10.dp))
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

            Button(
                shapes = expressiveButtonShapes(),
                onClick = {
                    clipboardManager.setText(AnnotatedString(webhookUrl))
                    onShowToast(context.getString(R.string.toast_webhook_copied))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp),
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
        }

        // Action Buttons: Official Site & Manage in Catalog
        if (plugin.officialUrl != null || plugin.setupSteps.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (plugin.officialUrl != null) {
                    OutlinedButton(
                        shapes = expressiveButtonShapes(),
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, plugin.officialUrl.toUri())
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                onShowToast(e.message ?: "URL açılamadı")
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.OpenInBrowser,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = stringResource(R.string.settings_open_website),
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                FilledTonalButton(
                    shapes = expressiveButtonShapes(),
                    onClick = onOpenCatalog,
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Extension,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = stringResource(R.string.settings_manage_in_catalog),
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Setup steps guide box
        if (plugin.setupSteps.isNotEmpty()) {
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
                        text = plugin.setupSteps.mapIndexed { idx, step -> "${idx + 1}. $step" }.joinToString("\n"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
