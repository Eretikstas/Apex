package app.aaps.plugins.learner

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class LearnerMemoryTest {

    private val hour = 3_600_000L
    private val day = 24 * hour

    private fun steps(n: Int) = (0 until n).map { LearnerStep(it * 300_000L, 0, 0, 120.0, 0.0, 0.0, 0.0, 24.0) }

    @Test
    fun `readings of a special day are left out, the rest stays`() {
        val s = LearnerMemory.withoutSpecial(steps(48), listOf(Pair(hour, 2 * hour)))
        assertThat(s.count { it.bgMgdl == null }).isEqualTo(12)
        assertThat(s[0].bgMgdl).isEqualTo(120.0)
        assertThat(s[12].bgMgdl).isNull()
        assertThat(s[24].bgMgdl).isEqualTo(120.0)
    }

    @Test
    fun `no special day changes nothing`() {
        val st = steps(10)
        assertThat(LearnerMemory.withoutSpecial(st, emptyList())).isSameInstanceAs(st)
    }

    @Test
    fun `a special day runs only inside its interval`() {
        val sp = listOf(Pair(10 * hour, 10 * hour + LearnerMemory.SPECIAL_MS))
        assertThat(LearnerMemory.specialNow(11 * hour, sp)).isTrue()
        assertThat(LearnerMemory.specialNow(35 * hour, sp)).isFalse()
    }

    @Test
    fun `a failed change is not proposed again for 14 days`() {
        val failed = mapOf("cr:DINNER" to 100 * day)
        assertThat(LearnerMemory.failedRecently(110 * day, "cr:DINNER", failed)).isTrue()
        assertThat(LearnerMemory.failedRecently(115 * day, "cr:DINNER", failed)).isFalse()
        assertThat(LearnerMemory.failedRecently(110 * day, "cr:LUNCH", failed)).isFalse()
    }
}
