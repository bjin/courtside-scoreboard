// Copyright (c) 2026 Bin Jin <bjin@protonmail.com>
// SPDX-License-Identifier: MIT

package io.github.bjin.courtside.core

/** Display side of the scoreboard, as seen by someone looking at the phone. */
enum class Side {
    LEFT,
    RIGHT,
}

/**
 * The two competitors. Points are recorded per team rather than per display side so that
 * undo keeps reversing the right score after the teams change ends ([ScoreState.swapped]).
 */
enum class Team(val code: Char) {
    A('A'),
    B('B'),
}

/**
 * Immutable scoreboard state.
 *
 * The score is a pure function of [history]: replaying the recorded points from 0 : 0 yields
 * [teamA] and [teamB], so the history can never disagree with the displayed score. Reset is the
 * only operation that discards history. Scores are capped at [MAX_SCORE], which also bounds the
 * history to `2 * MAX_SCORE` entries.
 */
class ScoreState private constructor(
    val history: List<Team>,
    val teamA: Int,
    val teamB: Int,
    /** True when team A is displayed on the right (teams changed ends). */
    val swapped: Boolean,
) {
    val left: Int get() = if (swapped) teamB else teamA
    val right: Int get() = if (swapped) teamA else teamB
    val canUndo: Boolean get() = history.isNotEmpty()

    fun scoreOf(side: Side): Int = if (side == Side.LEFT) left else right

    fun teamOn(side: Side): Team = if ((side == Side.LEFT) != swapped) Team.A else Team.B

    /**
     * Display side that won the most recent point, i.e. the side that serves next in badminton
     * (the rally winner serves). Null at 0 : 0, where the first server is chosen by toss.
     */
    val lastPointSide: Side? get() = history.lastOrNull()?.let(::sideOf)

    fun sideOf(team: Team): Side = if (teamOn(Side.LEFT) == team) Side.LEFT else Side.RIGHT

    /** Adds one point to [side]; returns `this` unchanged when that side is already at [MAX_SCORE]. */
    fun point(side: Side): ScoreState {
        if (scoreOf(side) >= MAX_SCORE) return this
        val team = teamOn(side)
        return ScoreState(
            history = history + team,
            teamA = teamA + if (team == Team.A) 1 else 0,
            teamB = teamB + if (team == Team.B) 1 else 0,
            swapped = swapped,
        )
    }

    /** Reverses the most recent point; returns `this` when there is nothing to undo. */
    fun undo(): ScoreState {
        val last = history.lastOrNull() ?: return this
        return ScoreState(
            history = history.subList(0, history.size - 1).toList(),
            teamA = teamA - if (last == Team.A) 1 else 0,
            teamB = teamB - if (last == Team.B) 1 else 0,
            swapped = swapped,
        )
    }

    /** Teams change ends: the scores trade places, history (per team) is kept. */
    fun swapSides(): ScoreState = ScoreState(history, teamA, teamB, !swapped)

    /** Back to 0 : 0 with an empty history. */
    fun reset(): ScoreState = EMPTY

    /** Compact persistence form: history as a string of team codes, e.g. "AABAB". */
    fun encodeHistory(): String = buildString(history.size) { history.forEach { append(it.code) } }

    override fun equals(other: Any?): Boolean =
        other is ScoreState && other.swapped == swapped && other.history == history

    override fun hashCode(): Int = 31 * history.hashCode() + swapped.hashCode()

    override fun toString(): String = "ScoreState($left : $right, swapped=$swapped, history=${encodeHistory()})"

    companion object {
        const val MAX_SCORE = 99

        val EMPTY = ScoreState(emptyList(), 0, 0, swapped = false)

        /**
         * Rebuilds a state by replaying [encodedHistory]. Returns null for anything that could not
         * have been produced by [encodeHistory] (unknown codes or a score above [MAX_SCORE]).
         */
        fun decode(encodedHistory: String, swapped: Boolean): ScoreState? {
            val history = ArrayList<Team>(encodedHistory.length)
            var a = 0
            var b = 0
            for (code in encodedHistory) {
                when (code) {
                    Team.A.code -> { a++; history += Team.A }
                    Team.B.code -> { b++; history += Team.B }
                    else -> return null
                }
                if (a > MAX_SCORE || b > MAX_SCORE) return null
            }
            return ScoreState(history, a, b, swapped)
        }
    }
}
