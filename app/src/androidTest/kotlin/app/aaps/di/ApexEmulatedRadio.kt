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
 * protocol. Everything above it is the real v4 driver. The pump to talk to is [Atc3Emulation.emulator]; while it is
 * null this behaves as the real class.
 */
class EmulatedApexBLE(aapsLogger: AAPSLogger, context: Context, trace: ApexTrace) : ApexBLE(aapsLogger, context, trace) {

    private val emulator: Atc3PumpEmulator? get() = Atc3Emulation.emulator

    /** The link's start and end are told from a thread of their own, as the Bluetooth stack does. */
    private val events = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "EmulatedApexBLE").apply { isDaemon = true } }

    @Volatile private var callback: ApexBleCallback? = null
    @Volatile private var connecting = false
    @Volatile private var heardAt = 0L
    @Volatile private var upAt = 0L

    override val isConnected: Boolean get() = emulator?.isLinkUp ?: super.isConnected
    override val isConnecting: Boolean get() = if (emulator != null) connecting else super.isConnecting
    override val isBluetoothOn: Boolean get() = if (emulator != null) true else super.isBluetoothOn
    override val hasBluetooth: Boolean get() = if (emulator != null) true else super.hasBluetooth
    override val backoffRemainingMs: Long get() = if (emulator != null) 0L else super.backoffRemainingMs
    override val quietForMs: Long
        get() = if (emulator != null) (if (heardAt == 0L) -1L else System.currentTimeMillis() - heardAt) else super.quietForMs

    override var linkUpAtMs: Long
        get() = if (emulator != null) upAt else super.linkUpAtMs
        set(value) {
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
        noteHeardFrom()
    }

    override fun noteHeardFrom() {
        heardAt = System.currentTimeMillis()
        if (emulator == null) super.noteHeardFrom()
    }

    override fun connect(address: String): Boolean {
        val emulator = emulator ?: return super.connect(address)
        if (emulator.isLinkUp || connecting) return true
        emulator.onNotification = { chunk -> callback?.onDataReceived(chunk) }
        emulator.onLinkLost = { events.execute { callback?.onDisconnected() } }
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
