// Copyright (c) 2026 Bin Jin <bjin@protonmail.com>
// SPDX-License-Identifier: MIT

package io.github.bjin.courtside.media

import android.content.Intent
import android.media.session.MediaSession
import android.os.Bundle
import android.view.KeyEvent
import androidx.core.content.IntentCompat
import io.github.bjin.courtside.core.MediaCommandMapping
import io.github.bjin.courtside.core.MediaCommandMapping.KeyDecision
import io.github.bjin.courtside.core.RemoteCommand
import io.github.bjin.courtside.core.RemoteInput
import io.github.bjin.courtside.core.ScoreboardController

/**
 * Turns everything a media controller can send into scoreboard commands. Runs on the main thread
 * (the session is created with a main-looper handler), the same thread as touch input.
 */
internal class SessionCallback(
    private val controller: ScoreboardController,
    /** Package of the controller that sent the current command (API 28+), for diagnostics. */
    private val callerPackage: () -> String?,
    /** Playback position the session currently reports, used to tell seek directions apart. */
    private val currentPositionMs: () -> Long,
    /** Re-publishes the playback state after a seek so controllers resync their position. */
    private val onPositionConsumed: () -> Unit,
) : MediaSession.Callback() {

    override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
        val event = IntentCompat.getParcelableExtra(
            mediaButtonIntent,
            Intent.EXTRA_KEY_EVENT,
            KeyEvent::class.java,
        ) ?: return super.onMediaButtonEvent(mediaButtonIntent)
        return when (val decision = MediaCommandMapping.decideKey(event.action, event.keyCode, event.repeatCount)) {
            is KeyDecision.Execute -> {
                run(decision.command, "key " + KeyEvent.keyCodeToString(event.keyCode).removePrefix("KEYCODE_"))
                true
            }
            KeyDecision.Consume -> true
            KeyDecision.Pass -> super.onMediaButtonEvent(mediaButtonIntent)
        }
    }

    override fun onSkipToPrevious() {
        run(RemoteCommand.POINT_LEFT, "skipToPrevious")
    }

    override fun onSkipToNext() {
        run(RemoteCommand.POINT_RIGHT, "skipToNext")
    }

    override fun onPlay() {
        run(RemoteCommand.UNDO, "play")
    }

    override fun onPause() {
        run(RemoteCommand.UNDO, "pause")
    }

    override fun onRewind() {
        run(RemoteCommand.POINT_LEFT, "rewind")
    }

    override fun onFastForward() {
        run(RemoteCommand.POINT_RIGHT, "fastForward")
    }

    override fun onSeekTo(pos: Long) {
        val current = currentPositionMs()
        val description = "seekTo %+.1fs".format((pos - current) / 1000.0)
        val command = MediaCommandMapping.forSeek(targetMs = pos, currentMs = current)
        if (command != null) run(command, description) else ignore(description)
        onPositionConsumed()
    }

    override fun onStop() {
        ignore("stop")
    }

    override fun onCustomAction(action: String, extras: Bundle?) {
        ignore("customAction $action")
    }

    private fun run(command: RemoteCommand, description: String) {
        controller.onRemoteCommand(command, RemoteInput(description, callerPackage()))
    }

    private fun ignore(description: String) {
        controller.onIgnoredRemote(RemoteInput(description, callerPackage()))
    }
}
