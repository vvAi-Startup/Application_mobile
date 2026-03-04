package com.vvai.calmwave.data.repository

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.vvai.calmwave.data.local.AppDatabase
import com.vvai.calmwave.data.model.AnalyticsEvent
import com.vvai.calmwave.data.model.AudioProcessingMetrics
import com.vvai.calmwave.data.remote.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Repositório para gerenciamento de analytics e sincronização com backend
 */
class AnalyticsRepository(context: Context) {
    
    private val database = AppDatabase.getDatabase(context)
    private val analyticsDao = database.analyticsEventDao()
    private val apiService = ApiClient.getApiService()
    private val gson = Gson()
    
    companion object {
        private const val TAG = "AnalyticsRepository"
        private const val MAX_SYNC_ATTEMPTS = 3
    }
    
    // ========== Gravação de Eventos ==========
    
    /**
     * Registra um evento de analytics
     * Armazena localmente e sincroniza quando houver conexão
     */
    suspend fun logEvent(
        eventType: String,
        details: Map<String, Any?>,
        screen: String? = null,
        level: String = "info",
        userId: Long? = null
    ): Long = withContext(Dispatchers.IO) {
        try {
            val detailsJson = gson.toJson(details)
            val event = AnalyticsEvent(
                userId = userId,
                eventType = eventType,
                detailsJson = detailsJson,
                screen = screen,
                level = level,
                timestamp = System.currentTimeMillis(),
                synced = false
            )
            
            val eventId = analyticsDao.insert(event)
            Log.d(TAG, "Evento registrado localmente: $eventType (ID: $eventId)")
            
            // Tenta sincronizar imediatamente se possível
            // (mas não aguarda o resultado)
            trySync()
            
            eventId
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao registrar evento: ${e.message}", e)
            -1L
        }
    }
    
    /**
     * Registra métricas de processamento de áudio
     */
    suspend fun logAudioProcessingMetrics(metrics: AudioProcessingMetrics): Long {
        val details = mapOf(
            "filename" to metrics.filename,
            "processing_time_ms" to metrics.processingTimeMs,
            "recording_duration_ms" to metrics.recordingDurationMs,
            "original_file_size_bytes" to metrics.originalFileSizeBytes,
            "processed_file_size_bytes" to metrics.processedFileSizeBytes,
            "sample_rate" to metrics.sampleRate,
            "device_origin" to metrics.deviceOrigin,
            "was_offline_processed" to metrics.wasOfflineProcessed,
            "model_version" to metrics.modelVersion,
            "error_occurred" to metrics.errorOccurred,
            "error_message" to metrics.errorMessage
        )
        
        return logEvent(
            eventType = "AUDIO_PROCESSED",
            details = details,
            screen = "GravarActivity",
            level = if (metrics.errorOccurred) "error" else "info",
            userId = metrics.userId
        )
    }
    
    /**
     * Registra evento de gravação de áudio
     */
    suspend fun logAudioRecorded(
        durationMs: Long,
        fileSizeBytes: Long,
        userId: Long? = null
    ): Long {
        return logEvent(
            eventType = "AUDIO_RECORDED",
            details = mapOf(
                "duration_ms" to durationMs,
                "file_size_bytes" to fileSizeBytes,
                "timestamp" to System.currentTimeMillis()
            ),
            screen = "GravarActivity",
            userId = userId
        )
    }
    
    /**
     * Registra evento de reprodução de áudio
     */
    suspend fun logAudioPlayed(
        audioId: Long?,
        filename: String,
        isProcessed: Boolean,
        userId: Long? = null
    ): Long {
        return logEvent(
            eventType = "AUDIO_PLAYED",
            details = mapOf(
                "audio_id" to audioId,
                "filename" to filename,
                "is_processed" to isProcessed
            ),
            screen = "PrincipalActivity",
            userId = userId
        )
    }
    
    /**
     * Registra navegação de tela
     */
    suspend fun logScreenView(
        screenName: String,
        userId: Long? = null
    ): Long {
        return logEvent(
            eventType = "SCREEN_VIEW",
            details = mapOf("screen_name" to screenName),
            screen = screenName,
            userId = userId
        )
    }
    
    // ========== Sincronização ==========
    
    /**
     * Sincroniza eventos pendentes com o backend
     * Retorna número de eventos sincronizados com sucesso
     */
    suspend fun syncPendingEvents(): Int = withContext(Dispatchers.IO) {
        try {
            val unsyncedEvents = analyticsDao.getUnsyncedEventsLimited(limit = 20)
            
            if (unsyncedEvents.isEmpty()) {
                Log.d(TAG, "Nenhum evento para sincronizar")
                return@withContext 0
            }
            
            Log.d(TAG, "Sincronizando ${unsyncedEvents.size} eventos...")
            
            // Backend não suporta batch, sincroniza um por um
            val eventsToSync = unsyncedEvents.filter { it.syncAttempts < MAX_SYNC_ATTEMPTS }
            val syncedCount = syncIndividually(eventsToSync)
            
            syncedCount
        } catch (e: Exception) {
            Log.e(TAG, "Erro na sincronização: ${e.message}", e)
            0
        }
    }
    
    /**
     * Sincroniza eventos individualmente
     */
    private suspend fun syncIndividually(events: List<AnalyticsEvent>): Int {
        var syncedCount = 0
        
        for (event in events) {
            try {
                // Converte details de JSON string para Map
                val details = try {
                    gson.fromJson(event.detailsJson, Map::class.java) as? Map<String, Any?> ?: emptyMap()
                } catch (e: Exception) {
                    emptyMap<String, Any?>()
                }
                
                val eventData = mapOf(
                    "user_id" to event.userId,
                    "event_type" to event.eventType,
                    "details" to details,
                    "screen" to event.screen,
                    "level" to event.level
                )
                
                val response = apiService.sendEvent(eventData)
                
                if (response.isSuccessful) {
                    analyticsDao.markAsSynced(event.id)
                    syncedCount++
                    Log.d(TAG, "✅ Evento ${event.eventType} sincronizado")
                } else {
                    analyticsDao.incrementSyncAttempts(event.id)
                    Log.w(TAG, "Falha ao sincronizar evento ${event.id}: ${response.code()}")
                }
            } catch (e: Exception) {
                analyticsDao.incrementSyncAttempts(event.id)
                Log.w(TAG, "Erro ao sincronizar evento ${event.id}: ${e.message}")
            }
        }
        
        Log.d(TAG, "✅ ${syncedCount}/${events.size} eventos sincronizados")
        return syncedCount
    }
    
    /**
     * Tenta sincronizar (não aguarda resultado)
     */
    private fun trySync() {
        // Isso será chamado pelo WorkManager em background
        // Por enquanto, apenas registra a intenção
        Log.d(TAG, "Sincronização agendada")
    }
    
    /**
     * Upload de arquivo de áudio processado
     * Backend adm_calm_wave aceita apenas file e device_origin
     */
    suspend fun uploadAudioFile(
        audioFile: File,
        metrics: AudioProcessingMetrics
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val requestFile = audioFile.asRequestBody("audio/wav".toMediaTypeOrNull())
            val body = MultipartBody.Part.createFormData("file", audioFile.name, requestFile)
            val deviceOrigin = "Android".toRequestBody("text/plain".toMediaTypeOrNull())
            
            val response = apiService.uploadAudio(
                file = body,
                deviceOrigin = deviceOrigin
            )
            
            if (response.isSuccessful) {
                Log.d(TAG, "✅ Áudio enviado com sucesso: ${audioFile.name}")
                
                // Registra evento de upload bem-sucedido com as métricas
                logEvent(
                    eventType = "AUDIO_UPLOADED",
                    details = mapOf(
                        "filename" to audioFile.name,
                        "file_size_bytes" to audioFile.length(),
                        "processing_time_ms" to metrics.processingTimeMs,
                        "was_offline_processed" to metrics.wasOfflineProcessed,
                        "model_version" to metrics.modelVersion
                    ),
                    screen = "Upload",
                    level = "info"
                )
                
                true
            } else {
                Log.e(TAG, "❌ Falha ao enviar áudio: ${response.code()}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao enviar áudio: ${e.message}", e)
            false
        }
    }
    
    // ========== Consultas ==========
    
    /**
     * Obtém contagem de eventos não sincronizados
     */
    suspend fun getUnsyncedEventCount(): Int = withContext(Dispatchers.IO) {
        analyticsDao.countUnsyncedEvents()
    }
    
    /**
     * Observa eventos não sincronizados
     */
    fun observeUnsyncedEvents(): Flow<List<AnalyticsEvent>> {
        return analyticsDao.observeUnsyncedEvents()
    }
    
    /**
     * Limpa eventos sincronizados antigos (mais de 30 dias)
     */
    suspend fun cleanupOldSyncedEvents() = withContext(Dispatchers.IO) {
        val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
        analyticsDao.deleteSyncedEventsBefore(thirtyDaysAgo)
        Log.d(TAG, "Eventos sincronizados antigos removidos")
    }
}
