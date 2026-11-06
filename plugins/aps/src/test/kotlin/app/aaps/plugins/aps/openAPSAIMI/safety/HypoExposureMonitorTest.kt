package app.aaps.plugins.aps.openAPSAIMI.safety

import app.aaps.core.data.model.GV
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.interfaces.Preferences
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

/**
 * The guard as the app runs it: glucose read from the database, the level found, and what each
 * mode lets through to the doses.
 */
class HypoExposureMonitorTest {

    private val now = 1_800_000_000_000L
    private val day = 24L * 60 * 60 * 1000

    /**
     * 14 days of 5-minute readings: 11 in 100 at 60 mg/dL and 1 in 100 at 50 mg/dL (the real case
     * of 2026-11), the rest at 140.
     */
    fun history(lowEvery: Int = 9): List<GV> = (0 until 14 * 288).map { i ->
        val value = when {
            i % 100 == 0 -> 50.0
            i % lowEvery == 0 -> 60.0
            else -> 140.0
        }
        GV(
            timestamp = now - 14 * day + i * 5 * 60_000L,
            raw = null,
            value = value,
            trendArrow = TrendArrow.NONE,
            noise = null,
            sourceSensor = SourceSensor.UNKNOWN,
        )
    }

    private val persistenceLayer = mockk<PersistenceLayer>()
    private var clock = now
    private val dateUtil = mockk<DateUtil> { every { now() } answers { clock } }
    private var mode = 2
    private val preferences = mockk<Preferences> { every { get(IntKey.OApsAIMIHypoExposureGuardMode) } answers { mode } }

    private fun monitor() = HypoExposureMonitor(persistenceLayer, dateUtil, preferences, mockk<AAPSLogger>(relaxed = true))

    @Test
    fun `a history with many lows is hypo-prone and, when on, asks for 0_85`() = runBlocking {
        coEvery { persistenceLayer.getBgReadingsDataFromTimeToTime(any(), any(), any()) } returns history()
        val m = monitor()
        m.refreshIfDue()

        assertThat(m.verdict.level).isEqualTo(HypoExposureGuard.Level.HYPO_PRONE)
        assertThat(m.verdict.exposure!!.below70Pct).isGreaterThan(6.0)
        assertThat(m.applied.scale).isEqualTo(0.85)
        assertThat(m.applied.stopExtraBoosts).isTrue()
        assertThat(m.applied.learnedOnlyGentler).isTrue()
    }

    @Test
    fun `watch only finds the same level but changes no dose`() = runBlocking {
        coEvery { persistenceLayer.getBgReadingsDataFromTimeToTime(any(), any(), any()) } returns history()
        mode = 1
        val m = monitor()
        m.refreshIfDue()

        assertThat(m.verdict.level).isEqualTo(HypoExposureGuard.Level.HYPO_PRONE)
        assertThat(m.applied.asksForLess).isFalse()
        assertThat(m.applied.scale).isEqualTo(1.0)
    }

    @Test
    fun `off reads nothing and asks for nothing`() = runBlocking {
        mode = 0
        val m = monitor()
        m.refreshIfDue()

        coVerify(exactly = 0) { persistenceLayer.getBgReadingsDataFromTimeToTime(any(), any(), any()) }
        assertThat(m.applied.asksForLess).isFalse()
    }

    @Test
    fun `a good history asks for nothing`() = runBlocking {
        // 1 in 50 below 70: 2 %, under the 4 % target.
        coEvery { persistenceLayer.getBgReadingsDataFromTimeToTime(any(), any(), any()) } returns
            history(lowEvery = 50).map { if (it.value == 50.0) it.copy(value = 140.0) else it }
        val m = monitor()
        m.refreshIfDue()

        assertThat(m.verdict.level).isEqualTo(HypoExposureGuard.Level.NORMAL)
        assertThat(m.applied.asksForLess).isFalse()
    }

    @Test
    fun `the history is read at most once an hour`() = runBlocking {
        coEvery { persistenceLayer.getBgReadingsDataFromTimeToTime(any(), any(), any()) } returns history()
        val m = monitor()
        m.refreshIfDue()
        clock = now + 30 * 60_000L
        m.refreshIfDue()
        coVerify(exactly = 1) { persistenceLayer.getBgReadingsDataFromTimeToTime(any(), any(), any()) }

        clock = now + 61 * 60_000L
        m.refreshIfDue()
        coVerify(exactly = 2) { persistenceLayer.getBgReadingsDataFromTimeToTime(any(), any(), any()) }
    }

    @Test
    fun `a failed read leaves the guard neutral`() = runBlocking {
        coEvery { persistenceLayer.getBgReadingsDataFromTimeToTime(any(), any(), any()) } throws IllegalStateException("db")
        val m = monitor()
        m.refreshIfDue()

        assertThat(m.verdict).isEqualTo(HypoExposureGuard.Verdict.NEUTRAL)
        assertThat(m.applied.asksForLess).isFalse()
    }

    @Test
    fun `only 3 days of data is not enough`() = runBlocking {
        coEvery { persistenceLayer.getBgReadingsDataFromTimeToTime(any(), any(), any()) } returns history().takeLast(3 * 288)
        val m = monitor()
        m.refreshIfDue()

        assertThat(m.verdict.level).isEqualTo(HypoExposureGuard.Level.NOT_ENOUGH_DATA)
        assertThat(m.applied.asksForLess).isFalse()
    }
}
