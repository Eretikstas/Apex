package app.aaps.ui.compose.overview.ring

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import app.aaps.core.ui.compose.AapsRingTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.RM
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.interfaces.aps.APSResult
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.aps.LoopNote
import app.aaps.core.interfaces.aps.LoopNotes
import app.aaps.core.interfaces.overview.graph.BgDataPoint
import app.aaps.core.interfaces.overview.graph.BgInfoData
import app.aaps.core.interfaces.overview.graph.BgRange
import app.aaps.core.interfaces.overview.graph.BgType
import app.aaps.core.interfaces.overview.graph.TbrState
import app.aaps.core.interfaces.overview.graph.TimeRange
import app.aaps.core.interfaces.rx.events.Event
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.UnitDoubleKey
import app.aaps.core.ui.compose.LocalMetroViewModelFactory
import app.aaps.core.ui.compose.preference.PreferenceSubScreenDef
import app.aaps.ui.compose.main.MainNavigationBar
import app.aaps.ui.compose.main.MainTopBar
import app.aaps.ui.compose.main.TempTargetChipState
import app.aaps.ui.search.SearchUiState
import app.aaps.ui.compose.overview.OverviewScreen
import app.aaps.ui.compose.testing.AapsScreenFixture
import app.aaps.ui.compose.testing.OverviewViewModelFixture
import app.aaps.ui.compose.testing.OverviewViewModelFixture.Companion.NOW
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.ViewModelAssistedFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale
import kotlin.math.sin
import kotlin.reflect.KClass
import app.aaps.core.ui.R as CoreUiR

/**
 * Pictures of the "Кольцо" overview, in Russian and mmol/L, day and night, for a person to look at. Writes PNG files to
 * `build/ring-shots` (CI uploads them). The only check is that the screen draws; the pictures are the result.
 *
 * Test data: 6 hours of glucose (dinner, then a slow rise now), the loop's last answer an SMB on that rise, and a learner note.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ru-rRU-w411dp-h1500dp-port-xhdpi")
class RingScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var screen: AapsScreenFixture
    private lateinit var viewModels: OverviewViewModelFixture
    private val loopNotes = LoopNotes()
    private val viewModelStoreOwner = object : ViewModelStoreOwner {
        override val viewModelStore: ViewModelStore = ViewModelStore()
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        screen = AapsScreenFixture()
        viewModels = OverviewViewModelFixture(screen)
        mmol()
        glucose()
        loopAnswer()
        loopNotes.put(LoopNote("learner", "Ученик: базал проверен сегодня, менять не нужно.", NOW, NOW + 3_600_000L))
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun day() = shoot("ring_day", dark = false)

    @Test
    fun night() = shoot("ring_night", dark = true)

    /** The phone screen as one sees it: the app's top bar and bottom bar around the start of the ring screen. */
    @Test
    fun dayScreen() = shoot("ring_day_screen", dark = false, phone = true)

    @Test
    fun nightScreen() = shoot("ring_night_screen", dark = true, phone = true)

    private fun shoot(name: String, dark: Boolean, phone: Boolean = false) {
        whenever(screen.preferences.observe(StringKey.GeneralDarkMode)).thenReturn(MutableStateFlow(if (dark) "dark" else "light"))
        val factory = ShotViewModelFactory(mapOf(RingViewModel::class to { ringViewModel() }))
        compose.setContent {
            CompositionLocalProvider(
                LocalMetroViewModelFactory provides factory,
                LocalViewModelStoreOwner provides viewModelStoreOwner
            ) {
                Portrait(dark, if (phone) PHONE_HEIGHT_DP else HEIGHT_DP) {
                    screen.Content {
                        // The ring look (light by day, dark by night), bars included, as in MainScreen.
                        AapsRingTheme { Surface(Modifier.fillMaxSize()) {
                            if (phone) Column(Modifier.fillMaxSize()) {
                                MainTopBar(SearchUiState(), onMenuClick = {}, onPreferencesClick = {}, onSearchQueryChange = {}, onSearchClear = {}, onSearchActiveChange = {})
                                Box(Modifier.weight(1f)) { Overview(dark) }
                                MainNavigationBar(onManageClick = {}, onTreatmentClick = {}, objectivesProgressText = "0/10", objectivesProgressDescription = "0 / 10")
                            } else Overview(dark)
                        } }
                    }
                }
            }
        }
        compose.waitForIdle()
        val dir = File("build/ring-shots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { compose.onNodeWithTag(SHOT_TAG).captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun ringViewModel(): RingViewModel {
        val rxBus = viewModels.rxBus
        whenever(rxBus.toFlow(any<KClass<Event>>())).thenReturn(emptyFlow())
        whenever(viewModels.persistenceLayer.observeChanges(GV::class)).thenReturn(emptyFlow())
        return RingViewModel(viewModels.loop, viewModels.persistenceLayer, screen.dateUtil, loopNotes, rxBus)
    }

    /** The user's units: mmol/L with a comma, as on the phone. */
    private fun mmol() {
        whenever(screen.profileUtil.units).thenReturn(GlucoseUnit.MMOL)
        whenever(screen.profileUtil.fromMgdlToStringWithUnits(anyOrNull())).thenAnswer { inv ->
            (inv.arguments[0] as Double?)?.let { String.format(Locale("ru"), "%.1f ммоль/л", it / 18.0) } ?: "—"
        }
        whenever(screen.preferences.observe(BooleanKey.OverviewRingShowGraphs)).thenReturn(MutableStateFlow(true))
        whenever(screen.preferences.get(UnitDoubleKey.OverviewHighMark)).thenReturn(10.0)
        whenever(screen.preferences.get(UnitDoubleKey.OverviewLowMark)).thenReturn(3.9)
        whenever(screen.preferences.observe(UnitDoubleKey.OverviewHighMark)).thenReturn(MutableStateFlow(10.0))
        whenever(screen.preferences.observe(UnitDoubleKey.OverviewLowMark)).thenReturn(MutableStateFlow(3.9))
        whenever(screen.dateUtil.now()).thenReturn(NOW)
        whenever(screen.dateUtil.timeString(any<Long>())).thenAnswer { inv ->
            val z = Instant.ofEpochMilli(inv.arguments[0] as Long).atZone(ZoneOffset.ofHours(3))
            String.format(Locale.ROOT, "%02d:%02d", z.hour, z.minute)
        }
        whenever(screen.dateUtil.dateAndTimeString(any<Long>())).thenReturn("20.11 21:30")
    }

    /** 6 h of readings every 5 min: a dinner rise to about 11 and back to 6, then a new slow rise to 8.6 now. */
    private fun glucose() {
        val points = (72 downTo 0).map { k ->
            val h = (72 - k) / 12.0
            val meal = if (h < 4.0) 90.0 * sin(Math.PI * h / 4.0) else 0.0
            val rise = if (h > 4.5) 50.0 * (h - 4.5) / 1.5 else 0.0
            val mgdl = 108.0 + meal + rise
            Triple(NOW - k * 300_000L, mgdl, if (mgdl > 180) BgRange.HIGH else if (mgdl < 70) BgRange.LOW else BgRange.IN_RANGE)
        }
        viewModels.cache.timeRangeFlow.value = TimeRange(NOW - 6 * 3_600_000L, NOW, NOW + 3_600_000L)
        viewModels.cache.bgReadingsFlow.value = points.map { BgDataPoint(it.first, it.second / 18.0, it.third, BgType.REGULAR) }
        val last = points.last().second
        viewModels.cache.bgInfoFlow.value = BgInfoData(
            bgValue = last / 18.0, bgText = String.format(Locale("ru"), "%.1f", last / 18.0), bgRange = BgRange.IN_RANGE,
            isOutdated = false, timestamp = NOW - 60_000L, trendArrow = TrendArrow.FORTY_FIVE_UP,
            trendDescription = "", delta = 0.3, deltaText = "+0,3",
            shortAvgDelta = null, shortAvgDeltaText = null, longAvgDelta = null, longAvgDeltaText = null
        )
        viewModels.cache.predictionsFlow.value = (1..36).map { k ->
            BgDataPoint(NOW + k * 300_000L, (last + 14.0 * sin(Math.PI * minOf(k, 16) / 16.0) - 1.2 * maxOf(0, k - 16)) / 18.0, BgRange.IN_RANGE, BgType.UAM_PREDICTION)
        }
        val day = (288 downTo 0).map { k ->
            val v = 130.0 + 70.0 * sin(k / 40.0) - if (k % 97 < 4) 70.0 else 0.0
            GV(timestamp = NOW - k * 300_000L, value = v, raw = v, noise = null, trendArrow = TrendArrow.NONE, sourceSensor = SourceSensor.UNKNOWN)
        }
        runBlocking { whenever(viewModels.persistenceLayer.getBgReadingsDataFromTimeToTime(any(), any(), any())).thenReturn(day) }
    }

    /** The loop's last answer: a small SMB on a rise, AutoISF reason text as the loop writes it. */
    private fun loopAnswer() {
        val request: APSResult = mock()
        whenever(request.reason).thenReturn(
            "COB: 12, Dev: 18, BGI: -1.1, ISF: 9.0, CR: 25.8, Target: 6, minPredBG 6.1, minGuardBG 5.8, IOBpredBG 6.0; " +
                "Eventual BG 9.1 >= 6.0, insulinReq 0.32. Microbolusing 0.15U. "
        )
        whenever(request.smb).thenReturn(0.15)
        whenever(request.isTempBasalRequested).thenReturn(false)
        whenever(request.date).thenReturn(NOW - 120_000L)
        whenever(request.json()).thenReturn(null)
        whenever(request.mealData).thenReturn(null)
        val run = Loop.LastRun().apply { this.request = request }
        whenever(viewModels.loop.lastRun).thenReturn(run)
    }

    /** Day: the profile basal runs; night: a temp basal above it, with its percent. */
    @Composable
    private fun Overview(dark: Boolean = false) {
        OverviewScreen(
            ringLayout = true,
            profileName = "Профиль", isProfileModified = false, profileProgress = 0f,
            tempTargetText = "6,0", tempTargetState = TempTargetChipState.None,
            tempTargetProgress = 0f, tempTargetReason = null,
            runningMode = RM.Mode.CLOSED_LOOP, runningModeText = "Замкнутый цикл",
            runningModeRemaining = "", runningModeProgress = 0f,
            tbrState = if (dark) TbrState.HIGH else TbrState.NONE, tbrRate = if (dark) 0.54 else 0.45,
            tbrPercent = if (dark) 120 else null, smbEnabled = true, isSimpleMode = false, calcProgress = 100,
            graphViewModel = viewModels.graphViewModel, chipsViewModel = viewModels.chipsViewModel,
            manageViewModel = viewModels.manageViewModel, statusViewModel = viewModels.awaitStatusItems(),
            statusLightsDef = PreferenceSubScreenDef(key = "statuslights", titleResId = CoreUiR.string.statuslights),
            onNavigate = {}, onTbrChipClick = {}, onIobChipClick = {},
            notifications = emptyList(), onDismissNotification = {}, onNotificationActionClick = {},
            autoShowNotificationSheet = false, onAutoShowConsumed = {},
            paddingValues = PaddingValues(0.dp)
        )
    }

    /** A tall phone in portrait, so the whole screen fits in one picture. */
    @Composable
    private fun Portrait(dark: Boolean, heightDp: Int, content: @Composable () -> Unit) {
        val base = LocalConfiguration.current
        val configuration = Configuration(base).apply {
            screenWidthDp = WIDTH_DP
            screenHeightDp = heightDp
            orientation = Configuration.ORIENTATION_PORTRAIT
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                (if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO)
        }
        CompositionLocalProvider(LocalConfiguration provides configuration) {
            Box(Modifier.requiredSize(WIDTH_DP.dp, heightDp.dp).testTag(SHOT_TAG)) { content() }
        }
    }

    private class ShotViewModelFactory(
        override val viewModelProviders: Map<KClass<out ViewModel>, () -> ViewModel>
    ) : MetroViewModelFactory() {

        override val assistedFactoryProviders: Map<KClass<out ViewModel>, () -> ViewModelAssistedFactory> = emptyMap()
        override val manualAssistedFactoryProviders: Map<KClass<out ManualViewModelAssistedFactory>, () -> ManualViewModelAssistedFactory> = emptyMap()
    }

    private companion object {

        const val WIDTH_DP = 411
        const val HEIGHT_DP = 1500
        const val PHONE_HEIGHT_DP = 891
        const val SHOT_TAG = "ring_shot"
    }
}
