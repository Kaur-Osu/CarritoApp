package com.carlosmvp.mandado

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Micrófono y barras que reflejan el nivel de entrada reportado por el reconocedor. */
@Composable
fun SpeechAudioIndicator(levelDb: Float, modifier: Modifier = Modifier) {
    val intensity = ((levelDb + 2f) / 12f).coerceIn(0f, 1f)
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicText("🎤", style = TextStyle(fontSize = 23.sp))
        Canvas(Modifier.width(66.dp).height(30.dp)) {
            val barCount = 5
            val barWidth = size.width / 10f
            val gap = size.width / 10f
            val weights = listOf(0.45f, 0.75f, 1f, 0.75f, 0.45f)
            for (index in 0 until barCount) {
                val height = size.height * (0.14f + intensity * weights[index] * 0.86f)
                val x = index * (barWidth + gap)
                drawRoundRect(
                    color = Color(0xFF4CAF50),
                    topLeft = Offset(x, (size.height - height) / 2f),
                    size = Size(barWidth, height),
                    cornerRadius = CornerRadius(barWidth / 2f)
                )
            }
        }
    }
}
