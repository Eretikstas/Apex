package app.aaps.plugins.learner

import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

/** What the dura card found: the share of flat highs and of lows, and the weight it proposes (null = keep). */
data class DuraCard(val flatHighPct: Double, val lowPct: Double, val from: Double, val to: Double?)

/**
 * The learner's card for the AutoISF weight `dura` (how much a long flat high makes ISF stronger). The same rule as tested
 * on the body model of the replay lab (lab token LEARNDURA). It may propose up or down:
 *
 * - From the readings of the last 7 days (a cannula older than 72 h is left out, like in the basal card).
 * - "Flat high": a reading that ends 30 minutes all over 10 mmol/L that moved less than 1 mmol/L.
 * - Flat highs 2 % or more and lows (under 3.9) 3 % or less -> one step up (+0.25, at most 2.0).
 * - Lows over 4 % -> one step down (-0.25, not under 0). Otherwise keep.
 * - Too little data (under one day of readings) -> keep.
 */
object DuraCardEngine {

    const val DAYS = 7
    const val STEP = 0.25
    const val MAX = 2.0

    fun card(steps: List<LearnerStep>, dura: Double): DuraCard {
        val bgs = steps.filter { (it.cannulaAgeH ?: 0.0) <= CANNULA_MAX_H }.mapNotNull { it.bgMgdl }
        if (bgs.size < MIN_READINGS) return DuraCard(0.0, 0.0, dura, null)
        var flat = 0
        for (i in WINDOW until bgs.size) {
            val w = bgs.subList(i - WINDOW, i + 1)
            if (w.all { it > HIGH_MGDL } && w.max() - w.min() < FLAT_MGDL) flat++
        }
        val flatPct = flat * 100.0 / bgs.size
        val lowPct = bgs.count { it < LOW_MGDL } * 100.0 / bgs.size
        val to = when {
            flatPct >= FLAT_UP_PCT && lowPct <= LOW_UP_MAX_PCT && dura < MAX -> min(MAX, dura + STEP)
            lowPct > LOW_DOWN_PCT && dura > 0.0                                -> max(0.0, dura - STEP)
            else                                                               -> null
        }
        return DuraCard(flatPct, lowPct, dura, to?.let { round(it * 100) / 100 })
    }

    private const val CANNULA_MAX_H = 72.0
    private const val MIN_READINGS = 288
    private const val WINDOW = 6
    private const val HIGH_MGDL = 180.0
    private const val FLAT_MGDL = 18.0
    private const val LOW_MGDL = 70.0
    private const val FLAT_UP_PCT = 2.0
    private const val LOW_UP_MAX_PCT = 3.0
    private const val LOW_DOWN_PCT = 4.0
}
