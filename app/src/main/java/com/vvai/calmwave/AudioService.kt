package com.vvai.calmwave

import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
            AudioTrack.MODE_STREAM
        )
        audioTrack?.play()
    }

    private fun stopAndReleaseAudioTrack() {
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
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
}