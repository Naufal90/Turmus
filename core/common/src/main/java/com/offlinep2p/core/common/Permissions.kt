package com.offlinep2p.core.common

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Permission plan for P2P + voice (AGENTS.md §22).
 * The set depends on Android version — Nearby Connections on API < 33
 * still needs fine location, and API 31+ needs BLUETOOTH_CONNECT/SCAN
 * with `neverForLocation` for advertising over Bluetooth.
 */
object P2PPermissions {

    /** Runtime permissions required to advertise/discover + do voice I/O. */
    fun required(): Array<String> {
        val list = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_WIFI_STATE,
        )
        // Bluetooth (API 31+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            list += Manifest.permission.BLUETOOTH_CONNECT
            list += Manifest.permission.BLUETOOTH_SCAN
            list += Manifest.permission.BLUETOOTH_ADVERTISE
        }
        // Nearby Wi-Fi devices (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list += Manifest.permission.NEARBY_WIFI_DEVICES
            list += Manifest.permission.POST_NOTIFICATIONS
            list += Manifest.permission.READ_MEDIA_AUDIO
        } else {
            // Nearby Connections needs fine location on API < 33.
            list += Manifest.permission.ACCESS_FINE_LOCATION
            list += Manifest.permission.ACCESS_COARSE_LOCATION
        }
        return list.toTypedArray()
    }

    fun missing(context: Context): List<String> =
        required().filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }

    fun allGranted(context: Context): Boolean = missing(context).isEmpty()
}
