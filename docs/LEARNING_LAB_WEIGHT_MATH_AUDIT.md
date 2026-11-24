# Learning Lab — AutoISF 3.2.1 weight mathematics audit

Baseline: `4.0.0-aisf3.2.1`, AutoISF `3.2.1`, frozen source `916fe1ff6d1cf360347a67d5971872f6c7b69127`.
Working branch: `aimi-learning-lab`.

This document describes code behavior for synthetic educational replay only. It does not recommend therapeutic settings.

## Source-declared preference bounds

| Weight | Preference key | Default | UI min–max |
|---|---|---:|---:|
| BG acceleration | `bgAccel_ISF_weight` | 0 | 0–1 |
| BG braking | `bgBrake_ISF_weight` | 0 | 0–1 |
| Lower BG range | `lower_ISFrange_weight` | 0 | 0–2 |
| Higher BG range | `higher_ISFrange_weight` | 0 | 0–2 |
| Post-prandial | `pp_ISF_weight` | 0 | 0–0.15 |
| Duration | `dura_ISF_weight` | 0 | 0–3 |

The master preference `openapsama_enable_autoISF` defaults to `false`. These bounds are UI validation limits, not validated safe or effective clinical ranges.

## Calculation path verified in OpenAPSAutoISFPlugin.autoISF()

Let `T` be the active target and `G` the current glucose. The code forms:

`bg_off = T + 10 - G`

and evaluates the glucose-range curve at:

`x = 100 - bg_off = G - T + 90`.

### 1. Lower / higher BG range weights

The piecewise curve uses knots:

- x: 50, 60, 80, 90, 100, 110, 150, 180, 200
- y: -0.5, -0.5, -0.3, -0.2, 0, 0, 0.5, 0.7, 0.7

The interpolated y is multiplied by `lower_ISFrange_weight` when x <= 100, otherwise by `higher_ISFrange_weight`. Then:

`bg_ISF = 1 + interpolate(x)`

The sign differs around x=100: the lower-range curve is non-positive, the higher-range curve non-negative. If `bg_ISF < 1`, the algorithm takes an early-return branch after combining it with the acceleration factor and applying `withinISFlimits()`; post-prandial and duration factors are not evaluated on that branch.

### 2. Acceleration / braking weights

The parabola fit must have `corrSqu >= 0.9`; otherwise this adaptation is bypassed. The fit confidence multiplier is:

`fit_share = 10 * (corrSqu - 0.9)`

The selected signed weight is chosen from acceleration direction and glucose/target context. The factor is:

`acce_ISF = 1 + bgAcceleration * cap_weight * selected_weight * fit_share`

Below target, the code can halve `cap_weight` for stronger positive acceleration. If a fitted minimum is projected within 30 minutes and below target, the braking weight is negated. In the final combination, when `acce_ISF < 1`, it can weaken the strongest other factor by multiplication. Thus acceleration and braking are context-dependent and are not simple interchangeable positive multipliers.

### 3. Post-prandial weight

This path is gated: `bg_off <= 0` and `shortAvgDelta >= 0`. When active:

`pp_ISF = 1 + max(0, delta * pp_ISF_weight)`

It is bypassed when average glucose is below `target + 10`, or when the short average delta is negative.

### 4. Duration weight

This path is bypassed when duration is under 10 minutes or the duration-average glucose is at/below target. Otherwise:

`dura_ISF = 1 + (dura05 / 60) * (dura_ISF_weight / target) * (avg05 - target)`

The code describes this as responding to sustained high glucose. This term can grow with both duration and distance above target; the final combined factor is subsequently limited by `autoISF_min` and `autoISF_max`.

## Persisted-value validation finding

A source-level audit found a concrete non-finite-value gap in this baseline:

- `PreferenceDoubleState.value` writes `value.coerceIn(key.min, key.max)`.
- Kotlin range clamping does not turn `NaN` into a finite in-range number; comparisons against NaN are false, so NaN can pass through unchanged.
- `PreferencesImpl.get(DoublePreferenceKey)` reads the stored double from the key-value store without an additional finite/range repair step.
- The AutoISF plugin reads the six weights from preferences for its calculations; the UI slider's nominal min/max is therefore not proof that a corrupted/imported persisted value is safe.

This is a software robustness finding, not evidence that a normal user can enter NaN through the visible slider. The lab must add regression coverage for NaN and ±Infinity at both the preference-state/storage boundary and the AutoISF calculation boundary before proposing a production fix. Do not change clinical behavior on this finding alone.

## Combination and test implications

When the algorithm reaches the combined branch, it takes the maximum of duration, BG-range, acceleration, and post-prandial factors. If acceleration is below 1, that maximum is multiplied by acceleration. The result is passed through `withinISFlimits()`, which also incorporates the autosens/activity/temporary-target sensitivity ratio.

The source test suite currently includes:
- `interpolateTest` with five curve checkpoints;
- three new synthetic unit tests verify independent lower/higher range-weight scaling, zero-weight neutrality, and the center-knot invariant;
- `withinISFLimitsTest` for lower/upper bounds and sensitivity-ratio cases;
- three `autoISF` tests for run autosens reuse, missing glucose status, and weights disabled;
- a broad `autoISFTest` integration-style test.

The current tests do not constitute a complete six-weight test matrix. One existing integration-style test injects an acceleration weight of 2.0 even though the preference's declared maximum is 1.0; this is a mock input outside the UI range and must not be treated as an endorsed or valid setting. Missing explicit coverage to add in the lab:
- each weight independently at zero, interior, and source-declared upper bound;
- activation/bypass gates for fit correlation, delta direction, duration threshold, and target-relative glucose;
- lower-branch early return versus combined branch;
- interactions: acceleration with braking, acceleration with BG-range, pp with duration, and all factors before min/max limiting;
- non-finite values (NaN and ±Infinity), invalid persisted values, and clamping/repair behavior;
- unit invariance for mg/dL and mmol/L inputs where applicable.

## Strict educational test policy

- Use all-zero weights as the neutral baseline.
- Change exactly one weight per experiment before testing interactions.
- Do not describe UI min/max as recommended values.
- Use deterministic synthetic traces and record input features, each intermediate factor, final factor, and bypass reason.
- Keep all experiments disconnected from real pump, CGM, patient profile, and therapy configuration.

## Input-validation finding: non-finite persisted values

A source-path audit found a validation gap that needs a dedicated fix/review before any broader testing:

- The six AutoISF weight getters in `OpenAPSAutoISFPlugin` read `Preferences.get(DoubleKey...)` directly.
- `PreferencesImpl.get(DoublePreferenceKey)` reads the stored double (or its default in simple mode); it does not check finiteness or the key's min/max.
- `PreferencesImpl.put(DoubleNonPreferenceKey)` and `putRemote(...)` write the supplied double directly to the key-value store.
- The Compose `PreferenceDoubleState` setter calls `coerceIn(key.min, key.max)`, but this alone is not a finite-number validator: NaN comparisons do not select either bound, so NaN can pass through that clamp operation.

Therefore UI slider bounds are not sufficient protection against corrupted, imported, or synchronized values. The AutoISF algorithm itself needs a reviewed finite-and-range guard for its six weights (and min/max factors), with a deterministic fallback and regression tests for NaN and both infinities. This lab audit does not change production calculation code; the finding is recorded for a separate, explicit hardening review.
