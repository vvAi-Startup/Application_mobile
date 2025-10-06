package com.vvai.calmwave

import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject

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
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

class AudioService {
    private var webSocket: WebSocket? = null
    private var webSocketJob: Job? = null
    private var isWebSocketConnected = false
    private var processedOutputStream: FileOutputStream? = null
    private var processedOutputFile: File? = null
    private var processedDataBytes: Long = 0

    private var jsonWs: com.vvai.calmwave.service.WebSocketService? = null
    private var currentSessionId: String? = null

    // Variáveis para controle do áudio do WebSocket
    private var webSocketAudioBuffer = mutableListOf<ByteArray>()
    private var webSocketAudioPosition: Long = 0
    private var webSocketAudioDuration: Long = 0
    private var isWebSocketAudioPlaying = false
    private var isWebSocketAudioPaused = false
    private var webSocketAudioStartTime: Long = 0

    // Callback para notificar quando áudio WebSocket é recebido
    private var onWebSocketAudioReceived: (() -> Unit)? = null

    fun connectWebSocket(apiWsUrl: String, context: Context, onConnected: (() -> Unit)? = null, onFailure: ((Throwable) -> Unit)? = null) {
        try {
            println("=== CONECTANDO WEBSOCKET ===")
            println("URL: $apiWsUrl")
            
            // Prepare output file for processed audio
            val outDir = context.getExternalFilesDir(null)
            processedOutputFile = File(outDir, "processed_${System.currentTimeMillis()}.wav")
            processedOutputStream = FileOutputStream(processedOutputFile!!)
            writeWavHeader(processedOutputStream!!) // cabeçalho temporário
            processedDataBytes = 0
            
            println("Arquivo processado criado: ${processedOutputFile?.absolutePath}")

            stopAndReleaseAudioTrack()
            setupAudioTrack()

            // Build json websocket using OkHttp client already present
            if (jsonWs == null) jsonWs = com.vvai.calmwave.service.WebSocketService(client)

            val listener = object : com.vvai.calmwave.service.WebSocketService.Listener {
                override fun onOpen() {
                    isWebSocketConnected = true
                    // Start a new session like the Python tester
                    currentSessionId = UUID.randomUUID().toString()
                    val startMsg = "{" +
                        "\"type\":\"start_session\"," +
                        "\"session_id\":\"${currentSessionId}\"" +
                    "}"
                    jsonWs?.sendText(startMsg)
                    
                    // Inicia o áudio WebSocket quando conectar
                    isWebSocketAudioPlaying = false
                    isWebSocketAudioPaused = false
                    webSocketAudioPosition = 0
                    webSocketAudioDuration = 0
                    webSocketAudioBuffer.clear()
                    
                    onConnected?.invoke()
                }
                override fun onTextMessage(text: String) {
                    // Expect JSON messages, possibly containing processed_audio_data (base64 of WAV)
                    try {
                        println("WebSocket recebeu mensagem: ${text.take(100)}...")
                        val obj = JSONObject(text)
                        val type = obj.optString("type")
                        when (type) {
                            "audio_processed" -> {
                                val processed = obj.optString("processed_audio_data", null)
                                if (!processed.isNullOrEmpty()) {
                                    try {
                                        val bytes = android.util.Base64.decode(processed, android.util.Base64.DEFAULT)
                                        if (bytes == null || bytes.isEmpty()) {
                                            println("Erro: dados de áudio Base64 inválidos")
                                            return
                                        }
                                        
                                        val pcm = extractPcm(bytes)
                                        if (pcm == null || pcm.isEmpty()) {
                                            println("Erro: PCM extraído é nulo ou vazio")
                                            return
                                        }
                                        
                                        // Inicia o áudio do WebSocket se ainda não estiver ativo
                                        if (!isWebSocketAudioPlaying && !isWebSocketAudioPaused) {
                                            isWebSocketAudioPlaying = true
                                            webSocketAudioStartTime = System.currentTimeMillis()
                                            try {
                                                audioTrack?.play()
                                                println("AudioTrack iniciado para WebSocket")
                                            } catch (e: Exception) {
                                                println("Erro ao iniciar AudioTrack: ${e.message}")
                                                return
                                            }
                                            // Notifica que áudio WebSocket foi recebido (apenas uma vez)
                                            try {
                                                onWebSocketAudioReceived?.invoke()
                                            } catch (e: Exception) {
                                                println("Erro ao invocar callback: ${e.message}")
                                            }
                                        }
                                                          // Processa áudio diretamente para evitar complexidade de coroutines
                                        try {
                                            // Validação final antes do processamento
                                            if (pcm.isEmpty() || !isWebSocketConnected) {
                                                println("Estado inválido para processamento de áudio")
                                                return
                                            }
                                            
                                            // Adiciona ao buffer de forma thread-safe
                                            synchronized(webSocketAudioBuffer) {
                                                try {
                                                    // Cria uma cópia defensiva para evitar problemas de concorrência
                                                    val bufferCopy = ByteArray(pcm.size)
                                                    System.arraycopy(pcm, 0, bufferCopy, 0, pcm.size)
                                                    webSocketAudioBuffer.add(bufferCopy)
                                                    
                                                    // Limita o tamanho do buffer para evitar uso excessivo de memória
                                                    while (webSocketAudioBuffer.size > 50) {
                                                        webSocketAudioBuffer.removeFirstOrNull()
                                                    }
                                                } catch (e: Exception) {
                                                    println("Erro ao gerenciar buffer WebSocket: ${e.message}")
                                                    return
                                                }
                                            }
                                            
                                            // Reproduz o áudio se estiver ativo - com múltiplas validações
                                            if (isWebSocketAudioPlaying && !isWebSocketAudioPaused) {
                                                val currentTrack = audioTrack
                                                if (currentTrack != null) {
                                                    try {
                                                        // Verifica múltiplos estados antes de escrever
                                                        when {
                                                            currentTrack.state != AudioTrack.STATE_INITIALIZED -> {
                                                                println("AudioTrack não inicializado, estado: ${currentTrack.state}")
                                                            }
                                                            currentTrack.playState == AudioTrack.PLAYSTATE_STOPPED -> {
                                                                println("AudioTrack parado, estado: ${currentTrack.playState}")
                                                            }
                                                            pcm.size <= 0 -> {
                                                                println("PCM vazio ou inválido: ${pcm.size}")
                                                            }
                                                            else -> {
                                                                // Escreve o áudio de forma segura
                                                                val result = currentTrack.write(pcm, 0, pcm.size)
                                                                when {
                                                                    result > 0 -> {
                                                                        // Sucesso - não faz nada
                                                                    }
                                                                    result == AudioTrack.ERROR_INVALID_OPERATION -> {
                                                                        println("AudioTrack: Operação inválida")
                                                                        isWebSocketAudioPlaying = false
                                                                    }
                                                                    result == AudioTrack.ERROR_BAD_VALUE -> {
                                                                        println("AudioTrack: Valor inválido")
                                                                    }
                                                                    result == AudioTrack.ERROR_DEAD_OBJECT -> {
                                                                        println("AudioTrack: Objeto morto")
                                                                        isWebSocketAudioPlaying = false
                                                                        audioTrack = null
                                                                    }
                                                                    else -> {
                                                                        println("AudioTrack erro desconhecido: $result")
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    } catch (e: IllegalStateException) {
                                                        println("AudioTrack IllegalStateException: ${e.message}")
                                                        isWebSocketAudioPlaying = false
                                                    } catch (e: Exception) {
                                                        println("AudioTrack Exception geral: ${e.message}")
                                                        e.printStackTrace()
                                                    }
                                                } else {
                                                    println("AudioTrack é nulo durante reprodução WebSocket")
                                                    isWebSocketAudioPlaying = false
                                                }
                                            }
                                            
                                            // Salva no arquivo processado com proteções robustas
                                            val currentStream = processedOutputStream
                                            if (currentStream != null) {
                                                try {
                                                    synchronized(currentStream) {
                                                        // Verifica se o stream ainda está válido
                                                        if (processedOutputStream == currentStream) {
                                                            currentStream.write(pcm)
                                                            currentStream.flush()
                                                            
                                                            // Atualiza contadores de forma thread-safe
                                                            synchronized(this@AudioService) {
                                                                processedDataBytes += pcm.size
                                                                
                                                                // Atualiza a duração estimada
                                                                if (sampleRate > 0) {
                                                                    webSocketAudioDuration = (processedDataBytes * 1000L) / (sampleRate * 2)
                                                                }
                                                            }
                                                        }
                                                    }
                                                } catch (e: IOException) {
                                                    println("Erro I/O ao salvar áudio processado: ${e.message}")
                                                } catch (e: Exception) {
                                                    println("Erro geral ao salvar áudio: ${e.message}")
                                                }
                                            }
                                            
                                        } catch (e: Exception) {
                                            println("Erro crítico no processamento WebSocket: ${e.message}")
                                            e.printStackTrace()
                                            
                                            // Recovery: para o áudio WebSocket em caso de erro crítico
                                            try {
                                                isWebSocketAudioPlaying = false
                                                isWebSocketAudioPaused = false
                                                val track = audioTrack
                                                if (track != null && track.state == AudioTrack.STATE_INITIALIZED) {
                                                    track.pause()
                                                }
                                            } catch (ex: Exception) {
                                                println("Erro durante recovery: ${ex.message}")
                                            }
                                        }
                                    } catch (e: Exception) {
                                        println("Erro ao processar dados Base64: ${e.message}")
                                        e.printStackTrace()
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        println("Erro ao processar mensagem WebSocket: ${e.message}")
                        e.printStackTrace()
                    }
                }
                override fun onClosed(code: Int, reason: String) {
                    println("WebSocket fechado - código: $code, razão: $reason")
                    isWebSocketConnected = false
                    
                    // Limpa recursos de áudio WebSocket de forma segura
                    try {
                        isWebSocketAudioPlaying = false
                        isWebSocketAudioPaused = false
                        
                        // Para o AudioTrack se estiver tocando
                        audioTrack?.let { track ->
                            try {
                                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                                    track.pause()
                                }
                            } catch (e: Exception) {
                                println("Erro ao pausar AudioTrack no fechamento: ${e.message}")
                            }
                        }
                        
                        // Limpa o buffer de forma segura
                        synchronized(webSocketAudioBuffer) {
                            webSocketAudioBuffer.clear()
                        }
                        
                        // Fecha o arquivo de saída se existir
                        processedOutputStream?.let { stream ->
                            try {
                                synchronized(stream) {
                                    stream.flush()
                                    stream.close()
                                }
                            } catch (e: Exception) {
                                println("Erro ao fechar stream: ${e.message}")
                            }
                        }
                        processedOutputStream = null
                        
                    } catch (e: Exception) {
                        println("Erro durante limpeza no fechamento do WebSocket: ${e.message}")
                        e.printStackTrace()
                    }
                }
                
                override fun onFailure(t: Throwable) {
                    println("WebSocket falhou: ${t.message}")
                    isWebSocketConnected = false
                    
                    // Realiza a mesma limpeza que no onClosed
                    try {
                        isWebSocketAudioPlaying = false
                        isWebSocketAudioPaused = false
                        
                        audioTrack?.let { track ->
                            try {
                                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                                    track.pause()
                                }
                            } catch (e: Exception) {
                                println("Erro ao pausar AudioTrack na falha: ${e.message}")
                            }
                        }
                        
                        synchronized(webSocketAudioBuffer) {
                            webSocketAudioBuffer.clear()
                        }
                        
                    } catch (e: Exception) {
                        println("Erro durante limpeza na falha do WebSocket: ${e.message}")
                    }
                    
                    onFailure?.invoke(t)
                }
            }

            jsonWs?.connect(apiWsUrl, listener)
        } catch (e: Exception) {
            onFailure?.invoke(e)
        }
    }

    fun disconnectWebSocket() {
        println("Desconectando WebSocket e limpando recursos...")
        
        // Para o processamento primeiro
        isWebSocketConnected = false
        
        // Send stop_session if we have an active session
        try {
            currentSessionId?.let { sid ->
                val stopMsg = JSONObject().apply {
                    put("type", "stop_session")
                    put("session_id", sid)
                }
                jsonWs?.sendText(stopMsg.toString())
            }
        } catch (e: Exception) {
            println("Erro ao enviar stop_session: ${e.message}")
        }
        
        // Limpa o estado do áudio WebSocket primeiro
        try {
            stopWebSocketAudio()
        } catch (e: Exception) {
            println("Erro ao parar áudio WebSocket: ${e.message}")
        }
        
        // Fecha conexões
        try {
            webSocket?.close(1000, "Normal closure")
            jsonWs?.close()
        } catch (e: Exception) {
            println("Erro ao fechar WebSocket: ${e.message}")
        }
        
        // Cancela jobs
        try {
            webSocketJob?.cancel()
        } catch (e: Exception) {
            println("Erro ao cancelar job: ${e.message}")
        }
        
        // Finaliza output
        try {
            finalizeProcessedOutput()
        } catch (e: Exception) {
            println("Erro ao finalizar output: ${e.message}")
        }
        
        // Limpa referências
        webSocket = null
        currentSessionId = null
        
        println("WebSocket desconectado e recursos limpos")
    }

    fun sendAudioChunkViaWebSocket(chunk: ByteArray) {
        if (isWebSocketConnected) {
            // Build a proper WAV for this chunk (16kHz mono 16-bit) and base64 encode
            val wavHeader = createWavHeaderFor16kMono16bit(chunk.size)
            val wavBytes = wavHeader + chunk
            val b64 = android.util.Base64.encodeToString(wavBytes, android.util.Base64.NO_WRAP)
            val msg = JSONObject().apply {
                put("type", "audio_chunk")
                put("session_id", currentSessionId ?: UUID.randomUUID().toString().also { currentSessionId = it })
                put("chunk_id", "chunk_${System.currentTimeMillis()}")
                put("audio_data", b64)
                put("is_final", false)
                put("format", "wav")
                put("sample_rate", 16000)
                put("channels", 1)
                put("bits_per_sample", 16)
            }
            jsonWs?.sendText(msg.toString())
        }
    }

    fun sendAudioChunksPeriodically(audioFile: File, chunkDurationMs: Long = 10000L) {
        webSocketJob?.cancel()
        webSocketJob = CoroutineScope(Dispatchers.IO).launch {
            stopAndReleaseAudioTrack()
            setupAudioTrack()
            FileInputStream(audioFile).use { fis ->
                val buffer = ByteArray(CHUNK_SIZE)
                var bytesRead: Int
                var accumulatedData = ByteArray(0)
                var lastChunkTime = System.currentTimeMillis()
                while (fis.read(buffer, 0, CHUNK_SIZE).also { bytesRead = it } != -1 && isWebSocketConnected) {
                    val chunkData = if (bytesRead < CHUNK_SIZE) buffer.copyOf(bytesRead) else buffer
                    accumulatedData += chunkData
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastChunkTime >= chunkDurationMs) {
                        if (accumulatedData.isNotEmpty()) {
                            sendAudioChunkViaWebSocket(accumulatedData)
                            accumulatedData = ByteArray(0)
                            lastChunkTime = currentTime
                        }
                    }
                    delay(10)
                }
                // Envia qualquer dado restante
                if (accumulatedData.isNotEmpty()) {
                    sendAudioChunkViaWebSocket(accumulatedData)
                }
            }
        }
    }
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
    private val sampleRate = 16000
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
        try {
            val minBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            // Use um buffer maior para reduzir uso de CPU
            val bufferSize = minBufferSize * 4
            
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
            
            // Configura callback para melhor gerenciamento
            audioTrack?.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
                override fun onMarkerReached(track: AudioTrack?) {
                    // Implementar se necessário
                }
                
                override fun onPeriodicNotification(track: AudioTrack?) {
                    // Reduz chamadas desnecessárias
                }
            })
            
            audioTrack?.play()
            println("AudioTrack inicializado com sucesso - Buffer: $bufferSize bytes")
        } catch (e: Exception) {
            println("Erro ao configurar AudioTrack: ${e.message}")
            e.printStackTrace()
        }
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

    private fun writeWavHeader(out: FileOutputStream) {
        val header = ByteArray(44)
        val buffer = java.nio.ByteBuffer.wrap(header).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        val numChannels = 1
        val bitsPerSample = 16
        val byteRate = sampleRate * numChannels * bitsPerSample / 8

        buffer.putInt(0x46464952)
        buffer.putInt(0)
        buffer.putInt(0x45564157)
        buffer.putInt(0x20746d66)
        buffer.putInt(16)
        buffer.putShort(1)
        buffer.putShort(numChannels.toShort())
        buffer.putInt(sampleRate)
        buffer.putInt(byteRate)
        buffer.putShort((numChannels * bitsPerSample / 8).toShort())
        buffer.putShort(bitsPerSample.toShort())
        buffer.putInt(0x61746164)
        buffer.putInt(0)
        out.write(header, 0, 44)
    }

    private fun finalizeProcessedOutput() {
        try {
            processedOutputStream?.flush()
            processedOutputStream?.close()
        } catch (_: Exception) {}

        processedOutputStream = null
        processedOutputFile?.let { file ->
            try {
                if (processedDataBytes > 0) {
                    val raf = java.io.RandomAccessFile(file, "rw")
                    val dataSize = processedDataBytes.toInt()
                    val fileSize = 36 + dataSize
                    raf.seek(4)
                    raf.writeInt(java.lang.Integer.reverseBytes(fileSize))
                    raf.seek(40)
                    raf.writeInt(java.lang.Integer.reverseBytes(dataSize))
                    raf.close()
                } else {
                    // Nada recebido, remove arquivo vazio
                    file.delete()
                }
            } catch (_: Exception) {}
        }
        processedOutputFile = null
        processedDataBytes = 0
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
    val chunkIntervalMs = 10000L // 10 segundos
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
                    
                    // Envia chunk a cada 10 segundos
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
            
            // Deriva /health a partir do upload informado
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

    private fun createWavHeaderFor16kMono16bit(dataSize: Int): ByteArray {
        val header = ByteArray(44)
        val buffer = java.nio.ByteBuffer.wrap(header).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        val sampleRate = 16000
        val numChannels = 1
        val bitsPerSample = 16
        val byteRate = sampleRate * numChannels * bitsPerSample / 8

        buffer.putInt(0x46464952)
        buffer.putInt(36 + dataSize)
        buffer.putInt(0x45564157)
        buffer.putInt(0x20746d66)
        buffer.putInt(16)
        buffer.putShort(1)
        buffer.putShort(numChannels.toShort())
        buffer.putInt(sampleRate)
        buffer.putInt(byteRate)
        buffer.putShort((numChannels * bitsPerSample / 8).toShort())
        buffer.putShort(bitsPerSample.toShort())
        buffer.putInt(0x61746164)
        buffer.putInt(dataSize)
        return header
    }

    // Extrai PCM de bytes WAV (ou retorna os próprios bytes se já forem PCM)
    private fun extractPcm(data: ByteArray): ByteArray {
        return try {
            // Validações básicas de entrada
            if (data.isEmpty()) {
                println("extractPcm: Dados vazios")
                return ByteArray(0)
            }
            
            if (data.size < 44) {
                println("extractPcm: Dados muito pequenos (${data.size} bytes), retornando como está")
                return data
            }
            
            // Verifica se é um arquivo WAV válido
            val isWav = data.size >= 4 && 
                       data[0] == 'R'.code.toByte() && 
                       data[1] == 'I'.code.toByte() && 
                       data[2] == 'F'.code.toByte() && 
                       data[3] == 'F'.code.toByte()
            
            if (isWav) {
                println("extractPcm: Processando arquivo WAV de ${data.size} bytes")
                
                // Procura chunk 'data' de forma mais segura
                var offset = 12 // após RIFF(4) + size(4) + WAVE(4)
                var iterations = 0
                
                while (offset + 8 <= data.size && iterations < 20) { // Limite de iterações para evitar loops infinitos
                    try {
                        // Verifica se há bytes suficientes para ler o ID
                        if (offset + 4 > data.size) break
                        
                        val id = String(data.copyOfRange(offset, offset + 4), Charsets.US_ASCII)
                        
                        // Verifica se há bytes suficientes para ler o tamanho
                        if (offset + 8 > data.size) break
                        
                        val sizeBytes = data.copyOfRange(offset + 4, offset + 8)
                        val size = java.nio.ByteBuffer.wrap(sizeBytes).order(java.nio.ByteOrder.LITTLE_ENDIAN).int
                        
                        // Verifica se o tamanho é razoável
                        if (size < 0 || size > data.size) {
                            println("extractPcm: Tamanho inválido do chunk '$id': $size")
                            break
                        }
                        
                        if (id == "data") {
                            val start = offset + 8
                            val end = (start + size).coerceAtMost(data.size)
                            
                            if (start < data.size && end > start) {
                                val pcmData = data.copyOfRange(start, end)
                                println("extractPcm: Extraído ${pcmData.size} bytes de PCM")
                                return pcmData
                            } else {
                                println("extractPcm: Índices inválidos para chunk data: start=$start, end=$end")
                                break
                            }
                        }
                        
                        offset += 8 + size
                        iterations++
                        
                    } catch (e: Exception) {
                        println("extractPcm: Erro ao processar chunk na posição $offset: ${e.message}")
                        break
                    }
                }
                
                // Se não encontrou o chunk 'data', tenta remover cabeçalho padrão
                println("extractPcm: Chunk 'data' não encontrado, removendo cabeçalho padrão")
                return if (data.size > 44) {
                    data.copyOfRange(44, data.size)
                } else {
                    data
                }
            } else {
                println("extractPcm: Não é um arquivo WAV, retornando dados como PCM bruto")
                return data
            }
            
        } catch (e: Exception) {
            println("extractPcm: Erro durante extração: ${e.message}")
            e.printStackTrace()
            // Em caso de erro, retorna os dados originais
            return data
        }
    }

    fun getLatestProcessedFile(): File? {
        return processedOutputFile
    }

    fun getCurrentProcessedFilePath(): String? {
        return processedOutputFile?.absolutePath
    }

    // Funções para controle do áudio do WebSocket
    fun pauseWebSocketAudio() {
        if (isWebSocketAudioPlaying) {
            isWebSocketAudioPaused = true
            isWebSocketAudioPlaying = false
            try {
                audioTrack?.pause()
                println("WebSocket audio pausado")
            } catch (e: Exception) {
                println("Erro ao pausar WebSocket audio: ${e.message}")
            }
        }
    }

    fun resumeWebSocketAudio() {
        if (isWebSocketAudioPaused) {
            isWebSocketAudioPaused = false
            isWebSocketAudioPlaying = true
            try {
                audioTrack?.play()
                webSocketAudioStartTime = System.currentTimeMillis() - webSocketAudioPosition
                println("WebSocket audio retomado")
            } catch (e: Exception) {
                println("Erro ao retomar WebSocket audio: ${e.message}")
            }
        }
    }

    fun stopWebSocketAudio() {
        println("Parando áudio WebSocket...")
        
        // Para todos os flags primeiro
        isWebSocketAudioPlaying = false
        isWebSocketAudioPaused = false
        webSocketAudioPosition = 0
        
        try {
            // Limpa o buffer de forma thread-safe
            synchronized(webSocketAudioBuffer) {
                webSocketAudioBuffer.clear()
            }
            
            // Para e limpa o AudioTrack de forma segura
            audioTrack?.let { track ->
                try {
                    if (track.state == AudioTrack.STATE_INITIALIZED) {
                        if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                            track.pause() // Pausa primeiro
                        }
                        track.flush() // Limpa o buffer interno
                        println("AudioTrack pausado e limpo para WebSocket")
                    }
                } catch (e: IllegalStateException) {
                    println("AudioTrack em estado inválido ao parar: ${e.message}")
                } catch (e: Exception) {
                    println("Erro ao manipular AudioTrack: ${e.message}")
                }
            }
            
            // Limpa outros recursos relacionados
            webSocketAudioStartTime = 0L
            webSocketAudioDuration = 0L
            
            println("WebSocket audio parado e recursos limpos")
            
        } catch (e: Exception) {
            println("Erro crítico ao parar WebSocket audio: ${e.message}")
            e.printStackTrace()
            
            // Força limpeza mesmo com erro
            try {
                isWebSocketAudioPlaying = false
                isWebSocketAudioPaused = false
                webSocketAudioBuffer.clear()
            } catch (ex: Exception) {
                println("Erro na limpeza forçada: ${ex.message}")
            }
        }
    }

    fun seekWebSocketAudio(positionMs: Long) {
        webSocketAudioPosition = positionMs
        // Implementar lógica de seek se necessário
        // Para áudio de streaming, pode ser complexo implementar seek
    }

    fun getWebSocketAudioPosition(): Long {
        return try {
            if (isWebSocketAudioPlaying && !isWebSocketAudioPaused) {
                val currentTime = System.currentTimeMillis()
                val calculatedPosition = webSocketAudioPosition + (currentTime - webSocketAudioStartTime)
                // Limita a posição à duração máxima
                minOf(calculatedPosition, webSocketAudioDuration)
            } else {
                webSocketAudioPosition
            }
        } catch (e: Exception) {
            println("Erro ao calcular posição WebSocket: ${e.message}")
            webSocketAudioPosition
        }
    }

    fun getWebSocketAudioDuration(): Long = webSocketAudioDuration

    fun isWebSocketAudioActive(): Boolean = isWebSocketAudioPlaying || isWebSocketAudioPaused

    fun setWebSocketAudioCallback(callback: () -> Unit) {
        onWebSocketAudioReceived = callback
    }
}