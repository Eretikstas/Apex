package app.aaps.plugins.learner

import kotlin.math.sqrt

/** One week in numbers: shares in percent, mean in mg/dL, insulin in U a day, hypo carbs in g a day. */
data class WeekStats(
    val inRangePct: Double,
    val low39Pct: Double,
    val low30Pct: Double,
    val high10Pct: Double,
    val cvPct: Double,
    val meanMgdl: Double,
    val insulinPerDay: Double,
    val hypoGPerDay: Double,
    val readings: Int,
)

/** The weekly digest: this week, the week before, and the goals met this week. */
data class WeeklyDigest(val thisWeek: WeekStats, val lastWeek: WeekStats, val goalsMet: Int, val goals: Int)

/**
 * The learner's weekly digest (advisor only): the last 7 days against the 7 days before. Goals (the family's, 03.11):
 * 3.9-10 at least 80 %, under 3.9 at most 2 %, over 10 at most 5 %, CV at most 33 % (aim about 30). Only numbers, no
 * advice: what to change stays with the family.
 */
object WeeklyDigestEngine {

    const val DAYS = 14
    private const val STEPS_PER_DAY = 288
    private const val LOW39 = 70.0
    private const val LOW30 = 54.0
    private const val HIGH10 = 180.0

    fun week(steps: List<LearnerStep>): WeekStats {
        val bgs = steps.mapNotNull { it.bgMgdl }
        val days = steps.size / STEPS_PER_DAY.toDouble()
        if (bgs.isEmpty() || days <= 0) return WeekStats(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0)
        val n = bgs.size.toDouble()
        val mean = bgs.average()
        val sd = sqrt(bgs.sumOf { (it - mean) * (it - mean) } / n)
        return WeekStats(
            inRangePct = 100.0 * bgs.count { it >= LOW39 && it <= HIGH10 } / n,
            low39Pct = 100.0 * bgs.count { it < LOW39 } / n,
            low30Pct = 100.0 * bgs.count { it < LOW30 } / n,
            high10Pct = 100.0 * bgs.count { it > HIGH10 } / n,
            cvPct = if (mean > 0) 100.0 * sd / mean else 0.0,
            meanMgdl = mean,
            insulinPerDay = steps.sumOf { it.insulinU } / days,
            hypoGPerDay = steps.sumOf { it.hypoG } / days,
            readings = bgs.size
        )
    }

    /** [steps] are the last 14 days, oldest first: the second half is this week. */
    fun digest(steps: List<LearnerStep>): WeeklyDigest {
        val half = steps.size / 2
        val now = week(steps.drop(half))
        val goals = listOf(now.inRangePct >= 80.0, now.low39Pct <= 2.0, now.high10Pct <= 5.0, now.cvPct <= 33.0)
        return WeeklyDigest(now, week(steps.take(half)), if (now.readings > 0) goals.count { it } else 0, goals.size)
    }
}
