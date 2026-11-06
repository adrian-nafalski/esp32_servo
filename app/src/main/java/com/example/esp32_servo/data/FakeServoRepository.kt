package com.example.esp32_servo.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Atrapa repozytorium do podglądów Compose i testów jednostkowych. Nie dotyka Bluetooth.
 *
 * Wywołania API zachowują się jak u prawdziwego repozytorium "do pierwszego kroku"
 * (np. connect() przechodzi w Connecting), a dalsze przejścia wywołuje się jawnie
 * metodami simulate*(). Dzięki temu testy kontrolują każdy etap.
 */
class FakeServoRepository(
    availability: BluetoothAvailability = BluetoothAvailability.Ready,
    scanState: ScanState = ScanState(),
    connectionState: ConnectionState = ConnectionState.Disconnected(),
    /** Źródło czasu dla [sentCommands]; w testach – zegar wirtualny schedulera. */
    private val clock: () -> Long = { 0L },
) : ServoRepository {

    data class SentCommand(val session: Long, val angle: Int, val atMs: Long)

    private val _availability = MutableStateFlow(availability)
    override val availability: StateFlow<BluetoothAvailability> = _availability.asStateFlow()

    private val _scanState = MutableStateFlow(scanState)
    override val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    private val _connectionState = MutableStateFlow(connectionState)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    /** Komendy, które faktycznie dotarły do "urządzenia" (z aktualnej sesji). */
    val sentCommands = mutableListOf<SentCommand>()
    val connectRequests = mutableListOf<DiscoveredDevice>()
    var scanStartCount = 0
        private set
    var refreshCount = 0
        private set

    /** Symulowany czas trwania zapisu BLE. */
    var sendDurationMs = 0L

    /** Wynik kolejnych zapisów. */
    var sendResult: SendResult = SendResult.Acknowledged

    private var lastSession = (connectionState as? ConnectionState.Ready)?.session ?: 0L

    override fun refreshAvailability() {
        refreshCount++
    }

    override fun startScan(showAllDevices: Boolean) {
        scanStartCount++
        _scanState.value = ScanState(phase = ScanPhase.Scanning, showAllDevices = showAllDevices)
    }

    override fun stopScan(clearResults: Boolean) {
        _scanState.update {
            if (clearResults) {
                ScanState(showAllDevices = it.showAllDevices)
            } else {
                it.copy(phase = if (it.phase == ScanPhase.Scanning) ScanPhase.Finished else it.phase)
            }
        }
    }

    override fun connect(device: DiscoveredDevice) {
        connectRequests += device
        // Jak BleManager: łączenie zawsze kończy trwające skanowanie.
        _scanState.update { if (it.phase == ScanPhase.Scanning) it.copy(phase = ScanPhase.Finished) else it }
        _connectionState.value = ConnectionState.Connecting(device.info)
    }

    override fun disconnect() {
        val device = _connectionState.value.device
        _connectionState.value = ConnectionState.Disconnected(
            device = device,
            reason = if (device != null) DisconnectReason.UserRequested else null,
        )
    }

    override suspend fun sendTargetAngle(session: Long, angle: Int): SendResult {
        if (!isReady(session)) return SendResult.NotReady
        sentCommands += SentCommand(session, angle, clock())
        if (sendDurationMs > 0) delay(sendDurationMs)
        return if (isReady(session)) sendResult else SendResult.NotReady
    }

    private fun isReady(session: Long): Boolean =
        (_connectionState.value as? ConnectionState.Ready)?.session == session

    // ---- Sterowanie atrapą ----

    fun simulateAvailability(availability: BluetoothAvailability) {
        _availability.value = availability
    }

    fun simulateScan(devices: List<DiscoveredDevice>, phase: ScanPhase = ScanPhase.Scanning) {
        _scanState.update { it.copy(phase = phase, devices = devices) }
    }

    fun simulateDiscoveringServices(device: BleDeviceInfo = SampleDevices.servo.info) {
        _connectionState.value = ConnectionState.DiscoveringServices(device)
    }

    /** Symuluje pomyślną walidację usługi; zwraca numer nowej sesji. */
    fun simulateReady(
        device: BleDeviceInfo = SampleDevices.servo.info,
        firmwareTarget: FirmwareTarget = FirmwareTarget.NoCommandSinceBoot,
        writeType: AngleWriteType = AngleWriteType.WithResponse,
    ): Long {
        lastSession++
        _connectionState.value = ConnectionState.Ready(
            device = device,
            session = lastSession,
            link = LinkDetails(writeType = writeType, statusCharacteristicAvailable = true),
            firmwareTarget = firmwareTarget,
        )
        return lastSession
    }

    fun simulateDisconnected(reason: DisconnectReason) {
        _connectionState.value = ConnectionState.Disconnected(_connectionState.value.device, reason)
    }
}

/** Przykładowe urządzenia dla podglądów i testów. */
object SampleDevices {
    val servo = DiscoveredDevice(
        address = "A4:CF:12:6B:3D:90",
        name = "Servo-ESP32",
        rssi = -58,
        advertisesServoService = true,
    )
    val servoBench = DiscoveredDevice(
        address = "3C:71:BF:0A:21:4E",
        name = "Servo-ESP32",
        rssi = -74,
        advertisesServoService = true,
    )
    val unrelated = DiscoveredDevice(
        address = "5E:1A:93:C2:77:08",
        name = null,
        rssi = -86,
        advertisesServoService = false,
    )
}
