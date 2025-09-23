package com.vvai.calmwave.models

// ========================================
// BACKEND: WEBSOCKET STATE MODEL
// ========================================
// Este arquivo define o estado da conexão WebSocket
//  MANTER: Modelo de dados para controle de estado da conexão

import com.vvai.calmwave.models.ConnectionStatus
import com.vvai.calmwave.models.ServerInfo

// ========================================
// BACKEND: Estado da conexão WebSocket
// ========================================
//  MANTER: Estrutura para controle de estado da conexão
data class WebSocketState(
    // ========================================
    // BACKEND: Status da conexão
    // ========================================
    val connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED, //  MANTER: Status atual
    val isConnected: Boolean = false,                                      //  MANTER: Indica se está conectado
    val isConnecting: Boolean = false,                                     //  MANTER: Indica se está conectando
    val isReconnecting: Boolean = false,                                   //  MANTER: Indica se está reconectando
    
    // ========================================
    // BACKEND: Informações da conexão
    // ========================================
    val serverUrl: String? = null,                                         //  MANTER: URL do servidor
    val sessionId: String? = null,                                         //  MANTER: ID da sessão atual
    val clientId: String? = null,                                          //  MANTER: ID único do cliente
    val lastConnected: Long? = null,                                       //  MANTER: Timestamp da última conexão
    
    // ========================================
    // BACKEND: Métricas de conexão
    // ========================================
    val latency: Long? = null,                                             //  MANTER: Latência em ms
    val connectionDuration: Long = 0L,                                     //  MANTER: Duração da conexão atual
    val reconnectAttempts: Int = 0,                                        //  MANTER: Tentativas de reconexão
    val maxReconnectAttempts: Int = 5,                                     //  MANTER: Máximo de tentativas
    
    // ========================================
    // BACKEND: Informações do servidor
    // ========================================
    val serverInfo: ServerInfo? = null,                                    //  MANTER: Informações do servidor
    val serverCapabilities: List<String> = emptyList(),                     //  MANTER: Capacidades disponíveis
    
    // ========================================
    // BACKEND: Estatísticas de comunicação
    // ========================================
    val messagesSent: Long = 0L,                                           //  MANTER: Total de mensagens enviadas
    val messagesReceived: Long = 0L,                                       //  MANTER: Total de mensagens recebidas
    val bytesSent: Long = 0L,                                              //  MANTER: Total de bytes enviados
    val bytesReceived: Long = 0L,                                          //  MANTER: Total de bytes recebidos
    
    // ========================================
    // BACKEND: Controle de heartbeat
    // ========================================
    val lastHeartbeat: Long? = null,                                       //  MANTER: Último heartbeat enviado
    val heartbeatInterval: Long = 30000L,                                  //  MANTER: Intervalo entre heartbeats (ms)
    val missedHeartbeats: Int = 0,                                         //  MANTER: Heartbeats perdidos
    val maxMissedHeartbeats: Int = 3,                                      //  MANTER: Máximo de heartbeats perdidos
    
    // ========================================
    // BACKEND: Estado de transmissão
    // ========================================
    val isTransmitting: Boolean = false,                                   //  MANTER: Indica se está transmitindo áudio
    val currentTransmissionId: String? = null,                             //  MANTER: ID da transmissão atual
    val transmissionStartTime: Long? = null,                               //  MANTER: Início da transmissão atual
    val chunksTransmitted: Int = 0,                                        //  MANTER: Chunks transmitidos na sessão atual
    
    // ========================================
    // BACKEND: Histórico de erros
    // ========================================
    val lastError: String? = null,                                         //  MANTER: Último erro ocorrido
    val errorCount: Int = 0,                                               //  MANTER: Contador de erros
    val lastErrorTime: Long? = null                                        //  MANTER: Timestamp do último erro
) {
    
    // ========================================
    // BACKEND: Funções de conveniência
    // ========================================
    //  MANTER: Métodos úteis para verificação de estado
    
    fun canConnect(): Boolean = !isConnected && !isConnecting && !isReconnecting
    
    fun canReconnect(): Boolean = reconnectAttempts < maxReconnectAttempts
    
    fun shouldSendHeartbeat(): Boolean {
        val now = System.currentTimeMillis()
        return lastHeartbeat == null || (now - lastHeartbeat!!) >= heartbeatInterval
    }
    
    fun isHeartbeatHealthy(): Boolean = missedHeartbeats < maxMissedHeartbeats
    
    fun getConnectionUptime(): Long {
        return lastConnected?.let { System.currentTimeMillis() - it } ?: 0L
    }
    
    fun getTransmissionDuration(): Long {
        return transmissionStartTime?.let { System.currentTimeMillis() - it } ?: 0L
    }
    
    fun getTransmissionProgress(): Float {
        return if (transmissionStartTime != null) {
            val duration = getTransmissionDuration()
            if (duration > 0) duration.toFloat() / 1000f else 0f
        } else 0f
    }
    
    fun getConnectionQuality(): ConnectionQuality {
        return when {
            latency == null -> ConnectionQuality.UNKNOWN
            latency < 50 -> ConnectionQuality.EXCELLENT
            latency < 100 -> ConnectionQuality.GOOD
            latency < 200 -> ConnectionQuality.FAIR
            latency < 500 -> ConnectionQuality.POOR
            else -> ConnectionQuality.UNUSABLE
        }
    }
    
    fun getStatusDescription(): String {
        return when (connectionStatus) {
            ConnectionStatus.CONNECTING -> "Conectando..."
            ConnectionStatus.CONNECTED -> "Conectado"
            ConnectionStatus.DISCONNECTED -> "Desconectado"
            ConnectionStatus.RECONNECTING -> "Reconectando... (${reconnectAttempts}/${maxReconnectAttempts})"
            ConnectionStatus.ERROR -> "Erro: $lastError"
        }
    }
}

// ========================================
// BACKEND: Enum para qualidade da conexão
// ========================================
//  MANTER: Classificação da qualidade da conexão
enum class ConnectionQuality {
    UNKNOWN,    //  MANTER: Qualidade desconhecida
    EXCELLENT,  //  MANTER: Excelente (< 50ms)
    GOOD,       //  MANTER: Boa (50-100ms)
    FAIR,       //  MANTER: Razoável (100-200ms)
    POOR,       //  MANTER: Ruim (200-500ms)
    UNUSABLE    //  MANTER: Inutilizável (> 500ms)
}

// ========================================
// BACKEND: Eventos de mudança de estado
// ========================================
//  MANTER: Eventos para notificar mudanças de estado
sealed class WebSocketEvent {
    data class Connected(val serverInfo: ServerInfo) : WebSocketEvent()
    data class Disconnected(val reason: String?) : WebSocketEvent()
    data class Reconnecting(val attempt: Int) : WebSocketEvent()
    data class Error(val error: String) : WebSocketEvent()
    data class MessageReceived(val message: WebSocketMessage) : WebSocketEvent()
    data class MessageSent(val message: WebSocketMessage) : WebSocketEvent()
    data class Heartbeat(val latency: Long) : WebSocketEvent()
    data class TransmissionStarted(val transmissionId: String) : WebSocketEvent()
    data class TransmissionProgress(val chunksSent: Int) : WebSocketEvent()
    data class TransmissionCompleted(val transmissionId: String) : WebSocketEvent()
    data class ServerCapabilitiesUpdated(val capabilities: List<String>) : WebSocketEvent()
}
