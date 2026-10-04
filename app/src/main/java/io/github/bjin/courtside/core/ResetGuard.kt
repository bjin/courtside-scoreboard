// Copyright (c) 2026 Bin Jin <bjin@protonmail.com>
// SPDX-License-Identifier: MIT

package io.github.bjin.courtside.core

/**
 * Two-step confirmation for resetting from a remote volume control.
 *
 * The first volume input arms the reset; a second, separate input within [windowMs] confirms it.
 * Inputs closer than [minGapMs] to the previous input belong to the same press: the COROS app
 * delivers one watch volume press as a burst of up to six calls at most ~90 ms apart, while
 * deliberate presses measured 460 ms apart or more. Such a burst can arm but never confirm, so
 * only two separate presses reset the score. The arm window restarts with every input of the
 * burst. Held hardware keys are filtered before they get here ([VolumeStepFilter], key repeat
 * counts), because their first auto-repeat comes only ~500 ms after the press.
 */
class ResetGuard(
    private val windowMs: Long = DEFAULT_WINDOW_MS,
    private val minGapMs: Long = DEFAULT_MIN_GAP_MS,
) {
    enum class Result {
        /** First press: reset is now pending confirmation. */
        ARMED,

        /** Part of the press that armed (or confirmed) the reset; nothing new happens. */
        CONTINUED,

        /** Second, separate press within the window: reset now. */
        CONFIRMED,
    }

    private var armed = false
    private var lastInputAt = NEVER

    fun onVolumeInput(now: Long): Result {
        val gap = if (lastInputAt == NEVER) Long.MAX_VALUE else now - lastInputAt
        lastInputAt = now
        return when {
            gap < minGapMs -> Result.CONTINUED
            armed && gap < windowMs -> {
                armed = false
                Result.CONFIRMED
            }
            else -> {
                armed = true
                Result.ARMED
            }
        }
    }

    /** True while a confirming press would reset: until [armedUntil], exclusive. */
    fun isArmed(now: Long): Boolean = armed && now - lastInputAt < windowMs

    /** Time at which the pending reset expires, or null when not armed. */
    fun armedUntil(): Long? = if (armed) lastInputAt + windowMs else null

    /** Cancels a pending reset (any other command was used, or the window expired). */
    fun disarm() {
        armed = false
    }

    companion object {
        const val DEFAULT_WINDOW_MS = 5_000L
        const val DEFAULT_MIN_GAP_MS = 300L
        private const val NEVER = Long.MIN_VALUE
    }
}
