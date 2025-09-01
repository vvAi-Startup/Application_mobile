package com.vvai.calmwave

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
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
                // Estado para mostrar o modal do controlador de áudio
                var showModal by remember { mutableStateOf(false) }
                // Nome do áudio selecionado (pode ser alterado para o áudio real)
                val audioName = "Aula-000123-2025-03-18"

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
                        // Barra superior personalizada
                        TopBar(title = "Playlists")
                        Spacer(modifier = Modifier.height(16.dp))

                        // Filtros e busca
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Botão para filtrar áudios
                            Button(
                                onClick = { /* Aqui você pode filtrar para mostrar apenas áudios */ },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2DC9C6))
                            ) {
                                Text("Áudios")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            // Botão para filtrar playlists
                            OutlinedButton(
                                onClick = { /* Aqui você pode filtrar para mostrar apenas playlists */ }
                            ) {
                                Text("Playlists")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            // Campo de busca, digite para filtrar os áudios/playlists
                            var searchText by remember { mutableStateOf("") }
                            OutlinedTextField(
                                value = searchText,
                                onValueChange = { searchText = it }, // Atualiza o texto de busca
                                placeholder = { Text("Buscar") },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp), // altura mais fina
                                shape = MaterialTheme.shapes.small, // bordas arredondadas
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedBorderColor = Color(0xFF2DC9C6),
                                    focusedBorderColor = Color(0xFF2DC9C6),
                                    cursorColor = Color(0xFF2DC9C6),
                                    unfocusedContainerColor = Color.White,
                                    focusedContainerColor = Color.White
                                ),
                                singleLine = true,
                                textStyle = LocalTextStyle.current.copy(fontSize = MaterialTheme.typography.bodyMedium.fontSize)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Lista de áudios (apenas o primeiro é clicável para mostrar o modal)
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .weight(1f)
                        ) {
                            // Exemplo de item clicável para abrir o modal de controle de áudio
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .background(Color(0xFF8EEAE7), shape = MaterialTheme.shapes.medium)
                                        .padding(12.dp)
                                        .clickable { showModal = true }, // Ao clicar, abre o modal
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Nome do áudio
                                    Text(
                                        text = audioName,
                                        modifier = Modifier.weight(1f),
                                        fontWeight = FontWeight.Medium
                                    )
                                    // Espaço reservado para futuros ícones de timer e opções
                                    Spacer(modifier = Modifier.width(32.dp))
                                    Spacer(modifier = Modifier.width(32.dp))
                                }
                            }
                            // Os demais itens não são clicáveis (apenas visual)
                            items(7) { _ ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .background(Color(0xFF8EEAE7), shape = MaterialTheme.shapes.medium)
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = audioName,
                                        modifier = Modifier.weight(1f),
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.width(32.dp))
                                    Spacer(modifier = Modifier.width(32.dp))
                                }
                            }
                        }

                        // Barra de navegação inferior para trocar de tela
                        BottomNavigationBar(selected = "Playlists")

                        // Modal com controladores de áudio (aparece ao clicar no primeiro áudio)
                        AudioControllerModal(
                            audioName = audioName,
                            showModal = showModal,
                            onDismiss = { showModal = false }
                        )
                    }
                }
            }
        }
    }
}

// Modalzinho com os controladores de áudio
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
            modifier = Modifier
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Nome do áudio selecionado
                Text(
                    text = audioName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))
                // Barra de progresso do áudio (placeholder, insira aqui o progresso real)
                LinearProgressIndicator(
                    progress = 0.5f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                )
                Spacer(modifier = Modifier.height(24.dp))
                // Botões de controle do áudio
                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Botão para retroceder o áudio
                    Button(onClick = { /* Insira aqui a função de retroceder */ }) {
                        Text("<<")
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    // Botão para play/pause do áudio
                    Button(onClick = { /* Insira aqui a função de play/pause */ }) {
                        Text("Play/Pause")
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    // Botão para avançar o áudio
                    Button(onClick = { /* Insira aqui a função de avançar */ }) {
                        Text(">>")
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                // Botão para fechar o modal
                Button(onClick = onDismiss) {
                    Text("Fechar")
                }
            }
        }
    }
}