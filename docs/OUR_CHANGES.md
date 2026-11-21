# Our changes on top of blaqone AAPS-autoISF

Branch `autoisf-atc3` is built on the real history of blaqone/AAPS-autoISF `aaps4-autoisf`, so an update
from blaqone comes in by a normal `git merge`. Keep our code in our own modules and keep edits in
blaqone files small and listed here. When a merge touches one of these places, check it by hand and run
the tests named next to it.

## Our own modules (blaqone never touches them)
- `pump/atc3` - ATC3 (Apex TruCare III) pump driver. Tests: `:pump:atc3:testDebugUnitTest`.
- `pump/atc3-emulator` - virtual ATC3 pump for tests only (excluded from the app). Its control API (`PumpEmulatorControl`, the official AAPS emulator shape) lives in the module (`app.aaps.pump.atc3.emulator.control`), blaqone has none.
- `tools/atc3_emulator_check.sh` - emulator check used by CI.
- `sibionics-core/` - NOT part of the AAPS build: a small plain Kotlin project with the Sibionics code taken from
  JugglucoNG (GPL-3.0, see its README) and its tests. It moves into AAPS only when the source plugin is made.
- `.github/workflows/autoisf-atc3-ci.yml`, `.github/workflows/upstream-check.yml` - our CI.

- `ui/.../overview/ring/` - the "Кольцо" overview: ring, period numbers, next hour, plain "why" of oref/AutoISF decisions (`RingWhyMapper`, tests in `ui/src/commonTest/.../ring`).
- `plugins/learner` - the learner, first part: basal card every 4 days at 06:00 (`BasalCardEngine`, the rules tested on the body model), proposal with "Внести" / "Отказаться"; apply edits the active profile in place and switches to it at 100 %.
- `plugins/sibionics` - native Sibionics 2 source + its calibration plugin; compiles `sibionics-core/` (JugglucoNG, GPL-3.0) in unchanged.

## Edits in blaqone files (keep this list complete)
| File | What | Test |
|---|---|---|
| `core/data/.../pump/defs/PumpType.kt` | `ATC3` entry, `Source.Atc3` | driver tests |
| `core/data/.../pump/defs/DoseStepSize.kt` | `Atc3` steps | driver tests |
| `core/data/.../pump/defs/ManufacturerType.kt` | `Atc3` | driver tests |
| `core/data/.../pump/defs/PumpCapability.kt` | `Atc3Capabilities` | driver tests |
| `database/impl/.../embedments/InterfaceIDs.kt` | `PumpType.ATC3` | driver tests |
| `database/persistence/.../PumpTypeExtension.kt` | ATC3 both ways | driver tests |
| `database/impl/.../SyncPumpTemporaryBasalTransaction.kt` | temp basal safeguard | `SyncPumpTemporaryBasalTransactionTest` |
| `core/data/.../model/SourceSensor.kt` | `SIBIONICS_NATIVE("SibionicsNative")` | sibionics job |
| `core/data/.../model/RawObservationSource.kt` | `SIBIONICS_NATIVE` in the one-minute set, like the other Sibionics | sibionics job |
| `database/impl/.../entities/GlucoseValue.kt` | `SIBIONICS_NATIVE` (stored by name, no migration) | `ConvertersSourceSensorTest` |
| `database/persistence/.../SourceSensorExtension.kt` | `SIBIONICS_NATIVE` both ways | `SourceSensorExtensionTest` |
| `core/keys/.../BooleanKey.kt` + `core/keys/src/androidMain/res/values/strings.xml` | `OverviewRingLayout` (default on), `OverviewRingShowGraphs` (default on: the big graphs under the ring) and their title/summary; Russian in `values-ru-rRU/strings_ring.xml` | `PreferenceKeySnapshotTest` |
| `app/src/testFull/resources/prefs-schema.txt` | `overview_ring_layout` | `PreferenceKeySnapshotTest` |
| `ui/.../search/BuiltInSearchables.kt` | the ring switch in Appearance | - |
| `ui/.../main/MainScreen.kt`, `ui/.../overview/OverviewScreen.kt` | read the switch, phone portrait -> `OverviewScreenRing` | `OverviewSizeSweepTest` |
| `ui/.../overview/OverviewChipsColumn.kt` | parameter `showIobCob` (default true): the ring screen hides the IOB/COB chips, they are at the ring | ring screenshot test |
| `ui/src/androidMain/res/values/strings.xml` | `ring_*` strings at the end; Russian in `values-ru-rRU/strings_ring.xml` | build |
| `settings.gradle` | include `:pump:atc3`, `:pump:atc3-emulator`, `:plugins:sibionics`, `:plugins:learner` | build |
| `app/build.gradle.kts` | `:pump:atc3-emulator` in `pumpExclusions`; full flavor depends on `:plugins:sibionics` and `:plugins:learner`; androidTest depends on `:pump:atc3` and `:pump:atc3-emulator`; `-PbenchApk` gives the full flavor the package suffix `.bench` and the name "AAPS bench" | build |
| `app/src/androidTest/.../di/Atc3EmulatedRadio.kt`, `.../e2e/Atc3EmulatorScenarioTest.kt` | our files in the app's device tests (radio swap through `Atc3RadioSwap`) | emulator job |
| `app/src/androidTest/.../ReplayApsResultsTest.kt` | blaqone bug: `activity_consoleLog = ""` added to the AutoISF call, or device tests do not compile | emulator job |
| `core/interfaces/.../aps/LoopNotes.kt` (new) + `core/objects/.../di/CoreObjectsGraph.kt` | one shared holder of plugin notes for the ring "why" (the learner writes, the ring reads) | `LoopNotesTest` |
| `*/res/values-ru-rRU/strings_ru_overlay.xml` (new files) | our Russian for strings that have no Russian yet (only names missing from the Crowdin `strings.xml`; if Crowdin adds the same name later, delete ours or the build fails with a duplicate) | app build |
| `plugins/sync/src/androidHostTest/.../IosClientNsRecordsTest.kt` (new) | the iOS client's real Nightscout records (carbs, profile switch) are taken when accepted, not when not | ring/sync test step |
| `core/nssdk/.../mapper/TreatmentMapper.kt` | keeps the record's `app` (who wrote it) | `IosClientNsRecordsTest` |
| `core/interfaces/.../notifications/NotificationId.kt` | `NS_IOS_CLIENT_SKIPPED`: own notice for a skipped iPhone client record (was `NS_MALFUNCTION`) | `IosClientNsRecordsTest` |
| `plugins/sync/.../nsclientV3/NsIncomingDataProcessor.kt` + `strings.xml` | a fresh record of the iPhone client (`AAPSClient-iOS`) that is not taken raises a notice (accept off / hardened mode), at most every 30 min | `IosClientNsRecordsTest` |
| `core/keys/.../BooleanNonKey.kt` + `prefs-schema.txt` | `OverviewRingPredictionOffDone`: the ring screen switches the big graph's prediction overlay off once (user can turn it on, PRED) | `PreferenceKeySnapshotTest` |
| `implementation/.../queue/CommandExecutor.kt` | taken from official AAPS dev 9221bf3385 (16.11): the connection limit counts from the last command, not from the start of the queue run (matters with a 3 min loop); drop when blaqone has it | queue tests in CI, ATC3 emulator scenarios |
| `core/interfaces/.../calibration/AddEntryResult.kt` + `implementation/.../bolus/MasterRecordWriter.kt` + `ui/.../calibrationDialog/CalibrationDialogViewModel.kt` + `core/ui` `strings.xml` | new rejection `TooFarFromSensor` (finger value more than 8 mmol/L from the sensor: a typing error); used by the Sibionics calibration | `SibionicsCalibrationPluginTest` |
| `ui/.../overview/chips/TbrChip.kt` | a temp basal of 0 (basal off) shows its rate "0,00" too, not only the down icon; the percent of the profile is always shown (100 % with no temp basal) | ring screenshots |
| `core/ui/.../compose/AapsRingTheme.kt` (new) + `ui/.../main/MainScreen.kt` | look of the «Кольцо» overview (cards and tiles like AIMI; light by day, dark navy by night, user 21.11): MainScreen is wrapped in `AapsRingThemeIf(ringLayout)` | ring screenshots |
| `.github/workflows/*` | blaqone workflows removed on this branch | - |

## Rules
- New features (learner, Sibionics source, ring, translation) go into new modules. A change in a blaqone
  file only as a small hook (an interface call), listed above, with a test that fails if the hook is lost.
- Russian translation stays an overlay layer, not edits of blaqone `values-ru` files.
- Update = run `upstream-check.yml`; merge only when it is green and the user agreed.
