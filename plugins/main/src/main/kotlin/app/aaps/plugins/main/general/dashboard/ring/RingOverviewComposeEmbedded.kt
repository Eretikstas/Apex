package app.aaps.plugins.main.general.dashboard.ring

import app.aaps.core.ui.compose.icons.IcPumpBattery
import app.aaps.core.ui.compose.icons.IcPumpCartridge
import app.aaps.core.ui.compose.icons.IcCgmInsert
import app.aaps.core.ui.compose.icons.IcCannulaChange
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.overview.graph.BgDataPoint
import app.aaps.core.interfaces.overview.graph.BgType
import app.aaps.core.keys.StringKey
import app.aaps.core.ui.UiMode
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.LocalPreferences
import app.aaps.plugins.main.R
import app.aaps.plugins.main.general.dashboard.compose.LocalDashboardHeroCommands
import app.aaps.plugins.main.general.dashboard.glass.LocalGlassHeroCommands
import app.aaps.plugins.main.general.dashboard.viewmodel.AdjustmentCardState
import app.aaps.plugins.main.general.dashboard.viewmodel.OverviewViewModel
import app.aaps.plugins.main.general.dashboard.viewmodel.StatusCardState
import app.aaps.ui.compose.overview.graphs.GraphViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs

/** The period of the ring and of the statistics next to it. */
internal enum class RingPeriod(val hours: Int) { H3(3), DAY(24), WEEK(24 * 7) }

/** Colors of the Ring skin; one set for day and one for night (the app's dark mode). */
internal data class RingPalette(
    val bg: Color, val card: Color, val text: Color, val soft: Color, val muted: Color, val line: Color, val track: Color,
    val brand: Color, val inRange: Color, val high: Color, val low: Color, val bandAlpha: Float,
    val okBg: Color, val okFg: Color, val warnBg: Color, val warnFg: Color, val stopBg: Color, val stopFg: Color,
    val segOn: Color, val segOnText: Color, val btnLine: Color, val primary: Color, val primaryText: Color,
    val stableAccent: Color, val stableText: Color, val risingAccent: Color, val risingText: Color,
    val fallingAccent: Color, val fallingText: Color,
) {

    companion object {

        val Day = RingPalette(
            bg = Color(0xFFE8F3EE), card = Color(0xFFFFFFFF), text = Color(0xFF14201C), soft = Color(0xFF33433E),
            muted = Color(0xFF4F5E59), line = Color(0xFFE1E6E4), track = Color(0xFFE1E6E4), brand = Color(0xFF0F6B55),
            inRange = Color(0xFF1F9E7A), high = Color(0xFFE08A1E), low = Color(0xFF8F2D0A), bandAlpha = 0.10f,
            okBg = Color(0xFFE3ECE8), okFg = Color(0xFF0F4D3E), warnBg = Color(0xFFFBE9D3), warnFg = Color(0xFF7A4306),
            stopBg = Color(0xFFF6DDD4), stopFg = Color(0xFF7A260A), segOn = Color(0xFF14201C), segOnText = Color(0xFFFFFFFF),
            btnLine = Color(0xFFC9D3CF), primary = Color(0xFF0F6B55), primaryText = Color(0xFFFFFFFF),
            stableAccent = Color(0xFF1F9E7A), stableText = Color(0xFF0F6B55), risingAccent = Color(0xFFC27410),
            risingText = Color(0xFF7A4306), fallingAccent = Color(0xFF8F2D0A), fallingText = Color(0xFF8F2D0A),
        )

        val Night = RingPalette(
            bg = Color(0xFF05070A), card = Color(0xFF11151B), text = Color(0xFFD6CFC4), soft = Color(0xFFB9B1A5),
            muted = Color(0xFF9C9488), line = Color(0xFF262B33), track = Color(0xFF1C2128), brand = Color(0xFF8FC7B2),
            inRange = Color(0xFF3F7A66), high = Color(0xFFC9A46A), low = Color(0xFFD0694A), bandAlpha = 0.22f,
            okBg = Color(0xFF17231F), okFg = Color(0xFF9FD3BF), warnBg = Color(0xFF2A2418), warnFg = Color(0xFFE2C48F),
            stopBg = Color(0xFF2D1A14), stopFg = Color(0xFFF2A38C), segOn = Color(0xFFD6CFC4), segOnText = Color(0xFF05070A),
            btnLine = Color(0xFF2A3038), primary = Color(0xFF2F5F51), primaryText = Color(0xFFE6EEE9),
            stableAccent = Color(0xFF4E9A80), stableText = Color(0xFF8FC7B2), risingAccent = Color(0xFFC9A46A),
            risingText = Color(0xFFE2C48F), fallingAccent = Color(0xFFD0694A), fallingText = Color(0xFFF09A80),
        )
    }
}

/**
 * The home screen of the Ring skin (docs/RING_SKIN.md): ring with BG, period statistics, the "next hour"
 * tile, the plain "why" of the last loop decision and the treatment buttons. It only reads data and opens
 * the existing dialogs; it changes nothing in the loop.
 */
@Composable
internal fun RingOverviewComposeEmbedded(
    overviewViewModel: OverviewViewModel,
    graphViewModel: GraphViewModel,
    persistenceLayer: PersistenceLayer,
    modifier: Modifier = Modifier,
) {
    val status by overviewViewModel.statusCardState.observeAsState()
    val adjustment by overviewViewModel.adjustmentState.observeAsState()
    val bgReadings by graphViewModel.bgReadingsFlow.collectAsStateWithLifecycle()
    val predictions by graphViewModel.predictionsFlow.collectAsStateWithLifecycle()

    AapsTheme {
        val preferences = LocalPreferences.current
        val darkModeValue by preferences.observe(StringKey.GeneralDarkMode).collectAsState()
        val isDark = when (UiMode.fromString(darkModeValue)) {
            UiMode.LIGHT  -> false
            UiMode.DARK   -> true
            UiMode.SYSTEM -> isSystemInDarkTheme()
        }
        val p = if (isDark) RingPalette.Night else RingPalette.Day
        val mmol = status?.unitText?.contains("mmol", ignoreCase = true) ?: true

        var period by remember { mutableStateOf(RingPeriod.DAY) }
        var stats by remember { mutableStateOf<RingStats?>(null) }
        // The graph list is not always oldest first: take the newest by time.
        val lastBgTime = bgReadings.maxOfOrNull { it.timestamp }
        LaunchedEffect(period, lastBgTime) {
            val now = System.currentTimeMillis()
            val values = withContext(Dispatchers.IO) {
                runCatching {
                    persistenceLayer.getBgReadingsDataFromTimeToTime(now - period.hours * 3_600_000L, now, true).map { it.value }
                }.getOrDefault(emptyList())
            }
            stats = RingLogic.stats(values)
        }

        val now = System.currentTimeMillis()
        val readings = remember(bgReadings) {
            bgReadings.filter { it.type == BgType.REGULAR }.ifEmpty { bgReadings.filter { it.type == BgType.BUCKETED } }
                .sortedBy { it.timestamp }
        }
        val future = predictions.filter { it.timestamp in (now + 1)..(now + 60 * 60_000L) }
        val main = future.filter { it.type == BgType.UAM_PREDICTION }.ifEmpty { future.filter { it.type == BgType.COB_PREDICTION } }
            .ifEmpty { future.filter { it.type == BgType.IOB_PREDICTION } }
        val pred30 = main.minByOrNull { abs(it.timestamp - (now + 30 * 60_000L)) }?.value
        val pred60 = main.maxByOrNull { it.timestamp }?.value
        val minPred = future.minOfOrNull { it.value }
        val maxPred = main.maxOfOrNull { it.value }
        val bgMgdl = status?.glucoseMgdl?.toDouble()
        val delta = if (readings.size >= 2 && readings.last().timestamp - readings[readings.size - 2].timestamp <= 6 * 60_000L)
            readings.last().value - readings[readings.size - 2].value else null

        val adj = adjustment
        val why = adj?.let {
            RingWhyMapper.map(
                reason = it.detailedReason,
                smbUnits = it.smb ?: 0.0,
                rateUph = if (it.tempBasalRequested) it.basal else null,
                durationMin = if (it.tempBasalRequested) it.tbrDuration else null,
                eventualMgdl = it.eventualBg,
            )
        }
        val hour = RingLogic.hourState(bgMgdl, delta, minPred, why != null && why.limited && why.action is RingAction.Smb)

        Column(
            modifier = modifier
                .fillMaxSize()
                .background(p.bg)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StatusChips(status, p)
            RingHero(status, adj, pred30, period, stats, mmol, lastBgTime, p)
            PeriodRow(period, stats, p) { period = it }
            HourTile(hour, readings, main, now, minPred, pred60, maxPred, why, mmol, p)
            WhyCard(why, adj, mmol, p)
            ActionButtons(p)
            // Room under the buttons, so the app's floating buttons never cover them.
            Spacer(Modifier.height(88.dp))
        }
    }
}

/** Glucose in the user's unit, without the unit. */
internal fun formatBg(mgdl: Double, mmol: Boolean): String =
    if (mmol) String.format(Locale.getDefault(), "%.1f", mgdl / 18.0) else Math.round(mgdl).toString()

private fun formatU(units: Double): String = String.format(Locale.getDefault(), "%.2f", units)

/** Catheter, sensor, reservoir and pump battery, each colored by [RingLogic] limits. Missing values are left out. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatusChips(status: StatusCardState?, p: RingPalette) {
    if (status == null) return
    val now = System.currentTimeMillis()

    /** One chip: the icon AAPS uses for it, the short value, the full sentence for screen readers, the level. */
    class Chip(val icon: ImageVector, val value: String, val spoken: String, val level: RingLevel)

    val chips = listOfNotNull(
        status.cannulaChangeAt?.let {
            val age = supplyAge(now - it)
            Chip(IcCannulaChange, age, stringResource(R.string.ring_chip_cannula, age), RingLogic.cannulaLevel(now - it))
        },
        status.sensorChangeAt?.let {
            val age = supplyAge(now - it)
            Chip(IcCgmInsert, age, stringResource(R.string.ring_chip_sensor, age), RingLogic.sensorLevel(now - it))
        },
        status.reservoirUnits?.let {
            val units = formatUnits0(it)
            Chip(IcPumpCartridge, stringResource(R.string.ring_units, units), stringResource(R.string.ring_chip_reservoir, units), RingLogic.reservoirLevel(it))
        },
        status.pumpBatteryPct?.let {
            Chip(IcPumpBattery, stringResource(R.string.ring_percent, it), stringResource(R.string.ring_chip_battery, it), RingLogic.batteryLevel(it))
        },
    )
    if (chips.isEmpty()) return
    // Icons and numbers only, so the four fit in one row on a phone.
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
        chips.forEach { chip ->
            val (bg, fg) = when (chip.level) {
                RingLevel.OK    -> p.okBg to p.okFg
                RingLevel.WARN  -> p.warnBg to p.warnFg
                RingLevel.ALARM -> p.stopBg to p.stopFg
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(bg)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .semantics(mergeDescendants = true) { contentDescription = chip.spoken },
            ) {
                Icon(chip.icon, contentDescription = null, tint = fg, modifier = Modifier.size(14.dp))
                Text(
                    text = chip.value, fontSize = 12.sp, color = fg, maxLines = 1,
                    fontWeight = if (chip.level == RingLevel.OK) FontWeight.Normal else FontWeight.Bold,
                )
            }
        }
    }
}

/** "5 д 3 ч" or "7 ч". */
@Composable
private fun supplyAge(ageMs: Long): String {
    val hours = (ageMs / 3_600_000L).coerceAtLeast(0L).toInt()
    return if (hours >= 24) stringResource(R.string.ring_age_days_hours, hours / 24, hours % 24) else stringResource(R.string.ring_age_hours, hours)
}

private fun formatUnits0(units: Double): String = String.format(Locale.getDefault(), "%.0f", units)

@Composable
private fun RingHero(
    status: StatusCardState?, adj: AdjustmentCardState?, pred30: Double?, period: RingPeriod, stats: RingStats?, mmol: Boolean,
    lastBgTime: Long?, p: RingPalette,
) {
    val glass = LocalGlassHeroCommands.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.width(76.dp)) {
            SideValue(
                stringResource(R.string.ring_iob_title),
                status?.iobTotalU?.let { stringResource(R.string.ring_units, formatU(it)) } ?: "--",
                stringResource(R.string.ring_iob_sub), p, Alignment.Start,
            )
            SideValue(stringResource(R.string.ring_cob_title), status?.cobText ?: "--", stringResource(R.string.ring_cob_sub), p, Alignment.Start)
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .weight(1f)
                .height(200.dp)
                .clickable(role = Role.Button, onClick = { glass.openLoop() }),
        ) {
            val low = (stats?.lowPct ?: 0) / 100f
            val inR = (stats?.inRangePct ?: 0) / 100f
            val high = (stats?.highPct ?: 0) / 100f
            Canvas(modifier = Modifier.size(196.dp)) {
                val stroke = 11.dp.toPx()
                val d = size.minDimension - stroke
                val topLeft = Offset((size.width - d) / 2, (size.height - d) / 2)
                val arc = Size(d, d)
                drawArc(p.track, 0f, 360f, false, topLeft, arc, style = Stroke(stroke))
                if (stats != null) {
                    drawArc(p.inRange, -90f, 360f * inR, false, topLeft, arc, style = Stroke(stroke))
                    drawArc(p.high, -90f + 360f * inR, 360f * high, false, topLeft, arc, style = Stroke(stroke))
                    drawArc(p.low, -90f + 360f * (inR + high), 360f * low, false, topLeft, arc, style = Stroke(stroke))
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(ageText(lastBgTime), fontSize = 11.sp, color = p.muted)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = status?.glucoseText ?: "--", fontSize = 60.sp, fontWeight = FontWeight.Bold, color = p.text,
                        modifier = Modifier.semantics { contentDescription = status?.contentDescription ?: "" },
                    )
                    status?.trendArrowRes?.let {
                        Image(painterResource(it), contentDescription = status?.trendDescription, colorFilter = ColorFilter.tint(p.text), modifier = Modifier.size(28.dp))
                    }
                }
                status?.deltaText?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 13.sp, color = p.muted) }
                pred30?.let { Text(stringResource(R.string.ring_pred_30, formatBg(it, mmol)), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = p.brand) }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.End, modifier = Modifier.width(76.dp)) {
            SideValue(
                stringResource(R.string.ring_basal_title),
                status?.effectiveBasalRateUh?.let { stringResource(R.string.ring_rate, formatU(it)) } ?: "--",
                "", p, Alignment.End,
            )
            val isf = adj?.variableSens
            SideValue(
                stringResource(R.string.ring_target_title),
                status?.targetText ?: "--",
                isf?.let { stringResource(R.string.ring_isf, formatBg(it, mmol)) } ?: "",
                p, Alignment.End,
            )
        }
    }
}

/** "now" or "N min ago" for the last reading; empty when there is none. */
@Composable
private fun ageText(timestamp: Long?): String {
    if (timestamp == null) return ""
    val minutes = ((System.currentTimeMillis() - timestamp) / 60_000L).coerceAtLeast(0L).toInt()
    return if (minutes == 0) stringResource(R.string.ring_axis_now) else stringResource(R.string.ring_ago, minutes)
}

@Composable
private fun SideValue(title: String, value: String, sub: String, p: RingPalette, align: Alignment.Horizontal) {
    Column(horizontalAlignment = align, modifier = Modifier.heightIn(min = 44.dp)) {
        Text(title, fontSize = 11.sp, color = p.muted, maxLines = 1)
        // Long values ("0,35 ЕД/ч") get a smaller font so they fit the 76 dp side column.
        Text(value, fontSize = if (value.length > 7) 15.sp else 18.sp, fontWeight = FontWeight.Bold, color = p.text, maxLines = 1, softWrap = false)
        if (sub.isNotBlank()) Text(sub, fontSize = 11.sp, color = p.muted, maxLines = 1)
    }
}

@Composable
private fun PeriodRow(period: RingPeriod, stats: RingStats?, p: RingPalette, onPick: (RingPeriod) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            RingPeriod.entries.forEach { item ->
                val on = item == period
                val label = stringResource(
                    when (item) {
                        RingPeriod.H3   -> R.string.ring_period_3h
                        RingPeriod.DAY  -> R.string.ring_period_day
                        RingPeriod.WEEK -> R.string.ring_period_week
                    }
                )
                Text(
                    text = label, fontSize = 12.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (on) p.segOnText else p.text,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (on) p.segOn else p.track)
                        .clickable(role = Role.Tab, onClick = { onPick(item) })
                        .heightIn(min = 32.dp)
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                )
            }
        }
        if (stats == null) {
            Text(stringResource(R.string.ring_stats_none), fontSize = 11.sp, color = p.muted)
        } else {
            val cd = stringResource(R.string.ring_stats_cd, stats.lowPct, stats.inRangePct, stats.highPct, stats.cvPct)
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.semantics { contentDescription = cd }) {
                Text(stringResource(R.string.ring_stats_low, stats.lowPct), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = p.fallingText)
                Text(stringResource(R.string.ring_stats_in, stats.inRangePct), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = p.brand)
                Text(stringResource(R.string.ring_stats_high, stats.highPct), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = p.risingText)
                Text(stringResource(R.string.ring_stats_cv, stats.cvPct), fontSize = 12.sp, color = p.text)
            }
        }
    }
}

@Composable
private fun HourTile(
    hour: RingHourState, readings: List<BgDataPoint>, prediction: List<BgDataPoint>, now: Long,
    minPred: Double?, pred60: Double?, maxPred: Double?, why: RingWhy?, mmol: Boolean, p: RingPalette,
) {
    val (accent, textColor, title) = when (hour) {
        RingHourState.STABLE  -> Triple(p.stableAccent, p.stableText, stringResource(R.string.ring_hour_stable))
        RingHourState.RISING  -> Triple(p.risingAccent, p.risingText, stringResource(R.string.ring_hour_rising))
        RingHourState.FALLING -> Triple(p.fallingAccent, p.fallingText, stringResource(R.string.ring_hour_falling))
    }
    val line = when {
        hour == RingHourState.FALLING && minPred != null -> stringResource(R.string.ring_hour_why_falling, formatBg(minPred, mmol))
        hour == RingHourState.RISING && pred60 != null && why?.limited == true -> stringResource(R.string.ring_hour_why_rising_capped, formatBg(pred60, mmol))
        hour == RingHourState.RISING && pred60 != null -> stringResource(R.string.ring_hour_why_rising, formatBg(pred60, mmol))
        minPred != null && maxPred != null -> stringResource(R.string.ring_hour_why_stable, formatBg(minPred, mmol), formatBg(maxPred, mmol))
        else -> stringResource(R.string.ring_hour_no_prediction)
    }
    val level = when (hour) {
        RingHourState.STABLE  -> 1
        RingHourState.RISING  -> 2
        RingHourState.FALLING -> 3
    }
    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(RoundedCornerShape(14.dp)).background(p.card)) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(accent))
        Column(Modifier.padding(start = 10.dp, end = 12.dp, top = 10.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.ring_hour_title), fontSize = 11.sp, color = p.muted)
                    Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = textColor)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    for (i in 1..3) Box(Modifier.width(14.dp).height(6.dp).clip(CircleShape).background(if (i <= level) accent else p.track))
                }
            }
            HourGraph(readings, prediction, now, accent, p)
            Text(line, fontSize = 12.sp, color = p.soft)
        }
    }
}

@Composable
private fun HourGraph(readings: List<BgDataPoint>, prediction: List<BgDataPoint>, now: Long, accent: Color, p: RingPalette) {
    val past = readings.filter { it.timestamp >= now - 3 * 3_600_000L }
    Canvas(modifier = Modifier.fillMaxWidth().height(56.dp)) {
        val split = size.width * 0.72f
        fun x(t: Long): Float = if (t <= now) split * (1f - (now - t) / (3f * 3_600_000f)) else split + (size.width - split) * ((t - now) / 3_600_000f)
        fun y(v: Double): Float {
            val c = v.coerceIn(36.0, 288.0)
            return (size.height * (1.0 - (c - 36.0) / 252.0)).toFloat()
        }
        drawRect(p.inRange.copy(alpha = p.bandAlpha), Offset(0f, y(180.0)), Size(size.width, y(70.0) - y(180.0)))
        drawLine(p.line, Offset(split, 0f), Offset(split, size.height), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
        past.zipWithNext { a, b ->
            if (b.timestamp - a.timestamp <= 15 * 60_000L)
                drawLine(p.text, Offset(x(a.timestamp), y(a.value)), Offset(x(b.timestamp), y(b.value)), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        }
        prediction.sortedBy { it.timestamp }.zipWithNext { a, b ->
            drawLine(
                accent, Offset(x(a.timestamp), y(a.value)), Offset(x(b.timestamp), y(b.value)), strokeWidth = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)), cap = StrokeCap.Round,
            )
        }
    }
    Row(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.ring_axis_past), fontSize = 9.sp, color = p.muted, modifier = Modifier.weight(0.72f))
        Text(stringResource(R.string.ring_axis_now), fontSize = 9.sp, color = p.muted, modifier = Modifier.weight(0.16f))
        Text(stringResource(R.string.ring_axis_future), fontSize = 9.sp, color = p.muted, textAlign = TextAlign.End, modifier = Modifier.weight(0.12f))
    }
}

@Composable
private fun WhyCard(why: RingWhy?, adj: AdjustmentCardState?, mmol: Boolean, p: RingPalette) {
    val glass = LocalGlassHeroCommands.current
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(p.card).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.ring_why_title), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = p.brand)
        if (why == null) {
            Text(stringResource(R.string.ring_why_none), fontSize = 14.sp, color = p.soft)
            return@Column
        }
        val action = when (val a = why.action) {
            is RingAction.Smb       -> stringResource(R.string.ring_act_smb, formatU(a.units))
            is RingAction.ZeroBasal -> stringResource(R.string.ring_act_zero, a.durationMin)
            is RingAction.TempBasal -> stringResource(R.string.ring_act_tbr, formatU(a.rateUph), a.durationMin)
            RingAction.NoChange     -> stringResource(R.string.ring_act_none)
        }
        val cause = stringResource(
            when (why.family) {
                RingWhyFamily.NO_DATA     -> R.string.ring_cause_no_data
                RingWhyFamily.SAFETY_STOP -> R.string.ring_cause_safety_stop
                RingWhyFamily.LOW         -> R.string.ring_cause_low
                RingWhyFamily.AFTER_LOW   -> R.string.ring_cause_after_low
                RingWhyFamily.ENOUGH_IOB  -> R.string.ring_cause_enough_iob
                RingWhyFamily.MEAL        -> R.string.ring_cause_meal
                RingWhyFamily.RISE        -> R.string.ring_cause_rise
                RingWhyFamily.ACTIVITY    -> R.string.ring_cause_activity
                RingWhyFamily.HONEYMOON   -> R.string.ring_cause_honeymoon
                RingWhyFamily.NORMAL      -> R.string.ring_cause_normal
                RingWhyFamily.UNKNOWN     -> R.string.ring_cause_unknown
            }
        )
        Text(stringResource(R.string.ring_why_line, action, cause), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = p.text)
        why.eventualMgdl?.let { Text(stringResource(R.string.ring_why_eventual, formatBg(it, mmol)), fontSize = 13.sp, color = p.soft) }
        when {
            why.limitWantedU != null && why.limitGivenU != null ->
                Text(stringResource(R.string.ring_why_limited_numbers, formatU(why.limitWantedU), formatU(why.limitGivenU)), fontSize = 13.sp, color = p.soft)
            why.limited -> Text(stringResource(R.string.ring_why_limited), fontSize = 13.sp, color = p.soft)
        }
        val reason = adj?.detailedReason.orEmpty().lowercase()
        val chips = buildList {
            if (reason.contains("smb off") || reason.contains("smb=0") || reason.contains("smb disabled")) add(stringResource(R.string.ring_chip_smb_off) to (p.stopBg to p.stopFg))
            if (why.family == RingWhyFamily.LOW) add(stringResource(R.string.ring_chip_low_guard) to (p.stopBg to p.stopFg))
            if (why.family == RingWhyFamily.MEAL) add(stringResource(R.string.ring_chip_meal) to (p.warnBg to p.warnFg))
        }
        if (chips.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                chips.forEach { (text, colors) ->
                    Text(text, fontSize = 11.sp, color = colors.second, modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(colors.first).padding(horizontal = 8.dp, vertical = 3.dp))
                }
            }
        }
        Text(
            text = stringResource(R.string.ring_why_details), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = p.brand,
            modifier = Modifier.clickable(role = Role.Button, onClick = { glass.openLoop() }).heightIn(min = 44.dp).padding(vertical = 12.dp),
        )
    }
}

@Composable
private fun ActionButtons(p: RingPalette) {
    val hero = LocalDashboardHeroCommands.current
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        RingButton(stringResource(R.string.ring_btn_target), p, primary = false, modifier = Modifier.weight(1f)) { hero.openTempTargetManagement() }
        RingButton(stringResource(R.string.ring_btn_carbs), p, primary = false, modifier = Modifier.weight(1f)) { hero.openCarbsEntry() }
        RingButton(stringResource(R.string.ring_btn_insulin), p, primary = false, modifier = Modifier.weight(1f)) { hero.openInsulinEntry() }
        RingButton(stringResource(R.string.ring_btn_calc), p, primary = true, modifier = Modifier.weight(1.35f)) { hero.openBolusWizard() }
    }
}

@Composable
private fun RingButton(text: String, p: RingPalette, primary: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (primary) p.primary else p.card)
            .then(if (primary) Modifier else Modifier.border(1.dp, p.btnLine, RoundedCornerShape(14.dp)))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 12.dp),
    ) {
        Text(text, fontSize = 13.sp, fontWeight = if (primary) FontWeight.Bold else FontWeight.SemiBold, color = if (primary) p.primaryText else p.text, maxLines = 1)
    }
}
