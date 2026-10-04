// Copyright (c) 2026 Bin Jin <bjin@protonmail.com>
// SPDX-License-Identifier: MIT

package io.github.bjin.courtside.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ScoreLayoutTest {

    /** Bundled B612 Bold digits at a 1000 px reference size (2000 units per em). */
    private val b612 = DigitMetrics(
        advance = 650f,
        inkLeft = 19f,
        inkTop = -762.5f,
        inkRight = 594.5f,
        inkBottom = 8.5f,
    )

    private data class Screen(val width: Int, val height: Int, val density: Float, val cutoutLeft: Float = 0f)

    private val screens = listOf(
        Screen(2608, 1200, 2.75f, cutoutLeft = 110f), // target phone, punch hole on the left in landscape
        Screen(1920, 1080, 2.625f), // 16:9
        Screen(2400, 1080, 2.625f, cutoutLeft = 90f), // 20:9
        Screen(2520, 1080, 2.625f), // 21:9
        Screen(2340, 1080, 2.75f), // 19.5:9
        Screen(3200, 1440, 3.5f, cutoutLeft = 130f), // QHD+ 20:9
        Screen(1280, 720, 2f), // small 16:9
    )

    private fun layout(s: Screen): ScoreboardGeometry {
        val band = 60f * s.density
        return ScoreLayout.compute(
            width = s.width.toFloat(),
            height = s.height.toFloat(),
            metrics = b612,
            safe = LayoutInsets(left = s.cutoutLeft),
            topBand = band,
            bottomBand = band,
        )
    }

    @Test
    fun twoDigitScoresFitTheirHalfWithoutTouchingTheOtherSideOrTheControlBands() {
        for (screen in screens) {
            val g = layout(screen)
            val band = 60f * screen.density
            for (digits in 1..2) {
                val left = g.inkBounds(Side.LEFT, digits)
                val right = g.inkBounds(Side.RIGHT, digits)
                // Shrinking by a hair absorbs float rounding when the ink exactly fills the box.
                assertTrue("$screen left $digits", g.leftBox.contains(left.inset(0.01f)))
                assertTrue("$screen right $digits", g.rightBox.contains(right.inset(0.01f)))
                assertFalse(left.intersects(right))
                assertTrue(left.right < g.dividerX && right.left > g.dividerX)
                assertTrue(left.top >= band && left.bottom <= screen.height - band)
                assertTrue("clear of the cutout", left.left >= screen.cutoutLeft)
            }
        }
    }

    @Test
    fun digitsFillTheLimitingDimensionOfTheSmallerHalf() {
        for (screen in screens) {
            val g = layout(screen)
            val ink = g.inkBounds(Side.LEFT, 2)
            val fillsWidth = abs(ink.width - g.leftBox.width) < 0.5f
            val fillsHeight = abs(ink.height - g.leftBox.height) < 0.5f
            assertTrue("$screen uses all available room", fillsWidth || fillsHeight)
        }
    }

    @Test
    fun sizeDoesNotJumpWhenAScoreGainsADigitAndBothSidesMatch() {
        for (screen in screens) {
            val g = layout(screen)
            val one = g.inkBounds(Side.RIGHT, 1)
            val two = g.inkBounds(Side.RIGHT, 2)
            assertEquals(one.height, two.height, 0.001f)
            assertEquals(one.top, two.top, 0.001f)
            assertEquals(g.inkBounds(Side.LEFT, 2).height, two.height, 0.001f)
            // A single digit is centred in its half.
            assertEquals(g.rightBox.centerX, (one.left + one.right) / 2f, 0.5f)
        }
    }

    @Test
    fun aOneSidedCutoutShrinksBothScoresOnlySlightly() {
        val target = screens.first()
        val withCutout = layout(target).inkBounds(Side.LEFT, 2).height
        val withoutCutout = layout(target.copy(cutoutLeft = 0f)).inkBounds(Side.LEFT, 2).height
        // 2608x1200 at ~0.059 mm/px: 690 px is ~41 mm tall digits.
        assertTrue("digit height $withCutout px", withCutout >= 690f)
        assertTrue(withCutout >= 0.95f * withoutCutout)
    }

    @Test
    fun aPunchHoleBesideTheDigitsIsAvoidedButOneInACornerCostsNothing() {
        val width = 2608f
        val height = 1200f
        val band = 165f
        fun withHole(hole: Box) = ScoreLayout.compute(
            width, height, b612, LayoutInsets(left = hole.right), band, band, cutouts = listOf(hole),
        )
        val plain = ScoreLayout.compute(width, height, b612, LayoutInsets(), band, band)

        // Centred punch hole of a portrait-top camera, on the left edge in landscape.
        val centred = Box(30f, 560f, 110f, 640f)
        val g = withHole(centred)
        assertFalse(g.inkBounds(Side.LEFT, 2).intersects(centred))
        assertTrue(g.scale < plain.scale)
        assertEquals(g.inkBounds(Side.LEFT, 2).height, g.inkBounds(Side.RIGHT, 2).height, 0.001f)

        // Hole in a corner, beside the control rows only (the emulator's "hole" cutout).
        val corner = Box(0f, 1052f, 148f, 1200f)
        assertEquals(plain.scale, withHole(corner).scale, 0.0001f)
    }

    private fun Box.inset(by: Float) = Box(left + by, top + by, right - by, bottom - by)
}
