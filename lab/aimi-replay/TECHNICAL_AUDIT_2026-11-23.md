# AIMI Replay Technical Audit — 2026-11-23

## Scope and safety boundary

Review branch: `aimi-replay`

Source changes are confined to `HistoryReplayTest.kt` and replay-only diagnostics/scenario generation. No learner formula, clinical parameter, live AAPS profile, pump, or Nightscout setting was changed. No replay batch was launched. The history-replay workflow was skipped for each source commit.

Claude remains the final reviewer and owner of replay execution. This report records ChatGPT's source-level findings and changes; it does not replace Claude's review or Results section.

## Changes made

Source commits:

- `3e7792f5d2e345ff82af335e886f20caf834d5be` — add independent audit reconciliation guards, strict OUTDAY/GAP token parsing, and deterministic scenario generation.
- `b40050a4e7591908598e08189332a32d06993493` — harden sensor-gap timeline and recovery handling.
- `8fb7ae73561ec8bc193be34f8d24413d78711dde` — preserve the non-causal ISF path and extend token boundary tests.
- `037cca0c148eec99b470ee30e0df4a61a8d9b4d5` — prevent synthetic family logic from using stale sensor values after a gap.
- `76828a294ba8fa4f0c309cba20d24d3789c5f727` — count intentional no-glucose GAP ticks separately from genuine replay exceptions.
- `e3c5e6f9d66a467887d1898ae8c5b1fd72e56524` — exclude outcome-learning episodes that overlap the blackout or its 60-minute recovery window.
- `70b796b71e1df3925742bf379a4e5f6de7e5e788` — require `LACAUSE` whenever GAP is requested, so blackout/recovery is audited causally.

### Learning Audit counters

- ISF previously printed `candidates = accepted + rejected` by deriving candidates from those same two values. This was not an independent reconciliation check.
- ISF now increments an independent candidate counter while scanning correction events and asserts `candidates == accepted + rejected`. The insufficient-window pre-scan also has an explicit reconciliation guard.
- Advanced Basal, classic Basal, and each CR block now have fail-fast reconciliation checks. A replay stops if any card's independent candidate total does not equal accepted plus mutually exclusive rejection counts.
- Basal, ISF, and CR causal audit windows now recognize both explicit `sensorGap` flags and timestamp discontinuities greater than five minutes. Sensor-gap is given priority as the primary rejection reason.
- The ISF timestamp-gap check is gated by `LACAUSE`, preserving the non-causal comparison path.
- Existing CR rule remains unchanged: any SMB in the complete six-hour post-meal window rejects that meal episode.

### New synthetic scenarios

- `OUTDAY_<day>_<factor>`: day is one-based; factor must be finite and positive. It scales true basal need on that day only. Example high-need outlier: `OUTDAY_4_1.3`; low-need outlier: `OUTDAY_4_0.7`.
- `GAP_15`, `GAP_30`, `GAP_60`: exactly one token is allowed, the synthetic dataset must contain at least four days, and `LACAUSE` is mandatory. The blackout starts on day 4 at 02:00 in the synthetic UTC+3 clock.
- During GAP, sensor samples are omitted from the sensor history; replay rows omit BG rather than filling or interpolating it. The underlying body/noise simulation continues. The loop receives no glucose status during the blackout. Sensor-dependent hypo treatments and manual corrections are suppressed; meal bolus calculation uses target BG (no unavailable sensor correction) while retaining IOB accounting.
- The gap and the following 60-minute recovery period are marked as sensor-contaminated for causal audit. This allows CR, ISF, Basal and Dura filters to reject blackout/recovery windows.
- Reading/noise generation still consumes the same seeded random stream during a blackout; only the sample insertion is omitted.
- Intentional `no glucose` skips during the blackout are counted as `sensor-gap ticks intentionally skipped`, not as replay exceptions. Episodes used by the separate rebound/outcome learner are also marked contaminated if they overlap the blackout or 60-minute recovery and cannot update that learner.

### Unit-test coverage added

- Valid GAP values 15/30/60.
- Valid one-based OUTDAY boundaries and a factor below 1.
- Invalid day range, zero factor, too-short GAP dataset, unsupported GAP duration, and duplicate scenario tokens.
- Reconciliation helper accepts a balanced count and throws on a mismatch.

These tests are present in the replay test source. The configured GitHub workflow compiles test sources only; it does not execute these new unit tests.

## Static audit findings

### Candidate accounting and gates

- CR: every selected meal candidate follows one mutually exclusive rejection branch or increments accepted/used. The six-hour SMB exclusion remains in place. The new guard will catch a missed accounting branch during replay.
- ISF: the normal path now independently counts correction candidates; accepted episodes and each rejection reason must reconcile. The existing accepted-data gate remains five clean episodes and, when `LADAYS` is enabled, three distinct days.
- Basal: both implementations independently count candidate windows and reasons. The advanced implementation retains per-hour sample thresholds, tail thresholds and fill/estimate reporting; classic basal retains its per-hour three-sample threshold. The new guards validate totals but do not prove the learner's fill semantics; Claude's review of `LBMIN`, `LBTAILMIN`, `LBFILL` and `LBFILL2` is still required.
- Existing causal rules remain in the audit: old cannula is over 72 hours; sensor gap/recovery is excluded; hypo/rebound, meal, activity, illness and false-low windows are filtered according to the current specification.
- Low-cause labels include `fasting`, `meal_bolus`, `smb`, `rebound`, `ambiguous`, and `other`. Only fasting/meal-bolus lows are eligible for the percentage card; `other` remains ineligible. Confirm the `other` versus `ambiguous` label convention with Claude before changing it.

### Determinism

Source inspection found the synthetic generator's random streams seeded from `SEED` (including the derived streams `SEED+7`, `SEED+11`, and `SEED+13`). OUTDAY and GAP do not introduce random draws. This supports deterministic behavior at source level, but identical-output replay comparisons have not been run.

### Existing token combinations

Source inspection shows the factors are applied at separate input points:

- `MEALX` scales generated meal grams.
- `CHGISF` scales insulin action after the configured `CHG` day.
- `DAYNEED` scales true basal need during 10:00–18:00.

No shared variable is overwritten by these token parsers. Their physiological outputs can still interact through glucose and the algorithm; the three matched pair replay checks have not been run and are not declared passed.

### Token validation limits

The new OUTDAY/GAP tokens fail on malformed values, duplicates, and invalid boundaries. There is not yet a global registry that rejects every unknown or malformed legacy token; adding one requires a complete token inventory and could otherwise reject valid historical test inputs.

## APK static inspection

File supplied: `aaps-4.1-custom-261015-1156.apk`

- Size: 143,068,212 bytes.
- SHA-256: `71a05169498495998d1e63a2edd0b18d824fba5e2b3a0d775f591c4d269c93bb`.
- ZIP CRC/integrity test: passed.
- Manifest package: `info.nightscout.androidaps`.
- Manifest versionName: `4.0.0`; versionCode: `2000`.
- Filename says `4.1-custom`, so the filename and manifest versionName do not match. Do not infer the app's actual code lineage from the filename.
- minSdk 31, targetSdk 35, compileSdk 37.
- Application class: `app.aaps.MainApp`.
- Ten DEX files; native libraries for arm64-v8a, armeabi-v7a, x86 and x86_64.
- Embedded Android Gradle Plugin metadata: 9.4.1.
- `META-INF/version-control-info.textproto` says `NO_VALID_GIT_FOUND`; the APK cannot be tied to a repository commit from this metadata.
- A raw byte scan of all ten DEX files found no literal `AIMI` or `openAPSAIMI` marker. This is not proof that AIMI is absent (obfuscation/build variants can hide names), but it is another reason not to assume this APK contains the current AIMI branch.
- No JAR-style `.RSA/.DSA/.EC/.SF` signature entries were present. APK v2/v3 signing was not verified because `apksigner` was unavailable in the inspection environment.

This was static archive/manifest inspection only. The APK was not installed, executed, or tested against a pump/CGM. No source-to-binary equivalence has been established.

## Validation status

At report update, latest source commit is `70b796b71e1df3925742bf379a4e5f6de7e5e788`. Compile-only run `45096047502` for the immediately preceding source commit `e3c5e6f9d66a467887d1898ae8c5b1fd72e56524` was in progress; a run for `70b796b` had not yet appeared in the Actions listing. Older intermediate compile runs were also still in progress. The history-replay workflow was skipped; no replay batch ran.

Update this report after the latest compile run finishes. A compile pass validates Kotlin test-source compilation only; it does not validate replay outputs or learning behavior.

## Deferred to Claude / next gates

1. Review advanced Basal measured/estimated-hour gate against `LBMIN`, `LBTAILMIN`, `LBFILL`, `LBFILL2`.
2. Review CR raw-row gap/missing-BG behavior and confirm the complete six-hour SMB exclusion.
3. Confirm audit instrumentation leaves learner inputs/formulas unchanged, including non-causal baseline.
4. Confirm every card's candidate/rejection partition against actual replay output.
5. Execute the existing 8 scenarios x 3 seeds, then the three pair-compatibility checks, under Claude's ownership and the agreed sequence.
6. Run OUTDAY and GAP scenarios only after the pair checks pass.
7. Implement and compare `LAORDER_SAFE` / `LAORDER_EVID` only at the planned later phase; neither token is implemented here.

No replay, live settings change, profile change, pump action, or Nightscout write was performed.
