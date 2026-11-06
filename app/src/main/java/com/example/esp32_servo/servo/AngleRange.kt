package com.example.esp32_servo.servo

import com.example.esp32_servo.ble.ServoProtocol

/**
 * Logiczny zakres kąta zadanego w stopniach (obustronnie domknięty).
 *
 * To zakres, który aplikacja pozwala ustawić. Nie gwarantuje, że mechanika serwa
 * bezpiecznie go obsłuży – granice trzeba dopasować do konkretnego egzemplarza.
 */
data class AngleRange(val min: Int, val max: Int) {

    init {
        require(min >= 0) { "Minimalny kąt nie może być ujemny: $min" }
        require(max <= ServoProtocol.MAX_ANGLE) { "Maksymalny kąt przekracza zakres protokołu: $max" }
        require(min < max) { "Zakres musi być niepusty: $min–$max" }
    }

    /** Środek zakresu zaokrąglony w dół, np. 0–180 → 90, 10–171 → 90. */
    val center: Int get() = min + (max - min) / 2

    operator fun contains(angle: Int): Boolean = angle in min..max

    fun clamp(angle: Int): Int = angle.coerceIn(min, max)

    fun angleOf(preset: AnglePreset): Int = when (preset) {
        AnglePreset.Min -> min
        AnglePreset.Center -> center
        AnglePreset.Max -> max
    }
}

/** Szybkie pozycje – zawsze wyliczane z aktualnie skonfigurowanego zakresu. */
enum class AnglePreset { Min, Center, Max }
