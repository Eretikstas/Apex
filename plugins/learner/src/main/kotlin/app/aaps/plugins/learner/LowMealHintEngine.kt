package app.aaps.plugins.learner

/** A carb entry for the low meal hint: time, grams, and the sensor glucose at that time in mg/dL (null when not known). */
data class MealEntry(val time: Long, val grams: Double, val bgMgdl: Double?)

/**
 * The learner's "meal at a low glucose" hint (advisor, text only). A meal eaten at a low glucose often ends in a low
 * anyway, with or without a bolus: the food lifts too slowly. So the hint says: fast carbs first (4-6 g), then the meal.
 *
 * - A carb entry of [MIN_MEAL_G] g or more in the last [SHOW_MS] (30 min) at a glucose under [LOW_MGDL] (4.5 mmol/L).
 * - The newest such meal is returned; the hint is shown until 30 min after it.
 */
object LowMealHintEngine {

    const val MIN_MEAL_G = 13.0
    const val LOW_MGDL = 81.0
    const val SHOW_MS = 30 * 60_000L

    /** The time of the meal the hint is about, or null. [carbs] are entries with the glucose at their time. */
    fun check(now: Long, carbs: List<MealEntry>): Long? =
        carbs.filter { it.time in (now - SHOW_MS)..now && it.grams >= MIN_MEAL_G && it.bgMgdl != null && it.bgMgdl < LOW_MGDL }
            .maxOfOrNull { it.time }
}
