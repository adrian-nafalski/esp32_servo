package com.example.esp32_servo.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.esp32_servo.ui.theme.ServoTheme

/**
 * Wskaźnik stanu: kształt + kolor + tekst. Kształt różni się między stanami,
 * więc informacja nie zależy wyłącznie od rozpoznawania koloru.
 *   gotowe – pełne koło, w toku – pierścień z kropką, błąd – romb, neutralny – pusty pierścień.
 */
@Composable
internal fun StatusLine(summary: StatusSummary, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        StatusGlyph(summary.tone)
        Text(
            text = statusText(summary),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun StatusGlyph(tone: StatusTone, modifier: Modifier = Modifier) {
    val color: Color = when (tone) {
        StatusTone.Positive -> ServoTheme.colors.positive
        StatusTone.Busy -> MaterialTheme.colorScheme.primary
        StatusTone.Negative -> MaterialTheme.colorScheme.error
        StatusTone.Neutral -> MaterialTheme.colorScheme.outline
    }
    Canvas(modifier.size(10.dp)) {
        val radius = size.minDimension / 2f
        val strokeWidth = 1.5.dp.toPx()
        when (tone) {
            StatusTone.Positive -> drawCircle(color, radius)
            StatusTone.Busy -> {
                drawCircle(color, radius - strokeWidth / 2f, style = Stroke(strokeWidth))
                drawCircle(color, radius * 0.35f)
            }

            StatusTone.Negative -> {
                val c = center
                val diamond = Path().apply {
                    moveTo(c.x, c.y - radius)
                    lineTo(c.x + radius, c.y)
                    lineTo(c.x, c.y + radius)
                    lineTo(c.x - radius, c.y)
                    close()
                }
                drawPath(diamond, color)
            }

            StatusTone.Neutral -> drawCircle(
                color = color,
                radius = radius - strokeWidth / 2f,
                center = Offset(size.width / 2f, size.height / 2f),
                style = Stroke(strokeWidth),
            )
        }
    }
}
