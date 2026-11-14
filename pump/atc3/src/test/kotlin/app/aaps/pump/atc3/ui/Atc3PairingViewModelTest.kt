package app.aaps.pump.atc3.ui

import android.Manifest
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Looper
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.atc3.keys.Atc3StringKey
import app.aaps.pump.atc3.link.Atc3Pairing
import app.aaps.shared.tests.AAPSLoggerTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyVararg
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The pairing screen's logic (AIMI / official AAPS acceptance U1 and U6, and the scan part of the Bluetooth contract) on
 * Robolectric's Bluetooth: the search finds the pump by its serial and stops, a search that finds nothing ends on its
 * own, Connect goes to the pump found with the serial and password, a refused password keeps the pump found, leaving the
 * screen stops the search, and Disconnect unpairs, but not during a bolus.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class Atc3PairingViewModelTest {

    private val context: Application = RuntimeEnvironment.getApplication()
    private val adapter: BluetoothAdapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    private val scheduler = TestCoroutineScheduler()
    private val rh: ResourceHelper = mock {
        on { gs(any()) } doReturn "text"
        on { gs(any(), anyVararg()) } doReturn "text"
    }
    private val preferences: Preferences = mock()
    private val pairing: Atc3Pairing = mock()

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher(scheduler))
        shadowOf(adapter).setEnabled(true)
        shadowOf(context).grantPermissions(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
        whenever(preferences.get(Atc3StringKey.Atc3SerialNumber)).thenReturn(SERIAL)
        whenever(preferences.get(Atc3StringKey.Atc3BtPassword)).thenReturn(PASSWORD)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = Atc3PairingViewModel(AAPSLoggerTest(), rh, preferences, pairing, context)

    private val scans get() = shadowOf(adapter.bluetoothLeScanner).scanCallbacks

    private fun searching(): Atc3PairingViewModel = viewModel().apply {
        onSerialChanged(SERIAL)
        find()
        settle()
    }

    private fun seen(name: String, address: String = ADDRESS) {
        val device: BluetoothDevice = adapter.getRemoteDevice(address)
        shadowOf(device).setName(name)
        @Suppress("DEPRECATION")
        scans.single().onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, ScanResult(device, null, -50, 0L))
        settle()
    }

    private fun settle() {
        scheduler.runCurrent()
        shadowOf(Looper.getMainLooper()).idle()
        scheduler.runCurrent()
    }

    // ---- The search (U1, Bluetooth contract: the scan) ----------------------------------------------

    @Test
    fun aSearchFindsThePumpByItsSerialAndStops() {
        val vm = searching()
        assertEquals("one scan", 1, scans.size)
        assertEquals(Atc3PairingViewModel.Step.SEARCHING, vm.state.value.step)

        seen("APEX$SERIAL")

        assertEquals(Atc3PairingViewModel.Step.FOUND, vm.state.value.step)
        assertTrue("the scan stopped", scans.isEmpty())
        assertTrue("the password is asked for", vm.state.value.showPassword)
    }

    @Test
    fun anotherPumpIsNotTaken() {
        val vm = searching()

        seen("APEX87654321", address = "00:11:22:AA:BB:DD")

        assertEquals(Atc3PairingViewModel.Step.SEARCHING, vm.state.value.step)
        assertEquals("still scanning", 1, scans.size)
    }

    @Test
    fun withoutTheScanPermissionNoScanStarts() {
        shadowOf(context).denyPermissions(Manifest.permission.BLUETOOTH_SCAN)
        val vm = viewModel()
        vm.onSerialChanged(SERIAL)

        vm.find()
        settle()

        assertTrue("no scan without the permission", scans.isEmpty())
        assertEquals(Atc3PairingViewModel.Step.SERIAL, vm.state.value.step)
    }

    @Test
    fun aSearchThatFindsNothingEndsOnItsOwn() {
        val vm = searching()

        scheduler.advanceTimeBy(31_000L)
        settle()

        assertEquals(Atc3PairingViewModel.Step.SERIAL, vm.state.value.step)
        assertTrue("the scan stopped", scans.isEmpty())
    }

    @Test
    fun noSearchWithAnInvalidSerial() {
        val vm = viewModel()
        vm.onSerialChanged("1234")

        vm.find()

        assertFalse(vm.state.value.canFind)
        assertTrue(scans.isEmpty())
    }

    // ---- Connect (U1) -------------------------------------------------------------------------------

    @Test
    fun connectGoesToThePumpFoundWithTheSerialAndPassword() {
        val vm = searching()
        seen("APEX$SERIAL")
        vm.onPasswordChanged(PASSWORD)

        vm.connect()

        verify(pairing).connect(eq(ADDRESS), eq(SERIAL), eq(PASSWORD), any())
        assertEquals(Atc3PairingViewModel.Step.CONNECTING, vm.state.value.step)
    }

    @Test
    fun aRefusedPasswordKeepsThePumpFound() {
        val vm = searching()
        seen("APEX$SERIAL")
        vm.onPasswordChanged("111111")
        vm.connect()
        val result = argumentCaptor<(Atc3Pairing.Outcome, String) -> Unit>()
        verify(pairing).connect(eq(ADDRESS), eq(SERIAL), eq("111111"), result.capture())

        result.firstValue(Atc3Pairing.Outcome.REFUSED, "")
        settle()

        assertEquals(Atc3PairingViewModel.Step.FOUND, vm.state.value.step)
        assertTrue("the password can be corrected", vm.state.value.passwordEditable)
    }

    // ---- Leaving the screen (U5) and unpairing (U6) --------------------------------------------------

    @Test
    fun leavingTheScreenStopsTheSearch() {
        val vm = searching()

        vm.onLeave()
        settle()

        assertTrue("the scan stopped", scans.isEmpty())
        assertEquals(Atc3PairingViewModel.Step.SERIAL, vm.state.value.step)
    }

    @Test
    fun disconnectUnpairsAPairedPump() {
        whenever(pairing.isPaired).thenReturn(true)
        val vm = viewModel()
        assertTrue(vm.state.value.paired)

        vm.disconnect()

        verify(pairing).disconnect()
    }

    @Test
    fun noUnpairingDuringABolus() {
        whenever(pairing.isPaired).thenReturn(true)
        whenever(pairing.bolusUnderWay).thenReturn(true)
        val vm = viewModel()

        vm.disconnect()

        verify(pairing, never()).disconnect()
    }

    private companion object {

        const val SERIAL = "12345678"
        const val PASSWORD = "123456"
        const val ADDRESS = "00:11:22:AA:BB:CC"
    }
}
