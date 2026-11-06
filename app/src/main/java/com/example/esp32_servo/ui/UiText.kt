package com.example.esp32_servo.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.example.esp32_servo.R
import com.example.esp32_servo.data.BleDeviceInfo
import com.example.esp32_servo.data.DisconnectReason
import com.example.esp32_servo.data.IncompatibilityProblem

/** Kody HCI przekazywane przez Androida jako status rozłączenia. */
private const val HCI_CONNECTION_TIMEOUT = 0x08
private const val HCI_REMOTE_USER_TERMINATED = 0x13

@Composable
internal fun statusText(summary: StatusSummary): String = stringResource(
    when (summary) {
        StatusSummary.Unsupported -> R.string.status_unsupported
        StatusSummary.PermissionsMissing -> R.string.status_permissions_missing
        StatusSummary.BluetoothOff -> R.string.status_bluetooth_off
        StatusSummary.Disconnected -> R.string.status_disconnected
        StatusSummary.Scanning -> R.string.status_scanning
        StatusSummary.Connecting -> R.string.status_connecting
        StatusSummary.DiscoveringServices -> R.string.status_discovering
        StatusSummary.Ready -> R.string.status_ready
        StatusSummary.Error -> R.string.status_error
    },
)

@Composable
internal fun disconnectTitle(reason: DisconnectReason): String = stringResource(
    when (reason) {
        DisconnectReason.UserRequested,
        DisconnectReason.AppInBackground,
        DisconnectReason.BluetoothOff,
        DisconnectReason.PermissionsMissing,
        -> R.string.notice_disconnected_title

        is DisconnectReason.LinkLost,
        DisconnectReason.WriteTimeout,
        -> R.string.notice_link_lost_title

        is DisconnectReason.Incompatible -> R.string.notice_incompatible_title

        DisconnectReason.ConnectTimeout,
        DisconnectReason.DiscoveryTimeout,
        DisconnectReason.StatusReadTimeout,
        is DisconnectReason.ConnectFailed,
        is DisconnectReason.ServiceDiscoveryFailed,
        -> R.string.notice_connect_failed_title
    },
)

@Composable
internal fun disconnectReasonText(reason: DisconnectReason, backgroundDelaySeconds: Int): String =
    when (reason) {
        DisconnectReason.UserRequested -> stringResource(R.string.reason_user)
        DisconnectReason.AppInBackground -> stringResource(R.string.reason_background, backgroundDelaySeconds)
        DisconnectReason.BluetoothOff -> stringResource(R.string.reason_bluetooth_off)
        DisconnectReason.PermissionsMissing -> stringResource(R.string.reason_permissions)
        DisconnectReason.ConnectTimeout -> stringResource(R.string.reason_connect_timeout)
        DisconnectReason.DiscoveryTimeout -> stringResource(R.string.reason_discovery_timeout)
        DisconnectReason.StatusReadTimeout -> stringResource(R.string.reason_status_read_timeout)
        DisconnectReason.WriteTimeout -> stringResource(R.string.reason_write_timeout)
        is DisconnectReason.ConnectFailed -> stringResource(R.string.reason_connect_failed, reason.gattStatus)
        is DisconnectReason.LinkLost -> when (reason.gattStatus) {
            HCI_REMOTE_USER_TERMINATED -> stringResource(R.string.reason_link_lost_remote)
            HCI_CONNECTION_TIMEOUT -> stringResource(R.string.reason_link_lost_timeout)
            else -> stringResource(R.string.reason_link_lost, reason.gattStatus)
        }

        is DisconnectReason.ServiceDiscoveryFailed ->
            stringResource(R.string.reason_discovery_failed, reason.gattStatus)

        is DisconnectReason.Incompatible -> stringResource(
            when (reason.problem) {
                IncompatibilityProblem.MissingService -> R.string.reason_missing_service
                IncompatibilityProblem.MissingAngleCharacteristic -> R.string.reason_missing_characteristic
                IncompatibilityProblem.AngleCharacteristicNotWritable -> R.string.reason_not_writable
            },
        )
    }

/** Kąt do odczytu przez czytnik ekranu, z poprawną odmianą: "1 stopień", "2 stopnie", "5 stopni". */
@Composable
internal fun spokenDegrees(degrees: Int): String =
    pluralStringResource(R.plurals.degrees_spoken, degrees, degrees)

@Composable
internal fun deviceDisplayName(device: BleDeviceInfo): String =
    device.name ?: stringResource(R.string.device_unnamed)

/** Znak minus typograficzny zamiast dywizu, np. "−58". */
internal fun Int.withTypographicMinus(): String = toString().replace('-', '−')
