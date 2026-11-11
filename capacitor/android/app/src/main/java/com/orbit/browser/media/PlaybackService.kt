package com.orbit.browser.media

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.orbit.browser.R

/**
 * Module 3 — lock-screen / background playback.
 *
 * The WebView itself plays the audio; this service exists to:
 *   • keep the process alive with a `mediaPlayback` foreground service,
 *   • own a MediaSession so lockscreen + Android 13 media card controls
 *     work (play/pause routes back into the page via PlaybackCoordinator),
 *   • hold audio focus (other apps can duck/pause us, and we honour them),
 *   • hold a partial wake lock while playing so MSE audio keeps decoding
 *     with the screen off.
 *
 * Started by PlaybackCoordinator when page media actually starts — never
 * speculatively, so there is no notification noise for plain browsing.
 */
class PlaybackService : Service() {

    private lateinit var session: MediaSessionCompat
    private lateinit var audioManager: AudioManager
    private var focusRequest: AudioFocusRequest? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val main = Handler(Looper.getMainLooper())

    private var playing = false
    private var title = ""
    private var url = ""

    /** Grace period after pause before the service lets itself be killed. */
    private val resumeChecker = Runnable {
        if (!playing) stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        PlaybackCoordinator.attachService(this)
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

        session = MediaSessionCompat(this, "OrbitPlayback").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    PlaybackCoordinator.dispatchPlayPause(true)
                    setPlaying(true)
                }

                override fun onPause() {
                    PlaybackCoordinator.dispatchPlayPause(false)
                    setPlaying(false)
                    scheduleResumeCheck()
                }

                override fun onStop() {
                    PlaybackCoordinator.dispatchPlayPause(false)
                    stopSelf()
                }
            })
            isActive = true
        }
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Notification/media-card button intents come in here too.
        when (intent?.action) {
            ACTION_PLAY -> {
                PlaybackCoordinator.dispatchPlayPause(true)
                setPlaying(true)
                return START_NOT_STICKY
            }
            ACTION_PAUSE -> {
                PlaybackCoordinator.dispatchPlayPause(false)
                setPlaying(false)
                scheduleResumeCheck()
                return START_NOT_STICKY
            }
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }

        intent?.getStringExtra(EXTRA_TITLE)?.let { if (it.isNotBlank()) title = it }
        intent?.getStringExtra(EXTRA_URL)?.let { if (it.isNotBlank()) url = it }

        startInForeground()
        requestFocusAndWake()
        setPlaying(true)
        return START_NOT_STICKY
    }

    /* ------------------------------ coordinator ----------------------------- */

    /** New media started (or page changed) — refresh metadata, stay alive. */
    fun updateMedia(newTitle: String, newUrl: String) {
        title = newTitle
        url = newUrl
        setPlaying(true)
    }
    /** Page media paused/ended — keep controls for a grace period. */
    fun onMediaPaused() {
        setPlaying(false)
        scheduleResumeCheck()
    }

    /** Main frame navigated: leaving the origin kills the old media. */
    fun onPageNavigated(newUrl: String) {
        if (url.isBlank() || originOf(newUrl) != originOf(url)) {
            PlaybackCoordinator.dispatchPlayPause(false)
            stopSelf()
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Swipe from recents: keep playing if audio is active (Chrome parity),
        // die immediately when nothing plays.
        if (!playing) stopSelf()
    }

    override fun onDestroy() {
        main.removeCallbacks(resumeChecker)
        abandonFocusAndWake()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        runCatching { session.isActive = false }
        session.release()
        PlaybackCoordinator.attachService(null)
        super.onDestroy()
    }

    /* -------------------------------- internals ------------------------------ */

    private fun scheduleResumeCheck() {
        main.removeCallbacks(resumeChecker)
        main.postDelayed(resumeChecker, RESUME_GRACE_MS)
    }

    private fun setPlaying(value: Boolean) {
        playing = value
        if (value) main.removeCallbacks(resumeChecker)

        val state = if (value) {
            PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE or
                        PlaybackStateCompat.ACTION_STOP
                )
                .setState(PlaybackStateCompat.STATE_PLAYING, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1f)
                .build()
        } else {
            PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE or
                        PlaybackStateCompat.ACTION_STOP
                )
                .setState(PlaybackStateCompat.STATE_PAUSED, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 0f)
                .build()
        }
        session.setPlaybackState(state)
        session.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title.ifBlank { "Orbit Browser" })
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "Orbit Browser")
                .build()
        )
        notifyCompat(buildNotification())
    }

    private fun requestFocusAndWake() {
        if (focusRequest == null) {
            focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setOnAudioFocusChangeListener { change ->
                    when (change) {
                        AudioManager.AUDIOFOCUS_LOSS,
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                            PlaybackCoordinator.dispatchPlayPause(false)
                            setPlaying(false)
                            scheduleResumeCheck()
                        }
                        AudioManager.AUDIOFOCUS_GAIN -> {
                            PlaybackCoordinator.dispatchPlayPause(true)
                            setPlaying(true)
                        }
                    }
                }
                .build()
        }
        runCatching { audioManager.requestAudioFocus(focusRequest!!) }

        if (wakeLock == null) {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Orbit:Playback")
        }
        runCatching {
            wakeLock?.acquire(WAKE_LOCK_TIMEOUT_MS)
        }
    }

    private fun abandonFocusAndWake() {
        focusRequest?.let { runCatching { audioManager.abandonAudioFocusRequest(it) } }
        runCatching {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        }
    }

    /* ------------------------------ notification ----------------------------- */

    private fun startInForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIF_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val launch = packageManager
            .getLaunchIntentForPackage(packageName)
            ?.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val contentIntent: PendingIntent? = launch?.let {
            PendingIntent.getActivity(
                this,
                0,
                it,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }

        val toggleIntent = Intent(this, PlaybackService::class.java)
            .setAction(if (playing) ACTION_PAUSE else ACTION_PLAY)
        val toggleIcon = if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val toggleLabel = if (playing) "Pause" else "Play"

        val style = androidx.media.app.NotificationCompat.MediaStyle()
            .setMediaSession(session.sessionToken)
            .setShowActionsInCompactView(0, 1)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notif_orbit)
            .setContentTitle(title.ifBlank { "Orbit Browser" })
            .setContentText("Playing in background")
            .setContentIntent(contentIntent)
            .setOngoing(playing)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(toggleIcon, toggleLabel, servicePendingIntent(toggleIntent))
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", servicePendingIntent(stopIntent()))
            .setStyle(style)
            .build()
    }

    private fun stopIntent(): Intent =
        Intent(this, PlaybackService::class.java).setAction(ACTION_STOP)

    private fun servicePendingIntent(intent: Intent): PendingIntent =
        PendingIntent.getService(this, intent.action.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE)

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Background playback",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Controls for media playing in Orbit"
            setShowBadge(false)
        }
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    private fun notifyCompat(notification: Notification) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, notification)
    }

    private fun originOf(raw: String): String? = runCatching {
        java.net.URI(raw).let { u -> "${u.scheme}://${u.host}" }
    }.getOrNull()

    companion object {
        const val EXTRA_TITLE = "orbit.title"
        const val EXTRA_URL = "orbit.url"
        const val CHANNEL_ID = "orbit_playback"
        const val NOTIF_ID = 41
        const val ACTION_PLAY = "com.orbit.browser.media.PLAY"
        const val ACTION_PAUSE = "com.orbit.browser.media.PAUSE"
        const val ACTION_STOP = "com.orbit.browser.media.STOP"
        private const val RESUME_GRACE_MS = 45_000L
        private const val WAKE_LOCK_TIMEOUT_MS = 6 * 60 * 60 * 1000L
    }
}
