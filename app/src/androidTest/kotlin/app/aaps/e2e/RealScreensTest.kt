package app.aaps.e2e

import android.Manifest
import android.content.Context
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import androidx.work.WorkManager
import app.aaps.ComposeMainActivity
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.RM
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.data.model.data.Block
import app.aaps.core.data.model.data.TargetBlock
import app.aaps.core.data.time.T
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.keys.BooleanComposedKey
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.BooleanNonKey
import app.aaps.core.keys.StringKey
import app.aaps.di.Atc3Emulation
import app.aaps.di.EmulatedApexBLE
import app.aaps.di.ResetGraphRule
import app.aaps.di.testGraphs
import app.aaps.pump.apex.ApexPumpPlugin
import app.aaps.pump.apex.keys.ApexStringKey
import app.aaps.pump.apex.link.ApexRadioSwap
import app.aaps.pump.atc3.emulator.Atc3PumpEmulator
import app.aaps.pump.atc3.emulator.VirtualAtc3Pump
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import java.io.File
import java.util.regex.Pattern
import kotlin.math.sin

/**
 * Real screenshots of the running app for a person to look at (not a check: a step that cannot be reached is
 * skipped, the rest still runs). The app runs as installed: the Apex v4 driver against our software pump (only the
 * radio is replaced), 6 hours of glucose in the database, a profile in mmol/L, the loop run once. The language is
 * set by the CI script (per-app locale). Pictures go to the app's external files, folder `realshots`, and the
 * script pulls them.
 */
@RunWith(AndroidJUnit4::class)
class RealScreensTest {

    @get:Rule val rules: RuleChain = RuleChain.outerRule(ResetGraphRule())

    private val preferences get() = testGraphs.preferences
    private val pluginList get() = testGraphs.allPlugins(testGraphs.aapsLogger)
    private val persistenceLayer get() = testGraphs.persistenceLayer
    private val profileRepository get() = testGraphs.profileRepository
    private val profileFunction get() = testGraphs.profileFunction
    private val insulinManager get() = testGraphs.insulinManager
    private val dateUtil get() = testGraphs.dateUtil
    private val loop get() = testGraphs.loop
    private val objectivesPlugin get() = testGraphs.objectivesPlugin
    private val commandQueue get() = testGraphs.commandQueue

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val device: UiDevice get() = UiDevice.getInstance(instrumentation)
    private val shots: File get() = File(instrumentation.targetContext.getExternalFilesDir(null), "realshots").apply { mkdirs() }

    private lateinit var pump: VirtualAtc3Pump

    @Before
    fun setUp() {
        clearAllSharedPrefs()
        // As in VirtualBenchTest: a fresh v4 database, a used bolus journal and the pump's heartbeat.
        instrumentation.targetContext.deleteDatabase("apex_database.db")
        pump = VirtualAtc3Pump(SERIAL) { System.currentTimeMillis() }
        pump.seedBolusHistory(System.currentTimeMillis())
        pump.startHeartbeats()
        Atc3Emulation.emulator = Atc3PumpEmulator(pump)
        ApexRadioSwap.make = { logger, context, trace -> EmulatedApexBLE(logger, context, trace) }
        for (p in listOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN, "android.permission.POST_NOTIFICATIONS"))
            runCatching { instrumentation.uiAutomation.grantRuntimePermission(PKG, p) }

        preferences.put(BooleanNonKey.GeneralSetupWizardProcessed, true)
        preferences.put(StringKey.GeneralUnits, "mmol")
        preferences.put(BooleanKey.GeneralSimpleMode, false)
        preferences.put(ApexStringKey.ApexSerialNumber, SERIAL)
        preferences.put(ApexStringKey.ApexBtPassword, pump.btPassword)
        preferences.put(ApexStringKey.ApexAddress, ADDRESS)
        preferences.put(BooleanComposedKey.ConfigBuilderEnabled, "PUMP_ApexPumpPlugin", value = true)
        preferences.put(BooleanComposedKey.ConfigBuilderEnabled, "PUMP_VirtualPumpPlugin", value = false)

        testGraphs.pluginStore.plugins = pluginList
        testGraphs.configBuilder.initialize()
        testGraphs.config.initCompleted()
        step("profile") { seedAndActivateProfile() }
        step("pump status") { runBlocking { withTimeout(240_000L) { commandQueue.readStatus("screens") } } }
        step("glucose") { seedGlucose() }
        step("loop") {
            objectivesPlugin.objectives.forEach { it.startedOn = 1 }
            runBlocking {
                persistenceLayer.insertOrUpdateRunningMode(
                    runningMode = RM(timestamp = dateUtil.now(), mode = RM.Mode.OPEN_LOOP, autoForced = false, duration = 0),
                    action = Action.OPEN_LOOP_MODE, source = Sources.Aaps, listValues = emptyList()
                )
                loop.invoke("screens", allowNotification = false)
            }
        }
    }

    @After
    fun tearDown() {
        runCatching { commandQueue.clear() }
        runCatching { WorkManager.getInstance(instrumentation.targetContext).cancelAllWork() }
        runCatching { pluginList.filterIsInstance<ApexPumpPlugin>().single().disconnect("screens end") }
        Atc3Emulation.emulator = null
        ApexRadioSwap.make = null
        runCatching { pump.shutdown() }
    }

    @Test
    fun screens() {
        ActivityScenario.launch(ComposeMainActivity::class.java).use {
            SystemClock.sleep(8_000)
            dismissDialogs()
            // The permissions sheet (folder, battery) covers the screen on a fresh install: take a picture, then close it.
            if (device.hasObject(By.text(Pattern.compile("(?i)(Требуются разрешения|Permissions Required)")))) {
                shot("00_permissions")
                device.pressBack()
                SystemClock.sleep(1_500)
            }
            shot("01_main")
            step("scroll") {
                device.swipe(device.displayWidth / 2, device.displayHeight * 3 / 4, device.displayWidth / 2, device.displayHeight / 4, 30)
                SystemClock.sleep(1_500)
                shot("02_main_scrolled")
                device.swipe(device.displayWidth / 2, device.displayHeight / 4, device.displayWidth / 2, device.displayHeight * 3 / 4, 30)
                SystemClock.sleep(1_000)
            }
            step("manage") {
                tapText("Управление|Manage")
                SystemClock.sleep(2_000)
                shot("03_manage")
                device.pressBack()
                SystemClock.sleep(1_000)
            }
            step("treatments") {
                tapText("Назначения|Treatments")
                SystemClock.sleep(2_000)
                shot("04_treatments")
                device.pressBack()
                SystemClock.sleep(1_000)
            }
            step("menu") {
                tapDesc("Показать панель навигации|Open navigation")
                SystemClock.sleep(2_000)
                shot("05_menu")
                device.pressBack()
                SystemClock.sleep(1_000)
            }
            for ((name, query) in listOf("06_apex" to "Apex", "07_learner" to "Ученик", "08_sibionics" to "Sibionics 2")) step(name) {
                // The search does not always list a plugin: then it is opened from the config builder.
                runCatching { openBySearch(query) }.getOrElse {
                    backToMain()
                    openViaConfigBuilder(query)
                }
                SystemClock.sleep(3_000)
                shot(name)
                backToMain()
            }
            step("settings") {
                tapDesc("Настройки|Settings")
                SystemClock.sleep(2_500)
                shot("09_settings")
                device.pressBack()
            }
        }
    }

    // Steps

    private fun openBySearch(query: String) {
        tapText("Поиск|Search")
        SystemClock.sleep(800)
        device.findObject(By.focused(true))?.text = query
        SystemClock.sleep(2_500)
        // Hide the keyboard, then open the plugin by its name in the results (not the switch next to it).
        device.pressBack()
        SystemClock.sleep(800)
        shot("search_$query")
        val field = device.findObject(By.focused(true))?.visibleBounds?.bottom ?: (device.displayHeight / 8)
        val hit = device.findObjects(By.text(Pattern.compile("(?i)$query.*")))
            .filter { it.visibleBounds.top > field }
            .minByOrNull { it.visibleBounds.top }
        checkNotNull(hit) { "no result for $query" }.click()
    }

    /** The plugin opened from «Конфигурация»: the menu, the config builder, its row scrolled to and tapped. */
    private fun openViaConfigBuilder(name: String) {
        tapDesc("Показать панель навигации|Open navigation")
        SystemClock.sleep(1_500)
        tapText("Конфигурация|Config builder")
        SystemClock.sleep(2_500)
        val byName = By.text(Pattern.compile("(?i)$name.*"))
        val hit = device.findObject(byName)
            ?: device.findObject(By.scrollable(true))?.scrollUntil(Direction.DOWN, Until.findObject(byName))
        checkNotNull(hit) { "no $name in the config builder" }.click()
    }

    private fun tapText(regex: String) {
        val o = device.wait(Until.findObject(By.text(Pattern.compile("(?i)($regex)"))), 5_000)
            ?: device.findObject(By.desc(Pattern.compile("(?i)($regex)")))
        checkNotNull(o) { "no '$regex' on screen" }.click()
    }

    /** Back until the main screen (its search field) shows again, at most 4 steps. */
    private fun backToMain() {
        repeat(4) {
            if (device.hasObject(By.text(Pattern.compile("(?i)(Поиск|Search)")))) return
            device.pressBack()
            SystemClock.sleep(1_000)
        }
    }

    private fun tapDesc(regex: String) {
        val o = device.wait(Until.findObject(By.desc(Pattern.compile("(?i)($regex)"))), 5_000)
        checkNotNull(o) { "no '$regex' button" }.click()
    }

    private fun dismissDialogs() {
        repeat(3) {
            val ok = device.findObject(By.text(Pattern.compile("(?i)(OK|ОК|Закрыть|Close|Понятно)")))
            if (ok != null) { ok.click(); SystemClock.sleep(800) }
        }
    }

    private fun shot(name: String) {
        runCatching { device.takeScreenshot(File(shots, "$name.png")) }
    }

    /** A step that fails is written down next to the pictures, and the run goes on. */
    private fun step(name: String, block: () -> Unit) {
        runCatching(block).onFailure { e -> File(shots, "failed_$name.txt").writeText(e.stackTraceToString()) }
    }

    // Data

    private fun seedAndActivateProfile() {
        val day = T.hours(24).msecs()
        runBlocking {
            profileRepository.add(
                profileRepository.newDraft().copy(
                    mgdl = false,
                    ic = listOf(Block(day, 20.0)),
                    isf = listOf(Block(day, 8.5)),
                    basal = listOf(Block(day, 0.4)),
                    target = listOf(TargetBlock(day, 6.0, 6.0))
                )
            )
        }.getOrThrow()
        var store = profileRepository.profile.value
        val end = SystemClock.uptimeMillis() + 20_000
        while (store?.getSpecificProfile(PROFILE_NAME) == null && SystemClock.uptimeMillis() < end) {
            SystemClock.sleep(250); store = profileRepository.profile.value
        }
        runBlocking {
            profileFunction.createProfileSwitch(
                profileStore = checkNotNull(store), profileName = PROFILE_NAME, durationInMinutes = 0, percentage = 100,
                timeShiftInHours = 0, timestamp = dateUtil.now(), action = Action.PROFILE_SWITCH,
                source = Sources.Aaps, listValues = emptyList(), iCfg = insulinManager.insulins.first()
            )
        }
    }

    /** Every 5 minutes for 6 hours: a dinner rise to about 11 and back, then a slow rise to about 8.5 now. */
    private fun seedGlucose() {
        val now = dateUtil.now()
        val values = (72 downTo 0).map { k ->
            val h = (72 - k) / 12.0
            val meal = if (h < 4.0) 90.0 * sin(Math.PI * h / 4.0) else 0.0
            val rise = if (h > 4.5) 45.0 * (h - 4.5) / 1.5 else 0.0
            val mgdl = 108.0 + meal + rise
            GV(timestamp = now - k * 300_000L, value = mgdl, raw = mgdl, noise = null, trendArrow = TrendArrow.FORTY_FIVE_UP, sourceSensor = SourceSensor.RANDOM)
        }
        runBlocking { persistenceLayer.insertCgmSourceData(Sources.Random, values, emptyList(), null) }
    }

    private fun clearAllSharedPrefs() {
        val ctx = instrumentation.targetContext
        File(ctx.applicationInfo.dataDir, "shared_prefs").listFiles()?.forEach { f ->
            if (f.name.endsWith(".xml")) ctx.getSharedPreferences(f.name.removeSuffix(".xml"), Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    private companion object {

        const val PKG = "info.nightscout.androidaps"
        const val SERIAL = "12345678"
        const val ADDRESS = "11:22:33:AA:BB:CC"
        const val PROFILE_NAME = "LocalProfile1"
    }
}
