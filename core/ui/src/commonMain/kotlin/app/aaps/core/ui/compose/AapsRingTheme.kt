package app.aaps.core.ui.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import app.aaps.core.ui.compose.navigation.DarkElementColors
import app.aaps.core.ui.compose.navigation.LocalElementColors

/**
 * Our addition (docs/OUR_CHANGES.md): the look of the "Кольцо" overview (cards and tiles as on the AIMI screen).
 * User 21.11: light by day, dark by night - it follows the AAPS dark mode (phone setting or the AAPS choice).
 * Dark: a navy Material scheme and the dark AAPS colors. Light: the normal light theme with light cards.
 */
@Immutable
data class RingLook(
    val background: Color,
    val cardTop: Color,
    val cardBottom: Color,
    val cardBorder: Color,
    val tile: Color,
    val tileBorder: Color,
    val text: Color,
    val textDim: Color,
    val warning: Color,
    val critical: Color
)

val NightLook = RingLook(
    background = Color(0xFF0B1120),
    cardTop = Color(0xFF1A2540),
    cardBottom = Color(0xFF0E1628),
    cardBorder = Color(0x33A9B8D6),
    tile = Color(0xFF1F2A42),
    tileBorder = Color(0x26A9B8D6),
    text = Color(0xFFE5E9F0),
    textDim = Color(0xFF9AA4B5),
    warning = Color(0xFFF59E0B),
    critical = Color(0xFFEF4444)
)

val DayLook = RingLook(
    background = Color(0xFFEEF2F8),
    cardTop = Color(0xFFFFFFFF),
    cardBottom = Color(0xFFF3F6FB),
    cardBorder = Color(0x332B3A55),
    tile = Color(0xFFF5F8FD),
    tileBorder = Color(0x262B3A55),
    text = Color(0xFF111827),
    textDim = Color(0xFF5B6576),
    warning = Color(0xFFD97706),
    critical = Color(0xFFDC2626)
)

val LocalRingLook = staticCompositionLocalOf { DayLook }

/** The ring look: dark navy when the AAPS theme around is dark, light cards when it is light. */
@Composable
fun AapsRingTheme(content: @Composable () -> Unit) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    if (!dark) {
        CompositionLocalProvider(LocalRingLook provides DayLook, content = content)
        return
    }
    val night = NightLook
    val scheme = darkColorScheme(
        primary = Color(0xFF38A9F5),
        onPrimary = Color(0xFF041322),
        secondaryContainer = Color(0xFF1E3A5F),
        onSecondaryContainer = Color(0xFFE2E8F0),
        background = night.background,
        onBackground = night.text,
        surface = night.background,
        onSurface = night.text,
        surfaceVariant = Color(0xFF1C2740),
        onSurfaceVariant = night.textDim,
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
        LocalRingLook provides night,
    ) {
        MaterialTheme(colorScheme = scheme, typography = MaterialTheme.typography, content = content)
    }
}

/** [AapsRingTheme] when [on], the normal theme around it when not. */
@Composable
fun AapsRingThemeIf(on: Boolean, content: @Composable () -> Unit) {
    if (on) AapsRingTheme(content) else content()
}
