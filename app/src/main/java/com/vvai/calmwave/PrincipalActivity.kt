package com.vvai.calmwave

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalContext
import com.vvai.calmwave.GravarActivity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import com.vvai.calmwave.R

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
    // Playlists state loaded from SharedPreferences
    val playlistsState = remember { mutableStateListOf<PlaylistItem>() }

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

    LaunchedEffect(Unit) { loadPlaylists() }
    Scaffold(
        topBar = { TopBar(title = "Calm Wave") },
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
                StatusCard(
                    title = "Conectar com\nOrientador",
                    color = Color(0xFF2DC9C6),
                    modifier = Modifier.weight(1f)
                )
                StatusCard(
                    title = "Fone\ndesconectado",
                    color = Color(0xFF9EEFD8),
                    modifier = Modifier.weight(1f)
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
                    playlists = playlistsState.toList()
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                RecordButton(onClick = {
                    val intent = Intent(context, GravarActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    }
                    context.startActivity(intent)
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
                        .offset(y = 30.dp)
                )
            }

        }
    }
}

@Composable
fun StatusCard(title: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .height(90.dp),
        shape = RoundedCornerShape(12.dp),
        color = color,
        tonalElevation = 4.dp
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.CenterStart) {
            Text(text = title, color = Color.White, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun PlaylistsCarousel(playlists: List<PlaylistItem>, modifier: Modifier = Modifier) {
    LazyRow(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(playlists) { item ->
            PlaylistCard(item = item)
        }
    }
}

@Composable
fun PlaylistCard(item: PlaylistItem, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .size(width = 140.dp, height = 140.dp),
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
        Text(text = "Iniciar Gravação", color = Color.White)
        
    }
}

@Composable
fun RecordingItem(title: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp),
        tonalElevation = 2.dp,
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFFF3F6F7)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp)) {
            IconButton(onClick = { /* tocar gravação */ }) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Play"
                )
            }
            Text(text = title, modifier = Modifier.weight(1f))
        }
    }
}
