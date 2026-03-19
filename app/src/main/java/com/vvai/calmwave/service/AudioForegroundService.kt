package com.vvai.calmwave.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.vvai.calmwave.WavRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class AudioForegroundService : Service() {

    companion object {
        const val ACTION_START_RECORD = "ACTION_START_RECORD"
        const val ACTION_STOP_RECORD = "ACTION_STOP_RECORD"
        const val ACTION_START_PLAY = "ACTION_START_PLAY"
        const val ACTION_STOP_PLAY = "ACTION_STOP_PLAY"
        const val EXTRA_FILE_PATH = "EXTRA_FILE_PATH"

        private const val CHANNEL_ID = "audio_foreground_channel"
        private const val NOTIF_ID = 1001
    }

    private val recorder = WavRecorder()
    private var mediaPlayer: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val path = intent?.getStringExtra(EXTRA_FILE_PATH)

        when (action) {
            ACTION_START_RECORD -> if (path != null) startRecording(path)
            ACTION_STOP_RECORD -> stopRecording()
            ACTION_START_PLAY -> if (path != null) startPlaying(path)
            ACTION_STOP_PLAY -> stopPlaying()
        }

        return START_NOT_STICKY
    }

    private fun hasRecordPermission(): Boolean {
        val perm = android.Manifest.permission.RECORD_AUDIO
        return ActivityCompat.checkSelfPermission(this, perm) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CalmWave:AudioLock")
        wakeLock?.acquire(10 * 60 * 1000L)
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (_: Exception) {}
        wakeLock = null
    }

    private fun startRecording(filePath: String) {
        if (!hasRecordPermission()) {
            Log.e("AudioService", "RECORD_AUDIO permission not granted. Stopping service.")
            stopSelf()
            return
        }

        // Inicia em foreground com notificação
        val notif = buildNotification("Gravando áudio")
        startForeground(NOTIF_ID, notif)

        acquireWakeLock()

        scope.launch {
            try {
                recorder.setChunkCallback { chunk, index, overlap ->
                    // NÃO alterar a lógica de envio de chunks aqui — apenas logamos
                    Log.d("AudioService", "Chunk $index tamanho=${chunk.size} overlap=$overlap")
                }
                recorder.startRecording(filePath)
            } catch (e: Exception) {
                Log.e("AudioService", "Erro na gravação", e)
                stopRecording()
            }
        }
    }

    private fun stopRecording() {
        scope.launch {
            try {
                recorder.stopRecording()
            } catch (e: Exception) {
                Log.w("AudioService", "Erro ao parar gravação", e)
            } finally {
                releaseWakeLock()
                try { stopForeground(false) } catch (_: Exception) {}
                stopSelf()
            }
        }
    }

    private fun startPlaying(path: String) {
        // start playback in foreground and keep CPU
        try {
            // Stop any ongoing recording to avoid mic conflict
            recorder.stopRecording()
        } catch (_: Exception) {}

        val notif = buildNotification("Reproduzindo áudio")
        startForeground(NOTIF_ID, notif)

        acquireWakeLock()

        // initialize MediaPlayer
        try {
            stopPlaying()
            val mp = MediaPlayer()
            mediaPlayer = mp
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            mp.setDataSource(path)
            // Keep CPU awake while playing
            mp.setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
            mp.setOnCompletionListener {
                stopPlaying()
            }
            mp.setOnErrorListener { _, what, extra ->
                Log.e("AudioService", "MediaPlayer error what=$what extra=$extra")
                stopPlaying()
                true
            }
            mp.prepareAsync()
            mp.setOnPreparedListener { it.start() }
        } catch (e: Exception) {
            Log.e("AudioService", "Erro ao iniciar reprodução", e)
            stopPlaying()
        }
    }

    private fun stopPlaying() {
        try {
            mediaPlayer?.let { mp ->
                try { if (mp.isPlaying) mp.stop() } catch (_: Exception) {}
                try { mp.reset() } catch (_: Exception) {}
                try { mp.release() } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            Log.w("AudioService", "Erro ao liberar MediaPlayer", e)
        } finally {
            mediaPlayer = null
            releaseWakeLock()
            try { stopForeground(false) } catch (_: Exception) {}
            // don't always stopSelf: only if nothing else running; for simplicity stop
            stopSelf()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(CHANNEL_ID, "Áudio", NotificationManager.IMPORTANCE_LOW)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Calm Wave")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
        return builder.build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        try { recorder.stopRecording() } catch (_: Exception) {}
        try { stopPlaying() } catch (_: Exception) {}
        releaseWakeLock()
    }
}

