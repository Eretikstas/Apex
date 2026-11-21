package app.aaps.ui.compose.overview.ring

import androidx.compose.foundation.layout.Arrangement
import app.aaps.core.interfaces.overview.graph.BgType
import app.aaps.core.ui.compose.LocalProfileUtil
import app.aaps.core.ui.compose.metroViewModel
import app.aaps.ui.compose.overview.OverviewChipsColumn
import app.aaps.ui.compose.overview.OverviewStatusSection
import kotlin.math.abs
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import app.aaps.core.ui.compose.LocalPreferences
import app.aaps.core.interfaces.overview.graph.SeriesType
import app.aaps.core.keys.BooleanNonKey
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.data.model.ActiveSceneState
import app.aaps.core.data.model.RM
import app.aaps.core.data.model.TT
import app.aaps.core.interfaces.overview.graph.TbrState
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.LocalConfig
import app.aaps.core.ui.compose.navigation.NavigationRequest
import app.aaps.core.ui.compose.preference.PreferenceSubScreenDef
import app.aaps.ui.compose.main.TempTargetChipState
import app.aaps.ui.compose.manageSheet.ManageViewModel
import app.aaps.ui.compose.overview.aapsClient.AapsClientStatusCard
import app.aaps.ui.compose.overview.chips.ChipsViewModel
import app.aaps.ui.compose.overview.chips.AutoIsfChip
import app.aaps.ui.compose.overview.graphs.GraphViewModel
import app.aaps.ui.compose.overview.graphs.GraphsSection
import app.aaps.ui.compose.overview.statusLights.StatusViewModel
import app.aaps.ui.compose.scenes.ActiveSceneBanner

/**
 * The "Кольцо" overview for a phone in portrait (our addition, see docs/OUR_CHANGES.md): the ring with glucose and time in
 * range, the period numbers, the next hour, the plain "why" of the loop's last decision, then the same command chips,
 * AutoISF chip, status lights and graphs as the usual stacked screen. It only reads; it changes nothing in the loop.
 */
@Composable
fun OverviewScreenRing(
    profileName: String,
    isProfileModified: Boolean,
    profileProgress: Float,
    profileSceneManaged: Boolean = false,
    tempTargetText: String,
    tempTargetState: TempTargetChipState,
    tempTargetProgress: Float,
    tempTargetReason: TT.Reason?,
    tempTargetSceneManaged: Boolean = false,
    runningMode: RM.Mode?,
    runningModeText: String,
    runningModeRemaining: String,
    runningModeProgress: Float,
    runningModeSceneManaged: Boolean = false,
    tbrState: TbrState,
    tbrRate: Double = 0.0,
    tbrPercent: Int? = null,
    smbEnabled: Boolean,
    smbBlocked: Boolean = false,
    isSimpleMode: Boolean,
    graphViewModel: GraphViewModel,
    chipsViewModel: ChipsViewModel,
    manageViewModel: ManageViewModel,
    statusViewModel: StatusViewModel,
    statusLightsDef: PreferenceSubScreenDef,
    onNavigate: (NavigationRequest) -> Unit,
    onTbrChipClick: () -> Unit,
    onIobChipClick: () -> Unit,
    paddingValues: PaddingValues,
    activeSceneState: ActiveSceneState? = null,
    sceneExpired: Boolean = false,
    activeSceneChainTargetName: String? = null,
    onEndScene: () -> Unit = {},
    onDismissScene: () -> Unit = {},
    endSceneEnabled: Boolean = true,
    commandsAllowed: Boolean = true,
    formatDuration: (Long) -> String = { ms -> "${(ms / 60000L).toInt()}m" },
    /** The "time to eat" bar, under the scene bar. */
    eatReminderBanner: @Composable () -> Unit = {},
    /** Height of the bars drawn over the content in preview mode (a short window, e.g. split screen); added to the end space. */
    overlaidBarsHeight: Dp = 0.dp,
    modifier: Modifier = Modifier
) {
    val config = LocalConfig.current
    val bgInfoState by graphViewModel.bgInfoState.collectAsStateWithLifecycle()
    val sensitivityUiState by chipsViewModel.sensitivityUiState.collectAsStateWithLifecycle()
    val iobUiState by chipsViewModel.iobUiState.collectAsStateWithLifecycle()
    val cobUiState by chipsViewModel.cobUiState.collectAsStateWithLifecycle()
    val autoIsfFactorsUiState by chipsViewModel.autoIsfFactorsUiState.collectAsStateWithLifecycle()
    val statusState by statusViewModel.uiState.collectAsStateWithLifecycle()
    val ringViewModel: RingViewModel = metroViewModel()
    val ringState by ringViewModel.uiState.collectAsStateWithLifecycle()
    val bgReadings by graphViewModel.bgReadingsFlow.collectAsStateWithLifecycle()
    val predictions by graphViewModel.predictionsFlow.collectAsStateWithLifecycle()
    val now by graphViewModel.nowTimestamp.collectAsStateWithLifecycle()
    val profileUtil = LocalProfileUtil.current

    val readings = bgReadings.filter { it.type == BgType.REGULAR }.ifEmpty { bgReadings.filter { it.type == BgType.BUCKETED } }
        .sortedBy { it.timestamp }
    // The state word keeps its tested rule: the lowest prediction of the next hour, all lines.
    val future = predictions.filter { it.timestamp in (now + 1)..(now + 60 * 60_000L) }
    val minPredMgdl = future.minOfOrNull { it.value }?.let { toMgdl(it) }
    // The graph and the value: one line of the loop's own prediction up to the chosen horizon, UAM first, then COB, then IOB.
    var horizon by rememberSaveable { mutableStateOf(RingHorizon.H1) }
    // One line for all horizons, chosen over the longest one (3 h): UAM first, then COB, then IOB.
    val ahead = predictions.filter { it.timestamp in (now + 1)..(now + RingHorizon.H3.hours * 3_600_000L + RingLogic.POINT_TOLERANCE_MS) }
    val mainLine = ahead.filter { it.type == BgType.UAM_PREDICTION }.ifEmpty { ahead.filter { it.type == BgType.COB_PREDICTION } }
        .ifEmpty { ahead.filter { it.type == BgType.IOB_PREDICTION } }
    val mainPrediction = mainLine.filter { it.timestamp <= now + horizon.hours * 3_600_000L + RingLogic.POINT_TOLERANCE_MS }
    val linePoints = mainLine.map { it.timestamp to it.value }
    val forecast = RingLogic.forecast(linePoints, now, horizon.hours * 3_600_000L)
    // The value at each full hour ahead, only where the loop's line reaches it.
    val perHour = RingHorizon.entries.map { h ->
        h.hours to RingLogic.forecast(linePoints, now, h.hours * 3_600_000L)?.takeIf { it.reachesHorizon }
            ?.let { profileUtil.fromMgdlToStringWithUnits(toMgdl(it.value)) }
    }
    val bgMgdl = bgInfoState.bgInfo?.let { if (it.isOutdated) null else toMgdl(it.bgValue) }
    val deltaMgdl = if (readings.size >= 2 && abs(readings.last().timestamp - readings[readings.size - 2].timestamp) <= 6 * 60_000L)
        toMgdl(readings.last().value - readings[readings.size - 2].value) else null
    val hour = RingLogic.hour(bgMgdl, deltaMgdl, minPredMgdl, ringState.why?.limited == true)
    val forecastText = forecast?.let { profileUtil.fromMgdlToStringWithUnits(toMgdl(it.value)) }
    val eventualText = ringState.why?.eventualMgdl?.let { profileUtil.fromMgdlToStringWithUnits(it) }

    var statusExpanded by rememberSaveable { mutableStateOf(false) }

    // Once: the big graph below drops its prediction overlay, the tile above shows the loop's prediction. The user can
    // turn it on again in the graph's pencil menu (PRED); this never runs a second time.
    val preferences = LocalPreferences.current
    LaunchedEffect(Unit) {
        if (!preferences.get(BooleanNonKey.OverviewRingPredictionOffDone)) {
            val config = graphViewModel.graphConfigFlow.value
            if (SeriesType.PREDICTIONS in config.bgOverlays) graphViewModel.updateGraphConfig(config.copy(bgOverlays = config.bgOverlays - SeriesType.PREDICTIONS))
            preferences.put(BooleanNonKey.OverviewRingPredictionOffDone, true)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(paddingValues)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ActiveSceneBanner(
            activeState = activeSceneState,
            expired = sceneExpired,
            chainTargetName = activeSceneChainTargetName,
            onEndClick = onEndScene,
            onDismiss = onDismissScene,
            endEnabled = endSceneEnabled,
            formatDuration = formatDuration
        )
        eatReminderBanner()
        // The loop's command chips (running mode, profile, temp target, temp basal) at the top, as on the usual screen.
        OverviewChipsColumn(
            runningMode = runningMode,
            runningModeText = runningModeText,
            runningModeRemaining = runningModeRemaining,
            runningModeProgress = runningModeProgress,
            runningModeSceneManaged = runningModeSceneManaged,
            smbEnabled = smbEnabled,
            smbBlocked = smbBlocked,
            profileName = profileName,
            isProfileModified = isProfileModified,
            profileProgress = profileProgress,
            profileSceneManaged = profileSceneManaged,
            tempTargetText = tempTargetText,
            tempTargetState = tempTargetState,
            tempTargetProgress = tempTargetProgress,
            tempTargetReason = tempTargetReason,
            tempTargetSceneManaged = tempTargetSceneManaged,
            tbrState = tbrState,
            tbrRate = tbrRate,
            tbrPercent = tbrPercent,
            iobUiState = iobUiState,
            cobUiState = cobUiState,
            sensitivityUiState = sensitivityUiState,
            onNavigate = onNavigate,
            onTbrChipClick = onTbrChipClick,
            onIobChipClick = onIobChipClick,
            commandsAllowed = commandsAllowed,
            showIobCob = false,
            modifier = Modifier.fillMaxWidth()
        )

        // Cannula, insulin left, sensor and pump battery right under the chips, seen without scrolling.
        OverviewStatusSection(
            sensorStatus = statusState.sensorStatus,
            insulinStatus = statusState.insulinStatus,
            cannulaStatus = statusState.cannulaStatus,
            batteryStatus = statusState.batteryStatus,
            showFill = statusState.showFill,
            showPumpBatteryChange = statusState.showPumpBatteryChange,
            commandsAllowed = commandsAllowed,
            onNavigate = onNavigate,
            statusLightsDef = statusLightsDef,
            expanded = statusExpanded,
            onExpandedChange = { statusExpanded = it }
        )

        RingHero(
            bgInfo = bgInfoState.bgInfo,
            timeAgo = bgInfoState.timeAgoText,
            stats = ringState.stats,
            iobText = iobUiState.text,
            cobText = cobUiState.text,
            onIobClick = onIobChipClick
        )
        RingPeriodRow(period = ringState.period, stats = ringState.stats, onPick = ringViewModel::selectPeriod)
        RingHourTile(
            hour = hour, readings = readings, prediction = mainPrediction, now = now, horizon = horizon, onHorizon = { horizon = it },
            forecast = forecast, forecastText = forecastText, perHour = perHour
        )
        RingWhyCard(why = ringState.why, whyTime = ringState.whyTime, eventualText = eventualText, notes = ringState.notes)

        AutoIsfChip(
            state = autoIsfFactorsUiState,
            onNavigate = onNavigate,
            onExpandedChange = chipsViewModel::setAutoIsfChipExpanded,
            onTogglePin = chipsViewModel::toggleAutoIsfPin
        )


        if (config.AAPSCLIENT) {
            val nsClientStatus by graphViewModel.nsClientStatusFlow.collectAsStateWithLifecycle()
            val flavorTint = when {
                config.AAPSCLIENT3 -> AapsTheme.generalColors.flavorClient3Tint
                config.AAPSCLIENT2 -> AapsTheme.generalColors.flavorClient2Tint
                else               -> AapsTheme.generalColors.flavorClient1Tint
            }
            AapsClientStatusCard(
                statusData = nsClientStatus,
                flavorTint = flavorTint
            )
        }

        GraphsSection(
            graphViewModel = graphViewModel, isSimpleMode = isSimpleMode, iobText = iobUiState.text, cobText = cobUiState.text,
            overlaidBarsHeight = overlaidBarsHeight
        )
    }
}
