package com.vvai.calmwave.data.repository

import android.content.Context
import android.os.Build
import android.util.Log
import com.google.gson.Gson
import com.vvai.calmwave.data.local.AppDatabase
import com.vvai.calmwave.data.model.AnalyticsEvent
import com.vvai.calmwave.data.model.AudioProcessingMetrics
import com.vvai.calmwave.data.model.AudioSyncRequest
import com.vvai.calmwave.data.model.PendingAudioUpload
import com.vvai.calmwave.data.remote.ApiClient
import com.vvai.calmwave.util.NetworkMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import retrofit2.Response
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Repositório para gerenciamento de analytics e sincronização com backend
 */
class AnalyticsRepository(context: Context) {
    private val appContext = context.applicationContext
    private val database = AppDatabase.getDatabase(context)
    private val analyticsDao = database.analyticsEventDao()
    private val pendingAudioUploadDao = database.pendingAudioUploadDao()
    private val apiService get() = ApiClient.getApiService()
    private val gson = Gson()
    private val networkMonitor = NetworkMonitor.getInstance(appContext)
    
    companion object {
        private const val TAG = "AnalyticsRepository"
        private const val MAX_SYNC_ATTEMPTS = 3
        private const val MAX_UPLOAD_SYNC_ATTEMPTS = 10
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
     * Sincroniza metadados do áudio na rota autenticada /audios/sync.
     * Envia apenas metadados: device_origin, duration_seconds, filename, recorded_at, size_bytes.
     */
    suspend fun uploadAudioFile(
        audioFile: File,
        metrics: AudioProcessingMetrics
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!audioFile.exists() || audioFile.length() <= 44) {
                Log.w(TAG, "Arquivo inválido para upload: ${audioFile.absolutePath}")
                return@withContext false
            }

            // Offline-first: se estiver offline, armazena em cache e sincroniza depois
            if (!networkMonitor.isCurrentlyOnline()) {
                enqueueAudioUpload(audioFile, metrics)
                Log.d(TAG, "📦 Áudio enfileirado para sincronização futura (offline): ${audioFile.name}")
                return@withContext true
            }

            if (ApiClient.getAuthToken().isNullOrBlank()) {
                Log.w(TAG, "Token de usuário ausente; adiando sincronização de metadados")
                enqueueAudioUpload(audioFile, metrics)
                return@withContext false
            }

            val request = AudioSyncRequest(
                filename = audioFile.name,
                durationSeconds = (metrics.recordingDurationMs / 1000.0).coerceAtLeast(0.0),
                sizeBytes = audioFile.length().coerceAtLeast(0L),
                deviceOrigin = resolveDeviceOrigin(metrics.deviceOrigin),
                recordedAt = toUtcIso8601(audioFile.lastModified())
            )

            val response = syncAudioMetadataAuthenticated(request)
            
            if (response.isSuccessful) {
                Log.d(TAG, "✅ Metadados de áudio sincronizados com sucesso: ${audioFile.name}")
                
                // Registra evento de sincronização bem-sucedida com as métricas
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
                Log.e(TAG, "❌ Falha ao sincronizar metadados de áudio: ${response.code()}")
                enqueueAudioUpload(audioFile, metrics)
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao sincronizar metadados de áudio: ${e.message}", e)
            enqueueAudioUpload(audioFile, metrics)
            false
        }
    }

    /**
     * Armazena upload pendente no banco local para sincronização posterior.
     */
    private suspend fun enqueueAudioUpload(audioFile: File, metrics: AudioProcessingMetrics? = null) {
        if (!audioFile.exists()) return

        val pendingUpload = PendingAudioUpload(
            filePath = audioFile.absolutePath,
            fileName = audioFile.name,
            mimeType = "audio/wav",
            deviceOrigin = metrics?.deviceOrigin ?: "Android"
        )
        pendingAudioUploadDao.insert(pendingUpload)
    }

    /**
     * Sincroniza uploads de áudio pendentes quando houver conectividade.
     */
    suspend fun syncPendingAudioUploads(): Int = withContext(Dispatchers.IO) {
        if (!networkMonitor.isCurrentlyOnline()) {
            Log.d(TAG, "Sem conexão - mantendo uploads pendentes em cache")
            return@withContext 0
        }

        if (ApiClient.getAuthToken().isNullOrBlank()) {
            Log.w(TAG, "Token de usuário ausente; uploads pendentes continuarão em fila")
            return@withContext 0
        }

        val pending = pendingAudioUploadDao
            .getPendingUploads(limit = 20)
            .filter { it.syncAttempts < MAX_UPLOAD_SYNC_ATTEMPTS }

        if (pending.isEmpty()) {
            return@withContext 0
        }

        var syncedCount = 0
        for (upload in pending) {
            val file = File(upload.filePath)

            if (!file.exists() || file.length() <= 44) {
                Log.w(TAG, "Removendo pendência inválida (arquivo ausente/corrompido): ${upload.filePath}")
                pendingAudioUploadDao.deleteById(upload.id)
                continue
            }

            try {
                val request = AudioSyncRequest(
                    filename = upload.fileName.ifBlank { file.name },
                    durationSeconds = estimateWavDurationSeconds(file),
                    sizeBytes = file.length().coerceAtLeast(0L),
                    deviceOrigin = resolveDeviceOrigin(upload.deviceOrigin),
                    recordedAt = toUtcIso8601(file.lastModified())
                )

                val response = syncAudioMetadataAuthenticated(request)

                if (response.isSuccessful) {
                    pendingAudioUploadDao.deleteById(upload.id)
                    syncedCount++
                    Log.d(TAG, "✅ Upload pendente sincronizado: ${upload.fileName}")
                } else {
                    pendingAudioUploadDao.incrementSyncAttempts(
                        id = upload.id,
                        error = "HTTP ${response.code()}"
                    )
                    Log.w(TAG, "Falha ao sincronizar upload pendente ${upload.id}: ${response.code()}")
                }
            } catch (e: Exception) {
                pendingAudioUploadDao.incrementSyncAttempts(
                    id = upload.id,
                    error = e.message
                )
                Log.w(TAG, "Erro ao sincronizar upload pendente ${upload.id}: ${e.message}")
            }
        }

        syncedCount
    }

    private suspend fun syncAudioMetadataAuthenticated(
        request: AudioSyncRequest
    ): Response<Map<String, Any>> {
        var response = apiService.syncAudioMetadataJsonNoApiPrefix(request)
        if (!response.isSuccessful && response.code() == 404) {
            response = apiService.syncAudioMetadataJson(request)
        }
        return response
    }

    suspend fun getPendingAudioUploadCount(): Int = withContext(Dispatchers.IO) {
        pendingAudioUploadDao.countPendingUploads()
    }

    suspend fun cleanupSyncedAudioUploads() = withContext(Dispatchers.IO) {
        pendingAudioUploadDao.deleteAllSynced()
    }

    private fun estimateWavDurationSeconds(file: File): Double {
        return try {
            if (!file.exists() || file.length() <= 44) return 0.0
            val dataSize = (file.length() - 44L).coerceAtLeast(0L)
            // Formato padrão do app: PCM 16-bit mono em 16kHz (2 bytes por amostra)
            val bytesPerSecond = 16000.0 * 2.0
            (dataSize / bytesPerSecond).coerceAtLeast(0.0)
        } catch (_: Exception) {
            0.0
        }
    }

    private fun resolveDeviceOrigin(candidate: String?): String {
        if (!candidate.isNullOrBlank()) return candidate
        val model = listOfNotNull(Build.MANUFACTURER, Build.MODEL)
            .joinToString(" ")
            .trim()
        return if (model.isBlank()) "Android" else model
    }

    private fun toUtcIso8601(timestampMillis: Long): String {
        val safeTs = if (timestampMillis > 0L) timestampMillis else System.currentTimeMillis()
        val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        formatter.timeZone = TimeZone.getTimeZone("UTC")
        return formatter.format(Date(safeTs))
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
