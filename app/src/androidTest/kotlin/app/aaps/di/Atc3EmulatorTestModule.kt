package app.aaps.di

import android.content.Context
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.pump.atc3.emulator.Atc3PumpEmulator
import app.aaps.pump.atc3.link.Atc3BLE
import app.aaps.pump.atc3.link.Atc3BleCallback
import app.aaps.pump.atc3.link.Atc3LinkProtection
import app.aaps.pump.atc3.trace.Atc3Trace
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * The software pump a device test wants the ATC3 driver to talk to, or null for the real radio.
 *
 * Set it before `hiltRule.inject()` and clear it when the test ends. Tests only: the app has no such switch.
 */
object Atc3Emulation {

    @Volatile var emulator: Atc3PumpEmulator? = null
}

/**
 * [Atc3BLE] with [Atc3PumpEmulator] in place of the radio. Everything above it is the real driver.
 *
 * While [Atc3Emulation.emulator] is null it behaves as the real class.
 */
class EmulatedAtc3BLE(aapsLogger: AAPSLogger, context: Context, trace: Atc3Trace) : Atc3BLE(aapsLogger, context, trace) {

    private val emulator: Atc3PumpEmulator? get() = Atc3Emulation.emulator

    /** The link's start and end are told from a thread of their own, as the Bluetooth stack does. */
    private val events = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "EmulatedAtc3BLE").apply { isDaemon = true } }

    @Volatile private var callback: Atc3BleCallback? = null
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
        }

    override var linkProtection: Atc3LinkProtection
        get() = if (emulator != null) (if (isConnected) Atc3LinkProtection.PROTECTED else Atc3LinkProtection.UNKNOWN) else super.linkProtection
        set(value) = Unit

    override fun isValidAddress(address: String): Boolean = if (emulator != null) true else super.isValidAddress(address)

    override fun setCallback(callback: Atc3BleCallback?) {
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
                // Out of range: the attempt goes nowhere and is given up, as the connect watchdog does.
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

/**
 * Binds [Atc3BLE] to [EmulatedAtc3BLE] in every device test. An explicit binding goes before the
 * class's own `@Inject` constructor, so nothing is replaced and the production modules stay as they are.
 */
@Module
@InstallIn(SingletonComponent::class)
object Atc3EmulatorTestModule {

    @Provides
    @Singleton
    fun provideAtc3BLE(aapsLogger: AAPSLogger, context: Context, trace: Atc3Trace): Atc3BLE = EmulatedAtc3BLE(aapsLogger, context, trace)
}
