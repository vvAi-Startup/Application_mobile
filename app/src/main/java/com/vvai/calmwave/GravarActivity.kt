package com.vvai.calmwave

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import com.vvai.calmwave.ui.theme.CalmWaveTheme
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip

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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CalmWaveTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFFF7F7F7)
                ) {
                    // Box para sobrepor barra de navegação fixa no fundo
                    Box(modifier = Modifier.fillMaxSize()) {
                        // altura aproximada da BottomNavigationBar para evitar sobreposição
                        val bottomBarHeight = 72.dp
                        // Estados no nível da Column para compartilhar entre botões e FAB
                        var isRecording by remember { mutableStateOf(false) }
                        var isPaused by remember { mutableStateOf(false) }
                        // contador de segundos que será exibido e sincronizado com o estado de gravação
                        var elapsedSeconds by remember { mutableStateOf(0L) }

                        // Reseta o contador quando iniciar/encerrar
                        LaunchedEffect(isRecording) {
                            if (isRecording) {
                                elapsedSeconds = 0L
                            } else {
                                elapsedSeconds = 0L
                            }
                        }

                        // Incrementa o contador apenas enquanto grava e não estiver pausado
                        LaunchedEffect(isRecording, isPaused) {
                            if (isRecording && !isPaused) {
                                while (isActive && isRecording && !isPaused) {
                                    delay(1000L)
                                    elapsedSeconds += 1L
                                }
                            }
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = bottomBarHeight), // restaurado padding original
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Header customizado com canto inferior arredondado
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(112.dp)
                                    .clip(RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp))
                                    .background(Color(0xFFE8FFFB))
                                    .padding(horizontal = 20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Gravação",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0B6B63)
                                    )
                                    // Avatar circular (placeholder com ícone)
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(Color.White),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Person,
                                            contentDescription = "Avatar",
                                            tint = Color(0xFF0B6B63),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }

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
                                        .fillMaxWidth() // ocupa completamente as laterais
                                        .fillMaxHeight(0.75f) // ocupa a parte inferior
                                        .align(Alignment.BottomCenter), // desloca a imagem 24.dp para baixo
                                    contentScale = ContentScale.Crop
                                )

                                // Overlay dos controles sobre a imagem
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = "Calm Wave",
                                            style = MaterialTheme.typography.titleLarge,
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF0B6B63) // trocado para verde
                                        )
                                        Text(
                                            text = "Acompanhe a sua gravação",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontSize = 16.sp,
                                            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                                            color = Color(0xFF0B6B63).copy(alpha = 0.9f) // subtítulo em verde
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
                                        // inicia nova gravação: reseta o tempo e começa
                                        elapsedSeconds = 0L
                                        isPaused = false
                                        isRecording = true
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
                                        // para a gravação e reseta o contador
                                        isRecording = false
                                        isPaused = false
                                        elapsedSeconds = 0L
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
                                    onClick = { isPaused = !isPaused },
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
                            modifier = Modifier.align(Alignment.BottomCenter)
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