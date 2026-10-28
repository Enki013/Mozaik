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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.*
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
    onNavigateToSettings: (() -> Unit)? = null
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
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
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
                .padding(innerPadding)
                .navigationBarsPadding(),
            contentPadding = PaddingValues(
                top = 12.dp,
                bottom = 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Search & Category Filters
            stickyHeader(key = "catalog_search_filters", contentType = "sticky_header") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ExpressiveSearchField(
                        value = state.searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        placeholder = stringResource(R.string.catalog_search_hint),
                        clearContentDescription = stringResource(R.string.action_clear),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    )

                    // Single-choice category filter: same connected button group as every other screen.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp)
                    ) {
                        ExpressiveConnectedButtonGroup(
                            items = IntegrationCategory.entries,
                            selectedItem = state.selectedCategory,
                            onItemSelected = { viewModel.setSelectedCategory(it) },
                            labelProvider = { stringResource(it.labelRes) }
                        )
                    }
                }
            }

            // 3. Plugin List
            if (state.plugins.isEmpty()) {
                item(key = "catalog_empty") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.catalog_empty_search),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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
                        onOpenSettings = onNavigateToSettings
                    )
                }
            }
        }
    }

    selectedWebhookPlugin?.let { plugin ->
        PluginWebhookBottomSheet(
            plugin = plugin,
            serverBaseUrl = state.serverBaseUrl,
            onDismiss = { selectedWebhookPlugin = null },
            onShowSnackbar = { msg ->
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(msg)
                }
            }
        )
    }
}

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
    val accentContainer = categoryColors.container
    val onAccentContainer = categoryColors.onContainer
    val iconContainerColor by animateColorAsState(
        targetValue = if (plugin.isEnabled) accentContainer else colorScheme.surfaceContainerHigh,
        animationSpec = M3ExpressiveMotion.defaultEffects(),
        label = "plugin_icon_container"
    )
    val syncRotation = remember { Animatable(0f) }
    LaunchedEffect(isSyncing) {
        if (isSyncing) {
            syncRotation.animateTo(
                targetValue = syncRotation.value + 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                )
            )
        } else {
            syncRotation.snapTo(0f)
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = M3Shapes.CornerLargeIncreased,
        colors = CardDefaults.cardColors(
            containerColor = colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header: Icon + Info + Switch
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
                            tint = if (plugin.isEnabled) onAccentContainer else colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 4.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = plugin.name,
                            style = MaterialTheme.typography.titleMediumEmphasized,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val badgeLabel = if (plugin.badgeRes != 0) stringResource(plugin.badgeRes) else plugin.badge
                            Surface(
                                shape = M3Shapes.CornerFull,
                                color = colorScheme.surfaceContainerHigh
                            ) {
                                Text(
                                    text = badgeLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }

                            val statusLabel = if (plugin.isEnabled) {
                                stringResource(R.string.plugin_status_active)
                            } else {
                                stringResource(R.string.plugin_status_paused)
                            }
                            ExpressiveStatusBadge(
                                text = statusLabel,
                                tone = if (plugin.isEnabled) StatusTone.Active else StatusTone.Paused
                            )
                        }
                    }
                }

                Switch(
                    checked = plugin.isEnabled,
                    onCheckedChange = { enabled ->
                        if (!plugin.isInDevelopment || !enabled) onToggle(enabled)
                    },
                    enabled = !plugin.isInDevelopment || plugin.isEnabled
                )
            }

            val desc = if (plugin.shortDescRes != 0) stringResource(plugin.shortDescRes) else plugin.shortDescription
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Keep actions separate so their shapes and touch targets stay clear on narrow screens.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (plugin.type == IntegrationType.WEBHOOK) {
                    val hasOfficialUrl = plugin.officialUrl != null
                    val setupInteraction = remember { MutableInteractionSource() }

                    FilledTonalButton(
                        onClick = onConfigureWebhook,
                        enabled = !plugin.isInDevelopment,
                        interactionSource = setupInteraction,
                        shapes = expressiveButtonShapes(),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .expressivePressScale(setupInteraction)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.catalog_setup_webhook),
                            style = MaterialTheme.typography.labelLargeEmphasized,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (hasOfficialUrl) {
                        OfficialSiteButton(plugin = plugin, context = context)
                    }
                } else if (plugin.syncServiceKey != null) {
                    val hasSettings = onOpenSettings != null
                    val hasOfficialUrl = plugin.officialUrl != null
                    val syncInteraction = remember { MutableInteractionSource() }

                    FilledTonalButton(
                        onClick = onSync,
                        enabled = plugin.isEnabled && !isSyncing && !plugin.isInDevelopment,
                        interactionSource = syncInteraction,
                        shapes = expressiveButtonShapes(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .expressivePressScale(syncInteraction)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = null,
                            modifier = Modifier
                                .size(18.dp)
                                .rotate(syncRotation.value)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.action_sync),
                            style = MaterialTheme.typography.labelLargeEmphasized,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (hasSettings) {
                        val settingsInteraction = remember { MutableInteractionSource() }
                        OutlinedButton(
                            onClick = {
                                if (!plugin.isInDevelopment) onOpenSettings?.invoke()
                            },
                            enabled = !plugin.isInDevelopment,
                            interactionSource = settingsInteraction,
                            shapes = expressiveButtonShapes(),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .expressivePressScale(settingsInteraction)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.catalog_configure),
                                style = MaterialTheme.typography.labelLargeEmphasized,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (hasOfficialUrl) {
                        OfficialSiteButton(plugin = plugin, context = context)
                    }
                }
            }
        }
    }
}

@Composable
private fun OfficialSiteButton(
    plugin: IntegrationPlugin,
    context: Context
) {
    val officialUrl = plugin.officialUrl ?: return
    FilledTonalIconButton(
        onClick = {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(officialUrl)))
            } catch (_: Exception) {
                // The system may not have a browser available for this URL.
            }
        },
        modifier = Modifier.size(48.dp)
    ) {
        Icon(
            imageVector = Icons.Default.OpenInBrowser,
            contentDescription = stringResource(R.string.catalog_open_official_site, plugin.name),
            modifier = Modifier.size(20.dp)
        )
    }
}

private fun IntegrationPlugin.recordCategory(): RecordCategory = when {
    id == "fatsecret" -> RecordCategory.Nutrition
    id == "goodreads" -> RecordCategory.Books
    id == "letterboxd" -> RecordCategory.Movies
    id == "health_connect" -> RecordCategory.Health
    category == IntegrationCategory.MUSIC -> RecordCategory.Music
    else -> RecordCategory.Notifications
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PluginWebhookBottomSheet(
    plugin: IntegrationPlugin,
    serverBaseUrl: String,
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
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(M3Shapes.CornerMedium)
                            .background(MaterialTheme.colorScheme.tertiaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = plugin.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
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

                val badgeText = if (plugin.badgeRes != 0) stringResource(plugin.badgeRes) else plugin.badge
                Surface(
                    shape = M3Shapes.CornerFull,
                    color = MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    Text(
                        text = badgeText,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        style = MaterialTheme.typography.labelSmallEmphasized,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
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
                        imageVector = Icons.Default.ContentCopy,
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
                            imageVector = Icons.Default.OpenInBrowser,
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
