// Copyright (c) 2026 Bin Jin <bjin@protonmail.com>
// SPDX-License-Identifier: MIT

package io.github.bjin.courtside.core

/**
 * Marks the serving side in the watch title. U+25CF and [NO_SERVE_MARK] (U+25CB) are part of the
 * common CJK character sets (GB 2312, JIS X 0208, KS X 1001), so watch fonts that cover CJK text
 * include both, at the same width.
 */
const val SERVE_MARK = "●"

/** Fills the other end of the watch title, so the centred score doesn't shift when the serve moves. */
const val NO_SERVE_MARK = "○"

/**
 * The score as the watch shows it (the session's track title), e.g. "11 : 9". With [markServer],
 * [SERVE_MARK] sits beside the score of the side that won the last point and serves next, and
 * [NO_SERVE_MARK] at the other end: "● 11 : 9 ○" or "○ 11 : 9 ●", and "○ 0 : 0 ○" before the first
 * rally. The spaces keep a circle from reading as a digit ("1○" looks like "10").
 */
fun watchTitle(score: ScoreState, markServer: Boolean): String {
    val title = "${score.left} : ${score.right}"
    if (!markServer) return title
    val server = score.lastPointSide
    val left = if (server == Side.LEFT) SERVE_MARK else NO_SERVE_MARK
    val right = if (server == Side.RIGHT) SERVE_MARK else NO_SERVE_MARK
    return "$left $title $right"
}
