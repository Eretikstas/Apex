package app.aaps.core.ui.compose.preference

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.DoubleKey
import androidx.compose.runtime.mutableStateMapOf
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.LocalConfig
import app.aaps.core.ui.compose.LocalPreferences
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.google.common.truth.Truth.assertThat
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import app.aaps.core.interfaces.configuration.Config as AppConfig

/**
 * Robolectric render tests for the Adaptive*PreferenceItem composables (key-driven rows), mirroring
 * their previews. Locals are provided outside [PreviewTheme] (whose own ProvidePreferenceTheme reads
 * LocalPreferences before providing it — fine in IDE inspection mode, needs an outer provider in tests).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class AdaptivePreferenceItemsTest {

    @get:Rule
    val compose = createComposeRule()

    private val prefs: Preferences = mock()
    private val config: AppConfig = mock()

    private fun render(block: @Composable () -> Unit) = compose.setContent {
        CompositionLocalProvider(LocalPreferences provides prefs, LocalConfig provides config) {
            PreviewTheme { block() }
        }
    }

    @Test
    fun switchItemRenders() {
        render { AdaptiveSwitchPreferenceItem(booleanKey = BooleanKey.OverviewKeepScreenOn) }
        compose.onRoot().assertIsDisplayed()
    }

    @Test
    fun stringItemRenders() {
        render { AdaptiveStringPreferenceItem(stringKey = StringKey.GeneralInstanceName) }
        compose.onRoot().assertIsDisplayed()
    }

    @Test
    fun intItemRenders() {
        render { AdaptiveIntPreferenceItem(intKey = IntKey.OverviewCarbsButtonIncrement1) }
        compose.onRoot().assertIsDisplayed()
    }

    @Test
    fun doubleItemRenders() {
        render { AdaptiveDoublePreferenceItem(doubleKey = DoubleKey.OverviewInsulinButtonIncrement1) }
        compose.onRoot().assertIsDisplayed()
    }

    @Test
    fun doublePreferenceStateRepairsNaNAndInfinitiesToDefault() {
        val key = DoubleKey.ApsAutoIsfBgAccelWeight
        listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY).forEach { invalid ->
            whenever(prefs.get(key)).thenReturn(invalid)
            val state = PreferenceDoubleState(prefs, key, mutableStateMapOf())
            assertThat(state.value).isEqualTo(key.defaultValue)
            verify(prefs, atLeastOnce()).put(key, key.defaultValue)
        }
    }

    @Test
    fun doublePreferenceStateClampsPersistedAndEnteredFiniteValues() {
        val key = DoubleKey.ApsAutoIsfDuraWeight
        whenever(prefs.get(key)).thenReturn(key.max + 10.0)
        val state = PreferenceDoubleState(prefs, key, mutableStateMapOf())
        assertThat(state.value).isEqualTo(key.max)
        verify(prefs, atLeastOnce()).put(key, key.max)

        state.value = key.min - 10.0
        assertThat(state.value).isEqualTo(key.min)
        verify(prefs, atLeastOnce()).put(key, key.min)

        state.value = Double.NaN
        assertThat(state.value).isEqualTo(key.defaultValue)
        state.value = Double.POSITIVE_INFINITY
        assertThat(state.value).isEqualTo(key.max)
        state.value = Double.NEGATIVE_INFINITY
        assertThat(state.value).isEqualTo(key.min)
    }

    @Test
    fun listIntItemRenders() {
        render {
            AdaptiveListIntPreferenceItem(
                intKey = IntKey.OverviewCarbsButtonIncrement1,
                entries = listOf("5g", "10g", "15g", "20g"),
                entryValues = listOf(5, 10, 15, 20)
            )
        }
        compose.onRoot().assertIsDisplayed()
    }

    @Test
    fun passwordItemRenders() {
        render {
            AdaptivePasswordPreferenceItem(
                stringKey = StringKey.ProtectionSettingsPassword,
                hashPassword = { it },
                onShowMessage = {}
            )
        }
        compose.onRoot().assertIsDisplayed()
    }
}
