package com.mediacontrol.scoreboard.core

import org.junit.Assert.assertEquals
import org.junit.Test

class WatchTitleTest {

    private fun ScoreState.points(vararg sides: Side): ScoreState = sides.fold(this) { s, side -> s.point(side) }

    @Test
    fun theMarkSitsBesideTheSideThatServesNext() {
        assertEquals("0 : 0", watchTitle(ScoreState.EMPTY, markServer = true))
        val state = ScoreState.EMPTY.points(Side.LEFT, Side.RIGHT, Side.RIGHT)
        assertEquals("1 : 2●", watchTitle(state, markServer = true))
        assertEquals("●1 : 0", watchTitle(state.undo().undo(), markServer = true))
        // Teams change ends: the mark follows the team that won the last point.
        assertEquals("●2 : 1", watchTitle(state.swapSides(), markServer = true))
    }

    @Test
    fun noMarkWhenTheServeHighlightIsOff() {
        assertEquals("1 : 0", watchTitle(ScoreState.EMPTY.points(Side.LEFT), markServer = false))
    }
}
