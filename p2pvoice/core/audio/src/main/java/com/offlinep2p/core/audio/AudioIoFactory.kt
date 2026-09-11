package com.offlinep2p.core.audio

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder

/**
 * Creates `AudioRecord` and `AudioTrack` sized to the current
 * [VoiceConfig] (AGENTS.md §8-§10).
 *
 * Since Phase 5, this factory NO LONGER attaches AEC/NS/AGC — that is
 * now the job of [CaptureEffectsController], because the controller
 * owns the effect lifecycle, exposes per-effect toggles, and negotiates
 * with the peer. Attaching them here as well would double-instantiate
 * each effect and leak references on release.
 *
 * Callers keep the [AudioRecord.getAudioSessionId] and hand it to
 * `CaptureEffectsController` after `startRecording()`.
 */
object AudioIoFactory {

    @SuppressLint("MissingPermission") // caller is expected to hold RECORD_AUDIO
    fun createRecord(config: VoiceConfig = VoiceConfig()): AudioRecord {
        val channelIn = if (config.channelCount == 1)
            AudioFormat.CHANNEL_IN_MONO else AudioFormat.CHANNEL_IN_STEREO
        val minBuf = AudioRecord.getMinBufferSize(
            config.sampleRateHz, channelIn, AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(config.bytesPerFrame * 4)

        return AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            config.sampleRateHz,
            channelIn,
            AudioFormat.ENCODING_PCM_16BIT,
            minBuf,
        )
    }

    fun createTrack(config: VoiceConfig = VoiceConfig()): AudioTrack {
        val channelOut = if (config.channelCount == 1)
            AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO
        val minBuf = AudioTrack.getMinBufferSize(
            config.sampleRateHz, channelOut, AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(config.bytesPerFrame * 4)

        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val fmt = AudioFormat.Builder()
            .setSampleRate(config.sampleRateHz)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setChannelMask(channelOut)
            .build()
        return AudioTrack(
            attrs, fmt, minBuf,
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE,
        )
    }
}
