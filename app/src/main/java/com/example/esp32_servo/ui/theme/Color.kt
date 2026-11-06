package com.example.esp32_servo.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/*
 * Paleta: neutralne tła i jeden przygaszony niebieski akcent inspirowany obudową serwa.
 * Kolory semantyczne (positive, error) służą wyłącznie do stanów.
 *
 * Tła MUSZĄ być zgodne z @color/servo_background (values i values-night), bo
 * na nich rysuje się systemowy splash screen i okno przed pierwszą klatką Compose.
 */

internal val ServoLightColors = lightColorScheme(
    primary = Color(0xFF2F6497),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD6E4F2),
    onPrimaryContainer = Color(0xFF0F2F4E),
    inversePrimary = Color(0xFF9CC0E8),
    secondary = Color(0xFF556271),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDDE3EA),
    onSecondaryContainer = Color(0xFF18222D),
    tertiary = Color(0xFF556271),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFDDE3EA),
    onTertiaryContainer = Color(0xFF18222D),
    background = Color(0xFFF6F7F8),
    onBackground = Color(0xFF1A1D21),
    surface = Color(0xFFF6F7F8),
    onSurface = Color(0xFF1A1D21),
    surfaceVariant = Color(0xFFE2E6EA),
    onSurfaceVariant = Color(0xFF48515A),
    surfaceTint = Color(0xFF2F6497),
    inverseSurface = Color(0xFF2E3236),
    inverseOnSurface = Color(0xFFEFF1F3),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    outline = Color(0xFF737B84),
    outlineVariant = Color(0xFFC6CCD3),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFF6F7F8),
    surfaceDim = Color(0xFFD8DCE0),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF0F2F4),
    surfaceContainer = Color(0xFFEBEEF1),
    surfaceContainerHigh = Color(0xFFE5E9ED),
    surfaceContainerHighest = Color(0xFFDFE3E8),
)

internal val ServoDarkColors = darkColorScheme(
    primary = Color(0xFF9CC0E8),
    onPrimary = Color(0xFF0D2D4A),
    primaryContainer = Color(0xFF23496F),
    onPrimaryContainer = Color(0xFFD6E4F2),
    inversePrimary = Color(0xFF2F6497),
    secondary = Color(0xFFB9C6D3),
    onSecondary = Color(0xFF243140),
    secondaryContainer = Color(0xFF2F3A46),
    onSecondaryContainer = Color(0xFFDDE3EA),
    tertiary = Color(0xFFB9C6D3),
    onTertiary = Color(0xFF243140),
    tertiaryContainer = Color(0xFF2F3A46),
    onTertiaryContainer = Color(0xFFDDE3EA),
    background = Color(0xFF121417),
    onBackground = Color(0xFFE3E6EA),
    surface = Color(0xFF121417),
    onSurface = Color(0xFFE3E6EA),
    surfaceVariant = Color(0xFF2E3339),
    onSurfaceVariant = Color(0xFFAAB2BB),
    surfaceTint = Color(0xFF9CC0E8),
    inverseSurface = Color(0xFFE3E6EA),
    inverseOnSurface = Color(0xFF2E3236),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    outline = Color(0xFF8A929B),
    outlineVariant = Color(0xFF3B4148),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF33373C),
    surfaceDim = Color(0xFF121417),
    surfaceContainerLowest = Color(0xFF0D0F11),
    surfaceContainerLow = Color(0xFF181B1F),
    surfaceContainer = Color(0xFF1C2024),
    surfaceContainerHigh = Color(0xFF23272C),
    surfaceContainerHighest = Color(0xFF2B3035),
)

/** Kolory spoza ColorScheme: stan "gotowe" i motyw serwa wspólny z ikoną i splash screenem. */
@Immutable
data class ServoExtendedColors(
    val positive: Color,
    val servoBody: Color,
    val servoHorn: Color,
)

internal val ServoLightExtendedColors = ServoExtendedColors(
    positive = Color(0xFF2E7D4F),
    servoBody = Color(0xFF3B73AB), // = @color/splash_servo_body
    servoHorn = Color(0xFF1F2933), // = @color/splash_servo_horn
)

internal val ServoDarkExtendedColors = ServoExtendedColors(
    positive = Color(0xFF7CC79A),
    servoBody = Color(0xFF4C83BA),
    servoHorn = Color(0xFFE6EAEE),
)
