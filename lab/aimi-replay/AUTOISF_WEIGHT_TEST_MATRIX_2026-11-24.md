# AutoISF weight validation matrix — laboratory handoff (2026-11-24)

## Scope and safety boundary

This is a test specification only. It does not modify AutoISF formulas, defaults, preference values, UI, automation behavior, profile, pump, CGM, Nightscout, or the Claude-owned `autoisf-atc3` branch. Tests must run only with deterministic synthetic fixtures and a test profile. No patient-specific weight recommendations may be derived from the results.

Source audit: `AUTOISF_WEIGHT_AUDIT_2026-11-24.md`.

## Test fixture contract

Build one immutable baseline fixture and vary exactly one input dimension per isolated case:
- identical glucose status, target, base ISF, sensitivity ratio, activity/temp-target flags, IOB/COB, current temp and timestamp;
- master `ApsUseAutoIsfWeights=true` for weighted-path tests; separate master-off bypass test;
- all six weights zero except the weight under test;
- `autoISF_min=0.3`, `autoISF_max=3.0` for unconstrained contribution tests, then explicit clamp fixtures;
- assert intermediate raw factor, branch-selected factor, final factor, returned ISF, console errors and telemetry value;
- compare exact input/output fixture hashes for Kotlin and JS-reference cases.

Do not infer clinical safety from a passing mathematical test.

## A. Enablement and neutral controls

| ID | Inputs | Expected invariant |
|---|---|---|
| EN-01 | master switch off; arbitrary valid weights | weighted path bypassed; output equals documented non-weighted dynamic ISF path |
| EN-02 | master on; all six weights 0; min=max=1 | all weight contributions neutral; final weight factor exactly 1 |
| EN-03 | master on; all six weights 0; min=0.3, max=3 | no contribution from weights; final factor exactly 1 |
| EN-04 | master on; one nonzero weight; min=max=1 | final factor clamped to 1; raw/intermediate factor remains observable and finite |

## B. One-factor-at-a-time matrix

For each row, test weight at 0, 50% of key maximum, and key maximum; keep every other weight at zero. Include a negative and non-finite/corrupt persisted value in the defensive-input suite (section F), not as a normal supported setting.

| ID | Weight | Required branch fixtures | Assertions |
|---|---|---|---|
| W-ACC | `bgAccel_ISF_weight` (0..1) | above target/rising; above target/falling; below target/rising toward target; below target/falling away; correlation just below, exactly at, just above 0.9 | selected weight and sign match branch; factor finite; expected monotonic response within each fixed branch |
| W-BRK | `bgBrake_ISF_weight` (0..1) | same four BG/acceleration quadrants; predicted minimum below target within 30 min | brake branch selected correctly; predicted-minimum sign inversion is explicit and tested; no accidental sign flip outside that branch |
| W-PP | `pp_ISF_weight` (0..0.15) | shortAvgDelta negative/zero/positive; average BG below/at/above target+10 | PP bypasses below gate; zero delta contributes 1; positive delta is monotonic and never creates a negative contribution |
| W-LOW | `lower_ISFrange_weight` (0..2) | xdata immediately below, exactly at, immediately above 100 mg/dL | lower weight selected at <=100; interpolation continuous at boundary as specified; zero disables polygon contribution |
| W-HIGH | `higher_ISFrange_weight` (0..2) | xdata immediately below, exactly at, immediately above 100 mg/dL | higher weight selected only above 100; no off-by-one branch or discontinuity beyond formula's intended slope |
| W-DURA | `dura_ISF_weight` (0..3) | duration 9.999/10/10.001 min; average BG below/equal/above target | bypass before 10 min and at/below target; active branch is monotonic in duration, weight and BG excess for fixed target |

For each isolated fixture, assert both the intermediate factor and final returned ISF. Since ISF is inversely related to factor, assert the correct inverse direction and rounding/cap behavior separately.

## C. Pairwise interactions

| ID | Pair | Fixture | Required assertions |
|---|---|---|---|
| P-01 | accel × brake | all four BG/acceleration quadrants plus predicted-minimum branch | branch precedence and sign; one weight does not leak into the other branch |
| P-02 | lower-range × accel | BG below/equal 100, include accel factor above and below 1 | low-BG early-return combination matches source equation; no PP/Dura execution after early return |
| P-03 | higher-range × accel | BG above 100, include accel factor above and below 1 | max-selection/product rule matches source equation; no unintended double application |
| P-04 | PP × Dura | positive post-meal delta with sustained high BG | both raw factors recorded; selected combined factor matches current max/acceleration rule |
| P-05 | each weight × min clamp | raw factor below min, equal min, just above min | final factor respects min; telemetry distinguishes raw from clamped |
| P-06 | each weight × max clamp | raw factor below max, equal max, just above max | final factor respects max; telemetry distinguishes raw from clamped |
| P-07 | low-BG early return × PP/Dura | BG factor <1 and PP/Dura would otherwise be active | PP and Dura are skipped exactly as implementation specifies; explicit regression assertion |

## D. Boundary and invalid-limit suite

Test `autoISF_min` and `autoISF_max` independently:
- factor exactly at min/max and epsilon below/above;
- min=max=1 neutral clamp;
- valid broad range 0.3..3.0;
- min > max, min/max NaN, +Infinity, -Infinity, zero and negative values if corrupted storage/import can supply them.

Expected defensive behavior must be specified by the app owner before implementation. At minimum, no NaN/Infinity may escape into returned ISF, basal/SMB decision, or telemetry. Invalid bounds must fail closed or abort the weighted calculation; they must not silently produce a more insulin-aggressive factor.

## E. Kotlin / JS reference parity

Use shared deterministic JSON fixtures for branches represented in both sources. Compare:
- branch selection;
- raw accel/BG/PP/Dura factors;
- early-return selection;
- pre-limit and post-limit factor;
- returned ISF and rounding.

Known divergence to classify, not silently erase: Kotlin `withinISFlimits` contains resistance/activity/inactivity branches not represented equivalently in the bundled JS reference. Mark those fixtures as an intentional Kotlin-only expectation or update the reference only after Claude confirms ownership and intended semantics. Do not count a known intentional divergence as parity pass.

## F. Invalid persisted/imported weights

For each of the six weights, inject:
- NaN, +Infinity, -Infinity;
- negative value;
- value just above declared maximum;
- extremely large finite value.

Exercise the full path: preference deserialization -> plugin read -> profile construction -> formula -> final result/telemetry. UI slider validation is not sufficient evidence. Verify that no invalid value can invert a protective branch, bypass min/max, or create non-finite insulin factors. Define the expected safe fallback/abort explicitly with the app owner before code changes.

## G. Preference, automation and scene lifecycle

- Round-trip each supported preference through persistence and app restart.
- For documented `ActionSetAcceWeight`: valid min/mid/max, negative, above max, NaN and infinities; verify clamp/reject behavior and audit log.
- Scene apply -> normal exit -> restore previous acceleration weight.
- Scene apply -> process interruption/restart -> restore previous value or report a deterministic incomplete-restore state.
- Nested/overlapping scenes, duplicate apply, cancellation, and stale saved state.
- Verify the five other weights are not falsely advertised as automation-controlled unless corresponding actions/triggers are implemented.
- No automation test may write to a live profile or pump.

## H. Telemetry contract

For every branch, capture:
- selected branch and bypass reason;
- raw factor for each contributing weight;
- combined pre-limit factor;
- min/max-clamped factor;
- activity/temp-target-adjusted factor;
- final ISF;
- finite/non-finite guard result.

The UI chip, graph and persisted APS result must use the factor actually applied at the relevant stage. A raw acceleration factor must not be labelled as final ISF factor. Test the low-BG early return separately.

## I. Acceptance gates and execution order

1. Claude reviews this matrix and confirms intended invalid-input fallback and Kotlin/JS divergence classification.
2. Add isolated unit tests for EN and W-ACC..W-DURA.
3. Add P-01..P-07 pair/short-circuit tests.
4. Add invalid persisted/imported values and min/max corruption tests.
5. Add lifecycle/scene restore tests.
6. Add shared-fixture Kotlin/JS comparison for common branches.
7. Run the relevant AutoISF plugin unit/host tests and instrumentation tests; record exact commands, commit, run IDs, pass/fail counts and skipped tests.

A compile pass is not a formula test pass. A unit-test pass is not a device/emulator pass. A device/emulator pass is not clinical validation. Any formula, default, UI, or automation behavior change requires separate review and explicit user approval.
