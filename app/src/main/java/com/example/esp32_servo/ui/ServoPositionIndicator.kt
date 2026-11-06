package com.example.esp32_servo.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.example.esp32_servo.R
import com.example.esp32_servo.servo.AngleRange
import com.example.esp32_servo.ui.theme.ServoTheme
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Sposób narysowania orczyka – rozróżnia wartość wysłaną od jedynie proponowanej. */
enum class ArmStyle {
    /** Wartość wysłana lub zgłoszona przez ESP32 – pełny orczyk. */
    Commanded,

    /** Wartość lokalna, jeszcze niewysłana – sam kontur. */
    Proposed,

    /** Sterowanie nieaktywne – wyszarzone. */
    Disabled,
}

/**
 * Widok serwa z góry z łukiem dozwolonego zakresu. Pokazuje kąt ZADANY, a nie zmierzony.
 * Kierunek rysunku jest zgodny z suwakiem (min po lewej), co nie musi odpowiadać
 * fizycznemu kierunkowi obrotu wału – to zależy od serwa i montażu.
 */
@Composable
fun ServoPositionIndicator(
    angle: Int,
    range: AngleRange,
    armStyle: ArmStyle,
    modifier: Modifier = Modifier,
) {
    // Płynny podgląd lokalny; przy wyłączonych animacjach systemowych przeskakuje od razu.
    val animatedAngle by animateFloatAsState(
        targetValue = angle.toFloat(),
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label = "servoArmAngle",
    )
    val scheme = MaterialTheme.colorScheme
    val servoColors = ServoTheme.colors
    val bodyColor = if (armStyle == ArmStyle.Disabled) scheme.outlineVariant else servoColors.servoBody
    val hornColor = if (armStyle == ArmStyle.Disabled) scheme.outline else servoColors.servoHorn
    val axisColor = scheme.background
    val arcColor = scheme.outlineVariant
    val tickColor = scheme.outline
    val labelStyle = MaterialTheme.typography.labelMedium.copy(color = scheme.onSurfaceVariant)
    val textMeasurer = rememberTextMeasurer()
    val description = stringResource(R.string.indicator_description, spokenDegrees(angle))

    Spacer(
        modifier
            .semantics { contentDescription = description }
            .drawWithCache {
                val sideReserve = 40.dp.toPx()
                val topReserve = 24.dp.toPx()
                val bottomReserve = 2.dp.toPx()
                val unit = min(
                    (size.width / 2f - sideReserve) / TICK_OUTER,
                    (size.height - topReserve - bottomReserve) / (TICK_OUTER + ServoGlyph.BODY_BOTTOM),
                ).coerceAtLeast(0.1f)
                val pivot = Offset(size.width / 2f, topReserve + TICK_OUTER * unit)
                val stroke = 1.5.dp.toPx()
                val arcRadius = ARC_RADIUS * unit
                val horn = ServoGlyph.hornPath(unit)

                val majorTicks = listOf(range.min, range.center, range.max).distinct()
                val minorTicks = (range.min..range.max).filter { it % 30 == 0 && it !in majorTicks }
                val labels = majorTicks.map { degrees ->
                    val layout = textMeasurer.measure("$degrees°", labelStyle)
                    val direction = direction(degrees.toFloat())
                    val distance = TICK_OUTER * unit + 4.dp.toPx() +
                        abs(direction.x) * layout.size.width / 2f +
                        abs(direction.y) * layout.size.height / 2f
                    val center = pivot + direction * distance
                    layout to Offset(center.x - layout.size.width / 2f, center.y - layout.size.height / 2f)
                }

                onDrawBehind {
                    // Łuk dozwolonego zakresu. 0° rysunku = godzina 9, 180° = godzina 3.
                    drawArc(
                        color = arcColor,
                        startAngle = 180f + range.min,
                        sweepAngle = (range.max - range.min).toFloat(),
                        useCenter = false,
                        topLeft = pivot - Offset(arcRadius, arcRadius),
                        size = Size(arcRadius * 2f, arcRadius * 2f),
                        style = Stroke(stroke),
                    )
                    minorTicks.forEach { degrees ->
                        val d = direction(degrees.toFloat())
                        drawLine(arcColor, pivot + d * (TICK_INNER * unit), pivot + d * (TICK_MINOR_OUTER * unit), stroke, StrokeCap.Round)
                    }
                    majorTicks.forEach { degrees ->
                        val d = direction(degrees.toFloat())
                        drawLine(tickColor, pivot + d * (TICK_INNER * unit), pivot + d * (TICK_OUTER * unit), stroke, StrokeCap.Round)
                    }
                    labels.forEach { (layout, topLeft) -> drawText(layout, topLeft = topLeft) }

                    drawRoundRect(
                        color = bodyColor,
                        topLeft = pivot + Offset(ServoGlyph.BODY_LEFT * unit, ServoGlyph.BODY_TOP * unit),
                        size = Size(
                            (ServoGlyph.BODY_RIGHT - ServoGlyph.BODY_LEFT) * unit,
                            (ServoGlyph.BODY_BOTTOM - ServoGlyph.BODY_TOP) * unit,
                        ),
                        cornerRadius = CornerRadius(ServoGlyph.BODY_CORNER * unit),
                    )

                    translate(pivot.x, pivot.y) {
                        // Orczyk narysowany jest "w górę" (90°), stąd przesunięcie o −90°.
                        rotate(degrees = animatedAngle - 90f, pivot = Offset.Zero) {
                            if (armStyle == ArmStyle.Proposed) {
                                drawPath(horn, hornColor, style = Stroke(width = stroke * 1.4f))
                            } else {
                                drawPath(horn, hornColor)
                            }
                        }
                    }
                    drawCircle(axisColor, radius = ServoGlyph.AXIS_RADIUS * unit, center = pivot)
                }
            },
    )
}

/** Wektor jednostkowy dla kąta zadanego: 0° → w lewo, 90° → w górę, 180° → w prawo. */
private fun direction(degrees: Float): Offset {
    val radians = Math.toRadians(degrees.toDouble())
    return Offset(-cos(radians).toFloat(), -sin(radians).toFloat())
}

private const val ARC_RADIUS = 42f
private const val TICK_INNER = 44f
private const val TICK_MINOR_OUTER = 46f
private const val TICK_OUTER = 48f
