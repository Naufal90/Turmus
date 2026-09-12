package com.offlinep2p.voice.ui.screens

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.offlinep2p.feature.effects.VoicePreset
import com.offlinep2p.voice.ui.ConnectionViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EffectsScreen(
    vm: ConnectionViewModel = viewModel(factory = ConnectionViewModel.Factory)
) {
    val preset by vm.voice.voicePreset.collectAsState()
    val active by vm.voice.active.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Voice Effects", style = MaterialTheme.typography.headlineSmall)
        Text(
            if (active) "Streaming — changes take effect on the next 20 ms frame."
            else "Pick a preset. Effects run on the capture side, so your peer hears the processed voice.",
            style = MaterialTheme.typography.bodyMedium,
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Preset", style = MaterialTheme.typography.titleMedium)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    VoicePreset.values().forEach { p ->
                        FilterChip(
                            selected = preset == p,
                            onClick = { vm.setVoicePreset(p) },
                            label = { Text(p.displayName) },
                        )
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("What each preset does", style = MaterialTheme.typography.titleMedium)
                PresetDescription(preset)
            }
        }
    }
}

@Composable
private fun PresetDescription(preset: VoicePreset) {
    val text = when (preset) {
        VoicePreset.Normal -> "Bypass. No DSP is applied to the capture frame."
        VoicePreset.Deep -> "Pitch shifter −4 semitones + 4 dB low-shelf at 200 Hz."
        VoicePreset.Chipmunk -> "Pitch shifter +7 semitones."
        VoicePreset.Robot -> "Ring modulator with a 70 Hz sine carrier — classic Cylon effect."
        VoicePreset.Monster -> "Pitch shifter −6 semitones followed by a 25 Hz ring modulator."
        VoicePreset.Radio -> "High-pass 300 Hz + low-pass 3 kHz + soft-clip saturation."
        VoicePreset.Telephone -> "Band-pass 300–3400 Hz — the classic phone frequency response."
        VoicePreset.Echo -> "Single-tap 250 ms delay, 45 % feedback, 55 % wet."
        VoicePreset.Reverb -> "Two-comb + one-allpass Schroeder reverberator (small room)."
    }
    Text(text, style = MaterialTheme.typography.bodyMedium)
}
