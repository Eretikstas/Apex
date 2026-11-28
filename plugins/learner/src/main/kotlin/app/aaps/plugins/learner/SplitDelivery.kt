package app.aaps.plugins.learner

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * What the split bolus needs from the app. `LearnerPlugin` gives the real one (store, smoothed glucose, history, command
 * queue); tests give a fake pump.
 */
interface SplitPort {

    /** The waiting plan (kept over a restart), or null. */
    var plan: SplitPlan?

    /** The user's switch. */
    val enabled: Boolean

    /** Null when a bolus may be given now, else why not (loop suspended, pump disconnected...). */
    fun bolusRejection(): String?

    /** The smoothed glucose of now and its change over the last 15 minutes, mg/dL (nulls when not known). */
    suspend fun glucose(now: Long): Pair<Double?, Double?>

    /** All insulin given as boluses (meal, correction, SMS, SMB; no priming) from [from] to [now], units. */
    suspend fun bolusesSince(from: Long, now: Long): Double

    /** Sends one meal bolus of [units] (with [carbs] g, 0 = none) through the command queue: (success, comment). */
    suspend fun bolus(units: Double, carbs: Double, first: Boolean, now: Long): Pair<Boolean, String>
}

/** What one check of the rest did. */
sealed class SplitOutcome {

    data object NoPlan : SplitOutcome()

    /** The switch is off: the plan was dropped, nothing given. */
    data object SwitchedOff : SplitOutcome()

    data class Waiting(val why: SplitWait?) : SplitOutcome()

    /** A bolus is not allowed now (the loop or the pump): nothing given, the plan waits (it is dropped at its limit). */
    data class Held(val why: String) : SplitOutcome()

    data class Given(val units: Double, val success: Boolean, val comment: String) : SplitOutcome()

    data object Covered : SplitOutcome()

    data object Dropped : SplitOutcome()
}

/** What "give the first part" did. */
sealed class SplitStart {

    data class Refused(val why: String) : SplitStart()

    data class Failed(val comment: String) : SplitStart()

    /** The first part was given; [waiting] = a rest waits. */
    data class Started(val plan: SplitPlan, val waiting: Boolean) : SplitStart()
}

/**
 * The delivery of the split bolus: the only learner code that gives insulin. Rules:
 *
 * - One check at a time (a lock): two checks at once can never give the rest twice.
 * - The plan is removed BEFORE the rest is sent: a crash or a restart during the send can never give it again.
 * - "Cancel the rest" works at once: just before the send the check looks again whether the plan is still there.
 * - Nothing is ever retried: a failed rest is reported, not sent again.
 * - The rest is the plan rest minus ALL boluses given since the meal (the loop's SMB, a manual or SMS correction, another
 *   meal bolus), not counting the first part itself. So a correction after the meal never adds to the rest.
 * - With a bolus not allowed now (`SplitPort.bolusRejection`), nothing is given; the plan waits and is dropped at its limit.
 */
class SplitDelivery(private val port: SplitPort, private val stepU: Double) {

    private val lock = Mutex()

    /** Gives the first part with the carbs and keeps the rest waiting. [plan] must be made at [now]. */
    suspend fun start(plan: SplitPlan, now: Long): SplitStart = lock.withLock {
        if (!port.enabled) return SplitStart.Refused("off")
        if (port.plan != null) return SplitStart.Refused("a rest is already waiting")
        port.bolusRejection()?.let { return SplitStart.Refused(it) }
        val (ok, comment) = port.bolus(plan.firstU, plan.carbsG, first = true, now = now)
        if (!ok) return SplitStart.Failed(comment)
        val waiting = plan.restU >= SplitBolusEngine.MIN_BOLUS_U
        if (waiting) port.plan = plan.copy(madeAt = now)
        SplitStart.Started(plan, waiting)
    }

    /** One check of the waiting rest (every minute). */
    suspend fun check(now: Long): SplitOutcome = lock.withLock {
        val plan = port.plan ?: return SplitOutcome.NoPlan
        if (!port.enabled) {
            port.plan = null
            return SplitOutcome.SwitchedOff
        }
        val (bg, delta15) = port.glucose(now)
        // The first part is a bolus in this window too (its record may carry the pump time, so the window starts a bit
        // before the meal): take it away. A bolus just before the meal is then taken away as well - less insulin, safe.
        val others = (port.bolusesSince(plan.madeAt - BEFORE_MS, now) - plan.firstU).coerceAtLeast(0.0)
        when (val d = SplitBolusEngine.decide(now, plan, bg, delta15, null, others, stepU)) {
            is SplitDecision.Wait  -> SplitOutcome.Waiting(d.why)
            SplitDecision.Covered  -> {
                port.plan = null
                SplitOutcome.Covered
            }

            SplitDecision.Dropped  -> {
                port.plan = null
                SplitOutcome.Dropped
            }

            is SplitDecision.Give  -> {
                // "Cancel the rest" may have come while the glucose and the history were read.
                if (port.plan != plan) return SplitOutcome.NoPlan
                port.bolusRejection()?.let { return SplitOutcome.Held(it) }
                port.plan = null
                val (ok, comment) = try {
                    port.bolus(d.units, 0.0, first = false, now = now)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Pair(false, e.message.orEmpty())
                }
                SplitOutcome.Given(d.units, ok, comment)
            }
        }
    }

    /** "Cancel the rest": at once, also while a check runs (the check looks at the plan again just before it sends). */
    fun cancel() {
        port.plan = null
    }

    companion object {

        const val BEFORE_MS = 5 * 60_000L
    }
}
