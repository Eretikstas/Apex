package app.aaps.plugins.sibionics

import tk.glucodata.drivers.sibionics.SibionicsAdaptiveAlgorithmContext
import tk.glucodata.drivers.sibionics.SibionicsAlgorithmContext
import tk.glucodata.drivers.sibionics.SibionicsAlgorithmMode
import tk.glucodata.drivers.sibionics.SibionicsAlgorithmSelection
import tk.glucodata.drivers.sibionics.SibionicsCalibrationAnchor
import tk.glucodata.drivers.sibionics.SibionicsConstants
import tk.glucodata.drivers.sibionics.SibionicsProtocol
import tk.glucodata.drivers.sibionics.SibionicsSessionPolicy
import tk.glucodata.drivers.sibionics.SibionicsSourceSample
import tk.glucodata.drivers.sibionics.SibionicsVariantLock

/** One glucose value made from one sensor minute. */
internal data class SibionicsReading(
    val timestamp: Long,
    /** The value AAPS uses: the vendor algorithm value with the finger-stick calibration applied, mg/dL. */
    val glucoseMgdl: Double,
    /** The vendor algorithm value without calibration (Stock), mg/dL. A finger stick is paired with this one. */
    val stockMgdl: Double,
    /** Raw sensor value, mg/dL. */
    val rawMgdl: Double,
    val index: Int,
    val live: Boolean
)

/** What the session needs from the radio. All calls come on the radio's own thread. */
internal interface SibionicsSessionIo {

    /** Write [bytes] to the sensor; false when the radio refused it (busy or not connected). */
    fun write(bytes: ByteArray, label: String): Boolean

    /** Run [Timer] after [delayMs]; one pending run per timer, a new call replaces the old one. */
    fun schedule(timer: SibionicsSession.Timer, delayMs: Long)

    fun cancel(timer: SibionicsSession.Timer)

    /** Drop the link and connect again after [delayMs]. */
    fun reconnect(reason: String, delayMs: Long)

    fun log(message: String)
}

/** What the session tells AAPS. */
internal interface SibionicsSessionListener {

    /** New values, oldest first. [sensorStart] is set once, with the first values of a new sensor session. */
    fun onReadings(readings: List<SibionicsReading>, sensorStart: Long?)

    fun onState(state: SibionicsSession.State)
}

/** Where the session keeps what must survive an app restart. */
internal interface SibionicsSessionStore {

    /** Start of the sensor session (ms), 0 if not known. */
    var sessionStart: Long

    /** Time of the newest value seen (ms). */
    var lastSeen: Long

    /** The raw minutes of the session, the only input the vendor algorithm needs to be rebuilt exactly. */
    fun journal(): List<SibionicsSourceSample>

    fun appendJournal(samples: List<SibionicsSourceSample>)

    fun clearJournal()
}

/**
 * The Sibionics 2 (V120) conversation for one sensor: handshake, data requests, the vendor algorithm and its
 * state. Pure Kotlin, no Android: the radio calls the `on...` functions on one thread and does what [io] asks.
 *
 * The flow and its rules come from JugglucoNG's `SibionicsBleManager` (https://github.com/ctqvva/JugglucoNG,
 * commit 75ff85a, GPL-3.0), cut down to Sibionics 2:
 * auth (session key of the app's key group) -> activation -> time sync -> data request from our cursor ->
 * pages of one-minute samples -> live samples every minute. The algorithm state is rebuilt at start by replaying
 * the journal of raw minutes (no binary checkpoint): what was replayed is exactly what was processed.
 */
internal class SibionicsSession(
    private val identity: SibionicsIdentity,
    private val bleAddress: String,
    private val store: SibionicsSessionStore,
    private val io: SibionicsSessionIo,
    private val listener: SibionicsSessionListener,
    /** Finger-stick calibrations of this session: stock value at pairing and finger value, both mmol/L. */
    private val anchors: () -> List<SibionicsCalibrationAnchor> = { emptyList() },
    private val clock: () -> Long = System::currentTimeMillis
) {

    enum class Phase { IDLE, CONNECTED, AUTHENTICATING, ACTIVATING, SYNCING_TIME, REQUESTING_DATA, STREAMING }

    enum class Timer { AUTH, HANDSHAKE, STREAMING }

    data class State(
        val phase: Phase,
        val sessionStart: Long,
        val nextIndex: Int,
        val lastSeen: Long,
        val lastGlucoseMgdl: Double?,
        val authFailed: Boolean
    )

    private val algorithm = SibionicsAlgorithmContext(identity.sensorId)

    // Only its calibration formula is used (JugglucoNG "integrated calibration", Stock calibrated): it keeps no state.
    // The algorithm itself stays on plain Stock, so the journal replay never depends on calibrations.
    private val calibrator = SibionicsAdaptiveAlgorithmContext()
    private var phase = Phase.IDLE

    /** Next index to ask the sensor for: everything below it was processed. */
    private var nextIndex = 0
    private var sessionStart = 0L
    private var lastSeen = 0L
    private var lastGlucose: Double? = null
    private var lastLiveIndex = -1
    private var keyGroups: List<SibionicsConstants.Variant> = emptyList()
    private var keyGroupIndex = 0
    private var authFailed = false
    private var unrequestedSeenThisLink = false
    private var unrequestedLinks = 0
    private var restartDownloadPending = false

    init {
        configureAlgorithm()
        restore()
    }

    val phaseNow: Phase get() = phase

    fun state(): State = State(phase, sessionStart, nextIndex, lastSeen, lastGlucose, authFailed)

    /** The link is up and notifications are on: start the handshake. */
    fun onReady() {
        unrequestedSeenThisLink = false
        restartDownloadPending = false
        keyGroups = SibionicsVariantLock.keyOrder(SibionicsIdentity.VARIANT)
        keyGroupIndex = 0
        phase = Phase.CONNECTED
        sendAuth()
    }

    fun onDisconnected() {
        io.cancel(Timer.AUTH)
        io.cancel(Timer.HANDSHAKE)
        io.cancel(Timer.STREAMING)
        if (unrequestedSeenThisLink) unrequestedLinks++
        phase = Phase.IDLE
        publishState()
    }

    fun onNotification(bytes: ByteArray) {
        if (bytes.isEmpty()) return
        when (val result = SibionicsProtocol.parseV120(bytes)) {
            is SibionicsProtocol.ParseResult.Handshake        -> onHandshake(result.response)
            is SibionicsProtocol.ParseResult.V120Data         -> onData(result.entries)
            is SibionicsProtocol.ParseResult.V120AuthRequired -> {
                keyGroupIndex = 0
                sendAuth()
            }

            is SibionicsProtocol.ParseResult.ChecksumError    -> io.log("checksum error ${SibionicsProtocol.toHex(result.plaintext)}")
            else                                              -> io.log("unknown frame ${SibionicsProtocol.toHex(bytes)}")
        }
    }

    fun onTimer(timer: Timer) {
        when (timer) {
            Timer.AUTH      -> if (phase == Phase.AUTHENTICATING) nextKeyGroup("auth timeout")
            Timer.HANDSHAKE -> if (phase == Phase.ACTIVATING || phase == Phase.SYNCING_TIME || phase == Phase.REQUESTING_DATA) {
                io.log("handshake timeout in $phase, auth again")
                keyGroupIndex = 0
                sendAuth()
            }

            Timer.STREAMING -> if (phase == Phase.STREAMING && clock() - lastSeen > CURRENT_FRESH_MS) io.reconnect("no new value for 3 min", 0)
        }
    }

    private fun onHandshake(response: SibionicsProtocol.ResponseType) {
        when (response) {
            SibionicsProtocol.ResponseType.AUTH_ACCEPTED    -> {
                if (phase != Phase.AUTHENTICATING) return
                io.cancel(Timer.AUTH)
                authFailed = false
                phase = Phase.ACTIVATING
                io.write(SibionicsProtocol.buildActivationPacket(clock() / 1000L), "activation")
                io.schedule(Timer.HANDSHAKE, HANDSHAKE_TIMEOUT_MS)
            }

            SibionicsProtocol.ResponseType.TIME_SYNC_NEEDED -> {
                if (phase == Phase.SYNCING_TIME || phase == Phase.REQUESTING_DATA || phase == Phase.STREAMING) return
                io.cancel(Timer.AUTH)
                phase = Phase.SYNCING_TIME
                io.write(SibionicsProtocol.buildTimeSyncPacket(clock() / 1000L), "time sync")
                io.schedule(Timer.HANDSHAKE, HANDSHAKE_TIMEOUT_MS)
            }

            SibionicsProtocol.ResponseType.DATA_REQUESTED   -> {
                if (phase == Phase.STREAMING) return
                // Asked again while we wait: our request did not arrive, send it again.
                io.cancel(Timer.AUTH)
                phase = Phase.REQUESTING_DATA
                io.write(SibionicsProtocol.buildDataRequestPacket(nextIndex), "data request from $nextIndex")
                io.schedule(Timer.HANDSHAKE, HANDSHAKE_TIMEOUT_MS)
            }

            SibionicsProtocol.ResponseType.STREAMING_READY  -> {
                io.cancel(Timer.AUTH)
                io.cancel(Timer.HANDSHAKE)
                phase = Phase.STREAMING
                io.schedule(Timer.STREAMING, STREAMING_CHECK_MS)
            }
        }
        publishState()
    }

    private fun sendAuth() {
        if (keyGroups.isEmpty()) keyGroups = SibionicsVariantLock.keyOrder(SibionicsIdentity.VARIANT)
        val group = keyGroups[keyGroupIndex.coerceIn(0, keyGroups.lastIndex)]
        val key = SibionicsProtocol.deriveSessionKey(group)
        if (key == null) {
            nextKeyGroup("no key for ${group.id}")
            return
        }
        phase = Phase.AUTHENTICATING
        io.write(SibionicsProtocol.buildAuthPacket(SibionicsConstants.macBytes(bleAddress), key), "auth ${group.id}")
        io.schedule(Timer.AUTH, AUTH_TIMEOUT_MS)
        publishState()
    }

    private fun nextKeyGroup(reason: String) {
        keyGroupIndex++
        if (keyGroupIndex >= keyGroups.size) {
            keyGroupIndex = 0
            authFailed = true
            phase = Phase.IDLE
            io.log("auth failed: $reason")
            publishState()
            io.reconnect("auth failed", RECONNECT_DELAY_MS)
            return
        }
        io.log("auth retry ($reason) with ${keyGroups[keyGroupIndex].id}")
        sendAuth()
    }

    private fun onData(entries: List<SibionicsProtocol.V120Entry>) {
        // Some sensors stream their own current minute right after auth, before our data request. That page is not
        // what we asked for: it is skipped quietly, the request follows and the sensor then serves from our cursor.
        if (phase != Phase.REQUESTING_DATA && phase != Phase.STREAMING) {
            io.log("page idx=${entries.firstOrNull()?.index} before our request, skipped (phase $phase)")
            return
        }
        io.cancel(Timer.HANDSHAKE)
        phase = Phase.STREAMING
        io.schedule(Timer.STREAMING, STREAMING_CHECK_MS)
        if (entries.isEmpty()) {
            publishState()
            return
        }
        val now = clock()
        val ordered = entries.sortedBy { it.index }
        val samples = ordered.map { SibionicsSessionPolicy.SessionSample(it.index, it.eventTimeMs, isLive(it, now)) }
        if (!checkSessionRestart(samples, now)) return
        var newSessionStart: Long? = null
        val readings = mutableListOf<SibionicsReading>()
        val journal = mutableListOf<SibionicsSourceSample>()
        for (entry in ordered) {
            val live = isLive(entry, now)
            val time = sanitizeTime(entry.eventTimeMs, now)
            if (entry.index < 0 || !entry.rawMmol.isFinite() || entry.rawMmol <= 0f) continue
            // Already processed: a repeated page or a repeated notification.
            if (entry.index < nextIndex) continue
            if (live && lastLiveIndex >= 0 && entry.index <= lastLiveIndex) continue
            // A page we did not ask for. The state behind our cursor is still exact: drop it and ask again.
            // With no cursor yet (a sensor first seen mid-session) the sensor's first minute is taken as it is.
            if (nextIndex > 0 && entry.index > nextIndex) {
                unrequestedSeenThisLink = true
                continue
            }
            if (sessionStart <= 0L) {
                sessionStart = time - entry.index * SibionicsConstants.READING_INTERVAL_MS
                store.sessionStart = sessionStart
                newSessionStart = sessionStart
            }
            journal += SibionicsSourceSample(
                index = entry.index,
                timestampMs = time,
                rawMmol = entry.rawMmol,
                temperatureC = entry.temperatureC,
                impedance = entry.rawImpedance.toFloat(),
                variantId = SibionicsIdentity.VARIANT.ordinal
            )
            val glucoseMmol = runAlgorithm(entry.index, entry.rawMmol, entry.temperatureC, entry.rawImpedance.toFloat(), time, live)
            nextIndex = entry.index + 1
            if (live) lastLiveIndex = entry.index
            if (time > lastSeen) lastSeen = time
            val stockMgdl = glucoseMmol * SibionicsConstants.MGDL_PER_MMOLL
            if (!SibionicsConstants.isValidAlgorithmGlucoseMgdl(stockMgdl)) {
                io.log("invalid glucose idx=${entry.index} value=$stockMgdl raw=${entry.rawMgdl}")
                continue
            }
            val calibratedMgdl = calibrate(glucoseMmol, time) * SibionicsConstants.MGDL_PER_MMOLL
            val glucoseMgdl = if (SibionicsConstants.isValidAlgorithmGlucoseMgdl(calibratedMgdl)) calibratedMgdl else stockMgdl
            readings += SibionicsReading(time, glucoseMgdl.toDouble(), stockMgdl.toDouble(), entry.rawMgdl.toDouble(), entry.index, live)
        }
        // The raw minutes are stored before anything is reported: a restart then rebuilds the same state.
        if (journal.isNotEmpty()) store.appendJournal(journal)
        store.lastSeen = lastSeen
        if (readings.isNotEmpty()) {
            unrequestedLinks = 0
            lastGlucose = readings.last().glucoseMgdl
            listener.onReadings(readings, newSessionStart)
        }
        if (unrequestedSeenThisLink && readings.isEmpty()) onUnrequestedPage()
        publishState()
    }

    private fun onUnrequestedPage() {
        if (unrequestedLinks + 1 >= MAX_UNREQUESTED_LINKS) {
            // The sensor keeps refusing our cursor: give up the exact state and take the session from its start.
            io.log("sensor would not serve idx=$nextIndex on $MAX_UNREQUESTED_LINKS links, reading the session again")
            unrequestedLinks = 0
            unrequestedSeenThisLink = false
            resetState(keepSessionStart = true)
        }
        io.reconnect("ask again for idx=$nextIndex", BACKLOG_RECONNECT_DELAY_MS)
    }

    /** False when the page proved a new sensor session and was set aside to read the new session from its start. */
    private fun checkSessionRestart(samples: List<SibionicsSessionPolicy.SessionSample>, now: Long): Boolean {
        if (restartDownloadPending) return false
        val restartedAt = SibionicsSessionPolicy.restartedSessionStartMs(
            samples = samples,
            knownStartMs = sessionStart,
            knownCursor = nextIndex,
            lastSeenMs = lastSeen,
            isRehydrating = false,
            nowMs = now
        ) ?: return true
        io.log("new sensor session: start=$restartedAt previous=$sessionStart cursor=$nextIndex page=${samples.first().index}..${samples.last().index}")
        resetState(keepSessionStart = false)
        if (!SibionicsSessionPolicy.shouldDownloadRestartedSessionFromStart(samples)) return true
        restartDownloadPending = true
        io.reconnect("new sensor session, reading it from idx=0", BACKLOG_RECONNECT_DELAY_MS)
        return false
    }

    private fun resetState(keepSessionStart: Boolean) {
        algorithm.reset()
        store.clearJournal()
        nextIndex = 0
        lastLiveIndex = -1
        if (!keepSessionStart) {
            sessionStart = 0L
            store.sessionStart = 0L
            lastSeen = 0L
            store.lastSeen = 0L
            lastGlucose = null
        }
    }

    private fun runAlgorithm(index: Int, rawMmol: Float, temperatureC: Float, impedance: Float, time: Long, live: Boolean): Float {
        if (index <= 1) algorithm.reset()
        return algorithm.process(
            rawMmol = rawMmol,
            temperatureC = temperatureC,
            index = index,
            mode = if (live) SibionicsAlgorithmMode.LIVE else SibionicsAlgorithmMode.REPLAY,
            impedance = impedance,
            eventTimeMs = time
        )
    }

    /** Stock value [stockMmol] at [time] with the calibrations made up to then; Stock itself when there are none. */
    private fun calibrate(stockMmol: Float, time: Long): Float {
        val list = anchors().filter { it.timestampMs >= sessionStart }
        if (list.isEmpty()) return stockMmol
        return calibrator.applyIntegratedCalibration(stockMmol, time, list)
    }

    private fun configureAlgorithm() {
        algorithm.configure(identity.shortCode, identity.sensitivity, SibionicsIdentity.VARIANT, SibionicsAlgorithmSelection.STOCK)
    }

    /**
     * Rebuild the algorithm from the journal: the same minutes, in the same order, as were processed live. A journal
     * with a hole is dropped and the session is read again from the sensor.
     */
    private fun restore() {
        sessionStart = store.sessionStart
        lastSeen = store.lastSeen
        val samples = store.journal().sortedBy { it.index }
        if (samples.isEmpty()) return
        val contiguous = samples.zipWithNext().all { (a, b) -> b.index == a.index + 1 }
        if (!contiguous || sessionStart <= 0L) {
            io.log("journal not usable (first=${samples.first().index} size=${samples.size} start=$sessionStart), reading the session again")
            store.clearJournal()
            return
        }
        var last: Float? = null
        for (sample in samples) {
            val value = runAlgorithm(sample.index, sample.rawMmol, sample.temperatureC, sample.impedance, sample.timestampMs, live = false)
            if (value.isFinite()) last = value
        }
        nextIndex = samples.last().index + 1
        lastGlucose = last?.let { (it * SibionicsConstants.MGDL_PER_MMOLL).toDouble() }
        io.log("algorithm rebuilt from ${samples.size} journal minutes, next idx=$nextIndex")
    }

    private fun isLive(entry: SibionicsProtocol.V120Entry, now: Long): Boolean =
        entry.eventTimeMs in (now - LIVE_WINDOW_MS)..(now + MAX_FUTURE_DRIFT_MS)

    private fun sanitizeTime(time: Long, now: Long): Long =
        if (time < MIN_REASONABLE_TIME_MS || time > now + MAX_FUTURE_DRIFT_MS) now else time

    private fun publishState() = listener.onState(state())

    companion object {

        const val AUTH_TIMEOUT_MS = 15_000L
        const val HANDSHAKE_TIMEOUT_MS = 15_000L
        const val STREAMING_CHECK_MS = 180_000L
        const val CURRENT_FRESH_MS = 180_000L
        const val RECONNECT_DELAY_MS = 8_000L
        const val BACKLOG_RECONNECT_DELAY_MS = 1_000L
        const val MAX_UNREQUESTED_LINKS = 3
        const val LIVE_WINDOW_MS = 90_000L
        const val MAX_FUTURE_DRIFT_MS = 10L * 60L * 1000L
        const val MIN_REASONABLE_TIME_MS = 946_684_800_000L
    }
}
