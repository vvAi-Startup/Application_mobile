package com.vvai.calmwave

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerNotificationManager
import com.vvai.calmwave.ui.theme.CalmWaveTheme

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

    private var notificationManager: PlayerNotificationManager? = null
    private val player: ExoPlayer by lazy { PlaybackPlayerHolder.getPlayer(this) }

    override fun onCreate() {
        super.onCreate()
        createChannelIfNeeded()
        setupNotificationManager()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                player.pause()
                player.stop()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START, null -> {
                notificationManager?.setPlayer(player)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        notificationManager?.setPlayer(null)
        notificationManager = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun setupNotificationManager() {
        val openAppIntent = Intent(this, PlaylistActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            201,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        notificationManager = PlayerNotificationManager.Builder(
            this,
            NOTIFICATION_ID,
            CHANNEL_ID
        )
            .setMediaDescriptionAdapter(object : PlayerNotificationManager.MediaDescriptionAdapter {
                override fun getCurrentContentTitle(player: Player): CharSequence {
                    val item = player.currentMediaItem
                    val path = item?.localConfiguration?.uri?.path ?: item?.localConfiguration?.uri?.toString().orEmpty()
                    return path.substringAfterLast('/').ifBlank { "CalmWave" }
                }

                override fun createCurrentContentIntent(player: Player): PendingIntent = pendingIntent

                override fun getCurrentContentText(player: Player): CharSequence = "Reprodução em segundo plano"

                override fun getCurrentLargeIcon(
                    player: Player,
                    callback: PlayerNotificationManager.BitmapCallback
                ) = null
            })
            .setNotificationListener(object : PlayerNotificationManager.NotificationListener {
                override fun onNotificationPosted(notificationId: Int, notification: Notification, ongoing: Boolean) {
                    if (ongoing) {
                        startForeground(notificationId, notification)
                    } else {
                        stopForeground(STOP_FOREGROUND_DETACH)
                    }
                }

                override fun onNotificationCancelled(notificationId: Int, dismissedByUser: Boolean) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    if (dismissedByUser) {
                        player.pause()
                    }
                    stopSelf()
                }
            })
            .build()
            .apply {
                setUseNextAction(true)
                setUsePreviousAction(true)
                setUsePlayPauseActions(true)
                setUseFastForwardAction(false)
                setUseRewindAction(false)
                setPriority(NotificationManagerCompat.IMPORTANCE_LOW)
                setSmallIcon(R.mipmap.ic_launcher)
                setPlayer(player)
            }
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

@Composable
private fun PlaybackForegroundServiceNotificationPreview() {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "denoised_1711357782.wav",
                color = Color.White,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Reprodução em segundo plano",
                color = Color.White.copy(alpha = 0.75f),
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {}) {
                    Icon(
                        imageVector = Icons.Filled.SkipPrevious,
                        contentDescription = "Anterior",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
                IconButton(onClick = {}) {
                    Icon(
                        imageVector = Icons.Filled.Pause,
                        contentDescription = "Pausar",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                IconButton(onClick = {}) {
                    Icon(
                        imageVector = Icons.Filled.SkipNext,
                        contentDescription = "Próximo",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Preview(name = "Playback Notification", showBackground = true, widthDp = 393)
@Composable
private fun PlaybackForegroundServicePreview() {
    CalmWaveTheme {
        Surface(color = Color(0xFFECEFF1)) {
            PlaybackForegroundServiceNotificationPreview()
        }
    }
}
