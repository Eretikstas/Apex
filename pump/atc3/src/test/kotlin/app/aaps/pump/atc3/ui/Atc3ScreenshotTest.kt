package app.aaps.pump.atc3.ui

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Refresh
import app.aaps.core.ui.compose.ScreenAction
import app.aaps.core.ui.compose.StatusLevel
import app.aaps.core.ui.compose.pump.PumpInfoRow
import app.aaps.core.ui.compose.pump.PumpOverviewScreen
import app.aaps.core.ui.compose.pump.PumpOverviewUiState
import app.aaps.pump.atc3.R
import app.aaps.core.ui.R as CoreUiR

/**
 * A picture of the ATC3 pump screen in Russian, for a person to look at: the real pump screen of AAPS filled the way
 * the ATC3 driver fills it (same rows, same texts), with example values of a paired, running pump.
 * Writes `build/screen-shots/atc3_pump.png`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RoboConfig(sdk = [35], qualifiers = "ru-rRU-w411dp-h1200dp-port-xhdpi")
class Atc3ScreenshotTest {

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

    @Test
    fun pump() {
        val c = RuntimeEnvironment.getApplication()
        val res = c.resources
        val state = PumpOverviewUiState(
            infoRows = listOf(
                PumpInfoRow(c.getString(CoreUiR.string.serial_number), "A1B2C3D4"),
                PumpInfoRow(c.getString(R.string.atc3_connection_label), c.getString(R.string.atc3_connection_connected)),
                PumpInfoRow(c.getString(R.string.atc3_last_answer_label), res.getQuantityString(R.plurals.atc3_minutes_ago, 2, 2)),
                PumpInfoRow(c.getString(R.string.atc3_pump_state_label), c.getString(R.string.atc3_state_running), StatusLevel.NORMAL),
                PumpInfoRow(c.getString(CoreUiR.string.reservoir_label), c.getString(R.string.atc3_reservoir_value, "142", 300)),
                PumpInfoRow(c.getString(R.string.atc3_battery_label), c.getString(R.string.atc3_battery_value, 80, 1.45)),
                PumpInfoRow(c.getString(R.string.atc3_max_bolus_label), "3,00 ЕД"),
                PumpInfoRow(c.getString(R.string.atc3_max_basal_label), "2,00 ЕД/ч"),
                PumpInfoRow(c.getString(R.string.atc3_bolus_speed_label), c.getString(R.string.atc3_bolus_speed_normal)),
                PumpInfoRow(c.getString(R.string.atc3_password_label), c.getString(R.string.atc3_password_none)),
                PumpInfoRow(c.getString(R.string.atc3_day_account_label), c.getString(R.string.atc3_day_account_value, 9.85, 9.85, 0.0)),
                PumpInfoRow(c.getString(CoreUiR.string.firmware), "1.1.1"),
                PumpInfoRow(c.getString(R.string.atc3_driver_version_label), "test")
            ),
            primaryActions = listOf(
                ScreenAction(c.getString(CoreUiR.string.refresh), Icons.Filled.Refresh) {},
                ScreenAction(c.getString(R.string.atc3_stop_pump), Icons.Filled.Pause) {}
            ),
            managementActions = listOf(
                ScreenAction(c.getString(R.string.atc3_change_pump), Icons.Filled.Bluetooth) {},
                ScreenAction(c.getString(R.string.atc3_change_password), Icons.Filled.Key) {}
            )
        )
        shoot("atc3_pump", 1200) { PumpOverviewScreen(state = state) }
    }
}
