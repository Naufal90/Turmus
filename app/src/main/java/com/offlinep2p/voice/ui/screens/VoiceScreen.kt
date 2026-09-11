package com.offlinep2p.voice.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.offlinep2p.core.common.AudioRoute
import com.offlinep2p.feature.control.SessionPhase
import com.offlinep2p.voice.ui.ConnectionViewModel

@Composable
fun VoiceScreen(
    vm: ConnectionViewModel = viewModel(factory = ConnectionViewModel.Factory)
) {
    val phase by vm.control.phase.collectAsState()
    val active by vm.voice.active.collectAsState()
    val codec by vm.voice.codecName.collectAsState()
    val err by vm.voice.lastError.collectAsState()
    val rtt by vm.control.rttMs.collectAsState()
    val fx by vm.voice.activeEffects.collectAsState()
    val route by vm.router.currentRoute.collectAsState()
    val preset by vm.voice.voicePreset.collectAsState()

    val ready = phase == SessionPhase.Ready
    val fxLabel = listOfNotNull(
        "AEC".takeIf { fx.aec },
        "NS".takeIf { fx.ns },
        "AGC".takeIf { fx.agc },
    ).joinToString(" · ").ifEmpty { "none" }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Voice", style = MaterialTheme.typography.headlineSmall)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    if (active) "● Streaming"
                    else if (ready) "Ready to stream"
                    else "Waiting for handshake…",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    "Codec: ${codec ?: "—"}   RTT: ${rtt?.let { "$it ms" } ?: "—"}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text("Capture DSP: $fxLabel", style = MaterialTheme.typography.bodyMedium)
                Text("Output: ${routeLabel(route)}", style = MaterialTheme.typography.bodyMedium)
                Text("Preset: ${preset.displayName}", style = MaterialTheme.typography.bodyMedium)
                if (err != null) {
                    Text(err!!, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        if (!active) {
            Button(
                enabled = ready,
                onClick = { vm.startVoice() },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Start voice") }
        } else {
            OutlinedButton(
                onClick = { vm.stopVoice() },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Stop voice") }
        }

        Text(
            "Full-duplex 16 kHz mono. 20 ms frames. Opus when supported (API 29+), PCM fallback otherwise. Change the output in Settings, the voice preset in Effects.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private fun routeLabel(route: AudioRoute): String = when (route) {
    AudioRoute.Auto -> "Automatic"
    AudioRoute.PhoneSpeaker -> "Phone speaker"
    AudioRoute.Earpiece -> "Earpiece"
    AudioRoute.BluetoothHeadset -> "Bluetooth headset"
    AudioRoute.BluetoothSpeaker -> "Bluetooth speaker"
    AudioRoute.WiredHeadset -> "Wired headset"
}
