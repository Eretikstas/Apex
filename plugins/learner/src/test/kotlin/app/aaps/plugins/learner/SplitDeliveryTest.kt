package app.aaps.plugins.learner

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * The split bolus delivery on a fake pump: the rest is given at most once (also after a crash, a restart, two checks
 * at once), never when the loop forbids a bolus, never after "Cancel the rest", and never on top of a correction.
 */
class SplitDeliveryTest {

    private val min = 60_000L
    private val t0 = 1_800_000_000_000L
    private val step = 0.05

    /** A fake pump with its history. Glucose: rising 6.7 mmol/L (+0.8 in 15 min) unless set. */
    private class FakePump : SplitPort {

        data class Sent(val at: Long, val units: Double, val carbs: Double, val first: Boolean)

        override var plan: SplitPlan? = null
        override var enabled = true
        var rejection: String? = null
        var bg: Double? = 120.0
        var delta15: Double? = 15.0
        val sent = mutableListOf<Sent>()
        val history = mutableListOf<Pair<Long, Double>>()
        var failNext = false
        var throwNext = false
        var planSeenAtRestSend: SplitPlan? = SplitPlan(0, MealType.NORMAL, 0.0, 0.0)
        var onGlucose: () -> Unit = {}
        var sendDelayMs = 0L

        override suspend fun bolusRejection(): String? = rejection
        override suspend fun glucose(now: Long): Pair<Double?, Double?> {
            onGlucose()
            return Pair(bg, delta15)
        }

        override suspend fun bolusesSince(from: Long, now: Long): Double = history.filter { it.first in from..now }.sumOf { it.second }
        override suspend fun bolus(units: Double, carbs: Double, first: Boolean, now: Long): Pair<Boolean, String> {
            if (!first) planSeenAtRestSend = plan
            if (sendDelayMs > 0) delay(sendDelayMs)
            if (throwNext) {
                throwNext = false
                throw IllegalStateException("pump lost")
            }
            if (failNext) {
                failNext = false
                return Pair(false, "no connection")
            }
            sent += Sent(now, units, carbs, first)
            // The pump writes its own time: a little after the phone time.
            history += Pair(now + 20_000L, units)
            return Pair(true, "")
        }

        fun correction(at: Long, units: Double) {
            history += Pair(at, units)
        }

        fun restsSent() = sent.filter { !it.first }
    }

    private fun normalPlan() = SplitBolusEngine.plan(t0, MealType.NORMAL, 2.0, step).copy(carbsG = 50.0)

    private suspend fun started(pump: FakePump): SplitDelivery {
        val d = SplitDelivery(pump, step)
        assertThat(d.start(normalPlan(), t0)).isInstanceOf(SplitStart.Started::class.java)
        return d
    }

    @Test
    fun `first part goes with the carbs and the rest waits`() = runTest {
        val pump = FakePump()
        started(pump)
        assertThat(pump.sent).hasSize(1)
        assertThat(pump.sent[0].units).isWithin(1e-9).of(1.2)
        assertThat(pump.sent[0].carbs).isEqualTo(50.0)
        assertThat(pump.plan).isNotNull()
    }

    @Test
    fun `no first part when off, when the loop forbids a bolus, or when a rest already waits`() = runTest {
        val off = FakePump().apply { enabled = false }
        assertThat(SplitDelivery(off, step).start(normalPlan(), t0)).isInstanceOf(SplitStart.Refused::class.java)
        val suspended = FakePump().apply { rejection = "loop suspended" }
        assertThat(SplitDelivery(suspended, step).start(normalPlan(), t0)).isInstanceOf(SplitStart.Refused::class.java)
        val pump = FakePump()
        val d = started(pump)
        assertThat(d.start(normalPlan(), t0 + min)).isInstanceOf(SplitStart.Refused::class.java)
        assertThat(off.sent + suspended.sent).isEmpty()
        assertThat(pump.sent).hasSize(1)
    }

    @Test
    fun `failed first part keeps no rest`() = runTest {
        val pump = FakePump().apply { failNext = true }
        assertThat(SplitDelivery(pump, step).start(normalPlan(), t0)).isInstanceOf(SplitStart.Failed::class.java)
        assertThat(pump.plan).isNull()
        assertThat(pump.sent).isEmpty()
    }

    @Test
    fun `fast meal gives all at once and keeps no rest`() = runTest {
        val pump = FakePump()
        val plan = SplitBolusEngine.plan(t0, MealType.FAST, 2.0, step)
        assertThat(SplitDelivery(pump, step).start(plan, t0)).isEqualTo(SplitStart.Started(plan, false))
        assertThat(pump.plan).isNull()
        assertThat(pump.sent.single().units).isWithin(1e-9).of(2.0)
    }

    @Test
    fun `the rest is given exactly once, and the plan is gone before it is sent`() = runTest {
        val pump = FakePump()
        val d = started(pump)
        assertThat(d.check(t0 + 10 * min)).isEqualTo(SplitOutcome.Waiting(SplitWait.TOO_EARLY))
        val given = d.check(t0 + 30 * min)
        assertThat(given).isInstanceOf(SplitOutcome.Given::class.java)
        assertThat((given as SplitOutcome.Given).units).isWithin(1e-9).of(0.8)
        assertThat(pump.planSeenAtRestSend).isNull()
        repeat(10) { assertThat(d.check(t0 + (31 + it) * min)).isEqualTo(SplitOutcome.NoPlan) }
        assertThat(pump.restsSent()).hasSize(1)
        assertThat(pump.sent.sumOf { it.units }).isWithin(1e-9).of(2.0)
    }

    @Test
    fun `a crash while the rest is sent never sends it again`() = runTest {
        val pump = FakePump()
        val d = started(pump)
        pump.throwNext = true
        val o = d.check(t0 + 30 * min)
        assertThat(o).isInstanceOf(SplitOutcome.Given::class.java)
        assertThat((o as SplitOutcome.Given).success).isFalse()
        assertThat(pump.plan).isNull()
        assertThat(d.check(t0 + 31 * min)).isEqualTo(SplitOutcome.NoPlan)
        assertThat(pump.restsSent()).isEmpty()
    }

    @Test
    fun `a failed rest is not retried`() = runTest {
        val pump = FakePump()
        val d = started(pump)
        pump.failNext = true
        assertThat((d.check(t0 + 30 * min) as SplitOutcome.Given).success).isFalse()
        repeat(5) { d.check(t0 + (31 + it) * min) }
        assertThat(pump.restsSent()).isEmpty()
    }

    @Test
    fun `after a restart a given rest is not given again`() = runTest {
        val pump = FakePump()
        started(pump).check(t0 + 30 * min)
        // The app restarts: a new delivery on the same stored state.
        val again = SplitDelivery(pump, step)
        assertThat(again.check(t0 + 32 * min)).isEqualTo(SplitOutcome.NoPlan)
        assertThat(pump.restsSent()).hasSize(1)
    }

    @Test
    fun `after a restart a waiting rest still waits and is given once`() = runTest {
        val pump = FakePump()
        started(pump)
        val again = SplitDelivery(pump, step)
        assertThat(again.check(t0 + 30 * min)).isInstanceOf(SplitOutcome.Given::class.java)
        assertThat(again.check(t0 + 31 * min)).isEqualTo(SplitOutcome.NoPlan)
        assertThat(pump.restsSent()).hasSize(1)
    }

    @Test
    fun `a manual or SMS correction after the meal is taken from the rest`() = runTest {
        val pump = FakePump()
        val d = started(pump)
        pump.correction(t0 + 15 * min, 0.5)
        val o = d.check(t0 + 30 * min) as SplitOutcome.Given
        assertThat(o.units).isWithin(1e-9).of(0.3)
    }

    @Test
    fun `a correction as big as the rest gives nothing`() = runTest {
        val pump = FakePump()
        val d = started(pump)
        pump.correction(t0 + 15 * min, 1.0)
        assertThat(d.check(t0 + 30 * min)).isEqualTo(SplitOutcome.Covered)
        assertThat(pump.restsSent()).isEmpty()
        assertThat(pump.plan).isNull()
    }

    @Test
    fun `the loop's SMB counts like a correction`() = runTest {
        val pump = FakePump()
        val d = started(pump)
        pump.correction(t0 + 22 * min, 0.3)
        pump.correction(t0 + 27 * min, 0.3)
        assertThat((d.check(t0 + 30 * min) as SplitOutcome.Given).units).isWithin(1e-9).of(0.2)
    }

    @Test
    fun `never when low or falling, dropped at the limit`() = runTest {
        val pump = FakePump()
        val d = started(pump)
        pump.bg = 75.0
        assertThat(d.check(t0 + 30 * min)).isEqualTo(SplitOutcome.Waiting(SplitWait.LOW))
        pump.bg = 130.0
        pump.delta15 = -5.0
        assertThat(d.check(t0 + 40 * min)).isEqualTo(SplitOutcome.Waiting(SplitWait.FALLING))
        pump.bg = null
        assertThat(d.check(t0 + 50 * min)).isEqualTo(SplitOutcome.Waiting(SplitWait.LOW))
        assertThat(d.check(t0 + 91 * min)).isEqualTo(SplitOutcome.Dropped)
        pump.bg = 150.0
        pump.delta15 = 20.0
        assertThat(d.check(t0 + 92 * min)).isEqualTo(SplitOutcome.NoPlan)
        assertThat(pump.restsSent()).isEmpty()
    }

    @Test
    fun `nothing while the loop forbids a bolus, then dropped at the limit`() = runTest {
        val pump = FakePump()
        val d = started(pump)
        pump.rejection = "loop suspended"
        assertThat(d.check(t0 + 30 * min)).isEqualTo(SplitOutcome.Held("loop suspended"))
        assertThat(d.check(t0 + 60 * min)).isEqualTo(SplitOutcome.Held("loop suspended"))
        assertThat(d.check(t0 + 91 * min)).isEqualTo(SplitOutcome.Dropped)
        assertThat(pump.restsSent()).isEmpty()
    }

    @Test
    fun `switch off drops the rest`() = runTest {
        val pump = FakePump()
        val d = started(pump)
        pump.enabled = false
        assertThat(d.check(t0 + 30 * min)).isEqualTo(SplitOutcome.SwitchedOff)
        pump.enabled = true
        assertThat(d.check(t0 + 31 * min)).isEqualTo(SplitOutcome.NoPlan)
        assertThat(pump.restsSent()).isEmpty()
    }

    @Test
    fun `cancel works also while a check is running`() = runTest {
        val pump = FakePump()
        val d = started(pump)
        pump.onGlucose = { d.cancel() }
        assertThat(d.check(t0 + 30 * min)).isEqualTo(SplitOutcome.NoPlan)
        assertThat(pump.restsSent()).isEmpty()
    }

    @Test
    fun `two checks at once give the rest once`() = runTest {
        val pump = FakePump()
        val d = started(pump)
        pump.sendDelayMs = 5_000L
        val a = launch { d.check(t0 + 30 * min) }
        val b = launch { d.check(t0 + 30 * min) }
        a.join()
        b.join()
        assertThat(pump.restsSent()).hasSize(1)
    }
}
