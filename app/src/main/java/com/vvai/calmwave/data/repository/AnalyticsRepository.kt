package com.vvai.calmwave.data.repository

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.vvai.calmwave.data.auth.GuestSessionManager
import com.vvai.calmwave.data.local.AppDatabase
import com.vvai.calmwave.data.model.AnalyticsEvent
import com.vvai.calmwave.data.model.AudioProcessingMetrics
import com.vvai.calmwave.data.model.PendingAudioUpload
import com.vvai.calmwave.data.remote.ApiClient
import com.vvai.calmwave.util.NetworkMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import kotlin.math.roundToInt

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
     * Sincroniza metadados do áudio processado na rota /api/audios/sync.
     * Não envia arquivo, apenas dados essenciais de gravação/processamento.
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

            ensureGuestAuthorizationIfNeeded()

            val response = syncAudioMultipart(
                audioFile = audioFile,
                deviceOrigin = metrics.deviceOrigin,
                durationSeconds = (metrics.recordingDurationMs / 1000.0).roundToInt(),
                processingTimeMs = metrics.processingTimeMs,
                transcriptionText = null,
                processedFile = null
            )

            // Se token estiver ausente/expirado, tenta revalidar guest 1x e repetir.
            // (syncAudioMultipart já faz 1 retry interno em caso de 401)
            
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

        ensureGuestAuthorizationIfNeeded()

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
                val response = syncAudioMultipart(
                    audioFile = file,
                    deviceOrigin = upload.deviceOrigin,
                    durationSeconds = estimateWavDurationSeconds(file),
                    processingTimeMs = null,
                    transcriptionText = null,
                    processedFile = null
                )

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

    private suspend fun syncAudioMultipart(
        audioFile: File,
        deviceOrigin: String,
        durationSeconds: Int?,
        processingTimeMs: Long?,
        transcriptionText: String?,
        processedFile: File?
    ): retrofit2.Response<Map<String, Any>> {
        val fileRequestBody = audioFile.asRequestBody("audio/wav".toMediaTypeOrNull())
        val filePart = MultipartBody.Part.createFormData("file", audioFile.name, fileRequestBody)

        val deviceOriginBody = deviceOrigin
            .ifBlank { "Android" }
            .toRequestBody("text/plain".toMediaTypeOrNull())

        val durationBody: RequestBody? = durationSeconds
            ?.coerceAtLeast(0)
            ?.toString()
            ?.toRequestBody("text/plain".toMediaTypeOrNull())

        val processingBody: RequestBody? = processingTimeMs
            ?.coerceAtLeast(0L)
            ?.toString()
            ?.toRequestBody("text/plain".toMediaTypeOrNull())

        val transcriptionBody: RequestBody? = transcriptionText
            ?.takeIf { it.isNotBlank() }
            ?.toRequestBody("text/plain".toMediaTypeOrNull())

        val processedPart: MultipartBody.Part? = processedFile?.takeIf { it.exists() && it.length() > 44 }?.let { pf ->
            val body = pf.asRequestBody("audio/wav".toMediaTypeOrNull())
            MultipartBody.Part.createFormData("processed_file", pf.name, body)
        }

        var response = apiService.syncAudioMultipart(
            file = filePart,
            deviceOrigin = deviceOriginBody,
            durationSeconds = durationBody,
            processingTimeMs = processingBody,
            transcriptionText = transcriptionBody,
            processedFile = processedPart
        )

        if (response.code() == 401) {
            Log.w(TAG, "401 ao sincronizar (multipart); tentando renovar sessão guest")
            GuestSessionManager.clear(appContext)
            ensureGuestAuthorizationIfNeeded()
            response = apiService.syncAudioMultipart(
                file = filePart,
                deviceOrigin = deviceOriginBody,
                durationSeconds = durationBody,
                processingTimeMs = processingBody,
                transcriptionText = transcriptionBody,
                processedFile = processedPart
            )
        }

        return response
    }

    private suspend fun ensureGuestAuthorizationIfNeeded() {
        // Se já existe token em memória, não faz nada.
        if (!ApiClient.getAuthToken().isNullOrBlank()) return

        val token = GuestSessionManager.ensureGuestSession(appContext)
        if (token.isNullOrBlank()) {
            Log.w(TAG, "Não foi possível obter token guest; chamadas autenticadas podem falhar")
        }
    }

    suspend fun getPendingAudioUploadCount(): Int = withContext(Dispatchers.IO) {
        pendingAudioUploadDao.countPendingUploads()
    }

    suspend fun cleanupSyncedAudioUploads() = withContext(Dispatchers.IO) {
        pendingAudioUploadDao.deleteAllSynced()
    }

    private fun estimateWavDurationSeconds(file: File): Int {
        return try {
            if (!file.exists() || file.length() <= 44) return 0
            val dataSize = (file.length() - 44L).coerceAtLeast(0L)
            // Formato padrão do app: PCM 16-bit mono em 16kHz (2 bytes por amostra)
            val bytesPerSecond = 16000L * 2L
            (dataSize / bytesPerSecond).toInt().coerceAtLeast(0)
        } catch (_: Exception) {
            0
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
