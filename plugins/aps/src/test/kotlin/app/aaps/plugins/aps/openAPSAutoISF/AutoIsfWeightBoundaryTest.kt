package app.aaps.plugins.aps.openAPSAutoISF

import app.aaps.core.data.aps.SMBDefaults
import app.aaps.core.data.configuration.Constants
import app.aaps.core.interfaces.bgQualityCheck.BgQualityCheck
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.iob.GlucoseStatusProvider
import app.aaps.core.interfaces.profiling.Profiler
import app.aaps.core.keys.DoubleKey
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.whenever

/**
 * Deterministic boundary tests for the two BG-range AutoISF weights and the final min/max clamp.
 *
 * These tests exercise the production interpolation and limit functions directly. They do not
 * claim coverage of the acceleration, PP or duration branches, nor do they define a policy for
 * corrupted/non-finite persisted preferences.
 */
class AutoIsfWeightBoundaryTest : TestBaseWithProfile() {

    @Mock lateinit var constraintChecker: ConstraintsChecker
    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var glucoseStatusProvider: GlucoseStatusProvider
    @Mock lateinit var determineBasalSMB: DetermineBasalAutoISF
    @Mock lateinit var bgQualityCheck: BgQualityCheck
    @Mock lateinit var profiler: Profiler

    private lateinit var sut: OpenAPSAutoISFPlugin

    @BeforeEach
    fun setUp() {
        sut = OpenAPSAutoISFPlugin(
            aapsLogger, rxBus, constraintChecker, rh, profileFunction, profileUtil, config, activePlugin,
            iobCobCalculator, hardLimits, preferences, dateUtil, processedTbrEbData, persistenceLayer,
            glucoseStatusProvider, bgQualityCheck, notificationManager, determineBasalSMB, profiler,
            GlucoseStatusCalculatorAutoIsf(
                aapsLogger, iobCobCalculator, dateUtil, decimalFormatter, deltaCalculator
            ), apsResultProvider, ch
        )
    }

    @Test
    fun `lower and higher range weights scale only their respective interpolation sides`() {
        whenever(preferences.get(DoubleKey.ApsAutoIsfLowBgWeight)).thenReturn(2.0)
        whenever(preferences.get(DoubleKey.ApsAutoIsfHighBgWeight)).thenReturn(3.0)

        // Existing interpolation control points: unweighted lower contribution at x=45 is -0.5;
        // unweighted higher contribution at x=130 is +0.25.
        assertThat(sut.interpolate(45.0)).isWithin(1e-9).of(-1.0)
        assertThat(sut.interpolate(130.0)).isWithin(1e-9).of(0.75)
    }

    @Test
    fun `zero range weights neutralize both sides and exact central point`() {
        whenever(preferences.get(DoubleKey.ApsAutoIsfLowBgWeight)).thenReturn(0.0)
        whenever(preferences.get(DoubleKey.ApsAutoIsfHighBgWeight)).thenReturn(0.0)

        assertThat(sut.interpolate(45.0)).isEqualTo(0.0)
        assertThat(sut.interpolate(100.0)).isEqualTo(0.0)
        assertThat(sut.interpolate(130.0)).isEqualTo(0.0)
    }

    @Test
    fun `range selection changes at 100 without applying the opposite weight`() {
        whenever(preferences.get(DoubleKey.ApsAutoIsfLowBgWeight)).thenReturn(2.0)
        whenever(preferences.get(DoubleKey.ApsAutoIsfHighBgWeight)).thenReturn(3.0)

        val below = sut.interpolate(99.999)
        val center = sut.interpolate(100.0)
        val above = sut.interpolate(100.001)

        assertThat(below).isAtMost(0.0)
        assertThat(center).isEqualTo(0.0)
        assertThat(above).isAtLeast(0.0)
    }

    @Test
    fun `min and max limits clamp values immediately outside boundaries`() {
        val min = 0.7
        val max = 1.2
        val sensitivityRatio = 1.0
        val target = Constants.NORMAL_TARGET_MGDL

        fun limited(value: Double) = sut.withinISFlimits(
            value, min, max, sensitivityRatio, "", false, false, target.toDouble(), target
        )

        assertThat(limited(min - 1e-6)).isEqualTo(min)
        assertThat(limited(min)).isEqualTo(min)
        assertThat(limited(min + 1e-6)).isWithin(1e-9).of(min + 1e-6)
        assertThat(limited(max - 1e-6)).isWithin(1e-9).of(max - 1e-6)
        assertThat(limited(max)).isEqualTo(max)
        assertThat(limited(max + 1e-6)).isEqualTo(max)
    }

    @Test
    fun `neutral min max limits preserve factor one`() {
        val result = sut.withinISFlimits(
            1.0, 1.0, 1.0, 1.0, "", false, false,
            Constants.NORMAL_TARGET_MGDL.toDouble(), Constants.NORMAL_TARGET_MGDL
        )

        assertThat(result).isEqualTo(1.0)
    }
}
