package com.vvai.calmwave.components

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
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
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF222222))
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Playlists",
            color = if (selected == "Playlists") Color(0xFF2DC9C6) else Color.White,
            modifier = Modifier
                .clickable {
                    if (selected != "Playlists") {
                        context.startActivity(Intent(context, PlaylistActivity::class.java))
                    }
                }
        )
        Text(
            text = "Gravação",
            color = if (selected == "Gravação") Color(0xFF2DC9C6) else Color.White,
            modifier = Modifier
                .clickable {
                    if (selected != "Gravação") {
                        context.startActivity(Intent(context, GravarActivity::class.java))
                    }
                }
        )
    }
}