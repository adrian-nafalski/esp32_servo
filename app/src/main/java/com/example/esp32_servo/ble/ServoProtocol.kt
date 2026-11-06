package com.example.esp32_servo.ble

import com.example.esp32_servo.data.FirmwareTarget
import java.util.UUID

/**
 * WŁASNY PROTOKÓŁ DEMONSTRACYJNY – to nie jest żaden standard BLE dla serw.
 *
 * Jedna usługa GATT z dwiema charakterystykami:
 *
 * | Charakterystyka       | Właściwości | Zawartość                                         |
 * |-----------------------|-------------|---------------------------------------------------|
 * | TARGET_ANGLE (0002)   | Write       | kąt zadany, uint16 little-endian, 2 bajty, stopnie |
 * | LAST_ACCEPTED (0003)  | Read        | ostatni kąt PRZYJĘTY przez firmware, uint16 LE;    |
 * |                       |             | 0xFFFF = brak komendy od uruchomienia ESP32        |
 *
 * LAST_ACCEPTED jest opcjonalna. Opisuje ostatnią komendę, którą firmware zaakceptował
 * po walidacji i przekazał do generatora PWM. NIE jest pomiarem położenia wału:
 * SG90/MG90S nie ma czujnika, który mógłby to potwierdzić.
 *
 * Po zmianie UUID zaktualizuj te same stałe w firmware/esp32_servo_ble/esp32_servo_ble.ino.
 */
object ServoProtocol {

    val SERVICE_UUID: UUID = UUID.fromString("f6330001-4862-4597-8d0d-4b9db8a3b9c7")
    val TARGET_ANGLE_UUID: UUID = UUID.fromString("f6330002-4862-4597-8d0d-4b9db8a3b9c7")
    val LAST_ACCEPTED_ANGLE_UUID: UUID = UUID.fromString("f6330003-4862-4597-8d0d-4b9db8a3b9c7")

    const val PAYLOAD_SIZE = 2

    /** Wartość zarezerwowana w charakterystyce statusu: brak komendy od uruchomienia ESP32. */
    const val NO_COMMAND_SINCE_BOOT = 0xFFFF

    /** Największy kąt, jaki da się zakodować (0xFFFF jest zarezerwowane). */
    const val MAX_ANGLE = 0xFFFE

    /** Koduje kąt w stopniach jako uint16 little-endian: 180 → [0xB4, 0x00]. */
    fun encodeTargetAngle(degrees: Int): ByteArray {
        require(degrees in 0..MAX_ANGLE) { "Kąt poza zakresem protokołu: $degrees" }
        return byteArrayOf(
            (degrees and 0xFF).toByte(),
            ((degrees ushr 8) and 0xFF).toByte(),
        )
    }

    /** Dekoduje uint16 little-endian; zwraca null, gdy rozmiar danych jest inny niż 2 bajty. */
    fun decodeUInt16(bytes: ByteArray): Int? {
        if (bytes.size != PAYLOAD_SIZE) return null
        return (bytes[0].toInt() and 0xFF) or ((bytes[1].toInt() and 0xFF) shl 8)
    }

    fun decodeLastAccepted(bytes: ByteArray): FirmwareTarget = when (val value = decodeUInt16(bytes)) {
        null -> FirmwareTarget.ReadFailed
        NO_COMMAND_SINCE_BOOT -> FirmwareTarget.NoCommandSinceBoot
        else -> FirmwareTarget.Angle(value)
    }
}
