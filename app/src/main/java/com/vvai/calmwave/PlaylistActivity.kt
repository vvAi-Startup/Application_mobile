package com.vvai.calmwave

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
import com.vvai.calmwave.components.BottomNavigationBar
import com.vvai.calmwave.components.TopBar
import com.vvai.calmwave.ui.components.PlaylistComponents.PlaylistCard
import com.vvai.calmwave.ui.components.PlaylistComponents.PlaylistTabs
import com.vvai.calmwave.ui.components.PlaylistComponents.PlaylistSelectionDialog
import com.vvai.calmwave.ui.components.PlaylistComponents.FilterSheet
import java.io.File
import com.vvai.calmwave.R
import com.vvai.calmwave.ui.theme.CalmWaveTheme

class PlaylistActivity : ComponentActivity() {
    private lateinit var exoPlayerAudioPlayer: ExoPlayerAudioPlayer

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        exoPlayerAudioPlayer = ExoPlayerAudioPlayer(this)
        setContent {
            CalmWaveTheme {
                val context = LocalContext.current
                // --- State ---
            var showModal by remember { mutableStateOf(false) }
            var selectedAudioFile by remember { mutableStateOf<File?>(null) }
            var selectedTab by remember { mutableStateOf("Playlists") }
            var searchText by remember { mutableStateOf("") }

            data class PlaylistItem(
                val id: Int,
                var title: String,
                val subtitle: String,
                val color: Color
            )

            val playlists = remember { mutableStateListOf<PlaylistItem>() }
            // cores disponíveis para criação de playlist
            val availableColors = listOf(
                Color(0xFF6FAF9E),
                Color(0xFF4B5563),
                Color(0xFFF29345),
                Color(0xFF2DC9C6),
                Color(0xFF8EEAE7)
            )
            val audioToPlaylistMap = remember { mutableStateMapOf<String, String>() }
            val favoriteIds = remember { mutableStateListOf<Int>() }
            var onlyFavorites by remember { mutableStateOf(false) }
            var showFilterMenu by remember { mutableStateOf(false) }
            var showPlaylistDialogForAudio by remember { mutableStateOf<File?>(null) }
            var playlistFilter by remember { mutableStateOf<String?>(null) }
            val activeColor = Color(0xFF2DC9C6)
            val audioDurationCache = remember { mutableStateMapOf<String, Long>() }
            
            // Global state for DropdownMenu control - using file path as key
            var menuOpenedForFilePath by remember { mutableStateOf<String?>(null) }

            // --- Persistence ---
            fun savePlaylists() {
                val prefs = context.getSharedPreferences("playlists_prefs", 0)
                val editor = prefs.edit()
                val playlistJson = playlists.joinToString("||") {
                    listOf(it.id, it.title, it.subtitle, it.color.value).joinToString("|")
                }
                editor.putString("playlists", playlistJson)
                val audioMapJson =
                    audioToPlaylistMap.entries.joinToString("||") { it.key + "|" + it.value }
                editor.putString("audioToPlaylistMap", audioMapJson)
                editor.apply()
            }

            fun loadPlaylists() {
                val prefs = context.getSharedPreferences("playlists_prefs", 0)
                val playlistJson = prefs.getString("playlists", null)
                playlists.clear()
                if (!playlistJson.isNullOrBlank()) {
                    playlistJson.split("||").forEach {
                        val parts = it.split("|")
                        if (parts.size == 4) {
                            playlists.add(
                                PlaylistItem(
                                    parts[0].toInt(),
                                    parts[1],
                                    parts[2],
                                    Color(parts[3].toULong())
                                )
                            )
                        }
                    }
                } else {
                    // não carregar playlists padrão — começar com lista vazia e instruir o usuário a criar
                }
                val audioMapJson = prefs.getString("audioToPlaylistMap", null)
                audioToPlaylistMap.clear()
                if (!audioMapJson.isNullOrBlank()) {
                    audioMapJson.split("||").forEach {
                        val parts = it.split("|")
                        if (parts.size == 2) audioToPlaylistMap[parts[0]] = parts[1]
                    }
                }
            }
            LaunchedEffect(Unit) { loadPlaylists() }
            LaunchedEffect(playlists.toList(), audioToPlaylistMap.toMap()) { savePlaylists() }

            // --- Audio Files ---
            val wavFiles = remember {
                val dir = context.getExternalFilesDir(null)
                dir?.listFiles { f -> f.isFile && f.name.endsWith(".wav", ignoreCase = true) }
                    ?.sortedByDescending { it.lastModified() } // Ordena por data de modificação (mais recente primeiro)
                    ?: emptyList()
            }

            // --- UI ---
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color(0xFFF7F7F7)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.weight(1f)) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize(), // removido .padding(top = 16.dp) para colar a TopBar no topo
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Top Bar
                            // colar a TopBar no topo da tela respeitando a barra de status
                            TopBar(title = "Playlists", modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                            )
                            // Tabs and Search
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
                                // Compact search field rewritten to match tab button height exactly (40.dp)
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp)
                                        .background(Color.White, shape = MaterialTheme.shapes.small)
                                        .border(1.dp, color = Color(0xFFBEEAF0), shape = MaterialTheme.shapes.small),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                                        // Text area
                                        Box(modifier = Modifier.weight(1f).padding(start = 12.dp, end = 4.dp)) {
                                            androidx.compose.foundation.text.BasicTextField(
                                                value = searchText,
                                                onValueChange = { searchText = it },
                                                singleLine = true,
                                                textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, color = Color.Black),
                                                cursorBrush = androidx.compose.ui.graphics.SolidColor(activeColor),
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                            if (searchText.isBlank()) {
                                                Text(text = "Buscar", color = Color(0xFF9E9E9E), fontSize = 12.sp)
                                            }
                                        }

                                        // Trailing filter icon - compact touch target
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clickable { showFilterMenu = true },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.FilterList,
                                                contentDescription = "Filtrar",
                                                tint = activeColor,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(20.dp))
                            // Content by Tab
                            if (selectedTab == "Áudios") {
                                val filteredWavFiles = if (playlistFilter != null) {
                                    wavFiles.filter { audioToPlaylistMap[it.absolutePath] == playlistFilter }
                                } else {
                                    wavFiles
                                }
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp)
                                        .weight(1f)
                                ) {
                                    items(
                                        items = filteredWavFiles,
                                        key = { it.absolutePath }
                                    ) { file: File ->
                                        // Remove local state and use global state based on file path
                                        val isMenuOpen = menuOpenedForFilePath == file.absolutePath
                                        val durationMs =
                                            audioDurationCache.getOrPut(file.absolutePath) {
                                                val player = ExoPlayerAudioPlayer(this@PlaylistActivity)
                                                player.initializeCustom(file.absolutePath)
                                                val dur = player.getDuration()
                                                player.release()
                                                if (dur > 0 && dur < 1000 * 60 * 60 * 10) dur else -1L
                                            }
                                        val playlistName = audioToPlaylistMap[file.absolutePath]
                                        val playlistObj = playlists.find { it.title == playlistName }
                                        val playlistColor = playlistObj?.color ?: Color(0xFF8EEAE7)
                                        val textColor =
                                            if (playlistObj != null) Color.White else Color.Black
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp)
                                                .background(
                                                    playlistColor,
                                                    shape = MaterialTheme.shapes.medium
                                                )
                                                .padding(12.dp)
                                                .clickable {
                                                    selectedAudioFile = file
                                                    showModal = true
                                                },
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                if (!playlistName.isNullOrBlank()) {
                                                    Text(
                                                        text = playlistName,
                                                        color = textColor,
                                                        style = MaterialTheme.typography.bodySmall
                                                    )
                                                }
                                                Text(
                                                    text = file.name,
                                                    fontWeight = FontWeight.Medium,
                                                    color = textColor
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = if (durationMs > 0) formatMillis(durationMs) else "--:--",
                                                color = textColor.copy(alpha = 0.85f),
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            IconButton(onClick = { 
                                                menuOpenedForFilePath = if (isMenuOpen) null else file.absolutePath
                                                println("DEBUG: Menu button clicked for ${file.name}, isMenuOpen: $isMenuOpen -> ${!isMenuOpen}")
                                            }) {
                                                Icon(
                                                    imageVector = Icons.Filled.MoreVert,
                                                    contentDescription = "Opções"
                                                )
                                            }
                                            DropdownMenu(
                                                expanded = isMenuOpen,
                                                onDismissRequest = { 
                                                    menuOpenedForFilePath = null
                                                    println("DEBUG: Menu dismissed for ${file.name}")
                                                }
                                            ) {
                                                DropdownMenuItem(
                                                    text = { Text("Excluir") },
                                                    onClick = {
                                                        menuOpenedForFilePath = null
                                                        println("DEBUG: Excluir clicked for ${file.name}")
                                                        // TODO: Add audio delete logic
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("Mover para playlist") },
                                                    onClick = {
                                                        menuOpenedForFilePath = null
                                                        showPlaylistDialogForAudio = file
                                                        println("DEBUG: Mover para playlist clicked for ${file.name}")
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                val filteredBySearch =
                                    if (searchText.isBlank()) playlists else playlists.filter {
                                        it.title.contains(
                                            searchText,
                                            ignoreCase = true
                                        )
                                    }
                                val displayed =
                                    if (onlyFavorites) filteredBySearch.filter { favoriteIds.contains(it.id) } else filteredBySearch
                                var showAddDialog by remember { mutableStateOf(false) }
                                var newPlaylistName by remember { mutableStateOf("") }
                                var selectedColor by remember { mutableStateOf(availableColors.first()) }
                                Box(Modifier.fillMaxSize()) {
                                    if (displayed.isEmpty()) {
                                        // tela vazia amigável para crianças
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(horizontal = 24.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Image(
                                                painter = painterResource(id = R.drawable.disc),
                                                contentDescription = "Disco",
                                                modifier = Modifier.size(120.dp)
                                            )
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Text(
                                                text = "Está vazio!",
                                                style = MaterialTheme.typography.titleLarge,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = "Toque no + para criar uma playlist",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = Color(0xFF6B6B6B)
                                            )
                                        }
                                    } else {
                                        LazyColumn(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp)
                                                .fillMaxHeight(),
                                            verticalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            items(displayed, key = { it.id }) { item ->
                                                // subtitle agora calculado dinamicamente: audios atribuídos / total de áudios
                                                PlaylistCard(
                                                    title = item.title,
                                                    subtitle = "${audioToPlaylistMap.values.count { it == item.title }}/${wavFiles.size} ÁUDIOS",
                                                    color = item.color,
                                                    isFavorite = favoriteIds.contains(item.id),
                                                    onFavoriteToggle = {
                                                        if (favoriteIds.contains(item.id)) favoriteIds.remove(
                                                            item.id
                                                        ) else favoriteIds.add(item.id)
                                                    },
                                                    onClick = {
                                                        playlistFilter = item.title
                                                        // Muda automaticamente para aba de Áudios
                                                        selectedTab = "Áudios"
                                                        onlyFavorites = false
                                                    },
                                                    onRename = { newName ->
                                                        val idx = playlists.indexOfFirst { it.id == item.id }
                                                        if (idx >= 0) {
                                                            val oldName = playlists[idx].title
                                                            playlists[idx] = playlists[idx].copy(title = newName)
                                                            val updated = audioToPlaylistMap.mapValues { (k, v) -> if (v == oldName) newName else v }
                                                            audioToPlaylistMap.clear()
                                                            audioToPlaylistMap.putAll(updated)
                                                        }
                                                    },
                                                    onDelete = {
                                                        val playlistName = item.title
                                                        playlists.removeAll { it.id == item.id }
                                                        val toRemove = audioToPlaylistMap.filterValues { it == playlistName }.keys.toList()
                                                        toRemove.forEach { audioToPlaylistMap.remove(it) }
                                                    }
                                                )
                                            }
                                        }
                                    }
                                    FloatingActionButton(
                                        onClick = { showAddDialog = true },
                                        containerColor = Color(0xFF2DC9C6),
                                        contentColor = Color.White,
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(end = 24.dp, bottom = 88.dp) // padding extra para não sobrepor a BottomNavigationBar
                                    ) {
                                        Icon(Icons.Filled.Add, contentDescription = "Nova Playlist")
                                    }
                                    if (showAddDialog) {
                                        AlertDialog(
                                            onDismissRequest = { showAddDialog = false },
                                            title = { Text("Nova Playlist") },
                                            text = {
                                                Column {
                                                    OutlinedTextField(
                                                        value = newPlaylistName,
                                                        onValueChange = { newPlaylistName = it },
                                                        label = { Text("Nome da playlist") }
                                                    )
                                                    Spacer(modifier = Modifier.height(12.dp))
                                                    Text(text = "Escolha a cor:", style = MaterialTheme.typography.bodyMedium)
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                        availableColors.forEach { c ->
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(36.dp)
                                                                    .clip(CircleShape)
                                                                    .background(c)
                                                                    .border(
                                                                        width = if (selectedColor == c) 3.dp else 1.dp,
                                                                        color = if (selectedColor == c) Color.White else Color.Transparent,
                                                                        shape = CircleShape
                                                                    )
                                                                    .clickable { selectedColor = c }
                                                            )
                                                        }
                                                    }
                                                }
                                            },
                                            confirmButton = {
                                                TextButton(onClick = {
                                                    if (newPlaylistName.isNotBlank()) {
                                                        val nextId =
                                                            (playlists.maxOfOrNull { it.id } ?: 0) + 1
                                                        playlists.add(
                                                            PlaylistItem(
                                                                nextId,
                                                                newPlaylistName,
                                                                "${0}/${wavFiles.size} ÁUDIOS",
                                                                selectedColor
                                                            )
                                                        )
                                                        newPlaylistName = ""
                                                        selectedColor = availableColors.first()
                                                        showAddDialog = false
                                                    }
                                                }) { Text("Criar") }
                                            },
                                            dismissButton = {
                                                TextButton(onClick = {
                                                    showAddDialog = false
                                                }) { Text("Cancelar") }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        // Audio Player Modal (fixed at bottom, above nav bar)
                        if (showModal && selectedAudioFile != null) {
                            ModalBottomSheet(
                                onDismissRequest = { showModal = false },
                                content = {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFF2DC9C6))
                                            .padding(16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally // CORRIGIDO
                                    ) {
                                        Text(
                                            text = selectedAudioFile?.name ?: "",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        val duration = remember { mutableStateOf(0L) }
                                        val position = remember { mutableStateOf(0L) }
                                        val isPlaying = remember { mutableStateOf(false) }
                                        var playbackSpeed by remember { mutableStateOf(1.0f) }
                                        LaunchedEffect(selectedAudioFile) {
                                            exoPlayerAudioPlayer.release()
                                            exoPlayerAudioPlayer.initializeCustom(selectedAudioFile!!.absolutePath)
                                            exoPlayerAudioPlayer.setPlaybackSpeed(playbackSpeed)
                                            exoPlayerAudioPlayer.play()
                                        }
                                        LaunchedEffect(selectedAudioFile) {
                                            while (showModal) {
                                                duration.value = exoPlayerAudioPlayer.getDuration()
                                                position.value = exoPlayerAudioPlayer.getCurrentPosition()
                                                isPlaying.value = exoPlayerAudioPlayer.isPlaying()
                                                kotlinx.coroutines.delay(33)
                                            }
                                        }
                                        val sliderPosition by animateFloatAsState(
                                            targetValue = if (duration.value > 0) position.value.toFloat() / duration.value else 0f,
                                            label = "Audio Progress Animation"
                                        )
                                        Slider(
                                            value = sliderPosition,
                                            onValueChange = { newValue ->
                                                val seekTo = (newValue * duration.value).toLong()
                                                exoPlayerAudioPlayer.seekTo(seekTo)
                                            },
                                            colors = SliderDefaults.colors(
                                                thumbColor = Color.White,
                                                activeTrackColor = Color.White,
                                                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                                            ),
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = formatMillis(position.value),
                                                color = Color.White,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                            Text(
                                                text = formatMillis(duration.value),
                                                color = Color.White,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Row(
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            IconButton(onClick = { exoPlayerAudioPlayer.previous() }) {
                                                Icon(
                                                    imageVector = Icons.Filled.SkipPrevious,
                                                    contentDescription = "Anterior",
                                                    tint = Color.White
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(16.dp))
                                            IconButton(onClick = {
                                                if (isPlaying.value) exoPlayerAudioPlayer.pause() else exoPlayerAudioPlayer.play()
                                            }) {
                                                Icon(
                                                    imageVector = if (isPlaying.value) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                                    contentDescription = if (isPlaying.value) "Pause" else "Play",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(36.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(16.dp))
                                            IconButton(onClick = { exoPlayerAudioPlayer.next() }) {
                                                Icon(
                                                    imageVector = Icons.Filled.SkipNext,
                                                    contentDescription = "Próximo",
                                                    tint = Color.White
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Button(
                                                onClick = {
                                                    playbackSpeed = 0.5f
                                                    exoPlayerAudioPlayer.setPlaybackSpeed(playbackSpeed)
                                                },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = Color.White.copy(
                                                        alpha = 0.18f
                                                    )
                                                ),
                                                modifier = Modifier.padding(horizontal = 4.dp)
                                            ) { Text("0.5x", color = Color.White) }
                                            Button(
                                                onClick = {
                                                    playbackSpeed = 0.75f
                                                    exoPlayerAudioPlayer.setPlaybackSpeed(playbackSpeed)
                                                },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = Color.White.copy(
                                                        alpha = 0.18f
                                                    )
                                                ),
                                                modifier = Modifier.padding(horizontal = 4.dp)
                                            ) { Text("0.75x", color = Color.White) }
                                            Button(
                                                onClick = {
                                                    playbackSpeed = 1.0f
                                                    exoPlayerAudioPlayer.setPlaybackSpeed(playbackSpeed)
                                                },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = Color.White.copy(
                                                        alpha = 0.18f
                                                    )
                                                ),
                                                modifier = Modifier.padding(horizontal = 4.dp)
                                            ) { Text("1x", color = Color.White) }
                                            Button(
                                                onClick = {
                                                    playbackSpeed = 1.5f
                                                    exoPlayerAudioPlayer.setPlaybackSpeed(playbackSpeed)
                                                },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = Color.White.copy(
                                                        alpha = 0.18f
                                                    )
                                                ),
                                                modifier = Modifier.padding(horizontal = 4.dp)
                                            ) { Text("1.5x", color = Color.White) }
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Button(
                                            onClick = {
                                                showModal = false
                                                exoPlayerAudioPlayer.pause()
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color.White.copy(
                                                    alpha = 0.12f
                                                )
                                            )
                                        ) {
                                            Text("Fechar", color = Color.White)
                                        }
                                    }
                                    // Playlist assignment dialog
                                    if (showPlaylistDialogForAudio != null) {
                                        val currentPlaylist = audioToPlaylistMap[showPlaylistDialogForAudio!!.absolutePath]
                                        // Usar key para forçar recomposição correta
                                        key(showPlaylistDialogForAudio!!.absolutePath) {
                                            AlertDialog(
                                                onDismissRequest = { showPlaylistDialogForAudio = null },
                                                title = { Text("Escolha a playlist") },
                                                text = {
                                                    Column {
                                                        playlists.forEach { playlist ->
                                                            val isCurrent = playlist.title == currentPlaylist
                                                            TextButton(
                                                                onClick = {
                                                                    if (!isCurrent) {
                                                                        audioToPlaylistMap[showPlaylistDialogForAudio!!.absolutePath] = playlist.title
                                                                        savePlaylists()
                                                                    }
                                                                    showPlaylistDialogForAudio = null
                                                                },
                                                                enabled = !isCurrent
                                                            ) {
                                                                Text(
                                                                    playlist.title + if (isCurrent) " (atual)" else "",
                                                                    color = if (isCurrent) Color.Gray else Color.Unspecified
                                                                )
                                                            }
                                                        }
                                                    }
                                                },
                                                confirmButton = {},
                                                dismissButton = {
                                                    TextButton(onClick = { showPlaylistDialogForAudio = null }) {
                                                        Text("Cancelar")
                                                    }
                                                }
                                            )
                                        }
                                    }
                                    // Filter BottomSheet
                                    if (showFilterMenu) {
                                        var tempOnlyFavorites by remember { mutableStateOf(onlyFavorites) }
                                        var tempPlaylistFilter by remember { mutableStateOf(playlistFilter) }
                                        var expandedPlaylistDropdown by remember { mutableStateOf(false) }
                                        val sheetColor = Color(0xFF2DC9C6)
                                        ModalBottomSheet(
                                            onDismissRequest = { showFilterMenu = false },
                                            modifier = Modifier.fillMaxWidth(),
                                            containerColor = sheetColor,
                                            content = {
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(16.dp)
                                                ) {
                                                    Text(
                                                        text = "Filtros",
                                                        style = MaterialTheme.typography.titleMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White
                                                    )
                                                    Spacer(modifier = Modifier.height(12.dp))
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Checkbox(
                                                            checked = tempOnlyFavorites,
                                                            onCheckedChange = { tempOnlyFavorites = it },
                                                            colors = CheckboxDefaults.colors(
                                                                checkedColor = Color.White,
                                                                uncheckedColor = Color.White
                                                            )
                                                        )
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text("Apenas favoritos", color = Color.White)
                                                    }
                                                    Spacer(modifier = Modifier.height(12.dp))
                                                    Text("Playlist:", color = Color.White)
                                                    ExposedDropdownMenuBox(
                                                        expanded = expandedPlaylistDropdown,
                                                        onExpandedChange = {
                                                            expandedPlaylistDropdown = !expandedPlaylistDropdown
                                                        }
                                                    ) {
                                                        OutlinedTextField(
                                                            value = tempPlaylistFilter ?: "Todas",
                                                            onValueChange = {},
                                                            readOnly = true,
                                                            label = { Text("Playlist") },
                                                            trailingIcon = {
                                                                ExposedDropdownMenuDefaults.TrailingIcon(
                                                                    expanded = expandedPlaylistDropdown
                                                                )
                                                            },
                                                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                                                            colors = OutlinedTextFieldDefaults.colors(
                                                                unfocusedBorderColor = Color.White,
                                                                focusedBorderColor = Color.White,
                                                                cursorColor = Color.White,
                                                                unfocusedContainerColor = Color.White.copy(alpha = 0.12f),
                                                                focusedContainerColor = Color.White.copy(alpha = 0.12f),
                                                                unfocusedLabelColor = Color.White,
                                                                focusedLabelColor = Color.White
                                                            )
                                                        )
                                                        ExposedDropdownMenu(
                                                            expanded = expandedPlaylistDropdown,
                                                            onDismissRequest = { expandedPlaylistDropdown = false }
                                                        ) {
                                                            DropdownMenuItem(
                                                                text = { Text("Todas", color = Color.White) },
                                                                onClick = {
                                                                    tempPlaylistFilter = null
                                                                    expandedPlaylistDropdown = false
                                                                }
                                                            )
                                                            playlists.forEach { playlist ->
                                                                DropdownMenuItem(
                                                                    text = {
                                                                        Text(
                                                                            playlist.title,
                                                                            color = Color.White
                                                                        )
                                                                    },
                                                                    onClick = {
                                                                        tempPlaylistFilter = playlist.title
                                                                        expandedPlaylistDropdown = false
                                                                    }
                                                                )
                                                            }
                                                        }
                                                    }
                                                    Spacer(modifier = Modifier.height(16.dp))
                                                    Row(
                                                        horizontalArrangement = Arrangement.End,
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        TextButton(onClick = {
                                                            showFilterMenu = false
                                                        }) { Text("Cancelar", color = Color.White) }
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Button(
                                                            onClick = {
                                                                onlyFavorites = tempOnlyFavorites
                                                                playlistFilter = tempPlaylistFilter
                                                                showFilterMenu = false
                                                            },
                                                            colors = ButtonDefaults.buttonColors(
                                                                containerColor = Color.White.copy(
                                                                    alpha = 0.12f
                                                                )
                                                            )
                                                        ) {
                                                            Text("Aplicar", color = Color.White)
                                                        }
                                                    }
                                                }
                                            })
                                        }
                                    })
                                }
                    }
                // Playlist assignment dialog (outside player modal so it appears immediately)
                if (showPlaylistDialogForAudio != null) {
                    val currentPlaylist = audioToPlaylistMap[showPlaylistDialogForAudio!!.absolutePath]
                    // delegate to reusable composable
                    key(showPlaylistDialogForAudio!!.absolutePath) {
                        PlaylistSelectionDialog(
                            playlists = playlists.map { it.title },
                            current = currentPlaylist,
                            onSelect = { selectedTitle ->
                                audioToPlaylistMap[showPlaylistDialogForAudio!!.absolutePath] = selectedTitle
                                savePlaylists()
                                showPlaylistDialogForAudio = null
                            },
                            onDismiss = { showPlaylistDialogForAudio = null }
                        )
                    }
                }

                // Filter BottomSheet delegated to a reusable component
                if (showFilterMenu) {
                    FilterSheet(
                        playlists = playlists.map { it.title },
                        initialOnlyFavorites = onlyFavorites,
                        initialPlaylist = playlistFilter,
                        onApply = { onlyFavs, selectedPlaylist ->
                            onlyFavorites = onlyFavs
                            playlistFilter = selectedPlaylist
                            showFilterMenu = false
                        },
                        onDismiss = { showFilterMenu = false }
                    )
                }

                    // Bottom Navigation Bar
                    BottomNavigationBar(
                        selected = if (selectedTab == "Playlists" || selectedTab == "Áudios") "Playlists" else selectedTab,
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.End)
                    )
                }
                }
            }
        }

        fun onDestroy() {
            super.onDestroy()
            exoPlayerAudioPlayer.release()
        }
    }

    fun formatMillis(millis: Long): String {
        val totalSeconds = millis / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%02d:%02d".format(minutes, seconds)
    }
}