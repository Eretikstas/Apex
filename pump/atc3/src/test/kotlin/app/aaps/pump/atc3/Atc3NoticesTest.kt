package app.aaps.pump.atc3

import app.aaps.core.interfaces.notifications.Notification
import app.aaps.core.interfaces.notifications.NotificationAction
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationLevel
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify

/**
 * The driver's notices on the AAPS 4.0 notification system: every one it posts must reach the user,
 * and every one it takes away must go, see [Atc3Notices].
 */
class Atc3NoticesTest : TestBase() {

    @Mock lateinit var notificationManager: NotificationManager

    private lateinit var notices: Atc3Notices

    /** Every 3.4 id the driver posts or takes away; a new one must be added to [Atc3Notices.idOf] too. */
    private val idsUsed = listOf(
        Notification.PUMP_ERROR, Notification.PUMP_WARNING, Notification.PUMP_SUSPENDED, Notification.PUMP_SYNC_ERROR,
        Notification.PUMP_UNREACHABLE, Notification.WRONG_PUMP_PASSWORD, Notification.BLUETOOTH_NOT_ENABLED,
        Notification.PROFILE_SET_OK, Notification.FAILED_UPDATE_PROFILE, Notification.INSIGHT_DATE_TIME_UPDATED,
        Notification.OVER_24H_TIME_CHANGE_REQUESTED
    )

    @BeforeEach
    fun setup() {
        notices = Atc3Notices(aapsLogger, notificationManager)
    }

    @Test
    fun `every id the driver uses has an AAPS 4_0 id`() {
        for (id in idsUsed) assertThat(Atc3Notices.idOf(id)).isNotNull()
    }

    @Test
    fun `an urgent pump error stays an alarm, as AAPS 4_0 itself rates it`() {
        assertThat(Atc3Notices.levelOf(Notification.URGENT, NotificationId.PUMP_ERROR)).isEqualTo(NotificationLevel.URGENT)
        assertThat(Atc3Notices.levelOf(Notification.URGENT, NotificationId.PUMP_UNREACHABLE)).isEqualTo(NotificationLevel.URGENT)
    }

    @Test
    fun `an urgent notice is never shown below important`() {
        // The bolus journal fault: urgent for the driver, normal by AAPS 4.0's own rating.
        assertThat(Atc3Notices.levelOf(Notification.URGENT, NotificationId.PUMP_SYNC_ERROR)).isEqualTo(NotificationLevel.IMPORTANT)
    }

    @Test
    fun `a lower level is kept as the driver chose it`() {
        assertThat(Atc3Notices.levelOf(Notification.NORMAL, NotificationId.WRONG_PUMP_PASSWORD)).isEqualTo(NotificationLevel.NORMAL)
        assertThat(Atc3Notices.levelOf(Notification.INFO, NotificationId.PROFILE_SET_OK)).isEqualTo(NotificationLevel.INFO)
    }

    @Test
    fun `a notice is posted to the notification manager`() {
        notices.addNotification(Notification.PUMP_ERROR, "pump alarm", Notification.URGENT)

        verify(notificationManager).post(eq(NotificationId.PUMP_ERROR), eq("pump alarm"), eq(NotificationLevel.URGENT), any<Int>(), anyOrNull<Int>(), any<List<NotificationAction>>(), anyOrNull<() -> Boolean>())
    }

    @Test
    fun `a notice asked a sound for is an alarm with that sound`() {
        notices.addNotificationWithSound(Notification.PUMP_UNREACHABLE, "no answer", Notification.URGENT, 42)

        verify(notificationManager).post(eq(NotificationId.PUMP_UNREACHABLE), eq("no answer"), eq(NotificationLevel.URGENT), any<Int>(), eq(42), any<List<NotificationAction>>(), anyOrNull<() -> Boolean>())
    }

    @Test
    fun `taking a notice away dismisses it in the notification manager`() {
        // AAPS 4.0 does not listen for the old bus event: without this the notice would stay for ever.
        notices.dismiss(Notification.PUMP_SUSPENDED)

        verify(notificationManager).dismiss(NotificationId.PUMP_SUSPENDED)
    }
}
