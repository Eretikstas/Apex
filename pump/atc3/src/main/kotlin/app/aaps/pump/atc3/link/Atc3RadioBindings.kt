package app.aaps.pump.atc3.link

import android.content.Context
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.pump.atc3.trace.Atc3Trace
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/**
 * Device tests only: what makes the radio instead of the real [Atc3BLE]. A test sets it before the app's graph
 * is made (the radio is made once per graph) and clears it when it ends. The app never sets it, so there it is
 * null and the real radio is used.
 */
object Atc3RadioSwap {

    @Volatile var make: ((AAPSLogger, Context, Atc3Trace) -> Atc3BLE)? = null
}

/** The one radio of the driver: the real one, or what a device test put in [Atc3RadioSwap]. */
@ContributesTo(AppScope::class)
@BindingContainer
object Atc3RadioBindings {

    @Provides
    @SingleIn(AppScope::class)
    fun radio(aapsLogger: AAPSLogger, context: Context, trace: Atc3Trace): Atc3BLE =
        Atc3RadioSwap.make?.invoke(aapsLogger, context, trace) ?: Atc3BLE(aapsLogger, context, trace)
}
