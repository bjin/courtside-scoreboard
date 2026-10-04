// Copyright (c) 2026 Bin Jin <bjin@protonmail.com>
// SPDX-License-Identifier: MIT

package io.github.bjin.courtside.core

/**
 * Turns relative volume steps (VolumeProvider.onAdjustVolume) into presses.
 *
 * A hardware volume key held down auto-repeats: the first repeat comes after the key-repeat
 * timeout (~500 ms), then one every ~50 ms, and key-up sends a 0 step. Those repeats belong to the
 * press that started the hold, so holding a key can never count as a second, confirming press.
 */
class VolumeStepFilter(private val holdRepeatMs: Long = DEFAULT_HOLD_REPEAT_MS) {
    private var released = true
    private var lastStepAt = 0L

    /** Returns true when [direction] starts a new press; 0 (key-up) ends the current one. */
    fun onStep(direction: Int, now: Long): Boolean {
        if (direction == 0) {
            released = true
            return false
        }
        val repeat = !released && now - lastStepAt < holdRepeatMs
        released = false
        lastStepAt = now
        return !repeat
    }

    companion object {
        /** Longer than the platform key-repeat timeout (500 ms), so the first repeat is caught. */
        const val DEFAULT_HOLD_REPEAT_MS = 600L
    }
}
