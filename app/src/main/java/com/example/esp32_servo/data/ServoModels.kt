package com.example.esp32_servo.data

/** Czy telefon w ogóle może teraz skanować i łączyć się przez BLE. */
enum class BluetoothAvailability {
    /** Brak adaptera albo brak obsługi Bluetooth Low Energy. */
    Unsupported,

    /** Brak BLUETOOTH_SCAN lub BLUETOOTH_CONNECT. */
    PermissionsMissing,

    /** Adapter wyłączony (także w trakcie włączania lub wyłączania). */
    Disabled,

    Ready,
}

data class BleDeviceInfo(
    val address: String,
    val name: String?,
)

data class DiscoveredDevice(
    val address: String,
    val name: String?,
    val rssi: Int?,
    /** Czy pakiet ogłoszeniowy zawierał UUID usługi Servo. To wskazówka, nie potwierdzenie. */
    val advertisesServoService: Boolean,
) {
    val info: BleDeviceInfo get() = BleDeviceInfo(address, name)
}

enum class ScanPhase { Idle, Scanning, Finished, Failed }

enum class ScanError {
    /** Własna ochrona przed limitem Androida (5 startów skanowania w 30 s). */
    TooFrequent,
    ScannerUnavailable,
    Failed,
}

data class ScanState(
    val phase: ScanPhase = ScanPhase.Idle,
    val devices: List<DiscoveredDevice> = emptyList(),
    val showAllDevices: Boolean = false,
    val error: ScanError? = null,
    val errorCode: Int? = null,
)

/**
 * Stan łącza z ESP32. Samo połączenie GATT NIE oznacza gotowości:
 * dopiero [Ready] gwarantuje, że usługa i zapisywalna charakterystyka kąta istnieją.
 */
sealed interface ConnectionState {
    val device: BleDeviceInfo?

    data class Disconnected(
        override val device: BleDeviceInfo? = null,
        /** null – nie było jeszcze żadnej próby połączenia. */
        val reason: DisconnectReason? = null,
    ) : ConnectionState

    data class Connecting(override val device: BleDeviceInfo) : ConnectionState

    data class DiscoveringServices(override val device: BleDeviceInfo) : ConnectionState

    data class Ready(
        override val device: BleDeviceInfo,
        /** Rośnie przy każdym połączeniu; komenda z poprzedniej sesji jest odrzucana. */
        val session: Long,
        val link: LinkDetails,
        val firmwareTarget: FirmwareTarget,
    ) : ConnectionState
}

data class LinkDetails(
    val writeType: AngleWriteType,
    val statusCharacteristicAvailable: Boolean,
)

enum class AngleWriteType {
    /** Write Request: ESP32 odsyła potwierdzenie warstwy ATT. */
    WithResponse,

    /** Write Command: brak potwierdzenia od ESP32. */
    WithoutResponse,
}

/** Co firmware zgłosił w opcjonalnej charakterystyce statusu tuż po połączeniu. */
sealed interface FirmwareTarget {
    data object NotSupported : FirmwareTarget
    data object ReadFailed : FirmwareTarget
    data object NoCommandSinceBoot : FirmwareTarget

    /** Ostatni kąt przyjęty przez firmware. Nie jest to zmierzone położenie wału. */
    data class Angle(val degrees: Int) : FirmwareTarget
}

enum class IncompatibilityProblem { MissingService, MissingAngleCharacteristic, AngleCharacteristicNotWritable }

sealed interface DisconnectReason {
    data object UserRequested : DisconnectReason
    data object AppInBackground : DisconnectReason
    data object BluetoothOff : DisconnectReason
    data object PermissionsMissing : DisconnectReason
    data object ConnectTimeout : DisconnectReason
    data object DiscoveryTimeout : DisconnectReason
    data object StatusReadTimeout : DisconnectReason
    data object WriteTimeout : DisconnectReason
    data class ConnectFailed(val gattStatus: Int) : DisconnectReason
    data class LinkLost(val gattStatus: Int) : DisconnectReason
    data class ServiceDiscoveryFailed(val gattStatus: Int) : DisconnectReason
    data class Incompatible(val problem: IncompatibilityProblem) : DisconnectReason
}

/** Rozłączenia zamierzone przez użytkownika lub politykę aplikacji nie są błędami. */
val DisconnectReason.isError: Boolean
    get() = this != DisconnectReason.UserRequested && this != DisconnectReason.AppInBackground

/**
 * Wynik próby wysłania kąta. Rozróżnia, CO faktycznie wiadomo:
 * - [Acknowledged] – stos BLE ESP32 potwierdził zapis (ATT Write Response).
 *   To potwierdzenie transportu, a nie walidacji przez firmware ani ruchu serwa.
 * - [Sent] – zapis bez potwierdzenia przekazany do stosu BLE telefonu.
 */
sealed interface SendResult {
    data object Acknowledged : SendResult
    data object Sent : SendResult
    data object NotReady : SendResult
    data object InvalidAngle : SendResult
    data object Timeout : SendResult

    /** Stos BLE telefonu nie przyjął zlecenia zapisu (np. zajęty). */
    data class Rejected(val code: Int) : SendResult

    /** ESP32 odpowiedział błędem ATT. */
    data class Failed(val gattStatus: Int) : SendResult
}

val SendResult.isDelivered: Boolean
    get() = this == SendResult.Acknowledged || this == SendResult.Sent
