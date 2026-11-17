package app.aaps.plugins.aps.openAPSAIMI

import app.aaps.core.data.model.BS
import app.aaps.core.data.model.CA
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.ICfg
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TB
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.interfaces.aps.AutosensResult
import app.aaps.core.interfaces.aps.CurrentTemp
import app.aaps.core.interfaces.aps.GlucoseStatusAIMI
import app.aaps.core.interfaces.aps.IobTotal
import app.aaps.core.interfaces.aps.MealData
import app.aaps.core.interfaces.aps.OapsProfileAimi
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.UnitDoubleKey
import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.DoublePreferenceKey
import app.aaps.core.keys.interfaces.IntPreferenceKey
import app.aaps.core.keys.interfaces.LongPreferenceKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.StringPreferenceKey
import app.aaps.core.keys.interfaces.UnitDoublePreferenceKey
import app.aaps.plugins.aps.openAPSAIMI.learning.BasalNeuralLearner
import app.aaps.plugins.aps.openAPSAIMI.safety.HypoExposureMonitor
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Replays recorded loop cycles through the AIMI decision engine and writes what AIMI would have
 * decided at each one. It needs a prepared input file (`AIMI_REPLAY_IN`, gzipped JSON) and an output
 * path (`AIMI_REPLAY_OUT`); without them it is skipped, so the normal test runs never see it.
 *
 * The input may hold a person's health data: this test prints nothing of it, and the CI job that runs
 * it keeps both files encrypted.
 *
 * Limits of the replay, on purpose: AIMI's own sensitivity chain (PK/PD fusion, physio) is replaced by
 * the profile ISF, its learners are off, and every tick sees the same history AAPS really had.
 */
class HistoryReplayTest {

    private val minute = 60_000L

    @Test
    fun `replay recorded cycles through AIMI`() {
        val inPath = System.getenv("AIMI_REPLAY_IN")
        val outPath = System.getenv("AIMI_REPLAY_OUT")
        assumeTrue(!inPath.isNullOrBlank() && !outPath.isNullOrBlank(), "no replay input given")

        val data = JSONObject(GZIPInputStream(File(inPath!!).inputStream()).bufferedReader().readText())
        val meta = data.getJSONObject("meta")
        val history = readHistory(data)
        val ticks = data.getJSONArray("ticks")

        var now = ticks.getJSONObject(0).getLong("t")
        val prefs = preferences(meta)
        val harness = DetermineBasalAimiScenarioTestHarness(
            now = now,
            preferences = prefs,
            trajectoryGuard = mockk(relaxed = true),
            autodriveGater = mockk(relaxed = true),
            autodriveEngine = mockk(relaxed = true),
            physioAdapter = mockk(relaxed = true),
            basalNeuralLearner = mockk<BasalNeuralLearner>(relaxed = true),
        )
        val clock = mockk<DateUtil>(relaxed = true) { every { now() } answers { now } }
        harness.engine.dateUtil = clock
        stubDatabase(harness, history) { now }
        harness.engine.hypoExposureMonitor = HypoExposureMonitor(
            persistenceLayer = harness.persistenceLayer,
            dateUtil = clock,
            preferences = prefs,
            aapsLogger = mockk<AAPSLogger>(relaxed = true),
        )

        val out = JSONArray()
        var failed = 0
        var lastGuardRefresh = 0L
        for (i in 0 until ticks.length()) {
            val tick = ticks.getJSONObject(i)
            now = tick.getLong("t")
            if (now - lastGuardRefresh > 60 * minute) {
                runBlocking { harness.engine.refreshHypoExposure() }
                lastGuardRefresh = now
            }
            val row = JSONObject().put("t", now)
            try {
                val rt = runTick(harness, tick, meta, now)
                row.put("rate", rt.rate ?: JSONObject.NULL)
                    .put("dur", rt.duration ?: JSONObject.NULL)
                    .put("units", rt.units ?: 0.0)
                    .put("req", rt.insulinReq ?: JSONObject.NULL)
                    .put("ev", rt.eventualBG ?: JSONObject.NULL)
                    .put("reason", rt.reason.toString().take(600))
            } catch (e: Throwable) {
                failed++
                row.put("error", e.javaClass.simpleName)
            }
            out.put(row)
        }
        GZIPOutputStream(File(outPath!!).outputStream()).bufferedWriter().use { it.write(out.toString()) }
        // Counts only: no values of the input reach the test log.
        println("AIMI replay: ${ticks.length()} ticks, $failed failed")
    }

    private class History(val bg: List<GV>, val boluses: List<BS>, val carbs: List<CA>, val temps: List<TB>)

    private fun readHistory(data: JSONObject): History {
        val icfg = ICfg(insulinLabel = "replay", peak = 45, dia = 5.0, concentration = 1.0)
        val bg = data.getJSONArray("bg").rows().map {
            GV(timestamp = it.getLong(0), raw = null, value = it.getDouble(1), trendArrow = TrendArrow.NONE, noise = null, sourceSensor = SourceSensor.UNKNOWN)
        }
        val boluses = data.getJSONArray("boluses").rows().map {
            BS(timestamp = it.getLong(0), amount = it.getDouble(1), type = if (it.getBoolean(2)) BS.Type.SMB else BS.Type.NORMAL, iCfg = icfg)
        }
        val carbs = data.getJSONArray("carbs").rows().map { CA(timestamp = it.getLong(0), duration = 0L, amount = it.getDouble(1)) }
        val temps = data.getJSONArray("temps").rows().map {
            TB(timestamp = it.getLong(0), type = TB.Type.NORMAL, isAbsolute = true, rate = it.getDouble(1), duration = it.getLong(2))
        }
        return History(bg, boluses, carbs, temps)
    }

    private fun JSONArray.rows(): List<JSONArray> = (0 until length()).map { getJSONArray(it) }

    /** The database as AAPS had it at the tick: nothing after `now()`. */
    private fun stubDatabase(harness: DetermineBasalAimiScenarioTestHarness, h: History, now: () -> Long) {
        val db = harness.persistenceLayer
        coEvery { db.getBgReadingsDataFromTime(any(), any()) } answers {
            val from = firstArg<Long>(); val asc = secondArg<Boolean>()
            h.bg.filter { it.timestamp in from..now() }.let { if (asc) it else it.reversed() }
        }
        coEvery { db.getBgReadingsDataFromTimeToTime(any(), any(), any()) } answers {
            val from = firstArg<Long>(); val to = minOf(secondArg<Long>(), now()); val asc = thirdArg<Boolean>()
            h.bg.filter { it.timestamp in from..to }.let { if (asc) it else it.reversed() }
        }
        coEvery { db.getBolusesFromTime(any(), any()) } answers {
            val from = firstArg<Long>(); val asc = secondArg<Boolean>()
            h.boluses.filter { it.timestamp in from..now() }.let { if (asc) it else it.reversed() }
        }
        coEvery { db.getCarbsFromTime(any(), any()) } answers {
            val from = firstArg<Long>(); val asc = secondArg<Boolean>()
            h.carbs.filter { it.timestamp in from..now() }.let { if (asc) it else it.reversed() }
        }
        coEvery { db.getTemporaryBasalsStartingFromTime(any(), any()) } answers {
            val from = firstArg<Long>(); val asc = secondArg<Boolean>()
            h.temps.filter { it.timestamp in from..now() }.let { if (asc) it else it.reversed() }
        }
        coEvery { db.getMostRecentCarbByDate() } answers { h.carbs.lastOrNull { it.timestamp <= now() }?.timestamp }
        coEvery { db.getMostRecentCarbAmount() } answers { h.carbs.lastOrNull { it.timestamp <= now() }?.amount }
    }

    /** Every setting at its default, except the ones that describe the child and the pump limits. */
    private fun preferences(meta: JSONObject): Preferences = mockk(relaxed = true) {
        every { get(any<BooleanPreferenceKey>()) } answers { firstArg<BooleanPreferenceKey>().defaultValue }
        every { get(any<DoublePreferenceKey>()) } answers { firstArg<DoublePreferenceKey>().defaultValue }
        every { get(any<IntPreferenceKey>()) } answers { firstArg<IntPreferenceKey>().defaultValue }
        every { get(any<LongPreferenceKey>()) } answers { firstArg<LongPreferenceKey>().defaultValue }
        every { get(any<StringPreferenceKey>()) } answers { firstArg<StringPreferenceKey>().defaultValue }
        every { get(any<UnitDoublePreferenceKey>()) } answers { firstArg<UnitDoublePreferenceKey>().defaultValue }
        every { get(DoubleKey.OApsAIMIweight) } returns meta.getDouble("weightKg")
        every { get(DoubleKey.OApsAIMITDD7) } returns meta.getDouble("tdd")
        every { get(DoubleKey.ApsSmbMaxIob) } returns meta.getDouble("maxIob")
        every { get(DoubleKey.SafetyMaxBolus) } returns meta.getDouble("maxBolus")
        every { get(DoubleKey.ApsMaxBasal) } returns meta.getDouble("maxBasal")
        every { get(IntKey.ApsMaxSmbFrequency) } returns meta.getInt("smbInterval")
        every { get(IntKey.ApsMaxMinutesOfBasalToLimitSmb) } returns meta.getInt("maxSmbMin")
        every { get(IntKey.ApsUamMaxMinutesOfBasalToLimitSmb) } returns meta.getInt("maxUamSmbMin")
        every { get(UnitDoubleKey.ApsLgsThreshold) } returns meta.getDouble("lgs")
    }

    private fun runTick(harness: DetermineBasalAimiScenarioTestHarness, tick: JSONObject, meta: JSONObject, now: Long) =
        run {
            val gs = tick.getJSONObject("gs")
            val prof = tick.getJSONObject("prof")
            val act = tick.getJSONObject("act")
            val temp = tick.getJSONObject("temp")
            val iob = tick.getJSONArray("iob")
            val lastBolus = tick.getLong("lastBolus")
            val iobArray = Array(iob.length()) { k ->
                val r = iob.getJSONArray(k)
                IobTotal(time = now + k * 5 * minute, iob = r.getDouble(0), activity = r.getDouble(1), basaliob = r.getDouble(2), lastBolusTime = lastBolus)
            }
            val tt = if (tick.isNull("tt")) null else tick.getDouble("tt")
            val target = tt ?: prof.getDouble("target")
            val profile = OapsProfileAimi(
                dia = prof.getDouble("dia"),
                min_5m_carbimpact = 0.0,
                max_iob = meta.getDouble("maxIob"),
                max_daily_basal = prof.getDouble("basal"),
                max_basal = meta.getDouble("maxBasal"),
                min_bg = target,
                max_bg = target,
                target_bg = target,
                carb_ratio = prof.getDouble("ic"),
                sens = prof.getDouble("isf"),
                autosens_adjust_targets = false,
                max_daily_safety_multiplier = 3.0,
                current_basal_safety_multiplier = 4.0,
                high_temptarget_raises_sensitivity = false,
                low_temptarget_lowers_sensitivity = false,
                sensitivity_raises_target = false,
                resistance_lowers_target = false,
                adv_target_adjustments = false,
                exercise_mode = false,
                half_basal_exercise_target = 160,
                maxCOB = 120,
                skip_neutral_temps = false,
                remainingCarbsCap = 90,
                enableUAM = true,
                A52_risk_enable = false,
                SMBInterval = meta.getInt("smbInterval"),
                enableSMB_with_COB = true,
                enableSMB_with_temptarget = true,
                allowSMB_with_high_temptarget = false,
                enableSMB_always = true,
                enableSMB_after_carbs = true,
                maxSMBBasalMinutes = meta.getInt("maxSmbMin"),
                maxUAMSMBBasalMinutes = meta.getInt("maxUamSmbMin"),
                bolus_increment = 0.025,
                carbsReqThreshold = 8,
                current_basal = prof.getDouble("basal"),
                temptargetSet = tt != null,
                autosens_max = 1.2,
                out_units = "mmol/L",
                lgsThreshold = meta.getDouble("lgs").toInt(),
                variable_sens = prof.getDouble("isf"),
                insulinDivisor = 75,
                TDD = meta.getDouble("tdd"),
                peakTime = prof.getDouble("peak"),
                futureActivity = act.getDouble("fut"),
                sensorLagActivity = act.getDouble("lag"),
                historicActivity = act.getDouble("hist"),
                currentActivity = act.getDouble("cur"),
            )
            harness.engine.determine_basal(
                glucose_status = GlucoseStatusAIMI(
                    glucose = gs.getDouble("g"), noise = 0.0, delta = gs.getDouble("d"),
                    shortAvgDelta = gs.getDouble("sad"), longAvgDelta = gs.getDouble("lad"), date = gs.getLong("t")
                ),
                currenttemp = CurrentTemp(duration = temp.getInt("dur"), rate = temp.getDouble("rate"), minutesrunning = temp.getInt("running")),
                iob_data_array = iobArray,
                profile = profile,
                autosens_data = AutosensResult(ratio = 1.0),
                mealData = MealData(
                    carbs = tick.getDouble("carbs3h"), mealCOB = tick.getDouble("cob"),
                    lastBolusTime = lastBolus, lastCarbTime = tick.getLong("lastCarb")
                ),
                microBolusAllowed = true,
                currentTime = now,
                flatBGsDetected = false,
                dynIsfMode = false,
                uiInteraction = mockk<UiInteraction>(relaxed = true),
                extraDebug = "history replay",
            )
        }
}
