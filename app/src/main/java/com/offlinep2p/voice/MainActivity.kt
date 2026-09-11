package com.offlinep2p.voice

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.offlinep2p.voice.ui.AppRoot
import com.offlinep2p.voice.ui.theme.OfflineP2PTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            OfflineP2PTheme {
                AppRoot()
            }
        }
    }
}
