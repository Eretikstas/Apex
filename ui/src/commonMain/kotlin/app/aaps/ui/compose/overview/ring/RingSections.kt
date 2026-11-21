package app.aaps.ui.compose.overview.ring

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
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
internal fun RingHero(bgInfo: BgInfoData?, timeAgo: String, stats: RingStats?, iobText: String, cobText: String, onIobClick: (() -> Unit)? = null) {
    val low = AapsTheme.generalColors.bgLow
    val inRange = AapsTheme.generalColors.bgInRange
    val high = AapsTheme.generalColors.bgHigh
    val track = MaterialTheme.colorScheme.surfaceVariant
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        SideValue(stringResource(CoreUiStrings.iob), iobText, Alignment.Start, Modifier.weight(1f), onClick = onIobClick)
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
private fun SideValue(title: String, value: String, align: Alignment.Horizontal, modifier: Modifier, onClick: (() -> Unit)? = null) {
    // Tappable like the IOB chip of the usual screen: it opens the same details.
    Column(modifier = if (onClick != null) modifier.clickable(role = Role.Button, onClick = onClick) else modifier, horizontalAlignment = align) {
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

/**
 * "Next hours": the state word (its rule looks at the next hour), the last 3 hours with the loop's own prediction up to
 * the chosen horizon (1, 2 or 3 h), and the predicted value at the horizon. When the loop's line ends before the
 * horizon, the tile says where it ends instead of drawing further.
 */
@Composable
internal fun RingHourTile(
    hour: RingHour,
    readings: List<BgDataPoint>,
    prediction: List<BgDataPoint>,
    now: Long,
    horizon: RingHorizon,
    onHorizon: (RingHorizon) -> Unit,
    forecast: RingForecast?,
    forecastText: String?,
    /** Hours ahead and the loop's value then (null where its line does not reach). */
    perHour: List<Pair<Int, String?>> = emptyList()
) {
    val accent = when (hour) {
        RingHour.FALLING -> AapsTheme.generalColors.bgLow
        RingHour.RISING  -> AapsTheme.generalColors.bgHigh
        RingHour.STABLE  -> AapsTheme.generalColors.bgInRange
        RingHour.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val dateUtil = LocalDateUtil.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(AapsSpacing.large)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
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
            Row(horizontalArrangement = Arrangement.spacedBy(AapsSpacing.small)) {
                RingHorizon.entries.forEach { h ->
                    FilterChip(selected = h == horizon, onClick = { onHorizon(h) }, label = { Text(stringResource(UiStrings.ring_horizon_hours, h.hours)) })
                }
            }
            HourGraph(readings, prediction, now, horizon.hours * 3_600_000L, accent)
            if (forecast != null && forecastText != null) Text(
                if (forecast.reachesHorizon) stringResource(UiStrings.ring_pred_in, horizon.hours, forecastText)
                else stringResource(UiStrings.ring_pred_until, dateUtil.timeString(forecast.time), forecastText),
                style = MaterialTheme.typography.bodyMedium
            )
            else Text(stringResource(UiStrings.ring_pred_none), style = MaterialTheme.typography.bodyMedium)
            if (forecast != null && perHour.isNotEmpty()) Row(
                modifier = Modifier.fillMaxWidth().padding(top = AapsSpacing.small),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                perHour.forEach { (h, text) ->
                    Text(
                        if (text != null) stringResource(UiStrings.ring_pred_hour_item, h, text) else stringResource(UiStrings.ring_pred_hour_none, h),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun HourGraph(readings: List<BgDataPoint>, prediction: List<BgDataPoint>, now: Long, aheadMs: Long, accent: Color) {
    val past = readings.filter { it.timestamp >= now - 3 * 3_600_000L }
    val future = prediction.filter { it.timestamp in now..(now + aheadMs + RingLogic.POINT_TOLERANCE_MS) }
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
    val t1 = now + aheadMs
    // Full clock hours in the window, with their labels, so the graph reads by the hour.
    val dateUtil = LocalDateUtil.current
    val hours = generateSequence((t0 / 3_600_000L + 1) * 3_600_000L) { it + 3_600_000L }.takeWhile { it < t1 }.toList()
    val labels = hours.map { dateUtil.timeString(it) }
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    Canvas(modifier = Modifier.fillMaxWidth().height(112.dp).padding(vertical = AapsSpacing.small)) {
        val labelHeight = 16.dp.toPx()
        val plot = size.height - labelHeight
        fun x(t: Long) = ((t - t0).toFloat() / (t1 - t0)) * size.width
        fun y(v: Double) = (plot - ((v - minV) / (maxV - minV)).toFloat() * plot)
        drawRect(band, Offset(0f, y(highLine)), Size(size.width, y(lowLine) - y(highLine)))
        hours.forEachIndexed { i, h ->
            drawLine(line.copy(alpha = 0.15f), Offset(x(h), 0f), Offset(x(h), plot))
            val measured = textMeasurer.measure(labels[i], labelStyle)
            val left = (x(h) - measured.size.width / 2f).coerceIn(0f, size.width - measured.size.width)
            drawText(measured, topLeft = Offset(left, plot + 2f))
        }
        past.forEach { drawCircle(line, 2.5f, Offset(x(it.timestamp), y(it.value))) }
        future.forEach { drawCircle(accent, 2.5f, Offset(x(it.timestamp), y(it.value))) }
        drawLine(line.copy(alpha = 0.4f), Offset(x(now), 0f), Offset(x(now), plot), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
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
