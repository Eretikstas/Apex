package app.aaps.plugins.aps.openAPSAIMI

/**
 * The clock AIMI reads. In the app it is the system clock. A history replay sets [override] so that
 * every timer, cooldown and learner window of AIMI follows the replayed time, not the wall clock.
 */
object AimiReplayClock {

    @Volatile var override: (() -> Long)? = null

    /**
     * Lowest value of the learned reactivity (AIMI: 0.5). Only a replay changes it, to try a lower floor;
     * read once from AIMI_REPLAY_REACT_FLOOR.
     */
    val reactFloor: Double = System.getenv("AIMI_REPLAY_REACT_FLOOR")?.toDoubleOrNull()?.coerceIn(0.1, 0.5) ?: 0.5

    fun now(): Long = override?.invoke() ?: System.currentTimeMillis()
}
