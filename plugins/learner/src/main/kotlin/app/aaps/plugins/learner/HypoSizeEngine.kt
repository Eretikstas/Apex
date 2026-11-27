package app.aaps.plugins.learner

import kotlin.math.ceil

/** What the learner learned about hypo treatments: the rise in 30 minutes per gram (mg/dL per g) and from how many. */
data class HypoSize(val risePerGMgdl: Double, val treatments: Int)

/**
 * The learner's hypo treatment size (advisor, text only). From the hypo treatments of the history it learns how much one
 * gram of fast carbs lifts the glucose in 30 minutes, and from that how many grams are needed to reach about 5.5 mmol/L.
 * Too little -> the low goes on; too much -> a rebound high and then the loop's insulin -> a new low.
 *
 * - A treatment counts when it is [MAX_G] g or less, the glucose is known at it and 30 minutes later, and no meal and no
 *   other treatment is logged from 30 minutes before to 60 minutes after it.
 * - At least [MIN_TREATMENTS] treatments; the median rise per gram is used. A median of 0 or less -> nothing learned.
 * - The suggestion is never under [MIN_G] g (a real low needs real carbs) and never over [MAX_SUGGEST_G] g.
 */
object HypoSizeEngine {

    const val MAX_G = 12.0
    const val MIN_TREATMENTS = 5
    const val MIN_G = 4
    const val MAX_SUGGEST_G = 10
    const val AIM_MGDL = 99.0
    private const val AFTER_STEPS = 6
    private const val BEFORE_FREE = 6
    private const val AFTER_FREE = 12

    fun learn(steps: List<LearnerStep>): HypoSize? {
        val rises = ArrayList<Double>()
        for ((k, s) in steps.withIndex()) {
            if (s.hypoG <= 0.0 || s.hypoG > MAX_G) continue
            if (k - BEFORE_FREE < 0 || k + AFTER_FREE >= steps.size) continue
            val bg0 = s.bgMgdl ?: continue
            val bg30 = steps[k + AFTER_STEPS].bgMgdl ?: continue
            val clean = (k - BEFORE_FREE..k + AFTER_FREE).all { j -> steps[j].mealG <= 0.0 && (j == k || steps[j].hypoG <= 0.0) }
            if (!clean) continue
            rises += (bg30 - bg0) / s.hypoG
        }
        if (rises.size < MIN_TREATMENTS) return null
        val median = rises.sorted().let { r -> if (r.size % 2 == 1) r[r.size / 2] else (r[r.size / 2 - 1] + r[r.size / 2]) / 2 }
        return if (median > 0.0) HypoSize(median, rises.size) else null
    }

    /** Grams for a glucose of [bgMgdl], from what was learned. */
    fun suggest(size: HypoSize, bgMgdl: Double): Int =
        ceil((AIM_MGDL - bgMgdl).coerceAtLeast(0.0) / size.risePerGMgdl).toInt().coerceIn(MIN_G, MAX_SUGGEST_G)
}
