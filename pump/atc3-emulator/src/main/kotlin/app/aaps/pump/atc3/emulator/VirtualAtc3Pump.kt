package app.aaps.pump.atc3.emulator

import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.math.min

/**
 * A software TruCare III pump for tests: it takes the bytes the driver writes and answers with the
 * bytes a pump would send, built only from the protocol description
 * (https://github.com/russoturisto2026-create/apex-trucare-III-protocol, `PROTOCOL_EN.md`), not from
 * the driver's own code. So it checks the driver against the description independently.
 *
 * It is not the real firmware: what the description does not say (timings, an empty log, a wrong
 * password in detail) is kept simple, and is said where it is.
 *
 * Source of each behaviour: the § of the description named next to it (as read 2026-11-11). Nothing
 * here is measured on a real pump yet (spare ATC3, firmware 1.1.1, bench test pending). A behaviour
 * the description does not give is marked `UNVERIFIED`, with what is guessed, to be checked on the bench
 * (AIMI pump driver acceptance E2).
 *
 * Offsets in this file are the description's: data starts at byte 6 of a response frame.
 */
class VirtualAtc3Pump(
    val serial: String = "12345678",
    /** The phone's clock; the pump's clock is this plus [clockOffsetMs]. */
    private val phoneNow: () -> Long
) {

    /** Where the pump's notifications go: the driver's Bluetooth callback. */
    var sink: ((ByteArray) -> Unit)? = null

    private val timer: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "VirtualAtc3Pump").apply { isDaemon = true }
    }

    // State, all in the pump's own units (0.025 U, 0.001 U for the reservoir)

    var clockOffsetMs = 0L
    var reservoirMilliUnits = 200_000L
    var batteryCentiVolts = 145
    var activeProfile = 0
    val profiles = Array(8) { IntArray(48) { 40 } } // 1.0 U/h everywhere
    var suspended = false
    var stopHour = 0
    var stopMinute = 0
    var locked = false
    var deliveredTodayRaw = 0
    var firmware = intArrayOf(1, 1, 1)
    var protocol = intArrayOf(4, 12)
    var heartbeatMinutes = 3
    var activeAlarms = mutableListOf<Int>()

    /** The 16 byte settings block of `a1/32`, §5.1. */
    val settings = ByteArray(16).also {
        // Max basal 8.0 U/h, max bolus 10.0 U, daily limit 100 U.
        putU16(it, 12, 320)
        putU16(it, 14, 400)
        putU16(it, 10, 100)
    }

    var btPassword = "487613"

    class Bolus(val atMs: Long, var position: Int, val requested: Int, var delivered: Int)

    /** Newest first, §4.4. */
    val bolusLog = mutableListOf<Bolus>()

    class Tbr(val startMs: Long, val fromApp: Boolean, val absolute: Boolean, val durationSteps: Int, val value: Int, var delivered: Int)

    var tbr: Tbr? = null

    class FinishedTbr(val tbr: Tbr, val endMs: Long, val how: Int)

    var lastFinishedTbr: FinishedTbr? = null
    val tbrLog = mutableListOf<FinishedTbr>()

    class Refill(val atMs: Long, val raw: Int, val type: Int)

    val refillLog = mutableListOf<Refill>()

    class AlarmRecord(val atMs: Long, val code: Int)

    val alarmLog = mutableListOf<AlarmRecord>()

    // Fault injection

    /** Commands with these codes are not answered at all. */
    val silentFor = mutableSetOf<Int>()

    /** A running bolus stops being delivered after this many raw steps, and nothing more is sent. */
    var bolusStallsAt: Int? = null

    /** The pump starts a bolus but its answer to the command is lost on the way. */
    var bolusAckLost: Boolean = false

    /** The link drops after this many raw steps of a bolus. */
    var dropLinkAtBolusRaw: Int? = null

    /** A running bolus stops after this many raw steps with the no delivery alarm, as at an occlusion. Used once. */
    @Volatile var bolusOccludesAt: Int? = null

    /** One fault for the next frame that matches: class, code and payload size of the command. */
    class FrameFault(val cls: Int, val code: Int, val payloadSize: Int? = null, val alsoCodes: Set<Int> = emptySet()) {

        fun matches(cls: Int, code: Int, payloadSize: Int): Boolean =
            this.cls == cls && (this.code == code || code in alsoCodes) && (this.payloadSize == null || this.payloadSize == payloadSize)
    }

    /** The next matching command does not reach the pump: nothing is done, nothing is answered. Used once. */
    val loseCommands = mutableListOf<FrameFault>()

    /** The pump runs the next matching command, but its direct answer is lost. Used once. */
    val loseAnswers = mutableListOf<FrameFault>()

    /** The thread whose answers are dropped now, see [loseAnswers]. */
    @Volatile private var mutedThread: Thread? = null

    private fun takeFault(faults: MutableList<FrameFault>, cls: Int, code: Int, payloadSize: Int): Boolean = synchronized(faults) {
        val hit = faults.firstOrNull { it.matches(cls, code, payloadSize) }
        if (hit != null) faults.remove(hit)
        hit != null
    }

    /** The pump raises an alarm now: it is active in the status and written to the alarm log. */
    fun raiseAlarm(code: Int) {
        synchronized(activeAlarms) { if (code !in activeAlarms) activeAlarms.add(0, code) }
        synchronized(alarmLog) { alarmLog.add(0, AlarmRecord(pumpNow(), code)) }
    }

    /** Called when the pump drops the link. */
    var onLinkDropped: (() -> Unit)? = null

    /** Every command frame received, as `class/code` text, for checks. */
    val received = mutableListOf<String>()

    /** Milliseconds between two bolus progress frames, short here. UNVERIFIED: about 1 s per 0.05 U on a real pump. */
    var bolusTickMs = 20L

    @Volatile private var bolusRunning: BolusRun? = null

    private class BolusRun(val requested: Int, var delivered: Int, var cancelled: Boolean = false)

    fun pumpNow(): Long = phoneNow() + clockOffsetMs

    fun shutdown() {
        timer.shutdownNow()
    }

    val bolusInProgress: Boolean get() = bolusRunning != null

    // Receiving

    /** Take one frame written by the driver. @return true, as a write that went out */
    fun receive(frame: ByteArray): Boolean {
        if (frame.size < 20) return true
        val length = (frame[1].toInt() and 0xFF) or ((frame[2].toInt() and 0xFF) shl 8)
        if (length != frame.size) return true
        val crc = Crc16.modbusLeBytes(frame, frame.size - 2)
        if (crc[0] != frame[frame.size - 2] || crc[1] != frame[frame.size - 1]) return true
        // §2.1 says the marker is 0x35 for the official app; the driver also uses 0x55 for some
        // reads and the cancel, which it was bench tested with. Both are taken.
        val marker = frame[0].toInt() and 0xFF
        if (marker != 0x35 && marker != 0x55) return true
        val identity = String(frame, 6, 12, Charsets.US_ASCII)
        if (identity != "APEX$serial") return true
        val cls = frame[3].toInt() and 0xFF
        val code = frame[4].toInt() and 0xFF
        val index = frame[5].toInt() and 0xFF
        val payload = frame.copyOfRange(18, frame.size - 2)
        synchronized(received) { received.add("%02x/%02x".format(cls, code)) }
        if (code in silentFor) return true
        if (takeFault(loseCommands, cls, code, payload.size)) return true
        if (takeFault(loseAnswers, cls, code, payload.size)) mutedThread = Thread.currentThread()
        try {
            when (cls) {
                0xA3 -> read(code, index)
                0xA1 -> control(code, payload)
            }
        } finally {
            mutedThread = null
        }
        return true
    }

    // Reads, §4

    private fun read(code: Int, index: Int) {
        when (code) {
            0x00 -> send(response(0xA3, 0x00, 0xAA, 0x00, statusV1()))
            0x0C -> send(response(0xA3, 0x0C, 0xAA, 0x00, statusV2()))
            0x31 -> send(response(0xA3, 0x31, 0xAA, 0x00, version()))
            0x0A -> send(response(0xA3, 0x0A, 0xAA, 0x00, activeTbr()))
            0x0B -> send(response(0xA3, 0x0B, 0xAA, 0x00, finishedTbr()))
            0x08 -> for (p in 0 until 8) send(response(0xA3, 0x08, p, 0x08, profileBytes(p)))
            // The periodic bolus log: ten records, the field after the length is 0x80 (§2.2).
            0x21 -> sendBolusLog(0x21, 10, 0x80)
            0x01 -> sendBolusLog(0x01, 128, 0x80)
            0x27 -> sendRecords(0x27, 10) { i -> tbrLog.getOrNull(i)?.let(::tbrRecord) ?: ByteArray(14) }
            0x06, 0x26 -> sendRecords(code, 10) { i -> dailyRecord(i) }
            0x04 -> sendRecords(0x04, 11) { i -> refillLog.getOrNull(i)?.let(::refillRecord) ?: ByteArray(10) }
            0x03 -> sendRecords(0x03, 12) { i -> alarmLog.getOrNull(i)?.let(::alarmRecord) ?: ByteArray(8) }
        }
    }

    private fun sendBolusLog(code: Int, count: Int, field2: Int) {
        val snapshot = synchronized(bolusLog) { bolusLog.toList() }
        for (i in 0 until count) {
            val record = snapshot.getOrNull(i)?.let(::bolusRecord) ?: ByteArray(14)
            send(response(0xA3, code, i, field2, record))
        }
    }

    private fun sendRecords(code: Int, count: Int, record: (Int) -> ByteArray) {
        for (i in 0 until count) send(response(0xA3, code, i, count, record(i)))
    }

    /** §4.1, 88 bytes. */
    private fun statusV1(): ByteArray {
        val d = ByteArray(88)
        d[0] = 3 // battery bars
        d[1] = settings[1]
        d[2] = (settings[0].toInt() and 0x01).toByte()
        d[3] = settings[2]
        d[4] = (settings[6] * 2).toByte()
        d[5] = if (settings[0].toInt() and 0x02 != 0) 1 else 0
        d[6] = if (settings[0].toInt() and 0x04 != 0) 1 else 0
        d[7] = settings[3]
        d[8] = settings[4]
        d[9] = settings[5]
        d[10] = if (settings[0].toInt() and 0x08 != 0) 1 else 0
        d[11] = if (locked) 1 else 0
        d[12] = activeProfile.toByte()
        d[13] = if (settings[0].toInt() and 0x20 != 0) 1 else 0
        settings.copyInto(d, 14, 8, 10)
        putU16(d, 16, deliveredTodayRaw)
        settings.copyInto(d, 20, 10, 12)
        settings.copyInto(d, 24, 12, 14)
        settings.copyInto(d, 26, 14, 16)
        // Regular status carries the minute; seconds only at events (§4.13). Kept whole here.
        clock(pumpNow(), withSeconds = true).copyInto(d, 44)
        d[50] = if (settings[0].toInt() and 0x40 != 0) 1 else 0
        val running = tbr
        d[51] = if (running != null) 1 else 0
        putU32(d, 52, reservoirMilliUnits)
        activeAlarms.getOrNull(0)?.let { d[56] = it.toByte(); d[57] = 1 }
        activeAlarms.getOrNull(1)?.let { d[58] = it.toByte(); d[59] = 1 }
        putU16(d, 76, if (suspended) 0xFFFF else currentProfileRate())
        d[78] = stopHour.toByte()
        d[79] = stopMinute.toByte()
        if (running != null) {
            val rate = if (running.absolute) running.value else currentProfileRate() * running.value / 100
            putU16(d, 80, rate)
            putU16(d, 82, if (running.absolute) 1 else 0)
            putU16(d, 84, running.durationSteps * 15)
            putU16(d, 86, ((pumpNow() - running.startMs) / 60_000L).toInt())
        }
        return d
    }

    /** §4.2, 20 bytes; bytes 0..1 are not described, left zero. */
    private fun statusV2(): ByteArray = ByteArray(20).also { it[3] = batteryCentiVolts.toByte() }

    /** §4.11. */
    private fun version(): ByteArray =
        byteArrayOf(0, 0, 0, firmware[0].toByte(), firmware[1].toByte(), firmware[2].toByte(), protocol[0].toByte(), protocol[1].toByte())

    /** §4.7, 20 bytes. */
    private fun activeTbr(): ByteArray {
        val d = ByteArray(20)
        val running = tbr ?: lastFinishedTbr?.tbr ?: return d
        if (running.fromApp) clock(running.startMs, withSeconds = false).copyInto(d, 0)
        clock(running.startMs, withSeconds = false).copyInto(d, 7)
        d[13] = if (running.absolute) 1 else 0
        putU16(d, 14, running.durationSteps)
        putU16(d, 16, running.value)
        putU16(d, 18, if (tbr != null) running.delivered else 0)
        return d
    }

    /** §4.8, 26 bytes. */
    private fun finishedTbr(): ByteArray {
        val d = ByteArray(26)
        val done = lastFinishedTbr ?: return d
        d[0] = done.how.toByte()
        if (done.tbr.fromApp) clock(done.tbr.startMs, withSeconds = false).copyInto(d, 1)
        clock(done.tbr.startMs, withSeconds = false).copyInto(d, 7)
        d[13] = if (done.tbr.absolute) 1 else 0
        putU16(d, 14, done.tbr.durationSteps)
        putU16(d, 16, done.tbr.value)
        clock(done.endMs, withSeconds = true).copyInto(d, 18)
        putU16(d, 24, done.tbr.delivered)
        return d
    }

    private fun profileBytes(p: Int): ByteArray = ByteArray(96).also { d -> for (s in 0 until 48) putU16(d, s * 2, profiles[p][s]) }

    /** §4.4, 14 bytes. */
    private fun bolusRecord(b: Bolus): ByteArray {
        val d = ByteArray(14)
        clock(b.atMs, withSeconds = false).copyInto(d, 0, 0, 5)
        d[5] = b.position.toByte()
        putU16(d, 6, b.requested)
        putU16(d, 8, b.delivered)
        return d
    }

    /** §4.5, 14 bytes. */
    private fun tbrRecord(f: FinishedTbr): ByteArray {
        val d = ByteArray(14)
        clock(f.tbr.startMs, withSeconds = false).copyInto(d, 0)
        putU16(d, 6, if (f.tbr.absolute) 1 else 0)
        putU16(d, 8, f.tbr.durationSteps)
        putU16(d, 10, f.tbr.value)
        putU16(d, 12, f.tbr.delivered)
        return d
    }

    /** §4.10, 10 bytes: today first, the rest empty. */
    private fun dailyRecord(i: Int): ByteArray {
        val d = ByteArray(10)
        val day = pumpNow() - i * 86_400_000L
        val c = calendar(day)
        if (i == 0) putU16(d, 2, deliveredTodayRaw)
        d[6] = (c.get(Calendar.YEAR) - 2000).toByte()
        d[7] = (c.get(Calendar.MONTH) + 1).toByte()
        d[8] = c.get(Calendar.DAY_OF_MONTH).toByte()
        return d
    }

    /** §4.15, 10 bytes. */
    private fun refillRecord(r: Refill): ByteArray =
        ByteArray(10).also { clock(r.atMs, withSeconds = false).copyInto(it, 0); putU16(it, 6, r.raw); it[8] = r.type.toByte() }

    /** §4.16, 8 bytes. */
    private fun alarmRecord(a: AlarmRecord): ByteArray =
        ByteArray(8).also { clock(a.atMs, withSeconds = false).copyInto(it, 0); putU16(it, 6, a.code) }

    // Control, §5

    private fun control(code: Int, payload: ByteArray) {
        // §4.12: a locked pump refuses a command with a1/a5; the lock itself is set by a1/32.
        if (locked && code != 0x32) {
            send(response(0xA1, 0xA5, 0x00, 0x00, byteArrayOf(0, 0)))
            return
        }
        when (code) {
            0x00 -> {
                for (s in 0 until 48) profiles[activeProfile][s] = u16(payload, s * 2)
                ack()
            }

            0x02 -> if (payload.size == 2) {
                // `00 00`: cancel the running bolus (§5).
                bolusRunning?.cancelled = true
                ack()
            } else {
                startTbr(absolute = payload[0].toInt() == 1, durationSteps = payload[1].toInt() and 0xFF, value = u16(payload, 2))
                ack()
            }

            0x04 -> {
                activeProfile = payload[0].toInt()
                ack()
            }

            0x05 -> {
                endTbr(how = 3)
                ack()
            }

            0x12 -> {
                val dose = u16(payload, 0)
                // §4.12: the bolus start is acknowledged with an echo of its payload.
                if (!bolusAckLost) send(response(0xA1, 0x55, 0x00, 0x00, payload))
                startBolus(dose)
            }

            0x21 -> {
                if (payload[0].toInt() == 1) {
                    suspended = true
                    val c = calendar(pumpNow())
                    stopHour = c.get(Calendar.HOUR_OF_DAY)
                    stopMinute = c.get(Calendar.MINUTE)
                    bolusRunning?.cancelled = true
                } else suspended = false
                ack()
            }

            0x31 -> {
                clockOffsetMs = fromClock(payload, 0) - phoneNow()
                ack()
            }

            0x32 -> {
                payload.copyInto(settings, 0, 0, min(16, payload.size))
                locked = settings[0].toInt() and 0x10 != 0
                ack()
            }

            0x33 -> {
                heartbeatMinutes = payload[0].toInt()
                ack()
            }

            // UNVERIFIED: the six digits of the change command are taken as the new password as sent
            // (value + 65536, see Atc3BtPassword.candidatesFor); which one the real pump keeps is a bench check.
            0x35 -> {
                if (payload.size == 6 && payload.all { it in 0..9 }) btPassword = payload.joinToString("") { it.toString() }
                ack()
            }
            else -> ack()
        }
    }

    private fun ack() = send(response(0xA1, 0x55, 0x00, 0x00, byteArrayOf(0, 0)))

    private fun startTbr(absolute: Boolean, durationSteps: Int, value: Int) {
        if (tbr != null) endTbr(how = 3)
        tbr = Tbr(minuteOf(pumpNow()), fromApp = true, absolute = absolute, durationSteps = durationSteps, value = value, delivered = 0)
    }

    private fun endTbr(how: Int) {
        val running = tbr ?: return
        val done = FinishedTbr(running, pumpNow(), how)
        lastFinishedTbr = done
        tbrLog.add(0, done)
        tbr = null
    }

    private fun startBolus(dose: Int) {
        val run = BolusRun(requested = dose, delivered = 0)
        bolusRunning = run
        val step = 2
        timer.schedule(object : Runnable {
            override fun run() {
                if (run.cancelled) return finishBolus(run, completed = false)
                if (bolusStallsAt?.let { run.delivered >= it } == true) return
                if (bolusOccludesAt?.let { run.delivered >= it } == true) {
                    // UNVERIFIED: what the real pump sends at an occlusion is not in the description: here the
                    // bolus ends short, is logged, and the no delivery alarm (code 8) is active.
                    bolusOccludesAt = null
                    finishBolus(run, completed = false)
                    raiseAlarm(8)
                    return
                }
                // UNVERIFIED: here a lost link ends the bolus short. A real pump may go on and finish it
                // without the phone; the driver must count what the log says in both cases.
                if (dropLinkAtBolusRaw?.let { run.delivered >= it } == true) {
                    finishBolus(run, completed = false)
                    onLinkDropped?.invoke()
                    return
                }
                run.delivered = min(run.requested, run.delivered + step)
                reservoirMilliUnits -= step * 25L
                send(response(0xA1, 0xA0, 0x00, 0x00, u16Bytes(run.delivered)))
                if (run.delivered >= run.requested) {
                    finishBolus(run, completed = true)
                    send(response(0xA1, 0xAA, 0x00, 0x00, u16Bytes(run.delivered)))
                } else timer.schedule(this, bolusTickMs, TimeUnit.MILLISECONDS)
            }
        }, bolusTickMs, TimeUnit.MILLISECONDS)
    }

    /** §6: the record appears on completion or cancellation, not at the start. */
    @Suppress("UNUSED_PARAMETER")
    private fun finishBolus(run: BolusRun, completed: Boolean) {
        deliveredTodayRaw += run.delivered
        addBolusRecord(pumpNow(), run.requested, run.delivered)
        bolusRunning = null
    }

    /** A bolus given on the pump's own keypad: in the log, nothing sent over the air (§4.4). */
    fun bolusOnKeypad(units: Double, atMs: Long = pumpNow()) {
        val raw = Math.round(units / 0.025).toInt()
        deliveredTodayRaw += raw
        reservoirMilliUnits -= raw * 25L
        addBolusRecord(atMs, raw, raw)
    }

    /** The user stops or starts the insulin on the pump's own keypad. UNVERIFIED: nothing is sent over the air; a running bolus ends. */
    fun suspendOnKeypad(stop: Boolean) {
        if (stop) {
            suspended = true
            val c = calendar(pumpNow())
            stopHour = c.get(Calendar.HOUR_OF_DAY)
            stopMinute = c.get(Calendar.MINUTE)
            bolusRunning?.cancelled = true
        } else suspended = false
    }

    /** §4.4: 59 for the newest record of the minute, 58, 57 for the earlier ones. */
    private fun addBolusRecord(atMs: Long, requested: Int, delivered: Int) = synchronized(bolusLog) {
        val minute = minuteOf(atMs)
        bolusLog.filter { it.atMs == minute }.forEach { it.position-- }
        bolusLog.add(0, Bolus(minute, 59, requested, delivered))
        while (bolusLog.size > 128) bolusLog.removeAt(bolusLog.size - 1)
    }

    /** Send the heartbeat `a5/03`, §2.5: its length field is the size without the CRC. */
    fun heartbeat() {
        val body = byteArrayOf(0xAA.toByte(), 0x06, 0x00, 0xA5.toByte(), heartbeatMinutes.toByte(), 0x00)
        send(body + Crc16.modbusLeBytes(body))
    }

    // Frames

    private fun send(frame: ByteArray) {
        if (mutedThread === Thread.currentThread()) return
        // Long frames arrive in several notifications (§2.2): split at 20 bytes to test reassembly.
        var at = 0
        while (at < frame.size) {
            val end = min(frame.size, at + CHUNK)
            sink?.invoke(frame.copyOfRange(at, end))
            at = end
        }
    }

    private fun response(cls: Int, code: Int, index: Int, field2: Int, data: ByteArray): ByteArray {
        val total = 6 + data.size + 2
        val frame = ByteArray(total)
        frame[0] = 0xAA.toByte()
        frame[1] = total.toByte()
        frame[2] = field2.toByte()
        frame[3] = cls.toByte()
        frame[4] = code.toByte()
        frame[5] = index.toByte()
        data.copyInto(frame, 6)
        Crc16.modbusLeBytes(frame, total - 2).copyInto(frame, total - 2)
        return frame
    }

    private fun currentProfileRate(): Int {
        val c = calendar(pumpNow())
        val slot = c.get(Calendar.HOUR_OF_DAY) * 2 + c.get(Calendar.MINUTE) / 30
        return profiles[activeProfile][slot]
    }

    companion object {

        const val CHUNK = 20

        private fun calendar(ms: Long): Calendar = Calendar.getInstance(TimeZone.getDefault()).apply { timeInMillis = ms }

        fun minuteOf(ms: Long): Long = ms - ms % 60_000L

        /** §2.4: `YY MM DD HH MM SS`, plain bytes. */
        fun clock(ms: Long, withSeconds: Boolean): ByteArray {
            val c = calendar(ms)
            return byteArrayOf(
                (c.get(Calendar.YEAR) - 2000).toByte(), (c.get(Calendar.MONTH) + 1).toByte(), c.get(Calendar.DAY_OF_MONTH).toByte(),
                c.get(Calendar.HOUR_OF_DAY).toByte(), c.get(Calendar.MINUTE).toByte(), if (withSeconds) c.get(Calendar.SECOND).toByte() else 0
            )
        }

        fun fromClock(b: ByteArray, at: Int): Long = Calendar.getInstance(TimeZone.getDefault()).apply {
            set(2000 + b[at], b[at + 1] - 1, b[at + 2].toInt(), b[at + 3].toInt(), b[at + 4].toInt(), b[at + 5].toInt())
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        fun u16(b: ByteArray, at: Int): Int = (b[at].toInt() and 0xFF) or ((b[at + 1].toInt() and 0xFF) shl 8)

        fun putU16(b: ByteArray, at: Int, v: Int) {
            b[at] = (v and 0xFF).toByte()
            b[at + 1] = ((v shr 8) and 0xFF).toByte()
        }

        fun putU32(b: ByteArray, at: Int, v: Long) {
            for (i in 0 until 4) b[at + i] = ((v shr (8 * i)) and 0xFF).toByte()
        }

        fun u16Bytes(v: Int): ByteArray = ByteArray(2).also { putU16(it, 0, v) }
    }
}
