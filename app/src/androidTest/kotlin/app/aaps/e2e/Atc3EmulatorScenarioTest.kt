package app.aaps.e2e

import android.Manifest
import android.content.Context
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import app.aaps.core.data.model.BS
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.insulin.InsulinManager
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileRepository
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.emulator.EmulatedCommand
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.BooleanComposedKey
import app.aaps.core.keys.BooleanNonKey
import app.aaps.core.keys.LongNonKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.di.Atc3Emulation
import app.aaps.implementation.plugin.PluginStore
import app.aaps.plugins.aps.utils.StaticInjector
import app.aaps.pump.atc3.Atc3PumpPlugin
import app.aaps.pump.atc3.emulator.Atc3PumpEmulator
import app.aaps.pump.atc3.emulator.VirtualAtc3Pump
import app.aaps.pump.atc3.keys.Atc3StringKey
import app.aaps.pump.atc3.link.Atc3Pairing
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import app.aaps.core.interfaces.pump.emulator.EmulatedAlarm
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import java.io.File
import javax.inject.Inject
import kotlin.math.abs

/**
 * The ATC3 driver against a software pump, through the real command queue and the real (in memory)
 * database of AAPS. Only the radio is replaced, see `EmulatedAtc3BLE`.
 *
 * The cases follow the pump scenarios of the official AAPS (`PumpScenario`), written for this
 * snapshot's Hilt test harness. The rule most of them check: **the insulin AAPS has on record is the
 * insulin the pump gave**, also when a command or its answer is lost, the link drops or a bolus stops.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class Atc3EmulatorScenarioTest {

    val hiltRule = HiltAndroidRule(this)

    @get:Rule val rules: RuleChain = RuleChain.outerRule(RetryRule()).around(hiltRule)

    @Inject lateinit var preferences: Preferences
    @Inject lateinit var pluginStore: PluginStore
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var atc3Plugin: Atc3PumpPlugin
    @Inject lateinit var pluginList: List<@JvmSuppressWildcards PluginBase>
    @Inject lateinit var configBuilder: ConfigBuilder
    @Inject lateinit var config: Config
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var profileRepository: ProfileRepository
    @Inject lateinit var insulinManager: InsulinManager
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var persistenceLayer: PersistenceLayer
    @Inject lateinit var pairing: Atc3Pairing
    @Inject lateinit var aapsLogger: AAPSLogger
    @Suppress("unused") @Inject lateinit var staticInjector: StaticInjector

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    private lateinit var pump: VirtualAtc3Pump
    private lateinit var emulator: Atc3PumpEmulator
    private var startedAt = 0L

    @Before
    fun setUp() {
        clearAllSharedPrefs()
        pump = VirtualAtc3Pump(SERIAL) { System.currentTimeMillis() }
        emulator = Atc3PumpEmulator(pump)
        // Before the graph is made: the driver's radio is made once with it.
        Atc3Emulation.emulator = emulator
        hiltRule.inject()
        instrumentation.uiAutomation.grantRuntimePermission(PKG, Manifest.permission.BLUETOOTH_CONNECT)
        runCatching { instrumentation.uiAutomation.grantRuntimePermission(PKG, Manifest.permission.BLUETOOTH_SCAN) }
        runCatching { instrumentation.uiAutomation.grantRuntimePermission(PKG, "android.permission.POST_NOTIFICATIONS") }

        preferences.put(BooleanNonKey.GeneralSetupWizardProcessed, true)
        preferences.put(StringKey.GeneralUnits, "mg/dl")
        preferences.put(Atc3StringKey.Atc3SerialNumber, SERIAL)
        preferences.put(Atc3StringKey.Atc3BtPassword, pump.btPassword)
        preferences.put(Atc3StringKey.Atc3Address, ADDRESS)
        preferences.put(BooleanComposedKey.ConfigBuilderEnabled, "PUMP_Atc3PumpPlugin", value = true)
        preferences.put(BooleanComposedKey.ConfigBuilderEnabled, "PUMP_VirtualPumpPlugin", value = false)

        pluginStore.plugins = pluginList
        configBuilder.initialize()
        config.initCompleted()
        seedLocalProfile()
        activateSeededProfile()

        status("scenario start")
        assertWithMessage("the driver is initialized after the first status read")
            .that(awaitTrue(INIT_TIMEOUT) { atc3Plugin.isInitialized() }).isTrue()
        startInAFreshMinute()
        startedAt = System.currentTimeMillis()
    }

    /**
     * On a phone the pump was paired and the profile ran long before any bolus. Here both happen a few
     * seconds before the scenario, and the pump logs to the minute: a record of that same minute would be
     * older than the pump's registration in AAPS (which refuses it) or than the running profile (which
     * refuses a bolus). So the scenario starts in the next minute after both.
     */
    private fun startInAFreshMinute() {
        assertWithMessage("a profile runs").that(awaitTrue(INIT_TIMEOUT) { runBlocking { profileFunction.getProfile() } != null }).isTrue()
        val ready = maxOf(System.currentTimeMillis(), preferences.get(LongNonKey.ActivePumpChangeTimestamp))
        val nextMinute = (ready / 60_000L + 1) * 60_000L + 2_000L
        val wait = nextMinute - System.currentTimeMillis()
        if (wait > 0) SystemClock.sleep(wait)
    }

    @After
    fun tearDown() {
        runCatching { commandQueue.clear() }
        runCatching { WorkManager.getInstance(instrumentation.targetContext).cancelAllWork() }
        runCatching { atc3Plugin.disconnect("test end") }
        runCatching { atc3Plugin.setPluginEnabledBlocking(PluginType.PUMP, false) }
        Atc3Emulation.emulator = null
        runCatching { pump.shutdown() }
        clearAllSharedPrefs()
    }

    // Reading the pump

    @Test
    fun statusRead_fillsThePumpState() {
        assertThat(atc3Plugin.isInitialized()).isTrue()
        assertThat(atc3Plugin.reservoirLevel.value.cU).isWithin(0.5).of(200.0)
        assertThat(emulator.state().commands).isGreaterThan(0)
    }

    // Boluses

    @Test
    fun bolus_isDeliveredAndWrittenOnce() {
        val result = bolus(1.0)

        assertThat(result.success).isTrue()
        assertThat(result.bolusDelivered).isWithin(EPS).of(1.0)
        assertThat(emulator.state().boluses).containsExactly(1.0)
        assertRecordIsWhatThePumpGave()
        assertThat(aapsBoluses()).hasSize(1)
    }

    @Test
    fun bolusWhoseAnswerIsLost_isCountedOnce() {
        assertThat(emulator.loseAnswer(EmulatedCommand.BOLUS_START)).isTrue()

        bolus(1.0)
        status("after a lost answer")

        assertThat(emulator.state().boluses).containsExactly(1.0)
        assertRecordIsWhatThePumpGave()
        assertThat(aapsBoluses()).hasSize(1)
    }

    @Test
    fun bolusCommandThatIsLost_isNotCountedAsGiven() {
        assertThat(emulator.loseCommand(EmulatedCommand.BOLUS_START)).isTrue()

        val result = bolus(1.0)
        status("after a lost command")

        // Whatever the driver did about the silence, the record and the answer must be what the pump gave.
        val given = emulator.state().boluses.sum()
        assertThat(result.bolusDelivered).isWithin(EPS).of(given)
        assertRecordIsWhatThePumpGave()
    }

    @Test
    fun bolusThatStopsAtAnOcclusion_isCountedAsWhatWentIn() {
        assertThat(emulator.stopNextBolusAt(0.5)).isTrue()

        val result = bolus(2.0)
        status("after an occlusion")

        val given = emulator.state().boluses.sum()
        assertThat(given).isLessThan(2.0)
        assertWithMessage("a bolus the pump cut short is not reported as given in full").that(result.bolusDelivered).isLessThan(2.0)
        assertRecordIsWhatThePumpGave()
    }

    @Test
    fun linkLostDuringABolus_isCountedAsWhatWentIn() {
        pump.dropLinkAtBolusRaw = 10 // 0.25 U

        val result = bolus(1.0)
        status("after a lost link")

        val given = emulator.state().boluses.sum()
        assertThat(given).isLessThan(1.0)
        assertWithMessage("a bolus cut by a lost link is not reported as given in full").that(result.bolusDelivered).isLessThan(1.0)
        assertRecordIsWhatThePumpGave()
    }

    @Test
    fun bolusGivenOnThePump_reachesAaps() {
        assertThat(emulator.bolusOnPump(0.5)).isTrue()

        status("after a bolus on the pump")

        assertRecordIsWhatThePumpGave()
        assertThat(aapsBoluses()).hasSize(1)
    }

    /**
     * A dual bolus given on the pump's keypad (allowed there, then the setting turned off): while it runs the loop
     * waits, and when it ends AAPS has the part given at once as a bolus and the rest as an extended bolus, the way
     * other drivers write it. The pump keeps no length, so the extended row ends when its record is read.
     */
    @Test
    fun dualBolusGivenOnThePump_reachesAapsAsBolusAndExtended() {
        val startedOnPump = System.currentTimeMillis()
        assertWithMessage("the loop may run before").that(loopAllowed()).isTrue()

        pump.extendedRunningRaw = 4
        status("an extended bolus runs on the pump")
        assertWithMessage("the loop waits while an extended bolus runs").that(awaitTrue(RECORD_TIMEOUT) { !loopAllowed() }).isTrue()

        pump.extendedOnKeypadEnded(nowUnits = 1.0, extendedUnits = 0.5, startedAtMs = startedOnPump)
        status("after a dual bolus on the pump")

        assertRecordIsWhatThePumpGave()
        assertThat(aapsBoluses()).containsExactly(1.0)
        assertWithMessage("the extended part is an extended bolus").that(awaitTrue(RECORD_TIMEOUT) { aapsExtended().isNotEmpty() }).isTrue()
        val extended = aapsExtended().single()
        assertThat(extended.amount).isWithin(EPS).of(0.5)
        assertThat(extended.timestamp).isEqualTo(startedOnPump - startedOnPump % 60_000L)
        assertThat(extended.end).isAtMost(System.currentTimeMillis())
        assertWithMessage("the loop runs again once the record is in").that(loopAllowed()).isTrue()

        status("read again")
        assertThat(aapsExtended()).hasSize(1)
        assertThat(aapsBoluses()).containsExactly(1.0)
    }

    @Test
    fun pumpOutOfRange_noBolusIsCounted_andThePumpIsBackAfter() {
        runBlocking { withTimeout(STEP_TIMEOUT) { runCatching { atc3Plugin.disconnect("going out of range") } } }
        assertThat(emulator.setOutOfRange(true)).isTrue()

        val result = bolus(1.0)

        assertThat(result.success).isFalse()
        assertThat(emulator.state().boluses).isEmpty()
        assertRecordIsWhatThePumpGave()

        emulator.setOutOfRange(false)
        status("back in range")
        assertThat(awaitTrue(STEP_TIMEOUT) { emulator.state().linkUp || atc3Plugin.isConnected() }).isTrue()
        assertRecordIsWhatThePumpGave()
    }

    // Temporary basal

    @Test
    fun tempBasal_isSetOnThePumpAndInAaps_andCancelledInBoth() {
        val set = runBlocking {
            withTimeout(STEP_TIMEOUT) { commandQueue.tempBasalAbsolute(TBR_RATE, TBR_MINUTES, enforceNew = true, profile(), PumpSync.TemporaryBasalType.NORMAL) }
        }

        assertThat(set.success).isTrue()
        assertThat(emulator.state().tempBasal?.absolute ?: -1.0).isWithin(EPS).of(TBR_RATE)
        assertThat(awaitTrue(RECORD_TIMEOUT) { aapsTempBasalRate() != null }).isTrue()
        assertThat(aapsTempBasalRate() ?: -1.0).isWithin(EPS).of(TBR_RATE)

        val cancelled = runBlocking { withTimeout(STEP_TIMEOUT) { commandQueue.cancelTempBasal(enforceNew = true) } }

        assertThat(cancelled.success).isTrue()
        assertThat(emulator.state().tempBasal).isNull()
        assertThat(awaitTrue(RECORD_TIMEOUT) { aapsTempBasalRate() == null }).isTrue()
    }

    @Test
    fun tempBasalWhoseAnswerIsLost_aapsShowsWhatThePumpRuns() {
        assertThat(emulator.loseAnswer(EmulatedCommand.TEMP_BASAL_START)).isTrue()

        runBlocking {
            withTimeout(STEP_TIMEOUT) { commandQueue.tempBasalAbsolute(TBR_RATE, TBR_MINUTES, enforceNew = true, profile(), PumpSync.TemporaryBasalType.NORMAL) }
        }
        status("after a lost answer")

        val onPump = emulator.state().tempBasal?.absolute
        assertWithMessage("the pump ran the command whose answer was lost").that(onPump ?: -1.0).isWithin(EPS).of(TBR_RATE)
        assertWithMessage("AAPS shows the temp basal the pump runs")
            .that(awaitTrue(RECORD_TIMEOUT) { aapsTempBasalRate()?.let { abs(it - TBR_RATE) < EPS } == true }).isTrue()
    }

    @Test
    fun tempBasalCommandThatIsLost_aapsShowsNoTempBasal() {
        assertThat(emulator.loseCommand(EmulatedCommand.TEMP_BASAL_START)).isTrue()

        runBlocking {
            withTimeout(STEP_TIMEOUT) { commandQueue.tempBasalAbsolute(TBR_RATE, TBR_MINUTES, enforceNew = true, profile(), PumpSync.TemporaryBasalType.NORMAL) }
        }
        status("after a lost command")

        // The driver may send the command again: then both run it. What must not be is AAPS and the pump apart.
        val onPump = emulator.state().tempBasal?.absolute
        assertWithMessage("AAPS and the pump agree on the temp basal, the pump runs $onPump")
            .that(awaitTrue(RECORD_TIMEOUT) { sameRate(aapsTempBasalRate(), onPump) }).isTrue()
    }

    // Amounts (official AAPS acceptance S2): rounded to the pump's step, never cut. 1.15 / 0.025 is 45.999... in floating
    // point, and a cut gives 1.125 U.

    @Test
    fun oddBoluses_reachThePumpAsAsked_andAreRecordedAsGiven() {
        val asked = listOf(1.15, 0.35, 0.05)
        for (units in asked) {
            val result = bolus(units)
            assertWithMessage("a bolus of $units U is delivered").that(result.success).isTrue()
            assertWithMessage("the answer for $units U").that(result.bolusDelivered).isWithin(EPS).of(units)
        }
        val given = emulator.state().boluses
        assertWithMessage("the pump gave $given").that(given).hasSize(asked.size)
        // The pump counts in 0.05 U steps, so compare with a small tolerance, not exactly.
        asked.zip(given).forEach { (a, g) -> assertWithMessage("the pump gave $given").that(g).isWithin(EPS).of(a) }
        assertRecordIsWhatThePumpGave()
        assertThat(aapsBoluses()).hasSize(asked.size)
    }

    @Test
    fun oddTempBasals_reachThePumpAsAsked() {
        for (rate in listOf(0.35, 0.45, 0.05, 1.15)) {
            setTempBasal(rate)
            assertWithMessage("the pump runs $rate U/h").that(emulator.state().tempBasal?.absolute ?: -1.0).isWithin(EPS).of(rate)
            assertWithMessage("AAPS shows $rate U/h")
                .that(awaitTrue(RECORD_TIMEOUT) { aapsTempBasalRate()?.let { abs(it - rate) < EPS } == true }).isTrue()
        }
    }

    // Bolus stops (AIMI acceptance S4)

    @Test
    fun bolusStopWhoseAnswerIsLost_countsWhatThePumpGave() {
        assertThat(emulator.loseAnswer(EmulatedCommand.BOLUS_STOP)).isTrue()

        val result = bolusStoppedOnTheWay(2.0)
        status("after a stop whose answer was lost")

        val given = emulator.state().boluses.sum()
        assertWithMessage("the answer AAPS got is what the pump gave").that(result.bolusDelivered).isWithin(EPS).of(given)
        assertRecordIsWhatThePumpGave()
        assertThat(aapsBoluses()).hasSize(1)
    }

    @Test
    fun bolusStopThatIsLost_countsWhatThePumpGave() {
        assertThat(emulator.loseCommand(EmulatedCommand.BOLUS_STOP)).isTrue()

        val result = bolusStoppedOnTheWay(2.0)
        status("after a lost stop")

        // The stop never reached the pump, so it may have given it all (or the driver sent the stop again).
        val given = emulator.state().boluses.sum()
        assertWithMessage("the answer AAPS got is what the pump gave").that(result.bolusDelivered).isWithin(EPS).of(given)
        assertRecordIsWhatThePumpGave()
        assertThat(aapsBoluses()).hasSize(1)
    }

    // Temp basal cancels (S5) and the loop's steps (S3)

    @Test
    fun tempBasalCancelWhoseAnswerIsLost_isKnown() {
        setTempBasal(TBR_RATE)
        assertThat(emulator.loseAnswer(EmulatedCommand.TEMP_BASAL_STOP)).isTrue()

        runBlocking { withTimeout(STEP_TIMEOUT) { commandQueue.cancelTempBasal(enforceNew = true) } }
        status("after a cancel whose answer was lost")

        assertThat(emulator.state().tempBasal).isNull()
        assertWithMessage("AAPS knows the temp basal ended").that(awaitTrue(RECORD_TIMEOUT) { aapsTempBasalRate() == null }).isTrue()
    }

    @Test
    fun tempBasalCancelWhoseCheckIsLostToo_isKnownAfterAStatusRead() {
        setTempBasal(TBR_RATE)
        assertThat(emulator.loseAnswer(EmulatedCommand.TEMP_BASAL_STOP)).isTrue()
        // The driver reads the status before it cancels: that read goes through, the check after the cancel is lost.
        synchronized(pump.loseAnswers) {
            pump.loseAnswers.add(VirtualAtc3Pump.FrameFault(0xA3, 0x00, alsoCodes = setOf(0x0A, 0x0C), skip = 1))
        }

        runBlocking { withTimeout(STEP_TIMEOUT) { commandQueue.cancelTempBasal(enforceNew = true) } }
        status("first read after the cancel")
        status("second read after the cancel")

        assertThat(emulator.state().tempBasal).isNull()
        assertWithMessage("AAPS knows the temp basal ended").that(awaitTrue(RECORD_TIMEOUT) { aapsTempBasalRate() == null }).isTrue()
    }

    @Test
    fun tempBasalCancelWhoseFirstReadIsLost_isNotSent_andAapsAndThePumpAgree() {
        setTempBasal(TBR_RATE)
        assertThat(emulator.loseAnswer(EmulatedCommand.STATUS)).isTrue()

        runBlocking { withTimeout(STEP_TIMEOUT) { commandQueue.cancelTempBasal(enforceNew = true) } }
        status("after a cancel that could not read the pump first")

        // Without a status the driver does not cancel blind: the pump keeps it, and AAPS must say the same.
        val onPump = emulator.state().tempBasal?.absolute
        assertWithMessage("AAPS and the pump agree on the temp basal, the pump runs $onPump")
            .that(awaitTrue(RECORD_TIMEOUT) { sameRate(aapsTempBasalRate(), onPump) }).isTrue()
    }

    @Test
    fun tempBasalCancelThatIsLost_aapsAndThePumpAgree() {
        setTempBasal(TBR_RATE)
        assertThat(emulator.loseCommand(EmulatedCommand.TEMP_BASAL_STOP)).isTrue()

        runBlocking { withTimeout(STEP_TIMEOUT) { commandQueue.cancelTempBasal(enforceNew = true) } }
        status("after a lost cancel")

        // The pump may still run it (cancel lost) or not (the driver sent it again): AAPS must say the same.
        val onPump = emulator.state().tempBasal?.absolute
        assertWithMessage("AAPS and the pump agree on the temp basal, the pump runs $onPump")
            .that(awaitTrue(RECORD_TIMEOUT) { sameRate(aapsTempBasalRate(), onPump) }).isTrue()
    }

    @Test
    fun theLoopsTempBasals_twelveInARow_reachThePump() {
        val rates = listOf(0.0, 0.25, 0.75, 1.0, 0.5, 0.0, 1.25, 0.35, 0.6, 0.0, 0.9, 0.45)
        for ((i, rate) in rates.withIndex()) {
            setTempBasal(rate)
            assertWithMessage("step ${i + 1}: the pump runs $rate U/h")
                .that(emulator.state().tempBasal?.absolute ?: -1.0).isWithin(0.026).of(rate)
        }
        val last = rates.last()
        assertWithMessage("AAPS shows the last temp basal")
            .that(awaitTrue(RECORD_TIMEOUT) { aapsTempBasalRate()?.let { abs(it - last) < 0.026 } == true }).isTrue()
    }

    // On the pump (S8) and its alarms (S9)

    @Test
    fun suspendOnThePump_isRecorded_andTheResumeToo() {
        assertThat(emulator.suspendOnPump(true)).isTrue()
        status("after a stop on the pump")
        assertWithMessage("AAPS records the stop as a zero temp basal")
            .that(awaitTrue(RECORD_TIMEOUT) { aapsTempBasalRate()?.let { it < EPS } == true }).isTrue()

        assertThat(emulator.suspendOnPump(false)).isTrue()
        status("after a resume on the pump")
        assertWithMessage("AAPS ends the stop").that(awaitTrue(RECORD_TIMEOUT) { aapsTempBasalRate() == null }).isTrue()
    }

    @Test
    fun emptyReservoir_stopsThePump_andAapsKnows() {
        assertThat(emulator.raise(EmulatedAlarm.EMPTY_RESERVOIR)).isTrue()

        status("after an empty reservoir")

        assertWithMessage("the driver stops a pump that cannot deliver").that(awaitTrue(RECORD_TIMEOUT) { emulator.state().suspended }).isTrue()
        status("read the stop")
        assertWithMessage("AAPS records the stop").that(awaitTrue(RECORD_TIMEOUT) { aapsTempBasalRate()?.let { it < EPS } == true }).isTrue()
        assertThat(bolus(0.5).success).isFalse()
        assertThat(emulator.state().boluses).isEmpty()
    }

    // The driver itself (S7) and the profile (S6)

    @Test
    fun aDriverReload_keepsThePump() {
        val reservoir = atc3Plugin.reservoirLevel.value.cU

        runBlocking {
            atc3Plugin.onStop()
            atc3Plugin.onStart()
        }
        status("after a driver reload")

        assertThat(atc3Plugin.isInitialized()).isTrue()
        assertThat(atc3Plugin.reservoirLevel.value.cU).isWithin(0.5).of(reservoir)
        assertThat(bolus(0.5).success).isTrue()
        assertThat(emulator.state().boluses).containsExactly(0.5)
        assertRecordIsWhatThePumpGave()
        assertThat(aapsBoluses()).hasSize(1)
    }

    @Test
    fun aProfileWhoseAnswerIsLost_isKnown() {
        assertThat(emulator.loseAnswer(EmulatedCommand.BASAL_PROFILE)).isTrue()

        switchProfile(percentage = 150)
        status("after a profile whose answer was lost")

        // The seeded basal is 0.5 U/h, so 150 % is 0.75 U/h in every hour.
        assertWithMessage("the pump runs the new basal ${emulator.state().basal}")
            .that(awaitTrue(STEP_TIMEOUT) { emulator.state().basal?.all { abs(it - 0.75) < 0.026 } == true }).isTrue()
        assertWithMessage("AAPS runs the new profile")
            .that(awaitTrue(RECORD_TIMEOUT) { runBlocking { persistenceLayer.getEffectiveProfileSwitchActiveAt(dateUtil.now()) }?.originalPercentage == 150 })
            .isTrue()
    }

    // A whole working day in one run (official acceptance S1)

    @Test
    fun fullRun_aDayOfWorkInOneGo() {
        // Status
        assertThat(atc3Plugin.isInitialized()).isTrue()
        assertThat(atc3Plugin.reservoirLevel.value.cU).isWithin(0.5).of(200.0)

        // A bolus
        assertThat(bolus(1.0).success).isTrue()
        assertRecordIsWhatThePumpGave()

        // A bolus the user stops on the way
        val stopped = bolusStoppedOnTheWay(2.0)
        val given = emulator.state().boluses
        assertWithMessage("the stopped bolus gave less than asked: $given").that(given.last()).isLessThan(2.0)
        assertWithMessage("the answer is what the pump gave").that(stopped.bolusDelivered).isWithin(EPS).of(given.last())
        assertRecordIsWhatThePumpGave()

        // A temp basal and its cancel
        setTempBasal(TBR_RATE)
        assertThat(emulator.state().tempBasal?.absolute ?: -1.0).isWithin(EPS).of(TBR_RATE)
        assertThat(awaitTrue(RECORD_TIMEOUT) { sameRate(aapsTempBasalRate(), TBR_RATE) }).isTrue()
        assertThat(runBlocking { withTimeout(STEP_TIMEOUT) { commandQueue.cancelTempBasal(enforceNew = true) } }.success).isTrue()
        assertThat(emulator.state().tempBasal).isNull()
        assertThat(awaitTrue(RECORD_TIMEOUT) { aapsTempBasalRate() == null }).isTrue()

        // A profile switch reaches the pump
        switchProfile(percentage = 120)
        assertWithMessage("the pump runs 120 % (0.6 U/h): ${emulator.state().basal}")
            .that(awaitTrue(STEP_TIMEOUT) { emulator.state().basal?.all { abs(it - 0.6) < 0.026 } == true }).isTrue()

        // The pump's daily totals reach AAPS: today's boluses as the pump counts them. AAPS stamps a daily total at
        // midnight and takes no record older than the pump's registration, so the pump is taken as paired since
        // yesterday, as on a phone (here it was registered a minute ago).
        preferences.put(LongNonKey.ActivePumpChangeTimestamp, startedAt - 2 * 86_400_000L)
        assertThat(runBlocking { withTimeout(STEP_TIMEOUT) { commandQueue.loadTDDs() } }.success).isTrue()
        val pumpGave = emulator.state().boluses.sum()
        assertWithMessage("today's bolus total in AAPS is what the pump gave ($pumpGave U)").that(
            awaitTrue(RECORD_TIMEOUT) {
                runBlocking { persistenceLayer.getLastTotalDailyDoses(1, ascending = false) }.firstOrNull()?.let { abs(it.bolusAmount - pumpGave) < 0.026 } == true
            }
        ).isTrue()

        // Unpair: the pump is forgotten, and no insulin goes to it after that
        pairing.disconnect()
        assertThat(pairing.isPaired).isFalse()
        val bolusesBefore = emulator.state().boluses.size
        // AAPS itself answers for a pump that is not set up: done as nothing ("pump not configured"), nothing enacted.
        val afterUnpair = bolus(0.5)
        assertWithMessage("no bolus without a paired pump").that(afterUnpair.enacted).isFalse()
        assertThat(afterUnpair.bolusDelivered).isWithin(EPS).of(0.0)
        assertThat(emulator.state().boluses).hasSize(bolusesBefore)
        assertRecordIsWhatThePumpGave()
    }

    // Steps

    /** Starts a bolus through the queue and stops it while the pump gives it, as the user's Stop does. */
    private fun bolusStoppedOnTheWay(units: Double): PumpEnactResult {
        pump.bolusTickMs = 100
        val result = AtomicReference<PumpEnactResult?>(null)
        val thread = Thread { result.set(bolus(units)) }
        thread.start()
        assertWithMessage("the bolus runs").that(awaitTrue(RECORD_TIMEOUT) { emulator.state().bolusRunning }).isTrue()
        SystemClock.sleep(600)
        commandQueue.cancelAllBoluses(null)
        thread.join(STEP_TIMEOUT)
        return checkNotNull(result.get()) { "the bolus command did not end" }
    }

    private fun setTempBasal(rate: Double) {
        val result = runBlocking {
            withTimeout(STEP_TIMEOUT) { commandQueue.tempBasalAbsolute(rate, TBR_MINUTES, enforceNew = true, profile(), PumpSync.TemporaryBasalType.NORMAL) }
        }
        assertWithMessage("a temp basal of $rate U/h is set").that(result.success).isTrue()
    }

    private fun switchProfile(percentage: Int) {
        val store = checkNotNull(profileRepository.profile.value) { "No profile store" }
        val switch = runBlocking {
            profileFunction.createProfileSwitch(
                profileStore = store, profileName = PROFILE_NAME, durationInMinutes = 0, percentage = percentage,
                timeShiftInHours = 0, timestamp = dateUtil.now(), action = Action.PROFILE_SWITCH,
                source = Sources.Aaps, listValues = emptyList(), iCfg = insulinManager.insulins.first()
            )
        }
        checkNotNull(switch) { "Could not switch to $percentage %" }
    }


    private fun status(reason: String): PumpEnactResult = runBlocking { withTimeout(STEP_TIMEOUT) { commandQueue.readStatus(reason) } }

    private fun bolus(units: Double): PumpEnactResult =
        runBlocking { withTimeout(STEP_TIMEOUT) { commandQueue.bolus(DetailedBolusInfo().also { it.insulin = units; it.bolusType = BS.Type.NORMAL }) } }

    private fun profile(): Profile = checkNotNull(runBlocking { profileFunction.getProfile() }) { "No profile runs" }

    /** The boluses AAPS has on record since the scenario started, in units. */
    private fun aapsBoluses(): List<Double> =
        runBlocking { persistenceLayer.getBolusesFromTime(startedAt - 2 * 60_000L, ascending = true) }.filter { it.amount > 0.0 }.map { it.amount }

    /** The extended boluses AAPS has on record since the scenario started. */
    private fun aapsExtended() =
        runBlocking { persistenceLayer.getExtendedBolusesStartingFromTime(startedAt - 2 * 60_000L, ascending = true) }

    /** What the driver says of running the loop now. */
    private fun loopAllowed(): Boolean = atc3Plugin.isLoopInvocationAllowed(ConstraintObject(true, aapsLogger)).value()

    private fun aapsTempBasalRate(): Double? = runBlocking { persistenceLayer.getTemporaryBasalActiveAt(System.currentTimeMillis()) }?.rate

    private fun sameRate(a: Double?, b: Double?): Boolean = if (a == null || b == null) a == b else abs(a - b) < EPS

    /** The record is written a moment after the command returns, so it is waited for. */
    private fun assertRecordIsWhatThePumpGave() {
        val given = emulator.state().boluses.sum()
        awaitTrue(RECORD_TIMEOUT) { abs(aapsBoluses().sum() - given) < EPS }
        assertWithMessage("insulin AAPS has on record ${aapsBoluses()}, the pump gave ${emulator.state().boluses}")
            .that(aapsBoluses().sum()).isWithin(EPS).of(given)
    }

    // Set up, as in Atc3UiSmokeTest

    private fun seedLocalProfile() {
        val profile = profileRepository.newDraft().apply {
            mgdl = true
            ic = JSONArray(singleValue(10.0))
            isf = JSONArray(singleValue(50.0))
            basal = JSONArray(singleValue(0.5))
            targetLow = JSONArray(singleValue(100.0))
            targetHigh = JSONArray(singleValue(110.0))
        }
        runBlocking { profileRepository.add(profile) }.getOrThrow()
    }

    private fun activateSeededProfile() {
        val store = checkNotNull(
            awaitNotNull(PROFILE_STORE_TIMEOUT) {
                profileRepository.profile.value?.takeIf { it.getSpecificProfile(PROFILE_NAME) != null }
            }
        ) { "The profile store never published '$PROFILE_NAME'" }
        val switch = runBlocking {
            profileFunction.createProfileSwitch(
                profileStore = store, profileName = PROFILE_NAME, durationInMinutes = 0, percentage = 100,
                timeShiftInHours = 0, timestamp = dateUtil.now(), action = Action.PROFILE_SWITCH,
                source = Sources.Aaps, listValues = emptyList(), iCfg = insulinManager.insulins.first()
            )
        }
        checkNotNull(switch) { "Could not activate the seeded local profile" }
    }

    private fun singleValue(value: Double) = """[{"time":"00:00","timeAsSeconds":0,"value":$value}]"""

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
        private const val PROFILE_STORE_TIMEOUT = 20_000L
        private const val INIT_TIMEOUT = 60_000L

        /** One command through the queue, with the driver's own waits for a silent pump inside it. */
        private const val STEP_TIMEOUT = 240_000L
        private const val RECORD_TIMEOUT = 30_000L
        private const val POLL_MS = 250L
        private const val EPS = 1e-6
        private const val TBR_RATE = 0.25
        private const val TBR_MINUTES = 30
    }
}
