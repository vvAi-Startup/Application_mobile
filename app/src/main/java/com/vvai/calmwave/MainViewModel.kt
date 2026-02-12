package com.vvai.calmwave

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import android.util.Log
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
import com.vvai.calmwave.service.AudioUploadService

class MainViewModel(
    private val audioService: AudioService,
    private val wavRecorder: WavRecorder,
    private val context: Context
) : ViewModel() {
    
    // Serviço de upload
    private val uploadService = AudioUploadService()

    // Processador local de denoising (offline)
    private val localDenoiser = LocalAudioDenoiser(context)

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
        localDenoiser.release()
    }

    // Funções de Gravação e Processamento (modo offline com streaming em tempo real)
    fun startRecording(filePath: String) {
        // Guarda o caminho ANTES de suspender, para que stopRecordingAndProcess o veja
        currentRecordingPath = filePath
        recordingDone = CompletableDeferred()

        viewModelScope.launch(Dispatchers.IO) {
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
            val pcmChannel = Channel<ByteArray>(capacity = Channel.UNLIMITED)

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

    fun stopRecordingAndProcess(apiEndpoint: String) {
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

                if (processedPath != null) {
                    val processedFile = File(processedPath)
                    onProcessedAudioSaved?.invoke(processedFile)
                    _uiState.value = _uiState.value.copy(
                        statusText = "✅ Áudio processado com IA local!"
                    )
                    Log.i("MainViewModel", "✅ Denoising streaming concluído: $processedPath")
                } else if (currentRecordingPath != null) {
                    // Fallback: nenhum processamento ocorreu (modelo não carregou)
                    _uiState.value = _uiState.value.copy(
                        statusText = "⚠️ Modelo IA indisponível. Áudio original salvo."
                    )
                    Log.w("MainViewModel", "Streaming não produziu saída — modelo indisponível?")
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
    
    /**
     * Faz transcrição do arquivo de áudio processado usando OpenAI Whisper
     */
    private fun uploadProcessedAudio(processedFile: File, uploadUrl: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                println("=== INICIANDO TRANSCRIÇÃO ===")
                println("Arquivo: ${processedFile.absolutePath}")
                println("Tamanho: ${processedFile.length()} bytes")
                println("URL de transcrição: $uploadUrl")
                
                _uiState.value = _uiState.value.copy(
                    isUploading = true,
                    uploadProgress = 0,
                    statusText = "Iniciando transcrição do áudio..."
                )
                
                // Callback para acompanhar progresso do upload/transcrição
                val onProgress: (Long, Long) -> Unit = { uploaded, total ->
                    val percentage = if (total > 0) (uploaded * 100 / total).toInt() else 0
                    _uiState.value = _uiState.value.copy(
                        uploadProgress = percentage,
                        statusText = when {
                            percentage < 50 -> "Enviando áudio: $percentage%"
                            percentage < 90 -> "Processando transcrição: $percentage%"
                            else -> "Finalizando transcrição: $percentage%"
                        }
                    )
                }
                
                // Executa a transcrição usando o método conveniente ou personalizado
                val result = if (uploadUrl.contains("transcricao")) {
                    // Usa o método conveniente para transcrição
                    uploadService.transcribeAudio(
                        audioFile = processedFile,
                        onProgress = onProgress
                    )
                } else {
                    // Usa o método customizado se for outro endpoint
                    uploadService.uploadProcessedAudio(
                        uploadUrl = uploadUrl,
                        audioFile = processedFile,
                        language = "pt",
                        modelSize = "medium",
                        highQuality = true,
                        onProgress = onProgress
                    )
                }
                
                // Trata o resultado da transcrição
                when (result) {
                    is AudioUploadService.UploadResult.Success -> {
                        _uiState.value = _uiState.value.copy(
                            isUploading = false,
                            uploadProgress = 100,
                            statusText = "Transcrição concluída com sucesso! Texto extraído do áudio."
                        )
                        println("Transcrição bem-sucedida: ${result.response}")
                        // TODO: Aqui você pode processar o texto transcrito (result.response)
                        // Por exemplo, salvar em um arquivo ou exibir na interface
                    }
                    is AudioUploadService.UploadResult.Error -> {
                        _uiState.value = _uiState.value.copy(
                            isUploading = false,
                            uploadProgress = 0,
                            statusText = "Áudio salvo, mas falha na transcrição: ${result.message}"
                        )
                        println("Erro na transcrição: ${result.message}")
                    }
                }
                
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isUploading = false,
                    uploadProgress = 0,
                    statusText = "Áudio salvo, mas erro na transcrição: ${e.message}"
                )
                println("Exceção durante transcrição: ${e.message}")
                e.printStackTrace()
            }
        }
    }
    
    /**
     * Permite fazer transcrição manual de um arquivo de áudio específico
     */
    fun transcribeAudioFile(audioFile: File, uploadUrl: String? = null) {
        if (!audioFile.exists()) {
            _uiState.value = _uiState.value.copy(
                statusText = "Erro: Arquivo não encontrado para transcrição."
            )
            return
        }
        
        // Usa o endpoint de transcrição padrão se não especificado
        val transcriptionUrl = uploadUrl ?: Config.transcriptionUrl
        uploadProcessedAudio(audioFile, transcriptionUrl)
    }
    
    /**
     * Método legado para compatibilidade - redireciona para transcrição
     */
    @Deprecated("Use transcribeAudioFile instead", ReplaceWith("transcribeAudioFile(audioFile, uploadUrl)"))
    fun uploadAudioFile(audioFile: File, uploadUrl: String = Config.transcriptionUrl) {
        transcribeAudioFile(audioFile, uploadUrl)
    }
    
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
        println("=== SALVANDO ÁUDIO PROCESSADO ===")
        val processedFile = audioService.getLatestProcessedFile()
        println("Arquivo processado obtido: ${processedFile?.absolutePath}")
        
        return processedFile?.let { file ->
            println("Arquivo existe: ${file.exists()}")
            println("Tamanho do arquivo: ${file.length()} bytes")
            
            // Só tenta salvar se o arquivo processado existe e tem conteúdo
            if (file.exists() && file.length() > 44) { // 44 bytes = cabeçalho WAV mínimo
                println("✅ Arquivo processado válido - salvando...")
                // Esta função será chamada pela MainActivity para salvar no Downloads
                onProcessedAudioSaved?.invoke(file) // Chama o callback com o arquivo processado
                return file.absolutePath
            } else {
                println("❌ Arquivo processado inválido ou muito pequeno")
                null
            }
        } ?: run {
            println("❌ Nenhum arquivo processado encontrado")
            null
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