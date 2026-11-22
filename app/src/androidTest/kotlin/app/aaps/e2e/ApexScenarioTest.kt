package app.aaps.e2e

import android.Manifest
import android.content.Context
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import app.aaps.core.data.model.BS
import app.aaps.core.data.model.data.Block
import app.aaps.core.data.model.data.TargetBlock
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.time.T
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.insulin.InsulinManager
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileRepository
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.BooleanComposedKey
import app.aaps.core.keys.BooleanNonKey
import app.aaps.core.keys.LongNonKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.di.Atc3Emulation
import app.aaps.di.EmulatedApexBLE
import app.aaps.di.ResetGraphRule
import app.aaps.di.testGraphs
import app.aaps.implementation.plugin.PluginStore
import app.aaps.pump.apex.ApexPumpPlugin
import app.aaps.pump.apex.keys.ApexIntKey
import app.aaps.pump.apex.keys.ApexStringKey
import app.aaps.pump.apex.link.ApexRadioSwap
import app.aaps.pump.atc3.emulator.Atc3PumpEmulator
import app.aaps.pump.atc3.emulator.VirtualAtc3Pump
import app.aaps.pump.atc3.emulator.control.EmulatedCommand
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
 * The Apex v4 driver (the driver of our build) against our software pump, through the real command queue and the real
 * (in memory) database of AAPS: the faults of our old ATC3 scenarios, moved to v4. The rule they check: **the insulin
 * AAPS has on record is the insulin the pump gave**, also when a command or its answer is lost or the link drops.
 * Two of them check our own fix in v4: a lost answer to a basal profile write is read back, not a failure.
 */
@RunWith(AndroidJUnit4::class)
class ApexScenarioTest {

    @get:Rule val rules: RuleChain = RuleChain.outerRule(RetryRule()).around(ResetGraphRule())

    private val preferences: Preferences get() = testGraphs.preferences
    private val pluginStore: PluginStore get() = testGraphs.pluginStore
    private val commandQueue: CommandQueue get() = testGraphs.commandQueue
    private val pluginList: List<PluginBase> get() = testGraphs.allPlugins(testGraphs.aapsLogger)
    private val pumpPlugin: ApexPumpPlugin get() = pluginList.filterIsInstance<ApexPumpPlugin>().single()
    private val configBuilder: ConfigBuilder get() = testGraphs.configBuilder
    private val config: Config get() = testGraphs.config
    private val profileFunction: ProfileFunction get() = testGraphs.profileFunction
    private val profileRepository: ProfileRepository get() = testGraphs.profileRepository
    private val insulinManager: InsulinManager get() = testGraphs.insulinManager
    private val dateUtil: DateUtil get() = testGraphs.dateUtil
    private val persistenceLayer: PersistenceLayer get() = testGraphs.persistenceLayer

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    private lateinit var pump: VirtualAtc3Pump
    private lateinit var emulator: Atc3PumpEmulator
    private var startedAt = 0L

    @Before
    fun setUp() {
        clearAllSharedPrefs()
        runBlocking { persistenceLayer.clearDatabases() }
        pump = VirtualAtc3Pump(SERIAL) { System.currentTimeMillis() }
        pump.seedBolusHistory(System.currentTimeMillis())
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
        // The shortest heartbeat v4 allows: its accounting runs at the heartbeat.
        preferences.put(ApexIntKey.HeartbeatPeriod, 2)
        preferences.put(BooleanComposedKey.ConfigBuilderEnabled, "PUMP_ApexPumpPlugin", value = true)
        preferences.put(BooleanComposedKey.ConfigBuilderEnabled, "PUMP_VirtualPumpPlugin", value = false)

        pluginStore.plugins = pluginList
        configBuilder.initialize()
        config.initCompleted()
        seedProfile()
        activateProfile()

        status("scenario start")
        assertWithMessage("the driver is initialized").that(awaitTrue(INIT_TIMEOUT) { pumpPlugin.isInitialized() }).isTrue()
        assertWithMessage("a profile runs").that(awaitTrue(INIT_TIMEOUT) { runBlocking { profileFunction.getProfile() } != null }).isTrue()
        val ready = maxOf(System.currentTimeMillis(), preferences.get(LongNonKey.ActivePumpChangeTimestamp))
        val wait = (ready / 60_000L + 1) * 60_000L + 2_000L - System.currentTimeMillis()
        if (wait > 0) SystemClock.sleep(wait)
        startedAt = System.currentTimeMillis()
    }

    @After
    fun tearDown() {
        runCatching { commandQueue.clear() }
        runCatching { WorkManager.getInstance(instrumentation.targetContext).cancelAllWork() }
        runCatching { pumpPlugin.disconnect("test end") }
        runCatching { pumpPlugin.setPluginEnabledBlocking(PluginType.PUMP, false) }
        Atc3Emulation.emulator = null
        ApexRadioSwap.make = null
        runCatching { pump.shutdown() }
        clearAllSharedPrefs()
    }

    // Boluses

    @Test
    fun bolus_isDelivered_andRecordedOnce() {
        val result = bolus(1.0)
        assertWithMessage("a bolus of 1 U is delivered").that(result.success).isTrue()
        assertRecordIsWhatThePumpGave()
    }

    @Test
    fun oddBoluses_reachThePumpAsAsked() {
        for (units in listOf(1.15, 0.35, 0.05)) {
            val result = bolus(units)
            assertWithMessage("a bolus of $units U is delivered").that(result.success).isTrue()
        }
        assertWithMessage("the pump gave ${given()}").that(given()).hasSize(3)
        assertRecordIsWhatThePumpGave()
    }

    @Test
    fun bolusAnswerLost_butDelivered_isRecordedOnce() {
        assertWithMessage("the fault is set").that(emulator.loseAnswer(EmulatedCommand.BOLUS_START)).isTrue()
        bolus(1.0)
        assertWithMessage("the pump gave the bolus").that(given()).hasSize(1)
        assertRecordIsWhatThePumpGave()
    }

    @Test
    fun bolusCommandLost_isNotReportedAsGiven_andNothingIsRecorded() {
        assertWithMessage("the fault is set").that(emulator.loseCommand(EmulatedCommand.BOLUS_START)).isTrue()
        val result = bolus(1.0)
        assertWithMessage("the pump gave nothing").that(given()).isEmpty()
        assertWithMessage("a bolus that never reached the pump is not a success").that(result.success).isFalse()
        assertRecordIsWhatThePumpGave()
    }

    @Test
    fun bolusStoppedHalfWay_recordIsWhatThePumpGave() {
        assertWithMessage("the bolus stops half way").that(emulator.stopNextBolusAt(0.5)).isTrue()
        bolus(1.0)
        assertRecordIsWhatThePumpGave()
    }

    @Test
    fun linkLostDuringBolus_recordIsWhatThePumpGave() {
        val breaker = Thread {
            awaitTrue(STEP_TIMEOUT) { emulator.state().bolusRunning }
            emulator.breakLink()
        }.apply { start() }
        bolus(2.0)
        breaker.join(STEP_TIMEOUT)
        assertRecordIsWhatThePumpGave()
    }

    // Temporary basal

    @Test
    fun tempBasal_reachesThePump_andAapsAgrees() {
        tempBasal(0.8)
        assertTempBasalSame("after a temp basal")
    }

    @Test
    fun tempBasalAnswerLost_aapsAndPumpAgree() {
        assertWithMessage("the fault is set").that(emulator.loseAnswer(EmulatedCommand.TEMP_BASAL_START)).isTrue()
        tempBasal(0.8)
        assertTempBasalSame("after a lost answer")
    }

    @Test
    fun tempBasalCommandLost_aapsAndPumpAgree() {
        assertWithMessage("the fault is set").that(emulator.loseCommand(EmulatedCommand.TEMP_BASAL_START)).isTrue()
        tempBasal(0.8)
        assertTempBasalSame("after a lost command")
    }

    // Basal profile: our fix in v4

    @Test
    fun basalProfileAnswerLost_isReadBack_notAFailure() {
        assertWithMessage("the fault is set").that(emulator.loseAnswer(EmulatedCommand.BASAL_PROFILE)).isTrue()
        switchProfile(150)
        assertWithMessage("the pump runs the 150 % basal (${emulator.state().basal})")
            .that(awaitTrue(RECORD_TIMEOUT) { emulator.state().basal?.all { abs(it - PROFILE_BASAL * 1.5) < 0.02 } == true }).isTrue()
        // The running profile of AAPS has the percent in its values already: its basal is what to compare.
        assertWithMessage("AAPS runs the 150 % basal too")
            .that(awaitTrue(RECORD_TIMEOUT) { abs(profile().getBasal() - PROFILE_BASAL * 1.5) < 0.02 }).isTrue()
    }

    // Helpers

    private fun bolus(units: Double): PumpEnactResult {
        awaitTrue(RECORD_TIMEOUT) { commandQueue.performing() == null }
        return runBlocking { withTimeout(STEP_TIMEOUT) { commandQueue.bolus(DetailedBolusInfo().also { it.insulin = units; it.bolusType = BS.Type.NORMAL }) } }
    }

    private fun tempBasal(rate: Double) {
        runBlocking { withTimeout(STEP_TIMEOUT) { commandQueue.tempBasalAbsolute(rate, 30, enforceNew = true, profile(), PumpSync.TemporaryBasalType.NORMAL) } }
    }

    private fun switchProfile(percentage: Int) {
        val store = checkNotNull(profileRepository.profile.value) { "No profile store" }
        runBlocking {
            profileFunction.createProfileSwitch(
                profileStore = store, profileName = PROFILE_NAME, durationInMinutes = 0, percentage = percentage,
                timeShiftInHours = 0, timestamp = dateUtil.now(), action = Action.PROFILE_SWITCH,
                source = Sources.Aaps, listValues = emptyList(), iCfg = insulinManager.insulins.first()
            )
        }
    }

    private fun profile(): Profile = checkNotNull(runBlocking { profileFunction.getProfile() }) { "No profile runs" }

    /**
     * The boluses the pump gave in this scenario, oldest first, U: the journal is full of old ones (seeded hours and
     * days back) and drops its oldest on each new bolus, so the new ones are told by their time, not by their count.
     */
    private fun given(): List<Double> =
        synchronized(pump.bolusLog) { pump.bolusLog.filter { it.atMs >= startedAt - 2 * 60_000L }.reversed().map { it.delivered * RAW_UNIT } }

    private fun aapsBoluses(): List<Double> =
        runBlocking { persistenceLayer.getBolusesFromTime(startedAt - 2 * 60_000L, ascending = true) }.filter { it.amount > 0.0 }.map { it.amount }

    /** v4 writes a bolus row from the pump's journal record, read after the bolus or at the next heartbeat. */
    private fun assertRecordIsWhatThePumpGave() {
        val pumpGave = given().sum()
        awaitTrue(RECORD_TIMEOUT) { abs(aapsBoluses().sum() - pumpGave) < EPS }
        assertWithMessage("insulin AAPS has on record ${aapsBoluses()}, the pump gave ${given()}")
            .that(aapsBoluses().sum()).isWithin(EPS).of(pumpGave)
    }

    private fun assertTempBasalSame(where: String) {
        val agree = awaitTrue(RECORD_TIMEOUT) {
            val onPump = emulator.state().tempBasal?.absolute
            val inAaps = runBlocking { persistenceLayer.getTemporaryBasalActiveAt(System.currentTimeMillis()) }?.rate
            if (onPump == null || inAaps == null) onPump == inAaps else abs(onPump - inAaps) < 0.01
        }
        assertWithMessage("AAPS and the pump agree on the temp basal $where").that(agree).isTrue()
    }

    private fun seedProfile() {
        val day = T.hours(24).msecs()
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
        private const val INIT_TIMEOUT = 180_000L
        private const val STEP_TIMEOUT = 240_000L

        /** Up to two heartbeats of 2 min and the reads after them. */
        private const val RECORD_TIMEOUT = 300_000L
        private const val POLL_MS = 250L
        private const val EPS = 1e-6

        /** The pump's dose step, U. */
        private const val RAW_UNIT = 0.025
    }
}
