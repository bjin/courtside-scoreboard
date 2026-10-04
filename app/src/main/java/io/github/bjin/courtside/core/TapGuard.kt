// Copyright (c) 2026 Bin Jin <bjin@protonmail.com>
// SPDX-License-Identifier: MIT

package io.github.bjin.courtside.core

import kotlin.math.hypot
import kotlin.math.min

/** Radii of the display's rounded corners in pixels; 0 where a corner is square. */
data class CornerRadii(
    val topLeft: Float = 0f,
    val topRight: Float = 0f,
    val bottomLeft: Float = 0f,
    val bottomRight: Float = 0f,
)

/**
 * Decides where a touch may start a score tap, in window pixels (the window covers the display).
 *
 * A touch never scores when it goes down closer than the edge guard to the display's visible
 * outline (its edges, rounded at the corners): a hand holding the phone and the system's edge
 * swipes land there. Nor inside the no-score zone around a control, [ZONE_SCALE] times the
 * control's width and height, so a slightly missed button never adds a point.
 *
 * Plain mutable state on the main thread: composition sets the display, layout records the
 * controls, and a touch reads both when it goes down, so nothing recomposes.
 */
class TapGuard {
    private var width = 0f
    private var height = 0f
    private var corners = CornerRadii()
    private var edgeGuard = 0f
    private val zones = HashMap<String, Box>()

    fun setDisplay(width: Float, height: Float, corners: CornerRadii, edgeGuard: Float) {
        this.width = width
        this.height = height
        this.corners = corners
        this.edgeGuard = edgeGuard
    }

    /** Records where control [key] is now; its no-score zone is centred on it. */
    fun setControl(key: String, left: Float, top: Float, right: Float, bottom: Float) {
        val growX = (right - left) * (ZONE_SCALE - 1f) / 2f
        val growY = (bottom - top) * (ZONE_SCALE - 1f) / 2f
        zones[key] = Box(left - growX, top - growY, right + growX, bottom + growY)
    }

    fun allows(x: Float, y: Float): Boolean =
        distanceToOutline(x, y) >= edgeGuard &&
            zones.values.none { x >= it.left && x <= it.right && y >= it.top && y <= it.bottom }

    /**
     * Distance from (x, y) to the display's visible outline, negative outside it. Inside a rounded
     * corner's square the outline is the corner arc, which is always nearer than the straight edges.
     */
    private fun distanceToOutline(x: Float, y: Float): Float {
        var distance = minOf(x, y, width - x, height - y)
        val (topLeft, topRight, bottomLeft, bottomRight) = corners
        if (x < topLeft && y < topLeft) {
            distance = min(distance, topLeft - hypot(topLeft - x, topLeft - y))
        }
        if (x > width - topRight && y < topRight) {
            distance = min(distance, topRight - hypot(x - (width - topRight), topRight - y))
        }
        if (x < bottomLeft && y > height - bottomLeft) {
            distance = min(distance, bottomLeft - hypot(bottomLeft - x, y - (height - bottomLeft)))
        }
        if (x > width - bottomRight && y > height - bottomRight) {
            distance = min(distance, bottomRight - hypot(x - (width - bottomRight), y - (height - bottomRight)))
        }
        return distance
    }

    companion object {
        /** A control's no-score zone is this many times its width and height, centred on it. */
        const val ZONE_SCALE = 1.5f
    }
}
