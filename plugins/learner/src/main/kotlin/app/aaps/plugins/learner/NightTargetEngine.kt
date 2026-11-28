package app.aaps.plugins.learner

import app.aaps.core.data.model.data.TargetBlock
import kotlin.math.abs

/** What the night target card saw: nights with enough data, nights with a low, the share of night time over 10. */
data class NightTargetCard(val nights: Int, val lowNights: Int, val highPct: Double, val propose: Boolean)

/** A night target proposal, in the units of the profile: the target low per night hour now and proposed, and the high. */
data class NightTargetProposal(
    val profileName: String,
    val madeAt: Long,
    val lowNights: Int,
    val nights: Int,
    val fromLow: Map<Int, Double>,
    val toLow: Map<Int, Double>,
    val toHigh: Map<Int, Double>
)

/**
 * The learner's night target card (advisor: a proposal with "Внести" / "Отказаться", off by default). It only ever
 * proposes a HIGHER night target = less insulin at night.
 *
 * - Night = 21:00 to 06:00 (the hours the lab tested). Last [DAYS] days; a night counts with 70 % of its readings.
 * - A night "with a low": 15 minutes or more under 3.9 mmol/L.
 * - Proposal when there are [MIN_NIGHTS] nights or more, a low in [LOW_NIGHT_PCT] % of them or more, and night time over
 *   10 mmol/L is [MAX_HIGH_PCT] % or less: the night target + 1.0 mmol/L, never over 8.0 mmol/L.
 * - Lab (synthetic child): a night target of 7.0 gave fewer lows and less hypo carbs, and more highs. A trade-off: the
 *   family decides. 40 %, 10 %, +1.0 and 8.0 are our estimates.
 */
object NightTargetEngine {

    const val DAYS = 14
    const val MIN_NIGHTS = 7
    const val LOW_NIGHT_PCT = 40.0
    const val MAX_HIGH_PCT = 10.0
    const val STEP_MGDL = 18.0
    const val MAX_TARGET_MGDL = 144.0
    val HOURS: List<Int> = (21..23) + (0..5)

    private const val LOW = 70.0
    private const val HIGH = 180.0
    private const val LOW_STEPS = 3
    private const val NIGHT_STEPS = 9 * 12
    private const val HOUR_MS = 3_600_000L

    fun card(steps: List<LearnerStep>): NightTargetCard {
        val nights = steps.filter { it.hour in HOURS }.groupBy { s ->
            // The start of its night (21:00 of the day before for the hours after midnight).
            s.time - ((s.hour - 21 + 24) % 24) * HOUR_MS - s.minute * 60_000L
        }
        var valid = 0
        var low = 0
        var readings = 0
        var high = 0
        for ((_, night) in nights) {
            val bgs = night.sortedBy { it.time }.map { it.bgMgdl }
            if (bgs.count { it != null } < NIGHT_STEPS * 0.7) continue
            valid++
            var run = 0
            var hadLow = false
            for (bg in bgs) {
                run = if (bg != null && bg < LOW) run + 1 else 0
                if (run >= LOW_STEPS) hadLow = true
            }
            if (hadLow) low++
            readings += bgs.count { it != null }
            high += bgs.count { it != null && it > HIGH }
        }
        val highPct = if (readings > 0) 100.0 * high / readings else 0.0
        val propose = valid >= MIN_NIGHTS && 100.0 * low / valid >= LOW_NIGHT_PCT && highPct <= MAX_HIGH_PCT
        return NightTargetCard(valid, low, highPct, propose)
    }

    /**
     * The proposed night targets for the targets per hour now ([lowMgdl], [highMgdl], 24 values, mg/dL): low + 1.0
     * mmol/L up to 8.0, high at least the new low. Null when no night hour would go up.
     */
    fun higher(lowMgdl: DoubleArray, highMgdl: DoubleArray): Pair<Map<Int, Double>, Map<Int, Double>>? {
        val low = HOURS.associateWith { h -> minOf(lowMgdl[h] + STEP_MGDL, MAX_TARGET_MGDL).coerceAtLeast(lowMgdl[h]) }
        if (HOURS.all { abs(low.getValue(it) - lowMgdl[it]) < 1e-9 }) return null
        val high = HOURS.associateWith { h -> maxOf(highMgdl[h], low.getValue(h)) }
        return Pair(low, high)
    }
}

/** [blocks] with the hours of [low] / [high] set to new targets (hours not in the maps stay). */
internal fun withTargetHours(blocks: List<TargetBlock>, low: Map<Int, Double>, high: Map<Int, Double>): List<TargetBlock> {
    val hour = 3_600_000L
    val pieces = ArrayList<TargetBlock>()
    var t = 0L
    for (b in blocks) {
        var from = t
        val end = t + b.duration
        while (from < end) {
            val h = (from / hour).toInt()
            val to = minOf(end, (from / hour + 1) * hour)
            pieces += TargetBlock(to - from, low[h] ?: b.lowTarget, high[h] ?: b.highTarget)
            from = to
        }
        t = end
    }
    val out = ArrayList<TargetBlock>()
    for (p in pieces) {
        val last = out.lastOrNull()
        if (last != null && abs(last.lowTarget - p.lowTarget) < 1e-9 && abs(last.highTarget - p.highTarget) < 1e-9)
            out[out.size - 1] = TargetBlock(last.duration + p.duration, last.lowTarget, last.highTarget)
        else out += p
    }
    return out
}
