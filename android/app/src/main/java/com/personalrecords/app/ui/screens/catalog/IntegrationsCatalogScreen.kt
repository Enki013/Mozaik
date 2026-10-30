package com.personalrecords.app.ui.screens.catalog

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personalrecords.app.R
import com.personalrecords.app.core.integrations.IntegrationCategory
import com.personalrecords.app.core.integrations.IntegrationPlugin
import com.personalrecords.app.core.integrations.IntegrationType
import com.personalrecords.app.core.theme.*
import com.personalrecords.app.ui.components.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun IntegrationsCatalogScreen(
    viewModel: IntegrationsCatalogViewModel,
    onNavigateBack: () -> Unit,
    /** Opens the settings page of one integration, by plugin id. */
    onNavigateToSettings: ((String) -> Unit)? = null
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
    var selectedWebhookPlugin by remember { mutableStateOf<IntegrationPlugin?>(null) }

    LaunchedEffect(state.toastMessage) {
        state.toastMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearToast()
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets.statusBars,
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp)
            )
        },
        topBar = {
            ExpressivePageTopAppBar(
                title = stringResource(R.string.title_integrations_catalog),
                subtitle = stringResource(R.string.catalog_subtitle),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(
                start = FilterGutter,
                end = FilterGutter,
                top = 12.dp,
                bottom = 24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Search & Category Filters
            stickyHeader(key = "catalog_search_filters", contentType = "sticky_header") {
                val hasActiveFilters = state.searchQuery.isNotEmpty() ||
                    state.selectedCategory != IntegrationCategory.ALL
                ExpressiveFilterHeader {
                    ExpressiveSearchField(
                        value = state.searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        placeholder = stringResource(R.string.catalog_search_hint),
                        clearContentDescription = stringResource(R.string.action_clear),
                        modifier = Modifier
                            .fillMaxWidth()
                            .filterHeaderItem()
                    )
                    ExpressiveFilterRow {
                        ExpressiveConnectedButtonGroup(
                            items = IntegrationCategory.entries,
                            selectedItem = state.selectedCategory,
                            onItemSelected = { viewModel.setSelectedCategory(it) },
                            labelProvider = { stringResource(it.labelRes) }
                        )
                    }
                    if (hasActiveFilters) {
                        ExpressiveFilterRow {
                            ExpressiveClearFiltersChip(
                                onClick = {
                                    viewModel.setSearchQuery("")
                                    viewModel.setSelectedCategory(IntegrationCategory.ALL)
                                }
                            )
                        }
                        ExpressiveFilterResultCount(count = state.plugins.size)
                    }
                }
            }

            // 3. Plugin List
            if (state.plugins.isEmpty()) {
                item(key = "catalog_empty") {
                    ExpressiveEmptyState(
                        title = stringResource(R.string.catalog_empty_search),
                        icon = Icons.Rounded.SearchOff
                    )
                }
            } else {
                items(
                    items = state.plugins,
                    key = { it.id }
                ) { plugin ->
                    CatalogPluginCard(
                        plugin = plugin,
                        isSyncing = state.syncingPluginId == plugin.id,
                        onToggle = { isEnabled -> viewModel.togglePlugin(plugin.id, isEnabled) },
                        onConfigureWebhook = { selectedWebhookPlugin = plugin },
                        onSync = { viewModel.syncPlugin(plugin) },
                        onOpenSettings = onNavigateToSettings?.let { open -> { open(plugin.id) } }
                    )
                }
            }
        }
    }

    selectedWebhookPlugin?.let { plugin ->
        PluginWebhookBottomSheet(
            plugin = plugin,
            serverBaseUrl = state.serverBaseUrl,
            serverApiKey = state.serverApiKey,
            onDismiss = { selectedWebhookPlugin = null },
            onShowSnackbar = { msg ->
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(msg)
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CatalogPluginCard(
    plugin: IntegrationPlugin,
    isSyncing: Boolean,
    onToggle: (Boolean) -> Unit,
    onConfigureWebhook: () -> Unit,
    onSync: () -> Unit,
    onOpenSettings: (() -> Unit)?
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val categoryColors = recordCategoryColors(plugin.recordCategory())
    val iconContainerColor by animateColorAsState(
        targetValue = if (plugin.isEnabled) categoryColors.container else colorScheme.surfaceContainerHigh,
        animationSpec = M3ExpressiveMotion.defaultEffects(),
        label = "plugin_icon_container"
    )
    // In-development plugins can be turned off but not on.
    val canToggle = !plugin.isInDevelopment || plugin.isEnabled

    // Same container and corner as the Settings integration cards.
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = M3Shapes.CornerLargeIncreased,
        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // The whole header row is the switch target, so screen readers read
            // "<plugin name>, switch, on" instead of an unnamed switch.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(M3Shapes.CornerMedium)
                    .toggleable(
                        value = plugin.isEnabled,
                        enabled = canToggle,
                        role = Role.Switch,
                        onValueChange = onToggle
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(M3Shapes.CornerLarge)
                        .background(iconContainerColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = plugin.icon,
                        contentDescription = null,
                        tint = if (plugin.isEnabled) categoryColors.onContainer else colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = plugin.name,
                        style = MaterialTheme.typography.titleMediumEmphasized,
                        color = colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    // One badge: the integration type, or "in development". The switch already shows on/off.
                    Surface(
                        shape = M3Shapes.CornerFull,
                        color = colorScheme.surfaceContainerHigh
                    ) {
                        Text(
                            text = if (plugin.isInDevelopment) {
                                stringResource(R.string.plugin_badge_in_development)
                            } else {
                                stringResource(plugin.type.displayNameRes)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Switch(
                    checked = plugin.isEnabled,
                    onCheckedChange = null,
                    enabled = canToggle
                )
            }

            val desc = if (plugin.shortDescRes != 0) stringResource(plugin.shortDescRes) else plugin.shortDescription
            // Description with the website link at its end, so the link does not add its own row.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                plugin.officialUrl?.let { url ->
                    OfficialSiteLink(pluginName = plugin.name, url = url, context = context)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (plugin.type == IntegrationType.WEBHOOK) {
                    FilledTonalButton(
                        onClick = onConfigureWebhook,
                        enabled = !plugin.isInDevelopment,
                        shapes = expressiveButtonShapes(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize)
                        )
                        Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                        Text(
                            text = stringResource(R.string.catalog_setup_webhook),
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else if (plugin.syncServiceKey != null) {
                    FilledTonalButton(
                        onClick = onSync,
                        enabled = plugin.isEnabled && !isSyncing && !plugin.isInDevelopment,
                        shapes = expressiveButtonShapes(),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isSyncing) {
                            ExpressiveLoadingIndicator(size = ButtonDefaults.IconSize)
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Sync,
                                contentDescription = null,
                                modifier = Modifier.size(ButtonDefaults.IconSize)
                            )
                        }
                        Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                        Text(
                            text = stringResource(R.string.action_sync),
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (onOpenSettings != null) {
                        OutlinedButton(
                            onClick = onOpenSettings,
                            enabled = !plugin.isInDevelopment,
                            shapes = expressiveButtonShapes(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = null,
                                modifier = Modifier.size(ButtonDefaults.IconSize)
                            )
                            Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                            Text(
                                text = stringResource(R.string.catalog_configure),
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

        }
    }
}

/** Labeled link to the service's website, instead of an unlabeled icon button. */
@Composable
private fun OfficialSiteLink(
    pluginName: String,
    url: String,
    context: Context
) {
    val description = stringResource(R.string.catalog_open_official_site, pluginName)
    TextButton(
        onClick = {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (_: Exception) {
                // The system may not have a browser available for this URL.
            }
        },
        contentPadding = ButtonDefaults.TextButtonWithIconContentPadding,
        modifier = Modifier.semantics { contentDescription = description }
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
            contentDescription = null,
            modifier = Modifier.size(ButtonDefaults.IconSize)
        )
        Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
        Text(
            text = stringResource(R.string.catalog_official_site),
            style = MaterialTheme.typography.labelLarge
        )
    }
}

/** Maps a plugin to the record category whose color it should use. */
internal fun IntegrationPlugin.recordCategory(): RecordCategory = when (category) {
    IntegrationCategory.MUSIC -> RecordCategory.Music
    IntegrationCategory.MEDIA -> if (id == "goodreads") RecordCategory.Books else RecordCategory.Movies
    IntegrationCategory.HEALTH -> if (id == "fatsecret") RecordCategory.Nutrition else RecordCategory.Health
    else -> RecordCategory.Notifications
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PluginWebhookBottomSheet(
    plugin: IntegrationPlugin,
    serverBaseUrl: String,
    serverApiKey: String,
    onDismiss: () -> Unit,
    onShowSnackbar: (String) -> Unit
) {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )
    val context = LocalContext.current
    val clipboardManager = remember {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }
    val cleanBaseUrl = serverBaseUrl.trim().trimEnd('/')
    val effectiveWebhookUrl = remember(cleanBaseUrl, plugin.webhookPath) {
        if (cleanBaseUrl.isNotBlank() && plugin.webhookPath != null) {
            "$cleanBaseUrl${plugin.webhookPath}"
        } else {
            "https://...${plugin.webhookPath.orEmpty()}"
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = M3Shapes.CornerExtraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Row: Extension Icon + Title + Brand Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Same category color and shape as the plugin's card in the list.
                    val categoryColors = recordCategoryColors(plugin.recordCategory())
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(M3Shapes.CornerLarge)
                            .background(categoryColors.container),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = plugin.icon,
                            contentDescription = null,
                            tint = categoryColors.onContainer,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = plugin.name,
                            style = MaterialTheme.typography.titleMediumEmphasized,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Surface(
                    shape = M3Shapes.CornerFull,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Text(
                        text = stringResource(plugin.type.displayNameRes),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            val longDesc = if (plugin.longDescRes != 0) stringResource(plugin.longDescRes) else plugin.longDescription
            Text(
                text = longDesc,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Webhook Target URL box
            Surface(
                shape = M3Shapes.CornerMedium,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = stringResource(R.string.settings_target_webhook_url),
                        style = MaterialTheme.typography.labelSmallEmphasized,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SelectionContainer {
                        Text(
                            text = effectiveWebhookUrl,
                            style = MaterialTheme.typography.bodySmallEmphasized,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        val clip = ClipData.newPlainText("Webhook URL", effectiveWebhookUrl)
                        clipboardManager.setPrimaryClip(clip)
                        onShowSnackbar(context.getString(R.string.toast_webhook_copied))
                    },
                    shapes = expressiveButtonShapes(),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.action_copy_url),
                        style = MaterialTheme.typography.labelLargeEmphasized,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (plugin.officialUrl != null) {
                    OutlinedButton(
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(plugin.officialUrl))
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                onShowSnackbar(context.getString(R.string.music_open_failed))
                            }
                        },
                        shapes = expressiveButtonShapes(),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.OpenInBrowser,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = plugin.name,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Same guidance as Settings: a key-protected server needs the key in the URL or a header.
            if (serverApiKey.isNotBlank() && plugin.webhookPath != null && cleanBaseUrl.isNotBlank()) {
                WebhookAuthCard(
                    onCopy = {
                        val clip = ClipData.newPlainText("Webhook URL", "$effectiveWebhookUrl?api_key=$serverApiKey")
                        clipboardManager.setPrimaryClip(clip)
                        onShowSnackbar(context.getString(R.string.toast_webhook_auth_copied))
                    }
                )
            }

            // Setup Steps guide
            if (plugin.setupSteps.isNotEmpty()) {
                Surface(
                    shape = M3Shapes.CornerMedium,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
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
                        plugin.setupSteps.forEachIndexed { i, step ->
                            Text(
                                text = "${i + 1}. $step",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (i < plugin.setupSteps.size - 1) {
                                Spacer(modifier = Modifier.height(2.dp))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
