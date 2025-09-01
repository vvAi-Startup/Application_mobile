package com.vvai.calmwave

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vvai.calmwave.components.BottomNavigationBar
import com.vvai.calmwave.components.TopBar
import com.vvai.calmwave.ui.theme.CalmWaveTheme
class PlaylistActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CalmWaveTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFFF7F7F7)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // TopBar
                        TopBar(title = "Playlists")

                        Spacer(modifier = Modifier.height(16.dp))

                        // Filtros
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = { /* TODO */ },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2DC9C6))
                            ) {
                                Text("Áudios")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedButton(
                                onClick = { /* TODO */ }
                            ) {
                                Text("Playlists")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedTextField(
                                value = "",
                                onValueChange = {},
                                placeholder = { Text("Buscar") },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Lista de áudios
                        val audios = List(8) { "Aula-000123-2025-03-18" }
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .weight(1f)
                        ) {
                            items(audios) { audio ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .background(Color(0xFF8EEAE7), shape = MaterialTheme.shapes.medium)
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = audio,
                                        modifier = Modifier.weight(1f),
                                        fontWeight = FontWeight.Medium
                                    )
                                    // Espaço para botões/ícones futuros
                                    Spacer(modifier = Modifier.width(32.dp))
                                    Spacer(modifier = Modifier.width(32.dp))
                                }
                            }
                        }

                        // Bottom Navigation
                        BottomNavigationBar(selected = "Playlists")
                    }
                }
            }
        }
    }
}