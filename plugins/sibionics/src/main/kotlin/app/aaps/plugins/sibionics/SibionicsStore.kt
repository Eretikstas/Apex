package app.aaps.plugins.sibionics

import android.content.Context
import app.aaps.core.keys.interfaces.Preferences
import org.json.JSONObject
import tk.glucodata.drivers.sibionics.SibionicsSampleJournal
import tk.glucodata.drivers.sibionics.SibionicsSourceSample
import java.io.File

/**
 * The sensor that is set up, and its session state, kept as one document under [SibionicsStringNonKey.State] (device
 * state, not exported). The journal of raw minutes is a file of its own.
 */
internal class SibionicsStore(context: Context, private val preferences: Preferences) {

    private val dir = File(context.filesDir, "sibionics")

    // One document written from the radio thread and the screen: read-modify-write under one lock.
    private val lock = Any()

    private fun read(): JSONObject = synchronized(lock) {
        runCatching { JSONObject(preferences.get(SibionicsStringNonKey.State)) }.getOrElse { JSONObject() }
    }

    private fun update(block: JSONObject.() -> Unit) = synchronized(lock) {
        val o = read()
        o.block()
        preferences.put(SibionicsStringNonKey.State, o.toString())
    }

    /** The code read from the box (QR text or serial), empty when no sensor is set up. */
    var sensorCode: String
        get() = read().optString(KEY_CODE)
        set(value) = update { put(KEY_CODE, value) }

    /** Bluetooth address of the transmitter, empty until it was found. */
    var address: String
        get() = read().optString(KEY_ADDRESS)
        set(value) = update { put(KEY_ADDRESS, value) }

    /** Name the transmitter advertises. */
    var bleName: String
        get() = read().optString(KEY_BLE_NAME)
        set(value) = update { put(KEY_BLE_NAME, value) }

    fun identity(): SibionicsIdentity? = SibionicsIdentityParser.parse(sensorCode.ifBlank { null })

    /** Session state of [identity]'s sensor. */
    fun sessionStore(identity: SibionicsIdentity): SibionicsSessionStore = SessionFiles(identity.sensorId)

    /** Forget the sensor: its code, address and session files. */
    fun forget() {
        identity()?.let { SessionFiles(it.sensorId).clearAll() }
        preferences.put(SibionicsStringNonKey.State, "")
    }

    private inner class SessionFiles(sensorId: String) : SibionicsSessionStore {

        private val safeId = sensorId.filter { it.isLetterOrDigit() }
        private val journal = SibionicsSampleJournal(File(dir, "$safeId.journal"))

        override var sessionStart: Long
            get() = read().optLong(KEY_START + safeId, 0L)
            set(value) = update { put(KEY_START + safeId, value) }

        override var lastSeen: Long
            get() = read().optLong(KEY_LAST_SEEN + safeId, 0L)
            set(value) = update { put(KEY_LAST_SEEN + safeId, value) }

        override fun journal(): List<SibionicsSourceSample> = journal.snapshot()

        override fun appendJournal(samples: List<SibionicsSourceSample>) {
            journal.appendAll(samples)
        }

        override fun clearJournal() = journal.clear()

        fun clearAll() {
            journal.clear()
            update {
                remove(KEY_START + safeId)
                remove(KEY_LAST_SEEN + safeId)
            }
        }
    }

    private companion object {

        const val KEY_CODE = "sensor_code"
        const val KEY_ADDRESS = "address"
        const val KEY_BLE_NAME = "ble_name"
        const val KEY_START = "session_start_"
        const val KEY_LAST_SEEN = "last_seen_"
    }
}
