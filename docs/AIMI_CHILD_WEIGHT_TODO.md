# AIMI and the weight of a child

Status: **limits fixed in code on 2026-11-06, waiting for the unit tests in CI and for a check on the
phone.** Found on 2026-11-05 while reading AIMI (`dev_OAPSAIMI` snapshot `fd426643`). These are
changes to the AIMI algorithm, not to the ATC3 driver. They should also go to the AIMI author.

The case they are made for: a child of **25 kg**, TDD about **17 U** a day (Nightscout analyser,
7 days to 2026-11-06: boluses and SMB about 10–13 U, basal about 6.5 U).

## The setting

- `DoubleKey.OApsAIMIweight` (`key_aimiweight`): default **50 kg**, range 1–200 kg. A child whose
  weight is not entered is taken as 50 kg. **Enter the real weight.** (Not changed in code.)

## What was changed

All paths are under `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/`. Every change
gives a child the same or **less** insulin; nothing changes for an adult with a known TDD.

| Place | Before | Now | For 25 kg / TDD 17 U |
|---|---|---|---|
| `autodrive/models/AutoDriveModels.kt` (`AutoDriveState`, constructor check and `createSafe`) | weight kept in 40–250 kg | `MIN_PATIENT_WEIGHT_KG` = **20**, 250 kg | Autodrive uses 25 kg, not 40 kg |
| → `autodrive/controller/MpcController.kt` (dose cap per 5 min = weight × 0.065 U/kg) | 40 kg × 0.065 = 2.6 U | follows the weight | **1.6 U** |
| → `autodrive/estimator/ContinuousStateEstimator.kt` (carb appearance ceiling = weight / 10 × 1.5) | 6.0 | follows the weight | **3.75** |
| `pkpd/PkPdIntegration.kt` (`buildWeightKineticFactor`) | 1.0 below 40 kg | `MIN_KINETIC_WEIGHT_KG` = **20** | factor 1.08: higher pkpd scale, so a higher ISF candidate and less insulin |
| `safety/InsulinLoadGovernor.kt` (`physiologicalBudgetU`) | at least 4 U; without a TDD 8 U | at least `PHYS_BUDGET_MIN_U` = **1 U**; without a TDD from the weight | **2.7 U** instead of 4 U: the basal boost brake comes sooner |
| `DetermineBasalAIMI2.kt` (`tightSpiralSmbCapIobThresholdU`) | at least 3 U; without a TDD 8 U | at least **1 U**; without a TDD from the weight | **2.7 U** instead of 3 U |
| `DetermineBasalAIMI2.kt` (`tightSpiralSmbCapEnergyThresholdU`) | at least 3 U | at least **1 U** | **2.2 U** instead of 3 U |

Both IOB levels now come from one function, `InsulinLoadGovernor.weightAndTddScaledIobU`.
Without a TDD, an adult of 75 kg still gets 8 U; the default weight of 50 kg gives 5.3 U (was 8 U).

Not changed: `UndeclaredCobEstimator` (carb cap = real weight × g/kg) and
`basalaimi = tdd7Days / weight` already used the real weight.

## Tests

- `plugins/aps/src/test/.../openAPSAIMI/ChildWeightLimitsTest.kt`: each limit above for 25 kg /
  17 U, and for the whole range of weight 20–75 kg and TDD 10–55 U: never above the old formula,
  growing with weight and TDD, the 20–250 kg bounds, and the adult values unchanged.
- `PkPdIntegrationTest`: a 25 kg child gets a factor above 1.0 and an ISF not lower than an adult.
- CI job `aimi-tests` in `.github/workflows/atc3-ci.yml` (commit message with `[aimi]`).

## Still to do

1. Run the tests in CI and look at the result.
2. On the phone: enter the real weight; keep Max IOB, Max SMB and the maximum basal low for the
   child (these limits do not depend on the weight and hold the doses most); keep the pump limits.
3. Watch the first days with Autodrive: the logs show `AdaptiveBasal IOB-budget x/2.7U` and the
   spiral thresholds.
4. After an AIMI update: check these places again (they may conflict) and send the changes to the
   AIMI author.
