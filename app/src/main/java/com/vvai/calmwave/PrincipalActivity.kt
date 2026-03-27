package com.vvai.calmwave

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vvai.calmwave.components.BottomNavigationBar
import com.vvai.calmwave.components.TopBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.Mic
import androidx.compose.ui.platform.LocalContext
import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.vvai.calmwave.ui.theme.CalmWaveTheme
import com.vvai.calmwave.data.remote.ApiClient

// Top-level model used by several composables
data class PlaylistItem(val title: String, val subtitle: String = "", val color: Color)

class PrincipalActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PrincipalScreen()
        }
    }
}

@Composable
fun PrincipalScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    // Playlists state loaded from SharedPreferences
    val playlistsState = remember { mutableStateListOf<PlaylistItem>() }
    var foneConnected by remember { mutableStateOf(false) }
    var showBluetoothDialog by remember { mutableStateOf(false) }
    val pairedDevices = remember { mutableStateListOf<String>() }
    var loggedUserName by remember { mutableStateOf("Usuário") }

    fun loadPairedDevices() {
        pairedDevices.clear()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) return
        try {
            val adapter = BluetoothAdapter.getDefaultAdapter()
            adapter?.bondedDevices?.forEach { d ->
                pairedDevices.add((d.name ?: "Desconhecido") + " - " + d.address)
            }
        } catch (_: SecurityException) { /* permission not granted */ }
    }

    fun loadPlaylists() {
        val prefs = context.getSharedPreferences("playlists_prefs", 0)
        val playlistJson = prefs.getString("playlists", null)
        playlistsState.clear()
        if (!playlistJson.isNullOrBlank()) {
            playlistJson.split("||").forEach {
                val parts = it.split("|")
                if (parts.size == 4) {
                    try {
                        val title = parts[1]
                        val subtitle = parts[2]
                        val color = Color(parts[3].toULong())
                        playlistsState.add(PlaylistItem(title, subtitle, color))
                    } catch (_: Exception) {
                        // ignore malformed entries
                    }
                }
            }
        } else {
            // no saved playlists: keep list empty. UI will show empty state.
        }
    }

    fun loadLoggedUserName() {
        val authPrefs = context.getSharedPreferences("calmwave_auth", Context.MODE_PRIVATE)
        val savedName = authPrefs.getString("user_name", null)
        val fallbackEmail = authPrefs.getString("user_email", null)
        loggedUserName = when {
            !savedName.isNullOrBlank() -> savedName
            !fallbackEmail.isNullOrBlank() -> fallbackEmail
            else -> "Usuário"
        }
    }

    // reload when the composable resumes (every time user returns to this screen)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                loadPlaylists()
                loadPairedDevices()
                loadLoggedUserName()
                // keep existing foneConnected state — system broadcasts will update it
            }
        }
        // register system receiver for headset (wired) and bluetooth ACL connect/disconnect
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent == null) return
                when (intent.action) {
                    BluetoothDevice.ACTION_ACL_CONNECTED -> {
                        foneConnected = true
                        loadPairedDevices()
                    }
                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                        foneConnected = false
                    }
                    Intent.ACTION_HEADSET_PLUG -> {
                        val state = intent.getIntExtra("state", -1)
                        foneConnected = state == 1
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(Intent.ACTION_HEADSET_PLUG)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        context.registerReceiver(receiver, filter)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            try { context.unregisterReceiver(receiver) } catch (_: Exception) { }
        }
    }

    LaunchedEffect(Unit) { loadPlaylists() }
    LaunchedEffect(Unit) { loadLoggedUserName() }
    // initial paired devices
    LaunchedEffect(Unit) { loadPairedDevices() }
    Scaffold(
        topBar = {
            TopBar(
                title = "Calm Wave",
                userName = loggedUserName,
                onLogoutClick = {
                    val authPrefs = context.getSharedPreferences("calmwave_auth", Context.MODE_PRIVATE)
                    authPrefs.edit()
                        .remove("access_token")
                        .remove("user_name")
                        .remove("user_email")
                        .apply()
                    ApiClient.clear()

                    val intent = Intent(context, LoginActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    }
                    context.startActivity(intent)
                    (context as? Activity)?.finish()
                }
            )
        },
        bottomBar = {
            BottomNavigationBar(selected = "Principal", modifier = Modifier.fillMaxWidth())
        }
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFF6FCFD))
                .padding(horizontal = 18.dp, vertical = 12.dp)
        ) {
            Text(
                text = "Bem vindo!",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF0F4B58),
                modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Conectar com orientador (abre modal bluetooth)
                StatusCard(
                    title = "Conectar com\nOrientador",
                    color = Color(0xFF2DC9C6),
                    icon = Icons.Filled.Mic,
                    modifier = Modifier.weight(1f).clickable { showBluetoothDialog = true }
                )
                // Fone status (dinâmico)
                StatusCard(
                    title = if (foneConnected) "Fone\nconectado" else "Conectar fone\nBluetooth",
                    color = if (foneConnected) Color(0xFF1F8B78) else Color(0xFF9EEFD8),
                    icon = Icons.Filled.Headset,
                    modifier = Modifier.weight(1f).clickable { showBluetoothDialog = true }
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(text = "Suas Playlists", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(8.dp))

            if (playlistsState.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
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
                        text = "Vá para tela 'Playlist' e crie uma :)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF6B6B6B)
                    )
                }
            } else {
                PlaylistsCarousel(
                    playlists = playlistsState.toList(),
                    onPlaylistClick = { item ->
                        val intentPlaylist = Intent(context, PlaylistActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            putExtra(PlaylistActivity.EXTRA_OPEN_PLAYLIST_TITLE, item.title)
                            putExtra(PlaylistActivity.EXTRA_OPEN_AUDIOS_TAB, true)
                        }
                        context.startActivity(intentPlaylist)
                    }
                )
            }

            Spacer(modifier = Modifier.height(35.dp))

            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                RecordButton(onClick = {
                    val intent = Intent(context, GravarActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    }
                    context.startActivity(intent)
                    // finish PrincipalActivity so it does not remain in back stack
                    (context as? Activity)?.finish()
                })
            }

            // push remaining content to the bottom and show the menino image encostado na bottom bar
            Spacer(modifier = Modifier.weight(1f))
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                Image(
                    painter = painterResource(id = R.drawable.menino),
                    contentDescription = "Menino",
                    modifier = Modifier
                        .size(width = 95.dp, height = 123.dp)
                        // nudge down so it visually touches the bottom bar; adjust value if needed
                        .offset(y = 10.dp)
                )
            }

        }
    }

    // Bluetooth modal
    if (showBluetoothDialog) {
        AlertDialog(
            onDismissRequest = { showBluetoothDialog = false },
            title = { Text("Dispositivos pareados") },
            text = {
                Column {
                    if (pairedDevices.isEmpty()) {
                        Text("Nenhum dispositivo pareado encontrado.")
                    } else {
                        pairedDevices.forEach { d ->
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(text = d, modifier = Modifier.weight(1f))
                                TextButton(onClick = {
                                    // simulate connect
                                    foneConnected = true
                                    showBluetoothDialog = false
                                }) { Text("Conectar") }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = { loadPairedDevices() }) { Text("Atualizar") }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBluetoothDialog = false }) { Text("Fechar") }
            }
        )
    }
}

@Composable
fun StatusCard(title: String, color: Color, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .height(90.dp),
        shape = RoundedCornerShape(12.dp),
        color = color,
        tonalElevation = 4.dp
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.CenterStart) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (icon != null) {
                    Icon(imageVector = icon, contentDescription = null, tint = Color.White)
                }
                Text(text = title, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 18.sp)
            }
        }
    }
}

@Composable
fun PlaylistsCarousel(
    playlists: List<PlaylistItem>,
    modifier: Modifier = Modifier,
    onPlaylistClick: (PlaylistItem) -> Unit = {}
) {
    // Exibe no máximo 3 colunas x 2 linhas visíveis; se houver mais, rolagem vertical.
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier
            .fillMaxWidth()
            .height(236.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(playlists) { item ->
            PlaylistCard(
                item = item,
                modifier = Modifier.aspectRatio(1f),
                onClick = { onPlaylistClick(item) }
            )
        }
    }
}

@Composable
fun PlaylistCard(item: PlaylistItem, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    Surface(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = Color.Transparent
    ) {
        val gradient = Brush.verticalGradient(listOf(item.color, lerp(item.color, Color.Black, 0.12f)))
        Box(modifier = Modifier
            .fillMaxSize()
            .background(gradient)
            .padding(8.dp)) {
            // disc image
            Image(
                painter = painterResource(id = R.drawable.disc),
                contentDescription = "Disco",
                modifier = Modifier
                    .size(100.dp)
                    .align(Alignment.Center),
                contentScale = ContentScale.Fit
            )
            Box(modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp)) {
                Text(text = item.title, color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun RecordButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2DC9C6)),
        modifier = modifier
            .height(48.dp)
            .width(220.dp)
    ) {
        Text(text = "Iniciar Gravação", color = Color.White, fontSize = 20.sp)

    }
}

@Preview(name = "Tela Inicial", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun PrincipalScreenPreview() {
    CalmWaveTheme {
        Scaffold(
            topBar = { TopBar(title = "Calm Wave", userName = "Usuário Demo") },
            bottomBar = { BottomNavigationBar(selected = "Principal", modifier = Modifier.fillMaxWidth()) }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(Color(0xFFF6FCFD))
                    .padding(horizontal = 18.dp, vertical = 12.dp)
            ) {


                Text(
                    text = "Bem vindo!",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF0F4B58),
                    modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatusCard(
                        title = "Conectar com\nOrientador",
                        color = Color(0xFF2DC9C6),
                        icon = Icons.Filled.Mic,
                        modifier = Modifier.weight(1f)
                    )
                    StatusCard(
                        title = "Fone\nconectado",
                        color = Color(0xFF1F8B78),
                        icon = Icons.Filled.Headset,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))
                Text(text = "Suas Playlists", fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
                Spacer(modifier = Modifier.height(8.dp))

                PlaylistsCarousel(
                    playlists = listOf(
                        PlaylistItem("Dormir", "", Color(0xFF6FAF9E)),
                        PlaylistItem("Foco", "", Color(0xFFF29345)),
                        PlaylistItem("Relax", "", Color(0xFF2DC9C6))
                    )
                )

                Spacer(modifier = Modifier.height(35.dp))
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    RecordButton(onClick = {})
                }
            }
        }
    }
}
