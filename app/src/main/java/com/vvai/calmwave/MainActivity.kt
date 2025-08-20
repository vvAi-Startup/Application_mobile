package com.vvai.calmwave

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.core.content.ContextCompat
import com.vvai.calmwave.ui.theme.CalmWaveTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

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
                AudioPlayerScreen(
                    audioService = audioService,
                    onRecordClicked = {
                        lifecycleScope.launch(Dispatchers.IO) {
                            startRecordingProcess()
                        }
                    },
                    onStopClicked = {
                        lifecycleScope.launch(Dispatchers.IO) {
                            stopRecordingAndProcess()
                        }
                    },
                    listFilesProvider = { listRecordedWavFiles() }
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

        // Permissões de armazenamento para salvar no Downloads
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ (API 33+)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_AUDIO)
            }
        } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            // Android 9 e inferior (API 28 e inferior)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionsLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    private fun generateFileName(): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return "calmwave_recording_$timestamp.wav"
    }

    private fun getDownloadsDirectory(): File? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        } else {
            File(Environment.getExternalStorageDirectory(), "Download")
        }
    }

    private fun listRecordedWavFiles(): List<File> {
        val dir = getDownloadsDirectory() ?: externalCacheDir
        val files = dir?.listFiles { f -> f.isFile && f.name.endsWith(".wav", ignoreCase = true) }?.toList() ?: emptyList()
        return files.sortedByDescending { it.lastModified() }
    }

    private suspend fun startRecordingProcess() {
        try {
            val downloadsDir = getDownloadsDirectory()
            if (downloadsDir != null) {
                if (!downloadsDir.exists()) {
                    downloadsDir.mkdirs()
                }

                val fileName = generateFileName()
                val filePath = "${downloadsDir.absolutePath}/$fileName"
                currentRecordingPath = filePath

                audioService.startBluetoothSco(this)
                wavRecorder.startRecording(filePath)

                lifecycleScope.launch(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Iniciando gravação: $fileName", Toast.LENGTH_SHORT).show()
                }
            } else {
                // Fallback para cache se Downloads não estiver disponível
                val cacheDir = externalCacheDir
                if (cacheDir != null) {
                    if (!cacheDir.exists()) {
                        cacheDir.mkdirs()
                    }
                    val fileName = generateFileName()
                    val filePath = "${cacheDir.absolutePath}/$fileName"
                    currentRecordingPath = filePath

                    audioService.startBluetoothSco(this)
                    wavRecorder.startRecording(filePath)

                    lifecycleScope.launch(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "Salvando no cache: $fileName", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    lifecycleScope.launch(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "Erro: Nenhum diretório disponível para gravação.", Toast.LENGTH_LONG).show()
                    }
                }
            }
        } catch (e: Exception) {
            lifecycleScope.launch(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, "Erro ao iniciar gravação: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private var currentRecordingPath: String? = null

    private suspend fun stopRecordingAndProcess() {
        try {
            wavRecorder.stopRecording()
            audioService.stopBluetoothSco(this)

            // Verifica se o arquivo existe antes de tentar enviar
            val audioFile = currentRecordingPath?.let { File(it) }
            if (audioFile != null && audioFile.exists()) {
                audioService.sendAndPlayWavFile(
                    filePath = currentRecordingPath!!,
                    apiEndpoint = "http://172.20.0.3:5000/upload"
                )

                // Feedback visual ao usuário (executado na thread principal após a operação de I/O)
                lifecycleScope.launch(Dispatchers.Main) {
                    val fileName = audioFile.name
                    Toast.makeText(this@MainActivity, "Gravação salva: $fileName", Toast.LENGTH_SHORT).show()
                }
            } else {
                lifecycleScope.launch(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Erro: Arquivo de áudio não encontrado para processamento.", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            lifecycleScope.launch(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, "Erro ao parar gravação: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}

private fun formatTime(milliseconds: Long): String {
    val totalSeconds = milliseconds / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

@Composable
fun AudioPlayerScreen(
    audioService: AudioService,
    onRecordClicked: () -> Unit,
    onStopClicked: () -> Unit,
    listFilesProvider: () -> List<File>
) {
    var isRecording by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("Pronto para gravar") }
    var wavFiles by remember { mutableStateOf(listFilesProvider()) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableStateOf(0L) }
    var totalDuration by remember { mutableStateOf(0L) }
    var playbackProgress by remember { mutableStateOf(0f) }
    var isSeeking by remember { mutableStateOf(false) }
    
    // Atualiza o estado de reprodução periodicamente
    LaunchedEffect(Unit) {
        while (true) {
            if (!isSeeking) {
                isPlaying = audioService.isCurrentlyPlaying()
                currentPosition = audioService.getCurrentPlaybackPosition()
                totalDuration = audioService.getTotalPlaybackDuration()
                playbackProgress = audioService.getPlaybackProgress()
            }
            delay(100) // Atualiza a cada 100ms
        }
    }

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

            Spacer(modifier = Modifier.height(16.dp))

            // Barra de progresso e tempo (exibe apenas quando há duração conhecida)
            if (totalDuration > 0) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    // Slider para selecionar tempo
                    val clampedPosition = currentPosition.coerceIn(0L, totalDuration)
                    Slider(
                        value = clampedPosition.toFloat(),
                        onValueChange = { newValue ->
                            isSeeking = true
                            val bounded = newValue.toLong().coerceIn(0L, totalDuration)
                            currentPosition = bounded
                        },
                        onValueChangeFinished = {
                            isSeeking = false
                            val bounded = currentPosition.coerceIn(0L, totalDuration)
                            currentPosition = bounded
                            audioService.seekTo(bounded)
                        },
                        valueRange = 0f..totalDuration.toFloat(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Tempo atual / tempo total
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatTime(currentPosition),
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = formatTime(totalDuration),
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(onClick = {
                    if (audioService.isCurrentlyPlaying()) {
                        audioService.pausePlayback()
                    } else {
                        audioService.resumePlayback()
                    }
                }) {
                    Text(text = if (isPlaying) "Pausar" else "Continuar")
                }
                
                Button(onClick = {
                    audioService.stopPlayback()
                }) {
                    Text(text = "Parar")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(text = "Gravações (WAV)")
            Spacer(modifier = Modifier.height(8.dp))
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            ) {
                items(wavFiles) { file ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { 
                                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                    val success = audioService.playLocalWavFile(file.absolutePath)
                                    if (!success) {
                                        println("Erro ao reproduzir áudio")
                                    }
                                }
                            }
                            .padding(vertical = 8.dp)
                    ) {
                        Text(
                            text = file.name,
                            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Tamanho: ${(file.length() / 1024)} KB • Data: ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(file.lastModified()))}",
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
