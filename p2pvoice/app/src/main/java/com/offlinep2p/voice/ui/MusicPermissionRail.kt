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
import androidx.compose.ui.unit.dp

/**
 * Small banner that requests READ_MEDIA_AUDIO / READ_EXTERNAL_STORAGE
 * before the music library can be scanned. Returns true when granted.
 */
@Composable
fun rememberMusicPermissionState(vm: MusicViewModel): Boolean {
    var granted by remember { mutableStateOf(vm.hasReadAudioPermission()) }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { ok ->
        granted = ok
        if (ok) vm.refresh()
    }
    if (!granted) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Music library access", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Allow the app to read local audio files so you can share playback with your peer.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(
                    onClick = { launcher.launch(vm.readAudioPermission()) },
                    modifier = Modifier.padding(top = 8.dp),
                ) { Text("Grant access") }
            }
        }
    }
    return granted
}
