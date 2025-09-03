package com.vvai.calmwave.service

// ========================================
// BACKEND: WEBSOCKET SERVICE
// ========================================
// Este arquivo gerencia a comunicação WebSocket em tempo real
//  MANTER: Serviço principal para comunicação WebSocket bidirecional

import android.content.Context
import android.util.Base64
import android.util.Log
import com.vvai.calmwave.models.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.*
import okhttp3.Response
import okio.ByteString
import java.util.*
import java.util.concurrent.TimeUnit

class WebSocketService(
    private val context: Context,
    private val coroutineScope: CoroutineScope
) {
    
    // ========================================
    // BACKEND: Dependências e configurações
    // ========================================
    //  MANTER: Configurações do cliente WebSocket
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()
    
    private var webSocket: WebSocket? = null
    private var reconnectJob: Job? = null
    private var heartbeatJob: Job? = null
    
    // ========================================
    // BACKEND: Estado da conexão
    // ========================================
    //  MANTER: Controle do estado da conexão WebSocket
    private val _webSocketState = MutableStateFlow(WebSocketState())
    val webSocketState: StateFlow<WebSocketState> = _webSocketState.asStateFlow()
    
    // ========================================
    // BACKEND: Eventos WebSocket
    // ========================================
    //  MANTER: Fluxo de eventos para notificações
    private val _webSocketEvents = MutableSharedFlow<WebSocketEvent>()
    val webSocketEvents: SharedFlow<WebSocketEvent> = _webSocketEvents.asSharedFlow()
    
    // ========================================
    // BACKEND: Callbacks para áudio
    // ========================================
    //  MANTER: Callbacks para processamento de áudio recebido
    private var onAudioResponseCallback: ((AudioResponseMessage) -> Unit)? = null
    private var onAudioProcessedCallback: ((AudioProcessedMessage) -> Unit)? = null
    private var onConnectionStatusCallback: ((ConnectionStatusMessage) -> Unit)? = null
    
    // ========================================
    // BACKEND: Configurações de reconexão
    // ========================================
    //  MANTER: Configurações para reconexão automática
    private val reconnectDelays = listOf(1000L, 2000L, 5000L, 10000L, 30000L) // Delays em ms
    private var currentReconnectDelayIndex = 0
    
    // ========================================
    // BACKEND: Inicialização
    // ========================================
    //  MANTER: Setup inicial do serviço
    init {
        // Gera ID único do cliente
        val clientId = generateClientId()
        _webSocketState.value = _webSocketState.value.copy(clientId = clientId)
        
        // Inicia monitoramento de heartbeat
        startHeartbeatMonitoring()
    }
    
    // ========================================
    // BACKEND: Conectar ao servidor
    // ========================================
    //  MANTER: Estabelece conexão WebSocket com o servidor
    fun connect(serverUrl: String): Boolean {
        if (!_webSocketState.value.canConnect()) {
            Log.w(TAG, "Não é possível conectar: já conectando ou conectado")
            return false
        }
        
        try {
            _webSocketState.value = _webSocketState.value.copy(
                connectionStatus = ConnectionStatus.CONNECTING,
                isConnecting = true,
                serverUrl = serverUrl
            )
            
            val request = Request.Builder()
                .url(serverUrl)
                .addHeader("User-Agent", "CalmWave-Android")
                .addHeader("Client-ID", _webSocketState.value.clientId ?: "")
                .build()
            
            webSocket = client.newWebSocket(request, createWebSocketListener())
            
            Log.i(TAG, "Tentando conectar ao servidor: $serverUrl")
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao conectar: ${e.message}")
            _webSocketState.value = _webSocketState.value.copy(
                connectionStatus = ConnectionStatus.ERROR,
                isConnecting = false,
                lastError = e.message,
                lastErrorTime = System.currentTimeMillis()
            )
            return false
        }
    }
    
    // ========================================
    // BACKEND: Desconectar do servidor
    // ========================================
    //  MANTER: Fecha a conexão WebSocket
    fun disconnect() {
        try {
            // Cancela jobs de reconexão e heartbeat
            reconnectJob?.cancel()
            heartbeatJob?.cancel()
            
            // Fecha WebSocket
            webSocket?.close(1000, "Desconexão solicitada pelo cliente")
            webSocket = null
            
            _webSocketState.value = _webSocketState.value.copy(
                connectionStatus = ConnectionStatus.DISCONNECTED,
                isConnected = false,
                isConnecting = false,
                isReconnecting = false,
                sessionId = null,
                lastConnected = null
            )
            
            Log.i(TAG, "Desconectado do servidor")
            
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao desconectar: ${e.message}")
        }
    }
    
    // ========================================
    // BACKEND: Enviar mensagem
    // ========================================
    //  MANTER: Envia mensagem WebSocket para o servidor
    fun sendMessage(message: WebSocketMessage): Boolean {
        if (!_webSocketState.value.isConnected) {
            Log.w(TAG, "Não é possível enviar mensagem: não conectado")
            return false
        }
        
        if (webSocket == null) {
            Log.w(TAG, "WebSocket não está disponível")
            return false
        }
        
        // Valida a mensagem antes de enviar
        if (!WebSocketMessageUtils.validateMessage(message)) {
            Log.e(TAG, "Mensagem inválida: ${message.type}")
            return false
        }
        
        return try {
            val json = WebSocketMessageUtils.toJson(message)
            
            if (json.isEmpty()) {
                Log.e(TAG, "Falha ao serializar mensagem para JSON")
                return false
            }
            
            val success = webSocket?.send(json) ?: false
            
            if (success) {
                // Atualiza estatísticas
                _webSocketState.value = _webSocketState.value.copy(
                    messagesSent = _webSocketState.value.messagesSent + 1,
                    bytesSent = _webSocketState.value.bytesSent + json.length
                )
                
                // Emite evento
                coroutineScope.launch {
                    _webSocketEvents.emit(WebSocketEvent.MessageSent(message))
                }
                
                Log.d(TAG, "Mensagem enviada: ${message.type}")
            } else {
                Log.w(TAG, "Falha ao enviar mensagem via WebSocket")
            }
            
            success
            
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao enviar mensagem: ${e.message}")
            
            // Atualiza contador de erros
            _webSocketState.value = _webSocketState.value.copy(
                errorCount = _webSocketState.value.errorCount + 1,
                lastError = e.message,
                lastErrorTime = System.currentTimeMillis()
            )
            
            false
        }
    }
    
    // ========================================
    // BACKEND: Enviar chunk de áudio
    // ========================================
    //  MANTER: Envia chunk de áudio em tempo real
    fun sendAudioChunk(
        audioData: ByteArray,
        chunkIndex: Int,
        sessionId: String? = null
    ): Boolean {
        // Validação de parâmetros
        if (audioData.isEmpty()) {
            Log.w(TAG, "Tentativa de enviar chunk vazio")
            return false
        }
        
        if (chunkIndex < 0) {
            Log.w(TAG, "Índice de chunk inválido: $chunkIndex")
            return false
        }
        
        val base64Audio = Base64.encodeToString(audioData, Base64.NO_WRAP)
        
        val message = AudioChunkMessage(
            sessionId = sessionId ?: _webSocketState.value.sessionId,
            chunkIndex = chunkIndex,
            audioData = base64Audio,
            chunkSize = audioData.size
        )
        
        val success = sendMessage(message)
        
        if (success) {
            // Atualiza contador de chunks transmitidos
            _webSocketState.value = _webSocketState.value.copy(
                chunksTransmitted = _webSocketState.value.chunksTransmitted + 1
            )
        }
        
        return success
    }
    
    // ========================================
    // BACKEND: Iniciar transmissão de áudio
    // ========================================
    //  MANTER: Sinaliza início de transmissão de áudio
    fun startAudioTransmission(
        totalChunks: Int,
        estimatedDuration: Long,
        sessionId: String? = null
    ): Boolean {
        // Validação de parâmetros
        if (totalChunks <= 0) {
            Log.w(TAG, "Total de chunks deve ser maior que zero")
            return false
        }
        
        if (estimatedDuration <= 0) {
            Log.w(TAG, "Duração estimada deve ser maior que zero")
            return false
        }
        
        val transmissionId = UUID.randomUUID().toString()
        
        val message = AudioStartMessage(
            sessionId = sessionId ?: _webSocketState.value.sessionId,
            totalChunks = totalChunks,
            estimatedDuration = estimatedDuration,
            metadata = createAudioMetadata()
        )
        
        val success = sendMessage(message)
        
        if (success) {
            _webSocketState.value = _webSocketState.value.copy(
                isTransmitting = true,
                currentTransmissionId = transmissionId,
                transmissionStartTime = System.currentTimeMillis(),
                chunksTransmitted = 0
            )
            
            coroutineScope.launch {
                _webSocketEvents.emit(WebSocketEvent.TransmissionStarted(transmissionId))
            }
        }
        
        return success
    }
    
    // ========================================
    // BACKEND: Finalizar transmissão de áudio
    // ========================================
    //  MANTER: Sinaliza fim de transmissão de áudio
    fun endAudioTransmission(
        totalChunksSent: Int,
        finalDuration: Long,
        sessionId: String? = null
    ): Boolean {
        // Validação de parâmetros
        if (totalChunksSent < 0) {
            Log.w(TAG, "Total de chunks enviados não pode ser negativo")
            return false
        }
        
        if (finalDuration < 0) {
            Log.w(TAG, "Duração final não pode ser negativa")
            return false
        }
        
        val message = AudioEndMessage(
            sessionId = sessionId ?: _webSocketState.value.sessionId,
            totalChunksSent = totalChunksSent,
            finalDuration = finalDuration
        )
        
        val success = sendMessage(message)
        
        if (success) {
            val transmissionId = _webSocketState.value.currentTransmissionId
            
            _webSocketState.value = _webSocketState.value.copy(
                isTransmitting = false,
                currentTransmissionId = null,
                transmissionStartTime = null,
                chunksTransmitted = 0
            )
            
            transmissionId?.let { id ->
                coroutineScope.launch {
                    _webSocketEvents.emit(WebSocketEvent.TransmissionCompleted(id))
                }
            }
        }
        
        return success
    }
    
    // ========================================
    // BACKEND: Configurar callbacks
    // ========================================
    //  MANTER: Configura callbacks para diferentes tipos de mensagem
    fun setOnAudioResponseCallback(callback: (AudioResponseMessage) -> Unit) {
        onAudioResponseCallback = callback
    }
    
    fun setOnAudioProcessedCallback(callback: (AudioProcessedMessage) -> Unit) {
        onAudioProcessedCallback = callback
    }
    
    fun setOnConnectionStatusCallback(callback: (ConnectionStatusMessage) -> Unit) {
        onConnectionStatusCallback = callback
    }
    
    // ========================================
    // BACKEND: Listener WebSocket
    // ========================================
    //  MANTER: Gerencia eventos de conexão WebSocket
    private fun createWebSocketListener(): WebSocketListener {
        return object : WebSocketListener() {
            
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "Conexão WebSocket estabelecida")
                
                _webSocketState.value = _webSocketState.value.copy(
                    connectionStatus = ConnectionStatus.CONNECTED,
                    isConnected = true,
                    isConnecting = false,
                    isReconnecting = false,
                    lastConnected = System.currentTimeMillis(),
                    reconnectAttempts = 0
                )
                
                // Envia heartbeat inicial
                sendHeartbeat()
            }
            
            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "Mensagem recebida: $text")
                
                try {
                    val message = WebSocketMessageUtils.fromJson(text)
                    if (message != null) {
                        handleIncomingMessage(message)
                        
                        // Atualiza estatísticas
                        _webSocketState.value = _webSocketState.value.copy(
                            messagesReceived = _webSocketState.value.messagesReceived + 1,
                            bytesReceived = _webSocketState.value.bytesReceived + text.length
                        )
                        
                        // Emite evento
                        coroutineScope.launch {
                            _webSocketEvents.emit(WebSocketEvent.MessageReceived(message))
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Erro ao processar mensagem: ${e.message}")
                }
            }
            
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                Log.d(TAG, "Mensagem binária recebida: ${bytes.size} bytes")
                // Para mensagens binárias, convertemos para string se possível
                onMessage(webSocket, bytes.utf8())
            }
            
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "WebSocket fechando: $code - $reason")
                
                _webSocketState.value = _webSocketState.value.copy(
                    connectionStatus = ConnectionStatus.DISCONNECTED,
                    isConnected = false
                )
            }
            
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "WebSocket fechado: $code - $reason")
                
                _webSocketState.value = _webSocketState.value.copy(
                    connectionStatus = ConnectionStatus.DISCONNECTED,
                    isConnected = false,
                    isConnecting = false,
                    isReconnecting = false
                )
                
                // Tenta reconectar se não foi uma desconexão intencional
                if (code != 1000) {
                    scheduleReconnect()
                }
            }
            
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "Falha na WebSocket: ${t.message}")
                
                _webSocketState.value = _webSocketState.value.copy(
                    connectionStatus = ConnectionStatus.ERROR,
                    isConnected = false,
                    isConnecting = false,
                    lastError = t.message,
                    lastErrorTime = System.currentTimeMillis(),
                    errorCount = _webSocketState.value.errorCount + 1
                )
                
                // Tenta reconectar
                scheduleReconnect()
            }
        }
    }
    
    // ========================================
    // BACKEND: Processar mensagens recebidas
    // ========================================
    //  MANTER: Processa diferentes tipos de mensagem recebida
    private fun handleIncomingMessage(message: WebSocketMessage) {
        when (message) {
            is AudioResponseMessage -> {
                Log.d(TAG, "Resposta de áudio recebida: ${message.responseType}")
                onAudioResponseCallback?.invoke(message)
            }
            
            is AudioProcessedMessage -> {
                Log.d(TAG, "Áudio processado: ${message.result.success}")
                onAudioProcessedCallback?.invoke(message)
            }
            
            is ConnectionStatusMessage -> {
                Log.d(TAG, "Status de conexão: ${message.status}")
                onConnectionStatusCallback?.invoke(message)
                
                // Atualiza informações do servidor
                message.serverInfo?.let { serverInfo ->
                    _webSocketState.value = _webSocketState.value.copy(
                        serverInfo = serverInfo,
                        serverCapabilities = serverInfo.capabilities
                    )
                    
                    coroutineScope.launch {
                        _webSocketEvents.emit(WebSocketEvent.ServerCapabilitiesUpdated(serverInfo.capabilities))
                    }
                }
            }
            
            is ErrorMessage -> {
                Log.e(TAG, "Erro do servidor: ${message.errorCode} - ${message.errorMessage}")
            }
            
            else -> {
                Log.d(TAG, "Mensagem não processada: ${message.type}")
            }
        }
    }
    
    // ========================================
    // BACKEND: Reconexão automática
    // ========================================
    //  MANTER: Agenda tentativa de reconexão
    private fun scheduleReconnect() {
        if (!_webSocketState.value.canReconnect()) {
            Log.w(TAG, "Máximo de tentativas de reconexão atingido")
            return
        }
        
        val delay = reconnectDelays.getOrNull(_webSocketState.value.reconnectAttempts) ?: 30000L
        
        _webSocketState.value = _webSocketState.value.copy(
            connectionStatus = ConnectionStatus.RECONNECTING,
            isReconnecting = true,
            reconnectAttempts = _webSocketState.value.reconnectAttempts + 1
        )
        
        reconnectJob = coroutineScope.launch {
            delay(delay)
            
            Log.i(TAG, "Tentando reconectar... (tentativa ${_webSocketState.value.reconnectAttempts})")
            
            coroutineScope.launch {
                _webSocketEvents.emit(WebSocketEvent.Reconnecting(_webSocketState.value.reconnectAttempts))
            }
            
            val serverUrl = _webSocketState.value.serverUrl
            if (serverUrl != null) {
                connect(serverUrl)
            }
        }
    }
    
    // ========================================
    // BACKEND: Sistema de heartbeat
    // ========================================
    //  MANTER: Monitora e envia heartbeats para manter conexão
    private fun startHeartbeatMonitoring() {
        heartbeatJob = coroutineScope.launch {
            while (isActive) {
                try {
                    if (_webSocketState.value.isConnected && _webSocketState.value.shouldSendHeartbeat()) {
                        sendHeartbeat()
                    }
                    delay(10000) // Verifica a cada 10 segundos
                } catch (e: Exception) {
                    Log.e(TAG, "Erro no monitoramento de heartbeat: ${e.message}")
                }
            }
        }
    }
    
    private fun sendHeartbeat(): Boolean {
        val message = HeartbeatMessage(
            clientId = _webSocketState.value.clientId ?: ""
        )
        
        val success = sendMessage(message)
        
        if (success) {
            _webSocketState.value = _webSocketState.value.copy(
                lastHeartbeat = System.currentTimeMillis(),
                missedHeartbeats = 0
            )
        }
        
        return success
    }
    
    // ========================================
    // BACKEND: Utilitários
    // ========================================
    //  MANTER: Funções auxiliares
    private fun generateClientId(): String {
        return "android_${UUID.randomUUID().toString().take(8)}"
    }
    
    private fun createAudioMetadata(): AudioMetadata {
        return AudioMetadata(
            deviceInfo = DeviceInfo(
                deviceId = android.provider.Settings.Secure.getString(
                    context.contentResolver,
                    android.provider.Settings.Secure.ANDROID_ID
                ) ?: "unknown",
                deviceModel = android.os.Build.MODEL,
                androidVersion = android.os.Build.VERSION.RELEASE,
                appVersion = "1.0.0" // TODO: Obter versão real do app
            ),
            recordingSettings = RecordingSettings(),
            userInfo = null
        )
    }
    
    companion object {
        private const val TAG = "WebSocketService"
    }
}
