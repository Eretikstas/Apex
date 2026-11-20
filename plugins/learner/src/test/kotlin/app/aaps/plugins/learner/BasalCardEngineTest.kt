package app.aaps.plugins.learner

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * The basal card on a history with a known need: profile 0.30 U/h all day, the body needs 0.40 at 03-06 h and 0.20 at
 * 15-18 h. Glucose moves by exactly what the missing or extra insulin does (ISF 153 mg/dL/U), no meals.
 */
class BasalCardEngineTest {

    private val isf = 153.0
    private val profile = DoubleArray(24) { 0.30 }
    private fun need(h: Int) = when (h) {
        in 3..6   -> 0.40
        in 15..18 -> 0.20
        else      -> 0.30
    }

    private fun history(days: Int = 16, cannula: Double? = 24.0, lowsAtHour: Int? = null): List<LearnerStep> {
        val steps = ArrayList<LearnerStep>()
        var bg = 120.0
        for (i in 0 until days * 288) {
            val h = (i / 12) % 24
            val m = (i % 12) * 5
            val low = lowsAtHour != null && h == lowsAtHour && i >= (days - 7) * 288 && m < 40
            steps += LearnerStep(
                time = i * 300_000L, hour = h, minute = m, bgMgdl = if (low) 60.0 else bg,
                insulinU = profile[h] * 5 / 60, mealG = 0.0, hypoG = 0.0, cannulaAgeH = cannula
            )
            bg += (need(h) - profile[h]) * isf / 12
        }
        return steps
    }

    private val engine = BasalCardEngine(isfMgdl = isf, icG = 24.0, peakMin = 35.0, diaH = 5.0)

    @Test
    fun `the need of the clean hours is found`() {
        val needs = engine.hourNeeds(history().takeLast(14 * 288))
        assertThat(needs[4].size).isAtLeast(10)
        assertThat(needs[4].sorted()[needs[4].size / 2]).isWithin(0.02).of(0.40)
        assertThat(needs[16].sorted()[needs[16].size / 2]).isWithin(0.02).of(0.20)
        assertThat(needs[10].sorted()[needs[10].size / 2]).isWithin(0.02).of(0.30)
    }

    @Test
    fun `one card is not enough, the second card proposes half way, both ways`() {
        val first = engine.card(history(), profile, BasalCardState())
        assertThat(first.changes).isEmpty()
        val second = engine.card(history(), profile, first.state)
        val byHour = second.changes.associateBy { it.hour }
        assertThat(byHour[4]!!.to).isWithin(0.001).of(0.35)
        assertThat(byHour[5]!!.to).isWithin(0.001).of(0.35)
        assertThat(byHour[16]!!.to).isWithin(0.001).of(0.25)
        assertThat(byHour[17]!!.to).isWithin(0.001).of(0.25)
        assertThat(byHour.keys).containsNoneOf(9, 10, 11, 22)
        second.hours.forEach { assertThat(it.source).isEqualTo(HourSource.MEASURED) }
    }

    @Test
    fun `no step is bigger than 20 percent`() {
        val big = DoubleArray(24) { 0.10 }
        val first = engine.card(history(), big, BasalCardState())
        val second = engine.card(history(), big, first.state)
        second.changes.forEach { assertThat(it.to).isAtMost(it.from * 1.2 + 0.001) }
        assertThat(second.changes).isNotEmpty()
    }

    @Test
    fun `lows near the hour block a raise`() {
        val h = history(lowsAtHour = 4)
        val first = engine.card(h, profile, BasalCardState())
        val second = engine.card(h, profile, first.state)
        // No raise near the lows; the lows themselves may bring an hour down, which is allowed.
        assertThat(second.changes.filter { it.to > it.from }.map { it.hour }).containsNoneOf(2, 3, 4, 5, 6)
    }

    @Test
    fun `hours with an old cannula are not used`() {
        val card = engine.card(history(cannula = 80.0), profile, BasalCardState())
        card.hours.forEach { assertThat(it.source).isEqualTo(HourSource.NONE) }
        assertThat(card.changes).isEmpty()
    }

    @Test
    fun `an hour with no windows takes the value between the measured hours`() {
        // Meals at 12:00 every day leave 12-16 h without clean windows (5 h after a meal).
        val h = history().map { if (it.hour == 12 && it.minute == 0) it.copy(mealG = 30.0) else it }
        val card = engine.card(h, profile, BasalCardState())
        val r = card.hours.associateBy { it.hour }
        assertThat(r[14]!!.source).isEqualTo(HourSource.ESTIMATE)
        assertThat(r[10]!!.source).isEqualTo(HourSource.MEASURED)
    }
}
