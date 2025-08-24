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
                // Inicia a gravação
                wavRecorder.startRecording(filePath)
                currentRecordingPath = filePath
                
                _uiState.value = _uiState.value.copy(
                    statusText = "Gravando... (preparando chunks para IA)"
                )
                
                // TODO: API será implementada futuramente
                // Configura o callback para enviar chunks em tempo real
                // val sessionId = java.util.UUID.randomUUID().toString()
                // wavRecorder.setChunkCallback { chunkData, chunkIndex ->
                //     viewModelScope.launch {
                //         audioService.sendChunkToAPI(
                //             chunkData = chunkData,
                //             sessionId = sessionId,
                //             chunkIndex = chunkIndex,
                //             apiEndpoint = "http://127.0.0.1:5000/upload"
                //         )
                //     }
                // }
                
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = _uiState.value.copy(
                    isRecording = false,
                    statusText = "Erro ao iniciar gravação: ${e.message}"
                )
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

                val audioFile = currentRecordingPath?.let { File(it) }
                if (audioFile?.exists() == true) {
                    _uiState.value = _uiState.value.copy(
                        statusText = "Gravação processada com sucesso!"
                    )
                    
                    // TODO: API será implementada futuramente
                    // audioService.sendAndPlayWavFile(
                    //     filePath = audioFile.absolutePath,
                    //     apiEndpoint = apiEndpoint
                    // )
                    
                    // Recarrega a lista de arquivos após o processamento
                    loadWavFiles { 
                        val recordingsDir = File(context.getExternalFilesDir(null), "recordings")
                        if (recordingsDir.exists()) {
                            recordingsDir.listFiles()?.filter { it.extension == "wav" }?.toList() ?: emptyList()
                        } else {
                            emptyList()
                        }
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
        // Atualiza imediatamente a posição na UI para feedback visual
        _uiState.value = _uiState.value.copy(
            currentPosition = timeMs,
            playbackProgress = if (_uiState.value.totalDuration > 0) {
                timeMs.toFloat() / _uiState.value.totalDuration.toFloat()
            } else 0f
        )
        
        audioService.seekTo(timeMs, viewModelScope)
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
                val isPlayingFromService = audioService.isCurrentlyPlaying()
                val totalDuration = audioService.getTotalPlaybackDuration()
                val currentPosition = audioService.getCurrentPlaybackPosition()
                val playbackProgress = audioService.getPlaybackProgress()
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

                    // Só atualiza posição se não estiver pausado ou se estiver reproduzindo
                    val updatedPosition = if (isPlayingFromService || !_uiState.value.isPaused) {
                        currentPosition
                    } else {
                        _uiState.value.currentPosition // Mantém a posição quando pausado
                    }

                    _uiState.value = _uiState.value.copy(
                        isPlaying = isPlayingFromService,
                        currentPosition = updatedPosition,
                        totalDuration = totalDuration,
                        playbackProgress = if (totalDuration > 0) updatedPosition.toFloat() / totalDuration.toFloat() else 0f,
                        hasActiveAudio = hasActiveAudio,
                        currentPlayingFile = currentPlayingFile,
                        recordingDuration = recordingDuration
                    )
                } else if (isPlayingFromService && !_uiState.value.isPaused) {
                    // Atualiza apenas a posição durante a reprodução (não durante seek)
                    if (kotlin.math.abs(_uiState.value.currentPosition - currentPosition) > 50) { // Tolerância de 50ms
                        _uiState.value = _uiState.value.copy(
                            currentPosition = currentPosition,
                            playbackProgress = if (totalDuration > 0) currentPosition.toFloat() / totalDuration.toFloat() else 0f
                        )
                    }
                }

                delay(100) // Atualiza a cada 100ms
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