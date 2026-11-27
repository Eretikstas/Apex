package app.aaps.plugins.learner

/** What the cannula hint found: the cannula age in hours and the share of readings over 10 mmol/L in the last 6 hours. */
data class CannulaHint(val ageH: Double, val highPct: Double)

/**
 * The learner's cannula hint (advisor only). An old cannula brings more highs: the family's own data (kept private, not
 * in this repository) showed long high stretches clearly more often with a cannula of 60 hours or more. The rule:
 *
 * - the cannula is 60 hours old or more (from the last cannula change logged in AAPS; unknown age = no hint);
 * - in the last 6 hours 40 % or more of the readings are over 10 mmol/L (at least 80 % of the readings there).
 *
 * The hint only shows text ("probably the cannula, consider a change"). Nothing is changed.
 */
object CannulaHintEngine {

    const val MIN_AGE_H = 60.0
    const val WINDOW_MS = 6 * 3_600_000L
    const val HIGH_MGDL = 180.0
    const val HIGH_PCT = 40.0

    /** Readings needed in the window: 80 % of one reading per 5 minutes. */
    private const val MIN_READINGS = 58

    /**
     * The hint at [now], or null. [cannulaChangeAt] is the last cannula change (null = not known). [readings] are
     * (time, mg/dL) pairs; only those of the last 6 hours count, and a reading every minute counts like one per 5 minutes.
     */
    fun check(now: Long, cannulaChangeAt: Long?, readings: List<Pair<Long, Double>>): CannulaHint? {
        if (cannulaChangeAt == null || cannulaChangeAt > now) return null
        val ageH = (now - cannulaChangeAt) / 3_600_000.0
        if (ageH < MIN_AGE_H) return null
        // One value per 5 minute slot (the newest in the slot), so a sensor read every minute is not counted 5 times.
        val slots = readings.filter { it.first in (now - WINDOW_MS)..now }.associate { it.first / 300_000L to it.second }
        if (slots.size < MIN_READINGS) return null
        val highPct = 100.0 * slots.values.count { it > HIGH_MGDL } / slots.size
        return if (highPct >= HIGH_PCT) CannulaHint(ageH, highPct) else null
    }
}
