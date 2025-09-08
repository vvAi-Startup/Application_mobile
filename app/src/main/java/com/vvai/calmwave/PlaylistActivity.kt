package com.vvai.calmwave

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vvai.calmwave.components.BottomNavigationBar
import com.vvai.calmwave.components.TopBar
import com.vvai.calmwave.ui.theme.CalmWaveTheme
import com.vvai.calmwave.ui.components.PlaylistComponents.AudioControllerModal
import com.vvai.calmwave.ui.components.PlaylistComponents.PlaylistCard
import com.vvai.calmwave.ui.components.PlaylistComponents.PlaylistTabs
import java.io.File // Adicione este import no topo do arquivo

@OptIn(ExperimentalMaterial3Api::class)
class PlaylistActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels {
        MainViewModelFactory(
            audioService = AudioService(),
            wavRecorder = WavRecorder(),
            context = applicationContext
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CalmWaveTheme {
                LaunchedEffect(Unit) {
                    viewModel.loadWavFiles {
                        val dir = applicationContext.getExternalFilesDir(null)
                        dir?.listFiles { f -> f.isFile && f.name.endsWith(".wav", ignoreCase = true) }?.toList() ?: emptyList()
                    }
                }

                val uiState by viewModel.uiState.collectAsState()
                val wavFiles = uiState.wavFiles // lista dos áudios gravados

                // Estado para mostrar o modal do controlador de áudio
                var showModal by remember { mutableStateOf(false) }
                var selectedAudioFile by remember { mutableStateOf<File?>(null) }
                // Aba selecionada: "Áudios" ou "Playlists"
                var selectedTab by remember { mutableStateOf("Playlists") }
                // Texto de busca
                var searchText by remember { mutableStateOf("") }
                // Nome do áudio selecionado (pode ser alterado para o áudio real)
                val audioName = "Aula-000123-2025-03-18"
                // Lista fixa de playlists
                data class PlaylistItem(val id: Int, val title: String, val subtitle: String, val color: Color)
                val allPlaylists = listOf(
                    PlaylistItem(0, "Na paz pelas ruas", "22/100 ÁUDIOS", Color(0xFF6FAF9E)),
                    PlaylistItem(1, "Lost in sound", "22/100 ÁUDIOS", Color(0xFF4B5563)),
                    PlaylistItem(2, "Matemática | 3º bim...", "22/100 ÁUDIOS", Color(0xFFF29345))
                )
                // Estado para favoritos
                val favoriteIds = remember { mutableStateListOf<Int>() }
                // Estado para filtro "Apenas favoritos" e para mostrar o menu de filtros
                var onlyFavorites by remember { mutableStateOf(false) }
                var showFilterMenu by remember { mutableStateOf(false) }

                val activeColor = Color(0xFF2DC9C6)

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

                        // Tabs (Áudios / Playlists) e busca
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PlaylistTabs(
                                selectedTab = selectedTab,
                                onTabSelected = { selectedTab = it }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedTextField(
                                value = searchText,
                                onValueChange = { searchText = it },
                                placeholder = { Text("Buscar") },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp),
                                shape = MaterialTheme.shapes.small,
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedBorderColor = activeColor,
                                    focusedBorderColor = activeColor,
                                    cursorColor = activeColor,
                                    unfocusedContainerColor = Color.White,
                                    focusedContainerColor = Color.White
                                ),
                                singleLine = true,
                                trailingIcon = {
                                    IconButton(onClick = { showFilterMenu = true }) {
                                        Icon(imageVector = Icons.Filled.FilterList, contentDescription = "Filtrar", tint = activeColor)
                                    }
                                },
                                textStyle = LocalTextStyle.current.copy(fontSize = MaterialTheme.typography.bodyMedium.fontSize)
                            )
                            Spacer(modifier = Modifier.width(8.dp))

                            // Modal BottomSheet para filtros
                            if (showFilterMenu) {
                                var tempOnlyFavorites by remember { mutableStateOf(onlyFavorites) }
                                val sheetColor = Color(0xFF2DC9C6)
                                ModalBottomSheet(
                                    onDismissRequest = { showFilterMenu = false },
                                    modifier = Modifier.fillMaxWidth(),
                                    containerColor = sheetColor
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp)
                                    ) {
                                        Text(text = "Filtros", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(
                                                checked = tempOnlyFavorites,
                                                onCheckedChange = { tempOnlyFavorites = it },
                                                colors = CheckboxDefaults.colors(checkedColor = Color.White, uncheckedColor = Color.White)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Apenas favoritos", color = Color.White)
                                        }
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                            TextButton(onClick = { showFilterMenu = false }) { Text("Cancelar", color = Color.White) }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Button(onClick = { onlyFavorites = tempOnlyFavorites; showFilterMenu = false }, colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.12f))) { Text("Aplicar", color = Color.White) }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Conteúdo dependendo da aba selecionada
                        if (selectedTab == "Áudios") {
                            // Lista de áudios (apenas o primeiro é clicável para mostrar o modal)
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    .weight(1f)
                            ) {
                                items(wavFiles) { file: File -> // Especifique o tipo aqui
                                    var showMenu by remember { mutableStateOf(false) }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .background(Color(0xFF8EEAE7), shape = MaterialTheme.shapes.medium)
                                            .padding(12.dp)
                                            .clickable {
                                                selectedAudioFile = file
                                                showModal = true
                                                viewModel.playAudioFile(file.absolutePath)
                                            },
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = file.name,
                                            modifier = Modifier.weight(1f),
                                            fontWeight = FontWeight.Medium
                                        )
                                        Spacer(modifier = Modifier.width(16.dp))
                                        IconButton(onClick = { showMenu = true }) {
                                            Icon(
                                                imageVector = Icons.Filled.MoreVert,
                                                contentDescription = "Opções"
                                            )
                                        }
                                        DropdownMenu(
                                            expanded = showMenu,
                                            onDismissRequest = { showMenu = false }
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("Excluir") },
                                                onClick = {
                                                    showMenu = false
                                                    // TODO: Adicione aqui a lógica para excluir o áudio
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Mover para playlist") },
                                                onClick = {
                                                    showMenu = false
                                                    // TODO: Adicione aqui a lógica para mover para playlist
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            // Playlists: exibe cards como no mock
                            val filteredBySearch = if (searchText.isBlank()) allPlaylists else allPlaylists.filter { it.title.contains(searchText, ignoreCase = true) }
                            val displayed = if (onlyFavorites) filteredBySearch.filter { favoriteIds.contains(it.id) } else filteredBySearch

                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    .weight(1f),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(displayed) { item ->
                                    PlaylistCard(
                                        title = item.title,
                                        subtitle = item.subtitle,
                                        color = item.color,
                                        isFavorite = favoriteIds.contains(item.id),
                                        onFavoriteToggle = {
                                            if (favoriteIds.contains(item.id)) favoriteIds.remove(item.id) else favoriteIds.add(item.id)
                                        },
                                        onClick = { /* abrir playlist */ }
                                    )
                                }
                            }
                        }

                        // Barra de navegação inferior para trocar de tela
                        BottomNavigationBar(selected = "Playlists")

                        // Modal com controladores de áudio (aparece ao clicar no primeiro áudio)
                        AudioControllerModal(
                            audioName = selectedAudioFile?.name ?: "",
                            showModal = showModal,
                            onDismiss = { 
                                showModal = false
                                viewModel.stopPlayback()
                            }
                        )
                    }
                }
            }
        }
    }
}