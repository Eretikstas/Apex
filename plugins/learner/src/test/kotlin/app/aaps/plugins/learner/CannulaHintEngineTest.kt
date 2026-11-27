package app.aaps.plugins.learner

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class CannulaHintEngineTest {

    private val hour = 3_600_000L
    private val now = 1_000_000 * hour

    /** 6 hours of readings, one every [stepMin] minutes: the first [highShare] of them at 12 mmol/L, the rest at 7. */
    private fun readings(highShare: Double, stepMin: Int = 5): List<Pair<Long, Double>> {
        val n = 6 * 60 / stepMin
        return (0 until n).map { i -> (now - i * stepMin * 60_000L) to if (i < n * highShare) 216.0 else 126.0 }
    }

    @Test
    fun `old cannula with many highs gives the hint`() {
        val h = CannulaHintEngine.check(now, now - 65 * hour, readings(0.5))
        assertThat(h).isNotNull()
        assertThat(h!!.ageH).isWithin(0.01).of(65.0)
        assertThat(h.highPct).isAtLeast(40.0)
    }

    @Test
    fun `young cannula with the same highs gives no hint`() {
        assertThat(CannulaHintEngine.check(now, now - 40 * hour, readings(0.8))).isNull()
    }

    @Test
    fun `old cannula with few highs gives no hint`() {
        assertThat(CannulaHintEngine.check(now, now - 70 * hour, readings(0.3))).isNull()
    }

    @Test
    fun `unknown cannula age gives no hint`() {
        assertThat(CannulaHintEngine.check(now, null, readings(0.9))).isNull()
    }

    @Test
    fun `too few readings give no hint`() {
        assertThat(CannulaHintEngine.check(now, now - 70 * hour, readings(0.9).take(40))).isNull()
    }

    @Test
    fun `a reading every minute counts like one every 5 minutes`() {
        val h = CannulaHintEngine.check(now, now - 70 * hour, readings(0.5, stepMin = 1))
        assertThat(h).isNotNull()
        assertThat(h!!.highPct).isWithin(2.0).of(50.0)
    }
}
