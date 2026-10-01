package io.github.bjin.courtside.media

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.VolumeProvider
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import io.github.bjin.courtside.core.VolumeStepFilter

/**
 * Session-only volume: with `setPlaybackToRemote`, volume commands addressed to our session
 * arrive here instead of changing the phone's media volume. The COROS app sends its watch volume
 * presses as `MediaController.setVolumeTo` (absolute levels); they always count.
 *
 * Relative steps are the phone's own volume keys, routed to this session because it is the
 * playing session. With the scoreboard on screen they are reset presses (two needed). Otherwise
 * the user is somewhere else, so they are passed on to the phone's media volume and never touch
 * the score: the keys keep working normally in other apps and can't reset the score by accident.
 */
internal class SessionVolumeProvider(
    private val onScreen: () -> Boolean,
    private val adjustPhoneVolume: (direction: Int) -> Unit,
    private val onInput: (description: String) -> Unit,
) : VolumeProvider(VOLUME_CONTROL_ABSOLUTE, MAX_LEVEL, MID_LEVEL) {

    private val steps = VolumeStepFilter()

    override fun onAdjustVolume(direction: Int) {
        if (!onScreen()) {
            if (direction != 0) adjustPhoneVolume(direction)
            return
        }
        // Key auto-repeat and the key-up echo (direction 0) of a held hardware key are one press.
        if (!steps.onStep(direction, SystemClock.uptimeMillis())) return
        onInput("volume %+d".format(direction))
        currentVolume = MID_LEVEL
    }

    override fun onSetVolumeTo(volume: Int) {
        if (volume == currentVolume) return
        onInput("volume set $volume/$MAX_LEVEL")
        currentVolume = MID_LEVEL
    }

    companion object {
        const val MAX_LEVEL = 10
        const val MID_LEVEL = 5
    }
}

/**
 * Watches the phone's real media volume (STREAM_MUSIC) through the platform's volume broadcasts.
 *
 * A watch app that changes the stream directly bypasses the session's [SessionVolumeProvider]. When
 * [restoring] is on and the scoreboard is on screen, such a change is reported as a volume input
 * and the previous level (and mute state) is put back right away, so the phone's volume is never
 * left modified. Otherwise the change is the user's: it becomes the new level to restore to and is
 * only reported for diagnostics.
 *
 * The broadcasts are not public API ([AudioManager] sends them as protected system broadcasts);
 * they have been stable for a decade but are treated as best effort.
 */
internal class PhoneVolumeWatcher(
    private val context: Context,
    private val onScreen: () -> Boolean,
    private val onChange: (description: String, restored: Boolean) -> Unit,
) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private var registered = false
    private var baselineVolume = 0
    private var baselineMuted = false

    var restoring: Boolean = false
        set(value) {
            if (value && !field) captureBaseline()
            field = value
        }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.getIntExtra(EXTRA_STREAM_TYPE, -1) != AudioManager.STREAM_MUSIC) return
            when (intent.action) {
                ACTION_VOLUME_CHANGED -> onVolumeChanged(
                    value = intent.getIntExtra(EXTRA_VALUE, -1),
                    previous = intent.getIntExtra(EXTRA_PREVIOUS_VALUE, -1),
                )
                ACTION_MUTE_CHANGED -> onMuteChanged(intent.getBooleanExtra(EXTRA_MUTED, false))
            }
        }
    }

    fun start() {
        if (registered) return
        val filter = IntentFilter().apply {
            addAction(ACTION_VOLUME_CHANGED)
            addAction(ACTION_MUTE_CHANGED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        registered = true
        captureBaseline()
    }

    fun stop() {
        if (!registered) return
        context.unregisterReceiver(receiver)
        registered = false
    }

    private fun captureBaseline() {
        baselineVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        baselineMuted = audio.isStreamMute(AudioManager.STREAM_MUSIC)
    }

    private fun onVolumeChanged(value: Int, previous: Int) {
        if (value < 0 || value == previous) return
        val counts = restoring && onScreen()
        if (counts && value == baselineVolume) return // our own restore (or already at baseline)
        onChange("phone volume $previous→$value", counts)
        if (counts) {
            runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, baselineVolume, 0) }
                .onFailure { Log.w(TAG, "Could not restore media volume", it) }
        } else {
            baselineVolume = value
        }
    }

    private fun onMuteChanged(muted: Boolean) {
        if (muted == baselineMuted) return // restore echo, or no actual change
        val counts = restoring && onScreen()
        onChange(if (muted) "phone mute" else "phone unmute", counts)
        if (counts) {
            val direction = if (baselineMuted) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE
            runCatching { audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0) }
                .onFailure { Log.w(TAG, "Could not restore media mute state", it) }
        } else {
            baselineMuted = muted
        }
    }

    private companion object {
        const val TAG = "PhoneVolumeWatcher"

        // AudioManager.VOLUME_CHANGED_ACTION / STREAM_MUTE_CHANGED_ACTION and their extras (@hide).
        const val ACTION_VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION"
        const val ACTION_MUTE_CHANGED = "android.media.STREAM_MUTE_CHANGED_ACTION"
        const val EXTRA_STREAM_TYPE = "android.media.EXTRA_VOLUME_STREAM_TYPE"
        const val EXTRA_VALUE = "android.media.EXTRA_VOLUME_STREAM_VALUE"
        const val EXTRA_PREVIOUS_VALUE = "android.media.EXTRA_PREV_VOLUME_STREAM_VALUE"
        const val EXTRA_MUTED = "android.media.EXTRA_STREAM_VOLUME_MUTED"
    }
}
