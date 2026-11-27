package app.aaps.plugins.learner

import kotlin.math.floor

/** The meal type the family picks in the calculator. */
enum class MealType { FAST, NORMAL, SLOW }

/**
 * A split meal bolus: [totalU] is what the calculator found for the meal, [firstU] was given at [madeAt], the rest waits
 * for the food to be seen. [carbsG] are the meal carbs.
 */
data class SplitPlan(val madeAt: Long, val type: MealType, val totalU: Double, val firstU: Double, val carbsG: Double = 0.0) {

    val restU: Double get() = (totalU - firstU).coerceAtLeast(0.0)
}

/** What to do with the rest now. */
sealed class SplitDecision {

    /** Not yet: check again later. [why] for the log and the screen. */
    data class Wait(val why: SplitWait) : SplitDecision()

    /** Give [units] now (the rest minus the SMB the loop gave since the meal). */
    data class Give(val units: Double) : SplitDecision()

    /** Nothing more to give: the loop's SMB covered the rest. */
    data object Covered : SplitDecision()

    /** The rest is dropped: the food was not seen in time. The family gets a notice. */
    data object Dropped : SplitDecision()
}

enum class SplitWait { TOO_EARLY, LOW, FALLING, NO_FOOD_SEEN }

/**
 * The learner's split meal bolus rule (lab: 60 % now, the rest from 20 minutes; checked on the body model 27.11 against
 * the lab split). This file only decides; `LearnerPlugin` gives the insulin through the command queue, OFF by default,
 * after the family confirms. It goes to the child only after the bench.
 *
 * - FAST meal: the whole bolus now, no split ([firstShare] = 1).
 * - NORMAL: 60 % now; SLOW: 50 % now (the 50 % is an assumption, not yet tested on the model).
 * - The rest is checked from 20 minutes after the meal, every few minutes.
 * - It is given only when the food is seen: the glucose rose by at least [RISE_MGDL] (0.5 mmol/L) in 15 minutes and
 *   is at [MIN_GIVE_MGDL] (5.5 mmol/L) or more, or AAPS sees carbs absorbing (deviation of [DEVIATION_MGDL] mg/dL per 5
 *   minutes or more) at 5.5 or more.
 * - Never under 4.5 mmol/L and never while the glucose falls.
 * - What is given is the rest minus the SMB the loop gave since the meal; under [MIN_BOLUS_U] -> covered, nothing given.
 * - Not seen by 90 minutes (SLOW: 180) -> the rest is dropped and the family gets a notice.
 */
object SplitBolusEngine {

    const val CHECK_FROM_MS = 20 * 60_000L
    const val NORMAL_LIMIT_MS = 90 * 60_000L
    const val SLOW_LIMIT_MS = 180 * 60_000L
    const val LOW_MGDL = 81.0
    const val MIN_GIVE_MGDL = 99.0
    const val RISE_MGDL = 9.0
    const val DEVIATION_MGDL = 2.0
    const val MIN_BOLUS_U = 0.05

    fun firstShare(type: MealType) = when (type) {
        MealType.FAST   -> 1.0
        MealType.NORMAL -> 0.6
        MealType.SLOW   -> 0.5
    }

    /** The plan for a meal bolus of [totalU] given at [now], rounded down to the pump step [stepU]. */
    fun plan(now: Long, type: MealType, totalU: Double, stepU: Double): SplitPlan {
        val first = floorTo(totalU * firstShare(type), stepU)
        return SplitPlan(now, type, totalU, first)
    }

    /**
     * The decision at [now]. [bgMgdl] is the last sensor glucose, [delta15Mgdl] its change over the last 15 minutes,
     * [deviationMgdl] the AAPS deviation (null when not known), [smbSinceU] the SMB given since the meal.
     */
    fun decide(now: Long, plan: SplitPlan, bgMgdl: Double?, delta15Mgdl: Double?, deviationMgdl: Double?, smbSinceU: Double, stepU: Double): SplitDecision {
        val rest = floorTo(plan.restU - smbSinceU, stepU)
        if (rest < MIN_BOLUS_U) return SplitDecision.Covered
        val age = now - plan.madeAt
        val limit = if (plan.type == MealType.SLOW) SLOW_LIMIT_MS else NORMAL_LIMIT_MS
        if (age > limit) return SplitDecision.Dropped
        if (age < CHECK_FROM_MS) return SplitDecision.Wait(SplitWait.TOO_EARLY)
        if (bgMgdl == null || bgMgdl < LOW_MGDL) return SplitDecision.Wait(SplitWait.LOW)
        if (delta15Mgdl == null || delta15Mgdl < 0) return SplitDecision.Wait(SplitWait.FALLING)
        val seen = bgMgdl >= MIN_GIVE_MGDL && (delta15Mgdl >= RISE_MGDL || (deviationMgdl ?: 0.0) >= DEVIATION_MGDL)
        return if (seen) SplitDecision.Give(rest) else SplitDecision.Wait(SplitWait.NO_FOOD_SEEN)
    }

    private fun floorTo(u: Double, step: Double): Double = if (step <= 0) u else floor(u / step + 1e-9) * step
}
