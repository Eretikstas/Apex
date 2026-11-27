package app.aaps.plugins.learner

import kotlin.math.abs
import kotlin.math.exp

/** The result of the night fit: the peak that fits best, the error at the profile peak and at the best one, and the data. */
data class PeakFit(val bestPeakMin: Int, val currentPeakMin: Int, val errNow: Double, val errBest: Double, val points: Int, val nights: Int)

/**
 * The learner's insulin peak check (advisor, text only, it changes nothing). At night without food the glucose change
 * follows the insulin activity: change per 5 min = b - a * activity. For each candidate peak (25-75 min, DIA as in the
 * profile) the activity is computed from all insulin given (basal, boluses, SMB) and a and b are fitted by least squares;
 * the peak with the smallest error fits best.
 *
 * - Points: 00:00-06:00, no meal or hypo carbs in the 5 h before, glucose known now and 15 min later (the change over
 *   15 min / 3, less sensor noise).
 * - At least [MIN_NIGHTS] nights with [MIN_POINTS_NIGHT] points each.
 * - A proposal only when the best peak is [MIN_DIFF_MIN] min or more away from the profile peak AND its error is at
 *   least [MIN_GAIN] (10 %) smaller. The family decides; a peak change changes how fast the loop thinks insulin acts.
 */
object PeakFitEngine {

    val CANDIDATES = (25..75 step 5).toList()
    const val MIN_NIGHTS = 5
    const val MIN_POINTS_NIGHT = 30
    const val MIN_DIFF_MIN = 10
    const val MIN_GAIN = 0.10
    private const val STEP_MIN = 5.0
    private const val FREE_STEPS = 60

    /** Insulin activity (share of a dose acting per minute) of the oref exponential curve at [t] minutes. */
    fun activity(t: Double, peak: Double, diaMin: Double): Double {
        if (t <= 0.0 || t >= diaMin) return 0.0
        val tau = peak * (1 - peak / diaMin) / (1 - 2 * peak / diaMin)
        val a = 2 * tau / diaMin
        val s = 1 / (1 - a + (1 + a) * exp(-diaMin / tau))
        return (s / (tau * tau)) * t * (1 - t / diaMin) * exp(-t / tau)
    }

    fun fit(steps: List<LearnerStep>, currentPeakMin: Int, diaH: Double): PeakFit? {
        val diaMin = diaH * 60
        val back = (diaMin / STEP_MIN).toInt()
        val idx = steps.indices.filter { i ->
            val s = steps[i]
            s.hour in 0..5 && i >= FREE_STEPS && i + 3 < steps.size && s.bgMgdl != null && steps[i + 3].bgMgdl != null &&
                (i - FREE_STEPS..i).none { steps[it].mealG > 0.0 || steps[it].hypoG > 0.0 }
        }
        val byNight = idx.groupBy { steps[it].time / 86_400_000L }.filterValues { it.size >= MIN_POINTS_NIGHT }
        if (byNight.size < MIN_NIGHTS) return null
        val points = byNight.values.flatten()
        val y = points.map { (steps[it + 3].bgMgdl!! - steps[it].bgMgdl!!) / 3.0 }
        fun err(peak: Int): Double {
            fun actAt(i: Int): Double {
                var act = 0.0
                for (j in (i - back).coerceAtLeast(0)..i) act += steps[j].insulinU * activity((i - j) * STEP_MIN, peak.toDouble(), diaMin) * STEP_MIN
                return act
            }
            // The change from i to i+3 comes from the activity of the steps i+1 .. i+3: the same window.
            val x = points.map { i -> (actAt(i + 1) + actAt(i + 2) + actAt(i + 3)) / 3.0 }
            // Least squares for y = b - a * x.
            val n = x.size.toDouble()
            val mx = x.average(); val my = y.average()
            val sxx = x.sumOf { (it - mx) * (it - mx) }
            if (sxx <= 0.0) return Double.MAX_VALUE
            val slope = x.indices.sumOf { (x[it] - mx) * (y[it] - my) } / sxx
            val b = my - slope * mx
            return x.indices.sumOf { val e = y[it] - (b + slope * x[it]); e * e } / n
        }
        val errs = CANDIDATES.associateWith { err(it) }
        val best = errs.minBy { it.value }
        val now = errs[CANDIDATES.minBy { abs(it - currentPeakMin) }] ?: return null
        return PeakFit(best.key, currentPeakMin, now, best.value, points.size, byNight.size)
    }

    /** Whether the fit is clear enough to show a proposal. */
    fun proposes(f: PeakFit): Boolean =
        abs(f.bestPeakMin - f.currentPeakMin) >= MIN_DIFF_MIN && f.errBest <= f.errNow * (1 - MIN_GAIN)
}
