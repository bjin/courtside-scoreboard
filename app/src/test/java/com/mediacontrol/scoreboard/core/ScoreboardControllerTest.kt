package com.mediacontrol.scoreboard.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoreboardControllerTest {

    private class MemoryStore(var saved: ScoreState = ScoreState.EMPTY) : ScoreStore {
        var saves = 0
        override fun load() = saved
        override fun save(state: ScoreState) {
            saved = state
            saves++
        }
    }

    private class ManualScheduler : DelayedRunner {
        val pending = mutableListOf<Pair<Long, () -> Unit>>()
        override fun runAfter(delayMs: Long, action: () -> Unit): Cancellable {
            val entry = delayMs to action
            pending += entry
            return Cancellable { pending.remove(entry) }
        }

        fun runAll() {
            val due = pending.toList()
            pending.clear()
            due.forEach { it.second() }
        }
    }

    private var now = 100_000L
    private val store = MemoryStore()
    private val scheduler = ManualScheduler()
    private val controller = ScoreboardController(store, { now }, { 0L }, scheduler)
    private val input = RemoteInput("test", caller = "com.yf.smart.coros.dist")

    private fun remote(command: RemoteCommand) = controller.onRemoteCommand(command, input)
    private fun volume(atMs: Long): Feedback? {
        now = atMs
        return controller.onRemoteVolume(input)
    }

    @Test
    fun watchCommandsChangeTheScoreAndArePersisted() {
        remote(RemoteCommand.POINT_LEFT)
        remote(RemoteCommand.POINT_RIGHT)
        remote(RemoteCommand.POINT_RIGHT)
        assertEquals(1, controller.state.value.left)
        assertEquals(2, controller.state.value.right)
        assertEquals(controller.state.value, store.saved)

        val undo = remote(RemoteCommand.UNDO)
        assertEquals(ActionKind.UNDO, undo.kind)
        assertEquals(setOf(Side.RIGHT), undo.sides)
        assertEquals(1, controller.state.value.right)
        assertEquals(store.saved, controller.state.value)
    }

    @Test
    fun undoOnEmptyHistoryReportsNothingAndDoesNotPersist() {
        val outcome = remote(RemoteCommand.UNDO)
        assertEquals(ActionKind.NOTHING_TO_UNDO, outcome.kind)
        assertEquals(0, store.saves)
    }

    @Test
    fun feedbackNamesTheSideAndSourceOfEachChange() {
        val point = controller.point(Side.LEFT, InputSource.TOUCH)
        assertEquals(ActionKind.POINT, point.kind)
        assertEquals(setOf(Side.LEFT), point.sides)
        assertEquals(InputSource.TOUCH, point.source)
        val remotePoint = remote(RemoteCommand.POINT_RIGHT)
        assertEquals(InputSource.REMOTE, remotePoint.source)
        assertTrue(remotePoint.serial > point.serial)
        assertEquals(remotePoint, controller.feedback.value)
    }

    @Test
    fun twoSeparateVolumePressesResetToZeroAndClearHistory() {
        repeat(3) { remote(RemoteCommand.POINT_LEFT) }
        assertEquals(ActionKind.RESET_ARMED, volume(200_000)?.kind)
        assertEquals(205_000L, controller.resetArmedUntil.value)
        assertEquals(3, controller.state.value.left) // armed only, nothing changed yet

        assertEquals(ActionKind.RESET, volume(201_500)?.kind)
        assertEquals(ScoreState.EMPTY, controller.state.value)
        assertEquals(ScoreState.EMPTY, store.saved)
        assertNull(controller.resetArmedUntil.value)
        assertEquals(ActionKind.NOTHING_TO_UNDO, remote(RemoteCommand.UNDO).kind)
    }

    @Test
    fun anyOtherCommandCancelsAnArmedReset() {
        remote(RemoteCommand.POINT_LEFT)
        volume(200_000)
        remote(RemoteCommand.POINT_RIGHT)
        assertNull(controller.resetArmedUntil.value)
        assertEquals(ActionKind.RESET_ARMED, volume(201_500)?.kind)
        assertEquals(1, controller.state.value.left)
        assertEquals(1, controller.state.value.right)
    }

    @Test
    fun anArmedResetExpires() {
        remote(RemoteCommand.POINT_LEFT)
        volume(200_000)
        assertNotNull(controller.resetArmedUntil.value)
        now = 205_000
        scheduler.runAll()
        assertNull(controller.resetArmedUntil.value)
        assertEquals(ActionKind.RESET_ARMED, volume(205_100)?.kind)
        assertEquals(1, controller.state.value.left)
    }

    @Test
    fun aBurstOfVolumeStepsOnlyArms() {
        remote(RemoteCommand.POINT_LEFT)
        assertEquals(ActionKind.RESET_ARMED, volume(200_000)?.kind)
        assertNull(volume(200_100))
        assertNull(volume(200_200))
        assertEquals(1, controller.state.value.left)
        assertEquals(205_200L, controller.resetArmedUntil.value)
    }

    @Test
    fun remoteInputsAreLoggedNewestFirstIncludingIgnoredOnes() {
        remote(RemoteCommand.POINT_LEFT)
        controller.onIgnoredRemote(RemoteInput("stop", caller = null))
        val log = controller.remoteLog.value
        assertEquals(listOf("stop", "test"), log.map { it.input })
        assertNull(log[0].outcome)
        assertEquals(ActionKind.POINT, log[1].outcome?.kind)
        assertEquals("com.yf.smart.coros.dist", log[1].caller)
    }

    @Test
    fun stateIsRestoredFromTheStoreOnStart() {
        val persisted = ScoreState.decode("ABBA", swapped = false)!!
        val restored = ScoreboardController(MemoryStore(persisted), { now }, { 0L }, scheduler)
        assertEquals(2, restored.state.value.left)
        assertEquals(2, restored.state.value.right)
        restored.undo(InputSource.REMOTE)
        assertEquals(1, restored.state.value.left)
    }

    @Test
    fun maxScoreIsReportedWithoutChangingOrPersisting() {
        val full = ScoreState.decode("A".repeat(ScoreState.MAX_SCORE), swapped = false)!!
        val capped = ScoreboardController(MemoryStore(full), { now }, { 0L }, scheduler)
        assertEquals(ActionKind.MAX_SCORE, capped.onRemoteCommand(RemoteCommand.POINT_LEFT, input).kind)
        assertEquals(99, capped.state.value.left)
    }
}
