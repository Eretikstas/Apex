package app.aaps.ui.compose.overview.ring

/**
 * What the loop did in its last decision, read from the numbers of the result (never from the text).
 */
sealed class RingAction {

    /** A micro bolus of [units]. A temp basal may run with it: [rateUph] for [durationMin], or null. */
    data class Smb(val units: Double, val rateUph: Double?, val durationMin: Int?) : RingAction()

    /** No insulin: a temp basal of 0 for [durationMin]. */
    data class ZeroBasal(val durationMin: Int) : RingAction()

    /** A temp basal of [rateUph] for [durationMin], without a micro bolus. */
    data class TempBasal(val rateUph: Double, val durationMin: Int) : RingAction()

    /** Nothing new: the running basal stays. */
    data object NoChange : RingAction()
}

/** The reason family of the last decision. Checked in this order. */
enum class RingWhyFamily {

    /** No usable glucose: old, unchanged or noisy data; the loop does nothing. */
    NO_DATA,

    /** Glucose is or will be low: the loop holds insulin back. */
    LOW,

    /** Enough insulin is already acting (max IOB, max bolus, waiting between micro boluses). */
    ENOUGH_IOB,

    /** Carbs on board are being covered. */
    MEAL,

    /** Glucose rises without carbs (UAM) or will end above target. */
    RISE,

    /** Glucose is where it should be: the basal stays or a neutral temp runs. */
    IN_RANGE,
    UNKNOWN,
}

/**
 * The plain "why" of the last decision: the family, the action and the numbers the sentence needs. The words are
 * string resources in the UI; nothing here is shown as it is.
 *
 * @param limitWantedU the micro bolus the loop wanted when a limit cut it, else null
 * @param limitGivenU the micro bolus the limit allowed, else null
 */
data class RingWhy(
    val family: RingWhyFamily,
    val action: RingAction,
    val eventualMgdl: Double?,
    val cobG: Double,
    val limitWantedU: Double?,
    val limitGivenU: Double?,
) {

    val limited: Boolean get() = limitWantedU != null
}

/**
 * Builds [RingWhy] from the last loop result of oref / AutoISF (DetermineBasalSMB, DetermineBasalAutoISF). Their reason
 * text is English and stable; only short markers in it are used, and only to pick a family. The action and the numbers
 * come from the result's own fields.
 */
object RingWhyMapper {

    /** Plain markers of two families; low glucose is found by the patterns below. Lower case. */
    internal val FAMILIES: List<Pair<RingWhyFamily, List<String>>> = listOf(
        RingWhyFamily.NO_DATA to listOf(
            "bg data is too old", "cgm is calibrating", "cgm data is unchanged", "aborting run", "noise is high",
        ),
        RingWhyFamily.ENOUGH_IOB to listOf(
            "> max_iob", "maxbolus", "to microbolus again",
        ),
    )

    private val LOW_EVENTUAL = Regex("eventual bg [0-9.]+ < [0-9.]+")
    private val MIN_GUARD_LOW = Regex("minguardbg [0-9.]+ < [0-9.]+")
    private val NEGATIVE_IOB = Regex("iob -?[0-9.]+ < -[0-9.]+")

    /**
     * @param reason the reason text of the result
     * @param smbUnits the micro bolus of the result (0 when none)
     * @param tempRequested whether the result asks for a temp basal
     * @param rateUph the asked temp basal rate
     * @param durationMin the asked temp basal duration
     * @param eventualMgdl the eventual glucose of the prediction, mg/dL
     * @param cobG carbs on board the decision saw
     * @param smbAllowedU the micro bolus left after the safety limits (constraints), or null when not known
     */
    fun map(
        reason: String,
        smbUnits: Double,
        tempRequested: Boolean,
        rateUph: Double,
        durationMin: Int,
        eventualMgdl: Double?,
        cobG: Double,
        smbAllowedU: Double? = null,
    ): RingWhy {
        val text = reason.lowercase()
        val action = when {
            smbUnits > 0.0                         -> RingAction.Smb(smbUnits, if (tempRequested) rateUph else null, if (tempRequested) durationMin else null)
            tempRequested && rateUph <= 0.0001     -> RingAction.ZeroBasal(durationMin)
            tempRequested                          -> RingAction.TempBasal(rateUph, durationMin)
            else                                   -> RingAction.NoChange
        }
        val limitedTo = smbAllowedU?.takeIf { smbUnits > 0.0 && it < smbUnits - 0.001 }
        val family = when {
            contains(text, RingWhyFamily.NO_DATA)                                          -> RingWhyFamily.NO_DATA
            action is RingAction.ZeroBasal                                                 -> RingWhyFamily.LOW
            LOW_EVENTUAL.containsMatchIn(text) || MIN_GUARD_LOW.containsMatchIn(text)      -> RingWhyFamily.LOW
            NEGATIVE_IOB.containsMatchIn(text) || text.contains("naive_eventualbg < 40")   -> RingWhyFamily.LOW
            eventualMgdl != null && eventualMgdl < LOW_MGDL && action !is RingAction.Smb   -> RingWhyFamily.LOW
            contains(text, RingWhyFamily.ENOUGH_IOB) && action !is RingAction.Smb          -> RingWhyFamily.ENOUGH_IOB
            limitedTo != null                                                              -> RingWhyFamily.ENOUGH_IOB
            (action is RingAction.Smb || action is RingAction.TempBasal) && cobG > 0.5     -> RingWhyFamily.MEAL
            action is RingAction.Smb || (action is RingAction.TempBasal && isHighTemp(text)) -> RingWhyFamily.RISE
            text.contains("in range") || text.contains("doing nothing") || text.contains("neutral temp") ||
                text.contains("no temp required") || text.contains("same as profile rate") -> RingWhyFamily.IN_RANGE
            action is RingAction.NoChange                                                  -> RingWhyFamily.IN_RANGE
            else                                                                           -> RingWhyFamily.UNKNOWN
        }
        return RingWhy(
            family = family,
            action = action,
            eventualMgdl = eventualMgdl,
            cobG = cobG,
            limitWantedU = limitedTo?.let { smbUnits },
            limitGivenU = limitedTo,
        )
    }

    private fun contains(text: String, family: RingWhyFamily): Boolean =
        FAMILIES.first { it.first == family }.second.any { text.contains(it) }

    /** A temp basal above the profile rate: oref writes "setting x U/hr" after an eventual BG above target. */
    private fun isHighTemp(text: String): Boolean = text.contains("eventual bg") && text.contains(">")

    private const val LOW_MGDL = 70.0
}
