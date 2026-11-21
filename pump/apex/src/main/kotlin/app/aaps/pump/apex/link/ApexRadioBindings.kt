package app.aaps.pump.apex.link

import android.content.Context
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.pump.apex.trace.ApexTrace
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/**
 * Our addition (docs/OUR_CHANGES.md). Device tests only: what makes the radio instead of the real [ApexBLE]. A test
 * sets it before the app's graph is made (the radio is made once per graph) and clears it when it ends. The app
 * never sets it, so there it is null and the real radio is used.
 */
object ApexRadioSwap {

    @Volatile var make: ((AAPSLogger, Context, ApexTrace) -> ApexBLE)? = null
}

/** The one radio of the driver: the real one, or what a device test put in [ApexRadioSwap]. */
@ContributesTo(AppScope::class)
@BindingContainer
object ApexRadioBindings {

    @Provides
    @SingleIn(AppScope::class)
    fun radio(aapsLogger: AAPSLogger, context: Context, trace: ApexTrace): ApexBLE =
        ApexRadioSwap.make?.invoke(aapsLogger, context, trace) ?: ApexBLE(aapsLogger, context, trace)
}
