package app.aaps.plugins.sibionics

import app.aaps.core.keys.interfaces.StringNonPreferenceKey

enum class SibionicsStringNonKey(
    override val key: String,
    override val defaultValue: String,
    override val exportable: Boolean = false
) : StringNonPreferenceKey {

    /**
     * The sensor that is set up and its session state, one JSON document (see `SibionicsStore`). Not exportable: it is
     * device state, not a setting, and must not travel with a settings export to another phone.
     */
    State("sibionics_native_state", ""),
}
