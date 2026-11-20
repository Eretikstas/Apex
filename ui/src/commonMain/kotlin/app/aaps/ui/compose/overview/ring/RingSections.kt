package app.aaps.ui.compose.overview.ring

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.interfaces.aps.LoopNote
import app.aaps.core.interfaces.overview.graph.BgDataPoint
import app.aaps.core.interfaces.overview.graph.BgInfoData
import app.aaps.core.interfaces.overview.graph.BgRange
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.LocalDateUtil
import app.aaps.core.ui.compose.LocalProfileUtil
import app.aaps.core.ui.compose.stringResource
import app.aaps.ui.UiStrings
import kotlin.math.roundToInt

private val RING_SIZE = 200.dp
private val RING_STROKE = 14.dp

@Composable
private fun BgRange.color(): Color = when (this) {
    BgRange.HIGH     -> AapsTheme.generalColors.bgHigh
    BgRange.IN_RANGE -> AapsTheme.generalColors.bgInRange
    BgRange.LOW      -> AapsTheme.generalColors.bgLow
}

/** Glucose in mg/dL from a value in the user's unit. */
@Composable
internal fun toMgdl(valueInUnits: Double): Double =
    if (LocalProfileUtil.current.units == GlucoseUnit.MMOL) valueInUnits * 18.0 else valueInUnits

/**
 * The ring: its arc is the time in range of the chosen period (low part, in range, high part), glucose with arrow and
 * delta in the middle, IOB on the left and COB on the right.
 */
@Composable
internal fun RingHero(bgInfo: BgInfoData?, timeAgo: String, stats: RingStats?, iobText: String, cobText: String) {
    val low = AapsTheme.generalColors.bgLow
    val inRange = AapsTheme.generalColors.bgInRange
    val high = AapsTheme.generalColors.bgHigh
    val track = MaterialTheme.colorScheme.surfaceVariant
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        SideValue(stringResource(CoreUiStrings.iob), iobText, Alignment.Start, Modifier.weight(1f))
        Box(modifier = Modifier.size(RING_SIZE), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(RING_SIZE)) {
                val stroke = RING_STROKE.toPx()
                val arcSize = Size(size.width - stroke, size.height - stroke)
                val topLeft = Offset(stroke / 2, stroke / 2)
                drawArc(track, -90f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
                if (stats != null) {
                    var start = -90f
                    for ((pct, c) in listOf(stats.lowPct to low, stats.inRangePct to inRange, stats.highPct to high)) {
                        val sweep = (pct * 3.6).toFloat()
                        if (sweep > 0.5f) drawArc(c, start, sweep - 0.5f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Butt))
                        start += sweep
                    }
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = bgInfo?.let { "${it.bgText} ${it.trendArrow?.symbol.orEmpty()}" } ?: "—",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = bgInfo?.bgRange?.color() ?: MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (bgInfo?.isOutdated == true) TextDecoration.LineThrough else null
                )
                bgInfo?.deltaText?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
                Text(timeAgo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        SideValue(stringResource(CoreUiStrings.cob), cobText, Alignment.End, Modifier.weight(1f))
    }
}

@Composable
private fun SideValue(title: String, value: String, align: Alignment.Horizontal, modifier: Modifier) {
    Column(modifier = modifier, horizontalAlignment = align) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value.ifEmpty { "—" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

/** The period chips and the numbers of the chosen period. */
@Composable
internal fun RingPeriodRow(period: RingPeriod, stats: RingStats?, onPick: (RingPeriod) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium)) {
            RingPeriod.entries.forEach { p ->
                FilterChip(
                    selected = p == period,
                    onClick = { onPick(p) },
                    label = {
                        Text(
                            stringResource(
                                when (p) {
                                    RingPeriod.H3   -> UiStrings.ring_period_3h
                                    RingPeriod.DAY  -> UiStrings.ring_period_day
                                    RingPeriod.WEEK -> UiStrings.ring_period_week
                                }
                            )
                        )
                    }
                )
            }
        }
        if (stats == null) Text(stringResource(UiStrings.ring_no_stats), style = MaterialTheme.typography.bodyMedium)
        else {
            Text(stringResource(UiStrings.ring_in_range, stats.inRangePct.roundToInt()), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(UiStrings.ring_low_high, stats.lowPct.roundToInt(), stats.highPct.roundToInt(), stats.cvPct.roundToInt()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** "Next hour": the state word, the last 3 hours with the 1 hour prediction, and the value in one hour. */
@Composable
internal fun RingHourTile(hour: RingHour, readings: List<BgDataPoint>, prediction: List<BgDataPoint>, now: Long, pred60Text: String?) {
    val accent = when (hour) {
        RingHour.FALLING -> AapsTheme.generalColors.bgLow
        RingHour.RISING  -> AapsTheme.generalColors.bgHigh
        RingHour.STABLE  -> AapsTheme.generalColors.bgInRange
        RingHour.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(AapsSpacing.large)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(UiStrings.ring_hour_title), style = MaterialTheme.typography.labelLarge)
                Text(
                    stringResource(
                        when (hour) {
                            RingHour.STABLE  -> UiStrings.ring_hour_stable
                            RingHour.RISING  -> UiStrings.ring_hour_rising
                            RingHour.FALLING -> UiStrings.ring_hour_falling
                            RingHour.UNKNOWN -> UiStrings.ring_hour_unknown
                        }
                    ),
                    style = MaterialTheme.typography.labelLarge, color = accent, fontWeight = FontWeight.Bold
                )
            }
            HourGraph(readings, prediction, now, accent)
            pred60Text?.let { Text(stringResource(UiStrings.ring_pred_60, it), style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@Composable
private fun HourGraph(readings: List<BgDataPoint>, prediction: List<BgDataPoint>, now: Long, accent: Color) {
    val past = readings.filter { it.timestamp >= now - 3 * 3_600_000L }
    val future = prediction.filter { it.timestamp in now..(now + 3_600_000L) }
    val all = past + future
    if (all.isEmpty()) return
    val band = AapsTheme.generalColors.bgInRange.copy(alpha = 0.12f)
    val line = MaterialTheme.colorScheme.onSurface
    val mmol = LocalProfileUtil.current.units == GlucoseUnit.MMOL
    val lowLine = if (mmol) 3.9 else 70.0
    val highLine = if (mmol) 10.0 else 180.0
    val minV = minOf(all.minOf { it.value }, lowLine) * 0.9
    val maxV = maxOf(all.maxOf { it.value }, highLine) * 1.05
    val t0 = now - 3 * 3_600_000L
    val t1 = now + 3_600_000L
    Canvas(modifier = Modifier.fillMaxWidth().height(96.dp).padding(vertical = AapsSpacing.small)) {
        fun x(t: Long) = ((t - t0).toFloat() / (t1 - t0)) * size.width
        fun y(v: Double) = (size.height - ((v - minV) / (maxV - minV)).toFloat() * size.height)
        drawRect(band, Offset(0f, y(highLine)), Size(size.width, y(lowLine) - y(highLine)))
        past.forEach { drawCircle(line, 2.5f, Offset(x(it.timestamp), y(it.value))) }
        future.forEach { drawCircle(accent, 2.5f, Offset(x(it.timestamp), y(it.value))) }
        drawLine(line.copy(alpha = 0.4f), Offset(x(now), 0f), Offset(x(now), size.height), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
    }
}

/** "Почему": the plain words of the loop's last decision, what it did, a limit if one cut it, and the expected end. */
@Composable
internal fun RingWhyCard(why: RingWhy?, whyTime: Long, eventualText: String?, notes: List<LoopNote> = emptyList()) {
    if (why == null && notes.isEmpty()) return
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(AapsSpacing.large), verticalArrangement = Arrangement.spacedBy(AapsSpacing.small)) {
            Text(stringResource(UiStrings.ring_why_title), style = MaterialTheme.typography.labelLarge)
            if (why != null) RingWhyBody(why, whyTime, eventualText)
            notes.forEach { note ->
                Text(note.text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun RingWhyBody(why: RingWhy, whyTime: Long, eventualText: String?) {
    val dateUtil = LocalDateUtil.current
    Column(verticalArrangement = Arrangement.spacedBy(AapsSpacing.small)) {
        Text(
            when (why.family) {
                RingWhyFamily.NO_DATA    -> stringResource(UiStrings.ring_why_no_data)
                RingWhyFamily.LOW        -> stringResource(UiStrings.ring_why_low)
                RingWhyFamily.ENOUGH_IOB -> stringResource(UiStrings.ring_why_enough_iob)
                RingWhyFamily.MEAL       -> stringResource(UiStrings.ring_why_meal, why.cobG.roundToInt())
                RingWhyFamily.RISE       -> stringResource(UiStrings.ring_why_rise)
                RingWhyFamily.IN_RANGE   -> stringResource(UiStrings.ring_why_in_range)
                RingWhyFamily.UNKNOWN    -> stringResource(UiStrings.ring_why_unknown)
            },
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            when (val a = why.action) {
                is RingAction.Smb       ->
                    if (a.rateUph != null && a.durationMin != null) stringResource(UiStrings.ring_act_smb_tbr, a.units, a.rateUph, a.durationMin)
                    else stringResource(UiStrings.ring_act_smb, a.units)

                is RingAction.ZeroBasal -> stringResource(UiStrings.ring_act_zero, a.durationMin)
                is RingAction.TempBasal -> stringResource(UiStrings.ring_act_tbr, a.rateUph, a.durationMin)
                RingAction.NoChange     -> stringResource(UiStrings.ring_act_none)
            },
            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold
        )
        if (why.limitWantedU != null && why.limitGivenU != null)
            Text(stringResource(UiStrings.ring_limited, why.limitWantedU, why.limitGivenU), style = MaterialTheme.typography.bodyMedium)
        eventualText?.let { Text(stringResource(UiStrings.ring_eventual, it), style = MaterialTheme.typography.bodyMedium) }
        if (whyTime > 0) Text(
            stringResource(UiStrings.ring_decided_at, dateUtil.timeString(whyTime)),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
