package com.example.esp32_servo.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin

/**
 * Geometria serwa wspólna z ikoną launchera i splash screenem
 * (res/drawable/ic_launcher_foreground.xml, splash_servo.xml).
 * Jednostki jak w siatce 108 ikony; współrzędne względem osi obrotu (63, 62).
 */
internal object ServoGlyph {
    const val HUB_RADIUS = 9f
    const val TIP_RADIUS = 4.5f
    const val ARM_LENGTH = 28f
    const val AXIS_RADIUS = 3.2f

    const val BODY_LEFT = -36f
    const val BODY_TOP = -14f
    const val BODY_RIGHT = 14f
    const val BODY_BOTTOM = 14f
    const val BODY_CORNER = 7f

    /**
     * Orczyk w kształcie zwężającej się kapsuły, skierowany w górę, z osią w (0, 0).
     * Okręgi piasty i końcówki są połączone zewnętrznymi stycznymi.
     */
    fun hornPath(unit: Float): Path {
        val hub = HUB_RADIUS * unit
        val tip = TIP_RADIUS * unit
        val length = ARM_LENGTH * unit
        val alpha = asin((HUB_RADIUS - TIP_RADIUS) / ARM_LENGTH)
        val alphaDegrees = Math.toDegrees(alpha.toDouble()).toFloat()
        val cosA = cos(alpha)
        val sinA = sin(alpha)
        return Path().apply {
            moveTo(-tip * cosA, -length + tip * sinA)
            // Kąty w Compose rosną zgodnie z ruchem wskazówek zegara, 0° = godzina 3.
            arcTo(
                rect = Rect(center = Offset(0f, -length), radius = tip),
                startAngleDegrees = 180f - alphaDegrees,
                sweepAngleDegrees = 180f + 2f * alphaDegrees,
                forceMoveTo = false,
            )
            lineTo(hub * cosA, hub * sinA)
            arcTo(
                rect = Rect(center = Offset.Zero, radius = hub),
                startAngleDegrees = alphaDegrees,
                sweepAngleDegrees = 180f - 2f * alphaDegrees,
                forceMoveTo = false,
            )
            close()
        }
    }
}
