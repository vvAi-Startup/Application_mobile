package com.vvai.calmwave.ui.components.PlaylistComponents


import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.vvai.calmwave.ui.components.WaveProgressBar


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioControllerModal(
    audioName: String,
    showModal: Boolean,
    onDismiss: () -> Unit
) {
    if (showModal) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            modifier = Modifier.fillMaxWidth(),
            containerColor = Color(0xFF2DC9C6)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = audioName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(16.dp))
                WaveProgressBar(
                    progress = 0.5f,
                    barColor = Color.White.copy(alpha = 0.85f),
                    trackColor = Color.White.copy(alpha = 0.28f),
                    waveColor = Color.White.copy(alpha = 0.55f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp),
                    animationDurationMs = 1700
                )
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(onClick = { /* retroceder */ }, colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.12f))) {
                        Text("<<", color = Color.White)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Button(onClick = { /* play/pause */ }, colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.12f))) {
                        Text("Play/Pause", color = Color.White)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Button(onClick = { /* avançar */ }, colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.12f))) {
                        Text(">>", color = Color.White)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.12f))) {
                    Text("Fechar", color = Color.White)
                }
            }
        }
    }
}