// Copyright (c) 2026 Bin Jin <bjin@protonmail.com>
// SPDX-License-Identifier: MIT

package io.github.bjin.courtside.core

/**
 * Tracks scoreboard screens: how many exist (so the MediaSession service stops only when the last
 * one closes) and whether one is on screen (started).
 *
 * When the scoreboard is reopened right after leaving it, the new screen starts (and claims the
 * service) before the old, finishing screen is destroyed. Stopping the service on every finishing
 * screen would then remove the session the new screen and the watch rely on.
 */
class ScreenRegistry {
    private var open = 0
    private var started = 0

    /** True while a scoreboard screen is started: in front, not behind another app or screen-off. */
    val onScreen: Boolean get() = started > 0

    fun opened() {
        open++
    }

    fun started() {
        started++
    }

    fun stopped() {
        started = (started - 1).coerceAtLeast(0)
    }

    /** Returns true when the service should stop: [finishing] and no other screen is open. */
    fun closed(finishing: Boolean): Boolean {
        open = (open - 1).coerceAtLeast(0)
        return finishing && open == 0
    }
}
