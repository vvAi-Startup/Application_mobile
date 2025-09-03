package com.vvai.calmwave.controller

// ========================================
// BACKEND: RECORDING CONTROLLER
// ========================================
// Este arquivo gerencia gravações temporárias e processo de renomeação
//  MANTER: Controle de gravações temporárias e renomeação antes de salvar

import android.content.Context
import com.vvai.calmwave.models.TempRecording
import com.vvai.calmwave.models.TempRecordingStatus
import com.vvai.calmwave.service.AudioService
import com.vvai.calmwave.service.WavRecorder
import com.vvai.calmwave.service.WebSocketService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import android.util.Log

class RecordingController(
    private val audioService: AudioService,
    private val wavRecorder: WavRecorder,
    private val webSocketService: WebSocketService,
    private val context: Context,
    private val coroutineScope: CoroutineScope
) {
    
    // ========================================
    // BACKEND: Estado interno do controller
    // ========================================
    //  MANTER: Controle de gravação atual e temporária
    private var currentRecordingPath: String? = null
    private var recordingStartTime: Long = 0L
    private var tempRecording: TempRecording? = null
    private var sessionId: String? = null
    
    // ========================================
    // BACKEND: Iniciar gravação
    // ========================================
    //  MANTER: Lógica principal de início de gravação
    fun startRecording(filePath: String): Boolean {
        currentRecordingPath = filePath
        recordingStartTime = System.currentTimeMillis()
        
        // Gera ID único para a sessão de gravação
        sessionId = UUID.randomUUID().toString()
        
        coroutineScope.launch(Dispatchers.IO) {
            try {
                // Inicia o bluetooth SCO
                audioService.startBluetoothSco()
                
                // Inicia a gravação com WebSocket
                val success = wavRecorder.startRecording(filePath, sessionId)
                
                if (success) {
                    // Inicia transmissão WebSocket se conectado
                    if (webSocketService.webSocketState.value.isConnected) {
                        // Estima duração e chunks baseado no histórico ou configuração padrão
                        val estimatedDuration = 30000L // 30 segundos estimados
                        val estimatedChunks = (estimatedDuration / 100).toInt() // 100ms por chunk
                        
                        webSocketService.startAudioTransmission(
                            totalChunks = estimatedChunks,
                            estimatedDuration = estimatedDuration,
                            sessionId = sessionId
                        )
                    }
                } else {
                    // Falha na gravação
                    audioService.stopBluetoothSco()
                    throw Exception("Falha ao iniciar gravação")
                }
                
            } catch (e: Exception) {
                e.printStackTrace()
                audioService.stopBluetoothSco()
                throw e
            }
        }
        
        return true
    }
    
    // ========================================
    // BACKEND: Parar gravação e criar temporária
    // ========================================
    //  MANTER: Lógica de parada e criação de gravação temporária
    fun stopRecording(): TempRecording? {
        val endTime = System.currentTimeMillis()
        val duration = endTime - recordingStartTime
        
        coroutineScope.launch(Dispatchers.IO) {
            try {
                // Para a gravação
                wavRecorder.stopRecording()
                
                // Para o bluetooth SCO
                audioService.stopBluetoothSco()
                
                // Finaliza transmissão WebSocket se conectado
                if (webSocketService.webSocketState.value.isConnected) {
                    val stats = wavRecorder.getRecordingStats()
                    webSocketService.endAudioTransmission(
                        totalChunksSent = stats.chunksSent,
                        finalDuration = duration,
                        sessionId = sessionId ?: stats.sessionId
                    )
                }
                
            } catch (e: Exception) {
                Log.e("RecordingController", "Erro ao parar gravação: ${e.message}")
                e.printStackTrace()
            }
        }
        
        // Cria gravação temporária para renomeação
        currentRecordingPath?.let { path ->
            val file = File(path)
            if (file.exists()) {
                val suggestedName = generateSuggestedName(recordingStartTime)
                
                tempRecording = TempRecording(
                    id = sessionId ?: UUID.randomUUID().toString(),
                    originalFilePath = path,
                    tempFilePath = path,
                    startTime = recordingStartTime,
                    endTime = endTime,
                    duration = duration,
                    fileSize = file.length(),
                    suggestedName = suggestedName,
                    status = TempRecordingStatus.PENDING_RENAME // Garante status correto
                )
                
                return tempRecording
            }
        }
        
        return null
    }
    
    // ========================================
    // BACKEND: Gerar nome sugerido
    // ========================================
    //  MANTER: Gera nome baseado na data e hora da gravação
    private fun generateSuggestedName(timestamp: Long): String {
        val date = Date(timestamp)
        val formatter = SimpleDateFormat("dd-MM-yyyy_HH-mm-ss", Locale.getDefault())
        return "Gravação_${formatter.format(date)}"
    }
    
    // ========================================
    // BACKEND: Confirmar nome e salvar
    // ========================================
    //  MANTER: Confirma o nome e move o arquivo para localização final
    suspend fun confirmAndSaveRecording(customName: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                tempRecording?.let { temp ->
                    // Atualiza a gravação temporária com o nome confirmado
                    val updatedTemp = temp.confirmName(customName)
                    
                    // Cria o caminho final
                    val finalPath = updatedTemp.getFinalPath()
                    val finalFile = File(finalPath)
                    
                    // Move o arquivo temporário para o local final
                    val tempFile = File(temp.tempFilePath)
                    if (tempFile.exists()) {
                        val success = tempFile.renameTo(finalFile)
                        if (success) {
                            // Limpa a gravação temporária
                            tempRecording = null
                            currentRecordingPath = null
                            sessionId = null
                            return@withContext true
                        } else {
                            Log.e("RecordingController", "Falha ao renomear arquivo")
                        }
                    } else {
                        Log.e("RecordingController", "Arquivo temporário não encontrado")
                    }
                }
                false
            } catch (e: Exception) {
                Log.e("RecordingController", "Erro ao confirmar gravação: ${e.message}")
                e.printStackTrace()
                false
            }
        }
    }
    
    // ========================================
    // BACKEND: Cancelar gravação
    // ========================================
    //  MANTER: Cancela a gravação e remove arquivo temporário
    suspend fun cancelRecording(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                tempRecording?.let { temp ->
                    // Atualiza status para cancelado
                    tempRecording = temp.cancel()
                    
                    // Remove arquivo temporário
                    val tempFile = File(temp.tempFilePath)
                    if (tempFile.exists()) {
                        val deleted = tempFile.delete()
                        if (!deleted) {
                            Log.w("RecordingController", "Falha ao deletar arquivo temporário")
                        }
                    }
                    
                    // Limpa referências
                    tempRecording = null
                    currentRecordingPath = null
                    sessionId = null
                    return@withContext true
                }
                false
            } catch (e: Exception) {
                Log.e("RecordingController", "Erro ao cancelar gravação: ${e.message}")
                e.printStackTrace()
                false
            }
        }
    }
    
    // ========================================
    // BACKEND: Obter gravação temporária atual
    // ========================================
    //  MANTER: Acesso ao estado da gravação temporária
    fun getCurrentTempRecording(): TempRecording? = tempRecording
    
    // ========================================
    // BACKEND: Verificar se há gravação em andamento
    // ========================================
    //  MANTER: Verifica se há gravação ativa
    fun isRecording(): Boolean = currentRecordingPath != null
    
    // ========================================
    // BACKEND: Obter estatísticas de gravação
    // ========================================
    //  MANTER: Retorna estatísticas da gravação atual
    fun getRecordingStats() = wavRecorder.getRecordingStats()
    
    // ========================================
    // BACKEND: Obter status da conexão WebSocket
    // ========================================
    //  MANTER: Retorna status da conexão WebSocket
    fun getWebSocketStatus() = webSocketService.webSocketState.value
    
    // ========================================
    // BACKEND: Conectar ao servidor WebSocket
    // ========================================
    //  MANTER: Conecta ao servidor WebSocket
    fun connectToServer(serverUrl: String): Boolean {
        return webSocketService.connect(serverUrl)
    }
    
    // ========================================
    // BACKEND: Desconectar do servidor WebSocket
    // ========================================
    //  MANTER: Desconecta do servidor WebSocket
    fun disconnectFromServer() {
        webSocketService.disconnect()
    }
}
