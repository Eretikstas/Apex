package app.aaps.plugins.learner

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class WeeklyDigestEngineTest {

    /** 7 days: per day [lows] readings at 3.5 mmol/L and [highs] at 12, the rest at 6.5; 0.05 U and [hypoG] g a day. */
    private fun week(lows: Int, highs: Int, hypoG: Double = 0.0) = (0 until 7 * 288).map { k ->
        val s = k % 288
        val bg = when {
            s < lows         -> 63.0
            s < lows + highs -> 216.0
            else             -> 117.0
        }
        LearnerStep(k * 300_000L, 0, 0, bg, 0.05, 0.0, if (s == 0) hypoG else 0.0, 24.0)
    }

    @Test
    fun `this week is the second half`() {
        val d = WeeklyDigestEngine.digest(week(lows = 15, highs = 30) + week(lows = 3, highs = 10, hypoG = 10.0))
        assertThat(d.thisWeek.low39Pct).isWithin(0.01).of(100.0 * 3 / 288)
        assertThat(d.lastWeek.low39Pct).isWithin(0.01).of(100.0 * 15 / 288)
        assertThat(d.thisWeek.insulinPerDay).isWithin(0.001).of(0.05 * 288)
        assertThat(d.thisWeek.hypoGPerDay).isWithin(0.001).of(10.0)
    }

    @Test
    fun `goals are counted`() {
        // 1 % lows, 3.5 % highs, the rest in range: all four goals met (CV stays low).
        val good = WeeklyDigestEngine.digest(week(10, 10) + week(lows = 3, highs = 10))
        assertThat(good.goalsMet).isEqualTo(4)
        // 10 % lows and 20 % highs: in range, lows and highs missed.
        val bad = WeeklyDigestEngine.digest(week(3, 10) + week(lows = 29, highs = 58))
        assertThat(bad.goalsMet).isAtMost(1)
    }

    @Test
    fun `no readings give no goals`() {
        val empty = (0 until 14 * 288).map { LearnerStep(it * 300_000L, 0, 0, null, 0.0, 0.0, 0.0, null) }
        assertThat(WeeklyDigestEngine.digest(empty).goalsMet).isEqualTo(0)
    }
}
