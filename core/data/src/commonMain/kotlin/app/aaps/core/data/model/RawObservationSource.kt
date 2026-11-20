package app.aaps.core.data.model

/**
 * Sources whose observation may be fed to the temporal estimator at its own cadence instead of the
 * interpolated 5-minute grid.
 *
 * Deliberately separate from [advancedFilteringSupported]: that list also gates SMB and AutoISF's raw
 * fit, so adding a source there has wider effects than a source-specific estimator input needs.
 *
 * Sibionics reports a sensor-state-calibrated observation every minute; AiDex, Libre 2/3 and
 * Ottai/Syai also deliver one-minute readings. A sender that forwards only every fifth minute still
 * gets the 5-minute profile.
 */
fun SourceSensor.rawObservationEstimatorSupported(): Boolean = this in RAW_OBSERVATION_SOURCES

private val RAW_OBSERVATION_SOURCES = setOf(
    SourceSensor.SIBIONIC_GS1,
    SourceSensor.SIBIONIC_GS3,
    SourceSensor.SIBIONIC_UNKNOWN,
    SourceSensor.SIBIONICS_NATIVE,
    // AiDex broadcasts every minute; feeding the estimator its own readings avoids the 5-minute
    // bucket interpolation. Newer senders name it `AidexX`, older ones `GlucoRx Aidex`.
    SourceSensor.AIDEX,
    SourceSensor.AIDEX_X,
    // Libre 2 (also matches "Libre2 Native"), Libre 3, and Ottai/Syai (one sensor sold under two
    // brands, sent as "Syai Tag" or "Ottai") deliver one-minute readings through Juggluco and the
    // vendor apps.
    SourceSensor.LIBRE_2,
    SourceSensor.LIBRE_3,
    SourceSensor.SYAI_TAG,
)
