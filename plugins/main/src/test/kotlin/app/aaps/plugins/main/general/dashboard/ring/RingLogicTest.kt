package app.aaps.plugins.main.general.dashboard.ring

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class RingLogicTest {

    @Test
    fun `a low prediction under 6 mmol is falling`() {
        assertThat(RingLogic.hourState(90.0, -4.0, 60.0, smbLimited = false)).isEqualTo(RingHourState.FALLING)
    }

    @Test
    fun `a low prediction at a high BG is not falling yet`() {
        assertThat(RingLogic.hourState(140.0, -4.0, 60.0, smbLimited = false)).isEqualTo(RingHourState.STABLE)
    }

    @Test
    fun `rising over 8 mmol is rising, and a capped SMB too`() {
        assertThat(RingLogic.hourState(160.0, 3.0, 150.0, smbLimited = false)).isEqualTo(RingHourState.RISING)
        assertThat(RingLogic.hourState(120.0, 0.0, 110.0, smbLimited = true)).isEqualTo(RingHourState.RISING)
    }

    @Test
    fun `falling wins over a capped SMB`() {
        assertThat(RingLogic.hourState(95.0, -5.0, 55.0, smbLimited = true)).isEqualTo(RingHourState.FALLING)
    }

    @Test
    fun `no data is stable`() {
        assertThat(RingLogic.hourState(null, null, null, smbLimited = false)).isEqualTo(RingHourState.STABLE)
        assertThat(RingLogic.hourState(110.0, 0.0, null, smbLimited = false)).isEqualTo(RingHourState.STABLE)
    }

    @Test
    fun `stats split into three parts that add to 100`() {
        val s = RingLogic.stats(listOf(60.0, 100.0, 120.0, 150.0, 200.0, 0.0))!!
        assertThat(s.count).isEqualTo(5)
        assertThat(s.lowPct).isEqualTo(20)
        assertThat(s.highPct).isEqualTo(20)
        assertThat(s.inRangePct).isEqualTo(60)
        assertThat(s.lowPct + s.inRangePct + s.highPct).isEqualTo(100)
        assertThat(s.cvPct).isGreaterThan(0)
    }

    @Test
    fun `no readings give no stats`() {
        assertThat(RingLogic.stats(emptyList())).isNull()
    }
}
