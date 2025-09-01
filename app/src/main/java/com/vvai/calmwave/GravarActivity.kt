package com.vvai.calmwave

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale

class GravarActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CalmWaveTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFFF7F7F7)
                ) {
                    // Box para sobrepor barra de navegação fixa no fundo
                    Box(modifier = Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Barra superior personalizada
                            TopBar(title = "Gravação")

                            Spacer(modifier = Modifier.height(32.dp))

                            // Título principal da tela
                            Text(
                                text = "Calm Wave",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            // Subtítulo explicativo
                            Text(
                                text = "Acompanhe a sua gravação",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                            )

                            // Estado para controlar gravação e pausa
                            var isRecording by remember { mutableStateOf(false) }
                            var isPaused by remember { mutableStateOf(false) }

                            // Botão para iniciar ou encerrar gravação
                            Button(
                                onClick = {
                                    if (isRecording) {
                                        // Encerrar gravação
                                        isRecording = false
                                        isPaused = false
                                        // Insira aqui a função de encerrar gravação
                                    } else {
                                        // Iniciar gravação
                                        isRecording = true
                                        // Insira aqui a função de iniciar gravação
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isRecording) Color(0xFF0A0E58) else Color(0xFF2DC9C6)
                                ),
                                modifier = Modifier
                                    .padding(vertical = 8.dp)
                                    .fillMaxWidth(0.5f)
                                    .height(48.dp)
                            ) {
                                Text(
                                    text = if (isRecording) "Encerrar" else "Iniciar",
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Exibição do tempo de gravação
                            // Atualize este valor conforme a gravação avança
                            Text(
                                text = "01:26:28",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )

                            // Placeholder para o gráfico de onda do áudio gravado
                            // Substitua por um componente de waveform real se desejar
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.7f)
                                    .height(48.dp)
                                    .background(Color(0xFFE0F7FA), shape = MaterialTheme.shapes.medium),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("waveform", color = Color(0xFF2DC9C6))
                            }

                            // Botão de pausar/continuar aparece somente durante gravação
                            if (isRecording) {
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = {
                                        isPaused = !isPaused
                                        // Insira aqui a função de pausar ou continuar gravação
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFC7F2EB)
                                    ),
                                    modifier = Modifier
                                        .height(40.dp)
                                ) {
                                    Text(
                                        text = if (isPaused) "Play" else "Pause",
                                        color = Color(0xFF0CAC92),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(32.dp))

                            // Espaço extra para posicionar a imagem corretamente acima da barra
                            Spacer(modifier = Modifier.height(32.dp))

                            // Imagem da menina sentada na nuvem
                            // Insira aqui a ilustração desejada
                            Image(
                                painter = painterResource(id = R.drawable.menina_nuvem),
                                contentDescription = "Menina sentada na nuvem",
                                modifier = Modifier
                                    .size(220.dp),
                                contentScale = ContentScale.Fit
                            )

                            Spacer(modifier = Modifier.height(16.dp))
                        }
                        // Barra de navegação inferior fixa
                        // Use para trocar entre telas do app
                        BottomNavigationBar(
                            selected = "Gravação",
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                    }
                }
            }
        }
    }
}