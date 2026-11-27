package app.aaps.plugins.learner

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class CrBlockCardEngineTest {

    /**
     * 14 days of 5 minute steps at 6.5 mmol/L, a 20 g meal at 08:00, 13:00 and 18:00 every day; the meal of a block ends
     * low (3.5 at +60 min) on the first [lowDays] days of that block.
     */
    private fun steps(breakfastLows: Int, lunchLows: Int, dinnerLows: Int, days: Int = 14): List<LearnerStep> {
        val n = days * 288
        val bg = DoubleArray(n) { 117.0 }
        val meal = DoubleArray(n)
        for (d in 0 until days) for ((hour, lows) in listOf(8 to breakfastLows, 13 to lunchLows, 18 to dinnerLows)) {
            val k = d * 288 + hour * 12
            meal[k] = 20.0
            if (d < lows) for (j in 12..15) bg[k + j] = 63.0
        }
        return (0 until n).map { k -> LearnerStep(k * 300_000L, (k / 12) % 24, (k % 12) * 5, bg[k], 0.0, meal[k], 0.0, 24.0) }
    }

    @Test
    fun `many lows after dinner only propose a weaker dinner CR`() {
        val c = CrBlockCardEngine.card(steps(breakfastLows = 2, lunchLows = 3, dinnerLows = 7))
        assertThat(c.weaker).isEqualTo(MealBlock.DINNER)
        assertThat(c.lowPct[MealBlock.DINNER]).isWithin(0.1).of(50.0)
    }

    @Test
    fun `lows after every meal propose nothing - not a CR problem of one block`() {
        assertThat(CrBlockCardEngine.card(steps(6, 6, 7)).weaker).isNull()
    }

    @Test
    fun `few lows propose nothing`() {
        assertThat(CrBlockCardEngine.card(steps(1, 2, 4)).weaker).isNull()
    }

    @Test
    fun `too few meals in a block do not count`() {
        // 5 days only: 5 dinners < 8.
        assertThat(CrBlockCardEngine.card(steps(0, 0, 5, days = 5)).weaker).isNull()
    }

    @Test
    fun `the weaker CR is 10 percent more grams per unit, block hours only`() {
        val ic = DoubleArray(24) { 25.83 }
        val w = CrBlockCardEngine.weakerHours(ic, MealBlock.DINNER)
        assertThat(w.keys).containsExactly(16, 17, 18, 19, 20)
        assertThat(w[18]).isWithin(0.001).of(28.41)
    }
}
