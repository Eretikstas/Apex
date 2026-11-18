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
            manual = bolusRows.filter { !it.getBoolean(2) }.map { Pair(movedTime(it.getLong(0)), it.getDouble(1)) },
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
        // Scenario NEED_<pct>: the body needs pct % more basal insulin (lower sensitivity). It acts like
        // insulin that is used up by the body, so glucose drifts up unless the algorithm gives more.
        val prefsText = System.getenv("AIMI_REPLAY_PREFS").orEmpty()
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
        var prevNow = startMs
        var traceLeft = if (System.getenv("AIMI_REPLAY_TRACE") == "1") 25 else 0
        val autoIsf = DetermineBasalAutoISF(mockk(relaxed = true))

        val out = JSONArray()
        var failed = 0
        var lastGuardRefresh = 0L
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
                    bgIdx++
                }
                gsSim = glucoseStatus(history.bg, bgT, now)
                // The automations of the phone: SMB off below 4.2 mmol/L, back on above 6.5 when rising.
                gsSim?.let { gs ->
                    if (gs.glucose < 75.6) smbState = false
                    else if (gs.glucose > 117.0 && gs.shortAvgDelta > 1.8) smbState = true
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
                val smbOk = if (sim) smbState else tick.optBoolean("smbOk", true)
                val rt = when (algo) {
                    "aimi" -> runTick(harness, tick, meta, now, chained, gsSim, smbOk)
                    "autoisf" -> runTickAutoIsf(
                        autoIsf, tick, meta, now, chained, gsSim!!, smbOk,
                        liveVs = if (aisfLive) autoIsf321Sens(history.bg, bgT, now, tick) else null,
                    )
                    else -> realTick(realSmbAfter, prevNowOf(ticks, i), now, realRateAt(now), basalAt(now))
                }
                if (sim && algo != "real") (rt.units ?: 0.0).takeIf { it > 0.0 }?.let { body.addInsulin(now, it) }
                if (sim) row.put("bg", gsSim?.glucose ?: JSONObject.NULL).put("smbOk", smbOk)
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
        if (sim) println("AIMI replay: body model, algorithm $algo, ${body.treats.size} model hypo treatments")
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
        val manual: List<Pair<Long, Double>>,
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
        System.getenv("AIMI_REPLAY_PREFS").orEmpty().split(',').map { it.trim() }.filter { it.contains('=') && !it.startsWith("LOOP_MIN") && !it.startsWith("AISF_LIVE") && !it.startsWith("SMB_INTERVAL") && !it.startsWith("LATE_BOLUS") && !it.startsWith("NEED_") && !it.startsWith("CARBS_") }.forEach { pair ->
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
