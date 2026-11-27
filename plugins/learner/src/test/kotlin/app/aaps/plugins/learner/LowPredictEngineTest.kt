package app.aaps.plugins.learner

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class LowPredictEngineTest {

    private val min = 60_000L
    private val now = 1_000_000 * min

    @Test
    fun `5_5 falling 0_3 per 5 min warns, a slow fall or a high glucose does not`() {
        assertThat(LowPredictEngine.warn(now, 99.0, -5.4, 0.0, 0L)).isTrue()
        assertThat(LowPredictEngine.warn(now, 99.0, -2.0, 0.0, 0L)).isFalse()
        assertThat(LowPredictEngine.warn(now, 130.0, -12.0, 0.0, 0L)).isFalse()
        assertThat(LowPredictEngine.warn(now, 99.0, 1.0, 0.0, 0L)).isFalse()
    }

    @Test
    fun `not twice within 30 minutes`() {
        assertThat(LowPredictEngine.warn(now, 99.0, -5.4, 0.0, now - 20 * min)).isFalse()
        assertThat(LowPredictEngine.warn(now, 99.0, -5.4, 0.0, now - 31 * min)).isTrue()
    }

    @Test
    fun `a stricter margin needs a steeper forecast`() {
        assertThat(LowPredictEngine.warn(now, 99.0, -5.4, 9.0, 0L)).isFalse()
        assertThat(LowPredictEngine.warn(now, 99.0, -7.5, 9.0, 0L)).isTrue()
    }

    @Test
    fun `many false warnings make it stricter, many true ones looser, few change nothing`() {
        val falseOnes = List(8) { LowWarning(it * 1000L, it < 2) }
        assertThat(LowPredictEngine.learn(0.0, falseOnes)).isEqualTo(9.0)
        assertThat(LowPredictEngine.learn(27.0, falseOnes)).isEqualTo(27.0)
        val trueOnes = List(8) { LowWarning(it * 1000L, it != 0) }
        assertThat(LowPredictEngine.learn(18.0, trueOnes)).isEqualTo(9.0)
        assertThat(LowPredictEngine.learn(9.0, List(3) { LowWarning(it * 1000L, false) })).isEqualTo(9.0)
    }
}
