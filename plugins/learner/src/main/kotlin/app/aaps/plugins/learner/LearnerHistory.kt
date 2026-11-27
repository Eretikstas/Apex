package app.aaps.plugins.learner

import app.aaps.core.data.model.BS
import app.aaps.core.data.model.TE
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.profile.ProfileFunction
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import java.time.Instant
import java.time.ZoneId

/**
 * The last days of history as 5 minute steps for the learner: glucose, all insulin given (basal as it ran, boluses and
 * SMB, no priming), meal carbs and hypo carbs apart, and the cannula age.
 *
 * Hypo carbs: an entry of 12 g or less at glucose under 4.5 mmol/L (the family's hypo treatments are 4-6 g); any other
 * carb entry is a meal.
 */
@SingleIn(AppScope::class)
@Inject
class LearnerHistory(
    private val persistenceLayer: PersistenceLayer,
    private val profileFunction: ProfileFunction,
    private val iobCobCalculator: IobCobCalculator,
) {

    suspend fun steps(now: Long, days: Int, zone: ZoneId = ZoneId.systemDefault()): List<LearnerStep> {
        val step = 300_000L
        val end = now - now % step
        val start = end - days * 86_400_000L
        val bgs = persistenceLayer.getBgReadingsDataFromTimeToTime(start - 600_000L, end, true)
        val boluses = persistenceLayer.getBolusesFromTimeToTime(start, end, true).filter { it.type != BS.Type.PRIMING }
        val carbs = persistenceLayer.getCarbsFromTimeToTimeExpanded(start, end, true)
        val cannulaChanges = persistenceLayer.getTherapyEventDataFromToTime(start - 10 * 86_400_000L, end)
            .filter { it.type == TE.Type.CANNULA_CHANGE }.map { it.timestamp }.sorted()
        val n = ((end - start) / step).toInt()
        val bgAt = DoubleArray(n) { Double.NaN }
        var bi = 0
        for (k in 0 until n) {
            val t = start + k * step
            // The newest reading not later than t and not older than 10 minutes.
            while (bi + 1 < bgs.size && bgs[bi + 1].timestamp <= t) bi++
            if (bgs.isNotEmpty() && bgs[bi].timestamp <= t && t - bgs[bi].timestamp <= 600_000L) bgAt[k] = bgs[bi].value
        }
        val bolusU = DoubleArray(n)
        boluses.forEach { b -> ((b.timestamp - start) / step).toInt().takeIf { it in 0 until n }?.let { bolusU[it] += b.amount } }
        val mealG = DoubleArray(n)
        val hypoG = DoubleArray(n)
        carbs.forEach { c ->
            val k = ((c.timestamp - start) / step).toInt()
            if (k !in 0 until n || c.amount <= 0) return@forEach
            val bg = bgAt[k]
            if (c.amount <= HYPO_MAX_G && !bg.isNaN() && bg < HYPO_BG_MGDL) hypoG[k] += c.amount else mealG[k] += c.amount
        }
        val out = ArrayList<LearnerStep>(n)
        var hourProfileTime = -1L
        var profile = profileFunction.getProfile(start)
        for (k in 0 until n) {
            val t = start + k * step
            val hourStart = t - t % 3_600_000L
            if (hourStart != hourProfileTime) {
                profile = profileFunction.getProfile(t) ?: profile
                hourProfileTime = hourStart
            }
            val rate = profile?.let { p ->
                runCatching { iobCobCalculator.getBasalData(p, t) }.getOrNull()?.let { if (it.isTempBasalRunning) it.tempBasalAbsolute else it.basal }
            } ?: 0.0
            val z = Instant.ofEpochMilli(t).atZone(zone)
            val lastChange = cannulaChanges.lastOrNull { it <= t }
            out += LearnerStep(
                time = t, hour = z.hour, minute = z.minute,
                bgMgdl = bgAt[k].takeIf { !it.isNaN() },
                insulinU = rate * 5.0 / 60.0 + bolusU[k],
                mealG = mealG[k], hypoG = hypoG[k],
                cannulaAgeH = lastChange?.let { (t - it) / 3_600_000.0 }
            )
        }
        return out
    }

    /**
     * For the sensor hint: the last sensor change logged in AAPS within [SensorHintEngine.WATCH_MS] before [now] (null if
     * none), and the readings from 10 minutes before it up to [now], oldest first, as (time, mg/dL).
     */
    suspend fun sensorWatch(now: Long): Pair<Long?, List<Pair<Long, Double>>> {
        val change = persistenceLayer.getTherapyEventDataFromToTime(now - SensorHintEngine.WATCH_MS, now)
            .filter { it.type == TE.Type.SENSOR_CHANGE }.maxOfOrNull { it.timestamp } ?: return Pair(null, emptyList())
        val readings = persistenceLayer.getBgReadingsDataFromTimeToTime(change - 600_000L, now, true).map { it.timestamp to it.value }
        return Pair(change, readings)
    }

    /**
     * For the cannula hint: the last cannula change logged in AAPS within 10 days before [now] (null if none) and the
     * readings of the last [CannulaHintEngine.WINDOW_MS], as (time, mg/dL).
     */
    suspend fun cannulaWatch(now: Long): Pair<Long?, List<Pair<Long, Double>>> {
        val change = persistenceLayer.getTherapyEventDataFromToTime(now - 10 * 86_400_000L, now)
            .filter { it.type == TE.Type.CANNULA_CHANGE }.maxOfOrNull { it.timestamp } ?: return Pair(null, emptyList())
        val readings = persistenceLayer.getBgReadingsDataFromTimeToTime(now - CannulaHintEngine.WINDOW_MS, now, true).map { it.timestamp to it.value }
        return Pair(change, readings)
    }

    /** For the low meal hint: carb entries of the last 30 minutes with the sensor glucose at each (raw, as the family sees it). */
    suspend fun recentCarbs(now: Long): List<MealEntry> {
        val from = now - LowMealHintEngine.SHOW_MS
        val bgs = persistenceLayer.getBgReadingsDataFromTimeToTime(from - 600_000L, now, true)
        return persistenceLayer.getCarbsFromTimeToTimeExpanded(from, now, true).filter { it.amount > 0 }.map { c ->
            val before = bgs.lastOrNull { it.timestamp <= c.timestamp && c.timestamp - it.timestamp <= 600_000L }
            MealEntry(c.timestamp, c.amount, before?.value)
        }
    }

    /** Sensor changes logged in AAPS within [days] days before [now]. */
    suspend fun sensorChanges(now: Long, days: Int): List<Long> =
        persistenceLayer.getTherapyEventDataFromToTime(now - days * 86_400_000L, now).filter { it.type == TE.Type.SENSOR_CHANGE }.map { it.timestamp }

    private companion object {

        const val HYPO_MAX_G = 12.0
        const val HYPO_BG_MGDL = 81.0
    }
}
