package app.aaps.plugins.learner

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class SensorHintEngineTest {

    private val min = 60_000L
    private val change = 100_000 * min

    /** One reading a minute from [from] minutes after the change, values in mmol/L. */
    private fun everyMinute(from: Int, vararg mmol: Double) = mmol.mapIndexed { i, v -> (change + (from + i) * min) to v * 18.0 }

    /** One reading every 5 minutes from [from] minutes after the change, values in mmol/L. */
    private fun everyFive(from: Int, vararg mmol: Double) = mmol.mapIndexed { i, v -> (change + (from + 5 * i) * min) to v * 18.0 }

    @Test
    fun `no change known or too long ago gives no hint`() {
        val r = everyFive(0, 6.0, 6.1, 6.2)
        assertThat(SensorHintEngine.check(change + 30 * min, null, r)).isNull()
        assertThat(SensorHintEngine.check(change + 25 * 60 * min, change, r)).isNull()
        assertThat(SensorHintEngine.check(change - min, change, r)).isNull()
    }

    @Test
    fun `first two hours after a change give the first hours hint`() {
        val h = SensorHintEngine.check(change + 30 * min, change, everyFive(0, 6.0, 6.1, 6.2, 6.1))
        assertThat(h?.kind).isEqualTo(SensorHintKind.FIRST_HOURS)
        assertThat(SensorHintEngine.check(change + 3 * 60 * min, change, everyFive(0, 6.0, 6.1, 6.2))).isNull()
    }

    @Test
    fun `a first value far above the last one before the change is a jump`() {
        // A first value far above the last one before the change.
        val r = listOf((change - 5 * min) to 7.5 * 18.0, (change + min) to 21.0 * 18.0) + everyFive(6, 9.1, 5.5, 5.6)
        val h = SensorHintEngine.check(change + 20 * min, change, r)
        assertThat(h?.kind).isEqualTo(SensorHintKind.JUMP)
        assertThat(h?.toMgdl).isWithin(0.01).of(5.5 * 18.0)
    }

    @Test
    fun `a rise of 1_5 in 5 minutes on the first day is a jump, read every minute`() {
        // 3.0 -> 4.5 in 5 minutes, 10 hours after the change.
        val r = everyMinute(600, 3.0, 3.0, 3.0, 3.2, 3.6, 4.1, 4.5, 4.4, 4.4)
        val h = SensorHintEngine.check(change + 620 * min, change, r)
        assertThat(h?.kind).isEqualTo(SensorHintKind.JUMP)
        assertThat(h?.fromMgdl).isWithin(0.01).of(3.0 * 18.0)
    }

    @Test
    fun `a smooth fall to a low is not a jump`() {
        // A real low must never be called false: 5.3 -> 3.7 over an hour.
        val r = everyFive(600, 5.3, 5.1, 4.9, 4.7, 4.5, 4.4, 4.2, 4.1, 4.0, 3.9, 3.8, 3.7)
        assertThat(SensorHintEngine.check(change + 660 * min, change, r)).isNull()
    }

    @Test
    fun `a fast real rise of 1_0 in 5 minutes is not a jump`() {
        val r = everyFive(600, 6.0, 7.0, 8.0, 9.0)
        assertThat(SensorHintEngine.check(change + 620 * min, change, r)).isNull()
    }

    @Test
    fun `a jump is shown for 30 minutes, then the first hours hint comes back`() {
        val r = listOf((change - 5 * min) to 7.5 * 18.0, (change + min) to 21.0 * 18.0) + everyFive(6, 9.1, 9.0, 8.9)
        assertThat(SensorHintEngine.check(change + 30 * min, change, r)?.kind).isEqualTo(SensorHintKind.JUMP)
        assertThat(SensorHintEngine.check(change + 50 * min, change, r)?.kind).isEqualTo(SensorHintKind.FIRST_HOURS)
    }

    @Test
    fun `a jump after 24 hours is not reported`() {
        val r = everyFive(25 * 60, 3.0, 4.6)
        assertThat(SensorHintEngine.check(change + 25 * 60 * min + 10 * min, change, r)).isNull()
    }

    @Test
    fun `readings with a gap longer than 7 minutes are not a pair`() {
        val r = listOf((change + 600 * min) to 3.0 * 18.0, (change + 610 * min) to 4.6 * 18.0)
        assertThat(SensorHintEngine.check(change + 611 * min, change, r)).isNull()
    }
}
