package com.vvai.calmwave.models

// ========================================
// BACKEND: WEBSOCKET MESSAGE MODEL
// ========================================
// Este arquivo define as estruturas de mensagens para comunicação WebSocket
//  MANTER: Modelo de dados para comunicação WebSocket em tempo real

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// ========================================
// BACKEND: Tipos de mensagem WebSocket
// ========================================
//  MANTER: Enum para identificar tipos de mensagem
enum class MessageType {
    AUDIO_CHUNK,        //  MANTER: Chunk de áudio em tempo real
    AUDIO_START,        //  MANTER: Início de transmissão de áudio
    AUDIO_END,          //  MANTER: Fim de transmissão de áudio
    AUDIO_PROCESSED,    //  MANTER: Áudio processado pelo servidor
    AUDIO_RESPONSE,     //  MANTER: Resposta de áudio do servidor
    CONNECTION_STATUS,  //  MANTER: Status da conexão
    ERROR,              //  MANTER: Mensagem de erro
    HEARTBEAT          //  MANTER: Manutenção da conexão
}

// ========================================
// BACKEND: Mensagem base WebSocket
// ========================================
//  MANTER: Estrutura base para todas as mensagens
@Serializable
sealed class WebSocketMessage {
    abstract val type: MessageType
    abstract val timestamp: Long
    abstract val sessionId: String?
}

// ========================================
// BACKEND: Mensagem de chunk de áudio
// ========================================
//  MANTER: Envio de chunks de áudio em tempo real
@Serializable
data class AudioChunkMessage(
    override val type: MessageType = MessageType.AUDIO_CHUNK,
    override val timestamp: Long = System.currentTimeMillis(),
    override val sessionId: String? = null,
    val chunkIndex: Int,                    //  MANTER: Índice sequencial do chunk
    val audioData: String,                  //  MANTER: Dados de áudio em Base64
    val sampleRate: Int = 44100,            //  MANTER: Taxa de amostragem
    val channels: Int = 1,                  //  MANTER: Número de canais
    val bitDepth: Int = 16,                 //  MANTER: Profundidade de bits
    val chunkSize: Int                      //  MANTER: Tamanho do chunk em bytes
) : WebSocketMessage()

// ========================================
// BACKEND: Mensagem de início de áudio
// ========================================
//  MANTER: Sinaliza início de transmissão de áudio
@Serializable
data class AudioStartMessage(
    override val type: MessageType = MessageType.AUDIO_START,
    override val timestamp: Long = System.currentTimeMillis(),
    override val sessionId: String? = null,
    val totalChunks: Int,                   //  MANTER: Total de chunks esperados
    val estimatedDuration: Long,            //  MANTER: Duração estimada em ms
    val metadata: AudioMetadata             //  MANTER: Metadados do áudio
) : WebSocketMessage()

// ========================================
// BACKEND: Mensagem de fim de áudio
// ========================================
//  MANTER: Sinaliza fim de transmissão de áudio
@Serializable
data class AudioEndMessage(
    override val type: MessageType = MessageType.AUDIO_END,
    override val timestamp: Long = System.currentTimeMillis(),
    override val sessionId: String? = null,
    val totalChunksSent: Int,               //  MANTER: Total de chunks enviados
    val finalDuration: Long,                //  MANTER: Duração final em ms
    val checksum: String? = null            //  MANTER: Checksum para verificação
) : WebSocketMessage()

// ========================================
// BACKEND: Mensagem de áudio processado
// ========================================
//  MANTER: Confirmação de processamento pelo servidor
@Serializable
data class AudioProcessedMessage(
    override val type: MessageType = MessageType.AUDIO_PROCESSED,
    override val timestamp: Long = System.currentTimeMillis(),
    override val sessionId: String? = null,
    val processingTime: Long,               //  MANTER: Tempo de processamento em ms
    val result: ProcessingResult,           //  MANTER: Resultado do processamento
    val analysis: AudioAnalysis? = null     //  MANTER: Análise do áudio (se disponível)
) : WebSocketMessage()

// ========================================
// BACKEND: Mensagem de resposta de áudio
// ========================================
//  MANTER: Áudio processado retornado pelo servidor
@Serializable
data class AudioResponseMessage(
    override val type: MessageType = MessageType.AUDIO_RESPONSE,
    override val timestamp: Long = System.currentTimeMillis(),
    override val sessionId: String? = null,
    val responseAudio: String,              //  MANTER: Áudio de resposta em Base64
    val responseType: ResponseType,         //  MANTER: Tipo de resposta
    val metadata: ResponseMetadata          //  MANTER: Metadados da resposta
) : WebSocketMessage()

// ========================================
// BACKEND: Mensagem de status de conexão
// ========================================
//  MANTER: Informações sobre o status da conexão
@Serializable
data class ConnectionStatusMessage(
    override val type: MessageType = MessageType.CONNECTION_STATUS,
    override val timestamp: Long = System.currentTimeMillis(),
    override val sessionId: String? = null,
    val status: ConnectionStatus,           //  MANTER: Status atual da conexão
    val latency: Long? = null,              //  MANTER: Latência em ms
    val serverInfo: ServerInfo? = null      //  MANTER: Informações do servidor
) : WebSocketMessage()

// ========================================
// BACKEND: Mensagem de erro
// ========================================
//  MANTER: Comunicação de erros
@Serializable
data class ErrorMessage(
    override val type: MessageType = MessageType.ERROR,
    override val timestamp: Long = System.currentTimeMillis(),
    override val sessionId: String? = null,
    val errorCode: String,                  //  MANTER: Código do erro
    val errorMessage: String,               //  MANTER: Descrição do erro
    val details: String? = null             //  MANTER: Detalhes adicionais
) : WebSocketMessage()

// ========================================
// BACKEND: Mensagem de heartbeat
// ========================================
//  MANTER: Manutenção da conexão
@Serializable
data class HeartbeatMessage(
    override val type: MessageType = MessageType.HEARTBEAT,
    override val timestamp: Long = System.currentTimeMillis(),
    override val sessionId: String? = null,
    val clientId: String                    //  MANTER: ID do cliente
) : WebSocketMessage()

// ========================================
// BACKEND: Estruturas de suporte
// ========================================
//  MANTER: Estruturas auxiliares para mensagens

@Serializable
data class AudioMetadata(
    val deviceInfo: DeviceInfo,             //  MANTER: Informações do dispositivo
    val recordingSettings: RecordingSettings, //  MANTER: Configurações de gravação
    val userInfo: UserInfo? = null          //  MANTER: Informações do usuário (opcional)
)

@Serializable
data class DeviceInfo(
    val deviceId: String,                   //  MANTER: ID único do dispositivo
    val deviceModel: String,                //  MANTER: Modelo do dispositivo
    val androidVersion: String,             //  MANTER: Versão do Android
    val appVersion: String                  //  MANTER: Versão da aplicação
)

@Serializable
data class RecordingSettings(
    val sampleRate: Int = 44100,            //  MANTER: Taxa de amostragem
    val channels: Int = 1,                  //  MANTER: Número de canais
    val bitDepth: Int = 16,                 //  MANTER: Profundidade de bits
    val compression: String = "PCM"         //  MANTER: Tipo de compressão
)

@Serializable
data class UserInfo(
    val userId: String? = null,             //  MANTER: ID do usuário
    val sessionToken: String? = null        //  MANTER: Token da sessão
)

@Serializable
data class ProcessingResult(
    val success: Boolean,                   //  MANTER: Indica se o processamento foi bem-sucedido
    val message: String,                    //  MANTER: Mensagem descritiva
    val confidence: Float? = null           //  MANTER: Nível de confiança (0.0 a 1.0)
)

@Serializable
data class AudioAnalysis(
    val duration: Long,                     //  MANTER: Duração analisada
    val quality: AudioQuality,              //  MANTER: Qualidade do áudio
    val features: List<String> = emptyList() //  MANTER: Características detectadas
)

@Serializable
data class AudioQuality(
    val score: Float,                       //  MANTER: Pontuação de qualidade (0.0 a 1.0)
    val noiseLevel: Float,                  //  MANTER: Nível de ruído
    val clarity: Float                      //  MANTER: Clareza do áudio
)

@Serializable
enum class ResponseType {
    PROCESSED_AUDIO,                        //  MANTER: Áudio processado
    ANALYSIS_RESULT,                        //  MANTER: Resultado de análise
    TRANSCRIPTION,                          //  MANTER: Transcrição do áudio
    EMOTION_DETECTION                       //  MANTER: Detecção de emoção
}

@Serializable
data class ResponseMetadata(
    val responseId: String,                 //  MANTER: ID único da resposta
    val processingTime: Long,               //  MANTER: Tempo de processamento
    val modelVersion: String? = null        //  MANTER: Versão do modelo usado
)

@Serializable
enum class ConnectionStatus {
    CONNECTING,                             //  MANTER: Conectando
    CONNECTED,                              //  MANTER: Conectado
    DISCONNECTED,                           //  MANTER: Desconectado
    RECONNECTING,                           //  MANTER: Reconectando
    ERROR                                   //  MANTER: Erro na conexão
}

@Serializable
data class ServerInfo(
    val serverId: String,                   //  MANTER: ID do servidor
    val version: String,                    //  MANTER: Versão do servidor
    val capabilities: List<String>          //  MANTER: Capacidades disponíveis
)

// ========================================
// BACKEND: Utilitários para serialização
// ========================================
//  MANTER: Funções auxiliares para conversão de mensagens

object WebSocketMessageUtils {
    
    // ========================================
    // BACKEND: Serializar mensagem para JSON
    // ========================================
    //  MANTER: Converte mensagem para string JSON
    fun toJson(message: WebSocketMessage): String {
        return Json.encodeToString(WebSocketMessage.serializer(), message)
    }
    
    // ========================================
    // BACKEND: Deserializar JSON para mensagem
    // ========================================
    //  MANTER: Converte string JSON para mensagem
    fun fromJson(json: String): WebSocketMessage? {
        return try {
            Json.decodeFromString(WebSocketMessage.serializer(), json)
        } catch (e: Exception) {
            null
        }
    }
    
    // ========================================
    // BACKEND: Verificar tipo de mensagem
    // ========================================
    //  MANTER: Identifica o tipo de mensagem sem deserializar completamente
    fun getMessageType(json: String): MessageType? {
        return try {
            val element = Json.parseToJsonElement(json)
            val typeString = element.jsonObject["type"]?.jsonPrimitive?.content
            typeString?.let { MessageType.valueOf(it) }
        } catch (e: Exception) {
            null
        }
    }
}
