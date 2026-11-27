package app.aaps.plugins.learner

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class ChangeEvalEngineTest {

    private val day = 86_400_000L
    private val change = 1_000 * day

    /**
     * [days] days of 5 minute steps from [from]: per day [lows39] readings at 3.5 mmol/L, [lows30] at 2.8, [highs] at
     * 12, the rest at 6.5 (out of 288).
     */
    private fun steps(from: Long, days: Int, lows39: Int = 0, lows30: Int = 0, highs: Int = 0, minMgdl: Double? = null) =
        (0 until days * 288).map { k ->
            val s = k % 288
            val bg = when {
                k == 100 && minMgdl != null -> minMgdl
                s < lows30                  -> 50.4
                s < lows30 + lows39         -> 63.0
                s < lows30 + lows39 + highs -> 216.0
                else                        -> 117.0
            }
            LearnerStep(from + k * 300_000L, 0, 0, bg, 0.0, 0.0, 0.0, 24.0)
        }

    private fun before(lows39: Int = 10, lows30: Int = 0, highs: Int = 20) =
        ChangeEvalEngine.stats(steps(change - 3 * day, 3, lows39, lows30, highs))

    @Test
    fun `fewer lows and the same highs is better`() {
        val e = ChangeEvalEngine.evaluate(before(lows39 = 15), steps(change, 3, lows39 = 5, highs = 20), change, change + 3 * day)
        assertThat(e.verdict).isEqualTo(EvalVerdict.BETTER)
        assertThat(e.rollback).isFalse()
    }

    @Test
    fun `fewer highs bought with more lows is worse`() {
        val e = ChangeEvalEngine.evaluate(before(lows39 = 10, highs = 30), steps(change, 3, lows39 = 16, highs = 5), change, change + 3 * day)
        assertThat(e.verdict).isEqualTo(EvalVerdict.WORSE)
    }

    @Test
    fun `small moves are the same`() {
        val e = ChangeEvalEngine.evaluate(before(), steps(change, 3, lows39 = 11, highs = 19), change, change + 3 * day)
        assertThat(e.verdict).isEqualTo(EvalVerdict.SAME)
    }

    @Test
    fun `under 3_0 over 1 percent two days in a row hits the rollback rule`() {
        // 4 readings of 288 under 3.0 = 1.4 % a day.
        val e = ChangeEvalEngine.evaluate(before(), steps(change, 3, lows30 = 4), change, change + 3 * day)
        assertThat(e.rollback).isTrue()
        assertThat(e.verdict).isEqualTo(EvalVerdict.WORSE)
    }

    @Test
    fun `under 3_0 over 1 percent on one day only is not the rollback rule`() {
        val after = steps(change, 1, lows30 = 4) + steps(change + day, 2)
        assertThat(ChangeEvalEngine.rollback(change, after, change + 3 * day)).isFalse()
    }

    @Test
    fun `a value under 2_5 hits the rollback rule at once`() {
        val after = steps(change, 1, minMgdl = 43.0)
        assertThat(ChangeEvalEngine.rollback(change, after, change + day / 2)).isTrue()
    }

    @Test
    fun `too few readings after the change give no verdict`() {
        val e = ChangeEvalEngine.evaluate(before(), steps(change, 3).take(100), change, change + 3 * day)
        assertThat(e.verdict).isEqualTo(EvalVerdict.NOT_ENOUGH_DATA)
    }
}
