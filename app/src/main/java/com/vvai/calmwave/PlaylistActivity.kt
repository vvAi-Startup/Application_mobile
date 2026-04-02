package com.vvai.calmwave

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import com.vvai.calmwave.components.BottomNavigationBar
import com.vvai.calmwave.components.TopBar
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.vvai.calmwave.ui.components.PlaylistComponents.PlaylistCard
import com.vvai.calmwave.ui.components.PlaylistComponents.ColorWheelPicker
import com.vvai.calmwave.ui.components.PlaylistComponents.PlaylistTabs
import com.vvai.calmwave.ui.components.PlaylistComponents.PlaylistSelectionDialog
import com.vvai.calmwave.ui.components.PlaylistComponents.FilterSheet
import java.io.File
import com.vvai.calmwave.R
import com.vvai.calmwave.data.repository.AnalyticsRepository
import com.vvai.calmwave.data.remote.ApiClient
import com.vvai.calmwave.util.AudioMetadataCache
import com.vvai.calmwave.util.clearAuthSession
import com.vvai.calmwave.util.enterImmersiveMode
import com.vvai.calmwave.util.getPlaybackUiPollingMs
import com.vvai.calmwave.util.getUserAudioDir
import com.vvai.calmwave.util.getUserScopedKey
import com.vvai.calmwave.util.NetworkMonitor
import com.vvai.calmwave.ui.theme.CalmWaveTheme
import kotlinx.coroutines.launch

private const val PREFS_PLAYLISTS = "playlists"
private const val PREFS_AUDIO_TO_PLAYLIST_MAP = "audioToPlaylistMap"
private const val PREFS_AUDIO_DISPLAY_NAMES = "audioDisplayNames"

private val DEFAULT_PLAYLIST_COLORS = listOf(
    Color(0xFF6FAF9E),
    Color(0xFF4B5563),
    Color(0xFFF29345),
    Color(0xFF2DC9C6),
    Color(0xFF8EEAE7)
)

private data class PlaylistEntry(
    val id: Int,
    var title: String,
    val subtitle: String,
    val color: Color
)

class PlaylistActivity : ComponentActivity() {
    private lateinit var exoPlayerAudioPlayer: ExoPlayerAudioPlayer

    companion object {
        const val EXTRA_OPEN_PLAYLIST_TITLE = "extra_open_playlist_title"
        const val EXTRA_OPEN_AUDIOS_TAB = "extra_open_audios_tab"
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        enterImmersiveMode()

        val openPlaylistTitle = intent.getStringExtra(EXTRA_OPEN_PLAYLIST_TITLE)
        val openAudiosTab = intent.getBooleanExtra(EXTRA_OPEN_AUDIOS_TAB, true)
        
        // Define animação de entrada (deslizar da esquerda ao vir de Gravação)
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        
        exoPlayerAudioPlayer = ExoPlayerAudioPlayer(this)
        setContent {
            CalmWaveTheme {
                val context = LocalContext.current
                val lifecycleOwner = LocalLifecycleOwner.current
                val analyticsRepository = remember { AnalyticsRepository(context) }
                val networkMonitor = remember { NetworkMonitor.getInstance(context) }
                // --- State ---
            var showModal by remember { mutableStateOf(false) }
            var selectedAudioFile by remember { mutableStateOf<File?>(null) }
            var selectedPlaybackQueue by remember { mutableStateOf<List<File>>(emptyList()) }
            var selectedTab by remember { mutableStateOf("Playlists") }
            var searchText by remember { mutableStateOf("") }

            val playlists = remember { mutableStateListOf<PlaylistEntry>() }
            // cores disponíveis para criação de playlist
            val availableColors = DEFAULT_PLAYLIST_COLORS
            val audioToPlaylistMap = remember { mutableStateMapOf<String, String>() }
            val audioDisplayNames = remember { mutableStateMapOf<String, String>() }
            val favoriteIds = remember { mutableStateListOf<Int>() }
            var onlyFavorites by remember { mutableStateOf(false) }
            var showFilterMenu by remember { mutableStateOf(false) }
            var showPlaylistDialogForAudio by remember { mutableStateOf<File?>(null) }
            var playlistFilter by remember { mutableStateOf<String?>(null) }
            val activeColor = Color(0xFF2DC9C6)
            val playbackUiPollingMs = remember { getPlaybackUiPollingMs(context) }
            var consumedInitialNavigation by remember { mutableStateOf(false) }
            var pendingSyncPaths by remember { mutableStateOf(setOf<String>()) }
            var isOnline by remember { mutableStateOf(networkMonitor.isCurrentlyOnline()) }
            
            // Global state for DropdownMenu control - using file path as key
            var menuOpenedForFilePath by remember { mutableStateOf<String?>(null) }

            // --- Persistence ---
            fun savePlaylists() {
                val prefs = context.getSharedPreferences("playlists_prefs", 0)
                val editor = prefs.edit()
                val playlistsKey = getUserScopedKey(context, PREFS_PLAYLISTS)
                val audioMapKey = getUserScopedKey(context, PREFS_AUDIO_TO_PLAYLIST_MAP)
                val displayNamesKey = getUserScopedKey(context, PREFS_AUDIO_DISPLAY_NAMES)
                val playlistJson = playlists.joinToString("||") {
                    listOf(it.id, it.title, it.subtitle, it.color.value).joinToString("|")
                }
                editor.putString(playlistsKey, playlistJson)
                val audioMapJson =
                    audioToPlaylistMap.entries.joinToString("||") { it.key + "|" + it.value }
                editor.putString(audioMapKey, audioMapJson)
                val audioDisplayNamesJson =
                    audioDisplayNames.entries.joinToString("||") { it.key + "|" + it.value }
                editor.putString(displayNamesKey, audioDisplayNamesJson)
                editor.apply()
            }

            fun loadPlaylists() {
                val prefs = context.getSharedPreferences("playlists_prefs", 0)
                val playlistsKey = getUserScopedKey(context, PREFS_PLAYLISTS)
                val audioMapKey = getUserScopedKey(context, PREFS_AUDIO_TO_PLAYLIST_MAP)
                val displayNamesKey = getUserScopedKey(context, PREFS_AUDIO_DISPLAY_NAMES)

                val playlistJson = prefs.getString(playlistsKey, null)
                playlists.clear()
                if (!playlistJson.isNullOrBlank()) {
                    playlistJson.split("||").forEach {
                        val parts = it.split("|")
                        if (parts.size == 4) {
                            playlists.add(
                                PlaylistEntry(
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
                val audioMapJson = prefs.getString(audioMapKey, null)
                audioToPlaylistMap.clear()
                if (!audioMapJson.isNullOrBlank()) {
                    audioMapJson.split("||").forEach {
                        val parts = it.split("|")
                        if (parts.size == 2) audioToPlaylistMap[parts[0]] = parts[1]
                    }
                }

                val audioDisplayNamesJson = prefs.getString(displayNamesKey, null)
                audioDisplayNames.clear()
                if (!audioDisplayNamesJson.isNullOrBlank()) {
                    audioDisplayNamesJson.split("||").forEach {
                        val parts = it.split("|")
                        if (parts.size == 2) audioDisplayNames[parts[0]] = parts[1]
                    }
                }
            }
            LaunchedEffect(Unit) { loadPlaylists() }
            LaunchedEffect(playlists.toList(), audioToPlaylistMap.toMap(), audioDisplayNames.toMap()) { savePlaylists() }
            suspend fun refreshPendingSyncState() {
                pendingSyncPaths = analyticsRepository.getPendingAudioFilePaths()
                isOnline = networkMonitor.isCurrentlyOnline()
            }
            LaunchedEffect(Unit) { refreshPendingSyncState() }
            LaunchedEffect(Unit) {
                while (true) {
                    refreshPendingSyncState()
                    kotlinx.coroutines.delay(2500)
                }
            }
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                            refreshPendingSyncState()
                        }
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }
            LaunchedEffect(playlists.toList(), consumedInitialNavigation) {
                if (!consumedInitialNavigation && !openPlaylistTitle.isNullOrBlank()) {
                    if (playlists.any { it.title == openPlaylistTitle }) {
                        playlistFilter = openPlaylistTitle
                        selectedTab = if (openAudiosTab) "Áudios" else "Playlists"
                    }
                    consumedInitialNavigation = true
                }
            }

            // --- Audio Files ---
            val wavFiles = remember { mutableStateListOf<File>() }

            fun refreshWavFiles() {
                val dir = getUserAudioDir(context)
                val files = dir?.listFiles { f -> f.isFile && f.name.endsWith(".wav", ignoreCase = true) }
                    ?.sortedByDescending { it.lastModified() }
                    ?: emptyList()
                wavFiles.clear()
                wavFiles.addAll(files)
                AudioMetadataCache.invalidateMissing(files.map { it.absolutePath }.toSet())

                // Limpa nomes personalizados de arquivos que não existem mais
                val currentPaths = wavFiles.map { it.absolutePath }.toSet()
                val removedKeys = audioDisplayNames.keys.filter { it !in currentPaths }
                if (removedKeys.isNotEmpty()) {
                    removedKeys.forEach { audioDisplayNames.remove(it) }
                }
            }

            LaunchedEffect(Unit) { refreshWavFiles() }

            // --- UI ---
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color(0xFFF7F7F7)
            ) {
                // Calcula a altura da BottomNavigationBar + padding das barras de navegação do sistema
                val navigationBarHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                val bottomBarTotalHeight = 72.dp + navigationBarHeight
                
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.weight(1f)) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize(), // removido .padding(top = 16.dp) para colar a TopBar no topo
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Top Bar
                            // colar a TopBar no topo da tela respeitando a barra de status
                            TopBar(
                                title = "Playlists",
                                modifier = Modifier.fillMaxWidth(),
                                onLogoutClick = {
                                    clearAuthSession(context)
                                    ApiClient.clear()

                                    val intent = Intent(context, LoginActivity::class.java).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                    }
                                    context.startActivity(intent)
                                    (context as? Activity)?.finish()
                                }
                            )
                            val isSyncing = pendingSyncPaths.isNotEmpty()
                            Surface(
                                color = if (isSyncing) Color(0xFFFFF3E0) else Color(0xFFE8F5E9),
                                shape = RoundedCornerShape(999.dp),
                                modifier = Modifier
                                    .padding(top = 10.dp)
                                    .align(Alignment.End)
                                    .padding(end = 16.dp)
                            ) {
                                Text(
                                    text = if (isSyncing) "Sincronizando" else "Sincronizado",
                                    color = if (isSyncing) Color(0xFF8A3B00) else Color(0xFF1B5E20),
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(20.dp))
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
                                AudioTabContent(
                                    filteredWavFiles = filteredWavFiles,
                                    playlists = playlists,
                                    audioToPlaylistMap = audioToPlaylistMap,
                                    audioDisplayNames = audioDisplayNames,
                                    pendingSyncPaths = pendingSyncPaths,
                                    menuOpenedForFilePath = menuOpenedForFilePath,
                                    onMenuOpenedChange = { menuOpenedForFilePath = it },
                                    onAudioSelected = { file, queue ->
                                        selectedAudioFile = file
                                        selectedPlaybackQueue = queue
                                        showModal = true
                                    },
                                    onMoveToPlaylist = { file ->
                                        showPlaylistDialogForAudio = file
                                    },
                                    onRenameAudio = { file, customName ->
                                        val trimmed = customName.trim()
                                        if (trimmed.isBlank()) {
                                            audioDisplayNames.remove(file.absolutePath)
                                        } else {
                                            audioDisplayNames[file.absolutePath] = trimmed
                                        }
                                        savePlaylists()
                                    },
                                    formatDuration = { durationMs -> formatMillis(durationMs) }
                                )
                            } else {
                                PlaylistsTabContent(
                                    playlists = playlists,
                                    audioToPlaylistMap = audioToPlaylistMap,
                                    favoriteIds = favoriteIds,
                                    onlyFavorites = onlyFavorites,
                                    searchText = searchText,
                                    wavFiles = wavFiles,
                                    availableColors = availableColors,
                                    bottomBarTotalHeight = bottomBarTotalHeight,
                                    onOpenAudiosByPlaylist = { selectedPlaylist ->
                                        playlistFilter = selectedPlaylist
                                        selectedTab = "Áudios"
                                        onlyFavorites = false
                                    }
                                )
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
                                            text = selectedAudioFile?.let {
                                                audioDisplayNames[it.absolutePath]?.takeIf { name -> name.isNotBlank() }
                                                    ?: it.name
                                            } ?: "",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        val duration = remember { mutableStateOf(0L) }
                                        val position = remember { mutableStateOf(0L) }
                                        val isPlaying = remember { mutableStateOf(false) }
                                        var playbackSpeed by remember { mutableStateOf(1.0f) }
                                        LaunchedEffect(selectedAudioFile, selectedPlaybackQueue) {
                                            val queue = selectedPlaybackQueue.ifEmpty { listOfNotNull(selectedAudioFile) }
                                            val queuePaths = queue.map { it.absolutePath }
                                            val startIndex = queue.indexOfFirst { it.absolutePath == selectedAudioFile!!.absolutePath }

                                            exoPlayerAudioPlayer.initializeQueue(
                                                audioFiles = queuePaths,
                                                startIndex = if (startIndex >= 0) startIndex else 0
                                            )
                                            exoPlayerAudioPlayer.setPlaybackSpeed(playbackSpeed)
                                            exoPlayerAudioPlayer.play()
                                        }
                                        LaunchedEffect(selectedAudioFile) {
                                            while (showModal) {
                                                duration.value = exoPlayerAudioPlayer.getDuration()
                                                position.value = exoPlayerAudioPlayer.getCurrentPosition()
                                                isPlaying.value = exoPlayerAudioPlayer.isPlaying()

                                                val currentPath = exoPlayerAudioPlayer.getCurrentMediaPath()
                                                if (!currentPath.isNullOrBlank()) {
                                                    selectedPlaybackQueue
                                                        .firstOrNull { it.absolutePath == currentPath }
                                                        ?.let { currentFile ->
                                                            if (selectedAudioFile?.absolutePath != currentFile.absolutePath) {
                                                                selectedAudioFile = currentFile
                                                            }
                                                        }
                                                }
                                                kotlinx.coroutines.delay(playbackUiPollingMs)
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
                                            Spacer(modifier = Modifier.width(8.dp))
                                            IconButton(onClick = {
                                                exoPlayerAudioPlayer.stopAllPlayback()
                                                showModal = false
                                            }) {
                                                Icon(
                                                    imageVector = Icons.Filled.Stop,
                                                    contentDescription = "Parar",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(34.dp)
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
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersiveMode()
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    private fun formatMillis(millis: Long): String {
        val totalSeconds = millis / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%02d:%02d".format(minutes, seconds)
    }
}

@Composable
private fun ColumnScope.AudioTabContent(
    filteredWavFiles: List<File>,
    playlists: List<PlaylistEntry>,
    audioToPlaylistMap: MutableMap<String, String>,
    audioDisplayNames: MutableMap<String, String>,
    pendingSyncPaths: Set<String>,
    menuOpenedForFilePath: String?,
    onMenuOpenedChange: (String?) -> Unit,
    onAudioSelected: (File, List<File>) -> Unit,
    onMoveToPlaylist: (File) -> Unit,
    onRenameAudio: (File, String) -> Unit,
    formatDuration: (Long) -> String
) {
    var renameTarget by remember { mutableStateOf<File?>(null) }
    var renameText by remember { mutableStateOf("") }
    var showProcessedCategory by remember { mutableStateOf(true) }
    var showOriginalCategory by remember { mutableStateOf(true) }

    val processedFiles = remember(filteredWavFiles) {
        filteredWavFiles.filter {
            it.name.startsWith("denoised_", ignoreCase = true) ||
                it.name.startsWith("processed_", ignoreCase = true) // compatibilidade com arquivos antigos
        }
    }
    val originalFiles = remember(filteredWavFiles) {
        filteredWavFiles.filter {
            it.name.startsWith("audio_", ignoreCase = true) ||
                (!it.name.startsWith("denoised_", ignoreCase = true) &&
                    !it.name.startsWith("processed_", ignoreCase = true))
        }
    }
    val playbackOrder = remember(processedFiles, originalFiles, showProcessedCategory, showOriginalCategory) {
        buildList {
            if (showProcessedCategory) addAll(processedFiles)
            if (showOriginalCategory) addAll(originalFiles)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .weight(1f)
    ) {
        fun androidx.compose.foundation.lazy.LazyListScope.audioSection(
            title: String,
            files: List<File>,
            expanded: Boolean,
            onToggle: () -> Unit
        ) {
            item(key = "section_$title") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 6.dp)
                        .clickable { onToggle() },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$title ${if (expanded) "v" else ">"}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF0F4B58)
                    )
                }
            }

            if (!expanded) return

            if (files.isEmpty()) {
                item(key = "empty_$title") {
                    Text(
                        text = "Nenhum áudio em $title",
                        color = Color(0xFF6B6B6B),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                return
            }

            items(
                items = files,
                key = { it.absolutePath }
            ) { file: File ->
            val isMenuOpen = menuOpenedForFilePath == file.absolutePath
            val durationMs = AudioMetadataCache.getDurationMs(file)
            val playlistName = audioToPlaylistMap[file.absolutePath]
            val playlistObj = playlists.find { it.title == playlistName }
            val playlistColor = playlistObj?.color ?: Color(0xFF8EEAE7)
            val textColor = readableTextColorForBackground(playlistColor)
            val displayName = audioDisplayNames[file.absolutePath]?.takeIf { it.isNotBlank() } ?: file.name
            val isPendingSync = pendingSyncPaths.contains(file.absolutePath)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .background(
                        playlistColor,
                        shape = MaterialTheme.shapes.medium
                    )
                    .padding(12.dp)
                    .clickable { onAudioSelected(file, playbackOrder) },
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
                        text = displayName,
                        fontWeight = FontWeight.Medium,
                        color = textColor
                    )
                    if (isPendingSync) {
                        Text(
                            text = "Pendente de sincronização",
                            color = textColor.copy(alpha = 0.9f),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (durationMs > 0) formatDuration(durationMs) else "--:--",
                    color = textColor.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.width(8.dp))

                IconButton(onClick = {
                    onMenuOpenedChange(if (isMenuOpen) null else file.absolutePath)
                }) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "Opções",
                        tint = textColor
                    )
                }

                DropdownMenu(
                    expanded = isMenuOpen,
                    onDismissRequest = { onMenuOpenedChange(null) }
                ) {
                    DropdownMenuItem(
                        text = { Text("Renomear") },
                        onClick = {
                            onMenuOpenedChange(null)
                            renameTarget = file
                            renameText = audioDisplayNames[file.absolutePath] ?: file.nameWithoutExtension
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Excluir") },
                        onClick = { onMenuOpenedChange(null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Mover para playlist") },
                        onClick = {
                            onMenuOpenedChange(null)
                            onMoveToPlaylist(file)
                        }
                    )
                }
            }
        }

        }

        audioSection(
            title = "Processados",
            files = processedFiles,
            expanded = showProcessedCategory,
            onToggle = { showProcessedCategory = !showProcessedCategory }
        )
        audioSection(
            title = "Originais",
            files = originalFiles,
            expanded = showOriginalCategory,
            onToggle = { showOriginalCategory = !showOriginalCategory }
        )
    }

    if (renameTarget != null) {
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Renomear áudio") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("Nome do áudio") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    renameTarget?.let { target ->
                        onRenameAudio(target, renameText)
                    }
                    renameTarget = null
                }) {
                    Text("Salvar")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

private fun readableTextColorForBackground(background: Color): Color {
    return if (background.luminance() > 0.62f) Color(0xFF1C1C1C) else Color.White
}

@Composable
private fun PlaylistsTabContent(
    playlists: MutableList<PlaylistEntry>,
    audioToPlaylistMap: MutableMap<String, String>,
    favoriteIds: MutableList<Int>,
    onlyFavorites: Boolean,
    searchText: String,
    wavFiles: List<File>,
    availableColors: List<Color>,
    bottomBarTotalHeight: Dp,
    onOpenAudiosByPlaylist: (String) -> Unit
) {
    val filteredBySearch = if (searchText.isBlank()) {
        playlists
    } else {
        playlists.filter { it.title.contains(searchText, ignoreCase = true) }
    }

    val displayed = if (onlyFavorites) {
        filteredBySearch.filter { favoriteIds.contains(it.id) }
    } else {
        filteredBySearch
    }

    var showAddDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf(availableColors.first()) }

    Box(Modifier.fillMaxSize()) {
        if (displayed.isEmpty()) {
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
                    PlaylistCard(
                        title = item.title,
                        subtitle = "${audioToPlaylistMap.values.count { it == item.title }}/${wavFiles.size} ÁUDIOS",
                        color = item.color,
                        isFavorite = favoriteIds.contains(item.id),
                        availableColors = availableColors,
                        onFavoriteToggle = {
                            if (favoriteIds.contains(item.id)) {
                                favoriteIds.remove(item.id)
                            } else {
                                favoriteIds.add(item.id)
                            }
                        },
                        onClick = { onOpenAudiosByPlaylist(item.title) },
                        onRename = { newName ->
                            val idx = playlists.indexOfFirst { it.id == item.id }
                            if (idx >= 0) {
                                val oldName = playlists[idx].title
                                playlists[idx] = playlists[idx].copy(title = newName)
                                val updated = audioToPlaylistMap.mapValues { (_, value) ->
                                    if (value == oldName) newName else value
                                }
                                audioToPlaylistMap.clear()
                                audioToPlaylistMap.putAll(updated)
                            }
                        },
                        onColorChange = { newColor ->
                            val idx = playlists.indexOfFirst { it.id == item.id }
                            if (idx >= 0) {
                                playlists[idx] = playlists[idx].copy(color = newColor)
                            }
                        },
                        onDelete = {
                            val playlistName = item.title
                            playlists.removeAll { it.id == item.id }
                            val toRemove = audioToPlaylistMap
                                .filterValues { it == playlistName }
                                .keys
                                .toList()
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
                .padding(end = 24.dp, bottom = 16.dp)
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
                        ColorWheelPicker(
                            selectedColor = selectedColor,
                            onColorSelected = { selectedColor = it },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            availableColors.forEach { color ->
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .border(
                                            width = if (selectedColor == color) 3.dp else 1.dp,
                                            color = if (selectedColor == color) Color.White else Color.Transparent,
                                            shape = CircleShape
                                        )
                                        .clickable { selectedColor = color }
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (newPlaylistName.isNotBlank()) {
                            val nextId = (playlists.maxOfOrNull { it.id } ?: 0) + 1
                            playlists.add(
                                PlaylistEntry(
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
                    TextButton(onClick = { showAddDialog = false }) { Text("Cancelar") }
                }
            )
        }
    }
}

@Preview(name = "Playlist Screen", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun PlaylistScreenPreview() {
    CalmWaveTheme {
        val playlists = remember {
            mutableStateListOf(
                PlaylistEntry(1, "Dormir", "2/6 ÁUDIOS", Color(0xFF6FAF9E)),
                PlaylistEntry(2, "Foco", "1/6 ÁUDIOS", Color(0xFFF29345)),
                PlaylistEntry(3, "Relax", "3/6 ÁUDIOS", Color(0xFF2DC9C6))
            )
        }
        val audioMap = remember {
            mutableStateMapOf(
                "audio_1.wav" to "Dormir",
                "audio_2.wav" to "Dormir",
                "audio_3.wav" to "Foco",
                "audio_4.wav" to "Relax",
                "audio_5.wav" to "Relax",
                "audio_6.wav" to "Relax"
            )
        }
        val favoriteIds = remember { mutableStateListOf(1, 3) }

        Scaffold(
            topBar = { TopBar(title = "Playlists", userName = "Usuário Demo") },
            bottomBar = {
                BottomNavigationBar(
                    selected = "Playlists",
                    modifier = Modifier.fillMaxWidth()
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(Color(0xFFF7F7F7))
            ) {
                PlaylistsTabContent(
                    playlists = playlists,
                    audioToPlaylistMap = audioMap,
                    favoriteIds = favoriteIds,
                    onlyFavorites = false,
                    searchText = "",
                    wavFiles = listOf(
                        File("audio_1.wav"),
                        File("audio_2.wav"),
                        File("audio_3.wav"),
                        File("audio_4.wav"),
                        File("audio_5.wav"),
                        File("audio_6.wav")
                    ),
                    availableColors = DEFAULT_PLAYLIST_COLORS,
                    bottomBarTotalHeight = 72.dp,
                    onOpenAudiosByPlaylist = {}
                )
            }
        }
    }
}