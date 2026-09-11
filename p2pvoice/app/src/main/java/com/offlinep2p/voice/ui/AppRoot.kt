package com.offlinep2p.voice.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.offlinep2p.voice.R
import com.offlinep2p.voice.ui.screens.ConnectionScreen
import com.offlinep2p.voice.ui.screens.EffectsScreen
import com.offlinep2p.voice.ui.screens.MusicScreen
import com.offlinep2p.voice.ui.screens.SettingsScreen
import com.offlinep2p.voice.ui.screens.VoiceScreen

private enum class Tab(val labelRes: Int) {
    Connection(R.string.tab_connection),
    Voice(R.string.tab_voice),
    Music(R.string.tab_music),
    Effects(R.string.tab_effects),
    Settings(R.string.tab_settings),
}

@Composable
fun AppRoot() {
    var current by remember { mutableStateOf(Tab.Connection) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.values().forEach { tab ->
                    NavigationBarItem(
                        selected = current == tab,
                        onClick = { current = tab },
                        icon = {
                            Icon(
                                imageVector = when (tab) {
                                    Tab.Connection -> Icons.Filled.Wifi
                                    Tab.Voice -> Icons.Filled.Mic
                                    Tab.Music -> Icons.Filled.LibraryMusic
                                    Tab.Effects -> Icons.Filled.GraphicEq
                                    Tab.Settings -> Icons.Filled.Settings
                                },
                                contentDescription = null
                            )
                        },
                        label = { Text(stringResource(tab.labelRes)) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            when (current) {
                Tab.Connection -> ConnectionScreen()
                Tab.Voice -> VoiceScreen()
                Tab.Music -> MusicScreen()
                Tab.Effects -> EffectsScreen()
                Tab.Settings -> SettingsScreen()
            }
        }
    }
}
