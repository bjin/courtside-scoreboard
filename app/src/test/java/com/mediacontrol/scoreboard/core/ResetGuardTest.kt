package com.mediacontrol.scoreboard.core

import com.mediacontrol.scoreboard.core.ResetGuard.Result.ARMED
import com.mediacontrol.scoreboard.core.ResetGuard.Result.CONFIRMED
import com.mediacontrol.scoreboard.core.ResetGuard.Result.CONTINUED
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResetGuardTest {

    private val guard = ResetGuard(windowMs = 5_000, minGapMs = 650)

    @Test
    fun twoSeparatePressesWithinTheWindowConfirm() {
        assertEquals(ARMED, guard.onVolumeInput(10_000))
        assertTrue(guard.isArmed(11_000))
        assertEquals(15_000L, guard.armedUntil())
        assertEquals(CONFIRMED, guard.onVolumeInput(11_200))
        assertFalse(guard.isArmed(11_200))
        assertNull(guard.armedUntil())
    }

    @Test
    fun aHeldKeyThatAutoRepeatsNeverConfirms() {
        // Hardware auto-repeat: first repeat after 500 ms, then every 50 ms.
        val times = listOf(0L, 500L) + (550L..4_000L step 50)
        val results = times.map { guard.onVolumeInput(it) }
        assertEquals(ARMED, results.first())
        assertTrue(results.drop(1).all { it == CONTINUED })
        assertTrue(guard.isArmed(4_000))
    }

    @Test
    fun aSliderDragIsOnePressAndAPauseThenAnotherInputConfirms() {
        assertEquals(ARMED, guard.onVolumeInput(0))
        assertEquals(CONTINUED, guard.onVolumeInput(120))
        assertEquals(CONTINUED, guard.onVolumeInput(240))
        assertEquals(CONFIRMED, guard.onVolumeInput(1_200))
    }

    @Test
    fun theWindowRestartsWithEveryInputOfTheArmingPress() {
        guard.onVolumeInput(0)
        guard.onVolumeInput(400) // still the same press
        assertEquals(5_400L, guard.armedUntil())
        assertEquals(CONFIRMED, guard.onVolumeInput(5_399))
    }

    @Test
    fun aPressAfterTheWindowArmsAgainInsteadOfResetting() {
        guard.onVolumeInput(0)
        assertFalse(guard.isArmed(5_000))
        assertEquals(ARMED, guard.onVolumeInput(5_000))
        assertEquals(CONFIRMED, guard.onVolumeInput(6_000))
    }

    @Test
    fun theTailOfTheConfirmingPressDoesNotArmAgain() {
        guard.onVolumeInput(0)
        assertEquals(CONFIRMED, guard.onVolumeInput(1_000))
        assertEquals(CONTINUED, guard.onVolumeInput(1_500))
        assertEquals(CONTINUED, guard.onVolumeInput(1_550))
        assertFalse(guard.isArmed(1_600))
        assertEquals(ARMED, guard.onVolumeInput(3_000))
    }

    @Test
    fun disarmCancelsAPendingReset() {
        guard.onVolumeInput(0)
        guard.disarm()
        assertFalse(guard.isArmed(100))
        assertEquals(ARMED, guard.onVolumeInput(1_000))
    }
}
