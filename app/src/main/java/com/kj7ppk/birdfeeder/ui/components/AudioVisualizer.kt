package com.kj7ppk.birdfeeder.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun AudioVisualizer(
    levels: List<Float>,
    modifier: Modifier = Modifier,
    barColor: Color = Color(0xFF87CEEB) // SkyBlue
) {
    Canvas(modifier = modifier
        .fillMaxWidth()
        .height(150.dp)) {
        val width = size.width
        val height = size.height
        val barWidth = width / 50f
        val gap = 4f

        if (levels.isEmpty()) {
            // Draw a subtle placeholder (e.g., flat line or "Mic Off" graphic)
            val centerY = height / 2
            drawLine(
                color = barColor.copy(alpha = 0.3f),
                start = Offset(0f, centerY),
                end = Offset(width, centerY),
                strokeWidth = 2.dp.toPx()
            )
            // Optional: Draw some static "resting" bars
            for (i in 0 until 50) {
                drawRoundRect(
                    color = barColor.copy(alpha = 0.1f),
                    topLeft = Offset(i * (barWidth + gap), centerY - 2.dp.toPx()),
                    size = Size(barWidth, 4.dp.toPx()),
                    cornerRadius = CornerRadius(2f, 2f)
                )
            }
        } else {
            val lastLevels = levels.takeLast(50)
            lastLevels.forEachIndexed { index, level ->
                val barHeight = (level * height).coerceAtLeast(4f)
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(index * (barWidth + gap), (height - barHeight) / 2),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(4f, 4f)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AudioVisualizerPreview() {
    AudioVisualizer(levels = List(50) { (0..100).random() / 100f })
}
