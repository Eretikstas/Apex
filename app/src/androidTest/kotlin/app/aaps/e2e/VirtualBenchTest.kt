package app.aaps.e2e

import android.Manifest
import android.content.Context
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.RM
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.data.model.data.Block
import app.aaps.core.data.model.data.TargetBlock
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.time.T
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.insulin.InsulinManager
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileRepository
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.BooleanComposedKey
import app.aaps.core.keys.BooleanNonKey
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.LongNonKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.di.Atc3Emulation
import app.aaps.di.EmulatedApexBLE
import app.aaps.di.ResetGraphRule
import app.aaps.di.testGraphs
import app.aaps.implementation.plugin.PluginStore
import app.aaps.plugins.constraints.objectives.ObjectivesPlugin
import app.aaps.plugins.learner.LearnerPlugin
import app.aaps.pump.apex.ApexPumpPlugin
import app.aaps.pump.apex.keys.ApexIntKey
import app.aaps.pump.apex.keys.ApexStringKey
import app.aaps.pump.apex.link.ApexRadioSwap
import app.aaps.pump.atc3.emulator.Atc3PumpEmulator
import app.aaps.pump.atc3.emulator.VirtualAtc3Pump
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

/**
 * The virtual bench (user 21.11: "погоняй на стенде"; no test phone yet): the real app on an Android emulator, the Apex
 * v4 driver against our software pump (same TruCare III protocol), AutoISF closed loop, the learner. Glucose comes from a small virtual child body
 * (made-up child values, no personal data) and from scripted rises and falls.
 *
 * What it checks is what the real bench would: the loop's decisions reach the pump and AAPS keeps what the pump does;
 * the learner's percentage card proposes, "Внести" reaches the pump as a new basal; one change at a time is kept.
 * It cannot check how well the child does: that is the body model's job (lab), and the real bench's after it.
 */
@RunWith(AndroidJUnit4::class)
class VirtualBenchTest {

    @get:Rule val rules: RuleChain = RuleChain.outerRule(RetryRule()).around(ResetGraphRule())

    private val preferences: Preferences get() = testGraphs.preferences
    private val pluginStore: PluginStore get() = testGraphs.pluginStore
    private val commandQueue: CommandQueue get() = testGraphs.commandQueue
    private val pluginList: List<PluginBase> get() = testGraphs.allPlugins(testGraphs.aapsLogger)
    private val pumpPlugin: ApexPumpPlugin get() = pluginList.filterIsInstance<ApexPumpPlugin>().single()
    private val learner: LearnerPlugin get() = pluginList.filterIsInstance<LearnerPlugin>().single()
    private val configBuilder: ConfigBuilder get() = testGraphs.configBuilder
    private val config: Config get() = testGraphs.config
    private val profileFunction: ProfileFunction get() = testGraphs.profileFunction
    private val profileRepository: ProfileRepository get() = testGraphs.profileRepository
    private val insulinManager: InsulinManager get() = testGraphs.insulinManager
    private val dateUtil: DateUtil get() = testGraphs.dateUtil
    private val persistenceLayer: PersistenceLayer get() = testGraphs.persistenceLayer
    private val loop: Loop get() = testGraphs.loop
    private val objectivesPlugin: ObjectivesPlugin get() = testGraphs.objectivesPlugin

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    private lateinit var pump: VirtualAtc3Pump
    private lateinit var emulator: Atc3PumpEmulator

    @Before
    fun setUp() {
        clearAllSharedPrefs()
        runBlocking { persistenceLayer.clearDatabases() }
        // As in ApexScenarioTest: v4's own database removed (a fresh install), a used bolus journal, and the pump's
        // heartbeat, at which v4 settles its journal. Without them the SMBs reached the pump but never AAPS.
        instrumentation.targetContext.deleteDatabase(APEX_DATABASE)
        pump = VirtualAtc3Pump(SERIAL) { System.currentTimeMillis() }
        pump.seedBolusHistory(System.currentTimeMillis())
        pump.startHeartbeats()
        emulator = Atc3PumpEmulator(pump)
        Atc3Emulation.emulator = emulator
        ApexRadioSwap.make = { logger, context, trace -> EmulatedApexBLE(logger, context, trace) }
        instrumentation.uiAutomation.grantRuntimePermission(PKG, Manifest.permission.BLUETOOTH_CONNECT)
        runCatching { instrumentation.uiAutomation.grantRuntimePermission(PKG, Manifest.permission.BLUETOOTH_SCAN) }
        runCatching { instrumentation.uiAutomation.grantRuntimePermission(PKG, "android.permission.POST_NOTIFICATIONS") }

        preferences.put(BooleanNonKey.GeneralSetupWizardProcessed, true)
        preferences.put(StringKey.GeneralUnits, "mg/dl")
        preferences.put(ApexStringKey.ApexSerialNumber, SERIAL)
        preferences.put(ApexStringKey.ApexBtPassword, pump.btPassword)
        preferences.put(ApexStringKey.ApexAddress, ADDRESS)
        // The shortest heartbeat v4 allows: it settles its journal (the SMB rows) at the heartbeat.
        preferences.put(ApexIntKey.HeartbeatPeriod, 2)
        preferences.put(BooleanComposedKey.ConfigBuilderEnabled, "PUMP_ApexPumpPlugin", value = true)
        preferences.put(BooleanComposedKey.ConfigBuilderEnabled, "PUMP_VirtualPumpPlugin", value = false)
        // AutoISF with SMB, as on the real phone; room for the loop to act (a child's caps).
        preferences.put(BooleanComposedKey.ConfigBuilderEnabled, "APS_OpenAPSSMBPlugin", value = false)
        preferences.put(BooleanComposedKey.ConfigBuilderEnabled, "APS_OpenAPSAutoISFPlugin", value = true)
        preferences.put(DoubleKey.ApsMaxBasal, 2.0)
        preferences.put(DoubleKey.ApsSmbMaxIob, 3.0)
        preferences.put(IntKey.ApsLoopInterval, 1)
        // SMB at most every 10 min, as on the real phone (the AAPS default is 3).
        preferences.put(IntKey.ApsMaxSmbFrequency, 10)

        pluginStore.plugins = pluginList
        configBuilder.initialize()
        config.initCompleted()
        seedProfile()
        activateProfile()
        // Closed loop allowed: every objective done, running mode closed.
        objectivesPlugin.objectives.forEach { it.startedOn = 1; it.accomplishedOn = 1 }
        runBlocking {
            persistenceLayer.insertOrUpdateRunningMode(
                runningMode = RM(timestamp = dateUtil.now(), mode = RM.Mode.CLOSED_LOOP, autoForced = false, duration = 0),
                action = Action.CLOSED_LOOP_MODE, source = Sources.Aaps, listValues = listOf(ValueWithUnit.SimpleString("bench"))
            )
        }
        status("bench start")
        assertWithMessage("the driver is initialized").that(awaitTrue(INIT_TIMEOUT) { pumpPlugin.isInitialized() }).isTrue()
        assertWithMessage("a profile runs").that(awaitTrue(INIT_TIMEOUT) { runBlocking { profileFunction.getProfile() } != null }).isTrue()
        // A record of the same minute as the pump registration is refused by AAPS: start in the next minute.
        // v4 registers the pump with AAPS at its first heartbeat read; AAPS refuses a row dated before that moment, so a
        // bolus given earlier would never get its row. Wait for it, as a real pump is registered long before.
        assertWithMessage("AAPS registered the pump").that(awaitTrue(REGISTER_TIMEOUT) { preferences.get(StringNonKey.ActivePumpSerialNumber) == SERIAL }).isTrue()
        val ready = maxOf(System.currentTimeMillis(), preferences.get(LongNonKey.ActivePumpChangeTimestamp))
        val wait = (ready / 60_000L + 1) * 60_000L + 2_000L - System.currentTimeMillis()
        if (wait > 0) SystemClock.sleep(wait)
    }

    @After
    fun tearDown() {
        runCatching { commandQueue.clear() }
        runCatching { WorkManager.getInstance(instrumentation.targetContext).cancelAllWork() }
        runCatching { pumpPlugin.disconnect("bench end") }
        runCatching { pumpPlugin.setPluginEnabledBlocking(PluginType.PUMP, false) }
        objectivesPlugin.objectives.forEach { it.startedOn = 0; it.accomplishedOn = 0 }
        Atc3Emulation.emulator = null
        ApexRadioSwap.make = null
        runCatching { pump.shutdown() }
        clearAllSharedPrefs()
    }

    /**
     * Closed loop on a fast rise, then on a fast fall (one reading a minute, loop every minute). On the rise the loop
     * must give more insulin (a high temp basal or an SMB) and the pump must have it; on the fall it must hold back
     * (temp basal under the profile, no new SMB). At every step AAPS and the pump agree on the running temp basal.
     */
    @Test
    fun closedLoop_riseThenFall_reachesThePump() {
        val start = System.currentTimeMillis()
        // 30 minutes of steady history so the loop has a trend.
        backfill(start, minutes = 30, value = 140.0)
        // Rise: 150 -> 230 mg/dL over 12 minutes.
        for (i in 0 until RISE_MIN) {
            reading(150.0 + i * 7.0)
            waitAMinute()
        }
        val onRise = emulator.state()
        val smbOnRise = newBoluses(start)
        val givenOnRise = (onRise.tempBasal?.absolute ?: 0.0) > PROFILE_BASAL + 0.01 || smbOnRise.isNotEmpty()
        assertWithMessage("on a fast rise the loop gave more insulin and the pump has it (temp ${onRise.tempBasal}, boluses $smbOnRise)")
            .that(givenOnRise).isTrue()
        assertTempBasalSame("after the rise")

        val bolusesBeforeFall = smbOnRise.size
        // Fall: 140 -> 70 mg/dL over 12 minutes.
        for (i in 0 until FALL_MIN) {
            reading(140.0 - i * 6.0)
            waitAMinute()
        }
        val onFall = emulator.state()
        assertWithMessage("on a fast fall the pump runs a temp basal under the profile (${onFall.tempBasal})")
            .that((onFall.tempBasal?.absolute ?: PROFILE_BASAL) < PROFILE_BASAL - 0.01).isTrue()
        assertWithMessage("no SMB on the fall (${newBoluses(start)})").that(newBoluses(start).size).isEqualTo(bolusesBeforeFall)
        assertTempBasalSame("after the fall")
        assertWithMessage("the loop ran").that(loop.lastRun).isNotNull()
        // The insulin the loop counts is the insulin the pump gave: every SMB is in AAPS, once.
        val pumpGave = newBoluses(start).sum()
        awaitTrue(SMB_RECORD_TIMEOUT) { abs(aapsBoluses(start).sum() - pumpGave) < 1e-6 }
        assertWithMessage("SMBs AAPS has on record ${aapsBoluses(start)}, the pump gave ${newBoluses(start)}")
            .that(aapsBoluses(start).sum()).isWithin(1e-6).of(pumpGave)
    }

    /** The boluses the pump gave since [sinceMs], oldest first, U: the journal also holds the old seeded ones. */
    private fun newBoluses(sinceMs: Long): List<Double> =
        synchronized(pump.bolusLog) { pump.bolusLog.filter { it.atMs >= sinceMs - 2 * 60_000L }.reversed().map { it.delivered * RAW_UNIT } }

    /** The boluses AAPS has on record since [sinceMs], U. */
    private fun aapsBoluses(sinceMs: Long): List<Double> =
        runBlocking { persistenceLayer.getBolusesFromTime(sinceMs - 2 * 60_000L, ascending = true) }.filter { it.amount > 0.0 }.map { it.amount }

    /**
     * The learner on 17 days of a virtual child with too many lows in the last 3 days: the percentage card proposes
     * 100 -> 90 %, "Внести" makes a 90 % switch and the pump gets the 90 % basal. The basal card then does not
     * propose (one change at a time: the last change is still judged).
     */
    @Test
    fun learner_percentageCard_reachesThePump() {
        val now = System.currentTimeMillis()
        seedHistory(now, days = 17, lowsLast3Days = true)

        runBlocking { learner.runPctCardNowForBench() }
        val proposal = learner.status.value.pctProposal
        assertWithMessage("the percentage card proposes on many lows (${learner.status.value.lastPct})").that(proposal).isNotNull()
        assertWithMessage("it proposes 10 % less").that(proposal!!.to).isEqualTo(90)

        learner.acceptPct()
        assertWithMessage("a 90 % profile runs").that(awaitTrue(SWITCH_TIMEOUT) { runBlocking { profileFunction.getRequestedProfile() }?.percentage == 90 }).isTrue()
        assertWithMessage("the pump got the 90 % basal (${emulator.state().basal})")
            .that(awaitTrue(SWITCH_TIMEOUT) { emulator.state().basal?.all { abs(it - PROFILE_BASAL * 0.9) < 0.02 } == true }).isTrue()

        runBlocking { learner.runBasalCardNowForBench() }
        assertWithMessage("no basal proposal while the last change is judged").that(learner.status.value.proposal).isNull()
    }

    // The virtual child: history and readings

    /** [days] of 5 minute readings: mostly 100-160, meals with a rise, and many lows in the last 3 days if asked. */
    private fun seedHistory(now: Long, days: Int, lowsLast3Days: Boolean) {
        val step = 5 * 60_000L
        val n = days * 288
        val values = ArrayList<GV>(n)
        for (k in 0 until n) {
            val t = now - (n - k) * step
            val minuteOfDay = ((t / 60_000L) % 1440).toInt()
            var v = 130.0 + 25.0 * Math.sin(minuteOfDay / 1440.0 * 2 * Math.PI)
            // A meal rise at 8, 13 and 18 o'clock (UTC on the emulator).
            for (meal in intArrayOf(8 * 60, 13 * 60, 18 * 60)) {
                val m = minuteOfDay - meal
                if (m in 0..180) v += 60.0 * Math.sin(m / 180.0 * Math.PI)
            }
            val lastThree = k >= n - 3 * 288
            // About 6 % of the last 3 days low: 17 readings a day around 3 o'clock.
            if (lowsLast3Days && lastThree && minuteOfDay in (3 * 60) until (3 * 60 + 85)) v = 62.0
            values += GV(timestamp = t, value = v, raw = null, noise = null, trendArrow = TrendArrow.FLAT, sourceSensor = SourceSensor.RANDOM)
        }
        runBlocking { persistenceLayer.insertCgmSourceData(Sources.Random, values, emptyList(), null) }
    }

    private fun backfill(now: Long, minutes: Int, value: Double) {
        val values = (minutes downTo 1).map { m ->
            GV(timestamp = now - m * 60_000L, value = value, raw = null, noise = null, trendArrow = TrendArrow.FLAT, sourceSensor = SourceSensor.RANDOM)
        }
        runBlocking { persistenceLayer.insertCgmSourceData(Sources.Random, values, emptyList(), null) }
    }

    /** One reading now: AAPS calculates and the loop runs on it (loop interval 1 min). */
    private fun reading(value: Double) {
        val gv = GV(timestamp = System.currentTimeMillis(), value = value, raw = null, noise = null, trendArrow = TrendArrow.NONE, sourceSensor = SourceSensor.RANDOM)
        runBlocking { persistenceLayer.insertCgmSourceData(Sources.Random, listOf(gv), emptyList(), null) }
    }

    private fun waitAMinute() = SystemClock.sleep(60_000L)

    // Checks

    private fun assertTempBasalSame(where: String) {
        val agree = awaitTrue(RECORD_TIMEOUT) {
            val onPump = emulator.state().tempBasal?.absolute
            val inAaps = runBlocking { persistenceLayer.getTemporaryBasalActiveAt(System.currentTimeMillis()) }?.rate
            if (onPump == null || inAaps == null) onPump == inAaps else abs(onPump - inAaps) < 0.01
        }
        assertWithMessage("AAPS and the pump agree on the temp basal $where").that(agree).isTrue()
    }

    // Set up

    private fun seedProfile() {
        val day = T.hours(24).msecs()
        // Made-up child values (mg/dL): basal 0.4 U/h, ISF 150, IC 25, target 108.
        val profile = profileRepository.newDraft().copy(
            mgdl = true,
            ic = listOf(Block(day, 25.0)),
            isf = listOf(Block(day, 150.0)),
            basal = listOf(Block(day, PROFILE_BASAL)),
            target = listOf(TargetBlock(day, 108.0, 108.0))
        )
        runBlocking { profileRepository.add(profile) }.getOrThrow()
    }

    private fun activateProfile() {
        val store = checkNotNull(awaitNotNull(PROFILE_STORE_TIMEOUT) { profileRepository.profile.value?.takeIf { it.getSpecificProfile(PROFILE_NAME) != null } }) {
            "The profile store never published '$PROFILE_NAME'"
        }
        val switch = runBlocking {
            profileFunction.createProfileSwitch(
                profileStore = store, profileName = PROFILE_NAME, durationInMinutes = 0, percentage = 100,
                timeShiftInHours = 0, timestamp = dateUtil.now(), action = Action.PROFILE_SWITCH,
                source = Sources.Aaps, listValues = emptyList(), iCfg = insulinManager.insulins.first()
            )
        }
        checkNotNull(switch) { "Could not activate the profile" }
    }

    private fun status(reason: String): PumpEnactResult = runBlocking { withTimeout(STEP_TIMEOUT) { commandQueue.readStatus(reason) } }

    private fun <T> awaitNotNull(timeoutMs: Long, supplier: () -> T?): T? {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < end) {
            runCatching(supplier).getOrNull()?.let { return it }
            SystemClock.sleep(POLL_MS)
        }
        return null
    }

    private fun awaitTrue(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < end) {
            if (runCatching(condition).getOrDefault(false)) return true
            SystemClock.sleep(POLL_MS)
        }
        return false
    }

    private fun clearAllSharedPrefs() {
        val ctx = instrumentation.targetContext
        File(ctx.applicationInfo.dataDir, "shared_prefs").listFiles()?.forEach { f ->
            if (f.name.endsWith(".xml"))
                ctx.getSharedPreferences(f.name.removeSuffix(".xml"), Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    companion object {

        private const val PKG = "info.nightscout.androidaps"
        private const val SERIAL = "12345678"
        private const val ADDRESS = "11:22:33:AA:BB:CC"
        private const val PROFILE_NAME = "LocalProfile1"
        private const val PROFILE_BASAL = 0.4
        private const val PROFILE_STORE_TIMEOUT = 20_000L
        private const val INIT_TIMEOUT = 60_000L
        private const val STEP_TIMEOUT = 240_000L
        private const val RECORD_TIMEOUT = 60_000L

        /** v4 writes an SMB from the journal right after it, or at the next heartbeat (2 min) and the reads after it. */
        private const val SMB_RECORD_TIMEOUT = 300_000L

        /** The pump's dose step, U. */
        private const val RAW_UNIT = 0.025

        /** The file of v4's own database, see `ApexDatabase.build`. */
        private const val APEX_DATABASE = "apex_database.db"
        private const val SWITCH_TIMEOUT = 120_000L
        private const val POLL_MS = 250L

        /** Up to a heartbeat (2 min) and its read. */
        private const val REGISTER_TIMEOUT = 200_000L
        private const val RISE_MIN = 12
        private const val FALL_MIN = 12
    }
}
