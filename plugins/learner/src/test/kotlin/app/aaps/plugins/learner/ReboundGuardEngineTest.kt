package app.aaps.plugins.learner

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class ReboundGuardEngineTest {

    private val min = 60_000L
    private val now = 1_000_000 * min

    @Test
    fun `5 g at 3_5 holds the loop for 2 hours`() {
        val c = listOf(CarbEntry(now - 20 * min, 5.0, 63.0))
        val s = ReboundGuardEngine.state(now, c, 110.0)
        assertThat(s.active).isTrue()
        assertThat(s.until).isEqualTo(now - 20 * min + 120 * min)
        assertThat(ReboundGuardEngine.state(now + 101 * min, c, 110.0).active).isFalse()
    }

    @Test
    fun `a second treatment moves the end`() {
        val c = listOf(CarbEntry(now - 50 * min, 5.0, 63.0), CarbEntry(now - 20 * min, 4.0, 68.0))
        val s = ReboundGuardEngine.state(now, c, 90.0)
        assertThat(s.active).isTrue()
        assertThat(s.startedAt).isEqualTo(now - 50 * min)
        assertThat(s.until).isEqualTo(now - 20 * min + 120 * min)
    }

    @Test
    fun `carbs at a normal glucose are not a hypo treatment`() {
        assertThat(ReboundGuardEngine.state(now, listOf(CarbEntry(now - 20 * min, 5.0, 110.0)), 120.0).active).isFalse()
    }

    @Test
    fun `more than 12 g is a meal, not a hypo treatment`() {
        val s = ReboundGuardEngine.state(now, listOf(CarbEntry(now - 20 * min, 20.0, 70.0)), 120.0)
        assertThat(s.active).isFalse()
        assertThat(s.offReason).isEqualTo(GuardOff.NO_HYPO_TREATMENT)
    }

    @Test
    fun `a meal after the treatment lets go`() {
        val c = listOf(CarbEntry(now - 40 * min, 5.0, 63.0), CarbEntry(now - 10 * min, 25.0, 100.0))
        val s = ReboundGuardEngine.state(now, c, 130.0)
        assertThat(s.active).isFalse()
        assertThat(s.offReason).isEqualTo(GuardOff.MEAL)
    }

    @Test
    fun `a meal just before the treatment lets go too`() {
        val c = listOf(CarbEntry(now - 45 * min, 30.0, 80.0), CarbEntry(now - 20 * min, 5.0, 70.0))
        assertThat(ReboundGuardEngine.state(now, c, 130.0).offReason).isEqualTo(GuardOff.MEAL)
    }

    @Test
    fun `an old meal does not let go`() {
        val c = listOf(CarbEntry(now - 140 * min, 30.0, 110.0), CarbEntry(now - 20 * min, 5.0, 65.0))
        assertThat(ReboundGuardEngine.state(now, c, 130.0).active).isTrue()
    }

    @Test
    fun `glucose of 13_9 or more lets go`() {
        val c = listOf(CarbEntry(now - 60 * min, 6.0, 60.0))
        val s = ReboundGuardEngine.state(now, c, 252.0)
        assertThat(s.active).isFalse()
        assertThat(s.offReason).isEqualTo(GuardOff.HIGH)
    }

    @Test
    fun `unknown glucose at the entry is not a hypo treatment`() {
        assertThat(ReboundGuardEngine.state(now, listOf(CarbEntry(now - 20 * min, 5.0, null)), 120.0).active).isFalse()
    }

    @Test
    fun `the cap is 1_5 times the profile basal`() {
        assertThat(ReboundGuardEngine.basalCap(0.4)).isWithin(1e-9).of(0.6)
    }
}
