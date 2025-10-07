package com.vvai.calmwave

import android.Manifest
import android.content.Intent
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
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
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

        // 3. Configura callback para salvar automaticamente áudio processado
        viewModel.setProcessedAudioSaveCallback { processedFile ->
            val savedFile = saveProcessedAudioToDownloads(processedFile)
            if (savedFile != null) {
                runOnUiThread {
                    Toast.makeText(this, "Áudio processado salvo: ${savedFile.name}", Toast.LENGTH_LONG).show()
                }
            }
        }

        enableEdgeToEdge()
        setContent {
            CalmWaveTheme {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Button(onClick = {
                        // evita empilhar múltiplas instâncias: traz a activity existente para frente
                        val intentGravar = Intent(this@MainActivity, GravarActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        }
                        startActivity(intentGravar)
                    }) {
                        Text("Ir para Gravar")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = {
                        val intentPlaylist = Intent(this@MainActivity, PlaylistActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        }
                        startActivity(intentPlaylist)
                    }) {
                        Text("Ir para Playlist")
                    }

                    // 3. Coleta o estado da UI do ViewModel e passa para o Composable
                    val uiState by viewModel.uiState.collectAsState()

                    // 4. Carrega a lista de arquivos (originais e processados) ao iniciar a tela
                    // O LaunchedEffect executa a lógica apenas uma vez
                    val context = LocalContext.current
                    LaunchedEffect(Unit) {
                        val listFilesProvider: () -> List<File> = { listAllWavFiles() }
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
                            viewModel.stopRecordingAndProcess(apiEndpoint = Config.uploadUrl)
                        },
                        onTestAPIClicked = {
                            viewModel.testAPI()
                        },
                        onTestBasicConnectivity = {
                            viewModel.testBasicConnectivity()
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
                            val listFilesProvider: () -> List<File> = { listAllWavFiles() }
                            viewModel.loadWavFiles(listFilesProvider)
                        },
                        onSaveProcessedAudio = {
                            viewModel.saveProcessedAudio()?.let { processedFilePath ->
                                val processedFile = File(processedFilePath)
                                if (processedFile.exists()) {
                                    val savedFile = saveProcessedAudioToDownloads(processedFile)
                                    if (savedFile != null) {
                                        Toast.makeText(context, "Áudio processado salvo em Downloads: ${savedFile.name}", Toast.LENGTH_LONG).show()
                                        // Recarrega a lista para mostrar o novo arquivo
                                        val listFilesProvider: () -> List<File> = { listAllWavFiles() }
                                        viewModel.loadWavFiles(listFilesProvider)
                                    } else {
                                        Toast.makeText(context, "Erro ao salvar áudio processado", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } ?: Toast.makeText(context, "Nenhum áudio processado disponível", Toast.LENGTH_SHORT).show()
                        }
                    )
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
        val dir = getDownloadsDirectory() ?: externalCacheDir
        val files = dir?.listFiles { f -> f.isFile && f.name.endsWith(".wav", ignoreCase = true) }?.toList() ?: emptyList()
        return files.sortedByDescending { it.lastModified() }
    }

    private fun listProcessedWavFiles(): List<File> {
        val dir = getExternalFilesDir(null)
        val files = dir?.listFiles { f -> f.isFile && f.name.startsWith("processed_") && f.name.endsWith(".wav", ignoreCase = true) }?.toList() ?: emptyList()
        return files.sortedByDescending { it.lastModified() }
    }

    private fun saveProcessedAudioToDownloads(processedFile: File): File? {
        return try {
            val downloadsDir = getDownloadsDirectory()
            if (downloadsDir?.exists() != true) {
                downloadsDir?.mkdirs()
            }
            
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val newFileName = "calmwave_processed_$timestamp.wav"
            val destinationFile = File(downloadsDir, newFileName)
            
            processedFile.copyTo(destinationFile, overwrite = true)
            destinationFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun listAllWavFiles(): List<File> {
        val recordedFiles = listRecordedWavFiles()
        val processedFiles = listProcessedWavFiles()
        
        // Combina as duas listas e ordena por data de modificação
        return (recordedFiles + processedFiles).sortedByDescending { it.lastModified() }
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
    onTestBasicConnectivity: () -> Unit,
    onFileClicked: (String) -> Unit,
    onPauseResumeClicked: () -> Unit,
    onStopPlaybackClicked: () -> Unit,
    onSeek: (Long) -> Unit,
    onRefreshFiles: () -> Unit,
    onSaveProcessedAudio: () -> Unit
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
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Botões de Teste
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = onTestAPIClicked,
                    enabled = !uiState.isRecording && !uiState.isProcessing
                ) {
                    Text(text = "Testar API")
                }
                
                Button(
                    onClick = onTestBasicConnectivity,
                    enabled = !uiState.isRecording && !uiState.isProcessing
                ) {
                    Text(text = "Testar Conexão")
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
                    // Slider para selecionar tempo (única barra de progresso)
                    var sliderPosition by remember { mutableStateOf(uiState.currentPosition.toFloat()) }
                    
                    // Atualiza a posição do slider apenas se não estiver sendo arrastado
                    LaunchedEffect(uiState.currentPosition, isSeeking) {
                        if (!isSeeking) {
                            // Atualização suave da posição do slider
                            sliderPosition = uiState.currentPosition.toFloat()
                        }
                    }
                    
                    Slider(
                        value = sliderPosition,
                        onValueChange = { newValue ->
                            if (!isSeeking) {
                                isSeeking = true
                            }
                            sliderPosition = newValue
                        },
                        onValueChangeFinished = {
                            val bounded = sliderPosition.toLong().coerceIn(0L, uiState.totalDuration)
                            
                            // Executa o seek de forma segura
                            try {
                                onSeek(bounded)
                            } catch (e: Exception) {
                                println("Erro durante seek no Slider: ${e.message}")
                                e.printStackTrace()
                            }
                            
                            // Aguarda um pouco antes de permitir atualizações automáticas
                            // Delay maior para seek para trás para evitar conflitos
                            val delayMs = if (bounded < uiState.currentPosition) 300L else 150L
                            GlobalScope.launch {
                                kotlinx.coroutines.delay(delayMs)
                                isSeeking = false
                            }
                        },
                        valueRange = 0f..maxOf(uiState.totalDuration.toFloat(), 1f), // Evita divisão por zero
                        modifier = Modifier.fillMaxWidth(),
                        enabled = uiState.hasActiveAudio && !uiState.isProcessing // Só habilita quando há áudio ativo e não está processando
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
                Text(text = "Áudios (Originais e Processados)")
                Row {
                    Button(
                        onClick = onSaveProcessedAudio,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(text = "Salvar Processado")
                    }
                    Button(onClick = onRefreshFiles) {
                        Text(text = "Recarregar")
                    }
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
                        val isProcessed = file.name.startsWith("processed_") || file.name.contains("_processed_")
                        val typeIcon = if (isProcessed) "🎵 " else "🎤 "
                        val typeText = if (isProcessed) "(Processado)" else "(Original)"
                        
                        Text(
                            text = "$typeIcon${file.name}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Tipo: $typeText • Tamanho: ${(file.length() / 1024)} KB • Data: ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(file.lastModified()))}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}