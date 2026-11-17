package app.aaps.plugins.aps.openAPSAIMI

/**
 * The clock AIMI reads. In the app it is the system clock. A history replay sets [override] so that
 * every timer, cooldown and learner window of AIMI follows the replayed time, not the wall clock.
 */
object AimiReplayClock {

    @Volatile var override: (() -> Long)? = null

    fun now(): Long = override?.invoke() ?: System.currentTimeMillis()
}
