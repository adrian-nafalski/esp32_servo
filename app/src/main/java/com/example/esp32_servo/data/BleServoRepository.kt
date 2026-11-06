package com.example.esp32_servo.data

import com.example.esp32_servo.ble.BleManager
import com.example.esp32_servo.ble.ServoProtocol
import com.example.esp32_servo.servo.AngleRange
import com.example.esp32_servo.servo.ServoConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Produkcyjna implementacja: tłumaczy kąt na ładunek protokołu i deleguje do [BleManager].
 * Odpowiada też za politykę pracy w tle (połączenie służy tylko aplikacji na pierwszym planie).
 */
class BleServoRepository(
    private val ble: BleManager,
    private val scope: CoroutineScope,
    private val range: AngleRange = ServoConfig.angleRange,
    private val backgroundDisconnectDelayMs: Long = ServoConfig.BACKGROUND_DISCONNECT_DELAY_S * 1_000L,
) : ServoRepository {

    override val availability: StateFlow<BluetoothAvailability> get() = ble.availability
    override val scanState: StateFlow<ScanState> get() = ble.scanState
    override val connectionState: StateFlow<ConnectionState> get() = ble.connectionState

    private var backgroundDisconnectJob: Job? = null

    override fun refreshAvailability() = ble.refreshAvailability()

    override fun startScan(showAllDevices: Boolean) = ble.startScan(showAllDevices)

    override fun stopScan(clearResults: Boolean) = ble.stopScan(clearResults)

    override fun connect(device: DiscoveredDevice) = ble.connect(device.address, device.name)

    override fun disconnect() = ble.disconnect(DisconnectReason.UserRequested)

    override suspend fun sendTargetAngle(session: Long, angle: Int): SendResult {
        // Druga linia obrony: ViewModel już przycina kąt do zakresu.
        if (angle !in range) return SendResult.InvalidAngle
        return ble.writeTargetAngle(session, ServoProtocol.encodeTargetAngle(angle))
    }

    /**
     * Wywoływane z ProcessLifecycleOwner na wątku głównym. ON_STOP przychodzi z opóźnieniem
     * ok. 700 ms i nie występuje przy zmianie konfiguracji, więc obrót ekranu nie rozłącza.
     *
     * Tło: natychmiast kończymy skanowanie, a po [backgroundDisconnectDelayMs] zamykamy
     * połączenie (zwalnia ESP32 dla innych telefonów i oszczędza baterię).
     * Powrót: tylko odświeżenie stanu – bez automatycznego łączenia i bez wysyłania komend.
     */
    fun onAppForegroundChanged(inForeground: Boolean) {
        backgroundDisconnectJob?.cancel()
        backgroundDisconnectJob = null
        if (inForeground) {
            ble.refreshAvailability()
            return
        }
        ble.stopScan(clearResults = false)
        backgroundDisconnectJob = scope.launch {
            delay(backgroundDisconnectDelayMs)
            ble.disconnect(DisconnectReason.AppInBackground)
        }
    }
}
