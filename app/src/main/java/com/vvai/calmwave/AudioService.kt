package com.vvai.calmwave

import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.AudioTrack.MODE_STREAM
import kotlinx.coroutines.*
import kotlinx.coroutines.Job
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
        // Limpa o estado de seek primeiro
        seekInProgress = false
        seekCoroutine?.cancel()
        seekCoroutine = null
        seekDirection = 0
        
        // Limpa o buffer circular
        audioBuffer.clear()
        bufferStartPosition = 0L
        
        try { 
            audioTrack?.stop() 
        } catch (e: IllegalStateException) {
            e.printStackTrace()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        try {
            audioTrack?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
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
                            // Cria um arquivo temporário para enviar como multipart/form-data
                            val tempFile = java.io.File.createTempFile("chunk_${chunkIndex}_", ".wav")
                            tempFile.writeBytes(accumulatedData)
                            
                            try {
                                val requestBody = okhttp3.MultipartBody.Builder()
                                    .setType(okhttp3.MultipartBody.FORM)
                                    .addFormDataPart(
                                        "audio",
                                        "chunk_${chunkIndex}.wav",
                                        okhttp3.RequestBody.create(
                                            "audio/wav".toMediaType(),
                                            tempFile
                                        )
                                    )
                                    .build()
                                
                                val request = Request.Builder()
                                    .url(apiEndpoint)
                                    .addHeader("X-Session-ID", sessionId)
                                    .addHeader("X-Chunk-Index", chunkIndex.toString())
                                    .addHeader("X-Chunk-Timestamp", currentTime.toString())
                                    .post(requestBody)
                                    .build()

                                try {
                                    client.newCall(request).execute().use { response ->
                                        if (response.isSuccessful) {
                                            val responseBody = response.body?.string()
                                            println("Chunk $chunkIndex enviado com sucesso. Resposta: $responseBody")
                                            
                                            // Se a API retornar áudio processado, reproduz
                                            val processedAudioBytes = response.body?.bytes()
                                            if (processedAudioBytes != null && processedAudioBytes.isNotEmpty()) {
                                                audioTrack?.write(processedAudioBytes, 0, processedAudioBytes.size)
                                                println("Áudio processado reproduzido para chunk $chunkIndex")
                                            }
                                        } else {
                                            println("Falha ao enviar chunk $chunkIndex: ${response.code}")
                                        }
                                    }
                                } catch (e: Exception) {
                                    println("Erro ao enviar chunk $chunkIndex: ${e.message}")
                                }
                            } finally {
                                tempFile.delete()
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
                    val tempFile = java.io.File.createTempFile("chunk_final_", ".wav")
                    tempFile.writeBytes(accumulatedData)
                    
                    try {
                        val requestBody = okhttp3.MultipartBody.Builder()
                            .setType(okhttp3.MultipartBody.FORM)
                            .addFormDataPart(
                                "audio",
                                "chunk_final.wav",
                                okhttp3.RequestBody.create(
                                    "audio/wav".toMediaType(),
                                    tempFile
                                )
                            )
                            .build()
                        
                        val request = Request.Builder()
                            .url(apiEndpoint)
                            .addHeader("X-Session-ID", sessionId)
                            .addHeader("X-Chunk-Index", chunkIndex.toString())
                            .addHeader("X-Chunk-Timestamp", System.currentTimeMillis().toString())
                            .addHeader("X-Chunk-Final", "true")
                            .post(requestBody)
                            .build()

                        try {
                            client.newCall(request).execute().use { response ->
                                if (response.isSuccessful) {
                                    val responseBody = response.body?.string()
                                    println("Chunk final $chunkIndex enviado com sucesso. Resposta: $responseBody")
                                    
                                    val processedAudioBytes = response.body?.bytes()
                                    if (processedAudioBytes != null && processedAudioBytes.isNotEmpty()) {
                                        audioTrack?.write(processedAudioBytes, 0, processedAudioBytes.size)
                                        println("Áudio processado reproduzido para chunk final $chunkIndex")
                                    }
                                } else {
                                    println("Falha ao enviar chunk final $chunkIndex: ${response.code}")
                                }
                            }
                        } catch (e: Exception) {
                            println("Erro ao enviar chunk final $chunkIndex: ${e.message}")
                        }
                    } finally {
                        tempFile.delete()
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
                    // Verifica se há um seek pendente
                    if (seekInProgress && targetSeekPosition != currentPlaybackPosition) {
                        println("Seek detectado durante reprodução: ${targetSeekPosition}ms")
                        
                        // Calcula quantos bytes pular (pode ser positivo ou negativo)
                        val seekBytes = (targetSeekPosition - currentPlaybackPosition) * sampleRate * 2 / 1000L
                        
                        if (seekBytes != 0L) {
                            if (seekBytes > 0) {
                                // Seek para frente: pula bytes
                                val skipped = fis.skip(seekBytes)
                                totalBytesRead += skipped
                                currentPlaybackPosition = targetSeekPosition
                                println("Seek para frente: pulou $skipped bytes, nova posição: ${currentPlaybackPosition}ms")
                            } else {
                                // Seek para trás: reinicia o arquivo na nova posição
                                println("Seek para trás detectado, reiniciando arquivo na posição ${targetSeekPosition}ms")
                                
                                // Fecha o stream atual
                                fis.close()
                                
                                // Abre um novo stream na posição desejada
                                val newFis = FileInputStream(file)
                                newFis.skip(44) // Pula o cabeçalho WAV
                                
                                // Pula para a posição desejada
                                val bytesToSkip = (targetSeekPosition * sampleRate * 2) / 1000L
                                var remaining = bytesToSkip
                                while (remaining > 0) {
                                    val skippedNow = newFis.skip(remaining)
                                    if (skippedNow <= 0) break
                                    remaining -= skippedNow
                                }
                                
                                // Atualiza as variáveis de controle
                                currentPlaybackPosition = targetSeekPosition
                                totalBytesRead = bytesToSkip
                                
                                // Continua a reprodução com o novo stream
                                while (isPlaying && !isPaused && coroutineContext.isActive) {
                                    val bytesRead = newFis.read(buffer)
                                    if (bytesRead == -1) break
                                    
                                    if (bytesRead > 0) {
                                        // Adiciona ao buffer circular
                                        val chunkCopy = buffer.copyOf(bytesRead)
                                        audioBuffer.add(chunkCopy)
                                        
                                        // Remove chunks antigos se o buffer estiver cheio
                                        if (audioBuffer.size > bufferSize) {
                                            audioBuffer.removeAt(0)
                                            bufferStartPosition += (CHUNK_SIZE * 1000L) / (sampleRate * 2)
                                        }
                                        
                                        audioTrack?.write(buffer, 0, bytesRead)
                                        totalBytesRead += bytesRead
                                        currentPlaybackPosition = (totalBytesRead * 1000L) / (sampleRate * 2)
                                    }
                                }
                                
                                // Fecha o novo stream
                                newFis.close()
                                break
                            }
                        }
                        seekInProgress = false
                    }
                    
                    val bytesRead = fis.read(buffer)
                    if (bytesRead == -1) break
                    
                    if (bytesRead > 0) {
                        // Adiciona ao buffer circular
                        val chunkCopy = buffer.copyOf(bytesRead)
                        audioBuffer.add(chunkCopy)
                        
                        // Remove chunks antigos se o buffer estiver cheio
                        if (audioBuffer.size > bufferSize) {
                            audioBuffer.removeAt(0)
                            bufferStartPosition += (CHUNK_SIZE * 1000L) / (sampleRate * 2)
                        }
                        
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
            
            // Limpa qualquer seek pendente ao retomar
            seekInProgress = false
            seekCoroutine?.cancel()
            seekCoroutine = null
            seekDirection = 0
            
            // Limpa o buffer ao retomar
            audioBuffer.clear()
            bufferStartPosition = 0L
            
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

    // Sistema de seek inteligente que não interrompe a reprodução
    private var seekInProgress = false
    private var targetSeekPosition = 0L
    private var seekCoroutine: Job? = null
    private var seekDirection = 0 // -1 para trás, 0 para frente, 1 para frente
    
    // Buffer circular para seek para trás
    private val audioBuffer = mutableListOf<ByteArray>()
    private val bufferSize = 50 // Mantém 50 chunks em memória
    private var bufferStartPosition = 0L
    
    fun seekTo(timeMs: Long, coroutineScope: CoroutineScope) {
        if (timeMs < 0 || timeMs > totalPlaybackDuration || currentPlayingFile == null) return
        
        try {
            println("=== SEEK INICIADO ===")
            println("Posição solicitada: ${timeMs}ms")
            println("Posição atual: ${currentPlaybackPosition}ms")
            println("Estado atual - isPlaying: $isPlaying, isPaused: $isPaused")
            println("Arquivo atual: $currentPlayingFile")
            
            // Cancela qualquer seek anterior em progresso
            seekCoroutine?.cancel()
            
            // Se já há um seek em progresso, aguarda um pouco
            if (seekInProgress) {
                println("Seek anterior em progresso, aguardando...")
                coroutineScope.launch {
                    delay(100)
                    seekTo(timeMs, coroutineScope)
                }
                return
            }
            
            // Atualiza a posição atual imediatamente para feedback visual
            currentPlaybackPosition = timeMs
            targetSeekPosition = timeMs
            
            // Se estava pausado, apenas atualiza a posição
            if (isPaused) {
                println("Seek em áudio pausado: posição atualizada para ${timeMs}ms")
                return
            }
            
            // Se estava reproduzindo, faz seek inteligente
            if (isPlaying) {
                println("Iniciando seek inteligente para ${timeMs}ms")
                seekInProgress = true
                
                // Verifica se é seek para trás (posição menor que a atual)
                val isSeekBackward = timeMs < currentPlaybackPosition
                
                if (isSeekBackward) {
                    println("Seek para trás detectado, usando método de reinicialização...")
                    seekDirection = -1
                    
                    // Para seek para trás, usamos o método de reinicialização
                    seekCoroutine = coroutineScope.launch {
                        try {
                            // Pequeno delay para estabilizar
                            delay(150)
                            
                            // Marca o seek como concluído
                            seekInProgress = false
                            seekDirection = 0
                            println("Seek para trás concluído para ${timeMs}ms")
                        } catch (e: Exception) {
                            println("Erro durante seek para trás: ${e.message}")
                            e.printStackTrace()
                            seekInProgress = false
                            seekDirection = 0
                        }
                    }
                } else {
                    // Seek para frente: apenas atualiza a posição
                    println("Seek para frente, posição será atualizada durante reprodução")
                    seekDirection = 1
                    seekCoroutine = coroutineScope.launch {
                        try {
                            delay(100) // Pequeno delay para permitir que a UI se atualize
                            seekInProgress = false
                            seekDirection = 0
                            println("Seek para frente concluído para ${timeMs}ms")
                        } catch (e: Exception) {
                            println("Erro durante seek para frente: ${e.message}")
                            e.printStackTrace()
                            seekInProgress = false
                            seekDirection = 0
                        }
                    }
                }
            }
        } catch (e: Exception) {
            println("Erro ao iniciar seek: ${e.message}")
            e.printStackTrace()
            seekInProgress = false
            seekDirection = 0
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
            
            // Cria um arquivo temporário para enviar como multipart/form-data
            val tempFile = java.io.File.createTempFile("chunk_${chunkIndex}_", ".wav")
            tempFile.writeBytes(fullWavData)
            
            try {
                // Cria o request body como multipart/form-data
                val requestBody = okhttp3.MultipartBody.Builder()
                    .setType(okhttp3.MultipartBody.FORM)
                    .addFormDataPart(
                        "audio",
                        "chunk_${chunkIndex}.wav",
                        okhttp3.RequestBody.create(
                            "audio/wav".toMediaType(),
                            tempFile
                        )
                    )
                    .build()
                
                val request = Request.Builder()
                    .url(apiEndpoint)
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
                        val responseBody = response.body?.string()
                        println("Chunk $chunkIndex processado com sucesso. Resposta: $responseBody")
                        
                        // Se a API retornar áudio processado, reproduz
                        val processedAudioBytes = response.body?.bytes()
                        if (processedAudioBytes != null && processedAudioBytes.isNotEmpty()) {
                            audioTrack?.write(processedAudioBytes, 0, processedAudioBytes.size)
                            println("Áudio processado reproduzido para chunk $chunkIndex")
                        }
                    } else {
                        val errorBody = response.body?.string()
                        println("Falha ao enviar chunk $chunkIndex: ${response.code} - $errorBody")
                    }
                }
            } finally {
                // Remove o arquivo temporário
                tempFile.delete()
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
    
    // Método para testar conectividade básica (GET simples)
    suspend fun testBasicConnectivity(apiEndpoint: String) = withContext(Dispatchers.IO) {
        try {
            println("=== TESTE DE CONECTIVIDADE BÁSICA ===")
            println("Endpoint base: $apiEndpoint")
            
            // Remove o /upload do endpoint e adiciona /health
            val baseUrl = apiEndpoint.replace("/upload", "/health")
            println("Testando conectividade em: $baseUrl")
            
            val request = Request.Builder()
                .url(baseUrl)
                .get()
                .build()

            println("Executando requisição GET...")
            val startTime = System.currentTimeMillis()
            
            client.newCall(request).execute().use { response ->
                val endTime = System.currentTimeMillis()
                val duration = endTime - startTime
                
                println("=== RESPOSTA DO SERVIDOR ===")
                println("Tempo de resposta: ${duration}ms")
                println("Código de status: ${response.code}")
                println("Mensagem: ${response.message}")
                
                val responseBody = response.body?.string()
                println("Corpo da resposta: $responseBody")
                
                if (response.isSuccessful) {
                    println("✅ SERVIDOR RESPONDEU!")
                    return@withContext true
                } else {
                    println("❌ SERVIDOR RESPONDEU COM ERRO - Código: ${response.code}")
                    return@withContext false
                }
            }
        } catch (e: Exception) {
            println("=== ERRO NA CONECTIVIDADE BÁSICA ===")
            println("Tipo de erro: ${e.javaClass.simpleName}")
            println("Mensagem: ${e.message}")
            e.printStackTrace()
            return@withContext false
        }
    }

    // Método para testar a conectividade com a API
    suspend fun testAPIConnection(apiEndpoint: String) = withContext(Dispatchers.IO) {
        try {
            println("=== TESTE DE CONEXÃO COM API ===")
            println("Endpoint: $apiEndpoint")
            println("Iniciando teste de conectividade...")
            
            val testData = ByteArray(1024) { 0 } // Dados de teste vazios
            val wavHeader = createWavHeader(testData.size)
            val fullData = wavHeader + testData
            
            println("Dados de teste preparados: ${fullData.size} bytes")
            
            // Cria um arquivo temporário para o teste
            val tempFile = java.io.File.createTempFile("test_", ".wav")
            tempFile.writeBytes(fullData)
            
            try {
                // Cria o request body como multipart/form-data
                val requestBody = okhttp3.MultipartBody.Builder()
                    .setType(okhttp3.MultipartBody.FORM)
                    .addFormDataPart(
                        "audio",
                        "test_audio.wav",
                        okhttp3.RequestBody.create(
                            "audio/wav".toMediaType(),
                            tempFile
                        )
                    )
                    .build()
                
                val request = Request.Builder()
                    .url(apiEndpoint)
                    .addHeader("X-Session-ID", "test-session-${System.currentTimeMillis()}")
                    .addHeader("X-Chunk-Index", "0")
                    .addHeader("X-Chunk-Timestamp", System.currentTimeMillis().toString())
                    .addHeader("X-Chunk-Size", testData.size.toString())
                    .addHeader("X-Test-Mode", "true")
                    .post(requestBody)
                    .build()

                println("Requisição preparada:")
                println("  URL: ${request.url}")
                println("  Método: ${request.method}")
                println("  Headers: ${request.headers}")

                println("Executando requisição...")
                val startTime = System.currentTimeMillis()
                
                client.newCall(request).execute().use { response ->
                    val endTime = System.currentTimeMillis()
                    val duration = endTime - startTime
                    
                    println("=== RESPOSTA DA API ===")
                    println("Tempo de resposta: ${duration}ms")
                    println("Código de status: ${response.code}")
                    println("Mensagem: ${response.message}")
                    println("Headers da resposta: ${response.headers}")
                    
                    val responseBody = response.body?.string()
                    println("Corpo da resposta: $responseBody")
                    
                    if (response.isSuccessful) {
                        println("✅ CONEXÃO BEM-SUCEDIDA!")
                        return@withContext true
                    } else {
                        println("❌ FALHA NA CONEXÃO - Código: ${response.code}")
                        return@withContext false
                    }
                }
            } finally {
                // Remove o arquivo temporário
                tempFile.delete()
            }
        } catch (e: Exception) {
            println("=== ERRO NO TESTE DA API ===")
            println("Tipo de erro: ${e.javaClass.simpleName}")
            println("Mensagem: ${e.message}")
            println("Causa: ${e.cause?.message}")
            e.printStackTrace()
            return@withContext false
        }
    }
}