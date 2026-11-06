package com.example.esp32_servo.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.esp32_servo.data.BluetoothAvailability
import com.example.esp32_servo.data.DisconnectReason
import com.example.esp32_servo.data.FakeServoRepository
import com.example.esp32_servo.data.FirmwareTarget
import com.example.esp32_servo.data.IncompatibilityProblem
import com.example.esp32_servo.data.SampleDevices
import com.example.esp32_servo.data.ScanPhase
import com.example.esp32_servo.servo.ServoConfig
import com.example.esp32_servo.ui.theme.ServoTheme

@Preview(name = "Jasny", showBackground = true, backgroundColor = 0xFFF6F7F8, widthDp = 360, heightDp = 860)
@Preview(
    name = "Ciemny",
    showBackground = true,
    backgroundColor = 0xFF121417,
    widthDp = 360,
    heightDp = 860,
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
)
internal annotation class LightDarkPreviews

/** Stany do podglądów budowane tą samą funkcją co w ViewModelu, na danych z atrapy repozytorium. */
private object PreviewStates {
    private val range = ServoConfig.angleRange

    private fun build(repository: FakeServoRepository, local: ServoLocalState): ServoUiState =
        buildServoUiState(
            availability = repository.availability.value,
            scan = repository.scanState.value,
            connection = repository.connectionState.value,
            local = local,
            range = range,
            commandIntervalMs = ServoConfig.COMMAND_INTERVAL_MS,
        )

    fun disconnected(): ServoUiState =
        build(FakeServoRepository(), ServoLocalState(targetAngle = range.center))

    fun connecting(): ServoUiState {
        val repository = FakeServoRepository()
        repository.connect(SampleDevices.servo)
        return build(repository, ServoLocalState(targetAngle = range.center))
    }

    fun readyDelivered(): ServoUiState {
        val repository = FakeServoRepository()
        val session = repository.simulateReady(firmwareTarget = FirmwareTarget.Angle(90))
        return build(
            repository,
            ServoLocalState(
                targetAngle = 124,
                command = CommandStatus.Delivered(124, acknowledged = true),
                lastDeliveredAngle = 124,
                activeSession = session,
            ),
        )
    }

    fun readyNothingSent(): ServoUiState {
        val repository = FakeServoRepository()
        val session = repository.simulateReady(firmwareTarget = FirmwareTarget.NoCommandSinceBoot)
        return build(repository, ServoLocalState(targetAngle = range.center, activeSession = session))
    }

    fun linkLost(): ServoUiState {
        val repository = FakeServoRepository()
        repository.simulateReady()
        repository.simulateDisconnected(DisconnectReason.LinkLost(gattStatus = 8))
        return build(repository, ServoLocalState(targetAngle = 124))
    }

    fun pickerScanning(): DevicePickerUiState {
        val repository = FakeServoRepository()
        repository.startScan(showAllDevices = false)
        repository.simulateScan(listOf(SampleDevices.servo, SampleDevices.servoBench), ScanPhase.Scanning)
        return build(repository, ServoLocalState(targetAngle = range.center, pickerVisible = true)).picker
    }

    fun pickerConnecting(): DevicePickerUiState {
        val repository = FakeServoRepository()
        repository.startScan(showAllDevices = false)
        repository.simulateScan(listOf(SampleDevices.servo, SampleDevices.servoBench), ScanPhase.Finished)
        repository.connect(SampleDevices.servo)
        return build(repository, ServoLocalState(targetAngle = range.center, pickerVisible = true)).picker
    }

    fun pickerIncompatible(): DevicePickerUiState {
        val repository = FakeServoRepository()
        repository.startScan(showAllDevices = true)
        repository.simulateScan(listOf(SampleDevices.servo, SampleDevices.unrelated), ScanPhase.Finished)
        repository.connect(SampleDevices.unrelated)
        repository.simulateDisconnected(
            DisconnectReason.Incompatible(IncompatibilityProblem.MissingService),
        )
        return build(
            repository,
            ServoLocalState(
                targetAngle = range.center,
                pickerVisible = true,
                showAllDevices = true,
                pickerConnectAddress = SampleDevices.unrelated.address,
            ),
        ).picker
    }

    fun pickerEmpty(): DevicePickerUiState {
        val repository = FakeServoRepository()
        repository.simulateScan(emptyList(), ScanPhase.Finished)
        return build(repository, ServoLocalState(targetAngle = range.center, pickerVisible = true)).picker
    }

    fun pickerPermission(): DevicePickerUiState {
        val repository = FakeServoRepository(availability = BluetoothAvailability.PermissionsMissing)
        return build(repository, ServoLocalState(targetAngle = range.center, pickerVisible = true)).picker
    }
}

@Composable
private fun ScreenPreview(state: ServoUiState) {
    ServoTheme {
        ServoScreen(
            state = state.copy(picker = state.picker.copy(visible = false)),
            onAction = {},
            onRequestPermissions = {},
            onEnableBluetooth = {},
            onOpenAppSettings = {},
        )
    }
}

@Composable
private fun PickerPreview(state: DevicePickerUiState) {
    ServoTheme {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            DevicePickerContent(
                state = state,
                onAction = {},
                onRequestPermissions = {},
                onEnableBluetooth = {},
                onOpenAppSettings = {},
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

@LightDarkPreviews
@Composable
private fun DisconnectedPreview() = ScreenPreview(PreviewStates.disconnected())

@LightDarkPreviews
@Composable
private fun ReadyPreview() = ScreenPreview(PreviewStates.readyDelivered())

@Preview(name = "Gotowe – nic nie wysłano", showBackground = true, widthDp = 360, heightDp = 860)
@Composable
private fun ReadyNothingSentPreview() = ScreenPreview(PreviewStates.readyNothingSent())

@Preview(name = "Łączenie", showBackground = true, widthDp = 360, heightDp = 860)
@Composable
private fun ConnectingPreview() = ScreenPreview(PreviewStates.connecting())

@LightDarkPreviews
@Composable
private fun LinkLostPreview() = ScreenPreview(PreviewStates.linkLost())

@Preview(name = "Arkusz – skanowanie", showBackground = true, widthDp = 360)
@Preview(
    name = "Arkusz – skanowanie, ciemny",
    showBackground = true,
    widthDp = 360,
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
)
@Composable
private fun PickerScanningPreview() = PickerPreview(PreviewStates.pickerScanning())

@Preview(name = "Arkusz – łączenie", showBackground = true, widthDp = 360)
@Composable
private fun PickerConnectingPreview() = PickerPreview(PreviewStates.pickerConnecting())

@Preview(name = "Arkusz – niezgodne urządzenie", showBackground = true, widthDp = 360)
@Composable
private fun PickerIncompatiblePreview() = PickerPreview(PreviewStates.pickerIncompatible())

@Preview(name = "Arkusz – brak wyników", showBackground = true, widthDp = 360)
@Composable
private fun PickerEmptyPreview() = PickerPreview(PreviewStates.pickerEmpty())

@Preview(name = "Arkusz – brak uprawnień", showBackground = true, widthDp = 360)
@Composable
private fun PickerPermissionPreview() = PickerPreview(PreviewStates.pickerPermission())
