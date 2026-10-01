package com.mediacontrol.scoreboard.ui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.mediacontrol.scoreboard.core.ActionKind

/**
 * Pure black/white for the digits (21:1 contrast). The serving-side tint keeps >= 10:1 against the
 * digits; the brief change flashes keep >= 6:1, so the score stays readable at all times.
 */
@Immutable
data class Palette(
    val background: Color,
    val digits: Color,
    /** Background of the half that won the last point (serves next). */
    val serveTint: Color,
    val divider: Color,
    val control: Color,
    val controlDisabled: Color,
    val pointFlash: Color,
    val warnFlash: Color,
    val pill: Color,
    val onPill: Color,
    val alertPill: Color,
    val onAlertPill: Color,
) {
    fun flashFor(kind: ActionKind): Color? = when (kind) {
        ActionKind.POINT, ActionKind.SWAP -> pointFlash
        ActionKind.UNDO, ActionKind.RESET, ActionKind.MAX_SCORE -> warnFlash
        ActionKind.RESET_ARMED, ActionKind.NOTHING_TO_UNDO -> null
    }

    companion object {
        val Dark = Palette(
            background = Color.Black,
            digits = Color.White,
            serveTint = Color(0xFF0B33A8),
            divider = Color(0xFF5C5C5C),
            control = Color(0xFFA8A8A8),
            controlDisabled = Color(0xFF4A4A4A),
            pointFlash = Color(0xFF2F5BFF),
            warnFlash = Color(0xFFB00020),
            pill = Color(0xFF2A2A2A),
            onPill = Color.White,
            alertPill = Color(0xFFFF1744),
            onAlertPill = Color.Black,
        )

        val Light = Palette(
            background = Color.White,
            digits = Color.Black,
            serveTint = Color(0xFF90CAF9),
            divider = Color(0xFFA6A6A6),
            control = Color(0xFF555555),
            controlDisabled = Color(0xFFC4C4C4),
            pointFlash = Color(0xFFFF9800),
            warnFlash = Color(0xFFFF6E6E),
            pill = Color(0xFFE6E6E6),
            onPill = Color.Black,
            alertPill = Color(0xFFD50000),
            onAlertPill = Color.White,
        )
    }
}
