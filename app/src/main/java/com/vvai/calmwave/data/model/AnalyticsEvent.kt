package com.vvai.calmwave.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

/**
 * Evento de analytics a ser enviado para o backend.
 * Armazenado localmente quando offline e sincronizado quando online.
 */
@Entity(tableName = "analytics_events")
data class AnalyticsEvent(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    @SerializedName("user_id")
    val userId: Long? = null,
    
    @SerializedName("event_type")
    val eventType: String, // Ex: "AUDIO_PROCESSED", "AUDIO_RECORDED", "AUDIO_PLAYED", etc
    
    @SerializedName("details")
    val detailsJson: String, // JSON com detalhes do evento
    
    @SerializedName("screen")
    val screen: String? = null, // Tela onde ocorreu o evento
    
    @SerializedName("level")
    val level: String = "info", // info, warning, error
    
    @SerializedName("timestamp")
    val timestamp: Long = System.currentTimeMillis(),
    
    // Controle de sincronização
    val synced: Boolean = false,
    val syncAttempts: Int = 0,
    val lastSyncAttempt: Long? = null
)

/**
 * Métricas de processamento de áudio
 */
data class AudioProcessingMetrics(
    val audioId: Long? = null,
    val filename: String,
    val processingTimeMs: Long,
    val recordingDurationMs: Long,
    val originalFileSizeBytes: Long,
    val processedFileSizeBytes: Long,
    val sampleRate: Int = 16000,
    val deviceOrigin: String = "Android",
    val wasOfflineProcessed: Boolean,
    val processedAt: Long = System.currentTimeMillis(),
    val userId: Long? = null,
    val modelVersion: String = "1.0",
    val errorOccurred: Boolean = false,
    val errorMessage: String? = null
)

/**
 * Evento de uso do aplicativo
 */
data class AppUsageEvent(
    val eventType: String,
    val screen: String,
    val action: String,
    val timestamp: Long = System.currentTimeMillis(),
    val userId: Long? = null,
    val additionalData: Map<String, Any> = emptyMap()
)

/**
 * Estatísticas de sincronização
 */
data class SyncStats(
    val totalPendingEvents: Int,
    val totalSyncedEvents: Int,
    val lastSyncTimestamp: Long?,
    val failedAttempts: Int
)
