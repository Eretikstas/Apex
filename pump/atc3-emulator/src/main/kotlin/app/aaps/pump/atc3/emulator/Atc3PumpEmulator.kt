package app.aaps.pump.atc3.emulator

import app.aaps.core.interfaces.pump.emulator.EmulatedAlarm
import app.aaps.core.interfaces.pump.emulator.EmulatedCommand
import app.aaps.core.interfaces.pump.emulator.EmulatedPumpState
import app.aaps.core.interfaces.pump.emulator.EmulatedTempBasal
import app.aaps.core.interfaces.pump.emulator.EmulatorSpeed
import app.aaps.core.interfaces.pump.emulator.PumpEmulatorControl
import kotlin.math.roundToInt

/**
 * [VirtualAtc3Pump] with a radio link, driven by a test through [PumpEmulatorControl].
 *
 * The transport of the driver calls [connect], [disconnect] and [write]; what the pump sends comes out of
 * [onNotification], and a link the pump side ended is told through [onLinkLost].
 *
 * The pump has no extended bolus over the air, so those hooks answer false.
 */
class Atc3PumpEmulator(val pump: VirtualAtc3Pump) : PumpEmulatorControl {

    /** Where the pump's notifications go while the link is up. */
    @Volatile var onNotification: ((ByteArray) -> Unit)? = null

    /** Called when the link ends and the phone did not ask for it. */
    @Volatile var onLinkLost: (() -> Unit)? = null

    @Volatile private var linkUp = false
    @Volatile private var outOfRange = false

    init {
        pump.sink = { chunk -> if (linkUp) onNotification?.invoke(chunk) }
        pump.onLinkDropped = { dropLink() }
        // Fast by default: the pump's own default tick is already the fast one.
    }

    val isLinkUp: Boolean get() = linkUp

    /** The phone asks for a link. @return false when the pump is out of range */
    fun connect(): Boolean {
        if (outOfRange) return false
        linkUp = true
        return true
    }

    /** The phone lets the link go. */
    fun disconnect() {
        linkUp = false
    }

    /** One frame from the phone. @return false when there is no link to write on */
    fun write(frame: ByteArray): Boolean = if (linkUp) pump.receive(frame) else false

    private fun dropLink() {
        val wasUp = linkUp
        linkUp = false
        if (wasUp) onLinkLost?.invoke()
    }

    override var speed: EmulatorSpeed
        get() = if (pump.bolusTickMs >= REAL_TICK_MS) EmulatorSpeed.REAL else EmulatorSpeed.FAST
        set(value) {
            // One progress frame is 0.05 U: about one a second on the real pump.
            pump.bolusTickMs = if (value == EmulatorSpeed.REAL) REAL_TICK_MS else FAST_TICK_MS
        }

    override fun breakLink(): Boolean {
        dropLink()
        return true
    }

    override fun setOutOfRange(outOfRange: Boolean): Boolean {
        this.outOfRange = outOfRange
        if (outOfRange) dropLink()
        return true
    }

    private fun fault(command: EmulatedCommand): VirtualAtc3Pump.FrameFault? = when (command) {
        EmulatedCommand.BOLUS_START          -> VirtualAtc3Pump.FrameFault(CONTROL, 0x12)
        // The bolus cancel and the temp basal start share a code and differ in the payload size.
        EmulatedCommand.BOLUS_STOP           -> VirtualAtc3Pump.FrameFault(CONTROL, 0x02, 2)
        EmulatedCommand.TEMP_BASAL_START     -> VirtualAtc3Pump.FrameFault(CONTROL, 0x02, 4)
        EmulatedCommand.TEMP_BASAL_STOP      -> VirtualAtc3Pump.FrameFault(CONTROL, 0x05)
        EmulatedCommand.BASAL_PROFILE        -> VirtualAtc3Pump.FrameFault(CONTROL, 0x00)
        // Every read of what the pump runs now: status v1 and v2, and the running temp basal. Not its history.
        EmulatedCommand.STATUS               -> VirtualAtc3Pump.FrameFault(READ, 0x00, alsoCodes = setOf(0x0A, 0x0C))
        EmulatedCommand.EXTENDED_BOLUS_START,
        EmulatedCommand.EXTENDED_BOLUS_STOP  -> null
    }

    override fun loseCommand(command: EmulatedCommand): Boolean {
        val fault = fault(command) ?: return false
        synchronized(pump.loseCommands) { pump.loseCommands.add(fault) }
        return true
    }

    override fun loseAnswer(command: EmulatedCommand): Boolean {
        val fault = fault(command) ?: return false
        synchronized(pump.loseAnswers) { pump.loseAnswers.add(fault) }
        return true
    }

    override fun raise(alarm: EmulatedAlarm): Boolean {
        val code = when (alarm) {
            EmulatedAlarm.LOW_BATTERY      -> 1
            EmulatedAlarm.OCCLUSION        -> 8
            EmulatedAlarm.EMPTY_RESERVOIR  -> 13
            // No code for these two is known from the description.
            EmulatedAlarm.LOW_RESERVOIR,
            EmulatedAlarm.STOPPED_TOO_LONG -> return false
        }
        if (alarm == EmulatedAlarm.EMPTY_RESERVOIR) pump.reservoirMilliUnits = 0
        pump.raiseAlarm(code)
        return true
    }

    override fun stopNextBolusAt(units: Double): Boolean {
        pump.bolusOccludesAt = (units / RAW_UNIT).roundToInt()
        return true
    }

    override fun shiftPumpClock(offsetMs: Long): Boolean {
        pump.clockOffsetMs += offsetMs
        return true
    }

    override fun bolusOnPump(units: Double): Boolean {
        pump.bolusOnKeypad(units)
        return true
    }

    override fun suspendOnPump(suspended: Boolean): Boolean {
        pump.suspendOnKeypad(suspended)
        return true
    }

    override fun state(): EmulatedPumpState {
        val tbr = pump.tbr
        val profile = pump.profiles[pump.activeProfile]
        return EmulatedPumpState(
            boluses = synchronized(pump.bolusLog) { pump.bolusLog.reversed().map { it.delivered * RAW_UNIT } },
            bolusRunning = pump.bolusInProgress,
            tempBasal = tbr?.let {
                if (it.absolute) EmulatedTempBasal(absolute = it.value * RAW_UNIT, percent = null)
                else EmulatedTempBasal(absolute = null, percent = it.value)
            },
            suspended = pump.suspended,
            reservoir = pump.reservoirMilliUnits / 1000.0,
            linkUp = linkUp,
            commands = synchronized(pump.received) { pump.received.size },
            // The pump keeps 48 half hour rates; a scenario compares hours, so each hour is its two halves' mean.
            basal = List(24) { h -> (profile[h * 2] + profile[h * 2 + 1]) / 2.0 * RAW_UNIT }
        )
    }

    /** The emulator lives in the test's process only: there is nothing to carry over an app restart. */
    override fun saveState(): ByteArray? = null

    companion object {

        private const val CONTROL = 0xA1
        private const val READ = 0xA3
        private const val RAW_UNIT = 0.025
        private const val FAST_TICK_MS = 20L
        private const val REAL_TICK_MS = 1000L
    }
}
