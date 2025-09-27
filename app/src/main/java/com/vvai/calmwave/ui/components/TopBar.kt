package com.vvai.calmwave.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun TopBar(title: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clickCount = remember { mutableStateOf(0) }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFFE6F7FA))
            .padding(vertical = 12.dp)
            .height(64.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                style = androidx.compose.material3.MaterialTheme.typography.titleTitle,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF174A5A)
            )
            Image(
                painter = painterResource(id = R.drawable.avatar),
                contentDescription = "Avatar",
                modifier = Modifier
                    .size(40.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
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
        }
    }
}