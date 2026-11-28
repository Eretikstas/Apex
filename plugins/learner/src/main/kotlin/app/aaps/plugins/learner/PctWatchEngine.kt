package app.aaps.plugins.learner

/** A profile percentage and since when it runs. */
data class PctRun(val percent: Int, val since: Long)

enum class PctHintKind { HIGH_LONG, DROP_WITH_IOB }

/** A percentage hint: what, the percentage (now, or before the drop), how long it ran (hours), the active insulin. */
data class PctHint(val kind: PctHintKind, val percent: Int, val hours: Double, val iobU: Double, val key: String)

/**
 * The learner's profile percentage watch (advisor, a notice only, off by default). Two hints:
 *
 * - HIGH_LONG: the percentage is over 100 % for [LONG_MS] (4 h) or more ("still 150 %, for 5 h"). Again every 4 h.
 * - DROP_WITH_IOB: the percentage came down from over 100 % within the last [DROP_MS] (60 min) and the active insulin is
 *   [IOB_U] or more ("the insulin of the high hours still works: watch the next hour for a low").
 *
 * The 4 h, 60 min and 1.0 U are our estimates, not a clinical rule. Nothing is changed: it only tells the family.
 */
object PctWatchEngine {

    const val LONG_MS = 4 * 3_600_000L
    const val DROP_MS = 60 * 60_000L
    const val IOB_U = 1.0

    /** [current] the percentage running now, [previous] the one before it (null if none), [iobU] the active insulin now. */
    fun check(now: Long, current: PctRun, previous: PctRun?, iobU: Double): PctHint? {
        val ranMs = now - current.since
        if (current.percent > 100 && ranMs >= LONG_MS) {
            val block = ranMs / LONG_MS
            return PctHint(PctHintKind.HIGH_LONG, current.percent, ranMs / 3_600_000.0, iobU, "high@${current.since}#$block")
        }
        if (previous != null && previous.percent > 100 && current.percent < previous.percent && ranMs in 0..DROP_MS && iobU >= IOB_U) {
            val hours = (current.since - previous.since) / 3_600_000.0
            return PctHint(PctHintKind.DROP_WITH_IOB, previous.percent, hours, iobU, "drop@${current.since}")
        }
        return null
    }
}
