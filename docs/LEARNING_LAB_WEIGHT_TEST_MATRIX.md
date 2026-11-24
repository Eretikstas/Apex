# Learning Lab — strict synthetic test matrix for six AutoISF weights

Target: AutoISF 3.2.1 on frozen app baseline `4.0.0-aisf3.2.1`.
Scope: software education and deterministic synthetic replay only. This is not a clinical tuning guide.

## Parameter boundary matrix

The only neutral baseline is all six weights = 0.0 with the AutoISF weight switch disabled/enabled as separate test conditions. The values below are software test points derived from the source-declared UI bounds, not clinical recommendations.

| Weight | Neutral | 25% of declared max | 50% of declared max | Boundary-only max |
|---|---:|---:|---:|---:|
| BG acceleration | 0.0 | 0.25 | 0.50 | 1.0 |
| BG braking | 0.0 | 0.25 | 0.50 | 1.0 |
| Lower BG range | 0.0 | 0.50 | 1.00 | 2.0 |
| Higher BG range | 0.0 | 0.50 | 1.00 | 2.0 |
| Post-prandial | 0.0 | 0.0375 | 0.075 | 0.15 |
| Duration | 0.0 | 0.75 | 1.50 | 3.0 |

The boundary-only maximum is used to test UI/storage bounds and numerical behavior. It must never be presented as a preferred operating value.

## One-factor-at-a-time scenarios

| ID | Synthetic condition | Weight under test | Required observation |
|---|---|---|---|
| W01 | All weights zero; AutoISF switch off | master switch | profile/Autosens ratio path remains in effect |
| W02 | Same input, switch on, all weights zero | all | no weight-derived factor deviates from 1.0 |
| W03 | Fit correlation below 0.9 | acceleration / braking | parabola adaptation bypass reason is logged |
| W04 | Fit correlation 0.9, then 1.0 | acceleration / braking | fit-share rises from 0 to 1 |
| W05 | Positive and negative acceleration, separately | acceleration | direction selects the intended weight path |
| W06 | Projected minimum below target within 30 min | braking | signed braking path is selected |
| W07 | x below 100; x=100; x above 100 | lower / higher range | lower and higher weights affect only their own sides; center remains zero |
| W08 | Average BG below target+10 | post-prandial | PP factor is bypassed |
| W09 | Average BG at/above target+10; shortAvgDelta negative | post-prandial | PP factor is bypassed |
| W10 | Same, shortAvgDelta nonnegative | post-prandial | PP factor follows max(0, delta × weight) |
| W11 | Duration below 10 min | duration | duration factor is bypassed |
| W12 | Duration at/above 10 min; average at/below target | duration | duration factor is bypassed |
| W13 | Duration at/above 10 min; average above target | duration | duration factor follows the documented formula |
| W14 | BG-range factor below 1.0 | range + acceleration | early-return branch skips PP and duration |
| W15 | BG-range factor at/above 1.0 | all | combined branch takes max factor and applies deceleration interaction |
| W16 | Values at min/max, just inside/outside, NaN, +Infinity, -Infinity | all | invalid persisted values are rejected/repaired before algorithm use |

## Interaction matrix

Only after one-factor tests pass:
1. Acceleration × braking: opposite signs and projection gate; prove there is no unintended double-counting.
2. BG-range × acceleration: verify the `bg_ISF < 1` early-return and lift interaction.
3. Post-prandial × duration: verify max-selection, not addition.
4. Acceleration below 1 × strongest other factor: verify multiplicative weakening.
5. All factors × `autoISF_min` / `autoISF_max`: verify final limiting and logged reason.
6. All factors × Autosens/activity/temporary-target ratio: verify `withinISFlimits()` precedence.

## Acceptance criteria

- Every case is deterministic and uses synthetic glucose/status/profile inputs.
- The test records inputs, six weights, gates, intermediate factors, final factor, and bypass/limit reason.
- No test writes to a real user profile, pump, CGM, or Nightscout.
- No UI maximum is called a recommended setting.
- Any NaN/Infinity behavior must be explicitly tested; ordinary range sliders alone are not proof of persisted-value validation.
- A passing unit test is not an emulator test or clinical validation.
