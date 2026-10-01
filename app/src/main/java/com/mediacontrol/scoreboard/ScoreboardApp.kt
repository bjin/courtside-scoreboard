package com.mediacontrol.scoreboard

import android.app.Application
import android.content.Context
import android.media.session.MediaSession
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.mediacontrol.scoreboard.core.Cancellable
import com.mediacontrol.scoreboard.core.DelayedRunner
import com.mediacontrol.scoreboard.core.ScoreboardController
import com.mediacontrol.scoreboard.data.PrefsScoreStore
import com.mediacontrol.scoreboard.data.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow

class ScoreboardApp : Application() {
    lateinit var controller: ScoreboardController
        private set
    lateinit var settings: SettingsStore
        private set

    /** Token of the live MediaSession while [com.mediacontrol.scoreboard.media.ScoreboardService] runs. */
    val sessionToken = MutableStateFlow<MediaSession.Token?>(null)

    override fun onCreate() {
        super.onCreate()
        val mainHandler = Handler(Looper.getMainLooper())
        controller = ScoreboardController(
            store = PrefsScoreStore(this),
            uptimeMillis = SystemClock::uptimeMillis,
            wallTimeMillis = System::currentTimeMillis,
            delayed = DelayedRunner { delayMs, action ->
                val runnable = Runnable(action)
                mainHandler.postDelayed(runnable, delayMs)
                Cancellable { mainHandler.removeCallbacks(runnable) }
            },
        )
        settings = SettingsStore(this)
    }
}

val Context.scoreboardApp: ScoreboardApp get() = applicationContext as ScoreboardApp
