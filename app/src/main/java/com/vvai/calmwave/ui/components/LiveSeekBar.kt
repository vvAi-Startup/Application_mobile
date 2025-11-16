package com.vvai.calmwave.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward5
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay5
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Barra de seek estilo "live" semelhante a streaming com DVR.
 * Mostra:
 * - Duração total em buffer (até maxBufferMin)
 * - Posição atual
 * - Indicador LIVE quando no head.
 * Usuário pode arrastar para trás e apertar botão "Ao Vivo" para retornar.
 */
@Composable
fun LiveSeekBar(
	bufferedMs: Long,
	playPositionMs: Long,
	behindLiveMs: Long,
	isLive: Boolean,
	onSeekBehindLive: (offsetMs: Long) -> Unit,
	onGoLive: () -> Unit,
	modifier: Modifier = Modifier
) {
	// Slider trabalha com 0..bufferedMs (posição relativa ao início)
	var localValue by remember(playPositionMs, bufferedMs) { mutableStateOf(playPositionMs.toFloat()) }
	val clampedBuffered = bufferedMs.coerceAtLeast(1L)
	Column(modifier = modifier.fillMaxWidth()) {
		Row(
			modifier = Modifier.fillMaxWidth(),
			verticalAlignment = Alignment.CenterVertically
		) {
			// Retroceder 5s
			IconButton(onClick = {
				val newOffset = (behindLiveMs + 5000L).coerceAtMost(clampedBuffered)
				onSeekBehindLive(newOffset)
			}) {
				Icon(imageVector = Icons.Filled.Replay5, contentDescription = "Voltar 5s", tint = Color(0xFF0B6B63))
			}

			// Slider estilizado
			Slider(
				value = localValue.coerceIn(0f, clampedBuffered.toFloat()),
				onValueChange = {
					localValue = it
					val offsetFromLive = clampedBuffered - it.toLong()
					onSeekBehindLive(offsetFromLive)
				},
				valueRange = 0f..clampedBuffered.toFloat(),
				modifier = Modifier.weight(1f),
				colors = SliderDefaults.colors(
					thumbColor = Color(0xFF12B089),
					activeTrackColor = Color(0xFF12B089),
					inactiveTrackColor = Color(0xFFBDEEDC)
				)
			)

			// Avançar 5s
			IconButton(onClick = {
				val newOffset = (behindLiveMs - 5000L).coerceAtLeast(0L)
				onSeekBehindLive(newOffset)
			}) {
				Icon(imageVector = Icons.Filled.Forward5, contentDescription = "Avançar 5s", tint = Color(0xFF0B6B63))
			}

			Spacer(modifier = Modifier.width(8.dp))
			if (!isLive) {
				// Botão mais alinhado com a paleta do app (verde), em formato pill
				Button(
					onClick = { onGoLive() },
					colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF12B089), contentColor = Color.White),
					shape = MaterialTheme.shapes.large,
					contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
				) {
					Icon(Icons.Filled.PlayArrow, contentDescription = "Ir para Ao vivo")
					Spacer(modifier = Modifier.width(6.dp))
					Text("Ao vivo")
				}
			} else {
				// Indicador AO VIVO compacto com ponto vermelho
				Row(
					verticalAlignment = Alignment.CenterVertically,
					modifier = Modifier
						.background(Color(0x14FF3B30), shape = MaterialTheme.shapes.large)
						.padding(horizontal = 12.dp, vertical = 8.dp)
				) {
					Box(
						modifier = Modifier
							.size(8.dp)
							.background(Color(0xFFFF3B30), CircleShape)
					)
					Spacer(modifier = Modifier.width(6.dp))
					Text("AO VIVO", color = Color(0xFFFF3B30), style = MaterialTheme.typography.labelMedium)
				}
			}
		}
		Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
			Text(formatMs(playPositionMs), style = MaterialTheme.typography.labelSmall)
			if (!isLive) {
				Text("-" + formatMs(behindLiveMs) + " atrás", style = MaterialTheme.typography.labelSmall)
			} else {
				Text(formatMs(bufferedMs), style = MaterialTheme.typography.labelSmall)
			}
		}
	}
}

private fun formatMs(ms: Long): String {
	val totalSeconds = ms / 1000
	val m = totalSeconds / 60
	val s = totalSeconds % 60
	return String.format("%02d:%02d", m, s)
}
