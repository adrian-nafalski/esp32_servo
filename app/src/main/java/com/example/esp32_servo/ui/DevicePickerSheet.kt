package com.example.esp32_servo.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.esp32_servo.R
import com.example.esp32_servo.data.BluetoothAvailability
import com.example.esp32_servo.data.DiscoveredDevice
import com.example.esp32_servo.data.ScanError
import com.example.esp32_servo.data.ScanPhase
import com.example.esp32_servo.servo.ServoConfig
import com.example.esp32_servo.ui.theme.TechnicalTextStyle

/**
 * Modalny arkusz wyboru ESP32. Gdy ViewModel ukryje arkusz (np. po połączeniu),
 * najpierw odtwarzamy animację chowania, a dopiero potem usuwamy go z kompozycji.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DevicePickerSheet(
    state: DevicePickerUiState,
    onAction: (ServoAction) -> Unit,
    onRequestPermissions: () -> Unit,
    onEnableBluetooth: () -> Unit,
    onOpenAppSettings: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var composed by remember { mutableStateOf(false) }

    LaunchedEffect(state.visible) {
        if (state.visible) {
            composed = true
        } else if (composed) {
            sheetState.hide()
            composed = false
        }
    }

    if (composed) {
        ModalBottomSheet(
            onDismissRequest = { onAction(ServoAction.DismissPicker) },
            sheetState = sheetState,
        ) {
            DevicePickerContent(
                state = state,
                onAction = onAction,
                onRequestPermissions = onRequestPermissions,
                onEnableBluetooth = onEnableBluetooth,
                onOpenAppSettings = onOpenAppSettings,
            )
        }
    }
}

/** Zawartość arkusza – osobno, aby dało się ją pokazać w podglądzie bez okna dialogowego. */
@Composable
internal fun DevicePickerContent(
    state: DevicePickerUiState,
    onAction: (ServoAction) -> Unit,
    onRequestPermissions: () -> Unit,
    onEnableBluetooth: () -> Unit,
    onOpenAppSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scan = state.scan
    val busyConnecting = state.connectingAddress != null

    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
    ) {
        item(key = "header") {
            Column(modifier = Modifier.padding(bottom = 12.dp)) {
                Text(
                    text = stringResource(R.string.picker_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() },
                )
                if (state.availability == BluetoothAvailability.Ready) {
                    Text(
                        text = stringResource(
                            if (scan.showAllDevices) R.string.picker_subtitle_all else R.string.picker_subtitle_filtered,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }

        when (state.availability) {
            BluetoothAvailability.Unsupported -> item(key = "unsupported") {
                MessageBlock(
                    title = stringResource(R.string.notice_unsupported_title),
                    body = stringResource(R.string.notice_unsupported_body),
                )
            }

            BluetoothAvailability.PermissionsMissing -> item(key = "permissions") {
                MessageBlock(
                    title = stringResource(R.string.picker_permission_title),
                    body = stringResource(
                        if (state.permissionPermanentlyDenied) {
                            R.string.picker_permission_denied_body
                        } else {
                            R.string.picker_permission_body
                        },
                    ),
                    actionLabel = stringResource(
                        if (state.permissionPermanentlyDenied) R.string.picker_open_settings else R.string.picker_permission_grant,
                    ),
                    onAction = if (state.permissionPermanentlyDenied) onOpenAppSettings else onRequestPermissions,
                )
            }

            BluetoothAvailability.Disabled -> item(key = "bluetoothOff") {
                MessageBlock(
                    title = stringResource(R.string.picker_bluetooth_off_title),
                    body = stringResource(R.string.picker_bluetooth_off_body),
                    actionLabel = stringResource(R.string.picker_enable_bluetooth),
                    onAction = onEnableBluetooth,
                )
            }

            BluetoothAvailability.Ready -> {
                item(key = "scanStatus") {
                    ScanStatusRow(
                        phase = scan.phase,
                        durationSeconds = state.scanDurationSeconds,
                        actionsEnabled = !busyConnecting,
                        onStop = { onAction(ServoAction.StopScan) },
                        onRescan = { onAction(ServoAction.Rescan) },
                    )
                }

                state.connectionError?.let { reason ->
                    item(key = "connectionError") {
                        ErrorBlock(disconnectReasonText(reason, ServoConfig.BACKGROUND_DISCONNECT_DELAY_S))
                    }
                }
                if (scan.phase == ScanPhase.Failed) {
                    item(key = "scanError") {
                        ErrorBlock(
                            when (scan.error) {
                                ScanError.TooFrequent -> stringResource(R.string.scan_error_too_frequent)
                                ScanError.ScannerUnavailable -> stringResource(R.string.scan_error_unavailable)
                                ScanError.Failed, null -> stringResource(R.string.scan_error_failed, scan.errorCode ?: -1)
                            },
                        )
                    }
                }

                items(items = scan.devices, key = { it.address }) { device ->
                    DeviceRow(
                        device = device,
                        connecting = device.address == state.connectingAddress,
                        enabled = !busyConnecting,
                        onClick = { onAction(ServoAction.SelectDevice(device)) },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }

                if (scan.devices.isEmpty()) {
                    item(key = "empty") {
                        when (scan.phase) {
                            ScanPhase.Scanning -> Text(
                                text = stringResource(R.string.picker_waiting_for_results),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 16.dp),
                            )

                            ScanPhase.Finished -> Column(modifier = Modifier.padding(vertical = 16.dp)) {
                                Text(
                                    text = stringResource(R.string.picker_empty_title),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    text = stringResource(R.string.picker_empty_body),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }

                            ScanPhase.Idle, ScanPhase.Failed -> Unit
                        }
                    }
                }

                item(key = "filterToggle") {
                    TextButton(
                        onClick = { onAction(ServoAction.ShowAllDevices(!scan.showAllDevices)) },
                        enabled = !busyConnecting,
                        // Wyrównanie tekstu przycisku do lewej krawędzi treści (TextButton ma 12 dp paddingu).
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .offset(x = (-12).dp),
                    ) {
                        Text(
                            stringResource(
                                if (scan.showAllDevices) R.string.picker_show_compatible else R.string.picker_show_all,
                            ),
                        )
                    }
                }
            }
        }

        item(key = "bottomSpace") { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun ScanStatusRow(
    phase: ScanPhase,
    durationSeconds: Int,
    actionsEnabled: Boolean,
    onStop: () -> Unit,
    onRescan: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = when (phase) {
                    ScanPhase.Scanning -> stringResource(R.string.picker_scanning, durationSeconds)
                    ScanPhase.Finished, ScanPhase.Failed -> stringResource(R.string.picker_scan_finished)
                    ScanPhase.Idle -> stringResource(R.string.picker_scan_idle)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (phase == ScanPhase.Scanning) {
                TextButton(onClick = onStop) { Text(stringResource(R.string.picker_stop)) }
            } else {
                TextButton(onClick = onRescan, enabled = actionsEnabled) {
                    Text(stringResource(R.string.picker_rescan))
                }
            }
        }
        // Stała wysokość – lista nie przeskakuje, gdy pasek postępu znika.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp),
        ) {
            if (phase == ScanPhase.Scanning) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun DeviceRow(
    device: DiscoveredDevice,
    connecting: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val name = device.name ?: stringResource(R.string.device_unnamed)
    val rssiDescription = device.rssi?.let { stringResource(R.string.picker_rssi_description, it) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(MaterialTheme.shapes.small)
            .clickable(
                enabled = enabled,
                onClickLabel = stringResource(R.string.picker_connect_description, name),
                role = Role.Button,
                onClick = onClick,
            )
            .alpha(if (enabled || connecting) 1f else 0.5f)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = device.address,
                style = TechnicalTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!device.advertisesServoService) {
                Text(
                    text = stringResource(R.string.picker_no_service_badge),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (connecting) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Text(
                    text = stringResource(R.string.picker_connecting),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else if (device.rssi != null && rssiDescription != null) {
            Text(
                text = stringResource(R.string.picker_rssi, device.rssi.withTypographicMinus()),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { contentDescription = rssiDescription },
            )
        }
    }
}

@Composable
private fun MessageBlock(
    title: String,
    body: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier.padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_bluetooth),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Text(text = title, style = MaterialTheme.typography.titleMedium)
        }
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (actionLabel != null && onAction != null) {
            Button(
                onClick = onAction,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .heightIn(min = 48.dp),
            ) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
private fun ErrorBlock(message: String) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_alert),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Text(text = message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
