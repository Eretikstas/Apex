package app.aaps.plugins.sibionics.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.LocalConfig
import app.aaps.core.ui.compose.LocalDateUtil
import app.aaps.core.ui.compose.LocalPreferences
import app.aaps.core.ui.compose.LocalProfileUtil
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config as RoboConfig
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.Locale
import app.aaps.plugins.sibionics.SibionicsPlugin
import app.aaps.plugins.sibionics.SibionicsSession

/**
 * Pictures of the Sibionics source screen in Russian, for a person to look at: the first step (code from the box) and
 * a sensor that sends values. Example values only. Writes PNG files to `build/screen-shots`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RoboConfig(sdk = [35], qualifiers = "ru-rRU-w411dp-h900dp-port-xhdpi")
class SibionicsScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val preferences: Preferences = mock()
    private val config: Config = mock()
    private val dateUtil: DateUtil = mock()
    private val profileUtil: ProfileUtil = mock()

    @Before
    fun setUpTheme() {
        whenever(preferences.observe(StringKey.GeneralDarkMode)).thenReturn(MutableStateFlow("light"))
        whenever(config.AAPSCLIENT).thenReturn(false)
        whenever(profileUtil.units).thenReturn(GlucoseUnit.MMOL)
    }

    private fun shoot(name: String, heightDp: Int, content: @Composable () -> Unit) {
        compose.setContent {
            CompositionLocalProvider(
                LocalPreferences provides preferences,
                LocalConfig provides config,
                LocalDateUtil provides dateUtil,
                LocalProfileUtil provides profileUtil
            ) {
                AapsTheme {
                    Box(Modifier.requiredSize(411.dp, heightDp.dp).testTag("shot")) { Surface(Modifier.fillMaxSize()) { content() } }
                }
            }
        }
        compose.waitForIdle()
        val dir = File("build/screen-shots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { compose.onNodeWithTag("shot").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun screen(status: SibionicsPlugin.Status) {
        SibionicsScreenContent(
            status = status, found = emptyList(), scanning = false, initialCode = "", hasPermission = true,
            formatGlucose = { v -> v?.let { String.format(Locale("ru"), "%.1f ммоль/л", it / 18.0) } ?: "—" },
            formatTime = { t -> if (t > 1_700_000_000_000L) "20.11.2026 22:41" else "13.11.2026 09:05" },
            onSetCode = { true }, onScan = {}, onChoose = {}, onForget = {}
        )
    }

    @Test
    fun firstStep() = shoot("sibionics_first_step", 900) { screen(SibionicsPlugin.Status()) }

    @Test
    fun streaming() = shoot("sibionics_streaming", 900) {
        screen(
            SibionicsPlugin.Status(
                sensorName = "SB2 1234567", sensitivity = 1.26f, sensitivityFromCode = true, bleName = "SIBIONICS-1A2B",
                connected = true, phase = SibionicsSession.Phase.STREAMING, sessionStart = 1_699_000_000_000L,
                lastSeen = 1_800_000_000_000L, lastGlucoseMgdl = 112.0, nextIndex = 11200
            )
        )
    }
}
