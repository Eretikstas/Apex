package app.aaps.ui.compose.main

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import app.aaps.ui.compose.manageSheet.ManageBottomSheetContent
import app.aaps.ui.compose.testing.AapsScreenFixture
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Pictures of the main menu (☰) and the "Управление" sheet in Russian, for a person to look at. Writes PNG files to
 * `build/ring-shots`. These are the base AAPS screens, unchanged by our work apart from the Russian.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ru-rRU-w411dp-h900dp-port-xhdpi")
class MenuScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val screen = AapsScreenFixture()

    @Test
    fun drawer() = shoot("menu_drawer") {
        MainDrawer(drawerState = rememberDrawerState(DrawerValue.Open), versionName = "4.0.0", onNavigate = {}, isTreatmentsEnabled = true, showExit = true)
    }

    @Test
    fun manage() = shoot("menu_manage") {
        ManageBottomSheetContent(
            showTempTarget = true, showTempBasal = true, showCancelTempBasal = false, showExtendedBolus = true,
            showCancelExtendedBolus = false, showBatteryChange = true, showFill = true, showAuthorizedClients = true,
            cancelTempBasalText = "", cancelExtendedBolusText = "", customActions = emptyList()
        )
    }

    private fun shoot(name: String, content: @Composable () -> Unit) {
        compose.setContent {
            screen.Content {
                Box(Modifier.requiredSize(411.dp, 900.dp).testTag(TAG)) { Surface(Modifier.fillMaxSize()) { content() } }
            }
        }
        compose.waitForIdle()
        val dir = File("build/ring-shots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { compose.onNodeWithTag(TAG).captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private companion object {

        const val TAG = "menu_shot"
    }
}
