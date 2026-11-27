package app.aaps.plugins.learner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.ComposablePluginContent
import app.aaps.core.ui.compose.LocalDateUtil
import app.aaps.core.ui.compose.ToolbarConfig
import app.aaps.plugins.learner.BasalChange
import app.aaps.plugins.learner.LearnerPlugin.Status
import app.aaps.plugins.learner.HourSource
import app.aaps.plugins.learner.LearnerPlugin
import app.aaps.plugins.learner.EvalStats
import app.aaps.plugins.learner.EvalVerdict
import app.aaps.plugins.learner.HypoSizeEngine
import app.aaps.plugins.learner.LowCause
import app.aaps.plugins.learner.PeakFitEngine
import app.aaps.plugins.learner.R
import app.aaps.plugins.learner.WeekStats
import app.aaps.plugins.learner.SensorHintKind

/** The learner's screen: the basal proposal with "Внести" / "Отказаться", and what it measured per hour. */
class LearnerComposeContent(private val plugin: LearnerPlugin) : ComposablePluginContent {

    @Composable
    override fun Render(setToolbarConfig: (ToolbarConfig) -> Unit, onNavigateBack: () -> Unit, onSettings: (() -> Unit)?) {
        val title = stringResource(R.string.learner_name)
        LaunchedEffect(Unit) {
            setToolbarConfig(
                ToolbarConfig(
                    title = title,
                    navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) } },
                    actions = {}
                )
            )
        }
        LearnerScreen(plugin)
    }
}

@Composable
private fun LearnerScreen(plugin: LearnerPlugin) {
    val status by plugin.status.collectAsStateWithLifecycle()
    LearnerScreenContent(
        status = status,
        onAccept = { plugin.accept() },
        onRefuse = plugin::refuse,
        onAcceptDura = plugin::acceptDura,
        onRefuseDura = plugin::refuseDura,
        onAcceptPct = plugin::acceptPct,
        onRefusePct = plugin::refusePct,
        onSpecialDay = plugin::setSpecialDay,
        onCurveWatch = plugin::setCurveWatch,
        onPeakFit = plugin::setPeakFitOn,
        onLowPred = plugin::setLowPredOn,
        onLowCauses = plugin::setLowCauses,
        onAcceptCr = plugin::acceptCr,
        onRefuseCr = plugin::refuseCr,
        blockName = plugin::blockName,
        onSensorHints = plugin::setSensorHints,
        onCannulaHints = plugin::setCannulaHints,
        onLowMealHints = plugin::setLowMealHints,
        onEval = plugin::setEvalOn,
        onDigest = plugin::setDigestOn,
        onRebound = plugin::setReboundOn
    )
}

/** The screen itself, from the status only, so it can also be drawn in a test. */
@Composable
internal fun LearnerScreenContent(
    status: Status,
    onAccept: () -> Unit,
    onRefuse: () -> Unit,
    onAcceptDura: () -> Unit,
    onRefuseDura: () -> Unit,
    onAcceptPct: () -> Unit = {},
    onRefusePct: () -> Unit = {},
    onSpecialDay: (Boolean) -> Unit = {},
    onCurveWatch: (Boolean) -> Unit = {},
    onPeakFit: (Boolean) -> Unit = {},
    onLowPred: (Boolean) -> Unit = {},
    onLowCauses: (Boolean) -> Unit = {},
    onAcceptCr: () -> Unit = {},
    onRefuseCr: () -> Unit = {},
    blockName: (String) -> String = { it },
    onSensorHints: (Boolean) -> Unit = {},
    onCannulaHints: (Boolean) -> Unit = {},
    onLowMealHints: (Boolean) -> Unit = {},
    onEval: (Boolean) -> Unit = {},
    onDigest: (Boolean) -> Unit = {},
    onRebound: (Boolean) -> Unit = {}
) {
    val dateUtil = LocalDateUtil.current
    var confirm by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(AapsSpacing.large),
        verticalArrangement = Arrangement.spacedBy(AapsSpacing.medium)
    ) {
        Text(
            if (status.lastCardAt > 0) stringResource(R.string.learner_last_card, dateUtil.dateAndTimeString(status.lastCardAt))
            else stringResource(R.string.learner_no_card_yet),
            style = MaterialTheme.typography.bodyMedium
        )
        Text(stringResource(R.string.learner_next_card, dateUtil.dateAndTimeString(status.nextCardAt)), style = MaterialTheme.typography.bodyMedium)
        if (status.message.isNotEmpty()) Text(status.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)

        SpecialSection(status, onSpecialDay)
        CurveSection(status, onCurveWatch)
        PeakFitSection(status, onPeakFit)
        LowPredSection(status, onLowPred)
        LowCauseSection(status, onLowCauses)

        status.proposal?.let { p ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(AapsSpacing.large), verticalArrangement = Arrangement.spacedBy(AapsSpacing.small)) {
                    Text(stringResource(R.string.learner_proposal_title, p.profileName), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.learner_proposal_why), style = MaterialTheme.typography.bodySmall)
                    p.changes.forEach { ChangeRow(it) }
                    Row(horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium)) {
                        Button(onClick = { confirm = true }, enabled = !status.busy) { Text(stringResource(R.string.learner_accept)) }
                        OutlinedButton(onClick = onRefuse, enabled = !status.busy) { Text(stringResource(R.string.learner_refuse)) }
                    }
                }
            }
            if (confirm) AlertDialog(
                onDismissRequest = { confirm = false },
                title = { Text(stringResource(R.string.learner_confirm_title, p.profileName)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(AapsSpacing.small)) {
                        p.changes.forEach { ChangeRow(it) }
                        Text(stringResource(R.string.learner_confirm_text))
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        confirm = false
                        onAccept()
                    }) { Text(stringResource(R.string.learner_accept)) }
                },
                dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.learner_cancel)) } }
            )
        } ?: Text(stringResource(R.string.learner_no_proposal), style = MaterialTheme.typography.bodyMedium)

        ReboundSection(status, onRebound)
        PctSection(status, onAcceptPct, onRefusePct)
        HypoSizeSection(status)
        CrSection(status, onAcceptCr, onRefuseCr, blockName)
        DuraSection(status, onAcceptDura, onRefuseDura)
        SensorSection(status, onSensorHints)
        CannulaSection(status, onCannulaHints)
        LowMealSection(status, onLowMealHints)
        EvalSection(status, onEval)
        DigestSection(status, onDigest)

        if (status.hours.isNotEmpty()) {
            Text(stringResource(R.string.learner_hours_title), style = MaterialTheme.typography.titleMedium)
            status.hours.forEach { h ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.learner_hour, h.hour), modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.learner_rate, h.profile), modifier = Modifier.weight(1f))
                    Text(h.need?.let { stringResource(R.string.learner_rate, it) } ?: "—", modifier = Modifier.weight(1f))
                    Text(
                        stringResource(
                            when (h.source) {
                                HourSource.MEASURED -> R.string.learner_measured
                                HourSource.ESTIMATE -> R.string.learner_estimate
                                HourSource.NONE     -> R.string.learner_no_data
                            }
                        ),
                        modifier = Modifier.weight(1.4f), style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun ChangeRow(c: BasalChange) {
    Text(stringResource(R.string.learner_change, c.hour, c.from, c.to), fontWeight = FontWeight.SemiBold)
}

/** The dura card: what it saw, the rule, and a proposal with "Внести" / "Отказаться" when there is one. */
@Composable
private fun DuraSection(status: Status, onAccept: () -> Unit, onRefuse: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(AapsSpacing.large), verticalArrangement = Arrangement.spacedBy(AapsSpacing.small)) {
            Text(stringResource(R.string.learner_dura_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.learner_dura_now, status.dura), style = MaterialTheme.typography.bodyMedium)
            status.lastDura?.let { Text(stringResource(R.string.learner_dura_seen, it.first, it.second), style = MaterialTheme.typography.bodyMedium) }
            Text(stringResource(R.string.learner_dura_rule), style = MaterialTheme.typography.bodySmall)
            status.duraProposal?.let { p ->
                Text(stringResource(R.string.learner_dura_change, p.from, p.to), fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium)) {
                    Button(onClick = { confirm = true }, enabled = !status.busy) { Text(stringResource(R.string.learner_accept)) }
                    OutlinedButton(onClick = onRefuse, enabled = !status.busy) { Text(stringResource(R.string.learner_refuse)) }
                }
                if (confirm) AlertDialog(
                    onDismissRequest = { confirm = false },
                    title = { Text(stringResource(R.string.learner_dura_title)) },
                    text = { Text(stringResource(R.string.learner_dura_change, p.from, p.to), fontWeight = FontWeight.SemiBold) },
                    confirmButton = {
                        TextButton(onClick = {
                            confirm = false
                            onAccept()
                        }) { Text(stringResource(R.string.learner_accept)) }
                    },
                    dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.learner_cancel)) } }
                )
            }
        }
    }
}

/** The percentage card (safety): what it saw, its rule, a proposal with "Внести" / "Отказаться", or the hint. */
@Composable
private fun PctSection(status: Status, onAccept: () -> Unit, onRefuse: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(AapsSpacing.large), verticalArrangement = Arrangement.spacedBy(AapsSpacing.small)) {
            Text(stringResource(R.string.learner_pct_title), style = MaterialTheme.typography.titleMedium)
            status.lastPct?.let { Text(stringResource(R.string.learner_pct_seen, it.first, it.second), style = MaterialTheme.typography.bodyMedium) }
            Text(stringResource(R.string.learner_pct_rule), style = MaterialTheme.typography.bodySmall)
            status.pctProposal?.let { p ->
                Text(stringResource(R.string.learner_pct_change, p.from, p.to), fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium)) {
                    Button(onClick = { confirm = true }, enabled = !status.busy) { Text(stringResource(R.string.learner_accept)) }
                    OutlinedButton(onClick = onRefuse, enabled = !status.busy) { Text(stringResource(R.string.learner_refuse)) }
                }
                if (confirm) AlertDialog(
                    onDismissRequest = { confirm = false },
                    title = { Text(stringResource(R.string.learner_pct_title)) },
                    text = { Text(stringResource(R.string.learner_pct_change, p.from, p.to), fontWeight = FontWeight.SemiBold) },
                    confirmButton = {
                        TextButton(onClick = {
                            confirm = false
                            onAccept()
                        }) { Text(stringResource(R.string.learner_accept)) }
                    },
                    dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.learner_cancel)) } }
                )
            } ?: run {
                if (status.lastPct?.third == true) Text(stringResource(R.string.learner_pct_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/** The sensor hint: the switch (off by default), its rule, and the last hint. Only text, nothing is changed. */
@Composable
private fun SensorSection(status: Status, onSwitch: (Boolean) -> Unit) {
    val dateUtil = LocalDateUtil.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(AapsSpacing.large), verticalArrangement = Arrangement.spacedBy(AapsSpacing.small)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.learner_sensor_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Switch(checked = status.sensorHints, onCheckedChange = onSwitch)
            }
            Text(stringResource(R.string.learner_sensor_rule), style = MaterialTheme.typography.bodySmall)
            if (status.sensorHints) status.sensorHint?.let { h ->
                Text(
                    when (h.kind) {
                        SensorHintKind.FIRST_HOURS -> stringResource(R.string.learner_sensor_last_first_hours, dateUtil.dateAndTimeString(h.changeAt))
                        SensorHintKind.JUMP        -> stringResource(
                            R.string.learner_sensor_last_jump, dateUtil.dateAndTimeString(h.at), (h.fromMgdl ?: 0.0) / 18.0, (h.toMgdl ?: 0.0) / 18.0
                        )
                    },
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/** The cannula hint: the switch (off by default), its rule, and the hint shown now. Only text, nothing is changed. */
@Composable
private fun CannulaSection(status: Status, onSwitch: (Boolean) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(AapsSpacing.large), verticalArrangement = Arrangement.spacedBy(AapsSpacing.small)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.learner_cannula_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Switch(checked = status.cannulaHints, onCheckedChange = onSwitch)
            }
            Text(stringResource(R.string.learner_cannula_rule), style = MaterialTheme.typography.bodySmall)
            if (status.cannulaHints) status.cannulaHint?.let { h ->
                Text(
                    stringResource(R.string.learner_cannula_now, h.ageH, h.highPct),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/** The change evaluation: the switch (off by default), its rule, a change waiting for its verdict, and the last verdict. */
@Composable
private fun EvalSection(status: Status, onSwitch: (Boolean) -> Unit) {
    val dateUtil = LocalDateUtil.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(AapsSpacing.large), verticalArrangement = Arrangement.spacedBy(AapsSpacing.small)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.learner_eval_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Switch(checked = status.evalOn, onCheckedChange = onSwitch)
            }
            Text(stringResource(R.string.learner_eval_rule), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.learner_eval_failed_note), style = MaterialTheme.typography.bodySmall)
            if (!status.evalOn) return@Column
            status.evalPending?.let { p ->
                Text(
                    stringResource(R.string.learner_eval_pending, p.label, dateUtil.dateAndTimeString(p.at), dateUtil.dateAndTimeString(p.at + 3 * 86_400_000L)),
                    style = MaterialTheme.typography.bodyMedium
                )
                if (p.warned) Text(stringResource(R.string.learner_eval_warned), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
            status.lastEval?.let { d ->
                Text(
                    stringResource(
                        R.string.learner_eval_done, d.label, dateUtil.dateAndTimeString(d.at),
                        stringResource(
                            when {
                                d.eval.rollback                            -> R.string.learner_eval_verdict_rollback
                                d.eval.verdict == EvalVerdict.BETTER       -> R.string.learner_eval_verdict_better
                                d.eval.verdict == EvalVerdict.WORSE        -> R.string.learner_eval_verdict_worse
                                d.eval.verdict == EvalVerdict.SAME         -> R.string.learner_eval_verdict_same
                                else                                       -> R.string.learner_eval_verdict_no_data
                            }
                        )
                    ),
                    fontWeight = FontWeight.SemiBold
                )
                StatsLine(R.string.learner_eval_before, d.eval.before)
                StatsLine(R.string.learner_eval_after, d.eval.after)
            }
        }
    }
}

@Composable
private fun StatsLine(label: Int, s: EvalStats) {
    Text(
        stringResource(R.string.learner_eval_stats, stringResource(label), s.low39Pct, s.low30Pct, s.high10Pct, s.cvPct, s.minMgdl / 18.0),
        style = MaterialTheme.typography.bodySmall
    )
}

/** The weekly digest: the switch (off by default), this week against the week before, and the goals met. */
@Composable
private fun DigestSection(status: Status, onSwitch: (Boolean) -> Unit) {
    val dateUtil = LocalDateUtil.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(AapsSpacing.large), verticalArrangement = Arrangement.spacedBy(AapsSpacing.small)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.learner_digest_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Switch(checked = status.digestOn, onCheckedChange = onSwitch)
            }
            Text(stringResource(R.string.learner_digest_rule), style = MaterialTheme.typography.bodySmall)
            if (!status.digestOn) return@Column
            status.lastDigest?.let { (at, d) ->
                Text(stringResource(R.string.learner_digest_made, dateUtil.dateAndTimeString(at), d.goalsMet, d.goals), fontWeight = FontWeight.SemiBold)
                WeekLine(R.string.learner_digest_this, d.thisWeek)
                WeekLine(R.string.learner_digest_last, d.lastWeek)
            }
        }
    }
}

@Composable
private fun WeekLine(label: Int, w: WeekStats) {
    Text(
        stringResource(
            R.string.learner_digest_week, stringResource(label), w.inRangePct, w.low39Pct, w.low30Pct, w.high10Pct, w.cvPct, w.meanMgdl / 18.0,
            w.insulinPerDay, w.hypoGPerDay
        ),
        style = MaterialTheme.typography.bodySmall
    )
}

/** The CR block card (safety, only a weaker CR): what it saw per block, its rule, and a proposal with "Внести" / "Отказаться". */
@Composable
private fun CrSection(status: Status, onAccept: () -> Unit, onRefuse: () -> Unit, blockName: (String) -> String) {
    var confirm by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(AapsSpacing.large), verticalArrangement = Arrangement.spacedBy(AapsSpacing.small)) {
            Text(stringResource(R.string.learner_cr_title), style = MaterialTheme.typography.titleMedium)
            status.lastCr?.takeIf { it.isNotEmpty() }?.let { m ->
                m.forEach { (block, pct) -> Text(stringResource(R.string.learner_cr_seen, blockName(block), pct), style = MaterialTheme.typography.bodyMedium) }
            }
            Text(stringResource(R.string.learner_cr_rule), style = MaterialTheme.typography.bodySmall)
            status.crProposal?.let { p ->
                val from = p.from.values.firstOrNull() ?: 0.0
                val to = p.to.values.firstOrNull() ?: 0.0
                Text(stringResource(R.string.learner_cr_change, blockName(p.block), from, to), fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium)) {
                    Button(onClick = { confirm = true }, enabled = !status.busy) { Text(stringResource(R.string.learner_accept)) }
                    OutlinedButton(onClick = onRefuse, enabled = !status.busy) { Text(stringResource(R.string.learner_refuse)) }
                }
                if (confirm) AlertDialog(
                    onDismissRequest = { confirm = false },
                    title = { Text(stringResource(R.string.learner_cr_title)) },
                    text = { Text(stringResource(R.string.learner_cr_change, blockName(p.block), from, to), fontWeight = FontWeight.SemiBold) },
                    confirmButton = {
                        TextButton(onClick = {
                            confirm = false
                            onAccept()
                        }) { Text(stringResource(R.string.learner_accept)) }
                    },
                    dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.learner_cancel)) } }
                )
            }
        }
    }
}

/** The low meal hint: the switch (off by default) and its rule. Only text, nothing is changed. */
@Composable
private fun LowMealSection(status: Status, onSwitch: (Boolean) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(AapsSpacing.large), verticalArrangement = Arrangement.spacedBy(AapsSpacing.small)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.learner_low_meal_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Switch(checked = status.lowMealHints, onCheckedChange = onSwitch)
            }
            Text(stringResource(R.string.learner_low_meal_rule), style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** The special day button: 24 h without learning; the readings of that time are left out of the cards later. */
@Composable
private fun SpecialSection(status: Status, onSwitch: (Boolean) -> Unit) {
    val dateUtil = LocalDateUtil.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(AapsSpacing.large), verticalArrangement = Arrangement.spacedBy(AapsSpacing.small)) {
            Text(stringResource(R.string.learner_special_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.learner_special_rule), style = MaterialTheme.typography.bodySmall)
            if (status.specialUntil > 0L) {
                Text(stringResource(R.string.learner_special_on, dateUtil.dateAndTimeString(status.specialUntil)), color = MaterialTheme.colorScheme.primary)
                OutlinedButton(onClick = { onSwitch(false) }) { Text(stringResource(R.string.learner_special_end)) }
            } else Button(onClick = { onSwitch(true) }) { Text(stringResource(R.string.learner_special_start)) }
        }
    }
}

/** What the learner learned about hypo treatments: how much 1 g lifts, and how many grams for 3.5 and 3.0 mmol/L. */
@Composable
private fun HypoSizeSection(status: Status) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(AapsSpacing.large), verticalArrangement = Arrangement.spacedBy(AapsSpacing.small)) {
            Text(stringResource(R.string.learner_hypo_title), style = MaterialTheme.typography.titleMedium)
            status.hypoSize?.let { h ->
                Text(stringResource(R.string.learner_hypo_seen, h.risePerGMgdl / 18.0, h.treatments), style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(R.string.learner_hypo_suggest, HypoSizeEngine.suggest(h, 63.0), HypoSizeEngine.suggest(h, 54.0)),
                    fontWeight = FontWeight.SemiBold
                )
            } ?: Text(stringResource(R.string.learner_hypo_none), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.learner_hypo_rule), style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** The insulin curve watch: the switch (off by default), the curve now, and the last change. Only text. */
@Composable
private fun CurveSection(status: Status, onSwitch: (Boolean) -> Unit) {
    val dateUtil = LocalDateUtil.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(AapsSpacing.large), verticalArrangement = Arrangement.spacedBy(AapsSpacing.small)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.learner_curve_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Switch(checked = status.curveWatch, onCheckedChange = onSwitch)
            }
            Text(stringResource(R.string.learner_curve_rule), style = MaterialTheme.typography.bodySmall)
            status.curve?.let { Text(stringResource(R.string.learner_curve_now, it.label, it.peakMin, it.diaH), style = MaterialTheme.typography.bodyMedium) }
            if (status.curveWatch) status.curveChange?.let { (at, before) ->
                Text(
                    stringResource(R.string.learner_curve_changed, dateUtil.dateAndTimeString(at), before.label, before.peakMin, before.diaH),
                    color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/** "Why was it low": the switch (off by default), counts per likely cause and the last lows. Only text. */
@Composable
private fun LowCauseSection(status: Status, onSwitch: (Boolean) -> Unit) {
    val dateUtil = LocalDateUtil.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(AapsSpacing.large), verticalArrangement = Arrangement.spacedBy(AapsSpacing.small)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.learner_lows_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Switch(checked = status.lowCausesOn, onCheckedChange = onSwitch)
            }
            Text(stringResource(R.string.learner_lows_rule), style = MaterialTheme.typography.bodySmall)
            if (!status.lowCausesOn) return@Column
            if (status.lows.isEmpty()) {
                Text(stringResource(R.string.learner_lows_none), style = MaterialTheme.typography.bodyMedium)
                return@Column
            }
            LowCause.entries.forEach { c ->
                val n = status.lows.count { it.cause == c }
                if (n > 0) Text(stringResource(R.string.learner_lows_count, stringResource(causeName(c)), n), fontWeight = FontWeight.SemiBold)
            }
            status.lows.takeLast(10).reversed().forEach { e ->
                Text(
                    stringResource(R.string.learner_lows_item, dateUtil.dateAndTimeString(e.start), e.minMgdl / 18.0, e.minutes, stringResource(causeName(e.cause))),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

private fun causeName(c: LowCause): Int = when (c) {
    LowCause.MEAL_AT_LOW -> R.string.learner_cause_meal_at_low
    LowCause.REBOUND     -> R.string.learner_cause_rebound
    LowCause.AFTER_MEAL  -> R.string.learner_cause_after_meal
    LowCause.NIGHT       -> R.string.learner_cause_night
    LowCause.OTHER       -> R.string.learner_cause_other
}

/** The night peak check: the switch (off by default), the last fit and a proposal (text only). */
@Composable
private fun PeakFitSection(status: Status, onSwitch: (Boolean) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(AapsSpacing.large), verticalArrangement = Arrangement.spacedBy(AapsSpacing.small)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.learner_peak_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Switch(checked = status.peakFitOn, onCheckedChange = onSwitch)
            }
            Text(stringResource(R.string.learner_peak_rule), style = MaterialTheme.typography.bodySmall)
            if (!status.peakFitOn) return@Column
            status.peakFit?.let { f ->
                Text(stringResource(R.string.learner_peak_seen, f.bestPeakMin, f.currentPeakMin, f.nights), style = MaterialTheme.typography.bodyMedium)
                if (PeakFitEngine.proposes(f))
                    Text(stringResource(R.string.learner_peak_propose, f.bestPeakMin), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            } ?: Text(stringResource(R.string.learner_peak_none), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** The low predictor: the switch (off by default), what it learned, and its record. */
@Composable
private fun LowPredSection(status: Status, onSwitch: (Boolean) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(AapsSpacing.large), verticalArrangement = Arrangement.spacedBy(AapsSpacing.small)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.learner_pred_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Switch(checked = status.lowPredOn, onCheckedChange = onSwitch)
            }
            Text(stringResource(R.string.learner_pred_rule), style = MaterialTheme.typography.bodySmall)
            if (status.lowPredOn) Text(
                stringResource(R.string.learner_pred_seen, status.lowPredStats.first, status.lowPredStats.second, status.lowPredMargin / 18.0),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
/**
 * The rebound guard: the switch (off by default), its rule, and whether it holds the loop now. A switch to "on" asks
 * first, because this one changes what the loop does (it only takes insulin away).
 */
@Composable
private fun ReboundSection(status: Status, onSwitch: (Boolean) -> Unit) {
    val dateUtil = LocalDateUtil.current
    var confirm by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(AapsSpacing.large), verticalArrangement = Arrangement.spacedBy(AapsSpacing.small)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.learner_guard_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Switch(checked = status.reboundOn, onCheckedChange = { on -> if (on) confirm = true else onSwitch(false) })
            }
            Text(stringResource(R.string.learner_guard_rule), style = MaterialTheme.typography.bodySmall)
            if (status.reboundOn) Text(
                if (status.guard.active) stringResource(R.string.learner_guard_holds, dateUtil.timeString(status.guard.until))
                else stringResource(R.string.learner_guard_idle),
                style = MaterialTheme.typography.bodyMedium,
                color = if (status.guard.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text(stringResource(R.string.learner_guard_title)) },
        text = { Text(stringResource(R.string.learner_guard_confirm)) },
        confirmButton = {
            TextButton(onClick = {
                confirm = false
                onSwitch(true)
            }) { Text(stringResource(R.string.learner_guard_turn_on)) }
        },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.learner_cancel)) } }
    )
}
