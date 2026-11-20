package app.aaps.plugins.sibionics

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import tk.glucodata.drivers.sibionics.SibionicsSampleJournal
import tk.glucodata.drivers.sibionics.SibionicsSourceSample
import java.io.File

/**
 * The sensor that is set up, and its session state. Kept apart from the AAPS preferences, like a pump's own
 * store: it is device state, not a setting, and must not travel with a settings export to another phone.
 */
internal class SibionicsStore(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val dir = File(context.filesDir, "sibionics")

    /** The code read from the box (QR text or serial), empty when no sensor is set up. */
    var sensorCode: String
        get() = prefs.getString(KEY_CODE, "").orEmpty()
        set(value) = prefs.edit { putString(KEY_CODE, value) }

    /** Bluetooth address of the transmitter, empty until it was found. */
    var address: String
        get() = prefs.getString(KEY_ADDRESS, "").orEmpty()
        set(value) = prefs.edit { putString(KEY_ADDRESS, value) }

    /** Name the transmitter advertises. */
    var bleName: String
        get() = prefs.getString(KEY_BLE_NAME, "").orEmpty()
        set(value) = prefs.edit { putString(KEY_BLE_NAME, value) }

    fun identity(): SibionicsIdentity? = SibionicsIdentityParser.parse(sensorCode.ifBlank { null })

    /** Session state of [identity]'s sensor. */
    fun sessionStore(identity: SibionicsIdentity): SibionicsSessionStore = SessionFiles(identity.sensorId)

    /** Forget the sensor: its code, address and session files. */
    fun forget() {
        identity()?.let { SessionFiles(it.sensorId).clearAll() }
        prefs.edit { clear() }
    }

    private inner class SessionFiles(private val sensorId: String) : SibionicsSessionStore {

        private val safeId = sensorId.filter { it.isLetterOrDigit() }
        private val journal = SibionicsSampleJournal(File(dir, "$safeId.journal"))

        override var sessionStart: Long
            get() = prefs.getLong(KEY_START + safeId, 0L)
            set(value) = prefs.edit { putLong(KEY_START + safeId, value) }

        override var lastSeen: Long
            get() = prefs.getLong(KEY_LAST_SEEN + safeId, 0L)
            set(value) = prefs.edit { putLong(KEY_LAST_SEEN + safeId, value) }

        override fun journal(): List<SibionicsSourceSample> = journal.snapshot()

        override fun appendJournal(samples: List<SibionicsSourceSample>) {
            journal.appendAll(samples)
        }

        override fun clearJournal() = journal.clear()

        fun clearAll() {
            journal.clear()
            prefs.edit {
                remove(KEY_START + safeId)
                remove(KEY_LAST_SEEN + safeId)
            }
        }
    }

    private companion object {

        const val PREFS = "sibionics_native"
        const val KEY_CODE = "sensor_code"
        const val KEY_ADDRESS = "address"
        const val KEY_BLE_NAME = "ble_name"
        const val KEY_START = "session_start_"
        const val KEY_LAST_SEEN = "last_seen_"
    }
}
