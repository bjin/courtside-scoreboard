package io.github.bjin.courtside.core

import android.view.KeyEvent
import io.github.bjin.courtside.core.MediaCommandMapping.KeyDecision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaCommandMappingTest {

    @Test
    fun watchButtonsMapToScoreboardCommands() {
        assertEquals(RemoteCommand.POINT_LEFT, MediaCommandMapping.forKeyCode(KeyEvent.KEYCODE_MEDIA_PREVIOUS))
        assertEquals(RemoteCommand.POINT_RIGHT, MediaCommandMapping.forKeyCode(KeyEvent.KEYCODE_MEDIA_NEXT))
        for (key in listOf(
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PAUSE,
            KeyEvent.KEYCODE_HEADSETHOOK,
        )) {
            assertEquals(RemoteCommand.UNDO, MediaCommandMapping.forKeyCode(key))
        }
    }

    @Test
    fun podcastStyleSkipKeysFollowTheSameSides() {
        assertEquals(RemoteCommand.POINT_LEFT, MediaCommandMapping.forKeyCode(KeyEvent.KEYCODE_MEDIA_REWIND))
        assertEquals(RemoteCommand.POINT_LEFT, MediaCommandMapping.forKeyCode(KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD))
        assertEquals(RemoteCommand.POINT_RIGHT, MediaCommandMapping.forKeyCode(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD))
        assertEquals(RemoteCommand.POINT_RIGHT, MediaCommandMapping.forKeyCode(KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD))
    }

    @Test
    fun stopAndNonMediaKeysAreNotScoreboardCommands() {
        assertNull(MediaCommandMapping.forKeyCode(KeyEvent.KEYCODE_MEDIA_STOP))
        assertNull(MediaCommandMapping.forKeyCode(KeyEvent.KEYCODE_VOLUME_UP))
        assertNull(MediaCommandMapping.forKeyCode(KeyEvent.KEYCODE_ENTER))
    }

    @Test
    fun onlyTheInitialKeyDownExecutes() {
        val next = KeyEvent.KEYCODE_MEDIA_NEXT
        assertEquals(
            KeyDecision.Execute(RemoteCommand.POINT_RIGHT),
            MediaCommandMapping.decideKey(KeyEvent.ACTION_DOWN, next, repeatCount = 0),
        )
        // Auto-repeat of a held key and the key-up are swallowed, never a second point.
        assertEquals(KeyDecision.Consume, MediaCommandMapping.decideKey(KeyEvent.ACTION_DOWN, next, repeatCount = 1))
        assertEquals(KeyDecision.Consume, MediaCommandMapping.decideKey(KeyEvent.ACTION_UP, next, repeatCount = 0))
        assertEquals(
            KeyDecision.Pass,
            MediaCommandMapping.decideKey(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_STOP, repeatCount = 0),
        )
    }

    @Test
    fun playPauseIsUndoImmediatelyEvenWhenPressedTwiceQuickly() {
        // The framework default would delay the first press and turn a double press into "next".
        repeat(2) {
            assertEquals(
                KeyDecision.Execute(RemoteCommand.UNDO),
                MediaCommandMapping.decideKey(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, repeatCount = 0),
            )
        }
    }

    @Test
    fun seeksMapByDirectionAndIgnoreTinyAdjustments() {
        val now = 3_600_000L
        assertEquals(RemoteCommand.POINT_RIGHT, MediaCommandMapping.forSeek(now + 15_000, now))
        assertEquals(RemoteCommand.POINT_LEFT, MediaCommandMapping.forSeek(now - 10_000, now))
        assertEquals(RemoteCommand.POINT_RIGHT, MediaCommandMapping.forSeek(now + 1_000, now))
        assertEquals(RemoteCommand.POINT_LEFT, MediaCommandMapping.forSeek(now - 1_000, now))
        assertNull(MediaCommandMapping.forSeek(now + 999, now))
        assertNull(MediaCommandMapping.forSeek(now - 400, now))
    }
}
