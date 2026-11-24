package app.aaps.e2e

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import app.aaps.ComposeMainActivity
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.core.keys.BooleanNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.implementation.plugin.PluginStore
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import javax.inject.Inject

/** Synthetic UI smoke for the nested AutoISF preference screen. */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class AutoIsfWeightsUiTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var preferences: Preferences
    @Inject lateinit var pluginStore: PluginStore
    @Inject lateinit var pluginList: List<@JvmSuppressWildcards PluginBase>
    @Inject lateinit var configBuilder: ConfigBuilder
    @Inject lateinit var config: Config

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val device get() = UiDevice.getInstance(instrumentation)
    private val ctx get() = instrumentation.targetContext

    @Before
    fun setUp() {
        clearAllSharedPrefs()
        hiltRule.inject()
        preferences.put(BooleanNonKey.GeneralSetupWizardProcessed, true)
        pluginStore.plugins = pluginList
        configBuilder.initialize()
        config.initCompleted()
    }

    @After
    fun tearDown() {
        runCatching { device.pressBack() }
        clearAllSharedPrefs()
    }

    @Test
    fun allSixWeightControlsAreReachable() {
        val intent = Intent(ctx, ComposeMainActivity::class.java)
            .putExtra(ComposeMainActivity.EXTRA_NAVIGATE_ROUTE, "preference_screen/auto_isf_settings")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        ctx.startActivity(intent)

        val expected = listOf(
            "BG acceleration ISF weight",
            "BG brake ISF weight",
            "Low BG range weight",
            "High BG range weight",
            "Post-prandial ISF weight",
            "Duration ISF weight"
        )
        val found = linkedSetOf<String>()
        val deadline = SystemClock.uptimeMillis() + 60_000L

        while (SystemClock.uptimeMillis() < deadline && found.size < expected.size) {
            device.waitForIdle(500)
            expected.filter { it !in found }.forEach { label ->
                if (device.findObject(By.text(label)) != null) found += label
            }
            if (found.size < expected.size) {
                device.swipe(
                    device.displayWidth / 2,
                    device.displayHeight * 3 / 4,
                    device.displayWidth / 2,
                    device.displayHeight / 4,
                    35
                )
                SystemClock.sleep(350)
            }
        }

        val missing = expected.filterNot { it in found }
        val evidenceDir = File(ctx.getExternalFilesDir(null), "screens").apply { mkdirs() }
        runCatching { device.takeScreenshot(File(evidenceDir, "autoisf-weights-ui.png")) }
        assertTrue(
            "AutoISF settings screen did not expose all six weight controls. Found=$found; missing=$missing; currentPackage=${device.currentPackageName}",
            missing.isEmpty()
        )
    }

    private fun clearAllSharedPrefs() {
        File(ctx.applicationInfo.dataDir, "shared_prefs").listFiles()?.forEach { file ->
            if (file.name.endsWith(".xml")) {
                ctx.getSharedPreferences(file.name.removeSuffix(".xml"), Context.MODE_PRIVATE)
                    .edit().clear().commit()
            }
        }
    }
}
