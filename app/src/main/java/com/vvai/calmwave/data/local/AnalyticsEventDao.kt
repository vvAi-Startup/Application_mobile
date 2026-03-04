package com.vvai.calmwave.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.vvai.calmwave.data.model.AnalyticsEvent
import kotlinx.coroutines.flow.Flow

/**
 * DAO para operações com eventos de analytics
 */
@Dao
interface AnalyticsEventDao {
    
    /**
     * Insere um novo evento
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: AnalyticsEvent): Long
    
    /**
     * Insere múltiplos eventos
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<AnalyticsEvent>)
    
    /**
     * Atualiza um evento
     */
    @Update
    suspend fun update(event: AnalyticsEvent)
    
    /**
     * Obtém todos os eventos não sincronizados
     */
    @Query("SELECT * FROM analytics_events WHERE synced = 0 ORDER BY timestamp ASC")
    suspend fun getUnsyncedEvents(): List<AnalyticsEvent>
    
    /**
     * Obtém eventos não sincronizados limitados
     */
    @Query("SELECT * FROM analytics_events WHERE synced = 0 ORDER BY timestamp ASC LIMIT :limit")
    suspend fun getUnsyncedEventsLimited(limit: Int = 50): List<AnalyticsEvent>
    
    /**
     * Marca evento como sincronizado
     */
    @Query("UPDATE analytics_events SET synced = 1 WHERE id = :eventId")
    suspend fun markAsSynced(eventId: Long)
    
    /**
     * Marca múltiplos eventos como sincronizados
     */
    @Query("UPDATE analytics_events SET synced = 1 WHERE id IN (:eventIds)")
    suspend fun markMultipleAsSynced(eventIds: List<Long>)
    
    /**
     * Incrementa tentativas de sincronização
     */
    @Query("UPDATE analytics_events SET syncAttempts = syncAttempts + 1, lastSyncAttempt = :timestamp WHERE id = :eventId")
    suspend fun incrementSyncAttempts(eventId: Long, timestamp: Long = System.currentTimeMillis())
    
    /**
     * Conta eventos não sincronizados
     */
    @Query("SELECT COUNT(*) FROM analytics_events WHERE synced = 0")
    suspend fun countUnsyncedEvents(): Int
    
    /**
     * Flow de eventos não sincronizados (para observar mudanças)
     */
    @Query("SELECT * FROM analytics_events WHERE synced = 0 ORDER BY timestamp ASC")
    fun observeUnsyncedEvents(): Flow<List<AnalyticsEvent>>
    
    /**
     * Deleta eventos sincronizados mais antigos que X dias
     */
    @Query("DELETE FROM analytics_events WHERE synced = 1 AND timestamp < :timestampBefore")
    suspend fun deleteSyncedEventsBefore(timestampBefore: Long)
    
    /**
     * Deleta todos os eventos sincronizados
     */
    @Query("DELETE FROM analytics_events WHERE synced = 1")
    suspend fun deleteAllSyncedEvents()
    
    /**
     * Obtém todos os eventos (para debug)
     */
    @Query("SELECT * FROM analytics_events ORDER BY timestamp DESC")
    suspend fun getAllEvents(): List<AnalyticsEvent>
    
    /**
     * Deleta evento por ID
     */
    @Query("DELETE FROM analytics_events WHERE id = :eventId")
    suspend fun deleteById(eventId: Long)
}
