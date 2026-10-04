// Copyright (c) 2026 Bin Jin <bjin@protonmail.com>
// SPDX-License-Identifier: MIT

package io.github.bjin.courtside.ui

import android.content.Context
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.core.content.res.ResourcesCompat
import io.github.bjin.courtside.R
import io.github.bjin.courtside.core.DigitMetrics
import kotlin.math.max
import kotlin.math.min

/**
 * The ten score digits as vector outlines, extracted once from the bundled font (B612 Bold subset).
 *
 * Drawing outlines instead of text gives exact ink bounds, so the digits can fill the available
 * box to the pixel instead of relying on font line metrics, and avoids huge-glyph text caches.
 */
class DigitGlyphs private constructor(
    private val paths: List<Path>,
    /** Per digit x offset that centres it in the shared cell (all zero for tabular fonts). */
    private val cellOffsets: FloatArray,
    val metrics: DigitMetrics,
) {
    /** Draws [score] with the first digit's origin at ([x], [baseline]) and [scale] px per unit. */
    fun DrawScope.drawScore(score: Int, x: Float, baseline: Float, scale: Float, color: Color) {
        val text = score.toString()
        for (i in text.indices) {
            val digit = text[i] - '0'
            translate(left = x + (i * metrics.advance + cellOffsets[digit]) * scale, top = baseline) {
                scale(scale, pivot = Offset.Zero) {
                    drawPath(paths[digit], color)
                }
            }
        }
    }

    companion object {
        private const val REFERENCE_SIZE = 1000f

        fun load(context: Context): DigitGlyphs {
            val typeface = ResourcesCompat.getFont(context, R.font.score_digits)
                ?: error("Bundled score font missing")
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.typeface = typeface
                textSize = REFERENCE_SIZE
            }
            val advances = FloatArray(10) { paint.measureText(it.toString()) }
            val cell = advances.max()
            val bounds = RectF()
            var left = Float.MAX_VALUE
            var top = Float.MAX_VALUE
            var right = -Float.MAX_VALUE
            var bottom = -Float.MAX_VALUE
            val offsets = FloatArray(10) { (cell - advances[it]) / 2f }
            val paths = List(10) { digit ->
                val path = android.graphics.Path()
                paint.getTextPath(digit.toString(), 0, 1, 0f, 0f, path)
                path.computeBounds(bounds, true)
                left = min(left, bounds.left + offsets[digit])
                top = min(top, bounds.top)
                right = max(right, bounds.right + offsets[digit])
                bottom = max(bottom, bounds.bottom)
                path.asComposePath()
            }
            return DigitGlyphs(paths, offsets, DigitMetrics(cell, left, top, right, bottom))
        }
    }
}
