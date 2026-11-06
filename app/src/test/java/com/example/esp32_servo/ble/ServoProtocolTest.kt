package com.example.esp32_servo.ble

import com.example.esp32_servo.data.FirmwareTarget
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServoProtocolTest {

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun `angle is encoded as two byte unsigned little endian`() {
        assertArrayEquals(bytes(0x00, 0x00), ServoProtocol.encodeTargetAngle(0))
        assertArrayEquals(bytes(0x5A, 0x00), ServoProtocol.encodeTargetAngle(90))
        assertArrayEquals(bytes(0xB4, 0x00), ServoProtocol.encodeTargetAngle(180))
        assertArrayEquals(bytes(0x2C, 0x01), ServoProtocol.encodeTargetAngle(300))
        assertArrayEquals(bytes(0xFE, 0xFF), ServoProtocol.encodeTargetAngle(ServoProtocol.MAX_ANGLE))
    }

    @Test
    fun `payload always has exactly two bytes`() {
        for (angle in listOf(0, 1, 127, 128, 255, 256, 1_000, ServoProtocol.MAX_ANGLE)) {
            assertEquals(ServoProtocol.PAYLOAD_SIZE, ServoProtocol.encodeTargetAngle(angle).size)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative angle is rejected`() {
        ServoProtocol.encodeTargetAngle(-1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `reserved value 0xFFFF cannot be sent as an angle`() {
        ServoProtocol.encodeTargetAngle(0xFFFF)
    }

    @Test
    fun `encoding round trips through decoding`() {
        for (angle in 0..1_000) {
            assertEquals(angle, ServoProtocol.decodeUInt16(ServoProtocol.encodeTargetAngle(angle)))
        }
    }

    @Test
    fun `payload of wrong size is not decoded`() {
        assertNull(ServoProtocol.decodeUInt16(bytes()))
        assertNull(ServoProtocol.decodeUInt16(bytes(0x5A)))
        assertNull(ServoProtocol.decodeUInt16(bytes(0x5A, 0x00, 0x00)))
    }

    @Test
    fun `last accepted characteristic distinguishes angle, no command and garbage`() {
        assertEquals(FirmwareTarget.Angle(180), ServoProtocol.decodeLastAccepted(bytes(0xB4, 0x00)))
        assertEquals(FirmwareTarget.NoCommandSinceBoot, ServoProtocol.decodeLastAccepted(bytes(0xFF, 0xFF)))
        assertEquals(FirmwareTarget.ReadFailed, ServoProtocol.decodeLastAccepted(bytes(0x01)))
    }

    @Test
    fun `service and characteristics have distinct uuids`() {
        val uuids = setOf(
            ServoProtocol.SERVICE_UUID,
            ServoProtocol.TARGET_ANGLE_UUID,
            ServoProtocol.LAST_ACCEPTED_ANGLE_UUID,
        )
        assertEquals(3, uuids.size)
    }
}
