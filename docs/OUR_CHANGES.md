# Our changes on top of blaqone AAPS-autoISF

Branch `autoisf-atc3` is built on the real history of blaqone/AAPS-autoISF `aaps4-autoisf`, so an update
from blaqone comes in by a normal `git merge`. Keep our code in our own modules and keep edits in
blaqone files small and listed here. When a merge touches one of these places, check it by hand and run
the tests named next to it.

## Our own modules (blaqone never touches them)
- `pump/atc3` - ATC3 (Apex TruCare III) pump driver. Tests: `:pump:atc3:testDebugUnitTest`.
- `pump/atc3-emulator` - virtual ATC3 pump for tests only (excluded from the app). Its control API (`PumpEmulatorControl`, the official AAPS emulator shape) lives in the module (`app.aaps.pump.atc3.emulator.control`), blaqone has none.
- `tools/atc3_emulator_check.sh` - emulator check used by CI.
- `.github/workflows/autoisf-atc3-ci.yml`, `.github/workflows/upstream-check.yml` - our CI.

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
| `settings.gradle` | include `:pump:atc3`, `:pump:atc3-emulator` | build |
| `app/build.gradle.kts` | `:pump:atc3-emulator` in `pumpExclusions`; androidTest depends on `:pump:atc3` and `:pump:atc3-emulator` | build |
| `app/src/androidTest/.../di/Atc3EmulatedRadio.kt`, `.../e2e/Atc3EmulatorScenarioTest.kt` | our files in the app's device tests (radio swap through `Atc3RadioSwap`) | emulator job |
| `.github/workflows/*` | blaqone workflows removed on this branch | - |

## Rules
- New features (learner, Sibionics source, ring, translation) go into new modules. A change in a blaqone
  file only as a small hook (an interface call), listed above, with a test that fails if the hook is lost.
- Russian translation stays an overlay layer, not edits of blaqone `values-ru` files.
- Update = run `upstream-check.yml`; merge only when it is green and the user agreed.
