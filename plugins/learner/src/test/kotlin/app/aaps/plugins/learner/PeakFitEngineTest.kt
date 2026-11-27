package app.aaps.plugins.learner

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class PeakFitEngineTest {

    /**
     * [days] days where the glucose follows insulin with a TRUE peak of [truePeak] min (DIA 5 h): basal 0.02 U per 5 min,
     * night boluses of 0.3 U at 00:30 and 03:00; no food at night (one meal at 18:00).
     */
    private fun days(truePeak: Double, days: Int = 7): List<LearnerStep> {
        val n = days * 288
        val ins = DoubleArray(n) { 0.02 }
        val meal = DoubleArray(n)
        for (d in 0 until days) {
            ins[d * 288 + 6] += 0.3
            ins[d * 288 + 36] += 0.3
            meal[d * 288 + 216] = 20.0
        }
        val bg = DoubleArray(n)
        bg[0] = 130.0
        for (i in 1 until n) {
            var act = 0.0
            for (j in (i - 60).coerceAtLeast(0)..i) act += ins[j] * PeakFitEngine.activity((i - j) * 5.0, truePeak, 300.0) * 5.0
            // The liver gives what the basal takes; the boluses lower the glucose (ISF 150 mg/dL per U); the 18:00 meal
            // lifts it by 90 mg/dL over 2 h.
            val food = if (i % 288 in 216 until 240) 90.0 / 24 else 0.0
            bg[i] = (bg[i - 1] + 150.0 * (0.02 - act) + food).coerceIn(60.0, 300.0)
        }
        return (0 until n).map { LearnerStep(it * 300_000L, (it / 12) % 24, (it % 12) * 5, bg[it], ins[it], meal[it], 0.0, 24.0) }
    }

    @Test
    fun `finds the true peak and proposes it when the profile is far off`() {
        val f = PeakFitEngine.fit(days(35.0), currentPeakMin = 75, diaH = 5.0)!!
        assertThat(f.bestPeakMin).isIn(30..40)
        assertThat(PeakFitEngine.proposes(f)).isTrue()
    }

    @Test
    fun `no proposal when the profile peak is already right`() {
        val f = PeakFitEngine.fit(days(35.0), currentPeakMin = 35, diaH = 5.0)!!
        assertThat(PeakFitEngine.proposes(f)).isFalse()
    }

    @Test
    fun `too few nights give nothing`() {
        assertThat(PeakFitEngine.fit(days(35.0, days = 3), currentPeakMin = 75, diaH = 5.0)).isNull()
    }
}
