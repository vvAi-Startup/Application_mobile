package com.vvai.calmwave.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipRect
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun WaveProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    barColor: Color = Color(0xFF2DC9C6),
    trackColor: Color = Color(0xFF2B2B2B),
    waveColor: Color = Color.White.copy(alpha = 0.38f),
    animationDurationMs: Int = 1500
) {
    val transition = rememberInfiniteTransition(label = "wave_progress_transition")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = animationDurationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )

    androidx.compose.foundation.Canvas(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(percent = 50))
            .background(trackColor)
    ) {
        val clampedProgress = progress.coerceIn(0f, 1f)
        val fillWidth = size.width * clampedProgress
        val radius = CornerRadius(size.height / 2f, size.height / 2f)

        drawRoundRect(
            color = trackColor,
            size = Size(size.width, size.height),
            cornerRadius = radius
        )

        if (fillWidth <= 0f) return@Canvas

        drawRoundRect(
            color = barColor,
            size = Size(fillWidth, size.height),
            cornerRadius = radius
        )

        val amplitude = size.height * 0.18f
        val wavelength = (size.width * 0.42f).coerceAtLeast(1f)

        fun buildWavePath(phaseOffset: Float, baselineRatio: Float): Path {
            val path = Path()
            val baseline = size.height * baselineRatio
            path.moveTo(0f, size.height)
            path.lineTo(0f, baseline)

            var x = 0f
            while (x <= size.width) {
                val y = baseline + amplitude * sin(((x / wavelength) * 2f * PI).toFloat() + phaseOffset)
                path.lineTo(x, y)
                x += 4f
            }

            path.lineTo(size.width, size.height)
            path.close()
            return path
        }

        clipRect(right = fillWidth) {
            val primaryWaveAlpha = (waveColor.alpha * 1.15f).coerceIn(0f, 1f)
            val secondaryWaveAlpha = (waveColor.alpha * 0.82f).coerceIn(0f, 1f)

            drawPath(
                path = buildWavePath(phase, 0.42f),
                color = waveColor.copy(alpha = primaryWaveAlpha)
            )
            drawPath(
                path = buildWavePath(-phase * 1.2f, 0.62f),
                color = waveColor.copy(alpha = secondaryWaveAlpha)
            )
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
fun WaveProgressBarPreview() {
    WaveProgressBar(
        progress = 0.5f,
        modifier = Modifier.height(25.dp)
    )
}
