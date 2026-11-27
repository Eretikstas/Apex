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
- `plugins/learner` - the learner, first part: basal card every 4 days at 06:00 (`BasalCardEngine`, the rules tested on the body model), proposal with "Внести" / "Отказаться"; apply edits the active profile in place and switches to it at 100 %. Sensor hint (`SensorHintEngine`, off by default, switch on the learner screen): after a logged sensor change a note in the ring "why" for the first 2 h and for a jump of 1.5 mmol/L or more in 5 min in the first 24 h; only text, values, alarms and the loop are not touched. Cannula hint (`CannulaHintEngine`, off by default): a cannula of 60 h or more and 40 % or more over 10 mmol/L in the last 6 h -> a note "probably the cannula"; only text. Change evaluation (`ChangeEvalEngine`, off by default): a change applied with "Внести" is judged 3 days later against the 3 days before it (better / same / worse) and the family's rollback rule gives an early note; nothing is undone by itself. Weekly digest (`WeeklyDigestEngine`, off by default): from 06:00 every 7 days, the last week against the week before and the family's goals met; only numbers.
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
| `app/src/androidTest/.../di/BaseTestApp.kt` (+ our `TestDatabase.kt`) | the graph opens a database file when `TestDatabase.file` is set (else in memory, as before): the restart scenarios keep AAPS's records through a restart | emulator job (ApexScenarioTest restart_*) |
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
| `core/keys/.../IntKey.kt` | status light defaults: `statuslights_res_critical` 40 U (was 10); sensor age warning 360 h / critical 480 h (15 / 20 days, was 216 / 240 h) | - |
| `pump/apex` (vendored, see pump/apex/UPSTREAM.md) + `settings.gradle` + `app/build.gradle.kts` + core `DoseStepSize`, `ManufacturerType`, `PumpCapability`, `PumpType`, `InterfaceIDs`, `PumpTypeExtension` | Apex v4 driver (russoturisto2026-create, 89d91d8) replaces our ATC3 in the app (user 21.11); its registration lines added next to the ATC3 ones; `:pump:atc3` stays only for its tests | `:pump:apex:testDebugUnitTest`, app build |
| `core/keys/.../StringKey.kt` + `core/keys/.../IntKey.kt` + `core/keys/src/androidMain/res/values/strings.xml` | ring look: `OverviewRingTheme` (auto / app / light / dark), `OverviewRingDarkFrom` 21 h and `OverviewRingDarkTo` 7 h (dark hours of "auto"), their titles and `ring_theme_*` names | `PreferenceKeySnapshotTest` |
| `app/src/testFull/resources/prefs-schema.txt` | also `overview_ring_show_graphs`, `overview_ring_theme`, `overview_ring_dark_from/to`, `overview_ring_prediction_off_done`, the `apex_*` keys, `learner_state`, `sibionics_native_state` | `PreferenceKeySnapshotTest` |
| `ui/.../search/BuiltInSearchables.kt` | also `OverviewRingShowGraphs`, `OverviewRingTheme`, `OverviewRingDarkFrom`, `OverviewRingDarkTo` in Appearance | - |
| `database/impl/.../SyncPumpTemporaryBasalTransactionTest.kt` | tests of our temp basal safeguard (closed / cut temp basals) | itself |
| `app/src/androidTest/.../di/ApexEmulatedRadio.kt`, `.../e2e/ApexScenarioTest.kt`, `.../e2e/VirtualBenchTest.kt`, `.../e2e/RealScreensTest.kt` (new) | our device tests: Apex v4 on the software pump (scenarios, virtual bench), real screens | emulator job (vbench, screens) |
| `ui/src/androidHostTest/.../main/MenuScreenshotTest.kt` (new) | screenshots of the menu with our entries | `:ui` host tests |
| `pump/apex/.../link/ApexBLE.kt` + new `ApexRadioBindings.kt` (our edits in the vendored v4 driver) | ApexBLE open and made by a provider, so a device test can put our software pump in place of the radio (`ApexRadioSwap`); the app never sets it | VirtualBenchTest |
| `pump/apex/.../manager/ApexManager.kt` (our edit in the vendored v4 driver) | basal profile and settings writes with a LOST answer read back instead of failing at once (our ATC3 findings 3 and 4, still in v4) | virtual pump scenarios (to port) |
| `.github/workflows/*` | blaqone workflows removed on this branch | - |

## Rules
- New features (learner, Sibionics source, ring, translation) go into new modules. A change in a blaqone
  file only as a small hook (an interface call), listed above, with a test that fails if the hook is lost.
- Russian translation stays an overlay layer, not edits of blaqone `values-ru` files.
- Update = run `upstream-check.yml`; merge only when it is green and the user agreed.
