package com.mediacontrol.scoreboard.core

/**
 * Marks the serving side in the watch title. U+25CF is part of the common CJK character sets
 * (GB 2312, JIS X 0208, KS X 1001), so watch fonts that cover CJK text include it.
 */
const val SERVE_MARK = "●"

/**
 * The score as the watch shows it (the session's track title), e.g. "11 : 9". With [markServer],
 * [SERVE_MARK] sits beside the score of the side that won the last point and serves next:
 * "●11 : 9" or "11 : 9●". Nobody is marked at 0 : 0, before the first rally.
 */
fun watchTitle(score: ScoreState, markServer: Boolean): String {
    val title = "${score.left} : ${score.right}"
    return when (if (markServer) score.lastPointSide else null) {
        Side.LEFT -> SERVE_MARK + title
        Side.RIGHT -> title + SERVE_MARK
        null -> title
    }
}
