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
import androidx.collection.LongSparseArray
import app.aaps.core.data.aps.AverageTDD
import app.aaps.core.data.model.BS
import app.aaps.core.data.model.CA
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.ICfg
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TDD
import app.aaps.core.data.model.TB
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.interfaces.aps.APSResult
import app.aaps.core.interfaces.aps.AutosensResult
import app.aaps.core.interfaces.aps.CurrentTemp
import app.aaps.core.interfaces.aps.GlucoseStatusAIMI
import app.aaps.core.interfaces.aps.GlucoseStatusSMB
import app.aaps.core.interfaces.aps.IobTotal
import app.aaps.core.interfaces.aps.MealData
import app.aaps.core.interfaces.aps.OapsProfileAimi
import app.aaps.core.interfaces.aps.OapsProfileAutoIsf
import app.aaps.core.interfaces.aps.RT
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.stats.TIR
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
import app.aaps.plugins.aps.openAPSAutoISF.DetermineBasalAutoISF
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
import java.util.Random
import app.aaps.core.data.iob.InMemoryGlucoseValue
import app.aaps.plugins.smoothing.UnscentedKalmanFilterPlugin
import kotlinx.coroutines.runBlocking
import kotlin.math.roundToInt
import kotlin.math.roundToLong
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
        val learn = System.getenv("AIMI_REPLAY_LEARN") == "1"
        // Two passes: the first one only teaches the learners, the second one (16 days later, same
        // time of day) starts with what they learned and is the one written out.
        val passes = System.getenv("AIMI_REPLAY_PASSES")?.toIntOrNull()?.coerceIn(1, 2) ?: 1
        // Learner files live in a fresh folder of this run, kept between the passes.
        val storageDir = Files.createTempDirectory("aimi-replay").toFile()
        val prefsAll = System.getenv("AIMI_REPLAY_PREFS").orEmpty()
        if (prefsAll.contains("SYNTH")) {
            val data = JSONObject(GZIPInputStream(File(inPath!!).inputStream()).bufferedReader().readText())
            synthetic(data, outPath!!, prefsAll)
            println("AIMI replay: pass 1 of 1 done")
            return
        }
        for (pass in 0 until passes) {
            val data = JSONObject(GZIPInputStream(File(inPath!!).inputStream()).bufferedReader().readText())
            if (pass > 0) shiftTimes(data, pass * 16 * 86_400_000L)
            replayOnce(data, storageDir, outPath!!, learn)
            println("AIMI replay: pass ${pass + 1} of $passes done")
        }
    }

    /** Moves every time in the input by [by] ms. */
    private fun shiftTimes(data: JSONObject, by: Long) {
        for (name in listOf("bg", "boluses", "carbs", "temps")) {
            val a = data.getJSONArray(name)
            for (k in 0 until a.length()) a.getJSONArray(k).let { it.put(0, it.getLong(0) + by) }
        }
        val ticks = data.getJSONArray("ticks")
        for (k in 0 until ticks.length()) {
            val t = ticks.getJSONObject(k)
            t.put("t", t.getLong("t") + by)
            t.getJSONObject("gs").let { it.put("t", it.getLong("t") + by) }
            if (t.getLong("lastBolus") > 0) t.put("lastBolus", t.getLong("lastBolus") + by)
            if (t.getLong("lastCarb") > 0) t.put("lastCarb", t.getLong("lastCarb") + by)
        }
    }

    private fun replayOnce(data: JSONObject, storageDir: File, outPath: String, learn: Boolean) {
        val meta = data.getJSONObject("meta")
        val history = readHistory(data)
        val prefsText = System.getenv("AIMI_REPLAY_PREFS").orEmpty()
        // LOOP_MIN_<n>: the loop runs every n minutes (AAPS: 5). Extra cycles copy the inputs of the cycle before.
        val loopMin = Regex("LOOP_MIN_([0-9]+)").find(prefsText)?.groupValues?.get(1)?.toLong()
        // AISF_LIVE: AutoISF 3.2.1 works out its ISF factor from the model glucose (else: the factor AAPS logged).
        val aisfLive = prefsText.contains("AISF_LIVE")
        val ticks = loopMin?.let { expandTicks(data.getJSONArray("ticks"), it) } ?: data.getJSONArray("ticks")

        var now = ticks.getJSONObject(0).getLong("t")
        // Every AIMI timer and learner window follows the replayed time.
        AimiReplayClock.override = { now }
        val prefs = preferences(meta)
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
        // Eva's own totals, read at each tick: the harness default is an adult 48 U/day.
        var tdd24 = 0.0
        var tdd7 = 0.0
        var below24 = 0.0
        harness.engine.tddCalculator = mockk(relaxed = true) {
            coEvery { calculate(any<Long>(), any<Boolean>()) } answers {
                LongSparseArray<TDD>().apply { put(now, TDD(timestamp = now, totalAmount = if (firstArg<Long>() <= 1L) tdd24 else tdd7)) }
            }
            coEvery { calculate(any<Long>(), any<Long>(), any<Boolean>()) } answers {
                LongSparseArray<TDD>().apply { put(now, TDD(timestamp = now, totalAmount = if (secondArg<Long>() <= 1L) tdd24 else tdd7)) }
            }
            coEvery { calculateDaily(any(), any()) } answers { TDD(timestamp = now, totalAmount = tdd24) }
            every { averageTDD(any()) } answers {
                val v = firstArg<LongSparseArray<TDD>?>()?.let { if (it.size() > 0) it.valueAt(0).totalAmount else null } ?: tdd24
                AverageTDD(TDD(timestamp = now, totalAmount = v), true)
            }
        }
        harness.engine.tirCalculator = mockk(relaxed = true) {
            every { averageTIR(any()) } answers { mockk<TIR>(relaxed = true) { every { belowPct() } returns below24 } }
        }
        // The recent glucose the safety checks read, newest first as the AAPS bucketed table is.
        harness.engine.glucoseStatusCalculatorAimi = mockk(relaxed = true) {
            every { getRecentGlucose() } answers {
                history.bg.filter { it.timestamp in (now - 3 * 3_600_000L)..now }.sortedByDescending { it.timestamp }.map { it.value.toFloat() }
            }
            every { getBucketedGlucoseSinceMinutes(any()) } answers {
                val back = firstArg<Int>() * 60_000L
                history.bg.filter { it.timestamp in (now - back)..now }.sortedByDescending { it.timestamp }.map { TimestampedBgSample(it.timestamp, it.value) }
            }
        }
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
        // Scenario "food first": a meal bolus given at glucose below 4.5 mmol/L is given LATE_BOLUS_<min> later.
        val lateBolusMin = Regex("LATE_BOLUS_([0-9]+)").find(System.getenv("AIMI_REPLAY_PREFS").orEmpty())?.groupValues?.get(1)?.toLong() ?: 0L
        val bgTimes = history.bg.map { it.timestamp }.toLongArray()
        fun bgBefore(t: Long): Double = history.bg[(bgTimes.binarySearch(t).let { if (it >= 0) it else -it - 2 }).coerceIn(0, bgTimes.size - 1)].value
        val movedBoluses = if (System.getenv("AIMI_REPLAY_SIM") == "1" && lateBolusMin > 0)
            bolusRows.filter { !it.getBoolean(2) && it.getLong(0) >= startMs && bgBefore(it.getLong(0)) < 81.0 }
                .map { Triple(it.getLong(0), it.getLong(0) + lateBolusMin * minute, it.getDouble(1)) }
        else emptyList()
        fun movedTime(t: Long): Long = movedBoluses.firstOrNull { it.first == t }?.second ?: t
        history.boluses.replaceAll { b -> if (b.type != BS.Type.SMB && movedBoluses.any { it.first == b.timestamp }) b.copy(timestamp = movedTime(b.timestamp)) else b }
        history.boluses.sortBy { it.timestamp }
        val chain = Chain(
            startMs = startMs,
            manual = bolusRows.filter { !it.getBoolean(2) }.map { Pair(movedTime(it.getLong(0)), it.getDouble(1)) }.toMutableList(),
            realBefore = bolusRows.filter { it.getBoolean(2) && it.getLong(0) < startMs }.map { Pair(it.getLong(0), it.getDouble(1)) },
            realTempsBefore = data.getJSONArray("temps").rows().filter { it.getLong(0) < startMs }
                .map { OwnTemp(it.getLong(0), it.getDouble(1), minOf(it.getLong(0) + it.getLong(2), startMs)) },
            basalAt = basalAt,
        )
        // The database AIMI reads holds the same: human boluses, AutoISF's doses before the start, AIMI's after.
        history.boluses.removeAll { it.type == BS.Type.SMB && it.timestamp >= startMs }
        history.temps.removeAll { it.timestamp >= startMs }

        // Closed loop on a simple body model: glucose answers the insulin the algorithm gives. See [Body].
        val sim = System.getenv("AIMI_REPLAY_SIM") == "1"
        val algo = System.getenv("AIMI_REPLAY_ALGO").orEmpty().ifBlank { "aimi" }
        require(algo in setOf("aimi", "autoisf", "real")) { "unknown algorithm $algo" }
        require(sim || algo == "aimi") { "autoisf and real need the body model" }
        val prof0 = ticks.getJSONObject(0).getJSONObject("prof")
        val bodyIsf = System.getenv("AIMI_REPLAY_SIM_ISF")?.toDoubleOrNull() ?: prof0.getDouble("isf")
        val bgT = LongArray(history.bg.size) { history.bg[it].timestamp }
        val bgReal = DoubleArray(history.bg.size) { history.bg[it].value }
        fun realBgAt(t: Long): Double = bgReal[(bgT.binarySearch(t).let { if (it >= 0) it else -it - 2 }).coerceIn(0, bgT.size - 1)]
        // Hypo treatments really given (small carbs at low glucose) leave the history; the model gives its own.
        val realTreats = history.carbs.filter { it.timestamp >= startMs && it.amount <= 12.0 && realBgAt(it.timestamp) < 90.0 }
            .map { Pair(it.timestamp, it.amount) }
        val body = Body(bodyIsf, bodyIsf / prof0.getDouble("ic"), realTreats)
        movedBoluses.forEach { (from, to, u) -> body.addInsulin(from, -u); body.addInsulin(to, u) }
        // Learner "smart calculator" (SMART): a meal bolus (13 g or more carbs within 20 min) at model glucose below
        // 4.5 mmol/L waits. Checks at +10 min, +15 min, then every reading: given when glucose is at least 5.5 and
        // rising, or 1.5 mmol/L above the start and not falling; never below 4.5 or when falling; dropped after 45 min.
        // The dose is cut by the SMB given while waiting. SMART_ADJ: -20 % on a weak rise, +10 % on a sharp one.
        val smart = sim && prefsText.contains("SMART")
        // NOIOB: the bolus wizard adds negative basal IOB (insulin "owed" after a zero temp) to a meal bolus. With
        // NOIOB it does not when glucose is below 4.5 mmol/L (NOIOB55: below 5.5): the bolus is cut by that amount.
        val noIob = sim && prefsText.contains("NOIOB")
        val noIobBg = if (prefsText.contains("NOIOB55")) 99.0 else 81.0
        val iobLeft = curve(35.0, 5.0)
        fun basalIobAt(ts: Long): Double {
            var sum = 0.0
            for (tb in chain.realTempsBefore + chain.ownTemps) {
                var p0 = maxOf(tb.start, ts - 5 * 3_600_000L); val end = minOf(tb.end, ts)
                while (p0 < end) {
                    val q = minOf(p0 + 5 * minute, end)
                    sum += (tb.rate - basalAt(p0)) * (q - p0) / 3_600_000.0 * iobLeft((ts - (p0 + q) / 2) / 60_000.0).first
                    p0 = q
                }
            }
            return sum
        }
        var noIobCut = 0; var noIobUnits = 0.0
        val smartAdj = prefsText.contains("SMART_ADJ")
        class Pending(val t0: Long, val u: Double, val g0: Double, var next: Long)
        val pending = ArrayList<Pending>()
        val mealBoluses = if (smart || noIob) chain.manual.filter { b -> b.first >= startMs &&
            history.carbs.any { it.amount > 12.0 && it.timestamp - b.first in -20 * minute..20 * minute } }.toMutableList() else ArrayList()
        var smartDeferred = 0; var smartGiven = 0; var smartDropped = 0; var smartWaitMin = 0L; var smartUnitsCut = 0.0
        var lastReadingTs = startMs
        // Scenario NEED_<pct>: the body needs pct % more basal insulin (lower sensitivity). It acts like
        // insulin that is used up by the body, so glucose drifts up unless the algorithm gives more.
        // prefsText is read at the top of replayOnce
        val needPct = Regex("NEED_([0-9]+)").find(prefsText)?.groupValues?.get(1)?.toDouble() ?: 0.0
        // Scenario CARBS_<pct>: every meal (> 12 g) is pct % bigger; the parent enters it and gives the
        // meal bolus for it with the profile carb ratio, as with the real meals.
        val carbsPct = Regex("CARBS_([0-9]+)").find(prefsText)?.groupValues?.get(1)?.toDouble() ?: 0.0
        if (sim && carbsPct > 0) {
            val ic = prof0.getDouble("ic")
            history.carbs.filter { it.timestamp >= startMs && it.amount > 12.0 }.forEach { c ->
                val extra = c.amount * carbsPct / 100.0
                body.extraMeals.add(Pair(c.timestamp, extra))
                body.addInsulin(c.timestamp, extra / ic)
                chain.extraManual.add(Pair(c.timestamp, extra / ic))
                history.boluses.add(BS(timestamp = c.timestamp, amount = extra / ic, type = BS.Type.NORMAL, iCfg = ICfg(insulinLabel = "replay", peak = 45, dia = 5.0, concentration = 1.0)))
            }
            history.boluses.sortBy { it.timestamp }
            history.carbs.replaceAll { c -> if (c.timestamp >= startMs && c.amount > 12.0) c.copy(amount = c.amount * (1 + carbsPct / 100.0)) else c }
            println("AIMI replay: ${body.extraMeals.size} meals made bigger")
        }
        val cobScale = 1.0 + carbsPct / 100.0
        if (movedBoluses.isNotEmpty()) println("AIMI replay: ${movedBoluses.size} meal boluses moved later")
        if (sim) history.carbs.removeAll { c -> realTreats.any { it.first == c.timestamp } }
        val realSmbAfter = bolusRows.filter { it.getBoolean(2) && it.getLong(0) >= startMs }.map { Pair(it.getLong(0), it.getDouble(1)) }
        val realTemps = data.getJSONArray("temps").rows().map { Triple(it.getLong(0), it.getDouble(1), it.getLong(2)) }.sortedBy { it.first }
        fun realRateAt(p: Long): Double? = realTemps.lastOrNull { it.first <= p && p < it.first + it.third }?.second
        var bgIdx = bgT.indexOfFirst { it > startMs }.let { if (it < 0) bgT.size else it }
        var smbState = ticks.getJSONObject(0).optBoolean("smbOk", true)
        // Phone automations. Hypo protection: below 4.2 mmol/L SMB off for 90 min (a temp target that ends by
        // itself). "Защита 2": SMB back on above Z2_<mg/dL> (default 117 = 6.5) when rising.
        val z2On = Regex("Z2_([0-9]+)").find(prefsText)?.groupValues?.get(1)?.toDouble() ?: 117.0
        var smbOffAt = Long.MIN_VALUE / 2
        // Learner "rebound guard": after a hypo treatment, SMB is held for GUARD_<min> unless food shows up
        // (a meal over 12 g logged, or glucose over 9 mmol/L and rising fast). It can only hold insulin.
        val guardMin = Regex("GUARD_([0-9]+)").find(prefsText)?.groupValues?.get(1)?.toLong() ?: 0L
        var guardTicks = 0
        var prevNow = startMs
        var traceLeft = if (System.getenv("AIMI_REPLAY_TRACE") == "1") 25 else 0
        val autoIsf = DetermineBasalAutoISF(mockk(relaxed = true))

        val out = JSONArray()
        var failed = 0
        var lastGuardRefresh = 0L
        var guardHold = false
        for (i in 0 until ticks.length()) {
            val tick = ticks.getJSONObject(i)
            now = tick.getLong("t")
            if (cobScale != 1.0) {
                tick.put("cob", tick.getDouble("cob") * cobScale)
                tick.put("carbs3h", tick.getDouble("carbs3h") * cobScale)
            }
            tdd24 = tick.optDouble("tdd24", meta.getDouble("tdd"))
            tdd7 = tick.optDouble("tdd7", meta.getDouble("tdd"))
            below24 = tick.optDouble("below24", 0.0)
            var gsSim: GlucoseStatusAIMI? = null
            if (sim) {
                // Insulin between the last tick and this one: what the algorithm set, against what really ran.
                var p = prevNow
                while (p < now) {
                    val q = minOf(p + 5 * minute, now)
                    val b = basalAt(p)
                    val real = realRateAt(p) ?: b
                    val mine = if (algo == "real") real else chain.activeTemp(p)?.rate ?: b
                    val net = (mine - real) * (q - p) / 3_600_000.0
                    if (net != 0.0) body.addInsulin((p + q) / 2, net)
                    if (needPct > 0) body.addNeed((p + q) / 2, b * needPct / 100.0 * (q - p) / 3_600_000.0)
                    p = q
                }
                if (algo != "real") realSmbAfter.filter { it.first in prevNow until now }.forEach { body.addInsulin(it.first, -it.second) }
                // Glucose up to now, from the model; the family treats a low as they do.
                while (bgIdx < bgT.size && bgT[bgIdx] <= now) {
                    val ts = bgT[bgIdx]
                    val v = body.bgAt(ts, bgReal[bgIdx])
                    history.bg[bgIdx].value = v
                    if (v < 70.0 && (body.treats.lastOrNull()?.let { ts - it.first >= 20 * minute } != false)) {
                        body.treats.add(Pair(ts, 6.0))
                        history.carbs.add(CA(timestamp = ts, duration = 0L, amount = 6.0))
                    }
                    if (smart || noIob) {
                        // A meal bolus whose time has come: wait if glucose is low.
                        val due = mealBoluses.filter { it.first in (lastReadingTs + 1)..ts }
                        for (b0 in due) {
                            mealBoluses.remove(b0)
                            var b = b0
                            if (noIob && v < noIobBg) {
                                val cut = minOf(b.second, -minOf(0.0, basalIobAt(ts)))
                                if (cut > 0.0) {
                                    chain.manual.remove(b); b = Pair(b.first, b.second - cut); chain.manual.add(b)
                                    body.addInsulin(b.first, -cut)
                                    history.boluses.replaceAll { x -> if (x.type != BS.Type.SMB && x.timestamp == b.first) x.copy(amount = b.second) else x }
                                    noIobCut++; noIobUnits += cut
                                }
                            }
                            if (!smart || v >= 81.0) continue
                            chain.manual.remove(b); body.addInsulin(b.first, -b.second)
                            history.boluses.removeAll { it.type != BS.Type.SMB && it.timestamp == b.first }
                            pending.add(Pending(b.first, b.second, v, b.first + 10 * minute)); smartDeferred++
                        }
                        val it5 = (bgT.binarySearch(ts - 5 * minute).let { k -> if (k >= 0) k else -k - 2 }).coerceAtLeast(0)
                        val d5 = v - history.bg[it5].value
                        val done = ArrayList<Pending>()
                        for (pd in pending) {
                            if (ts < pd.next) continue
                            val fireAt = if (prefsText.contains("SMART6")) 108.0 else 99.0
                            val safe = v >= 81.0 && d5 >= 0.0 && ((v >= fireAt && d5 > 0.0) || v >= pd.g0 + 27.0)
                            if (safe) {
                                val lowCut = if (prefsText.contains("SMART_LOW20")) 0.8 else 1.0
                                val factor = lowCut * (if (!smartAdj) 1.0 else if (d5 < 2.0) 0.8 else if (d5 > 6.0) 1.1 else 1.0)
                                val smbSince = chain.ownSmb.filter { it.first in pd.t0..ts }.sumOf { it.second }
                                val dose = (pd.u * factor - smbSince).coerceAtLeast(0.0)
                                smartUnitsCut += pd.u - dose
                                if (dose > 0.0) {
                                    chain.manual.add(Pair(ts, dose)); body.addInsulin(ts, dose)
                                    history.boluses.add(BS(timestamp = ts, amount = dose, type = BS.Type.NORMAL, iCfg = ICfg(insulinLabel = "replay", peak = 45, dia = 5.0, concentration = 1.0)))
                                }
                                smartGiven++; smartWaitMin += (ts - pd.t0) / minute; done.add(pd)
                            } else if (ts - pd.t0 >= 45 * minute) {
                                smartDropped++; smartUnitsCut += pd.u; done.add(pd)
                            } else {
                                pd.next = if (pd.next == pd.t0 + 10 * minute) pd.t0 + 15 * minute else ts + minute
                            }
                        }
                        pending.removeAll(done)
                    }
                    lastReadingTs = ts
                    bgIdx++
                }
                gsSim = glucoseStatus(history.bg, bgT, now)
                // The automations of the phone: SMB off below 4.2 mmol/L, back on above 6.5 when rising.
                gsSim?.let { gs ->
                    if (gs.glucose < 75.6) { smbState = false; smbOffAt = now }
                    else if (gs.glucose > z2On && gs.shortAvgDelta > 1.8) smbState = true
                    else if (!smbState && now - smbOffAt >= 90 * minute) smbState = true
                }
                guardHold = false
                if (guardMin > 0) {
                    val lt = body.treats.lastOrNull()?.first
                    val gs = gsSim
                    if (lt != null && gs != null && now - lt < guardMin * minute) {
                        val meal = history.carbs.any { it.amount > 12.0 && it.timestamp in (lt - 30 * minute)..now }
                        val strongRise = gs.glucose > 162.0 && gs.shortAvgDelta > 3.6
                        if (!meal && !strongRise) { guardHold = true; if (smbState) guardTicks++ }
                    }
                }
                val d24 = body.insulinBetween(now - 86_400_000L, now)
                val d7 = body.insulinBetween(now - 7 * 86_400_000L, now) / 7.0
                tdd24 += d24
                tdd7 += d7
                val day = history.bg.subList(bgT.binarySearch(now - 86_400_000L).let { if (it >= 0) it else -it - 1 }.coerceAtMost(bgIdx), bgIdx)
                below24 = if (day.isEmpty()) 0.0 else 100.0 * day.count { it.value < 70.0 } / day.size
            }
            prevNow = now
            if (now - lastGuardRefresh > 60 * minute) {
                runBlocking { harness.engine.refreshHypoExposure() }
                lastGuardRefresh = now
            }
            val row = JSONObject().put("t", now)
            try {
                val chained = chainedInputs(chain, tick, now)
                val smbOk = if (sim) smbState && !guardHold else tick.optBoolean("smbOk", true)
                val rt = when (algo) {
                    "aimi" -> runTick(harness, tick, meta, now, chained, gsSim, smbOk)
                    "autoisf" -> runTickAutoIsf(
                        autoIsf, tick, meta, now, chained, gsSim!!, smbOk,
                        liveVs = if (aisfLive) autoIsf321Sens(history.bg, bgT, now, tick) else null,
                    )
                    else -> realTick(realSmbAfter, prevNowOf(ticks, i), now, realRateAt(now), basalAt(now))
                }
                if (sim && algo != "real") (rt.units ?: 0.0).takeIf { it > 0.0 }?.let { body.addInsulin(now, it) }
                if (sim) row.put("bg", gsSim?.glucose ?: JSONObject.NULL).put("smbOk", smbOk).put("guard", guardHold).put("ntreat", body.treats.size)
                val smb = rt.units ?: 0.0
                if (smb > 0.0) {
                    chain.ownSmb.add(Pair(now, smb))
                    history.boluses.add(BS(timestamp = now, amount = smb, type = BS.Type.SMB, iCfg = ICfg(insulinLabel = "replay", peak = 45, dia = 5.0, concentration = 1.0)))
                }
                // TEMPCAP (test only): while the rebound guard holds, a temp basal above the profile basal is cut to it.
                val profBasal = tick.getJSONObject("prof").getDouble("basal")
                val rate = if (guardHold && prefsText.contains("TEMPCAP") && (rt.rate ?: 0.0) > profBasal) profBasal else rt.rate
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
                // Full AIMI log for some ticks with a high temp at a normal glucose: to see which part set it.
                val basalNow = tick.getJSONObject("prof").getDouble("basal")
                if (traceLeft > 0 && algo == "aimi" && (rt.rate ?: 0.0) > basalNow * 1.5 && (gsSim?.glucose ?: 999.0) < 125.0 && i % 7 == 0) {
                    traceLeft--
                    row.put("trace", (rt.consoleLog ?: emptyList<String>()).joinToString("\n").take(20_000))
                }
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
                write(outPath, out)
            }
        }
        write(outPath, out)
        AimiReplayClock.override = null
        // Counts only: no values of the input reach the test log.
        if (sim) println("AIMI replay: body model, algorithm $algo, ${body.treats.size} model hypo treatments, guard held SMB on $guardTicks ticks")
        if (noIob) println("AIMI replay: NOIOB cut $noIobCut meal boluses by ${"%.2f".format(noIobUnits)} U in total")
        if (smart) println("AIMI replay: smart calculator deferred $smartDeferred meal boluses, given $smartGiven (mean wait ${if (smartGiven > 0) smartWaitMin / smartGiven else 0} min), dropped $smartDropped, units cut ${"%.2f".format(smartUnitsCut)}")
        println("AIMI replay: ${ticks.length()} ticks, $failed failed")
    }

    private fun write(path: String, out: JSONArray) =
        GZIPOutputStream(File(path).outputStream()).bufferedWriter().use { it.write(out.toString()) }

    private class History(val bg: MutableList<GV>, val boluses: MutableList<BS>, val carbs: MutableList<CA>, val temps: MutableList<TB>)

    /** A temp basal AIMI set in the replay; [end] moves earlier when the next one replaces it. */
    private class OwnTemp(val start: Long, val rate: Double, var end: Long)

    /**
     * The insulin of the replay: the human boluses as they were, and from [startMs] on AIMI's own SMBs
     * and temp basals instead of AutoISF's. Before [startMs] everything is as it really was, so the
     * first ticks start from the real insulin on board.
     */
    private inner class Chain(
        val startMs: Long,
        val manual: MutableList<Pair<Long, Double>>,
        val realBefore: List<Pair<Long, Double>>,
        val realTempsBefore: List<OwnTemp>,
        val basalAt: (Long) -> Double,
    ) {
        val ownSmb = mutableListOf<Pair<Long, Double>>()
        /** Extra meal boluses of the CARBS scenario. */
        val extraManual = mutableListOf<Pair<Long, Double>>()
        val ownTemps = mutableListOf<OwnTemp>()

        fun activeTemp(now: Long): OwnTemp? = ownTemps.lastOrNull { it.start <= now && now < it.end }

        /** Every dose that can act at [now], temp basals as net insulin in 5-minute pieces up to [until]. */
        fun doses(now: Long, diaMs: Long, until: Long): List<Pair<Long, Double>> {
            val from = now - diaMs
            val out = ArrayList<Pair<Long, Double>>()
            manual.filterTo(out) { it.first in from..now }
            extraManual.filterTo(out) { it.first in from..now }
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

    /**
     * Virtual Eva 2.0 (prefs SYNTH_<days>): a made-up but Eva-like life in closed loop with the real AutoISF.
     *
     * Body, per minute: glucose change = liver output (true basal need x true ISF) - insulin acting x true ISF x day factor
     * + carbs absorbed x true CSF + the liver's answer to a low (KCR) + slow noise (NOISE). The phone profile (what AAPS
     * believes) and the true body values come from `synth` in the encrypted input, never from this file.
     * Sensor: 5 min lag, noise, reads low at the bottom, rare one-minute spikes.
     * Family: meals on Eva's daily pattern with a bolus from the wizard (it adds negative basal IOB back, as the real
     * wizard does; NOIOB stops that at low glucose), 6 g for a low (sensor below 3.9, again after 20 min), and sometimes
     * a manual correction on a rebound (CORR_<percent>, default 30). Phone automations and the learner switches
     * (Z2_, GUARD_, TEMPCAP, NOIOB) work as in the replay. Other tokens: SEED_<n>, KCR_<x>, NOISE_<x>, DAYSD_<x>.
     */
    private fun synthetic(data: JSONObject, outPath: String, prefsText: String) {
        fun tok(name: String, def: Double) = Regex("${name}_([0-9.]+)").find(prefsText)?.groupValues?.get(1)?.toDouble() ?: def
        val cfg = data.getJSONObject("synth")
        // The phone's max basal (the replay input assumes 4.0; Eva's phone has its own value in synth).
        val meta = JSONObject(data.getJSONObject("meta").toString()).put("maxBasal", cfg.optDouble("maxBasal", data.getJSONObject("meta").getDouble("maxBasal")))
        val days = tok("SYNTH", 28.0).toInt()
        val rnd = Random(tok("SEED", 1.0).toLong())
        val kcr = tok("KCR", 0.03)
        val noiseSd = tok("NOISE", 0.25)
        val daySd = tok("DAYSD", 0.12)
        // NOISEPHI_<x>: how slowly the glucose noise changes (0.98 = minutes, 0.995 = hours); SBIAS_<x>: low-end sensor bias.
        val noisePhi = tok("NOISEPHI", 0.98)
        val sBias = tok("SBIAS", 0.3)
        val corrPct = tok("CORR", 30.0)
        val z2On = tok("Z2", 117.0)
        val guardMin = tok("GUARD", 0.0).toLong()
        val tempCap = prefsText.contains("TEMPCAP")
        val noIob = prefsText.contains("NOIOB")
        // UKF: the loop sees the AAPS Kalman (UKF) smoothed glucose on 5-minute data, as on Eva's phone.
        // The family (alarms, wizard) still sees the raw sensor value.
        val ukf = if (prefsText.contains("UKF")) UnscentedKalmanFilterPlugin(mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true)) else null
        val noIobBg = if (prefsText.contains("NOIOB55")) 99.0 else 81.0
        // Learner (LEARNG): learns the guard window (from 0 = no guard) and the temp basal cap on a rebound (from 2x profile)
        // from the outcome of each rebound. A new low after SMB or a high temp -> stricter (window +20 min, cap -0.2x);
        // three overshoots over 10 mmol/L in a row without a low -> a little looser (window -10 min, cap +0.05x).
        val learnG = prefsText.contains("LEARNG")
        // LEARNG2: the learner asks "what if": a low counts against the loop only if, without the extra insulin the loop
        // gave in the rebound window (SMB, temp basal above profile), the lowest value would have stayed at 75 or more;
        // an overshoot counts against the guard only if the insulin it held back would have kept the peak under 180.
        val learnG3 = prefsText.contains("LEARNG3")
        val learnG2 = prefsText.contains("LEARNG2") || learnG3
        // LEARNG3: the guard lets SMB go before the window ends when insulin is really needed: glucose went higher than the
        // treatment explains (glucose at the treatment + expected rebound + margin) and still rises, or it stays flat over 10
        // mmol/L for 15 min after the peak. Expected rebound is learned from rebounds without food; the margin grows after a
        // release that the what-if check blames for a low, and shrinks a little after an overshoot the guard caused.
        var expRise = 50.0; var relMargin = 30.0; var treatBg = 0.0; var releases = 0
        var lowsByLoop = 0; var lowsAnyway = 0; var overAvoidable = 0; var loosenScore = 0
        var guardW = if (learnG) 0.0 else guardMin.toDouble()
        // CAPX_<x>: a fixed temp basal cap on a rebound, x times the profile basal (with TEMPCAP), e.g. what a learner chose.
        var capMult = if (learnG) 2.0 else tok("CAPX", 1.0)
        val capOn = tempCap || learnG
        var overStreak = 0; var tighten = 0; var loosen = 0
        // Eva changes (CHG_<day>): from that day insulin acts CHGISF_<x> as strongly, the liver answers a low CHGKCR_<x> times.
        val chgDay = tok("CHG", 9999.0).toInt()
        val chgIsf = tok("CHGISF", 1.0); val chgKcr = tok("CHGKCR", 1.0)
        // PROF_<name>: run with another phone profile kept in synth.profiles.<name> of the encrypted input (basal, isf, ic).
        val profName = Regex("PROF_([A-Za-z0-9]+)").find(prefsText)?.groupValues?.get(1)
        val prof = profName?.let { cfg.getJSONObject("profiles").getJSONObject(it) }
        val profBasal = DoubleArray(24) { (prof ?: cfg).getJSONArray(if (prof != null) "basal" else "profBasal").getDouble(it) }
        // NEEDDX_<x>: the true basal need of the day hours (10-21 h) times x (the encrypted values are night-based).
        val needDx = tok("NEEDDX", 1.0)
        val needBasal = (0 until 24).map { cfg.getJSONArray("needBasal").getDouble(it) * (if (it in 10..21) needDx else 1.0) }
        val isfP = prof?.getDouble("isf") ?: cfg.getDouble("profIsf"); val icP = prof?.getDouble("ic") ?: cfg.getDouble("profIc")
        val targetP = prof?.optDouble("target", cfg.getDouble("profTarget")) ?: cfg.getDouble("profTarget")
        val isfT = cfg.getDouble("isfTrue"); val csfT = isfT / tok("CRT", cfg.getDouble("crTrue"))
        val peak = cfg.getDouble("peak"); val dia = cfg.getDouble("dia")
        val t0 = cfg.getLong("t0")
        val tz = ZoneOffset.ofHours(3)
        fun hourOf(t: Long) = Instant.ofEpochMilli(t).atZone(tz).hour
        val iobCurve = curve(peak, dia)
        // Share of a dose still to act after m minutes: all of it before the dose acts.
        fun iobLeft(m: Double): Pair<Double, Double> = if (m <= 0.0) Pair(1.0, 0.0) else iobCurve(m)

        // Meals of every day: slot (hour, minute sd, probability, grams, grams sd).
        class Meal(val t: Long, val logged: Double, val eaten: Double, val a: Double, val b: Double)
        val meals = ArrayList<Meal>()
        val slots = cfg.getJSONArray("meals")
        for (d in 0 until days) for (k in 0 until slots.length()) {
            val sl = slots.getJSONArray(k)
            if (rnd.nextDouble() > sl.getDouble(2)) continue
            val tm = t0 + d * 86_400_000L + (sl.getDouble(0) * 60 + rnd.nextGaussian() * sl.getDouble(1)).toLong().coerceAtLeast(0) * minute
            val g = (sl.getDouble(3) + rnd.nextGaussian() * sl.getDouble(4)).coerceIn(5.0, 60.0).roundToInt().toDouble()
            val eaten = if (rnd.nextDouble() < 0.15) g * (0.6 + 0.2 * rnd.nextDouble()) else g
            val fast = rnd.nextDouble() < 0.25
            meals.add(Meal(tm, g, eaten, if (fast) 60.0 else 150.0, if (fast) 150.0 else 270.0))
        }
        meals.sortBy { it.t }
        val dayFactor = DoubleArray(days + 1) { Math.exp(rnd.nextGaussian() * daySd) }

        fun triCdf(m: Double, a: Double, b: Double) = when {
            m <= 0 -> 0.0
            m <= a -> m * m / (a * b)
            m < b -> 1.0 - (b - m) * (b - m) / ((b - a) * b)
            else -> 1.0
        }
        // Body state.
        val doses = ArrayList<Pair<Long, Double>>()          // insulin really given (basal minutes, SMB, boluses)
        var donePtr = 0
        val eatenList = ArrayList<Meal>()                    // carbs really eaten (meals and treatments)
        var g = 110.0
        var z = 0.0; var e = 0.0; var sens = g
        // AAPS side.
        val chain = Chain(startMs = t0, manual = ArrayList(), realBefore = emptyList(), realTempsBefore = emptyList(),
            basalAt = { t -> profBasal[hourOf(t)] })
        class Logged(val t: Long, val g: Double, var left: Double)
        val logged = ArrayList<Logged>()
        val readT = ArrayList<Long>(); val readV = ArrayList<Double>()
        val autoIsf = DetermineBasalAutoISF(mockk(relaxed = true))
        var smbState = true; var smbOffAt = Long.MIN_VALUE / 2
        var lastTreat = Long.MIN_VALUE / 2; var nTreat = 0; var nCorr = 0; var nMeals = 0; var corrAsked = false
        val lateOn = prefsText.contains("LATE"); val lateCob = prefsText.contains("LATECOB"); val latePct = tok("LATEPCT", 50.0) / 100.0
        val mealLog = ArrayList<Pair<Long, Double>>(); val lateDone = HashSet<Long>(); var nLate = 0; var lateU = 0.0
        val canEvery = tok("CANCH", 0.0)
        val treatFix = tok("TREAT", 6.0); val treatL = prefsText.contains("TREATL"); var treatSum = 0.0
        val smartS = prefsText.contains("SMARTS")
        class Pending(val t: Long, val g: Double, val bg: Double)
        val splitP = tok("SPLIT", 100.0) / 100.0
        val splitMin = tok("SPLITMIN", 45.0).toLong()
        class Split(val t: Long, val u: Double, val smb0: Double, val maxMin: Long)
        val splitType = prefsText.contains("SPLITTYPE")
        val markRnd = Random(tok("SEED", 1.0).toLong() + 7)
        // SPLITABS: the rest waits for SEEN absorption - the AAPS deviation (rise beyond what insulin explains) above
        // 6 mg/dL per 5 min in two loop cycles in a row - instead of a glucose level.
        val splitAbs = prefsText.contains("SPLITABS")
        var lastDev = 0.0; var devStreak = 0
        val splits = ArrayList<Split>(); var nSplit = 0; var nSplitGiven = 0; var nSplitDrop = 0
        var pend: Pending? = null; var nDelayed = 0; var nFired = 0; var nCancel = 0; var delaySum = 0L
        var lastLoopBg: Double? = null; var lastAct = 0.0
        var mealPtr = 0
        val out = JSONArray()
        var failed = 0
        val end = t0 + days * 86_400_000L
        var t = t0
        var manThisTick = 0.0
        var mealThisTick = 0.0; var hypoThisTick = 0.0

        // Learner basal advisor (same method as advisor v3): clock hours with no meal 5 h before and no meal inside,
        // glucose 3.9-12 at the start; need = insulin acting in the hour + glucose change / ISF - hypo carbs / CR.
        // Median per hour from >= 3 hours = "measured"; step limited to +-20 % of the current value; other hours stay.
        val learnB = prefsText.contains("LEARNB")
        val lbEvery = tok("LBEVERY", 4.0).toInt()
        val lbDays = tok("LBDAYS", 14.0).toInt()
        val kern5 = DoubleArray(61) { j -> iobLeft(5.0 * j).first - iobLeft(5.0 * (j + 1)).first }
        var lbCards = 0
        // LEARNB2 = v2: an hour changes only when its need is >10 % away on two cards in a row (same side); the need of
        // an hour is smoothed with its neighbours; a SHIFT detector looks at the last 7 days: if the whole day needs
        // >10 % more or less on two cards in a row, all hours move together (up to +-15 %).
        val lb2 = prefsText.contains("LEARNB2")
        val lastDir = IntArray(24); var lastShift = 0
        fun hourNeeds(w: Int): Array<ArrayList<Double>> {
            val n = out.length(); val s0 = n - w
            val bg = DoubleArray(w); val ins = DoubleArray(w); val meal = DoubleArray(w); val hypo = DoubleArray(w)
            val hr = IntArray(w); val mn = IntArray(w)
            fun num(r: JSONObject, k: String) = r.optDouble(k, 0.0).let { if (it.isNaN()) 0.0 else it }
            for (i in 0 until w) {
                val r = out.getJSONObject(s0 + i)
                bg[i] = r.optDouble("bg", Double.NaN); meal[i] = num(r, "meal"); hypo[i] = num(r, "hypo")
                val z = Instant.ofEpochMilli(r.getLong("t")).atZone(tz); hr[i] = z.hour; mn[i] = z.minute
                val nx = if (i + 1 < w) out.getJSONObject(s0 + i + 1) else r
                ins[i] = num(nx, "deliv") * 5 / 60 + num(r, "units") + num(r, "man")
            }
            val act = DoubleArray(w) { k -> var a = 0.0; for (j in 0..minOf(60, k)) a += ins[k - j] * kern5[j]; a }
            val since = IntArray(w); var last = -100000
            for (k in 0 until w) { if (meal[k] > 0) last = k; since[k] = (k - last) * 5 }
            val needs = Array(24) { ArrayList<Double>() }
            for (k in 60 until w - 12) {
                if (mn[k] != 0 || since[k] < 300 || bg[k].isNaN() || bg[k + 12].isNaN() || bg[k] !in 70.0..216.0) continue
                if ((k until k + 12).any { meal[it] > 0 }) continue
                var a = 0.0; for (j in k until k + 12) a += act[j]
                var hy = 0.0; for (j in maxOf(0, k - 6) until k + 12) hy += hypo[j]
                needs[hr[k]].add(a + (bg[k + 12] - bg[k]) / isfP - hy / icP)
            }
            return needs
        }
        fun med(a: List<Double>) = a.sorted()[a.size / 2]
        fun learnBasal2(day: Int) {
            val needs = hourNeeds(minOf(out.length(), lbDays * 288))
            val sb = StringBuilder(); var changed = 0
            // shift detector on the last 7 days
            val n7 = hourNeeds(minOf(out.length(), 7 * 288))
            val ratios = (0 until 24).filter { n7[it].size >= 2 }.map { med(n7[it]) / profBasal[it] }
            val ratio = if (ratios.size >= 5) med(ratios) else 1.0
            val dirS = if (ratio > 1.10) 1 else if (ratio < 0.90) -1 else 0
            if (dirS != 0 && dirS == lastShift) {
                val f = ratio.coerceIn(0.85, 1.15)
                for (h in 0 until 24) profBasal[h] = Math.round(profBasal[h] * f * 100) / 100.0
                sb.append(" SHIFT x%.2f".format(f)); changed = 24; lastShift = 0
                lastDir.fill(0)
            } else {
                lastShift = dirS
                val own = DoubleArray(24) { if (needs[it].size >= 3) med(needs[it]) else Double.NaN }
                for (h in 0 until 24) {
                    if (own[h].isNaN()) { lastDir[h] = 0; continue }
                    val nb = listOf(own[(h + 23) % 24], own[(h + 1) % 24]).filter { !it.isNaN() }
                    val v = if (nb.isEmpty()) own[h] else 0.5 * own[h] + 0.5 * nb.average()
                    val dir = if (v > profBasal[h] * 1.10) 1 else if (v < profBasal[h] * 0.90) -1 else 0
                    if (dir != 0 && dir == lastDir[h]) {
                        val nv = Math.round(v.coerceIn(profBasal[h] * 0.8, profBasal[h] * 1.2) * 100) / 100.0
                        sb.append(" %02d:%.2f->%.2f".format(h, profBasal[h], nv)); profBasal[h] = nv; changed++; lastDir[h] = 0
                    } else lastDir[h] = dir
                }
            }
            lbCards++
            println("AIMI replay: learner basal card v2 day $day: $changed hours changed, total ${"%.2f".format(profBasal.sum())} U/day (shift ratio ${"%.2f".format(ratio)});$sb")
        }
        fun learnBasal(day: Int) {
            if (lb2) { learnBasal2(day); return }
            val n = out.length(); val w = minOf(n, lbDays * 288); val s0 = n - w
            val bg = DoubleArray(w); val ins = DoubleArray(w); val meal = DoubleArray(w); val hypo = DoubleArray(w)
            val hr = IntArray(w); val mn = IntArray(w)
            fun num(r: JSONObject, k: String) = r.optDouble(k, 0.0).let { if (it.isNaN()) 0.0 else it }
            for (i in 0 until w) {
                val r = out.getJSONObject(s0 + i)
                bg[i] = r.optDouble("bg", Double.NaN); meal[i] = num(r, "meal"); hypo[i] = num(r, "hypo")
                val z = Instant.ofEpochMilli(r.getLong("t")).atZone(tz); hr[i] = z.hour; mn[i] = z.minute
                val nx = if (i + 1 < w) out.getJSONObject(s0 + i + 1) else r
                ins[i] = num(nx, "deliv") * 5 / 60 + num(r, "units") + num(r, "man")
            }
            val act = DoubleArray(w) { k -> var a = 0.0; for (j in 0..minOf(60, k)) a += ins[k - j] * kern5[j]; a }
            val since = IntArray(w); var last = -100000
            for (k in 0 until w) { if (meal[k] > 0) last = k; since[k] = (k - last) * 5 }
            val needs = Array(24) { ArrayList<Double>() }
            for (k in 60 until w - 12) {
                if (mn[k] != 0 || since[k] < 300 || bg[k].isNaN() || bg[k + 12].isNaN() || bg[k] !in 70.0..216.0) continue
                if ((k until k + 12).any { meal[it] > 0 }) continue
                var a = 0.0; for (j in k until k + 12) a += act[j]
                var hy = 0.0; for (j in maxOf(0, k - 6) until k + 12) hy += hypo[j]
                needs[hr[k]].add(a + (bg[k + 12] - bg[k]) / isfP - hy / icP)
            }
            val sb = StringBuilder(); var changed = 0
            for (h in 0 until 24) {
                if (needs[h].size < 3) continue
                val med = needs[h].sorted()[needs[h].size / 2]
                val nv = (Math.round(med.coerceIn(profBasal[h] * 0.8, profBasal[h] * 1.2) * 100) / 100.0)
                if (Math.abs(nv - profBasal[h]) >= 0.02) { sb.append(" %02d:%.2f->%.2f".format(h, profBasal[h], nv)); profBasal[h] = nv; changed++ }
            }
            lbCards++
            println("AIMI replay: learner basal card day $day: $changed hours changed, total ${"%.2f".format(profBasal.sum())} U/day;$sb")
        }
        fun basalIob(now: Long): Double {
            var sum = 0.0
            for (tb in chain.ownTemps) {
                if (tb.end < now - (dia * 3_600_000).toLong()) continue
                var p0 = maxOf(tb.start, now - (dia * 3_600_000).toLong()); val en = minOf(tb.end, now)
                while (p0 < en) {
                    val q = minOf(p0 + 5 * minute, en)
                    sum += (tb.rate - profBasal[hourOf(p0)]) * (q - p0) / 3_600_000.0 * iobLeft((now - (p0 + q) / 2) / 60_000.0).first
                    p0 = q
                }
            }
            return sum
        }
        fun giveBolus(now: Long, u: Double) {
            if (u <= 0.0) return
            chain.manual.add(Pair(now, u)); doses.add(Pair(now, u)); manThisTick += u
        }

        class Episode(val t0: Long) {
            var smb = 0.0; var highTemp = false; var rose = false; var newLow = false; var highMin = 0; var done = false
            val extra = ArrayList<Triple<Long, Double, Boolean>>()   // insulin above profile: time, units, from a temp basal
            val held = ArrayList<Pair<Long, Double>>()               // insulin AutoISF wanted but the guard held back
            var nadir = 999.0; var nadirT = 0L; var peak = 0.0; var peakT = 0L
            var treatBg = 0.0; var released = false; var meal = false
        }
        fun effectAt(doses: List<Pair<Long, Double>>, at: Long) = doses.filter { it.first <= at }.sumOf { (dt, u) -> u * isfP * (1 - iobLeft((at - dt) / 60_000.0).first) }
        val episodes = ArrayList<Episode>()
        while (t < end) {
            val h = hourOf(t)
            val day = ((t - t0) / 86_400_000L).toInt()
            // Insulin given this minute: the running temp or the profile basal.
            val rateNow = chain.activeTemp(t)?.rate ?: profBasal[h]
            doses.add(Pair(t, rateNow / 60.0))
            // Insulin acting this minute (true curve).
            while (donePtr < doses.size && t - doses[donePtr].first >= (dia * 3_600_000).toLong()) donePtr++
            var acting = 0.0
            for (k in donePtr until doses.size) {
                val (dt, u) = doses[k]
                val m = (t - dt) / 60_000.0
                acting += u * (iobLeft(m - 1).first - iobLeft(m).first)
            }
            var carbsAbs = 0.0
            for (ml in eatenList) {
                val m = (t - ml.t) / 60_000.0
                if (m > ml.b + 1) continue
                carbsAbs += ml.eaten * (triCdf(m, ml.a, ml.b) - triCdf(m - 1, ml.a, ml.b))
            }
            eatenList.removeAll { (t - it.t) / 60_000.0 > it.b + 1 }
            val changed = day >= chgDay
            z += ((if (changed) kcr * chgKcr else kcr) * maxOf(0.0, 75.0 - g) - z) / 20.0
            e = noisePhi * e + rnd.nextGaussian() * noiseSd
            // CANCH_<h>: the cannula is changed every h hours; from 60 h it works worse (insulin acts down to 75 % at 96 h).
            val canAge = if (canEvery > 0) ((t - t0) / 3_600_000.0) % canEvery else 0.0
            val canF = if (canEvery > 0 && canAge > 60.0) 1.0 - 0.25 * minOf(1.0, (canAge - 60.0) / 36.0) else 1.0
            g += isfT * needBasal[h] / 60.0 - isfT * dayFactor[day] * (if (changed) chgIsf else 1.0) * canF * acting + csfT * carbsAbs + z + e
            g = g.coerceIn(30.0, 450.0)
            // Sensor.
            sens += (g - sens) / 5.0
            var reading = sens + rnd.nextGaussian() * 3.0
            if (sens < 100.0) reading -= sBias * (100.0 - sens)
            if (rnd.nextDouble() < 0.001) reading += if (rnd.nextBoolean()) 25.0 else -25.0
            reading = reading.coerceIn(39.0, 400.0)
            readT.add(t); readV.add(reading)

            // Family: a low is treated with 6 g.
            // The family treats a low the sensor shows for 2 minutes (not a one-minute spike).
            if (reading < 70.0 && readV.size > 1 && readV[readV.size - 2] < 70.0 && t - lastTreat >= 20 * minute) {
                // TREAT_<g>: fixed treatment size (default 6 g). TREATL: the learner's size - enough carbs to lift glucose
                // to ~5.5 after the fall still coming (the last 15 min trend for 15 more min) and a part of the active
                // insulin, at the profile carb sensitivity; 4-10 g, whole grams.
                val tg = if (treatL) {
                    val slope = if (readV.size > 15) (reading - readV[readV.size - 16]) / 3.0 else 0.0
                    var iobAll = basalIob(t)
                    for ((dt, u) in chain.manual + chain.ownSmb) iobAll += u * iobLeft((t - dt) / 60_000.0).first
                    val needMg = (99.0 - reading) + maxOf(0.0, -slope * 3) + maxOf(0.0, iobAll) * isfP * 0.3
                    Math.round((needMg / (isfP / icP)).coerceIn(4.0, 10.0)).toDouble()
                } else treatFix
                treatSum += tg
                eatenList.add(Meal(t, tg, tg, 10.0, 30.0)); logged.add(Logged(t, tg, tg)); hypoThisTick += tg
                val last = episodes.lastOrNull()
                if (last == null || last.done || t - last.t0 > 45 * minute) episodes.add(Episode(t).also { it.treatBg = reading })
                lastTreat = t; nTreat++; corrAsked = false; treatBg = reading
            }
            // Family: meals with a wizard bolus.
            while (mealPtr < meals.size && meals[mealPtr].t <= t) {
                val ml = meals[mealPtr]; mealPtr++
                if (ml.logged >= 13.0) mealLog.add(Pair(t, ml.logged))
                eatenList.add(Meal(t, ml.logged, ml.eaten, ml.a, ml.b)); logged.add(Logged(t, ml.logged, ml.logged)); nMeals++; mealThisTick += ml.logged
                var iobAll = 0.0
                for ((dt, u) in chain.manual + chain.ownSmb) iobAll += u * iobLeft((t - dt) / 60_000.0).first
                val bIob = basalIob(t)
                val bIobUsed = if (noIob && reading < noIobBg) maxOf(bIob, 0.0) else bIob
                if (smartS && reading < 81.0) { pend = Pending(t, ml.logged, reading); nDelayed++ }
                else {
                    val wiz = ml.logged / icP + (reading - targetP) / isfP - iobAll - bIobUsed
                    // SPLITTYPE: the family marks the meal fast or slow in the calculator (right 85 % of the time);
                    // a fast meal gets the whole bolus now, only a slow one is split.
                    val markedFast = splitType && (if (markRnd.nextDouble() < 0.85) ml.a < 100.0 else ml.a >= 100.0)
                    if (splitP < 1.0 && ml.logged >= 13.0 && !(splitType && markedFast)) {
                        // Split bolus: part now, the rest of the meal part waits for the rise.
                        val now1 = maxOf(wiz - ml.logged / icP * (1 - splitP), 0.0)
                        giveBolus(t, Math.round(now1 / 0.05) * 0.05)
                        val smbSoFar = chain.ownSmb.sumOf { it.second }
                        splits.add(Split(t, ml.logged / icP * (1 - splitP), smbSoFar, if (splitType) 180L else 90L)); nSplit++
                    } else giveBolus(t, (Math.round(maxOf(wiz, 0.0) / 0.05) * 0.05))
                }
            }
            // Smart calculator (SMARTS): a meal at a low glucose - carbs logged now, the bolus waits. Checks at 10 min,
            // then 15 min, then every minute. Fires when glucose >= 5.5 and rising, or 1.5 mmol/L above the entry.
            // Never while < 4.5 or falling. After 45 min without firing: cancelled (the loop's UAM/SMB covers the meal).
            // The dose is the wizard at the moment of firing: all carbs, minus all IOB (SMB given while waiting included).
            // Split bolus second part (SPLIT_<percent now>): checked from SPLITMIN_<min> (default 45) every minute; given when glucose >= 5.5
            // and not falling, minus the SMB the loop gave since the meal. Dropped after 90 min or if glucose < 4.5.
            splits.removeAll { sp ->
                val m = (t - sp.t) / minute
                val rising = readV.size > 5 && reading >= readV[readV.size - 6]
                val ready = if (splitAbs) devStreak >= 2 && reading >= 90.0 else reading >= 99.0
                if (m > sp.maxMin || (m >= splitMin && reading < 81.0)) { nSplitDrop++; true }
                else if (m >= splitMin && ready && rising) {
                    val rest = sp.u - (chain.ownSmb.sumOf { it.second } - sp.smb0)
                    if (rest >= 0.1) { giveBolus(t, Math.round(rest / 0.05) * 0.05); nSplitGiven++ } else nSplitDrop++
                    true
                } else false
            }
            pend?.let { pd ->
                val m = (t - pd.t) / minute
                val check = m == 10L || m >= 15L
                val rising = readV.size > 5 && reading > readV[readV.size - 6]
                if (m > 45) { pend = null; nCancel++ }
                else if (check && reading >= 81.0 && rising && (reading >= 99.0 || reading >= pd.bg + 27.0)) {
                    var iobAll = basalIob(t)
                    for ((dt, u) in chain.manual + chain.ownSmb) iobAll += u * iobLeft((t - dt) / 60_000.0).first
                    val wiz = pd.g / icP + (reading - targetP) / isfP - iobAll
                    giveBolus(t, Math.round(maxOf(wiz, 0.0) / 0.05) * 0.05)
                    pend = null; nFired++; delaySum += m
                }
            }
            // Family: sometimes a manual correction on a rebound after a low.
            if (!corrAsked && t - lastTreat in 10 * minute..120 * minute && reading > 160.0 && readV.size > 6 && reading > readV[readV.size - 6]) {
                corrAsked = true
                if (rnd.nextDouble() * 100 < corrPct) {
                    var iobAll = basalIob(t)
                    for ((dt, u) in chain.manual + chain.ownSmb) iobAll += u * iobLeft((t - dt) / 60_000.0).first
                    val u = Math.round(maxOf((reading - targetP) / isfP - iobAll, 0.0) / 0.05) * 0.05
                    if (u >= 0.1) { giveBolus(t, u); nCorr++ }
                }
            }

            // The loop, every 5 minutes.
            if ((t - t0) % (5 * minute) == 0L) {
                var a = readT.size - 1
                while (a > 0 && t - readT[a - 1] <= 61 * minute) a--
                val winT = LongArray(readT.size - a) { readT[a + it] }
                val winV = (a until readT.size).map { GV(timestamp = readT[it], raw = null, value = readV[it], trendArrow = TrendArrow.NONE, noise = null, sourceSensor = SourceSensor.UNKNOWN) }
                val gs = if (ukf == null) glucoseStatus(winV, winT, t) else {
                    // last 6 h, one value per 5 minutes, newest first
                    val n5 = minOf(72, (readT.size - 1) / 5 + 1)
                    val list = MutableList(n5) { j -> InMemoryGlucoseValue(timestamp = readT[readT.size - 1 - 5 * j], value = readV[readT.size - 1 - 5 * j]) }
                    runBlocking { ukf.smooth(list) }
                    val sT = LongArray(n5) { list[n5 - 1 - it].timestamp }
                    val sV = (0 until n5).map { val x = list[n5 - 1 - it]; GV(timestamp = x.timestamp, raw = null, value = x.smoothed ?: x.value, trendArrow = TrendArrow.NONE, noise = null, sourceSensor = SourceSensor.UNKNOWN) }
                    glucoseStatus(sV, sT, t)
                }
                // COB the AAPS way: deviation-based absorption, at least min carb impact (4 mg/dL per 5 min).
                val csfP = isfP / icP
                if (gs != null && lastLoopBg != null) {
                    val dev = (gs.glucose - lastLoopBg!!) + lastAct * isfP * 5
                    lastDev = dev; devStreak = if (dev > 6.0) devStreak + 1 else 0
                    var absorbed = maxOf(dev, 4.0) / csfP
                    for (l in logged) { if (absorbed <= 0) break; val x = minOf(l.left, absorbed); l.left -= x; absorbed -= x }
                }
                // LATE: late meal tail. 90-240 min after a meal of 13 g or more, when absorption is SEEN (deviation > 6 in
                // two cycles), glucose > 7.8 and rising: one small extra bolus per meal = what is above target minus all
                // IOB, times LATEPCT_<p> (default 50 %), at most 30 % of the meal dose. Never under 4.5 or when falling.
                if (lateOn && gs != null && devStreak >= 2 && gs.glucose > 140.0 && gs.delta > 0) {
                    for (ml in mealLog) {
                        val age = (t - ml.first) / minute
                        if (age !in 90L..240L || ml.first in lateDone) continue
                        var iobAll = basalIob(t)
                        for ((dt, u) in chain.manual + chain.ownSmb) iobAll += u * iobLeft((t - dt) / 60_000.0).first
                        // LATECOB: also count the carbs AAPS still sees as not absorbed (COB) - the wizard way.
                        val cobU = if (lateCob) logged.sumOf { it.left } / icP else 0.0
                        val want = ((gs.glucose - targetP) / isfP + cobU - iobAll) * latePct
                        val u = Math.round(minOf(want, 0.3 * ml.second / icP) / 0.05) * 0.05
                        lateDone.add(ml.first)
                        if (u >= 0.1) { giveBolus(t, u); nLate++; lateU += u }
                        break
                    }
                }
                logged.removeAll { t - it.t > 6 * 3_600_000L || it.left <= 0.0 }
                lastLoopBg = gs?.glucose
                val tick = JSONObject().put("t", t)
                    .put("prof", JSONObject().put("basal", profBasal[h]).put("isf", isfP).put("ic", icP).put("target", targetP).put("peak", peak).put("dia", dia))
                    .put("tt", JSONObject.NULL).put("aaps", JSONObject())
                    .put("cob", logged.sumOf { it.left })
                    .put("carbs3h", logged.filter { t - it.t <= 3 * 3_600_000L }.sumOf { it.g })
                    .put("lastCarb", logged.maxOfOrNull { it.t } ?: 0L)
                // Phone automations and the learner's rebound guard.
                var guardHold = false
                if (gs != null) {
                    if (gs.glucose < 75.6) { smbState = false; smbOffAt = t }
                    else if (gs.glucose > z2On && gs.shortAvgDelta > 1.8) smbState = true
                    else if (!smbState && t - smbOffAt >= 90 * minute) smbState = true
                    if (guardW > 0 && t - lastTreat < guardW * minute) {
                        val meal = logged.any { it.g > 12.0 && it.t >= lastTreat - 30 * minute }
                        val strongRise = gs.glucose > 162.0 && gs.shortAvgDelta > 3.6
                        var release = false
                        if (learnG3) {
                            val unexplained = gs.glucose > treatBg + expRise + relMargin && gs.shortAvgDelta > 1.0
                            val flatHigh = gs.glucose > 180.0 && gs.shortAvgDelta in -1.0..1.0 && t - lastTreat > 45 * minute
                            release = unexplained || flatHigh
                        }
                        if (meal) episodes.lastOrNull()?.meal = true
                        if (!meal && !strongRise && !release) guardHold = true
                        else if (release && !meal && !strongRise) episodes.lastOrNull()?.takeIf { !it.done && !it.released }?.let { it.released = true; releases++ }
                    }
                }
                val smbOk = smbState && !guardHold && pend == null
                for (ep in episodes) {
                    if (ep.done) continue
                    if (reading >= 81.0) ep.rose = true
                    else if (ep.rose && reading < 70.0) ep.newLow = true
                    if (ep.rose && reading < ep.nadir) { ep.nadir = reading; ep.nadirT = t }
                    if (reading > ep.peak) { ep.peak = reading; ep.peakT = t }
                    if (reading > 180.0) ep.highMin += 5
                    if (t - ep.t0 >= 180 * minute) {
                        ep.done = true
                        if (learnG2) {
                            val smbEff = effectAt(ep.extra.filter { !it.third }.map { Pair(it.first, it.second) }, ep.nadirT)
                            val tempEff = effectAt(ep.extra.filter { it.third }.map { Pair(it.first, it.second) }, ep.nadirT)
                            val heldEff = effectAt(ep.held, ep.peakT)
                            if (learnG3 && !ep.meal && !ep.newLow && ep.peak > ep.treatBg) expRise = 0.8 * expRise + 0.2 * (ep.peak - ep.treatBg)
                            if (learnG3 && ep.released && ep.newLow && ep.nadir + smbEff + tempEff >= 75.0) relMargin = minOf(120.0, relMargin + 10.0)
                            if (learnG3 && !ep.released && ep.highMin >= 20 && heldEff > 0.0 && ep.peak - heldEff < 180.0) relMargin = maxOf(0.0, relMargin - 5.0)
                            if (ep.newLow && ep.nadir + smbEff + tempEff >= 75.0) {
                                lowsByLoop++
                                if (tempEff > smbEff) capMult = maxOf(1.0, capMult - 0.2) else guardW = minOf(240.0, maxOf(guardW, 60.0) + 20.0)
                                loosenScore = 0; tighten++
                            } else if (ep.newLow) {
                                lowsAnyway++
                            } else if (ep.highMin >= 20 && heldEff > 0.0 && ep.peak - heldEff < 180.0) {
                                overAvoidable++; loosenScore++
                                if (loosenScore >= 3) { guardW = maxOf(0.0, guardW - 10.0); capMult = minOf(2.0, capMult + 0.05); loosenScore = 0; loosen++ }
                            }
                        } else if (learnG) {
                            if (ep.newLow && (ep.smb > 0.05 || ep.highTemp)) {
                                guardW = minOf(180.0, maxOf(guardW, 60.0) + 20.0)
                                if (ep.highTemp) capMult = maxOf(1.0, capMult - 0.2)
                                overStreak = 0; tighten++
                            } else if (!ep.newLow && ep.highMin >= 20) {
                                overStreak++
                                if (overStreak >= 3) { guardW = maxOf(0.0, guardW - 10.0); capMult = minOf(2.0, capMult + 0.05); overStreak = 0; loosen++ }
                            } else if (!ep.newLow) overStreak = 0
                        }
                    }
                }
                episodes.removeAll { it.done && t - it.t0 > 6 * 3_600_000L }
                val row = JSONObject().put("t", t).put("bg", reading).put("bgTrue", g).put("smbOk", smbOk).put("guard", guardHold)
                    .put("ntreat", nTreat).put("man", manThisTick).put("gw", guardW).put("cap", capMult).put("exp", expRise).put("mrg", relMargin).put("deliv", rateNow).put("cob", logged.sumOf { it.left })
                row.put("meal", mealThisTick).put("hypo", hypoThisTick).put("prof", profBasal[h]).put("lbg", gs?.glucose ?: JSONObject.NULL)
                manThisTick = 0.0; mealThisTick = 0.0; hypoThisTick = 0.0
                try {
                    if (gs == null) throw IllegalStateException("no glucose")
                    val chained = chainedInputs(chain, tick, t)
                    lastAct = chained.iobArray[0].activity
                    val rt = runTickAutoIsf(autoIsf, tick, meta, t, chained, gs, smbOk, liveVs = autoIsf321Sens(winV, winT, t, tick))
                    val smb = rt.units ?: 0.0
                    if (smb > 0.0) { chain.ownSmb.add(Pair(t, smb)); doses.add(Pair(t, smb)) }
                    val capRate = profBasal[h] * capMult
                    val rate = if (guardHold && capOn && (rt.rate ?: 0.0) > capRate) capRate else rt.rate
                    val dur = rt.duration
                    episodes.lastOrNull()?.takeIf { !it.done && t - it.t0 <= 150 * minute }?.let { ep ->
                        ep.smb += smb
                        if ((rate ?: 0.0) > profBasal[h] * 1.05 && (dur ?: 0) > 0) ep.highTemp = true
                        if (learnG2) {
                            if (smb > 0.0) ep.extra.add(Triple(t, smb, false))
                            val applied = chain.activeTemp(t)?.rate ?: profBasal[h]
                            if (applied > profBasal[h]) ep.extra.add(Triple(t, (applied - profBasal[h]) * 5 / 60.0, true))
                            if (guardHold) {
                                val want = runTickAutoIsf(autoIsf, tick, meta, t, chained, gs, smbState, liveVs = autoIsf321Sens(winV, winT, t, tick))
                                val heldU = maxOf(0.0, (want.units ?: 0.0) - smb) +
                                    maxOf(0.0, (want.rate ?: profBasal[h]) - (rate ?: profBasal[h])) * 5 / 60.0
                                if (heldU > 0.0) ep.held.add(Pair(t, heldU))
                            }
                        }
                    }
                    if (rate != null && dur != null) {
                        chain.activeTemp(t)?.let { it.end = t }
                        if (dur > 0) chain.ownTemps.add(OwnTemp(t, rate, t + dur * minute))
                    }
                    row.put("units", smb).put("rate", rate ?: JSONObject.NULL).put("dur", dur ?: JSONObject.NULL).put("iob", chained.iobArray[0].iob)
                } catch (ex: Throwable) {
                    failed++
                    row.put("error", ex.javaClass.simpleName)
                }
                out.put(row)
                // LEARNB: the learner's basal card every LBEVERY_<d> days (default 4) at 06:00, accepted at once.
                val lt = Instant.ofEpochMilli(t).atZone(tz)
                if (learnB && day >= lbEvery && day % lbEvery == 0 && lt.hour == 6 && lt.minute == 0) learnBasal(day)
                if (out.length() % 500 == 0) clearAllMocks(answers = false, recordedCalls = true, childMocks = false)
            }
            t += minute
        }
        write(outPath, out)
        println("AIMI replay: synthetic $days days, $nMeals meals, $nTreat hypo treatments, $nCorr rebound corrections, $failed failed ticks, smart: $nDelayed delayed, $nFired fired (avg ${if (nFired > 0) delaySum / nFired else 0} min), $nCancel cancelled, late tail $nLate boluses ${"%.1f".format(lateU)} U, treatments avg ${if (nTreat > 0) "%.1f".format(treatSum / nTreat) else "-"} g, split: $nSplit, second part given $nSplitGiven, dropped $nSplitDrop")
        if (learnG3) println("AIMI replay: learner 3 let SMB go early $releases times; expected rebound ${expRise.roundToInt()} mg/dL, margin ${relMargin.roundToInt()} mg/dL")
        if (learnG2) println("AIMI replay: learner 2 saw $lowsByLoop lows caused by the loop, $lowsAnyway lows that would come anyway, $overAvoidable overshoots the guard caused")
        if (learnG) println("AIMI replay: learner ended with guard ${guardW.roundToInt()} min, cap ${"%.2f".format(capMult)}x profile; stricter $tighten times, looser $loosen times")
        println("AIMI replay: ${out.length()} ticks, $failed failed")
    }

    private fun readHistory(data: JSONObject): History {
        val icfg = ICfg(insulinLabel = "replay", peak = 45, dia = 5.0, concentration = 1.0)
        val bg = data.getJSONArray("bg").rows().mapTo(mutableListOf()) {
            GV(timestamp = it.getLong(0), raw = null, value = it.getDouble(1), trendArrow = TrendArrow.NONE, noise = null, sourceSensor = SourceSensor.UNKNOWN)
        }
        val boluses = data.getJSONArray("boluses").rows().mapTo(mutableListOf()) {
            BS(timestamp = it.getLong(0), amount = it.getDouble(1), type = if (it.getBoolean(2)) BS.Type.SMB else BS.Type.NORMAL, iCfg = icfg)
        }
        val carbs = data.getJSONArray("carbs").rows().mapTo(mutableListOf()) { CA(timestamp = it.getLong(0), duration = 0L, amount = it.getDouble(1)) }
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
        every { get(DoubleKey.OApsAIMIweight) } returns meta.getDouble("weightKg")
        every { get(DoubleKey.OApsAIMITDD7) } returns meta.getDouble("tdd")
        every { get(DoubleKey.ApsSmbMaxIob) } returns meta.getDouble("maxIob")
        every { get(DoubleKey.SafetyMaxBolus) } returns meta.getDouble("maxBolus")
        every { get(DoubleKey.ApsMaxBasal) } returns meta.getDouble("maxBasal")
        every { get(IntKey.ApsMaxSmbFrequency) } returns meta.getInt("smbInterval")
        every { get(IntKey.ApsMaxMinutesOfBasalToLimitSmb) } returns meta.getInt("maxSmbMin")
        every { get(IntKey.ApsUamMaxMinutesOfBasalToLimitSmb) } returns meta.getInt("maxUamSmbMin")
        every { get(UnitDoubleKey.ApsLgsThreshold) } returns meta.getDouble("lgs")
        // Settings to try, from the workflow: "Name=value,Name=value" with enum names of BooleanKey,
        // DoubleKey or IntKey.
        System.getenv("AIMI_REPLAY_PREFS").orEmpty().split(',').map { it.trim() }.filter { it.contains('=') && !it.startsWith("LOOP_MIN") && !it.startsWith("AISF_LIVE") && !it.startsWith("SMB_INTERVAL") && !it.startsWith("LATE_BOLUS") && !it.startsWith("NEED_") && !it.startsWith("CARBS_") && !it.startsWith("Z2_") && !it.startsWith("GUARD_") }.forEach { pair ->
            val (name, value) = pair.split('=', limit = 2).map { it.trim() }
            BooleanKey.entries.firstOrNull { it.name == name }?.let { key -> every { get(key) } returns value.toBoolean(); return@forEach }
            DoubleKey.entries.firstOrNull { it.name == name }?.let { key -> every { get(key) } returns value.toDouble(); return@forEach }
            IntKey.entries.firstOrNull { it.name == name }?.let { key -> every { get(key) } returns value.toInt(); return@forEach }
            error("unknown setting $name")
        }
    }

    /**
     * The body model of the closed loop replay. Glucose = the real glucose + what the extra (or missing)
     * insulin of the algorithm would have done, with ISF [isf] and the insulin curve peak 35 min / 5 h
     * (fitted on Eva's fasting periods). Everything else (food, the meal boluses, the day) is in the
     * real glucose already. The real hypo treatments ([removed]) are taken out and the model gives its
     * own ([treats], 6 g, carb sensitivity [csf], absorbed in 30 min after 5 min) when glucose is low.
     * No feedback of glucose on the body: a type 1 body has almost none.
     */
    private inner class Body(val isf: Double, val csf: Double, val removed: List<Pair<Long, Double>>) {
        private val curve = curve(35.0, 5.0)
        private val insulin = ArrayList<Pair<Long, Double>>()
        private var donePointer = 0
        private var doneUnits = 0.0
        val treats = ArrayList<Pair<Long, Double>>()
        /** Extra meal carbs of the CARBS scenario: slow absorption like Eva's meals (peak about 2.5 h, done in 4.5 h). */
        val extraMeals = ArrayList<Pair<Long, Double>>()

        private fun slowAbsorbed(m: Double): Double {
            // Triangle rate: 0 at 0 min, peak at 150 min, 0 at 270 min. Returns the absorbed fraction.
            val a = 150.0; val b = 270.0
            return when {
                m <= 0 -> 0.0
                m <= a -> m * m / (a * b)
                m < b -> 1.0 - (b - m) * (b - m) / ((b - a) * b)
                else -> 1.0
            }
        }

        private var sorted = true

        fun addInsulin(t: Long, u: Double) {
            if (insulin.isNotEmpty() && t < insulin.last().first) sorted = false
            insulin.add(Pair(t, u))
        }

        private val need = ArrayList<Pair<Long, Double>>()

        /** Extra need of the NEED scenario: counts like insulin used up by the body, not like a dose. */
        fun addNeed(t: Long, u: Double) {
            addInsulin(t, -u)
            need.add(Pair(t, u))
        }

        /** Insulin the algorithm gave beyond (or below) the real doses; the NEED part is not a dose. */
        fun insulinBetween(from: Long, to: Long) =
            insulin.filter { it.first in from until to }.sumOf { it.second } + need.filter { it.first in from until to }.sumOf { it.second }

        private fun carbsIn(list: List<Pair<Long, Double>>, ts: Long) =
            list.sumOf { (t, g) -> if (t > ts) 0.0 else g * ((ts - t - 5 * minute) / (30.0 * minute)).coerceIn(0.0, 1.0) }

        fun bgAt(ts: Long, real: Double): Double {
            // Doses older than 5 h act fully; keep their sum so each reading looks only at the last 5 h.
            if (!sorted) { insulin.sortBy { it.first }; sorted = true }
            while (donePointer < insulin.size && ts - insulin[donePointer].first >= 5 * 3_600_000L) {
                doneUnits += insulin[donePointer].second; donePointer++
            }
            var acted = doneUnits
            for (k in donePointer until insulin.size) {
                val (t, u) = insulin[k]
                if (t > ts) break
                acted += u * (1 - curve((ts - t) / 60_000.0).first)
            }
            val meals = extraMeals.sumOf { (t, g) -> if (t > ts) 0.0 else g * slowAbsorbed((ts - t) / 60_000.0) }
            val v = real - isf * acted + csf * (carbsIn(treats, ts) - carbsIn(removed, ts) + meals)
            return v.coerceIn(39.0, 400.0)
        }
    }

    /** Glucose status from the readings up to [now], the way the input file was made. */
    private fun glucoseStatus(bg: List<GV>, bgT: LongArray, now: Long): GlucoseStatusAIMI? {
        val i = bgT.binarySearch(now).let { if (it >= 0) it else -it - 2 }
        if (i < 0 || now - bgT[i] > 12 * minute) return null
        val g0 = bg[i].value
        val last = ArrayList<Double>(); val short = ArrayList<Double>(); val long = ArrayList<Double>()
        var j = i - 1
        while (j >= 0 && bgT[i] - bgT[j] <= 45 * minute) {
            val m = (bgT[i] - bgT[j]) / minute.toDouble()
            val d = (g0 - bg[j].value) / m * 5
            if (m > 2.5 && m < 7.5) last.add(d)
            if (m > 2.5 && m < 17.5) short.add(d) else if (m > 17.5 && m < 42.5) long.add(d)
            j--
        }
        fun avg(a: List<Double>) = if (a.isEmpty()) 0.0 else a.average()
        return GlucoseStatusAIMI(
            glucose = g0, noise = 0.0, delta = if (last.isNotEmpty()) avg(last) else avg(short),
            shortAvgDelta = avg(short), longAvgDelta = avg(long), date = bgT[i]
        )
    }

    private fun prevNowOf(ticks: JSONArray, i: Int) = if (i == 0) ticks.getJSONObject(0).getLong("t") - 5 * minute else ticks.getJSONObject(i - 1).getLong("t")

    /** What AAPS really did at the tick: the model check (the model must give back the real glucose then). */
    private fun realTick(realSmb: List<Pair<Long, Double>>, prev: Long, now: Long, rate: Double?, basal: Double) = RT(
        algorithm = APSResult.Algorithm.AUTO_ISF, runningDynamicIsf = false, timestamp = now,
        units = realSmb.filter { it.first in prev until now }.sumOf { it.second },
        rate = rate ?: basal, duration = 30,
    ).also { it.reason.append("real") }

    /** AutoISF of AAPS (the oref core with Eva's SMB settings). Its ISF factor is the one AAPS had at the tick. */
    private fun runTickAutoIsf(
        det: DetermineBasalAutoISF, tick: JSONObject, meta: JSONObject, now: Long, chained: Chained, gs: GlucoseStatusAIMI, smbOk: Boolean,
        liveVs: Double? = null,
    ): RT {
        val prof = tick.getJSONObject("prof")
        val tt = if (tick.isNull("tt")) null else tick.getDouble("tt")
        val target = tt ?: prof.getDouble("target")
        val vs = liveVs ?: tick.getJSONObject("aaps").optDouble("vs", 0.0).takeIf { it > 0.0 } ?: prof.getDouble("isf")
        val profile = OapsProfileAutoIsf(
            dia = 0.0, min_5m_carbimpact = 0.0, max_iob = meta.getDouble("maxIob"), max_daily_basal = prof.getDouble("basal"),
            max_basal = meta.getDouble("maxBasal"), min_bg = target, max_bg = target, target_bg = target,
            carb_ratio = prof.getDouble("ic"), sens = prof.getDouble("isf"), autosens_adjust_targets = false,
            max_daily_safety_multiplier = 3.0, current_basal_safety_multiplier = 4.0,
            high_temptarget_raises_sensitivity = false, low_temptarget_lowers_sensitivity = false,
            sensitivity_raises_target = false, resistance_lowers_target = false, adv_target_adjustments = false,
            exercise_mode = false, half_basal_exercise_target = 160, maxCOB = 120, skip_neutral_temps = false,
            remainingCarbsCap = 90, enableUAM = true, A52_risk_enable = false, SMBInterval = meta.getInt("smbInterval"),
            enableSMB_with_COB = true, enableSMB_with_temptarget = true, allowSMB_with_high_temptarget = false,
            enableSMB_always = true, enableSMB_after_carbs = true,
            maxSMBBasalMinutes = meta.getInt("maxSmbMin"), maxUAMSMBBasalMinutes = meta.getInt("maxUamSmbMin"),
            bolus_increment = 0.025, carbsReqThreshold = 8, current_basal = prof.getDouble("basal"), temptargetSet = tt != null,
            autosens_max = 1.2, out_units = "mmol/L", lgsThreshold = meta.getDouble("lgs").toInt(), variable_sens = vs,
            autoISF_version = "3.2", enable_autoISF = true, autoISF_max = 1.2, autoISF_min = 0.7,
            bgAccel_ISF_weight = 0.0, bgBrake_ISF_weight = 0.2, pp_ISF_weight = 0.01, lower_ISFrange_weight = 0.0,
            higher_ISFrange_weight = 0.0, dura_ISF_weight = 0.0,
            smb_delivery_ratio = 0.5, smb_delivery_ratio_min = 0.5, smb_delivery_ratio_max = 0.5, smb_delivery_ratio_bg_range = 0.0,
            smb_max_range_extension = 1.0, enableSMB_EvenOn_OddOff_always = false, iob_threshold_percent = 100, profile_percentage = 100,
        )
        return det.determine_basal(
            glucose_status = GlucoseStatusSMB(gs.glucose, 0.0, gs.delta, gs.shortAvgDelta, gs.longAvgDelta, gs.date),
            currenttemp = chained.temp, iob_data_array = chained.iobArray, profile = profile,
            autosens_data = AutosensResult(ratio = 1.0),
            meal_data = MealData(
                carbs = tick.getDouble("carbs3h"), mealCOB = tick.getDouble("cob"),
                lastBolusTime = chained.iobArray[0].lastBolusTime, lastCarbTime = tick.getLong("lastCarb")
            ),
            microBolusAllowed = smbOk, currentTime = now, flatBGsDetected = false, autoIsfMode = true, loop_wanted_smb = "AAPS",
            profile_percentage = 100, smb_ratio = 0.5, smb_max_range_extension = 1.0, iob_threshold_percent = 100,
            auto_isf_consoleError = mutableListOf(), auto_isf_consoleLog = mutableListOf(),
        )
    }

    /** More loop cycles: between two recorded cycles, one every [stepMin] minutes, each a copy of the earlier one. */
    private fun expandTicks(src: JSONArray, stepMin: Long): JSONArray {
        val out = JSONArray()
        for (i in 0 until src.length()) {
            val a = src.getJSONObject(i)
            val t0 = a.getLong("t")
            val t1 = if (i + 1 < src.length()) src.getJSONObject(i + 1).getLong("t") else t0 + 5 * minute
            var t = t0
            while (t < t1) {
                out.put(JSONObject(a.toString()).put("t", t))
                t += stepMin * minute
            }
        }
        return out
    }

    /**
     * ISF of AutoISF 3.2.1 (blaqone/JetFoxy port, OpenAPSAutoISFPlugin.autoISF) from the model glucose of the last
     * 47 minutes, one reading a minute (the raw fit path of GlucoseStatusCalculatorAutoIsf, shortest parabola 10 min).
     * Eva's weights: acce 0.1, brake 0.20, lower range 0.15, higher range 0.1, pp 0.01, dura 0.5, min 0.3, max 1.2;
     * autosens ratio 1, no activity, no exercise mode.
     */
    private fun autoIsf321Sens(bg: List<GV>, bgT: LongArray, now: Long, tick: JSONObject): Double {
        val prof = tick.getJSONObject("prof")
        val sens = prof.getDouble("isf")
        val target = if (tick.isNull("tt")) prof.getDouble("target") else tick.getDouble("tt")
        val i0 = bgT.binarySearch(now).let { if (it >= 0) it else -it - 2 }
        if (i0 < 4) return sens
        val src = ArrayList<Pair<Long, Double>>()
        var k = i0
        while (k >= 0 && bgT[i0] - bgT[k] <= 60 * minute) { src.add(Pair(bgT[k], bg[k].value)); k-- }
        val gNow = src[0].second
        // dura: minutes the glucose stayed within +-5 % of its running mean
        var sumBG = gNow; var oldAvg = gNow; var minutesDur = 0L; var n = 1
        for (j in 1 until src.size) {
            val (tj, v) = src[j]
            if (v <= 39) continue
            n += 1
            if (((src[0].first - tj) / 60_000.0).roundToInt() - minutesDur > 13) break
            if (v > oldAvg * 0.95 && v < oldAvg * 1.05) { sumBG += v; oldAvg = sumBG / n; minutesDur = ((src[0].first - tj) / 60_000.0).roundToLong() } else break
        }
        // parabola: best fit over the last 10..47 minutes
        var corrMax = 0.0; var a0 = 0.0; var a1 = 0.0; var a2 = 0.0; var acc = 0.0
        var sy = 0.0; var sx = 0.0; var sx2 = 0.0; var sx3 = 0.0; var sx4 = 0.0; var sxy = 0.0; var sx2y = 0.0
        var tiLast = 0.0; var nn = 0
        val time0 = src[0].first
        for (j in src.indices) {
            val v = src[j].second
            if (v <= 39) continue
            nn += 1
            val ti = (src[j].first - time0) / 1000.0
            if (-ti > 47 * 60) break
            if (ti < tiLast - 11.0 * 60) break
            tiLast = ti
            sx += ti; sx2 += ti * ti; sx3 += ti * ti * ti; sx4 += ti * ti * ti * ti; sy += v; sxy += ti * v; sx2y += ti * ti * v
            if (nn > 3 && -ti > 10 * 60) {
                val detH = sx4 * (sx2 * nn - sx * sx) - sx3 * (sx3 * nn - sx * sx2) + sx2 * (sx3 * sx - sx2 * sx2)
                if (detH == 0.0) continue
                val detA = sx2y * (sx2 * nn - sx * sx) - sxy * (sx3 * nn - sx * sx2) + sy * (sx3 * sx - sx2 * sx2)
                val detB = sx4 * (sxy * nn - sy * sx) - sx3 * (sx2y * nn - sy * sx2) + sx2 * (sx2y * sx - sxy * sx2)
                val detC = sx4 * (sx2 * sy - sx * sxy) - sx3 * (sx3 * sy - sx * sx2y) + sx2 * (sx3 * sxy - sx2 * sx2y)
                val a = detA / detH * 300.0 * 300.0; val b = detB / detH * 300.0; val c = detC / detH
                val yMean = sy / nn
                var ss = 0.0; var sr = 0.0
                for (q in 0..j) {
                    val dt = (src[q].first - time0) / 1000.0 / 300.0
                    ss += (src[q].second - yMean).let { it * it }
                    sr += (src[q].second - (a * dt * dt + b * dt + c)).let { it * it }
                }
                val r2 = if (ss != 0.0) 1 - sr / ss else 0.0
                if (r2 >= corrMax) { corrMax = r2; acc = 2 * a; a0 = c; a1 = b; a2 = a }
            }
        }
        // delta as oref: change over the last 5 minutes
        val delta = src.firstOrNull { src[0].first - it.first >= 5 * minute }?.let { gNow - it.second } ?: 0.0
        val bgOff = target + 10.0 - gNow
        var acceWeight = 1.0
        if (a2 != 0.0 && corrMax >= 0.9) {
            val mmDelta = -a1 / 2 / a2 * 5
            val mmValue = a0 - mmDelta * mmDelta / 25 * a2
            if (mmDelta > 0 && acc > 0.0 && mmDelta <= 30 && mmValue < target) acceWeight = -0.20
        }
        var acceIsf = 1.0; var modified = false
        if (corrMax >= 0.9) {
            val share = 10 * (corrMax - 0.9)
            var cap = 1.0
            if (acceWeight == 1.0 && gNow < target) {
                if (acc > 0) { if (acc > 1) cap = 0.5; acceWeight = 0.20 } else if (acc < 0) acceWeight = 0.1
            } else if (acceWeight == 1.0) {
                if (acc < 0) acceWeight = 0.20 else if (acc > 0) acceWeight = 0.1
            }
            acceIsf = 1.0 + acc * cap * acceWeight * share
            if (acceIsf != 1.0) modified = true
        }
        val bgIsf = 1 + aisfInterpolate(100 - bgOff)
        fun limit(lift: Double): Double { val l = lift.coerceIn(0.3, 1.2); return if (l >= 1) maxOf(l, 1.0) else minOf(l, 1.0) }
        if (bgIsf < 1.0) {
            val lift = if (acceIsf > 1.0) bgIsf * acceIsf else minOf(bgIsf, acceIsf)
            return minOf(720.0, sens / limit(lift))
        } else if (bgIsf > 1.0) modified = true
        var ppIsf = 1.0
        if (bgOff <= 0.0 && delta >= 0) { ppIsf = 1.0 + maxOf(0.0, delta * 0.01); if (ppIsf != 1.0) modified = true }
        var duraIsf = 1.0
        if (minutesDur >= 10 && oldAvg > target) { duraIsf += minutesDur / 60.0 * (0.5 / target) * (oldAvg - target); modified = true }
        if (!modified) return sens
        var lift = maxOf(duraIsf, maxOf(bgIsf, maxOf(acceIsf, ppIsf)))
        if (acceIsf < 1.0) lift *= acceIsf
        return sens / limit(lift)
    }

    private fun aisfInterpolate(x: Double): Double {
        val px = doubleArrayOf(50.0, 60.0, 80.0, 90.0, 100.0, 110.0, 150.0, 180.0, 200.0)
        val py = doubleArrayOf(-0.5, -0.5, -0.3, -0.2, 0.0, 0.0, 0.5, 0.7, 0.7)
        val v = when {
            x < px[0] -> py[0] + (py[1] - py[0]) / (px[1] - px[0]) * (x - px[0])
            x > px.last() -> py[7] + (py[8] - py[7]) / (px[8] - px[7]) * (x - px[7])
            else -> {
                var r = py.last()
                for (i in 1 until px.size) if (x <= px[i]) { r = py[i - 1] + (py[i] - py[i - 1]) / (px[i] - px[i - 1]) * (x - px[i - 1]); break }
                r
            }
        }
        return v * (if (x > 100) 0.1 else 0.15)
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
        // With a zero temp from now on for 4 h, as AAPS gives oref for its zero temp prediction.
        val zeroDoses = chain.doses(now, diaMs, until = now) + (0 until 48).map { k ->
            val p = now + k * 5 * minute
            Pair(p + 150_000L, -chain.basalAt(p) * 5 / 60.0)
        }
        fun atZero(whenMs: Long): Pair<Double, Double> {
            var iob = 0.0; var act = 0.0
            for ((t, u) in zeroDoses) {
                if (t > whenMs) continue
                val (i, a) = f((whenMs - t) / 60_000.0)
                iob += i * u; act += a * u
            }
            return Pair(iob, act)
        }
        val iobArray = Array(48) { k ->
            val (i, a, _) = at(now + k * 5 * minute)
            val (zi, za) = atZero(now + k * 5 * minute)
            IobTotal(
                time = now + k * 5 * minute, iob = i, activity = a, lastBolusTime = lastBolus,
                iobWithZeroTemp = IobTotal(time = now + k * 5 * minute, iob = zi, activity = za)
            )
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

    private fun runTick(
        harness: DetermineBasalAimiScenarioTestHarness, tick: JSONObject, meta: JSONObject, now: Long, chained: Chained,
        gsSim: GlucoseStatusAIMI?, smbOk: Boolean,
    ) =
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
                glucose_status = gsSim ?: GlucoseStatusAIMI(
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
                // As AAPS had it: the automations switched SMBs off on a quarter of the cycles.
                microBolusAllowed = smbOk,
                currentTime = now,
                flatBGsDetected = false,
                dynIsfMode = false,
                uiInteraction = mockk<UiInteraction>(relaxed = true),
                extraDebug = "history replay",
            )
        }
}
