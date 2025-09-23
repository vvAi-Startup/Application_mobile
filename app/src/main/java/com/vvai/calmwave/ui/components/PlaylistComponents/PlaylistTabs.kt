package com.vvai.calmwave.ui.components.PlaylistComponents


import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.*
import androidx.compose.ui.unit.dp

@Composable
fun PlaylistTabs(
    selectedTab: String,
    onTabSelected: (String) -> Unit
) {
    val activeColor = Color(0xFF2DC9C6)
    val inactiveColor = Color.White
    Row {
        OutlinedButton(
            onClick = { onTabSelected("Áudios") },
            colors = ButtonDefaults.outlinedButtonColors(containerColor = if (selectedTab == "Áudios") activeColor else inactiveColor),
            modifier = Modifier.height(40.dp)
        ) {
            Text("Áudios", color = if (selectedTab == "Áudios") Color.White else Color.Black)
        }
        Spacer(modifier = Modifier.width(8.dp))
        OutlinedButton(
            onClick = { onTabSelected("Playlists") },
            colors = ButtonDefaults.outlinedButtonColors(containerColor = if (selectedTab == "Playlists") activeColor else inactiveColor),
            modifier = Modifier.height(40.dp)
        ) {
            Text("Playlists", color = if (selectedTab == "Playlists") Color.White else Color.Black)
        }
    }
}