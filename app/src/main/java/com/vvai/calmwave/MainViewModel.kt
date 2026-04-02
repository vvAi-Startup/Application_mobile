package com.vvai.calmwave

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.vvai.calmwave.data.model.AudioProcessingMetrics
import com.vvai.calmwave.data.repository.AnalyticsRepository
import com.vvai.calmwave.util.FunnelAnalyticsTracker
import com.vvai.calmwave.util.getPlaybackMonitorPollingMs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
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
    
    // ✅ NOVO: Sincroniza apenas metadados em /api/audios/sync via AnalyticsRepository

    // Processador local de denoising (offline)
    private val localDenoiser = LocalAudioDenoiser(context)
    
    // Repositório de analytics para enviar métricas ao backend
    private val analyticsRepository = AnalyticsRepository(context)

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
        val isUploading: Boolean = false,
        val uploadProgress: Int = 0 // Progresso do upload em porcentagem
    )

    // Variável para armazenar o caminho do arquivo de gravação atual
    private var currentRecordingPath: String? = null
    // Sinaliza quando a gravação terminou por completo (arquivo WAV com header atualizado)
    private var recordingDone = CompletableDeferred<Unit>()
    private var onProcessedAudioSaved: ((File) -> Unit)? = null
    
    // Métricas de processamento
    private var recordingStartTime: Long = 0
    private var processingStartTime: Long = 0

    fun setProcessedAudioSaveCallback(callback: (File) -> Unit) {
        onProcessedAudioSaved = callback
    }

    // Bloco de inicialização para o ViewModel
    init {
        // Inicializa o AudioService com o contexto da aplicação
        audioService.init(context.applicationContext)
        // Inicializa o modelo de denoising local (ONNX) em background
        viewModelScope.launch(Dispatchers.IO) {
            val loaded = localDenoiser.initialize()
            if (loaded) {
                Log.i("MainViewModel", "✅ Modelo de denoising local carregado com sucesso")
            } else {
                Log.e("MainViewModel", "❌ Modelo de denoising local INDISPONÍVEL — áudio não será processado")
            }
        }
        // Inicia um loop de atualização de reprodução
        startPlaybackMonitor()
    }

    override fun onCleared() {
        super.onCleared()
        RecordingForegroundService.stop(context.applicationContext)
        localDenoiser.release()
    }

    // Funções de Gravação e Processamento (modo offline com streaming em tempo real)
    fun startRecording(filePath: String) {
        // Guarda o caminho ANTES de suspender, para que stopRecordingAndProcess o veja
        currentRecordingPath = filePath
        recordingDone = CompletableDeferred()
        recordingStartTime = System.currentTimeMillis()

        // Mantém o processo em prioridade de foreground durante gravação/processamento
        RecordingForegroundService.start(context.applicationContext)

        viewModelScope.launch(Dispatchers.IO) {
            // Registra evento de início de gravação
            analyticsRepository.logScreenView("GravarActivity")
            
            _uiState.value = _uiState.value.copy(
                isRecording = true,
                statusText = "Gravando...",
                isPlaying = false
            )

            // Para qualquer reprodução anterior
            audioService.stopPlayback()

            // Inicializa o streaming de áudio processado (AudioTrack + arquivo de saída)
            if (localDenoiser.isReady()) {
                audioService.initStreamingPlayback(context)
                Log.i("MainViewModel", "Streaming de denoising em tempo real ativado")
            }

            // Canal não-bloqueante: o callback enfileira dados instantaneamente,
            // uma coroutine separada consome e processa com ONNX sem bloquear a gravação
            // Capacidade otimizada para buffering adequado
            val pcmChannel = Channel<ByteArray>(capacity = 32)

            // ── Coroutine CONSUMIDORA (processa ONNX em thread separada) ──
            val processingJob = launch(Dispatchers.Default) {
                val segmentBytes = SignalProcessor.SEGMENT_LENGTH * 2  // 64000 bytes
                var pcmAccumulator = ByteArray(0)
                var segmentCount = 0

                // Suspende até receber dados; sai quando o canal é fechado
                for (data in pcmChannel) {
                    pcmAccumulator += data

                    // Processa todos os segmentos completos de 2 s acumulados
                    while (pcmAccumulator.size >= segmentBytes && localDenoiser.isReady()) {
                        val segmentPcm = pcmAccumulator.copyOf(segmentBytes)
                        pcmAccumulator = pcmAccumulator.copyOfRange(segmentBytes, pcmAccumulator.size)

                        segmentCount++
                        val processedPcm = localDenoiser.processChunkPcm(segmentPcm)
                        if (processedPcm != null) {
                            audioService.streamProcessedChunk(processedPcm)
                            // Pequeno delay para sincronização e prevenção de buffer underrun
                            delay(10)
                            Log.d("MainViewModel", "Segmento $segmentCount: ${processedPcm.size} bytes processados e tocados")
                        }
                    }
                }

                // Canal fechado — processa resíduo acumulado (último segmento < 2 s)
                if (pcmAccumulator.isNotEmpty() && localDenoiser.isReady()) {
                    val actualSamples = pcmAccumulator.size / 2
                    val paddedPcm = if (pcmAccumulator.size < segmentBytes) {
                        pcmAccumulator + ByteArray(segmentBytes - pcmAccumulator.size)
                    } else {
                        pcmAccumulator
                    }
                    segmentCount++
                    val processedPcm = localDenoiser.processChunkPcm(paddedPcm, actualSamples)
                    if (processedPcm != null) {
                        audioService.streamProcessedChunk(processedPcm)
                        delay(10)
                        Log.d("MainViewModel", "Segmento final $segmentCount: ${processedPcm.size} bytes (resíduo)")
                    }
                }
                Log.i("MainViewModel", "Processamento concluído: $segmentCount segmentos totais")
            }

            // ── Callback PRODUTOR (executa na thread de gravação — NÃO bloqueia) ──
            wavRecorder.setChunkCallback { chunkData, chunkIndex, overlapSize ->
                // Remove o overlap (já pertence ao chunk anterior)
                val newData = if (overlapSize > 0 && chunkData.size > overlapSize) {
                    chunkData.copyOfRange(overlapSize, chunkData.size)
                } else {
                    chunkData
                }
                // Enfileira instantaneamente — nunca bloqueia a thread de gravação
                pcmChannel.trySend(newData)
                Log.d("MainViewModel", "Chunk $chunkIndex enfileirado: ${newData.size} bytes")
            }

            try {
                // Gravação offline — sem WebSocket
                audioService.startBluetoothSco()
                // startRecording suspende até isRecording = false (arquivo WAV finalizado)
                wavRecorder.startRecording(filePath)
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = _uiState.value.copy(
                    isRecording = false,
                    statusText = "Erro ao iniciar gravação: ${e.message}"
                )
                audioService.stopBluetoothSco()
                RecordingForegroundService.stop(context.applicationContext)
            } finally {
                // Fecha o canal — sinaliza à coroutine consumidora que não há mais dados
                pcmChannel.close()
                // Aguarda a consumidora terminar de processar todos os segmentos restantes
                processingJob.join()

                // Limpa o callback
                wavRecorder.setChunkCallback { _, _, _ -> }

                // Sinaliza que o arquivo WAV está completo (header atualizado)
                recordingDone.complete(Unit)
            }
        }
    }

    fun stopRecordingAndProcess() {
        processingStartTime = System.currentTimeMillis()
        
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(
                isRecording = false,
                isProcessing = true,
                statusText = "Finalizando processamento...",
                currentPosition = 0
            )
            
            try {
                wavRecorder.stopRecording()
                audioService.stopBluetoothSco()

                // Aguarda a coroutine de gravação finalizar (flush + header WAV + último chunk)
                recordingDone.await()

                // Finaliza o streaming e obtém o caminho do arquivo processado
                val processedPath = audioService.stopStreamingPlayback()
                
                val processingEndTime = System.currentTimeMillis()
                val processingTimeMs = processingEndTime - processingStartTime
                val recordingDurationMs = processingStartTime - recordingStartTime

                if (processedPath != null) {
                    val processedFile = File(processedPath)
                    val originalFile = currentRecordingPath?.let { File(it) }
                    
                    // Coleta métricas de processamento
                    val metrics = AudioProcessingMetrics(
                        filename = processedFile.name,
                        processingTimeMs = processingTimeMs,
                        recordingDurationMs = recordingDurationMs,
                        originalFileSizeBytes = originalFile?.length() ?: 0L,
                        processedFileSizeBytes = processedFile.length(),
                        sampleRate = 16000,
                        deviceOrigin = "Android",
                        wasOfflineProcessed = true,
                        processedAt = processingEndTime,
                        userId = null, // TODO: Adicionar user ID quando implementar autenticação
                        modelVersion = "1.0",
                        errorOccurred = false
                    )
                    
                    // Registra métricas localmente (sincroniza automaticamente quando online)
                    analyticsRepository.logAudioProcessingMetrics(metrics)
                    analyticsRepository.logAudioRecorded(
                        durationMs = recordingDurationMs,
                        fileSizeBytes = originalFile?.length() ?: 0L
                    )
                    FunnelAnalyticsTracker.trackFirstRecordingCompleted(
                        context = context,
                        repository = analyticsRepository,
                        recordingDurationMs = recordingDurationMs,
                        processed = true
                    )
                    
                    // Tenta fazer upload do áudio (se online)
                    if (processedFile.exists() && processedFile.length() > 44) {
                        analyticsRepository.uploadAudioFile(processedFile, metrics)
                    }
                    
                    onProcessedAudioSaved?.invoke(processedFile)
                    _uiState.value = _uiState.value.copy(
                        statusText = "✅ Áudio processado com IA local!"
                    )
                    Log.i("MainViewModel", "✅ Denoising streaming concluído: $processedPath")
                } else if (currentRecordingPath != null) {
                    val errorMessage = "Modelo IA indisponível"
                    
                    // Registra falha
                    val metrics = AudioProcessingMetrics(
                        filename = currentRecordingPath?.let { File(it).name } ?: "unknown",
                        processingTimeMs = processingTimeMs,
                        recordingDurationMs = recordingDurationMs,
                        originalFileSizeBytes = currentRecordingPath?.let { File(it).length() } ?: 0L,
                        processedFileSizeBytes = 0L,
                        wasOfflineProcessed = false,
                        errorOccurred = true,
                        errorMessage = errorMessage
                    )
                    analyticsRepository.logAudioProcessingMetrics(metrics)
                    
                    // Fallback: nenhum processamento ocorreu (modelo não carregou)
                    _uiState.value = _uiState.value.copy(
                        statusText = "⚠️ Modelo IA indisponível. Áudio original salvo."
                    )
                    Log.w("MainViewModel", "Streaming não produziu saída — modelo indisponível?")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                val errorMessage = e.message
                
                // Registra erro
                val metrics = AudioProcessingMetrics(
                    filename = currentRecordingPath?.let { File(it).name } ?: "unknown",
                    processingTimeMs = System.currentTimeMillis() - processingStartTime,
                    recordingDurationMs = processingStartTime - recordingStartTime,
                    originalFileSizeBytes = currentRecordingPath?.let { File(it).length() } ?: 0L,
                    processedFileSizeBytes = 0L,
                    wasOfflineProcessed = false,
                    errorOccurred = true,
                    errorMessage = errorMessage
                )
                analyticsRepository.logAudioProcessingMetrics(metrics)
                
                _uiState.value = _uiState.value.copy(
                    statusText = "Erro ao parar gravação: ${e.message}"
                )
            } finally {
                _uiState.value = _uiState.value.copy(
                    isProcessing = false
                )
                RecordingForegroundService.stop(context.applicationContext)
            }
        }
    }
    
    // ✅ NOVO: Sincroniza apenas metadados de gravação/processamento em /api/audios/sync
    // Use AnalyticsRepository.uploadAudioFile() para sincronizar áudios
    
    /**
     * Cancela o upload em progresso (se implementado no futuro)
     */
    fun cancelUpload() {
        if (_uiState.value.isUploading) {
            _uiState.value = _uiState.value.copy(
                isUploading = false,
                uploadProgress = 0,
                statusText = "Upload cancelado pelo usuário."
            )
        }
    }

    fun saveProcessedAudio(): String? {
        val processedFile = audioService.getLatestProcessedFile()
        
        return processedFile?.let { file ->
            // Só tenta salvar se o arquivo processado existe e tem conteúdo
            if (file.exists() && file.length() > 44) { // 44 bytes = cabeçalho WAV mínimo
                // Esta função será chamada pela MainActivity para salvar no Downloads
                onProcessedAudioSaved?.invoke(file) // Chama o callback com o arquivo processado
                return file.absolutePath
            } else {
                null
            }
        }
    }

    // Funções de Reprodução
    fun playAudioFile(filePath: String) {
        viewModelScope.launch {
            // Registra evento de reprodução de áudio
            val file = File(filePath)
            val isProcessed = filePath.contains("denoised") || filePath.contains("processed")
            analyticsRepository.logAudioPlayed(
                audioId = null,
                filename = file.name,
                isProcessed = isProcessed
            )
            
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
                Log.e("MainViewModel", "Erro durante seek no ViewModel", e)
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
        val pollingMs = getPlaybackMonitorPollingMs(context)
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

                    delay(pollingMs)
                } catch (e: Exception) {
                    Log.e("MainViewModel", "Erro no monitor de reprodução", e)
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