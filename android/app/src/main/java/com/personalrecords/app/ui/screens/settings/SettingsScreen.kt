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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.*
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
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
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
                    .padding(horizontal = M3Spacing.Large),
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
                    SettingsPageColumn {
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
                                    icon = Icons.Rounded.Dns,
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
                                    imageVector = Icons.Rounded.Dns,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            trailingIcon = if (state.serverUrl.isNotBlank()) {
                                {
                                    IconButton(onClick = { viewModel.updateServerUrlInput("") }) {
                                        Icon(Icons.Rounded.Clear, contentDescription = stringResource(R.string.action_clear))
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
                                    imageVector = Icons.Rounded.Key,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            trailingIcon = {
                                IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                                    Icon(
                                        imageVector = if (apiKeyVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
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
                                    Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(16.dp))
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
                                        Icon(Icons.Rounded.NetworkCheck, contentDescription = null, modifier = Modifier.size(16.dp))
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
                        NotificationSettingsPage(state = state, viewModel = viewModel, snackbarHostState = snackbarHostState)
                    }
                }

                // 2. Health Connect Webhook
                if (pageId == "health_connect") {
                    item(key = "config_health_connect") {
                        HealthConnectSettingsPage(state = state, viewModel = viewModel, snackbarHostState = snackbarHostState)
                    }
                }

                // 3. FatSecret BYOK
                if (pageId == "fatsecret") {
                    item(key = "config_fatsecret") {
                        FatSecretSettingsPage(state = state, viewModel = viewModel, snackbarHostState = snackbarHostState)
                    }
                }

                // 4. Letterboxd RSS
                if (pageId == "letterboxd") {
                    item(key = "config_letterboxd") {
                        LetterboxdSettingsPage(state = state, viewModel = viewModel, snackbarHostState = snackbarHostState)
                    }
                }

                // 5. Goodreads RSS
                if (pageId == "goodreads") {
                    item(key = "config_goodreads") {
                        GoodreadsSettingsPage(state = state, viewModel = viewModel, snackbarHostState = snackbarHostState)
                    }
                }

                // 5b. Trakt.tv API
                if (pageId == "trakt") {
                    item(key = "config_trakt") {
                        TraktSettingsPage(state = state, viewModel = viewModel, snackbarHostState = snackbarHostState)
                    }
                }

                // 5c. MDBList API
                if (pageId == "mdblist") {
                    item(key = "config_mdblist") {
                        MdbListSettingsPage(state = state, viewModel = viewModel, snackbarHostState = snackbarHostState)
                    }
                }

                // 5d. Simkl API
                if (pageId == "simkl") {
                    item(key = "config_simkl") {
                        SimklSettingsPage(state = state, viewModel = viewModel, snackbarHostState = snackbarHostState)
                    }
                }

                // 6. Last.fm API
                if (pageId == "lastfm") {
                    item(key = "config_lastfm") {
                        LastFmSettingsPage(state = state, viewModel = viewModel, snackbarHostState = snackbarHostState)
                    }
                }

                // 7. ListenBrainz API
                if (pageId == "listenbrainz") {
                    item(key = "config_listenbrainz") {
                        ListenBrainzSettingsPage(state = state, viewModel = viewModel, snackbarHostState = snackbarHostState)
                    }
                }

                // 8. Stats.fm API
                if (pageId == "statsfm") {
                    item(key = "config_statsfm") {
                        StatsFmSettingsPage(state = state, viewModel = viewModel, snackbarHostState = snackbarHostState)
                    }
                }

                // 9. Web Scrobbler Brand Integration Card
                if (pageId == "web_scrobbler") {
                    item(key = "config_web_scrobbler") {
                        WebScrobblerSettingsPage(state = state, viewModel = viewModel, snackbarHostState = snackbarHostState)
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
                        icon = Icons.Rounded.Language,
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
                        icon = Icons.Rounded.DarkMode,
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
                            icon = Icons.Rounded.Palette,
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
                        icon = Icons.Rounded.Dns,
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
                        icon = Icons.Rounded.Add,
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
                        icon = Icons.Rounded.Download,
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
                        icon = Icons.Rounded.Storage,
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
        imageVector = Icons.Rounded.ChevronRight,
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
    ExpressiveListSubheader(title = title)
}

/** Body of a settings page: the integration's content directly on the page, without a card. */
@Composable
internal fun SettingsPageColumn(
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        content = content
    )
}

@Composable
internal fun SettingsIconBox(
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color
) {
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
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
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
                SegmentedListItem(
                    selected = isSelected,
                    onClick = { onLanguageSelected(item.code) },
                    shapes = ListItemDefaults.segmentedShapes(index = index, count = languages.size),
                    colors = ListItemDefaults.segmentedColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    // onClick = null: the row handles selection and semantics.
                    trailingContent = { RadioButton(selected = isSelected, onClick = null) },
                    modifier = Modifier
                        .padding(top = if (index > 0) ListItemDefaults.SegmentedGap else 0.dp)
                        .fillMaxWidth()
                ) {
                    Text(text = label)
                }
            }
        }
    }
}

internal fun extractLetterboxdUsername(url: String): String {
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

internal fun extractGoodreadsId(url: String): String {
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

    SettingsPageColumn {
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
                        imageVector = Icons.Rounded.ContentCopy,
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
                                imageVector = Icons.Rounded.OpenInBrowser,
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
                            imageVector = Icons.Rounded.Extension,
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
                            imageVector = Icons.AutoMirrored.Rounded.HelpOutline,
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
