package com.vvai.calmwave.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.ui.platform.LocalContext
import com.vvai.calmwave.R
import com.vvai.calmwave.ui.theme.titleTitle
import android.content.Intent
import com.vvai.calmwave.MainActivity
import com.vvai.calmwave.util.getSecureAuthPrefs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import androidx.compose.ui.tooling.preview.Preview
import com.vvai.calmwave.ui.theme.CalmWaveTheme

@Composable
fun TopBar(
    title: String,
    modifier: Modifier = Modifier,
    userName: String? = null,
    onAvatarClick: (() -> Unit)? = null,
    onLogoutClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clickCount = remember { mutableStateOf(0) }
    val showProfileMenu = remember { mutableStateOf(false) }
    val persistedUserName = remember {
        val authPrefs = getSecureAuthPrefs(context)
        authPrefs.getString("user_name", null)
            ?: authPrefs.getString("user_email", null)
    }
    val displayUserName = if (!userName.isNullOrBlank()) userName else persistedUserName
    
    // Configura a cor da status bar
    val systemUiController = rememberSystemUiController()
    val statusBarColor = Color(0xFFE6F7FA)
    
    systemUiController.setStatusBarColor(
        color = statusBarColor,
        darkIcons = true // Ícones escuros (pretos) para contraste com fundo claro
    )
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding() // Adiciona padding para a status bar
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(bottomStart = 30.dp, bottomEnd = 30.dp),
                clip = false
            )
            .clip(RoundedCornerShape(bottomStart = 30.dp, bottomEnd = 30.dp))
            .background(Color(0xFFE6F7FA))
            .padding(vertical = 12.dp)
            .height(64.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                style = androidx.compose.material3.MaterialTheme.typography.titleTitle,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF174A5A)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!displayUserName.isNullOrBlank()) {
                    Text(
                        text = displayUserName,
                        color = Color(0xFF0F4B58),
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                }
                Box {
                    Image(
                        painter = painterResource(id = R.drawable.avatar),
                        contentDescription = "Avatar",
                        modifier = Modifier
                            .size(40.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                if (onAvatarClick != null) {
                                    onAvatarClick()
                                    return@clickable
                                }

                                if (onLogoutClick != null) {
                                    showProfileMenu.value = true
                                    return@clickable
                                }

                                // simple triple-tap detector: three taps within 1s
                                clickCount.value += 1
                                if (clickCount.value == 1) {
                                    scope.launch {
                                        delay(1000)
                                        clickCount.value = 0
                                    }
                                }
                                if (clickCount.value >= 3) {
                                    clickCount.value = 0
                                    val intent = Intent(context, MainActivity::class.java)
                                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                                    context.startActivity(intent)
                                }
                            }
                    )

                    DropdownMenu(
                        expanded = showProfileMenu.value,
                        onDismissRequest = { showProfileMenu.value = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Sair") },
                            onClick = {
                                showProfileMenu.value = false
                                onLogoutClick?.invoke()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Preview(name = "TopBar", showBackground = true, widthDp = 393)
@Composable
private fun TopBarPreview() {
    CalmWaveTheme {
        TopBar(title = "Calm Wave", userName = "Usuário Demo")
    }
}