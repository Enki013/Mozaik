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

/** Settings page of the listenbrainz integration, opened from the main settings list. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ListenBrainzSettingsPage(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    snackbarHostState: SnackbarHostState
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    var showListenBrainzEditor by remember { mutableStateOf(false) }
    var listenBrainzTokenVisible by remember { mutableStateOf(false) }
    var listenBrainzTokenCleared by remember(showListenBrainzEditor) { mutableStateOf(false) }

    LaunchedEffect(state.listenbrainzSaveMessage) {
        state.listenbrainzSaveMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearListenbrainzSaveMessage()
            if (!it.startsWith("Hata") && !it.startsWith("Error")) {
                showListenBrainzEditor = false
            }
        }
    }

    SettingsPageColumn {
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
                    icon = Icons.Rounded.Headset,
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
                            imageVector = Icons.Rounded.CheckCircle,
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
                                imageVector = if (showListenBrainzEditor) Icons.Rounded.ExpandLess else Icons.Rounded.Edit,
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
                                imageVector = Icons.Rounded.AlternateEmail,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingIcon = if (state.listenbrainzUsername.isNotBlank()) {
                            {
                                IconButton(onClick = { viewModel.updateListenBrainzUsername("") }) {
                                    Icon(Icons.Rounded.Clear, contentDescription = stringResource(R.string.action_clear))
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
                                imageVector = Icons.Rounded.Key,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingIcon = {
                            if (displayToken.isNotBlank()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { listenBrainzTokenVisible = !listenBrainzTokenVisible }) {
                                        Icon(
                                            imageVector = if (listenBrainzTokenVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
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
                            imageVector = Icons.Rounded.Sync,
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
