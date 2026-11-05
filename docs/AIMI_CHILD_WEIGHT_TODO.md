# AIMI and the weight of a child: to fix

Status: **open, critical for a child below 40 kg**. Found on 2026-11-05 while reading AIMI
(`dev_OAPSAIMI` snapshot `fd426643`). Nothing here is changed yet. Every change below is a change to
the AIMI algorithm, not to the ATC3 driver: it needs tests and should be discussed with the AIMI
author before it is used on a person.

## The setting

- `DoubleKey.OApsAIMIweight` (`key_aimiweight`): default **50 kg**, range 1–200 kg
  (`core/keys/.../DoubleKey.kt`). A child whose weight is not entered is taken as 50 kg.

## Where the weight is used

All paths below are under `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/`.

| Place | What it does with the weight | Problem for a child |
|---|---|---|
| `autodrive/models/AutoDriveModels.kt` (`AutoDriveState.createSafe`) | `patientWeightKg.coerceIn(40.0, 250.0)`; the constructor `require`s 40..250 | **A child below 40 kg is taken as 40 kg** in all of Autodrive. |
| `autodrive/controller/MpcController.kt` | dose cap per 5 min = `weight × OApsAIMIMpcInsulinUPerKgPerStep` (default 0.065 U/kg) | With the 40 kg floor, a 20 kg child gets **2.6 U instead of 1.3 U**: the cap is doubled. |
| `autodrive/estimator/ContinuousStateEstimator.kt` | `baseMaxRa = weight / 10 × 1.5`, `weightRaFactor = (75 / weight).coerceIn(0.84, 1.08)` | Uses the floored weight. |
| `pkpd/PkPdIntegration.kt` (`buildWeightKineticFactor`) | weight below 40 kg → factor 1.0 | The weight of a child is **not used** for insulin kinetics. |
| `safety/InsulinLoadGovernor.kt` (`physiologicalBudgetU`) | IOB budget = max(from TDD, from weight), **at least 4 U** (`8 U × 0.5`) | For a 20 kg child with TDD 15 U the formula gives about 2.2 U, but **4 U** is used: the brake comes late. |
| `DetermineBasalAIMI2.kt` (`tightSpiralSmbCapIobThresholdU`) | IOB threshold from TDD and weight, kept in **3–20 U** | 3 U floor, whatever the weight. |
| `UndeclaredCobEstimator.kt` | carb cap = weight × g/kg | Uses the real weight: safer for a child. OK. |
| `DetermineBasalAIMI2.kt` (`basalaimi = tdd7Days / weight`) | real weight | OK. |

## What to do now, before any fix

1. Enter the child's real weight in AIMI. Never leave the 50 kg default.
2. Set Max IOB, Max SMB and the maximum basal for the child, low, and raise them slowly. These
   limits do not depend on the weight and are what really holds the doses.
3. Keep the limits set in the Apex pump itself; the ATC3 driver keeps to them.
4. Do not turn on Autodrive while the child is below 40 kg.

## Fix to work on (proposal, not done)

- Remove the 40 kg floor in `AutoDriveState.createSafe` and in `PkPdIntegration`, or lower it to a
  child weight with a clear lower bound (for example 10 kg).
- Make the IOB floors (`InsulinLoadGovernor`, tight spiral) scale with TDD and weight instead of
  fixed 4 U / 3 U, never above what TDD and weight give.
- Do not default the weight to 50 kg: ask for it, and block Autodrive until it is entered.
- Add unit tests with child values (for example 15 kg / TDD 10 U, 25 kg / TDD 18 U, 35 kg / TDD 25 U)
  that check every cap above is not larger than for the real weight.
