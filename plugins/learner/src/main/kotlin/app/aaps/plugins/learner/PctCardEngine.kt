package app.aaps.plugins.learner

/** What the percentage card saw over the last 3 days, and what it proposes (null = keep). */
data class PctCard(val highPct: Double, val lowPct: Double, val from: Int, val to: Int?, val hintUp: Boolean)

/**
 * The learner's profile percentage card, a safety card (user 21.11, lab PCTCARD + PCTMAX_100): on the body model a
 * percentage that also goes above 100 % cut highs but always cost more hypo carbs, so it only goes down and back:
 *
 * - Last 3 days, lows (under 3.9) over 4 % -> 10 % lower (not under 80 %).
 * - Below 100 %, highs (over 10) over 8 % with lows at most 3 % -> 10 % back up, never above 100 %.
 * - At 100 % or more, highs over 8 % with lows at most 2 % -> only a hint that 110 % could be tried; the user decides.
 * - Under one day of readings -> keep.
 */
object PctCardEngine {

    const val DAYS = 3

    fun card(steps: List<LearnerStep>, nowPct: Int): PctCard {
        val bgs = steps.mapNotNull { it.bgMgdl }
        if (bgs.size < MIN_READINGS) return PctCard(0.0, 0.0, nowPct, null, false)
        val hi = bgs.count { it > HIGH_MGDL } * 100.0 / bgs.size
        val lo = bgs.count { it < LOW_MGDL } * 100.0 / bgs.size
        val to = when {
            lo > LOW_DOWN_PCT && nowPct > MIN_PCT                          -> maxOf(MIN_PCT, nowPct - STEP)
            nowPct < 100 && hi > HIGH_PCT && lo <= LOW_UP_MAX_PCT -> minOf(100, nowPct + STEP)
            else                                                           -> null
        }
        val hint = to == null && nowPct >= 100 && hi > HIGH_PCT && lo <= LOW_HINT_MAX_PCT
        return PctCard(hi, lo, nowPct, to, hint)
    }

    private const val STEP = 10
    private const val MIN_PCT = 80
    private const val MIN_READINGS = 288
    private const val HIGH_MGDL = 180.0
    private const val LOW_MGDL = 70.0
    private const val HIGH_PCT = 8.0
    private const val LOW_DOWN_PCT = 4.0
    private const val LOW_UP_MAX_PCT = 3.0
    private const val LOW_HINT_MAX_PCT = 2.0
}
