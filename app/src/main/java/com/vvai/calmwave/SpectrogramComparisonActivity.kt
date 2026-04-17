package com.vvai.calmwave

import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vvai.calmwave.components.TopBar
import com.vvai.calmwave.ui.theme.CalmWaveTheme
import com.vvai.calmwave.util.SpectrogramGenerator
import com.vvai.calmwave.util.enterImmersiveMode
import com.vvai.calmwave.util.getUserAudioDir
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class SpectrogramComparisonActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        enterImmersiveMode()

        setContent {
            CalmWaveTheme {
                SpectrogramScreen(onBack = { finish() })
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersiveMode()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpectrogramScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var files by remember { mutableStateOf<List<File>>(emptyList()) }
    var isProcessing by remember { mutableStateOf(false) }
    var selectedFile by remember { mutableStateOf<File?>(null) }
    var originalBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var processedBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Load available files (only originals for simplicity, avoiding ones starting with processed_)
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val dir = getUserAudioDir(context)
            val audioFiles = dir?.listFiles { f ->
                f.isFile && f.name.endsWith(".wav", ignoreCase = true) && !f.name.contains("denoised_") && !f.name.contains("processed_")
            }?.sortedByDescending { it.lastModified() } ?: emptyList()

            withContext(Dispatchers.Main) {
                files = audioFiles
            }
        }
    }

    fun processAudioFile(file: File) {
        selectedFile = file
        isProcessing = true
        originalBitmap = null
        processedBitmap = null

        coroutineScope.launch(Dispatchers.IO) {
            val origBmp = SpectrogramGenerator.generateSpectrogram(file)
            
            // Generate processed file using LocalAudioDenoiser
            val denoiser = LocalAudioDenoiser(context)
            var procBmp: Bitmap? = null
            if (denoiser.initialize()) {
                val processedPath = denoiser.processWavFile(file.absolutePath)
                if (processedPath != null) {
                    val processedFile = File(processedPath)
                    procBmp = SpectrogramGenerator.generateSpectrogram(processedFile)
                }
                denoiser.release()
            }

            withContext(Dispatchers.Main) {
                originalBitmap = origBmp
                processedBitmap = procBmp
                isProcessing = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Comparar Espectrogramas") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFFE6F7FA),
                    titleContentColor = Color(0xFF174A5A),
                    navigationIconContentColor = Color(0xFF174A5A)
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFFF7F7F7))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (selectedFile == null) {
                Text(
                    text = "Selecione um áudio para gerar e comparar os espectrogramas:",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF174A5A),
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(files) { file ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .clickable { processAudioFile(file) },
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = file.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Tamanho: ${file.length() / 1024} KB",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.DarkGray
                                )
                            }
                        }
                    }
                }
            } else if (isProcessing) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color(0xFF2DC9C6))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Gerando Espectrogramas...\nIsso pode levar alguns segundos.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.DarkGray,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                Text(
                    text = "Comparação: ${selectedFile?.name}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF174A5A),
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item {
                        Text("Áudio Original", fontWeight = FontWeight.SemiBold, color = Color(0xFF174A5A))
                        Spacer(modifier = Modifier.height(8.dp))
                        if (originalBitmap != null) {
                            Image(
                                bitmap = originalBitmap!!.asImageBitmap(),
                                contentDescription = "Original Spectrogram",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.Black),
                                contentScale = ContentScale.FillBounds
                            )
                        } else {
                            Text("Erro ao gerar espectrograma original", color = Color.Red)
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Text("Áudio Processado (Denoised)", fontWeight = FontWeight.SemiBold, color = Color(0xFF174A5A))
                        Spacer(modifier = Modifier.height(8.dp))
                        if (processedBitmap != null) {
                            Image(
                                bitmap = processedBitmap!!.asImageBitmap(),
                                contentDescription = "Processed Spectrogram",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.Black),
                                contentScale = ContentScale.FillBounds
                            )
                        } else {
                            Text("Erro ao gerar espectrograma processado", color = Color.Red)
                        }

                        Spacer(modifier = Modifier.height(32.dp))
                        Button(
                            onClick = { selectedFile = null },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2DC9C6)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Selecionar outro áudio", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
fun SpectrogramScreenPreview() {
    SpectrogramScreen(onBack = {})
}
