package app.aaps.plugins.learner

import app.aaps.core.data.model.data.TargetBlock
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class NightTargetEngineTest {

    private val hour = 3_600_000L

    /** [days] days of 5 minute steps from midnight; [bgAt] gives the glucose of day d at hour h, minute m. */
    private fun days(days: Int, bgAt: (Int, Int, Int) -> Double?): List<LearnerStep> =
        (0 until days * 288).map { k ->
            val d = k / 288
            val h = (k / 12) % 24
            val m = (k % 12) * 5
            LearnerStep(k * 300_000L, h, m, bgAt(d, h, m), 0.0, 0.0, 0.0, 24.0)
        }

    @Test
    fun `lows in half of the nights propose a higher night target`() {
        // A low of 20 minutes at 23:00 every second night.
        val steps = days(14) { d, h, m -> if (d % 2 == 0 && h == 23 && m < 20) 62.0 else 110.0 }
        val card = NightTargetEngine.card(steps)
        assertThat(card.nights).isAtLeast(12)
        assertThat(card.lowNights).isEqualTo(7)
        assertThat(card.propose).isTrue()
    }

    @Test
    fun `a short dip under 15 minutes is not a low night`() {
        val steps = days(14) { _, h, m -> if (h == 23 && m < 10) 62.0 else 110.0 }
        assertThat(NightTargetEngine.card(steps).lowNights).isEqualTo(0)
        assertThat(NightTargetEngine.card(steps).propose).isFalse()
    }

    @Test
    fun `a low after midnight belongs to the night before`() {
        val steps = days(3) { d, h, m -> if (d == 1 && h == 2 && m < 30) 60.0 else 110.0 }
        assertThat(NightTargetEngine.card(steps).lowNights).isEqualTo(1)
    }

    @Test
    fun `many highs at night stop the proposal`() {
        val steps = days(14) { d, h, m -> if (d % 2 == 0 && h == 23 && m < 20) 62.0 else if (h in 1..3) 200.0 else 110.0 }
        val card = NightTargetEngine.card(steps)
        assertThat(card.highPct).isGreaterThan(10.0)
        assertThat(card.propose).isFalse()
    }

    @Test
    fun `too few nights with data give no proposal`() {
        val steps = days(14) { d, h, m -> if (d >= 5) null else if (h == 23 && m < 20) 62.0 else 110.0 }
        assertThat(NightTargetEngine.card(steps).propose).isFalse()
    }

    @Test
    fun `higher adds 1 mmol, never over 8, only at night`() {
        val low = DoubleArray(24) { 108.0 }.also { it[22] = 135.0; it[23] = 144.0 }
        val high = DoubleArray(24) { 108.0 }
        val (newLow, newHigh) = NightTargetEngine.higher(low, high)!!
        assertThat(newLow.keys).containsExactlyElementsIn(NightTargetEngine.HOURS)
        assertThat(newLow[21]).isEqualTo(126.0)
        assertThat(newLow[22]).isEqualTo(144.0)
        assertThat(newLow[23]).isEqualTo(144.0)
        assertThat(newLow[5]).isEqualTo(126.0)
        assertThat(newHigh[21]).isEqualTo(126.0)
        assertThat(newLow.containsKey(12)).isFalse()
    }

    @Test
    fun `already 8 at night gives nothing`() {
        assertThat(NightTargetEngine.higher(DoubleArray(24) { 144.0 }, DoubleArray(24) { 144.0 })).isNull()
    }

    @Test
    fun `target blocks change only in the night hours`() {
        val blocks = listOf(TargetBlock(24 * hour, 6.0, 6.0))
        val low = NightTargetEngine.HOURS.associateWith { 7.0 }
        val out = withTargetHours(blocks, low, low)
        assertThat(out).containsExactly(
            TargetBlock(6 * hour, 7.0, 7.0), TargetBlock(15 * hour, 6.0, 6.0), TargetBlock(3 * hour, 7.0, 7.0)
        ).inOrder()
    }
}
