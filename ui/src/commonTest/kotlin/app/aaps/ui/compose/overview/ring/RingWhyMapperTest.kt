package app.aaps.ui.compose.overview.ring

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The plain "why" from real oref / AutoISF reason texts (DetermineBasalAutoISF), action from the numbers only. */
class RingWhyMapperTest {

    private val head = "COB: 0, Dev: 12, BGI: -1.2, ISF: 8.5, CR: 24.3, Target: 6, minPredBG 5.2, minGuardBG 4.8, IOBpredBG 5.1; "

    private fun map(
        reason: String, smb: Double = 0.0, temp: Boolean = false, rate: Double = 0.0, duration: Int = 0,
        eventual: Double? = 110.0, cob: Double = 0.0, allowed: Double? = null
    ) = RingWhyMapper.map(head + reason, smb, temp, rate, duration, eventual, cob, allowed)

    @Test
    fun oldDataIsNoData() {
        val why = map("If current system time 12:00 is correct, then BG data is too old. The last BG data was read 14m ago at 11:46")
        assertEquals(RingWhyFamily.NO_DATA, why.family)
        assertEquals(RingAction.NoChange, why.action)
    }

    @Test
    fun unchangedCgmIsNoData() {
        assertEquals(RingWhyFamily.NO_DATA, map("Error: CGM data is unchanged for the past ~45m").family)
    }

    @Test
    fun zeroTempIsLow() {
        val why = map("Eventual BG 3.4 < 6.0, setting 60m zero temp. ", temp = true, rate = 0.0, duration = 60, eventual = 61.0)
        assertEquals(RingWhyFamily.LOW, why.family)
        assertEquals(RingAction.ZeroBasal(60), why.action)
    }

    @Test
    fun lowGuardWithLowTempIsLow() {
        val why = map("minGuardBG 3.6 < 3.9; setting 30m low temp of 0.1U/h", temp = true, rate = 0.1, duration = 30)
        assertEquals(RingWhyFamily.LOW, why.family)
        assertEquals(RingAction.TempBasal(0.1, 30), why.action)
    }

    @Test
    fun negativeIobIsLow() {
        assertEquals(RingWhyFamily.LOW, map("IOB -0.5 < -0.2 ", temp = true, rate = 0.0, duration = 30).family)
    }

    @Test
    fun maxIobWithoutSmbIsEnoughIob() {
        assertEquals(RingWhyFamily.ENOUGH_IOB, map("IOB 3.1 > max_iob 3.0 ").family)
    }

    @Test
    fun waitingBetweenMicroBolusesIsEnoughIob() {
        assertEquals(RingWhyFamily.ENOUGH_IOB, map("Waiting 2m 30s to microbolus again.").family)
    }

    @Test
    fun smbWithCarbsIsMeal() {
        val why = map(
            "Eventual BG 9.8 >= 6.0,  insulinReq 0.62; maxBolus 0.4. Microbolusing 0.35U. ",
            smb = 0.35, temp = true, rate = 0.9, duration = 30, eventual = 176.0, cob = 24.0
        )
        assertEquals(RingWhyFamily.MEAL, why.family)
        assertEquals(RingAction.Smb(0.35, 0.9, 30), why.action)
        assertEquals(24.0, why.cobG)
    }

    @Test
    fun smbWithoutCarbsIsRise() {
        val why = map("Eventual BG 10.5 >= 6.0, insulinReq 0.4. Microbolusing 0.2U. ", smb = 0.2, eventual = 189.0)
        assertEquals(RingWhyFamily.RISE, why.family)
        assertEquals(RingAction.Smb(0.2, null, null), why.action)
    }

    @Test
    fun smbCutByALimitIsEnoughIobWithBothNumbers() {
        val why = map("Microbolusing 0.6U. ", smb = 0.6, eventual = 200.0, allowed = 0.3)
        assertEquals(RingWhyFamily.ENOUGH_IOB, why.family)
        assertTrue(why.limited)
        assertEquals(0.6, why.limitWantedU)
        assertEquals(0.3, why.limitGivenU)
    }

    @Test
    fun smbNotCutIsNotLimited() {
        val why = map("Microbolusing 0.2U. ", smb = 0.2, allowed = 0.2)
        assertFalse(why.limited)
        assertNull(why.limitWantedU)
    }

    @Test
    fun inRangeNoTempIsInRange() {
        assertEquals(RingWhyFamily.IN_RANGE, map("5.6-5.4 in range: no temp required").family)
    }

    @Test
    fun neutralTempIsInRange() {
        assertEquals(
            RingWhyFamily.IN_RANGE,
            map("Temp 0.3 <= current basal 0.3U/hr; doing nothing. ").family
        )
    }

    @Test
    fun highTempWithoutSmbIsRise() {
        val why = map("Eventual BG 11.2 > 6.0, temp 0.30 < 1.20U/hr. ", temp = true, rate = 1.2, duration = 30, eventual = 202.0)
        assertEquals(RingWhyFamily.RISE, why.family)
        assertEquals(RingAction.TempBasal(1.2, 30), why.action)
    }
}
