package com.vvai.calmwave

import android.app.Application
import android.util.Log
import com.vvai.calmwave.data.remote.ApiClient
import com.vvai.calmwave.data.repository.AnalyticsRepository
import com.vvai.calmwave.util.FunnelAnalyticsTracker
import com.vvai.calmwave.util.NetworkMonitor
import com.vvai.calmwave.util.logDevicePerformanceProfile
import com.vvai.calmwave.workers.SyncAnalyticsWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Classe Application do CalmWave
 * Inicializa serviços globais e sincronização de analytics
 */
class CalmWaveApplication : Application() {
    
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var networkMonitor: NetworkMonitor
    
    companion object {
        private const val TAG = "CalmWaveApp"
    }
    
    override fun onCreate() {
        super.onCreate()
        
        Log.d(TAG, "Iniciando aplicação CalmWave")

        ApiClient.initialize(this)
        logDevicePerformanceProfile(this)
        
        // Inicializa monitor de rede
        networkMonitor = NetworkMonitor.getInstance(this)
        networkMonitor.startMonitoring()
        
        // Agenda sincronização periódica de analytics
        SyncAnalyticsWorker.schedulePeriodic(this)
        Log.d(TAG, "Sincronização periódica de analytics agendada")

        applicationScope.launch(Dispatchers.IO) {
            val repository = AnalyticsRepository(this@CalmWaveApplication)
            FunnelAnalyticsTracker.trackAppOpen(this@CalmWaveApplication, repository)
        }
        
        // Observa mudanças de conectividade para sincronizar quando ficar online
        observeNetworkChanges()
    }
    
    /**
     * Observa mudanças de conectividade e dispara sincronização quando ficar online
     */
    private fun observeNetworkChanges() {
        applicationScope.launch {
            networkMonitor.isOnline.collectLatest { isOnline ->
                Log.d(TAG, "Mudança de conectividade detectada: ${if (isOnline) "ONLINE" else "OFFLINE"}")
                
                if (isOnline) {
                    // Quando ficar online, agenda sincronização imediata
                    Log.d(TAG, "Dispositivo online - agendando sincronização imediata")
                    SyncAnalyticsWorker.scheduleImmediate(this@CalmWaveApplication)
                }
            }
        }
    }
    
    override fun onTerminate() {
        super.onTerminate()
        networkMonitor.stopMonitoring()
        Log.d(TAG, "Aplicação finalizada")
    }
}
