package app.aaps.plugins.sibionics

import android.Manifest
import android.content.Context
import app.aaps.core.data.model.CAL
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.db.observeChanges
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.PermissionGroup
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginBaseWithPreferences
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.source.BgSource
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.compose.icons.IcGenericCgm
import app.aaps.core.ui.compose.preference.PreferenceSubScreenDef
import app.aaps.plugins.sibionics.ui.SibionicsComposeContent
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.IntKey
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tk.glucodata.drivers.sibionics.SibionicsCalibrationAnchor
import tk.glucodata.drivers.sibionics.SibionicsConstants

/**
 * Sibionics 2 read by AAPS itself over Bluetooth, no other app in between. The vendor's own algorithm (V116A,
 * Stock) turns the raw sensor current into glucose, as in the vendor app and in JugglucoNG.
 *
 * Set up: QR code from the sensor box (or the serial) -> search for the transmitter -> pick it. The values go to
 * the AAPS database as source `SibionicsNative`; a new sensor session also writes a sensor change.
 */
@ContributesIntoMap(AppScope::class, binding = binding<PluginBase>())
@IntKey(515)
@SingleIn(AppScope::class)
@Inject
class SibionicsPlugin(
    context: Context,
    rh: ResourceHelper,
    aapsLogger: AAPSLogger,
    preferences: Preferences,
    notificationManager: NotificationManager,
    private val persistenceLayer: PersistenceLayer,
    private val profileUtil: ProfileUtil,
    private val dateUtil: DateUtil
) : PluginBaseWithPreferences(
    PluginDescription()
        .mainType(PluginType.BGSOURCE)
        .composeContent { plugin -> SibionicsComposeContent(plugin as SibionicsPlugin) }
        .icon(IcGenericCgm)
        .pluginName(TextRef.AndroidRes(R.string.sibionics_native))
        .preferencesVisibleInSimpleMode(false)
        .description(TextRef.AndroidRes(R.string.sibionics_native_description)),
    SibionicsStringNonKey.entries, aapsLogger, rh, preferences, notificationManager
), BgSource {

    /** What the screen shows. */
    internal data class Status(
        val sensorName: String = "",
        val sensitivity: Float = 0f,
        val sensitivityFromCode: Boolean = false,
        val bleName: String = "",
        val connected: Boolean = false,
        val phase: SibionicsSession.Phase = SibionicsSession.Phase.IDLE,
        val problem: String = "",
        val authFailed: Boolean = false,
        val sessionStart: Long = 0L,
        val lastSeen: Long = 0L,
        val lastGlucoseMgdl: Double? = null,
        val nextIndex: Int = 0
    )

    private val store = SibionicsStore(context, preferences)
    private val radio = SibionicsRadio(context, aapsLogger)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val _status = MutableStateFlow(Status())
    internal val status: StateFlow<Status> = _status.asStateFlow()

    private val _found = MutableStateFlow<List<SibionicsFoundDevice>>(emptyList())
    internal val found: StateFlow<List<SibionicsFoundDevice>> = _found.asStateFlow()
    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    override fun requiredPermissions(): List<PermissionGroup> = listOf(
        PermissionGroup(
            permissions = listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT),
            rationaleTitle = TextRef.AndroidRes(R.string.sibionics_permission_title),
            rationaleDescription = TextRef.AndroidRes(R.string.sibionics_permission_description)
        )
    )

    override fun getPreferenceScreenContent() = PreferenceSubScreenDef(
        key = "bg_source_settings",
        title = pluginDescription.pluginName!!,
        items = listOf(BooleanKey.BgSourceUploadToNs),
        icon = pluginDescription.icon
    )

    /** Finger-stick calibrations, cached for the radio thread; refreshed whenever a calibration changes. */
    @Volatile private var anchors: List<SibionicsCalibrationAnchor> = emptyList()
    private var calibrationWatch: Job? = null

    override suspend fun onStart() {
        super.onStart()
        reloadAnchors()
        calibrationWatch?.cancel()
        calibrationWatch = scope.launch { persistenceLayer.observeChanges<CAL>().collect { reloadAnchors() } }
        startLink()
    }

    private suspend fun reloadAnchors() {
        val start = store.identity()?.let { store.sessionStore(it).sessionStart } ?: 0L
        anchors = runCatching { persistenceLayer.getValidCalibrationEntriesSince(start) }.getOrDefault(emptyList()).map {
            SibionicsCalibrationAnchor(
                sensorMmol = (it.sensorMgdlAtPairing / SibionicsConstants.MGDL_PER_MMOLL).toFloat(),
                referenceMmol = (it.fingerstickMgdl / SibionicsConstants.MGDL_PER_MMOLL).toFloat(),
                timestampMs = it.timestamp
            )
        }
    }

    override suspend fun onStop() {
        calibrationWatch?.cancel()
        calibrationWatch = null
        radio.stop()
        super.onStop()
    }

    fun formatGlucose(mgdl: Double?): String = profileUtil.fromMgdlToStringWithUnits(mgdl)

    fun formatTime(time: Long): String = if (time > 0) dateUtil.dateAndTimeString(time) else "-"

    fun hasBluetoothPermission(): Boolean = radio.hasPermissions()

    /**
     * Take the code read from the box. A code of another sensor forgets the old one (its address and session).
     * @return false when the code is not a Sibionics 2 QR code or serial
     */
    fun setSensorCode(code: String): Boolean {
        val identity = SibionicsIdentityParser.parse(code) ?: return false
        if (store.identity()?.sensorId != identity.sensorId) {
            radio.stop()
            store.forget()
        }
        store.sensorCode = code
        aapsLogger.debug(LTag.BGSOURCE, "Sibionics: sensor ${identity.displayName} set, sensitivity ${identity.sensitivity}")
        refreshStatus()
        return true
    }

    /** Search for transmitters around for 20 s. */
    fun scan() {
        _found.value = emptyList()
        _scanning.value = true
        radio.scan(SCAN_MS, onFound = { device -> _found.value = (_found.value + device).sortedByDescending { it.rssi } }, onDone = { _scanning.value = false })
    }

    /** Use [device] as the sensor's transmitter and start reading it. */
    internal fun choose(device: SibionicsFoundDevice) {
        store.address = device.address
        store.bleName = device.name
        _found.value = emptyList()
        startLink()
    }

    /** Forget the sensor: stop the link and remove the code, the address and the session files. */
    fun forget() {
        radio.stop()
        store.forget()
        _status.value = Status()
    }

    fun sensorCode(): String = store.sensorCode

    /** Start of the current sensor session (ms), 0 when not known. */
    fun sessionStart(): Long = store.identity()?.let { store.sessionStore(it).sessionStart } ?: 0L

    private fun startLink() {
        val identity = store.identity()
        val address = store.address
        refreshStatus()
        if (identity == null || address.isBlank() || !isEnabled()) return
        aapsLogger.debug(LTag.BGSOURCE, "Sibionics: starting ${identity.displayName} at $address")
        radio.start(address) { io ->
            SibionicsSession(identity, address, store.sessionStore(identity), io, sessionListener, anchors = { anchors })
        }
    }

    private fun refreshStatus(state: SibionicsSession.State? = null) {
        val identity = store.identity()
        _status.value = Status(
            sensorName = identity?.displayName.orEmpty(),
            sensitivity = identity?.sensitivity ?: 0f,
            sensitivityFromCode = identity?.sensitivityFromCode ?: false,
            bleName = store.bleName,
            connected = radio.connected,
            phase = state?.phase ?: _status.value.phase,
            problem = radio.problem,
            authFailed = state?.authFailed ?: false,
            sessionStart = state?.sessionStart ?: _status.value.sessionStart,
            lastSeen = state?.lastSeen ?: _status.value.lastSeen,
            lastGlucoseMgdl = state?.lastGlucoseMgdl ?: _status.value.lastGlucoseMgdl,
            nextIndex = state?.nextIndex ?: _status.value.nextIndex
        )
    }

    private val sessionListener = object : SibionicsSessionListener {
        override fun onReadings(readings: List<SibionicsReading>, sensorStart: Long?) {
            val values = readings.map { it.toGv() }
            scope.launch {
                runCatching { persistenceLayer.insertCgmSourceData(Sources.SiBionic, values, emptyList(), sensorStart) }
                    .onFailure { aapsLogger.error(LTag.BGSOURCE, "Sibionics: values not stored", it) }
            }
        }

        override fun onState(state: SibionicsSession.State) = refreshStatus(state)
    }

    private companion object {

        const val SCAN_MS = 20_000L
    }
}

/**
 * A reading as AAPS stores it: the calibrated value, and as raw the Stock value without calibration, which is what
 * a finger stick is paired with (`SibionicsCalibrationPlugin`). Trend is left to AAPS.
 */
internal fun SibionicsReading.toGv(): GV = GV(
    timestamp = timestamp,
    value = glucoseMgdl,
    raw = stockMgdl,
    noise = null,
    trendArrow = TrendArrow.NONE,
    sourceSensor = SourceSensor.SIBIONICS_NATIVE
)
