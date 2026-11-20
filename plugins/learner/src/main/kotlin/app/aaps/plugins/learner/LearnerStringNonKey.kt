package app.aaps.plugins.learner

import app.aaps.core.keys.interfaces.StringNonPreferenceKey

enum class LearnerStringNonKey(
    override val key: String,
    override val defaultValue: String,
    override val exportable: Boolean = false
) : StringNonPreferenceKey {

    /**
     * The learner's memory, one JSON document (see `LearnerStore`): the basal card state, the last card time, a proposal
     * waiting for an answer and the hours of the last card. Not exportable: it belongs to this phone's history.
     */
    State("learner_state", ""),
}
