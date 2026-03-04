package com.vvai.calmwave.workers

import android.content.Context
import android.util.Log
import androidx.work.*
import com.vvai.calmwave.data.repository.AnalyticsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Worker para sincronizar eventos de analytics com o backend
 * Executa em background quando há conexão de rede
 */
class SyncAnalyticsWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    private val analyticsRepository = AnalyticsRepository(context)
    
    companion object {
        private const val TAG = "SyncAnalyticsWorker"
        const val WORK_NAME = "sync_analytics_work"
        
        /**
         * Agenda sincronização periódica (a cada 1 hora)
         */
        fun schedulePeriodic(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED) // Requer conexão
                .build()
            
            val syncRequest = PeriodicWorkRequestBuilder<SyncAnalyticsWorker>(
                repeatInterval = 1, // Repetir a cada 1 hora
                repeatIntervalTimeUnit = TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()
            
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP, // Mantém se já existe
                syncRequest
            )
            
            Log.d(TAG, "Sincronização periódica agendada")
        }
        
        /**
         * Agenda sincronização imediata (one-time)
         */
        fun scheduleImmediate(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            
            val syncRequest = OneTimeWorkRequestBuilder<SyncAnalyticsWorker>()
                .setConstraints(constraints)
                .build()
            
            WorkManager.getInstance(context).enqueue(syncRequest)
            Log.d(TAG, "Sincronização imediata agendada")
        }
        
        /**
         * Cancela sincronização periódica
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d(TAG, "Sincronização cancelada")
        }
    }
    
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        return@withContext try {
            Log.d(TAG, "Iniciando sincronização de analytics...")
            
            // Verifica se há eventos para sincronizar
            val pendingCount = analyticsRepository.getUnsyncedEventCount()
            
            if (pendingCount == 0) {
                Log.d(TAG, "Nenhum evento para sincronizar")
                return@withContext Result.success()
            }
            
            Log.d(TAG, "Encontrados $pendingCount eventos pendentes")
            
            // Sincroniza eventos
            val syncedCount = analyticsRepository.syncPendingEvents()
            
            if (syncedCount > 0) {
                Log.d(TAG, "✅ Sincronizados $syncedCount eventos com sucesso")
                
                // Limpa eventos antigos sincronizados
                analyticsRepository.cleanupOldSyncedEvents()
                
                Result.success(
                    workDataOf(
                        "synced_count" to syncedCount,
                        "pending_count" to pendingCount
                    )
                )
            } else {
                // Se nenhum evento foi sincronizado, retry
                Log.w(TAG, "⚠️ Nenhum evento foi sincronizado, tentando novamente...")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro na sincronização: ${e.message}", e)
            
            // Retry em caso de erro
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }
}
