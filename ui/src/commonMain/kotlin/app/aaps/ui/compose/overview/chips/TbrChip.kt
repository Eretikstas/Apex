package app.aaps.ui.compose.overview.chips

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.interfaces.overview.graph.TbrState
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.LocalAapsScale
import app.aaps.core.ui.compose.icons.IcNoTbr
import app.aaps.core.ui.compose.icons.IcTbrHigh
import app.aaps.core.ui.compose.icons.IcTbrLow
import app.aaps.core.ui.compose.navigation.label
import app.aaps.core.ui.compose.stringResource
import app.aaps.core.ui.compose.stringResourceOrNull

/**
 * @see TbrChipHighPreview
 * @see TbrChipLowPreview
 * @see TbrChipNonePreview
 */
@Composable
fun TbrChip(
    state: TbrState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** AutoISF fork: the basal rate running now in U/h. 0 shows the icon alone (no profile yet). */
    rate: Double = 0.0,
    /** AutoISF fork: a running temp basal in percent of the profile basal; null when none runs. */
    percent: Int? = null
) {
    val iconColor = AapsTheme.elementColors.tempBasal
    val containerColor = iconColor.copy(alpha = 0.2f)
    val haptic = LocalHapticFeedback.current

    // Disable the clickable Surface's 48dp minimum interactive size so this content-width chip
    // isn't padded to 48dp and centered, which would inset it from the row's right edge
    // (same guard IobChip uses).
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        // Whether a temp basal is running, and which way it goes, was carried ONLY by which of the
        // three icons was drawn - this chip has no text at all. So it announced "Temp basal" in
        // every state and a blind user could not tell a running temp basal from none.
        val tbrState = stringResource(state.toDescription())
        Surface(
            onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onClick() },
            shape = RoundedCornerShape(AapsSpacing.chipCornerRadius),
            color = containerColor,
            modifier = modifier
                .height(AapsSpacing.chipHeight * LocalAapsScale.current)
                .semantics { stateDescription = tbrState }
        ) {
            // AutoISF fork: the running basal rate as a number beside the icon, as in AAPS 3.
            // The profile rate is grey, a temp basal is in the basal colour with its percent.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AapsSpacing.small),
                modifier = Modifier.padding(horizontal = AapsSpacing.medium)
            ) {
                Icon(
                    imageVector = state.toIcon(),
                    contentDescription = stringResourceOrNull(ElementType.TEMP_BASAL.label()),
                    tint = iconColor,
                    modifier = Modifier.size(AapsSpacing.chipIconSize)
                )
                // Our change (docs/OUR_CHANGES.md): a temp basal of 0 (basal off) shows "0" too, not the icon alone.
                if (rate > 0.0 || state != TbrState.NONE) {
                    Text(
                        text = stringResource(CoreUiStrings.pump_base_basal_rate, rate),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (state == TbrState.NONE) MaterialTheme.colorScheme.onSurfaceVariant else iconColor,
                        maxLines = 1
                    )
                }
                // Our change (docs/OUR_CHANGES.md): the percent of the profile basal is always shown, day and night;
                // with no temp basal it is 100 %, so the chip always says what runs now and how it compares.
                val shownPercent = percent ?: if (state == TbrState.NONE && rate > 0.0) 100 else null
                if (shownPercent != null) {
                    Text(
                        text = stringResource(CoreUiStrings.units_format_percent, shownPercent),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * What the chip's icon means in words. Kept beside [toIcon] so the two cannot drift, and exhaustive
 * with no `else` so a new [TbrState] fails to compile here rather than silently going unspoken.
 */
private fun TbrState.toDescription(): TextRef = when (this) {
    TbrState.HIGH -> CoreUiStrings.tbr_state_above_profile
    TbrState.LOW  -> CoreUiStrings.tbr_state_below_profile
    TbrState.NONE -> CoreUiStrings.tbr_state_none
}

internal fun TbrState.toIcon(): ImageVector = when (this) {
    TbrState.HIGH -> IcTbrHigh
    TbrState.LOW  -> IcTbrLow
    TbrState.NONE -> IcNoTbr
}
