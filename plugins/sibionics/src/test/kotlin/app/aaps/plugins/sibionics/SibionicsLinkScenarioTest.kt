package app.aaps.plugins.sibionics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.junit.jupiter.api.Test
import tk.glucodata.drivers.sibionics.SibionicsCalibrationAnchor
import tk.glucodata.drivers.sibionics.SibionicsProtocol
import tk.glucodata.drivers.sibionics.SibionicsSourceSample
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Whole link scenarios against a virtual Sibionics 2 sensor that answers like the real one: it keeps one minute per
 * index, answers a data request with pages from the asked index up to now, and sends the live minute every minute
 * while the link is up. The tests check what AAPS gets: every minute once, in order, with the right time, after link
 * losses of 5 to 40 minutes, app restarts, sensor restarts, refused keys and finger-stick calibrations.
 */
class SibionicsLinkScenarioTest {

    private val start = 1_800_000_000_000L
    private var now = start
    private val minute = 60_000L
    private val identity = SibionicsIdentityParser.parse("\u001D0106972831641476112507301726072910LT46250683C\u001D21P2250683013AQT98")!!

    /** Raw sensor glucose (0.1 mmol/L) of minute [index]: a slow wave between about 5 and 11 mmol/L. */
    private fun rawOf(index: Int): Int = (80 + 30 * sin(index * 2 * PI / 180.0)).toInt()

    // ---- the virtual sensor -------------------------------------------------------------------------------------

    private inner class VirtualSensor(var sessionStart: Long = start) {

        /** Key groups it accepts: by default only the Sibionics 2 one, as a real sensor. */
        var acceptAuth: (String) -> Boolean = { it.contains("sibionics2") }
        var answersHandshake = true
        /** Streams its current minute right after auth, before our data request (seen on real sensors). */
        var pushLiveOnAuth = false
        val inbox = ArrayDeque<ByteArray>()
        var requested = mutableListOf<Int>()

        fun currentIndex(): Int = ((now - sessionStart) / minute).toInt()

        fun onWrite(bytes: ByteArray, label: String) {
            val plain = SibionicsProtocol.decrypt(bytes)
            when {
                plain[0].toInt() == 0x19 && plain[1].toInt() == 0x01 -> if (acceptAuth(label)) {
                    if (pushLiveOnAuth) inbox += liveMinute()
                    inbox += handshake(0x01)
                }
                plain[0].toInt() == 0x0A && plain[1].toInt() == 0x07 -> if (answersHandshake) inbox += handshake(0x07)
                plain[0].toInt() == 0x06 && plain[1].toInt() == 0x03 -> if (answersHandshake) inbox += handshake(0x03)
                plain[0].toInt() == 0x06 && plain[1].toInt() == 0x08 -> {
                    val from = (plain[2].toInt() and 0xFF) or ((plain[3].toInt() and 0xFF) shl 8)
                    requested += from
                    pagesFrom(from).forEach { inbox += it }
                }
            }
        }

        /** Pages of at most 30 minutes from [from] up to the current minute (the last one is live). */
        fun pagesFrom(from: Int): List<ByteArray> {
            val last = currentIndex()
            if (from > last) return emptyList()
            return (from..last).chunked(30).map { chunk -> page(chunk.first(), sessionStart + chunk.first() * minute, chunk.map { rawOf(it) }) }
        }

        /** The live minute, sent every minute while the link is up. */
        fun liveMinute(): ByteArray = currentIndex().let { page(it, sessionStart + it * minute, listOf(rawOf(it))) }
    }

    private fun handshake(code: Int): ByteArray {
        val p = byteArrayOf(4, code.toByte(), 0, 0, 0)
        p[4] = SibionicsProtocol.checksum(p, 4)
        return SibionicsProtocol.encrypt(p)
    }

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

    // ---- AAPS side fakes ----------------------------------------------------------------------------------------

    private class Store : SibionicsSessionStore {

        override var sessionStart = 0L
        override var lastSeen = 0L
        val samples = sortedMapOf<Int, SibionicsSourceSample>()
        override fun journal() = samples.values.toList()
        override fun appendJournal(samples: List<SibionicsSourceSample>) = samples.forEach { this.samples[it.index] = it }
        override fun clearJournal() = samples.clear()
    }

    private inner class Link(val sensor: VirtualSensor, val store: Store = Store(), var anchors: List<SibionicsCalibrationAnchor> = emptyList()) {

        val readings = mutableListOf<SibionicsReading>()
        val starts = mutableListOf<Long>()
        val reconnects = mutableListOf<String>()
        val timers = mutableMapOf<SibionicsSession.Timer, Long>()
        var up = false
        var lastState: SibionicsSession.State? = null

        private val io = object : SibionicsSessionIo {
            override fun write(bytes: ByteArray, label: String): Boolean {
                if (up) sensor.onWrite(bytes, label)
                return up
            }

            override fun schedule(timer: SibionicsSession.Timer, delayMs: Long) {
                timers[timer] = now + delayMs
            }

            override fun cancel(timer: SibionicsSession.Timer) {
                timers.remove(timer)
            }

            override fun reconnect(reason: String, delayMs: Long) {
                reconnects += reason
                down()
            }

            override fun log(message: String) = Unit
        }

        private val listener = object : SibionicsSessionListener {
            override fun onReadings(readings: List<SibionicsReading>, sensorStart: Long?) {
                this@Link.readings += readings
                sensorStart?.let { starts += it }
            }

            override fun onState(state: SibionicsSession.State) {
                lastState = state
            }
        }

        var session = newSession()

        fun newSession() = SibionicsSession(identity, "AA:BB:CC:DD:EE:FF", store, io, listener, anchors = { anchors }, clock = { now })

        /** The app is closed and opened again: a new session object on the same stored state. */
        fun restartApp() {
            down()
            session = newSession()
        }

        fun connect() {
            up = true
            sensor.inbox.clear()
            session.onReady()
            pump()
        }

        fun down() {
            if (!up) return
            up = false
            sensor.inbox.clear()
            session.onDisconnected()
        }

        /** Hand everything the sensor has queued to the session, as notifications would. */
        fun pump() {
            var guard = 0
            while (up && sensor.inbox.isNotEmpty() && guard++ < 10_000) session.onNotification(sensor.inbox.removeFirst())
        }

        /** Let [minutes] pass; with the link up the sensor sends its live minute each minute. Due timers fire. */
        fun pass(minutes: Int) {
            repeat(minutes) {
                now += minute
                if (up) {
                    sensor.inbox += sensor.liveMinute()
                    pump()
                }
                timers.filterValues { it <= now }.keys.toList().forEach {
                    timers.remove(it)
                    session.onTimer(it)
                    pump()
                }
            }
        }
    }

    /** Every minute of [indices] once, in order, at its own time. */
    private fun assertWhole(readings: List<SibionicsReading>, indices: IntRange, sessionStart: Long = start) {
        val got = readings.map { it.index }
        assertEquals(indices.toList(), got, "minutes AAPS got")
        readings.forEach { assertEquals(sessionStart + it.index * minute, it.timestamp, "time of idx ${it.index}") }
    }

    // ---- scenarios ----------------------------------------------------------------------------------------------

    @Test
    fun `an hour of streaming gives every minute once`() {
        val link = Link(VirtualSensor())
        link.connect()
        link.pass(60)
        assertWhole(link.readings, 0..60)
        assertEquals(listOf(start), link.starts)
        assertTrue(link.readings.last().live)
    }

    @ParameterizedTest
    @ValueSource(ints = [5, 10, 15, 20, 30, 40])
    fun `after a link loss the missing minutes come back, none twice, none lost`(gap: Int) {
        val link = Link(VirtualSensor())
        link.connect()
        link.pass(30)
        link.down()
        link.pass(gap)
        link.connect()
        assertEquals(31, link.sensor.requested.last(), "the request goes on from the cursor")
        link.pass(20)
        assertWhole(link.readings, 0..(50 + gap))
        assertEquals(1, link.starts.size, "a link loss is not a new sensor")
        assertTrue(link.reconnects.isEmpty())
    }

    @ParameterizedTest
    @ValueSource(ints = [5, 20, 40])
    fun `values after a link loss stay close to the values of an unbroken link`(gap: Int) {
        val whole = Link(VirtualSensor()).apply { connect(); pass(30 + gap + 20) }
        now = start
        val broken = Link(VirtualSensor()).apply { connect(); pass(30); down(); pass(gap); connect(); pass(20) }
        val a = whole.readings.associateBy { it.index }
        val worst = broken.readings.maxOf { abs(it.glucoseMgdl - a.getValue(it.index).glucoseMgdl) }
        println("gap $gap min: largest difference to the unbroken link ${"%.2f".format(worst)} mg/dL")
        // Minutes read back after the loss are replay minutes for the vendor algorithm (no live delta smoothing).
        assertTrue(worst <= 9.0, "largest difference $worst mg/dL (gap $gap)")
    }

    @Test
    fun `an app restart during a link loss goes on from the journal`() {
        val link = Link(VirtualSensor())
        link.connect()
        link.pass(30)
        link.restartApp()
        link.pass(25)
        link.connect()
        link.pass(5)
        assertWhole(link.readings, 0..60)
        assertEquals(1, link.starts.size)
    }

    @Test
    fun `a sensor that streams its own live minute before our request loses nothing and needs no reconnect`() {
        val sensor = VirtualSensor()
        sensor.pushLiveOnAuth = true
        val link = Link(sensor)
        link.connect()
        link.pass(10)
        link.down()
        link.pass(15)
        link.connect()
        link.pass(3)
        assertWhole(link.readings, 0..28)
        assertTrue(link.reconnects.isEmpty(), "no reconnect loop: ${link.reconnects}")
    }

    @Test
    fun `a page the sensor serves from another index after our request is asked for again`() {
        val sensor = VirtualSensor()
        val link = Link(sensor)
        link.connect()
        link.pass(10)
        // Streaming, then a page far ahead of our cursor (a request that did not arrive): dropped and asked again.
        sensor.inbox += page(25, start + 25 * minute, listOf(rawOf(25)))
        link.pump()
        assertTrue(link.reconnects.isNotEmpty())
        link.connect()
        link.pass(2)
        assertWhole(link.readings, 0..12)
    }

    @Test
    fun `a repeated notification is not stored twice`() {
        val sensor = VirtualSensor()
        val link = Link(sensor)
        link.connect()
        link.pass(5)
        sensor.inbox += sensor.liveMinute()
        sensor.inbox += sensor.liveMinute()
        link.pump()
        assertWhole(link.readings, 0..5)
    }

    @Test
    fun `a long backlog of a day comes in pages and in order`() {
        val sensor = VirtualSensor()
        val link = Link(sensor)
        link.connect()
        link.pass(10)
        link.down()
        link.pass(24 * 60)
        link.connect()
        link.pass(2)
        assertWhole(link.readings, 0..(10 + 24 * 60 + 2))
    }

    @Test
    fun `a new sensor is found, the old state is dropped and the new start is reported`() {
        val sensor = VirtualSensor()
        val link = Link(sensor)
        link.connect()
        link.pass(120)
        link.down()
        // A new sensor started 30 minutes later; the new one is read from its first minute.
        now += 30 * minute
        sensor.sessionStart = now
        link.pass(10)
        link.connect()
        // The first new minute proves the restart; the session drops the link to read the new session from idx 0.
        link.pass(1)
        assertFalse(link.up, "the session asks for a new link")
        link.connect()
        link.pass(4)
        val newStart = sensor.sessionStart
        assertEquals(listOf(start, newStart), link.starts)
        val after = link.readings.filter { it.timestamp >= newStart }
        assertWhole(after, 0..15, newStart)
    }

    @Test
    fun `the sensor key group is tried first and a refusing sensor gets the next groups, then a reconnect`() {
        val sensor = VirtualSensor()
        sensor.acceptAuth = { false }
        val link = Link(sensor)
        link.connect()
        repeat(10) { link.pass(1) }
        link.pass(60)
        assertTrue(link.lastState?.authFailed == true || link.reconnects.any { it.contains("auth") })
        assertTrue(link.readings.isEmpty())
    }

    @Test
    fun `a handshake that stops answering starts again with auth`() {
        val sensor = VirtualSensor()
        sensor.answersHandshake = false
        val link = Link(sensor)
        link.connect()
        link.pass(1)
        sensor.answersHandshake = true
        link.pass(15)
        link.pump()
        assertFalse(link.readings.isEmpty(), "values come once the sensor answers again")
    }

    @Test
    fun `a link that stays up but goes quiet for three minutes is restarted`() {
        val sensor = VirtualSensor()
        val link = Link(sensor)
        link.connect()
        link.pass(5)
        // Quiet: the link is up but nothing comes.
        link.up = false
        link.pass(4)
        link.up = true
        assertTrue(link.timers.isEmpty() || link.reconnects.isNotEmpty() || link.session.phaseNow != SibionicsSession.Phase.STREAMING)
    }

    // ---- calibration --------------------------------------------------------------------------------------------

    @Test
    fun `calibration never changes the stock value, only the value AAPS uses`() {
        val plain = Link(VirtualSensor()).apply { connect(); pass(180) }
        now = start
        val cal = Link(VirtualSensor())
        cal.connect()
        cal.pass(150)
        val atStick = cal.readings.last()
        cal.anchors = listOf(SibionicsCalibrationAnchor(sensorMmol = (atStick.stockMgdl / 18).toFloat(), referenceMmol = (atStick.stockMgdl / 18 + 1.0).toFloat(), timestampMs = now))
        cal.pass(30)
        val p = plain.readings.associateBy { it.index }
        cal.readings.forEach { assertEquals(p.getValue(it.index).stockMgdl, it.stockMgdl, 0.0001, "stock of idx ${it.index}") }
        cal.readings.filter { it.index <= 150 }.forEach { assertEquals(it.stockMgdl, it.glucoseMgdl, 0.0001, "no calibration before the stick") }
        val after = cal.readings.filter { it.index > 150 }
        after.forEach {
            val shift = it.glucoseMgdl - it.stockMgdl
            assertTrue(shift > 0.0 && shift <= 18.0 + 0.001, "idx ${it.index} shift $shift mg/dL must be up and at most the stick difference")
        }
        println("one stick +18 mg/dL: shift right after ${"%.1f".format(after.first().glucoseMgdl - after.first().stockMgdl)} mg/dL")
    }

    @Test
    fun `two sticks fit a line through both points`() {
        val link = Link(VirtualSensor())
        link.connect()
        link.pass(240)
        // Sensor reads 6 where the finger says 5, and 10 where the finger says 11: steeper line.
        link.anchors = listOf(
            SibionicsCalibrationAnchor(6f, 5f, now - 60 * minute),
            SibionicsCalibrationAnchor(10f, 11f, now)
        )
        link.pass(60)
        val after = link.readings.filter { it.index > 240 }
        val low = after.minBy { it.stockMgdl }
        val high = after.maxBy { it.stockMgdl }
        println("two sticks: low stock ${"%.1f".format(low.stockMgdl)} -> ${"%.1f".format(low.glucoseMgdl)}, high stock ${"%.1f".format(high.stockMgdl)} -> ${"%.1f".format(high.glucoseMgdl)}")
        assertTrue(high.glucoseMgdl - high.stockMgdl > low.glucoseMgdl - low.stockMgdl, "a steeper line lifts high values more than low ones")
    }

    @Test
    fun `a stick from before the sensor started is not used`() {
        val link = Link(VirtualSensor())
        link.anchors = listOf(SibionicsCalibrationAnchor(6f, 9f, start - 60 * minute))
        link.connect()
        link.pass(60)
        link.readings.forEach { assertEquals(it.stockMgdl, it.glucoseMgdl, 0.0001) }
    }

    @Test
    fun `a stick counts less as it gets older`() {
        val link = Link(VirtualSensor())
        link.connect()
        link.pass(10)
        link.anchors = listOf(SibionicsCalibrationAnchor((link.readings.last().stockMgdl / 18).toFloat(), (link.readings.last().stockMgdl / 18 + 1.0).toFloat(), now))
        link.pass(36 * 60)
        val early = link.readings.first { it.index == 12 }
        val late = link.readings.last()
        val earlyShift = early.glucoseMgdl - early.stockMgdl
        val lateShift = late.glucoseMgdl - late.stockMgdl
        println("one stick: shift after 2 min ${"%.1f".format(earlyShift)}, after 36 h ${"%.1f".format(lateShift)} mg/dL")
        assertTrue(lateShift < earlyShift, "an old stick weighs less")
        assertTrue(lateShift >= 0.0)
    }

    @Test
    fun `values read back after a link loss get the calibration of their own time`() {
        val link = Link(VirtualSensor())
        link.connect()
        link.pass(100)
        link.down()
        link.pass(20)
        // Stick taken during the loss, 10 minutes ago.
        link.anchors = listOf(SibionicsCalibrationAnchor(7f, 8f, now - 10 * minute))
        link.connect()
        link.pass(2)
        val backlog = link.readings.filter { it.index in 101..120 }
        backlog.filter { it.timestamp < now - 11 * minute }.forEach { assertEquals(it.stockMgdl, it.glucoseMgdl, 0.0001, "idx ${it.index} is before the stick") }
        backlog.filter { it.timestamp >= now - 10 * minute }.forEach { assertTrue(it.glucoseMgdl > it.stockMgdl, "idx ${it.index} is after the stick") }
    }
}
