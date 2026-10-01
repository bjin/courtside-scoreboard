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

    private val guard = ResetGuard()

    private fun feed(vararg times: Long) = times.map { guard.onVolumeInput(it) }

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
    fun aCorosVolumeBurstIsOnePress() {
        // Field log, COROS app 4.10.8: one watch volume press = setVolumeTo bursts <= 90 ms apart.
        assertEquals(listOf(ARMED, CONTINUED, CONTINUED, CONTINUED), feed(0, 27, 82, 93))
        assertTrue(guard.isArmed(1_000))
        assertEquals(listOf(CONFIRMED, CONTINUED, CONTINUED, CONTINUED), feed(1_146, 1_188, 1_223, 1_266))
        assertFalse(guard.isArmed(1_300))
    }

    @Test
    fun aQuickSecondPressConfirms() {
        // Field log: a second watch press 649 ms after the first burst was ignored with the old
        // 650 ms gap and the reset did not happen.
        feed(0, 49, 88)
        assertEquals(CONFIRMED, guard.onVolumeInput(737))
    }

    @Test
    fun theWindowRestartsWithEveryInputOfTheArmingPress() {
        feed(0, 200)
        assertEquals(5_200L, guard.armedUntil())
        assertEquals(CONFIRMED, guard.onVolumeInput(5_199))
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
        assertEquals(listOf(CONFIRMED, CONTINUED, CONTINUED), feed(1_000, 1_050, 1_100))
        assertFalse(guard.isArmed(1_200))
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
