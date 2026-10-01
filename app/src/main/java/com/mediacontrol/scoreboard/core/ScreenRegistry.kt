package com.mediacontrol.scoreboard.core

/**
 * Counts open scoreboard screens so the MediaSession service stops only when the last one closes.
 *
 * When the scoreboard is reopened right after leaving it, the new screen starts (and claims the
 * service) before the old, finishing screen is destroyed. Stopping the service on every finishing
 * screen would then remove the session the new screen and the watch rely on.
 */
class ScreenRegistry {
    private var open = 0

    fun opened() {
        open++
    }

    /** Returns true when the service should stop: [finishing] and no other screen is open. */
    fun closed(finishing: Boolean): Boolean {
        open = (open - 1).coerceAtLeast(0)
        return finishing && open == 0
    }
}
