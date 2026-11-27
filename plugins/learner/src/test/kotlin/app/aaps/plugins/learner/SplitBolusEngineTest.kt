package app.aaps.plugins.learner

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class SplitBolusEngineTest {

    private val min = 60_000L
    private val t0 = 1_000_000 * min
    private val step = 0.05

    private fun normal(total: Double = 2.0) = SplitBolusEngine.plan(t0, MealType.NORMAL, total, step)

    @Test
    fun `plans give 60 percent now for a normal meal, 50 for slow, all for fast`() {
        assertThat(normal().firstU).isWithin(1e-9).of(1.2)
        assertThat(SplitBolusEngine.plan(t0, MealType.SLOW, 2.0, step).firstU).isWithin(1e-9).of(1.0)
        val fast = SplitBolusEngine.plan(t0, MealType.FAST, 2.0, step)
        assertThat(fast.restU).isWithin(1e-9).of(0.0)
        assertThat(SplitBolusEngine.decide(t0 + 30 * min, fast, 150.0, 20.0, null, 0.0, step)).isEqualTo(SplitDecision.Covered)
    }

    @Test
    fun `the first part is rounded down to the pump step`() {
        assertThat(SplitBolusEngine.plan(t0, MealType.NORMAL, 1.33, step).firstU).isWithin(1e-9).of(0.75)
    }

    @Test
    fun `nothing before 20 minutes`() {
        assertThat(SplitBolusEngine.decide(t0 + 10 * min, normal(), 150.0, 30.0, 5.0, 0.0, step))
            .isEqualTo(SplitDecision.Wait(SplitWait.TOO_EARLY))
    }

    @Test
    fun `a rise of 0_5 in 15 minutes at 5_5 or more gives the rest`() {
        val d = SplitBolusEngine.decide(t0 + 25 * min, normal(), 110.0, 10.0, null, 0.0, step)
        assertThat(d).isInstanceOf(SplitDecision.Give::class.java)
        assertThat((d as SplitDecision.Give).units).isWithin(1e-9).of(0.8)
    }

    @Test
    fun `AAPS deviation also counts as food seen`() {
        assertThat(SplitBolusEngine.decide(t0 + 25 * min, normal(), 110.0, 3.0, 2.5, 0.0, step)).isInstanceOf(SplitDecision.Give::class.java)
    }

    @Test
    fun `never under 4_5`() {
        assertThat(SplitBolusEngine.decide(t0 + 30 * min, normal(), 79.0, 20.0, 5.0, 0.0, step)).isEqualTo(SplitDecision.Wait(SplitWait.LOW))
        assertThat(SplitBolusEngine.decide(t0 + 30 * min, normal(), null, 20.0, 5.0, 0.0, step)).isEqualTo(SplitDecision.Wait(SplitWait.LOW))
    }

    @Test
    fun `never while falling`() {
        assertThat(SplitBolusEngine.decide(t0 + 30 * min, normal(), 140.0, -3.0, 5.0, 0.0, step)).isEqualTo(SplitDecision.Wait(SplitWait.FALLING))
    }

    @Test
    fun `a rise under 5_5 waits`() {
        assertThat(SplitBolusEngine.decide(t0 + 30 * min, normal(), 95.0, 15.0, 5.0, 0.0, step)).isEqualTo(SplitDecision.Wait(SplitWait.NO_FOOD_SEEN))
    }

    @Test
    fun `SMB given since the meal are taken off the rest`() {
        val d = SplitBolusEngine.decide(t0 + 30 * min, normal(), 130.0, 12.0, null, 0.3, step)
        assertThat((d as SplitDecision.Give).units).isWithin(1e-9).of(0.5)
        assertThat(SplitBolusEngine.decide(t0 + 30 * min, normal(), 130.0, 12.0, null, 0.8, step)).isEqualTo(SplitDecision.Covered)
    }

    @Test
    fun `not seen by 90 minutes drops the rest, slow meals wait 180`() {
        assertThat(SplitBolusEngine.decide(t0 + 91 * min, normal(), 110.0, 0.0, null, 0.0, step)).isEqualTo(SplitDecision.Dropped)
        val slow = SplitBolusEngine.plan(t0, MealType.SLOW, 2.0, step)
        assertThat(SplitBolusEngine.decide(t0 + 120 * min, slow, 100.0, 1.0, null, 0.0, step)).isEqualTo(SplitDecision.Wait(SplitWait.NO_FOOD_SEEN))
        assertThat(SplitBolusEngine.decide(t0 + 181 * min, slow, 100.0, 1.0, null, 0.0, step)).isEqualTo(SplitDecision.Dropped)
    }
}
