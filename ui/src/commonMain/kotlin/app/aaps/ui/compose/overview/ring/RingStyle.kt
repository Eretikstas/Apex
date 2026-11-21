package app.aaps.ui.compose.overview.ring

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.aaps.core.ui.compose.LocalRingLook
import app.aaps.core.ui.compose.RingLook
import app.aaps.core.ui.compose.StatusLevel
import app.aaps.ui.compose.overview.statusLights.StatusItem

private val CardShape = RoundedCornerShape(24.dp)
private val TileShape = RoundedCornerShape(16.dp)

/** A card of the ring look: gradient, thin light border, round corners. */
@Composable
internal fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val look = LocalRingLook.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(Brush.verticalGradient(listOf(look.cardTop, look.cardBottom)))
            .border(1.dp, look.cardBorder, CardShape)
            .padding(14.dp),
        content = content
    )
}

private fun StatusLevel.color(look: RingLook): Color = when (this) {
    StatusLevel.WARNING  -> look.warning
    StatusLevel.CRITICAL -> look.critical
    else                 -> look.text
}

/** One small tile beside the ring: icon, name, value. */
@Composable
internal fun RingTile(icon: ImageVector?, title: String, value: String, valueColor: Color? = null, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val look = LocalRingLook.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(TileShape)
            .background(look.tile)
            .border(1.dp, look.tileBorder, TileShape)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = look.textDim, modifier = Modifier.size(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.labelSmall, color = look.textDim, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = valueColor ?: look.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** A status line (cannula, insulin, sensor, battery) as a tile: its level and age, colored by how close to a change. */
@Composable
internal fun StatusTile(item: StatusItem?, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    if (item == null) return
    val worst = listOf(item.ageStatus, item.levelStatus).maxByOrNull { if (it == StatusLevel.UNSPECIFIED) -1 else it.ordinal } ?: StatusLevel.UNSPECIFIED
    val value = listOfNotNull(item.level?.takeIf { it.isNotBlank() }, item.age.takeIf { it.isNotBlank() }).joinToString(" · ").ifEmpty { "—" }
    RingTile(icon = item.icon, title = item.label, value = value, valueColor = worst.color(LocalRingLook.current), modifier = modifier, onClick = onClick)
}
