package com.mediacontrol.scoreboard.ui

import android.annotation.SuppressLint
import android.os.Build
import android.view.RoundedCorner
import android.view.View
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import com.mediacontrol.scoreboard.core.LayoutInsets
import kotlin.math.max

enum class Corner { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

/** Screen areas to keep content out of, in window pixels. */
@Immutable
data class SafeArea(
    /** Display cutout bands (punch-hole camera, notch) on each edge. */
    val cutout: LayoutInsets,
    /** Rounded display corner radii, indexed by [Corner.ordinal]. */
    val cornerRadii: List<Float>,
) {
    /**
     * Distance from the two edges meeting at [corner] that keeps a control there fully visible:
     * outside the cutout bands and inside the rounded corner (a square's corner at distance d from
     * both edges lies within a corner arc of radius r when d >= r * (1 - 1/sqrt 2) ~= 0.293 r).
     */
    fun cornerInset(corner: Corner, margin: Float): Pair<Float, Float> {
        val round = cornerRadii[corner.ordinal] * 0.3f
        val horizontalBand = if (corner == Corner.TOP_LEFT || corner == Corner.BOTTOM_LEFT) cutout.left else cutout.right
        val verticalBand = if (corner == Corner.TOP_LEFT || corner == Corner.TOP_RIGHT) cutout.top else cutout.bottom
        return (max(horizontalBand, round) + margin) to (max(verticalBand, round) + margin)
    }
}

/**
 * Cutout insets come from Compose (they update on rotation); rounded corners are read from the
 * root window insets (API 31+) whenever the window size or the cutout changes.
 */
@Composable
fun rememberSafeArea(windowWidth: Int, windowHeight: Int): SafeArea {
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val cutout = WindowInsets.displayCutout
    val left = cutout.getLeft(density, direction)
    val top = cutout.getTop(density)
    val right = cutout.getRight(density, direction)
    val bottom = cutout.getBottom(density)
    val view = LocalView.current
    return remember(windowWidth, windowHeight, left, top, right, bottom, view) {
        SafeArea(
            cutout = LayoutInsets(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat()),
            cornerRadii = Corner.entries.map { cornerRadius(view, it) },
        )
    }
}

@SuppressLint("InlinedApi") // Position constants are compile-time ints; the call itself is guarded.
private fun cornerRadius(view: View, corner: Corner): Float {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return 0f
    val position = when (corner) {
        Corner.TOP_LEFT -> RoundedCorner.POSITION_TOP_LEFT
        Corner.TOP_RIGHT -> RoundedCorner.POSITION_TOP_RIGHT
        Corner.BOTTOM_LEFT -> RoundedCorner.POSITION_BOTTOM_LEFT
        Corner.BOTTOM_RIGHT -> RoundedCorner.POSITION_BOTTOM_RIGHT
    }
    return view.rootWindowInsets?.getRoundedCorner(position)?.radius?.toFloat() ?: 0f
}
