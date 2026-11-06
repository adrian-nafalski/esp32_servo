package com.example.esp32_servo.servo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AngleRangeTest {

    @Test
    fun `presets are derived from the configured range`() {
        val range = AngleRange(20, 160)
        assertEquals(20, range.angleOf(AnglePreset.Min))
        assertEquals(90, range.angleOf(AnglePreset.Center))
        assertEquals(160, range.angleOf(AnglePreset.Max))
    }

    @Test
    fun `center rounds down for odd spans`() {
        assertEquals(90, AngleRange(0, 180).center)
        assertEquals(90, AngleRange(10, 171).center)
        assertEquals(1, AngleRange(0, 3).center)
    }

    @Test
    fun `contains is inclusive on both ends`() {
        val range = AngleRange(0, 180)
        assertTrue(0 in range)
        assertTrue(180 in range)
        assertFalse(-1 in range)
        assertFalse(181 in range)
    }

    @Test
    fun `clamp keeps values inside the range`() {
        val range = AngleRange(10, 170)
        assertEquals(10, range.clamp(-50))
        assertEquals(10, range.clamp(9))
        assertEquals(95, range.clamp(95))
        assertEquals(170, range.clamp(171))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative minimum is rejected`() {
        AngleRange(-1, 180)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `empty range is rejected`() {
        AngleRange(90, 90)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `range above protocol limit is rejected`() {
        AngleRange(0, 0xFFFF)
    }

    @Test
    fun `default configuration is a valid demo range`() {
        assertEquals(AngleRange(0, 180), ServoConfig.angleRange)
    }
}
