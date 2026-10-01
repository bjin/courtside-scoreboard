package com.mediacontrol.scoreboard.ui

import android.os.SystemClock
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mediacontrol.scoreboard.R
import com.mediacontrol.scoreboard.core.ActionKind
import com.mediacontrol.scoreboard.core.Feedback
import com.mediacontrol.scoreboard.core.InputSource
import com.mediacontrol.scoreboard.core.ScoreLayout
import com.mediacontrol.scoreboard.core.ScoreState
import com.mediacontrol.scoreboard.core.Side
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/** Everything the scoreboard screen renders. */
@Immutable
data class ScoreboardUi(
    val score: ScoreState,
    val feedback: Feedback?,
    /** Uptime (ms) when a volume-armed reset expires, null when none is pending. */
    val resetArmedUntil: Long?,
    val darkTheme: Boolean,
    val haptics: Boolean,
    /** Tint the half that won the last point (it serves next in badminton). */
    val highlightServer: Boolean,
    /** Transient hint from outside the screen (e.g. "press back again"), null when none. */
    val hint: String?,
)

@Stable
class ScoreboardActions(
    val point: (Side) -> Unit,
    val undo: () -> Unit,
    val reset: () -> Unit,
    val swap: () -> Unit,
    val toggleTheme: () -> Unit,
    val openMenu: () -> Unit,
)

/** Height reserved above and below the digits for controls and messages. */
private val BAND = 60.dp
private val CONTROL = 44.dp
private val TEXT_CONTROL_WIDTH = 92.dp
private val EDGE = 8.dp
private val CONTROL_SHAPE = RoundedCornerShape(12.dp)

/** Clear space between the two scores (holds the divider), as a fraction of the window width. */
private const val CENTER_GAP_FRACTION = 0.06f
private const val EDGE_MARGIN_FRACTION = 0.02f
private const val CUTOUT_CLEARANCE_FRACTION = 0.005f
private const val DIVIDER_FRACTION = 0.008f
private const val FLASH_MS = 900
private const val MESSAGE_MS = 2_000L

/** A press longer than this is a hold (hand resting on the phone), not a score tap. */
private const val MAX_TAP_MS = 800L
private const val HOLD_TO_RESET_MS = 900L

@Composable
fun ScoreboardScreen(ui: ScoreboardUi, glyphs: DigitGlyphs, actions: ScoreboardActions) {
    val palette = if (ui.darkTheme) Palette.Dark else Palette.Light
    val view = LocalView.current
    val haptics by rememberUpdatedState(ui.haptics)
    val haptic = remember(view) { { type: Int -> if (haptics) view.performHapticFeedback(type) } }
    val scope = rememberCoroutineScope()
    val message = remember { TransientMessage() }
    val flashes = rememberSideFlashes(ui.feedback, palette)
    val server = if (ui.highlightServer) ui.score.lastPointSide else null

    val feedback = ui.feedback
    val feedbackText = feedback?.let { feedbackMessage(it) }
    LaunchedEffect(feedback?.serial) {
        if (feedback != null && feedbackText != null && feedback.serial != flashes.initialSerial) {
            message.show(scope, feedbackText)
        }
    }
    val holdHint = stringResource(R.string.hint_hold_reset)
    val servingText = stringResource(R.string.cd_serving)

    BoxWithConstraints(Modifier.fillMaxSize().background(palette.background)) {
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val density = LocalDensity.current
        val safe = rememberSafeArea(width, height)
        val edge = with(density) { EDGE.toPx() }
        val controlPx = with(density) { CONTROL.toPx() }
        val corners = Corner.entries.associateWith { safe.cornerInset(it, edge) }
        val band = max(with(density) { BAND.toPx() }, corners.values.maxOf { it.second } + controlPx + edge / 2f)
        val geometry = remember(width, height, safe, glyphs, band) {
            ScoreLayout.compute(
                width = width.toFloat(),
                height = height.toFloat(),
                metrics = glyphs.metrics,
                safe = safe.cutout,
                topBand = band,
                bottomBand = band,
                centerGap = width * CENTER_GAP_FRACTION,
                edgeMargin = width * EDGE_MARGIN_FRACTION,
                cutoutClearance = width * CUTOUT_CLEARANCE_FRACTION,
            )
        }

        // Layer 1: the two tap halves. They paint the serving-side tint and the change flash
        // behind the digits.
        Row(Modifier.fillMaxSize()) {
            for (side in Side.entries) key(side) {
                val flash = flashes.of(side)
                val tint = if (side == server) palette.serveTint else Color.Transparent
                val onTap = remember(actions, haptic) {
                    {
                        haptic(HapticFeedbackConstants.VIRTUAL_KEY)
                        actions.point(side)
                    }
                }
                val description = stringResource(
                    if (side == Side.LEFT) R.string.cd_left_score else R.string.cd_right_score,
                    ui.score.scoreOf(side),
                )
                Box(
                    Modifier
                        .width(with(density) { (if (side == Side.LEFT) geometry.dividerX else width - geometry.dividerX).toDp() })
                        .fillMaxHeight()
                        .background(tint)
                        .drawBehind {
                            val alpha = flash.alpha.value
                            if (alpha > 0f) drawRect(flash.color, alpha = alpha)
                        }
                        .scoreTaps(onTap)
                        .semantics {
                            contentDescription = description
                            if (side == server) stateDescription = servingText
                            onClick {
                                onTap()
                                true
                            }
                        },
                )
            }
        }

        // Layer 2: divider and digits (drawing only, never consumes touches).
        Canvas(Modifier.fillMaxSize()) {
            val dividerWidth = size.width * DIVIDER_FRACTION
            drawRect(
                color = palette.divider,
                topLeft = Offset(geometry.dividerX - dividerWidth / 2f, geometry.leftBox.top),
                size = Size(dividerWidth, geometry.leftBox.height),
            )
            with(glyphs) {
                for (side in Side.entries) {
                    val score = ui.score.scoreOf(side)
                    val (x, baseline) = geometry.origin(side, digits = if (score >= 10) 2 else 1)
                    drawScore(score, x, baseline, geometry.scale, palette.digits)
                }
            }
        }

        // Layer 3: controls. Each consumes its own touches, so they never count as score taps.
        val (topLeftX, topLeftY) = corners.getValue(Corner.TOP_LEFT)
        IconControl(
            description = stringResource(R.string.cd_toggle_theme),
            color = palette.control,
            onClick = actions.toggleTheme,
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset { IntOffset(topLeftX.roundToInt(), topLeftY.roundToInt()) },
        ) { drawThemeIcon(it) }

        val (topRightX, topRightY) = corners.getValue(Corner.TOP_RIGHT)
        IconControl(
            description = stringResource(R.string.cd_menu),
            color = palette.control,
            onClick = actions.openMenu,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(-topRightX.roundToInt(), topRightY.roundToInt()) },
        ) { drawMenuIcon(it) }

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset { IntOffset(0, -(safe.cutout.bottom + edge).roundToInt()) },
        ) {
            TextControl(
                label = stringResource(R.string.action_swap),
                color = palette.control,
                onClick = {
                    haptic(HapticFeedbackConstants.VIRTUAL_KEY)
                    actions.swap()
                },
            )
            TextControl(
                label = stringResource(R.string.action_undo),
                color = if (ui.score.canUndo) palette.control else palette.controlDisabled,
                onClick = {
                    haptic(HapticFeedbackConstants.VIRTUAL_KEY)
                    actions.undo()
                },
            )
            HoldToResetControl(
                label = stringResource(R.string.action_reset),
                palette = palette,
                onHeld = {
                    haptic(HapticFeedbackConstants.LONG_PRESS)
                    actions.reset()
                },
                onTooShort = { message.show(scope, holdHint) },
            )
        }

        // Layer 4: one-line status in the top band, never over the digits.
        val armedSeconds = rememberArmedSecondsLeft(ui.resetArmedUntil)
        val armedText = armedSeconds?.let { stringResource(R.string.status_reset_armed_screen, it) }
        val pillText = armedText ?: ui.hint ?: message.text
        if (pillText != null) {
            StatusPill(
                text = pillText,
                alert = armedText != null,
                palette = palette,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset { IntOffset(0, (safe.cutout.top + edge).roundToInt()) },
            )
        }
    }
}

@Composable
private fun feedbackMessage(feedback: Feedback): String? {
    val remote = feedback.source == InputSource.REMOTE
    val id = when (feedback.kind) {
        ActionKind.POINT -> when {
            !remote -> null
            Side.LEFT in feedback.sides -> R.string.msg_remote_point_left
            else -> R.string.msg_remote_point_right
        }
        ActionKind.UNDO -> if (remote) R.string.msg_remote_undo else null
        ActionKind.RESET -> if (remote) R.string.msg_remote_reset else R.string.msg_reset
        ActionKind.SWAP -> R.string.msg_swapped
        ActionKind.NOTHING_TO_UNDO -> R.string.msg_nothing_to_undo
        ActionKind.MAX_SCORE -> R.string.msg_max_score
        ActionKind.RESET_ARMED -> null
    }
    return id?.let { stringResource(it) }
}

/**
 * A score tap is a single finger going down and up within [MAX_TAP_MS] without moving beyond touch
 * slop. Swipes (e.g. revealing the system bars), long holds, multi-finger contact and gestures the
 * system cancels are ignored, which filters most accidental touches when handling the phone.
 */
private fun Modifier.scoreTaps(onTap: () -> Unit): Modifier = pointerInput(Unit) {
    val slop = viewConfiguration.touchSlop
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = true)
        var valid = true
        while (true) {
            val event = awaitPointerEvent()
            if (event.changes.size > 1) valid = false
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (change.isConsumed || (change.position - down.position).getDistance() > slop) valid = false
            if (!change.pressed) {
                if (valid && change.uptimeMillis - down.uptimeMillis <= MAX_TAP_MS) {
                    change.consume()
                    onTap()
                }
                break
            }
        }
    }
}

@Composable
private fun IconControl(
    description: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: DrawScope.(Color) -> Unit,
) {
    Box(
        modifier
            .size(CONTROL)
            .clip(CONTROL_SHAPE)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = description
                role = Role.Button
            }
            .drawBehind { icon(color) },
    )
}

@Composable
private fun TextControl(label: String, color: Color, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(TEXT_CONTROL_WIDTH, CONTROL)
            .clip(CONTROL_SHAPE)
            .border(1.5.dp, color, CONTROL_SHAPE)
            .clickable(onClick = onClick),
    ) {
        ControlLabel(label, color)
    }
}

/** Reset needs a deliberate ~1 s hold; a short tap only explains that. */
@Composable
private fun HoldToResetControl(label: String, palette: Palette, onHeld: () -> Unit, onTooShort: () -> Unit) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val held by rememberUpdatedState(onHeld)
    val tooShort by rememberUpdatedState(onTooShort)
    val description = stringResource(R.string.cd_hold_reset)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(TEXT_CONTROL_WIDTH, CONTROL)
            .clip(CONTROL_SHAPE)
            .drawBehind {
                drawRect(palette.warnFlash, size = Size(size.width * progress.value, size.height))
            }
            .border(1.5.dp, palette.control, CONTROL_SHAPE)
            .semantics {
                contentDescription = description
                role = Role.Button
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown().consume()
                    val fill = scope.launch {
                        progress.snapTo(0f)
                        progress.animateTo(1f, tween(HOLD_TO_RESET_MS.toInt(), easing = LinearEasing))
                    }
                    val releasedEarly = withTimeoutOrNull(HOLD_TO_RESET_MS) {
                        waitForUpOrCancellation()
                        true
                    }
                    fill.cancel()
                    if (releasedEarly == null) {
                        held()
                        waitForUpOrCancellation()
                    } else {
                        tooShort()
                    }
                    scope.launch { progress.animateTo(0f, tween(150)) }
                }
            },
    ) {
        ControlLabel(label, palette.control)
    }
}

@Composable
private fun ControlLabel(label: String, color: Color) {
    Text(
        text = label,
        color = color,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        maxLines = 1,
    )
}

@Composable
private fun StatusPill(text: String, alert: Boolean, palette: Palette, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = if (alert) palette.onAlertPill else palette.onPill,
        fontSize = if (alert) 20.sp else 16.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        modifier = modifier
            .background(if (alert) palette.alertPill else palette.pill, RoundedCornerShape(50))
            .padding(horizontal = 18.dp, vertical = 8.dp),
    )
}

/** Half-filled circle: the light/dark toggle. */
private fun DrawScope.drawThemeIcon(color: Color) {
    val radius = size.minDimension * 0.30f
    drawCircle(color, radius, style = Stroke(size.minDimension * 0.06f))
    drawArc(
        color = color,
        startAngle = 90f,
        sweepAngle = 180f,
        useCenter = true,
        topLeft = Offset(center.x - radius, center.y - radius),
        size = Size(radius * 2, radius * 2),
    )
}

/** Three dots: opens the menu. */
private fun DrawScope.drawMenuIcon(color: Color) {
    val dot = size.minDimension * 0.07f
    val gap = size.minDimension * 0.22f
    for (i in -1..1) drawCircle(color, dot, Offset(center.x + i * gap, center.y))
}

@Composable
private fun rememberArmedSecondsLeft(until: Long?): Int? {
    val seconds by produceState<Int?>(initialValue = null, until) {
        if (until == null) {
            value = null
            return@produceState
        }
        while (true) {
            val left = until - SystemClock.uptimeMillis()
            if (left <= 0) break
            value = ceil(left / 1000.0).toInt()
            delay(100)
        }
        value = null
    }
    return seconds
}

@Stable
private class TransientMessage {
    var text by mutableStateOf<String?>(null)
        private set
    private var job: Job? = null

    fun show(scope: CoroutineScope, value: String) {
        job?.cancel()
        text = value
        job = scope.launch {
            delay(MESSAGE_MS)
            text = null
        }
    }
}

@Stable
private class SideFlash {
    val alpha = Animatable(0f)
    var color by mutableStateOf(Color.Transparent)
}

@Stable
private class SideFlashes(val initialSerial: Long?) {
    private val left = SideFlash()
    private val right = SideFlash()
    fun of(side: Side) = if (side == Side.LEFT) left else right
}

/**
 * Briefly flashes the side(s) a change touched; the score itself stays fully visible. Animations
 * run in a scope that outlives the triggering effect, so a quick second change on the other side
 * cannot freeze the first flash half-way.
 */
@Composable
private fun rememberSideFlashes(feedback: Feedback?, palette: Palette): SideFlashes {
    val flashes = remember { SideFlashes(initialSerial = feedback?.serial) }
    val animationScope = rememberCoroutineScope()
    LaunchedEffect(feedback?.serial) {
        val current = feedback ?: return@LaunchedEffect
        if (current.serial == flashes.initialSerial) return@LaunchedEffect
        val color = palette.flashFor(current.kind) ?: return@LaunchedEffect
        for (side in current.sides) {
            val flash = flashes.of(side)
            flash.color = color
            animationScope.launch {
                flash.alpha.snapTo(1f)
                flash.alpha.animateTo(0f, tween(FLASH_MS, easing = LinearOutSlowInEasing))
            }
        }
    }
    return flashes
}
