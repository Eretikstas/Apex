package app.aaps.plugins.learner

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class DuraCardEngineTest {

    /** [days] of 5 minute steps; [bg] gives the glucose of step k. */
    private fun steps(days: Int, cannulaH: Double? = 24.0, bg: (Int) -> Double?): List<LearnerStep> =
        (0 until days * 288).map { k -> LearnerStep(k * 300_000L, (k / 12) % 24, (k % 12) * 5, bg(k), 0.0, 0.0, 0.0, cannulaH) }

    /** Mostly in range, a 3 h flat high at 12 mmol/L every day, [lowsPerDay] readings at 3.5. */
    private fun day(lowsPerDay: Int) = { k: Int ->
        val s = k % 288
        when {
            s in 140..175   -> 216.0 + (s % 2)
            s < lowsPerDay  -> 63.0
            else            -> 110.0
        }
    }

    @Test
    fun flatHighsAndFewLowsGoUpOneStep() {
        val card = DuraCardEngine.card(steps(7, bg = day(lowsPerDay = 3)), dura = 0.5)
        assertThat(card.flatHighPct).isAtLeast(2.0)
        assertThat(card.to).isEqualTo(0.75)
    }

    @Test
    fun neverAboveTheTop() {
        assertThat(DuraCardEngine.card(steps(7, bg = day(3)), dura = 2.0).to).isNull()
        assertThat(DuraCardEngine.card(steps(7, bg = day(3)), dura = 1.9).to).isEqualTo(2.0)
    }

    @Test
    fun manyLowsGoDownEvenWithFlatHighs() {
        val card = DuraCardEngine.card(steps(7, bg = day(lowsPerDay = 20)), dura = 1.0)
        assertThat(card.lowPct).isGreaterThan(4.0)
        assertThat(card.to).isEqualTo(0.75)
        assertThat(DuraCardEngine.card(steps(7, bg = day(20)), dura = 0.0).to).isNull()
    }

    @Test
    fun lowsBetween3And4KeepTheWeight() {
        // 10 lows a day = 3.5 %: too many to go up, too few to go down.
        assertThat(DuraCardEngine.card(steps(7, bg = day(lowsPerDay = 10)), dura = 1.0).to).isNull()
    }

    @Test
    fun oldCannulaAndLittleDataAreLeftOut() {
        assertThat(DuraCardEngine.card(steps(7, cannulaH = 80.0, bg = day(3)), dura = 0.5).to).isNull()
        assertThat(DuraCardEngine.card(steps(7, bg = { null }), dura = 0.5).to).isNull()
    }

    @Test
    fun unknownCannulaAgeIsLeftOut() {
        assertThat(DuraCardEngine.card(steps(7, cannulaH = null, bg = day(3)), dura = 0.5).to).isNull()
    }

    @Test
    fun aWindowWithAMissingReadingIsNoFlatHigh() {
        // The same highs, but every 4th reading of them is missing: no 30 minutes in a row are whole.
        val gappy = { k: Int -> val s = k % 288; if (s in 140..175 && s % 4 == 0) null else day(3)(k) }
        val card = DuraCardEngine.card(steps(7, bg = gappy), dura = 0.5)
        assertThat(card.flatHighPct).isEqualTo(0.0)
        assertThat(card.to).isNull()
    }
}
