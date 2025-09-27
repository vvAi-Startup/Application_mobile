package com.vvai.calmwave

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.unit.sp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vvai.calmwave.components.BottomNavigationBar
import com.vvai.calmwave.components.TopBar
import com.vvai.calmwave.ui.theme.CalmWaveTheme
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.core.content.ContextCompat // <-- Importação adicionada

// Novos imports para animação e desenho
import androidx.compose.animation.core.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.yield

class GravarActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels {
        MainViewModelFactory(
            audioService = AudioService(),
            wavRecorder = WavRecorder(),
            context = applicationContext
        )
    }

    // Contrato para solicitar múltiplas permissões quando esta Activity for exibida
    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allPermissionsGranted = permissions.entries.all { it.value }
            if (allPermissionsGranted) {
                Toast.makeText(this, "Todas as permissões concedidas!", Toast.LENGTH_SHORT).show()
            } else {
                
            }
        }

    // Função para checar e solicitar permissões necessárias para gravação
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Solicita permissões ao abrir a tela de gravação (primeira tela do app)
        checkAndRequestPermissions()

        setContent {
            CalmWaveTheme {
                val uiState by viewModel.uiState.collectAsState()
                val isRecording = uiState.isRecording
                val isPaused = uiState.isPaused
                val wavFiles = uiState.wavFiles
                val elapsedSeconds = uiState.currentPosition / 1000 // converte ms para segundos

                // LaunchedEffect para atualizar o tempo de gravação
                LaunchedEffect(isRecording, isPaused) {
                    while (isRecording && !isPaused) {
                        viewModel.incrementCurrentPosition(1000) // incrementa 1 segundo (1000 ms)
                        kotlinx.coroutines.delay(1000)
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFFF7F7F7)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // altura aproximada da BottomNavigationBar para evitar sobreposição
                        val bottomBarHeight = 72.dp

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = bottomBarHeight),
                            horizontalAlignment = Alignment.CenterHorizontally // CORRIGIDO
                        ) {
                            // Header customizado com canto inferior arredondado
                            // Usando o componente TopBar reutilizável
                            TopBar(title = "Gravação")

                            Spacer(modifier = Modifier.height(18.dp))

                            // Substitui Card pela imagem ocupando toda a área de gravação
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .clip(RoundedCornerShape(14.dp))
                            ) {
                                // Imagem de fundo alinhada à base, ocupando toda a largura
                                Image(
                                    painter = painterResource(id = R.drawable.menina_nuvem),
                                    contentDescription = "Fundo de gravação",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .fillMaxHeight(0.75f)
                                        .align(Alignment.BottomCenter),
                                    contentScale = ContentScale.Crop
                                )

                                // Overlay dos controles sobre a imagem
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally, // CORRIGIDO
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) { // CORRIGIDO
                                        Text(
                                            text = "Calm Wave",
                                            style = MaterialTheme.typography.titleLarge,
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF0B6B63)
                                        )
                                        Text(
                                            text = "Acompanhe a sua gravação",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontSize = 16.sp,
                                            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                                            color = Color(0xFF0B6B63).copy(alpha = 0.9f)
                                        )

                                        // Contador sincronizado com a gravação
                                        Text(
                                            text = formatDuration(elapsedSeconds),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontSize = 18.sp,
                                            modifier = Modifier.padding(vertical = 8.dp),
                                            color = Color(0xFF0B6B63)
                                        )

                                        // Waveform mock semi-transparente
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(88.dp)
                                                .border(2.dp, Color(0xFF12B089).copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                                                .padding(10.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            AnimatedWaveform(
                                                isRecording = isRecording,
                                                isPaused = isPaused,
                                                modifier = Modifier
                                                    .fillMaxWidth(0.95f)
                                                    .height(48.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))
                                    }
                                }
                            }
                        }
                        // Controles fixos na parte inferior (acima da BottomNavigationBar)
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = bottomBarHeight + 12.dp)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!isRecording) {
                                Button(
                                    onClick = {
                                        // Inicia gravação
                                        val filePath = "${applicationContext.getExternalFilesDir(null)?.absolutePath}/audio_${System.currentTimeMillis()}.wav"
                                        viewModel.startRecording(filePath)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF12B089)),
                                    shape = RoundedCornerShape(40.dp),
                                    modifier = Modifier
                                        .width(220.dp)
                                        .height(64.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = "Iniciar", tint = Color.White, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(text = "Iniciar", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                }
                            } else {
                                // Encerrar arredondado
                                Button(
                                    onClick = {
                                        // Encerra gravação
                                        viewModel.stopRecordingAndProcess("")
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0B6B63)),
                                    shape = RoundedCornerShape(40.dp),
                                    modifier = Modifier
                                        .width(140.dp)
                                        .height(64.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(imageVector = Icons.Filled.Close, contentDescription = "Encerrar", tint = Color.White, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(text = "Encerrar", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                // Pausar / Continuar arredondado
                                Button(
                                    onClick = {
                                        if (isPaused) {
                                            // Retomar gravação
                                            viewModel.resumeRecording()
                                        } else {
                                            // Pausar gravação
                                            viewModel.pauseRecording()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF12B089)),
                                    shape = RoundedCornerShape(40.dp),
                                    modifier = Modifier
                                        .width(140.dp)
                                        .height(64.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = if (isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                                            contentDescription = if (isPaused) "Retomar" else "Pausar",
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(text = if (isPaused) "Retomar" else "Pausar", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                }
                            }
                        }

                        BottomNavigationBar(
                            selected = "Gravação",
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                        )
                    }
                }
            }
        }
    }
}

// Composable para waveform animada (movida para o topo do arquivo para evitar erro de contexto)
@Composable
fun AnimatedWaveform(isRecording: Boolean, isPaused: Boolean, modifier: Modifier = Modifier) {
    val barCount = 36

    // animatable que mantém a fase atual; quando pausado o LaunchedEffect é cancelado e o valor fica 'congelado'
    val phaseAnim = remember { androidx.compose.animation.core.Animatable(0f) }

    // atualiza continuamente com suavização nas transições entre estados
    LaunchedEffect(isPaused, isRecording) {
        // loop mais lento para suavizar movimento geral
        while (isActive) {
            when {
                isRecording && !isPaused -> {
                    // gravação ativa: mais lento (suaviza início/pausa)
                    phaseAnim.animateTo(phaseAnim.value + 360f, animationSpec = tween(durationMillis = 2500, easing = LinearEasing))
                }
                isPaused -> {
                    // pausado: movimento muito suave, pequenos passos e maior delay
                    phaseAnim.animateTo(phaseAnim.value + 30f, animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing))
                    // mantém mais tempo 'congelado'
                    delay(500L)
                }
                else -> {
                    // não gravando: animação bem lenta e suave
                    phaseAnim.animateTo(phaseAnim.value + 360f, animationSpec = tween(durationMillis = 4500, easing = LinearEasing))
                }
            }
            // pausa curta para permitir reavaliação de estados sem tight loop
            yield()
        }
    }

    val phase = phaseAnim.value

    // cor verde pastel
    val pastelGreen = Color(0xFFBDEEDC)
    // Target alpha conforme estado; animado para suavizar mudanças abruptas
    val targetAlpha = when {
        isRecording && !isPaused -> 0.85f
        isPaused -> 0.45f
        else -> 0.35f
    }
    val alphaWhenRecording by animateFloatAsState(targetValue = targetAlpha, animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing))
     val radiusPx = with(LocalDensity.current) { 4.dp.toPx() }

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val gapFraction = 0.35f
            val barWidth = w / (barCount + (barCount - 1) * gapFraction)
            val gap = barWidth * gapFraction

            for (i in 0 until barCount) {
                val angle = (phase + i * 12) * (PI / 180f)
                val normalized = abs(sin(angle)).toFloat() // 0..1
                // base entre 20% e 100% da altura
                val barH = (0.2f + normalized * 0.8f) * h
                val x = i * (barWidth + gap)
                val top = h - barH
                drawRoundRect(
                    color = pastelGreen.copy(alpha = alphaWhenRecording * (0.5f + 0.5f * normalized)),
                    topLeft = Offset(x, top),
                    size = Size(barWidth, barH),
                    cornerRadius = CornerRadius(radiusPx, radiusPx)
                )
            }
        }
    }
}

// Utilitário para formatar segundos em HH:MM:SS
fun formatDuration(seconds: Long): String {
    val hrs = seconds / 3600
    val mins = (seconds % 3600) / 60
    val secs = seconds % 60
    return String.format("%02d:%02d:%02d", hrs, mins, secs)
}