package com.vvai.calmwave

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vvai.calmwave.data.repository.AnalyticsRepository
import com.vvai.calmwave.ui.theme.CalmWaveTheme
import com.vvai.calmwave.util.NetworkMonitor
import com.vvai.calmwave.workers.SyncAnalyticsWorker
import kotlinx.coroutines.flow.collectLatest

class SyncStatusActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CalmWaveTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF6FCFD)) {
                    SyncStatusScreen()
                }
            }
        }
    }
}

@Composable
private fun SyncStatusScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val repository = remember { AnalyticsRepository(context) }
    val networkMonitor = remember { NetworkMonitor.getInstance(context) }

    var isOnline by remember { mutableStateOf(networkMonitor.isCurrentlyOnline()) }
    var pendingEvents by remember { mutableIntStateOf(0) }
    var pendingUploads by remember { mutableIntStateOf(0) }
    var isSyncing by remember { mutableStateOf(false) }
    var lastMessage by remember { mutableStateOf("-") }

    suspend fun refreshStatus() {
        pendingEvents = repository.getUnsyncedEventCount()
        pendingUploads = repository.getPendingAudioUploadCount()
    }

    DisposableEffect(Unit) {
        networkMonitor.startMonitoring()
        onDispose { networkMonitor.stopMonitoring() }
    }

    LaunchedEffect(Unit) {
        refreshStatus()
        networkMonitor.isOnline.collectLatest {
            isOnline = it
            refreshStatus()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF6FCFD))
            .padding(16.dp),
        verticalArrangement = Arrangement.Top
    ) {
        Text("Status de sincronização", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))

        Card(colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = if (isOnline) "Online" else "Offline", color = if (isOnline) Color(0xFF1B8A5A) else Color(0xFFB00020), fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Eventos pendentes: $pendingEvents")
                Text("Uploads pendentes: $pendingUploads")
                Spacer(modifier = Modifier.height(8.dp))
                Text("Último status: $lastMessage", style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                isSyncing = true
                lastMessage = "Sincronizando..."
                SyncAnalyticsWorker.scheduleImmediate(context)
            },
            enabled = !isSyncing,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isSyncing) {
                CircularProgressIndicator(modifier = Modifier.height(18.dp), strokeWidth = 2.dp)
            } else {
                Text("Sincronizar agora")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                SyncAnalyticsWorker.schedulePeriodic(context)
                lastMessage = "Sincronização periódica ativa"
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Ativar sincronização automática")
        }

        LaunchedEffect(isSyncing) {
            if (isSyncing) {
                kotlinx.coroutines.delay(1800)
                refreshStatus()
                isSyncing = false
                lastMessage = "Atualizado"
            }
        }
    }
}
