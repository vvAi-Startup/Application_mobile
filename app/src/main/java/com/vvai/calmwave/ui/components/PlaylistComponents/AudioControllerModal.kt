package com.vvai.calmwave.ui.components.PlaylistComponents


import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp


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
                LinearProgressIndicator(
                    progress = 0.5f,
                    color = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
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