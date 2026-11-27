package app.aaps.plugins.learner

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class HypoSizeEngineTest {

    /** 2 days of steps at 6.5; a treatment of [g] g at 3.5 every 4 hours, glucose [rise] mg/dL higher 30 min later. */
    private fun steps(g: Double, rise: Double, count: Int = 8, mealNear: Boolean = false): List<LearnerStep> {
        val n = 2 * 288
        val bg = DoubleArray(n) { 117.0 }
        val hypo = DoubleArray(n)
        val meal = DoubleArray(n)
        for (i in 0 until count) {
            val k = 10 + i * 48
            bg[k] = 63.0
            bg[k + 6] = 63.0 + rise
            hypo[k] = g
            if (mealNear) meal[k + 3] = 20.0
        }
        return (0 until n).map { LearnerStep(it * 300_000L, 0, 0, bg[it], 0.0, meal[it], hypo[it], 24.0) }
    }

    @Test
    fun `learns the rise per gram`() {
        val h = HypoSizeEngine.learn(steps(5.0, 30.0))!!
        assertThat(h.risePerGMgdl).isWithin(0.001).of(6.0)
        assertThat(h.treatments).isEqualTo(8)
    }

    @Test
    fun `suggests enough to reach 5_5, never under 4 or over 10 g`() {
        val h = HypoSize(6.0, 8)
        assertThat(HypoSizeEngine.suggest(h, 63.0)).isEqualTo(6)
        assertThat(HypoSizeEngine.suggest(h, 90.0)).isEqualTo(4)
        assertThat(HypoSizeEngine.suggest(HypoSize(2.0, 8), 54.0)).isEqualTo(10)
    }

    @Test
    fun `too few treatments or meals around them teach nothing`() {
        assertThat(HypoSizeEngine.learn(steps(5.0, 30.0, count = 3))).isNull()
        assertThat(HypoSizeEngine.learn(steps(5.0, 30.0, mealNear = true))).isNull()
    }

    @Test
    fun `no rise teaches nothing`() {
        assertThat(HypoSizeEngine.learn(steps(5.0, -5.0))).isNull()
    }
}
