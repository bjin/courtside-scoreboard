// Copyright (c) 2026 Bin Jin <bjin@protonmail.com>
// SPDX-License-Identifier: MIT

package io.github.bjin.courtside

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.media.session.MediaSession
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import io.github.bjin.courtside.core.Cancellable
import io.github.bjin.courtside.core.DelayedRunner
import io.github.bjin.courtside.core.ScreenRegistry
import io.github.bjin.courtside.core.ScoreboardController
import io.github.bjin.courtside.data.PrefsScoreStore
import io.github.bjin.courtside.data.LanguageStore
import io.github.bjin.courtside.data.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow

class ScoreboardApp : Application() {
    lateinit var controller: ScoreboardController
        private set
    lateinit var settings: SettingsStore
        private set
    lateinit var languages: LanguageStore
        private set

    /** Token of the live MediaSession while [io.github.bjin.courtside.media.ScoreboardService] runs. */
    val sessionToken = MutableStateFlow<MediaSession.Token?>(null)

    /** Open scoreboard screens; decides when the session service may stop. Main thread only. */
    val screens = ScreenRegistry()

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
        languages = LanguageStore(this)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        languages.refresh()
    }
}

val Context.scoreboardApp: ScoreboardApp get() = applicationContext as ScoreboardApp
