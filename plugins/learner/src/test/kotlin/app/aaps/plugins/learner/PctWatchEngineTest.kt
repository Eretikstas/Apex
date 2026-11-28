package app.aaps.plugins.learner

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class PctWatchEngineTest {

    private val h = 3_600_000L
    private val t0 = 1_800_000_000_000L

    @Test
    fun `high percentage under 4 hours gives nothing`() {
        assertThat(PctWatchEngine.check(t0 + 3 * h, PctRun(150, t0), PctRun(100, t0 - h), 2.0)).isNull()
    }

    @Test
    fun `high percentage for 4 hours and more gives a hint, again every 4 hours`() {
        val a = PctWatchEngine.check(t0 + 4 * h, PctRun(150, t0), null, 0.5)!!
        assertThat(a.kind).isEqualTo(PctHintKind.HIGH_LONG)
        assertThat(a.percent).isEqualTo(150)
        assertThat(a.hours).isWithin(1e-9).of(4.0)
        val b = PctWatchEngine.check(t0 + 6 * h, PctRun(150, t0), null, 0.5)!!
        assertThat(b.key).isEqualTo(a.key)
        val c = PctWatchEngine.check(t0 + 8 * h, PctRun(150, t0), null, 0.5)!!
        assertThat(c.key).isNotEqualTo(a.key)
    }

    @Test
    fun `100 percent or less is never high`() {
        assertThat(PctWatchEngine.check(t0 + 10 * h, PctRun(100, t0), null, 3.0)).isNull()
        assertThat(PctWatchEngine.check(t0 + 10 * h, PctRun(80, t0), PctRun(70, t0 - h), 3.0)).isNull()
    }

    @Test
    fun `drop from a high percentage with much active insulin warns`() {
        val hint = PctWatchEngine.check(t0 + 8 * h + 10 * 60_000L, PctRun(100, t0 + 8 * h), PctRun(150, t0), 1.4)!!
        assertThat(hint.kind).isEqualTo(PctHintKind.DROP_WITH_IOB)
        assertThat(hint.percent).isEqualTo(150)
        assertThat(hint.hours).isWithin(1e-9).of(8.0)
    }

    @Test
    fun `drop with little active insulin, or more than an hour ago, gives nothing`() {
        assertThat(PctWatchEngine.check(t0 + 8 * h + 10 * 60_000L, PctRun(100, t0 + 8 * h), PctRun(150, t0), 0.6)).isNull()
        assertThat(PctWatchEngine.check(t0 + 9 * h + 60_000L, PctRun(100, t0 + 8 * h), PctRun(150, t0), 2.0)).isNull()
    }

    @Test
    fun `a rise or a change between low percentages is not a drop`() {
        assertThat(PctWatchEngine.check(t0 + h + 60_000L, PctRun(150, t0 + h), PctRun(100, t0), 2.0)).isNull()
        assertThat(PctWatchEngine.check(t0 + h + 60_000L, PctRun(80, t0 + h), PctRun(100, t0), 2.0)).isNull()
    }
}
