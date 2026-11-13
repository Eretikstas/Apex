package app.aaps.plugins.main.general.dashboard.ring

/**
 * What the loop did in its last decision, read from the numbers of the result (not from the text).
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

/** The reason family of the last decision, see docs/RING_SKIN.md. Checked in this order. */
enum class RingWhyFamily {
    NO_DATA,
    SAFETY_STOP,
    LOW,
    AFTER_LOW,
    ENOUGH_IOB,
    MEAL,
    RISE,
    ACTIVITY,
    HONEYMOON,
    NORMAL,
    UNKNOWN,
}

/**
 * The plain "why" of the last decision: the family, the action and the numbers the sentence needs.
 * The words are string resources in the UI; nothing here is shown as it is.
 *
 * @param limitWantedU the dose the loop wanted when a limit cut it, else null
 * @param limitGivenU the dose the limit allowed, else null
 */
data class RingWhy(
    val family: RingWhyFamily,
    val action: RingAction,
    val eventualMgdl: Double?,
    val limitWantedU: Double?,
    val limitGivenU: Double?,
    val limited: Boolean,
)

/**
 * Builds [RingWhy] from the last loop result. The reason text of AIMI changes between versions and mixes
 * languages, so only stable markers in it are used, and only to pick a family; the action and the numbers
 * come from the result's own fields.
 */
object RingWhyMapper {

    /** Markers per family, in the order they are checked. Lower case; the reason is lower-cased first. */
    internal val FAMILIES: List<Pair<RingWhyFamily, List<String>>> = listOf(
        RingWhyFamily.NO_DATA to listOf(
            "data stale", "bg data is too old", "cgm is calibrating", "cgm data is unchanged", "no gs", "no bg history",
        ),
        RingWhyFamily.SAFETY_STOP to listOf("safety halt", "safety tbr", "auditor rejected"),
        RingWhyFamily.LOW to listOf(
            "lgs", "hypo protection", "hard brake", "bg is dropping", "drop (δ", "predlow", "safety cut", "bg 80", "bg 90",
        ),
        RingWhyFamily.AFTER_LOW to listOf(
            "hyporecovery", "post_hypo_rebound", "post-hypo blend", "[hypo history]", "hypo history×", "hypo history x",
        ),
        RingWhyFamily.ENOUGH_IOB to listOf(
            "> maxiob", "highiob", "stack_cap", "iob surveillance", "meal mode rising: iob",
        ),
        RingWhyFamily.MEAL to listOf(
            "breakfast mode", "lunch mode", "dinner mode", "high carb mode", "snack mode", "meal mode", "fcl mode",
            "manual modes meal", "early meal", "meal/snack", "anticip", "🍽️fcl", "meal advisor", "post_hypo_meal", "slowcarb",
        ),
        RingWhyFamily.RISE to listOf(
            "smb (uam)", "hyper boost", "plateau", "boost => hyper", "drift terminator", "strong rise", "slow rise",
            "bg>180", "eventual bg>180", "hyperkicker", "hyper kicker", "postmealboost", "post-meal boost", "rise ceiling",
        ),
        RingWhyFamily.ACTIVITY to listOf("morning activity", "effort×", "effort x", "sport safety", "activity smb cap"),
        RingWhyFamily.HONEYMOON to listOf("honeymoon"),
        RingWhyFamily.NORMAL to listOf("eventual bg", "setting current basal", "temp basal"),
    )

    /**
     * Markers of a limit that cut the dose. Not "MaxSMB": AIMI names the ceiling in almost every SMB reason,
     * cut or not.
     */
    internal val LIMIT_MARKERS = listOf(
        "cap:", "limits:", "thyroid guard: cap", "endodampen", "coffee", "basal_terminal", "engine_bound",
    )

    private val CAP_NUMBERS = Regex("""(?:cap|limits):\s*([0-9]+[.,][0-9]+)\s*(?:→|->)\s*([0-9]+[.,][0-9]+)""")

    /**
     * @param reason the reason text of the last result, may be null or empty
     * @param smbUnits the micro bolus of the result, 0 when none
     * @param rateUph the temp basal rate of the result, null when the result sets none
     * @param durationMin the temp basal duration of the result
     * @param eventualMgdl the eventual BG of the result
     */
    fun map(reason: String?, smbUnits: Double, rateUph: Double?, durationMin: Int?, eventualMgdl: Double?): RingWhy {
        val text = reason.orEmpty().lowercase()
        val action = when {
            smbUnits >= 0.01 -> RingAction.Smb(smbUnits, rateUph, durationMin)
            rateUph != null && rateUph < 0.01 && (durationMin ?: 0) > 0 -> RingAction.ZeroBasal(durationMin ?: 0)
            rateUph != null && (durationMin ?: 0) > 0 -> RingAction.TempBasal(rateUph, durationMin ?: 0)
            else -> RingAction.NoChange
        }
        val family = FAMILIES.firstOrNull { (_, markers) -> markers.any { text.contains(it) } }?.first ?: RingWhyFamily.UNKNOWN
        val cap = CAP_NUMBERS.findAll(text).lastOrNull()
        val wanted = cap?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()
        val given = cap?.groupValues?.get(2)?.replace(',', '.')?.toDoubleOrNull()
        val capCut = wanted != null && given != null && given < wanted - 0.005
        return RingWhy(
            family = family,
            action = action,
            eventualMgdl = eventualMgdl,
            limitWantedU = if (capCut) wanted else null,
            limitGivenU = if (capCut) given else null,
            limited = capCut || LIMIT_MARKERS.any { text.contains(it) } && action is RingAction.Smb,
        )
    }
}
