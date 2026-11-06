package com.example.esp32_servo.ui

import com.example.esp32_servo.MainDispatcherRule
import com.example.esp32_servo.data.BluetoothAvailability
import com.example.esp32_servo.data.ConnectionState
import com.example.esp32_servo.data.DisconnectReason
import com.example.esp32_servo.data.FakeServoRepository
import com.example.esp32_servo.data.FirmwareTarget
import com.example.esp32_servo.data.IncompatibilityProblem
import com.example.esp32_servo.data.SampleDevices
import com.example.esp32_servo.data.SendResult
import com.example.esp32_servo.servo.AnglePreset
import com.example.esp32_servo.servo.AngleRange
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ServoViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun TestScope.fakeRepository(
        availability: BluetoothAvailability = BluetoothAvailability.Ready,
    ) = FakeServoRepository(availability = availability, clock = { testScheduler.currentTime })

    private fun TestScope.viewModel(
        repository: FakeServoRepository,
        range: AngleRange = AngleRange(0, 180),
    ): ServoViewModel {
        val viewModel = ServoViewModel(repository, range, commandIntervalMs = 50)
        // Aktywny subskrybent, tak jak ekran – uiState używa WhileSubscribed.
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        runCurrent()
        return viewModel
    }

    private val ServoViewModel.state get() = uiState.value

    private val FakeServoRepository.sentAngles get() = sentCommands.map { it.angle }

    // ---- Przejścia stanów ----

    @Test
    fun `controls stay locked until the angle characteristic is validated`() = runTest {
        val repository = fakeRepository()
        val viewModel = viewModel(repository)
        assertFalse(viewModel.state.control.enabled)
        assertEquals(StatusSummary.Disconnected, viewModel.state.summary)

        viewModel.onAction(ServoAction.Connect)
        runCurrent()
        assertTrue(viewModel.state.picker.visible)
        assertEquals(1, repository.scanStartCount)
        assertEquals(StatusSummary.Scanning, viewModel.state.summary)

        viewModel.onAction(ServoAction.SelectDevice(SampleDevices.servo))
        runCurrent()
        assertEquals(StatusSummary.Connecting, viewModel.state.summary)
        assertEquals(SampleDevices.servo.address, viewModel.state.picker.connectingAddress)
        assertFalse(viewModel.state.control.enabled)

        repository.simulateDiscoveringServices()
        runCurrent()
        assertEquals(StatusSummary.DiscoveringServices, viewModel.state.summary)
        assertFalse("Samo połączenie GATT nie może odblokować sterowania", viewModel.state.control.enabled)

        repository.simulateReady()
        runCurrent()
        assertEquals(StatusSummary.Ready, viewModel.state.summary)
        assertTrue(viewModel.state.control.enabled)
        assertFalse("Arkusz zamyka się po połączeniu", viewModel.state.picker.visible)
    }

    @Test
    fun `missing permission is handled in the sheet and scanning resumes after grant`() = runTest {
        val repository = fakeRepository(BluetoothAvailability.PermissionsMissing)
        val viewModel = viewModel(repository)

        viewModel.onAction(ServoAction.Connect)
        runCurrent()
        assertTrue(viewModel.state.picker.visible)
        assertEquals(BluetoothAvailability.PermissionsMissing, viewModel.state.picker.availability)
        assertEquals(0, repository.scanStartCount)

        viewModel.onPermissionsResult(allGranted = false, permanentlyDenied = true)
        runCurrent()
        assertTrue(viewModel.state.picker.permissionPermanentlyDenied)

        repository.simulateAvailability(BluetoothAvailability.Ready)
        runCurrent()
        assertEquals(1, repository.scanStartCount)
        assertFalse(viewModel.state.picker.permissionPermanentlyDenied)
    }

    @Test
    fun `bluetooth turned off is reported without opening any dialog`() = runTest {
        val repository = fakeRepository(BluetoothAvailability.Disabled)
        val viewModel = viewModel(repository)

        assertEquals(StatusSummary.BluetoothOff, viewModel.state.summary)
        assertFalse(viewModel.state.picker.visible)
    }

    @Test
    fun `user disconnect is not reported as an error`() = runTest {
        val repository = fakeRepository()
        val viewModel = viewModel(repository)
        repository.simulateReady()
        runCurrent()

        viewModel.onAction(ServoAction.Disconnect)
        runCurrent()

        assertEquals(StatusSummary.Disconnected, viewModel.state.summary)
        assertFalse(viewModel.state.control.enabled)
    }

    @Test
    fun `incompatible device is reported in the sheet`() = runTest {
        val repository = fakeRepository()
        val viewModel = viewModel(repository)
        viewModel.onAction(ServoAction.Connect)
        viewModel.onAction(ServoAction.SelectDevice(SampleDevices.unrelated))
        runCurrent()

        repository.simulateDisconnected(DisconnectReason.Incompatible(IncompatibilityProblem.MissingService))
        runCurrent()

        assertEquals(
            DisconnectReason.Incompatible(IncompatibilityProblem.MissingService),
            viewModel.state.picker.connectionError,
        )
        assertEquals(StatusSummary.Error, viewModel.state.summary)
        assertFalse(viewModel.state.control.enabled)
    }

    // ---- Brak automatycznego ruchu ----

    @Test
    fun `connecting never sends a command on its own`() = runTest {
        val repository = fakeRepository()
        val viewModel = viewModel(repository)

        repository.simulateReady()
        advanceUntilIdle()

        assertTrue(repository.sentCommands.isEmpty())
        assertEquals(CommandStatus.NothingSent, viewModel.state.control.command)
        assertEquals(90, viewModel.state.control.targetAngle)
    }

    @Test
    fun `reconnecting does not replay the previous command`() = runTest {
        val repository = fakeRepository()
        val viewModel = viewModel(repository)
        repository.simulateReady()
        runCurrent()
        viewModel.onAction(ServoAction.SliderChange(120f))
        viewModel.onAction(ServoAction.SliderChangeFinished)
        advanceUntilIdle()
        assertEquals(listOf(120), repository.sentAngles)

        repository.simulateDisconnected(DisconnectReason.LinkLost(gattStatus = 0x13))
        runCurrent()
        repository.simulateReady()
        advanceUntilIdle()

        assertEquals(listOf(120), repository.sentAngles)
        assertEquals(CommandStatus.NothingSent, viewModel.state.control.command)
        assertEquals("Suwak zachowuje lokalną wartość do zadania", 120, viewModel.state.control.targetAngle)
    }

    @Test
    fun `angle reported by firmware updates the slider without sending`() = runTest {
        val repository = fakeRepository()
        val viewModel = viewModel(repository)

        repository.simulateReady(firmwareTarget = FirmwareTarget.Angle(135))
        advanceUntilIdle()

        assertEquals(135, viewModel.state.control.targetAngle)
        assertEquals(CommandStatus.ReportedByDevice(135), viewModel.state.control.command)
        assertTrue(repository.sentCommands.isEmpty())
    }

    @Test
    fun `firmware angle outside the configured range is ignored`() = runTest {
        val repository = fakeRepository()
        val viewModel = viewModel(repository, AngleRange(10, 170))

        repository.simulateReady(firmwareTarget = FirmwareTarget.Angle(175))
        advanceUntilIdle()

        assertEquals(90, viewModel.state.control.targetAngle)
        assertEquals(CommandStatus.NothingSent, viewModel.state.control.command)
    }

    @Test
    fun `input is ignored while not ready`() = runTest {
        val repository = fakeRepository()
        val viewModel = viewModel(repository)

        viewModel.onAction(ServoAction.SliderChange(10f))
        viewModel.onAction(ServoAction.SliderChangeFinished)
        viewModel.onAction(ServoAction.Preset(AnglePreset.Max))
        advanceUntilIdle()

        assertTrue(repository.sentCommands.isEmpty())
        assertEquals(90, viewModel.state.control.targetAngle)
    }

    // ---- Wysyłanie komend ----

    @Test
    fun `slider drag is rate limited and ends on the final value`() = runTest {
        val repository = fakeRepository().apply { sendDurationMs = 8 }
        val viewModel = viewModel(repository)
        repository.simulateReady()
        runCurrent()

        for (angle in 0..100) {
            viewModel.onAction(ServoAction.SliderChange(angle.toFloat()))
            advanceTimeBy(5)
        }
        viewModel.onAction(ServoAction.SliderChangeFinished)
        advanceUntilIdle()

        val times = repository.sentCommands.map { it.atMs }
        times.zipWithNext().forEach { (a, b) -> assertTrue("Odstęp ${b - a} ms < 50 ms", b - a >= 50) }
        assertTrue("Za dużo komend: ${times.size}", times.size <= 12)
        repository.sentAngles.zipWithNext().forEach { (a, b) -> assertTrue("Nieaktualny kąt $b po $a", b > a) }
        assertEquals(100, repository.sentAngles.last())
        assertEquals(CommandStatus.Delivered(100, acknowledged = true), viewModel.state.control.command)
    }

    @Test
    fun `final slider value is delivered after the gesture ends`() = runTest {
        val repository = fakeRepository()
        val viewModel = viewModel(repository)
        repository.simulateReady()
        runCurrent()

        viewModel.onAction(ServoAction.SliderChange(10f))
        advanceTimeBy(10)
        viewModel.onAction(ServoAction.SliderChange(11f))
        advanceTimeBy(10)
        viewModel.onAction(ServoAction.SliderChange(12f))
        viewModel.onAction(ServoAction.SliderChangeFinished)
        advanceUntilIdle()

        assertEquals(listOf(10, 12), repository.sentAngles)
        assertEquals(CommandStatus.Delivered(12, acknowledged = true), viewModel.state.control.command)
    }

    @Test
    fun `gesture end does not resend an already delivered value`() = runTest {
        val repository = fakeRepository()
        val viewModel = viewModel(repository)
        repository.simulateReady()
        runCurrent()

        viewModel.onAction(ServoAction.SliderChange(30f))
        advanceUntilIdle()
        viewModel.onAction(ServoAction.SliderChangeFinished)
        advanceUntilIdle()

        assertEquals(listOf(30), repository.sentAngles)
    }

    @Test
    fun `failed write is reported and can be retried by the user`() = runTest {
        val repository = fakeRepository().apply { sendResult = SendResult.Failed(gattStatus = 0x80) }
        val viewModel = viewModel(repository)
        repository.simulateReady()
        runCurrent()

        viewModel.onAction(ServoAction.Preset(AnglePreset.Min))
        advanceUntilIdle()
        assertEquals(
            CommandStatus.Failed(0, SendResult.Failed(gattStatus = 0x80)),
            viewModel.state.control.command,
        )

        repository.sendResult = SendResult.Acknowledged
        viewModel.onAction(ServoAction.RetrySend)
        advanceUntilIdle()

        assertEquals(listOf(0, 0), repository.sentAngles)
        assertEquals(CommandStatus.Delivered(0, acknowledged = true), viewModel.state.control.command)
    }

    @Test
    fun `link loss drops the pending command and locks the controls`() = runTest {
        val repository = fakeRepository().apply { sendDurationMs = 30 }
        val viewModel = viewModel(repository)
        repository.simulateReady()
        runCurrent()

        viewModel.onAction(ServoAction.SliderChange(100f)) // wysyłane od razu (t = 0..30)
        advanceTimeBy(5)
        viewModel.onAction(ServoAction.SliderChange(110f)) // czeka na swoją kolej
        advanceTimeBy(5)
        repository.simulateDisconnected(DisconnectReason.LinkLost(gattStatus = 0x08))
        runCurrent()

        assertFalse(viewModel.state.control.enabled)
        assertEquals(CommandStatus.Unavailable, viewModel.state.control.command)
        assertEquals(StatusSummary.Error, viewModel.state.summary)
        assertTrue(viewModel.state.connection is ConnectionState.Disconnected)

        advanceUntilIdle()
        repository.simulateReady()
        advanceUntilIdle()

        assertEquals("110° nie może zostać wysłane do nowej sesji", listOf(100), repository.sentAngles)
        assertEquals(CommandStatus.NothingSent, viewModel.state.control.command)
    }

    @Test
    fun `presets and steps follow the configured range`() = runTest {
        val repository = fakeRepository()
        val viewModel = viewModel(repository, AngleRange(20, 160))
        repository.simulateReady()
        runCurrent()

        viewModel.onAction(ServoAction.Preset(AnglePreset.Min))
        advanceUntilIdle()
        assertEquals(20, viewModel.state.control.targetAngle)
        assertFalse(viewModel.state.control.canDecrement)

        viewModel.onAction(ServoAction.Step(-1))
        advanceUntilIdle()
        assertEquals(20, viewModel.state.control.targetAngle)

        viewModel.onAction(ServoAction.Step(+1))
        advanceUntilIdle()
        viewModel.onAction(ServoAction.Preset(AnglePreset.Center))
        advanceUntilIdle()
        viewModel.onAction(ServoAction.Preset(AnglePreset.Max))
        advanceUntilIdle()
        viewModel.onAction(ServoAction.Step(+1))
        advanceUntilIdle()

        assertEquals(listOf(20, 21, 90, 160), repository.sentAngles)
        assertFalse(viewModel.state.control.canIncrement)
    }

    @Test
    fun `closing the sheet stops scanning but keeps an ongoing connection`() = runTest {
        val repository = fakeRepository()
        val viewModel = viewModel(repository)
        viewModel.onAction(ServoAction.Connect)
        viewModel.onAction(ServoAction.SelectDevice(SampleDevices.servo))
        runCurrent()

        viewModel.onAction(ServoAction.DismissPicker)
        runCurrent()

        assertFalse(viewModel.state.picker.visible)
        assertTrue(viewModel.state.connection is ConnectionState.Connecting)
        assertNull(viewModel.state.picker.connectionError)
    }
}
