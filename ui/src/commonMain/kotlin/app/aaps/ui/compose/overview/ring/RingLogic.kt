package app.aaps.ui.compose.overview.ring

import kotlin.math.abs
import kotlin.math.sqrt

/** The period of the ring and of the statistics next to it. */
enum class RingPeriod(val hours: Int) { H3(3), DAY(24), WEEK(24 * 7) }

/** Time in range of a period, in percent of the readings, and the spread. */
data class RingStats(
    val count: Int,
    val inRangePct: Double,
    val lowPct: Double,
    val veryLowPct: Double,
    val highPct: Double,
    val meanMgdl: Double,
    val cvPct: Double,
)

/** What the next hour looks like, for the "next hour" tile. */
enum class RingHour { STABLE, RISING, FALLING, UNKNOWN }

object RingLogic {

    /** The child's goal ranges: 3.9-10 mmol/L in range, below 3.0 very low. */
    const val LOW_MGDL = 70.0
    const val HIGH_MGDL = 180.0
    const val VERY_LOW_MGDL = 54.0

    fun stats(valuesMgdl: List<Double>): RingStats? {
        val v = valuesMgdl.filter { it > 0 }
        if (v.isEmpty()) return null
        val n = v.size.toDouble()
        val mean = v.sum() / n
        val sd = sqrt(v.sumOf { (it - mean) * (it - mean) } / n)
        return RingStats(
            count = v.size,
            inRangePct = v.count { it >= LOW_MGDL && it <= HIGH_MGDL } * 100.0 / n,
            lowPct = v.count { it < LOW_MGDL } * 100.0 / n,
            veryLowPct = v.count { it < VERY_LOW_MGDL } * 100.0 / n,
            highPct = v.count { it > HIGH_MGDL } * 100.0 / n,
            meanMgdl = mean,
            cvPct = if (mean > 0) sd / mean * 100.0 else 0.0,
        )
    }

    /**
     * Falling wins: the lowest prediction of the next hour under 3.5 mmol/L while glucose is under 6. Rising: over 8 and
     * going up, or a micro bolus cut by a limit. Else stable. Null glucose = unknown.
     *
     * @param deltaMgdl change over the last 5 minutes
     */
    fun hour(bgMgdl: Double?, deltaMgdl: Double?, minPredMgdl: Double?, smbLimited: Boolean): RingHour = when {
        bgMgdl == null                                                         -> RingHour.UNKNOWN
        minPredMgdl != null && minPredMgdl < 63.0 && bgMgdl < 108.0            -> RingHour.FALLING
        (bgMgdl > 144.0 && (deltaMgdl ?: 0.0) > 0.0) || smbLimited             -> RingHour.RISING
        else                                                                   -> RingHour.STABLE
    }

    /**
     * The loop's own prediction for [horizonMs] ahead, nothing extended or guessed. [points] is one prediction line
     * (time, value), as the loop calculated it. oref stops a line early when it gets flat, so the line may end before
     * the horizon: then [RingForecast.reachesHorizon] is false and the value is the last point the loop gave, with its
     * own time. Null when the line has no point after [now].
     */
    fun forecast(points: List<Pair<Long, Double>>, now: Long, horizonMs: Long): RingForecast? {
        val future = points.filter { it.first > now }.sortedBy { it.first }
        if (future.isEmpty()) return null
        val target = now + horizonMs
        val atHorizon = future.filter { abs(it.first - target) <= POINT_TOLERANCE_MS }.minByOrNull { abs(it.first - target) }
        if (atHorizon != null) return RingForecast(atHorizon.first, atHorizon.second, reachesHorizon = true)
        // No point at the horizon (the line ends early, or has a gap): the last point the loop gave before it.
        val before = future.lastOrNull { it.first <= target } ?: return null
        return RingForecast(before.first, before.second, reachesHorizon = false)
    }

    /** A loop prediction point is every 5 minutes; a point within this distance counts as "at" the horizon. */
    const val POINT_TOLERANCE_MS = 150_000L
}

/** One value of the loop's prediction: its time, its value, and whether the line reaches the chosen horizon. */
data class RingForecast(val time: Long, val value: Double, val reachesHorizon: Boolean)

/** How far ahead the "next hours" tile shows the loop's prediction. */
enum class RingHorizon(val hours: Int) { H1(1), H2(2), H3(3) }

