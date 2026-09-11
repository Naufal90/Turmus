package com.offlinep2p.voice.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.offlinep2p.core.common.ConnectionState
import com.offlinep2p.feature.music.MusicRole
import com.offlinep2p.feature.music.PlaylistItem
import com.offlinep2p.voice.ui.ConnectionViewModel
import com.offlinep2p.voice.ui.MusicViewModel
import com.offlinep2p.voice.ui.rememberMusicPermissionState

@Composable
fun MusicScreen(
    music: MusicViewModel = viewModel(factory = MusicViewModel.Factory),
    connection: ConnectionViewModel = viewModel(factory = ConnectionViewModel.Factory),
) {
    val items by music.items.collectAsState()
    val scanning by music.scanning.collectAsState()
    val playback by music.playback.collectAsState()
    val role by music.role.collectAsState()
    val connState by connection.coordinator.state.collectAsState()

    // Wire the music sync engine to the shared transport as soon as
    // both view-models exist (idempotent inside attachMusic()).
    LaunchedEffect(Unit) {
        val engine = connection.attachMusic(
            playerLookup = { music.playerInstance() },
            libraryLookup = { id -> music.lookup(id) },
        )
        music.attachSync(engine)
    }

    val isConnected = connState is ConnectionState.Connected

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Music", style = MaterialTheme.typography.headlineSmall)
            IconButton(onClick = { music.refresh() }, enabled = !scanning) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh library")
            }
        }

        val permOk = rememberMusicPermissionState(music)

        // -------- Shared-session card --------
        SharedSessionCard(
            role = role,
            isConnected = isConnected,
            onHost = { music.becomeHost() },
            onJoin = { music.becomeClient() },
            onLeave = { music.leaveShared() },
        )

        if (playback.currentItem != null) {
            NowPlayingCard(music, role)
        }

        if (scanning) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

        if (!permOk) {
            Text(
                "Grant access to see your local audio files.",
                style = MaterialTheme.typography.bodyMedium,
            )
        } else if (items.isEmpty() && !scanning) {
            Text(
                "No local audio found. Add music files to the device and pull refresh.",
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(items, key = { it.contentId }) { item ->
                    TrackRow(
                        item = item,
                        isCurrent = playback.currentItem?.contentId == item.contentId,
                        enabled = role != MusicRole.Client,
                        onClick = { music.play(item) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SharedSessionCard(
    role: MusicRole,
    isConnected: Boolean,
    onHost: () -> Unit,
    onJoin: () -> Unit,
    onLeave: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Shared listening", style = MaterialTheme.typography.titleMedium)
            val roleLabel = when (role) {
                MusicRole.None -> "Solo"
                MusicRole.Host -> "You are hosting"
                MusicRole.Client -> "Following peer"
            }
            Text("Mode: $roleLabel", style = MaterialTheme.typography.bodyMedium)
            if (!isConnected) {
                Text(
                    "Connect to a peer from the Connection tab first.",
                    style = MaterialTheme.typography.bodySmall,
                )
                return@Column
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when (role) {
                    MusicRole.None -> {
                        Button(onClick = onHost, modifier = Modifier.weight(1f)) {
                            Text("Host")
                        }
                        OutlinedButton(onClick = onJoin, modifier = Modifier.weight(1f)) {
                            Text("Follow peer")
                        }
                    }
                    else -> {
                        OutlinedButton(onClick = onLeave, modifier = Modifier.fillMaxWidth()) {
                            Text("Leave shared session")
                        }
                    }
                }
            }
            Text(
                when (role) {
                    MusicRole.Host -> "Your play, pause, seek is mirrored to the peer, with a 1 s heartbeat for drift correction."
                    MusicRole.Client -> "Playback is driven by the host. Local controls are disabled."
                    MusicRole.None -> "Host to share your playback, or follow the peer's."
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun TrackRow(
    item: PlaylistItem,
    isCurrent: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = if (isCurrent) "▶ ${item.title}" else item.title,
                style = MaterialTheme.typography.titleSmall,
            )
            val subtitle = listOfNotNull(item.artist, item.album).joinToString(" · ")
            if (subtitle.isNotEmpty()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
            Text(formatDuration(item.durationMs), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun NowPlayingCard(vm: MusicViewModel, role: MusicRole) {
    val playback by vm.playback.collectAsState()
    val item = playback.currentItem ?: return
    val disabled = role == MusicRole.Client

    var scrubbing by remember { mutableStateOf(false) }
    var scrubValue by remember { mutableStateOf(0f) }
    val duration = playback.durationMs.coerceAtLeast(1).toFloat()
    val positionF = if (scrubbing) scrubValue else playback.positionMs.toFloat()

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Now playing", style = MaterialTheme.typography.titleMedium)
            Text(item.title, style = MaterialTheme.typography.bodyLarge)
            val subtitle = listOfNotNull(item.artist, item.album).joinToString(" · ")
            if (subtitle.isNotEmpty()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }

            Slider(
                value = positionF.coerceIn(0f, duration),
                onValueChange = { if (!disabled) { scrubbing = true; scrubValue = it } },
                onValueChangeFinished = {
                    if (!disabled) {
                        scrubbing = false
                        vm.seekTo(scrubValue.toLong())
                    }
                },
                enabled = !disabled,
                valueRange = 0f..duration,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(formatDuration(positionF.toLong()), style = MaterialTheme.typography.bodySmall)
                Text(formatDuration(playback.durationMs), style = MaterialTheme.typography.bodySmall)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(1f))
                IconButton(
                    onClick = { if (playback.isPlaying) vm.pause() else vm.resume() },
                    enabled = !disabled,
                ) {
                    Icon(
                        imageVector = if (playback.isPlaying) Icons.Filled.Pause
                        else Icons.Filled.PlayArrow,
                        contentDescription = null,
                    )
                }
                IconButton(onClick = { vm.stop() }, enabled = !disabled) {
                    Icon(Icons.Filled.Stop, contentDescription = "Stop")
                }
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val m = total / 60
    val s = total % 60
    return "%d:%02d".format(m, s)
}
