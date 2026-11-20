# Sibionics core taken from JugglucoNG (not part of the AAPS build)

`src/main/kotlin/tk/glucodata/` and `src/test/` hold code taken unchanged from JugglucoNG
(https://github.com/ctqvva/JugglucoNG, commit 75ff85a, `Common/src/main/java/tk/glucodata/drivers/sibionics`),
licensed GPL-3.0 (see `LICENSE-JugglucoNG-GPL-3.0.txt`). The package name is kept so an update from JugglucoNG is a plain copy.
Not taken: the Bluetooth manager and the Juggluco storage, registry, notification and migration code, which depend on Juggluco
itself. Only change: `tk/glucodata/Log.kt` is a stand-in for Juggluco's logger.

This is a small plain Kotlin project on its own: it is not in the AAPS `settings.gradle`, nothing in the app
uses it. CI (`sibionics-ci.yml`) builds it and runs the JugglucoNG tests on the real sensor traces.

## The part we need (Sibionics 2, Stock algorithm)
Eva's sensor is a Sibionics 2 (JugglucoNG variant `SIBIONICS2`, protocol `V120`, algorithm `V116A`).
- `SibionicsConstants` - variants, `SIBIONICS2` (official life 22 days), protocol mode `V120`, sensor id and BLE name rules.
- `SibionicsProtocol` - RC4 frames, session key, auth / activation / time sync / data request / reset packets, `parseV120`.
- `SibionicsSensitivity` (in `SibionicsProtocol.kt`) - the sensitivity code from the sensor's short code.
- `v116a/SibionicsExactV116A` - the vendor's V116A algorithm, exact (raw current -> glucose), used in STOCK mode.
- `SibionicsAlgorithmContext` in STOCK mode (`SibionicsAlgorithm.kt`) - feeds V116A; its other modes (V115G, adaptive,
  balanced, responsive) are JugglucoNG extras we do not use. They stay only so the file is unchanged and its tests run.
- `SibionicsResetPolicy`, `SibionicsSessionPolicy`, `SibionicsVariantLock`, `SibionicsBattery` - reset near day 22,
  session rules, the variant once seen, battery level.
- `SibionicsSampleJournal`, `SibionicsReplayHarness` - sample bookkeeping and the replay used by the tests.
Not needed for us: `SibionicsExactV115GCore/Clip` (GS1/other variants), `adaptive2/*`, `Sibionics*Algorithm` other than STOCK.

The AAPS side (BG source plugin, Bluetooth link, calibration inside the source) comes later and will call only the classes above.
