package app.aaps.pump.atc3

import androidx.annotation.RawRes
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.Notification
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationLevel
import app.aaps.core.interfaces.notifications.NotificationManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The driver's notices to the user, on the AAPS 4.0 notification system.
 *
 * The driver was written for AAPS 3.4, which posted a notice by an [Int] id and an [Int] level and
 * took it away with an event on the bus. AAPS 4.0 posts by [NotificationId] and [NotificationLevel]
 * and takes away with [NotificationManager.dismiss]; nothing listens for the old event any more.
 * This class keeps the driver's own calls as they were and does the translation in one place.
 *
 * Levels: in 3.4 an urgent notice was a red notice, with no sound unless asked for. In 4.0
 * [NotificationLevel.URGENT] is an alarm with sound on the whole screen. So an urgent notice of the
 * driver gets the level AAPS 4.0 itself gives that kind of notice, and never less than
 * [NotificationLevel.IMPORTANT]. A notice the driver asked a sound for is an alarm, as it was.
 */
@Singleton
class Atc3Notices @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val notificationManager: NotificationManager
) {

    fun addNotification(id: Int, text: String, level: Int) {
        val notice = noticeIdOf(id)
        notificationManager.post(notice, text, level = levelOf(level, notice))
    }

    fun addNotificationValidFor(id: Int, text: String, level: Int, validMinutes: Int) {
        val notice = noticeIdOf(id)
        notificationManager.post(notice, text, level = levelOf(level, notice), validMinutes = validMinutes)
    }

    /** [level] is taken as it comes: a notice asked a sound for was an alarm in 3.4, and only an alarm sounds in 4.0. */
    @Suppress("UNUSED_PARAMETER")
    fun addNotificationWithSound(id: Int, text: String, level: Int, @RawRes soundId: Int?) {
        notificationManager.post(noticeIdOf(id), text, level = NotificationLevel.URGENT, soundRes = soundId)
    }

    fun dismiss(id: Int) {
        notificationManager.dismiss(noticeIdOf(id))
    }

    private fun noticeIdOf(id: Int): NotificationId =
        idOf(id) ?: NotificationId.PUMP_ERROR.also {
            // Every id the driver uses is in the table; one that is not is still shown, as an error.
            aapsLogger.error(LTag.PUMP, "ATC3: notification id $id has no AAPS 4.0 id, shown as a pump error")
        }

    companion object {

        /** The AAPS 4.0 id for each 3.4 id the driver uses, or null for one it does not. */
        fun idOf(id: Int): NotificationId? = when (id) {
            Notification.PUMP_ERROR                     -> NotificationId.PUMP_ERROR
            Notification.PUMP_WARNING                   -> NotificationId.PUMP_WARNING
            Notification.PUMP_SUSPENDED                 -> NotificationId.PUMP_SUSPENDED
            Notification.PUMP_SYNC_ERROR                -> NotificationId.PUMP_SYNC_ERROR
            Notification.PUMP_UNREACHABLE               -> NotificationId.PUMP_UNREACHABLE
            Notification.WRONG_PUMP_PASSWORD            -> NotificationId.WRONG_PUMP_PASSWORD
            Notification.BLUETOOTH_NOT_ENABLED          -> NotificationId.BLUETOOTH_NOT_ENABLED
            Notification.PROFILE_SET_OK                 -> NotificationId.PROFILE_SET_OK
            Notification.FAILED_UPDATE_PROFILE          -> NotificationId.FAILED_UPDATE_PROFILE
            Notification.INSIGHT_DATE_TIME_UPDATED      -> NotificationId.INSIGHT_DATE_TIME_UPDATED
            Notification.OVER_24H_TIME_CHANGE_REQUESTED -> NotificationId.OVER_24H_TIME_CHANGE_REQUESTED
            else                                        -> null
        }

        /** The 4.0 level for a 3.4 [level] on [notice], see the class notes. */
        fun levelOf(level: Int, notice: NotificationId): NotificationLevel = when (level) {
            Notification.URGENT ->
                if (notice.defaultLevel == NotificationLevel.URGENT) NotificationLevel.URGENT else NotificationLevel.IMPORTANT

            Notification.NORMAL -> NotificationLevel.NORMAL
            Notification.LOW    -> NotificationLevel.LOW
            Notification.INFO   -> NotificationLevel.INFO
            else                -> NotificationLevel.ANNOUNCEMENT
        }
    }
}
