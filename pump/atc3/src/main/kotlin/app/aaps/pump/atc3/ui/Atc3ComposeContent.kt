package app.aaps.pump.atc3.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.interfaces.pump.BlePreCheck
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.ComposablePluginContent
import app.aaps.core.ui.compose.ToolbarConfig
import app.aaps.core.ui.compose.clearFocusOnTap
import app.aaps.core.ui.compose.dialogs.OkCancelDialog
import app.aaps.core.ui.compose.dialogs.OkDialog
import app.aaps.core.ui.compose.pump.BlePreCheckHost
import app.aaps.core.ui.compose.pump.PumpOverviewScreen
import app.aaps.pump.atc3.R
import app.aaps.pump.atc3.link.Atc3BtPassword
import kotlin.random.Random
import app.aaps.core.ui.R as CoreUiR

/**
 * The ATC3 tab: the pump's state and commands, and the screen a pump is connected from. Every command
 * goes through the AAPS command queue.
 */
class Atc3ComposeContent(
    private val blePreCheck: BlePreCheck
) : ComposablePluginContent {

    @Composable
    override fun Render(
        setToolbarConfig: (ToolbarConfig) -> Unit,
        onNavigateBack: () -> Unit,
        onSettings: (() -> Unit)?
    ) {
        val overviewViewModel: Atc3OverviewViewModel = hiltViewModel()

        var showPairing by remember { mutableStateOf(false) }
        var confirmStop by remember { mutableStateOf<Boolean?>(null) }
        var askPassword by remember { mutableStateOf<String?>(null) }
        var message by remember { mutableStateOf<String?>(null) }

        val title = stringResource(if (showPairing) R.string.atc3_scan_title else R.string.atc3_name)
        val backText = stringResource(CoreUiR.string.back)
        val settingsText = stringResource(CoreUiR.string.settings)
        LaunchedEffect(showPairing, title) {
            setToolbarConfig(
                ToolbarConfig(
                    title = title,
                    navigationIcon = {
                        IconButton(onClick = { if (showPairing) showPairing = false else onNavigateBack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = backText)
                        }
                    },
                    actions = settingsAction(onSettings, settingsText)
                )
            )
        }

        LaunchedEffect(overviewViewModel) {
            overviewViewModel.events.collect { event ->
                when (event) {
                    is Atc3OverviewEvent.ConfirmDeliveryChange -> confirmStop = event.stop
                    is Atc3OverviewEvent.AskPassword           -> askPassword = event.current
                    is Atc3OverviewEvent.OpenPairing           -> showPairing = true
                    is Atc3OverviewEvent.Message               -> message = event.text
                }
            }
        }

        if (showPairing) {
            // Bluetooth on and allowed before a search: the screen is left when it is not.
            BlePreCheckHost(blePreCheck = blePreCheck, onFailed = { showPairing = false })
            Atc3PairingScreen(onMessage = { message = it })
        } else {
            val uiState by overviewViewModel.uiState.collectAsStateWithLifecycle()
            PumpOverviewScreen(state = uiState)
        }

        confirmStop?.let { stop ->
            OkCancelDialog(
                message = stringResource(if (stop) R.string.atc3_confirm_stop else R.string.atc3_confirm_resume),
                onConfirm = {
                    confirmStop = null
                    overviewViewModel.changeDelivery(stop)
                },
                onDismiss = { confirmStop = null }
            )
        }

        askPassword?.let { current ->
            PasswordDialog(
                initial = current,
                onWrite = { entered -> overviewViewModel.writePassword(entered) },
                onDismiss = { askPassword = null }
            )
        }

        message?.let { text ->
            OkDialog(
                title = stringResource(R.string.atc3_name),
                message = text,
                onDismiss = { message = null }
            )
        }
    }

    private fun settingsAction(onSettings: (() -> Unit)?, settingsText: String): @Composable RowScope.() -> Unit = {
        onSettings?.let { action ->
            IconButton(onClick = action) {
                Icon(Icons.Filled.Settings, contentDescription = settingsText)
            }
        }
    }
}

/**
 * Give the pump a new Bluetooth password. A random value within what the change command can set, and
 * never `000000`, is offered.
 *
 * @param onWrite sends the value; returns the error to show, or null when it was sent
 */
@Composable
private fun PasswordDialog(
    initial: String,
    onWrite: (String) -> String?,
    onDismiss: () -> Unit
) {
    var value by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.atc3_password_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AapsSpacing.medium)) {
                Text(stringResource(R.string.atc3_password_change_hint), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = value,
                    onValueChange = {
                        value = it.filter(Char::isDigit).take(Atc3BtPassword.DIGITS)
                        error = null
                    },
                    label = { Text(stringResource(R.string.atc3_password_dialog_hint)) },
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(
                    onClick = {
                        value = Atc3BtPassword.format(Random.nextInt(Atc3BtPassword.MIN_SETTABLE + 1, Atc3BtPassword.MAX_SETTABLE + 1))
                        error = null
                    }
                ) { Text(stringResource(R.string.atc3_password_random)) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val problem = onWrite(value)
                    if (problem == null) onDismiss() else error = problem
                }
            ) { Text(stringResource(R.string.atc3_password_write)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(CoreUiR.string.cancel)) }
        }
    )
}

/** Connect a pump by its serial and password, or disconnect the one connected. */
@Composable
private fun Atc3PairingScreen(onMessage: (String) -> Unit) {
    val viewModel: Atc3PairingViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { onMessage(it) }
    }
    DisposableEffect(viewModel) {
        onDispose { viewModel.onLeave() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .clearFocusOnTap(focusManager)
            .verticalScroll(rememberScrollState())
            .padding(AapsSpacing.extraLarge),
        verticalArrangement = Arrangement.spacedBy(AapsSpacing.large)
    ) {
        Text(text = state.status, style = MaterialTheme.typography.titleMedium)

        OutlinedTextField(
            value = state.serial,
            onValueChange = viewModel::onSerialChanged,
            label = { Text(stringResource(R.string.atc3_scanner_serial_hint)) },
            enabled = state.serialEditable,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        if (!state.paired) {
            Button(
                onClick = viewModel::find,
                enabled = state.canFind,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(if (state.step == Atc3PairingViewModel.Step.SEARCHING) R.string.atc3_scanner_searching else R.string.atc3_scanner_find))
            }
        }

        if (state.showPassword) {
            OutlinedTextField(
                value = state.password,
                onValueChange = { viewModel.onPasswordChanged(it.filter(Char::isDigit).take(Atc3BtPassword.DIGITS)) },
                label = { Text(stringResource(R.string.atc3_scanner_password_hint)) },
                enabled = state.passwordEditable,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth()
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium)) {
            if (!state.paired && (state.step == Atc3PairingViewModel.Step.FOUND || state.step == Atc3PairingViewModel.Step.CONNECTING)) {
                Button(onClick = viewModel::connect, enabled = state.canConnect) {
                    Text(stringResource(R.string.atc3_scanner_connect))
                }
            }
            if (state.paired) {
                OutlinedButton(onClick = viewModel::disconnect) {
                    Text(stringResource(R.string.atc3_scanner_disconnect))
                }
            }
        }
    }
}
