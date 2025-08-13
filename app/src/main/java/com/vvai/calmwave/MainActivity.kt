package com.vvai.calmwave

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.core.content.ContextCompat
import com.vvai.calmwave.ui.theme.CalmWaveTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {

    private lateinit var wavRecorder: WavRecorder
    private lateinit var audioService: AudioService

    // Contrato para solicitar múltiplas permissões
    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allPermissionsGranted = permissions.entries.all { it.value }
            if (allPermissionsGranted) {
                // Todas as permissões foram concedidas.
                Toast.makeText(this, "Todas as permissões concedidas!", Toast.LENGTH_SHORT).show()
            } else {
                // Alguma permissão foi negada.
                Toast.makeText(this, "Algumas permissões foram negadas. O aplicativo pode não funcionar corretamente.", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        wavRecorder = WavRecorder()
        audioService = AudioService()

        checkAndRequestPermissions() // Verifica e solicita permissões ao iniciar a atividade

        enableEdgeToEdge()
        setContent {
            CalmWaveTheme {
                MyAudioScreen(
                    onRecordClicked = {
                        // Inicia uma corrotina na thread de I/O para evitar o bloqueio da UI
                        lifecycleScope.launch(Dispatchers.IO) {
                            startRecordingProcess()
                        }
                    },
                    onStopClicked = {
                        // Inicia uma corrotina na thread de I/O para evitar o bloqueio da UI
                        lifecycleScope.launch(Dispatchers.IO) {
                            stopRecordingAndProcess()
                        }
                    }
                )
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        // Permissão de gravação de áudio
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
        }

        // Permissões de Bluetooth para Android 12 (API 31) e superior
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN)
            }
        } else {
            // Permissão para versões mais antigas para descoberta de dispositivos (Bluetooth legado)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }

        // A permissão INTERNET é considerada 'normal' e é concedida automaticamente na instalação,
        // então não precisa ser solicitada em tempo de execução. Mas é bom estar na lista
        // para referência, embora não seja estritamente necessário aqui.

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionsLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    private suspend fun startRecordingProcess() {
        val cacheDir = externalCacheDir
        if (cacheDir != null) {
            // Cria o diretório se ele não existir
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            val filePath = "${cacheDir.absolutePath}/audio_record.wav"
            audioService.startBluetoothSco(this)
            wavRecorder.startRecording(filePath)
            // Feedback visual ao usuário (executado na thread principal após a operação de I/O)
            lifecycleScope.launch(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, "Iniciando gravação em: $filePath", Toast.LENGTH_SHORT).show()
            }
        } else {
            // Lidar com o caso em que o diretório de cache não está disponível
            lifecycleScope.launch(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, "Erro: Diretório de cache não disponível para gravação.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private suspend fun stopRecordingAndProcess() {
        wavRecorder.stopRecording()
        audioService.stopBluetoothSco(this)

        val filePath = "${externalCacheDir?.absolutePath}/audio_record.wav"
        // Verifica se o arquivo existe antes de tentar enviar
        val audioFile = File(filePath)
        if (audioFile.exists()) {
            audioService.sendAndPlayWavFile(
                filePath = filePath,
                apiEndpoint = "https://your.api.endpoint" // <-- ATUALIZE ESTE ENDPOINT COM A SUA API!
            )
            // Feedback visual ao usuário (executado na thread principal após a operação de I/O)
            lifecycleScope.launch(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, "Gravação parada e arquivo enviado/processado.", Toast.LENGTH_SHORT).show()
            }
        } else {
            lifecycleScope.launch(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, "Erro: Arquivo de áudio não encontrado para processamento.", Toast.LENGTH_LONG).show()
            }
        }
    }
}

@Composable
fun MyAudioScreen(
    onRecordClicked: () -> Unit,
    onStopClicked: () -> Unit
) {
    var isRecording by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("Pronto para gravar") }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = statusText,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Button(
                onClick = {
                    isRecording = true
                    statusText = "Gravando..."
                    onRecordClicked()
                },
                enabled = !isRecording // Desabilita o botão "Gravar" enquanto estiver gravando
            ) {
                Text(text = "Gravar")
            }

            Spacer(modifier = Modifier.height(16.dp)) // Espaço entre os botões

            Button(
                onClick = {
                    isRecording = false
                    statusText = "Parando gravação e processando..."
                    onStopClicked()
                },
                enabled = isRecording // Desabilita o botão "Parar" se não estiver gravando
            ) {
                Text(text = "Parar")
            }
        }
    }
}
