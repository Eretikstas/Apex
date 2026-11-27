package app.aaps.plugins.learner.ui

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
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import app.aaps.plugins.learner.BasalChange
import app.aaps.plugins.learner.BasalProposal
import app.aaps.plugins.learner.CannulaHint
import app.aaps.plugins.learner.DuraProposal
import app.aaps.plugins.learner.PctProposal
import app.aaps.plugins.learner.HourResult
import app.aaps.plugins.learner.HourSource
import app.aaps.plugins.learner.LearnerPlugin.Status
import app.aaps.plugins.learner.SensorHint
import app.aaps.plugins.learner.SensorHintKind
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config as RoboConfig
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Pictures of the learner screen for a person to look at, in Russian: a basal proposal, the confirm window after
 * "Внести", and a dura proposal. Writes PNG files to `build/learner-shots` (CI uploads them). Test numbers only.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RoboConfig(sdk = [35], qualifiers = "ru-rRU-w411dp-h1600dp-port-xhdpi")
class LearnerScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val preferences: Preferences = mock()
    private val config: Config = mock()
    private val dateUtil: DateUtil = mock()
    private val profileUtil: ProfileUtil = mock()

    private val profileBasal = doubleArrayOf(
        0.40, 0.40, 0.35, 0.20, 0.20, 0.20, 0.20, 0.30, 0.40, 0.40, 0.35, 0.35,
        0.40, 0.40, 0.45, 0.45, 0.45, 0.50, 0.50, 0.50, 0.45, 0.45, 0.45, 0.40
    )

    /** What the last card measured: night hours measured, day hours mostly estimates, two hours without data. */
    private val hours = (0 until 24).map { h ->
        when (h) {
            in 3..8   -> HourResult(h, profileBasal[h], profileBasal[h] * if (h in 3..6) 1.35 else 1.05, HourSource.MEASURED, 3 + h % 2)
            12, 13    -> HourResult(h, profileBasal[h], null, HourSource.NONE, 0)
            else      -> HourResult(h, profileBasal[h], profileBasal[h] * 0.97, HourSource.ESTIMATE, 0)
        }
    }

    private val basalProposal = BasalProposal(
        "Профиль", NOW - 2 * HOUR,
        listOf(BasalChange(3, 0.20, 0.23), BasalChange(4, 0.20, 0.24), BasalChange(5, 0.20, 0.24), BasalChange(6, 0.20, 0.23))
    )

    @Before
    fun setUp() {
        whenever(preferences.observe(StringKey.GeneralDarkMode)).thenReturn(MutableStateFlow("light"))
        whenever(config.AAPSCLIENT).thenReturn(false)
        whenever(profileUtil.units).thenReturn(GlucoseUnit.MMOL)
        whenever(dateUtil.dateAndTimeString(any())).thenAnswer { if ((it.arguments[0] as Long) > NOW) "24.11.2026 06:00" else "20.11.2026 06:00" }
    }

    @Test
    fun basalProposal() = shoot("learner_basal_proposal", Status(NOW - 2 * HOUR, NOW + 4 * DAY - 2 * HOUR, basalProposal, null, Pair(1.2, 2.1), 0.5, hours))

    @Test
    fun basalConfirm() {
        show(Status(NOW - 2 * HOUR, NOW + 4 * DAY - 2 * HOUR, basalProposal, null, Pair(1.2, 2.1), 0.5, hours))
        compose.onNodeWithText("Внести").performClick()
        compose.waitForIdle()
        save("learner_basal_confirm") { compose.onNode(isDialog()).captureToImage().asAndroidBitmap() }
    }

    @Test
    fun pctProposal() = shoot(
        "learner_pct_proposal",
        Status(NOW - 2 * HOUR, NOW + 4 * DAY - 2 * HOUR, null, null, Pair(1.2, 2.1), 0.5, hours,
            pctProposal = PctProposal(100, 90, NOW - HOUR, 3.0, 5.2), lastPct = Triple(3.0, 5.2, false))
    )

    @Test
    fun duraProposal() = shoot(
        "learner_dura_proposal",
        Status(NOW - 2 * HOUR, NOW + 4 * DAY - 2 * HOUR, null, DuraProposal(0.5, 0.75, NOW - 2 * HOUR, 3.4, 1.9), Pair(3.4, 1.9), 0.5, hours)
    )

    @Test
    fun sensorHint() = shoot(
        "learner_sensor_hint",
        Status(NOW - 2 * HOUR, NOW + 4 * DAY - 2 * HOUR, null, null, Pair(1.2, 2.1), 0.5, hours,
            sensorHints = true, sensorHint = SensorHint(SensorHintKind.JUMP, NOW - 10 * HOUR, NOW - 10 * 60_000L, 54.0, 81.0),
            cannulaHints = true, cannulaHint = CannulaHint(66.0, 47.0))
    )

    private fun shoot(name: String, status: Status) {
        show(status)
        save(name) { compose.onNodeWithTag(TAG).captureToImage().asAndroidBitmap() }
    }

    private fun show(status: Status) {
        compose.setContent {
            Locals {
                Box(Modifier.requiredSize(411.dp, 1600.dp).testTag(TAG)) {
                    Surface(Modifier.fillMaxSize()) {
                        LearnerScreenContent(status = status, onAccept = {}, onRefuse = {}, onAcceptDura = {}, onRefuseDura = {})
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun save(name: String, bitmap: () -> Bitmap) {
        val dir = File("build/learner-shots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Composable
    private fun Locals(content: @Composable () -> Unit) {
        CompositionLocalProvider(
            LocalPreferences provides preferences,
            LocalConfig provides config,
            LocalDateUtil provides dateUtil,
            LocalProfileUtil provides profileUtil
        ) { AapsTheme { content() } }
    }

    private companion object {

        const val TAG = "learner_shot"
        const val NOW = 1_795_000_000_000L
        const val HOUR = 3_600_000L
        const val DAY = 24 * HOUR
    }
}
