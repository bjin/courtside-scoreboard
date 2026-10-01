package com.mediacontrol.scoreboard.media

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.mediacontrol.scoreboard.R
import com.mediacontrol.scoreboard.core.ActionKind
import com.mediacontrol.scoreboard.core.Feedback
import com.mediacontrol.scoreboard.core.RemoteInput
import com.mediacontrol.scoreboard.core.Side
import com.mediacontrol.scoreboard.core.watchTitle
import com.mediacontrol.scoreboard.scoreboardApp
import com.mediacontrol.scoreboard.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Owns the MediaSession that makes the scoreboard the phone's active "media player", so the COROS
 * watch (through the COROS app's notification access + MediaController) shows the score as the
 * track title and sends its buttons here.
 *
 * Runs as a `mediaPlayback` foreground service while the scoreboard is open, including when the
 * screen is off or another app is in front, so the session keeps receiving commands for a whole
 * match. The session always reports STATE_PLAYING: it stays at the top of the active sessions list
 * (what watch apps pick) and the system never treats it as idle and demotes the service.
 */
class ScoreboardService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val app get() = scoreboardApp
    private lateinit var session: MediaSession
    private lateinit var phoneVolume: PhoneVolumeWatcher
    private lateinit var silentPulse: SilentPulse
    private val volumeProvider = SessionVolumeProvider(
        onScreen = { app.screens.onScreen },
        adjustPhoneVolume = { direction ->
            // Not adjustSuggestedStreamVolume: that would route the step back to this session.
            runCatching {
                getSystemService(AudioManager::class.java)
                    .adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
            }.onFailure { Log.w(TAG, "Could not pass a volume key on to the phone", it) }
        },
        onInput = { description -> app.controller.onRemoteVolume(RemoteInput(description, caller = null)) },
    )

    /** Reported playback position starts far from zero so "seek back" never clamps at 0. */
    private val positionOrigin = SystemClock.elapsedRealtime()
    private var lastNotificationAt = 0L
    private val notifyRunnable = Runnable { postNotification() }

    /** What the watch shows, waiting to be published as one update; see [observe]. */
    private var pendingMetadata: Published? = null
    private val metadataRunnable = Runnable {
        pendingMetadata?.let(::publishMetadata)
        pendingMetadata = null
    }

    override fun onCreate() {
        super.onCreate()
        session = MediaSession(this, TAG).apply {
            setCallback(
                SessionCallback(
                    controller = app.controller,
                    callerPackage = ::callerPackage,
                    currentPositionMs = ::currentPositionMs,
                    onPositionConsumed = { publishPlaybackState(PlaybackState.STATE_PLAYING) },
                ),
                handler,
            )
            setSessionActivity(openScoreboardIntent())
        }
        phoneVolume = PhoneVolumeWatcher(this, onScreen = { app.screens.onScreen }) { description, restored ->
            val input = RemoteInput(description, caller = null)
            if (restored) app.controller.onRemoteVolume(input) else app.controller.onIgnoredRemote(input)
        }
        silentPulse = SilentPulse(handler)

        val settings = app.settings.settings.value
        applyVolumeMode(settings.volumeReset)
        publishMetadata(Published(currentTitle(), app.controller.feedback.value, armed = false))
        publishPlaybackState(PlaybackState.STATE_PLAYING)
        session.isActive = true
        goForeground()
        app.sessionToken.value = session.sessionToken

        phoneVolume.start()
        observe()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Every startForegroundService() call must be answered with startForeground().
        goForeground()
        if (intent?.action == ACTION_CLAIM) claimMediaFocus()
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // The scoreboard was swiped away from recents: the match is over.
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        handler.removeCallbacks(notifyRunnable)
        handler.removeCallbacks(metadataRunnable)
        phoneVolume.stop()
        silentPulse.release()
        app.sessionToken.value = null
        session.isActive = false
        session.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun observe() {
        val controller = app.controller
        scope.launch {
            combine(
                controller.state,
                controller.feedback,
                controller.resetArmedUntil,
                app.settings.settings.map { it.highlightServer }.distinctUntilChanged(),
            ) { state, feedback, armed, markServer ->
                Published(watchTitle(state, markServer), feedback, armed != null)
            }.collect { latest ->
                // One action changes score, feedback and arm state one after another on this thread.
                // Publishing once, right after the action, gives the watch a single consistent update
                // instead of a burst with intermediate subtitles.
                if (pendingMetadata == null) handler.post(metadataRunnable)
                pendingMetadata = latest
                scheduleNotification()
            }
        }
        scope.launch {
            app.settings.settings.map { it.volumeReset }.distinctUntilChanged().collect(::applyVolumeMode)
        }
        scope.launch {
            app.settings.settings.map { it.volumeReset && it.phoneVolumeFallback }.distinctUntilChanged().collect {
                phoneVolume.restoring = it
            }
        }
    }

    /**
     * Brings the session back to the top of the active-session list (a non-playing -> playing
     * transition is what moves it there) and optionally makes this app the media-button target by
     * playing a short inaudible clip (the system routes media keys to the app that last played audio).
     */
    private fun claimMediaFocus() {
        publishPlaybackState(PlaybackState.STATE_PAUSED)
        publishPlaybackState(PlaybackState.STATE_PLAYING)
        if (app.settings.settings.value.claimMediaButtons) silentPulse.play()
    }

    private fun applyVolumeMode(sessionVolume: Boolean) {
        if (sessionVolume) {
            session.setPlaybackToRemote(volumeProvider)
        } else {
            session.setPlaybackToLocal(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
        }
    }

    private fun publishMetadata(published: Published) {
        val status = statusLine(published.feedback, published.armed)
        session.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, published.title)
                .putString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE, published.title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, status)
                .putString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, status)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, getString(R.string.app_name))
                .build(),
        )
    }

    private fun publishPlaybackState(state: Int) {
        session.setPlaybackState(
            PlaybackState.Builder()
                .setActions(SUPPORTED_ACTIONS)
                .setState(state, currentPositionMs(), 1f, SystemClock.elapsedRealtime())
                .build(),
        )
    }

    private fun currentPositionMs(): Long =
        POSITION_BASE_MS + (SystemClock.elapsedRealtime() - positionOrigin)

    /** Package of the controller sending the current command; only valid inside session callbacks. */
    private fun callerPackage(): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching { session.currentControllerInfo.packageName }.getOrNull()
        } else {
            null
        }

    private fun currentTitle() = watchTitle(app.controller.state.value, app.settings.settings.value.highlightServer)

    private fun statusLine(feedback: Feedback?, armed: Boolean): String = getString(
        when {
            armed -> R.string.status_reset_armed
            feedback == null -> R.string.status_idle
            else -> when (feedback.kind) {
                ActionKind.POINT -> if (Side.LEFT in feedback.sides) R.string.status_point_left else R.string.status_point_right
                ActionKind.UNDO -> R.string.status_undo
                ActionKind.RESET -> R.string.status_reset
                ActionKind.SWAP -> R.string.status_swap
                ActionKind.RESET_ARMED -> R.string.status_reset_cancelled
                ActionKind.NOTHING_TO_UNDO -> R.string.status_nothing_to_undo
                ActionKind.MAX_SCORE -> R.string.status_max
            }
        },
    )

    // The type constant is inlined; ServiceCompat ignores it below API 29.
    @SuppressLint("InlinedApi")
    private fun goForeground() {
        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
            lastNotificationAt = SystemClock.uptimeMillis()
        } catch (e: RuntimeException) {
            // ForegroundServiceStartNotAllowedException and friends: keep the session usable while
            // the activity is visible instead of crashing the scoreboard.
            Log.w(TAG, "Could not enter the foreground", e)
        }
    }

    /**
     * The notification mirrors the score for older Android versions; on API 33+ the system media
     * controls read the session directly. Updates are coalesced because the system drops
     * notification updates above ~5 per second, which could leave a stale score behind.
     */
    private fun scheduleNotification() {
        handler.removeCallbacks(notifyRunnable)
        val wait = lastNotificationAt + NOTIFICATION_MIN_INTERVAL_MS - SystemClock.uptimeMillis()
        handler.postDelayed(notifyRunnable, wait.coerceAtLeast(0))
    }

    // Media-session notifications are exempt from the POST_NOTIFICATIONS runtime permission, so the
    // app needs (and requests) no notification permission.
    @SuppressLint("NotificationPermission")
    private fun postNotification() {
        lastNotificationAt = SystemClock.uptimeMillis()
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW).apply {
                    setShowBadge(false)
                },
            )
        }
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_scoreboard)
            .setContentTitle(currentTitle())
            .setContentText(getString(R.string.notification_text))
            .setContentIntent(openScoreboardIntent())
            .setOngoing(true)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setStyle(Notification.MediaStyle().setMediaSession(session.sessionToken))
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
                }
            }
            .build()
    }

    private fun openScoreboardIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Score title (with the serve mark), last action and reset arm state, as the watch shows them. */
    private data class Published(val title: String, val feedback: Feedback?, val armed: Boolean)

    companion object {
        private const val TAG = "ScoreboardService"
        private const val CHANNEL_ID = "scoreboard"
        private const val NOTIFICATION_ID = 1
        private const val NOTIFICATION_MIN_INTERVAL_MS = 300L
        private const val POSITION_BASE_MS = 3_600_000L
        private const val ACTION_CLAIM = "com.mediacontrol.scoreboard.action.CLAIM"

        /**
         * Only previous/next/play/pause are advertised: they are what the watch should show
         * (music mode). Seek/rewind/fast-forward still work if a controller sends them anyway.
         */
        private const val SUPPORTED_ACTIONS = PlaybackState.ACTION_PLAY or
            PlaybackState.ACTION_PAUSE or
            PlaybackState.ACTION_PLAY_PAUSE or
            PlaybackState.ACTION_SKIP_TO_PREVIOUS or
            PlaybackState.ACTION_SKIP_TO_NEXT

        /** Starts (or re-claims) the session; call while the scoreboard activity is visible. */
        fun start(context: Context) {
            val intent = Intent(context, ScoreboardService::class.java).setAction(ACTION_CLAIM)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: RuntimeException) {
                Log.w(TAG, "Could not start the scoreboard service", e)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ScoreboardService::class.java))
        }
    }
}
