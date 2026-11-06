package app.aaps.plugins.aps.openAPSAIMI.safety

import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.interfaces.Preferences
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the [HypoExposureGuard] verdict: reads [HypoExposureGuard.LOOKBACK_DAYS] days of glucose
 * at most once an hour, from the loop's own call before each decision.
 *
 * Until the first read, or when a read fails, the verdict is neutral: the guard can only take
 * insulin away, so not knowing means doing nothing.
 */
@Singleton
class HypoExposureMonitor @Inject constructor(
    private val persistenceLayer: PersistenceLayer,
    private val dateUtil: DateUtil,
    private val preferences: Preferences,
    private val aapsLogger: AAPSLogger,
) {

    @Volatile var verdict: HypoExposureGuard.Verdict = HypoExposureGuard.Verdict.NEUTRAL
        private set

    @Volatile private var lastReadMs: Long = 0L

    val mode: HypoExposureGuard.Mode
        get() = HypoExposureGuard.Mode.of(preferences.get(IntKey.OApsAIMIHypoExposureGuardMode))

    /** The verdict as it acts on doses now: neutral unless the mode is [HypoExposureGuard.Mode.ON]. */
    val applied: HypoExposureGuard.Verdict
        get() = HypoExposureGuard.applied(verdict, mode)

    /** Read the glucose again when an hour has passed. Off: nothing is read, the verdict is neutral. */
    suspend fun refreshIfDue() {
        if (mode == HypoExposureGuard.Mode.OFF) {
            verdict = HypoExposureGuard.Verdict.NEUTRAL
            lastReadMs = 0L
            return
        }
        val now = dateUtil.now()
        if (lastReadMs != 0L && now - lastReadMs < REFRESH_MS) return
        lastReadMs = now
        try {
            val from = now - HypoExposureGuard.LOOKBACK_DAYS * DAY_MS
            val readings = persistenceLayer.getBgReadingsDataFromTimeToTime(from, now, true)
                .map { it.timestamp to it.value }
            val zone = TimeZone.getDefault()
            val exposure = HypoExposureGuard.exposureOf(readings) { (it + zone.getOffset(it)) / DAY_MS }
            verdict = HypoExposureGuard.verdictOf(exposure)
            aapsLogger.debug(LTag.APS, HypoExposureGuard.describe(verdict, mode))
        } catch (e: Exception) {
            verdict = HypoExposureGuard.Verdict.NEUTRAL
            aapsLogger.error(LTag.APS, "HYPO_EXPOSURE: could not read the glucose history", e)
        }
    }

    private companion object {

        const val DAY_MS = 24L * 60 * 60 * 1000
        const val REFRESH_MS = 60L * 60 * 1000
    }
}
