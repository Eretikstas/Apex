package app.aaps.pump.atc3

/**
 * The notice codes the driver uses inside: an id of a notice and its level. They are mapped onto the
 * AAPS [app.aaps.core.interfaces.notifications.NotificationId] and
 * [app.aaps.core.interfaces.notifications.NotificationLevel] in `Atc3Notices`, the only place that talks
 * to the notification manager. The numbers are the old AAPS ones, kept so the driver code and its tests
 * stay the same on every AAPS base.
 */
object Notification {

    // Levels
    const val URGENT = 0
    const val NORMAL = 1
    const val LOW = 2
    const val INFO = 3

    // Ids
    const val PROFILE_SET_OK = 1
    const val FAILED_UPDATE_PROFILE = 6
    const val PUMP_ERROR = 15
    const val PUMP_UNREACHABLE = 26
    const val WRONG_PUMP_PASSWORD = 33
    const val INSIGHT_DATE_TIME_UPDATED = 47
    const val OVER_24H_TIME_CHANGE_REQUESTED = 54
    const val PUMP_SUSPENDED = 80
    const val BLUETOOTH_NOT_ENABLED = 82
    const val PUMP_WARNING = 87
    const val PUMP_SYNC_ERROR = 88
}
