package app.aaps.plugins.sibionics.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.ComposablePluginContent
import app.aaps.core.ui.compose.ToolbarConfig
import app.aaps.core.ui.compose.clearFocusOnTap
import app.aaps.plugins.sibionics.R
import app.aaps.plugins.sibionics.SibionicsPlugin
import app.aaps.plugins.sibionics.SibionicsSession
import java.util.Locale

/** Screen of the native Sibionics source: sensor code, transmitter search, link state, last value. */
class SibionicsComposeContent(private val plugin: SibionicsPlugin) : ComposablePluginContent {

    @Composable
    override fun Render(setToolbarConfig: (ToolbarConfig) -> Unit, onNavigateBack: () -> Unit, onSettings: (() -> Unit)?) {
        val title = stringResource(R.string.sibionics_native)
        LaunchedEffect(Unit) {
            setToolbarConfig(
                ToolbarConfig(
                    title = title,
                    navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) } },
                    actions = {}
                )
            )
        }
        SibionicsScreen(plugin)
    }
}

@Composable
private fun SibionicsScreen(plugin: SibionicsPlugin) {
    val status by plugin.status.collectAsStateWithLifecycle()
    val found by plugin.found.collectAsStateWithLifecycle()
    val scanning by plugin.scanning.collectAsStateWithLifecycle()
    var code by remember { mutableStateOf(plugin.sensorCode()) }
    var codeError by remember { mutableStateOf(false) }
    var showCamera by remember { mutableStateOf(false) }
    var confirmForget by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .clearFocusOnTap(focusManager)
            .verticalScroll(rememberScrollState())
            .padding(AapsSpacing.large),
        verticalArrangement = Arrangement.spacedBy(AapsSpacing.medium)
    ) {
        if (!plugin.hasBluetoothPermission()) Text(stringResource(R.string.sibionics_no_permission), color = MaterialTheme.colorScheme.error)

        if (status.sensorName.isEmpty()) {
            Text(stringResource(R.string.sibionics_step_code), style = MaterialTheme.typography.titleMedium)
            Button(onClick = { showCamera = !showCamera }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.sibionics_scan_code)) }
            if (showCamera) QrScanner(
                onScanned = { text ->
                    if (plugin.setSensorCode(text)) {
                        code = text
                        showCamera = false
                        codeError = false
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
            )
            OutlinedTextField(
                value = code,
                onValueChange = { code = it; codeError = false },
                label = { Text(stringResource(R.string.sibionics_serial)) },
                isError = codeError,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (codeError) Text(stringResource(R.string.sibionics_code_not_valid), color = MaterialTheme.colorScheme.error)
            OutlinedButton(onClick = { codeError = !plugin.setSensorCode(code.trim()) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.sibionics_save_code))
            }
            return@Column
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(AapsSpacing.large), verticalArrangement = Arrangement.spacedBy(AapsSpacing.small)) {
                Text(stringResource(R.string.sibionics_sensor, status.sensorName), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.sibionics_sensitivity, String.format(Locale.getDefault(), "%.2f", status.sensitivity)))
                if (!status.sensitivityFromCode) Text(stringResource(R.string.sibionics_sensitivity_default), color = MaterialTheme.colorScheme.error)
                if (status.bleName.isNotEmpty()) Text(stringResource(R.string.sibionics_transmitter, status.bleName))
            }
        }

        if (status.bleName.isEmpty()) {
            Text(stringResource(R.string.sibionics_step_search), style = MaterialTheme.typography.titleMedium)
            Button(onClick = { plugin.scan() }, enabled = !scanning, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (scanning) R.string.sibionics_searching else R.string.sibionics_search))
            }
            found.forEach { device ->
                Card(modifier = Modifier
                    .fillMaxWidth()
                    .clickable { plugin.choose(device) }) {
                    Text(stringResource(R.string.sibionics_found_device, device.name, device.rssi), modifier = Modifier.padding(AapsSpacing.large))
                }
            }
        } else {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(AapsSpacing.large), verticalArrangement = Arrangement.spacedBy(AapsSpacing.small)) {
                    Text(stringResource(phaseText(status.phase, status.connected)))
                    if (status.authFailed) Text(stringResource(R.string.sibionics_auth_failed), color = MaterialTheme.colorScheme.error)
                    Text(stringResource(R.string.sibionics_last_value, plugin.formatGlucose(status.lastGlucoseMgdl), plugin.formatTime(status.lastSeen)))
                    Text(stringResource(R.string.sibionics_session_start, plugin.formatTime(status.sessionStart)))
                }
            }
        }

        TextButton(onClick = { confirmForget = true }) { Text(stringResource(R.string.sibionics_forget)) }
    }

    if (confirmForget) AlertDialog(
        onDismissRequest = { confirmForget = false },
        text = { Text(stringResource(R.string.sibionics_forget_confirm)) },
        confirmButton = {
            TextButton(onClick = {
                confirmForget = false
                code = ""
                plugin.forget()
            }) { Text(stringResource(R.string.sibionics_forget)) }
        },
        dismissButton = { TextButton(onClick = { confirmForget = false }) { Text(stringResource(R.string.sibionics_cancel)) } }
    )
}

private fun phaseText(phase: SibionicsSession.Phase, connected: Boolean): Int = when {
    phase == SibionicsSession.Phase.STREAMING -> R.string.sibionics_state_streaming
    phase != SibionicsSession.Phase.IDLE      -> R.string.sibionics_state_handshake
    connected                                 -> R.string.sibionics_state_connected
    else                                      -> R.string.sibionics_state_searching
}
