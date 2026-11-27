package app.aaps.plugins.learner

import app.aaps.core.keys.interfaces.Preferences
import org.json.JSONArray
import org.json.JSONObject

/** A basal proposal waiting for the user: the profile it is for, when it was made, and the hours that change. */
data class BasalProposal(val profileName: String, val madeAt: Long, val changes: List<BasalChange>)

/** A proposal of the percentage card: the profile percentage now, the one proposed, and what the card saw. */
data class PctProposal(val from: Int, val to: Int, val madeAt: Long, val highPct: Double, val lowPct: Double)

/** A proposal for the AutoISF weight dura: the weight now, the weight proposed, and what the card saw. */
data class DuraProposal(val from: Double, val to: Double, val madeAt: Long, val flatHighPct: Double, val lowPct: Double)

/**
 * The learner's memory: the state of the basal card, when the last card ran, and a proposal waiting for an answer. One
 * document under [LearnerStringNonKey.State].
 */
class LearnerStore(private val preferences: Preferences) {

    // One document: a read-modify-write from two threads (the card and a button) must not lose a field.
    private val lock = Any()

    private fun read(): JSONObject = synchronized(lock) {
        runCatching { JSONObject(preferences.get(LearnerStringNonKey.State)) }.getOrElse { JSONObject() }
    }

    private fun edit(block: JSONObject.() -> Unit) = synchronized(lock) {
        val o = read()
        o.block()
        preferences.put(LearnerStringNonKey.State, o.toString())
    }

    var lastCardAt: Long
        get() = read().optLong(KEY_LAST_CARD, 0L)
        set(value) = edit { put(KEY_LAST_CARD, value) }

    var cardState: BasalCardState
        get() {
            val dir = read().optString(KEY_DIR).ifEmpty { null }?.split(',')?.mapNotNull { it.toIntOrNull() }
            val cnt = read().optString(KEY_COUNT).ifEmpty { null }?.split(',')?.mapNotNull { it.toIntOrNull() }
            return if (dir?.size == 24 && cnt?.size == 24) BasalCardState(dir.toIntArray(), cnt.toIntArray()) else BasalCardState()
        }
        set(value) = edit {
            put(KEY_DIR, value.lastDir.joinToString(","))
            put(KEY_COUNT, value.count.joinToString(","))
        }

    var proposal: BasalProposal?
        get() = read().optString(KEY_PROPOSAL).ifEmpty { null }?.let { text ->
            runCatching {
                val o = JSONObject(text)
                val a = o.getJSONArray("changes")
                BasalProposal(
                    profileName = o.getString("profile"),
                    madeAt = o.getLong("madeAt"),
                    changes = (0 until a.length()).map { i ->
                        a.getJSONObject(i).let { BasalChange(it.getInt("h"), it.getDouble("from"), it.getDouble("to")) }
                    }
                )
            }.getOrNull()
        }
        set(value) = edit {
            if (value == null) remove(KEY_PROPOSAL)
            else put(
                KEY_PROPOSAL,
                JSONObject()
                    .put("profile", value.profileName)
                    .put("madeAt", value.madeAt)
                    .put("changes", JSONArray().apply {
                        value.changes.forEach { put(JSONObject().put("h", it.hour).put("from", it.from).put("to", it.to)) }
                    })
                    .toString()
            )
        }

    /** When the user last applied a learner proposal (any kind): the next one waits for the 3-4 day evaluation. */
    var lastAppliedAt: Long
        get() = read().optLong(KEY_LAST_APPLIED, 0L)
        set(value) = edit { put(KEY_LAST_APPLIED, value) }

    var duraProposal: DuraProposal?
        get() = read().optJSONObject(KEY_DURA)?.let { o ->
            runCatching { DuraProposal(o.getDouble("from"), o.getDouble("to"), o.getLong("madeAt"), o.getDouble("flat"), o.getDouble("low")) }.getOrNull()
        }
        set(value) = edit {
            if (value == null) remove(KEY_DURA)
            else put(
                KEY_DURA,
                JSONObject().put("from", value.from).put("to", value.to).put("madeAt", value.madeAt).put("flat", value.flatHighPct).put("low", value.lowPct)
            )
        }

    /** The last dura card, for the screen: "flat|low" in percent, empty before the first card. */
    var lastDura: Pair<Double, Double>?
        get() = read().optString(KEY_LAST_DURA).ifEmpty { null }?.split('|')?.takeIf { it.size == 2 }?.let { p ->
            val flat = p[0].toDoubleOrNull()
            val low = p[1].toDoubleOrNull()
            if (flat != null && low != null) Pair(flat, low) else null
        }
        set(value) = edit { if (value == null) remove(KEY_LAST_DURA) else put(KEY_LAST_DURA, "${value.first}|${value.second}") }

    /** The hours of the last card, for the screen: "need|source|windows" per hour. */
    var lastHours: List<HourResult>
        get() = read().optString(KEY_HOURS).ifEmpty { null }?.split(';')?.mapNotNull { row ->
            val p = row.split('|')
            // A broken row (an older format) is dropped, never a crash.
            if (p.size != 5) null
            else runCatching { HourResult(p[0].toInt(), p[1].toDouble(), p[2].toDoubleOrNull(), HourSource.valueOf(p[3]), p[4].toInt()) }.getOrNull()
        } ?: emptyList()
        set(value) = edit {
            put(KEY_HOURS, value.joinToString(";") { "${it.hour}|${it.profile}|${it.need ?: ""}|${it.source}|${it.windows}" })
        }

    var pctProposal: PctProposal?
        get() = read().optJSONObject(KEY_PCT)?.let { o ->
            runCatching { PctProposal(o.getInt("from"), o.getInt("to"), o.getLong("madeAt"), o.getDouble("high"), o.getDouble("low")) }.getOrNull()
        }
        set(value) = edit {
            if (value == null) remove(KEY_PCT)
            else put(KEY_PCT, JSONObject().put("from", value.from).put("to", value.to).put("madeAt", value.madeAt).put("high", value.highPct).put("low", value.lowPct))
        }

    /** The last percentage card: highs, lows (percent) and whether it hinted at trying more than 100 %. */
    var lastPct: Triple<Double, Double, Boolean>?
        get() = read().optJSONObject(KEY_LAST_PCT)?.let { o ->
            runCatching { Triple(o.getDouble("high"), o.getDouble("low"), o.getBoolean("hint")) }.getOrNull()
        }
        set(value) = edit {
            if (value == null) remove(KEY_LAST_PCT)
            else put(KEY_LAST_PCT, JSONObject().put("high", value.first).put("low", value.second).put("hint", value.third))
        }

    /** The day (epoch day, local) the percentage card last ran, and when a percentage from it was last applied. */
    var lastPctDay: Long
        get() = read().optLong(KEY_PCT_DAY, -1L)
        set(value) = edit { put(KEY_PCT_DAY, value) }

    var lastPctAppliedAt: Long
        get() = read().optLong(KEY_PCT_APPLIED, 0L)
        set(value) = edit { put(KEY_PCT_APPLIED, value) }

    /** The rebound guard is on (the user's switch). Off by default. */
    var reboundOn: Boolean
        get() = read().optBoolean(KEY_REBOUND_ON, false)
        set(value) = edit { put(KEY_REBOUND_ON, value) }

    private companion object {

        const val KEY_REBOUND_ON = "rebound_on"

        const val KEY_LAST_CARD = "last_card_at"
        const val KEY_DIR = "card_dir"
        const val KEY_COUNT = "card_count"
        const val KEY_PROPOSAL = "basal_proposal"
        const val KEY_HOURS = "last_hours"
        const val KEY_LAST_APPLIED = "last_applied_at"
        const val KEY_DURA = "dura_proposal"
        const val KEY_LAST_DURA = "last_dura"
        const val KEY_PCT = "pct_proposal"
        const val KEY_LAST_PCT = "last_pct"
        const val KEY_PCT_DAY = "pct_day"
        const val KEY_PCT_APPLIED = "pct_applied_at"
    }
}
