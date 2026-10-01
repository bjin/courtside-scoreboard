package com.mediacontrol.scoreboard.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VolumeStepFilterTest {

    private val filter = VolumeStepFilter()

    @Test
    fun aHeldHardwareKeyIsOnePressUntilKeyUp() {
        // Key down, first auto-repeat after 500 ms, then every 50 ms, then key-up (0).
        val steps = listOf(0L, 500L) + (550L..2_000L step 50)
        val presses = steps.map { filter.onStep(direction = +1, now = it) }
        assertEquals(listOf(true) + List(steps.size - 1) { false }, presses)
        assertFalse(filter.onStep(direction = 0, now = 2_010))
        assertTrue(filter.onStep(direction = -1, now = 2_300))
    }

    @Test
    fun controllersWithoutKeyUpStillCountSeparatePresses() {
        assertTrue(filter.onStep(+1, now = 0))
        assertTrue(filter.onStep(+1, now = 1_000))
        assertTrue(filter.onStep(-1, now = 2_000))
    }

    @Test
    fun keyUpAloneIsNotAPress() {
        assertFalse(filter.onStep(direction = 0, now = 0))
        assertTrue(filter.onStep(direction = -1, now = 10))
    }
}
