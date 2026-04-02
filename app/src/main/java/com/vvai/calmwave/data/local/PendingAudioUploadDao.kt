package com.vvai.calmwave.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.vvai.calmwave.data.model.PendingAudioUpload

@Dao
interface PendingAudioUploadDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(upload: PendingAudioUpload): Long

    @Query("SELECT * FROM pending_audio_uploads WHERE synced = 0 ORDER BY createdAt ASC LIMIT :limit")
    suspend fun getPendingUploads(limit: Int = 20): List<PendingAudioUpload>

    @Query("SELECT COUNT(*) FROM pending_audio_uploads WHERE synced = 0")
    suspend fun countPendingUploads(): Int

    @Query("SELECT filePath FROM pending_audio_uploads WHERE synced = 0")
    suspend fun getPendingFilePaths(): List<String>

    @Query("UPDATE pending_audio_uploads SET synced = 1 WHERE id = :id")
    suspend fun markAsSynced(id: Long)

    @Query("DELETE FROM pending_audio_uploads WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE pending_audio_uploads SET syncAttempts = syncAttempts + 1, lastSyncAttempt = :timestamp, lastError = :error WHERE id = :id")
    suspend fun incrementSyncAttempts(id: Long, timestamp: Long = System.currentTimeMillis(), error: String? = null)

    @Query("DELETE FROM pending_audio_uploads WHERE synced = 1")
    suspend fun deleteAllSynced()
}
