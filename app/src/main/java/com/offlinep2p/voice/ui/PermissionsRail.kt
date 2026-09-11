package com.offlinep2p.voice.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.offlinep2p.core.common.P2PPermissions
import com.offlinep2p.voice.R

/**
 * Renders a small banner + Grant button whenever any P2P permission
 * from AGENTS.md §22 is still missing. When everything is granted the
 * composable returns true so callers can enable transport-driven UI.
 */
@Composable
fun rememberP2PPermissionState(): Boolean {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(P2PPermissions.allGranted(ctx)) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        granted = results.values.all { it } && P2PPermissions.allGranted(ctx)
    }

    if (!granted) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Permissions required",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "Microphone, nearby devices, and (on older Android) location are needed to discover peers offline.",
                    style = MaterialTheme.typography.bodySmall
                )
                Button(
                    onClick = { launcher.launch(P2PPermissions.required()) },
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text(stringResource(R.string.action_grant_permissions))
                }
            }
        }
    }
    return granted
}
