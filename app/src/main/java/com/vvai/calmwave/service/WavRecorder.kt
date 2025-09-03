package com.vvai.calmwave.service

// ========================================
// BACKEND: WAV RECORDER SERVICE
// ========================================
// Este arquivo gerencia a gravação de áudio em formato WAV
//  MANTER: Sistema de gravação e envio de chunks em tempo real via WebSocket

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.vvai.calmwave.models.WebSocketMessage
import com.vvai.calmwave.models.WebSocketMessageUtils
import kotlinx.coroutines.*
import java.io.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WavRecorder(
    private val webSocketService: WebSocketService,
    private val coroutineScope: CoroutineScope
) {
    
    // ========================================
    // BACKEND: Constantes de áudio
    // ========================================
    //  MANTER: Configurações de qualidade de gravação
    private object AudioConstants {
        val SAMPLE_RATE = 44100 // Taxa de amostragem (Hz)
        val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO // Mono
        val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT // 16-bit
        val CHUNK_SIZE = 4096 // Tamanho do chunk em bytes
        val CHUNK_DURATION_MS = 100 // Duração de cada chunk em ms
    }
    
    // ========================================
    // BACKEND: Variáveis de estado
    // ========================================
    //  MANTER: Controle de estado de gravação
    private var audioRecorder: AudioRecord? = null
    @Volatile private var isRecording = false // Usar @Volatile para garantir visibilidade entre threads
    private var recordingJob: Job? = null
    
    // ========================================
    // BACKEND: Controle de gravação
    // ========================================
    //  MANTER: Variáveis para controle de gravação
    private var outputStream: FileOutputStream? = null
    private var totalBytesRecorded = 0L
    private var recordingStartTime = 0L
    private var chunkIndex = 0
    private var sessionId: String? = null
    private var currentFilePath: String? = null
    
    // ========================================
    // BACKEND: Callback para chunks
    // ========================================
    //  MANTER: Sistema de notificação de chunks em tempo real
    private var chunkCallback: ((ByteArray, Int) -> Unit)? = null
    
    // ========================================
    // BACKEND: Iniciar gravação
    // ========================================
    //  MANTER: Lógica principal de gravação
    suspend fun startRecording(filePath: String, sessionId: String? = null): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                if (isRecording) {
                    Log.w(TAG, "Gravação já está em andamento")
                    return@withContext false
                }
                
                this@WavRecorder.sessionId = sessionId
                this@WavRecorder.currentFilePath = filePath
                
                // Cria o diretório se não existir
                val file = File(filePath)
                file.parentFile?.mkdirs()
                
                // Inicializa o AudioRecord
                val bufferSize = AudioRecord.getMinBufferSize(
                    AudioConstants.SAMPLE_RATE,
                    AudioConstants.CHANNEL_CONFIG,
                    AudioConstants.AUDIO_FORMAT
                )
                
                audioRecorder = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    AudioConstants.SAMPLE_RATE,
                    AudioConstants.CHANNEL_CONFIG,
                    AudioConstants.AUDIO_FORMAT,
                    bufferSize
                )
                
                if (audioRecorder?.state != AudioRecord.STATE_INITIALIZED) {
                    Log.e(TAG, "AudioRecord não foi inicializado corretamente")
                    return@withContext false
                }
                
                // Cria o arquivo de saída
                outputStream = FileOutputStream(filePath)
                
                // Escreve o cabeçalho WAV
                writeWavHeader(outputStream!!)
                
                // Inicia a gravação
                audioRecorder?.startRecording()
                isRecording = true
                recordingStartTime = System.currentTimeMillis()
                totalBytesRecorded = 0L
                chunkIndex = 0
                
                // Inicia o job de gravação
                startRecordingJob(filePath)
                
                Log.i(TAG, "Gravação iniciada: $filePath")
                return@withContext true
                
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao iniciar gravação: ${e.message}")
                cleanup()
                return@withContext false
            }
        }
    }
    
    // ========================================
    // BACKEND: Job de gravação
    // ========================================
    //  MANTER: Job assíncrono para processar áudio em tempo real
    private fun startRecordingJob(filePath: String) {
        recordingJob = coroutineScope.launch(Dispatchers.IO) {
            try {
                val buffer = ByteArray(AudioConstants.CHUNK_SIZE)
                
                while (isRecording && isActive) {
                    val bytesRead = audioRecorder?.read(buffer, 0, buffer.size) ?: 0
                    
                    if (bytesRead > 0) {
                        // Escreve no arquivo
                        outputStream?.write(buffer, 0, bytesRead)
                        totalBytesRecorded += bytesRead
                        
                        // Envia chunk via WebSocket se conectado
                        if (webSocketService.webSocketState.value.isConnected) {
                            val chunkData = buffer.copyOf(bytesRead)
                            val success = webSocketService.sendAudioChunk(
                                audioData = chunkData,
                                chunkIndex = chunkIndex,
                                sessionId = sessionId
                            )
                            
                            if (success) {
                                chunkIndex++
                                Log.d(TAG, "Chunk $chunkIndex enviado via WebSocket")
                            } else {
                                Log.w(TAG, "Falha ao enviar chunk $chunkIndex via WebSocket")
                            }
                        }
                        
                        // Notifica callback se configurado
                        chunkCallback?.invoke(buffer.copyOf(bytesRead), chunkIndex)
                        
                        // Pequeno delay para controlar a taxa de envio
                        kotlinx.coroutines.delay(AudioConstants.CHUNK_DURATION_MS.toLong())
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Erro durante gravação: ${e.message}")
                isRecording = false
            }
        }
    }
    
    // ========================================
    // BACKEND: Parar gravação
    // ========================================
    //  MANTER: Lógica de parada de gravação
    suspend fun stopRecording(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                if (!isRecording) {
                    Log.w(TAG, "Gravação não está em andamento")
                    return@withContext false
                }
                
                // Para a gravação
                isRecording = false
                recordingJob?.cancel()
                
                // Para o AudioRecord
                audioRecorder?.stop()
                audioRecorder?.release()
                audioRecorder = null
                
                // Finaliza o arquivo WAV
                outputStream?.close()
                outputStream = null
                
                // Atualiza o cabeçalho WAV com o tamanho final
                currentFilePath?.let { path ->
                    updateWavHeader(path)
                }
                
                // Finaliza transmissão WebSocket se conectado
                if (webSocketService.webSocketState.value.isConnected) {
                    val finalDuration = System.currentTimeMillis() - recordingStartTime
                    webSocketService.endAudioTransmission(
                        totalChunksSent = chunkIndex,
                        finalDuration = finalDuration,
                        sessionId = sessionId
                    )
                }
                
                Log.i(TAG, "Gravação parada. Total de bytes: $totalBytesRecorded, Chunks: $chunkIndex")
                return@withContext true
                
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao parar gravação: ${e.message}")
                cleanup()
                return@withContext false
            }
        }
    }
    
    // ========================================
    // BACKEND: Configurar callback de chunks
    // ========================================
    //  MANTER: Sistema de notificação de chunks em tempo real
    fun setChunkCallback(callback: (ByteArray, Int) -> Unit) {
        chunkCallback = callback
    }
    
    // ========================================
    // BACKEND: Verificar se está gravando
    // ========================================
    //  MANTER: Verifica estado de gravação
    fun isRecording(): Boolean = isRecording
    
    // ========================================
    // BACKEND: Obter estatísticas de gravação
    // ========================================
    //  MANTER: Retorna informações sobre a gravação atual
    fun getRecordingStats(): RecordingStats {
        val duration = if (recordingStartTime > 0) {
            System.currentTimeMillis() - recordingStartTime
        } else 0L
        
        return RecordingStats(
            isRecording = isRecording,
            duration = duration,
            totalBytes = totalBytesRecorded,
            chunksSent = chunkIndex,
            sessionId = sessionId
        )
    }
    
    // ========================================
    // BACKEND: Escrever cabeçalho WAV
    // ========================================
    //  MANTER: Cria cabeçalho WAV padrão
    private fun writeWavHeader(outputStream: FileOutputStream) {
        val header = ByteArray(44)
        val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        
        // RIFF header
        buffer.put("RIFF".toByteArray())
        buffer.putInt(0) // Tamanho do arquivo (será atualizado depois)
        buffer.put("WAVE".toByteArray())
        
        // fmt chunk
        buffer.put("fmt ".toByteArray())
        buffer.putInt(16) // Tamanho do fmt chunk
        buffer.putShort(1) // PCM
        buffer.putShort(1) // Mono
        buffer.putInt(AudioConstants.SAMPLE_RATE) // Sample rate
        buffer.putInt(AudioConstants.SAMPLE_RATE * 2) // Byte rate
        buffer.putShort(2) // Block align
        buffer.putShort(16) // Bits per sample
        
        // data chunk
        buffer.put("data".toByteArray())
        buffer.putInt(0) // Tamanho dos dados (será atualizado depois)
        
        outputStream.write(header)
    }
    
    // ========================================
    // BACKEND: Atualizar cabeçalho WAV
    // ========================================
    //  MANTER: Atualiza tamanhos no cabeçalho WAV
    private fun updateWavHeader(filePath: String) {
        try {
            val file = RandomAccessFile(filePath, "rw")
            
            // Atualiza tamanho do arquivo (posição 4)
            file.seek(4)
            file.writeInt((totalBytesRecorded + 36).toInt())
            
            // Atualiza tamanho dos dados (posição 40)
            file.seek(40)
            file.writeInt(totalBytesRecorded.toInt())
            
            file.close()
            
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao atualizar cabeçalho WAV: ${e.message}")
        }
    }
    
    // ========================================
    // BACKEND: Limpeza de recursos
    // ========================================
    //  MANTER: Libera recursos utilizados
    private fun cleanup() {
        try {
            isRecording = false
            recordingJob?.cancel()
            
            audioRecorder?.release()
            audioRecorder = null
            
            outputStream?.close()
            outputStream = null
            
        } catch (e: Exception) {
            Log.e(TAG, "Erro durante limpeza: ${e.message}")
        }
    }
    
    // ========================================
    // BACKEND: Classe de estatísticas
    // ========================================
    //  MANTER: Estrutura para estatísticas de gravação
    data class RecordingStats(
        val isRecording: Boolean,
        val duration: Long,
        val totalBytes: Long,
        val chunksSent: Int,
        val sessionId: String?
    )
    
    companion object {
        private const val TAG = "WavRecorder"
    }
}