package com.vvai.calmwave

// ========================================
// FRONTEND + BACKEND MISTO - MAIN ACTIVITY
// ========================================
// Este arquivo contém tanto lógica de UI quanto lógica de negócio
// RECOMENDAÇÃO: Separar lógica de negócio para controllers

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
import com.vvai.calmwave.controller.MainViewModel
import com.vvai.calmwave.controller.MainViewModelFactory
import com.vvai.calmwave.service.AudioService
import com.vvai.calmwave.service.WavRecorder
import com.vvai.calmwave.service.WebSocketService
import com.vvai.calmwave.ui.RenameRecordingDialog
import com.vvai.calmwave.ui.theme.CalmWaveTheme
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import androidx.lifecycle.lifecycleScope
import com.vvai.calmwave.models.UiState

class MainActivity : ComponentActivity() {

    // ========================================
    // BACKEND: Inicialização do ViewModel
    // ========================================
    //  MANTER: Injeção de dependências
    private val viewModel: MainViewModel by viewModels {
        val audioService = AudioService()
        val webSocketService = WebSocketService(this, lifecycleScope)
        val wavRecorder = WavRecorder(webSocketService, lifecycleScope)
        
        MainViewModelFactory(
            audioService = audioService,
            wavRecorder = wavRecorder,
            webSocketService = webSocketService,
            context = this
        )
    }

    // ========================================
    // BACKEND: Contrato para solicitar permissões
    // ========================================
    // ⚠️ REVISAR: Pode ser movido para um PermissionController
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

        // ========================================
        // BACKEND: Verificação de permissões
        // ========================================
        // ⚠️ REVISAR: Pode ser movido para um PermissionController
        checkAndRequestPermissions()

        // ========================================
        // FRONTEND: Configuração da UI
        // ========================================
        //  MANTER: Configuração da interface
        enableEdgeToEdge()
        setContent {
            CalmWaveTheme {
                // ========================================
                // BACKEND: Coleta do estado da UI do ViewModel
                // ========================================
                //  MANTER: Comunicação com ViewModel
                val uiState by viewModel.uiState.collectAsState()

                // ========================================
                // BACKEND: Carrega a lista de arquivos ao iniciar
                // ========================================
                // ⚠️ REVISAR: Pode ser movido para um FileController
                val context = LocalContext.current
                LaunchedEffect(Unit) {
                    val listFilesProvider: () -> List<File> = { listRecordedWavFiles() }
                    viewModel.loadWavFiles(listFilesProvider)
                }

                // ========================================
                // FRONTEND: Interface principal do usuário
                // ========================================
                //  MANTER: UI principal
                AudioPlayerScreen(
                    uiState = uiState,
                    onRecordClicked = {
                        // ========================================
                        // BACKEND: Lógica de gravação
                        // ========================================
                        // ⚠️ REVISAR: Pode ser movido para um RecordingController
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
                        // ========================================
                        // BACKEND: Parar gravação e mostrar diálogo de renomeação
                        // ========================================
                        //  MANTER: Comunicação com ViewModel para parar gravação
                        viewModel.stopRecording()
                    },
                    onTestAPIClicked = {
                        // ========================================
                        // BACKEND: Teste de API
                        // ========================================
                        //  MANTER: Comunicação com ViewModel
                        viewModel.testAPI()
                    },
                    onTestBasicConnectivity = {
                        // ========================================
                        // BACKEND: Teste de conectividade
                        // ========================================
                        //  MANTER: Comunicação com ViewModel
                        viewModel.testBasicConnectivity()
                    },
                    onPlayClicked = { filePath ->
                        viewModel.playAudioFile(filePath)
                    },
                    onPauseClicked = {
                        viewModel.pausePlayback()
                    },
                    onResumeClicked = {
                        viewModel.resumePlayback()
                    },
                    onStopPlaybackClicked = {
                        viewModel.stopPlayback()
                    },
                    onSeek = { position ->
                        viewModel.seekTo(position)
                    },
                    onRefreshFiles = {
                        val listFilesProvider: () -> List<File> = { listRecordedWavFiles() }
                        viewModel.loadWavFiles(listFilesProvider)
                    },
                    onFileClicked = { filePath ->
                        viewModel.playAudioFile(filePath)
                    }
                )
                
                // ========================================
                // FRONTEND: Diálogo de renomeação
                // ========================================
                //  MANTER: Diálogo para renomear gravações antes de salvar
                if (uiState.showRenameDialog && uiState.tempRecording != null) {
                    RenameRecordingDialog(
                        tempRecording = uiState.tempRecording!!,
                        currentText = uiState.renameDialogText,
                        isSaving = uiState.isSaving,
                        onTextChange = { text ->
                            viewModel.updateRenameText(text)
                        },
                        onConfirm = {
                            viewModel.confirmRecordingName()
                        },
                        onCancel = {
                            viewModel.cancelRecording()
                        },
                        onDismiss = {
                            // Não permite fechar o diálogo clicando fora
                            // O usuário deve confirmar ou cancelar
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
    uiState: UiState,
    onRecordClicked: () -> Unit,
    onStopClicked: () -> Unit,
    onTestAPIClicked: () -> Unit,
    onTestBasicConnectivity: () -> Unit,
    onPlayClicked: (String) -> Unit,
    onPauseClicked: () -> Unit,
    onResumeClicked: () -> Unit,
    onStopPlaybackClicked: () -> Unit,
    onSeek: (Long) -> Unit,
    onRefreshFiles: () -> Unit,
    onFileClicked: (String) -> Unit
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
                        Button(onClick = onPauseClicked) {
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