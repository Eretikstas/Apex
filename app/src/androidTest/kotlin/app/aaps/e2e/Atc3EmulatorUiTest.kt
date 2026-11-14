package app.aaps.e2e

import android.Manifest
import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import androidx.work.WorkManager
import app.aaps.ComposeMainActivity
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.core.interfaces.insulin.InsulinManager
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileRepository
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.BooleanComposedKey
import app.aaps.core.keys.BooleanNonKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.di.Atc3Emulation
import app.aaps.implementation.plugin.PluginStore
import app.aaps.plugins.aps.utils.StaticInjector
import app.aaps.pump.atc3.Atc3PumpPlugin
import app.aaps.pump.atc3.emulator.Atc3PumpEmulator
import app.aaps.pump.atc3.emulator.VirtualAtc3Pump
import app.aaps.pump.atc3.keys.Atc3StringKey
import app.aaps.pump.atc3.link.Atc3BtPassword
import com.google.common.truth.Truth.assertWithMessage
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import java.io.File
import java.util.regex.Pattern
import javax.inject.Inject

/**
 * The ATC3 driver's own screens against the software pump (official AAPS pump driver acceptance, U2–U5): Refresh reads
 * the pump, Stop and Resume with their confirm reach the pump, a new Bluetooth password set from the screen reaches
 * the pump, and Back from the connection screen leads to the pump screen, not out of it. The pairing search and
 * Disconnect (U1, U6) need a radio and are checked on Robolectric's Bluetooth (`Atc3PairingViewModelTest`).
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class Atc3EmulatorUiTest {

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
    @Suppress("unused") @Inject lateinit var staticInjector: StaticInjector

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val device: UiDevice get() = UiDevice.getInstance(instrumentation)

    private lateinit var pump: VirtualAtc3Pump
    private lateinit var emulator: Atc3PumpEmulator

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
        device.executeShellCommand("settings put global heads_up_notifications_enabled 0")

        runBlocking { withTimeout(STEP_TIMEOUT) { commandQueue.readStatus("screen test start") } }
        assertWithMessage("the driver is initialized after the first status read")
            .that(awaitTrue(INIT_TIMEOUT) { atc3Plugin.isInitialized() }).isTrue()
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

    // ---- U2: Refresh reads the pump ------------------------------------------------------------------

    @Test
    fun refresh_readsThePump() = onThePumpScreen {
        val before = emulator.state().commands

        click(REFRESH)

        assertWithMessage("the pump was read after Refresh")
            .that(awaitTrue(STEP_TIMEOUT) { emulator.state().commands > before }).isTrue()
        assertVisible(DRIVER_VERSION)
    }

    // ---- U3: Stop and Resume from the screen ---------------------------------------------------------

    @Test
    fun stopAndResume_fromTheScreen_reachThePump() = onThePumpScreen {
        click(STOP_PUMP)
        assertVisibleContains("Stop the pump?")
        click(OK)
        assertWithMessage("the pump stopped").that(awaitTrue(STEP_TIMEOUT) { emulator.state().suspended }).isTrue()

        assertVisible(RESUME_PUMP)
        click(RESUME_PUMP)
        assertVisibleContains("Resume the pump")
        click(OK)
        assertWithMessage("the pump runs again").that(awaitTrue(STEP_TIMEOUT) { !emulator.state().suspended }).isTrue()
    }

    @Test
    fun stop_cancelledInTheConfirm_leavesThePumpRunning() = onThePumpScreen {
        click(STOP_PUMP)
        assertVisibleContains("Stop the pump?")
        click(CANCEL)

        SystemClock.sleep(3_000)
        assertWithMessage("a cancelled stop sends nothing").that(emulator.state().suspended).isFalse()
    }

    // ---- U4: one pump setting saved from the screen --------------------------------------------------

    @Test
    fun aNewBluetoothPassword_fromTheScreen_reachesThePump() = onThePumpScreen {
        val before = pump.btPassword

        click(CHANGE_PASSWORD)
        assertVisible(PASSWORD_DIALOG_TITLE)
        val field = checkNotNull(device.wait(Until.findObject(By.clazz("android.widget.EditText")), STEP_TIMEOUT)) { "no password field" }
        field.text = NEW_PASSWORD
        device.waitForIdle(IDLE_MS)
        click(PASSWORD_WRITE)

        assertWithMessage("the pump holds the new password (was $before, now ${pump.btPassword})")
            .that(awaitTrue(STEP_TIMEOUT) { pump.btPassword in Atc3BtPassword.candidatesFor(NEW_PASSWORD.toInt()) }).isTrue()
    }

    // ---- U5: Back from the connection screen ---------------------------------------------------------

    @Test
    fun back_fromTheConnectionScreen_leadsToThePumpScreen() = onThePumpScreen {
        click(CHANGE_PUMP)
        assertVisible(CONNECTION_TITLE)

        click(BACK)

        assertVisible(DRIVER_VERSION)
        assertWithMessage("still in the pump page").that(waitForVisible(STOP_PUMP)).isTrue()
    }

    // ---- The pump screen -----------------------------------------------------------------------------

    private fun onThePumpScreen(body: () -> Unit) {
        val scenario = ActivityScenario.launch(ComposeMainActivity::class.java)
        try {
            waitForOverview()
            openVia("Manage", expect = PUMP_MANAGEMENT)
            openVia(PUMP_MANAGEMENT, expect = DRIVER_VERSION)
            body()
        } catch (t: Throwable) {
            logScreen("E2E_ATC3_UI")
            throw AssertionError("${t.message}\nOn screen: ${screenText()}", t)
        } finally {
            scenario.close()
        }
    }

    // ---- ui helpers (as in Atc3UiSmokeTest) ----------------------------------------------------------

    private fun byText(s: String): BySelector = By.text(Pattern.compile(Pattern.quote(s), Pattern.CASE_INSENSITIVE))
    private fun byDesc(s: String): BySelector = By.desc(Pattern.compile(Pattern.quote(s), Pattern.CASE_INSENSITIVE))
    private fun byTextContains(s: String): BySelector =
        By.text(Pattern.compile(".*" + Pattern.quote(s) + ".*", Pattern.CASE_INSENSITIVE or Pattern.DOTALL))

    private fun find(label: String, timeout: Long = STEP_TIMEOUT): UiObject2 {
        val end = SystemClock.uptimeMillis() + timeout
        while (SystemClock.uptimeMillis() < end) {
            dismissSystemNotResponding()
            device.findObject(byText(label))?.let { return it }
            device.findObject(byDesc(label))?.let { return it }
            device.waitForIdle(IDLE_MS)
        }
        error("Timed out after ${timeout}ms looking for '$label'")
    }

    private fun click(label: String) = withStaleRetry { find(label).click() }

    private fun openVia(open: String, expect: String, attempts: Int = 4) {
        repeat(attempts) {
            click(open)
            if (waitForVisible(expect)) return
        }
        error("'$expect' not visible after $attempts taps on '$open'")
    }

    private fun waitForVisible(label: String, timeout: Long = STEP_TIMEOUT): Boolean {
        val end = SystemClock.uptimeMillis() + timeout
        while (SystemClock.uptimeMillis() < end) {
            dismissSystemNotResponding()
            if (device.findObject(byText(label)) != null || device.findObject(byDesc(label)) != null) return true
            device.waitForIdle(IDLE_MS)
        }
        return false
    }

    private fun assertVisible(label: String) {
        if (!waitForVisible(label)) error("'$label' not visible")
    }

    private fun assertVisibleContains(substring: String, timeout: Long = STEP_TIMEOUT) {
        val end = SystemClock.uptimeMillis() + timeout
        while (SystemClock.uptimeMillis() < end) {
            dismissSystemNotResponding()
            if (device.findObject(byTextContains(substring)) != null) return
            device.waitForIdle(IDLE_MS)
        }
        error("Timed out after ${timeout}ms looking for text containing '$substring'")
    }

    private fun waitForOverview() {
        val end = SystemClock.uptimeMillis() + INIT_TIMEOUT
        while (SystemClock.uptimeMillis() < end) {
            dismissSystemNotResponding()
            runCatching { device.findObject(byDesc("Close sheet"))?.click() }
            if (device.findObject(byDesc("Open navigation")) != null) return
            device.waitForIdle(IDLE_MS)
        }
        error("Overview never appeared within ${INIT_TIMEOUT}ms")
    }

    private fun dismissSystemNotResponding() {
        if (device.findObject(byTextContains("isn't responding")) == null) return
        runCatching { device.findObject(byText("Wait"))?.click() }
        device.waitForIdle(IDLE_MS)
    }

    private inline fun withStaleRetry(times: Int = STALE_RETRIES, block: () -> Unit) {
        var last: StaleObjectException? = null
        repeat(times) {
            try {
                block(); return
            } catch (e: StaleObjectException) {
                last = e; device.waitForIdle(STALE_SETTLE_MS)
            }
        }
        throw last ?: IllegalStateException("withStaleRetry exhausted")
    }

    private fun screenText(): String = runCatching {
        device.findObjects(By.pkg(Pattern.compile(".*"))).mapNotNull { o ->
            val txt = runCatching { o.text }.getOrNull()?.takeIf { it.isNotBlank() }
            val desc = runCatching { o.contentDescription }.getOrNull()?.takeIf { it.isNotBlank() }
            if (txt != null || desc != null) "[t=$txt|d=$desc]" else null
        }.joinToString(" ").take(3000)
    }.getOrDefault("(could not read the screen)")

    private fun logScreen(tag: String) {
        runCatching { screenText().chunked(3500).forEachIndexed { i, c -> Log.e(tag, "$i $c") } }
    }

    private fun awaitTrue(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < end) {
            if (runCatching(condition).getOrDefault(false)) return true
            SystemClock.sleep(IDLE_MS)
        }
        return false
    }

    // ---- set up, as in Atc3EmulatorScenarioTest ------------------------------------------------------

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
        val end = SystemClock.uptimeMillis() + PROFILE_STORE_TIMEOUT
        var store = profileRepository.profile.value?.takeIf { it.getSpecificProfile(PROFILE_NAME) != null }
        while (store == null && SystemClock.uptimeMillis() < end) {
            SystemClock.sleep(IDLE_MS)
            store = profileRepository.profile.value?.takeIf { it.getSpecificProfile(PROFILE_NAME) != null }
        }
        checkNotNull(store) { "The profile store never published '$PROFILE_NAME'" }
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
        private const val NEW_PASSWORD = "654321"

        // Labels in English: the CI emulator runs in English.
        private const val PUMP_MANAGEMENT = "Pump"
        private const val DRIVER_VERSION = "Driver version"
        private const val REFRESH = "Refresh"
        private const val STOP_PUMP = "Stop pump"
        private const val RESUME_PUMP = "Resume pump"
        private const val CHANGE_PUMP = "Change pump"
        private const val CHANGE_PASSWORD = "Change Bluetooth password"
        private const val PASSWORD_DIALOG_TITLE = "Bluetooth password"
        private const val PASSWORD_WRITE = "Set on pump"
        private const val CONNECTION_TITLE = "Apex pump connection"
        private const val OK = "OK"
        private const val CANCEL = "Cancel"
        private const val BACK = "Back"

        private const val PROFILE_STORE_TIMEOUT = 20_000L
        private const val INIT_TIMEOUT = 60_000L
        private const val STEP_TIMEOUT = 60_000L
        private const val IDLE_MS = 300L
        private const val STALE_RETRIES = 10
        private const val STALE_SETTLE_MS = 700L
    }
}
