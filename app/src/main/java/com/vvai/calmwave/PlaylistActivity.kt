package com.vvai.calmwave

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vvai.calmwave.components.BottomNavigationBar
import com.vvai.calmwave.components.TopBar
import com.vvai.calmwave.ui.theme.CalmWaveTheme

@OptIn(ExperimentalMaterial3Api::class)
class PlaylistActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CalmWaveTheme {
                // Estado para mostrar o modal do controlador de áudio
                var showModal by remember { mutableStateOf(false) }
                // Aba selecionada: "Áudios" ou "Playlists"
                var selectedTab by remember { mutableStateOf("Playlists") }
                // Texto de busca (agora no escopo global da tela para filtrar playlists)
                var searchText by remember { mutableStateOf("") }
                 // Nome do áudio selecionado (pode ser alterado para o áudio real)
                 val audioName = "Aula-000123-2025-03-18"
                // Lista fixa de playlists (id para gerenciamento de favoritos)
                data class PlaylistItem(val id: Int, val title: String, val subtitle: String, val color: Color)
                val allPlaylists = listOf(
                    PlaylistItem(0, "Na paz pelas ruas", "22/100 ÁUDIOS", Color(0xFF6FAF9E)),
                    PlaylistItem(1, "Lost in sound", "22/100 ÁUDIOS", Color(0xFF4B5563)),
                    PlaylistItem(2, "Matemática | 3º bim...", "22/100 ÁUDIOS", Color(0xFFF29345))
                )

                // Estado para favoritos (lista de ids)
                val favoriteIds = remember { mutableStateListOf<Int>() }

                // Estado para filtro "Apenas favoritos" e para mostrar o menu de filtros
                var onlyFavorites by remember { mutableStateOf(false) }
                var showFilterMenu by remember { mutableStateOf(false) }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFFF7F7F7)
                ) {
                    // altura aproximada da BottomNavigationBar para evitar sobreposição
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Barra superior personalizada
                        TopBar(title = "Playlists")
                        Spacer(modifier = Modifier.height(16.dp))

                        // Tabs (Áudios / Playlists) + busca
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Aba Áudios
                            val activeColor = Color(0xFF2DC9C6)
                            val inactiveColor = Color.White
                            OutlinedButton(
                                onClick = { selectedTab = "Áudios" },
                                colors = ButtonDefaults.outlinedButtonColors(containerColor = if (selectedTab == "Áudios") activeColor else inactiveColor),
                                modifier = Modifier.height(40.dp)
                            ) {
                                Text("Áudios", color = if (selectedTab == "Áudios") Color.White else Color.Black)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            // Aba Playlists
                            OutlinedButton(
                                onClick = { selectedTab = "Playlists" },
                                colors = ButtonDefaults.outlinedButtonColors(containerColor = if (selectedTab == "Playlists") activeColor else inactiveColor),
                                modifier = Modifier.height(40.dp)
                            ) {
                                Text("Playlists", color = if (selectedTab == "Playlists") Color.White else Color.Black)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            // Campo de busca (com ícone de filtro dentro como trailingIcon)
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
                                // estado local temporário dentro do sheet para permitir cancelar/aplicar
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

                        // fecha Row das tabs/busca/ícone de filtro
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
                                // Item clicável
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
                                        Text(
                                            text = audioName,
                                            modifier = Modifier.weight(1f),
                                            fontWeight = FontWeight.Medium
                                        )
                                        Spacer(modifier = Modifier.width(32.dp))
                                        Spacer(modifier = Modifier.width(32.dp))
                                    }
                                }
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
                        } else {
                            // Playlists: exibe cards como no mock
                            // Filtra playlists pelo texto de busca e pelo filtro de favoritos
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
                .fillMaxWidth(),
            containerColor = Color(0xFF2DC9C6)
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
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(16.dp))
                // Barra de progresso do áudio (placeholder, insira aqui o progresso real)
                LinearProgressIndicator(
                    progress = 0.5f,
                    color = Color.White.copy(alpha = 0.85f),
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
                    Button(onClick = { /* Insira aqui a função de retroceder */ }, colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.12f))) {
                        Text("<<", color = Color.White)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    // Botão para play/pause do áudio
                    Button(onClick = { /* Insira aqui a função de play/pause */ }, colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.12f))) {
                        Text("Play/Pause", color = Color.White)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    // Botão para avançar o áudio
                    Button(onClick = { /* Insira aqui a função de avançar */ }, colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.12f))) {
                        Text(">>", color = Color.White)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                // Botão para fechar o modal
                Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.12f))) {
                    Text("Fechar", color = Color.White)
                }
            }
        }
    }
}

@Composable
fun PlaylistCard(
    title: String,
    subtitle: String,
    color: Color,
    isFavorite: Boolean = false,
    onFavoriteToggle: () -> Unit = {},
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        // deixar o container do Card transparente para podermos desenhar o gradiente por baixo
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        // Gradiente do card todo usando a cor fornecida
        val cardGradient = Brush.horizontalGradient(
            listOf(color, lerp(color, Color.Black, 0.18f))
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(brush = cardGradient, shape = RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                // Thumbnail com gradiente horizontal baseado na cor fornecida (um pouco mais claro que o card)
                val thumbGradient = Brush.horizontalGradient(listOf(color, lerp(color, Color.Black, 0.12f)))
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(brush = thumbGradient)
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = subtitle, fontSize = 13.sp, color = Color.White.copy(alpha = 0.95f))
                }

                // Botão de favoritar - ícone em branco (tint ajustado para destaque quando favoritado)
                IconButton(onClick = onFavoriteToggle) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = if (isFavorite) "Desfavoritar" else "Favoritar",
                        tint = if (isFavorite) Color(0xFFFFC0C0) else Color.White.copy(alpha = 0.9f)
                    )
                }
                // Botão de opções
                IconButton(onClick = { /* opções */ }) {
                    Icon(imageVector = Icons.Filled.MoreVert, contentDescription = "Mais", tint = Color.White.copy(alpha = 0.9f))
                }
            }
        }
    }
}