package app.aaps.plugins.aps.openAPSAIMI

import app.aaps.core.data.model.GV
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.interfaces.aps.AutosensResult
import app.aaps.core.interfaces.aps.CurrentTemp
import app.aaps.core.interfaces.aps.GlucoseStatusAIMI
import app.aaps.core.interfaces.aps.IobTotal
import app.aaps.core.interfaces.aps.MealData
import app.aaps.core.interfaces.aps.OapsProfileAimi
import app.aaps.core.interfaces.aps.RT
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.aps.openAPSAIMI.learning.BasalNeuralLearner
import app.aaps.plugins.aps.openAPSAIMI.safety.HypoExposureMonitor
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

/**
 * The hypo history guard inside a whole AIMI decision: the same high and rising BG tick, run once
 * with the guard in "watch only" and once "on", over a 14-day history with many lows.
 *
 * It shows that the engine reads the guard on every tick, that "watch only" changes nothing, and
 * that "on" never gives more insulin, and gives less when there is an algorithm SMB to cut.
 */
class HypoExposureGuardScenarioTest {

    private val now = System.currentTimeMillis()
    private val day = 24L * 60 * 60 * 1000

    /** 14 days of 5-minute readings: about 11 in 100 below 70 and 1 in 100 below 54. */
    private val history: List<GV> = (0 until 14 * 288).map { i ->
        GV(
            timestamp = now - 14 * day + i * 5 * 60_000L,
            raw = null,
            value = when {
                i % 100 == 0 -> 50.0
                i % 9 == 0 -> 60.0
                else -> 140.0
            },
            trendArrow = TrendArrow.NONE,
            noise = null,
            sourceSensor = SourceSensor.UNKNOWN,
        )
    }

    private fun preferences(mode: Int): Preferences = mockk(relaxed = true) {
        every { get(BooleanKey.OApsAIMIT3cBrittleMode) } returns false
        every { get(BooleanKey.AimiPhysioAssistantEnable) } returns false
        every { get(BooleanKey.OApsAIMIUnifiedReactivityEnabled) } returns false
        every { get(BooleanKey.OApsAIMIautoDriveActive) } returns false
        every { get(BooleanKey.OApsAIMIMealAdvisorTrigger) } returns false
        every { get(DoubleKey.OApsAIMIMaxSMB) } returns 1.0
        every { get(DoubleKey.OApsAIMIHighBGMaxSMB) } returns 2.0
        every { get(DoubleKey.OApsAIMITDD7) } returns 17.0
        every { get(DoubleKey.OApsAIMIweight) } returns 25.0
        every { get(DoubleKey.ApsSmbMaxIob) } returns 6.0
        every { get(IntKey.OApsAIMIHypoExposureGuardMode) } returns mode
    }

    private fun profile() = mockk<OapsProfileAimi>(relaxed = true) {
        every { TDD } returns 17.0
        every { current_basal } returns 0.5
        every { max_daily_basal } returns 0.6
        every { max_basal } returns 2.0
        every { max_iob } returns 6.0
        every { sens } returns 160.0
        every { target_bg } returns 117.0
        every { min_bg } returns 117.0
        every { max_bg } returns 117.0
        every { carb_ratio } returns 20.0
        every { dia } returns 5.0
        every { peakTime } returns 60.0
        every { enableUAM } returns true
        every { enableSMB_always } returns true
        every { currentActivity } returns 0.0
        every { futureActivity } returns 0.0
        every { sensorLagActivity } returns 0.0
        every { historicActivity } returns 0.0
        every { lgsThreshold } returns 70
        every { variable_sens } returns 0.0
    }

    /** One decision tick at BG 230 mg/dL rising 6 per 5 min with 0.3 U on board. */
    private fun tick(mode: Int): RT {
        val prefs = preferences(mode)
        val harness = DetermineBasalAimiScenarioTestHarness(
            now = now,
            preferences = prefs,
            trajectoryGuard = mockk(relaxed = true),
            autodriveGater = mockk(relaxed = true),
            autodriveEngine = mockk(relaxed = true),
            physioAdapter = mockk(relaxed = true),
            basalNeuralLearner = mockk<BasalNeuralLearner>(relaxed = true),
        )
        coEvery { harness.persistenceLayer.getBgReadingsDataFromTimeToTime(any(), any(), any()) } returns history
        val monitor = HypoExposureMonitor(
            persistenceLayer = harness.persistenceLayer,
            dateUtil = mockk<DateUtil> { every { now() } returns now },
            preferences = prefs,
            aapsLogger = mockk<AAPSLogger>(relaxed = true),
        )
        harness.engine.hypoExposureMonitor = monitor
        runBlocking { harness.engine.refreshHypoExposure() }
        return harness.engine.determine_basal(
            glucose_status = mockk<GlucoseStatusAIMI>(relaxed = true) {
                every { glucose } returns 230.0
                every { delta } returns 6.0
                every { shortAvgDelta } returns 5.0
                every { longAvgDelta } returns 4.0
                every { combinedDelta } returns 6.0
                every { date } returns now
                every { noise } returns 0.0
            },
            currenttemp = mockk<CurrentTemp>(relaxed = true) {
                every { duration } returns 0
                every { rate } returns 0.5
            },
            iob_data_array = arrayOf(mockk<IobTotal>(relaxed = true) {
                every { iob } returns 0.3
                every { lastBolusTime } returns now - 2 * 60 * 60_000L
            }),
            profile = profile(),
            autosens_data = mockk<AutosensResult>(relaxed = true) { every { ratio } returns 1.0 },
            mealData = mockk<MealData>(relaxed = true) {
                every { lastCarbTime } returns 1L
                every { mealCOB } returns 0.0
                every { slopeFromMinDeviation } returns 2.0
            },
            microBolusAllowed = true,
            currentTime = now,
            flatBGsDetected = false,
            dynIsfMode = false,
            uiInteraction = mockk<UiInteraction>(relaxed = true),
            extraDebug = "hypo history guard scenario",
        )
    }

    @Test
    fun `the engine reads the guard every tick, watch only changes nothing, on never gives more`() {
        val watched = tick(mode = 1)
        val guarded = tick(mode = 2)

        val watchLog = watched.consoleLog.orEmpty().joinToString("\n")
        val onLog = guarded.consoleLog.orEmpty().joinToString("\n")
        assertThat(watchLog).contains("HYPO_EXPOSURE HYPO_PRONE")
        assertThat(watchLog).contains("watch only, would ask ×0.85")
        assertThat(onLog).contains("HYPO_EXPOSURE HYPO_PRONE")
        assertThat(onLog).contains("on, asks ×0.85")
        // Watch only never cuts a dose.
        assertThat(watchLog).doesNotContain("HYPO_EXPOSURE_SMB")

        val watchUnits = watched.units ?: 0.0
        val onUnits = guarded.units ?: 0.0
        assertThat(onUnits).isAtMost(watchUnits + 1e-9)
        if (watchUnits > 0.05) {
            // An algorithm SMB was there to cut: it was cut, and the reason says so.
            assertThat(onUnits).isLessThan(watchUnits)
            assertThat(guarded.reason.toString()).contains("hypo history")
        }
    }
}
