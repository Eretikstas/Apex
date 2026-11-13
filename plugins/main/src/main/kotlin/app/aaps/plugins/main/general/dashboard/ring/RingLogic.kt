package app.aaps.plugins.main.general.dashboard.ring

import kotlin.math.sqrt

/** The "next hour" tile, see docs/RING_SKIN.md. */
enum class RingHourState { STABLE, RISING, FALLING }

/** How a supply chip looks: normal, warning (orange) or alarm (red). */
enum class RingLevel { OK, WARN, ALARM }

/** Glucose statistics of one period. Percent values are whole numbers; null when there are no readings. */
data class RingStats(val lowPct: Int, val inRangePct: Int, val highPct: Int, val cvPct: Int, val count: Int)

object RingLogic {

    /** 3.9 mmol/L. */
    const val LOW_MGDL = 70.0

    /** 10.0 mmol/L. */
    const val HIGH_MGDL = 180.0

    /** "Falling": the lowest prediction in the next hour under 3.5 mmol/L ... */
    const val FALLING_PRED_MGDL = 63.0

    /** ... while BG is under 6 mmol/L. */
    const val FALLING_BG_MGDL = 108.0

    /** "Rising": BG over 8 mmol/L and going up. */
    const val RISING_BG_MGDL = 144.0

    /**
     * Falling wins over rising: a low is the more urgent of the two.
     *
     * @param bgMgdl the last BG, null when there is none
     * @param deltaMgdl the last 5 min change, null when unknown
     * @param minPredNextHourMgdl the lowest of all loop predictions in the next 60 min, null when there are none
     * @param smbLimited the last micro bolus was cut by a limit
     */
    fun hourState(bgMgdl: Double?, deltaMgdl: Double?, minPredNextHourMgdl: Double?, smbLimited: Boolean): RingHourState {
        if (bgMgdl == null) return RingHourState.STABLE
        if (minPredNextHourMgdl != null && minPredNextHourMgdl < FALLING_PRED_MGDL && bgMgdl < FALLING_BG_MGDL) return RingHourState.FALLING
        if ((bgMgdl > RISING_BG_MGDL && (deltaMgdl ?: 0.0) > 0.0) || smbLimited) return RingHourState.RISING
        return RingHourState.STABLE
    }

    /**
     * Below 3.9, 3.9–10 and above 10 mmol/L, and CV, of the readings given (mg/dL). The three percents add to 100.
     */
    fun stats(valuesMgdl: List<Double>): RingStats? {
        val v = valuesMgdl.filter { it > 0.0 }
        if (v.isEmpty()) return null
        val n = v.size.toDouble()
        val low = Math.round(v.count { it < LOW_MGDL } * 100.0 / n).toInt()
        val high = Math.round(v.count { it > HIGH_MGDL } * 100.0 / n).toInt()
        val inRange = (100 - low - high).coerceAtLeast(0)
        val mean = v.average()
        val sd = sqrt(v.sumOf { (it - mean) * (it - mean) } / n)
        val cv = if (mean > 0) Math.round(sd / mean * 100.0).toInt() else 0
        return RingStats(low, inRange, high, cv, v.size)
    }

    private const val HOUR_MS = 3_600_000L

    /** Catheter: from 72 h time above 10 rises (12 % -> 24 % in the data), so warn at 72 h, alarm at 96 h. */
    const val CANNULA_WARN_H = 72
    const val CANNULA_ALARM_H = 96

    /** Sensor: Sibionics 2 runs 28 days. Warn in the last 2 days, alarm in the last 12 h. */
    const val SENSOR_LIFE_H = 28 * 24
    const val SENSOR_WARN_H = SENSOR_LIFE_H - 48
    const val SENSOR_ALARM_H = SENSOR_LIFE_H - 12

    /** Reservoir in units: about one day of insulin left, then half a day. */
    const val RESERVOIR_WARN_U = 20.0
    const val RESERVOIR_ALARM_U = 10.0

    /** Pump battery in percent. */
    const val BATTERY_WARN_PCT = 25
    const val BATTERY_ALARM_PCT = 10

    fun cannulaLevel(ageMs: Long): RingLevel = byAge(ageMs, CANNULA_WARN_H, CANNULA_ALARM_H)

    fun sensorLevel(ageMs: Long): RingLevel = byAge(ageMs, SENSOR_WARN_H, SENSOR_ALARM_H)

    fun reservoirLevel(units: Double): RingLevel = when {
        units <= RESERVOIR_ALARM_U -> RingLevel.ALARM
        units <= RESERVOIR_WARN_U  -> RingLevel.WARN
        else                       -> RingLevel.OK
    }

    fun batteryLevel(pct: Int): RingLevel = when {
        pct <= BATTERY_ALARM_PCT -> RingLevel.ALARM
        pct <= BATTERY_WARN_PCT  -> RingLevel.WARN
        else                     -> RingLevel.OK
    }

    private fun byAge(ageMs: Long, warnH: Int, alarmH: Int): RingLevel = when {
        ageMs >= alarmH * HOUR_MS -> RingLevel.ALARM
        ageMs >= warnH * HOUR_MS  -> RingLevel.WARN
        else                      -> RingLevel.OK
    }
}
