package com.personalrecords.app.ui.screens.catalog

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntegrationsCatalogScreen(
    viewModel: IntegrationsCatalogViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToSettings: (() -> Unit)? = null
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var selectedWebhookPlugin by remember { mutableStateOf<IntegrationPlugin?>(null) }

    LaunchedEffect(state.toastMessage) {
        state.toastMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearToast()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.statusBars,
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = 96.dp)
            )
        },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.title_integrations_catalog),
                            style = MaterialTheme.typography.titleLargeEmphasized
                        )
                        Text(
                            text = stringResource(R.string.catalog_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                actions = {
                    if (onNavigateToSettings != null) {
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = stringResource(R.string.title_settings)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 12.dp,
                bottom = 120.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Ecosystem Banner
            item(key = "catalog_banner") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = M3Shapes.CornerLarge,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(M3Shapes.CornerMedium)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Extension,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = stringResource(R.string.catalog_banner_title),
                                    style = MaterialTheme.typography.titleMediumEmphasized,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = M3Shapes.CornerFull,
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        text = "${state.enabledPluginIds.size} ${stringResource(R.string.catalog_plugin_active)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.catalog_banner_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 2. Search & Category Filters
            item(key = "catalog_search_filters") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = state.searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        placeholder = {
                            Text(
                                text = stringResource(R.string.catalog_search_hint),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingIcon = {
                            if (state.searchQuery.isNotBlank()) {
                                IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = stringResource(R.string.action_clear)
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = M3Shapes.CornerFull,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        )
                    )

                    // Category Filter Chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IntegrationCategory.entries.forEach { cat ->
                            val isSelected = state.selectedCategory == cat
                            ExpressiveFilterChip(
                                selected = isSelected,
                                onClick = { viewModel.setSelectedCategory(cat) },
                                label = {
                                    Text(
                                        text = stringResource(cat.labelRes),
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            )
                        }
                    }
                }
            }

            // 3. Plugin List
            if (state.plugins.isEmpty()) {
                item(key = "catalog_empty") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
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

            item(key = "catalog_bottom_spacer") {
                Spacer(modifier = Modifier.height(100.dp))
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
        modifier = Modifier.fillMaxWidth(),
        shape = M3Shapes.CornerLarge,
        colors = CardDefaults.cardColors(
            containerColor = if (plugin.isEnabled) {
                MaterialTheme.colorScheme.surfaceContainerLow
            } else {
                MaterialTheme.colorScheme.surfaceContainerLowest
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = if (plugin.isEnabled) {
            null
        } else {
            CardDefaults.outlinedCardBorder()
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
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
                            .size(42.dp)
                            .clip(M3Shapes.CornerMedium)
                            .background(
                                if (plugin.isEnabled) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerHigh
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = plugin.icon,
                            contentDescription = null,
                            tint = if (plugin.isEnabled) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        FlowRow(
                            verticalArrangement = Arrangement.Center,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = plugin.name,
                                style = MaterialTheme.typography.titleMediumEmphasized,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Surface(
                                shape = M3Shapes.CornerFull,
                                color = if (plugin.isEnabled) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerHigh
                            ) {
                                Text(
                                    text = plugin.badge,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (plugin.isEnabled) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = plugin.type.displayName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Switch(
                    checked = plugin.isEnabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                        checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = plugin.shortDescription,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (plugin.type == IntegrationType.WEBHOOK) {
                    FilledTonalButton(
                        onClick = onConfigureWebhook,
                        shape = M3Shapes.CornerSmall,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.catalog_setup_webhook),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                } else if (plugin.syncServiceKey != null) {
                    Button(
                        onClick = onSync,
                        enabled = plugin.isEnabled && !isSyncing,
                        shape = M3Shapes.CornerSmall,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = null,
                            modifier = Modifier
                                .size(16.dp)
                                .rotate(syncRotation.value)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.action_sync),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }

                    if (onOpenSettings != null) {
                        OutlinedButton(
                            onClick = onOpenSettings,
                            shape = M3Shapes.CornerSmall,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.catalog_configure),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }

                if (plugin.officialUrl != null) {
                    OutlinedIconButton(
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(plugin.officialUrl))
                                context.startActivity(intent)
                            } catch (_: Exception) {}
                        },
                        shape = M3Shapes.CornerSmall,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInBrowser,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PluginWebhookBottomSheet(
    plugin: IntegrationPlugin,
    serverBaseUrl: String,
    onDismiss: () -> Unit,
    onShowSnackbar: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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
                            .clip(RoundedCornerShape(12.dp))
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
                        Text(
                            text = plugin.type.displayName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = M3Shapes.CornerFull,
                    color = MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    Text(
                        text = plugin.badge,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        style = MaterialTheme.typography.labelSmallEmphasized,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                text = plugin.longDescription,
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
                    shape = M3Shapes.CornerMedium,
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
                        shape = M3Shapes.CornerMedium,
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
