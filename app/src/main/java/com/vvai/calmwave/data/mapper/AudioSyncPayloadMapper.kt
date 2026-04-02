package com.vvai.calmwave.data.mapper

import com.vvai.calmwave.data.model.AudioProcessingMetrics
import java.io.File

data class AudioSyncPayload(
    val filename: String,
    val durationSeconds: Double,
    val sizeBytes: Long,
    val recordedAt: String,
    val processed: Boolean,
    val processingTimeMs: Long?,
    val deviceOrigin: String,
    val transcribed: Boolean,
    val transcriptionText: String?
)

object AudioSyncPayloadMapper {
    fun fromProcessedAudio(
        audioFile: File,
        metrics: AudioProcessingMetrics?,
        durationSeconds: Double,
        recordedAt: String,
        resolvedDeviceOrigin: String
    ): AudioSyncPayload {
        return AudioSyncPayload(
            filename = audioFile.name,
            durationSeconds = durationSeconds.coerceAtLeast(0.0),
            sizeBytes = audioFile.length().coerceAtLeast(0L),
            recordedAt = recordedAt,
            processed = true,
            processingTimeMs = metrics?.processingTimeMs,
            deviceOrigin = resolvedDeviceOrigin,
            transcribed = false,
            transcriptionText = null
        )
    }
}
