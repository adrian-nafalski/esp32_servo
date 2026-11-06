package com.example.esp32_servo.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Base = Typography()

/** Nieco większe rozmiary niż domyślne M3 – lepsza czytelność przy pracy z elektroniką. */
internal val ServoTypography = Base.copy(
    titleLarge = Base.titleLarge.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium),
    titleMedium = Base.titleMedium.copy(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
    bodyLarge = Base.bodyLarge.copy(fontSize = 17.sp, lineHeight = 24.sp),
    bodyMedium = Base.bodyMedium.copy(fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall = Base.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = Base.labelLarge.copy(fontSize = 15.sp, lineHeight = 20.sp),
    labelMedium = Base.labelMedium.copy(fontSize = 13.sp, lineHeight = 18.sp),
    labelSmall = Base.labelSmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
)

/** Duży odczyt kąta. Cyfry o stałej szerokości (tnum), aby liczba nie "pływała" przy przeciąganiu. */
internal val AngleDisplayStyle = TextStyle(
    fontSize = 64.sp,
    lineHeight = 72.sp,
    fontWeight = FontWeight.Normal,
    letterSpacing = (-1).sp,
    fontFeatureSettings = "tnum",
)

/** Adresy i UUID. */
internal val TechnicalTextStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 13.sp,
    lineHeight = 18.sp,
)
