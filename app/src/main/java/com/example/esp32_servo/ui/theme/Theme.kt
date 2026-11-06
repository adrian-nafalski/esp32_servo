package com.example.esp32_servo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

private val LocalServoColors = staticCompositionLocalOf { ServoLightExtendedColors }

/** Umiarkowane zaokrąglenia: narzędzie, nie zabawka. */
internal val ServoShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp),
)

/**
 * Motyw zależny od ustawień systemu (jasny / ciemny).
 * Dynamic color jest celowo wyłączony: barwy aplikacji muszą zgadzać się z ikoną
 * i splash screenem, które dynamicznych kolorów nie używają.
 */
@Composable
fun ServoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalServoColors provides if (darkTheme) ServoDarkExtendedColors else ServoLightExtendedColors,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) ServoDarkColors else ServoLightColors,
            typography = ServoTypography,
            shapes = ServoShapes,
            content = content,
        )
    }
}

object ServoTheme {
    val colors: ServoExtendedColors
        @Composable
        @ReadOnlyComposable
        get() = LocalServoColors.current
}
