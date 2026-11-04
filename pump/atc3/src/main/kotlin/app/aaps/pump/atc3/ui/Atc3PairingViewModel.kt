package app.aaps.pump.atc3.ui

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.atc3.R
import app.aaps.pump.atc3.keys.Atc3StringKey
import app.aaps.pump.atc3.link.Atc3BtPassword
import app.aaps.pump.atc3.link.Atc3Pairing
import app.aaps.pump.atc3.link.Atc3PumpNames
import app.aaps.pump.atc3.protocol.Atc3Frame
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The screen a pump is connected and disconnected from, in three steps:
 *
 * 1. The serial is typed and Find pressed: only a device advertising that serial counts. The pump
 *    accepts anyone who names its serial, so pumps are told apart by the number printed on them.
 * 2. The pump found, its password is asked for.
 * 3. Connect, which keeps both only if the pump answers, see [Atc3Pairing].
 *
 * A connected pump is shown with Disconnect; another can be connected only after it.
 */
@HiltViewModel
@Stable
class Atc3PairingViewModel @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val rh: ResourceHelper,
    private val preferences: Preferences,
    private val pairing: Atc3Pairing,
    @ApplicationContext private val context: Context
) : ViewModel() {

    enum class Step { SERIAL, SEARCHING, FOUND, CONNECTING }

    data class State(
        val paired: Boolean,
        val step: Step,
        val serial: String,
        val password: String,
        val status: String
    ) {

        val serialValid: Boolean get() = Atc3Frame.isValidSerial(serial.trim())
        val passwordValid: Boolean get() = Atc3BtPassword.isValid(password.trim())
        val serialEditable: Boolean get() = !paired && (step == Step.SERIAL || step == Step.FOUND)
        val canFind: Boolean get() = !paired && step == Step.SERIAL && serialValid
        val showPassword: Boolean get() = paired || step == Step.FOUND || step == Step.CONNECTING
        val passwordEditable: Boolean get() = !paired && step == Step.FOUND
        val canConnect: Boolean get() = !paired && step == Step.FOUND && passwordValid
    }

    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<State> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 5)
    val messages: SharedFlow<String> = _messages

    /** The address of the pump found for the serial on screen; null until it is found. */
    private var foundAddress: String? = null

    /** The scanner that started the scan stops it: toggling Bluetooth can change the adapter's. */
    private var scanningWith: BluetoothLeScanner? = null

    private var searchTimeout: Job? = null

    private val bluetoothAdapter: BluetoothAdapter?
        get() = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?)?.adapter

    private fun initialState(): State {
        // What is stored is shown only for a connected pump: nothing else is ever stored.
        val paired = pairing.isPaired
        return withStatus(
            State(
                paired = paired,
                step = Step.SERIAL,
                serial = if (paired) preferences.get(Atc3StringKey.Atc3SerialNumber) else "",
                password = if (paired) preferences.get(Atc3StringKey.Atc3BtPassword) else "",
                status = ""
            )
        )
    }

    private fun withStatus(state: State): State {
        val serial = state.serial.trim()
        val status = when {
            state.paired                    -> rh.gs(R.string.atc3_scanner_paired, Atc3PumpNames.advertisedNameFor(preferences.get(Atc3StringKey.Atc3SerialNumber)))
            !state.serialValid              -> rh.gs(R.string.atc3_scan_enter_serial)
            state.step == Step.SEARCHING    -> rh.gs(R.string.atc3_scan_looking_for, Atc3PumpNames.advertisedNameFor(serial))
            state.step == Step.FOUND        -> rh.gs(R.string.atc3_scanner_found, Atc3PumpNames.advertisedNameFor(serial))
            state.step == Step.CONNECTING   -> rh.gs(R.string.atc3_scanner_connecting, Atc3PumpNames.advertisedNameFor(serial))
            else                            -> rh.gs(R.string.atc3_scanner_press_find)
        }
        return state.copy(status = status)
    }

    private fun setState(change: (State) -> State) = _state.update { withStatus(change(it)) }

    /** A different serial is a different pump: whatever was found was found for the old one. */
    fun onSerialChanged(text: String) {
        if (!_state.value.serialEditable) return
        if (_state.value.step == Step.FOUND) foundAddress = null
        setState { if (it.step == Step.FOUND) it.copy(serial = text, step = Step.SERIAL) else it.copy(serial = text) }
    }

    fun onPasswordChanged(text: String) {
        if (!_state.value.passwordEditable) return
        setState { it.copy(password = text) }
    }

    /** Step 1: look for the pump with the serial on screen. */
    fun find() {
        if (!_state.value.canFind) return
        if (!startScan()) {
            _messages.tryEmit(rh.gs(R.string.atc3_scanner_no_bluetooth))
            return
        }
        setState { it.copy(step = Step.SEARCHING) }
        searchTimeout = viewModelScope.launch {
            delay(PUMP_SEARCH_MS)
            stopScan()
            val serial = _state.value.serial.trim()
            aapsLogger.debug(LTag.PUMP, "ATC3: pump $serial not found nearby")
            setState { it.copy(step = Step.SERIAL) }
            _messages.tryEmit(rh.gs(R.string.atc3_scanner_not_found, Atc3PumpNames.advertisedNameFor(serial)))
        }
    }

    /** The screen went away: a search is not carried on behind the user's back; Find starts it again. */
    fun onLeave() {
        if (_state.value.step == Step.SEARCHING) {
            searchTimeout?.cancel()
            setState { it.copy(step = Step.SERIAL) }
        }
        stopScan()
    }

    /** @return whether the scan is running */
    @SuppressLint("MissingPermission")
    private fun startScan(): Boolean {
        if (scanningWith != null) return true
        val scanner = bluetoothAdapter?.bluetoothLeScanner ?: return false
        return try {
            scanner.startScan(scanCallback)
            scanningWith = scanner
            true
        } catch (e: IllegalStateException) {
            aapsLogger.debug(LTag.PUMPBTCOMM, "ATC3: cannot scan, Bluetooth is off: ${e.message}")
            false
        } catch (e: SecurityException) {
            aapsLogger.error(LTag.PUMPBTCOMM, "ATC3: missing Bluetooth permission on scan", e)
            false
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopScan() {
        val scanner = scanningWith ?: return
        scanningWith = null
        try {
            scanner.stopScan(scanCallback)
        } catch (e: IllegalStateException) {
            // Bluetooth was switched off, so the scan is already gone. Nothing to do, but say so.
            aapsLogger.debug(LTag.PUMPBTCOMM, "ATC3: scan already stopped, Bluetooth is off: ${e.message}")
        } catch (e: SecurityException) {
            aapsLogger.error(LTag.PUMPBTCOMM, "ATC3: missing Bluetooth permission on scan stop", e)
        }
    }

    private val scanCallback = object : ScanCallback() {

        override fun onScanResult(callbackType: Int, result: ScanResult) {
            addDevice(result.device, result.scanRecord?.deviceName)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            results?.forEach { addDevice(it.device, it.scanRecord?.deviceName) }
        }

        override fun onScanFailed(errorCode: Int) {
            aapsLogger.error(LTag.PUMPBTCOMM, "ATC3: scan failed with code $errorCode")
        }
    }

    /** Take in a scan result, by its advertised name rather than the system's cached one. */
    @SuppressLint("MissingPermission")
    private fun addDevice(device: BluetoothDevice?, advertisedName: String?) {
        val name = advertisedName ?: try {
            device?.name
        } catch (e: SecurityException) {
            aapsLogger.error(LTag.PUMPBTCOMM, "ATC3: missing Bluetooth permission reading device name", e)
            null
        }
        if (device == null || name.isNullOrBlank()) return
        val address = device.address
        // Scan results arrive on a binder thread; the state is changed on the main one.
        viewModelScope.launch { onDeviceSeen(address, name.trim()) }
    }

    /** Step 2: the pump is on the air, so its password is asked for. */
    private fun onDeviceSeen(address: String, name: String) {
        val state = _state.value
        if (state.step != Step.SEARCHING) return
        val serial = state.serial.trim()
        if (!Atc3PumpNames.matchesSerial(name, serial)) return
        searchTimeout?.cancel()
        stopScan()
        foundAddress = address
        aapsLogger.debug(LTag.PUMP, "ATC3: found $name at $address")
        setState { it.copy(step = Step.FOUND) }
    }

    /** Step 3: connect, keeping the serial and the password only if the pump answers. */
    fun connect() {
        val state = _state.value
        val address = foundAddress ?: return
        if (!state.canConnect) return
        val serial = state.serial.trim()
        val password = state.password.trim()
        setState { it.copy(step = Step.CONNECTING) }
        pairing.connect(address, serial, password) { outcome, comment ->
            viewModelScope.launch { onConnectResult(outcome, comment, serial) }
        }
    }

    private fun onConnectResult(outcome: Atc3Pairing.Outcome, comment: String, serial: String) {
        _messages.tryEmit(
            when (outcome) {
                Atc3Pairing.Outcome.CONNECTED -> rh.gs(R.string.atc3_scanner_connected, Atc3PumpNames.advertisedNameFor(serial))
                Atc3Pairing.Outcome.REFUSED   -> rh.gs(R.string.atc3_scanner_refused)
                Atc3Pairing.Outcome.NO_ANSWER -> rh.gs(R.string.atc3_scanner_failed, comment.ifBlank { rh.gs(R.string.atc3_not_connected) })
            }
        )
        if (outcome == Atc3Pairing.Outcome.CONNECTED) {
            foundAddress = null
            _state.value = initialState()
        } else {
            // The pump found stays found: a wrong password is corrected and Connect pressed again.
            setState { it.copy(step = Step.FOUND) }
        }
    }

    fun disconnect() {
        if (!pairing.isPaired) return
        if (pairing.bolusUnderWay) {
            _messages.tryEmit(rh.gs(R.string.atc3_scanner_bolus_running))
            return
        }
        pairing.disconnect()
        foundAddress = null
        _state.value = initialState()
        _messages.tryEmit(rh.gs(R.string.atc3_scanner_disconnected))
    }

    override fun onCleared() {
        super.onCleared()
        // A scan still registered would keep this object through its callback.
        stopScan()
    }

    private companion object {

        /** How long Find looks for the pump: time enough to bring it close and wake its screen. */
        const val PUMP_SEARCH_MS = 30_000L
    }
}
