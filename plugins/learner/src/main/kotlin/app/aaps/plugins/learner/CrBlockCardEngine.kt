package app.aaps.plugins.learner

/** A meal block of the day: [fromHour] inclusive to [toHour] exclusive, local time. */
enum class MealBlock(val fromHour: Int, val toHour: Int) {

    BREAKFAST(5, 11), LUNCH(11, 16), DINNER(16, 21);

    companion object {

        fun of(hour: Int): MealBlock? = entries.firstOrNull { hour >= it.fromHour && hour < it.toHour }
    }
}

/** What the CR block card saw: per block the meals and the share followed by a low, and the block it proposes (or null). */
data class CrBlockCard(val meals: Map<MealBlock, Int>, val lowPct: Map<MealBlock, Double>, val weaker: MealBlock?)

/**
 * The learner's CR card per meal block (advisor, SAFETY ONLY: it only ever proposes a WEAKER CR, that is less insulin).
 * Real data 27.11: meals at dinner (16-21 h) were followed by a low within 4 h in 51 % of cases (79 % since the diet),
 * breakfast and lunch in 17-38 %, with the same bolus per gram.
 *
 * - The last [DAYS] days; meals of [MIN_MEAL_G] g or more; a meal "ends low" when a reading under 3.9 mmol/L comes 15 min
 *   to 4 h after it.
 * - A block counts with [MIN_MEALS] meals or more.
 * - Proposal: the block with the highest share, when it is [HIGH_PCT] % or more and every other counted block is
 *   [OTHER_MAX_PCT] % or less: that block's CR one step ([STEP], 10 %) weaker. At most one block.
 * - Never a stronger CR: more insulin is the family's own decision.
 */
object CrBlockCardEngine {

    const val DAYS = 14
    const val MIN_MEAL_G = 13.0
    const val MIN_MEALS = 8
    const val HIGH_PCT = 40.0
    const val OTHER_MAX_PCT = 30.0
    const val STEP = 0.10
    private const val LOW_MGDL = 70.0
    private const val FROM_STEP = 3
    private const val TO_STEP = 48

    fun card(steps: List<LearnerStep>): CrBlockCard {
        val meals = HashMap<MealBlock, Int>()
        val lows = HashMap<MealBlock, Int>()
        for ((k, s) in steps.withIndex()) {
            if (s.mealG < MIN_MEAL_G) continue
            val block = MealBlock.of(s.hour) ?: continue
            // A meal near the end of the data has not had its 4 hours yet.
            if (k + TO_STEP >= steps.size) continue
            meals[block] = (meals[block] ?: 0) + 1
            val low = (k + FROM_STEP..k + TO_STEP).any { (steps[it].bgMgdl ?: Double.MAX_VALUE) < LOW_MGDL }
            if (low) lows[block] = (lows[block] ?: 0) + 1
        }
        val counted = meals.filterValues { it >= MIN_MEALS }.keys
        val pct = counted.associateWith { 100.0 * (lows[it] ?: 0) / (meals[it] ?: 1) }
        val top = pct.maxByOrNull { it.value }
        val weaker = top?.takeIf { t -> t.value >= HIGH_PCT && pct.filterKeys { it != t.key }.values.all { it <= OTHER_MAX_PCT } }?.key
        return CrBlockCard(meals, pct, weaker)
    }

    /** The CR (g/U) of the hours of [block] one step weaker: [ic] per hour of the day, the result for the block hours only. */
    fun weakerHours(ic: DoubleArray, block: MealBlock): Map<Int, Double> =
        (block.fromHour until block.toHour).associateWith { h -> Math.round(ic[h] * (1 + STEP) * 100) / 100.0 }
}
