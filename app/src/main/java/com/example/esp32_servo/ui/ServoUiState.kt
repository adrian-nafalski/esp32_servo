package com.example.esp32_servo.ui

import androidx.compose.runtime.Immutable
import com.example.esp32_servo.data.BleDeviceInfo
import com.example.esp32_servo.data.BluetoothAvailability
import com.example.esp32_servo.data.ConnectionState
import com.example.esp32_servo.data.DisconnectReason
import com.example.esp32_servo.data.DiscoveredDevice
import com.example.esp32_servo.data.ScanPhase
import com.example.esp32_servo.data.ScanState
import com.example.esp32_servo.data.SendResult
import com.example.esp32_servo.data.isError
import com.example.esp32_servo.servo.AnglePreset
import com.example.esp32_servo.servo.AngleRange
import com.example.esp32_servo.servo.ServoConfig

/** Zwięzły stan pokazywany w górnym pasku. */
enum class StatusSummary {
    Unsupported,
    PermissionsMissing,
    BluetoothOff,
    Disconnected,
    Scanning,
    Connecting,
    DiscoveringServices,
    Ready,
    Error,
}

enum class StatusTone { Neutral, Busy, Positive, Negative }

val StatusSummary.tone: StatusTone
    get() = when (this) {
        StatusSummary.Ready -> StatusTone.Positive
        StatusSummary.Scanning, StatusSummary.Connecting, StatusSummary.DiscoveringServices -> StatusTone.Busy
        StatusSummary.Error, StatusSummary.Unsupported -> StatusTone.Negative
        StatusSummary.PermissionsMissing, StatusSummary.BluetoothOff, StatusSummary.Disconnected -> StatusTone.Neutral
    }

/**
 * Co wiadomo o komendzie kąta. Żaden wariant nie oznacza zmierzonego położenia wału –
 * aplikacja nie ma sprzężenia zwrotnego.
 */
sealed interface CommandStatus {
    /** Brak gotowego połączenia. */
    data object Unavailable : CommandStatus

    /** Połączono, ale w tej sesji nic nie wysłano, a firmware nie zgłosił ostatniej komendy. */
    data object NothingSent : CommandStatus

    /** Firmware zgłosił po połączeniu ostatnio przyjęty kąt; suwak przejął tę wartość. */
    data class ReportedByDevice(val angle: Int) : CommandStatus

    data class Sending(val angle: Int) : CommandStatus

    /** [acknowledged] – czy ESP32 potwierdził zapis na poziomie BLE (nie oznacza ruchu serwa). */
    data class Delivered(val angle: Int, val acknowledged: Boolean) : CommandStatus

    data class Failed(val angle: Int, val result: SendResult) : CommandStatus
}

@Immutable
data class ControlUiState(
    val enabled: Boolean,
    val range: AngleRange,
    /** Lokalna wartość do zadania (suwak). To nie jest odczyt położenia. */
    val targetAngle: Int,
    val command: CommandStatus,
) {
    val canDecrement: Boolean get() = enabled && targetAngle > range.min
    val canIncrement: Boolean get() = enabled && targetAngle < range.max
    fun presetAngle(preset: AnglePreset): Int = range.angleOf(preset)
}

@Immutable
data class DeviceUiState(
    val device: BleDeviceInfo,
    /** Connecting, DiscoveringServices albo Ready. */
    val connection: ConnectionState,
    val range: AngleRange,
    val commandIntervalMs: Long,
)

@Immutable
data class DevicePickerUiState(
    val visible: Boolean,
    val availability: BluetoothAvailability,
    val permissionPermanentlyDenied: Boolean,
    val scan: ScanState,
    val scanDurationSeconds: Int,
    /** Adres urządzenia, z którym trwa łączenie – pozostałe wiersze są wtedy nieaktywne. */
    val connectingAddress: String?,
    /** Błąd ostatniej próby połączenia z tego arkusza. */
    val connectionError: DisconnectReason?,
)

@Immutable
data class ServoUiState(
    val summary: StatusSummary,
    val availability: BluetoothAvailability,
    val connection: ConnectionState,
    val control: ControlUiState,
    val device: DeviceUiState?,
    val picker: DevicePickerUiState,
    val backgroundDisconnectDelaySeconds: Int,
)

/** Zdarzenia z UI przekazywane do ViewModelu (jednokierunkowy przepływ danych). */
sealed interface ServoAction {
    data object Connect : ServoAction
    data object Disconnect : ServoAction
    data object DismissPicker : ServoAction
    data object Rescan : ServoAction
    data object StopScan : ServoAction
    data class ShowAllDevices(val showAll: Boolean) : ServoAction
    data class SelectDevice(val device: DiscoveredDevice) : ServoAction
    data class SliderChange(val value: Float) : ServoAction
    data object SliderChangeFinished : ServoAction
    data class Step(val delta: Int) : ServoAction
    data class Preset(val preset: AnglePreset) : ServoAction
    data object RetrySend : ServoAction
}

/** Stan należący wyłącznie do ViewModelu (nie pochodzi z repozytorium). */
internal data class ServoLocalState(
    val targetAngle: Int,
    val command: CommandStatus = CommandStatus.NothingSent,
    val lastDeliveredAngle: Int? = null,
    /** Sesja, dla której ViewModel przygotował sterowanie; null – sterowanie zablokowane. */
    val activeSession: Long? = null,
    val pickerVisible: Boolean = false,
    val showAllDevices: Boolean = false,
    val permissionPermanentlyDenied: Boolean = false,
    /** Urządzenie wybrane w bieżąco otwartym arkuszu (do pokazania błędu tej próby). */
    val pickerConnectAddress: String? = null,
)

/** Czysta funkcja mapująca – używana przez ViewModel i podglądy Compose. */
internal fun buildServoUiState(
    availability: BluetoothAvailability,
    scan: ScanState,
    connection: ConnectionState,
    local: ServoLocalState,
    range: AngleRange,
    commandIntervalMs: Long,
): ServoUiState {
    val ready = connection as? ConnectionState.Ready
    val controlsEnabled = ready != null && ready.session == local.activeSession

    val summary = when {
        connection is ConnectionState.Ready -> StatusSummary.Ready
        connection is ConnectionState.DiscoveringServices -> StatusSummary.DiscoveringServices
        connection is ConnectionState.Connecting -> StatusSummary.Connecting
        availability == BluetoothAvailability.Unsupported -> StatusSummary.Unsupported
        availability == BluetoothAvailability.PermissionsMissing -> StatusSummary.PermissionsMissing
        availability == BluetoothAvailability.Disabled -> StatusSummary.BluetoothOff
        scan.phase == ScanPhase.Scanning -> StatusSummary.Scanning
        connection is ConnectionState.Disconnected && connection.reason?.isError == true -> StatusSummary.Error
        else -> StatusSummary.Disconnected
    }

    val device = when (connection) {
        is ConnectionState.Connecting -> DeviceUiState(connection.device, connection, range, commandIntervalMs)
        is ConnectionState.DiscoveringServices -> DeviceUiState(connection.device, connection, range, commandIntervalMs)
        is ConnectionState.Ready -> DeviceUiState(connection.device, connection, range, commandIntervalMs)
        is ConnectionState.Disconnected -> null
    }

    val connectingAddress = when (connection) {
        is ConnectionState.Connecting -> connection.device.address
        is ConnectionState.DiscoveringServices -> connection.device.address
        else -> null
    }
    val connectionError = (connection as? ConnectionState.Disconnected)
        ?.takeIf { it.device?.address != null && it.device.address == local.pickerConnectAddress }
        ?.reason
        ?.takeIf { it.isError }

    return ServoUiState(
        summary = summary,
        availability = availability,
        connection = connection,
        control = ControlUiState(
            enabled = controlsEnabled,
            range = range,
            targetAngle = local.targetAngle,
            command = if (controlsEnabled) local.command else CommandStatus.Unavailable,
        ),
        device = device,
        picker = DevicePickerUiState(
            visible = local.pickerVisible,
            availability = availability,
            permissionPermanentlyDenied = local.permissionPermanentlyDenied,
            scan = scan,
            scanDurationSeconds = ServoConfig.SCAN_DURATION_S,
            connectingAddress = connectingAddress,
            connectionError = connectionError,
        ),
        backgroundDisconnectDelaySeconds = ServoConfig.BACKGROUND_DISCONNECT_DELAY_S,
    )
}
