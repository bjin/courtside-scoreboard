package com.mediacontrol.scoreboard.ui

import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.mediacontrol.scoreboard.R
import com.mediacontrol.scoreboard.core.InputSource
import com.mediacontrol.scoreboard.core.RemoteInput
import com.mediacontrol.scoreboard.data.Settings
import com.mediacontrol.scoreboard.media.ScoreboardService
import com.mediacontrol.scoreboard.scoreboardApp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import android.graphics.Color as AndroidColor

class MainActivity : ComponentActivity() {

    private val hint = MutableStateFlow<String?>(null)
    private var hintJob: Job? = null
    private var backPressedAt = 0L
    private var menuOpen by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = scoreboardApp
        app.screens.opened()
        val controller = app.controller
        val settingsStore = app.settings
        applyWindowSettings(settingsStore.settings.value)
        hideSystemBars()

        val glyphs = DigitGlyphs.load(this)
        val actions = ScoreboardActions(
            point = { controller.point(it, InputSource.TOUCH) },
            undo = { controller.undo(InputSource.TOUCH) },
            reset = { controller.reset(InputSource.TOUCH) },
            swap = { controller.swapSides(InputSource.TOUCH) },
            toggleTheme = { settingsStore.update { it.copy(darkTheme = !it.darkTheme) } },
            openMenu = { menuOpen = true },
        )
        onBackPressedDispatcher.addCallback(this) { onBackRequested() }

        setContent {
            val score by controller.state.collectAsStateWithLifecycle()
            val feedback by controller.feedback.collectAsStateWithLifecycle()
            val armedUntil by controller.resetArmedUntil.collectAsStateWithLifecycle()
            val settings by settingsStore.settings.collectAsStateWithLifecycle()
            val hintText by hint.collectAsStateWithLifecycle()
            // Left/right are physical sides of the phone, never mirrored for RTL languages.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                ScoreboardScreen(
                    ui = ScoreboardUi(
                        score = score,
                        feedback = feedback,
                        resetArmedUntil = armedUntil,
                        darkTheme = settings.darkTheme,
                        haptics = settings.haptics,
                        highlightServer = settings.highlightServer,
                        hint = hintText,
                    ),
                    glyphs = glyphs,
                    actions = actions,
                )
            }
            if (menuOpen) {
                val log by controller.remoteLog.collectAsStateWithLifecycle()
                MenuDialog(
                    settings = settings,
                    remoteLog = log,
                    onUpdate = settingsStore::update,
                    onSwap = {
                        controller.swapSides(InputSource.TOUCH)
                        menuOpen = false
                    },
                    onExit = ::finish,
                    onDismiss = { menuOpen = false },
                )
            }
        }

        lifecycleScope.launch { settingsStore.settings.collect(::applyWindowSettings) }
    }

    /**
     * With the scoreboard in front, the phone's volume keys are reset presses (same two-step rule as
     * the watch). Consuming them here keeps the system volume panel from covering the score and
     * ignores key auto-repeat, so holding a key is one press. When volume reset is off, the keys
     * adjust the phone volume as usual.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (!isScoreboardVolumeKey(keyCode)) return super.onKeyDown(keyCode, event)
        if (event.repeatCount == 0) {
            val key = KeyEvent.keyCodeToString(keyCode).removePrefix("KEYCODE_")
            scoreboardApp.controller.onRemoteVolume(RemoteInput("phone key $key", caller = null))
        }
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean =
        isScoreboardVolumeKey(keyCode) || super.onKeyUp(keyCode, event)

    private fun isScoreboardVolumeKey(keyCode: Int): Boolean =
        (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) &&
            scoreboardApp.settings.settings.value.volumeReset

    override fun onStart() {
        super.onStart()
        scoreboardApp.screens.started()
        ScoreboardService.start(this)
    }

    override fun onStop() {
        scoreboardApp.screens.stopped()
        super.onStop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onDestroy() {
        // A screen reopened right after leaving starts before this one is destroyed; only the last
        // closing screen may end the session.
        if (scoreboardApp.screens.closed(isFinishing)) ScoreboardService.stop(this)
        super.onDestroy()
    }

    /** Leaving needs two back gestures within [EXIT_CONFIRM_MS]: a stray edge swipe must not end the match view. */
    private fun onBackRequested() {
        val now = SystemClock.uptimeMillis()
        if (now - backPressedAt <= EXIT_CONFIRM_MS) {
            finish()
            return
        }
        backPressedAt = now
        showHint(getString(R.string.hint_back_again))
    }

    private fun showHint(text: String) {
        hintJob?.cancel()
        hint.value = text
        hintJob = lifecycleScope.launch {
            delay(EXIT_CONFIRM_MS)
            hint.value = null
        }
    }

    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun applyWindowSettings(settings: Settings) {
        val background = if (settings.darkTheme) AndroidColor.BLACK else AndroidColor.WHITE
        val barStyle = if (settings.darkTheme) {
            SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        } else {
            SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
        }
        enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
        window.setBackgroundDrawable(background.toDrawable())
        if (settings.keepScreenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        window.attributes = window.attributes.apply {
            screenBrightness = if (settings.maxBrightness) {
                WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
            } else {
                WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(settings.showOnLockScreen)
        } else {
            @Suppress("DEPRECATION")
            if (settings.showOnLockScreen) {
                window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
            }
        }
    }

    private companion object {
        const val EXIT_CONFIRM_MS = 2_500L
    }
}
