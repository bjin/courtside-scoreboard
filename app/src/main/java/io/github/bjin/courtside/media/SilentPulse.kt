// Copyright (c) 2026 Bin Jin <bjin@protonmail.com>
// SPDX-License-Identifier: MIT

package io.github.bjin.courtside.media

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.util.Log

/**
 * Plays ~0.3 s of digital silence. Android routes media buttons (headset, Bluetooth remotes,
 * `cmd media_session dispatch`) to the session of the app that most recently played audio, so a
 * scoreboard that never plays anything would not receive them. The clip is all zeros at normal
 * player volume (a muted player would not count), does not request audio focus (other audio keeps
 * playing) and stops right away, so hardware volume keys keep going to the session.
 */
internal class SilentPulse(private val handler: Handler) {
    private var track: AudioTrack? = null
    private val stopRunnable = Runnable { stop() }

    fun play() {
        stop()
        val frames = SAMPLE_RATE * DURATION_MS / 1000
        val newTrack = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(frames * BYTES_PER_FRAME)
                .build()
        } catch (e: RuntimeException) {
            Log.w(TAG, "No audio output for the media-button claim", e)
            return
        }
        // A static track reports STATE_NO_STATIC_DATA until its buffer is written.
        newTrack.write(ShortArray(frames), 0, frames)
        if (newTrack.state != AudioTrack.STATE_INITIALIZED) {
            Log.w(TAG, "Media-button claim clip could not be prepared (state ${newTrack.state})")
            newTrack.release()
            return
        }
        try {
            newTrack.play()
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Media-button claim clip did not start", e)
            newTrack.release()
            return
        }
        track = newTrack
        handler.postDelayed(stopRunnable, DURATION_MS + STOP_MARGIN_MS)
    }

    fun release() = stop()

    private fun stop() {
        handler.removeCallbacks(stopRunnable)
        track?.let {
            runCatching { it.stop() }
            it.release()
        }
        track = null
    }

    private companion object {
        const val TAG = "SilentPulse"
        const val SAMPLE_RATE = 48_000
        const val DURATION_MS = 300
        const val STOP_MARGIN_MS = 200L
        const val BYTES_PER_FRAME = 2
    }
}
