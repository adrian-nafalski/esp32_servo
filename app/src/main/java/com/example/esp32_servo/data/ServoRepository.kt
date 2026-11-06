package com.example.esp32_servo.data

import kotlinx.coroutines.flow.StateFlow

/**
 * Domenowe API sterowania serwem. ViewModel zna wyłącznie ten interfejs,
 * dzięki czemu testy i podglądy Compose korzystają z [FakeServoRepository].
 */
interface ServoRepository {
    val availability: StateFlow<BluetoothAvailability>
    val scanState: StateFlow<ScanState>
    val connectionState: StateFlow<ConnectionState>

    /** Ponownie sprawdza uprawnienia i stan adaptera, np. po powrocie z ustawień. */
    fun refreshAvailability()

    /** Skanowanie ma własny limit czasu – nie trwa bez końca. */
    fun startScan(showAllDevices: Boolean)

    fun stopScan(clearResults: Boolean = false)

    fun connect(device: DiscoveredDevice)

    /** Rozłącza albo przerywa trwające łączenie. */
    fun disconnect()

    /**
     * Wysyła kąt zadany w ramach podanej sesji [ConnectionState.Ready.session].
     * Komenda z nieaktualnej sesji zwraca [SendResult.NotReady] i nie trafia do urządzenia.
     */
    suspend fun sendTargetAngle(session: Long, angle: Int): SendResult
}
