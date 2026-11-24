# AutoISF weight audit — static source review (2026-11-24)

## Scope and provenance

Read-only review of Claude-owned branch `autoisf-atc3`. No app source, preference defaults, clinical settings, profile, pump, Nightscout, or workflow was changed. This is a static source audit, not a runtime or clinical validation.

Sources inspected:
- `plugins/aps/src/commonMain/kotlin/app/aaps/plugins/aps/openAPSAutoISF/OpenAPSAutoISFPlugin.kt`
- `core/keys/src/commonMain/kotlin/app/aaps/core/keys/DoubleKey.kt`
- `core/interfaces/src/commonMain/kotlin/app/aaps/core/interfaces/aps/OapsProfileAutoIsf.kt`
- `app/src/androidTest/assets/OpenAPSSMBAutoISF/determine-basal.js`
- `plugins/aps/src/androidHostTest/kotlin/app/aaps/plugins/aps/openAPSAutoISF/OpenAPSAutoISFPluginTest.kt`
- `plugins/aps/src/androidHostTest/kotlin/app/aaps/plugins/aps/openAPSAutoISF/DetermineBasalAutoISFTest.kt`
- `docs/AutoISF-v4-Guide.md`

## Preference inventory from DoubleKey.kt

All six weights are Double preferences, default to 0.0, and have a non-negative UI/key range. The exact key/range/default values are:

| Preference key | Default | Min–max | Formula role |
|---|---:|---:|---|
| `bgAccel_ISF_weight` | 0.0 | 0.0–1.0 | Acceleration branch |
| `bgBrake_ISF_weight` | 0.0 | 0.0–1.0 | Braking/deceleration branch |
| `pp_ISF_weight` | 0.0 | 0.0–0.15 | Positive post-prandial delta |
| `lower_ISFrange_weight` | 0.0 | 0.0–2.0 | Interpolation below/equal 100 mg/dL |
| `higher_ISFrange_weight` | 0.0 | 0.0–2.0 | Interpolation above 100 mg/dL |
| `dura_ISF_weight` | 0.0 | 0.0–3.0 | Sustained high-glucose duration |
| `autoISF_min` (limit, not weight) | 1.0 | 0.3–1.0 | Lower final factor bound |
| `autoISF_max` (limit, not weight) | 1.0 | 1.0–3.0 | Upper final factor bound |

The keys are marked `defaultedBySM=true`, use bidirectional Cold sync, and are exposed in the AutoISF settings sub-screen. The plugin reads them from Preferences on each invocation and passes them into `OapsProfileAutoIsf`. The profile model carries the six weights and min/max as doubles. The guide describes the weight controls and scene/automation actions, but specifically documents scene/automation adjustment of the acceleration weight (not all six).

## Formula trace and neutral behavior

### 1. Acceleration / braking

The parabola contribution is active only when glucose status exists, dynamic ISF is enabled, AutoISF weights are enabled, and the fit correlation is at least 0.9. Otherwise the whole weighted AutoISF path or this contribution is bypassed.

`fit_share = 10 * (corrSqu - 0.9)`; the source requires `corrSqu >= 0.9`. The selected weight depends on BG relative to target and the sign of `bgAcceleration`:
- Above target: positive acceleration selects `bgAccel_ISF_weight`; negative acceleration selects `bgBrake_ISF_weight`.
- Below target: positive acceleration (towards target) selects brake weight, with half contribution when acceleration > 1; negative acceleration (away from target) selects accel weight.
- If the parabola predicts a minimum below target within 30 minutes, the code uses `-bgBrake_ISF_weight` as the multiplier.
- `acce_ISF = 1 + bgAcceleration * cap_weight * selected_weight * fit_share`.

The brake sign inversion in the predicted-near-minimum branch is deliberate in the current Kotlin and JS sources. The raw acceleration is multiplied directly; this is not a unitless input unless the acceleration calculation's scale is fixed and documented. The settings key labels alone do not state that scale.

### 2. BG-range interpolation

The polygon produces a signed delta around 1.0; the selected range weight multiplies that delta:
- `xdata <= 100`: `lower_ISFrange_weight`
- `xdata > 100`: `higher_ISFrange_weight`
- `bg_ISF = 1 + interpolate(...)`

Because the default weight is 0.0, both range weights neutralize the polygon contribution at defaults. The mathematical neutral multiplier for the delta is 1.0, not 0.0. This distinction is important: 0.0 means “no BG-range contribution”; 1.0 means “full unscaled polygon contribution”. The key ranges allow up to 2.0.

### 3. Post-prandial delta

When the average BG is not below target+10 and `shortAvgDelta >= 0`:
`pp_ISF = 1 + max(0, delta * pp_ISF_weight)`.
Otherwise it is bypassed. Default 0.0 neutralizes it. Its coefficient multiplies a BG delta, so its numerical scale is inverse to the glucose unit used by `delta`; source currently uses mg/dL in the calculation path. The key's three-decimal display type is not itself a physical unit declaration.

### 4. Dura

When `duraISFminutes >= 10` and `duraISFaverage > target_bg`:
`dura_ISF = 1 + (duration_minutes / 60) * (dura_ISF_weight / target_bg) * (average_bg - target_bg)`.
Otherwise bypassed. Default 0.0 neutralizes the contribution. The expression is dimensionless only if the weight's implied units are defined consistently with target/average BG and duration in hours; the current preference key does not encode those units.

### 5. Final combination and limits

For low BG-range contribution (`bg_ISF < 1`), the source takes the minimum of BG and acceleration factors, with a special product when acceleration factor >1, then applies min/max and sensitivity/activity/temp-target modifiers and returns early. Otherwise, if any factor changed, it selects the maximum of dura, BG, acceleration and PP; if acceleration <1, it multiplies that selected factor by acceleration again. It then applies `autoISF_min/max` and the activity/exercise/resistance logic. ISF output is `sens / finalFactor`, rounded to one decimal and capped at 720 in the low-BG early-return branch.

Both min and max default to 1.0. With those defaults, any factor different from 1.0 is clamped to 1.0. The six weight defaults are also 0.0, so all six contributions are neutral by default.

## Kotlin vs JS source comparison

The core equations for acceleration, BG interpolation, PP, Dura and min/max limiting are present in both Kotlin and the bundled JS reference. The JS file is an Android test asset/reference; it is not proof that production execution invokes the JS copy.

A material branch divergence is visible in `withinISFlimits`:
- Kotlin applies `sensitivityRatio` for exercise, resistance, step activity and step inactivity branches.
- The JS reference's corresponding function has an explicit high-temp-target exercise branch, then the generic `liftISF >= 1` / `else` logic; it does not show the Kotlin resistance/activity/inactivity branches.
This requires Claude to classify the difference as intentional or stale-reference behavior and add a parity test or explicit comment. Do not silently “fix” either implementation.

Other Kotlin hardening visible in the inspected path: it rejects non-finite/non-positive base ISF, carb ratio, autosens ratio and variable ISF before invoking the basal algorithm. That guard does not itself validate each weight's finiteness/range.

## Existing test coverage and gaps

Existing plugin tests cover:
- AutoISF disabled / weights disabled / missing glucose-status early returns.
- A limited acceleration example.
- A low/high interpolation example.
- Min/max limit behavior.
- SMB mode and delivery ratio behavior.

The current tests do not constitute an isolated six-weight matrix. Missing explicit cases:
1. Each of the six weights at 0, a mid-range value, and its maximum, with all other inputs fixed.
2. Separate positive/negative acceleration branches, below/above target, and the predicted-minimum brake sign inversion.
3. Correlation exactly 0.9 and just below/above it.
4. Range boundary exactly 100 mg/dL and immediately either side; neutral 0.0 versus full-scale 1.0 versus max 2.0.
5. PP gate boundaries: target+10 and `shortAvgDelta = 0`; negative and positive delta.
6. Dura boundaries: 9.999/10 minutes and average exactly at/just above target.
7. Final min/max boundaries, equality, reversed/corrupt bounds, and factor interactions (acce×brake, BG-range×acce, PP×Dura, each factor with min/max).
8. NaN, +Infinity, -Infinity, and out-of-range persisted/imported preference values for every weight. The key metadata supplies UI bounds, but the inspected algorithm path has no explicit per-weight finite/range validation before formula use.
9. Scene/automation set/restore tests for the documented acceleration-weight action, including interruption/restart; the guide says values restore but the inspected plugin tests do not verify it.

## Risk-ranked findings for Claude

- **High — Kotlin/JS parity gap:** activity/resistance handling differs in final factor limiting. Establish whether JS is intentionally obsolete or should match Kotlin.
- **High — invalid weight values are not guarded at the formula boundary:** UI key bounds exist, but the algorithm path should be tested against imported/corrupt non-finite values rather than assuming UI validation always protects it.
- **Medium — weight units/scales are implicit:** acceleration, PP and Dura coefficients are used numerically without unit metadata in the key declarations. Document the expected input units/scales and add dimensional/boundary tests.
- **Medium — neutral value is not uniform:** five weight families use 0.0 to disable contributions; range interpolation's multiplier is mathematically neutral at 1.0, but its default 0.0 disables the polygon. UI descriptions must make that distinction clear.
- **Medium — telemetry parity:** `AutoIsfFactors` stores acce/BG/PP/Dura/final factors, but confirm the values shown in the chip/graph are exactly the factors actually applied after branch-specific clamping and sensitivity modifiers. In particular, the early low-BG return and `finiteOnly()` path deserve tests.
- **Low — scene/automation scope:** guide describes automation/scene support for acceleration weight only; verify whether that is the intended supported scope rather than assuming all weights are automatable.

## Proposed deterministic test matrix (report-only proposal)

For each weight independently, freeze the same `GlucoseStatusAutoIsf`, target, base ISF, sensitivity ratio and all other preferences. Assert intermediate factor and returned ISF. Then add pair tests:
- accel vs brake in rising/falling and target-relative branches;
- BG-range low/high weight × acceleration;
- PP × Dura under sustained post-meal rise;
- each weight × min/max clamps;
- Kotlin vs JS reference on the same fixtures for common branches.

Do not derive patient-specific settings or recommend weight changes from these tests. No implementation change is proposed in this audit.

## Conclusion

This static pass identifies the formulas, preference defaults/ranges, neutral semantics, current test gaps, and one concrete Kotlin/JS branch difference. It does not establish runtime behavior, automation restoration, or clinical safety. Claude should review the source-linked findings and either confirm the JS divergence is intentional or add a parity test. Any formula/default/UI modification remains outside this report and requires separate explicit approval.
