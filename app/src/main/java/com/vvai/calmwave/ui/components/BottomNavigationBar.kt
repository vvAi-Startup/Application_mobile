package com.vvai.calmwave.components

import android.content.Context
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
import androidx.compose.ui.unit.dp
import com.vvai.calmwave.GravarActivity
import com.vvai.calmwave.PlaylistActivity

@Composable
fun BottomNavigationBar(selected: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
        // Fixed height bar so it appears same size across screens
        val barHeight = 72.dp
        Row(
            modifier = modifier
                .fillMaxWidth()
                .height(barHeight)
                .background(Color(0xFF222222)),
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
                tint = if (selected == "Playlists") Color(0xFF2DC9C6) else Color.White,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Playlists",
                color = if (selected == "Playlists") Color(0xFF2DC9C6) else Color.White,
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
                tint = if (selected == "Gravação") Color(0xFF2DC9C6) else Color.White,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Gravação",
                color = if (selected == "Gravação") Color(0xFF2DC9C6) else Color.White,
            )
        }
    }
}