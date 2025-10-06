package com.vvai.calmwave

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
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
    private val context: Context
) : ViewModel() {

    // Definição do Estado da UI
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    data class UiState(
        val isRecording: Boolean = false,
        val isProcessing: Boolean = false,
        val statusText: String = "Pronto para gravar",
        val isPlaying: Boolean = false,
        val isPaused: Boolean = false,
        val currentPosition: Long = 0,
        val totalDuration: Long = 0,
        val playbackProgress: Float = 0f,
        val wavFiles: List<File> = emptyList(),
        val currentPlayingFile: String? = null,
        val hasActiveAudio: Boolean = false, // Para manter a barra visível mesmo quando pausado
        // Estados para áudio do WebSocket
        val isWebSocketAudioPlaying: Boolean = false,
        val isWebSocketAudioPaused: Boolean = false,
        val webSocketAudioPosition: Long = 0,
        val webSocketAudioDuration: Long = 0,
        val webSocketAudioProgress: Float = 0f,
        val hasWebSocketAudio: Boolean = false
    )

    // Variável para armazenar o caminho do arquivo de gravação atual
    private var currentRecordingPath: String? = null
    private var onProcessedAudioSaved: ((File) -> Unit)? = null

    fun setProcessedAudioSaveCallback(callback: (File) -> Unit) {
        onProcessedAudioSaved = callback
    }

    // Bloco de inicialização para o ViewModel
    init {
        // Inicializa o AudioService com o contexto da aplicação
        audioService.init(context.applicationContext)
        // Configura o callback para quando áudio WebSocket é recebido
        audioService.setWebSocketAudioCallback {
            onWebSocketAudioReceived()
        }
        // Inicia um loop de atualização de reprodução
        startPlaybackMonitor()
    }

    // Funções de Gravação e Processamento
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
                // Configura conexão WebSocket e callback para envio de chunks
                val wsUrl = Config.wsStreamUrl
                audioService.connectWebSocket(wsUrl, context,
                    onConnected = {
                        println("WebSocket conectado")
                        // Inicia o monitoramento do áudio WebSocket quando conectar
                        startWebSocketAudio()
                    },
                    onFailure = { e ->
                        println("Falha no WebSocket: ${e.message}")
                        // Se falhar a conexão, não exibe a barra de progresso
                        _uiState.value = _uiState.value.copy(
                            hasWebSocketAudio = false
                        )
                    }
                )
                
                wavRecorder.setChunkCallback { chunkData, chunkIndex ->
                    viewModelScope.launch {
                        println("MainViewModel: Recebendo chunk $chunkIndex do WavRecorder")
                        
                        // Atualiza o status para mostrar que está enviando chunk
                        _uiState.value = _uiState.value.copy(
                            statusText = "Enviando chunk ${chunkIndex + 1} via WebSocket..."
                        )
                        
                        audioService.sendAudioChunkViaWebSocket(chunkData)
                        
                        // Atualiza o status de volta para gravação
                        _uiState.value = _uiState.value.copy(
                            statusText = "Gravando... (próximo chunk em 10s)"
                        )
                    }
                }
                
                // Inicia o bluetooth SCO e a gravação
                audioService.startBluetoothSco()
                wavRecorder.startRecording(filePath)
                currentRecordingPath = filePath
                
                _uiState.value = _uiState.value.copy(statusText = "Gravando... (primeiro chunk será enviado em 10s)")
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = _uiState.value.copy(
                    isRecording = false,
                    statusText = "Erro ao iniciar gravação: ${e.message}"
                )
                audioService.stopBluetoothSco()
            }
        }
    }

    fun stopRecordingAndProcess(apiEndpoint: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(
                isRecording = false,
                isProcessing = true,
                statusText = "Parando gravação e processando...",
                currentPosition = 0 // Resetar contador ao encerrar
            )
            try {
                wavRecorder.stopRecording()
                audioService.stopBluetoothSco()
                // Desconecta WS e finaliza arquivo processado
                audioService.disconnectWebSocket()

                val audioFile = currentRecordingPath?.let { File(it) }
                if (audioFile?.exists() == true) {
                    // Verifica se há um arquivo processado para salvar
                    val processedFilePath = saveProcessedAudio()
                    if (processedFilePath != null) {
                        val processedFile = File(processedFilePath)
                        // Chama o callback para salvar automaticamente no Downloads
                        onProcessedAudioSaved?.invoke(processedFile)
                        _uiState.value = _uiState.value.copy(
                            statusText = "Áudio gravado e processado salvos com sucesso!"
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            statusText = "Áudio gravado salvo. Áudio processado não disponível."
                        )
                    }
                } else {
                    _uiState.value = _uiState.value.copy(
                        statusText = "Erro: Arquivo não encontrado para processamento."
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = _uiState.value.copy(
                    statusText = "Erro ao parar gravação: ${e.message}"
                )
            } finally {
                _uiState.value = _uiState.value.copy(
                    isProcessing = false
                )
                // Recarrega a lista de arquivos após o processamento
                // A MainActivity irá recarregar automaticamente via seu callback
            }
        }
    }

    fun saveProcessedAudio(): String? {
        return audioService.getLatestProcessedFile()?.let { processedFile ->
            // Só tenta salvar se o arquivo processado existe e tem conteúdo
            if (processedFile.exists() && processedFile.length() > 44) { // 44 bytes = cabeçalho WAV mínimo
                // Esta função será chamada pela MainActivity para salvar no Downloads
                onProcessedAudioSaved?.invoke(processedFile) // Chama o callback com o arquivo processado
                return processedFile.absolutePath
            } else {
                null
            }
        }
    }

    // Funções de Reprodução
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
    
    // Incrementa o tempo atual de gravação (em ms)
    fun incrementCurrentPosition(deltaMs: Long) {
        _uiState.value = _uiState.value.copy(
            currentPosition = _uiState.value.currentPosition + deltaMs
        )
    }

    // Função para testar a API manualmente
    fun testAPI() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                statusText = "Testando conexão com API..."
            )
            
            val apiEndpoint = Config.uploadUrl
            val result = audioService.testAPIConnection(apiEndpoint)
            
            _uiState.value = _uiState.value.copy(
                statusText = if (result) "API conectada com sucesso!" else "Falha na conexão com a API"
            )
        }
    }

    // Função para testar conectividade básica
    fun testBasicConnectivity() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                statusText = "Testando conectividade básica..."
            )
            
            val apiEndpoint = Config.uploadUrl
            val result = audioService.testBasicConnectivity(apiEndpoint)
            
            _uiState.value = _uiState.value.copy(
                statusText = if (result) "Servidor respondendo!" else "Servidor não responde"
            )
        }
    }

    // Funções para carregar arquivos e monitorar a reprodução
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

    // Pausa a gravação
    fun pauseRecording() {
        wavRecorder.pauseRecording()
        _uiState.value = _uiState.value.copy(isPaused = true)
    }

    // Retoma a gravação
    fun resumeRecording() {
        wavRecorder.resumeRecording()
        _uiState.value = _uiState.value.copy(isPaused = false)
    }

    // Funções para controle do áudio do WebSocket
    fun startWebSocketAudio() {
        _uiState.value = _uiState.value.copy(
            isWebSocketAudioPlaying = true,
            isWebSocketAudioPaused = false,
            hasWebSocketAudio = true,
            webSocketAudioPosition = 0
        )
        // Inicia o monitoramento do progresso do áudio do WebSocket
        startWebSocketAudioMonitor()
    }

    fun pauseWebSocketAudio() {
        _uiState.value = _uiState.value.copy(
            isWebSocketAudioPlaying = false,
            isWebSocketAudioPaused = true
        )
        // Aqui você pode pausar o AudioTrack se necessário
        audioService.pauseWebSocketAudio()
    }

    fun resumeWebSocketAudio() {
        _uiState.value = _uiState.value.copy(
            isWebSocketAudioPlaying = true,
            isWebSocketAudioPaused = false
        )
        // Aqui você pode retomar o AudioTrack se necessário
        audioService.resumeWebSocketAudio()
    }

    fun stopWebSocketAudio() {
        _uiState.value = _uiState.value.copy(
            isWebSocketAudioPlaying = false,
            isWebSocketAudioPaused = false,
            hasWebSocketAudio = false,
            webSocketAudioPosition = 0,
            webSocketAudioDuration = 0,
            webSocketAudioProgress = 0f
        )
        audioService.stopWebSocketAudio()
    }

    fun seekWebSocketAudio(positionMs: Long) {
        _uiState.value = _uiState.value.copy(
            webSocketAudioPosition = positionMs,
            webSocketAudioProgress = if (_uiState.value.webSocketAudioDuration > 0) {
                positionMs.toFloat() / _uiState.value.webSocketAudioDuration.toFloat()
            } else 0f
        )
        audioService.seekWebSocketAudio(positionMs)
    }

    private fun startWebSocketAudioMonitor() {
        viewModelScope.launch {
            var lastUpdateTime = 0L
            
            while (_uiState.value.hasWebSocketAudio) {
                try {
                    val currentTime = System.currentTimeMillis()
                    
                    // Throttling: atualiza apenas a cada 200ms para reduzir CPU
                    if (currentTime - lastUpdateTime >= 200) {
                        val position = audioService.getWebSocketAudioPosition()
                        val duration = audioService.getWebSocketAudioDuration()
                        
                        // Só atualiza se houve mudança significativa
                        val currentState = _uiState.value
                        val positionDiff = kotlin.math.abs(currentState.webSocketAudioPosition - position)
                        val durationDiff = kotlin.math.abs(currentState.webSocketAudioDuration - duration)
                        
                        if (positionDiff > 500 || durationDiff > 1000) { // 500ms ou 1s de diferença
                            val progress = if (duration > 0) position.toFloat() / duration.toFloat() else 0f
                            
                            _uiState.value = currentState.copy(
                                webSocketAudioPosition = position,
                                webSocketAudioDuration = duration,
                                webSocketAudioProgress = progress
                            )
                        }
                        
                        lastUpdateTime = currentTime
                    }
                    
                    delay(200) // Reduzido para 200ms
                } catch (e: Exception) {
                    println("Erro no monitor WebSocket: ${e.message}")
                    delay(1000) // Em caso de erro, aguarda mais tempo
                    break
                }
            }
        }
    }

    // Função para ser chamada quando áudio WebSocket é recebido
    fun onWebSocketAudioReceived() {
        if (!_uiState.value.hasWebSocketAudio) {
            startWebSocketAudio()
        }
    }
}

// Factory para criar o ViewModel com as dependências
class MainViewModelFactory(
    private val audioService: AudioService,
    private val wavRecorder: WavRecorder,
    private val context: Context
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(audioService, wavRecorder, context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
