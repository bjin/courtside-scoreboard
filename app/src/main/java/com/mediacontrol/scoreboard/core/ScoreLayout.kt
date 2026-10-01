package com.mediacontrol.scoreboard.core

import kotlin.math.max
import kotlin.math.min

/**
 * Metrics of the score font's digits at some reference size. Coordinates follow Android text:
 * x grows to the right from the glyph origin, y grows downwards from the baseline (so [inkTop] is
 * negative). The ink bounds are the union over all ten digits, which makes every score fit the
 * same box and keeps positions stable while the score changes.
 */
data class DigitMetrics(
    /** Advance width shared by all digits (tabular figures). */
    val advance: Float,
    val inkLeft: Float,
    val inkTop: Float,
    val inkRight: Float,
    val inkBottom: Float,
) {
    val inkHeight: Float get() = inkBottom - inkTop

    /** Width of the ink of a [digits]-long number: all advances but the last, plus one glyph. */
    fun blockWidth(digits: Int): Float = (digits - 1) * advance + (inkRight - inkLeft)
}

data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f

    fun contains(other: Box): Boolean =
        other.left >= left && other.top >= top && other.right <= right && other.bottom <= bottom

    fun intersects(other: Box): Boolean =
        other.left < right && other.right > left && other.top < bottom && other.bottom > top
}

/** Everything the screen needs to keep clear of, in pixels of the window. */
data class LayoutInsets(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 0f,
    val bottom: Float = 0f,
)

/** Where the two scores go and how big they are drawn. */
data class ScoreboardGeometry(
    val width: Float,
    val height: Float,
    val leftBox: Box,
    val rightBox: Box,
    /** Multiplier from [DigitMetrics] units to pixels; identical for both sides and all scores. */
    val scale: Float,
    val dividerX: Float,
    val metrics: DigitMetrics,
) {
    fun box(side: Side): Box = if (side == Side.LEFT) leftBox else rightBox

    /** Glyph origin (x of the first digit, baseline y) for a score with [digits] digits. */
    fun origin(side: Side, digits: Int): Pair<Float, Float> {
        val box = box(side)
        val inkCenterX = (metrics.inkLeft + (digits - 1) * metrics.advance + metrics.inkRight) / 2f
        val inkCenterY = (metrics.inkTop + metrics.inkBottom) / 2f
        return (box.centerX - inkCenterX * scale) to (box.centerY - inkCenterY * scale)
    }

    /** Pixel bounds of the ink of a [digits]-long score on [side]. */
    fun inkBounds(side: Side, digits: Int): Box {
        val (x, baseline) = origin(side, digits)
        return Box(
            left = x + metrics.inkLeft * scale,
            top = baseline + metrics.inkTop * scale,
            right = x + ((digits - 1) * metrics.advance + metrics.inkRight) * scale,
            bottom = baseline + metrics.inkBottom * scale,
        )
    }
}

object ScoreLayout {
    /** Scores never exceed two digits ([ScoreState.MAX_SCORE] = 99). */
    const val MAX_DIGITS = 2

    /**
     * Splits the usable width into two equal halves and sizes the digits so a two-digit score
     * fills them. Single digits use the same scale, so nothing jumps at 10.
     *
     * A display cutout on one edge (punch-hole camera in landscape) replaces that edge's margin
     * instead of adding to it, and the divider moves to the middle of the remaining width, so the
     * cutout costs both scores as little size as possible while both stay the same size.
     *
     * @param safe areas to avoid on each edge (display cutout bands), in px.
     * @param topBand height reserved for the top controls row, measured from the window edge.
     * @param bottomBand height reserved for the bottom controls row, measured from the window edge.
     * @param centerGap clear space between the two scores (contains the divider), in px.
     * @param edgeMargin clear space between the scores and a side edge without cutout, in px.
     * @param cutoutClearance extra space kept between the scores and a cutout band, in px.
     */
    fun compute(
        width: Float,
        height: Float,
        metrics: DigitMetrics,
        safe: LayoutInsets,
        topBand: Float,
        bottomBand: Float,
        centerGap: Float,
        edgeMargin: Float,
        cutoutClearance: Float,
    ): ScoreboardGeometry {
        val left = max(if (safe.left > 0f) safe.left + cutoutClearance else 0f, edgeMargin)
        val right = width - max(if (safe.right > 0f) safe.right + cutoutClearance else 0f, edgeMargin)
        val dividerX = (left + right) / 2f
        val top = max(safe.top, topBand)
        val bottom = height - max(safe.bottom, bottomBand)
        val leftBox = Box(left, top, dividerX - centerGap / 2f, bottom)
        val rightBox = Box(dividerX + centerGap / 2f, top, right, bottom)
        val scale = max(0f, min(fitScale(metrics, leftBox), fitScale(metrics, rightBox)))
        return ScoreboardGeometry(width, height, leftBox, rightBox, scale, dividerX, metrics)
    }

    private fun fitScale(metrics: DigitMetrics, box: Box): Float =
        min(box.width / metrics.blockWidth(MAX_DIGITS), box.height / metrics.inkHeight)
}
