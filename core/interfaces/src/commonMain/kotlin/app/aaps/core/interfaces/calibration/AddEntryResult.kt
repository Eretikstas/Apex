package app.aaps.core.interfaces.calibration

/**
 * Outcome of [Calibration.addEntry]. Lets callers distinguish "persisted" from
 * "rejected and ignored" so the user can be told why nothing happened.
 */
sealed interface AddEntryResult {

    data object Accepted : AddEntryResult

    sealed interface Rejected : AddEntryResult {

        /**
         * Glucose was changing too fast for a reliable pairing.
         *
         * Units are mg/dL per 5 minutes (matches `GlucoseStatus.shortAvgDelta`, which is the
         * average of consecutive 5-minute CGM deltas).
         *
         * @param deltaMgdlPer5Min the measured short-average delta in mg/dL / 5 min
         * @param thresholdMgdlPer5Min the plugin's threshold above which an entry is rejected
         */
        data class DeltaTooHigh(val deltaMgdlPer5Min: Double, val thresholdMgdlPer5Min: Double) : Rejected

        /** No CGM reading was available within the pair lookback window. */
        data object NoSensorPair : Rejected

        /** Inside the sensor warm-up window — entry would be ignored anyway. */
        data class InWarmUp(val warmUpEndsAt: Long) : Rejected

        /** No recorded sensor session, so the pairing cannot be scoped. */
        data object NoSession : Rejected

        /**
         * Our addition (docs/OUR_CHANGES.md). The finger value is too far from the sensor value to be a real
         * calibration (probably a typing error or a bad finger stick).
         *
         * @param fingerMgdl the finger value in mg/dL
         * @param sensorMgdl the sensor value it was paired with, in mg/dL
         */
        data class TooFarFromSensor(val fingerMgdl: Double, val sensorMgdl: Double) : Rejected
    }
}
