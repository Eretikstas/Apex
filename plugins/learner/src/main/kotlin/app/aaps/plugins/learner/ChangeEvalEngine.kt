package app.aaps.plugins.learner

import kotlin.math.sqrt

/** Glucose numbers of a few days: shares in percent, CV in percent, the lowest value in mg/dL. */
data class EvalStats(val low39Pct: Double, val low30Pct: Double, val high10Pct: Double, val cvPct: Double, val minMgdl: Double, val readings: Int)

/** The verdict on a change after 3 days. */
enum class EvalVerdict { BETTER, SAME, WORSE, NOT_ENOUGH_DATA }

/** The result: the numbers before and after, the verdict, and whether the rollback rule was hit. */
data class ChangeEval(val before: EvalStats, val after: EvalStats, val verdict: EvalVerdict, val rollback: Boolean)

/**
 * The learner's change evaluation (advisor only): a change applied with "Внести" is judged on the 3 days after it, against
 * the 3 days before it. The family's rules (a low is worse than a high):
 *
 * - Rollback rule: under 3.0 mmol/L more than 1 % on two days in a row, or a value under 2.5 mmol/L -> "check whether to
 *   undo it". The learner never undoes anything itself.
 * - WORSE: the rollback rule, or under 3.9 up by 1.5 points or more, or under 3.0 up by 0.5 points or more.
 * - BETTER: under 3.9 not up by more than 0.5 points, CV not up by more than 3 points, and under 3.9 down by 1 point or
 *   more or over 10 down by 3 points or more.
 * - Otherwise SAME. Under one day of readings on either side -> NOT_ENOUGH_DATA.
 *
 * Days with a cannula failure, a new sensor or illness are not left out here: the text says so.
 */
object ChangeEvalEngine {

    const val DAYS = 3
    const val DAY_MS = 86_400_000L

    private const val MIN_READINGS = 288
    private const val LOW39 = 70.0
    private const val LOW30 = 54.0
    private const val HIGH10 = 180.0
    private const val MIN_OK = 45.0
    private const val ROLLBACK_LOW30_PCT = 1.0

    fun stats(steps: List<LearnerStep>): EvalStats {
        val bgs = steps.mapNotNull { it.bgMgdl }
        if (bgs.isEmpty()) return EvalStats(0.0, 0.0, 0.0, 0.0, 0.0, 0)
        val n = bgs.size.toDouble()
        val mean = bgs.average()
        val sd = sqrt(bgs.sumOf { (it - mean) * (it - mean) } / n)
        return EvalStats(
            low39Pct = 100.0 * bgs.count { it < LOW39 } / n,
            low30Pct = 100.0 * bgs.count { it < LOW30 } / n,
            high10Pct = 100.0 * bgs.count { it > HIGH10 } / n,
            cvPct = if (mean > 0) 100.0 * sd / mean else 0.0,
            minMgdl = bgs.min(),
            readings = bgs.size
        )
    }

    /**
     * The rollback rule on the days after a change made at [changeAt]: whole 24 hour blocks from the change, so a day here
     * is "the first 24 hours after it", and so on. A value under 2.5 counts at once, also in a block not yet complete.
     */
    fun rollback(changeAt: Long, after: List<LearnerStep>, now: Long): Boolean {
        val steps = after.filter { it.time >= changeAt && it.time <= now }
        if (steps.any { (it.bgMgdl ?: Double.MAX_VALUE) < MIN_OK }) return true
        val complete = ((now - changeAt) / DAY_MS).toInt()
        var lastOver = false
        for (d in 0 until complete) {
            val day = steps.filter { it.time >= changeAt + d * DAY_MS && it.time < changeAt + (d + 1) * DAY_MS }.mapNotNull { it.bgMgdl }
            val over = day.size >= MIN_READINGS / 2 && 100.0 * day.count { it < LOW30 } / day.size > ROLLBACK_LOW30_PCT
            if (over && lastOver) return true
            lastOver = over
        }
        return false
    }

    fun evaluate(before: EvalStats, afterSteps: List<LearnerStep>, changeAt: Long, now: Long): ChangeEval {
        val after = stats(afterSteps.filter { it.time >= changeAt })
        val rollback = rollback(changeAt, afterSteps, now)
        val verdict = when {
            rollback                                                       -> EvalVerdict.WORSE
            before.readings < MIN_READINGS || after.readings < MIN_READINGS -> EvalVerdict.NOT_ENOUGH_DATA
            after.low39Pct - before.low39Pct >= 1.5 || after.low30Pct - before.low30Pct >= 0.5 -> EvalVerdict.WORSE
            after.low39Pct - before.low39Pct <= 0.5 && after.cvPct - before.cvPct <= 3.0 &&
                (before.low39Pct - after.low39Pct >= 1.0 || before.high10Pct - after.high10Pct >= 3.0) -> EvalVerdict.BETTER
            else                                                           -> EvalVerdict.SAME
        }
        return ChangeEval(before, after, verdict, rollback)
    }
}
