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

/** Settings page of the health_connect integration, opened from the main settings list. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun HealthConnectSettingsPage(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    snackbarHostState: SnackbarHostState
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    SettingsPageColumn {
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
                    icon = Icons.Rounded.Favorite,
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
                        imageVector = Icons.Rounded.Download,
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
                    text = "${stringResource(R.string.settings_health_step_1)}\n${stringResource(R.string.settings_health_step_2)}\n${stringResource(R.string.settings_health_step_3)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

    }
}
