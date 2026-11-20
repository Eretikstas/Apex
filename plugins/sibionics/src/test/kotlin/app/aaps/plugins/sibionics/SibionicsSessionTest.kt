package app.aaps.plugins.sibionics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tk.glucodata.drivers.sibionics.SibionicsCalibrationAnchor
import tk.glucodata.drivers.sibionics.SibionicsProtocol
import tk.glucodata.drivers.sibionics.SibionicsSourceSample

/** The Sibionics 2 conversation against a fake sensor: frames are built and encrypted as the sensor does. */
class SibionicsSessionTest {

    private val now = 1_800_000_000_000L
    private val identity = SibionicsIdentityParser.parse("\u001D0106972831641476112507301726072910LT46250683C\u001D21P2250683013AQT98")!!

    private class FakeStore : SibionicsSessionStore {

        override var sessionStart = 0L
        override var lastSeen = 0L
        val samples = sortedMapOf<Int, SibionicsSourceSample>()
        override fun journal() = samples.values.toList()
        override fun appendJournal(samples: List<SibionicsSourceSample>) = samples.forEach { this.samples[it.index] = it }
        override fun clearJournal() = samples.clear()
    }

    private class FakeIo : SibionicsSessionIo {

        val writes = mutableListOf<String>()
        val reconnects = mutableListOf<String>()
        override fun write(bytes: ByteArray, label: String): Boolean = writes.add(label)
        override fun schedule(timer: SibionicsSession.Timer, delayMs: Long) = Unit
        override fun cancel(timer: SibionicsSession.Timer) = Unit
        override fun reconnect(reason: String, delayMs: Long) {
            reconnects += reason
        }

        override fun log(message: String) = Unit
    }

    private class FakeListener : SibionicsSessionListener {

        val readings = mutableListOf<SibionicsReading>()
        val starts = mutableListOf<Long>()
        override fun onReadings(readings: List<SibionicsReading>, sensorStart: Long?) {
            this.readings += readings
            sensorStart?.let { starts += it }
        }

        override fun onState(state: SibionicsSession.State) = Unit
    }

    private fun handshake(code: Int): ByteArray {
        val p = byteArrayOf(4, code.toByte(), 0, 0, 0)
        p[4] = SibionicsProtocol.checksum(p, 4)
        return SibionicsProtocol.encrypt(p)
    }

    /** A data page of [rawTenths] (glucose in 0.1 mmol/L) starting at [baseIndex], first minute at [firstMs]. */
    private fun page(baseIndex: Int, firstMs: Long, rawTenths: List<Int>): ByteArray {
        val size = 9 + rawTenths.size * 8 + 1
        val p = ByteArray(size)
        p[0] = (size - 1).toByte()
        p[1] = 0x08
        p[2] = rawTenths.size.toByte()
        p[3] = (baseIndex and 0xFF).toByte()
        p[4] = (baseIndex shr 8 and 0xFF).toByte()
        val seconds = firstMs / 1000L
        for (i in 0..3) p[5 + i] = (seconds shr (8 * i) and 0xFF).toByte()
        rawTenths.forEachIndexed { n, raw ->
            val o = 9 + n * 8
            p[o] = (350 and 0xFF).toByte()
            p[o + 1] = (350 shr 8).toByte()
            p[o + 2] = 100
            p[o + 4] = (raw and 0xFF).toByte()
            p[o + 5] = (raw shr 8 and 0xFF).toByte()
        }
        p[size - 1] = SibionicsProtocol.checksum(p, size - 1)
        return SibionicsProtocol.encrypt(p)
    }

    private fun session(
        store: FakeStore, io: FakeIo, listener: FakeListener,
        anchors: List<SibionicsCalibrationAnchor> = emptyList()
    ) = SibionicsSession(identity, "AA:BB:CC:DD:EE:FF", store, io, listener, anchors = { anchors }, clock = { now })

    /** Handshake up to the data request, then one page ending with the live minute. */
    private fun runFirstLink(s: SibionicsSession, io: FakeIo, count: Int = 6) {
        s.onReady()
        assertTrue(io.writes.last().startsWith("auth"))
        s.onNotification(handshake(0x01))
        assertEquals("activation", io.writes.last())
        s.onNotification(handshake(0x07))
        assertEquals("time sync", io.writes.last())
        s.onNotification(handshake(0x03))
        assertEquals("data request from 0", io.writes.last())
        s.onNotification(page(0, now - (count - 1) * 60_000L, List(count) { 60 + it }))
    }

    @Test
    fun `first link reads the session from its start and reports the sensor start once`() {
        val store = FakeStore()
        val io = FakeIo()
        val listener = FakeListener()
        val s = session(store, io, listener)
        runFirstLink(s, io)

        assertEquals(6, listener.readings.size)
        assertTrue(listener.readings.last().live)
        assertEquals(listOf(now - 5 * 60_000L), listener.starts)
        assertEquals(6, store.samples.size)
        assertEquals(6, s.state().nextIndex)
        assertEquals(SibionicsSession.Phase.STREAMING, s.phaseNow)
        listener.readings.forEach { assertEquals(it.stockMgdl, it.glucoseMgdl, 0.0001) }
    }

    @Test
    fun `after an app restart the journal gives the same cursor and the next request goes on from it`() {
        val store = FakeStore()
        FakeIo().let { firstIo -> runFirstLink(session(store, firstIo, FakeListener()), firstIo) }
        val io = FakeIo()
        val listener = FakeListener()
        val restarted = session(store, io, listener)

        assertEquals(6, restarted.state().nextIndex)
        assertNotNull(restarted.state().lastGlucoseMgdl)
        restarted.onReady()
        restarted.onNotification(handshake(0x01))
        restarted.onNotification(handshake(0x03))
        assertEquals("data request from 6", io.writes.last())
        // A repeated page is not reported again; no new sensor start either.
        restarted.onNotification(page(0, now - 5 * 60_000L, List(6) { 60 + it }))
        assertTrue(listener.readings.isEmpty())
        assertTrue(listener.starts.isEmpty())
    }

    @Test
    fun `a page after a hole is dropped and asked for again`() {
        val store = FakeStore()
        val io = FakeIo()
        val listener = FakeListener()
        val s = session(store, io, listener)
        runFirstLink(s, io)
        listener.readings.clear()

        s.onNotification(page(10, now - 60_000L, listOf(70, 71)))

        assertTrue(listener.readings.isEmpty())
        assertEquals(6, s.state().nextIndex)
        assertTrue(io.reconnects.any { it.contains("idx=6") })
    }

    @Test
    fun `a journal with a hole is dropped at start and the session is read again`() {
        val store = FakeStore()
        FakeIo().let { firstIo -> runFirstLink(session(store, firstIo, FakeListener()), firstIo) }
        store.samples.remove(3)

        val s = session(store, FakeIo(), FakeListener())

        assertEquals(0, s.state().nextIndex)
        assertTrue(store.samples.isEmpty())
    }

    @Test
    fun `a finger stick moves the value but not the stock value`() {
        val plain = FakeListener()
        FakeIo().let { firstIo -> runFirstLink(session(FakeStore(), firstIo, plain), firstIo) }
        val calibrated = FakeListener()
        val anchor = SibionicsCalibrationAnchor(sensorMmol = 6.0f, referenceMmol = 7.0f, timestampMs = now - 2 * 60_000L)
        val io = FakeIo()
        runFirstLink(session(FakeStore(), io, calibrated, listOf(anchor)), io)

        val before = plain.readings.last()
        val after = calibrated.readings.last()
        assertEquals(before.stockMgdl, after.stockMgdl, 0.0001)
        assertTrue(after.glucoseMgdl > after.stockMgdl, "offset +1 mmol/L must raise the value")
        assertTrue(after.glucoseMgdl - after.stockMgdl <= 18.0 + 0.0001, "never more than the finger-stick difference")
    }

    @Test
    fun `a new sensor session clears the old state and reports the new start`() {
        val store = FakeStore()
        val io = FakeIo()
        val listener = FakeListener()
        val s = session(store, io, listener)
        runFirstLink(s, io)
        listener.starts.clear()

        // A restarted sensor: index 0 again, live.
        s.onNotification(page(0, now, listOf(80)))

        assertEquals(listOf(now), listener.starts)
        assertEquals(1, s.state().nextIndex)
        assertEquals(1, store.samples.size)
    }

    @Test
    fun `no reading before the auth is accepted`() {
        val listener = FakeListener()
        val io = FakeIo()
        val s = session(FakeStore(), io, listener)
        s.onReady()
        assertNull(s.state().lastGlucoseMgdl)
        assertTrue(listener.readings.isEmpty())
    }
}
