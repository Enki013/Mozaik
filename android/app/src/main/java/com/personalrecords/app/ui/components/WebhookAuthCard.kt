package com.personalrecords.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.personalrecords.app.R
import com.personalrecords.app.core.theme.M3Shapes
import com.personalrecords.app.core.theme.titleSmallEmphasized

/**
 * Shown under a webhook URL when the server requires an API key. Calm secondary tonal
 * container (not a warning color): it is guidance, not an error.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WebhookAuthCard(
    onCopy: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = M3Shapes.CornerMedium,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = stringResource(R.string.settings_webhook_auth_title),
                    style = MaterialTheme.typography.titleSmallEmphasized
                )
            }
            Text(
                text = stringResource(R.string.settings_webhook_auth_header_tip),
                style = MaterialTheme.typography.bodySmall
            )
            Button(
                onClick = onCopy,
                shapes = expressiveButtonShapes(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Key,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize)
                )
                Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                Text(
                    text = stringResource(R.string.action_copy_auth_url),
                    style = MaterialTheme.typography.labelLarge
                )
            }
            Text(
                text = stringResource(R.string.settings_webhook_auth_alt),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
