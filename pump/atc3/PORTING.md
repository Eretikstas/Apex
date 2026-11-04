# ATC3 (Apex TruCare III) driver in AIMI 4.0

## Where the code comes from

- Driver: <https://github.com/russoturisto2026-create/aaps-apex-driver>, module `pump/atc3`,
  driver version `2026.10.12.1`, written for AAPS 3.4.2.6. Protocol description by the same
  authors: <https://github.com/russoturisto2026-create/apex-trucare-III-protocol>.
- Base: <https://github.com/MTR93600/OpenApsAIMI>, branch `dev_OAPSAIMI`, commit `fd42664`
  (version `4.0.0.0-dev.AIMI.211026`).
- See `DISCLAIMER.md` in this folder (from the driver repository).

The protocol, the history reconciliation, the bolus and temporary basal logic and the safety
checks are the driver's own code, unchanged. Only the places where AAPS 4.0 changed its
interfaces were changed.

## What changed for AAPS 4.0, and why

| Area | AAPS 3.4 | AAPS 4.0 | What the port does |
|---|---|---|---|
| Pump interface | blocking calls, `Double` amounts | `suspend` calls, `PumpInsulin` / `PumpRate`, `StateFlow` state | `runBlocking` bridges removed; state values mirrored as `StateFlow` in `Atc3PumpState` |
| `PumpSync` | blocking, `Double` | `suspend`, `PumpInsulin` / `PumpRate` | amounts wrapped where they are handed over |
| Command queue | `readStatus(reason, callback)` only queued the read | `readStatus(reason)` **waits** until the read has run | `Atc3Queue` only queues, as before. Waiting from inside a running command (after a bolus, on a time change) would have stopped the queue for good |
| Notices | `UiInteraction.addNotification(Int, …)`, removed with `EventDismissNotification` | `NotificationManager.post(NotificationId, …)` and `dismiss` | `Atc3Notices` translates. Nothing in 4.0 listens for the old event, so without it the driver's notices would never go away |
| Notice levels | "urgent" = red notice, no sound | `URGENT` = alarm with sound on the whole screen | urgent keeps the level AAPS 4.0 gives that notice, never below `IMPORTANT`; a notice with sound stays an alarm |
| New glucose, loop mode | `EventNewBG`, `EventRunningModeChange` | removed | watched with `persistenceLayer.observeChanges<GV>()` and `<RM>()` |
| Screen | fragment, scan activity, View preferences | Compose only | new Compose pump screen and pairing screen with the same steps; switches in `PreferenceSubScreenDef` |
| Registration | `PluginsListModule` in the app | Hilt module in the driver | `Atc3Module`, `@IntKey(1140)` |

## Added for safety

- The loop does not run, and the insulin concentration setting is not offered, unless the insulin
  is U100. The driver compares the AAPS journal with the pump's own count unit for unit; with any
  other concentration that comparison would be wrong.
- `isConfigured()`: no first status read before a pump is picked.

## Not ported yet

- Changing the pump's own maximum bolus, maximum basal and bolus speed from AAPS. These are shown
  (read only) on the pump screen and are set on the pump. Writing pump limits from the phone is
  left out on purpose until it can be tested on a pump.

## Core changes outside this module

- `PumpType.ATC3`, `ManufacturerType.Atc3`, `DoseStepSize.Atc3`, `PumpCapability.Atc3Capabilities`,
  `InterfaceIDs.PumpType.ATC3` and the converters, as in the driver repository. The AIMI `Apex`
  entries stay as they are; the AIMI `pump/apex` module stays out of the app build as before.
- `SyncPumpTemporaryBasalTransaction`: a temporary basal already cut by an end event can still be
  shortened by the pump (the pump's own account of what it delivered). Same change and same tests
  as the driver repository. This affects every pump driver.

## Tests

`.github/workflows/atc3-ci.yml` compiles the driver, runs all its unit tests and the tests of the
core change, and builds a debug APK. A green run is not proof that the driver works with a real
pump: test on a pump, open loop first.
