package com.vvai.calmwave.components

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import com.vvai.calmwave.R
import com.vvai.calmwave.GravarActivity
import com.vvai.calmwave.PlaylistActivity
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.media3.common.Player
import com.vvai.calmwave.PrincipalActivity
import com.vvai.calmwave.PlaybackForegroundService
import com.vvai.calmwave.PlaybackPlayerHolder
import androidx.compose.ui.tooling.preview.Preview
import com.vvai.calmwave.ui.theme.CalmWaveTheme
import com.vvai.calmwave.ui.components.WaveProgressBar
import com.vvai.calmwave.util.resolveAudioDisplayName
import com.vvai.calmwave.util.NetworkMonitor
import com.vvai.calmwave.util.SyncStatusTracker
import kotlinx.coroutines.delay
import androidx.compose.ui.text.font.FontWeight

@Composable
fun BottomNavigationBar(selected: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val player = remember(context) { PlaybackPlayerHolder.getPlayer(context) }
    val miniControlButtonSize = 48.dp
    val miniControlIconSize = 32.dp
    val miniPlayerRowHeight = 52.dp
    val miniProgressHeight = 10.dp
    val miniPlayerHeight = miniPlayerRowHeight + miniProgressHeight

    var hasMedia by remember { mutableStateOf(false) }
    var currentTitle by remember { mutableStateOf("") }
    var isPlaying by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }

    val networkMonitor = remember { NetworkMonitor.getInstance(context) }
    val isOnline by networkMonitor.isOnline.collectAsState(initial = networkMonitor.isCurrentlyOnline())
    val isSyncing by SyncStatusTracker.isSyncing.collectAsState()

    val (connectionStatusText, connectionStatusColor) = when {
        !isOnline -> "Offline" to Color(0xFFD32F2F)
        isSyncing -> "Sincronizando" to Color(0xFFF9A825)
        else -> "Online" to Color(0xFF2E7D32)
    }

    fun refreshMiniPlayerState() {
        val item = player.currentMediaItem
        val path = item?.localConfiguration?.uri?.path ?: item?.mediaId.orEmpty()
        hasMedia = player.mediaItemCount > 0 && path.isNotBlank()
        currentTitle = resolveAudioDisplayName(context, path)
        isPlaying = player.isPlaying
        val duration = player.duration
        val position = player.currentPosition
        progress = if (duration > 0) {
            (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                refreshMiniPlayerState()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                refreshMiniPlayerState()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                refreshMiniPlayerState()
            }
        }
        player.addListener(listener)
        refreshMiniPlayerState()
        onDispose {
            player.removeListener(listener)
        }
    }

    LaunchedEffect(player, hasMedia) {
        while (true) {
            if (hasMedia) {
                refreshMiniPlayerState()
            }
            delay(160)
        }
    }

    val smoothProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 180),
        label = "miniPlayerProgress"
    )

    val barHeight = 72.dp
    
    // Animações para Playlists
    val playlistsIconSize by animateDpAsState(
        targetValue = if (selected == "Playlists") 32.dp else 24.dp,
        animationSpec = tween(durationMillis = 300),
        label = "playlistsIconSize"
    )
    val playlistsColor by animateColorAsState(
        targetValue = if (selected == "Playlists") Color(0xFF2DC9C6) else Color.White,
        animationSpec = tween(durationMillis = 300),
        label = "playlistsColor"
    )
    
    // Animações para Gravação
    val gravacaoIconSize by animateDpAsState(
        targetValue = if (selected == "Gravação") 32.dp else 24.dp,
        animationSpec = tween(durationMillis = 300),
        label = "gravacaoIconSize"
    )
    val gravacaoColor by animateColorAsState(
        targetValue = if (selected == "Gravação") Color(0xFF2DC9C6) else Color.White,
        animationSpec = tween(durationMillis = 300),
        label = "gravacaoColor"
    )
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .height(barHeight)
            .background(Color(0xFF222222))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(70.dp)
                .align(Alignment.BottomCenter),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clickable {
                        if (selected != "Playlists") {
                            val intentPlaylist = Intent(context, PlaylistActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            }
                            context.startActivity(intentPlaylist)
                        }
                    }
            ) {
                Icon(
                    imageVector = Icons.Filled.PlaylistPlay,
                    contentDescription = "Ícone Playlists",
                    tint = playlistsColor,
                    modifier = Modifier.size(playlistsIconSize)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Playlists",
                    color = playlistsColor,
                )
            }

            // Principal (center) - logo only
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clickable {
                        if (selected != "Principal") {
                            val intent = Intent(context, PrincipalActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            }
                            context.startActivity(intent)
                        }
                    }
            ) {
                Image(
                    painter = painterResource(id = R.drawable.logo),
                    contentDescription = "Logo",
                    modifier = Modifier.size(30.dp),
                    contentScale = ContentScale.Fit
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Início",
                    color = if (selected == "Principal") Color(0xFF2DC9C6) else Color.White,
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clickable {
                        if (selected != "Gravação") {
                            val intentGravar = Intent(context, GravarActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            }
                            context.startActivity(intentGravar)
                        }
                    }
            ) {
                Icon(
                    imageVector = Icons.Filled.Mic,
                    contentDescription = "Ícone Gravação",
                    tint = gravacaoColor,
                    modifier = Modifier.size(gravacaoIconSize)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Gravação",
                    color = gravacaoColor,
                )
            }
        }

        Text(
            text = connectionStatusText,
            color = connectionStatusColor,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-4).dp, y = (-12).dp)
        )

        if (hasMedia) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .offset(y = -miniPlayerHeight)
                    .background(Color(0xFF222222))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(miniPlayerRowHeight)
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = currentTitle,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            PlaybackForegroundService.start(context)
                            if (player.hasPreviousMediaItem()) {
                                player.seekToPreviousMediaItem()
                            }
                        },
                        modifier = Modifier.size(miniControlButtonSize)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SkipPrevious,
                            contentDescription = "Anterior",
                            tint = Color.White,
                            modifier = Modifier.size(miniControlIconSize)
                        )
                    }
                    IconButton(
                        onClick = {
                            if (isPlaying) {
                                player.pause()
                            } else {
                                PlaybackForegroundService.start(context)
                                player.play()
                            }
                        },
                        modifier = Modifier.size(miniControlButtonSize)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (isPlaying) "Pausar" else "Tocar",
                            tint = Color.White,
                            modifier = Modifier.size(miniControlIconSize)
                        )
                    }
                    IconButton(
                        onClick = {
                            PlaybackForegroundService.start(context)
                            if (player.hasNextMediaItem()) {
                                player.seekToNextMediaItem()
                            }
                        },
                        modifier = Modifier.size(miniControlButtonSize)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SkipNext,
                            contentDescription = "Próximo",
                            tint = Color.White,
                            modifier = Modifier.size(miniControlIconSize)
                        )
                    }
                    IconButton(
                        onClick = {
                            player.pause()
                            player.stop()
                            player.clearMediaItems()
                            PlaybackForegroundService.stop(context)
                            refreshMiniPlayerState()
                        },
                        modifier = Modifier.size(miniControlButtonSize)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Stop,
                            contentDescription = "Parar",
                            tint = Color.White,
                            modifier = Modifier.size(miniControlIconSize)
                        )
                    }
                }

                WaveProgressBar(
                    progress = smoothProgress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(miniProgressHeight),
                    barColor = Color(0xFF2DC9C6),
                    trackColor = Color.White.copy(alpha = 0.20f),
                    waveColor = Color.White.copy(alpha = 0.45f),
                    animationDurationMs = 1400
                )
            }
        }
    }
}

@Preview(name = "Bottom Menu", showBackground = true, widthDp = 393, heightDp = 110)
@Composable
private fun BottomNavigationBarPreview() {
    CalmWaveTheme {
        BottomNavigationBar(selected = "Principal")
    }
}