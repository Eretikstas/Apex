package app.aaps.ui.compose.overview.ring

import app.aaps.core.ui.compose.ringLookIsDark
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class RingLookTest {

    @Test
    fun `auto is dark over midnight from 21 to 7`() {
        assertThat(ringLookIsDark("auto", appDark = false, hour = 21, from = 21, to = 7)).isTrue()
        assertThat(ringLookIsDark("auto", appDark = false, hour = 2, from = 21, to = 7)).isTrue()
        assertThat(ringLookIsDark("auto", appDark = false, hour = 7, from = 21, to = 7)).isFalse()
        assertThat(ringLookIsDark("auto", appDark = true, hour = 12, from = 21, to = 7)).isFalse()
    }

    @Test
    fun `auto window inside one day and an empty window`() {
        assertThat(ringLookIsDark("auto", appDark = false, hour = 3, from = 1, to = 6)).isTrue()
        assertThat(ringLookIsDark("auto", appDark = false, hour = 6, from = 1, to = 6)).isFalse()
        assertThat(ringLookIsDark("auto", appDark = true, hour = 3, from = 5, to = 5)).isFalse()
    }

    @Test
    fun `fixed choices and as the app`() {
        assertThat(ringLookIsDark("dark", appDark = false, hour = 12, from = 21, to = 7)).isTrue()
        assertThat(ringLookIsDark("light", appDark = true, hour = 23, from = 21, to = 7)).isFalse()
        assertThat(ringLookIsDark("app", appDark = true, hour = 12, from = 21, to = 7)).isTrue()
        assertThat(ringLookIsDark("app", appDark = false, hour = 23, from = 21, to = 7)).isFalse()
    }
}
