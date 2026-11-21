package app.aaps.plugins.sibionics

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import androidx.core.content.ContextCompat
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import tk.glucodata.drivers.sibionics.SibionicsConstants
import tk.glucodata.drivers.sibionics.SibionicsProtocol

/** A transmitter seen while searching. */
internal data class SibionicsFoundDevice(val name: String, val address: String, val rssi: Int)

/**
 * The Android Bluetooth side of one sensor: GATT link, notifications, writes, reconnects and the search for the
 * transmitter. Everything runs on one thread of its own; the [SibionicsSession] is only touched there.
 *
 * The link rules (high priority on connect, discovery after 250 ms, notifications on FF31 through the CCCD, writes
 * to FF32, retry a refused write, reconnect after 8 s) follow JugglucoNG's `SibionicsBleManager`.
 */
@SuppressLint("MissingPermission") // every Bluetooth call is behind hasPermissions()
internal class SibionicsRadio(
    private val context: Context,
    private val aapsLogger: AAPSLogger
) {

    private val thread = HandlerThread("sibionics").apply { start() }
    private val handler = Handler(thread.looper)
    private val adapter: BluetoothAdapter? get() = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private var session: SibionicsSession? = null
    private var address: String = ""
    private var gatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private var running = false
    private var pendingWrite: Pair<ByteArray, String>? = null
    private var pendingWriteAttempts = 0
    private var scanCallback: ScanCallback? = null
    private var scanDone: (() -> Unit)? = null
    private var scanTimeout: Runnable? = null
    private val timers = HashMap<SibionicsSession.Timer, Runnable>()

    /** Last link problem for the screen, empty when none. */
    @Volatile var problem: String = ""
        private set

    @Volatile var connected: Boolean = false
        private set

    fun hasPermissions(): Boolean =
        listOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
            .all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    /** Start talking to the sensor at [bleAddress]; [makeSession] builds its session with our [SibionicsSessionIo]. */
    fun start(bleAddress: String, makeSession: (SibionicsSessionIo) -> SibionicsSession) = handler.post {
        stopOnThread()
        address = bleAddress
        session = makeSession(io)
        running = true
        connect()
    }

    fun stop() = handler.post { stopOnThread() }

    /** Look for Sibionics 2 transmitters around; each one is reported once. Stops by itself after [durationMs]. */
    fun scan(durationMs: Long, onFound: (SibionicsFoundDevice) -> Unit, onDone: () -> Unit) = handler.post {
        finishScanOnThread()
        val scanner = adapter?.bluetoothLeScanner
        if (!hasPermissions() || scanner == null) {
            problem = if (!hasPermissions()) PROBLEM_PERMISSION else PROBLEM_BLUETOOTH_OFF
            onDone()
            return@post
        }
        val seen = HashSet<String>()
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val name = result.scanRecord?.deviceName ?: runCatching { result.device.name }.getOrNull()
                if (!SibionicsIdentityParser.isSibionics2Transmitter(name)) return
                val found = SibionicsFoundDevice(SibionicsConstants.normalizeBleName(name), result.device.address, result.rssi)
                handler.post { if (seen.add(found.address)) onFound(found) }
            }
        }
        scanCallback = callback
        scanDone = onDone
        val started = runCatching {
            scanner.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), callback)
        }.onFailure { aapsLogger.error(LTag.BGSOURCE, "Sibionics: scan failed", it) }.isSuccess
        if (!started) {
            finishScanOnThread()
            return@post
        }
        val timeout = Runnable { finishScanOnThread() }
        scanTimeout = timeout
        handler.postDelayed(timeout, durationMs)
    }

    /** Stop the scan (if one runs) and tell its caller once. Safe to call more than once. */
    private fun finishScanOnThread() {
        scanTimeout?.let { handler.removeCallbacks(it) }
        scanTimeout = null
        scanCallback?.let { callback -> runCatching { adapter?.bluetoothLeScanner?.stopScan(callback) } }
        scanCallback = null
        val done = scanDone
        scanDone = null
        done?.invoke()
    }

    private fun stopOnThread() {
        running = false
        // Removing all callbacks also removes the scan timeout, so the scan is ended here; else it would run on.
        finishScanOnThread()
        handler.removeCallbacksAndMessages(null)
        timers.clear()
        closeGatt()
        session?.onDisconnected()
        session = null
    }

    private fun connect() {
        if (!running) return
        if (!hasPermissions()) {
            problem = PROBLEM_PERMISSION
            retryLater(PERMISSION_RETRY_MS)
            return
        }
        val bt = adapter
        if (bt == null || !bt.isEnabled) {
            problem = PROBLEM_BLUETOOTH_OFF
            retryLater(SibionicsSession.RECONNECT_DELAY_MS)
            return
        }
        val device: BluetoothDevice = runCatching { bt.getRemoteDevice(address) }.getOrNull() ?: run {
            problem = PROBLEM_NO_ADDRESS
            return
        }
        aapsLogger.debug(LTag.BGSOURCE, "Sibionics: connecting $address")
        gatt = runCatching { device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE) }
            .onFailure { aapsLogger.error(LTag.BGSOURCE, "Sibionics: connectGatt failed", it) }
            .getOrNull()
        if (gatt == null) retryLater(SibionicsSession.RECONNECT_DELAY_MS)
        else handler.postDelayed(connectTimeout, CONNECT_TIMEOUT_MS)
    }

    private val connectTimeout = Runnable {
        if (!connected) {
            aapsLogger.debug(LTag.BGSOURCE, "Sibionics: no link after ${CONNECT_TIMEOUT_MS / 1000} s, trying again")
            reconnectOnThread("connect timeout", SibionicsSession.RECONNECT_DELAY_MS)
        }
    }

    private val setupTimeout = Runnable {
        if (connected && session?.phaseNow == SibionicsSession.Phase.IDLE) reconnectOnThread("setup timeout", SibionicsSession.RECONNECT_DELAY_MS)
    }

    private fun retryLater(delayMs: Long) {
        handler.removeCallbacks(reconnectRunnable)
        handler.postDelayed(reconnectRunnable, delayMs)
    }

    private val reconnectRunnable = Runnable { connect() }

    private fun reconnectOnThread(reason: String, delayMs: Long) {
        aapsLogger.debug(LTag.BGSOURCE, "Sibionics: reconnect in $delayMs ms: $reason")
        closeGatt()
        session?.onDisconnected()
        retryLater(delayMs)
    }

    private fun closeGatt() {
        handler.removeCallbacks(connectTimeout)
        handler.removeCallbacks(setupTimeout)
        clearPendingWrite()
        connected = false
        writeChar = null
        gatt?.let { runCatching { it.disconnect() }; runCatching { it.close() } }
        gatt = null
    }

    private val io = object : SibionicsSessionIo {
        override fun write(bytes: ByteArray, label: String): Boolean = writeOnThread(bytes, label)

        override fun schedule(timer: SibionicsSession.Timer, delayMs: Long) {
            timers.remove(timer)?.let { handler.removeCallbacks(it) }
            val runnable = Runnable {
                timers.remove(timer)
                session?.onTimer(timer)
            }
            timers[timer] = runnable
            handler.postDelayed(runnable, delayMs)
        }

        override fun cancel(timer: SibionicsSession.Timer) {
            timers.remove(timer)?.let { handler.removeCallbacks(it) }
        }

        override fun reconnect(reason: String, delayMs: Long) = reconnectOnThread(reason, delayMs)

        override fun log(message: String) = aapsLogger.debug(LTag.BGSOURCE, "Sibionics: $message")
    }

    private fun writeOnThread(bytes: ByteArray, label: String): Boolean {
        val ok = issueWrite(bytes)
        aapsLogger.debug(LTag.BGSOURCE, "Sibionics: write $label ok=$ok")
        if (ok) {
            clearPendingWrite()
        } else {
            // The latest command wins: the sensor leads the handshake and has moved past an older refused write.
            pendingWrite = bytes to label
            pendingWriteAttempts = 0
            handler.removeCallbacks(writeRetry)
            handler.postDelayed(writeRetry, WRITE_RETRY_DELAY_MS)
        }
        return ok
    }

    private val writeRetry = Runnable {
        val (bytes, label) = pendingWrite ?: return@Runnable
        pendingWriteAttempts++
        if (issueWrite(bytes)) {
            aapsLogger.debug(LTag.BGSOURCE, "Sibionics: write $label ok after $pendingWriteAttempts retries")
            clearPendingWrite()
        } else if (pendingWriteAttempts >= WRITE_RETRY_MAX) {
            aapsLogger.debug(LTag.BGSOURCE, "Sibionics: write $label given up")
            clearPendingWrite()
        } else {
            postWriteRetry()
        }
    }

    private fun postWriteRetry() {
        handler.removeCallbacks(writeRetry)
        handler.postDelayed(writeRetry, WRITE_RETRY_DELAY_MS)
    }

    private fun clearPendingWrite() {
        pendingWrite = null
        pendingWriteAttempts = 0
        handler.removeCallbacks(writeRetry)
    }

    @Suppress("DEPRECATION")
    private fun issueWrite(bytes: ByteArray): Boolean {
        val g = gatt ?: return false
        val ch = writeChar ?: return false
        val type = if (ch.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        else BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) g.writeCharacteristic(ch, bytes, type) == BluetoothGatt.GATT_SUCCESS
            else {
                ch.writeType = type
                ch.value = bytes
                g.writeCharacteristic(ch)
            }
        }.getOrDefault(false)
    }

    @Suppress("DEPRECATION")
    private fun enableNotify(g: BluetoothGatt, ch: BluetoothGattCharacteristic) {
        g.setCharacteristicNotification(ch, true)
        val descriptor = ch.getDescriptor(SibionicsConstants.CCCD)
        val wrote = descriptor != null && runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                g.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) == BluetoothGatt.GATT_SUCCESS
            else {
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                g.writeDescriptor(descriptor)
            }
        }.getOrDefault(false)
        if (!wrote) {
            aapsLogger.debug(LTag.BGSOURCE, "Sibionics: notification switch not written, starting anyway")
            session?.onReady()
        }
    }

    private fun isCurrent(g: BluetoothGatt): Boolean = running && g === gatt

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            handler.post {
                if (!isCurrent(g)) {
                    if (g !== gatt) runCatching { g.close() }
                    return@post
                }
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED    -> {
                        handler.removeCallbacks(connectTimeout)
                        connected = true
                        problem = ""
                        runCatching { g.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH) }
                        handler.postDelayed({ if (isCurrent(g)) runCatching { g.discoverServices() } }, DISCOVER_DELAY_MS)
                        handler.postDelayed(setupTimeout, SETUP_TIMEOUT_MS)
                    }

                    BluetoothProfile.STATE_DISCONNECTED -> reconnectOnThread("disconnected status=$status", SibionicsSession.RECONNECT_DELAY_MS)
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            handler.post {
                if (!isCurrent(g)) return@post
                val service = g.getService(SibionicsConstants.SERVICE_FF30)
                val notify = service?.getCharacteristic(SibionicsConstants.CHAR_NOTIFY_FF31)
                val write = service?.getCharacteristic(SibionicsConstants.CHAR_WRITE_FF32)
                if (status != BluetoothGatt.GATT_SUCCESS || notify == null || write == null) {
                    reconnectOnThread("services missing status=$status", SibionicsSession.RECONNECT_DELAY_MS)
                    return@post
                }
                writeChar = write
                enableNotify(g, notify)
            }
        }

        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            handler.post {
                if (!isCurrent(g)) return@post
                if (descriptor.characteristic?.uuid == SibionicsConstants.CHAR_NOTIFY_FF31) {
                    handler.removeCallbacks(setupTimeout)
                    session?.onReady()
                }
            }
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            // A refused write waits for the one in flight: retry as soon as it is done.
            handler.post { if (isCurrent(g) && pendingWrite != null) postWriteRetryNow() }
        }

        @Deprecated("Deprecated in Java")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            val value = characteristic.value?.copyOf() ?: return
            deliver(g, characteristic, value)
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            deliver(g, characteristic, value.copyOf())
        }
    }

    private fun postWriteRetryNow() {
        handler.removeCallbacks(writeRetry)
        handler.post(writeRetry)
    }

    private fun deliver(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
        if (characteristic.uuid != SibionicsConstants.CHAR_NOTIFY_FF31) return
        handler.post {
            if (!isCurrent(g)) return@post
            runCatching { session?.onNotification(value) }
                .onFailure { aapsLogger.error(LTag.BGSOURCE, "Sibionics: frame not handled ${SibionicsProtocol.toHex(value)}", it) }
        }
    }

    companion object {

        const val PROBLEM_PERMISSION = "permission"
        const val PROBLEM_BLUETOOTH_OFF = "bluetooth_off"
        const val PROBLEM_NO_ADDRESS = "no_address"
        private const val CONNECT_TIMEOUT_MS = 30_000L
        private const val SETUP_TIMEOUT_MS = 15_000L
        private const val DISCOVER_DELAY_MS = 250L
        private const val PERMISSION_RETRY_MS = 60_000L
        private const val WRITE_RETRY_DELAY_MS = 150L
        private const val WRITE_RETRY_MAX = 12
    }
}
