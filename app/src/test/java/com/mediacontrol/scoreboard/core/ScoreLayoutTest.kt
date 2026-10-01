package com.mediacontrol.scoreboard.core

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
            centerGap = s.width * 0.06f,
            edgeMargin = s.width * 0.02f,
            cutoutClearance = s.width * 0.005f,
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
        // 2608x1200 at ~0.059 mm/px: 700 px is ~41 mm tall digits.
        assertTrue("digit height $withCutout px", withCutout >= 700f)
        assertTrue(withCutout >= 0.95f * withoutCutout)
    }

    private fun Box.inset(by: Float) = Box(left + by, top + by, right - by, bottom - by)
}
