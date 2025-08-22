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
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.vvai.calmwave.ui.theme.CalmWaveTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    // 1. Inicializa o ViewModel usando a ViewModel Factory
    private val viewModel: MainViewModel by viewModels {
        MainViewModelFactory(
            audioService = AudioService(),
            wavRecorder = WavRecorder(),
            context = applicationContext
        )
    }

    // Contrato para solicitar múltiplas permissões
    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allPermissionsGranted = permissions.entries.all { it.value }
            if (allPermissionsGranted) {
                Toast.makeText(this, "Todas as permissões concedidas!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Algumas permissões foram negadas. O aplicativo pode não funcionar corretamente.", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 2. Verifica e solicita as permissões no início
        checkAndRequestPermissions()

        enableEdgeToEdge()
        setContent {
            CalmWaveTheme {
                // 3. Coleta o estado da UI do ViewModel e passa para o Composable
                val uiState by viewModel.uiState.collectAsState()

                // 4. Carrega a lista de arquivos ao iniciar a tela
                // O LaunchedEffect executa a lógica apenas uma vez
                val context = LocalContext.current
                LaunchedEffect(Unit) {
                    val listFilesProvider: () -> List<File> = { listRecordedWavFiles() }
                    viewModel.loadWavFiles(listFilesProvider)
                }

                AudioPlayerScreen(
                    uiState = uiState,
                    onRecordClicked = {
                        val downloadsDir = getDownloadsDirectory()
                        val fileName = generateFileName()
                        val filePath = if (downloadsDir?.exists() == true) {
                            "${downloadsDir.absolutePath}/$fileName"
                        } else {
                            val cacheDir = externalCacheDir
                            cacheDir?.mkdirs()
                            "${cacheDir?.absolutePath}/$fileName"
                        }
                        if (filePath != "null/null") {
                            viewModel.startRecording(filePath)
                        } else {
                            Toast.makeText(context, "Erro: Não foi possível obter o diretório de gravação.", Toast.LENGTH_LONG).show()
                        }
                    },
                    onStopClicked = {
                        viewModel.stopRecordingAndProcess(apiEndpoint = "http://127.0.0.1:5000/upload")
                    },
                    onTestAPIClicked = {
                        viewModel.testAPI()
                    },
                    onFileClicked = { filePath ->
                        viewModel.playAudioFile(filePath)
                    },
                    onPauseResumeClicked = {
                        if (uiState.isPlaying) {
                            viewModel.pausePlayback()
                        } else {
                            viewModel.resumePlayback()
                        }
                    },
                    onStopPlaybackClicked = {
                        viewModel.stopPlayback()
                    },
                    onSeek = { timeMs ->
                        viewModel.seekTo(timeMs)
                    },
                    onRefreshFiles = {
                        val listFilesProvider: () -> List<File> = { listRecordedWavFiles() }
                        viewModel.loadWavFiles(listFilesProvider)
                    }
                )
            }
        }
    }

    // Métodos para gerenciar arquivos, movidos para a MainActivity
    // pois eles dependem do contexto da Activity
    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_AUDIO)
            }
        } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
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
}

// Funções utilitárias para o Composable
private fun formatTime(milliseconds: Long): String {
    val totalSeconds = milliseconds / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

// 5. O Composable agora recebe o estado da UI e callbacks de evento
@Composable
fun AudioPlayerScreen(
    uiState: MainViewModel.UiState,
    onRecordClicked: () -> Unit,
    onStopClicked: () -> Unit,
    onTestAPIClicked: () -> Unit,
    onFileClicked: (String) -> Unit,
    onPauseResumeClicked: () -> Unit,
    onStopPlaybackClicked: () -> Unit,
    onSeek: (Long) -> Unit,
    onRefreshFiles: () -> Unit
) {
    var isSeeking by remember { mutableStateOf(false) }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = uiState.statusText,
                modifier = Modifier.padding(bottom = 16.dp, top = 16.dp)
            )

            // Botões de Gravação e Teste
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = onRecordClicked,
                    enabled = !uiState.isRecording && !uiState.isProcessing
                ) {
                    Text(text = "Gravar")
                }

                Button(
                    onClick = onStopClicked,
                    enabled = uiState.isRecording
                ) {
                    Text(text = "Parar Gravação")
                }
                
                Button(
                    onClick = onTestAPIClicked,
                    enabled = !uiState.isRecording && !uiState.isProcessing
                ) {
                    Text(text = "Testar API")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Barra de progresso e tempo (sempre visível quando há áudio ativo - reproduzindo ou pausado)
            if (uiState.hasActiveAudio) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    // Barra de progresso visual (sempre visível)
                    LinearProgressIndicator(
                        progress = { uiState.playbackProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Slider para selecionar tempo
                    var sliderPosition by remember { mutableStateOf(uiState.currentPosition.toFloat()) }
                    
                    // Atualiza a posição do slider apenas se não estiver sendo arrastado
                    LaunchedEffect(uiState.currentPosition, isSeeking) {
                        if (!isSeeking) {
                            sliderPosition = uiState.currentPosition.toFloat()
                        }
                    }
                    
                    Slider(
                        value = sliderPosition,
                        onValueChange = { newValue ->
                            isSeeking = true
                            sliderPosition = newValue
                        },
                        onValueChangeFinished = {
                            val bounded = sliderPosition.toLong().coerceIn(0L, uiState.totalDuration)
                            onSeek(bounded)
                            isSeeking = false
                        },
                        valueRange = 0f..uiState.totalDuration.toFloat(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Tempo atual / tempo total
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatTime(uiState.currentPosition),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = formatTime(uiState.totalDuration),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Controles de reprodução
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Button(onClick = onPauseResumeClicked) {
                            Text(text = if (uiState.isPlaying) "Pausar" else "Continuar")
                        }
                        Button(onClick = onStopPlaybackClicked) {
                            Text(text = "Parar")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Lista de gravações
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Gravações (WAV)")
                Button(onClick = onRefreshFiles) {
                    Text(text = "Recarregar")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            ) {
                items(uiState.wavFiles) { file ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onFileClicked(file.absolutePath) }
                            .padding(vertical = 8.dp)
                    ) {
                        Text(text = file.name, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "Tamanho: ${(file.length() / 1024)} KB • Data: ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(file.lastModified()))}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}