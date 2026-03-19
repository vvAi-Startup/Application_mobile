package com.vvai.calmwave.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Upload de áudio pendente para sincronização.
 *
 * Fluxo offline-first:
 * 1) Áudio processado localmente é salvo em disco
 * 2) Metadados do upload são persistidos localmente
 * 3) Worker sincroniza quando houver conexão
 */
@Entity(tableName = "pending_audio_uploads")
data class PendingAudioUpload(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val filePath: String,
    val fileName: String,
    val mimeType: String = "audio/wav",
    val deviceOrigin: String = "Android",
    val createdAt: Long = System.currentTimeMillis(),
    val synced: Boolean = false,
    val syncAttempts: Int = 0,
    val lastSyncAttempt: Long? = null,
    val lastError: String? = null
)
