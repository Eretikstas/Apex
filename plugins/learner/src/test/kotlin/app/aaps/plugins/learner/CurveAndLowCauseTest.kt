package app.aaps.plugins.learner

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class CurveAndLowCauseTest {

    @Test
    fun `a new curve name, peak or DIA is a change, the first look is not`() {
        val fiasp = CurveSnap("Fiasp test", 35, 5.0)
        assertThat(CurveWatchEngine.changed(null, fiasp)).isFalse()
        assertThat(CurveWatchEngine.changed(fiasp, fiasp)).isFalse()
        assertThat(CurveWatchEngine.changed(fiasp, CurveSnap("Rapid", 75, 8.0))).isTrue()
        assertThat(CurveWatchEngine.changed(fiasp, fiasp.copy(peakMin = 45))).isTrue()
        assertThat(CurveWatchEngine.changed(fiasp, fiasp.copy(diaH = 6.0))).isTrue()
    }

    /** One day of 5 minute steps at 6.5; [edit] changes it. */
    private fun day(edit: (bg: DoubleArray, meal: DoubleArray, hypo: DoubleArray) -> Unit): List<LearnerStep> {
        val bg = DoubleArray(288) { 117.0 }; val meal = DoubleArray(288); val hypo = DoubleArray(288)
        edit(bg, meal, hypo)
        return (0 until 288).map { LearnerStep(it * 300_000L, it / 12, (it % 12) * 5, bg[it], 0.0, meal[it], hypo[it], 24.0) }
    }

    @Test
    fun `a low after a meal eaten at a low glucose`() {
        val s = day { bg, meal, _ -> meal[120] = 20.0; bg[120] = 75.0; for (i in 132..135) bg[i] = 63.0 }
        val e = LowCauseEngine.episodes(s).single()
        assertThat(e.cause).isEqualTo(LowCause.MEAL_AT_LOW)
        assertThat(e.minutes).isEqualTo(20)
    }

    @Test
    fun `a rebound after a hypo treatment`() {
        val s = day { bg, _, hypo -> hypo[100] = 5.0; bg[100] = 63.0; bg[112] = 140.0; for (i in 130..133) bg[i] = 60.0 }
        val lows = LowCauseEngine.episodes(s)
        assertThat(lows.last().cause).isEqualTo(LowCause.REBOUND)
    }

    @Test
    fun `a low after a normal meal, at night, or other`() {
        assertThat(LowCauseEngine.episodes(day { bg, meal, _ -> meal[150] = 25.0; for (i in 165..168) bg[i] = 64.0 }).single().cause)
            .isEqualTo(LowCause.AFTER_MEAL)
        assertThat(LowCauseEngine.episodes(day { bg, _, _ -> for (i in 24..27) bg[i] = 64.0 }).single().cause).isEqualTo(LowCause.NIGHT)
        assertThat(LowCauseEngine.episodes(day { bg, _, _ -> for (i in 180..183) bg[i] = 64.0 }).single().cause).isEqualTo(LowCause.OTHER)
    }

    @Test
    fun `a single low reading is not a low episode`() {
        assertThat(LowCauseEngine.episodes(day { bg, _, _ -> bg[180] = 60.0 })).isEmpty()
    }
}
