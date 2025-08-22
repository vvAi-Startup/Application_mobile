package com.vvai.calmwave

import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.AudioTrack.MODE_STREAM
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.UUID

class AudioService {
    private val CHUNK_SIZE = 4096
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private var audioTrack: AudioTrack? = null
    @Volatile private var isPlaying = false
    @Volatile private var isPaused = false
    private var currentPlayingFile: String? = null
    private var currentPlaybackPosition: Long = 0
    private var totalPlaybackDuration: Long = 0
    private var audioManager: AudioManager? = null

    // Constantes de áudio
    private val sampleRate = 44100
    private val channelConfig = AudioFormat.CHANNEL_OUT_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    // Inicialização da classe com o contexto da aplicação
    fun init(context: Context) {
        if (audioManager == null) {
            audioManager = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        }
    }

    fun startBluetoothSco() {
        audioManager?.let { manager ->
            manager.startBluetoothSco()
            manager.isBluetoothScoOn = true
        }
    }

    fun stopBluetoothSco() {
        audioManager?.let { manager ->
            manager.stopBluetoothSco()
            manager.isBluetoothScoOn = false
        }
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
        
        if (audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
            println("Erro: Falha na inicialização do AudioTrack.")
            stopAndReleaseAudioTrack()
            return
        }
        audioTrack?.play()
    }

    private fun stopAndReleaseAudioTrack() {
        try { audioTrack?.stop() } catch (e: IllegalStateException) {
            e.printStackTrace()
        }
        audioTrack?.release()
        audioTrack = null
        isPlaying = false
        isPaused = false
        currentPlayingFile = null
        currentPlaybackPosition = 0
        totalPlaybackDuration = 0
    }

    suspend fun sendAndPlayWavFile(filePath: String, apiEndpoint: String) = withContext(Dispatchers.IO) {
        val file = File(filePath)
        if (!file.exists()) {
            println("Erro: Arquivo não encontrado em $filePath")
            return@withContext
        }
        
        stopAndReleaseAudioTrack()
        setupAudioTrack()
        
        val sessionId = UUID.randomUUID().toString()
        val chunkIntervalMs = 5000L // 5 segundos
        var lastChunkTime = 0L

        try {
            FileInputStream(file).use { fis ->
                val buffer = ByteArray(CHUNK_SIZE)
                var bytesRead: Int
                var chunkIndex = 0
                var accumulatedData = ByteArray(0)

                while (fis.read(buffer, 0, CHUNK_SIZE).also { bytesRead = it } != -1 && coroutineContext.isActive) {
                    val chunkData = if (bytesRead < CHUNK_SIZE) buffer.copyOf(bytesRead) else buffer
                    
                    // Acumula os dados
                    accumulatedData += chunkData
                    
                    val currentTime = System.currentTimeMillis()
                    
                    // Envia chunk a cada 5 segundos
                    if (currentTime - lastChunkTime >= chunkIntervalMs) {
                        if (accumulatedData.isNotEmpty()) {
                            val requestBody = accumulatedData.toRequestBody(null)
                            val request = Request.Builder()
                                .url(apiEndpoint)
                                .addHeader("Content-Type", "audio/wav")
                                .addHeader("X-Session-ID", sessionId)
                                .addHeader("X-Chunk-Index", chunkIndex.toString())
                                .addHeader("X-Chunk-Timestamp", currentTime.toString())
                                .post(requestBody)
                                .build()

                            try {
                                client.newCall(request).execute().use { response ->
                                    if (response.isSuccessful) {
                                        val processedAudioChunk = response.body?.bytes()
                                        if (processedAudioChunk != null) {
                                            audioTrack?.write(processedAudioChunk, 0, processedAudioChunk.size)
                                            println("Chunk $chunkIndex enviado e reproduzido com sucesso.")
                                        }
                                    } else {
                                        println("Falha ao enviar chunk $chunkIndex: ${response.code}")
                                    }
                                }
                            } catch (e: Exception) {
                                println("Erro ao enviar chunk $chunkIndex: ${e.message}")
                            }
                            
                            chunkIndex++
                            lastChunkTime = currentTime
                            accumulatedData = ByteArray(0) // Limpa os dados acumulados
                        }
                    }
                    
                    // Pequeno delay para não sobrecarregar o processamento
                    delay(10)
                }
                
                // Envia qualquer dado restante
                if (accumulatedData.isNotEmpty()) {
                    val requestBody = accumulatedData.toRequestBody(null)
                    val request = Request.Builder()
                        .url(apiEndpoint)
                        .addHeader("Content-Type", "audio/wav")
                        .addHeader("X-Session-ID", sessionId)
                        .addHeader("X-Chunk-Index", chunkIndex.toString())
                        .addHeader("X-Chunk-Timestamp", System.currentTimeMillis().toString())
                        .addHeader("X-Chunk-Final", "true")
                        .post(requestBody)
                        .build()

                    try {
                        client.newCall(request).execute().use { response ->
                            if (response.isSuccessful) {
                                val processedAudioChunk = response.body?.bytes()
                                if (processedAudioChunk != null) {
                                    audioTrack?.write(processedAudioChunk, 0, processedAudioChunk.size)
                                    println("Chunk final $chunkIndex enviado e reproduzido com sucesso.")
                                }
                            } else {
                                println("Falha ao enviar chunk final $chunkIndex: ${response.code}")
                            }
                        }
                    } catch (e: Exception) {
                        println("Erro ao enviar chunk final $chunkIndex: ${e.message}")
                    }
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            stopAndReleaseAudioTrack()
        }
    }

    suspend fun playLocalWavFile(filePath: String, startTimeMs: Long = 0) = withContext(Dispatchers.IO) {
        val file = File(filePath)
        if (!file.exists()) {
            println("Erro: Arquivo não encontrado em $filePath")
            return@withContext false
        }
        
        // Verifica se o arquivo mudou
        if (currentPlayingFile != filePath) {
            stopAndReleaseAudioTrack()
            setupAudioTrack()
        }
        
        isPlaying = true
        isPaused = false
        currentPlayingFile = filePath
        currentPlaybackPosition = startTimeMs

        try {
            FileInputStream(file).use { fis ->
                fis.skip(44) // Pula o cabeçalho WAV
                
                val fileSize = file.length() - 44
                totalPlaybackDuration = (fileSize * 1000L) / (sampleRate * 2)

                if (startTimeMs > 0) {
                    val bytesToSkip = (startTimeMs * sampleRate * 2) / 1000L
                    var remaining = bytesToSkip
                    while (remaining > 0) {
                        val skippedNow = fis.skip(remaining)
                        if (skippedNow <= 0) break
                        remaining -= skippedNow
                    }
                }
                
                val buffer = ByteArray(CHUNK_SIZE)
                var totalBytesRead: Long = (startTimeMs * sampleRate * 2) / 1000L
                
                while (isPlaying && !isPaused && coroutineContext.isActive) {
                    val bytesRead = fis.read(buffer)
                    if (bytesRead == -1) break
                    
                    if (bytesRead > 0) {
                        audioTrack?.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                        currentPlaybackPosition = (totalBytesRead * 1000L) / (sampleRate * 2)
                    }
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
            return@withContext false
        } finally {
            if (!isPaused) {
                stopAndReleaseAudioTrack()
            }
        }
        return@withContext true
    }

    fun pausePlayback() {
        isPaused = true
        isPlaying = false
        try { audioTrack?.pause() } catch (e: IllegalStateException) { e.printStackTrace() }
    }

    fun resumePlayback(coroutineScope: CoroutineScope) {
        if (currentPlayingFile != null && isPaused) {
            isPaused = false
            isPlaying = true
            coroutineScope.launch {
                playLocalWavFile(currentPlayingFile!!, currentPlaybackPosition)
            }
        }
    }

    fun stopPlayback() {
        isPlaying = false
        isPaused = false
        stopAndReleaseAudioTrack()
    }

    fun isCurrentlyPlaying(): Boolean = isPlaying && !isPaused
    fun isCurrentlyPaused(): Boolean = isPaused
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

    fun seekTo(timeMs: Long, coroutineScope: CoroutineScope) {
        if (timeMs < 0 || timeMs > totalPlaybackDuration || currentPlayingFile == null) return
        
        try {
            // Armazena o estado atual de pausa
            val wasPaused = isPaused
            
            // Para a reprodução atual
            isPlaying = false
            isPaused = false
            
            // Atualiza a posição atual imediatamente
            currentPlaybackPosition = timeMs
            
            coroutineScope.launch {
                try {
                    delay(50) // Pequeno delay para garantir que o estado seja atualizado
                    
                    if (wasPaused) {
                        // Se estava pausado, mantém pausado na nova posição
                        isPaused = true
                        isPlaying = false
                    } else {
                        // Se estava reproduzindo, inicia a reprodução na nova posição
                        playLocalWavFile(currentPlayingFile!!, timeMs)
                    }
                } catch (e: Exception) {
                    println("Erro durante seek: ${e.message}")
                    e.printStackTrace()
                }
            }
        } catch (e: Exception) {
            println("Erro ao iniciar seek: ${e.message}")
            e.printStackTrace()
        }
    }

    // Método para enviar chunks em tempo real durante a gravação
    suspend fun sendChunkToAPI(chunkData: ByteArray, sessionId: String, chunkIndex: Int, apiEndpoint: String) = withContext(Dispatchers.IO) {
        try {
            println("Enviando chunk $chunkIndex para API: $apiEndpoint")
            println("Tamanho do chunk: ${chunkData.size} bytes")
            println("Session ID: $sessionId")
            
            // Cria um cabeçalho WAV simples para o chunk
            val wavHeader = createWavHeader(chunkData.size)
            val fullWavData = wavHeader + chunkData
            
            val requestBody = fullWavData.toRequestBody("audio/wav".toMediaType())
            
            val request = Request.Builder()
                .url(apiEndpoint)
                .addHeader("Content-Type", "audio/wav")
                .addHeader("X-Session-ID", sessionId)
                .addHeader("X-Chunk-Index", chunkIndex.toString())
                .addHeader("X-Chunk-Timestamp", System.currentTimeMillis().toString())
                .addHeader("X-Chunk-Size", chunkData.size.toString())
                .post(requestBody)
                .build()

            println("Fazendo requisição para: ${request.url}")
            println("Headers: ${request.headers}")

            client.newCall(request).execute().use { response ->
                println("Resposta da API - Código: ${response.code}")
                println("Resposta da API - Mensagem: ${response.message}")
                
                if (response.isSuccessful) {
                    val processedAudioChunk = response.body?.bytes()
                    if (processedAudioChunk != null) {
                        println("Chunk $chunkIndex processado com sucesso. Tamanho da resposta: ${processedAudioChunk.size} bytes")
                        // Reproduz o áudio processado
                        audioTrack?.write(processedAudioChunk, 0, processedAudioChunk.size)
                    } else {
                        println("Chunk $chunkIndex: Resposta vazia da API")
                    }
                } else {
                    val errorBody = response.body?.string()
                    println("Falha ao enviar chunk $chunkIndex: ${response.code} - $errorBody")
                }
            }
        } catch (e: Exception) {
            println("Erro ao enviar chunk $chunkIndex: ${e.message}")
            e.printStackTrace()
        }
    }
    
    // Cria um cabeçalho WAV simples para chunks
    private fun createWavHeader(dataSize: Int): ByteArray {
        val header = ByteArray(44)
        val buffer = java.nio.ByteBuffer.wrap(header).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        
        buffer.putInt(0x46464952) // "RIFF"
        buffer.putInt(36 + dataSize) // Tamanho do arquivo
        buffer.putInt(0x45564157) // "WAVE"
        buffer.putInt(0x20746d66) // "fmt "
        buffer.putInt(16) // Tamanho do subchunk 1
        buffer.putShort(1) // Formato de áudio (1 = PCM)
        buffer.putShort(1) // Número de canais (mono)
        buffer.putInt(44100) // Taxa de amostragem
        buffer.putInt(44100 * 2) // Byte rate
        buffer.putShort(2) // Block align
        buffer.putShort(16) // Bits por amostra
        buffer.putInt(0x61746164) // "data"
        buffer.putInt(dataSize) // Tamanho dos dados
        
        return header
    }
    
    // Método para testar a conectividade com a API
    suspend fun testAPIConnection(apiEndpoint: String) = withContext(Dispatchers.IO) {
        try {
            println("Testando conexão com API: $apiEndpoint")
            
            val testData = ByteArray(1024) { 0 } // Dados de teste vazios
            val wavHeader = createWavHeader(testData.size)
            val fullData = wavHeader + testData
            
            val requestBody = fullData.toRequestBody("audio/wav".toMediaType())
            
            val request = Request.Builder()
                .url(apiEndpoint)
                .addHeader("Content-Type", "audio/wav")
                .addHeader("X-Session-ID", "test-session")
                .addHeader("X-Chunk-Index", "0")
                .addHeader("X-Chunk-Timestamp", System.currentTimeMillis().toString())
                .addHeader("X-Chunk-Size", testData.size.toString())
                .addHeader("X-Test-Mode", "true")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                println("Teste API - Código: ${response.code}")
                println("Teste API - Mensagem: ${response.message}")
                println("Teste API - Headers: ${response.headers}")
                
                val responseBody = response.body?.string()
                println("Teste API - Corpo da resposta: $responseBody")
                
                return@withContext response.isSuccessful
            }
        } catch (e: Exception) {
            println("Erro no teste da API: ${e.message}")
            e.printStackTrace()
            return@withContext false
        }
    }
}