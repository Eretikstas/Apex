package app.aaps.e2e

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
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
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.BooleanNonKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.implementation.plugin.PluginStore
import app.aaps.plugins.aps.utils.StaticInjector
import app.aaps.pump.atc3.Atc3PumpPlugin
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
import app.aaps.R as AppR
import app.aaps.core.ui.R as CoreUiR

/**
 * Not a check: it walks through the app on an emulator with a Russian system language and saves a
 * screenshot of every screen it reaches, so the translation can be read on real screens.
 * The overview, the menu and each menu screen are opened, and the buttons on them one level down.
 * It never confirms a dialog: after each screen it starts the app again from the overview.
 * Screenshots go to the app's external files dir, `screens/`, with `index.txt` (number, path).
 * Run by `.github/workflows/atc3-screens.yml`, which pulls them with adb.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class Atc3RuScreensTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)

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
    private val ctx: Context get() = instrumentation.targetContext

    private lateinit var outDir: File
    private var shot = 0
    private val started = SystemClock.uptimeMillis()

    /** Labels never pressed: they close the app, and the test runs inside the app process. */
    private val skip by lazy {
        setOf(
            ctx.getString(CoreUiR.string.nav_exit),
            ctx.getString(CoreUiR.string.exit),
            ctx.getString(AppR.string.nav_exit)
        ).map { it.lowercase() }.toSet()
    }

    @Before
    fun setUp() {
        clearAllSharedPrefs()
        hiltRule.inject()
        instrumentation.uiAutomation.grantRuntimePermission(PKG, Manifest.permission.BLUETOOTH_CONNECT)
        runCatching { instrumentation.uiAutomation.grantRuntimePermission(PKG, Manifest.permission.BLUETOOTH_SCAN) }
        runCatching { instrumentation.uiAutomation.grantRuntimePermission(PKG, "android.permission.POST_NOTIFICATIONS") }

        preferences.put(BooleanNonKey.GeneralSetupWizardProcessed, true)
        preferences.put(StringKey.GeneralUnits, "mmol")
        // Full mode: more menus and settings to read.
        preferences.put(BooleanKey.GeneralSimpleMode, false)
        preferences.put(BooleanComposedKey.ConfigBuilderEnabled, "PUMP_Atc3PumpPlugin", value = true)
        preferences.put(BooleanComposedKey.ConfigBuilderEnabled, "PUMP_VirtualPumpPlugin", value = false)

        pluginStore.plugins = pluginList
        configBuilder.initialize()
        config.initCompleted()
        seedLocalProfile()
        activateSeededProfile()
        device.executeShellCommand("settings put global heads_up_notifications_enabled 0")

        outDir = File(ctx.getExternalFilesDir(null), "screens").apply { deleteRecursively(); mkdirs() }
    }

    @After
    fun tearDown() {
        runCatching { commandQueue.clear() }
        runCatching { WorkManager.getInstance(ctx).cancelAllWork() }
        runCatching { atc3Plugin.setPluginEnabledBlocking(PluginType.PUMP, false) }
        clearAllSharedPrefs()
    }

    @Test
    fun walkAllScreens() {
        home()
        capture("Главный экран", scroll = true)

        // 1. Buttons on the overview.
        val overviewItems = clickableLabels()
        for (label in overviewItems) {
            if (outOfBudget()) break
            visit(listOf(label)) { home() }
        }

        // 2. The menu, each menu screen, and the buttons on each menu screen.
        if (!home() || !openMenu()) return
        capture("Меню", scroll = true)
        val menuItems = clickableLabels() - overviewItems.toSet()
        // First every menu screen, then one level down, so all menu screens fit in the time budget.
        val children = mutableMapOf<String, List<String>>()
        for (item in menuItems) {
            if (outOfBudget()) break
            val seen = visit(listOf("Меню", item)) { home() && openMenu() }
            if (seen != null) children[item] = seen.take(MAX_CHILDREN)
        }
        for ((item, labels) in children) {
            for (child in labels) {
                if (outOfBudget()) break
                visit(listOf("Меню", item, child)) { home() && openMenu() && clickLabel(item) }
            }
        }
        Log.i(TAG, "Done: $shot screenshots")
    }

    /**
     * Go to the parent with [reach], press the last label of [path], save the screen.
     * Returns the button labels seen on the new screen (for one level more), or null.
     */
    private fun visit(path: List<String>, reach: () -> Boolean): List<String>? {
        return try {
            if (!reach()) return null
            val before = clickableLabels().toSet()
            if (!clickLabel(path.last())) return null
            capture(path.joinToString(" › "), scroll = true)
            if (device.currentPackageName != PKG) null
            else clickableLabels().filter { it !in before }
        } catch (t: Throwable) {
            Log.w(TAG, "Visit $path failed: ${t.message}")
            null
        }
    }

    // ---- navigation -------------------------------------------------------------------------------

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
            // A sheet or dialog in front of the overview: Back closes it.
            if (SystemClock.uptimeMillis() - lastBack > 8_000L && device.currentPackageName == PKG) {
                device.pressBack()
                lastBack = SystemClock.uptimeMillis()
            }
            device.waitForIdle(IDLE_MS)
        }
        Log.w(TAG, "Overview not reached")
        return false
    }

    private fun openMenu(): Boolean {
        val openNav = ctx.getString(CoreUiR.string.open_navigation)
        val nav = device.findObject(By.pkg(PKG).desc(openNav)) ?: return false
        nav.click()
        settle()
        return true
    }

    private fun clickLabel(label: String): Boolean {
        val target = findByLabel(label) ?: return false
        val r = target.visibleBounds
        device.click(r.centerX(), r.centerY())
        settle()
        return true
    }

    private fun findByLabel(label: String): UiObject2? =
        clickables().firstOrNull { labelOf(it) == label }

    private fun clickables(): List<UiObject2> =
        runCatching { device.findObjects(By.pkg(PKG).clickable(true)) }.getOrDefault(emptyList())
            .filter { runCatching { !it.visibleBounds.isEmpty }.getOrDefault(false) }
            .sortedWith(compareBy({ boundsOf(it).top }, { boundsOf(it).left }))

    /** Labels of the buttons on screen, in screen order, without repeats and without [skip]. */
    private fun clickableLabels(): List<String> =
        clickables().mapNotNull { labelOf(it) }
            .filter { it.lowercase() !in skip }
            .distinct()
            .take(MAX_ITEMS)

    private fun labelOf(o: UiObject2): String? = runCatching {
        o.text?.takeIf { it.isNotBlank() }
            ?: o.contentDescription?.takeIf { it.isNotBlank() }
            ?: o.findObject(By.text(Pattern.compile(".+")))?.text
            ?: o.findObject(By.desc(Pattern.compile(".+")))?.contentDescription
    }.getOrNull()?.trim()?.take(60)

    private fun boundsOf(o: UiObject2): Rect = runCatching { o.visibleBounds }.getOrDefault(Rect())

    // ---- screenshots ------------------------------------------------------------------------------

    private fun capture(name: String, scroll: Boolean) {
        save(name)
        if (!scroll || device.currentPackageName != PKG) return
        repeat(MAX_PAGES - 1) { page ->
            val list = runCatching { device.findObject(By.pkg(PKG).scrollable(true)) }.getOrNull() ?: return
            val moved = runCatching { list.scroll(Direction.DOWN, 0.8f) }.getOrDefault(false)
            if (!moved) return
            settle()
            save("$name (стр. ${page + 2})")
        }
    }

    private fun save(name: String) {
        if (shot >= MAX_SHOTS) return
        shot++
        val file = File(outDir, "%03d.png".format(shot))
        val ok = runCatching { device.takeScreenshot(file) }.getOrDefault(false)
        // Written at once: if the app process ends, the screenshots so far keep their names.
        runCatching { File(outDir, "index.txt").appendText("%03d\t%s\t%s\n".format(shot, if (ok) "ok" else "fail", name)) }
        Log.i(TAG, "Shot $shot $name ok=$ok")
    }

    private fun outOfBudget() = shot >= MAX_SHOTS || SystemClock.uptimeMillis() - started > TIME_BUDGET

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

    // ---- set up (as in Atc3UiSmokeTest) -----------------------------------------------------------

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

        private const val TAG = "RU_SCREENS"
        private const val PKG = "info.nightscout.androidaps"
        private const val PROFILE_NAME = "LocalProfile1"
        private const val PROFILE_STORE_TIMEOUT = 20_000L
        private const val INIT_TIMEOUT = 60_000L
        private const val IDLE_MS = 300L
        private const val SETTLE_MS = 1_500L
        private const val MAX_ITEMS = 30
        private const val MAX_CHILDREN = 8
        private const val MAX_PAGES = 3
        private const val MAX_SHOTS = 300
        private const val TIME_BUDGET = 50 * 60_000L
    }
}
