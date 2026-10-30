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

/** Settings page of the lastfm integration, opened from the main settings list. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun LastFmSettingsPage(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    snackbarHostState: SnackbarHostState
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    var showLastFmEditor by remember { mutableStateOf(false) }
    var lastfmKeyVisible by remember { mutableStateOf(false) }
    var lastfmKeyCleared by remember(showLastFmEditor) { mutableStateOf(false) }

    LaunchedEffect(state.musicSaveMessage) {
        state.musicSaveMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMusicSaveMessage()
            if (!it.startsWith("Hata") && !it.startsWith("Error")) {
                showLastFmEditor = false
            }
        }
    }

    SettingsPageColumn {
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
                    icon = Icons.Rounded.MusicNote,
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

        // Configured Summary Box (12dp CornerMedium)
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
                            imageVector = Icons.Rounded.CheckCircle,
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
                                imageVector = if (showLastFmEditor) Icons.Rounded.ExpandLess else Icons.Rounded.Edit,
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
                                imageVector = Icons.Rounded.AlternateEmail,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingIcon = if (state.lastfmUsername.isNotBlank()) {
                            {
                                IconButton(onClick = { viewModel.updateLastfmUsername("") }) {
                                    Icon(Icons.Rounded.Clear, contentDescription = stringResource(R.string.action_clear))
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
                                imageVector = Icons.Rounded.Key,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingIcon = {
                            if (displayKey.isNotBlank()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { lastfmKeyVisible = !lastfmKeyVisible }) {
                                        Icon(
                                            imageVector = if (lastfmKeyVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
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
                                            imageVector = Icons.Rounded.Clear,
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
                                Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp))
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
                            imageVector = Icons.Rounded.Sync,
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
