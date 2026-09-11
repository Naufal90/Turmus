package com.offlinep2p.core.audio

import android.media.AudioDeviceInfo
import com.offlinep2p.core.common.AudioRoute
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioDeviceMapperTest {

    // toRoute is a pure switch on Int constants — safely unit-testable
    // without an emulator because AudioDeviceInfo.TYPE_* are compile-time
    // constants.

    @Test
    fun `builtin earpiece maps to Earpiece`() {
        assertEquals(AudioRoute.Earpiece,
            AudioDeviceMapper.toRoute(AudioDeviceInfo.TYPE_BUILTIN_EARPIECE))
    }

    @Test
    fun `builtin speaker maps to PhoneSpeaker`() {
        assertEquals(AudioRoute.PhoneSpeaker,
            AudioDeviceMapper.toRoute(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER))
    }

    @Test
    fun `bluetooth SCO maps to BluetoothHeadset`() {
        assertEquals(AudioRoute.BluetoothHeadset,
            AudioDeviceMapper.toRoute(AudioDeviceInfo.TYPE_BLUETOOTH_SCO))
    }

    @Test
    fun `bluetooth A2DP maps to BluetoothSpeaker`() {
        assertEquals(AudioRoute.BluetoothSpeaker,
            AudioDeviceMapper.toRoute(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP))
    }

    @Test
    fun `wired headset maps to WiredHeadset`() {
        assertEquals(AudioRoute.WiredHeadset,
            AudioDeviceMapper.toRoute(AudioDeviceInfo.TYPE_WIRED_HEADSET))
        assertEquals(AudioRoute.WiredHeadset,
            AudioDeviceMapper.toRoute(AudioDeviceInfo.TYPE_WIRED_HEADPHONES))
        assertEquals(AudioRoute.WiredHeadset,
            AudioDeviceMapper.toRoute(AudioDeviceInfo.TYPE_USB_HEADSET))
    }

    @Test
    fun `unknown type falls back to Auto`() {
        assertEquals(AudioRoute.Auto, AudioDeviceMapper.toRoute(9999))
        assertEquals(AudioRoute.Auto,
            AudioDeviceMapper.toRoute(AudioDeviceInfo.TYPE_FM_TUNER))
    }
}
