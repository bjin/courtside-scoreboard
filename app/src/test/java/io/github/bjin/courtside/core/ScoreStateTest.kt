// Copyright (c) 2026 Bin Jin <bjin@protonmail.com>
// SPDX-License-Identifier: MIT

package io.github.bjin.courtside.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ScoreStateTest {

    private fun ScoreState.points(vararg sides: Side): ScoreState = sides.fold(this) { s, side -> s.point(side) }

    private val L = Side.LEFT
    private val R = Side.RIGHT

    @Test
    fun undoReversesPointsInExactReverseOrder() {
        val states = mutableListOf(ScoreState.EMPTY)
        val sides = listOf(L, R, R, L, R, L, L, R, L, R)
        for (side in sides) states += states.last().point(side)
        assertEquals(5, states.last().left)
        assertEquals(5, states.last().right)

        var current = states.last()
        for (expected in states.reversed().drop(1)) {
            current = current.undo()
            assertEquals(expected, current)
            assertEquals(expected.left, current.left)
            assertEquals(expected.right, current.right)
        }
        assertFalse(current.canUndo)
    }

    @Test
    fun randomSequencesFullyUndoBackToZero() {
        val random = Random(42)
        repeat(200) {
            val sides = List(random.nextInt(0, 120)) { if (random.nextBoolean()) L else R }
            var state = sides.fold(ScoreState.EMPTY) { s, side -> s.point(side) }
            assertEquals(sides.count { it == L }, state.left)
            assertEquals(sides.count { it == R }, state.right)
            repeat(sides.size) { state = state.undo() }
            assertEquals(ScoreState.EMPTY, state)
            assertEquals(0, state.left + state.right)
        }
    }

    @Test
    fun undoWithEmptyHistoryChangesNothing() {
        assertSame(ScoreState.EMPTY, ScoreState.EMPTY.undo())
    }

    @Test
    fun resetReturnsToZeroAndClearsHistory() {
        val reset = ScoreState.EMPTY.points(L, L, R).swapSides().reset()
        assertEquals(0, reset.left)
        assertEquals(0, reset.right)
        assertFalse(reset.canUndo)
        assertSame(reset, reset.undo())
        assertNull(reset.lastPointSide)
    }

    @Test
    fun scoreIsCappedAtMaxPerSideWithoutRecordingHistory() {
        val full = (1..ScoreState.MAX_SCORE).fold(ScoreState.EMPTY) { s, _ -> s.point(L) }
        assertEquals(99, full.left)
        assertSame(full, full.point(L))
        val other = full.point(R)
        assertEquals(1, other.right)
        assertEquals(ScoreState.MAX_SCORE + 1, other.history.size)
    }

    @Test
    fun lastPointSideIsTheRallyWinnerAndFollowsUndo() {
        assertNull(ScoreState.EMPTY.lastPointSide)
        val state = ScoreState.EMPTY.points(L, R, R)
        assertEquals(R, state.lastPointSide)
        assertEquals(L, state.undo().undo().lastPointSide)
        assertNull(state.undo().undo().undo().lastPointSide)
    }

    @Test
    fun swapMovesScoresAndKeepsPointsWithTheirTeam() {
        val before = ScoreState.EMPTY.points(L, L, R) // A=2 on the left, B=1 on the right
        val swapped = before.swapSides()
        assertEquals(1, swapped.left)
        assertEquals(2, swapped.right)
        // The last point (team B) is now on the left, and the serve indicator follows it.
        assertEquals(L, swapped.lastPointSide)

        val more = swapped.point(L) // team B, now displayed on the left
        assertEquals(2, more.left)
        assertEquals(2, more.right)
        val undone = more.undo().undo() // removes B's two points
        assertEquals(0, undone.left)
        assertEquals(2, undone.right)
        assertEquals(before, swapped.swapSides())
    }

    @Test
    fun historyRoundTripsThroughPersistenceEncoding() {
        val state = ScoreState.EMPTY.points(L, R, R, L, L).swapSides().point(R)
        val restored = ScoreState.decode(state.encodeHistory(), state.swapped)
        assertEquals(state, restored)
        assertEquals(state.left, restored!!.left)
        assertEquals(state.right, restored.right)
        assertEquals(state.lastPointSide, restored.lastPointSide)
        // Undo history survives the round trip.
        assertEquals(state.undo(), restored.undo())
    }

    @Test
    fun decodeRejectsCorruptHistory() {
        assertNull(ScoreState.decode("AAX", swapped = false))
        assertNull(ScoreState.decode("A".repeat(ScoreState.MAX_SCORE + 1), swapped = false))
        assertEquals(ScoreState.EMPTY, ScoreState.decode("", swapped = false))
        assertTrue(ScoreState.decode("B".repeat(ScoreState.MAX_SCORE), swapped = true)!!.left == 99)
    }
}
