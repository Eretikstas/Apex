package app.aaps.plugins.learner

/**
 * Two small memories of the learner, as pure functions so they can be tested.
 *
 * "Special day" (button on the learner screen): illness, a cannula failure, a party - a day that must not teach the
 * learner. While it runs no card is made, and its readings are left out of every card and verdict later (as if the
 * sensor had no data then). It ends by itself after [SPECIAL_MS] (24 h) or when the family ends it.
 *
 * "What did not work": when the change evaluation judges a change WORSE (or the rollback rule is hit), the same kind of
 * change (for example "CR of dinner weaker" or "percentage down") is not proposed again for [FAILED_MS] (14 days).
 */
object LearnerMemory {

    const val SPECIAL_MS = 24 * 3_600_000L
    const val FAILED_MS = 14 * 86_400_000L

    /** [steps] with the glucose removed inside any of the [special] intervals (from, to). */
    fun withoutSpecial(steps: List<LearnerStep>, special: List<Pair<Long, Long>>): List<LearnerStep> =
        if (special.isEmpty()) steps
        else steps.map { s -> if (special.any { s.time >= it.first && s.time < it.second }) s.copy(bgMgdl = null) else s }

    /** Whether a special day runs at [now]. */
    fun specialNow(now: Long, special: List<Pair<Long, Long>>): Boolean = special.any { now >= it.first && now < it.second }

    /** Whether a change of [kind] failed within [FAILED_MS] before [now]; [failed] maps a kind to when it failed. */
    fun failedRecently(now: Long, kind: String, failed: Map<String, Long>): Boolean = failed[kind]?.let { now - it < FAILED_MS } ?: false
}
