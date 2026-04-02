package com.vvai.calmwave

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

object PlaybackPlayerHolder {
    @Volatile
    private var player: ExoPlayer? = null

    fun getPlayer(context: Context): ExoPlayer {
        return player ?: synchronized(this) {
            player ?: ExoPlayer.Builder(context.applicationContext).build().also {
                it.repeatMode = Player.REPEAT_MODE_OFF
                it.playWhenReady = false
                player = it
            }
        }
    }

    fun currentMediaPath(): String? {
        val item = player?.currentMediaItem ?: return null
        return item.localConfiguration?.uri?.path ?: item.localConfiguration?.uri?.toString()
    }

    fun setQueue(context: Context, files: List<String>, startIndex: Int = 0) {
        val exoPlayer = getPlayer(context)
        val mediaItems = files.map { filePath ->
            MediaItem.Builder()
                .setUri(filePath)
                .setMediaId(filePath)
                .build()
        }
        exoPlayer.setMediaItems(mediaItems, startIndex.coerceAtLeast(0), 0L)
        exoPlayer.prepare()
    }
}

class PlaybackForegroundService : Service() {

    private val player: ExoPlayer by lazy { PlaybackPlayerHolder.getPlayer(this) }
    private var startedInForeground = false
    private val handler = Handler(Looper.getMainLooper())
    private val progressUpdater = object : Runnable {
        override fun run() {
            if (player.mediaItemCount > 0) {
                updateNotification()
                handler.postDelayed(this, 1000)
            }
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            updateNotification()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updateNotification()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (startedInForeground && playbackState == Player.STATE_IDLE && player.mediaItemCount == 0) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            } else {
                updateNotification()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannelIfNeeded()
        player.addListener(playerListener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PREVIOUS -> player.seekToPreviousMediaItem()
            ACTION_NEXT -> player.seekToNextMediaItem()
            ACTION_PLAY_PAUSE -> {
                if (player.isPlaying) {
                    player.pause()
                } else {
                    player.play()
                }
            }
            ACTION_STOP -> {
                player.pause()
                player.stop()
                player.clearMediaItems()
                handler.removeCallbacks(progressUpdater)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }
        updateNotification()
        return START_STICKY
    }

    override fun onDestroy() {
        player.removeListener(playerListener)
        handler.removeCallbacks(progressUpdater)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val openAppIntent = Intent(this, PlaylistActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            201,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val previousIntent = PendingIntent.getService(
            this,
            211,
            Intent(this, PlaybackForegroundService::class.java).setAction(ACTION_PREVIOUS),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val playPauseIntent = PendingIntent.getService(
            this,
            212,
            Intent(this, PlaybackForegroundService::class.java).setAction(ACTION_PLAY_PAUSE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val nextIntent = PendingIntent.getService(
            this,
            213,
            Intent(this, PlaybackForegroundService::class.java).setAction(ACTION_NEXT),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this,
            214,
            Intent(this, PlaybackForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val path = PlaybackPlayerHolder.currentMediaPath().orEmpty()
        val title = path.substringAfterLast('/').ifBlank { "CalmWave" }
        val durationMs = player.duration.takeIf { it > 0 } ?: 0L
        val positionMs = player.currentPosition.coerceAtLeast(0L)
        val progressMax = durationMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val progressValue = positionMs.coerceAtMost(durationMs).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(
                if (durationMs > 0) {
                    "${formatTime(positionMs)} / ${formatTime(durationMs)}"
                } else {
                    "Reprodução em segundo plano"
                }
            )
            .setContentIntent(contentPendingIntent)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .setOngoing(player.isPlaying)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setProgress(progressMax, progressValue, durationMs <= 0)
            .addAction(android.R.drawable.ic_media_previous, "Anterior", previousIntent)
            .addAction(
                if (player.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (player.isPlaying) "Pausar" else "Tocar",
                playPauseIntent
            )
            .addAction(android.R.drawable.ic_media_next, "Próximo", nextIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Parar", stopIntent)
            .build()
    }

    private fun updateNotification() {
        if (player.mediaItemCount == 0) {
            handler.removeCallbacks(progressUpdater)
            if (startedInForeground) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                startedInForeground = false
            }
            return
        }

        val notification = buildNotification()
        if (!startedInForeground) {
            startForeground(NOTIFICATION_ID, notification)
            startedInForeground = true
        } else if (canPostNotifications()) {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
        }

        handler.removeCallbacks(progressUpdater)
        if (player.isPlaying) {
            handler.post(progressUpdater)
        }
    }

    private fun formatTime(ms: Long): String {
        val totalSeconds = (ms / 1000).coerceAtLeast(0)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    private fun canPostNotifications(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
    }

    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Reprodução em segundo plano",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Controles de áudio em reprodução"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "playback_foreground_channel"
        private const val NOTIFICATION_ID = 2101
        private const val ACTION_START = "com.vvai.calmwave.action.PLAYBACK_START"
        private const val ACTION_STOP = "com.vvai.calmwave.action.PLAYBACK_STOP"
        private const val ACTION_PLAY_PAUSE = "com.vvai.calmwave.action.PLAYBACK_PLAY_PAUSE"
        private const val ACTION_PREVIOUS = "com.vvai.calmwave.action.PLAYBACK_PREVIOUS"
        private const val ACTION_NEXT = "com.vvai.calmwave.action.PLAYBACK_NEXT"

        fun start(context: Context) {
            val intent = Intent(context, PlaybackForegroundService::class.java).apply {
                action = ACTION_START
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, PlaybackForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
