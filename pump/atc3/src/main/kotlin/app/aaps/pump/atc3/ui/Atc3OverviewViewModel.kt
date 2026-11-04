package app.aaps.pump.atc3.ui

import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.StatusLevel
import app.aaps.core.ui.compose.pump.ActionCategory
import app.aaps.core.ui.compose.pump.PumpAction
import app.aaps.core.ui.compose.pump.PumpCommunicationStatus
import app.aaps.core.ui.compose.pump.PumpInfoRow
import app.aaps.core.ui.compose.pump.PumpOverviewUiState
import app.aaps.core.ui.compose.pump.StatusBanner
import app.aaps.core.ui.compose.pump.tickerFlow
import app.aaps.pump.atc3.Atc3Const
import app.aaps.pump.atc3.R
import app.aaps.pump.atc3.command.Atc3Queue
import app.aaps.pump.atc3.command.Atc3SetBtPassword
import app.aaps.pump.atc3.command.Atc3SetSuspended
import app.aaps.pump.atc3.keys.Atc3StringKey
import app.aaps.pump.atc3.link.Atc3BtPassword
import app.aaps.pump.atc3.link.Atc3Connection
import app.aaps.pump.atc3.link.Atc3LinkProtection
import app.aaps.pump.atc3.protocol.Atc3Protocol
import app.aaps.pump.atc3.state.Atc3PumpState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import kotlin.math.roundToInt
import app.aaps.core.ui.R as CoreUiR

/** What the pump screen asks its host to show. */
sealed class Atc3OverviewEvent {

    /** Ask before stopping or resuming: a stop stops basal and a running bolus too. */
    data class ConfirmDeliveryChange(val stop: Boolean) : Atc3OverviewEvent()

    /** Ask for a new Bluetooth password to give the pump, starting from [current]. */
    data class AskPassword(val current: String) : Atc3OverviewEvent()

    /** Open the screen a pump is connected and disconnected from. */
    data object OpenPairing : Atc3OverviewEvent()

    /** A short message for the user. */
    data class Message(val text: String) : Atc3OverviewEvent()
}

/**
 * The state of the ATC3 pump for its screen, built from [Atc3PumpState] and the link. It only reads
 * and puts commands on the AAPS queue; every command to the pump goes through the queue, as on the
 * old screen.
 */
@HiltViewModel
@Stable
class Atc3OverviewViewModel @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val rh: ResourceHelper,
    private val preferences: Preferences,
    private val dateUtil: DateUtil,
    private val pumpState: Atc3PumpState,
    private val atc3Connection: Atc3Connection,
    private val atc3Queue: Atc3Queue,
    rxBus: RxBus,
    commandQueue: CommandQueue,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val communicationStatus = PumpCommunicationStatus(rxBus, commandQueue, context, scope)

    private val _events = MutableSharedFlow<Atc3OverviewEvent>(extraBufferCapacity = 5)
    val events: SharedFlow<Atc3OverviewEvent> = _events

    val uiState: StateFlow<PumpOverviewUiState> = combine(
        pumpState.lastConnectionFlow,
        pumpState.reservoirFlow,
        pumpState.batteryFlow,
        pumpState.lastBolusTimeFlow,
        communicationStatus.refreshTrigger,
        tickerFlow(PERIODIC_REFRESH_MS)
    ) { _ -> buildUiState() }
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), buildUiState())

    override fun onCleared() {
        super.onCleared()
        scope.cancel()
    }

    private fun buildUiState(): PumpOverviewUiState {
        val card = pumpState.statusCard
        val settings = pumpState.settings
        val noValue = rh.gs(R.string.atc3_placeholder_value)
        val paired = isPumpSelected()

        val rows = listOf(
            PumpInfoRow(
                label = rh.gs(CoreUiR.string.serial_number),
                value = preferences.get(Atc3StringKey.Atc3SerialNumber).ifBlank { rh.gs(R.string.atc3_no_serial) }
            ),
            PumpInfoRow(
                label = rh.gs(R.string.atc3_connection_label),
                value = when {
                    atc3Connection.isConnected  -> rh.gs(R.string.atc3_connection_connected)
                    atc3Connection.isConnecting -> rh.gs(R.string.atc3_connection_connecting)
                    else                        -> rh.gs(R.string.atc3_connection_waiting)
                }
            ),
            PumpInfoRow(
                label = rh.gs(R.string.atc3_last_answer_label),
                value = lastAnswerText(),
                level = if (pumpState.lastConnection > 0L && dateUtil.now() - pumpState.lastConnection > STALE_ANSWER_MS) StatusLevel.WARNING else StatusLevel.UNSPECIFIED
            ),
            PumpInfoRow(
                label = rh.gs(R.string.atc3_pump_state_label),
                value = when {
                    card == null     -> rh.gs(R.string.atc3_state_unknown)
                    card.locked      -> rh.gs(R.string.atc3_state_locked)
                    card.suspended   -> rh.gs(R.string.atc3_state_suspended)
                    else             -> rh.gs(R.string.atc3_state_running)
                },
                level = when {
                    card == null          -> StatusLevel.UNSPECIFIED
                    card.notDelivering    -> StatusLevel.CRITICAL
                    card.locked           -> StatusLevel.WARNING
                    else                  -> StatusLevel.NORMAL
                }
            ),
            PumpInfoRow(
                label = rh.gs(CoreUiR.string.reservoir_label),
                value = if (pumpState.isInitialized)
                    rh.gs(R.string.atc3_reservoir_value, pumpState.reservoirUnits.roundToInt().toString(), PumpType.ATC3.maxReservoirReading())
                else noValue
            ),
            PumpInfoRow(
                label = rh.gs(R.string.atc3_battery_label),
                value = pumpState.batteryPercent?.let { rh.gs(R.string.atc3_battery_value, it, pumpState.batteryVolts) } ?: noValue
            ),
            PumpInfoRow(
                label = rh.gs(R.string.atc3_max_bolus_label),
                value = settings?.let { rh.gs(CoreUiR.string.format_insulin_units, it.maxBolus) } ?: noValue
            ),
            PumpInfoRow(
                label = rh.gs(R.string.atc3_max_basal_label),
                value = settings?.let { rh.gs(CoreUiR.string.pump_base_basal_rate, it.maxBasal) } ?: noValue
            ),
            PumpInfoRow(
                label = rh.gs(R.string.atc3_bolus_speed_label),
                value = settings?.let { rh.gs(if (it.lowBolusSpeed) R.string.atc3_bolus_speed_low else R.string.atc3_bolus_speed_normal) } ?: noValue
            ),
            PumpInfoRow(
                label = rh.gs(R.string.atc3_password_label),
                value = preferences.get(Atc3StringKey.Atc3BtPassword).ifBlank { rh.gs(R.string.atc3_password_none) },
                level = if (atc3Connection.isPasswordEntered) StatusLevel.UNSPECIFIED else StatusLevel.CRITICAL
            ),
            PumpInfoRow(
                label = rh.gs(R.string.atc3_day_account_label),
                value = pumpState.dayAccount?.let { day ->
                    // The journal's sum on the pump's nearest step, both counted from midnight.
                    val aapsUnits = Math.round(day.aapsUnits / Atc3Protocol.DOSE_SCALE) * Atc3Protocol.DOSE_SCALE
                    rh.gs(R.string.atc3_day_account_value, aapsUnits, day.pumpUnits, day.pumpUnits - aapsUnits)
                } ?: rh.gs(R.string.atc3_day_account_none)
            ),
            PumpInfoRow(
                label = rh.gs(CoreUiR.string.firmware),
                value = pumpState.version?.firmwareText ?: rh.gs(R.string.atc3_firmware_unknown)
            ),
            PumpInfoRow(
                label = rh.gs(R.string.atc3_driver_version_label),
                value = Atc3Const.DRIVER_VERSION
            )
        )

        val stopped = card?.suspended == true
        val primary = listOf(
            PumpAction(
                label = rh.gs(CoreUiR.string.refresh),
                icon = Icons.Filled.Refresh,
                enabled = paired,
                onClick = ::onClickRefresh
            ),
            PumpAction(
                label = rh.gs(if (stopped) R.string.atc3_resume_pump else R.string.atc3_stop_pump),
                icon = if (stopped) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                // Only once the pump's state is known: the button says what it will do.
                enabled = paired && card != null,
                onClick = ::onClickDeliveryChange
            )
        )
        val management = listOf(
            PumpAction(
                label = rh.gs(if (paired) R.string.atc3_change_pump else R.string.atc3_select_pump),
                icon = Icons.Filled.Bluetooth,
                category = ActionCategory.MANAGEMENT,
                onClick = { _events.tryEmit(Atc3OverviewEvent.OpenPairing) }
            ),
            PumpAction(
                label = rh.gs(R.string.atc3_change_password),
                icon = Icons.Filled.Key,
                category = ActionCategory.MANAGEMENT,
                enabled = paired,
                onClick = { _events.tryEmit(Atc3OverviewEvent.AskPassword(preferences.get(Atc3StringKey.Atc3BtPassword))) }
            )
        )

        return PumpOverviewUiState(
            statusBanner = warningBanner() ?: communicationStatus.statusBanner(),
            infoRows = rows,
            primaryActions = primary,
            managementActions = management,
            queueStatus = communicationStatus.queueStatus()
        )
    }

    /**
     * The first thing the user must know, as on the old screen: no pump, no Bluetooth, or a link that
     * anybody in radio range could use.
     */
    private fun warningBanner(): StatusBanner? {
        val text = when {
            !isPumpSelected()        -> rh.gs(R.string.atc3_settings_no_pump)
            !bluetoothRadioEnabled() -> rh.gs(R.string.atc3_settings_no_bluetooth)
            else                     -> when (atc3Connection.linkProtection) {
                Atc3LinkProtection.UNPROTECTED -> rh.gs(R.string.atc3_link_unprotected)
                Atc3LinkProtection.UNSUPPORTED -> rh.gs(
                    R.string.atc3_link_unsupported,
                    pumpState.version?.firmwareText ?: rh.gs(R.string.atc3_firmware_unknown),
                    Atc3Const.PASSWORD_FIRMWARE.joinToString(".")
                )

                Atc3LinkProtection.PROTECTED,
                Atc3LinkProtection.UNKNOWN     -> null
            }
        } ?: return null
        return StatusBanner(text = text, level = StatusLevel.WARNING)
    }

    /** Whether a pump has been picked: an address to reach and a serial to address it by. */
    private fun isPumpSelected(): Boolean =
        preferences.get(Atc3StringKey.Atc3Address).isNotBlank() && atc3Connection.isConfigured

    /** Whether the phone's own Bluetooth radio is on. Without it the pump cannot be reached at all. */
    private fun bluetoothRadioEnabled(): Boolean =
        try {
            (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?)?.adapter?.isEnabled == true
        } catch (e: SecurityException) {
            // No Bluetooth permission: shown as the radio off, said in the log.
            aapsLogger.error(LTag.PUMPBTCOMM, "ATC3: cannot read the Bluetooth state", e)
            false
        }

    /** When the pump was last heard from, in plain words rather than a timestamp. */
    private fun lastAnswerText(): String {
        val last = pumpState.lastConnection
        if (last <= 0L) return rh.gs(R.string.atc3_never_connected)
        val minutes = ((dateUtil.now() - last) / 60_000L).toInt()
        return when {
            minutes < 1                -> rh.gs(R.string.atc3_just_now)
            minutes < MINUTES_PER_HOUR -> rh.gq(R.plurals.atc3_minutes_ago, minutes, minutes)
            minutes < MINUTES_PER_DAY  -> (minutes / MINUTES_PER_HOUR).let { rh.gq(R.plurals.atc3_hours_ago, it, it) }
            else                       -> (minutes / MINUTES_PER_DAY).let { rh.gq(R.plurals.atc3_days_ago, it, it) }
        }
    }

    fun onClickRefresh() {
        atc3Queue.readStatus(rh.gs(CoreUiR.string.user_request))
    }

    private fun onClickDeliveryChange() {
        val card = pumpState.statusCard ?: return
        _events.tryEmit(Atc3OverviewEvent.ConfirmDeliveryChange(stop = !card.suspended))
    }

    /** The user confirmed: stop or resume the pump through the queue. */
    fun changeDelivery(stop: Boolean) {
        aapsLogger.debug(LTag.PUMP, "ATC3: user asked to ${if (stop) "stop" else "resume"} the pump")
        atc3Queue.customCommand(Atc3SetSuspended(stop)) { success, comment ->
            if (!success) {
                aapsLogger.error(LTag.PUMP, "ATC3: the pump did not change its delivery state, $comment")
                _events.tryEmit(Atc3OverviewEvent.Message(comment.ifBlank { rh.gs(R.string.atc3_not_connected) }))
            }
            communicationStatus.refreshTrigger.value = dateUtil.now()
        }
    }

    /**
     * Give the pump a new Bluetooth password. The one AAPS presents is entered on the pairing screen;
     * this only changes the pump's.
     *
     * @return the error to show under the field, or null when the change was sent
     */
    fun writePassword(entered: String): String? {
        val trimmed = entered.trim()
        if (!Atc3BtPassword.isValid(trimmed)) return rh.gs(R.string.atc3_password_invalid)
        val value = trimmed.toInt()
        if (!Atc3BtPassword.isSettable(value)) {
            return rh.gs(R.string.atc3_password_out_of_range, Atc3BtPassword.format(Atc3BtPassword.MAX_SETTABLE))
        }
        aapsLogger.debug(LTag.PUMP, "ATC3: user asked to change the Bluetooth password")
        atc3Queue.customCommand(Atc3SetBtPassword(value)) { success, comment ->
            val text =
                if (success) rh.gs(R.string.atc3_password_written, Atc3BtPassword.format(value))
                else {
                    aapsLogger.error(LTag.PUMP, "ATC3: the pump did not take the new Bluetooth password, $comment")
                    rh.gs(R.string.atc3_password_write_failed, comment.ifBlank { rh.gs(R.string.atc3_not_connected) })
                }
            _events.tryEmit(Atc3OverviewEvent.Message(text))
            communicationStatus.refreshTrigger.value = dateUtil.now()
        }
        return null
    }

    private companion object {

        const val PERIODIC_REFRESH_MS = 10_000L
        const val MINUTES_PER_HOUR = 60
        const val MINUTES_PER_DAY = 24 * 60

        /** An answer older than this is shown as a warning: the loop runs every five minutes. */
        const val STALE_ANSWER_MS = 15 * 60_000L
    }
}
