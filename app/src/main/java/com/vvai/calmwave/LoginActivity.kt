package com.vvai.calmwave

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.vvai.calmwave.data.model.LoginRequest
import com.vvai.calmwave.data.remote.ApiClient
import com.vvai.calmwave.util.enterImmersiveMode
import com.vvai.calmwave.ui.theme.CalmWaveTheme
import com.vvai.calmwave.ui.theme.FredokaFamily
import kotlinx.coroutines.launch

class LoginActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterImmersiveMode()
        setContent {
            Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFE2E4FA)) {
                LoginScreen(
                    onLogin = { email, password, onResult -> doLogin(email, password, onResult) },
                    onRegisterClick = {
                        startActivity(Intent(this, CadastroActivity::class.java))
                        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                    }
                )
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersiveMode()
    }

    private fun doLogin(email: String, password: String, onResult: (Boolean, String?) -> Unit) {
        if (email.isBlank() || password.isBlank()) {
            onResult(false, "Preencha email e senha")
            return
        }

        lifecycleScope.launch {
            try {
                val api = ApiClient.getApiService()
                val request = LoginRequest(email.trim(), password)

                var response = api.login(request)
                if (!response.isSuccessful && response.code() == 404) {
                    response = api.loginNoApiPrefix(request)
                }

                if (response.isSuccessful) {
                    val body = response.body()
                    val token = body?.token
                    if (!token.isNullOrBlank()) {
                        val prefs = getSharedPreferences("calmwave_auth", MODE_PRIVATE)
                        prefs.edit()
                            .putString("access_token", token)
                            .putString("user_name", body?.user?.name)
                            .putString("user_email", body?.user?.email ?: email.trim())
                            .putLong("user_id", body?.user?.id ?: -1L)
                            .apply()
                        ApiClient.setAuthToken(token)

                        val intent = Intent(this@LoginActivity, PrincipalActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        }
                        onResult(true, null)
                        startActivity(intent)
                    } else {
                        onResult(false, "Resposta inválida da API")
                    }
                } else {
                    onResult(false, "Usuario ou senha Invalidos")
                }
            } catch (_: Exception) {
                onResult(false, "Erro ao conectar com a API")
            }
        }
    }
}

@Composable
private fun LoginScreen(
    onLogin: (String, String, (Boolean, String?) -> Unit) -> Unit,
    onRegisterClick: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFE2E4FA))
            .padding(horizontal = 28.dp)
    ) {
        DecorativeClouds()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 86.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Calm Wave",
                color = Color(0xFF0A4E65),
                fontSize = 56.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FredokaFamily
            )

            Spacer(modifier = Modifier.height(32.dp))

            Column(modifier = Modifier.fillMaxWidth(0.84f)) {
                Text(text = "Email ou Telefone", modifier = Modifier.padding(start = 4.dp), color = Color.Black, fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(text = "Senha", modifier = Modifier.padding(start = 4.dp), color = Color.Black, fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                )
            }

            Spacer(modifier = Modifier.height(30.dp))

            Button(
                onClick = {
                    isLoading = true
                    errorMessage = null
                    onLogin(email, password) { success, message ->
                        isLoading = false
                        if (!success) {
                            errorMessage = message ?: "Falha ao fazer login"
                        }
                    }
                },
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF12B089)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth(0.45f)
                    .height(46.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.5.dp
                    )
                } else {
                    Text(text = "ENTRAR", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                }
            }

            if (!errorMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = Color(0xFFFFE5E5),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(0.84f)
                ) {
                    Text(
                        text = errorMessage!!,
                        color = Color(0xFFB00020),
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(7.dp))
                Text(text = "OU", color = Color.Gray, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = "CADASTRE-SE",
                color = Color.Black,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier
                    .padding(bottom = 16.dp)
                    .align(Alignment.CenterHorizontally)
                    .clickable { onRegisterClick() }
                    .background(Color.Transparent)
                    .padding(2.dp)
            )

            SocialLoginButton("Login com o Google", Color(0xFFB39DDB), R.drawable.ic_google)
            Spacer(modifier = Modifier.height(12.dp))
            SocialLoginButton("Login com a Microsoft", Color(0xFF7EC8E3), R.drawable.ic_microsoft)

            Spacer(modifier = Modifier.weight(1f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.Bottom
            ) {
                Image(
                    painter = painterResource(id = R.drawable.morcego),
                    contentDescription = "Mascote",
                    modifier = Modifier.size(220.dp),
                    contentScale = ContentScale.Fit
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SocialLoginButton(text: String, borderColor: Color, iconResId: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .border(2.dp, borderColor, RoundedCornerShape(6.dp))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center

    ) {
        Image(
            painter = painterResource(id = iconResId),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            contentScale = ContentScale.Fit
        )
        Spacer(modifier = Modifier.size(16.dp))
        Text(text = text, color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp )
    }
}

@Composable
private fun DecorativeClouds() {
    Box(modifier = Modifier.fillMaxSize()) {
        Cloud(Modifier.align(Alignment.TopStart).padding(start = 6.dp, top = 34.dp))
        Cloud(Modifier.align(Alignment.TopEnd).padding(end = 18.dp, top = 20.dp))
        Cloud(Modifier.align(Alignment.CenterStart).padding(start = 4.dp, top = 30.dp))
        Cloud(Modifier.align(Alignment.CenterEnd).padding(end = 8.dp, top = 50.dp))
        Cloud(Modifier.align(Alignment.BottomStart).padding(start = 26.dp, bottom = 180.dp))
        Cloud(Modifier.align(Alignment.BottomEnd).padding(end = 32.dp, bottom = 130.dp))
    }
}

@Composable
private fun Cloud(modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(70.dp, 42.dp)) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .size(26.dp)
                .background(Color(0xFFC7D0EA), CircleShape)
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .size(28.dp)
                .background(Color(0xFFC7D0EA), CircleShape)
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(24.dp)
                .background(Color(0xFFC7D0EA), CircleShape)
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .size(56.dp, 24.dp)
                .background(Color(0xFFC7D0EA), RoundedCornerShape(20.dp))
        )
    }
}

@Preview(name = "Login Screen", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun LoginScreenPreview() {
    CalmWaveTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFE2E4FA)) {
            LoginScreen(
                onLogin = { _, _, callback -> callback(false, "Credenciais inválidas") },
                onRegisterClick = {}
            )
        }
    }
}
