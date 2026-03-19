package com.vvai.calmwave.components

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import com.vvai.calmwave.R
import com.vvai.calmwave.ui.screens.GravarActivity
import com.vvai.calmwave.PlaylistActivity
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.painterResource
import com.vvai.calmwave.ui.screens.PrincipalActivity

@Composable
fun BottomNavigationBar(selected: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
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
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF222222))
            .navigationBarsPadding()
            .height(barHeight),
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
            Spacer(modifier = Modifier.height(4.dp))
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
                modifier = Modifier.size(36.dp),
                contentScale = ContentScale.Fit
            )
            Spacer(modifier = Modifier.height(4.dp))
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
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Gravação",
                color = gravacaoColor,
            )
        }
    }
}