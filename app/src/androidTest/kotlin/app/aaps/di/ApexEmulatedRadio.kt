package app.aaps.di

import android.content.Context
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.pump.apex.link.ApexBLE
import app.aaps.pump.apex.link.ApexBleCallback
import app.aaps.pump.apex.trace.ApexTrace
import app.aaps.pump.atc3.emulator.Atc3PumpEmulator
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * [ApexBLE] (the Apex v4 driver's radio) with our software pump [Atc3PumpEmulator] in its place: the same TruCare III
 * protocol. Everything above it is the real v4 driver. The pump to talk to is [Atc3Emulation.emulator] when this radio is made; while it is
 * null this behaves as the real class.
 */
class EmulatedApexBLE(aapsLogger: AAPSLogger, context: Context, trace: ApexTrace) : ApexBLE(aapsLogger, context, trace) {

    /**
     * The pump of the test that made this radio, taken once: a radio of an earlier test's graph, not quite stopped,
     * must not reach the next test's pump. It did, by reading [Atc3Emulation.emulator] anew each time: it linked
     * first, took the answers, and the new driver heard nothing.
     */
    private val emulator: Atc3PumpEmulator?
        get() = pinned ?: Atc3Emulation.emulator?.also { pinned = it }

    /** Set by the first pump seen (normally at construction, which comes after the test set its pump). */
    @Volatile private var pinned: Atc3PumpEmulator? = Atc3Emulation.emulator

    /** The link's start and end are told from a thread of their own, as the Bluetooth stack does. */
    private val events = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "EmulatedApexBLE").apply { isDaemon = true } }

    @Volatile private var callback: ApexBleCallback? = null
    @Volatile private var connecting = false
    @Volatile private var heardAt = 0L
    @Volatile private var upAt = 0L

    /**
     * Connected only over a link THIS radio holds. The pump's link flag alone is not enough: after a test restart the
     * radio of the earlier AAPS start, not quite stopped, can raise the link again. The new radio then saw "connected",
     * never connected itself, and its driver never heard the link was up (no link setup, driver not initialized).
     */
    override val isConnected: Boolean get() = emulator?.let { it.isLinkUp && upAt != 0L } ?: super.isConnected
    override val isConnecting: Boolean get() = if (emulator != null) connecting else super.isConnecting
    override val isBluetoothOn: Boolean get() = if (emulator != null) true else super.isBluetoothOn
    override val hasBluetooth: Boolean get() = if (emulator != null) true else super.hasBluetooth
    override val backoffRemainingMs: Long get() = if (emulator != null) 0L else super.backoffRemainingMs
    override val quietForMs: Long
        get() = if (emulator != null) (if (heardAt == 0L) -1L else System.currentTimeMillis() - heardAt) else super.quietForMs

    override var linkUpAtMs: Long
        get() = if (emulator != null) upAt else super.linkUpAtMs
        protected set(value) {
            upAt = value
            super.linkUpAtMs = value
        }

    override fun isValidAddress(address: String): Boolean = if (emulator != null) true else super.isValidAddress(address)

    override fun setCallback(callback: ApexBleCallback?) {
        this.callback = callback
        super.setCallback(callback)
    }

    override fun setPassword(password: String?) {
        if (emulator == null) super.setPassword(password)
    }

    override fun noteHeartbeat() {
        // The real one: it notes the pump heard and asks the driver's heartbeat read (the journal is settled there).
        // An earlier version only noted the pump heard: v4 never learnt of a heartbeat from our pump.
        super.noteHeartbeat()
    }

    override fun noteHeardFrom() {
        heardAt = System.currentTimeMillis()
        if (emulator == null) super.noteHeardFrom()
    }

    override fun connect(address: String): Boolean {
        val emulator = emulator ?: return super.connect(address)
        if (connecting) return true
        if (emulator.isLinkUp && upAt != 0L) return true
        emulator.onNotification = { chunk -> callback?.onDataReceived(chunk) }
        emulator.onLinkLost = { events.execute { callback?.onDisconnected() } }
        if (emulator.isLinkUp) {
            // The link was opened by the radio of an earlier AAPS start (a test restart keeps the same pump). A new
            // process gets its own link, so this radio takes it over and tells its driver, or the driver waits forever.
            upAt = System.currentTimeMillis()
            events.execute { callback?.onConnected() }
            return true
        }
        connecting = true
        events.execute {
            if (emulator.connect()) {
                upAt = System.currentTimeMillis()
                connecting = false
                callback?.onConnected()
            } else {
                events.schedule({
                    connecting = false
                    callback?.onDisconnected()
                }, OUT_OF_RANGE_GIVE_UP_MS, TimeUnit.MILLISECONDS)
            }
        }
        return true
    }

    override fun disconnect() {
        val emulator = emulator ?: return super.disconnect()
        val wasUp = emulator.isLinkUp
        emulator.disconnect()
        upAt = 0L
        if (wasUp) events.execute { callback?.onDisconnected() }
    }

    override fun write(data: ByteArray): Boolean = emulator?.write(data) ?: super.write(data)

    private companion object {

        const val OUT_OF_RANGE_GIVE_UP_MS = 3_000L
    }
}
