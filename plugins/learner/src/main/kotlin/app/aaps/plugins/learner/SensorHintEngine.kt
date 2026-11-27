package app.aaps.plugins.learner

import kotlin.math.abs

/** What kind of sensor hint: the first hours after a change, or a jump too fast to be real. */
enum class SensorHintKind { FIRST_HOURS, JUMP }

/**
 * A hint about the sensor: [changeAt] is the logged sensor change, [at] the time of the reading it is about. For a jump,
 * [fromMgdl] and [toMgdl] are the two readings about 5 minutes apart.
 */
data class SensorHint(val kind: SensorHintKind, val changeAt: Long, val at: Long, val fromMgdl: Double? = null, val toMgdl: Double? = null)

/**
 * The learner's sensor hint (advisor only). After a sensor change logged in AAPS the first readings can be wrong: a first
 * value far off that falls back within half an hour, or a jump too fast to be real. There are too few sensor changes to
 * learn a rule, so the rule is fixed and careful:
 *
 * - in the first 2 hours after the change: "the values may be wrong";
 * - in the first 24 hours: a change of 1.5 mmol/L or more between two readings about 5 minutes apart is a "jump",
 *   shown for 30 minutes.
 *
 * The hint only shows text (do not calibrate, check with a finger stick). It never hides a value, never stops an alarm
 * and never changes what the loop does: a real low is worse than a false one.
 */
object SensorHintEngine {

    const val FIRST_HOURS_MS = 2 * 3_600_000L
    const val WATCH_MS = 24 * 3_600_000L
    const val JUMP_MGDL = 27.0

    /** A jump is shown for 30 minutes after its reading. */
    const val JUMP_SHOW_MS = 30 * 60_000L

    /** Two readings count as "5 minutes apart" when the older one is 5 to 7 minutes before the newer one. */
    private const val PAIR_MIN_MS = 5 * 60_000L
    private const val PAIR_MAX_MS = 7 * 60_000L

    /**
     * The hint at [now], or null. [changeAt] is the last logged sensor change (null = none known). [readings] are
     * (time, mg/dL) pairs, oldest first; readings before the change are used only as the older half of a pair.
     * A jump of the last 30 minutes wins over "first hours"; of several jumps the newest is given.
     */
    fun check(now: Long, changeAt: Long?, readings: List<Pair<Long, Double>>): SensorHint? {
        if (changeAt == null || now < changeAt || now - changeAt > WATCH_MS) return null
        var jump: SensorHint? = null
        var older = 0
        for (newer in readings.indices) {
            val (t, v) = readings[newer]
            if (t < changeAt || t > now || t < now - JUMP_SHOW_MS) continue
            // The newest reading that is at least 5 minutes older than this one.
            while (older + 1 < newer && readings[older + 1].first <= t - PAIR_MIN_MS) older++
            val (pt, pv) = readings[older]
            val gap = t - pt
            if (gap in PAIR_MIN_MS..PAIR_MAX_MS && abs(v - pv) >= JUMP_MGDL) jump = SensorHint(SensorHintKind.JUMP, changeAt, t, pv, v)
        }
        if (jump != null) return jump
        if (now - changeAt < FIRST_HOURS_MS) return SensorHint(SensorHintKind.FIRST_HOURS, changeAt, changeAt)
        return null
    }
}
