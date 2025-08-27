 package com.vvai.calmwave.ui.theme.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vvai.calmwave.R
import com.vvai.calmwave.ui.theme.*
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import kotlinx.coroutines.delay

@Composable
fun RecordingScreen(
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onPausePlay: () -> Unit,
    onPlaylistsClick: () -> Unit,
    onHomeClick: () -> Unit,
    onRecordingClick: () -> Unit,
    onTestAPIClicked: () -> Unit,
    onTestBasicConnectivity: () -> Unit,
    isRecording: Boolean = false,
    recordingTime: String = "00:00:00",
    statusText: String = "Pronto para gravar"
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Header azul claro com cantos arredondados
        HeaderSection()
        
        // Conteúdo principal
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(40.dp))
            
            // Título principal
            Text(
                text = stringResource(R.string.app_name_title),
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = DarkTeal
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Subtítulo
            Text(
                text = stringResource(R.string.recording_subtitle),
                fontSize = 16.sp,
                color = DarkTeal
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Status text
            Text(
                text = statusText,
                fontSize = 14.sp,
                color = DarkGray,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Botão Iniciar/Parar
            Button(
                onClick = if (isRecording) onStopRecording else onStartRecording,
                modifier = Modifier
                    .height(56.dp)
                    .fillMaxWidth(0.8f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRecording) Color(0xFFE57373) else TealGreen
                ),
                shape = RoundedCornerShape(28.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Ícone (equalizador para gravar, stop para parar)
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isRecording) "⏹️" else "🔊",
                            fontSize = 14.sp
                        )
                    }
                    
                    Text(
                        text = if (isRecording) stringResource(R.string.stop_button) else stringResource(R.string.start_button),
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Botões de teste
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = onTestAPIClicked,
                    enabled = !isRecording,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LightTealGreen
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = "Testar API",
                        color = DarkTeal,
                        fontSize = 14.sp
                    )
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Button(
                    onClick = onTestBasicConnectivity,
                    enabled = !isRecording,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LightTealGreen
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = "Testar Conexão",
                        color = DarkTeal,
                        fontSize = 14.sp
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Timer de gravação
            if (isRecording) {
                Text(
                    text = recordingTime,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Medium,
                    color = DarkGray
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Ondas de áudio (equalizador)
                AudioWaveform(isRecording = isRecording)
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Botões de Pause/Play (apenas quando gravando)
                PlayPauseButtons(onPausePlay = onPausePlay, isPlaying = false) // isPlaying is no longer a parameter
                
                Spacer(modifier = Modifier.height(32.dp))
            }
            
            // Espaço para a imagem da menina
            Spacer(modifier = Modifier.weight(1f))
            
            // Imagem da menina na parte inferior
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .padding(bottom = 16.dp)
            ) {
                androidx.compose.foundation.Image(
                    painter = painterResource(id = R.drawable.menina),
                    contentDescription = "Menina meditando",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
        }
        
        // Barra de navegação inferior
        BottomNavigationSection(
            onPlaylistsClick = onPlaylistsClick,
            onHomeClick = onHomeClick,
            onRecordingClick = onRecordingClick
        )
    }
}

@Composable
private fun HeaderSection() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .background(
                color = LightBlue,
                shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
            )
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.recording_title),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = DarkTeal
            )
            
            // Avatar do usuário
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(DarkTeal),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "👤",
                    fontSize = 20.sp
                )
            }
        }
    }
}

@Composable
private fun AudioWaveform(isRecording: Boolean) {
    var audioData by remember { mutableStateOf(List(20) { 0f }) }
    
    // Simula captura de áudio real (em produção, isso viria do WavRecorder)
    LaunchedEffect(isRecording) {
        while (true) {
            delay(50) // Atualiza a cada 50ms para smooth animation
            if (isRecording) {
                // Simula dados de áudio reais (em produção, isso seria real)
                audioData = List(20) { index ->
                    val baseValue = 0.3f
                    val variation = (Math.random() * 0.7).toFloat()
                    val position = index / 19f
                    val wave = Math.sin(System.currentTimeMillis() * 0.01 + position * Math.PI * 2).toFloat() * 0.3f
                    (baseValue + variation + wave).coerceIn(0f, 1f)
                }
            } else {
                // Quando não está gravando, mostra barras baixas
                audioData = List(20) { 0.1f }
            }
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .background(
                color = LightTealGreen.copy(alpha = 0.3f),
                shape = RoundedCornerShape(30.dp)
            )
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom
        ) {
            audioData.forEachIndexed { index, amplitude ->
                val height = (amplitude * 40 + 20).dp // 20-60dp baseado na amplitude
                
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(height)
                        .background(
                            color = LightTealGreen,
                            shape = RoundedCornerShape(1.5.dp)
                        )
                        .animateContentSize(
                            animationSpec = tween(50)
                        )
                )
            }
        }
    }
}

@Composable
private fun PlayPauseButtons(onPausePlay: () -> Unit, isPlaying: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        // Botão Pause/Play da gravação
        Button(
            onClick = onPausePlay,
            modifier = Modifier
                .size(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = TealGreen
            ),
            shape = CircleShape
        ) {
            Text(
                text = "⏸️",
                fontSize = 20.sp
            )
        }
    }
}

@Composable
private fun BottomNavigationSection(
    onPlaylistsClick: () -> Unit,
    onHomeClick: () -> Unit,
    onRecordingClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .background(
                color = NavigationGray,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            )
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Playlists
            NavigationItem(
                icon = "📋",
                label = stringResource(R.string.playlists_nav),
                onClick = onPlaylistsClick,
                isSelected = false
            )
            
            // Logo no meio (Home)
            NavigationItem(
                icon = null,
                label = "",
                onClick = onHomeClick,
                isSelected = false,
                isLogo = true
            )
            
            // Gravação (selecionado)
            NavigationItem(
                icon = "🎤",
                label = stringResource(R.string.recording_nav),
                onClick = onRecordingClick,
                isSelected = true
            )
        }
    }
}

@Composable
private fun NavigationItem(
    icon: String?,
    label: String,
    onClick: () -> Unit,
    isSelected: Boolean,
    isLogo: Boolean = false
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(if (isSelected) 48.dp else 40.dp)
                .background(
                    color = if (isSelected) TealGreen else Color.Transparent,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isLogo) {
                // Logo no meio
                androidx.compose.foundation.Image(
                    painter = painterResource(id = R.drawable.logo),
                    contentDescription = "Logo CalmWave",
                    modifier = Modifier.size(32.dp),
                    contentScale = ContentScale.Fit
                )
            } else {
                // Ícone normal
                Text(
                    text = icon ?: "",
                    fontSize = if (isSelected) 24.sp else 20.sp
                )
            }
        }
        
        if (label.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                color = Color.White,
                fontSize = 12.sp
            )
        }
    }
}