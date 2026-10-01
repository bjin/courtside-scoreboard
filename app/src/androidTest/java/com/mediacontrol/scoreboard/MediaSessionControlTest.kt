package com.mediacontrol.scoreboard

import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.SystemClock
import android.view.KeyEvent
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mediacontrol.scoreboard.core.InputSource
import com.mediacontrol.scoreboard.ui.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Drives the scoreboard the way the COROS app does: through a MediaController on the published
 * session (transport controls, media buttons, session volume), and checks the score + metadata.
 */
@RunWith(AndroidJUnit4::class)
class MediaSessionControlTest {

    @get:Rule
    val activity = ActivityScenarioRule(MainActivity::class.java)

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext.applicationContext as ScoreboardApp
    private lateinit var controller: MediaController

    @Before
    fun connect() {
        instrumentation.runOnMainSync {
            app.settings.update { it.copy(volumeReset = true, highlightServer = true) }
            app.controller.reset(InputSource.TOUCH)
        }
        val token = waitFor("session token") { app.sessionToken.value }
        controller = MediaController(instrumentation.targetContext, token)
        awaitTitle("○ 0 : 0 ○")
    }

    @Test
    fun previousNextAndPlayPauseMapToLeftRightAndUndo() {
        val transport = controller.transportControls
        transport.skipToNext()
        transport.skipToNext()
        transport.skipToPrevious()
        awaitTitle("● 1 : 2 ○")
        transport.pause()
        awaitTitle("○ 0 : 2 ●")
        transport.play()
        awaitTitle("○ 0 : 1 ●")
        assertEquals(PlaybackState.STATE_PLAYING, controller.playbackState?.state)
        assertEquals(app.getString(R.string.status_undo), metadata(MediaMetadata.METADATA_KEY_ARTIST))
    }

    @Test
    fun mediaButtonCountsOncePerPressAndPlayPauseUndoesImmediately() {
        press(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        press(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        awaitTitle("● 2 : 0 ○")
        // Two quick play/pause presses are two undos, not the framework's "double tap = next".
        press(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        press(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        awaitTitle("○ 0 : 0 ○")
    }

    @Test
    fun podcastStyleSeeksAddPointsByDirection() {
        controller.transportControls.seekTo(currentPosition() + 15_000)
        awaitTitle("○ 0 : 1 ●")
        controller.transportControls.seekTo(currentPosition() - 10_000)
        awaitTitle("● 1 : 1 ○")
    }

    /** The watch title marks the side that serves; the highlight setting turns the mark off too. */
    @Test
    fun theServeMarkFollowsTheHighlightSetting() {
        controller.transportControls.skipToPrevious()
        awaitTitle("● 1 : 0 ○")
        instrumentation.runOnMainSync { app.settings.update { it.copy(highlightServer = false) } }
        awaitTitle("1 : 0")
    }

    @Test
    fun twoSeparateVolumeInputsResetWithoutChangingThePhoneVolume() {
        val audio = app.getSystemService(AudioManager::class.java)
        val phoneVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        controller.transportControls.skipToNext()
        controller.transportControls.skipToPrevious()
        awaitTitle("● 1 : 1 ○")

        controller.adjustVolume(AudioManager.ADJUST_RAISE, 0)
        val armed = app.getString(R.string.status_reset_armed)
        waitFor("reset armed") { armed.takeIf { it == metadata(MediaMetadata.METADATA_KEY_ARTIST) } }
        assertEquals("● 1 : 1 ○", metadata(MediaMetadata.METADATA_KEY_TITLE))
        SystemClock.sleep(900)
        controller.setVolumeTo(8, 0)
        awaitTitle("○ 0 : 0 ○")

        assertEquals(phoneVolume, audio.getStreamVolume(AudioManager.STREAM_MUSIC))
        assertEquals(MediaController.PlaybackInfo.PLAYBACK_TYPE_REMOTE, controller.playbackInfo.playbackType)
    }

    /** The phone's volume keys reach the session when the scoreboard is not in front. */
    @Test
    fun phoneVolumeKeysOutsideTheScoreboardAdjustThePhoneAndNeverArmAReset() {
        val audio = app.getSystemService(AudioManager::class.java)
        val before = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        val up = before < audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        controller.transportControls.skipToNext()
        awaitTitle("○ 0 : 1 ●")
        activity.scenario.moveToState(Lifecycle.State.CREATED) // stopped: another app or screen off
        try {
            repeat(2) {
                controller.adjustVolume(if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER, 0)
                SystemClock.sleep(600)
            }
            waitFor("phone volume moved") {
                true.takeIf { audio.getStreamVolume(AudioManager.STREAM_MUSIC) != before }
            }
            assertNull(app.controller.resetArmedUntil.value)
            assertEquals("○ 0 : 1 ●", metadata(MediaMetadata.METADATA_KEY_TITLE))
        } finally {
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, before, 0)
        }
    }

    /** A watch app that sets the phone's media volume directly (bypassing the session). */
    @Test
    fun directPhoneVolumeChangesArmAndConfirmResetAndAreUndone() {
        instrumentation.runOnMainSync { app.settings.update { it.copy(phoneVolumeFallback = true) } }
        val audio = app.getSystemService(AudioManager::class.java)
        val baseline = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        val other = if (baseline < audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)) baseline + 1 else baseline - 1
        controller.transportControls.skipToPrevious()
        awaitTitle("● 1 : 0 ○")

        audio.setStreamVolume(AudioManager.STREAM_MUSIC, other, 0)
        val armed = app.getString(R.string.status_reset_armed)
        waitFor("reset armed") { armed.takeIf { it == metadata(MediaMetadata.METADATA_KEY_ARTIST) } }
        waitFor("phone volume restored") {
            true.takeIf { audio.getStreamVolume(AudioManager.STREAM_MUSIC) == baseline }
        }
        SystemClock.sleep(900)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, other, 0)
        awaitTitle("○ 0 : 0 ○")
        waitFor("phone volume restored") {
            true.takeIf { audio.getStreamVolume(AudioManager.STREAM_MUSIC) == baseline }
        }
    }

    private fun press(keyCode: Int) {
        controller.dispatchMediaButtonEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        controller.dispatchMediaButtonEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }

    private fun currentPosition(): Long {
        val state = checkNotNull(controller.playbackState) { "no playback state" }
        val elapsed = SystemClock.elapsedRealtime() - state.lastPositionUpdateTime
        return state.position + (elapsed * state.playbackSpeed).toLong()
    }

    private fun metadata(key: String): String? = controller.metadata?.getString(key)

    private fun awaitTitle(title: String) {
        waitFor("title \"$title\" (was \"${metadata(MediaMetadata.METADATA_KEY_TITLE)}\")") {
            title.takeIf { metadata(MediaMetadata.METADATA_KEY_TITLE) == it }
        }
    }

    private fun <T : Any> waitFor(what: String, timeoutMs: Long = 5_000, probe: () -> T?): T {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            probe()?.let { return it }
            SystemClock.sleep(20)
        }
        throw AssertionError("Timed out waiting for $what")
    }
}
