package com.vvai.calmwave.controller

// ========================================
// BACKEND: MAIN VIEW MODEL
// ========================================
// Este arquivo é o coração da lógica de negócio da aplicação
//  MANTER: Coordenação entre serviços e gerenciamento de estado

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vvai.calmwave.models.UiState
import com.vvai.calmwave.models.WebSocketState
import com.vvai.calmwave.models.ConnectionStatus
import com.vvai.calmwave.models.AudioResponseMessage
import com.vvai.calmwave.models.AudioProcessedMessage
import com.vvai.calmwave.models.ConnectionStatusMessage
import com.vvai.calmwave.models.ResponseType
import com.vvai.calmwave.service.AudioService
import com.vvai.calmwave.service.WavRecorder
import com.vvai.calmwave.service.WebSocketService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainViewModel(
    private val audioService: AudioService,
    private val wavRecorder: WavRecorder,
    private val webSocketService: WebSocketService,
    private val context: Context
) : ViewModel() {

    // ========================================
    // BACKEND: Estado da UI
    // ========================================
    //  MANTER: Gerenciamento centralizado do estado
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    // ========================================
    // BACKEND: Controllers
    // ========================================
    //  MANTER: Controllers para diferentes funcionalidades
    private val recordingController = RecordingController(
        audioService = audioService,
        wavRecorder = wavRecorder,
        webSocketService = webSocketService,
        context = context,
        coroutineScope = viewModelScope
    )

    // ========================================
    // BACKEND: Estado WebSocket
    // ========================================
    //  MANTER: Estado da conexão WebSocket
    private val _webSocketState = MutableStateFlow(WebSocketState())
    val webSocketState: StateFlow<WebSocketState> = _webSocketState.asStateFlow()

    // ========================================
    // BACKEND: Inicialização
    // ========================================
    //  MANTER: Setup inicial dos serviços
    init {
        // Inicializa o AudioService com o contexto da aplicação
        audioService.init(context.applicationContext)
        
        // Inicia um loop de atualização de reprodução
        startPlaybackMonitor()
        
        // Configura callbacks WebSocket
        setupWebSocketCallbacks()
        
        // Inicia monitoramento do estado WebSocket
        startWebSocketMonitoring()
    }

    // ========================================
    // BACKEND: Configurar callbacks WebSocket
    // ========================================
    //  MANTER: Configura callbacks para eventos WebSocket
    private fun setupWebSocketCallbacks() {
        webSocketService.setOnAudioResponseCallback { message ->
            handleAudioResponse(message)
        }
        
        webSocketService.setOnAudioProcessedCallback { message ->
            handleAudioProcessed(message)
        }
        
        webSocketService.setOnConnectionStatusCallback { message ->
            handleConnectionStatus(message)
        }
    }

    // ========================================
    // BACKEND: Monitorar estado WebSocket
    // ========================================
    //  MANTER: Monitora mudanças no estado WebSocket
    private fun startWebSocketMonitoring() {
        viewModelScope.launch {
            webSocketService.webSocketState.collect { state ->
                _webSocketState.value = state
                
                // Atualiza UI baseado no status da conexão
                when (state.connectionStatus) {
                    ConnectionStatus.CONNECTED -> {
                        _uiState.value = _uiState.value.copy(
                            statusText = "Conectado ao servidor via WebSocket"
                        )
                    }
                    ConnectionStatus.CONNECTING -> {
                        _uiState.value = _uiState.value.copy(
                            statusText = "Conectando ao servidor..."
                        )
                    }
                    ConnectionStatus.DISCONNECTED -> {
                        _uiState.value = _uiState.value.copy(
                            statusText = "Desconectado do servidor"
                        )
                    }
                    ConnectionStatus.RECONNECTING -> {
                        _uiState.value = _uiState.value.copy(
                            statusText = "Reconectando... (${state.reconnectAttempts}/${state.maxReconnectAttempts})"
                        )
                    }
                    ConnectionStatus.ERROR -> {
                        _uiState.value = _uiState.value.copy(
                            statusText = "Erro na conexão: ${state.lastError}"
                        )
                    }
                }
            }
        }
    }

    // ========================================
    // BACKEND: Conectar ao servidor WebSocket
    // ========================================
    //  MANTER: Conecta ao servidor WebSocket
    fun connectToServer(serverUrl: String): Boolean {
        return recordingController.connectToServer(serverUrl)
    }

    // ========================================
    // BACKEND: Desconectar do servidor WebSocket
    // ========================================
    //  MANTER: Desconecta do servidor WebSocket
    fun disconnectFromServer() {
        recordingController.disconnectFromServer()
    }

    // ========================================
    // BACKEND: Funções de Gravação e Processamento
    // ========================================
    //  MANTER: Lógica principal de gravação
    fun startRecording(filePath: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(
                isRecording = true,
                statusText = "Iniciando gravação...",
                isPlaying = false
            )
            
            // Para qualquer reprodução anterior
            audioService.stopPlayback()

            try {
                // Verifica se está conectado ao servidor
                if (!webSocketService.webSocketState.value.isConnected) {
                    _uiState.value = _uiState.value.copy(
                        statusText = "Conectando ao servidor antes de gravar..."
                    )
                    
                    // Tenta conectar ao servidor padrão
                    val connected = connectToServer("ws://10.0.2.2:8080/ws")
                    if (!connected) {
                        _uiState.value = _uiState.value.copy(
                            isRecording = false,
                            statusText = "Erro: Não foi possível conectar ao servidor"
                        )
                        return@launch
                    }
                }
                
                // Inicia a gravação usando o controller
                val success = recordingController.startRecording(filePath)
                
                if (success) {
                    _uiState.value = _uiState.value.copy(
                        statusText = "Gravando e transmitindo via WebSocket...",
                        isRecording = true
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isRecording = false,
                        statusText = "Erro ao iniciar gravação"
                    )
                }
                
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = _uiState.value.copy(
                    isRecording = false,
                    statusText = "Erro ao iniciar gravação: ${e.message}"
                )
            }
        }
    }

    // ========================================
    // BACKEND: Processar respostas de áudio
    // ========================================
    //  MANTER: Processa respostas de áudio recebidas via WebSocket
    private fun handleAudioResponse(message: AudioResponseMessage) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                statusText = "Resposta de áudio recebida: ${message.responseType}"
            )
            
            // Aqui você pode implementar a lógica para reproduzir o áudio de resposta
            // ou processar outros tipos de resposta
            when (message.responseType) {
                ResponseType.PROCESSED_AUDIO -> {
                    // Reproduz áudio processado
                    // TODO: Implementar reprodução do áudio de resposta
                }
                ResponseType.ANALYSIS_RESULT -> {
                    // Mostra resultado da análise
                    _uiState.value = _uiState.value.copy(
                        statusText = "Análise concluída: ${message.metadata.responseId}"
                    )
                }
                ResponseType.TRANSCRIPTION -> {
                    // Mostra transcrição
                    _uiState.value = _uiState.value.copy(
                        statusText = "Transcrição recebida"
                    )
                }
                ResponseType.EMOTION_DETECTION -> {
                    // Mostra detecção de emoção
                    _uiState.value = _uiState.value.copy(
                        statusText = "Análise de emoção concluída"
                    )
                }
            }
        }
    }

    // ========================================
    // BACKEND: Processar confirmação de áudio processado
    // ========================================
    //  MANTER: Processa confirmação de processamento
    private fun handleAudioProcessed(message: AudioProcessedMessage) {
        viewModelScope.launch {
            if (message.result.success) {
                _uiState.value = _uiState.value.copy(
                    statusText = "Áudio processado com sucesso em ${message.processingTime}ms"
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    statusText = "Erro no processamento: ${message.result.message}"
                )
            }
        }
    }

    // ========================================
    // BACKEND: Processar mudanças de status de conexão
    // ========================================
    //  MANTER: Processa mudanças no status da conexão
    private fun handleConnectionStatus(message: ConnectionStatusMessage) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                statusText = "Status da conexão: ${message.status}"
            )
        }
    }

    fun stopRecording() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(
                isRecording = false,
                isProcessing = true,
                statusText = "Parando gravação..."
            )
            
            try {
                // Para a gravação e cria temporária
                val tempRecording = recordingController.stopRecording()
                
                if (tempRecording != null) {
                    // Mostra diálogo de renomeação
                    _uiState.value = _uiState.value.copy(
                        isProcessing = false,
                        tempRecording = tempRecording,
                        showRenameDialog = true,
                        renameDialogText = tempRecording.suggestedName,
                        statusText = "Renomeie sua gravação antes de salvar"
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isProcessing = false,
                        statusText = "Erro: Não foi possível criar gravação temporária"
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    statusText = "Erro ao parar gravação: ${e.message}"
                )
            }
        }
    }

    // ========================================
    // BACKEND: Funções de Renomeação
    // ========================================
    //  MANTER: Lógica de renomeação e confirmação
    fun updateRenameText(text: String) {
        _uiState.value = _uiState.value.copy(renameDialogText = text)
    }

    fun confirmRecordingName() {
        viewModelScope.launch {
            val currentTemp = _uiState.value.tempRecording
            val customName = _uiState.value.renameDialogText.trim()
            
            if (currentTemp != null && customName.isNotEmpty()) {
                _uiState.value = _uiState.value.copy(isSaving = true, statusText = "Salvando gravação...")
                
                try {
                    val success = recordingController.confirmAndSaveRecording(customName)
                    
                    if (success) {
                        _uiState.value = _uiState.value.copy(
                            isSaving = false,
                            tempRecording = null,
                            showRenameDialog = false,
                            renameDialogText = "",
                            statusText = "Gravação salva com sucesso!"
                        )
                        
                        // Recarrega a lista de arquivos
                        loadWavFiles { emptyList() }
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isSaving = false,
                            statusText = "Erro ao salvar gravação"
                        )
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        statusText = "Erro ao salvar: ${e.message}"
                    )
                }
            }
        }
    }

    fun cancelRecording() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isSaving = true,
                statusText = "Cancelando gravação..."
            )
            
            try {
                val success = recordingController.cancelRecording()
                
                if (success) {
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        tempRecording = null,
                        showRenameDialog = false,
                        renameDialogText = "",
                        statusText = "Gravação cancelada"
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        statusText = "Erro ao cancelar gravação"
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    statusText = "Erro ao cancelar: ${e.message}"
                )
            }
        }
    }

    // ========================================
    // BACKEND: Funções de Reprodução
    // ========================================
    //  MANTER: Lógica de reprodução de áudio
    fun playAudioFile(filePath: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                statusText = "Reproduzindo...",
                isPlaying = true,
                isPaused = false,
                currentPlayingFile = filePath,
                hasActiveAudio = true
            )
            val success = withContext(Dispatchers.IO) {
                audioService.playLocalWavFile(filePath)
            }
            if (!success) {
                _uiState.value = _uiState.value.copy(
                    statusText = "Erro ao reproduzir o arquivo.",
                    isPlaying = false,
                    hasActiveAudio = false
                )
            }
        }
    }

    fun pausePlayback() {
        audioService.pausePlayback()
        _uiState.value = _uiState.value.copy(
            isPlaying = false,
            isPaused = true,
            statusText = "Pausado"
        )
    }

    fun resumePlayback() {
        audioService.resumePlayback(viewModelScope)
        _uiState.value = _uiState.value.copy(
            isPlaying = true,
            isPaused = false,
            statusText = "Reproduzindo..."
        )
    }

    fun stopPlayback() {
        audioService.stopPlayback()
        _uiState.value = _uiState.value.copy(
            isPlaying = false,
            isPaused = false,
            currentPlayingFile = null,
            currentPosition = 0,
            totalDuration = 0,
            playbackProgress = 0f,
            hasActiveAudio = false,
            statusText = "Pronto para gravar"
        )
    }

    fun seekTo(timeMs: Long) {
        // Atualiza imediatamente a posição na UI para feedback visual instantâneo
        val boundedTime = timeMs.coerceIn(0L, _uiState.value.totalDuration)
        
        // Atualiza a UI imediatamente para resposta rápida
        _uiState.value = _uiState.value.copy(
            currentPosition = boundedTime,
            playbackProgress = if (_uiState.value.totalDuration > 0) {
                boundedTime.toFloat() / _uiState.value.totalDuration.toFloat()
            } else 0f
        )
        
        // Executa o seek no AudioService de forma assíncrona
        viewModelScope.launch {
            try {
                // Pequeno delay para permitir que a UI se atualize
                delay(50)
                audioService.seekTo(boundedTime, viewModelScope)
            } catch (e: Exception) {
                println("Erro durante seek no ViewModel: ${e.message}")
                e.printStackTrace()
            }
        }
    }
    
    // ========================================
    // BACKEND: Funções para testar a API
    // ========================================
    //  MANTER: Testes de conexão com a API
    fun testAPI() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                statusText = "Testando conexão com API..."
            )
            
            val apiEndpoint = "http://10.0.2.2:5000/upload"
            val result = audioService.testAPIConnection(apiEndpoint)
            
            _uiState.value = _uiState.value.copy(
                statusText = if (result) "API conectada com sucesso!" else "Falha na conexão com a API"
            )
        }
    }

    // ========================================
    // BACKEND: Funções para testar conectividade básica
    // ========================================
    //  MANTER: Testes de conectividade básica
    fun testBasicConnectivity() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                statusText = "Testando conectividade básica..."
            )
            
            val apiEndpoint = "http://10.0.2.2:5000/upload"
            val result = audioService.testBasicConnectivity(apiEndpoint)
            
            _uiState.value = _uiState.value.copy(
                statusText = if (result) "Servidor respondendo!" else "Servidor não responde"
            )
        }
    }

    // ========================================
    // BACKEND: Funções para carregar arquivos e monitorar a reprodução
    // ========================================
    //  MANTER: Carregamento de arquivos e monitoramento de reprodução
    fun loadWavFiles(listFilesProvider: () -> List<File>) {
        viewModelScope.launch {
            val files = withContext(Dispatchers.IO) {
                listFilesProvider()
            }
            _uiState.value = _uiState.value.copy(wavFiles = files)
        }
    }

    private fun startPlaybackMonitor() {
        viewModelScope.launch {
            while (true) {
                try {
                    val isPlayingFromService = audioService.isCurrentlyPlaying()
                    val totalDuration = audioService.getTotalPlaybackDuration()
                    val currentPosition = audioService.getCurrentPlaybackPosition()
                    val currentPlayingFile = audioService.getCurrentPlayingFile()

                    // Determina se há áudio ativo (reproduzindo ou pausado)
                    val hasActiveAudio = currentPlayingFile != null && totalDuration > 0

                    // Atualiza o estado apenas se houver uma mudança significativa
                    if (_uiState.value.isPlaying != isPlayingFromService ||
                        _uiState.value.totalDuration != totalDuration ||
                        _uiState.value.hasActiveAudio != hasActiveAudio ||
                        currentPlayingFile != _uiState.value.currentPlayingFile) {

                        _uiState.value = _uiState.value.copy(
                            isPlaying = isPlayingFromService,
                            currentPosition = currentPosition,
                            totalDuration = totalDuration,
                            playbackProgress = if (totalDuration > 0) currentPosition.toFloat() / totalDuration.toFloat() else 0f,
                            hasActiveAudio = hasActiveAudio,
                            currentPlayingFile = currentPlayingFile
                        )
                    } else if (isPlayingFromService && !_uiState.value.isPaused && hasActiveAudio) {
                        // Atualiza apenas a posição durante a reprodução normal
                        // Usa tolerância maior durante operações de seek para evitar conflitos
                        val positionDifference = kotlin.math.abs(_uiState.value.currentPosition - currentPosition)
                        if (positionDifference > 200) { // Tolerância reduzida para 200ms para melhor sincronização
                            _uiState.value = _uiState.value.copy(
                                currentPosition = currentPosition,
                                playbackProgress = if (totalDuration > 0) currentPosition.toFloat() / totalDuration.toFloat() else 0f
                            )
                        }
                    }

                    delay(500) // Atualiza a cada 500ms para melhor responsividade
                } catch (e: Exception) {
                    println("Erro no monitor de reprodução: ${e.message}")
                    e.printStackTrace()
                    delay(2000) // Em caso de erro, aguarda mais tempo
                }
            }
        }
    }
}