package app.aaps.pump.atc3.virtual

import app.aaps.core.data.model.BS
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.IDs
import app.aaps.core.data.model.RM
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.notifications.Notification
import app.aaps.core.interfaces.pump.BlePreCheck
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpInsulin
import app.aaps.core.interfaces.pump.PumpRate
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.keys.LongNonKey
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.pump.atc3.Atc3Notices
import app.aaps.pump.atc3.Atc3PumpPlugin
import app.aaps.pump.atc3.basal.Atc3BasalPeriodKeeper
import app.aaps.pump.atc3.basal.Atc3BasalSpread
import app.aaps.pump.atc3.check.Atc3AapsJournal
import app.aaps.pump.atc3.check.Atc3Reconciliation
import app.aaps.pump.atc3.clock.Atc3ClockKeeper
import app.aaps.pump.atc3.clock.Atc3ClockWatch
import app.aaps.pump.atc3.command.Atc3BolusDelivery
import app.aaps.pump.atc3.command.Atc3Queue
import app.aaps.pump.atc3.command.Atc3SetSuspended
import app.aaps.pump.atc3.history.Atc3HistoryEvents
import app.aaps.pump.atc3.history.Atc3HistorySync
import app.aaps.pump.atc3.history.Atc3JournalFault
import app.aaps.pump.atc3.history.Atc3PumpRegistration
import app.aaps.pump.atc3.keys.Atc3BooleanKey
import app.aaps.pump.atc3.keys.Atc3StringKey
import app.aaps.pump.atc3.link.Atc3BLE
import app.aaps.pump.atc3.link.Atc3Connection
import app.aaps.pump.atc3.link.Atc3LinkKeeper
import app.aaps.pump.atc3.link.Atc3LinkProtection
import app.aaps.pump.atc3.manager.Atc3Manager
import app.aaps.pump.atc3.state.Atc3PumpState
import app.aaps.pump.atc3.store.Atc3Store
import app.aaps.pump.atc3.trace.Atc3Trace
import app.aaps.pump.atc3.exchange.Atc3Exchange
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.atomic.AtomicReference

/**
 * The driver run end to end against [VirtualAtc3Pump]: the plugin, the reconciliation, the manager,
 * the exchange and the connection are the real ones; only the Bluetooth transport is replaced by the
 * virtual pump. Each case is one thing that must hold for the user's safety.
 */
class VirtualPumpScenariosTest : TestBaseWithProfile() {

    @Mock lateinit var atc3BLE: Atc3BLE
    @Mock lateinit var notices: Atc3Notices
    @Mock lateinit var pumpSync: PumpSync
    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var commandQueue: CommandQueue
    @Mock lateinit var atc3Queue: Atc3Queue
    @Mock lateinit var bolusProgressData: BolusProgressData
    @Mock lateinit var blePreCheck: BlePreCheck
    @Mock lateinit var aapsJournal: Atc3AapsJournal

    private val serial = "12345678"
    private lateinit var pump: VirtualAtc3Pump
    private lateinit var pumpState: Atc3PumpState
    private lateinit var connection: Atc3Connection
    private lateinit var manager: Atc3Manager
    private lateinit var plugin: Atc3PumpPlugin

    @Volatile private var linkUp = false

    /** The bolus rows AAPS holds, as PumpSync was told of them. */
    private val rows = ArrayList<BS>()

    @BeforeEach
    fun setup() {
        pump = VirtualAtc3Pump(serial) { dateUtil.now() }
        pumpState = Atc3PumpState()

        whenever(rh.gs(anyInt())).thenReturn("mocked resource")
        whenever(rh.gs(anyInt(), anyOrNull())).thenReturn("mocked resource")
        whenever(rh.gs(anyInt(), anyOrNull(), anyOrNull())).thenReturn("mocked resource")
        whenever(preferences.get(Atc3StringKey.Atc3SerialNumber)).thenReturn(serial)
        whenever(preferences.get(Atc3StringKey.Atc3BtPassword)).thenReturn(pump.btPassword)
        whenever(preferences.get(Atc3StringKey.Atc3BtPasswordAlternate)).thenReturn("")
        whenever(preferences.get(Atc3StringKey.Atc3Address)).thenReturn("11:22:33:AA:BB:CC")
        whenever(preferences.get(Atc3BooleanKey.Trace)).thenReturn(false)
        whenever(preferences.get(Atc3BooleanKey.HoldLink)).thenReturn(true)
        whenever(preferences.get(LongNonKey.ActivePumpChangeTimestamp)).thenReturn(0L)

        // The transport: everything the driver writes goes to the virtual pump.
        whenever(atc3BLE.hasBluetooth).thenReturn(true)
        whenever(atc3BLE.isBluetoothOn).thenReturn(true)
        whenever(atc3BLE.isValidAddress(any())).thenReturn(true)
        whenever(atc3BLE.backoffRemainingMs).thenReturn(0L)
        whenever(atc3BLE.linkProtection).thenReturn(Atc3LinkProtection.PROTECTED)
        whenever(atc3BLE.isConnected).thenAnswer { linkUp }
        whenever(atc3BLE.isConnecting).thenReturn(false)
        whenever(atc3BLE.connect(any())).thenAnswer { linkUp = true; true }
        whenever(atc3BLE.write(any())).thenAnswer { if (linkUp) pump.receive(it.arguments[0] as ByteArray) else false }

        whenever(atc3Queue.readStatus(any())).thenReturn(true)
        whenever(ch.isU100()).thenReturn(true)
        whenever(persistenceLayer.observeChanges(GV::class.java)).thenReturn(MutableSharedFlow())
        whenever(persistenceLayer.observeChanges(RM::class.java)).thenReturn(MutableSharedFlow())
        whenever(pumpSync.verifyPumpIdentification(any(), any())).thenReturn(true)
        runBlocking {
            whenever(pumpSync.syncBolusWithPumpId(any(), any(), anyOrNull(), any(), any(), any())).thenAnswer {
                synchronized(rows) {
                    rows.add(
                        BS(
                            timestamp = it.getArgument(0), amount = it.getArgument<PumpInsulin>(1).cU, type = it.getArgument(2),
                            ids = IDs(pumpId = it.getArgument(3), pumpSerial = it.getArgument(5)), iCfg = someICfg
                        )
                    )
                }
                true
            }
            whenever(pumpSync.syncTemporaryBasalWithPumpId(any(), any(), any(), any(), anyOrNull(), any(), any(), any())).thenReturn(true)
            whenever(pumpSync.syncStopTemporaryBasalWithPumpId(any(), any(), any(), any(), any())).thenReturn(true)
            whenever(pumpSync.insertTherapyEventIfNewWithTimestamp(any(), any(), anyOrNull(), anyOrNull(), any(), any())).thenReturn(true)
            whenever(pumpSync.createOrUpdateTotalDailyDose(any(), any(), any(), any(), anyOrNull(), any(), any())).thenReturn(true)
            whenever(persistenceLayer.getBolusesFromTimeIncludingInvalid(any(), any())).thenAnswer { synchronized(rows) { rows.toList() } }
            whenever(aapsJournal.bolusesBetween(any(), any())).thenReturn(0.0)
        }

        val trace = Atc3Trace(aapsLogger, preferences)
        val clockWatch = Atc3ClockWatch()
        connection = Atc3Connection(aapsLogger, rxBus, preferences, atc3BLE, trace, notices, rh, mock())
        val exchange = Atc3Exchange(aapsLogger, preferences, atc3BLE, trace, connection)
        val bolusDelivery = Atc3BolusDelivery(aapsLogger, dateUtil, exchange)
        manager = Atc3Manager(aapsLogger, rxBus, preferences, dateUtil, atc3BLE, pumpState, trace, clockWatch, connection, exchange, bolusDelivery)
        val store = Atc3Store(aapsLogger, preferences, notices, rh)
        val registration = Atc3PumpRegistration(aapsLogger, rh, pumpSync, pumpState)
        val historySync = Atc3HistorySync(aapsLogger, rh, notices, preferences, store, pumpSync, persistenceLayer, dateUtil, pumpState, clockWatch, trace, registration)
        val historyEvents = Atc3HistoryEvents(aapsLogger, rh, preferences, pumpSync, dateUtil, pumpState, trace, registration)
        val journalFault = Atc3JournalFault(aapsLogger, rh, dateUtil, notices, pumpSync, manager, historySync, pumpState, trace)
        val reconciliation = Atc3Reconciliation(aapsLogger, rh, rxBus, notices, pumpState, manager, historySync, journalFault, aapsJournal, store, trace)
        val clockKeeper = Atc3ClockKeeper(aapsLogger, rh, rxBus, notices, dateUtil, pumpState, manager, historySync, clockWatch, trace)
        val basalPeriods = Atc3BasalPeriodKeeper(
            aapsLogger, rh, store, dateUtil, atc3Queue, pumpState, manager, historySync, aapsJournal, reconciliation, trace, Atc3BasalSpread()
        )
        val linkKeeper = Atc3LinkKeeper(aapsLogger, rh, preferences, store, dateUtil, atc3Queue, rxBus, notices, pumpState, connection, historySync, trace)
        plugin = Atc3PumpPlugin(
            aapsLogger, rh, preferences, commandQueue, pumpState, manager, bolusDelivery, rxBus, notices,
            historySync, historyEvents, reconciliation, clockWatch, dateUtil, aapsSchedulers, fabricPrivacy, pumpEnactResultProvider,
            trace, clockKeeper, basalPeriods, linkKeeper, connection, atc3Queue, bolusProgressData, persistenceLayer, blePreCheck, ch
        )

        // The pump's notifications reach the driver as the Bluetooth stack would hand them over.
        pump.sink = { chunk -> connection.onDataReceived(chunk) }
        pump.onLinkDropped = { linkUp = false; connection.onDisconnected() }
        connect()
    }

    @AfterEach
    fun tearDown() {
        pump.shutdown()
    }

    private fun connect() {
        connection.connect("test")
        connection.onConnected()
    }

    private fun bolus(units: Double, type: BS.Type = BS.Type.NORMAL, lastKnownBolusTime: Long = 0L) = DetailedBolusInfo().also {
        it.insulin = units
        it.bolusType = type
        it.lastKnownBolusTime = lastKnownBolusTime
        it.timestamp = dateUtil.now()
    }

    private fun bolusRows(): List<BS> = synchronized(rows) { rows.toList() }

    private fun status() = runBlocking { plugin.getPumpStatus("test") }

    // Reading the pump

    @Test
    fun `a status read fills the pump state from what the pump says`() {
        status()

        assertThat(plugin.isInitialized()).isTrue()
        assertThat(plugin.reservoirLevel.value.cU).isWithin(1e-9).of(200.0)
        assertThat(plugin.baseBasalRate.cU).isWithin(1e-9).of(1.0)
        assertThat(pumpState.version?.firmwareText).isEqualTo("1.1.1.0")
        // The heartbeat the held link is watched by is set once per link.
        assertThat(pump.received).contains("a1/33")
    }

    @Test
    fun `the pump's own maximum bolus limits what AAPS may ask`() {
        status()

        val asked = plugin.applyBolusConstraints(ConstraintObject(25.0, aapsLogger))

        assertThat(asked.value()).isWithin(1e-9).of(10.0)
    }

    // Boluses

    @Test
    fun `a bolus of 1 U is delivered, written once, and reported as delivered`() {
        status()

        val result = runBlocking { plugin.deliverTreatment(bolus(1.0)) }

        assertThat(result.success).isTrue()
        assertThat(result.bolusDelivered).isWithin(1e-9).of(1.0)
        assertThat(pump.bolusLog.first().delivered).isEqualTo(40)
        assertThat(bolusRows().filter { it.amount > 0 }.map { it.amount }).containsExactly(1.0)
    }

    @Test
    fun `a bolus off the pump's step goes out as what the pump can deliver`() {
        status()

        // 1.425 U: from 1 U the pump delivers in 0.05 U steps, so 1.45 U goes out, never more than asked plus one step.
        val result = runBlocking { plugin.deliverTreatment(bolus(1.425)) }

        assertThat(result.success).isTrue()
        val delivered = pump.bolusLog.first().delivered * 0.025
        assertThat(delivered).isAtMost(1.425 + 0.05 + 1e-9)
        assertThat(result.bolusDelivered).isWithin(1e-9).of(delivered)
    }

    @Test
    fun `a bolus stopped half way is reported with what went in, not with what was asked`() {
        status()
        pump.bolusTickMs = 50
        val result = AtomicReference<PumpEnactResult?>(null)
        val thread = Thread { result.set(runBlocking { plugin.deliverTreatment(bolus(2.0)) }) }
        thread.start()
        val deadline = System.currentTimeMillis() + 5_000
        while (!pump.bolusInProgress && System.currentTimeMillis() < deadline) Thread.sleep(10)
        Thread.sleep(300)

        plugin.stopBolusDelivering()
        thread.join(30_000)

        val inPump = pump.bolusLog.first().delivered * 0.025
        assertThat(inPump).isLessThan(2.0)
        assertThat(result.get()?.bolusDelivered ?: -1.0).isWithin(1e-9).of(inPump)
        // What AAPS counts is what the pump delivered.
        assertThat(bolusRows().sumOf { it.amount }).isWithin(1e-9).of(inPump)
    }

    @Test
    fun `a bolus the pump stops delivering is not reported as given`() {
        status()
        pump.bolusStallsAt = 10 // 0.25 U of 1 U

        val result = runBlocking { plugin.deliverTreatment(bolus(1.0)) }

        assertThat(result.success).isFalse()
    }

    @Test
    fun `a link lost during a bolus does not pass for a full bolus`() {
        status()
        pump.dropLinkAtBolusRaw = 10

        val result = runBlocking { plugin.deliverTreatment(bolus(1.0)) }

        assertThat(result.success).isFalse()
    }

    @Test
    fun `a bolus whose answer was lost but which the pump delivers is followed and counted once`() {
        status()
        pump.bolusAckLost = true

        val result = runBlocking { plugin.deliverTreatment(bolus(1.0)) }

        // The progress showed the bolus running: followed to its end like any other.
        assertThat(result.success).isTrue()
        assertThat(result.bolusDelivered).isWithin(1e-9).of(1.0)
        assertThat(pump.bolusLog.size).isEqualTo(1)
        assertThat(bolusRows().filter { it.amount > 0 }.map { it.amount }).containsExactly(1.0)
    }

    @Test
    fun `a bolus the pump never answered is not reported as simply failed, and the user is told to check`() {
        status()
        pump.silentFor.add(0x12)

        val result = runBlocking { plugin.deliverTreatment(bolus(1.0)) }

        assertThat(result.success).isFalse()
        assertThat(pump.bolusLog).isEmpty()
        assertThat(bolusRows().filter { it.amount > 0 }).isEmpty()
        // The warning stays on screen, not only in the dialog that may be closed already.
        verify(notices, atLeastOnce()).addNotification(eq(Notification.PUMP_WARNING), any(), eq(Notification.URGENT))
    }

    @Test
    fun `a bolus given on the pump reaches AAPS, and an SMB decided before it is refused`() {
        status()
        val decidedAt = dateUtil.now() - 60_000L
        pump.bolusOnKeypad(2.0)

        val smb = runBlocking { plugin.deliverTreatment(bolus(0.5, BS.Type.SMB, lastKnownBolusTime = decidedAt)) }

        assertThat(smb.success).isFalse()
        // Nothing more went in than the keypad bolus.
        assertThat(pump.bolusLog.size).isEqualTo(1)
        assertThat(bolusRows().map { it.amount }).contains(2.0)
    }

    // Temporary basal

    @Test
    fun `a temporary basal reaches the pump as an absolute rate in 15 minute steps`() {
        status()

        val result = runBlocking { plugin.setTempBasalAbsolute(1.5, 30, false, PumpSync.TemporaryBasalType.NORMAL) }

        assertThat(result.success).isTrue()
        val running = pump.tbr!!
        assertThat(running.absolute).isTrue()
        assertThat(running.durationSteps).isEqualTo(2)
        assertThat(running.value).isEqualTo(60)
        runBlocking {
            verify(pumpSync, atLeastOnce()).syncTemporaryBasalWithPumpId(
                any(), eq(PumpRate(1.5)), any(), eq(true), anyOrNull(), any(), eq(PumpType.ATC3), eq(serial)
            )
        }
    }

    @Test
    fun `cancelling a temporary basal cancels it on the pump`() {
        status()
        runBlocking { plugin.setTempBasalAbsolute(1.5, 30, false, PumpSync.TemporaryBasalType.NORMAL) }

        val result = runBlocking { plugin.cancelTempBasal(true) }

        assertThat(result.success).isTrue()
        assertThat(pump.tbr).isNull()
        assertThat(pump.received).contains("a1/05")
    }

    @Test
    fun `a percent temporary basal is not offered`() {
        status()

        val result = runBlocking { plugin.setTempBasalPercent(150, 30, false, PumpSync.TemporaryBasalType.NORMAL) }

        assertThat(result.success).isFalse()
        assertThat(pump.tbr).isNull()
    }

    // Stop and resume

    @Test
    fun `stopping the pump from AAPS stops delivery, and AAPS sees the pump stopped`() {
        status()

        val result = plugin.executeCustomCommand(Atc3SetSuspended(true))

        assertThat(result?.success).isTrue()
        assertThat(pump.suspended).isTrue()
        assertThat(plugin.isSuspended()).isTrue()
    }

    @Test
    fun `a bolus is not sent to a stopped pump`() {
        status()
        plugin.executeCustomCommand(Atc3SetSuspended(true))
        pump.received.clear()

        val result = runBlocking { plugin.deliverTreatment(bolus(1.0)) }

        assertThat(result.success).isFalse()
        assertThat(pump.received).doesNotContain("a1/12")
    }

    // A locked pump

    @Test
    fun `a locked pump gets no bolus, and nothing is delivered`() {
        pump.locked = true
        status()
        pump.received.clear()

        val result = runBlocking { plugin.deliverTreatment(bolus(1.0)) }

        assertThat(result.success).isFalse()
        assertThat(pump.bolusLog).isEmpty()
    }

    // The clock

    @Test
    fun `a pump clock a few minutes off is set from the phone`() {
        pump.clockOffsetMs = 3 * 60_000L

        status()

        assertThat(pump.received).contains("a1/31")
        assertThat(Math.abs(pump.clockOffsetMs)).isLessThan(60_000L)
    }

    // Alarms

    @Test
    fun `an alarm that stops delivery stops the pump`() {
        // Code 13, reservoir empty (§4.16).
        pump.activeAlarms.add(13)

        status()

        // Read by the driver as reservoir empty, which stops delivery.
        assertThat(pumpState.activeAlarms.any { it.stopsDelivery }).isTrue()
        // The driver stops the pump itself, so that both sides agree nothing goes in.
        assertThat(pump.suspended).isTrue()
        assertThat(plugin.isSuspended()).isTrue()
    }
}
