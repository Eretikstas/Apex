package app.aaps.plugins.learner

/** The likely cause of a low. */
enum class LowCause { MEAL_AT_LOW, REBOUND, AFTER_MEAL, NIGHT, OTHER }

/** One low: when it started, its lowest value (mg/dL), how long it lasted (minutes) and the likely cause. */
data class LowEpisode(val start: Long, val minMgdl: Double, val minutes: Int, val cause: LowCause)

/**
 * The learner's "why was it low" list (advisor, text only). Every low of the history (under 3.9 mmol/L for 10 minutes or
 * more) gets the most likely cause, checked in this order:
 *
 * 1. MEAL_AT_LOW: a meal of 13 g or more within 4 h before, eaten at a glucose under 4.5 mmol/L.
 * 2. REBOUND: a hypo treatment 30 min to 4 h before, and the glucose rose to 6.0 mmol/L or more in between.
 * 3. AFTER_MEAL: a meal of 13 g or more within 4 h before.
 * 4. NIGHT: it starts between 22:00 and 06:00 and none of the above.
 * 5. OTHER.
 *
 * It names a likely cause from the logged data, not a proven one.
 */
object LowCauseEngine {

    const val LOW_MGDL = 70.0
    private const val MEAL_G = 13.0
    private const val MEAL_LOW_MGDL = 81.0
    private const val REBOUND_MGDL = 108.0
    private const val MIN_STEPS = 2
    private const val MEAL_BACK = 48
    private const val TREAT_FROM = 6

    fun episodes(steps: List<LearnerStep>): List<LowEpisode> {
        val out = ArrayList<LowEpisode>()
        var k = 0
        while (k < steps.size) {
            if ((steps[k].bgMgdl ?: Double.MAX_VALUE) >= LOW_MGDL) { k++; continue }
            var e = k
            while (e + 1 < steps.size && (steps[e + 1].bgMgdl ?: Double.MAX_VALUE) < LOW_MGDL) e++
            val len = e - k + 1
            if (len >= MIN_STEPS) out += LowEpisode(steps[k].time, (k..e).minOf { steps[it].bgMgdl ?: Double.MAX_VALUE }, len * 5, cause(steps, k))
            k = e + 1
        }
        return out
    }

    private fun cause(steps: List<LearnerStep>, k: Int): LowCause {
        val from = (k - MEAL_BACK).coerceAtLeast(0)
        val meals = (from until k).filter { steps[it].mealG >= MEAL_G }
        if (meals.any { (steps[it].bgMgdl ?: Double.MAX_VALUE) < MEAL_LOW_MGDL }) return LowCause.MEAL_AT_LOW
        val treat = (from..(k - TREAT_FROM)).filter { steps[it].hypoG > 0.0 }.maxOrNull()
        if (treat != null && (treat until k).any { (steps[it].bgMgdl ?: 0.0) >= REBOUND_MGDL }) return LowCause.REBOUND
        if (meals.isNotEmpty()) return LowCause.AFTER_MEAL
        val h = steps[k].hour
        if (h >= 22 || h < 6) return LowCause.NIGHT
        return LowCause.OTHER
    }
}
