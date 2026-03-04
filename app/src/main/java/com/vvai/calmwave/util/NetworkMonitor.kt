package com.vvai.calmwave.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Monitor de conectividade de rede
 * Observa mudanças no estado da conexão e notifica quando fica online
 */
class NetworkMonitor(private val context: Context) {
    
    private val connectivityManager = 
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    
    private val _isOnline = MutableStateFlow(checkConnectivity())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()
    
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    
    companion object {
        private const val TAG = "NetworkMonitor"
        
        @Volatile
        private var INSTANCE: NetworkMonitor? = null
        
        fun getInstance(context: Context): NetworkMonitor {
            return INSTANCE ?: synchronized(this) {
                val instance = NetworkMonitor(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
    
    /**
     * Verifica conectividade atual
     */
    private fun checkConnectivity(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
    
    /**
     * Inicia monitoramento de rede
     */
    fun startMonitoring() {
        if (networkCallback != null) {
            Log.d(TAG, "Monitoramento já iniciado")
            return
        }
        
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(TAG, "✅ Rede disponível")
                _isOnline.value = true
            }
            
            override fun onLost(network: Network) {
                Log.d(TAG, "❌ Rede perdida")
                _isOnline.value = checkConnectivity() // Verifica se ainda há outra rede
            }
            
            override fun onCapabilitiesChanged(
                network: Network,
                capabilities: NetworkCapabilities
            ) {
                val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                                 capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                
                if (_isOnline.value != hasInternet) {
                    Log.d(TAG, "Status de conectividade mudou: $hasInternet")
                    _isOnline.value = hasInternet
                }
            }
        }
        
        connectivityManager.registerNetworkCallback(request, networkCallback!!)
        Log.d(TAG, "Monitoramento de rede iniciado")
    }
    
    /**
     * Para monitoramento de rede
     */
    fun stopMonitoring() {
        networkCallback?.let {
            connectivityManager.unregisterNetworkCallback(it)
            networkCallback = null
            Log.d(TAG, "Monitoramento de rede parado")
        }
    }
    
    /**
     * Verifica se está online agora
     */
    fun isCurrentlyOnline(): Boolean {
        return checkConnectivity()
    }
}
