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
import com.vvai.calmwave.ui.theme.screen.RecordingScreen
import com.vvai.calmwave.ui.theme.screen.PlaylistsScreen
import com.vvai.calmwave.ui.theme.screen.AudioItem
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
                // 3. Coleta o estado da UI do ViewModel
                val uiState by viewModel.uiState.collectAsState()

                // 4. Carrega a lista de arquivos ao iniciar a tela
                val context = LocalContext.current
                LaunchedEffect(Unit) {
                    val listFilesProvider: () -> List<File> = { listRecordedWavFiles() }
                    viewModel.loadWavFiles(listFilesProvider)
                }

                // Estado para controlar a navegação
                var currentScreen by remember { mutableStateOf("recording") }
                
                // Lista de áudios reais gravados para a tela de playlists
                val audioList = remember(uiState.wavFiles) {
                    uiState.wavFiles.map { file ->
                        AudioItem(
                            id = file.absolutePath,
                            name = file.name.replace(".wav", ""),
                            duration = "", // TODO: Implementar duração real
                            date = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(file.lastModified())),
                            filePath = file.absolutePath
                        )
                    }
                }

                when (currentScreen) {
                    "recording" -> {
                        // Tela de gravação
                        RecordingScreen(
                            onStartRecording = {
                                try {
                                    // Verifica permissões
                                    if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                                        Toast.makeText(context, "Permissão de gravação necessária", Toast.LENGTH_LONG).show()
                                        return@RecordingScreen
                                    }
                                    
                                    val recordingsDir = File(context.getExternalFilesDir(null), "recordings")
                                    if (!recordingsDir.exists()) {
                                        recordingsDir.mkdirs()
                                    }
                                    
                                    val fileName = generateFileName()
                                    val filePath = "${recordingsDir.absolutePath}/$fileName"
                                    
                                    println("Iniciando gravação em: $filePath")
                                    println("Diretório existe: ${recordingsDir.exists()}")
                                    println("Diretório pode escrever: ${recordingsDir.canWrite()}")
                                    
                                    viewModel.startRecording(filePath)
                                } catch (e: Exception) {
                                    println("Erro ao iniciar gravação: ${e.message}")
                                    e.printStackTrace()
                                    Toast.makeText(context, "Erro ao iniciar gravação: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            },
                            onStopRecording = {
                                try {
                                    println("Parando gravação...")
                                    viewModel.stopRecordingAndProcess(apiEndpoint = "http://127.0.0.1:5000/upload")
                                    
                                    // Recarrega a lista de arquivos após parar a gravação
                                    val listFilesProvider: () -> List<File> = { listRecordedWavFiles() }
                                    viewModel.loadWavFiles(listFilesProvider)
                                    
                                    println("Gravação parada, arquivos recarregados")
                                } catch (e: Exception) {
                                    println("Erro ao parar gravação: ${e.message}")
                                    e.printStackTrace()
                                    Toast.makeText(context, "Erro ao parar gravação: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            },
                            onPausePlay = {
                                // TODO: Implementar pausa da gravação
                                Toast.makeText(context, "Funcionalidade de pausar gravação em desenvolvimento", Toast.LENGTH_SHORT).show()
                            },
                            onPlaylistsClick = {
                                currentScreen = "playlists"
                            },
                            onHomeClick = {
                                // TODO: Implementar navegação para home
                                Toast.makeText(context, "Home em desenvolvimento", Toast.LENGTH_SHORT).show()
                            },
                            onRecordingClick = {
                                // Já estamos na tela de gravação
                            },
                            isRecording = uiState.isRecording,
                            recordingTime = formatRecordingTime(uiState.recordingDuration)
                        )
                    }
                    "playlists" -> {
                        // Tela de playlists
                        PlaylistsScreen(
                            onPlaylistsClick = {
                                // Já estamos na tela de playlists
                            },
                            onHomeClick = {
                                // TODO: Implementar navegação para home
                                Toast.makeText(context, "Home em desenvolvimento", Toast.LENGTH_SHORT).show()
                            },
                            onRecordingClick = {
                                currentScreen = "recording"
                            },
                            onAudioItemClick = { audioId ->
                                // Reproduzir o áudio selecionado
                                viewModel.playAudioFile(audioId)
                                Toast.makeText(context, "Reproduzindo áudio...", Toast.LENGTH_SHORT).show()
                            },
                            onPauseResume = {
                                if (uiState.isPlaying) {
                                    viewModel.pausePlayback()
                                } else {
                                    viewModel.resumePlayback()
                                }
                            },
                            onSeek = { position ->
                                viewModel.seekTo(position)
                            },
                            currentPlayingAudio = uiState.currentPlayingFile?.let { fileName ->
                                File(fileName).name.replace(".wav", "")
                            },
                            isPlaying = uiState.isPlaying,
                            audioList = audioList,
                            currentPosition = uiState.currentPosition,
                            totalDuration = uiState.totalDuration
                        )
                    }
                }
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
        // Usar diretório específico para gravações dentro do app
        val recordingsDir = File(getExternalFilesDir(null), "recordings")
        if (!recordingsDir.exists()) {
            recordingsDir.mkdirs()
        }
        
        val files = recordingsDir.listFiles { file -> file.isFile && file.name.endsWith(".wav", ignoreCase = true) }?.toList() ?: emptyList()
        return files.sortedByDescending { it.lastModified() }
    }

    private fun formatRecordingTime(durationMs: Long): String {
        val totalSeconds = durationMs / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
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