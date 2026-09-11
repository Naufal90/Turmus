package com.offlinep2p.voice.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.offlinep2p.core.audio.EffectsNegotiator
import com.offlinep2p.core.common.AudioDevice
import com.offlinep2p.core.common.AudioRoute
import com.offlinep2p.voice.BuildConfig
import com.offlinep2p.voice.ui.ConnectionViewModel

@Composable
fun SettingsScreen(
    vm: ConnectionViewModel = viewModel(factory = ConnectionViewModel.Factory)
) {
    val active by vm.voice.activeEffects.collectAsState()
    val remoteInfo by vm.control.remoteDeviceInfo.collectAsState()
    val devices by vm.router.devices.collectAsState()
    val currentRoute by vm.router.currentRoute.collectAsState()

    var aecOff by remember { mutableStateOf(false) }
    var nsOff by remember { mutableStateOf(false) }
    var agcOff by remember { mutableStateOf(false) }

    fun push() = vm.setEffectOverrides(
        EffectsNegotiator.UserOverrides(
            forceAecOff = aecOff,
            forceNsOff = nsOff,
            forceAgcOff = agcOff,
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)

        // ---------- Audio output ----------
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Audio output", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Current: ${routeLabel(currentRoute)}",
                    style = MaterialTheme.typography.bodySmall,
                )

                RouteRow(
                    device = AudioDevice(id = -1, displayName = "Automatic", route = AudioRoute.Auto),
                    selected = currentRoute == AudioRoute.Auto,
                    onClick = { vm.selectRoute(AudioRoute.Auto) },
                )
                if (devices.isEmpty()) {
                    Text(
                        "No devices reported yet. Plug a headset or pair a Bluetooth headphone.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    devices.forEach { device ->
                        RouteRow(
                            device = device,
                            selected = currentRoute == device.route,
                            onClick = { vm.selectRoute(device.route) },
                        )
                    }
                }
                Text(
                    "The list is refreshed live — plug or unplug a headset while streaming and the choice appears here immediately.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        // ---------- Capture DSP ----------
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Capture DSP", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Active now: " +
                        listOfNotNull(
                            "AEC".takeIf { active.aec },
                            "NS".takeIf { active.ns },
                            "AGC".takeIf { active.agc },
                        ).joinToString(" · ").ifEmpty { "none" },
                    style = MaterialTheme.typography.bodySmall,
                )
                DspRow("Disable AEC", aecOff) { aecOff = it; push() }
                DspRow("Disable NS", nsOff) { nsOff = it; push() }
                DspRow("Disable AGC", agcOff) { agcOff = it; push() }
                Text(
                    "These overrides are honoured immediately, both during an active call and on the next Start Voice.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        // ---------- Peer capability ----------
        if (remoteInfo != null) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Peer capability", style = MaterialTheme.typography.titleMedium)
                    Text("Device: ${remoteInfo!!.manufacturer} ${remoteInfo!!.model}")
                    Text(
                        "DSP: " +
                            listOfNotNull(
                                "AEC".takeIf { remoteInfo!!.aecAvailable },
                                "NS".takeIf { remoteInfo!!.nsAvailable },
                                "AGC".takeIf { remoteInfo!!.agcAvailable },
                            ).joinToString(" · ").ifEmpty { "none reported" },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        Text(
            "Build: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun RouteRow(device: AudioDevice, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Icon(
            imageVector = when (device.route) {
                AudioRoute.Auto -> Icons.Filled.Tune
                AudioRoute.PhoneSpeaker -> Icons.Filled.Speaker
                AudioRoute.Earpiece -> Icons.Filled.Hearing
                AudioRoute.BluetoothHeadset,
                AudioRoute.BluetoothSpeaker -> Icons.Filled.Bluetooth
                AudioRoute.WiredHeadset -> Icons.Filled.Headphones
            },
            contentDescription = null,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        Column {
            Text(device.displayName, style = MaterialTheme.typography.bodyLarge)
            Text(routeLabel(device.route), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun DspRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private fun routeLabel(route: AudioRoute): String = when (route) {
    AudioRoute.Auto -> "Automatic (OS chooses)"
    AudioRoute.PhoneSpeaker -> "Phone speaker"
    AudioRoute.Earpiece -> "Earpiece"
    AudioRoute.BluetoothHeadset -> "Bluetooth headset"
    AudioRoute.BluetoothSpeaker -> "Bluetooth speaker"
    AudioRoute.WiredHeadset -> "Wired headset"
}
