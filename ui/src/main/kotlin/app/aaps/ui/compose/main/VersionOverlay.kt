package app.aaps.ui.compose.main

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.aaps.core.ui.compose.VersionLabel

/**
 * Only the product and its major.minor version ("AIMI 4.0"); the color still warns about the build.
 *
 * @see VersionOverlayPreview
 */
@Composable
fun VersionOverlay(
    modifier: Modifier = Modifier,
) {
    VersionLabel(modifier = modifier.padding(top = 4.dp, end = 4.dp))
}
