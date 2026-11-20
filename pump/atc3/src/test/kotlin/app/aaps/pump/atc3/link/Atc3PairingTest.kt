package app.aaps.pump.atc3.link

import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.pump.atc3.command.Atc3Queue
import app.aaps.pump.atc3.history.Atc3HistorySync
import app.aaps.pump.atc3.keys.Atc3StringKey
import app.aaps.pump.atc3.state.Atc3PumpState
import app.aaps.shared.tests.TestBaseWithProfile
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyBlocking

/** Disconnecting a pump forgets it whole and drops the link. */
class Atc3PairingTest : TestBaseWithProfile() {

    @Mock lateinit var pumpSync: PumpSync
    @Mock lateinit var atc3HistorySync: Atc3HistorySync
    @Mock lateinit var atc3Connection: Atc3Connection
    @Mock lateinit var commandQueue: CommandQueue
    @Mock lateinit var atc3Queue: Atc3Queue

    private lateinit var pairing: Atc3Pairing

    @BeforeEach
    fun setup() {
        pairing = Atc3Pairing(
            aapsLogger, preferences, commandQueue, pumpSync, atc3HistorySync, Atc3PumpState(),
            lazy { atc3Connection }, atc3Queue
        )
    }

    private fun cancelAnswers(success: Boolean) {
        val result = mock<PumpEnactResult> { on { this.success } doReturn success }
        commandQueue.stub { onBlocking { cancelTempBasal(any(), any()) } doReturn result }
    }

    @Test
    fun `disconnecting forgets the serial and the password, everything kept of the pump, and the link`() = runBlocking {
        cancelAnswers(true)

        val reached = pairing.disconnect()

        assertTrue(reached)
        verify(preferences).put(Atc3StringKey.Atc3SerialNumber, "")
        verify(preferences).put(Atc3StringKey.Atc3BtPassword, "")
        verify(preferences).put(Atc3StringKey.Atc3Address, "")
        verify(atc3Connection).disconnect(any())
        verify(atc3HistorySync).forgetPump("")
    }

    @Test
    fun `the pump is tried first - its temp basal is cancelled`() = runBlocking {
        cancelAnswers(true)

        pairing.disconnect()

        verifyBlocking(commandQueue) { cancelTempBasal(eq(true), eq(false)) }
    }

    // Acceptance D9 / S7 aDeadPumpCanBeRemoved: a pump that does not answer is removed anyway, AAPS ends its temp
    // basal and extended bolus, and the caller is told it was not reached.
    @Test
    fun `a dead pump can be removed`() = runBlocking {
        cancelAnswers(false)

        val reached = pairing.disconnect()

        assertFalse(reached)
        verify(preferences).put(Atc3StringKey.Atc3SerialNumber, "")
        verify(atc3Connection).disconnect(any())
        verify(pumpSync).connectNewPump(eq(true))
    }

    @Test
    fun `a pump whose queue throws is removed too`() = runBlocking {
        commandQueue.stub { onBlocking { cancelTempBasal(any(), any()) } doThrow RuntimeException("link lost") }

        val reached = pairing.disconnect()

        assertFalse(reached)
        verify(pumpSync).connectNewPump(eq(true))
    }
}
