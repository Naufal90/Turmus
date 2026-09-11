package com.offlinep2p.voice.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.offlinep2p.core.common.ConnectionState
import com.offlinep2p.feature.connection.DiscoveredPeer
import com.offlinep2p.feature.control.SessionPhase
import com.offlinep2p.voice.R
import com.offlinep2p.voice.ui.ConnectionViewModel
import com.offlinep2p.voice.ui.rememberP2PPermissionState

@Composable
fun ConnectionScreen(
    vm: ConnectionViewModel = viewModel(factory = ConnectionViewModel.Factory)
) {
    val state by vm.coordinator.state.collectAsState()
    val peers by vm.coordinator.peers.collectAsState()
    val phase by vm.control.phase.collectAsState()
    val remote by vm.control.remote.collectAsState()
    val rttMs by vm.control.rttMs.collectAsState()
    val remoteInfo by vm.control.remoteDeviceInfo.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Offline P2P Voice",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "No Internet. No server. Just two phones.",
            style = MaterialTheme.typography.bodyMedium
        )

        val permissionsOk = rememberP2PPermissionState()

        StatusCard(state)

        if (state is ConnectionState.Connected) {
            SessionCard(
                phase = phase,
                peerName = remote?.deviceName ?: (state as ConnectionState.Connected).peerName,
                rttMs = rttMs,
                remoteModel = remoteInfo?.let { "${it.manufacturer} ${it.model}" },
                remoteEffects = remoteInfo?.let {
                    buildList {
                        if (it.aecAvailable) add("AEC")
                        if (it.nsAvailable) add("NS")
                        if (it.agcAvailable) add("AGC")
                    }.joinToString(" · ").ifEmpty { "none" }
                },
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                modifier = Modifier.weight(1f),
                enabled = permissionsOk && state !is ConnectionState.Connected,
                onClick = { vm.host() }
            ) { Text(stringResource(R.string.action_host)) }
            OutlinedButton(
                modifier = Modifier.weight(1f),
                enabled = permissionsOk && state !is ConnectionState.Connected,
                onClick = { vm.join() }
            ) { Text(stringResource(R.string.action_join)) }
        }

        if (state is ConnectionState.Discovering && peers.isNotEmpty()) {
            Text("Nearby peers", style = MaterialTheme.typography.titleSmall)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(peers, key = { it.peerId }) { peer -> PeerRow(peer) { vm.connect(peer) } }
            }
        }

        if (state !is ConnectionState.Disconnected) {
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = { vm.disconnect() }
            ) { Text(stringResource(R.string.action_disconnect)) }
        }
    }
}

@Composable
private fun PeerRow(peer: DiscoveredPeer, onConnect: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(peer.displayName, style = MaterialTheme.typography.titleSmall)
                Text(peer.peerId, style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = onConnect) { Text("Connect") }
        }
    }
}

@Composable
private fun StatusCard(state: ConnectionState) {
    val (label, description) = when (state) {
        is ConnectionState.Disconnected -> "● Disconnected" to "Tap Host or Join to start."
        is ConnectionState.Discovering -> "● Discovering" to "Looking for nearby peers…"
        is ConnectionState.Advertising -> "● Hosting" to "Waiting for a peer to join…"
        is ConnectionState.Connecting -> "● Connecting" to "Negotiating with ${state.peerName}"
        is ConnectionState.Connected -> "● Connected" to "Peer: ${state.peerName}"
        is ConnectionState.Reconnecting -> "● Reconnecting" to "Link dropped, retrying…"
        is ConnectionState.Failed -> "● Failed" to state.reason
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(description, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SessionCard(
    phase: SessionPhase,
    peerName: String,
    rttMs: Long?,
    remoteModel: String?,
    remoteEffects: String?,
) {
    val phaseLabel = when (phase) {
        SessionPhase.LinkUp -> "Link up — waiting for handshake"
        SessionPhase.HelloSent -> "HELLO sent — awaiting peer"
        SessionPhase.HelloReceived -> "HELLO received — replying"
        SessionPhase.Ready -> "Ready ✓"
        SessionPhase.Terminated -> "Terminated"
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Session", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text("Peer: $peerName", style = MaterialTheme.typography.bodyMedium)
            Text("Handshake: $phaseLabel", style = MaterialTheme.typography.bodyMedium)
            Text(
                "RTT: " + (rttMs?.let { "$it ms" } ?: "measuring…"),
                style = MaterialTheme.typography.bodyMedium
            )
            if (remoteModel != null) {
                Text("Remote: $remoteModel", style = MaterialTheme.typography.bodySmall)
            }
            if (remoteEffects != null) {
                Text("Remote DSP: $remoteEffects", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
