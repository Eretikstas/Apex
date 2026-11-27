package app.aaps.plugins.learner

import kotlin.math.abs

/** The insulin curve of the running profile: its name, peak (minutes) and DIA (hours). */
data class CurveSnap(val label: String, val peakMin: Int, val diaH: Double)

/**
 * The learner's insulin curve watch (advisor, text only). An app update or a settings import can silently put another
 * insulin curve into the profile (another peak or DIA). The loop then sees a wrong IOB and gives too little or too much
 * insulin for days. The watch compares the curve of the running profile with the one it saw last time and reports any
 * change, so the family can check it the same day.
 */
object CurveWatchEngine {

    /** Whether [now] is another curve than [before] (another name, peak or DIA). No [before] (first look) = no change. */
    fun changed(before: CurveSnap?, now: CurveSnap): Boolean =
        before != null && (before.label != now.label || before.peakMin != now.peakMin || abs(before.diaH - now.diaH) >= 0.1)
}
