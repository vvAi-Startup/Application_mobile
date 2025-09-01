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
                    Box(modifier = Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // TopBar
                            TopBar(title = "Gravação")

                            Spacer(modifier = Modifier.height(32.dp))

                            Text(
                                text = "Calm Wave",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Acompanhe a sua gravação",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                            )

                            Button(
                                onClick = { /* TODO: Iniciar gravação */ },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2DC9C6)),
                                modifier = Modifier
                                    .padding(vertical = 8.dp)
                                    .fillMaxWidth(0.5f)
                                    .height(48.dp)
                            ) {
                                Text("Iniciar", fontWeight = FontWeight.Bold)
                            }

                            Text(
                                text = "01:26:28",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.7f)
                                    .height(48.dp)
                                    .background(Color(0xFFE0F7FA), shape = MaterialTheme.shapes.medium),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("waveform", color = Color(0xFF2DC9C6))
                            }

                            Spacer(modifier = Modifier.height(32.dp))

                            Spacer(modifier = Modifier.height(32.dp))

                            // Imagem da menina sentada na nuvem
                            Image(
                                painter = painterResource(id = R.drawable.menina_nuvem),
                                contentDescription = "Menina sentada na nuvem",
                                modifier = Modifier
                                    .size(220.dp),
                                contentScale = ContentScale.Fit
                            )

                            Spacer(modifier = Modifier.height(16.dp))
                        }
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