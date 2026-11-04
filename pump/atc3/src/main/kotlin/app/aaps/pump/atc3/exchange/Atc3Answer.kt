package app.aaps.pump.atc3.exchange

/** How the pump answered a control command. */
enum class Atc3Answer { ACCEPTED, REFUSED, NONE }

/**
 * A control command's [answer], and whether the request was [written] to the pump at all. A request
 * that was written and got no answer may still have been carried out: only the answer was lost.
 */
data class Atc3ControlResult(val answer: Atc3Answer, val written: Boolean)
