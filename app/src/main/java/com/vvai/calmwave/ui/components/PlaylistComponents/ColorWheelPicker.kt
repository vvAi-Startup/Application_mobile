package com.vvai.calmwave.ui.components.PlaylistComponents

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun ColorWheelPicker(
    selectedColor: Color,
    onColorSelected: (Color) -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedHsv = remember(selectedColor) {
        FloatArray(3).also { hsv ->
            android.graphics.Color.colorToHSV(selectedColor.toArgb(), hsv)
        }
    }

    val hue = selectedHsv[0]
    val saturation = selectedHsv[1].coerceIn(0f, 1f)

    Canvas(
        modifier = modifier
            .size(220.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    updateColorFromOffset(
                        offset,
                        size.width.toFloat(),
                        size.height.toFloat(),
                        onColorSelected
                    )
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    updateColorFromOffset(
                        change.position,
                        size.width.toFloat(),
                        size.height.toFloat(),
                        onColorSelected
                    )
                }
            }
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = min(size.width, size.height) / 2f

        drawCircle(
            brush = Brush.sweepGradient(
                colors = listOf(
                    Color.Red,
                    Color.Yellow,
                    Color.Green,
                    Color.Cyan,
                    Color.Blue,
                    Color.Magenta,
                    Color.Red
                ),
                center = center
            ),
            radius = radius,
            center = center
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White, Color.Transparent),
                center = center,
                radius = radius
            ),
            radius = radius,
            center = center
        )

        val angle = Math.toRadians(hue.toDouble())
        val indicatorRadius = saturation * radius
        val indicator = Offset(
            x = center.x + (cos(angle) * indicatorRadius).toFloat(),
            y = center.y + (sin(angle) * indicatorRadius).toFloat()
        )

        drawCircle(
            color = Color.White,
            radius = 10f,
            center = indicator,
            style = Stroke(width = 4f)
        )
        drawCircle(
            color = selectedColor,
            radius = 7f,
            center = indicator
        )
    }
}

private fun updateColorFromOffset(
    offset: Offset,
    width: Float,
    height: Float,
    onColorSelected: (Color) -> Unit
) {
    val cx = width / 2f
    val cy = height / 2f
    val dx = offset.x - cx
    val dy = offset.y - cy
    val radius = min(width, height) / 2f

    val distance = sqrt(dx * dx + dy * dy)
    val saturation = (distance / radius).coerceIn(0f, 1f)
    val hue = ((Math.toDegrees(atan2(dy, dx).toDouble()) + 360.0) % 360.0).toFloat()

    val hsv = floatArrayOf(hue, saturation, 1f)
    val argb = android.graphics.Color.HSVToColor(hsv)
    onColorSelected(Color(argb))
}
