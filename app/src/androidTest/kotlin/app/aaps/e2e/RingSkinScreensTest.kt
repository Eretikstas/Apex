package app.aaps.e2e

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.work.WorkManager
import app.aaps.ComposeMainActivity
import app.aaps.core.data.model.CA
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.aps.APSResult
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.aps.Predictions
import app.aaps.core.interfaces.aps.RT
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.insulin.InsulinManager
import app.aaps.core.interfaces.overview.graph.BgDataPoint
import app.aaps.core.interfaces.overview.graph.BgRange
import app.aaps.core.interfaces.overview.graph.BgType
import app.aaps.core.interfaces.overview.graph.OverviewDataCache
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileRepository
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventLoopUpdateGui
import app.aaps.core.interfaces.rx.events.EventRefreshOverview
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.BooleanNonKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.implementation.plugin.PluginStore
import app.aaps.plugins.aps.utils.StaticInjector
import app.aaps.plugins.main.skins.SkinRing
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Provider
import kotlin.math.PI
import kotlin.math.sin
import app.aaps.core.ui.R as CoreUiR

/**
 * Not a check: screenshots of the "Ring and why" home (SkinRing) for reading the screen and its
 * Russian text. Seeds 7 days of made-up glucose, then shows three loop decisions (stable,
 * falling, rising with a cut micro bolus), each in the light and the dark theme.
 * Screenshots go to the app's external files dir, `screens/`, with `index.txt`.
 * Run by `.github/workflows/atc3-screens.yml` when the commit message has `[ring]`.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class RingSkinScreensTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var preferences: Preferences
    @Inject lateinit var pluginStore: PluginStore
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var pluginList: List<@JvmSuppressWildcards PluginBase>
    @Inject lateinit var configBuilder: ConfigBuilder
    @Inject lateinit var config: Config
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var profileRepository: ProfileRepository
    @Inject lateinit var insulinManager: InsulinManager
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var persistenceLayer: PersistenceLayer
    @Inject lateinit var loop: Loop
    @Inject lateinit var apsResultProvider: Provider<APSResult>
    /** A Provider: the cache reads the plugin list at creation, so it is made only after set up. */
    @Inject lateinit var overviewDataCache: Provider<OverviewDataCache>
    @Inject lateinit var rxBus: RxBus
    @Suppress("unused") @Inject lateinit var staticInjector: StaticInjector

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val device: UiDevice get() = UiDevice.getInstance(instrumentation)
    private val ctx: Context get() = instrumentation.targetContext

    private lateinit var outDir: File
    private var shot = 0

    /** One loop decision to show. */
    private class Case(
        val name: String,
        val reason: String,
        val smb: Double?,
        val rate: Double?,
        val duration: Int?,
        val eventualMgdl: Double,
        val hypoRisk: Boolean,
        /** Prediction for the next hour, mg/dL at minute m. */
        val prediction: (Int) -> Double
    )

    private val cases = listOf(
        Case(
            name = "Стабильно",
            reason = "Eventual BG 104 >= 100, temp basal 0.35 U/h for 30m",
            smb = null, rate = 0.35, duration = 30, eventualMgdl = 104.0, hypoRisk = false,
            prediction = { m -> LAST_BG + m * 0.12 }
        ),
        Case(
            name = "Падает",
            reason = "Hypo protection: BG is dropping, predLow 58 → zero temp",
            smb = null, rate = 0.0, duration = 30, eventualMgdl = 66.0, hypoRisk = true,
            prediction = { m -> LAST_BG - m * 0.8 }
        ),
        Case(
            name = "Растёт",
            reason = "Meal mode: SMB 0.80 U; cap: 0.80 → 0.50",
            smb = 0.5, rate = null, duration = null, eventualMgdl = 190.0, hypoRisk = false,
            prediction = { m -> LAST_BG + m * 1.3 }
        ),
    )

    @Before
    fun setUp() {
        clearAllSharedPrefs()
        hiltRule.inject()
        runCatching { instrumentation.uiAutomation.grantRuntimePermission(PKG, Manifest.permission.BLUETOOTH_CONNECT) }
        runCatching { instrumentation.uiAutomation.grantRuntimePermission(PKG, "android.permission.POST_NOTIFICATIONS") }

        preferences.put(BooleanNonKey.GeneralSetupWizardProcessed, true)
        preferences.put(StringKey.GeneralUnits, "mmol")
        preferences.put(BooleanKey.GeneralSimpleMode, false)
        preferences.put(StringKey.GeneralSkin, SkinRing::class.java.name)

        pluginStore.plugins = pluginList
        configBuilder.initialize()
        config.initCompleted()
        seedLocalProfile()
        activateSeededProfile()
        seedGlucose()
        seedCarbs()
        device.executeShellCommand("settings put global heads_up_notifications_enabled 0")

        outDir = File(ctx.getExternalFilesDir(null), "screens").apply { deleteRecursively(); mkdirs() }
    }

    @After
    fun tearDown() {
        runCatching { commandQueue.clear() }
        runCatching { WorkManager.getInstance(ctx).cancelAllWork() }
        clearAllSharedPrefs()
    }

    @Test
    fun ringScreens() {
        for (theme in listOf("light", "dark")) {
            preferences.put(StringKey.GeneralDarkMode, theme)
            val themeName = if (theme == "light") "день" else "ночь"
            for (case in cases) {
                runCatching {
                    if (!home()) return@runCatching
                    show(case)
                    capture("${case.name}, $themeName")
                }.onFailure { Log.w(TAG, "Case ${case.name} $theme failed: ${it.message}") }
            }
        }
        pressButtons()
        Log.i(TAG, "Done: $shot screenshots")
    }

    /** Each button under the why card opens its screen; it is saved and closed with Back, never confirmed. */
    private fun pressButtons() {
        for (label in listOf("Углеводы", "Инсулин", "Калькулятор", "Цель")) {
            runCatching {
                if (!home()) return@runCatching
                show(cases[0])
                closeDialogs()
                val w = device.displayWidth
                val h = device.displayHeight
                device.swipe(w / 2, h * 3 / 4, w / 2, h / 4, 30)
                settle()
                // "Углеводы" is also the title of the active carbs value: the button is the lowest one.
                val button = device.findObjects(By.pkg(PKG).text(label)).maxByOrNull { it.visibleBounds.top }
                if (button == null) {
                    save("Кнопка «$label» не найдена")
                    return@runCatching
                }
                button.click()
                settle()
                SystemClock.sleep(DATA_MS)
                save("Кнопка «$label»")
                device.pressBack()
                settle()
                device.pressBack()
            }.onFailure { Log.w(TAG, "Button $label failed: ${it.message}") }
        }
    }

    // ---- data -------------------------------------------------------------------------------------

    /** 7 days every 5 min: a slow wave 3.5–10.4 mmol/L that ends at [LAST_BG] and goes down. */
    private fun seedGlucose() {
        val now = dateUtil.now()
        val step = 5 * 60_000L
        val values = (0 until 7 * 288).map { i ->
            val t = now - i * step
            val phase = PHASE_NOW + 2 * PI * (t - now) / WAVE_MS
            val v = MEAN + AMPLITUDE * sin(phase)
            GV(
                timestamp = t, value = v, raw = 0.0, noise = null,
                trendArrow = TrendArrow.FORTY_FIVE_DOWN, sourceSensor = SourceSensor.RANDOM
            )
        }.reversed()
        val inserted = runBlocking { persistenceLayer.insertCgmSourceData(Sources.Random, values, emptyList(), null) }.inserted.size
        Log.i(TAG, "Seeded $inserted glucose values")
    }

    /** 20 g of carbs 40 min ago, so the active carbs (COB) have a value. */
    private fun seedCarbs() {
        runBlocking {
            persistenceLayer.insertOrUpdateCarbs(
                CA(timestamp = dateUtil.now() - 40 * 60_000L, amount = 20.0, duration = 0L),
                action = Action.CARBS, source = Sources.CarbDialog, note = null
            )
        }
        Log.i(TAG, "Seeded 20 g carbs")
    }

    /** Puts [case] in as the last loop run and its prediction into the graph data. */
    private fun show(case: Case) {
        val now = dateUtil.now()
        val points = (1..12).map { case.prediction(it * 5).toInt() }
        val rt = RT(
            algorithm = APSResult.Algorithm.AIMI,
            runningDynamicIsf = false,
            timestamp = now,
            bg = LAST_BG,
            eventualBG = case.eventualMgdl,
            targetBG = 100.0,
            units = case.smb,
            deliverAt = now,
            duration = case.duration,
            rate = case.rate,
            predBGs = Predictions(IOB = points, UAM = points),
            variable_sens = 153.0,
            isHypoRisk = case.hypoRisk
        ).also { it.reason.append(case.reason) }
        loop.lastRun = Loop.LastRun().apply {
            request = apsResultProvider.get().with(rt)
            lastAPSRun = now
        }
        overviewDataCache.get().updatePredictions(points.mapIndexed { i, v ->
            BgDataPoint(
                timestamp = now + (i + 1) * 5 * 60_000L, value = v.toDouble(),
                range = if (v < 70) BgRange.LOW else if (v > 180) BgRange.HIGH else BgRange.IN_RANGE,
                type = BgType.UAM_PREDICTION
            )
        })
        rxBus.send(EventLoopUpdateGui())
        rxBus.send(EventRefreshOverview("RingSkinScreensTest", now = true))
        settle()
        SystemClock.sleep(DATA_MS)
    }

    // ---- navigation and screenshots ---------------------------------------------------------------

    private fun home(): Boolean {
        val intent = Intent(ctx, ComposeMainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        ctx.startActivity(intent)
        val openNav = ctx.getString(CoreUiR.string.open_navigation)
        val end = SystemClock.uptimeMillis() + INIT_TIMEOUT
        var lastBack = SystemClock.uptimeMillis()
        while (SystemClock.uptimeMillis() < end) {
            dismissSystemNotResponding()
            if (device.findObject(By.pkg(PKG).desc(openNav)) != null) {
                settle()
                return true
            }
            if (SystemClock.uptimeMillis() - lastBack > 8_000L && device.currentPackageName == PKG) {
                device.pressBack()
                lastBack = SystemClock.uptimeMillis()
            }
            device.waitForIdle(IDLE_MS)
        }
        Log.w(TAG, "Overview not reached")
        save("Главный экран не открылся")
        return false
    }

    private fun capture(name: String) {
        closeDialogs()
        save(name)
        // The home is a Compose column in an AndroidView: UiAutomator does not see it as scrollable, so swipe.
        val w = device.displayWidth
        val h = device.displayHeight
        device.swipe(w / 2, h * 3 / 4, w / 2, h / 4, 30)
        settle()
        save("$name (низ)")
        device.swipe(w / 2, h / 4, w / 2, h * 3 / 4, 30)
        settle()
    }

    /** AIMI shows its own alert dialogs (e.g. hypo risk): saved once, then closed so the home is visible. */
    private fun closeDialogs() {
        repeat(3) {
            val ok = device.findObject(By.pkg(PKG).text(Pattern.compile("OK|ОК|Ок", Pattern.CASE_INSENSITIVE))) ?: return
            save("Окно AIMI")
            runCatching { ok.click() }
            settle()
        }
    }

    private fun save(name: String) {
        shot++
        val file = File(outDir, "%03d.png".format(shot))
        val ok = runCatching { device.takeScreenshot(file) }.getOrDefault(false)
        runCatching { File(outDir, "index.txt").appendText("%03d\t%s\t%s\n".format(shot, if (ok) "ok" else "fail", name)) }
        Log.i(TAG, "Shot $shot $name ok=$ok")
    }

    private fun settle() {
        device.waitForIdle(IDLE_MS)
        SystemClock.sleep(SETTLE_MS)
        dismissSystemNotResponding()
    }

    private fun dismissSystemNotResponding() {
        val dialog = device.findObject(By.text(Pattern.compile(".*(isn't responding|не отвечает).*", Pattern.DOTALL)))
        if (dialog == null) return
        runCatching {
            (device.findObject(By.text(Pattern.compile("Wait|Подождать|Ждать", Pattern.CASE_INSENSITIVE))))?.click()
        }
        device.waitForIdle(IDLE_MS)
    }

    // ---- set up (as in Atc3RuScreensTest) ---------------------------------------------------------

    private fun seedLocalProfile() {
        val profile = profileRepository.newDraft().apply {
            mgdl = true
            ic = JSONArray(singleValue(24.0))
            isf = JSONArray(singleValue(153.0))
            basal = JSONArray(singleValue(0.35))
            targetLow = JSONArray(singleValue(108.0))
            targetHigh = JSONArray(singleValue(108.0))
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
            SystemClock.sleep(IDLE_MS)
        }
        return null
    }

    private fun clearAllSharedPrefs() {
        File(ctx.applicationInfo.dataDir, "shared_prefs").listFiles()?.forEach { f ->
            if (f.name.endsWith(".xml"))
                ctx.getSharedPreferences(f.name.removeSuffix(".xml"), Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    companion object {

        private const val TAG = "RING_SCREENS"
        private const val PKG = "info.nightscout.androidaps"
        private const val PROFILE_NAME = "LocalProfile1"
        private const val PROFILE_STORE_TIMEOUT = 20_000L
        private const val INIT_TIMEOUT = 60_000L
        private const val IDLE_MS = 300L
        private const val SETTLE_MS = 1_500L
        private const val DATA_MS = 5_000L

        private const val MEAN = 125.0
        private const val AMPLITUDE = 62.0
        private const val WAVE_MS = 5 * 3_600_000L

        /** Wave phase now: sin = -0.45 and going down, so the last BG is about 97 mg/dL (5.4 mmol/L). */
        private const val PHASE_NOW = PI + 0.467
        private const val LAST_BG = MEAN - AMPLITUDE * 0.45
    }
}
