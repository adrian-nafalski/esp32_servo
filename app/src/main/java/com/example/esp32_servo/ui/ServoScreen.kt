package com.example.esp32_servo.ui

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.esp32_servo.R
import com.example.esp32_servo.ble.BlePermissions
import com.example.esp32_servo.data.BluetoothAvailability
import com.example.esp32_servo.data.ConnectionState
import com.example.esp32_servo.data.isError

/**
 * Punkt wejścia UI: zbiera stan z ViewModelu i obsługuje systemowe prośby
 * (uprawnienia, włączenie Bluetooth, ustawienia). Logika BLE pozostaje w warstwie danych.
 */
@Composable
fun ServoRoute(viewModel: ServoViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = LocalActivity.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        val granted = BlePermissions.required.all { results[it] == true }
        // Po odmowie brak "rationale" oznacza, że system nie pokaże już okna – trzeba do ustawień.
        val permanentlyDenied = !granted && activity != null && BlePermissions.required.any {
            results[it] == false && !activity.shouldShowRequestPermissionRationale(it)
        }
        viewModel.onPermissionsResult(granted, permanentlyDenied)
    }
    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { viewModel.onBluetoothEnableResult() }
    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { viewModel.onReturnedFromSettings() }

    ServoScreen(
        state = state,
        onAction = viewModel::onAction,
        onRequestPermissions = { permissionLauncher.launch(BlePermissions.required) },
        onEnableBluetooth = {
            // ACTION_REQUEST_ENABLE wymaga BLUETOOTH_CONNECT od Androida 12.
            val canRequest = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT,
            ) == PackageManager.PERMISSION_GRANTED
            if (canRequest) {
                enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            } else {
                viewModel.onBluetoothEnableResult()
            }
        },
        onOpenAppSettings = {
            settingsLauncher.launch(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null),
                ),
            )
        },
    )
}

/** Bezstanowy ekran – w pełni sterowany przez [ServoUiState]; używany też w podglądach. */
@Composable
fun ServoScreen(
    state: ServoUiState,
    onAction: (ServoAction) -> Unit,
    onRequestPermissions: () -> Unit,
    onEnableBluetooth: () -> Unit,
    onOpenAppSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { ServoTopBar(state, onAction) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                if (!state.control.enabled) {
                    ConnectionNotice(state = state, onConnect = { onAction(ServoAction.Connect) })
                }
                ControlSection(control = state.control, onAction = onAction)
                state.device?.let { DeviceSection(it) }
                Spacer(Modifier.size(8.dp))
            }
        }
    }

    DevicePickerSheet(
        state = state.picker,
        onAction = onAction,
        onRequestPermissions = onRequestPermissions,
        onEnableBluetooth = onEnableBluetooth,
        onOpenAppSettings = onOpenAppSettings,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServoTopBar(state: ServoUiState, onAction: (ServoAction) -> Unit) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() },
                )
                StatusLine(state.summary)
            }
        },
        actions = {
            when (state.connection) {
                is ConnectionState.Ready -> TextButton(onClick = { onAction(ServoAction.Disconnect) }) {
                    Text(stringResource(R.string.action_disconnect))
                }

                is ConnectionState.Connecting,
                is ConnectionState.DiscoveringServices,
                -> TextButton(onClick = { onAction(ServoAction.Disconnect) }) {
                    Text(stringResource(R.string.action_cancel))
                }

                is ConnectionState.Disconnected ->
                    if (state.availability != BluetoothAvailability.Unsupported) {
                        TextButton(onClick = { onAction(ServoAction.Connect) }) {
                            Text(stringResource(R.string.action_connect))
                        }
                    }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
        ),
    )
}

/** Panel widoczny, gdy sterowanie jest zablokowane: wyjaśnia dlaczego i co zrobić. */
@Composable
private fun ConnectionNotice(state: ServoUiState, onConnect: () -> Unit) {
    val connection = state.connection
    val title: String
    val body: String
    var isError = false
    var showProgress = false
    var showConnectButton = false

    when {
        state.availability == BluetoothAvailability.Unsupported -> {
            title = stringResource(R.string.notice_unsupported_title)
            body = stringResource(R.string.notice_unsupported_body)
            isError = true
        }

        connection is ConnectionState.Connecting -> {
            title = stringResource(R.string.notice_connecting_title, deviceDisplayName(connection.device))
            body = stringResource(R.string.notice_connecting_body)
            showProgress = true
        }

        connection is ConnectionState.DiscoveringServices -> {
            title = stringResource(R.string.notice_discovering_title, deviceDisplayName(connection.device))
            body = stringResource(R.string.notice_connecting_body)
            showProgress = true
        }

        connection is ConnectionState.Disconnected && connection.reason != null -> {
            val reason = connection.reason
            title = disconnectTitle(reason)
            isError = reason.isError
            val reasonText = disconnectReasonText(reason, state.backgroundDisconnectDelaySeconds)
            body = if (reason.isError) {
                reasonText + " " + stringResource(R.string.notice_controls_locked)
            } else {
                reasonText
            }
            showConnectButton = true
        }

        connection is ConnectionState.Disconnected -> {
            title = stringResource(R.string.notice_idle_title)
            body = stringResource(R.string.notice_idle_body)
            showConnectButton = true
        }

        // Ready, ale ViewModel jeszcze nie przejął sesji – stan przejściowy na jedną klatkę.
        else -> return
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (isError) {
                    Icon(
                        painter = painterResource(R.drawable.ic_alert),
                        contentDescription = null, // tytuł niesie tę samą informację
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
            }
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (showProgress) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                )
            }
            if (showConnectButton) {
                Button(
                    onClick = onConnect,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.action_connect_esp32))
                }
            }
        }
    }
}
