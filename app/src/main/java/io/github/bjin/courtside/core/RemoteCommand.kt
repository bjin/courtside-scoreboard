package io.github.bjin.courtside.core

import android.view.KeyEvent

/** Scoreboard actions reachable from the watch or any other media controller. */
enum class RemoteCommand {
    POINT_LEFT,
    POINT_RIGHT,
    UNDO,
}

/**
 * Media command mapping shared by the MediaSession callback paths.
 *
 * Transport controls (what a MediaController such as the COROS app calls) and media button key
 * events (headsets, `cmd media_session dispatch`, AVRCP) map to the same commands:
 * previous = left +1, next = right +1, play/pause = undo. Rewind/fast-forward and the skip keys
 * are what "podcast style" controllers send for their back/forward buttons, so they follow the
 * same left/right meaning. Stop is ignored on purpose: it is never a deliberate scoreboard input.
 */
object MediaCommandMapping {

    fun forKeyCode(keyCode: Int): RemoteCommand? = when (keyCode) {
        KeyEvent.KEYCODE_MEDIA_PREVIOUS,
        KeyEvent.KEYCODE_MEDIA_REWIND,
        KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD,
        -> RemoteCommand.POINT_LEFT

        KeyEvent.KEYCODE_MEDIA_NEXT,
        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
        KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD,
        -> RemoteCommand.POINT_RIGHT

        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
        KeyEvent.KEYCODE_MEDIA_PLAY,
        KeyEvent.KEYCODE_MEDIA_PAUSE,
        KeyEvent.KEYCODE_HEADSETHOOK,
        -> RemoteCommand.UNDO

        else -> null
    }

    /** What to do with one media button key event. */
    sealed interface KeyDecision {
        /** Run [command] now. */
        data class Execute(val command: RemoteCommand) : KeyDecision

        /** A mapped key, but not its initial press (key up or auto-repeat): swallow it. */
        data object Consume : KeyDecision

        /** Not a scoreboard key: let the framework apply its default handling. */
        data object Pass : KeyDecision
    }

    /**
     * Acts on the initial ACTION_DOWN only, so a key press counts once and holding a key never
     * repeats a point. Play/pause is executed immediately instead of going through the framework's
     * default handling, which delays it by the double-tap timeout and turns a double press into
     * "next" (that would award the right side a point instead of undoing twice).
     */
    fun decideKey(action: Int, keyCode: Int, repeatCount: Int): KeyDecision {
        val command = forKeyCode(keyCode) ?: return KeyDecision.Pass
        return if (action == KeyEvent.ACTION_DOWN && repeatCount == 0) {
            KeyDecision.Execute(command)
        } else {
            KeyDecision.Consume
        }
    }

    /** Seeks shorter than this are not deliberate skips (e.g. a controller re-syncing position). */
    const val MIN_SEEK_DELTA_MS = 1_000L

    /**
     * Podcast-style "jump back/forward N seconds" sent as `seekTo(position ± N)`. The session reports
     * a position far from zero, so both directions stay distinguishable: forward = right +1,
     * backward = left +1, anything shorter than [MIN_SEEK_DELTA_MS] is ignored.
     */
    fun forSeek(targetMs: Long, currentMs: Long): RemoteCommand? {
        val delta = targetMs - currentMs
        return when {
            delta >= MIN_SEEK_DELTA_MS -> RemoteCommand.POINT_RIGHT
            delta <= -MIN_SEEK_DELTA_MS -> RemoteCommand.POINT_LEFT
            else -> null
        }
    }
}
