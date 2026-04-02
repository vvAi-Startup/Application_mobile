package com.vvai.calmwave.data.repository

import android.content.Context
import android.os.Build
import android.util.Log
import com.google.gson.Gson
import com.vvai.calmwave.data.mapper.AudioSyncPayload
import com.vvai.calmwave.data.mapper.AudioSyncPayloadMapper
import com.vvai.calmwave.data.local.AppDatabase
import com.vvai.calmwave.data.model.AnalyticsEvent
import com.vvai.calmwave.data.model.AudioProcessingMetrics
import com.vvai.calmwave.data.model.PendingAudioUpload
import com.vvai.calmwave.data.remote.ApiClient
import com.vvai.calmwave.util.AppLogger
import com.vvai.calmwave.util.NetworkMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
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
        private const val AUDIO_SYNC_MAX_RETRIES = 3
        private const val AUDIO_SYNC_INITIAL_BACKOFF_MS = 600L
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
            AppLogger.e(TAG, "EVT_LOG_FAILED", "Erro ao registrar evento: ${e.message}", e)
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
            AppLogger.e(TAG, "SYNC_EVENTS_FAILED", "Erro na sincronização: ${e.message}", e)
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
                    AppLogger.w(TAG, "SYNC_EVENT_HTTP", "Falha ao sincronizar evento ${event.id}: ${response.code()}")
                }
            } catch (e: Exception) {
                analyticsDao.incrementSyncAttempts(event.id)
                AppLogger.w(TAG, "SYNC_EVENT_EXCEPTION", "Erro ao sincronizar evento ${event.id}: ${e.message}", e)
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
     * Sincroniza áudio processado na rota autenticada /audio/sync.
     * Envia multipart com arquivo + metadados.
     */
    suspend fun uploadAudioFile(
        audioFile: File,
        metrics: AudioProcessingMetrics
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!audioFile.exists() || audioFile.length() <= 44) {
                AppLogger.w(TAG, "UPLOAD_FILE_INVALID", "Arquivo inválido para upload: ${audioFile.absolutePath}")
                return@withContext false
            }

            // Offline-first: se estiver offline, armazena em cache e sincroniza depois
            if (!networkMonitor.isCurrentlyOnline()) {
                enqueueAudioUpload(audioFile, metrics)
                Log.d(TAG, "📦 Áudio enfileirado para sincronização futura (offline): ${audioFile.name}")
                return@withContext true
            }

            if (ApiClient.getAuthToken().isNullOrBlank()) {
                AppLogger.w(TAG, "UPLOAD_AUTH_MISSING", "Token de usuário ausente; adiando sincronização de metadados")
                enqueueAudioUpload(audioFile, metrics)
                return@withContext false
            }

            val payload = AudioSyncPayloadMapper.fromProcessedAudio(
                audioFile = audioFile,
                metrics = metrics,
                durationSeconds = (metrics.recordingDurationMs / 1000.0).coerceAtLeast(0.0),
                recordedAt = toUtcIso8601(audioFile.lastModified()),
                resolvedDeviceOrigin = resolveDeviceOrigin(metrics.deviceOrigin)
            )

            val response = syncProcessedAudioAuthenticated(audioFile, payload)
            
            if (response.isSuccessful) {
                Log.d(TAG, "✅ Áudio processado sincronizado com sucesso: ${audioFile.name}")
                
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
                AppLogger.e(TAG, "UPLOAD_HTTP_FAILED", "Falha ao sincronizar áudio processado: ${response.code()}")
                enqueueAudioUpload(audioFile, metrics)
                false
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "UPLOAD_EXCEPTION", "Erro ao sincronizar áudio processado: ${e.message}", e)
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
            AppLogger.w(TAG, "PENDING_AUTH_MISSING", "Token de usuário ausente; uploads pendentes continuarão em fila")
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
                AppLogger.w(TAG, "PENDING_FILE_INVALID", "Removendo pendência inválida (arquivo ausente/corrompido): ${upload.filePath}")
                pendingAudioUploadDao.deleteById(upload.id)
                continue
            }

            try {
                val payload = AudioSyncPayloadMapper.fromProcessedAudio(
                    audioFile = file,
                    metrics = null,
                    durationSeconds = estimateWavDurationSeconds(file),
                    recordedAt = toUtcIso8601(file.lastModified()),
                    resolvedDeviceOrigin = resolveDeviceOrigin(upload.deviceOrigin)
                )

                val response = syncProcessedAudioAuthenticated(file, payload)

                if (response.isSuccessful) {
                    pendingAudioUploadDao.deleteById(upload.id)
                    syncedCount++
                    Log.d(TAG, "✅ Upload pendente sincronizado: ${upload.fileName}")
                } else {
                    pendingAudioUploadDao.incrementSyncAttempts(
                        id = upload.id,
                        error = "HTTP ${response.code()}"
                    )
                    AppLogger.w(TAG, "PENDING_UPLOAD_HTTP", "Falha ao sincronizar upload pendente ${upload.id}: ${response.code()}")
                }
            } catch (e: Exception) {
                pendingAudioUploadDao.incrementSyncAttempts(
                    id = upload.id,
                    error = e.message
                )
                AppLogger.w(TAG, "PENDING_UPLOAD_EXCEPTION", "Erro ao sincronizar upload pendente ${upload.id}: ${e.message}", e)
            }
        }

        syncedCount
    }

    private suspend fun syncProcessedAudioAuthenticated(
        audioFile: File,
        payload: AudioSyncPayload
    ): Response<Map<String, Any>> {
        val multipartFile = MultipartBody.Part.createFormData(
            "file",
            payload.filename,
            audioFile.asRequestBody("audio/wav".toMediaType())
        )

        val filenameBody = payload.filename.toRequestBody(TEXT_PLAIN)
        val durationBody = payload.durationSeconds.toString().toRequestBody(TEXT_PLAIN)
        val sizeBody = payload.sizeBytes.toString().toRequestBody(TEXT_PLAIN)
        val recordedAtBody = payload.recordedAt.toRequestBody(TEXT_PLAIN)
        val processedBody = payload.processed.toString().toRequestBody(TEXT_PLAIN)
        val processingTimeBody = payload.processingTimeMs?.toString()?.toRequestBody(TEXT_PLAIN)
        val deviceOriginBody = payload.deviceOrigin.toRequestBody(TEXT_PLAIN)
        val transcribedBody = payload.transcribed.toString().toRequestBody(TEXT_PLAIN)

        var backoff = AUDIO_SYNC_INITIAL_BACKOFF_MS
        var lastResponse: Response<Map<String, Any>>? = null

        repeat(AUDIO_SYNC_MAX_RETRIES) { attempt ->
            try {
                var response = apiService.syncProcessedAudioMultipartPlural(
                    file = multipartFile,
                    filename = filenameBody,
                    durationSeconds = durationBody,
                    sizeBytes = sizeBody,
                    recordedAt = recordedAtBody,
                    processed = processedBody,
                    processingTimeMs = processingTimeBody,
                    deviceOrigin = deviceOriginBody,
                    transcribed = transcribedBody,
                    transcriptionText = null
                )
                if (!response.isSuccessful && response.code() == 404) {
                    response = apiService.syncProcessedAudioMultipartPluralNoApiPrefix(
                        file = multipartFile,
                        filename = filenameBody,
                        durationSeconds = durationBody,
                        sizeBytes = sizeBody,
                        recordedAt = recordedAtBody,
                        processed = processedBody,
                        processingTimeMs = processingTimeBody,
                        deviceOrigin = deviceOriginBody,
                        transcribed = transcribedBody,
                        transcriptionText = null
                    )
                }

                if (response.isSuccessful) {
                    return response
                }

                lastResponse = response

                // Erros de cliente (exceto 408/429) não devem ficar em retry infinito.
                if (response.code() in 400..499 && response.code() != 408 && response.code() != 429) {
                    return response
                }
            } catch (e: Exception) {
                AppLogger.w(TAG, "SYNC_RETRY_EXCEPTION", "Tentativa ${attempt + 1}/$AUDIO_SYNC_MAX_RETRIES falhou em api/audios/sync: ${e.message}", e)
            }

            if (attempt < AUDIO_SYNC_MAX_RETRIES - 1) {
                delay(backoff)
                backoff *= 2
            }
        }

        return lastResponse ?: apiService.syncProcessedAudioMultipartPlural(
            file = multipartFile,
            filename = filenameBody,
            durationSeconds = durationBody,
            sizeBytes = sizeBody,
            recordedAt = recordedAtBody,
            processed = processedBody,
            processingTimeMs = processingTimeBody,
            deviceOrigin = deviceOriginBody,
            transcribed = transcribedBody,
            transcriptionText = null
        )
    }

    suspend fun getPendingAudioUploadCount(): Int = withContext(Dispatchers.IO) {
        pendingAudioUploadDao.countPendingUploads()
    }

    suspend fun getPendingAudioFilePaths(): Set<String> = withContext(Dispatchers.IO) {
        pendingAudioUploadDao.getPendingFilePaths().toSet()
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

    private val TEXT_PLAIN = "text/plain".toMediaType()
    
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
