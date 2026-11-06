package com.example.esp32_servo.servo

/**
 * Jedno miejsce z parametrami zachowania aplikacji.
 *
 * ZAŁOŻENIE DEMONSTRACYJNE: serwo pozycyjne (SG90 / MG90S) i zakres logiczny 0–180°.
 * Zakres trzeba sprawdzić na sprzęcie. Jeśli serwo dochodzi do ogranicznika, buczy albo
 * grzeje się na krańcach, zawęź zakres TUTAJ i w firmware (ANGLE_MIN / ANGLE_MAX).
 * Firmware i tak odrzuca kąty spoza własnego zakresu.
 */
object ServoConfig {

    val angleRange = AngleRange(min = 0, max = 180)

    /** Najwyżej jedna komenda BLE na ten interwał (wartość demonstracyjna). */
    const val COMMAND_INTERVAL_MS = 50L

    /** Limit czasu jednego wyszukiwania BLE. */
    const val SCAN_DURATION_S = 10

    /** Po tylu sekundach w tle aplikacja zamyka połączenie z ESP32. */
    const val BACKGROUND_DISCONNECT_DELAY_S = 10
}
