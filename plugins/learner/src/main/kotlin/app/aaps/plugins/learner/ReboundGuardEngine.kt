package app.aaps.plugins.learner

/** A carb entry: time, grams, and the sensor glucose at that time in mg/dL (null when not known). */
data class CarbEntry(val time: Long, val grams: Double, val bgMgdl: Double?)

/** Whether the guard holds the loop now, from when, until when, and why it is off. */
data class GuardState(val active: Boolean, val startedAt: Long = 0L, val until: Long = 0L, val offReason: GuardOff? = null)

/** Why the guard does not hold the loop. */
enum class GuardOff { NO_HYPO_TREATMENT, MEAL, HIGH, RISE }

/**
 * The learner's rebound guard. The pattern it stops: low -> a few grams of fast carbs -> the glucose rises -> the loop
 * gives SMB and high temp basals for the rise -> a new low.
 *
 * The numbers are the lab's (27.11, body model, 3 seeds, lab token RGAPP2 with RGWIN_180 and RGCAP_1.0): the values the
 * lab's learning guard ends at, fixed. With the app's split bolus it gave under 3.9 4.7 % (best set 19.11: 4.9 %), under
 * 3.0 0.17 % (0.30 %), over 10 4.8 % (4.8 %), hypo carbs 26.7 g a day (29.0).
 *
 * SUPPRESS ONLY: while it holds, SMB are off and the temp basal is at most [BASAL_CAP] times the profile basal (no high
 * temps). It never adds insulin, never cancels a bolus the family gives and never raises a limit.
 *
 * - A hypo treatment is a carb entry of [HYPO_MAX_G] g or less at a glucose under 4.5 mmol/L (the family's hypo treatments
 *   are 4-6 g).
 * - The guard holds for [WINDOW_MS] (3 hours) after the last hypo treatment.
 * - It lets go at once when a meal is logged (any other carb entry from 30 minutes before the first hypo treatment of the
 *   window): the rise is food then, and the loop must cover it. All carb entries of the family are real.
 * - It lets go when the glucose is [RELEASE_MGDL] (13.9 mmol/L) or more: a long high must not be left alone.
 * - It lets go early when insulin is really needed (all with the 5 minute delta averaged over 15 minutes):
 *   a strong rise (over 9 mmol/L and rising more than 3.6 mg/dL per 5 min); a rise the treatment does not explain (80 mg/dL
 *   over the glucose at the last treatment and still rising more than 1 mg/dL per 5 min); a flat high (over 10 mmol/L,
 *   delta -1..1) later than 45 minutes after the last treatment.
 * - The glucose is the smoothed value the loop uses (see `LearnerHistory.reboundInputs`), not the raw reading.
 */
object ReboundGuardEngine {

    const val HYPO_MAX_G = 12.0
    const val HYPO_BG_MGDL = 81.0
    const val WINDOW_MS = 3 * 3_600_000L
    const val MEAL_BEFORE_MS = 30 * 60_000L
    const val RELEASE_MGDL = 250.0
    const val BASAL_CAP = 1.0
    const val STRONG_RISE_MGDL = 162.0
    const val STRONG_RISE_DELTA = 3.6
    const val UNEXPLAINED_MGDL = 80.0
    const val UNEXPLAINED_DELTA = 1.0
    const val FLAT_HIGH_MGDL = 180.0
    const val FLAT_DELTA = 1.0
    const val FLAT_AFTER_MS = 45 * 60_000L

    fun isHypoTreatment(c: CarbEntry) = c.grams > 0 && c.grams <= HYPO_MAX_G && c.bgMgdl != null && c.bgMgdl < HYPO_BG_MGDL

    /**
     * The state at [now]: [carbs] are the carb entries of the last few hours, [bgNowMgdl] the last glucose, [deltaMgdl]
     * its change per 5 minutes averaged over the last 15 minutes (null when not known: no early release then).
     */
    fun state(now: Long, carbs: List<CarbEntry>, bgNowMgdl: Double?, deltaMgdl: Double? = null): GuardState {
        val recent = carbs.filter { it.time <= now && it.time > now - WINDOW_MS - MEAL_BEFORE_MS }
        val hypos = recent.filter { isHypoTreatment(it) && it.time > now - WINDOW_MS }
        if (hypos.isEmpty()) return GuardState(false, offReason = GuardOff.NO_HYPO_TREATMENT)
        val first = hypos.minOf { it.time }
        val last = hypos.maxOf { it.time }
        if (recent.any { !isHypoTreatment(it) && it.grams > 0 && it.time >= first - MEAL_BEFORE_MS }) return GuardState(false, offReason = GuardOff.MEAL)
        if (bgNowMgdl != null && bgNowMgdl >= RELEASE_MGDL) return GuardState(false, offReason = GuardOff.HIGH)
        if (bgNowMgdl != null && deltaMgdl != null) {
            val lastBg = hypos.maxBy { it.time }.bgMgdl ?: 0.0
            val strongRise = bgNowMgdl > STRONG_RISE_MGDL && deltaMgdl > STRONG_RISE_DELTA
            val unexplained = bgNowMgdl > lastBg + UNEXPLAINED_MGDL && deltaMgdl > UNEXPLAINED_DELTA
            val flatHigh = bgNowMgdl > FLAT_HIGH_MGDL && deltaMgdl in -FLAT_DELTA..FLAT_DELTA && now - last > FLAT_AFTER_MS
            if (strongRise || unexplained || flatHigh) return GuardState(false, offReason = GuardOff.RISE)
        }
        return GuardState(true, first, last + WINDOW_MS)
    }

    /** The highest temp basal allowed while the guard holds, in U/h, for a profile basal of [profileBasal] U/h. */
    fun basalCap(profileBasal: Double) = profileBasal * BASAL_CAP
}
