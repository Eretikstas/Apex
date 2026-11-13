package app.aaps.ui.compose.main

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.aaps.core.keys.LongComposedKey
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.LocalConfig
import app.aaps.core.ui.compose.LocalPreferences

/**
 * @see VersionOverlayPreview
 */
@Composable
fun VersionOverlay(
    modifier: Modifier = Modifier,
) {
    val config = LocalConfig.current
    val preferences = LocalPreferences.current
    if (config.APS || config.PUMPCONTROL) {
        val colors = AapsTheme.generalColors
        val versionColor = when {
            config.COMMITTED                                                          -> colors.versionCommitted
            preferences.get(LongComposedKey.AppExpiration, config.VERSION_NAME) != 0L -> colors.versionWarning
            else                                                                      -> colors.versionUncommitted
        }
        Text(
            // Only the product and its major.minor version ("AIMI 4.0"); the color still warns about the build.
            text = shortVersion(config.VERSION_NAME),
            color = versionColor,
            fontSize = 10.sp,
            modifier = modifier.padding(top = 4.dp, end = 4.dp)
        )
    }
}

/** "4.0.0.0-dev.AIMI.211026" -> "AIMI 4.0". A name without a leading number is shown as it is. */
internal fun shortVersion(versionName: String): String =
    Regex("""^(\d+)\.(\d+)""").find(versionName)?.let { "AIMI ${it.groupValues[1]}.${it.groupValues[2]}" } ?: versionName
