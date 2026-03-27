package com.vvai.calmwave.data.model

import com.google.gson.annotations.SerializedName

/**
 * Payload tipado para POST /api/audios/sync.
 * Envia somente metadados; nenhum arquivo é enviado.
 */
data class AudioSyncRequest(
    @SerializedName("filename")
    val filename: String,

    @SerializedName("duration_seconds")
    val durationSeconds: Double? = null,

    @SerializedName("size_bytes")
    val sizeBytes: Long? = null,

    @SerializedName("device_origin")
    val deviceOrigin: String? = null,

    @SerializedName("recorded_at")
    val recordedAt: String? = null
)
