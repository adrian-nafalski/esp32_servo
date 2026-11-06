package com.example.esp32_servo.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import android.os.SystemClock
import com.example.esp32_servo.data.AngleWriteType
import com.example.esp32_servo.data.BleDeviceInfo
import com.example.esp32_servo.data.BluetoothAvailability
import com.example.esp32_servo.data.ConnectionState
import com.example.esp32_servo.data.DisconnectReason
import com.example.esp32_servo.data.DiscoveredDevice
import com.example.esp32_servo.data.FirmwareTarget
import com.example.esp32_servo.data.IncompatibilityProblem
import com.example.esp32_servo.data.LinkDetails
import com.example.esp32_servo.data.ScanError
import com.example.esp32_servo.data.ScanPhase
import com.example.esp32_servo.data.ScanState
import com.example.esp32_servo.data.SendResult
import com.example.esp32_servo.servo.ServoConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Natywna obsługa BLE: telefon jest BLE Central / GATT Client, ESP32 – Peripheral / GATT Server.
 *
 * Model współbieżności:
 * - każda zmiana stanu wykonuje się na [bleDispatcher] o równoległości 1, więc pola sesji
 *   i skanowania nie wymagają dodatkowej synchronizacji,
 * - callbacki Androida (wątki bindera) jedynie przekazują zdarzenia na ten dispatcher,
 * - operacje GATT (odczyt, zapis) są dodatkowo serializowane [gattMutex] – Android
 *   obsługuje jedną oczekującą operację na połączenie,
 * - każda sesja GATT ma własny callback; zdarzenia z zamkniętej sesji są ignorowane.
 *
 * Uprawnienia są sprawdzane w [updateAvailability] przed każdą publiczną operacją,
 * dlatego klasa ma adnotację @SuppressLint("MissingPermission").
 */
@SuppressLint("MissingPermission")
class BleManager(
    private val context: Context,
    parentScope: CoroutineScope,
) {
    private val bleDispatcher = Dispatchers.Default.limitedParallelism(1)
    private val scope = CoroutineScope(
        parentScope.coroutineContext + SupervisorJob(parentScope.coroutineContext[Job]) + bleDispatcher,
    )

    private val adapter: BluetoothAdapter? =
        context.getSystemService(BluetoothManager::class.java)?.adapter
    private val bleSupported: Boolean =
        adapter != null && context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)

    private val _availability = MutableStateFlow(computeAvailability())
    val availability: StateFlow<BluetoothAvailability> = _availability.asStateFlow()

    private val _scanState = MutableStateFlow(ScanState())
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected())
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    // ---- Stan używany wyłącznie na bleDispatcher ----
    private var activeScan: DeviceScanCallback? = null
    private var scanTimeoutJob: Job? = null
    private val scanStartTimes = ArrayDeque<Long>()
    private val scanResults = LinkedHashMap<String, DiscoveredDevice>()
    private var session: GattSession? = null
    private var lastSessionId = 0L
    private val gattMutex = Mutex()

    private val adapterStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                scope.launch { updateAvailability() }
            }
        }
    }

    init {
        // ACTION_STATE_CHANGED to chroniona transmisja systemowa: nie wymaga flagi eksportu.
        // Odbiornik żyje tyle co proces (BleManager jest singletonem w AppContainer).
        context.registerReceiver(
            adapterStateReceiver,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
        )
    }

    // ======================================================================================
    // API publiczne – wszystkie wywołania są asynchroniczne i bezpieczne z wątku głównego.
    // ======================================================================================

    fun refreshAvailability() {
        scope.launch { updateAvailability() }
    }

    fun startScan(showAllDevices: Boolean) {
        scope.launch { startScanInternal(showAllDevices) }
    }

    fun stopScan(clearResults: Boolean) {
        scope.launch {
            stopScanInternal(finalPhase = ScanPhase.Finished)
            if (clearResults) {
                scanResults.clear()
                _scanState.update { ScanState(showAllDevices = it.showAllDevices) }
            }
        }
    }

    fun connect(address: String, name: String?) {
        scope.launch { connectInternal(address, name) }
    }

    fun disconnect(reason: DisconnectReason) {
        scope.launch { session?.let { closeSession(it, reason) } }
    }

    /**
     * Zapisuje gotowy ładunek do charakterystyki kąta w ramach sesji [sessionId].
     * Zwraca dopiero po potwierdzeniu, błędzie albo przekroczeniu [WRITE_TIMEOUT_MS].
     */
    suspend fun writeTargetAngle(sessionId: Long, payload: ByteArray): SendResult =
        withContext(bleDispatcher) {
            val owner = session
            val ready = _connectionState.value as? ConnectionState.Ready
            val gatt = owner?.gatt
            val characteristic = owner?.angleCharacteristic
            if (owner == null || ready == null || gatt == null || characteristic == null ||
                owner.id != sessionId || ready.session != sessionId
            ) {
                return@withContext SendResult.NotReady
            }

            val outcome = runGattOperation(owner, WRITE_TIMEOUT_MS) {
                writeCharacteristicCompat(gatt, characteristic, payload, owner.writeType)
            }
            when (outcome) {
                is OperationOutcome.Completed -> when {
                    outcome.status != BluetoothGatt.GATT_SUCCESS -> SendResult.Failed(outcome.status)
                    owner.writeType == BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT -> SendResult.Acknowledged
                    else -> SendResult.Sent
                }

                is OperationOutcome.NotStarted -> SendResult.Rejected(outcome.code)
                OperationOutcome.TimedOut -> {
                    // Brak odpowiedzi stosu zwykle oznacza zerwane łącze. Zamykamy sesję,
                    // zamiast zostawiać zablokowaną kolejkę operacji GATT.
                    if (session === owner) closeSession(owner, DisconnectReason.WriteTimeout)
                    SendResult.Timeout
                }

                OperationOutcome.Cancelled -> SendResult.NotReady
            }
        }

    // ======================================================================================
    // Dostępność
    // ======================================================================================

    private fun computeAvailability(): BluetoothAvailability = when {
        !bleSupported -> BluetoothAvailability.Unsupported
        !BlePermissions.granted(context) -> BluetoothAvailability.PermissionsMissing
        adapter?.isEnabled != true -> BluetoothAvailability.Disabled
        else -> BluetoothAvailability.Ready
    }

    private fun updateAvailability() {
        val current = computeAvailability()
        _availability.value = current
        if (current == BluetoothAvailability.Ready) return

        stopScanInternal(finalPhase = null)
        scanResults.clear()
        _scanState.update { ScanState(showAllDevices = it.showAllDevices) }
        session?.let {
            val reason = if (current == BluetoothAvailability.PermissionsMissing) {
                DisconnectReason.PermissionsMissing
            } else {
                DisconnectReason.BluetoothOff
            }
            closeSession(it, reason)
        }
    }

    // ======================================================================================
    // Skanowanie
    // ======================================================================================

    private fun startScanInternal(showAllDevices: Boolean) {
        updateAvailability()
        if (_availability.value != BluetoothAvailability.Ready) return
        if (session != null) return // podczas łączenia lub połączenia nie skanujemy
        stopScanInternal(finalPhase = null)

        // Android po cichu blokuje aplikację, która uruchomi skanowanie więcej niż 5 razy
        // w ciągu 30 s (brak wyników, brak błędu). Lepiej powiedzieć to wprost.
        val now = SystemClock.elapsedRealtime()
        while (scanStartTimes.isNotEmpty() && now - scanStartTimes.first() >= SCAN_START_WINDOW_MS) {
            scanStartTimes.removeFirst()
        }
        if (scanStartTimes.size >= MAX_SCAN_STARTS_PER_WINDOW) {
            _scanState.value = ScanState(
                phase = ScanPhase.Failed,
                showAllDevices = showAllDevices,
                error = ScanError.TooFrequent,
            )
            return
        }

        val scanner = adapter?.bluetoothLeScanner
        if (scanner == null) {
            _scanState.value = ScanState(
                phase = ScanPhase.Failed,
                showAllDevices = showAllDevices,
                error = ScanError.ScannerUnavailable,
            )
            return
        }

        val filters = if (showAllDevices) {
            emptyList()
        } else {
            listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(ServoProtocol.SERVICE_UUID)).build())
        }
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY) // krótkie skanowanie na pierwszym planie
            .build()
        val callback = DeviceScanCallback()

        scanResults.clear()
        try {
            scanner.startScan(filters, settings, callback)
        } catch (e: SecurityException) {
            updateAvailability()
            return
        } catch (e: IllegalStateException) {
            // Adapter wyłączył się między sprawdzeniem a startem.
            _scanState.value = ScanState(
                phase = ScanPhase.Failed,
                showAllDevices = showAllDevices,
                error = ScanError.ScannerUnavailable,
            )
            return
        }
        scanStartTimes.addLast(now)
        activeScan = callback
        _scanState.value = ScanState(phase = ScanPhase.Scanning, showAllDevices = showAllDevices)
        scanTimeoutJob = scope.launch {
            delay(ServoConfig.SCAN_DURATION_S * 1_000L)
            scanTimeoutJob = null
            stopScanInternal(finalPhase = ScanPhase.Finished)
        }
    }

    /** Zatrzymuje skanowanie. [finalPhase] zastępuje fazę Scanning (null – bez zmiany stanu). */
    private fun stopScanInternal(finalPhase: ScanPhase?) {
        scanTimeoutJob?.cancel()
        scanTimeoutJob = null
        val callback = activeScan
        activeScan = null
        if (callback != null) {
            runCatching { adapter?.bluetoothLeScanner?.stopScan(callback) }
        }
        if (finalPhase != null && _scanState.value.phase == ScanPhase.Scanning) {
            _scanState.update { it.copy(phase = finalPhase) }
        }
    }

    private fun handleScanResult(callback: DeviceScanCallback, result: ScanResult) {
        if (callback !== activeScan) return
        val address = result.device.address
        val previous = scanResults[address]
        val record = result.scanRecord
        val advertisesService =
            record?.serviceUuids?.any { it.uuid == ServoProtocol.SERVICE_UUID } == true
        val name = record?.deviceName?.takeIf { it.isNotBlank() }
            ?: runCatching { result.device.name }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: previous?.name
        scanResults[address] = DiscoveredDevice(
            address = address,
            name = name,
            rssi = result.rssi,
            advertisesServoService = advertisesService || previous?.advertisesServoService == true,
        )
        // LinkedHashMap zachowuje kolejność wykrycia: lista nie "skacze" przy zmianach RSSI.
        _scanState.update { it.copy(devices = scanResults.values.toList()) }
    }

    private fun handleScanFailed(callback: DeviceScanCallback, errorCode: Int) {
        if (callback !== activeScan) return
        activeScan = null
        scanTimeoutJob?.cancel()
        scanTimeoutJob = null
        _scanState.update {
            it.copy(phase = ScanPhase.Failed, error = ScanError.Failed, errorCode = errorCode)
        }
    }

    private inner class DeviceScanCallback : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            scope.launch { handleScanResult(this@DeviceScanCallback, result) }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            val copy = results.toList()
            scope.launch { copy.forEach { handleScanResult(this@DeviceScanCallback, it) } }
        }

        override fun onScanFailed(errorCode: Int) {
            scope.launch { handleScanFailed(this@DeviceScanCallback, errorCode) }
        }
    }

    // ======================================================================================
    // Połączenie GATT
    // ======================================================================================

    private class GattSession(val id: Long, val device: BleDeviceInfo) {
        var gatt: BluetoothGatt? = null
        var phaseTimeout: Job? = null
        var angleCharacteristic: BluetoothGattCharacteristic? = null
        var writeType: Int = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT

        /** Wynik bieżącej operacji GATT; null oznacza przerwanie przez zamknięcie sesji. */
        var pendingOperation: CompletableDeferred<GattResult?>? = null
    }

    private class GattResult(val status: Int, val value: ByteArray?)

    private sealed interface OperationOutcome {
        class Completed(val status: Int, val value: ByteArray?) : OperationOutcome
        class NotStarted(val code: Int) : OperationOutcome
        data object TimedOut : OperationOutcome
        data object Cancelled : OperationOutcome
    }

    private fun connectInternal(address: String, name: String?) {
        updateAvailability()
        if (_availability.value != BluetoothAvailability.Ready) return
        val bluetoothAdapter = adapter ?: return

        // Skanowanie w trakcie łączenia pogarsza niezawodność – zatrzymujemy je.
        stopScanInternal(finalPhase = ScanPhase.Finished)
        session?.let { closeSession(it, reason = null) }

        val info = BleDeviceInfo(address, name)
        val device: BluetoothDevice = try {
            bluetoothAdapter.getRemoteDevice(address)
        } catch (e: IllegalArgumentException) {
            _connectionState.value = ConnectionState.Disconnected(
                info,
                DisconnectReason.ConnectFailed(GATT_STATUS_UNKNOWN),
            )
            return
        }

        val newSession = GattSession(id = ++lastSessionId, device = info)
        session = newSession
        _connectionState.value = ConnectionState.Connecting(info)

        // autoConnect = false: bezpośrednie, szybkie połączenie z urządzeniem wybranym przez użytkownika.
        // Ten wariant działa na API 31–37. Od API 37 istnieje też connectGatt(BluetoothGattConnectionSettings,
        // Executor, callback); stary jest tam oznaczony jako przestarzały, ale nadal obsługiwany.
        // Nowej ścieżki celowo nie dodano, dopóki nie zostanie sprawdzona na urządzeniu z API 37.
        val gatt = try {
            @Suppress("DEPRECATION")
            device.connectGatt(context, false, SessionCallback(newSession), BluetoothDevice.TRANSPORT_LE)
        } catch (e: SecurityException) {
            null
        }
        if (gatt == null) {
            closeSession(newSession, DisconnectReason.ConnectFailed(GATT_STATUS_UNKNOWN))
            return
        }
        newSession.gatt = gatt
        newSession.phaseTimeout = scope.launch {
            delay(CONNECT_TIMEOUT_MS)
            if (session === newSession) closeSession(newSession, DisconnectReason.ConnectTimeout)
        }
    }

    private fun handleConnectionStateChange(
        owner: GattSession,
        gatt: BluetoothGatt,
        status: Int,
        newState: Int,
    ) {
        if (session !== owner) return // zdarzenie z sesji, która została już zamknięta

        when (newState) {
            BluetoothProfile.STATE_CONNECTED -> {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    closeSession(owner, DisconnectReason.ConnectFailed(status))
                    return
                }
                if (_connectionState.value !is ConnectionState.Connecting) return
                owner.phaseTimeout?.cancel()
                // Połączenie GATT to jeszcze nie gotowość do sterowania – najpierw usługi.
                _connectionState.value = ConnectionState.DiscoveringServices(owner.device)
                owner.phaseTimeout = scope.launch {
                    delay(DISCOVERY_TIMEOUT_MS)
                    if (session === owner) closeSession(owner, DisconnectReason.DiscoveryTimeout)
                }
                if (!gatt.discoverServices()) {
                    closeSession(owner, DisconnectReason.ServiceDiscoveryFailed(GATT_STATUS_UNKNOWN))
                }
            }

            BluetoothProfile.STATE_DISCONNECTED -> {
                val reason = if (_connectionState.value is ConnectionState.Connecting) {
                    DisconnectReason.ConnectFailed(status)
                } else {
                    DisconnectReason.LinkLost(status)
                }
                closeSession(owner, reason)
            }
        }
    }

    private suspend fun handleServicesDiscovered(owner: GattSession, gatt: BluetoothGatt, status: Int) {
        if (session !== owner || _connectionState.value !is ConnectionState.DiscoveringServices) return
        owner.phaseTimeout?.cancel()
        owner.phaseTimeout = null

        if (status != BluetoothGatt.GATT_SUCCESS) {
            return closeSession(owner, DisconnectReason.ServiceDiscoveryFailed(status))
        }

        // Nazwa urządzenia niczego nie potwierdza – zgodność wynika z usługi i charakterystyki.
        val service = gatt.getService(ServoProtocol.SERVICE_UUID)
            ?: return closeSession(owner, incompatible(IncompatibilityProblem.MissingService))
        val angle = service.getCharacteristic(ServoProtocol.TARGET_ANGLE_UUID)
            ?: return closeSession(owner, incompatible(IncompatibilityProblem.MissingAngleCharacteristic))

        val properties = angle.properties
        val writeType = when {
            properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0 ->
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT

            properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0 ->
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE

            else -> return closeSession(
                owner,
                incompatible(IncompatibilityProblem.AngleCharacteristicNotWritable),
            )
        }
        owner.angleCharacteristic = angle
        owner.writeType = writeType

        // Krótszy interwał połączenia skraca opóźnienie komend. Dotyczy tylko pracy na pierwszym planie.
        gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)

        val statusCharacteristic = service.getCharacteristic(ServoProtocol.LAST_ACCEPTED_ANGLE_UUID)
            ?.takeIf { it.properties and BluetoothGattCharacteristic.PROPERTY_READ != 0 }

        val firmwareTarget = if (statusCharacteristic == null) {
            FirmwareTarget.NotSupported
        } else {
            val outcome = runGattOperation(owner, READ_TIMEOUT_MS) {
                if (gatt.readCharacteristic(statusCharacteristic)) {
                    BluetoothStatusCodes.SUCCESS
                } else {
                    BluetoothStatusCodes.ERROR_UNKNOWN
                }
            }
            when (outcome) {
                is OperationOutcome.Completed ->
                    if (outcome.status == BluetoothGatt.GATT_SUCCESS && outcome.value != null) {
                        ServoProtocol.decodeLastAccepted(outcome.value)
                    } else {
                        FirmwareTarget.ReadFailed
                    }

                is OperationOutcome.NotStarted -> FirmwareTarget.ReadFailed
                OperationOutcome.TimedOut -> return closeSession(owner, DisconnectReason.StatusReadTimeout)
                OperationOutcome.Cancelled -> return
            }
        }

        if (session !== owner) return
        _connectionState.value = ConnectionState.Ready(
            device = owner.device,
            session = owner.id,
            link = LinkDetails(
                writeType = if (writeType == BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) {
                    AngleWriteType.WithResponse
                } else {
                    AngleWriteType.WithoutResponse
                },
                statusCharacteristicAvailable = statusCharacteristic != null,
            ),
            firmwareTarget = firmwareTarget,
        )
    }

    private fun incompatible(problem: IncompatibilityProblem) = DisconnectReason.Incompatible(problem)

    /**
     * Wykonuje jedną operację GATT i czeka na jej callback.
     * [start] zwraca kod BluetoothStatusCodes (SUCCESS = operacja zlecona).
     */
    private suspend fun runGattOperation(
        owner: GattSession,
        timeoutMs: Long,
        start: () -> Int,
    ): OperationOutcome = gattMutex.withLock {
        if (session !== owner) return@withLock OperationOutcome.Cancelled

        val pending = CompletableDeferred<GattResult?>()
        owner.pendingOperation = pending
        val startCode = try {
            start()
        } catch (e: SecurityException) {
            BluetoothStatusCodes.ERROR_UNKNOWN
        }
        if (startCode != BluetoothStatusCodes.SUCCESS) {
            owner.pendingOperation = null
            return@withLock OperationOutcome.NotStarted(startCode)
        }

        val result = withTimeoutOrNull(timeoutMs) { pending.await() }
        if (owner.pendingOperation === pending) owner.pendingOperation = null
        when {
            result != null -> OperationOutcome.Completed(result.status, result.value)
            pending.isCompleted -> OperationOutcome.Cancelled // sesję zamknięto w trakcie
            else -> OperationOutcome.TimedOut
        }
    }

    private fun completeOperation(owner: GattSession, result: GattResult) {
        if (session === owner) owner.pendingOperation?.complete(result)
    }

    /**
     * Zamyka sesję: przerywa oczekującą operację, wywołuje disconnect() i close().
     * close() zwalnia klienta GATT w systemie – bez tego po kilkunastu połączeniach
     * Android zaczyna odrzucać nowe (błąd 133).
     */
    private fun closeSession(owner: GattSession, reason: DisconnectReason?) {
        val wasCurrent = session === owner
        owner.phaseTimeout?.cancel()
        owner.phaseTimeout = null
        owner.pendingOperation?.complete(null)
        owner.pendingOperation = null
        if (wasCurrent) session = null
        owner.gatt?.let { gatt ->
            runCatching { gatt.disconnect() }
            runCatching { gatt.close() }
        }
        owner.gatt = null
        owner.angleCharacteristic = null
        if (wasCurrent && reason != null) {
            _connectionState.value = ConnectionState.Disconnected(owner.device, reason)
        }
    }

    private fun writeCharacteristicCompat(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        writeType: Int,
    ): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        // API 33+: wartość przekazywana jawnie, wynik jako kod BluetoothStatusCodes.
        gatt.writeCharacteristic(characteristic, value, writeType)
    } else {
        writeCharacteristicLegacy(gatt, characteristic, value, writeType)
    }

    /** API 31–32: jedyna dostępna wersja zapisu (od API 33 oznaczona jako przestarzała). */
    @Suppress("DEPRECATION")
    private fun writeCharacteristicLegacy(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        writeType: Int,
    ): Int {
        characteristic.writeType = writeType
        characteristic.value = value
        return if (gatt.writeCharacteristic(characteristic)) {
            BluetoothStatusCodes.SUCCESS
        } else {
            BluetoothStatusCodes.ERROR_UNKNOWN
        }
    }

    /** Osobny callback dla każdej sesji – pozwala odrzucić spóźnione zdarzenia starych połączeń. */
    private inner class SessionCallback(private val owner: GattSession) : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            scope.launch { handleConnectionStateChange(owner, gatt, status, newState) }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            scope.launch { handleServicesDiscovered(owner, gatt, status) }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            scope.launch { completeOperation(owner, GattResult(status, null)) }
        }

        // API 33+
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int,
        ) {
            val copy = value.copyOf()
            scope.launch { completeOperation(owner, GattResult(status, copy)) }
        }

        // API 31–32. Od API 33 obsługuje to przeciążenie z parametrem value.
        @Deprecated("Używane tylko na API 31–32")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
            @Suppress("DEPRECATION")
            val copy = characteristic.value?.copyOf()
            scope.launch { completeOperation(owner, GattResult(status, copy)) }
        }
    }

    private companion object {
        const val SCAN_START_WINDOW_MS = 30_000L
        const val MAX_SCAN_STARTS_PER_WINDOW = 5
        const val CONNECT_TIMEOUT_MS = 15_000L
        const val DISCOVERY_TIMEOUT_MS = 10_000L
        const val READ_TIMEOUT_MS = 3_000L
        const val WRITE_TIMEOUT_MS = 2_000L
        const val GATT_STATUS_UNKNOWN = -1
    }
}
