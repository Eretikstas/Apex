package app.aaps.plugins.learner

import app.aaps.core.keys.interfaces.Preferences
import org.json.JSONArray
import org.json.JSONObject

/** A basal proposal waiting for the user: the profile it is for, when it was made, and the hours that change. */
data class BasalProposal(val profileName: String, val madeAt: Long, val changes: List<BasalChange>)

/** A proposal of the percentage card: the profile percentage now, the one proposed, and what the card saw. */
data class PctProposal(val from: Int, val to: Int, val madeAt: Long, val highPct: Double, val lowPct: Double)

/** A CR block proposal: the profile, the block, when, the share of its meals ending low, the CR per hour now and proposed. */
data class CrProposal(val profileName: String, val block: String, val madeAt: Long, val lowPct: Double, val from: Map<Int, Double>, val to: Map<Int, Double>)

/** A change waiting for its verdict: [label] says what changed, [warned] = the early rollback note was given. */
data class PendingEval(val label: String, val at: Long, val before: EvalStats, val warned: Boolean = false)

/** A change with its verdict. */
data class DoneEval(val label: String, val at: Long, val judgedAt: Long, val eval: ChangeEval)

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

    /** The sensor hint is on (the user's switch on the learner screen). Off by default. */
    var sensorHints: Boolean
        get() = read().optBoolean(KEY_SENSOR_HINTS, false)
        set(value) = edit { put(KEY_SENSOR_HINTS, value) }

    /** The cannula hint is on (the user's switch on the learner screen). Off by default. */
    var cannulaHints: Boolean
        get() = read().optBoolean(KEY_CANNULA_HINTS, false)
        set(value) = edit { put(KEY_CANNULA_HINTS, value) }

    /** The change evaluation is shown (the user's switch). Off by default; the numbers are kept also when it is off. */
    var evalOn: Boolean
        get() = read().optBoolean(KEY_EVAL_ON, false)
        set(value) = edit { put(KEY_EVAL_ON, value) }

    /** A change waiting for its 3 day verdict: what it was, when, and the numbers of the 3 days before it. */
    var evalPending: PendingEval?
        get() = read().optJSONObject(KEY_EVAL_PENDING)?.let { o ->
            runCatching { PendingEval(o.getString("label"), o.getLong("at"), statsOf(o.getJSONObject("before")), o.optBoolean("warned", false)) }.getOrNull()
        }
        set(value) = edit {
            if (value == null) remove(KEY_EVAL_PENDING)
            else put(KEY_EVAL_PENDING, JSONObject().put("label", value.label).put("at", value.at).put("before", json(value.before)).put("warned", value.warned))
        }

    /** The last verdict. */
    var lastEval: DoneEval?
        get() = read().optJSONObject(KEY_EVAL_LAST)?.let { o ->
            runCatching {
                DoneEval(
                    o.getString("label"), o.getLong("at"), o.getLong("judgedAt"),
                    ChangeEval(statsOf(o.getJSONObject("before")), statsOf(o.getJSONObject("after")), EvalVerdict.valueOf(o.getString("verdict")), o.getBoolean("rollback"))
                )
            }.getOrNull()
        }
        set(value) = edit {
            if (value == null) remove(KEY_EVAL_LAST)
            else put(
                KEY_EVAL_LAST, JSONObject().put("label", value.label).put("at", value.at).put("judgedAt", value.judgedAt)
                    .put("before", json(value.eval.before)).put("after", json(value.eval.after))
                    .put("verdict", value.eval.verdict.name).put("rollback", value.eval.rollback)
            )
        }

    var crProposal: CrProposal?
        get() = read().optJSONObject(KEY_CR)?.let { o ->
            runCatching {
                fun map(j: JSONObject) = j.keys().asSequence().associate { it.toInt() to j.getDouble(it) }
                CrProposal(o.getString("profile"), o.getString("block"), o.getLong("madeAt"), o.getDouble("low"), map(o.getJSONObject("from")), map(o.getJSONObject("to")))
            }.getOrNull()
        }
        set(value) = edit {
            if (value == null) remove(KEY_CR)
            else put(
                KEY_CR, JSONObject().put("profile", value.profileName).put("block", value.block).put("madeAt", value.madeAt).put("low", value.lowPct)
                    .put("from", JSONObject(value.from.mapKeys { it.key.toString() })).put("to", JSONObject(value.to.mapKeys { it.key.toString() }))
            )
        }

    /** The last CR block card: per block name the share of meals ending low. */
    var lastCr: Map<String, Double>?
        get() = read().optJSONObject(KEY_LAST_CR)?.let { o -> o.keys().asSequence().associateWith { o.getDouble(it) } }
        set(value) = edit { if (value == null) remove(KEY_LAST_CR) else put(KEY_LAST_CR, JSONObject(value)) }

    /** The weekly digest is on (the user's switch). Off by default. */
    var digestOn: Boolean
        get() = read().optBoolean(KEY_DIGEST_ON, false)
        set(value) = edit { put(KEY_DIGEST_ON, value) }

    /** The last weekly digest and when it was made. */
    var lastDigest: Pair<Long, WeeklyDigest>?
        get() = read().optJSONObject(KEY_DIGEST)?.let { o ->
            runCatching {
                o.getLong("at") to WeeklyDigest(weekOf(o.getJSONObject("this")), weekOf(o.getJSONObject("last")), o.getInt("met"), o.getInt("goals"))
            }.getOrNull()
        }
        set(value) = edit {
            if (value == null) remove(KEY_DIGEST)
            else put(
                KEY_DIGEST, JSONObject().put("at", value.first).put("this", json(value.second.thisWeek)).put("last", json(value.second.lastWeek))
                    .put("met", value.second.goalsMet).put("goals", value.second.goals)
            )
        }

    private fun json(w: WeekStats) = JSONObject().put("tir", w.inRangePct).put("l39", w.low39Pct).put("l30", w.low30Pct).put("h10", w.high10Pct)
        .put("cv", w.cvPct).put("mean", w.meanMgdl).put("ins", w.insulinPerDay).put("hypo", w.hypoGPerDay).put("n", w.readings)

    private fun weekOf(o: JSONObject) = WeekStats(
        o.getDouble("tir"), o.getDouble("l39"), o.getDouble("l30"), o.getDouble("h10"), o.getDouble("cv"), o.getDouble("mean"),
        o.getDouble("ins"), o.getDouble("hypo"), o.getInt("n")
    )

    private fun json(s: EvalStats) = JSONObject().put("l39", s.low39Pct).put("l30", s.low30Pct).put("h10", s.high10Pct)
        .put("cv", s.cvPct).put("min", s.minMgdl).put("n", s.readings)

    private fun statsOf(o: JSONObject) = EvalStats(o.getDouble("l39"), o.getDouble("l30"), o.getDouble("h10"), o.getDouble("cv"), o.getDouble("min"), o.getInt("n"))

    private companion object {

        const val KEY_CR = "cr_proposal"
        const val KEY_LAST_CR = "last_cr"
        const val KEY_DIGEST_ON = "digest_on"
        const val KEY_DIGEST = "digest_last"
        const val KEY_EVAL_ON = "eval_on"
        const val KEY_EVAL_PENDING = "eval_pending"
        const val KEY_EVAL_LAST = "eval_last"
        const val KEY_CANNULA_HINTS = "cannula_hints"
        const val KEY_SENSOR_HINTS = "sensor_hints"
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
