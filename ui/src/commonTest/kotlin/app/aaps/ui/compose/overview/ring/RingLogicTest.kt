package app.aaps.ui.compose.overview.ring

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RingLogicTest {

    @Test
    fun statsCountTheGoalRanges() {
        // 2 low (one very low), 6 in range, 2 high
        val s = RingLogic.stats(listOf(50.0, 65.0, 80.0, 100.0, 120.0, 140.0, 160.0, 180.0, 200.0, 250.0))!!
        assertEquals(10, s.count)
        assertEquals(20.0, s.lowPct)
        assertEquals(10.0, s.veryLowPct)
        assertEquals(60.0, s.inRangePct)
        assertEquals(20.0, s.highPct)
    }

    @Test
    fun noValuesNoStats() {
        assertNull(RingLogic.stats(emptyList()))
    }

    @Test
    fun fallingWinsUnderSixWithALowPrediction() {
        assertEquals(RingHour.FALLING, RingLogic.hour(100.0, -5.0, 60.0, smbLimited = false))
    }

    @Test
    fun lowPredictionAboveSixIsNotFalling() {
        assertEquals(RingHour.STABLE, RingLogic.hour(130.0, -5.0, 60.0, smbLimited = false))
    }

    @Test
    fun highAndRisingIsRising() {
        assertEquals(RingHour.RISING, RingLogic.hour(160.0, 4.0, 150.0, smbLimited = false))
    }

    @Test
    fun aCutSmbIsRising() {
        assertEquals(RingHour.RISING, RingLogic.hour(120.0, 0.0, 110.0, smbLimited = true))
    }

    @Test
    fun noGlucoseIsUnknown() {
        assertEquals(RingHour.UNKNOWN, RingLogic.hour(null, null, null, smbLimited = false))
    }
}
