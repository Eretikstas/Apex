package app.aaps.plugins.learner

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class PctCardEngineTest {

    /** 3 days of 5 minute steps: [lows] and [highs] readings a day out of 288, the rest in range. */
    private fun steps(lows: Int, highs: Int) = (0 until 3 * 288).map { k ->
        val s = k % 288
        val bg = when {
            s < lows          -> 63.0
            s < lows + highs  -> 220.0
            else              -> 120.0
        }
        LearnerStep(k * 300_000L, (k / 12) % 24, (k % 12) * 5, bg, 0.0, 0.0, 0.0, 24.0)
    }

    @Test
    fun `many lows lower the percentage, not under 80`() {
        assertThat(PctCardEngine.card(steps(lows = 15, highs = 0), 100).to).isEqualTo(90)
        assertThat(PctCardEngine.card(steps(lows = 15, highs = 0), 80).to).isNull()
    }

    @Test
    fun `below 100 highs bring it back, never above 100`() {
        assertThat(PctCardEngine.card(steps(lows = 5, highs = 30), 90).to).isEqualTo(100)
        val at100 = PctCardEngine.card(steps(lows = 5, highs = 30), 100)
        assertThat(at100.to).isNull()
        assertThat(at100.hintUp).isTrue()
    }

    @Test
    fun `no hint when lows are over 2 percent`() {
        val c = PctCardEngine.card(steps(lows = 8, highs = 30), 100)
        assertThat(c.to).isNull()
        assertThat(c.hintUp).isFalse()
    }

    @Test
    fun `calm days keep the percentage`() {
        val c = PctCardEngine.card(steps(lows = 2, highs = 5), 100)
        assertThat(c.to).isNull()
        assertThat(c.hintUp).isFalse()
    }
}
