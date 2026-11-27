package app.aaps.plugins.learner

/** One warning given: when, and whether a low (under 3.9) really came within [LowPredictEngine.CHECK_MS] after it. */
data class LowWarning(val at: Long, val trueLow: Boolean? = null)

/**
 * The learner's low predictor (advisor, a notice only). It looks ~30 minutes ahead and LEARNS to give fewer false
 * warnings, never to give fewer true ones on its own.
 *
 * - Forecast: smoothed glucose now + 6 x its change per 5 minutes (averaged over 15 min) = glucose in 30 min.
 * - Warning when the forecast is under 3.9 - [margin] and the glucose now is under 6.5 mmol/L and falling; at most one
 *   warning per [GAP_MS] (30 min).
 * - Every warning is checked 40 min later: did a low come (true) or not (false)?
 * - Learning, from the last [WINDOW] checked warnings: more than half false -> [margin] one step stricter (+9 mg/dL,
 *   at most 27); four of five true or more -> one step looser (-9, not under 0). So it warns less often only when it was
 *   wrong often.
 */
object LowPredictEngine {

    const val LOW_MGDL = 70.0
    const val NOW_MAX_MGDL = 117.0
    const val AHEAD_STEPS = 6
    const val GAP_MS = 30 * 60_000L
    const val CHECK_MS = 40 * 60_000L
    const val WINDOW = 10
    const val STEP_MGDL = 9.0
    const val MAX_MARGIN = 27.0

    fun forecast(bgMgdl: Double, delta5Mgdl: Double): Double = bgMgdl + AHEAD_STEPS * delta5Mgdl

    /** Whether to warn now. [lastWarning] is the time of the last warning (0 = none). */
    fun warn(now: Long, bgMgdl: Double?, delta5Mgdl: Double?, margin: Double, lastWarning: Long): Boolean {
        if (bgMgdl == null || delta5Mgdl == null || now - lastWarning < GAP_MS) return false
        return bgMgdl < NOW_MAX_MGDL && delta5Mgdl < 0.0 && forecast(bgMgdl, delta5Mgdl) < LOW_MGDL - margin
    }

    /** The new margin from the checked warnings (newest last). */
    fun learn(margin: Double, checked: List<LowWarning>): Double {
        val last = checked.filter { it.trueLow != null }.takeLast(WINDOW)
        if (last.size < 5) return margin
        val trueShare = last.count { it.trueLow == true }.toDouble() / last.size
        return when {
            trueShare < 0.5  -> (margin + STEP_MGDL).coerceAtMost(MAX_MARGIN)
            trueShare >= 0.8 -> (margin - STEP_MGDL).coerceAtLeast(0.0)
            else             -> margin
        }
    }
}
