package com.example.esp32_servo.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.esp32_servo.ServoApplication
import com.example.esp32_servo.data.BluetoothAvailability
import com.example.esp32_servo.data.ConnectionState
import com.example.esp32_servo.data.DiscoveredDevice
import com.example.esp32_servo.data.FirmwareTarget
import com.example.esp32_servo.data.ScanPhase
import com.example.esp32_servo.data.SendResult
import com.example.esp32_servo.data.ServoRepository
import com.example.esp32_servo.data.isDelivered
import com.example.esp32_servo.servo.AngleCommand
import com.example.esp32_servo.servo.AngleCommandSender
import com.example.esp32_servo.servo.AnglePreset
import com.example.esp32_servo.servo.AngleRange
import com.example.esp32_servo.servo.ServoConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Zasady bezpieczeństwa ruchu:
 * - żadna komenda nie jest wysyłana bez akcji użytkownika (także po połączeniu i ponownym połączeniu),
 * - po zmianie sesji niewysłane komendy są usuwane, a stare nie są odtwarzane,
 * - lokalna wartość suwaka to kąt DO ZADANIA, nigdy odczyt położenia.
 */
class ServoViewModel(
    private val repository: ServoRepository,
    private val range: AngleRange = ServoConfig.angleRange,
    private val commandIntervalMs: Long = ServoConfig.COMMAND_INTERVAL_MS,
) : ViewModel() {

    private val local = MutableStateFlow(ServoLocalState(targetAngle = range.center))

    private val sender = AngleCommandSender(
        scope = viewModelScope,
        minIntervalMs = commandIntervalMs,
        transmit = { command -> repository.sendTargetAngle(command.session, command.angle) },
        onResult = ::onCommandResult,
    )

    val uiState: StateFlow<ServoUiState> = combine(
        repository.availability,
        repository.scanState,
        repository.connectionState,
        local,
    ) { availability, scan, connection, localState ->
        buildServoUiState(availability, scan, connection, localState, range, commandIntervalMs)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = buildServoUiState(
            repository.availability.value,
            repository.scanState.value,
            repository.connectionState.value,
            local.value,
            range,
            commandIntervalMs,
        ),
    )

    init {
        viewModelScope.launch { repository.connectionState.collect(::onConnectionStateChanged) }
        viewModelScope.launch { repository.availability.collect(::onAvailabilityChanged) }
    }

    fun onAction(action: ServoAction) {
        when (action) {
            ServoAction.Connect -> openDevicePicker()
            ServoAction.Disconnect -> repository.disconnect()
            ServoAction.DismissPicker -> dismissDevicePicker()
            ServoAction.Rescan -> {
                local.update { it.copy(pickerConnectAddress = null) }
                repository.startScan(local.value.showAllDevices)
            }

            ServoAction.StopScan -> repository.stopScan(clearResults = false)
            is ServoAction.ShowAllDevices -> {
                local.update { it.copy(showAllDevices = action.showAll, pickerConnectAddress = null) }
                repository.startScan(action.showAll)
            }

            is ServoAction.SelectDevice -> selectDevice(action.device)
            is ServoAction.SliderChange -> requestAngle(action.value.roundToInt(), gestureFinished = false)
            ServoAction.SliderChangeFinished -> requestAngle(local.value.targetAngle, gestureFinished = true)
            is ServoAction.Step -> requestAngle(local.value.targetAngle + action.delta, gestureFinished = true)
            is ServoAction.Preset -> requestAngle(range.angleOf(action.preset), gestureFinished = true)
            ServoAction.RetrySend -> requestAngle(local.value.targetAngle, gestureFinished = true)
        }
    }

    /** Wynik systemowej prośby o uprawnienia (wywoływane z UI). */
    fun onPermissionsResult(allGranted: Boolean, permanentlyDenied: Boolean) {
        local.update { it.copy(permissionPermanentlyDenied = !allGranted && permanentlyDenied) }
        repository.refreshAvailability()
    }

    fun onBluetoothEnableResult() = repository.refreshAvailability()

    fun onReturnedFromSettings() = repository.refreshAvailability()

    // ---- Wybór urządzenia ----

    private fun openDevicePicker() {
        local.update { it.copy(pickerVisible = true, pickerConnectAddress = null) }
        repository.refreshAvailability()
        if (repository.availability.value == BluetoothAvailability.Ready) {
            repository.startScan(local.value.showAllDevices)
        }
        // W przeciwnym razie arkusz pokaże prośbę o uprawnienie albo włączenie Bluetooth.
    }

    private fun dismissDevicePicker() {
        local.update { it.copy(pickerVisible = false, pickerConnectAddress = null) }
        repository.stopScan(clearResults = true)
    }

    private fun selectDevice(device: DiscoveredDevice) {
        val connection = repository.connectionState.value
        if (connection is ConnectionState.Connecting || connection is ConnectionState.DiscoveringServices) return
        local.update { it.copy(pickerConnectAddress = device.address) }
        repository.connect(device)
    }

    private fun onAvailabilityChanged(availability: BluetoothAvailability) {
        if (availability != BluetoothAvailability.PermissionsMissing && local.value.permissionPermanentlyDenied) {
            local.update { it.copy(permissionPermanentlyDenied = false) }
        }
        // Dokończenie akcji użytkownika: arkusz był otwarty, a teraz nadano uprawnienia
        // lub włączono Bluetooth. Nie dotyczy połączenia – to wyłącznie wyszukiwanie.
        val state = local.value
        if (availability == BluetoothAvailability.Ready &&
            state.pickerVisible &&
            repository.scanState.value.phase == ScanPhase.Idle &&
            repository.connectionState.value is ConnectionState.Disconnected
        ) {
            repository.startScan(state.showAllDevices)
        }
    }

    // ---- Sesja połączenia ----

    private fun onConnectionStateChanged(state: ConnectionState) {
        if (state is ConnectionState.Ready) {
            if (state.session == local.value.activeSession) return
            // Nowa sesja: kasujemy wszystko, co niewysłane, i NICZEGO nie wysyłamy.
            sender.clearPending()
            val reported = (state.firmwareTarget as? FirmwareTarget.Angle)
                ?.degrees
                ?.takeIf { it in range }
            local.update {
                it.copy(
                    activeSession = state.session,
                    // Jeśli ESP32 zgłosił ostatnio przyjęty kąt, suwak go przejmuje, aby pierwsze
                    // dotknięcie nie spowodowało skoku. To tylko lokalny stan, nie komenda.
                    targetAngle = reported ?: it.targetAngle,
                    command = reported?.let(CommandStatus::ReportedByDevice) ?: CommandStatus.NothingSent,
                    lastDeliveredAngle = null,
                    pickerVisible = false,
                    pickerConnectAddress = null,
                )
            }
        } else {
            sender.clearPending()
            if (local.value.activeSession != null) {
                local.update {
                    it.copy(activeSession = null, command = CommandStatus.NothingSent, lastDeliveredAngle = null)
                }
            }
        }
    }

    private fun readySession(): Long? {
        val ready = repository.connectionState.value as? ConnectionState.Ready ?: return null
        return ready.session.takeIf { it == local.value.activeSession }
    }

    // ---- Komendy ----

    /**
     * [gestureFinished] = true dla końca przeciągania, przycisków i ponowienia: gwarantuje,
     * że końcowa wartość zostanie wysłana, jeśli nie jest już dostarczona, kolejkowana
     * ani właśnie wysyłana.
     */
    private fun requestAngle(requested: Int, gestureFinished: Boolean) {
        val session = readySession() ?: return
        val angle = range.clamp(requested)
        val command = AngleCommand(session, angle)
        val state = local.value
        if (angle == state.targetAngle) {
            if (!gestureFinished) return
            if (state.lastDeliveredAngle == angle || sender.isQueuedOrInFlight(command)) return
        }

        local.update { it.copy(targetAngle = angle, command = CommandStatus.Sending(angle)) }
        sender.submit(command)
    }

    private fun onCommandResult(command: AngleCommand, result: SendResult) {
        local.update { state ->
            if (command.session != state.activeSession) return@update state
            val delivered = result.isDelivered
            val status = when {
                sender.hasPending -> CommandStatus.Sending(state.targetAngle)
                delivered -> CommandStatus.Delivered(command.angle, acknowledged = result == SendResult.Acknowledged)
                else -> CommandStatus.Failed(command.angle, result)
            }
            state.copy(
                lastDeliveredAngle = if (delivered) command.angle else state.lastDeliveredAngle,
                command = status,
            )
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY] as ServoApplication
                ServoViewModel(application.container.servoRepository)
            }
        }
    }
}
