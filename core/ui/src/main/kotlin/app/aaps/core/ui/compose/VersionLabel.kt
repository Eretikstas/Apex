package app.aaps.core.ui.compose

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import app.aaps.core.keys.LongComposedKey

/**
 * The short app version ("AIMI 4.0"), colored as the build warning wants: committed, about to expire,
 * or uncommitted. Shown only in a build that runs a loop or a pump. Used by the version overlay and by
 * skins that show it inside their own layout (Ring).
 */
@Composable
fun VersionLabel(modifier: Modifier = Modifier, fontSize: TextUnit = 10.sp) {
    val config = LocalConfig.current
    val preferences = LocalPreferences.current
    if (!config.APS && !config.PUMPCONTROL) return
    val colors = AapsTheme.generalColors
    val versionColor = when {
        config.COMMITTED                                                          -> colors.versionCommitted
        preferences.get(LongComposedKey.AppExpiration, config.VERSION_NAME) != 0L -> colors.versionWarning
        else                                                                      -> colors.versionUncommitted
    }
    Text(text = shortVersion(config.VERSION_NAME), color = versionColor, fontSize = fontSize, maxLines = 1, modifier = modifier)
}

/** "4.0.0.0-dev.AIMI.211026" -> "AIMI 4.0". A name without a leading number is shown as it is. */
fun shortVersion(versionName: String): String =
    Regex("""^(\d+)\.(\d+)""").find(versionName)?.let { "AIMI ${it.groupValues[1]}.${it.groupValues[2]}" } ?: versionName
