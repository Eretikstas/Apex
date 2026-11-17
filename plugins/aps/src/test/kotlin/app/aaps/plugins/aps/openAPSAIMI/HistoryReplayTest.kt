package app.aaps.plugins.aps.openAPSAIMI

import android.content.Context
import android.os.Environment
import app.aaps.plugins.aps.openAPSAIMI.learning.BasalLearner
import app.aaps.plugins.aps.openAPSAIMI.learning.UnifiedReactivityLearner
import app.aaps.plugins.aps.openAPSAIMI.utils.AimiStorageHelper
import io.mockk.mockkStatic
import java.nio.file.Files
import java.time.Instant
import java.time.ZoneOffset
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
import app.aaps.core.keys.BooleanKey
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
import app.aaps.plugins.aps.openAPSAIMI.basal.BasalDecisionEngine
import app.aaps.plugins.aps.openAPSAIMI.basal.BasalPlanner
import app.aaps.plugins.aps.openAPSAIMI.basal.DynamicBasalController
import app.aaps.plugins.aps.openAPSAIMI.context.ContextInfluenceEngine
import app.aaps.plugins.aps.openAPSAIMI.learning.BasalNeuralLearner
import app.aaps.plugins.aps.openAPSAIMI.physio.PhysioMultipliersMTR
import app.aaps.plugins.aps.openAPSAIMI.validation.PumpCapabilityValidator
import app.aaps.plugins.aps.openAPSAIMI.wcycle.WCycleFacade
import app.aaps.plugins.aps.openAPSAIMI.safety.HypoExposureMonitor
import io.mockk.clearAllMocks
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
        // Every AIMI timer and learner window follows the replayed time.
        AimiReplayClock.override = { now }
        val learn = System.getenv("AIMI_REPLAY_LEARN") == "1"
        val prefs = preferences(meta)
        // Learner files live in a fresh folder of this run.
        val storageDir = Files.createTempDirectory("aimi-replay").toFile()
        mockkStatic(Environment::class)
        every { Environment.getExternalStorageDirectory() } returns storageDir
        val appContext = mockk<Context>(relaxed = true) { every { getExternalFilesDir(any()) } returns storageDir }
        val storage = AimiStorageHelper(appContext, mockk(relaxed = true))
        val harness = DetermineBasalAimiScenarioTestHarness(
            now = now,
            preferences = prefs,
            trajectoryGuard = mockk(relaxed = true),
            autodriveGater = mockk(relaxed = true),
            autodriveEngine = mockk(relaxed = true),
            physioAdapter = mockk(relaxed = true),
            basalNeuralLearner = if (learn) BasalNeuralLearner(appContext, prefs, storage, mockk(relaxed = true))
            else mockk<BasalNeuralLearner>(relaxed = true) {
                // Neutral answer: no learned basal change (the learners are off in this replay).
                every { getUniversalBasalDecision(any(), any(), any(), any(), any(), any(), any()) } returns
                    BasalNeuralLearner.UniversalBasalDecision(1.0, 1.0, BasalNeuralLearner.BasalMultiplierSource.DISABLED, 0.7, 1.3)
            },
            pumpCapabilityValidator = PumpCapabilityValidator(),
            dynamicBasalController = DynamicBasalController(mockk(relaxed = true)),
            wCycleFacade = mockk<WCycleFacade>(relaxed = true) { every { getIcMultiplier() } returns 1.0 },
        )
        // Real basal decision parts; learners and context give their neutral value (a relaxed mock would say 0).
        val adaptiveBasal = AIMIAdaptiveBasal(mockk(relaxed = true), mockk(relaxed = true))
        harness.engine.basalDecisionEngine = BasalDecisionEngine(mockk(relaxed = true), adaptiveBasal, BasalPlanner(adaptiveBasal, mockk(relaxed = true)))
        // The harness mocks the storage root again: point it back at this run's folder.
        every { Environment.getExternalStorageDirectory() } returns storageDir
        harness.engine.storageHelper = storage
        if (learn) {
            harness.engine.basalLearner = BasalLearner(appContext, mockk(relaxed = true), storage)
        } else {
            harness.engine.basalLearner = mockk(relaxed = true) { every { getMultiplier() } returns 1.0 }
            harness.engine.unifiedReactivityLearner = mockk(relaxed = true) { every { getCombinedFactor(any()) } returns 1.0 }
        }
        harness.engine.contextInfluenceEngine = mockk(relaxed = true) {
            every { computeInfluence(any(), any(), any(), any(), any()) } returns ContextInfluenceEngine.ContextInfluence.neutral()
        }
        every { harness.physioAdapter.getMultipliers(any(), any(), any(), any(), any()) } returns PhysioMultipliersMTR()
        val clock = mockk<DateUtil>(relaxed = true) { every { now() } answers { now } }
        harness.engine.dateUtil = clock
        stubDatabase(harness, history) { now }
        if (learn) harness.engine.unifiedReactivityLearner = UnifiedReactivityLearner(appContext, harness.persistenceLayer, clock, prefs, mockk(relaxed = true), storage)
        harness.engine.hypoExposureMonitor = HypoExposureMonitor(
            persistenceLayer = harness.persistenceLayer,
            dateUtil = clock,
            preferences = prefs,
            aapsLogger = mockk<AAPSLogger>(relaxed = true),
        )

        val startMs = ticks.getJSONObject(0).getLong("t")
        val basalTimes = (0 until ticks.length()).map { ticks.getJSONObject(it).getLong("t") }
        val basals = (0 until ticks.length()).map { ticks.getJSONObject(it).getJSONObject("prof").getDouble("basal") }
        val basalAt: (Long) -> Double = { t ->
            val k = basalTimes.binarySearch(t).let { if (it >= 0) it else (-it - 2).coerceAtLeast(0) }
            basals[k]
        }
        val bolusRows = data.getJSONArray("boluses").rows()
        val chain = Chain(
            startMs = startMs,
            manual = bolusRows.filter { !it.getBoolean(2) }.map { Pair(it.getLong(0), it.getDouble(1)) },
            realBefore = bolusRows.filter { it.getBoolean(2) && it.getLong(0) < startMs }.map { Pair(it.getLong(0), it.getDouble(1)) },
            realTempsBefore = data.getJSONArray("temps").rows().filter { it.getLong(0) < startMs }
                .map { OwnTemp(it.getLong(0), it.getDouble(1), minOf(it.getLong(0) + it.getLong(2), startMs)) },
            basalAt = basalAt,
        )
        // The database AIMI reads holds the same: human boluses, AutoISF's doses before the start, AIMI's after.
        history.boluses.removeAll { it.type == BS.Type.SMB && it.timestamp >= startMs }
        history.temps.removeAll { it.timestamp >= startMs }

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
                val chained = chainedInputs(chain, tick, now)
                val rt = runTick(harness, tick, meta, now, chained)
                val smb = rt.units ?: 0.0
                if (smb > 0.0) {
                    chain.ownSmb.add(Pair(now, smb))
                    history.boluses.add(BS(timestamp = now, amount = smb, type = BS.Type.SMB, iCfg = ICfg(insulinLabel = "replay", peak = 45, dia = 5.0, concentration = 1.0)))
                }
                val rate = rt.rate
                val dur = rt.duration
                if (rate != null && dur != null) {
                    chain.activeTemp(now)?.let { it.end = now }
                    if (dur > 0) {
                        chain.ownTemps.add(OwnTemp(now, rate, now + dur * minute))
                        history.temps.add(TB(timestamp = now, type = TB.Type.NORMAL, isAbsolute = true, rate = rate, duration = dur * minute))
                    }
                }
                if (learn && i % 12 == 0) {
                    row.put("learn", JSONObject()
                        .put("basal", harness.engine.basalLearner.getMultiplier())
                        .put("react", harness.engine.unifiedReactivityLearner.getCombinedFactor(Instant.ofEpochMilli(now).atZone(ZoneOffset.ofHours(3)).hour)))
                }
                row.put("iob", chained.iobArray[0].iob)
                    .put("errors", (rt.consoleError ?: emptyList<String>()).joinToString(" | ").take(400))
                    .put("pkpd", (rt.consoleLog ?: emptyList<String>()).filter { it.contains("PKPD") || it.contains("DIA (learned)") || it.contains("ISF") || it.contains("asal") }.joinToString(" | ").take(900))
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
            if (i % 200 == 199) {
                // mockk keeps every call it saw; over thousands of ticks that fills the heap. The answers stay.
                clearAllMocks(answers = false, recordedCalls = true, childMocks = false)
                // What is done so far is kept even if a later tick stops the run.
                write(outPath!!, out)
            }
        }
        write(outPath!!, out)
        AimiReplayClock.override = null
        // Counts only: no values of the input reach the test log.
        println("AIMI replay: ${ticks.length()} ticks, $failed failed")
    }

    private fun write(path: String, out: JSONArray) =
        GZIPOutputStream(File(path).outputStream()).bufferedWriter().use { it.write(out.toString()) }

    private class History(val bg: List<GV>, val boluses: MutableList<BS>, val carbs: List<CA>, val temps: MutableList<TB>)

    /** A temp basal AIMI set in the replay; [end] moves earlier when the next one replaces it. */
    private class OwnTemp(val start: Long, val rate: Double, var end: Long)

    /**
     * The insulin of the replay: the human boluses as they were, and from [startMs] on AIMI's own SMBs
     * and temp basals instead of AutoISF's. Before [startMs] everything is as it really was, so the
     * first ticks start from the real insulin on board.
     */
    private inner class Chain(
        val startMs: Long,
        val manual: List<Pair<Long, Double>>,
        val realBefore: List<Pair<Long, Double>>,
        val realTempsBefore: List<OwnTemp>,
        val basalAt: (Long) -> Double,
    ) {
        val ownSmb = mutableListOf<Pair<Long, Double>>()
        val ownTemps = mutableListOf<OwnTemp>()

        fun activeTemp(now: Long): OwnTemp? = ownTemps.lastOrNull { it.start <= now && now < it.end }

        /** Every dose that can act at [now], temp basals as net insulin in 5-minute pieces up to [until]. */
        fun doses(now: Long, diaMs: Long, until: Long): List<Pair<Long, Double>> {
            val from = now - diaMs
            val out = ArrayList<Pair<Long, Double>>()
            manual.filterTo(out) { it.first in from..now }
            realBefore.filterTo(out) { it.first in from..now }
            ownSmb.filterTo(out) { it.first in from..now }
            for (tb in realTempsBefore + ownTemps) {
                val end = minOf(tb.end, until)
                var p = maxOf(tb.start, from)
                while (p < end) {
                    val q = minOf(p + 5 * minute, end)
                    val net = (tb.rate - basalAt(p)) * (q - p) / 3_600_000.0
                    if (net != 0.0) out.add(Pair((p + q) / 2, net))
                    p = q
                }
            }
            return out
        }
    }

    /** oref exponential insulin curve: (insulin on board, activity per minute) for one unit after [m] minutes. */
    private fun curve(peak: Double, diaHours: Double): (Double) -> Pair<Double, Double> {
        val td = diaHours * 60.0
        val tau = peak * (1 - peak / td) / (1 - 2 * peak / td)
        val a = 2 * tau / td
        val sFactor = 1 / (1 - a + (1 + a) * Math.exp(-td / tau))
        return { m ->
            when {
                m < 0 -> Pair(0.0, 0.0)
                m >= td -> Pair(0.0, 0.0)
                else -> Pair(
                    1 - sFactor * (1 - a) * ((m * m / (tau * td * (1 - a)) - m / tau - 1) * Math.exp(-m / tau) + 1),
                    (sFactor / (tau * tau)) * m * (1 - m / td) * Math.exp(-m / tau)
                )
            }
        }
    }

    private fun readHistory(data: JSONObject): History {
        val icfg = ICfg(insulinLabel = "replay", peak = 45, dia = 5.0, concentration = 1.0)
        val bg = data.getJSONArray("bg").rows().map {
            GV(timestamp = it.getLong(0), raw = null, value = it.getDouble(1), trendArrow = TrendArrow.NONE, noise = null, sourceSensor = SourceSensor.UNKNOWN)
        }
        val boluses = data.getJSONArray("boluses").rows().mapTo(mutableListOf()) {
            BS(timestamp = it.getLong(0), amount = it.getDouble(1), type = if (it.getBoolean(2)) BS.Type.SMB else BS.Type.NORMAL, iCfg = icfg)
        }
        val carbs = data.getJSONArray("carbs").rows().map { CA(timestamp = it.getLong(0), duration = 0L, amount = it.getDouble(1)) }
        val temps = data.getJSONArray("temps").rows().mapTo(mutableListOf()) {
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
        every { get(BooleanKey.OApsAIMIPkpdEnabled) } returns true
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

    private class Chained(val iobArray: Array<IobTotal>, val cur: Double, val fut: Double, val lag: Double, val hist: Double, val temp: CurrentTemp)

    private fun chainedInputs(chain: Chain, tick: JSONObject, now: Long): Chained {
        val prof = tick.getJSONObject("prof")
        val peak = prof.getDouble("peak")
        val diaH = prof.getDouble("dia")
        val f = curve(peak, diaH)
        val diaMs = (diaH * 3_600_000).toLong()
        val active = chain.activeTemp(now)
        val doses = chain.doses(now, diaMs, until = maxOf(now, active?.end ?: now))
        val lastBolus = (chain.manual + chain.realBefore + chain.ownSmb).filter { it.first <= now }.maxOfOrNull { it.first } ?: 0L
        fun at(whenMs: Long): Triple<Double, Double, Double> {
            var iob = 0.0; var act = 0.0
            for ((t, u) in doses) {
                if (t > whenMs) continue
                val (i, a) = f((whenMs - t) / 60_000.0)
                iob += i * u; act += a * u
            }
            return Triple(iob, act, 0.0)
        }
        val iobArray = Array(48) { k ->
            val (i, a, _) = at(now + k * 5 * minute)
            IobTotal(time = now + k * 5 * minute, iob = i, activity = a, lastBolusTime = lastBolus)
        }
        fun sum(offsets: List<Int>) = offsets.sumOf { at(now + it * minute).second }
        val pk = maxOf(peak.toInt(), 35)
        val temp = if (active != null)
            CurrentTemp(duration = ((active.end - now) / minute).toInt(), rate = active.rate, minutesrunning = ((now - active.start) / minute).toInt())
        else CurrentTemp(duration = 0, rate = prof.getDouble("basal"), minutesrunning = 0)
        return Chained(
            iobArray = iobArray,
            cur = sum((4 downTo 0).toList()),
            fut = sum((-4..0).map { pk - it }),
            lag = sum((-4..0).map { -10 - it }),
            hist = sum((-2..2).map { -20 - it }),
            temp = temp,
        )
    }

    private fun runTick(harness: DetermineBasalAimiScenarioTestHarness, tick: JSONObject, meta: JSONObject, now: Long, chained: Chained) =
        run {
            val gs = tick.getJSONObject("gs")
            val prof = tick.getJSONObject("prof")
            val iobArray = chained.iobArray
            val lastBolus = iobArray[0].lastBolusTime
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
                futureActivity = chained.fut,
                sensorLagActivity = chained.lag,
                historicActivity = chained.hist,
                currentActivity = chained.cur,
            )
            harness.engine.determine_basal(
                glucose_status = GlucoseStatusAIMI(
                    glucose = gs.getDouble("g"), noise = 0.0, delta = gs.getDouble("d"),
                    shortAvgDelta = gs.getDouble("sad"), longAvgDelta = gs.getDouble("lad"), date = gs.getLong("t")
                ),
                currenttemp = chained.temp,
                iob_data_array = iobArray,
                pkpd_iob_data_array = iobArray,
                effective_dia_hours = prof.getDouble("dia"),
                effective_peak_minutes = prof.getDouble("peak"),
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
