package app.aaps.plugins.main.general.dashboard.ring

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/** Each family with reason lines as AIMI writes them (see docs/RING_SKIN.md), and the fallback. */
class RingWhyMapperTest {

    private fun family(reason: String, smb: Double = 0.0, rate: Double? = null, duration: Int? = null) =
        RingWhyMapper.map(reason, smb, rate, duration, 120.0).family

    @Test
    fun `stale or broken sensor data is no data`() {
        assertThat(family("⚠️ Data Stale (14m) -> Logic Paused\n")).isEqualTo(RingWhyFamily.NO_DATA)
        assertThat(family("Error: CGM data is unchanged for the past ~45m")).isEqualTo(RingWhyFamily.NO_DATA)
        assertThat(family("CGM is calibrating, in ??? state, or noise is high")).isEqualTo(RingWhyFamily.NO_DATA)
    }

    @Test
    fun `safety halt and auditor are a safety stop`() {
        assertThat(family("COB: 0 | ⚠ Safety Halt: tube breach")).isEqualTo(RingWhyFamily.SAFETY_STOP)
        assertThat(family("Auditor Rejected: dose too large")).isEqualTo(RingWhyFamily.SAFETY_STOP)
    }

    @Test
    fun `low glucose suspend and falling BG are low`() {
        assertThat(family("🛑 LGS: BG=68 ≤ 70 → TBR 0U/h (30m)\n", rate = 0.0, duration = 30)).isEqualTo(RingWhyFamily.LOW)
        assertThat(family("🛑 Hard Brake: Falling Fast & Decelerating -> Zero Basal\n")).isEqualTo(RingWhyFamily.LOW)
        assertThat(family("BG 80–90 & free fall → basal to 0.\n")).isEqualTo(RingWhyFamily.LOW)
        assertThat(family(", BG is dropping (delta -6), setting basal to 0.")).isEqualTo(RingWhyFamily.LOW)
        assertThat(family("🛑 Hypo protection + safety margin: minBG=65 ≤ Threshold=70 → SMB=0")).isEqualTo(RingWhyFamily.LOW)
    }

    @Test
    fun `after a low is after low`() {
        assertThat(family("🍬hypoRecovery SMB off ")).isEqualTo(RingWhyFamily.AFTER_LOW)
        assertThat(family("🛡️ POST_HYPO_REBOUND: Rebond suspecté (40min depuis BG<70, COB=0.0g noMeal)\n")).isEqualTo(RingWhyFamily.AFTER_LOW)
        assertThat(family("[Hypo history] high BG boost ×1.5/×1.7 is OFF.")).isEqualTo(RingWhyFamily.AFTER_LOW)
    }

    @Test
    fun `too much insulin on board is enough iob`() {
        assertThat(family("IOB 2.10 > maxIOB 2.00")).isEqualTo(RingWhyFamily.ENOUGH_IOB)
        assertThat(family(" | IOB surveillance: high active insulin and falling prediction — SMB reduced")).isEqualTo(RingWhyFamily.ENOUGH_IOB)
        assertThat(family(" | STACK_CAP: Mult=1.40x→1.00x (IOB×ISF=90 ≥ BG−70)")).isEqualTo(RingWhyFamily.ENOUGH_IOB)
    }

    @Test
    fun `meal modes and prebolus are meal`() {
        assertThat(family(" Microbolusing 1/2 Breakfast Mode 0.60U.", smb = 0.6)).isEqualTo(RingWhyFamily.MEAL)
        assertThat(family("Early meal detected → TBR forced to 1.20U/h x30 (override).\n")).isEqualTo(RingWhyFamily.MEAL)
        assertThat(family("🍕slowCarb cap0.3 ")).isEqualTo(RingWhyFamily.MEAL)
    }

    @Test
    fun `rise without carbs is rise`() {
        assertThat(family("💉 SMB (UAM): 0.15 U", smb = 0.15)).isEqualTo(RingWhyFamily.RISE)
        assertThat(family("⚡ Hyper boost: x1.5 (BG=170, Δ=3.2)", smb = 0.2)).isEqualTo(RingWhyFamily.RISE)
        assertThat(family("🧹 Drift Terminator: Plateau detected (Δ0.4 Avg0.5 Dev5) -> ENGAGED")).isEqualTo(RingWhyFamily.RISE)
    }

    @Test
    fun `activity and honeymoon`() {
        assertThat(family("Morning activity: 50%")).isEqualTo(RingWhyFamily.ACTIVITY)
        assertThat(family("🏃effort×0.70 ")).isEqualTo(RingWhyFamily.ACTIVITY)
        assertThat(family("Honeymoon small slope.")).isEqualTo(RingWhyFamily.HONEYMOON)
    }

    @Test
    fun `normal and unknown`() {
        assertThat(family("Eventual BG 6.2 >= 6.0, ; setting current basal of 0.25 as temp.")).isEqualTo(RingWhyFamily.NORMAL)
        assertThat(family("something AIMI may add in a later version")).isEqualTo(RingWhyFamily.UNKNOWN)
        assertThat(family("")).isEqualTo(RingWhyFamily.UNKNOWN)
        assertThat(RingWhyMapper.map(null, 0.0, null, null, null).family).isEqualTo(RingWhyFamily.UNKNOWN)
    }

    @Test
    fun `a low wins over a meal in the same reason`() {
        assertThat(family("Meal mode rising ... 🛑 LGS: BG=68 ≤ 70 → TBR 0U/h (30m)")).isEqualTo(RingWhyFamily.LOW)
    }

    @Test
    fun `action comes from the numbers`() {
        assertThat(RingWhyMapper.map("x", 0.15, 0.45, 30, null).action).isEqualTo(RingAction.Smb(0.15, 0.45, 30))
        assertThat(RingWhyMapper.map("x", 0.0, 0.0, 30, null).action).isEqualTo(RingAction.ZeroBasal(30))
        assertThat(RingWhyMapper.map("x", 0.0, 0.8, 30, null).action).isEqualTo(RingAction.TempBasal(0.8, 30))
        assertThat(RingWhyMapper.map("x", 0.0, null, null, null).action).isEqualTo(RingAction.NoChange)
    }

    @Test
    fun `a cap that cut the dose gives both numbers`() {
        val why = RingWhyMapper.map("💉 SMB (UAM): 0.22 U | 🛡️ Cap: 0.22 → 0.15", 0.15, null, null, 160.0)
        assertThat(why.limited).isTrue()
        assertThat(why.limitWantedU).isWithin(1e-9).of(0.22)
        assertThat(why.limitGivenU).isWithin(1e-9).of(0.15)
    }

    @Test
    fun `MaxSMB named in the reason alone is not a limit`() {
        val why = RingWhyMapper.map(" | MaxSMB 1.0 U \n Microbolusing 0.10U.", 0.1, null, null, 140.0)
        assertThat(why.limited).isFalse()
        assertThat(why.limitWantedU).isNull()
    }
}
