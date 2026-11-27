package app.aaps.plugins.learner

/** A carb entry: time, grams, and the sensor glucose at that time in mg/dL (null when not known). */
data class CarbEntry(val time: Long, val grams: Double, val bgMgdl: Double?)

/** Whether the guard holds the loop now, from when, until when, and why it is off. */
data class GuardState(val active: Boolean, val startedAt: Long = 0L, val until: Long = 0L, val offReason: GuardOff? = null)

/** Why the guard does not hold the loop. */
enum class GuardOff { NO_HYPO_TREATMENT, MEAL, HIGH }

/**
 * The learner's rebound guard (lab "learning rebound guard", best set 19.11: hypo carbs 37 -> about 24 g a day, under 3.9
 * 5.4 -> 3.3 % on the body model). The pattern it stops: low -> a few grams of fast carbs -> the glucose rises -> the loop
 * gives SMB and high temp basals for the rise -> a new low.
 *
 * SUPPRESS ONLY: while it holds, SMB are off and the temp basal is capped at [BASAL_CAP] times the profile basal. It never
 * adds insulin, never cancels a bolus the family gives and never raises a limit.
 *
 * - A hypo treatment is a carb entry of [HYPO_MAX_G] g or less at a sensor glucose under 4.5 mmol/L (the family's hypo
 *   treatments are 4-6 g).
 * - The guard holds for [WINDOW_MS] (2 hours) after the last hypo treatment.
 * - It lets go at once when a meal is logged (any other carb entry from 30 minutes before the first hypo treatment of the
 *   window): the rise is food then, and the loop must cover it. All carb entries of the family are real.
 * - It lets go when the glucose is [RELEASE_MGDL] (13.9 mmol/L) or more: a long high must not be left alone.
 * - The glucose is the smoothed value the loop uses (see `LearnerHistory.reboundInputs`), not the raw reading.
 */
object ReboundGuardEngine {

    const val HYPO_MAX_G = 12.0
    const val HYPO_BG_MGDL = 81.0
    const val WINDOW_MS = 2 * 3_600_000L
    const val MEAL_BEFORE_MS = 30 * 60_000L
    const val RELEASE_MGDL = 250.0
    const val BASAL_CAP = 1.5

    fun isHypoTreatment(c: CarbEntry) = c.grams > 0 && c.grams <= HYPO_MAX_G && c.bgMgdl != null && c.bgMgdl < HYPO_BG_MGDL

    /** The state at [now]: [carbs] are the carb entries of the last few hours, [bgNowMgdl] the last sensor glucose. */
    fun state(now: Long, carbs: List<CarbEntry>, bgNowMgdl: Double?): GuardState {
        val recent = carbs.filter { it.time <= now && it.time > now - WINDOW_MS - MEAL_BEFORE_MS }
        val hypos = recent.filter { isHypoTreatment(it) && it.time > now - WINDOW_MS }
        if (hypos.isEmpty()) return GuardState(false, offReason = GuardOff.NO_HYPO_TREATMENT)
        val first = hypos.minOf { it.time }
        val last = hypos.maxOf { it.time }
        if (recent.any { !isHypoTreatment(it) && it.grams > 0 && it.time >= first - MEAL_BEFORE_MS }) return GuardState(false, offReason = GuardOff.MEAL)
        if (bgNowMgdl != null && bgNowMgdl >= RELEASE_MGDL) return GuardState(false, offReason = GuardOff.HIGH)
        return GuardState(true, first, last + WINDOW_MS)
    }

    /** The highest temp basal allowed while the guard holds, in U/h, for a profile basal of [profileBasal] U/h. */
    fun basalCap(profileBasal: Double) = profileBasal * BASAL_CAP
}
