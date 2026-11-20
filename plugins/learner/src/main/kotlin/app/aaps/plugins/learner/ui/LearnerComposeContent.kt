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
import app.aaps.plugins.learner.HourSource
import app.aaps.plugins.learner.LearnerPlugin
import app.aaps.plugins.learner.R

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

        status.proposal?.let { p ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(AapsSpacing.large), verticalArrangement = Arrangement.spacedBy(AapsSpacing.small)) {
                    Text(stringResource(R.string.learner_proposal_title, p.profileName), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.learner_proposal_why), style = MaterialTheme.typography.bodySmall)
                    p.changes.forEach { ChangeRow(it) }
                    Row(horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium)) {
                        Button(onClick = { confirm = true }, enabled = !status.busy) { Text(stringResource(R.string.learner_accept)) }
                        OutlinedButton(onClick = { plugin.refuse() }, enabled = !status.busy) { Text(stringResource(R.string.learner_refuse)) }
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
                        plugin.accept()
                    }) { Text(stringResource(R.string.learner_accept)) }
                },
                dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.learner_cancel)) } }
            )
        } ?: Text(stringResource(R.string.learner_no_proposal), style = MaterialTheme.typography.bodyMedium)

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
