package app.aaps.core.ui.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import app.aaps.core.ui.compose.navigation.DarkElementColors
import app.aaps.core.ui.compose.navigation.LocalElementColors

/**
 * Our addition (docs/OUR_CHANGES.md): a dark navy look for the "Кольцо" overview, always dark whatever the phone and
 * the AAPS dark mode setting say (user 21.11: "всегда тёмный"). It gives the dark AAPS colors (glucose, treatments)
 * and a navy Material scheme; typography and scale stay those of [AapsTheme] around it.
 */
@Composable
fun AapsNightTheme(content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = NightColors.accent,
        onPrimary = Color(0xFF041322),
        secondaryContainer = Color(0xFF1E3A5F),
        onSecondaryContainer = Color(0xFFE2E8F0),
        background = NightColors.background,
        onBackground = NightColors.text,
        surface = NightColors.background,
        onSurface = NightColors.text,
        surfaceVariant = Color(0xFF1C2740),
        onSurfaceVariant = NightColors.textDim,
        surfaceContainerLowest = Color(0xFF0A1020),
        surfaceContainerLow = Color(0xFF0F1729),
        surfaceContainer = Color(0xFF131C31),
        surfaceContainerHigh = Color(0xFF172238),
        surfaceContainerHighest = Color(0xFF1B2740),
        outline = Color(0xFF3A4760),
        outlineVariant = Color(0xFF26324A)
    )
    CompositionLocalProvider(
        LocalGeneralColors provides DarkGeneralColors,
        LocalElementColors provides DarkElementColors,
    ) {
        MaterialTheme(colorScheme = scheme, typography = MaterialTheme.typography, content = content)
    }
}

/** [AapsNightTheme] when [on], the normal theme around it when not. */
@Composable
fun AapsNightThemeIf(on: Boolean, content: @Composable () -> Unit) {
    if (on) AapsNightTheme(content) else content()
}

/** The colors of the night look, for drawing its cards and tiles. */
object NightColors {

    val background = Color(0xFF0B1120)
    val cardTop = Color(0xFF1A2540)
    val cardBottom = Color(0xFF0E1628)
    val cardBorder = Color(0x33A9B8D6)
    val tile = Color(0xFF1F2A42)
    val tileBorder = Color(0x26A9B8D6)
    val text = Color(0xFFE5E9F0)
    val textDim = Color(0xFF9AA4B5)
    val accent = Color(0xFF38A9F5)
    val warning = Color(0xFFF59E0B)
    val critical = Color(0xFFEF4444)
}
