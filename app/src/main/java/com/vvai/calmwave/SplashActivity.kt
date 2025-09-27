package com.vvai.calmwave

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement.Center
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

class SplashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // check if splash was already shown
        val prefs = getSharedPreferences("calmwave_prefs", MODE_PRIVATE)
        val alreadyShown = prefs.getBoolean("splash_shown", false)
        if (alreadyShown) {
            // skip splash and go straight to GravarActivity
            val intent = Intent(this, GravarActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            startActivity(intent)
            finish()
            return
        }
        setContent {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(painter = painterResource(id = R.drawable.splash), contentDescription = "Splash", modifier = Modifier.fillMaxWidth().height(220.dp))
                    Spacer(modifier = Modifier.height(24.dp))
                    var progress by remember { mutableStateOf(0f) }
                    LaunchedEffect(Unit) {
                        // animate a faux progress bar for 1.5s
                        val steps = 30
                        repeat(steps) {
                            progress = (it + 1).toFloat() / steps
                            delay(50)
                        }
                        // mark splash as shown so next app open skips it
                        prefs.edit().putBoolean("splash_shown", true).apply()
                        // after progress completes, go to GravarActivity
                        val intent2 = Intent(this@SplashActivity, GravarActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        }
                        startActivity(intent2)
                        finish()
                    }
                    LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}
