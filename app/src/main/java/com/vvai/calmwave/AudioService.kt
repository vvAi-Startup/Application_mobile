package com.vvai.calmwave

import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.AudioTrack.MODE_STREAM
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.UUID

class AudioService {
    private val CHUNK_SIZE = 4096 // Tamanho do chunk em Bytes
    private val client = OkHttpClient()

    private var audioTrack: AudioTrack? = null
    private var isPlaying = false
    private var currentPlayingFile: String? = null
    private var currentPlaybackPosition: Long = 0
    private var totalPlaybackDuration: Long = 0
    private var seekPosition: Long = 0
    private var isPaused = false
    private var playbackJob: kotlinx.coroutines.Job? = null
    private var playbackSessionId: Long = 0L
    private val sampleRate = 44100
    private val channelConfig = AudioFormat.CHANNEL_OUT_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    fun startBluetoothSco(context: Context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.startBluetoothSco()
        audioManager.isBluetoothScoOn = true
        // Opcional: Adicionar um pequeno delay para a conexão Bluetooth se estabelecer
        // Thread.sleep(500) // Considere usar um Handler ou coroutine delay
    }

    fun stopBluetoothSco(context: Context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.stopBluetoothSco()
        audioManager.isBluetoothScoOn = false
    }

    private fun setupAudioTrack() {
        val bufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        audioTrack = AudioTrack(
            AudioManager.STREAM_MUSIC,
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize,
            MODE_STREAM
        )
        audioTrack?.play()
    }

    private fun stopAndReleaseAudioTrack() {
        try { audioTrack?.stop() } catch (_: Exception) {}
        try { audioTrack?.release() } catch (_: Exception) {}
        audioTrack = null
        isPlaying = false
        isPaused = false
        currentPlayingFile = null
        currentPlaybackPosition = 0
        totalPlaybackDuration = 0
        seekPosition = 0
    }

    suspend fun sendAndPlayWavFile(filePath: String, apiEndpoint: String) {
        withContext(Dispatchers.IO) {
            val file = File(filePath)
            if (!file.exists()) {
                println("Erro: Arquivo não encontrado em $filePath")
                return@withContext
            }
            val sessionId = UUID.randomUUID().toString()
            setupAudioTrack()

            try {
                FileInputStream(file).use { fis ->
                    val buffer = ByteArray(CHUNK_SIZE)
                    var bytesRead: Int
                    var chunkIndex = 0

                    while (fis.read(buffer, 0, CHUNK_SIZE).also { bytesRead = it } != -1) {
                        val chunkData = if (bytesRead < CHUNK_SIZE) {
                            buffer.copyOf(bytesRead)
                        } else {
                            buffer
                        }
                        val requestBody = RequestBody.create(null, chunkData)
                        val request = Request.Builder()
                            .url(apiEndpoint)
                            .addHeader("Content-Type", "audio/wav")
                            .addHeader("X-Session-ID", sessionId)
                            .addHeader("X-Chunk-Index", chunkIndex.toString())
                            .post(requestBody)
                            .build()

                        client.newCall(request).execute().use { response ->
                            if (response.isSuccessful) {
                                val processedAudioChunk = response.body?.bytes()
                                if (processedAudioChunk != null) {
                                    audioTrack?.write(processedAudioChunk, 0, processedAudioChunk.size)
                                    println("Chunk $chunkIndex reproduzido.")
                                }
                            } else {
                                println("Falha ao enviar chunk $chunkIndex: ${response.code}")
                            }
                        }
                        chunkIndex++
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
            } finally {
                stopAndReleaseAudioTrack()
            }
        }
    }

    suspend fun playLocalWavFile(filePath: String, startTimeMs: Long = 0): Boolean {
        return withContext(Dispatchers.IO) {
            playbackJob = currentCoroutineContext().job
            val file = File(filePath)
            if (!file.exists()) {
                println("Erro: Arquivo não encontrado em $filePath")
                return@withContext false
            }

            // Para qualquer reprodução atual antes de iniciar uma nova
            stopAndReleaseAudioTrack()

            setupAudioTrack()
            isPlaying = true
            isPaused = false
            currentPlayingFile = filePath
            currentPlaybackPosition = startTimeMs
            seekPosition = startTimeMs
            val localSessionId = System.nanoTime()
            playbackSessionId = localSessionId

            try {
                FileInputStream(file).use { fis ->
                    // Pula cabeçalho WAV de 44 bytes (compatível com o formato gravado pelo app)
                    val skipped = fis.skip(44)
                    if (skipped < 44) {
                        println("Erro: Cabeçalho WAV inválido para $filePath")
                        return@withContext false
                    }

                    // Calcula duração total baseada no tamanho do arquivo (menos cabeçalho)
                    val fileSize = file.length() - 44
                    totalPlaybackDuration = (fileSize * 1000L) / (sampleRate * 2) // 2 bytes por amostra (16-bit)

                    // Pula para a posição desejada (garante alinhamento de frame de 2 bytes e skip completo)
                    if (startTimeMs > 0) {
                        val bytesPerFrame = 2 // mono, 16-bit
                        val framesToSkip = (startTimeMs * sampleRate) / 1000L
                        val bytesToSkip = framesToSkip * bytesPerFrame
                        var remaining = bytesToSkip
                        var totalSkipped = 0L
                        while (remaining > 0) {
                            val skippedNow = fis.skip(remaining)
                            if (skippedNow <= 0) break
                            totalSkipped += skippedNow
                            remaining -= skippedNow
                        }
                        if (totalSkipped < bytesToSkip) {
                            println("Erro: Não foi possível pular para a posição desejada")
                            return@withContext false
                        }
                    }

                    val buffer = ByteArray(CHUNK_SIZE)
                    var bytesRead: Int = 0
                    var totalBytesRead: Long = (startTimeMs * sampleRate * 2) / 1000L
                    
                    while (isPlaying && !isPaused && fis.read(buffer).also { bytesRead = it } != -1) {
                        if (bytesRead > 0) {
                            audioTrack?.write(buffer, 0, bytesRead)
                            totalBytesRead += bytesRead
                            // Atualiza posição atual baseada nos bytes lidos
                            currentPlaybackPosition = (totalBytesRead * 1000L) / (sampleRate * 2)
                        }
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
                return@withContext false
            } finally {
                if (!isPaused && playbackSessionId == localSessionId) {
                    stopAndReleaseAudioTrack()
                }
            }
            return@withContext true
        }
    }

    fun pausePlayback() {
        isPaused = true
        isPlaying = false
        try {
            audioTrack?.pause()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun resumePlayback() {
        if (currentPlayingFile != null && isPaused) {
            isPaused = false
            isPlaying = true
            // Reinicia a reprodução do arquivo atual na posição atual
            try {
                playbackJob?.cancel()
            } catch (_: Exception) {}
            playbackJob = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                playLocalWavFile(currentPlayingFile!!, currentPlaybackPosition)
            }
        }
    }

    fun stopPlayback() {
        try {
            try { playbackJob?.cancel() } catch (_: Exception) {}
            playbackJob = null
            stopAndReleaseAudioTrack()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun isCurrentlyPlaying(): Boolean = isPlaying && !isPaused

    fun getCurrentPlayingFile(): String? = currentPlayingFile

    fun getCurrentPlaybackPosition(): Long = currentPlaybackPosition

    fun getTotalPlaybackDuration(): Long = totalPlaybackDuration

    fun getPlaybackProgress(): Float {
        return if (totalPlaybackDuration > 0) {
            currentPlaybackPosition.toFloat() / totalPlaybackDuration.toFloat()
        } else {
            0f
        }
    }

    fun seekTo(timeMs: Long) {
        try {
            if (timeMs < 0 || timeMs > totalPlaybackDuration) return
            seekPosition = timeMs
            currentPlaybackPosition = timeMs
            val file = currentPlayingFile ?: return
            val previousJob = playbackJob
            playbackJob = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                try {
                    // Sinaliza o loop atual para encerrar
                    isPlaying = false
                    try { previousJob?.cancel() } catch (_: Exception) {}
                    try { previousJob?.join() } catch (_: Exception) {}
                    try { audioTrack?.stop() } catch (_: Exception) {}
                    try { audioTrack?.release() } catch (_: Exception) {}
                    audioTrack = null
                    isPlaying = true
                    isPaused = false
                    playLocalWavFile(file, timeMs)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}