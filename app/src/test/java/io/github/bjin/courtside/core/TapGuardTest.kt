// Copyright (c) 2026 Bin Jin <bjin@protonmail.com>
// SPDX-License-Identifier: MIT

package io.github.bjin.courtside.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Target phone in landscape: 2608×1200, corner radius 170 px, 24 dp edge guard at density 3. */
class TapGuardTest {

    private val guard = TapGuard().apply {
        setDisplay(width = 2608f, height = 1200f, corners = CornerRadii(170f, 170f, 170f, 170f), edgeGuard = 72f)
    }

    @Test
    fun aimedTapsFromTheFieldLogsScore() {
        assertTrue(guard.allows(600f, 662f))
        assertTrue(guard.allows(1356f + 649f, 545f))
    }

    @Test
    fun touchesAtTheDisplayEdgeNeverScore() {
        // Field log: a stray touch 34 px above the bottom edge, in the rounded corner, scored +1.
        assertFalse(guard.allows(2537f, 1166f))
        // Start of a swipe up from the bottom edge.
        assertFalse(guard.allows(1280f, 1196f))
        assertFalse(guard.allows(30f, 600f))
        assertTrue(guard.allows(90f, 600f))
    }

    @Test
    fun theRoundedCornersWidenTheGuard() {
        // 90 px from both edges is fine on a straight edge, but only 57 px from the corner arc.
        assertFalse(guard.allows(90f, 90f))
        assertFalse(guard.allows(2608f - 90f, 1200f - 90f))
        assertTrue(guard.allows(150f, 150f))
    }

    @Test
    fun touchesThatSlightlyMissAControlNeverScore() {
        guard.setControl("theme", left = 168f, top = 75f, right = 300f, bottom = 207f)
        // Field log: two touches just outside the light/dark button each added a point.
        assertFalse(guard.allows(157f, 155f))
        assertFalse(guard.allows(205f, 74f))
        // The zone is 1.5 times the button: 33 px around it.
        assertFalse(guard.allows(136f, 141f))
        assertTrue(guard.allows(130f, 141f))
    }

    @Test
    fun aMovedControlTakesItsZoneAlong() {
        guard.setControl("menu", left = 2308f, top = 75f, right = 2440f, bottom = 207f)
        guard.setControl("menu", left = 2200f, top = 300f, right = 2332f, bottom = 432f)
        assertTrue(guard.allows(2374f, 141f))
        assertFalse(guard.allows(2266f, 366f))
    }
}
