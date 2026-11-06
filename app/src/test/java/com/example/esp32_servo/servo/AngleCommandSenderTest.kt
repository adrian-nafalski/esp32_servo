package com.example.esp32_servo.servo

import com.example.esp32_servo.data.SendResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AngleCommandSenderTest {

    // Pętla nadawcy działa w zwykłym (nie "background") zakresie, aby advanceUntilIdle()
    // przesuwał czas również dla jej opóźnień. Po teście zakres jest anulowany.
    private val senderJob = Job()

    @After
    fun tearDown() = senderJob.cancel()

    private class Link(private val scope: TestScope, private val durationMs: Long = 0L) {
        val sent = mutableListOf<Pair<Long, Int>>() // (czas startu zapisu, kąt)
        var inFlight = 0
        var maxInFlight = 0

        suspend fun transmit(command: AngleCommand): SendResult {
            sent += scope.testScheduler.currentTime to command.angle
            inFlight++
            maxInFlight = maxOf(maxInFlight, inFlight)
            if (durationMs > 0) delay(durationMs)
            inFlight--
            return SendResult.Acknowledged
        }
    }

    private fun TestScope.sender(link: Link, intervalMs: Long = 50L) = AngleCommandSender(
        scope = CoroutineScope(StandardTestDispatcher(testScheduler) + senderJob),
        minIntervalMs = intervalMs,
        transmit = link::transmit,
        onResult = { _, _ -> },
    )

    @Test
    fun `first command is sent immediately`() = runTest {
        val link = Link(this)
        val sender = sender(link)
        runCurrent()

        sender.submit(AngleCommand(session = 1, angle = 42))
        runCurrent()

        assertEquals(listOf(0L to 42), link.sent)
    }

    @Test
    fun `drag is limited to one command per interval and keeps only the latest value`() = runTest {
        val link = Link(this, durationMs = 8)
        val sender = sender(link)
        runCurrent()

        for (angle in 0..100) { // 101 zdarzeń co 5 ms ≈ 0,5 s przeciągania
            sender.submit(AngleCommand(1, angle))
            advanceTimeBy(5)
        }
        advanceUntilIdle()

        val times = link.sent.map { it.first }
        val angles = link.sent.map { it.second }
        times.zipWithNext().forEach { (a, b) -> assertTrue("Odstęp ${b - a} ms < 50 ms", b - a >= 50) }
        assertTrue("Wysłano ${angles.size} komend zamiast co najwyżej 12", angles.size <= 12)
        angles.zipWithNext().forEach { (a, b) -> assertTrue("Nieaktualny kąt $b po $a", b > a) }
        assertEquals("Końcowa wartość musi dotrzeć", 100, angles.last())
    }

    @Test
    fun `slow link does not build a queue of stale angles`() = runTest {
        val link = Link(this, durationMs = 200)
        val sender = sender(link)
        runCurrent()

        for (angle in 1..10) {
            sender.submit(AngleCommand(1, angle))
            advanceTimeBy(10)
        }
        advanceUntilIdle()

        assertEquals(listOf(1, 10), link.sent.map { it.second })
    }

    @Test
    fun `writes never overlap even when they take longer than the interval`() = runTest {
        val link = Link(this, durationMs = 80)
        val sender = sender(link)
        runCurrent()

        sender.submit(AngleCommand(1, 1))
        runCurrent()
        sender.submit(AngleCommand(1, 2))
        advanceTimeBy(100)
        sender.submit(AngleCommand(1, 3))
        advanceUntilIdle()

        assertEquals(1, link.maxInFlight)
        assertEquals(listOf(0L to 1, 80L to 2, 160L to 3), link.sent)
    }

    @Test
    fun `final value after a burst is delivered once the interval passes`() = runTest {
        val link = Link(this)
        val sender = sender(link)
        runCurrent()

        sender.submit(AngleCommand(1, 10))
        advanceTimeBy(10)
        sender.submit(AngleCommand(1, 11))
        advanceTimeBy(10)
        sender.submit(AngleCommand(1, 12))
        advanceUntilIdle()

        assertEquals(listOf(0L to 10, 50L to 12), link.sent)
    }

    @Test
    fun `clearPending drops the waiting command`() = runTest {
        val link = Link(this, durationMs = 100)
        val sender = sender(link)
        runCurrent()

        sender.submit(AngleCommand(1, 1))
        runCurrent()
        sender.submit(AngleCommand(1, 2))
        assertTrue(sender.hasPending)
        sender.clearPending()
        advanceUntilIdle()

        assertEquals(listOf(1), link.sent.map { it.second })
    }
}
