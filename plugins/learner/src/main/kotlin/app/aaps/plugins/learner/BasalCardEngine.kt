package app.aaps.plugins.learner

import kotlin.math.exp
import kotlin.math.roundToLong

/** One 5 minute step of history, oldest first. */
data class LearnerStep(
    val time: Long,
    val hour: Int,
    val minute: Int,
    /** Sensor glucose, mg/dL, null when missing. */
    val bgMgdl: Double?,
    /** Insulin given in these 5 minutes: basal (temp or profile) plus boluses and SMB, units. */
    val insulinU: Double,
    /** Meal carbs entered in these 5 minutes, grams. */
    val mealG: Double,
    /** Hypo treatment carbs entered in these 5 minutes, grams. */
    val hypoG: Double,
    /** Cannula age at this step, hours, null when not known. */
    val cannulaAgeH: Double?,
)

/** How an hour's basal was found. */
enum class HourSource { MEASURED, ESTIMATE, NONE }

data class HourResult(val hour: Int, val profile: Double, val need: Double?, val source: HourSource, val windows: Int)

/** One proposed change: hour, basal now, basal proposed (U/h). */
data class BasalChange(val hour: Int, val from: Double, val to: Double)

/** What the engine remembers between two cards: the side each hour was on, and how many cards in a row. */
data class BasalCardState(val lastDir: IntArray = IntArray(24), val count: IntArray = IntArray(24)) {

    override fun equals(other: Any?): Boolean =
        other is BasalCardState && lastDir.contentEquals(other.lastDir) && count.contentEquals(other.count)

    override fun hashCode(): Int = 31 * lastDir.contentHashCode() + count.contentHashCode()
}

data class BasalCard(val hours: List<HourResult>, val changes: List<BasalChange>, val state: BasalCardState)

/**
 * The learner's basal card, the same rules as tested on the body model of the replay lab (lab tokens LEARNB5, LBLOWMAX_6,
 * LBFILL2, LBMIN_2, CANCH_72):
 *
 * - The need of an hour comes from clean windows: on the full hour, 5 h and more after a meal, no meal in the hour,
 *   glucose 3.9-12 at the start, a cannula not older than 72 h. Need = insulin acting in the hour + glucose change / ISF
 *   - hypo carbs (from 30 min before) / CR.
 * - An hour is "measured" with 2 or more windows (median), smoothed half with its measured neighbours. An hour with no
 *   windows takes the value between the nearest measured hours before and after it ("estimate").
 * - A change needs the same side (more than +10 % or less than -10 %) on two cards in a row; a raise is blocked when
 *   more than 6 five-minute readings under 3.9 were within 2 h of the hour in the last 7 days. Each step goes half way
 *   to the need and at most 20 %.
 */
class BasalCardEngine(
    private val isfMgdl: Double,
    private val icG: Double,
    peakMin: Double,
    diaH: Double,
) {

    private val kernel = DoubleArray(KERNEL_STEPS + 1) { j -> iobLeft(peakMin, diaH, 5.0 * j) - iobLeft(peakMin, diaH, 5.0 * (j + 1)) }

    fun card(steps: List<LearnerStep>, profileBasal: DoubleArray, state: BasalCardState): BasalCard {
        require(profileBasal.size == 24)
        val needs = hourNeeds(steps.takeLast(NEED_DAYS * STEPS_PER_DAY))
        val own = DoubleArray(24) { if (needs[it].size >= MIN_WINDOWS) median(needs[it]) else Double.NaN }
        val lastDir = state.lastDir.copyOf()
        val count = state.count.copyOf()
        val hours = ArrayList<HourResult>(24)
        val changes = ArrayList<BasalChange>()
        val recent = steps.takeLast(LOW_DAYS * STEPS_PER_DAY)
        for (h in 0 until 24) {
            val prof = profileBasal[h]
            val v: Double
            val source: HourSource
            if (own[h].isNaN()) {
                val prev = (1..23).firstOrNull { !own[(h - it + 24) % 24].isNaN() }
                val next = (1..23).firstOrNull { !own[(h + it) % 24].isNaN() }
                if (prev == null || next == null) {
                    lastDir[h] = 0; count[h] = 0
                    hours += HourResult(h, prof, null, HourSource.NONE, 0)
                    continue
                }
                val a = own[(h - prev + 24) % 24]
                val b = own[(h + next) % 24]
                v = a + (b - a) * prev / (prev + next).toDouble()
                source = HourSource.ESTIMATE
            } else {
                val nb = listOf(own[(h + 23) % 24], own[(h + 1) % 24]).filter { !it.isNaN() }
                v = if (nb.isEmpty()) own[h] else 0.5 * own[h] + 0.5 * nb.average()
                source = HourSource.MEASURED
            }
            hours += HourResult(h, prof, v, source, needs[h].size)
            if (prof <= 0.0) continue
            val dir = if (v > prof * 1.10) 1 else if (v < prof * 0.90) -1 else 0
            if (dir == 1 && lowsNear(recent, h) > LOW_MAX) {
                lastDir[h] = 0; count[h] = 0
                continue
            }
            if (dir != 0 && dir == lastDir[h]) count[h]++ else count[h] = if (dir != 0) 1 else 0
            if (dir != 0 && count[h] >= CARDS_IN_A_ROW) {
                val target = prof + 0.5 * (v - prof)
                val nv = round2(target.coerceIn(prof * (1 - MAX_STEP), prof * (1 + MAX_STEP)))
                if (nv != prof) changes += BasalChange(h, prof, nv)
                lastDir[h] = 0; count[h] = 0
            } else lastDir[h] = dir
        }
        return BasalCard(hours, changes, BasalCardState(lastDir, count))
    }

    /** Clean one-hour windows per hour of the day; see the class description. */
    internal fun hourNeeds(steps: List<LearnerStep>): Array<ArrayList<Double>> {
        val w = steps.size
        val needs = Array(24) { ArrayList<Double>() }
        if (w < KERNEL_STEPS + 13) return needs
        val act = DoubleArray(w) { k -> var a = 0.0; for (j in 0..minOf(KERNEL_STEPS, k)) a += steps[k - j].insulinU * kernel[j]; a }
        val since = IntArray(w)
        var last = -100_000
        for (k in 0 until w) {
            if (steps[k].mealG > 0) last = k
            since[k] = (k - last) * 5
        }
        for (k in KERNEL_STEPS until w - 12) {
            val s = steps[k]
            val e = steps[k + 12]
            if (s.minute != 0 || since[k] < 300) continue
            // Unknown cannula age = no change in the history read, so older than 72 h.
            if ((s.cannulaAgeH ?: Double.MAX_VALUE) > CANNULA_MAX_H || (e.cannulaAgeH ?: Double.MAX_VALUE) > CANNULA_MAX_H) continue
            val bg0 = s.bgMgdl ?: continue
            val bg1 = e.bgMgdl ?: continue
            // A gap in the sensor can hide a low and its treatment: at most 2 readings of the hour may be missing.
            if ((k..k + 12).count { steps[it].bgMgdl == null } > MAX_MISSING) continue
            if (bg0 !in 70.0..216.0) continue
            if ((k until k + 12).any { steps[it].mealG > 0 }) continue
            var a = 0.0
            for (j in k until k + 12) a += act[j]
            var hy = 0.0
            for (j in maxOf(0, k - 6) until k + 12) hy += steps[j].hypoG
            needs[s.hour].add(a + (bg1 - bg0) / isfMgdl - hy / icG)
        }
        return needs
    }

    private fun lowsNear(steps: List<LearnerStep>, h: Int): Int = steps.count {
        val dh = minOf((it.hour - h + 24) % 24, (h - it.hour + 24) % 24)
        dh <= 2 && (it.bgMgdl ?: 200.0) < 70.0
    }

    companion object {

        const val STEPS_PER_DAY = 288
        const val NEED_DAYS = 14
        const val LOW_DAYS = 7
        const val MIN_WINDOWS = 2
        const val MAX_MISSING = 2
        const val LOW_MAX = 6
        const val CARDS_IN_A_ROW = 2
        const val MAX_STEP = 0.20
        const val CANNULA_MAX_H = 72.0
        private const val KERNEL_STEPS = 60

        private fun median(a: List<Double>) = a.sorted()[a.size / 2]
        private fun round2(v: Double) = (v * 100).roundToLong() / 100.0

        /** Insulin on board left of one unit after [m] minutes, oref exponential curve. */
        internal fun iobLeft(peak: Double, diaHours: Double, m: Double): Double {
            val td = diaHours * 60.0
            if (m < 0) return 1.0
            if (m >= td) return 0.0
            val tau = peak * (1 - peak / td) / (1 - 2 * peak / td)
            val a = 2 * tau / td
            val s = 1 / (1 - a + (1 + a) * exp(-td / tau))
            return 1 - s * (1 - a) * ((m * m / (tau * td * (1 - a)) - m / tau - 1) * exp(-m / tau) + 1)
        }
    }
}
