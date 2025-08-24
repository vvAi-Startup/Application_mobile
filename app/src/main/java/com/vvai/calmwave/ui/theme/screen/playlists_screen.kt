package com.vvai.calmwave.ui.theme.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vvai.calmwave.R
import com.vvai.calmwave.ui.theme.*

@Composable
fun PlaylistsScreen(
    onPlaylistsClick: () -> Unit,
    onHomeClick: () -> Unit,
    onRecordingClick: () -> Unit,
    onAudioItemClick: (String) -> Unit,
    onPauseResume: () -> Unit,
    onSeek: (Long) -> Unit,
    currentPlayingAudio: String? = null,
    isPlaying: Boolean = false,
    audioList: List<AudioItem> = emptyList(),
    currentPosition: Long = 0L,
    totalDuration: Long = 0L
) {
    var selectedAudio by remember { mutableStateOf<AudioItem?>(null) }
    var showAudioControls by remember { mutableStateOf(false) }
    
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header azul claro com cantos arredondados
            HeaderSection()
            
            // Filtros de navegação
            NavigationFiltersSection()
            
            // Lista de áudios
            AudioListSection(
                audioList = audioList,
                onAudioItemClick = { audioId ->
                    // Encontrar o áudio selecionado
                    val audio = audioList.find { it.id == audioId }
                    if (audio != null) {
                        selectedAudio = audio
                        showAudioControls = true
                    }
                    onAudioItemClick(audioId)
                }
            )
        }
        
        // Barra de navegação inferior com mini-player - FIXA na parte inferior
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            BottomNavigationWithMiniPlayer(
                onPlaylistsClick = onPlaylistsClick,
                onHomeClick = onHomeClick,
                onRecordingClick = onRecordingClick,
                onPauseResume = onPauseResume,
                currentPlayingAudio = currentPlayingAudio,
                isPlaying = isPlaying
            )
        }
    }
    
    // Bottom Sheet com controles de áudio
    if (showAudioControls && selectedAudio != null) {
        AudioControlsBottomSheet(
            audioItem = selectedAudio!!,
            isPlaying = isPlaying,
            onPauseResume = onPauseResume,
            onDismiss = { showAudioControls = false },
            currentPosition = currentPosition,
            totalDuration = totalDuration,
            onSeek = onSeek
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
                text = stringResource(R.string.playlists_title),
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
private fun NavigationFiltersSection() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        // Botões de filtro
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Botão Áudios (selecionado)
            Button(
                onClick = { },
                modifier = Modifier.fillMaxWidth(0.48f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = TealGreen
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.audios_filter),
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            
            // Botão Playlists
            Button(
                onClick = { },
                modifier = Modifier.fillMaxWidth(0.48f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = LightTealGreen.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.playlists_filter),
                    color = DarkTeal,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Barra de pesquisa com filtro
        SearchBarWithFilter()
    }
}

@Composable
private fun SearchBarWithFilter() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(
                color = Color.White,
                shape = RoundedCornerShape(24.dp)
            )
            .border(
                width = 1.dp,
                color = Color(0xFFE1BEE7), // Roxo claro
                shape = RoundedCornerShape(24.dp)
            )
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Ícone de lupa
            Text(
                text = "🔍",
                fontSize = 16.sp
            )
            
            // Campo de pesquisa
            BasicTextField(
                value = "",
                onValueChange = { },
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .padding(horizontal = 12.dp),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = DarkTeal
                ),
                decorationBox = { innerTextField ->
                    if (true) { // Sempre vazio por enquanto
                        Text(
                            text = stringResource(R.string.search_placeholder),
                            color = Color.Gray,
                            fontSize = 14.sp
                        )
                    }
                    innerTextField()
                }
            )
            
            // Ícone de filtro
            Text(
                text = "⚙️",
                fontSize = 16.sp
            )
        }
    }
}

@Composable
private fun AudioListSection(
    audioList: List<AudioItem>,
    onAudioItemClick: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 140.dp), // Espaço para a bottom bar
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(audioList) { audioItem ->
            AudioListItem(
                audioItem = audioItem,
                onClick = { onAudioItemClick(audioItem.id) }
            )
        }
    }
}

@Composable
private fun AudioListItem(
    audioItem: AudioItem,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(
                color = LightBlue.copy(alpha = 0.5f),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Nome do áudio
            Text(
                text = audioItem.name,
                color = DarkTeal,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.fillMaxWidth(0.5f)
            )
            
            // Data do áudio
            if (audioItem.date.isNotEmpty()) {
                Text(
                    text = audioItem.date,
                    color = DarkGray,
                    fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth(0.3f)
                )
            }
            
            // Ícones à direita
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Ícone de relógio
                Text(
                    text = "🕐",
                    fontSize = 16.sp
                )
                
                // Ícone de três pontos (menu)
                Text(
                    text = "⋮",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun BottomNavigationWithMiniPlayer(
    onPlaylistsClick: () -> Unit,
    onHomeClick: () -> Unit,
    onRecordingClick: () -> Unit,
    onPauseResume: () -> Unit,
    currentPlayingAudio: String?,
    isPlaying: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp) // Altura aumentada para incluir mini-player
            .background(
                color = NavigationGray,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            )
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Mini-player
            if (currentPlayingAudio != null) {
                MiniPlayerSection(
                    audioName = currentPlayingAudio,
                    isPlaying = isPlaying,
                    onPauseResume = onPauseResume
                )
            }
            
            // Barra de navegação principal
            BottomNavigationSection(
                onPlaylistsClick = onPlaylistsClick,
                onHomeClick = onHomeClick,
                onRecordingClick = onRecordingClick
            )
        }
    }
}

@Composable
private fun MiniPlayerSection(
    audioName: String,
    isPlaying: Boolean,
    onPauseResume: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Nome do áudio
            Text(
                text = audioName,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.fillMaxWidth(0.7f)
            )
            
            // Botão pause/play
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clickable { onPauseResume() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isPlaying) "⏸️" else "▶️",
                    fontSize = 16.sp
                )
            }
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
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Playlists (selecionado)
            NavigationItem(
                icon = "📋",
                label = stringResource(R.string.playlists_nav),
                onClick = onPlaylistsClick,
                isSelected = true
            )
            
            // Logo no meio (Home)
            NavigationItem(
                icon = null,
                label = "",
                onClick = onHomeClick,
                isSelected = false,
                isLogo = true
            )
            
            // Gravação
            NavigationItem(
                icon = "🎤",
                label = stringResource(R.string.recording_nav),
                onClick = onRecordingClick,
                isSelected = false
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

@Composable
private fun AudioControlsBottomSheet(
    audioItem: AudioItem,
    isPlaying: Boolean,
    onPauseResume: () -> Unit,
    onDismiss: () -> Unit,
    currentPosition: Long = 0L,
    totalDuration: Long = 0L,
    onSeek: (Long) -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable { onDismiss() }
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    color = Color.White,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                )
                .padding(24.dp)
                .clickable { /* Evita que o clique se propague */ },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Handle para arrastar
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .background(
                        color = Color.Gray.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(2.dp)
                    )
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Nome do áudio
            Text(
                text = audioItem.name,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = DarkTeal,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Data do áudio
            if (audioItem.date.isNotEmpty()) {
                Text(
                    text = audioItem.date,
                    fontSize = 14.sp,
                    color = DarkGray,
                    textAlign = TextAlign.Center
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Controles principais
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Botão Play/Pause principal
                IconButton(
                    onClick = onPauseResume,
                    modifier = Modifier
                        .size(72.dp)
                        .background(
                            color = TealGreen,
                            shape = CircleShape
                        )
                ) {
                    Text(
                        text = if (isPlaying) "⏸️" else "▶️",
                        fontSize = 32.sp
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Controle de progresso do áudio
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Progresso",
                    fontSize = 14.sp,
                    color = DarkGray,
                    fontWeight = FontWeight.Medium
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Slider de progresso do áudio
                Slider(
                    value = if (totalDuration > 0) (currentPosition.toFloat() / totalDuration.toFloat()) else 0f,
                    onValueChange = { progress ->
                        val newPosition = (progress * totalDuration).toLong()
                        onSeek(newPosition)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = TealGreen,
                        activeTrackColor = TealGreen,
                        inactiveTrackColor = LightBlue.copy(alpha = 0.3f)
                    )
                )
                
                // Tempo atual e total
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatTime(currentPosition),
                        fontSize = 12.sp,
                        color = DarkGray
                    )
                    Text(
                        text = formatTime(totalDuration),
                        fontSize = 12.sp,
                        color = DarkGray
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// Data class para representar um item de áudio
data class AudioItem(
    val id: String,
    val name: String,
    val duration: String = "",
    val date: String = "",
    val filePath: String = ""
)

// Função utilitária para formatar tempo
private fun formatTime(milliseconds: Long): String {
    val totalSeconds = milliseconds / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
} 