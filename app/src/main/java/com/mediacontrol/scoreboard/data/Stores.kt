package com.mediacontrol.scoreboard.data

import android.content.Context
import androidx.core.content.edit
import com.mediacontrol.scoreboard.core.ScoreState
import com.mediacontrol.scoreboard.core.ScoreStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persists the history (the score is replayed from it). `apply()` updates the in-memory map at once
 * and writes the file on a background thread, so a change never delays the next frame; Android
 * flushes pending writes when the activity stops or the service finishes a command.
 */
class PrefsScoreStore(context: Context) : ScoreStore {
    private val prefs = context.getSharedPreferences("score", Context.MODE_PRIVATE)

    override fun load(): ScoreState = ScoreState.decode(
        encodedHistory = prefs.getString(KEY_HISTORY, "").orEmpty(),
        swapped = prefs.getBoolean(KEY_SWAPPED, false),
    ) ?: ScoreState.EMPTY

    override fun save(state: ScoreState) {
        prefs.edit {
            putString(KEY_HISTORY, state.encodeHistory())
            putBoolean(KEY_SWAPPED, state.swapped)
        }
    }

    private companion object {
        const val KEY_HISTORY = "history"
        const val KEY_SWAPPED = "swapped"
    }
}

data class Settings(
    val darkTheme: Boolean = true,
    val keepScreenOn: Boolean = true,
    val maxBrightness: Boolean = true,
    val showOnLockScreen: Boolean = true,
    val haptics: Boolean = true,
    /** Tint the half that won the last point: in badminton the rally winner serves next. */
    val highlightServer: Boolean = true,
    /** Expose remote (session-only) volume so watch volume presses arm/confirm a reset. */
    val volumeReset: Boolean = true,
    /**
     * Also treat changes of the phone's real media volume as reset presses and put the volume
     * back at once: covers watch apps that set the phone volume directly instead of the session's.
     */
    val phoneVolumeFallback: Boolean = true,
    /** Play a short inaudible clip so media keys (headsets, BT remotes) are routed to this app. */
    val claimMediaButtons: Boolean = true,
)

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    fun update(transform: (Settings) -> Settings) {
        val updated = transform(_settings.value)
        if (updated == _settings.value) return
        _settings.value = updated
        prefs.edit {
            putBoolean(DARK, updated.darkTheme)
            putBoolean(KEEP_ON, updated.keepScreenOn)
            putBoolean(BRIGHT, updated.maxBrightness)
            putBoolean(LOCK, updated.showOnLockScreen)
            putBoolean(HAPTICS, updated.haptics)
            putBoolean(HIGHLIGHT_SERVER, updated.highlightServer)
            putBoolean(VOLUME_RESET, updated.volumeReset)
            putBoolean(PHONE_VOLUME, updated.phoneVolumeFallback)
            putBoolean(CLAIM_KEYS, updated.claimMediaButtons)
        }
    }

    private fun read(): Settings {
        val d = Settings()
        return Settings(
            darkTheme = prefs.getBoolean(DARK, d.darkTheme),
            keepScreenOn = prefs.getBoolean(KEEP_ON, d.keepScreenOn),
            maxBrightness = prefs.getBoolean(BRIGHT, d.maxBrightness),
            showOnLockScreen = prefs.getBoolean(LOCK, d.showOnLockScreen),
            haptics = prefs.getBoolean(HAPTICS, d.haptics),
            highlightServer = prefs.getBoolean(HIGHLIGHT_SERVER, d.highlightServer),
            volumeReset = prefs.getBoolean(VOLUME_RESET, d.volumeReset),
            phoneVolumeFallback = prefs.getBoolean(PHONE_VOLUME, d.phoneVolumeFallback),
            claimMediaButtons = prefs.getBoolean(CLAIM_KEYS, d.claimMediaButtons),
        )
    }

    private companion object {
        const val DARK = "dark_theme"
        const val KEEP_ON = "keep_screen_on"
        const val BRIGHT = "max_brightness"
        const val LOCK = "show_on_lock_screen"
        const val HAPTICS = "haptics"
        const val HIGHLIGHT_SERVER = "highlight_server"
        const val VOLUME_RESET = "volume_reset"
        const val PHONE_VOLUME = "phone_volume_fallback"
        const val CLAIM_KEYS = "claim_media_buttons"
    }
}
