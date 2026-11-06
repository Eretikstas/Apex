package app.aaps.plugins.aps.openAPSAIMI.safety

import app.aaps.plugins.aps.openAPSAIMI.safety.HypoExposureGuard.Exposure
import app.aaps.plugins.aps.openAPSAIMI.safety.HypoExposureGuard.Level
import app.aaps.plugins.aps.openAPSAIMI.safety.HypoExposureGuard.Mode
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class HypoExposureGuardTest {

    private fun exposure(below70: Double, below54: Double, days: Int = 14, readings: Int = 4000) =
        Exposure(daysWithData = days, readings = readings, below70Pct = below70, below54Pct = below54)

    // ---- levels ---------------------------------------------------------------------------------

    @Test
    fun `within targets nothing is asked`() {
        val v = HypoExposureGuard.verdictOf(exposure(below70 = 3.9, below54 = 0.9))
        assertThat(v.level).isEqualTo(Level.NORMAL)
        assertThat(v.scale).isEqualTo(1.0)
        assertThat(v.asksForLess).isFalse()
    }

    @Test
    fun `exactly at the targets is still normal`() {
        val v = HypoExposureGuard.verdictOf(exposure(below70 = 4.0, below54 = 1.0))
        assertThat(v.level).isEqualTo(Level.NORMAL)
    }

    @Test
    fun `above a target asks for 0_92 and nothing more`() {
        for (e in listOf(exposure(below70 = 4.1, below54 = 0.0), exposure(below70 = 0.0, below54 = 1.1))) {
            val v = HypoExposureGuard.verdictOf(e)
            assertThat(v.level).isEqualTo(Level.ABOVE_TARGET)
            assertThat(v.scale).isEqualTo(0.92)
            assertThat(v.stopExtraBoosts).isFalse()
            assertThat(v.learnedOnlyGentler).isFalse()
        }
    }

    @Test
    fun `at the hypo-prone cut-points it is above target, past them hypo-prone`() {
        assertThat(HypoExposureGuard.verdictOf(exposure(below70 = 6.0, below54 = 1.5)).level).isEqualTo(Level.ABOVE_TARGET)
        assertThat(HypoExposureGuard.verdictOf(exposure(below70 = 6.1, below54 = 0.0)).level).isEqualTo(Level.HYPO_PRONE)
        assertThat(HypoExposureGuard.verdictOf(exposure(below70 = 0.0, below54 = 1.6)).level).isEqualTo(Level.HYPO_PRONE)
    }

    @Test
    fun `the real case of 2026-11 is hypo-prone, 0_85, extra boosts off, learned only gentler`() {
        // 7 days to 2026-11-06: 11.3 % below 70, 1.0 % below 54.
        val v = HypoExposureGuard.verdictOf(exposure(below70 = 11.3, below54 = 1.0))
        assertThat(v.level).isEqualTo(Level.HYPO_PRONE)
        assertThat(v.scale).isEqualTo(0.85)
        assertThat(v.stopExtraBoosts).isTrue()
        assertThat(v.learnedOnlyGentler).isTrue()
    }

    // ---- not enough data ------------------------------------------------------------------------

    @Test
    fun `fewer than 7 days or 1500 readings asks for nothing`() {
        assertThat(HypoExposureGuard.verdictOf(exposure(below70 = 20.0, below54 = 5.0, days = 6)).level).isEqualTo(Level.NOT_ENOUGH_DATA)
        assertThat(HypoExposureGuard.verdictOf(exposure(below70 = 20.0, below54 = 5.0, readings = 1499)).asksForLess).isFalse()
        assertThat(HypoExposureGuard.verdictOf(exposure(below70 = 20.0, below54 = 5.0, days = 7, readings = 1500)).level).isEqualTo(Level.HYPO_PRONE)
    }

    // ---- exposure from readings -----------------------------------------------------------------

    @Test
    fun `exposure counts readings below 70 and 54 and the days with data`() {
        val day = 24L * 60 * 60 * 1000
        val readings = listOf(
            0L to 100.0, 1L to 65.0, 2L to 50.0, 3L to 120.0, // day 0: one below 70, one below 54 (also below 70)
            day to 80.0, day + 1 to 90.0, day + 2 to 69.9, day + 3 to Double.NaN, // day 1: NaN left out
        )
        val e = HypoExposureGuard.exposureOf(readings) { it / day }
        assertThat(e.readings).isEqualTo(7)
        assertThat(e.daysWithData).isEqualTo(2)
        assertThat(e.below70Pct).isWithin(1e-9).of(3 * 100.0 / 7)
        assertThat(e.below54Pct).isWithin(1e-9).of(100.0 / 7)
    }

    @Test
    fun `no readings is no data`() {
        val e = HypoExposureGuard.exposureOf(emptyList()) { 0L }
        assertThat(e.readings).isEqualTo(0)
        assertThat(HypoExposureGuard.verdictOf(e).asksForLess).isFalse()
    }

    // ---- modes ----------------------------------------------------------------------------------

    @Test
    fun `only the on mode changes doses`() {
        val hypoProne = HypoExposureGuard.verdictOf(exposure(below70 = 11.3, below54 = 1.0))
        assertThat(HypoExposureGuard.applied(hypoProne, Mode.OFF).asksForLess).isFalse()
        assertThat(HypoExposureGuard.applied(hypoProne, Mode.WATCH).asksForLess).isFalse()
        assertThat(HypoExposureGuard.applied(hypoProne, Mode.ON)).isEqualTo(hypoProne)
        // Watch keeps the numbers for the log.
        assertThat(HypoExposureGuard.applied(hypoProne, Mode.WATCH).exposure).isEqualTo(hypoProne.exposure)
    }

    @Test
    fun `an unknown stored mode is watch only, the default`() {
        assertThat(Mode.of(7)).isEqualTo(Mode.WATCH)
        assertThat(Mode.of(0)).isEqualTo(Mode.OFF)
        assertThat(Mode.of(2)).isEqualTo(Mode.ON)
    }

    // ---- what is changed, and what is never changed ---------------------------------------------

    private val hypoProne = HypoExposureGuard.verdictOf(exposure(below70 = 11.3, below54 = 1.0))
    private val aboveTarget = HypoExposureGuard.verdictOf(exposure(below70 = 5.0, below54 = 0.5))
    private val neutral = HypoExposureGuard.Verdict.NEUTRAL

    @Test
    fun `a dose is scaled down and never up`() {
        assertThat(HypoExposureGuard.scaleDose(0.5, hypoProne)).isWithin(1e-9).of(0.425)
        assertThat(HypoExposureGuard.scaleDose(0.5, aboveTarget)).isWithin(1e-9).of(0.46)
        assertThat(HypoExposureGuard.scaleDose(0.5, neutral)).isEqualTo(0.5)
        assertThat(HypoExposureGuard.scaleDose(0.0, hypoProne)).isEqualTo(0.0)
    }

    @Test
    fun `only the part of a basal multiplier above 1_0 is touched`() {
        // Hypo-prone: a learned boost is removed.
        assertThat(HypoExposureGuard.scaleBoostMultiplier(1.5, hypoProne)).isEqualTo(1.0)
        // Above target: the boost part is scaled, 1 + 0.5 x 0.92.
        assertThat(HypoExposureGuard.scaleBoostMultiplier(1.5, aboveTarget)).isWithin(1e-9).of(1.46)
        // A lower basal is never touched: it removes insulin.
        assertThat(HypoExposureGuard.scaleBoostMultiplier(0.7, hypoProne)).isEqualTo(0.7)
        assertThat(HypoExposureGuard.scaleBoostMultiplier(1.0, hypoProne)).isEqualTo(1.0)
        assertThat(HypoExposureGuard.scaleBoostMultiplier(1.5, neutral)).isEqualTo(1.5)
    }

    @Test
    fun `learned factors may only be gentler when hypo-prone`() {
        assertThat(HypoExposureGuard.learnedFactor(1.3, hypoProne)).isEqualTo(1.0)
        assertThat(HypoExposureGuard.learnedFactor(0.8, hypoProne)).isEqualTo(0.8)
        assertThat(HypoExposureGuard.learnedFactor(1.3, aboveTarget)).isEqualTo(1.3)
        assertThat(HypoExposureGuard.learnedFactor(1.3, neutral)).isEqualTo(1.3)
    }

    @Test
    fun `the log line says what it would do in watch mode`() {
        val line = HypoExposureGuard.describe(hypoProne, Mode.WATCH)
        assertThat(line).contains("HYPO_PRONE")
        assertThat(line).contains("watch only, would ask ×0.85")
        assertThat(line).contains("<70 11.3%")
    }
}
