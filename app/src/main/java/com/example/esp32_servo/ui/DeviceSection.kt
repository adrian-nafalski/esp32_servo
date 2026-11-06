package com.example.esp32_servo.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.esp32_servo.R
import com.example.esp32_servo.ble.ServoProtocol
import com.example.esp32_servo.data.AngleWriteType
import com.example.esp32_servo.data.ConnectionState
import com.example.esp32_servo.data.FirmwareTarget
import com.example.esp32_servo.ui.theme.TechnicalTextStyle

@Composable
internal fun DeviceSection(device: DeviceUiState) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            text = stringResource(R.string.device_section_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(top = 16.dp)
                .semantics { heading() },
        )
        Text(
            text = deviceDisplayName(device.device),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            text = stringResource(
                when (device.connection) {
                    is ConnectionState.Ready -> R.string.device_state_ready
                    is ConnectionState.DiscoveringServices -> R.string.device_state_discovering
                    else -> R.string.device_state_connecting
                },
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        val chevronRotation by animateFloatAsState(if (expanded) 180f else 0f, label = "detailsChevron")
        Row(
            modifier = Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(MaterialTheme.shapes.small)
                .clickable(
                    onClickLabel = stringResource(if (expanded) R.string.details_collapse else R.string.details_expand),
                    role = Role.Button,
                ) { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.details_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.weight(1f))
            Icon(
                painter = painterResource(R.drawable.ic_chevron_down),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(20.dp)
                    .rotate(chevronRotation),
            )
        }

        AnimatedVisibility(visible = expanded) {
            TechnicalDetails(device)
        }
    }
}

@Composable
private fun TechnicalDetails(device: DeviceUiState) {
    val ready = device.connection as? ConnectionState.Ready
    Column(
        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        DetailRow(stringResource(R.string.details_address), device.device.address, technical = true)
        DetailRow(stringResource(R.string.details_service), ServoProtocol.SERVICE_UUID.toString(), technical = true)
        DetailRow(
            stringResource(R.string.details_angle_characteristic),
            ServoProtocol.TARGET_ANGLE_UUID.toString(),
            technical = true,
        )
        if (ready != null) {
            DetailRow(
                stringResource(R.string.details_write_type),
                stringResource(
                    when (ready.link.writeType) {
                        AngleWriteType.WithResponse -> R.string.details_write_with_response
                        AngleWriteType.WithoutResponse -> R.string.details_write_without_response
                    },
                ),
            )
            DetailRow(
                stringResource(R.string.details_status_characteristic),
                stringResource(
                    if (ready.link.statusCharacteristicAvailable) {
                        R.string.details_status_available
                    } else {
                        R.string.details_status_missing
                    },
                ),
            )
            DetailRow(
                stringResource(R.string.details_firmware_target),
                firmwareTargetText(ready.firmwareTarget, device),
            )
        }
        DetailRow(stringResource(R.string.details_encoding), stringResource(R.string.details_encoding_value))
        DetailRow(
            stringResource(R.string.details_command_interval),
            stringResource(R.string.details_command_interval_value, device.commandIntervalMs.toInt()),
        )
        DetailRow(
            stringResource(R.string.details_range),
            stringResource(R.string.details_range_value, device.range.min, device.range.max),
        )
    }
}

@Composable
private fun firmwareTargetText(target: FirmwareTarget, device: DeviceUiState): String = when (target) {
    FirmwareTarget.NotSupported -> stringResource(R.string.firmware_target_not_supported)
    FirmwareTarget.ReadFailed -> stringResource(R.string.firmware_target_read_failed)
    FirmwareTarget.NoCommandSinceBoot -> stringResource(R.string.firmware_target_none)
    is FirmwareTarget.Angle -> if (target.degrees in device.range) {
        stringResource(R.string.angle_degrees, target.degrees)
    } else {
        stringResource(R.string.firmware_target_out_of_range, target.degrees)
    }
}

@Composable
private fun DetailRow(label: String, value: String, technical: Boolean = false) {
    Column(modifier = Modifier.semantics(mergeDescendants = true) {}) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = if (technical) TechnicalTextStyle else MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
