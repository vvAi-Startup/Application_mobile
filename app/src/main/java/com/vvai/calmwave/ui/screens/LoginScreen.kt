package com.vvai.calmwave.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vvai.calmwave.R

/**
 * Tela de Login seguindo as diretrizes de UX:
 * - Título: "Calm Wave" com a fonte Fredoka One (usada em MaterialTheme.typography.titleLarge)
 * - Labels e botões usam Baloo2 (configurado em Typography)
 * - Fundo: #C3B1E1
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    modifier: Modifier = Modifier,
    onLogin: () -> Unit = {},
    onSignUp: () -> Unit = {},
    onGoogleLogin: () -> Unit = {},
    onMicrosoftLogin: () -> Unit = {}
) {
    // Fundo lilás claro aproximando a "TELA"
    val background = Color(0xFFE4E3FF)
    val primaryGreen = Color(0xFF00B894) // verde do botão Entrar

    Surface(
        modifier = modifier
            .fillMaxSize()
            .background(background),
        color = Color.Transparent
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Fundo com nuvens
            Image(
                painter = painterResource(id = R.drawable.bg_nuvens),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // Conteúdo principal centralizado
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Calm Wave",
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))

                val emailState = remember { mutableStateOf("") }
                OutlinedTextField(
                    value = emailState.value,
                    onValueChange = { emailState.value = it },
                    label = { Text(text = "Email ou Telefone", style = MaterialTheme.typography.bodyMedium) },
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                val passwordState = remember { mutableStateOf("") }
                OutlinedTextField(
                    value = passwordState.value,
                    onValueChange = { passwordState.value = it },
                    label = { Text(text = "Senha", style = MaterialTheme.typography.bodyMedium) },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onLogin,
                    modifier = Modifier
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = primaryGreen)
                ) {
                    Text(text = "Entrar", style = MaterialTheme.typography.bodyLarge, color = Color.White)
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(onClick = onSignUp) {
                    Text(text = "Cadastre-se", style = MaterialTheme.typography.bodyMedium, color = Color.DarkGray)
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Botões sociais em coluna (vertical)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onGoogleLogin,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(text = "Login com o Google", style = MaterialTheme.typography.bodyMedium)
                    }

                    OutlinedButton(
                        onClick = onMicrosoftLogin,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(text = "Login com a Microsoft", style = MaterialTheme.typography.bodyMedium)
                    }
                }

                // Espaço extra para não colidir com o morcego
                Spacer(modifier = Modifier.height(48.dp))
            }

            // Morcego no canto inferior direito
            Image(
                painter = painterResource(id = R.drawable.morcego),
                contentDescription = "Personagem morcego convidando para cadastro",
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .size(180.dp)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun LoginScreenPreview() {
    LoginScreen()
}
