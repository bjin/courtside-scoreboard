package com.mediacontrol.scoreboard.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenRegistryTest {

    private val screens = ScreenRegistry()

    @Test
    fun leavingTheOnlyScreenStopsTheService() {
        screens.opened()
        assertTrue(screens.closed(finishing = true))
    }

    @Test
    fun reopeningRightAfterLeavingKeepsTheService() {
        // Field log: Exit, then the launcher icon within a second. The new screen was created
        // before the old one was destroyed, and the old one's destroy removed the session.
        screens.opened() // old screen
        screens.opened() // new screen, before the old one is destroyed
        assertFalse(screens.closed(finishing = true)) // old screen destroyed
        assertTrue(screens.closed(finishing = true)) // new screen left later
    }

    @Test
    fun aRecreatedScreenNeverStopsTheService() {
        screens.opened()
        screens.opened()
        assertFalse(screens.closed(finishing = false))
        assertFalse(screens.closed(finishing = false))
    }
}
