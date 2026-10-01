package com.mediacontrol.scoreboard.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Persistence for the score; implementations must be cheap to call on every change. */
interface ScoreStore {
    fun load(): ScoreState
    fun save(state: ScoreState)
}

/** Schedules [action] on the controller's thread after [delayMs]; the returned handle cancels it. */
fun interface DelayedRunner {
    fun runAfter(delayMs: Long, action: () -> Unit): Cancellable
}

fun interface Cancellable {
    fun cancel()
}

enum class InputSource {
    TOUCH,
    REMOTE,
}

enum class ActionKind {
    POINT,
    UNDO,
    RESET,
    SWAP,
    RESET_ARMED,
    NOTHING_TO_UNDO,
    MAX_SCORE,
}

/** A user-visible outcome; [serial] distinguishes repeated identical outcomes. */
data class Feedback(
    val serial: Long,
    val kind: ActionKind,
    /** Display sides whose score changed (or would have, for [ActionKind.MAX_SCORE]). */
    val sides: Set<Side>,
    val source: InputSource,
)

/** What arrived from a media controller, for the on-device diagnostics list. */
data class RemoteInput(val description: String, val caller: String?)

data class RemoteLogEntry(
    val wallTimeMillis: Long,
    val input: String,
    val caller: String?,
    /** Null when the input was deliberately ignored or only continued a volume press. */
    val outcome: Feedback?,
)

/**
 * Single owner of the score. Every input (touch, MediaSession callback, volume) goes through here on
 * the main thread, so ordering is total and each change is persisted before the next one is handled.
 */
class ScoreboardController(
    private val store: ScoreStore,
    private val uptimeMillis: () -> Long,
    private val wallTimeMillis: () -> Long,
    private val delayed: DelayedRunner,
    private val resetGuard: ResetGuard = ResetGuard(),
) {
    private val _state = MutableStateFlow(store.load())
    val state: StateFlow<ScoreState> = _state.asStateFlow()

    private val _feedback = MutableStateFlow<Feedback?>(null)

    /** The latest outcome of any input, for highlights and status text. */
    val feedback: StateFlow<Feedback?> = _feedback.asStateFlow()

    private val _resetArmedUntil = MutableStateFlow<Long?>(null)

    /** Uptime (ms) at which a volume-armed reset expires; null when no reset is pending. */
    val resetArmedUntil: StateFlow<Long?> = _resetArmedUntil.asStateFlow()

    private val _remoteLog = MutableStateFlow<List<RemoteLogEntry>>(emptyList())

    /** Most recent remote inputs first, at most [REMOTE_LOG_SIZE]. */
    val remoteLog: StateFlow<List<RemoteLogEntry>> = _remoteLog.asStateFlow()

    private var serial = 0L
    private var pendingExpiry: Cancellable? = null

    fun point(side: Side, source: InputSource): Feedback {
        cancelPendingReset()
        val before = _state.value
        val after = before.point(side)
        if (after === before) return emit(ActionKind.MAX_SCORE, setOf(side), source)
        commit(after)
        return emit(ActionKind.POINT, setOf(side), source)
    }

    fun undo(source: InputSource): Feedback {
        cancelPendingReset()
        val before = _state.value
        val team = before.history.lastOrNull()
            ?: return emit(ActionKind.NOTHING_TO_UNDO, emptySet(), source)
        commit(before.undo())
        return emit(ActionKind.UNDO, setOf(before.sideOf(team)), source)
    }

    fun reset(source: InputSource): Feedback {
        cancelPendingReset()
        commit(_state.value.reset())
        return emit(ActionKind.RESET, BOTH_SIDES, source)
    }

    fun swapSides(source: InputSource): Feedback {
        cancelPendingReset()
        commit(_state.value.swapSides())
        return emit(ActionKind.SWAP, BOTH_SIDES, source)
    }

    fun onRemoteCommand(command: RemoteCommand, input: RemoteInput): Feedback {
        val outcome = when (command) {
            RemoteCommand.POINT_LEFT -> point(Side.LEFT, InputSource.REMOTE)
            RemoteCommand.POINT_RIGHT -> point(Side.RIGHT, InputSource.REMOTE)
            RemoteCommand.UNDO -> undo(InputSource.REMOTE)
        }
        log(input, outcome)
        return outcome
    }

    /**
     * A volume change from a remote control: the first press arms a reset, a second separate press
     * confirms it (see [ResetGuard]). Returns null when the input only continued the current press.
     */
    fun onRemoteVolume(input: RemoteInput): Feedback? {
        val now = uptimeMillis()
        val outcome = when (resetGuard.onVolumeInput(now)) {
            ResetGuard.Result.CONFIRMED -> reset(InputSource.REMOTE)
            ResetGuard.Result.ARMED -> {
                scheduleResetExpiry(now)
                emit(ActionKind.RESET_ARMED, BOTH_SIDES, InputSource.REMOTE)
            }
            ResetGuard.Result.CONTINUED -> {
                if (resetGuard.isArmed(now)) scheduleResetExpiry(now)
                null
            }
        }
        log(input, outcome)
        return outcome
    }

    /** Records a controller input that is intentionally not mapped to any scoreboard action. */
    fun onIgnoredRemote(input: RemoteInput) {
        log(input, null)
    }

    private fun scheduleResetExpiry(now: Long) {
        val until = resetGuard.armedUntil() ?: return
        pendingExpiry?.cancel()
        pendingExpiry = delayed.runAfter(until - now) {
            pendingExpiry = null
            resetGuard.disarm()
            _resetArmedUntil.value = null
        }
        _resetArmedUntil.value = until
    }

    private fun cancelPendingReset() {
        resetGuard.disarm()
        pendingExpiry?.cancel()
        pendingExpiry = null
        _resetArmedUntil.value = null
    }

    private fun commit(newState: ScoreState) {
        _state.value = newState
        store.save(newState)
    }

    private fun emit(kind: ActionKind, sides: Set<Side>, source: InputSource): Feedback =
        Feedback(++serial, kind, sides, source).also { _feedback.value = it }

    private fun log(input: RemoteInput, outcome: Feedback?) {
        val entry = RemoteLogEntry(wallTimeMillis(), input.description, input.caller, outcome)
        _remoteLog.value = (listOf(entry) + _remoteLog.value).take(REMOTE_LOG_SIZE)
    }

    companion object {
        const val REMOTE_LOG_SIZE = 30
        private val BOTH_SIDES = setOf(Side.LEFT, Side.RIGHT)
    }
}
