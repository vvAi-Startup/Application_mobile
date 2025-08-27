package com.vvai.calmwave

import android.content.Context
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
        val recordingDuration: Long = 0L // Duração da gravação em milissegundos
    )

    // Variável para armazenar o caminho do arquivo de gravação atual
    private var currentRecordingPath: String? = null
    
    // Variável para rastrear o tempo de gravação
    private var recordingStartTime: Long = 0L

    // Bloco de inicialização para o ViewModel
    init {
        // Inicializa o AudioService com o contexto da aplicação
        audioService.init(context.applicationContext)
        // Inicia um loop de atualização de reprodução
        startPlaybackMonitor()
    }

    // Funções de Gravação e Processamento
    fun startRecording(filePath: String) {
        viewModelScope.launch(Dispatchers.IO) {
            // Inicializa o tempo de gravação
            recordingStartTime = System.currentTimeMillis()
            
            _uiState.value = _uiState.value.copy(
                isRecording = true,
                statusText = "Iniciando gravação...",
                isPlaying = false,
                recordingDuration = 0L
            )
            
            // Para qualquer reprodução anterior
            audioService.stopPlayback()

            try {
                // Configura o callback para enviar chunks em tempo real
                val sessionId = java.util.UUID.randomUUID().toString()
                val apiEndpoint = "http://10.0.2.2:5000/upload"
                
                wavRecorder.setChunkCallback { chunkData, chunkIndex ->
                    viewModelScope.launch {
                        println("MainViewModel: Recebendo chunk $chunkIndex do WavRecorder")
                        
                        // Atualiza o status para mostrar que está enviando chunk
                        _uiState.value = _uiState.value.copy(
                            statusText = "Enviando chunk ${chunkIndex + 1} para API..."
                        )
                        
                        audioService.sendChunkToAPI(
                            chunkData = chunkData,
                            sessionId = sessionId,
                            chunkIndex = chunkIndex,
                            apiEndpoint = apiEndpoint
                        )
                        
                        // Atualiza o status de volta para gravação
                        _uiState.value = _uiState.value.copy(
                            statusText = "Gravando... (próximo chunk em 5s)"
                        )
                    }
                }
                
                // Inicia o bluetooth SCO e a gravação
                audioService.startBluetoothSco()
                wavRecorder.startRecording(filePath)
                currentRecordingPath = filePath
                
                _uiState.value = _uiState.value.copy(
                    statusText = "Gravando... (primeiro chunk será enviado em 5s)"
                )
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
            // Para o rastreamento do tempo de gravação
            recordingStartTime = 0L
            
            _uiState.value = _uiState.value.copy(
                isRecording = false,
                isProcessing = true,
                statusText = "Parando gravação e processando...",
                recordingDuration = 0L
            )
            try {
                wavRecorder.stopRecording()
                audioService.stopBluetoothSco()

                val audioFile = currentRecordingPath?.let { File(it) }
                if (audioFile?.exists() == true) {
                    audioService.sendAndPlayWavFile(
                        filePath = audioFile.absolutePath,
                        apiEndpoint = apiEndpoint
                    )
                    _uiState.value = _uiState.value.copy(
                        statusText = "Gravação processada com sucesso!"
                    )
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
                loadWavFiles { emptyList() } // Será atualizado pelo MainActivity
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
    
    // Função para testar a API manualmente
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

    // Função para testar conectividade básica
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

                    // Calcula o tempo de gravação se estiver gravando
                    val recordingDuration = if (_uiState.value.isRecording && recordingStartTime > 0) {
                        System.currentTimeMillis() - recordingStartTime
                    } else {
                        0L
                    }

                    // Atualiza o estado apenas se houver uma mudança significativa
                    if (_uiState.value.isPlaying != isPlayingFromService ||
                        _uiState.value.totalDuration != totalDuration ||
                        _uiState.value.hasActiveAudio != hasActiveAudio ||
                        currentPlayingFile != _uiState.value.currentPlayingFile ||
                        _uiState.value.recordingDuration != recordingDuration) {

                        _uiState.value = _uiState.value.copy(
                            isPlaying = isPlayingFromService,
                            currentPosition = currentPosition,
                            totalDuration = totalDuration,
                            playbackProgress = if (totalDuration > 0) currentPosition.toFloat() / totalDuration.toFloat() else 0f,
                            hasActiveAudio = hasActiveAudio,
                            currentPlayingFile = currentPlayingFile,
                            recordingDuration = recordingDuration
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