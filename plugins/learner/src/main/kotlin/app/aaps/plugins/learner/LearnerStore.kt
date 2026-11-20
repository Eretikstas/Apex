package app.aaps.plugins.learner

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

/** A basal proposal waiting for the user: the profile it is for, when it was made, and the hours that change. */
data class BasalProposal(val profileName: String, val madeAt: Long, val changes: List<BasalChange>)

/** The learner's memory: the state of the basal card, when the last card ran, and a proposal waiting for an answer. */
class LearnerStore(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("learner", Context.MODE_PRIVATE)

    var lastCardAt: Long
        get() = prefs.getLong(KEY_LAST_CARD, 0L)
        set(value) = prefs.edit { putLong(KEY_LAST_CARD, value) }

    var cardState: BasalCardState
        get() {
            val dir = prefs.getString(KEY_DIR, null)?.split(',')?.mapNotNull { it.toIntOrNull() }
            val cnt = prefs.getString(KEY_COUNT, null)?.split(',')?.mapNotNull { it.toIntOrNull() }
            return if (dir?.size == 24 && cnt?.size == 24) BasalCardState(dir.toIntArray(), cnt.toIntArray()) else BasalCardState()
        }
        set(value) = prefs.edit {
            putString(KEY_DIR, value.lastDir.joinToString(","))
            putString(KEY_COUNT, value.count.joinToString(","))
        }

    var proposal: BasalProposal?
        get() = prefs.getString(KEY_PROPOSAL, null)?.let { text ->
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
        set(value) = prefs.edit {
            if (value == null) remove(KEY_PROPOSAL)
            else putString(
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

    /** The hours of the last card, for the screen: "need|source|windows" per hour. */
    var lastHours: List<HourResult>
        get() = prefs.getString(KEY_HOURS, null)?.split(';')?.mapNotNull { row ->
            val p = row.split('|')
            if (p.size != 5) null
            else HourResult(p[0].toInt(), p[1].toDouble(), p[2].toDoubleOrNull(), HourSource.valueOf(p[3]), p[4].toInt())
        } ?: emptyList()
        set(value) = prefs.edit {
            putString(KEY_HOURS, value.joinToString(";") { "${it.hour}|${it.profile}|${it.need ?: ""}|${it.source}|${it.windows}" })
        }

    private companion object {

        const val KEY_LAST_CARD = "last_card_at"
        const val KEY_DIR = "card_dir"
        const val KEY_COUNT = "card_count"
        const val KEY_PROPOSAL = "basal_proposal"
        const val KEY_HOURS = "last_hours"
    }
}
