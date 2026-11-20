package app.aaps.plugins.sibionics

import app.aaps.core.data.iob.InMemoryGlucoseValue
import app.aaps.core.data.model.CAL
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.interfaces.aps.GlucoseStatus
import app.aaps.core.interfaces.calibration.AddEntryResult
import app.aaps.core.interfaces.calibration.CalibrationContext
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.iob.GlucoseStatusProvider
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * The finger-stick side of the calibration inside the source: when a stick is taken, and that it is paired with the
 * source's Stock value (the raw of a native Sibionics reading), never with the calibrated value.
 */
class SibionicsCalibrationPluginTest {

    private val now = 1_800_000_000_000L
    private val hour = 3_600_000L
    private lateinit var persistence: PersistenceLayer
    private lateinit var status: GlucoseStatusProvider
    private lateinit var source: SibionicsPlugin
    private lateinit var plugin: SibionicsCalibrationPlugin

    @BeforeEach
    fun setUp() {
        persistence = mock()
        status = mock()
        source = mock()
        val dateUtil: DateUtil = mock()
        whenever(dateUtil.now()).thenReturn(now)
        plugin = SibionicsCalibrationPlugin(mock<AAPSLogger>(), mock<ResourceHelper>(), mock<NotificationManager>(), persistence, status, dateUtil, source)
    }

    private fun gv(time: Long, value: Double, raw: Double?, sensor: SourceSensor = SourceSensor.SIBIONICS_NATIVE) =
        GV(timestamp = time, value = value, raw = raw, noise = null, trendArrow = TrendArrow.NONE, sourceSensor = sensor)

    private fun steady(delta: Double = 1.0) {
        val gs: GlucoseStatus = mock()
        whenever(gs.shortAvgDelta).thenReturn(delta)
        whenever(status.glucoseStatusData).thenReturn(gs)
    }

    @Test
    fun `no sensor session, no stick`() = runTest {
        whenever(source.sessionStart()).thenReturn(0L)
        assertThat(plugin.addEntry(100.0, now)).isEqualTo(AddEntryResult.Rejected.NoSession)
        verify(persistence, never()).insertOrUpdateCalibrationEntry(any())
    }

    @Test
    fun `no stick in the first two hours of a sensor`() = runTest {
        whenever(source.sessionStart()).thenReturn(now - hour)
        assertThat(plugin.addEntry(100.0, now)).isInstanceOf(AddEntryResult.Rejected.InWarmUp::class.java)
    }

    @Test
    fun `no stick while glucose moves fast`() = runTest {
        whenever(source.sessionStart()).thenReturn(now - 10 * hour)
        steady(delta = 9.0)
        assertThat(plugin.addEntry(100.0, now)).isInstanceOf(AddEntryResult.Rejected.DeltaTooHigh::class.java)
    }

    @Test
    fun `the stick is paired with the Stock value, not with the calibrated one`() = runTest {
        whenever(source.sessionStart()).thenReturn(now - 10 * hour)
        steady()
        // AAPS shows 120 (already calibrated), the Stock value of that minute was 108.
        whenever(persistence.getBgReadingsDataFromTimeToTime(any(), any(), any())).thenReturn(listOf(gv(now - 60_000L, value = 120.0, raw = 108.0)))
        assertThat(plugin.addEntry(130.0, now)).isEqualTo(AddEntryResult.Accepted)
        val cal = argumentCaptor<CAL>()
        verify(persistence).insertOrUpdateCalibrationEntry(cal.capture())
        assertThat(cal.firstValue.fingerstickMgdl).isEqualTo(130.0)
        assertThat(cal.firstValue.sensorMgdlAtPairing).isEqualTo(108.0)
        assertThat(cal.firstValue.timestamp).isEqualTo(now)
    }

    @Test
    fun `a value of another source is not a pair`() = runTest {
        whenever(source.sessionStart()).thenReturn(now - 10 * hour)
        steady()
        whenever(persistence.getBgReadingsDataFromTimeToTime(any(), any(), any())).thenReturn(listOf(gv(now - 60_000L, 120.0, 118.0, SourceSensor.UNKNOWN)))
        assertThat(plugin.addEntry(130.0, now)).isEqualTo(AddEntryResult.Rejected.NoSensorPair)
        verify(persistence, never()).insertOrUpdateCalibrationEntry(any())
    }

    @Test
    fun `no value in the last 10 minutes, no stick`() = runTest {
        whenever(source.sessionStart()).thenReturn(now - 10 * hour)
        steady()
        whenever(persistence.getBgReadingsDataFromTimeToTime(any(), any(), any())).thenReturn(emptyList())
        assertThat(plugin.addEntry(130.0, now)).isEqualTo(AddEntryResult.Rejected.NoSensorPair)
    }

    @Test
    fun `AAPS calibration step changes nothing, so a stick is never applied twice`() = runTest {
        val data = mutableListOf(InMemoryGlucoseValue(timestamp = now, value = 120.0, sourceSensor = SourceSensor.SIBIONICS_NATIVE))
        val out = plugin.calibrate(data, CalibrationContext.NONE)
        assertThat(out[0].calibrated).isNull()
        assertThat(out[0].value).isEqualTo(120.0)
    }
}
