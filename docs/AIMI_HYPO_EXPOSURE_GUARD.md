# AIMI hypo history guard

Status: **in code since 2026-11-06, mode "watch only" by default.** Not checked on a phone yet.
It is a change to the AIMI algorithm (not to the ATC3 driver). Idea and numbers from the
auto-configuration of Boost V5 (tim2000s/Boost-in-AAPS_3.4, `BoostV5AutoConfig.kt`,
`AggressionBudget.kt`).

## Why

AIMI reacts to a low of the last hours, but has no long memory of lows. In the 7 days to
2026-11-06 the time below 70 mg/dL (3.9 mmol/L) was 11.3 % (target under 4 %) and below 54 mg/dL
(3.0 mmol/L) 1.0 % (target under 1 %).

## What it does

Once an hour it reads the last 14 days of glucose from AAPS (at least 7 days and 1500 readings,
else it does nothing) and sets a level:

| Level | When | Asks for |
|---|---|---|
| Normal | below 70 ≤ 4 % and below 54 ≤ 1 % | nothing |
| Above target | below 70 > 4 % or below 54 > 1 % | extra insulin ×0.92 |
| Hypo-prone | below 70 > 6 % or below 54 > 1.5 % | extra insulin ×0.85, extra boosts off, learned settings only gentler |

What "extra insulin" means, and where it is applied (all under
`plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/`):

- **SMB** chosen by the algorithm: `DetermineBasalAIMI2.kt`, finalize step (next to the effort
  protection) and the AI auditor dose in `applySmbUnits`. Meal advisor, meal modes and other
  doses the user asked for are not changed.
- **Autodrive dose cap per 5 min**: `AutoDriveState.hypoExposureScale` → `MpcController`.
- **Adaptive basal**: the part above ×1.0 is scaled; hypo-prone removes it (×1.0).
- **HighBG PKPD boost (SMB ×1.2)**: off when hypo-prone (`SmbInstructionExecutor`).
- **Learned factors** (unified reactivity in SMB factors and in the basal of the old modes, learned
  adaptive basal): when hypo-prone a learned factor above 1.0 is used as 1.0.

Never touched: a lower basal, a suspend, the hypo guards, the profile.

Not covered: the "brain factor" in `OpenAPSAIMIPlugin` (autosens ratio / reactivity) — its
direction is not clear from the code, so it is left as it is.

## Setting

AIMI settings → Protection: **Hypo history guard** (`IntKey.OApsAIMIHypoExposureGuardMode`):
0 off, 1 **watch only** (default: writes `🩸 HYPO_EXPOSURE …` in the log, changes nothing),
2 on. When on, the log shows `🩸 HYPO_EXPOSURE_SMB`, `🩸 HYPO_EXPOSURE_BASAL` and the reason
shows `🩸hypo history×0.85`.

## Code

- `safety/HypoExposureGuard.kt`: pure logic (levels, scaling).
- `safety/HypoExposureMonitor.kt`: reads the history once an hour, from the plugin before each decision.
- Tests: `HypoExposureGuardTest`, `MpcControllerCalculateOptimalDoseTest` (cap with the guard).

## Plan

1. Some days in "watch only": compare the log with what happened (would it have cut doses before lows?).
2. Then "on", with low Max IOB / Max SMB, and watch the time below range in the evening stats.
3. After an AIMI update: check the places above again.
