package app.aaps.plugins.learner

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class LowMealHintEngineTest {

    private val min = 60_000L
    private val now = 1_000_000 * min

    @Test
    fun `a meal at 4_2 gives the hint`() {
        assertThat(LowMealHintEngine.check(now, listOf(MealEntry(now - 5 * min, 20.0, 76.0)))).isEqualTo(now - 5 * min)
    }

    @Test
    fun `a meal at 5_0 or a small hypo treatment does not`() {
        assertThat(LowMealHintEngine.check(now, listOf(MealEntry(now - 5 * min, 20.0, 90.0)))).isNull()
        assertThat(LowMealHintEngine.check(now, listOf(MealEntry(now - 5 * min, 5.0, 60.0)))).isNull()
    }

    @Test
    fun `an older meal or an unknown glucose does not`() {
        assertThat(LowMealHintEngine.check(now, listOf(MealEntry(now - 40 * min, 20.0, 70.0)))).isNull()
        assertThat(LowMealHintEngine.check(now, listOf(MealEntry(now - 5 * min, 20.0, null)))).isNull()
    }
}
