package app.aaps.plugins.aps.openAPSAIMI.safety

import java.util.Locale
import kotlin.math.min

/**
 * Hypo exposure guard: a long memory of lows, after the auto-configuration of Boost V5
 * (tim2000s/Boost-in-AAPS_3.4, `BoostV5AutoConfig`).
 *
 * AIMI already reacts to a low of the last hours. This guard looks at the last [LOOKBACK_DAYS]
 * days instead: when the time below 70 mg/dL (3.9 mmol/L) or below 54 mg/dL (3.0 mmol/L) is above
 * the international targets, everything that ADDS insulin above the normal dose is made smaller,
 * and what AIMI has learned may make the dosing gentler, never stronger.
 *
 * It never touches what removes insulin: a lower basal, a suspend, the hypo guards.
 *
 * Pure logic, no Android: the readings come from [HypoExposureMonitor].
 */
object HypoExposureGuard {

    /** Days of glucose looked at. */
    const val LOOKBACK_DAYS = 14L

    /** Fewer days with readings than this: not enough to judge, the guard does nothing. */
    const val MIN_DAYS = 7

    /** Fewer readings than this (about 7 days of 5-minute readings with gaps): the guard does nothing. */
    const val MIN_READINGS = 1500

    const val LOW_MGDL = 70.0
    const val SEVERE_LOW_MGDL = 54.0

    /** International targets: time below 70 under 4 %, time below 54 under 1 %. */
    const val TARGET_BELOW_70_PCT = 4.0
    const val TARGET_BELOW_54_PCT = 1.0

    /** "Hypo-prone" cut-points, the same as in Boost V5. */
    const val HYPO_PRONE_BELOW_70_PCT = 6.0
    const val HYPO_PRONE_BELOW_54_PCT = 1.5

    /** Scale on the extra insulin above target, and when hypo-prone (Boost V5 "Aggression"). */
    const val SCALE_ABOVE_TARGET = 0.92
    const val SCALE_HYPO_PRONE = 0.85

    /** The guard setting: 0 off, 1 watch only (logs what it would do), 2 on. */
    enum class Mode(val value: Int) {
        OFF(0), WATCH(1), ON(2);

        companion object {

            fun of(value: Int): Mode = entries.firstOrNull { it.value == value } ?: WATCH
        }
    }

    enum class Level { NOT_ENOUGH_DATA, NORMAL, ABOVE_TARGET, HYPO_PRONE }

    /** Time below range over the window. */
    data class Exposure(
        val daysWithData: Int,
        val readings: Int,
        val below70Pct: Double,
        val below54Pct: Double,
    )

    /**
     * What the guard asks for.
     *
     * @property scale multiplier on insulin above the normal dose, 1.0 when nothing is asked
     * @property stopExtraBoosts the extra boosts (high BG SMB ×1.2) are off
     * @property learnedOnlyGentler learned factors above 1.0 (more insulin) are not used
     */
    data class Verdict(
        val level: Level,
        val exposure: Exposure?,
        val scale: Double,
        val stopExtraBoosts: Boolean,
        val learnedOnlyGentler: Boolean,
    ) {

        /** True when the guard asks for anything at all. */
        val asksForLess: Boolean get() = scale < 1.0 || stopExtraBoosts || learnedOnlyGentler

        companion object {

            val NEUTRAL = Verdict(Level.NOT_ENOUGH_DATA, null, 1.0, stopExtraBoosts = false, learnedOnlyGentler = false)
        }
    }

    /**
     * Time below range from glucose readings.
     *
     * @param readings pairs of time (ms) and glucose (mg/dL), any order; bad values are left out
     * @param dayOf the day number of a time, for the count of days with data (local days in the app)
     */
    fun exposureOf(readings: List<Pair<Long, Double>>, dayOf: (Long) -> Long): Exposure {
        val valid = readings.filter { it.second.isFinite() && it.second > 0.0 }
        if (valid.isEmpty()) return Exposure(daysWithData = 0, readings = 0, below70Pct = 0.0, below54Pct = 0.0)
        val days = valid.map { dayOf(it.first) }.distinct().size
        val below70 = valid.count { it.second < LOW_MGDL }
        val below54 = valid.count { it.second < SEVERE_LOW_MGDL }
        return Exposure(
            daysWithData = days,
            readings = valid.size,
            below70Pct = below70 * 100.0 / valid.size,
            below54Pct = below54 * 100.0 / valid.size,
        )
    }

    /** The level and what it asks for. Not enough data: neutral. */
    fun verdictOf(exposure: Exposure): Verdict {
        if (exposure.daysWithData < MIN_DAYS || exposure.readings < MIN_READINGS) {
            return Verdict.NEUTRAL.copy(exposure = exposure)
        }
        val hypoProne = exposure.below70Pct > HYPO_PRONE_BELOW_70_PCT || exposure.below54Pct > HYPO_PRONE_BELOW_54_PCT
        val aboveTarget = exposure.below70Pct > TARGET_BELOW_70_PCT || exposure.below54Pct > TARGET_BELOW_54_PCT
        return when {
            hypoProne -> Verdict(Level.HYPO_PRONE, exposure, SCALE_HYPO_PRONE, stopExtraBoosts = true, learnedOnlyGentler = true)
            aboveTarget -> Verdict(Level.ABOVE_TARGET, exposure, SCALE_ABOVE_TARGET, stopExtraBoosts = false, learnedOnlyGentler = false)
            else -> Verdict(Level.NORMAL, exposure, 1.0, stopExtraBoosts = false, learnedOnlyGentler = false)
        }
    }

    /** What is applied in a mode: only [Mode.ON] changes doses. */
    fun applied(verdict: Verdict, mode: Mode): Verdict = if (mode == Mode.ON) verdict else Verdict.NEUTRAL.copy(exposure = verdict.exposure)

    /** A dose that adds insulin (an SMB, a dose cap), scaled down. Never raised, never below 0. */
    fun scaleDose(units: Double, verdict: Verdict): Double =
        if (units <= 0.0 || !units.isFinite()) units else units * verdict.scale.coerceIn(0.0, 1.0)

    /**
     * A multiplier where only the part above 1.0 adds insulin (an adaptive basal ×1.5): that part is
     * scaled, and removed for a hypo-prone history. A multiplier at or below 1.0 is left as it is.
     */
    fun scaleBoostMultiplier(multiplier: Double, verdict: Verdict): Double {
        if (multiplier <= 1.0 || !multiplier.isFinite()) return multiplier
        if (verdict.learnedOnlyGentler) return 1.0
        return 1.0 + (multiplier - 1.0) * verdict.scale.coerceIn(0.0, 1.0)
    }

    /** A learned factor where above 1.0 means more insulin: kept at most 1.0 when asked. */
    fun learnedFactor(factor: Double, verdict: Verdict): Double =
        if (verdict.learnedOnlyGentler && factor.isFinite()) min(factor, 1.0) else factor

    /** One log line, the same in every mode. */
    fun describe(verdict: Verdict, mode: Mode): String {
        val e = verdict.exposure
        val data = if (e == null) "no data" else
            "<70 ${"%.1f".format(Locale.US, e.below70Pct)}% <54 ${"%.1f".format(Locale.US, e.below54Pct)}% " +
                "(${e.daysWithData} d, ${e.readings} readings)"
        val ask = if (verdict.asksForLess)
            "×${"%.2f".format(Locale.US, verdict.scale)}" +
                (if (verdict.stopExtraBoosts) ", extra boosts off" else "") +
                (if (verdict.learnedOnlyGentler) ", learned only gentler" else "")
        else "nothing"
        val how = when (mode) {
            Mode.OFF -> "off"
            Mode.WATCH -> "watch only, would ask $ask"
            Mode.ON -> "on, asks $ask"
        }
        return "🩸 HYPO_EXPOSURE ${verdict.level}: $data, $how"
    }
}
