package app.aaps.plugins.sibionics

import app.aaps.core.data.iob.InMemoryGlucoseValue
import app.aaps.core.data.model.CAL
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.calibration.AddEntryResult
import app.aaps.core.interfaces.calibration.Calibration
import app.aaps.core.interfaces.calibration.CalibrationContext
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.iob.GlucoseStatusProvider
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.compose.icons.IcCalibration
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.IntKey
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.math.abs

/**
 * Finger-stick calibration for the native Sibionics source ("calibration inside the source", decided 19.11).
 *
 * This plugin only stores the finger stick (CAL) and pairs it with the source's UNCALIBRATED Stock value, which the
 * source writes as the raw value of each reading. The correction itself is made by the source, on the Stock value,
 * with JugglucoNG's integrated calibration (one point = offset, more points = robust line, older points count less).
 * So [calibrate] changes nothing here: the value in AAPS, Nightscout and the follower apps is one and the same
 * number, and a calibration is never applied twice.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesIntoMap(AppScope::class, binding = binding<PluginBase>())
@IntKey(715)
class SibionicsCalibrationPlugin(
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    notificationManager: NotificationManager,
    private val persistenceLayer: PersistenceLayer,
    private val glucoseStatusProvider: GlucoseStatusProvider,
    private val dateUtil: DateUtil,
    private val sibionicsPlugin: SibionicsPlugin
) : PluginBase(
    PluginDescription()
        .mainType(PluginType.CALIBRATION)
        .icon(IcCalibration)
        .pluginName(TextRef.AndroidRes(R.string.sibionics_calibration))
        .description(TextRef.AndroidRes(R.string.sibionics_calibration_description)),
    aapsLogger, rh, notificationManager
), Calibration {

    override suspend fun calibrate(data: MutableList<InMemoryGlucoseValue>, context: CalibrationContext): MutableList<InMemoryGlucoseValue> = data

    override suspend fun checkPreconditions(): AddEntryResult = check(dateUtil.now(), bgMgdl = null).first

    override suspend fun addEntry(bgMgdl: Double, timestamp: Long): AddEntryResult {
        val (result, stockMgdl) = check(timestamp, bgMgdl)
        if (result is AddEntryResult.Rejected || stockMgdl == null) {
            aapsLogger.warn(LTag.GLUCOSE, "SibionicsCalibration: finger stick $bgMgdl not taken: $result")
            return result
        }
        persistenceLayer.insertOrUpdateCalibrationEntry(CAL(timestamp = timestamp, fingerstickMgdl = bgMgdl, sensorMgdlAtPairing = stockMgdl))
        aapsLogger.debug(LTag.GLUCOSE, "SibionicsCalibration: finger stick $bgMgdl paired with Stock $stockMgdl")
        return AddEntryResult.Accepted
    }

    /**
     * Same rules as the AAPS linear calibration: a sensor session, 2 h warm-up, steady glucose, a value within 10 min.
     * Our extra rules: the steadiness is judged at the finger-stick time (not only now), and a finger value [bgMgdl]
     * far from the sensor is not taken (a typing error or a bad stick would move every value after it).
     */
    private suspend fun check(timestamp: Long, bgMgdl: Double?): Pair<AddEntryResult, Double?> {
        val sessionStart = sibionicsPlugin.sessionStart().takeIf { it > 0 } ?: return AddEntryResult.Rejected.NoSession to null
        val warmUpEnd = sessionStart + WARM_UP_MS
        if (timestamp < warmUpEnd) return AddEntryResult.Rejected.InWarmUp(warmUpEnd) to null
        // Only a native Sibionics value carries the Stock value as raw.
        val native = persistenceLayer.getBgReadingsDataFromTimeToTime(start = timestamp - DELTA_LOOKBACK_MS, end = timestamp, ascending = false)
            .filter { it.sourceSensor == SourceSensor.SIBIONICS_NATIVE && it.raw != null }
        val delta = deltaAt(native) ?: glucoseStatusProvider.glucoseStatusData?.shortAvgDelta
        if (delta != null && abs(delta) > DELTA_GATE_MGDL_PER_5MIN) return AddEntryResult.Rejected.DeltaTooHigh(delta, DELTA_GATE_MGDL_PER_5MIN) to null
        val pair = native.firstOrNull { it.timestamp >= timestamp - PAIR_LOOKBACK_MS } ?: return AddEntryResult.Rejected.NoSensorPair to null
        val stock = checkNotNull(pair.raw)
        // Near the shown (already calibrated) value is also fine: a sensor whose Stock drifted far must stay correctable.
        if (bgMgdl != null && !isPlausible(bgMgdl, stock) && !isPlausible(bgMgdl, pair.value))
            return AddEntryResult.Rejected.TooFarFromSensor(bgMgdl, pair.value) to null
        return AddEntryResult.Accepted to stock
    }

    /** Stock change per 5 min over the last [DELTA_LOOKBACK_MS] before the finger stick; null with too little data. */
    private fun deltaAt(newestFirst: List<GV>): Double? {
        val newest = newestFirst.firstOrNull() ?: return null
        val oldest = newestFirst.lastOrNull { newest.timestamp - it.timestamp >= MIN_DELTA_SPAN_MS } ?: return null
        val minutes = (newest.timestamp - oldest.timestamp) / 60_000.0
        return (checkNotNull(newest.raw) - checkNotNull(oldest.raw)) / minutes * 5.0
    }

    internal companion object {

        const val WARM_UP_MS = 2L * 60L * 60L * 1000L
        const val DELTA_GATE_MGDL_PER_5MIN = 5.0
        const val PAIR_LOOKBACK_MS = 10L * 60L * 1000L
        const val DELTA_LOOKBACK_MS = 15L * 60L * 1000L
        const val MIN_DELTA_SPAN_MS = 10L * 60L * 1000L

        /** Finger and sensor may differ by up to 45 mg/dL (2.5 mmol/L) or 30 % of the sensor value, the larger. */
        const val MAX_DIFF_MGDL = 45.0
        const val MAX_DIFF_SHARE = 0.30

        fun isPlausible(fingerMgdl: Double, sensorMgdl: Double): Boolean =
            abs(fingerMgdl - sensorMgdl) <= maxOf(MAX_DIFF_MGDL, MAX_DIFF_SHARE * sensorMgdl)
    }
}
