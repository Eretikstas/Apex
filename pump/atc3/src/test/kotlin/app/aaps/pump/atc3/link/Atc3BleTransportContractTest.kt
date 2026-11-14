package app.aaps.pump.atc3.link

import android.Manifest
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Looper
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.atc3.keys.Atc3BooleanKey
import app.aaps.pump.atc3.trace.Atc3Trace
import app.aaps.shared.tests.AAPSLoggerTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The ATC3 radio code ([Atc3BLE]) on its real Android code against Robolectric's Bluetooth.
 *
 * The checks of the official AAPS `BleTransportContract` (Milos Kozak, official dev, 2026-11-12), written for the
 * ATC3 link, which is not a `BleTransport`: one GATT client, the link told up and down, the characteristics found,
 * notifications on, data in, data out, and nothing after a close. Added for the ATC3: its setup chain with the
 * Bluetooth password (MTU, discovery, password subscription, password, data subscription) and a refused password.
 * The scan is the pairing screen's and is tested with the screens.
 *
 * Robolectric may answer a GATT call by itself; each step of the chain is then also fired here by hand. The link
 * takes a step only from the state before it, so a callback it already had is ignored, as on a real stack.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class Atc3BleTransportContractTest {

    private val context: Application = RuntimeEnvironment.getApplication()
    private val adapter: BluetoothAdapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    private val device: BluetoothDevice get() = adapter.getRemoteDevice(ADDRESS)
    private val events = Recorder()
    private lateinit var ble: Atc3BLE

    @Before
    fun setUp() {
        shadowOf(adapter).setEnabled(true)
        shadowOf(device).setName(ADVERTISED_NAME)
        shadowOf(context).grantPermissions(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
        val preferences = mock<Preferences>()
        whenever(preferences.get(Atc3BooleanKey.Trace)).thenReturn(false)
        val logger = AAPSLoggerTest()
        ble = Atc3BLE(logger, context, Atc3Trace(logger, preferences))
        ble.setCallback(events)
        ble.setPassword(PASSWORD)
    }

    @After
    fun tearDown() {
        runCatching { ble.disconnect() }
    }

    // ---- Permissions and the GATT client ------------------------------------------------------------

    @Test
    fun withoutTheConnectPermissionNoGattIsOpened() {
        shadowOf(context).denyPermissions(Manifest.permission.BLUETOOTH_CONNECT)

        val asked = ble.connect(ADDRESS)

        val open = shadowOf(device).bluetoothGatts.count { !shadowOf(it).isClosed }
        assertEquals("open GATT clients without the permission (connect said $asked)", 0, open)
        assertFalse("not connected", ble.isConnected)
    }

    @Test
    fun aConnectOpensOneGattWithTheDriversCallback() {
        assertTrue(ble.connect(ADDRESS))

        val gatts = shadowOf(device).bluetoothGatts
        assertEquals("GATT clients", 1, gatts.size)
        assertNotNull(shadowOf(gatts.single()).gattCallback)
    }

    /** Android has few GATT clients. One that is left open ends in status 133 and no connection at all. */
    @Test
    fun aSecondConnectLeavesAtMostOneGattOpen() {
        ble.connect(ADDRESS)
        ble.connect(ADDRESS)

        val open = shadowOf(device).bluetoothGatts.count { !shadowOf(it).isClosed }
        assertTrue("open GATT clients: $open", open <= 1)
    }

    // ---- The setup chain ----------------------------------------------------------------------------

    @Test
    fun theWholeSetupWithAPasswordEndsReadyAndIsToldOnce() {
        ready(withPassword = true)

        assertTrue(ble.isConnected)
        assertEquals(Atc3LinkProtection.PROTECTED, ble.linkProtection)
        assertEquals(listOf("up"), events.links)
    }

    @Test
    fun aPumpWithoutThePasswordServiceIsReadyAfterTheDataSubscription() {
        ready(withPassword = false)

        assertTrue(ble.isConnected)
        assertEquals(Atc3LinkProtection.UNSUPPORTED, ble.linkProtection)
        assertEquals(listOf("up"), events.links)
    }

    @Test
    fun aRefusedPasswordEndsTheLinkAndIsReported() {
        val pump = connectedAndDiscovered(withPassword = true)
        pump.callback.onDescriptorWrite(pump.gatt, pump.authCccd!!, BluetoothGatt.GATT_SUCCESS)
        pump.callback.onCharacteristicWrite(pump.gatt, pump.authWrite!!, BluetoothGatt.GATT_SUCCESS)

        pump.callback.onCharacteristicChanged(pump.gatt, pump.authNotify!!, byteArrayOf(Atc3BtPassword.REJECTED))

        await("the refusal reported") { events.rejections > 0 }
        assertFalse(ble.isConnected)
        assertFalse("never told as up", events.links.contains("up"))
    }

    @Test
    fun thePasswordGoesOutBeforeTheDataSubscription() {
        val pump = connectedAndDiscovered(withPassword = true)
        pump.callback.onDescriptorWrite(pump.gatt, pump.authCccd!!, BluetoothGatt.GATT_SUCCESS)

        await("the password on the radio") { shadowOf(pump.gatt).latestWrittenBytes?.contentEquals(Atc3BtPassword.authValue(PASSWORD)) == true }
        assertFalse("not ready before the pump answers the password", ble.isConnected)
    }

    // ---- The link, data in and out ------------------------------------------------------------------

    @Test
    fun theLinkGoingDownIsToldOnce() {
        val pump = ready(withPassword = true)

        pump.callback.onConnectionStateChange(pump.gatt, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_DISCONNECTED)
        pump.callback.onConnectionStateChange(pump.gatt, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_DISCONNECTED)

        assertEquals(listOf("up", "down"), events.links)
        assertFalse(ble.isConnected)
    }

    @Test
    fun aNotificationReachesTheDriver() {
        val pump = ready(withPassword = true)
        val bytes = byteArrayOf(1, 2, 3, 4)

        pump.callback.onCharacteristicChanged(pump.gatt, pump.notify, bytes)

        await("the data reported") { events.received.any { it.contentEquals(bytes) } }
    }

    @Test
    fun theAnswerToThePasswordIsNotData() {
        val pump = ready(withPassword = true)

        pump.callback.onCharacteristicChanged(pump.gatt, pump.authNotify!!, byteArrayOf(Atc3BtPassword.ACCEPTED))

        assertTrue("no data from the password characteristic", events.received.isEmpty())
    }

    @Test
    fun aWriteGoesToTheWriteCharacteristic() {
        val pump = ready(withPassword = true)
        val bytes = byteArrayOf(5, 6, 7, 8)

        assertTrue(ble.write(bytes))

        await("the bytes on the radio") { shadowOf(pump.gatt).latestWrittenBytes?.contentEquals(bytes) == true }
    }

    @Test
    fun aWriteWithoutALinkIsRefused() {
        assertFalse(ble.write(byteArrayOf(1)))
        assertTrue(shadowOf(device).bluetoothGatts.isEmpty())
    }

    @Test
    fun closeEndsTheGattAndLaterCallsDoNothing() {
        val pump = ready(withPassword = true)

        ble.disconnect()

        assertTrue("closed", shadowOf(pump.gatt).isClosed)
        assertFalse("no write on a closed link", ble.write(byteArrayOf(9)))
        val before = events.received.size
        pump.callback.onCharacteristicChanged(pump.gatt, pump.notify, byteArrayOf(7))
        pump.callback.onConnectionStateChange(pump.gatt, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_CONNECTED)
        idle()
        assertEquals("nothing taken from a closed link", before, events.received.size)
        assertFalse("a closed link does not come back by itself", ble.isConnected)
        assertEquals("told down once", 1, events.links.count { it == "down" })
    }

    // ---- The pump on Robolectric's radio -----------------------------------------------------------

    private class Pump(
        val gatt: BluetoothGatt,
        val callback: BluetoothGattCallback,
        val notify: BluetoothGattCharacteristic,
        val dataCccd: BluetoothGattDescriptor,
        val authNotify: BluetoothGattCharacteristic?,
        val authWrite: BluetoothGattCharacteristic?,
        val authCccd: BluetoothGattDescriptor?
    )

    /** Connected, the MTU set and the pump's services found: the link waits for its first subscription. */
    private fun connectedAndDiscovered(withPassword: Boolean): Pump {
        assertTrue("connect", ble.connect(ADDRESS))
        val gatt = shadowOf(device).bluetoothGatts.last()
        val callback = shadowOf(gatt).gattCallback
        val pump = addPumpServices(gatt, callback, withPassword)

        callback.onConnectionStateChange(gatt, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_CONNECTED)
        callback.onMtuChanged(gatt, MTU, BluetoothGatt.GATT_SUCCESS)
        gatt.discoverServices()
        callback.onServicesDiscovered(gatt, BluetoothGatt.GATT_SUCCESS)
        idle()
        return pump
    }

    /** The whole chain, as a pump answers it, until the link is ready. */
    private fun ready(withPassword: Boolean): Pump {
        val pump = connectedAndDiscovered(withPassword)
        if (withPassword) {
            pump.callback.onDescriptorWrite(pump.gatt, pump.authCccd!!, BluetoothGatt.GATT_SUCCESS)
            pump.callback.onCharacteristicWrite(pump.gatt, pump.authWrite!!, BluetoothGatt.GATT_SUCCESS)
            pump.callback.onCharacteristicChanged(pump.gatt, pump.authNotify!!, byteArrayOf(Atc3BtPassword.ACCEPTED))
        }
        pump.callback.onDescriptorWrite(pump.gatt, pump.dataCccd, BluetoothGatt.GATT_SUCCESS)
        await("the link ready (state ${ble.linkState})") { ble.isConnected }
        return pump
    }

    private fun addPumpServices(gatt: BluetoothGatt, callback: BluetoothGattCallback, withPassword: Boolean): Pump {
        val notifyService = BluetoothGattService(GattAttributes.serviceNotifyUuid, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        val notify = BluetoothGattCharacteristic(
            GattAttributes.characteristicNotifyUuid,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        val dataCccd = cccd()
        notify.addDescriptor(dataCccd)
        notifyService.addCharacteristic(notify)

        val writeService = BluetoothGattService(GattAttributes.serviceWriteUuid, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        writeService.addCharacteristic(
            BluetoothGattCharacteristic(
                GattAttributes.characteristicWriteUuid,
                BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
                BluetoothGattCharacteristic.PERMISSION_WRITE
            )
        )
        shadowOf(gatt).addDiscoverableService(notifyService)
        shadowOf(gatt).addDiscoverableService(writeService)
        shadowOf(gatt).allowCharacteristicNotification(notify)

        var authNotify: BluetoothGattCharacteristic? = null
        var authWrite: BluetoothGattCharacteristic? = null
        var authCccd: BluetoothGattDescriptor? = null
        if (withPassword) {
            val authService = BluetoothGattService(GattAttributes.serviceAuthUuid, BluetoothGattService.SERVICE_TYPE_PRIMARY)
            authNotify = BluetoothGattCharacteristic(
                GattAttributes.characteristicAuthNotifyUuid,
                BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ
            )
            authCccd = cccd()
            authNotify.addDescriptor(authCccd)
            authWrite = BluetoothGattCharacteristic(
                GattAttributes.characteristicAuthWriteUuid,
                BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_WRITE
            )
            authService.addCharacteristic(authNotify)
            authService.addCharacteristic(authWrite)
            shadowOf(gatt).addDiscoverableService(authService)
            shadowOf(gatt).allowCharacteristicNotification(authNotify)
        }
        return Pump(gatt, callback, notify, dataCccd, authNotify, authWrite, authCccd)
    }

    private fun cccd() = BluetoothGattDescriptor(
        GattAttributes.characteristicConfigDescriptor,
        BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
    )

    private fun await(what: String, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + AWAIT_MS
        while (!condition()) {
            if (System.currentTimeMillis() > end) fail("Timeout waiting for: $what. Events: $events")
            idle()
        }
    }

    private fun idle() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50))
        Thread.sleep(10)
    }

    /** What the link told the driver, in order. */
    private class Recorder : Atc3BleCallback {

        val links = CopyOnWriteArrayList<String>()
        val received = CopyOnWriteArrayList<ByteArray>()
        @Volatile var rejections = 0
        @Volatile var sendErrors = 0

        override fun onConnected() {
            links += "up"
        }

        override fun onDisconnected() {
            links += "down"
        }

        override fun onDataReceived(chunk: ByteArray) {
            received += chunk
        }

        override fun onSendError(reason: String) {
            sendErrors++
        }

        override fun onAuthenticationRejected() {
            rejections++
        }

        override fun toString(): String = "links=$links received=${received.size} rejections=$rejections sendErrors=$sendErrors"
    }

    private companion object {

        const val ADDRESS = "00:11:22:AA:BB:CC"
        const val ADVERTISED_NAME = "APEX12345678"
        const val PASSWORD = "123456"
        const val MTU = 185
        const val AWAIT_MS = 5_000L
    }
}
