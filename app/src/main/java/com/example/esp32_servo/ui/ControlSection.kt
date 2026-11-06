package com.example.esp32_servo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.esp32_servo.R
import com.example.esp32_servo.servo.AnglePreset
import com.example.esp32_servo.ui.theme.AngleDisplayStyle
import com.example.esp32_servo.ui.theme.ServoTheme

@Composable
internal fun ControlSection(control: ControlUiState, onAction: (ServoAction) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.control_target_label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        AngleReadout(control = control, onAction = onAction)

        CommandStatusLine(
            status = control.command,
            canRetry = control.enabled,
            onRetry = { onAction(ServoAction.RetrySend) },
        )

        ServoPositionIndicator(
            angle = control.targetAngle,
            range = control.range,
            armStyle = when (control.command) {
                CommandStatus.Unavailable -> ArmStyle.Disabled
                CommandStatus.NothingSent, is CommandStatus.Failed -> ArmStyle.Proposed
                is CommandStatus.ReportedByDevice,
                is CommandStatus.Sending,
                is CommandStatus.Delivered,
                -> ArmStyle.Commanded
            },
            modifier = Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
                .heightIn(max = 260.dp)
                .aspectRatio(1.8f),
        )
        Text(
            text = stringResource(R.string.indicator_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )

        AngleSlider(control = control, onAction = onAction)
        PresetRow(control = control, onAction = onAction)
    }
}

@Composable
private fun AngleReadout(control: ControlUiState, onAction: (ServoAction) -> Unit) {
    val description = stringResource(R.string.angle_content_description, spokenDegrees(control.targetAngle))
    val numberColor = if (control.enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepButton(
            label = stringResource(R.string.control_step_down),
            description = stringResource(R.string.control_step_down_description),
            enabled = control.canDecrement,
            onClick = { onAction(ServoAction.Step(-1)) },
        )
        // Autodopasowanie rozmiaru: duża czcionka systemowa nie wypycha przycisków ±1°.
        BasicText(
            text = stringResource(R.string.angle_degrees, control.targetAngle),
            style = AngleDisplayStyle.copy(color = numberColor, textAlign = TextAlign.Center),
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 32.sp, maxFontSize = AngleDisplayStyle.fontSize),
            modifier = Modifier
                .weight(1f)
                .semantics { contentDescription = description },
        )
        StepButton(
            label = stringResource(R.string.control_step_up),
            description = stringResource(R.string.control_step_up_description),
            enabled = control.canIncrement,
            onClick = { onAction(ServoAction.Step(+1)) },
        )
    }
}

@Composable
private fun StepButton(label: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        contentPadding = PaddingValues(horizontal = 8.dp),
        modifier = Modifier
            .widthIn(min = 64.dp)
            .heightIn(min = 48.dp)
            .semantics { contentDescription = description },
    ) {
        Text(text = label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun CommandStatusLine(status: CommandStatus, canRetry: Boolean, onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val muted = MaterialTheme.colorScheme.onSurfaceVariant
            when (status) {
                CommandStatus.Unavailable -> StatusText(stringResource(R.string.command_unavailable), muted)
                CommandStatus.NothingSent -> StatusText(stringResource(R.string.command_nothing_sent), muted)
                is CommandStatus.ReportedByDevice -> StatusText(stringResource(R.string.command_reported), muted)
                is CommandStatus.Sending -> StatusText(stringResource(R.string.command_sending), muted)
                is CommandStatus.Delivered -> {
                    Icon(
                        painter = painterResource(R.drawable.ic_check),
                        contentDescription = null,
                        tint = ServoTheme.colors.positive,
                        modifier = Modifier.size(18.dp),
                    )
                    StatusText(
                        text = stringResource(
                            if (status.acknowledged) R.string.command_delivered_ack else R.string.command_delivered_no_ack,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                is CommandStatus.Failed -> {
                    Icon(
                        painter = painterResource(R.drawable.ic_alert),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                    StatusText(
                        text = stringResource(R.string.command_failed, status.angle),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    if (canRetry) {
                        TextButton(onClick = onRetry) { Text(stringResource(R.string.command_retry)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusText(
    text: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = color,
        textAlign = TextAlign.Center,
        modifier = modifier,
    )
}

@Composable
private fun AngleSlider(control: ControlUiState, onAction: (ServoAction) -> Unit) {
    val range = control.range
    val sliderDescription = stringResource(R.string.control_slider_description)
    val sliderState = spokenDegrees(control.targetAngle)
    Column(modifier = Modifier.padding(top = 16.dp)) {
        Slider(
            value = control.targetAngle.toFloat(),
            onValueChange = { onAction(ServoAction.SliderChange(it)) },
            onValueChangeFinished = { onAction(ServoAction.SliderChangeFinished) },
            valueRange = range.min.toFloat()..range.max.toFloat(),
            enabled = control.enabled,
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = sliderDescription
                    stateDescription = sliderState
                },
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            RangeLabel(stringResource(R.string.angle_degrees, range.min))
            Box(Modifier.weight(1f))
            RangeLabel(stringResource(R.string.angle_degrees, range.max))
        }
    }
}

@Composable
private fun RangeLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun PresetRow(control: ControlUiState, onAction: (ServoAction) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AnglePreset.entries.forEach { preset ->
            val angle = control.presetAngle(preset)
            val label = stringResource(
                when (preset) {
                    AnglePreset.Min -> R.string.preset_min
                    AnglePreset.Center -> R.string.preset_center
                    AnglePreset.Max -> R.string.preset_max
                },
            )
            val description = stringResource(
                when (preset) {
                    AnglePreset.Min -> R.string.preset_min_description
                    AnglePreset.Center -> R.string.preset_center_description
                    AnglePreset.Max -> R.string.preset_max_description
                },
                spokenDegrees(angle),
            )
            OutlinedButton(
                onClick = { onAction(ServoAction.Preset(preset)) },
                enabled = control.enabled,
                shape = MaterialTheme.shapes.medium,
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp)
                    .semantics { contentDescription = description },
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = label, style = MaterialTheme.typography.labelMedium)
                    Text(
                        text = stringResource(R.string.angle_degrees, angle),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}
