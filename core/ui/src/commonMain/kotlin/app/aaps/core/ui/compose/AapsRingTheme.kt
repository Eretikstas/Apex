package app.aaps.core.ui.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.StringKey
import app.aaps.core.ui.compose.navigation.DarkElementColors
import app.aaps.core.ui.compose.navigation.LightElementColors
import app.aaps.core.ui.compose.navigation.LocalElementColors
import kotlinx.coroutines.delay
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * Our addition (docs/OUR_CHANGES.md): the look of the "Кольцо" overview (cards and tiles as on the AIMI screen).
 * User 21.11: light by day, dark by night, with a choice (Appearance): auto by time (default 21-07 dark), as the AAPS
 * color scheme, light, dark. Auto switches by itself on the hour.
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

/**
 * Is the ring look dark at [hour] (0-23), for the choice [mode]: "dark", "light", "app" (as the AAPS theme around,
 * [appDark]) or "auto" (dark from [from] o'clock until [to] o'clock, over midnight when [from] is later than [to]).
 */
fun ringLookIsDark(mode: String, appDark: Boolean, hour: Int, from: Int, to: Int): Boolean = when (mode) {
    "dark"  -> true
    "light" -> false
    "app"   -> appDark
    else    -> if (from == to) false else if (from < to) hour in from until to else hour >= from || hour < to
}

/** The hour of the day now; it changes by itself, so the auto look switches without a restart. */
@Composable
private fun hourNow(): Int {
    fun now() = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).hour
    var hour by remember { mutableIntStateOf(now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            hour = now()
        }
    }
    return hour
}

/** The ring look, light or dark navy by the user's choice (Appearance): auto by time, as AAPS, light or dark. */
@Composable
fun AapsRingTheme(content: @Composable () -> Unit) {
    val preferences = LocalPreferences.current
    val mode by preferences.observe(StringKey.OverviewRingTheme).collectAsState()
    val from by preferences.observe(IntKey.OverviewRingDarkFrom).collectAsState()
    val to by preferences.observe(IntKey.OverviewRingDarkTo).collectAsState()
    val appDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val dark = ringLookIsDark(mode, appDark, hourNow(), from, to)
    if (!dark) {
        // Light: the light AAPS scheme even when the app theme around is dark (choice "light" or a day hour in "auto").
        val scheme = if (appDark) lightColorScheme() else MaterialTheme.colorScheme
        CompositionLocalProvider(LocalGeneralColors provides LightGeneralColors, LocalElementColors provides LightElementColors, LocalRingLook provides DayLook) {
            MaterialTheme(colorScheme = scheme, typography = MaterialTheme.typography, content = content)
        }
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
