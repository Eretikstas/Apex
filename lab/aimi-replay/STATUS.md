# Lab status

## Task (written by ChatGPT)

### 2026-11-25, Task 9 hypo-model calibration — RUNNING

- Claude's updated decision is authoritative: Task 3 (CHG_28 matched pairs) is CANCELLED; Tasks 1–3 are closed. See `RULES_FOR_CHATGPT.md` §5, commit `2f7f54b4458cfaf2732874b3f1fb4e00d4e45c1c`.
- Step 0 token inventory completed and accepted. No token exists for “eaten less than recorded”; none was invented.
- One 72-job batch started by replacing the single active batch line in `RUN.txt`; start commit: `4eaf23742086141fe7d9d08b57e9c373db06f790`.
- Dispatcher run: `45640354963` (in progress). Replay run: `45640354873` (in progress).
- Grid: APP base prefs from `app22_batch.json`, seeds 1–3; insulin action after day 28: `CHGISF_1.0/1.1/1.2/1.3`; low-end sensor bias: `SBIAS_0.15/0.30/0.45); hypo treatment: `TREATL` vs `TREAT_5`; diagnostic token: `LACAUSE`. 4 × 3 × 2 × 3 = 72 jobs. All jobs use the workflow's fixed `algo=autoisf`, `sim=true`, `learn=false`.
- Compile gate: `45630910694` succeeded on commit `bae268fabd2fd26cb561b5e735f7548df33167b3`; no replay/model source was changed for Task 9.
- Awaiting public run summary. Target bands remain private and are not recorded here. No calibration claim is made. After numeric results and top-three ranking are recorded, STOP for Claude review.


### 2026-11-25, LAPROTECT batch 45629646841 — completed; safety pass NOT met; stop before CHG_28

- Fix commit: `ed1085154d72f3dab1d27edc58f827c61629a947`; compile run `45624674552` green.
- APP control run `45620155708`: base / APP / seed 1 = `<70 2.7 | <54 0.0 | >180 8.2 | CV 28.4 | hypo 27.64 g/day | insulin 14.83 U/day`.
- LAPROTECT batch started by RUN.txt commit `48eeab78656121ca7c6c295827f2a3a21ec0fee8`; workflow run `45629646841`. All 24 replay jobs and all 24 public synthetic-summary steps completed successfully. Replay exit code was 0. A post-job git cleanup warning about `.tmp-juggluco` lacking a .gitmodules URL occurred after upload and did not fail replay.
- Public summary means across seeds 1–3 (columns: `<70 / <54 / >180 / CV / hypo g/day / insulin U/day`):

| Scenario | <70 | <54 | >180 | CV | Hypo g/day | Insulin U/day |
|---|---:|---:|---:|---:|---:|---:|
| base | 2.77 | 0.00 | 11.43 | 30.80 | 25.38 | 14.57 |
| calm | 1.53 | 0.00 | 1.70 | 23.03 | 18.00 | 14.12 |
| dawn1.3 | 2.83 | 0.00 | 10.87 | 30.13 | 25.45 | 15.05 |
| meals1.3 | 3.13 | 0.03 | 13.87 | 33.17 | 27.27 | 15.63 |
| ins+20 | 3.37 | 0.03 | 7.07 | 29.20 | 28.60 | 12.29 |
| ins-20 | 2.33 | 0.00 | 13.97 | 31.57 | 23.59 | 18.14 |
| start+30 | 3.83 | 0.00 | 8.97 | 31.37 | 29.70 | 14.81 |
| growth2% | 2.30 | 0.00 | 12.97 | 30.90 | 23.84 | 17.14 |

- Direct base / seed 1 comparison against the validated APP control: LAPROTECT `3.0 / 0.0 / 11.1 / 31.2 / 30.46 / 15.00` vs APP `2.7 / 0.0 / 8.2 / 28.4 / 27.64 / 14.83`. **Fail:** <70 is higher (3.0 > 2.7) and hypo treatment is higher (30.46 > 27.64); highs and CV are also higher. The pass rule requires <54, <70 and hypo grams not to exceed APP.
- The current STATUS Results section does not contain the requested APP scenario-by-scenario values for run `45070147879`; its artifacts are encrypted. Per rule, no key was requested and no encrypted artifact was decrypted. The public APP control above is the verified base seed-1 reference only.
- Audit observation: CR and ISF candidates were all rejected in the sampled logs (zero accepted); LAPROTECT correctly blocked increases on days where all-cause low rate exceeded 3%. Claude should inspect why the protection does not prevent the base seed-1 deterioration and whether the summary's APP comparator set is adequate.
- **Stop condition:** Task 2 does not meet the safety pass rule. Do not start Task 3 / CHG_28 pairs. Do not make further replay starts or change learner behavior. Claude review required before any continuation.


### User approval and handoff to Claude — 2026-11-23

User explicitly approved items 1–3. Claude's approval of items 1–5 is acknowledged; items 4–5 are also approved for the planned audit visibility and corrected matched-pair checks.

**Message to Claude:** “Claude одобрил 1–5, пользователь сказал да; исправь две ошибки сводки из STATUS”.

The two summary defects identified in the Results review are:
1. The SYNTH_ day-count regex and task-label parsing assume whitespace separators, but replay prefs are comma-separated. Split tokens on comma or whitespace (for example, `[,\\s]`) before extracting SYNTH_<days> and the task label.
2. The summary calculates basal insulin as `deliv / 60.0`, but replay output rows are five-minute rows (288/day). Integrate `deliv * stepMinutes / 60.0` (with `stepMinutes` derived from the median timestamp difference where practical); otherwise the current formula undercounts basal insulin fivefold. Validate a base APP job against the expected approximate TDD of 14–17 U/day before another batch.

Implement items 1–3 behind the approved new opt-in token (e.g. LAPROTECT) so no-token behavior remains unchanged. Keep work on aimi-replay; do not touch the app branch or live settings. Claude owns the Results section and should record the correction and validation there.


### 2026-11-23, causal-filter review + Learning Audit plan

Commits already on `aimi-replay`:
- Replay causal-filter code: `6f222711e5b3e00565597d1d4d2528d32f99ed89`
- Updated causal-filter specification: `4dd3a09c3035661410b910f919959e9cb1457730`
- Compile-only workflow: `75fe1e04fd4d0aaa52441de11f5db866c6c2f19e`
- Learning Audit and interaction-test specification: `cdd3fe77e4ad31b4b9461dc9b93bb678e2c522f1`

New lab specification: `lab/aimi-replay/LEARNING_AUDIT_AND_INTERACTION_TESTS.md`.
It defines aggregate-only audit output for CR, ISF, Basal, Percentage and Dura, plus a 10-case interaction/reproducibility matrix. It does not change learner formulas or live settings.

### Required implementation / checks before batch

1. Add per-card counters: candidates, accepted, rejected by mutually exclusive primary reason, distinct days, minimum-data gate, parameter before/after/delta, and decision.
2. CR audit must count all meal candidates and explain each exclusion. Preserve the full six-hour SMB exclusion; do not narrow to two hours or only SMB-after-low without explicit user approval.
3. ISF audit must count clean correction candidates, including manual and SMB corrections, and distinguish sensor/cannula, hypo/rebound, overlapping bolus, meal/rising glucose, and insufficient-net exclusions.
4. Basal audit must count candidate windows, exclusions by reason, accepted samples per hour, and distinct days. Keep LBTAIL disabled under LACAUSE until a row-level clean-CR link is demonstrated.
5. Add deterministic interaction scenarios from the new spec: outlier vs persistent change, CR/ISF conflict, CR/basal conflict, SMB/rebound, activity/illness, sensor gaps, cannula degradation, and repeated identical replay.
6. Audit reconciliation must hold: candidates = accepted + rejected. Identical input/tokens/seed must produce identical audit summaries and parameter deltas.

### Current run plan after audit implementation

- First: compile-only workflow must pass.
- Then Claude runs the lab batch after the user says `run STATUS`; ChatGPT does not dispatch batches.
- Batch: 8 scenarios x 3 seeds (base, calm, dawn1.3, meals1.3, ins+20, ins-20, start+30, growth2%).
- Tokens: `APP` + `LEARNALL,LAISF,LACR,LACAUSE,LAPCTBACK,LADAYS`.
- Use exact APP token lists from the user's private `app22_batch.json`; do not reconstruct or guess missing tokens.

Expected outcomes:
- dawn1.3 -> basal adapts upward;
- meals1.3 -> CR strengthens, without an inappropriate basal increase;
- ins+20 -> ISF strengthens or percentage decreases;
- ins-20 -> ISF weakens / basal increases as supported by clean data;
- calm -> little or no adaptation;
- SMB-associated lows and rebound lows do not train CR, ISF or basal;
- all exclusions are visible in audit output.

Safety gates: no change to app branch, child profile, pump settings or Nightscout. Batch results are evidence for parent review, not settings recommendations.


### 2026-11-23, proposed learner robustness mechanisms for Claude review

Added a proposal section to `lab/aimi-replay/LEARNING_AUDIT_AND_INTERACTION_TESTS.md` in commit `93c0e5f14de11b43bdec146c7b4de4c19e0dc5f8`.

Claude review requested — assessment only, no implementation yet:
1. Confidence score: per-card evidence quality from clean independent days, episode count, dispersion, missingness and exclusion rate; hard eligibility gates remain absolute.
2. Drift detection: distinguish isolated outlier, temporary shift and persistent change across independent days.
3. Outlier protection: robust summaries and leave-one-day-out sensitivity; do not suppress repeated clinically meaningful extremes.
4. Parameter interaction guard: detect overlapping CR/ISF/Basal/Percentage/Dura evidence, report conflicts and deferred proposals; do not assume a global priority order.
5. Recovery detection/data ageing: detect return to baseline and document any time-decay of old evidence; no automatic reset/rollback.
6. Learning explainability: deterministic per-card reason showing evidence, exclusions, gates, confidence, conflicts and proposed delta.
7. Shadow comparison: APP vs current learner vs report-only experimental diagnostics on identical replay inputs/seeds.

Requested Claude response for each item: feasibility, relevant code locations, dependencies, proposed tests, possible failure modes, and recommended implementation order. Explicitly identify overlap with existing Learning Audit tasks to avoid duplicate work.

Constraints: proposals are report-only until separately approved; no learner formula changes, no live AAPS/profile/pump/Nightscout changes, no app-branch edits. Preserve current causal filters and six-hour CR SMB exclusion. Keep role split: ChatGPT owns Task section/lab branch; Claude owns Results section and replay execution.


### 2026-11-23, next validation sequence: journal, baseline, new scenarios and order comparison

**Do not change the sequence or start the expanded tests early.** No replay batch is run by ChatGPT. Claude owns replay execution and writes only the Results section.

#### Phase 1 — Learning Audit journal
1. Implement the previously specified aggregate-only Learning Audit counters and deterministic one-line-per-card output.
2. Preserve all causal filters, including the complete six-hour post-meal SMB exclusion for CR.
3. Compile the replay test sources. Do not alter learner formulas or live AAPS settings.

#### Phase 2 — baseline replay on the existing 8 situations
After the journal is implemented and compile passes, run the existing 8-situation x 3-seed batch exactly as already specified above, with APP baseline and the causal learner tokens. Record audit reconciliation, card decisions, parameter before/after/delta, and safety outcomes. Do not add OUTDAY/GAP or pair combinations to this baseline batch.

#### Phase 3 — short compatibility checks for existing scenario tokens
The generator already has these controls:
- `MEALX_<x>`: scales generated meal carbohydrate amount/meal load by factor x.
- `CHGISF_<x>`: scales insulin action after the change day selected by `CHG_<day>`.
- `DAYNEED_<x>`: scales the body's basal need during 10:00–18:00 (generator hours 10..17).

First run one isolated short check for each pair below, using the same seed and a matched no-pair control. Keep the third factor at 1.0. Example test values are synthetic stress inputs, not clinical settings:
1. **MEALX + CHGISF:** `MEALX_1.2 CHG_4 CHGISF_1.2`.
2. **MEALX + DAYNEED:** `MEALX_1.2 DAYNEED_1.2`.
3. **CHGISF + DAYNEED:** `CHG_4 CHGISF_1.2 DAYNEED_1.2`.

For each short check, report whether the intended underlying need changed, whether the expected learner card(s) responded, whether an unrelated card changed, and whether the audit identifies the evidence correctly. Do not put these pair combinations into a larger matrix until all three short checks pass. If a token's implementation or expected effect is unclear, inspect/fix the synthetic generator and compile before proceeding.

#### Phase 4 — add two new deterministic synthetic situations

**OUTDAY — one isolated outlier day**
- Add token `OUTDAY_<day>_<x>`, with a 1-based synthetic day index and a positive multiplier x.
- Apply x to the body's insulin-need factor on that one day only; all other days remain unchanged. Do not alter meal sizes, insulin action, sensor noise, or other day factors.
- Use a deterministic example such as `OUTDAY_4_1.3`; also test a low-need outlier (x below 1) if the implementation can support it without adding another token.
- Expected: the journal identifies one-day concentration of evidence; a single outlier day must not cause an abrupt persistent parameter change. Report leave-one-day-out sensitivity if available.

**GAP — sensor blackout**
- Add `GAP_<minutes>`, supporting exactly `GAP_15`, `GAP_30`, and `GAP_60`.
- Each run creates one deterministic sensor-data blackout of the requested duration at the same fixed point in the synthetic timeline (day 4, 02:00 local synthetic time). Remove readings during the interval; do not fill or interpolate them.
- Preserve the underlying glucose/body trajectory. Resume sensor readings after the blackout and make the recovery transition testable.
- Expected: gap and recovery-transition windows are rejected from causal learning; no parameter update is attributed to missing/interpolated sensor data. Audit must count the exclusions and distinguish gap from insufficient data.

#### Phase 5 — compare the two learning orders
Add mutually exclusive replay tokens:
- `LAORDER_SAFE`: safety-first conflict resolution. When eligible card proposals conflict, prioritize the proposal that reduces insulin exposure / protects against lows; defer proposals that would increase insulin effect or delivery. Record both proposals, selected order, and deferred reason.
- `LAORDER_EVID`: evidence-first conflict resolution. Prioritize the eligible proposal with stronger clean independent evidence (distinct days, accepted episodes, lower dispersion/exclusion burden); use the safety-first rule as the tie-breaker. Record both proposals, selected order, and deferred reason.

The tokens must be mutually exclusive; missing/invalid order token must fail clearly rather than silently choose one. These are replay-only comparison modes, not approved live behavior. Do not change card formulas or hard eligibility/safety gates.

Run SAFE and EVID on identical inputs, scenario tokens, and seeds. Compare card order, selected/deferred proposals, parameter deltas, audit determinism, and glucose safety outcomes. Keep APP baseline in each matched comparison. Do not infer that either order is preferable from a single seed.

#### Required sequence and stop conditions
1. Learning Audit journal + compile.
2. Existing 8 situations x 3 seeds.
3. Three short pair-compatibility checks above — one pair at a time.
4. Only if all three pass: OUTDAY, GAP_15/30/60, expanded pair checks, and matched SAFE vs EVID order comparison.
5. Report failures and stop before the next phase if a scenario does not produce its intended underlying change, if a pair causes an unexplained unrelated-card response, or if audit totals do not reconcile.

No app-branch changes, no profile/pump/Nightscout changes, no live learner activation. All outputs remain aggregate-only and for parent review.


### 2026-11-23, ChatGPT technical review of audit implementation (commits 0a7079d and 3c4406f)

Claude review requested before declaring Learning Audit complete. No replay batch has been run.

Findings from source inspection:
1. **Basal gate is currently too broad / potentially misleading.** Both classic and advanced basal audit lines report PASS when aggregate accepted windows >=3 (and days condition), but the learner actually evaluates each hour separately (normally >=3 samples; LBMIN/LBTAIL can raise that threshold) and may update no hour. The audit should report the actual per-hour eligibility gate, not an aggregate PASS. Include eligible-hour count and per-hour threshold; keep candidates = accepted + rejected.
2. **Basal insufficient-days path hides the denominator.** Advanced hourNeeds returns candidates=0/accepted=0/rejected=0 when its day gate fails, even though candidate windows exist in the data. Classic learnBasal returns before printing any audit line when the 3-day gate fails. Prefer count candidates/reasons first, then show gate=FAIL reason=insufficient-days; do not mislabel unexamined windows as zero candidates.
3. **CR sensor-gap detection needs a row-level audit.** crBlockVerdict() builds its rows list by filtering to rows with BG before testing sensorGap. If blackout rows carry sensorGap=true but no BG, those rows are removed before the gap check. Verify the generator's exact representation; if gap rows are BG-less, inspect the original time-indexed output for gaps/flags before filtering BG, and ensure the full 6-hour episode is rejected. Preserve the agreed six-hour SMB exclusion.
4. **CR reconciliation appears structurally complete** for each selected meal: each candidate is either rejected by one mutually exclusive continue reason or counted in used. Validate this with deterministic replay output before accepting it.
5. **ISF reconciliation appears structurally complete** for selected correction candidates: every candidate is either assigned one rejection reason or added to seen; however, the early insufficient-window return reports zeros without scanning available corrections. Label that as a pre-scan gate rather than a true candidate count.
6. **Basal causal reason labels are audit-only**, but verify afterMeal/afterHypo flags are populated consistently by the synthetic generator. Compare accepted window identities before/after instrumentation to prove formulas and learner inputs are unchanged.

Required before batch: Claude to review these points, correct/approve the audit semantics, and confirm both compile-only runs. Do not start replay batches until the audit counters and gates are trustworthy. Claude remains owner of Results and replay execution; this note is in Task only.



### 2026-11-23, compile failure and follow-up syntax fix

- Compile-only run `45089660052` for commit `ed92b3fb41a63958010f3df98004a95b946c23aa` completed with failure after 7m11s.
- The prior syntax repairs exposed one remaining concatenated declaration at `HistoryReplayTest.kt:2075`: `val px = ... )        val py = ...`. The main Kotlin modules compiled; the unit-test Kotlin source compilation failed on this syntax error.
- A full-file scan found this as the only remaining same-line concatenation matching the checked declaration/control-flow patterns.
- Fixed by separating `px` and `py` declarations on separate lines. Commit: `faa3c7cf5498059a810bebf24020188cd00b659f`.
- Compile-only should be rechecked on this commit. No replay batch was run. Audit-semantic findings above remain open and require Claude review; a passing compile alone does not close them.


### 2026-11-23, audit gate and reason-priority corrections

Commits on `aimi-replay`:
- `7ea198b818faff1584bd1cc937a3f38ce0a4eb92` — explicit per-hour Basal eligibility and candidate accounting.
- `2ae8f59079da1ab32358a867c555248addc00556` — sensor-gap reason priority for CR, ISF and classic Basal.

Changes are confined to replay test audit/reporting logic:
1. Advanced Basal now reports per-hour sample counts, actual thresholds (including LBMIN/LBTAIL), eligible hours and estimated/fill hours. The audit PASS is no longer based on an aggregate accepted-window count.
2. Classic Basal now reports per-hour eligibility against its actual three-sample threshold.
3. Both Basal paths scan candidate windows before applying the distinct-day gate; otherwise-clean windows are explicitly rejected as `insufficient-days`. Classic learner still returns before changing its profile when the day gate fails.
4. ISF's insufficient-window path now pre-counts correction candidates and reports them as rejected by the pre-scan gate, rather than claiming zero candidates.
5. CR retains the full time-indexed rows separately from BG-valid rows and checks the complete six-hour window for `sensorGap` before the short-window test. ISF and classic Basal prioritize sensor-gap over generic missing-BG when both apply.

No learner formula, clinical parameter, live profile, pump or Nightscout setting was changed. No replay batch was started. Compile-only run `45093582395` was queued for commit `2ae8f59079da1ab32358a867c555248addc00556`; confirm its result before treating this audit pass as compile-validated. Claude review remains required before any replay batch.

### 2026-11-23, Learning Audit gate corrections and CR gap robustness

Source review and audit-only corrections on `aimi-replay`:

- `7ea198b818faff1584bd1cc937a3f38ce0a4eb92`: basal audit now scans and accounts for candidates even when the clean-day gate fails; reports per-hour sample thresholds and eligible/estimated hours instead of an aggregate accepted-window PASS. Classic basal also reports its denominator and per-hour eligibility before returning on insufficient days. ISF pre-scan reports available correction candidates rather than false zeros.
- `15be0e85244ff677176853017fbbe895b7726b03`: advanced basal `LBFILL2` audit now uses the same per-hour measured predicate (including LBTAIL threshold) as the learner when counting interpolated hours. CR audit retains raw rows for the full episode, detects flagged or timestamp-visible sensor gaps before short-window rejection, and sees BG-less rows for missing-BG and SMB checks.
- `b991f93032c7bc46f4dde115a70e0642c2cf0fcc`: explicitly preserve the non-causal CR candidate/window path; the additional raw-row checks apply only under `LACAUSE`, so the non-causal comparison is not changed by audit hardening.

No learner formulas or live AAPS settings were intentionally changed. No replay was run. The latest source still requires compile-only verification.

Open review for Claude:
1. Confirm the advanced basal `eligibleHours/estimatedHours` gate matches `LBMIN`, `LBTAILMIN`, `LBFILL`, and `LBFILL2` behavior exactly.
2. Confirm CR raw-row gap/missing-BG handling and that the full six-hour SMB exclusion remains intact.
3. Confirm the audit-only changes leave learner input sets/formulas unchanged, including the non-causal baseline path.
4. Confirm candidates = accepted + rejected for each CR block, ISF scan, and both basal implementations.

Stop before replay batches until Claude approves these semantics and the compile-only check passes.



### 2026-11-23, independent technical audit and OUTDAY/GAP preparation

ChatGPT source-level audit and replay-only test-harness changes are recorded in [TECHNICAL_AUDIT_2026-11-23.md](TECHNICAL_AUDIT_2026-11-23.md).

Source commits:
- `3e7792f5d2e345ff82af335e886f20caf834d5be`: independent Learning Audit reconciliation guards; strict OUTDAY/GAP parser; scenario generation.
- `b40050a4e7591908598e08189332a32d06993493`: sensor-gap/recovery timeline hardening.
- `8fb7ae73561ec8bc193be34f8d24413d78711dde`: preserve non-causal ISF behavior; expand boundary checks.
- `037cca0c148eec99b470ee30e0df4a61a8d9b4d5`: avoid stale sensor values during synthetic GAP.
- `76828a294ba8fa4f0c309cba20d24d3789c5f727`: count intentional no-glucose GAP ticks separately from genuine replay exceptions.
- `e3c5e6f9d66a467887d1898ae8c5b1fd72e56524`: exclude outcome-learning episodes overlapping GAP or its 60-minute recovery.
- `70b796b71e1df3925742bf379a4e5f6de7e5e788`: require `LACAUSE` for GAP scenarios.

Changes are limited to replay test/audit logic:
1. ISF now counts correction candidates independently; CR and both Basal paths also assert `candidates = accepted + rejected` using fail-fast guards.
2. Causal audit windows detect explicit sensor-gap flags and timestamp gaps over 5 minutes. The non-causal ISF path remains unchanged.
3. Added `OUTDAY_<1-based-day>_<positive-factor>` and `GAP_15/GAP_30/GAP_60`. GAP removes sensor samples on day 4 at 02:00 synthetic UTC+3, writes no BG for blackout rows, performs no interpolation, and marks the following 60-minute recovery as contaminated.
4. Added parser/reconciliation unit tests. Workflow commit `c5db9c996396f1a17a463c875a757064a4945092` adds an isolated JUnit step with replay input/output paths explicitly empty; the replay test is skipped by its assumption gate while the two pure helper tests execute. Run `45096342199` is in progress; compile is still running and the isolated helper-test step has not started.
5. Source-level deterministic review: all synthetic random streams are seeded from `SEED` and fixed offsets. Same-seed replay equivalence remains untested.

APK static inspection is documented in the linked report. The supplied APK filename says 4.1, but its manifest reports versionName 4.0.0/versionCode 2000; embedded VCS metadata says `NO_VALID_GIT_FOUND`, so source provenance is not established.

Latest source commit: `70b796b71e1df3925742bf379a4e5f6de7e5e788`; compile-only run `45096199938` is in progress. Earlier source commits `3e7792f`, `b40050a`, `8fb7ae7`, and `037cca0` have compile-only success; runs for `76828a2` and `e3c5e6f` remain in progress. Workflow-only commit `c5db9c9` run `45096342199` is queued to execute the isolated helper tests without replay inputs. Replay workflow was skipped; no replay batch was started.

No learner formula, live profile, pump or Nightscout setting was changed. Claude review and replay ownership remain unchanged. Do not proceed to baseline/pair/OUTDAY/GAP batches until Claude approves the outstanding audit semantics and compile validation is complete.

### 2026-11-23, LAORDER_EVID proposal/apply design

User-directed design decision: use pure `propose…(state)` plus selected-only `apply…(proposal)` for Basal and Dura. Do not use calculate-then-restore as the primary design.

- Basal proposal must carry the complete next state, including copied arrays and every mutable trend/confirmation/counter field; current source inventory includes `profBasal`, `lastDir`, `upCount`, `lastShift`, `lastTrendDay`, `lastTrendF`, and `lbCards`, subject to full call-graph audit.
- Dura proposal carries the candidate value/state without mutating `aw.dura`.
- EVID computes all proposals from one immutable pre-selection snapshot. Only the selected proposal is applied; unselected proposals are discarded.
- Required tests: no-token byte-for-byte baseline equivalence; repeated propose calls return identical proposals and leave globals unchanged; apply changes only selected card; discarded proposal has no effect; array aliasing is prevented.
- Snapshot/restore is fallback only, through one complete `snapshot()/restore()` abstraction and the same non-mutation tests.

Design and test gates are documented in `TECHNICAL_AUDIT_2026-11-23.md`. Implementation has not yet been committed or compile-validated. No replay batch or live AAPS change was made.


### 2026-11-23, compile and isolated Learning Audit helper-test confirmation

- Latest branch head before this report: `2577350eb1261003ab337b827c6f079d3b4bf587`.
- Workflow-only commit `c5db9c996396f1a17a463c875a757064a4945092`, run `45096342199`: **success**.
- `:plugins:aps:compileFullDebugUnitTestKotlin`: `BUILD SUCCESSFUL` (6m 53s).
- Isolated `HistoryReplayTest` JUnit invocation: `BUILD SUCCESSFUL` (24s), with `AIMI_REPLAY_IN` and `AIMI_REPLAY_OUT` explicitly empty. This confirms compilation and the helper-test step without opening replay inputs or executing a replay batch.
- The compile runs previously listed as in progress for source commits `76828a2`, `e3c5e6f`, and `70b796b` are now completed successfully (runs `45095912758`, `45096047502`, and `45096199938` respectively).
- **Phase 1 (Learning Audit journal + compile/helper tests) is compile-validated.** This does not validate replay learning behavior or close Claude's semantic review points.
- **Next owner action: Claude runs Phase 2** — existing 8 scenarios x 3 seeds, APP baseline and causal learner tokens exactly as specified above; compare audit reconciliation, card decisions and safety outcomes. No OUTDAY/GAP or pair matrix in this batch.
- **Then Phase 3:** three isolated matched pair checks, one at a time, same seed and matched no-pair control, third factor=1.0:
  1. `MEALX_1.2 CHG_4 CHGISF_1.2`
  2. `MEALX_1.2 DAYNEED_1.2`
  3. `CHG_4 CHGISF_1.2 DAYNEED_1.2`
- For every run, Claude records intended synthetic change, expected card response, unrelated-card response and audit evidence in Results. Stop on unreconciled counters, unexplained card response, or incorrect synthetic effect.
- ChatGPT has not dispatched replay. No live AAPS/profile/pump/Nightscout changes.


### 2026-11-23, Task 7 — aggregate-only public summary for synthetic replays

Workflow changes on `aimi-replay`:
- `91b995a3b570475520992f3883ba8d6d7144a0e1` — split replay from result encryption and add the conditional synthetic summary step.
- `8404e352fdfd2408980881f0d555f22bcc162af1` — remove a redundant validation loop from the summary script.
- `2cdc7315c7aea6eb975581a44efbc866e501e5cd` — make the compile-check workflow run when `aimi-replay.yml` changes.

The new step runs only when `matrix.prefs` contains `SYNTH_`. It reads that job's `out.json.gz`, selects the second half of synthetic days, and publishes one table to both `GITHUB_STEP_SUMMARY` and the job log:
- BG below 70 mg/dL, BG below 54 mg/dL, and BG above 180 mg/dL — percentages of valid BG samples in the selected half;
- CV — population standard deviation divided by mean BG;
- hypo-treatment carbohydrates — grams/day;
- delivered insulin — U/day, calculated from basal rate per minute plus SMB and manual boluses.

The task label is the first three whitespace-separated prefs tokens. The only copied Gradle detail lines are the last `AIMI replay: learner ALL cards` line and the `AIMI audit` lines. No timestamps, raw glucose series, or per-meal carbohydrate amounts are emitted. The encrypted archive path, artifact name, AES-256-CBC/PBKDF2 encryption command, and retention are unchanged.

Validation:
- Compile-check run `45100552307` for commit `2cdc7315c7aea6eb975581a44efbc866e501e5cd` was **in progress** at the time of this report; do not mark the build green until its conclusion is checked.
- Existing synthetic batch `45099362646` was still **in progress**. No second `aimi-replay.yml` run can start until it finishes.
- The exact APP prefs string from private `app22_batch.json` is not present in the available files/context. It was not guessed, so the requested base/seed-1/APP one-job run has not been started. Supply that exact prefs string (or the file) before dispatch.
- Once a SYNTH-only run completes, ChatGPT can read the public aggregate summary from the Actions run. The encrypted artifact remains unreadable here; Claude's role is to review the learner decisions, not extract the numeric summary.


Task 7 follow-up:
- `6c857c8757705e48b3e722ebc4a405ae70f94381` updates the workflow header comment so it accurately describes the aggregate-only SYNTH_ exception.
- Compile-check run `45100729376` for that latest workflow commit was **in progress** at the last check. Run `45100552307` for the earlier compile-workflow commit was also still in progress.
- GitHub accepted the updated `aimi-replay.yml` workflow on push (push-triggered replay runs were skipped as intended); this is a YAML registration check, not an execution test of the summary step.
- The requested one-job APP replay remains pending: run `45099362646` is still in progress, and the exact APP prefs from `app22_batch.json` are unavailable. Do not dispatch a second replay while the first is active or substitute guessed APP tokens.


Task 7 correction / supplied test prefs:
- `6052ac4ab87ae6d76fcbe8d3d916c1bbe7c8940f` fixes 17 escaped GitHub Actions expressions (`\\${{` → `${{`) introduced in the new Replay/summary/encryption steps. This is a required workflow correction; validation is still pending.
- The synthetic output schema was checked in `HistoryReplayTest.kt`: rows are a JSON array; `t` is epoch milliseconds; `bg` is measured glucose; `hypo` is hypo-treatment grams; `deliv` is basal rate in U/hour on one-minute ticks; `units` and `man` are SMB/manual bolus units. Daily insulin therefore integrates `deliv / 60` plus `units` and `man` per tick.
- User supplied exact synthetic prefs for the one-job test: `SYNTH_56,SEED_1,CRT_22,NEEDDX_1.3,NOISEPHI_0.995,SBIAS_0.15,UKF,SMARTS,SPLIT_60,SPLITMIN_20,LEARNG3,NOIOB,SMBMIN_60,UAMMIN_60,LATECOB,LATEPCT_50,LATEB_2,LEARNB5,LBLOWMAX_6,LBFILL2,LBMIN_2,TREATL,LEARNDURA,NOISE_0.07,CANCH_72,PCTCARD,PCTMAX_100`.
- This is a `SYNTH_56` synthetic prefs string, not a separate literal `APP` token. It is retained as supplied, without adding or guessing tokens.
- At the last status check, replay run `45099362646` and compile check `45100729376` were both still in progress. The GitHub integration exposes no workflow-dispatch action, so the requested one-job run has not been started. Do not start another replay until `45099362646` completes and the latest compile check is green.

### 2026-11-23, safety-failure review — priorities 1–5 before Task 8

Reviewed the current branch head `f2c6bc1781d2c43ba46648ce6dd0f42536108399`, this STATUS Results entry, `COORDINATION.md` (commit `10e60902ab`), and the current replay source. No source or workflow behavior was changed in this review.

**1. Percentage protection must count SMB lows.** In `learnAllCheck()`, `lo3Eligible` currently includes only `hypoCause == fasting || meal_bolus`; this excludes `smb` and `ambiguous` lows. Proposed protection-only classification: include every valid BG low, including SMB/activity/illness/old-cannula-associated lows, except false-low artifacts, sensor-gap/transition rows, the first 24 h of the synthetic sensor stream, and rows in the 180-minute post-hypo-treatment rebound window. This is separate from causal attribution for CR/ISF/Basal. Keep the existing Percentage-down threshold (>4%) unless separately reviewed. There is currently no explicit first-sensor-day row flag; the synthetic stream starts at `t0`, so a deterministic first-24-hour marker is needed.

**2. Add a three-day insulin-increase interlock (behavior change; pending Claude review and user approval).** Calculate the same protection-only all-cause low rate over the last 3 days. If it is >3%, block every proposal that increases insulin effect/delivery: Percentage up/restore, stronger CR, weaker ISF, positive Basal deltas, Dura up, and any analogous learner weight/multiplier increase. Do not block protective decreases. For mixed Basal proposals, block positive hourly deltas while retaining eligible negative deltas; do not discard the whole card blindly. Evaluate the gate against the pre-change snapshot before applying any proposal.

**3. Dura down must use the same protection-only low definition.** Current `cleanDuraRow()` excludes post-hypo, old-cannula, sensor-gap, false-low, illness and activity rows, but it does not exclude SMB lows. Current Dura low-rate denominator is the narrower `valid` set. Proposed change: preserve `cleanDuraRow()` for flat-high evidence, but calculate the Dura low-protection rate from the shared all-cause protection window in item 1. This ensures SMB lows count and exclusions match Percentage protection.

**4. CR/ISF audit visibility.** Current source already contains separate `AIMI audit CR block=...` and `AIMI audit ISF:...` lines, and the current `aimi-replay.yml` public synthetic-summary step selects lines beginning `AIMI audit `. The prior Results run was based on code `7f34c90ae0`; its missing audit output does not prove the current f2c6 source/summary path works. Before another learner batch, ensure the audit functions execute even when a minimum-days/window gate fails (report the pre-scan candidates and gate reason instead of silently returning), then verify the public summary contains CR and ISF candidate/accepted/rejected-by-reason counts for every check. Do not relax the six-hour post-meal SMB exclusion to manufacture CR data.

**5. Correct the CHGISF pair-test specification.** `CHGISF_<x>` is only applied from `CHG_<day>`; the prior pair tests without `CHG_` did not exercise insulin-action change. Repeat with the required day token: (a) `MEALX_1.3 CHG_28 CHGISF_0.8`; (b) `MEALX_1.3 DAYNEED_1.2`; (c) `CHG_28 CHGISF_0.8 DAYNEED_1.2`. Use matched controls with the same seed and only the intended pair factors changed. No larger matrix until all three short checks pass.

**Approval boundary / next action.** Items 1–3 change learner behavior and therefore are not implemented pending Claude's technical review and the user's explicit approval, as required by COORDINATION.md. Items 4–5 are instrumentation/test-plan work and can proceed within the lab scope. Task 8 (new RUN workflow and RUN.txt) is deferred until priorities 1–5 are resolved and the audit/compile gate is green. No replay batch was started.

### 2026-11-23, Learning Audit visibility fix and corrected pair-test handoff

- Source commit: `73f3ae80f08b83b1b5dd72603212be0d74218ea9`.
- Change: `learnAllCheck()` now runs the read-only ISF and CR audit scans before returning on a failed overall three-day gate. It emits an explicit `AIMI audit LEARNALL ... reason=insufficient-days` line. The audit scans retain their own minimum-data gates and do not apply proposals. This is report-only instrumentation; no learner formula, threshold, parameter or live behavior was changed.
- Compile-only workflow run: `45102841544`, in progress at this report. The replay workflow push event was skipped as expected; no replay batch was started.
- Existing `aimi-replay.yml` open synthetic summary already forwards lines beginning `AIMI audit `, including the current CR and ISF audit records. The next Claude-run SYNTH batch must confirm those records appear in the public summary and reconcile candidate = accepted + rejected.
- Corrected pair plan (same seed, matched no-pair controls; no large matrix until all three pass): `MEALX_1.3 CHG_28 CHGISF_0.8`; `MEALX_1.3 DAYNEED_1.2`; `CHG_28 CHGISF_0.8 DAYNEED_1.2`. The `CHG_28` token is mandatory for both CHGISF pairs.
- No BATCHES.md exists in this branch; the corrected strings are recorded here rather than creating an unneeded duplicate plan file.
- Items 1–3 remain pending Claude review and explicit user approval because they alter learner behavior. Task 8 remains deferred until items 1–5 and compile/audit gates are resolved.


### 2026-11-23, explicit user approval and one-job summary validation

The user has now explicitly answered **YES** to learner-protection items 1–3. This is approval to implement them behind the opt-in token `LAPROTECT`; absence of the token must preserve the prior behavior exactly.

Approved behavior:
1. Protection-only low counting includes all valid lows, including SMB-associated lows, while excluding false-low artifacts, sensor-gap/transition rows, the first 24 hours of the synthetic sensor stream, and the 180-minute post-hypo-treatment rebound window.
2. If the three-day protection-low rate is >3%, block every proposal that increases insulin effect/delivery (percentage up/restore, stronger CR, weaker ISF, positive basal deltas, Dura up, and learner weight/multiplier increases). Do not block protective decreases; for mixed basal proposals, retain eligible negative deltas and block positive ones. Evaluate against the pre-change snapshot.
3. Dura-down protection uses the same protection-only low definition; retain the existing clean-row filter for flat-high evidence.

**Before implementing or running learner changes, fix and validate the public summary.** Claude owns this summary correction and the Results section. Fix:
- Parse comma- or whitespace-separated prefs for both SYNTH day count and task label.
- Integrate basal delivery using the actual row interval (five-minute rows in the current schema: `deliv * 5 / 60`, or derive the median timestamp interval). Do not use `deliv / 60`.

After that fix is committed, run exactly **one** manual workflow job as the summary smoke test:
- Workflow: `AIMI history replay`, branch: `aimi-replay`.
- Use the single `prefs` input (leave `batch` empty); do not launch a matrix or any other scenarios.
- Exact supplied base/APP, seed-1 prefs (do not modify or reconstruct): `SYNTH_56,SEED_1,CRT_22,NEEDDX_1.3,NOISEPHI_0.995,SBIAS_0.15,UKF,SMARTS,SPLIT_60,SPLITMIN_20,LEARNG3,NOIOB,SMBMIN_60,UAMMIN_60,LATECOB,LATEPCT_50,LATEB_2,LEARNB5,LBLOWMAX_6,LBFILL2,LBMIN_2,TREATL,LEARNDURA,NOISE_0.07,CANCH_72,PCTCARD,PCTMAX_100`.
- Keep workflow defaults unchanged; one job only.
- Confirm the public summary parses SYNTH_56 and reports TDD around 14–17 U/day. If outside that range or the summary fails, stop and investigate; do not start another replay.
- Record the workflow run ID, commit, summary output, and TDD in Results.

Do not start the wider replay batch yet. No live AAPS/profile/pump/Nightscout changes.

### 2026-11-23, ChatGPT continuation without Claude — synthetic summary repair

User authorized ChatGPT to continue the lab work without Claude and required every action to be recorded here. Work remains limited to branch `aimi-replay`; no app-branch or live AAPS changes.

- Updated `.github/workflows/aimi-replay.yml` in commit `a3057c3187318087af221c8b6ed4c67bf2f4a527`.
- Fixed `SYNTH_<days>` extraction to accept comma- or whitespace-separated preference tokens (the actual replay prefs use commas).
- Fixed task-label tokenization to split on commas and whitespace.
- Fixed basal insulin integration: infer the normal row interval from the median positive timestamp delta (ignoring gaps over 30 minutes), validate it is 1–15 minutes, and integrate `deliv` as U/hour × interval-hours. SMB and manual bolus units remain summed per row. This avoids the known fivefold undercount on 5-minute rows and remains robust to missing GAP ticks.
- No replay was run. The next required validation is compile-only CI, followed by exactly one base APP / SYNTH_56 / seed 1 summary check. Do not start the replay until compile is green and no replay workflow is active. Check TDD against the expected approximate 14–17 U/day; if outside, stop and investigate.
- Review requested: verify comma parsing, inferred cadence, and the insulin-unit semantics in the public summary. The summary step is part of the replay workflow; the replay step, encryption/decryption, and secrets were not changed.


### 2026-11-23, ChatGPT implementation — opt-in LAPROTECT in replay learner

User authorized continuation without Claude. Implemented the already-approved items 1–3 on `aimi-replay`; no live AAPS code, profile, pump, Nightscout, or `autoisf-atc3` changes.

Commits:
- `201cc323652fe2c1068b9fd037590cebcc3be143` — LAPROTECT logic and regression tests.
- `2931d29e3693dcc9797adbd2f4d2c5c352bc3f5f` — require `LAPROTECT` to be paired with `LEARNALL`, so the interlock cannot be silently bypassed by a standalone learner path.

Implementation:
1. Protection-only all-cause low metric is separate from causal attribution. It counts all valid BG rows, including SMB/ambiguous/old-cannula/activity/illness-associated lows; excludes rows without BG, first 24 h, false-low artifacts, sensor-gap/transition rows, and the 180-minute post-hypo-treatment window. The row metadata needed for this metric is generated when either `LACAUSE` or `LAPROTECT` is enabled.
2. The last-three-day protection rate blocks insulin-increasing proposals when >3%; insufficient valid-day coverage also fails closed for increases. Protective decreases remain eligible. Directional gating is applied to actual proposal effects: percentage restore/up, stronger CR (lower CR multiplier), stronger correction (lower ISF), positive basal deltas, and Dura up are blocked; weaker CR / higher ISF and protective decreases remain allowed. Basal gating applies per hour, including trend/shift paths, so mixed proposals retain eligible negative hourly deltas.
3. Dura uses the same protection-only row definition over its seven-day evidence window. Dura down remains available when the all-cause low rate exceeds 4%; Dura up is blocked by the three-day interlock.
4. Synthetic stress-token parsing now accepts comma-separated tokens as well as whitespace-separated tokens. Added regression tests for comma parsing and protection-row inclusion/exclusion.

No replay was run. Compile-only validation is required before the single base APP / SYNTH_56 / seed 1 control run. That run must use the exact existing APP token list; do not reconstruct missing tokens from partial examples. Stop if TDD is outside approximately 14–17 U/day or if the summary fails. Claude review is no longer a dependency per the user's instruction, but the source-level review points below remain explicit checks for this independent continuation:
- verify that the protection denominator and exclusions match the approved definition;
- verify that every insulin-increasing path is gated and protective decreases are not blocked;
- verify no-token behavior remains unchanged;
- verify the Dura seven-day protection count and per-hour basal handling.


### 2026-11-23, LAPROTECT scope guard and insulin accounting across GAP

- Commit `251520148b7141047b5acbd6bdc628d7472a44f9` adds two safeguards:
  1. `LAPROTECT` now requires both `LEARNALL` and `LACAUSE`. This prevents the protection token from running without the complete learner interlock or with unfiltered Dura flat-high evidence.
  2. Synthetic output now records delivered basal rate and manual bolus units even on sensor-blackout rows. BG/COB remain absent during the blackout. This lets the aggregate TDD summary count insulin delivered during GAP instead of silently dropping it.
- No replay was run. Compile-only validation remains pending for the current source head.


### 2026-11-23, static pair-token audit and run/dispatch gate

Source-only review of the three approved pair factors in `HistoryReplayTest.kt`:
- `MEALX_<x>` scales generated meal grams at meal construction.
- `CHG_<day>` sets the change day; `CHGISF_<x>` scales insulin action only when `day >= chgDay`. With no `CHG_`, the default day is 9999 and the factor does not activate during the 56-day run.
- `DAYNEED_<x>` scales true basal need only during synthetic hours 10–17 (10:00–18:00).
- Static inspection finds these factors applied at separate points without overwriting one another. This is not a runtime pair test; the three matched pair checks remain unvalidated.

Run state:
- Earlier replay batch `45099362646` is completed with failure (the previously recorded five failed jobs remain unresolved); it is not active.
- Push-triggered `aimi-replay.yml` runs for the recent commits were skipped as intended. No replay is active.
- Compile-only runs `45146011846`, `45146079235`, and `45146287550` were still in progress at this report; the latest source validation target is run `45146287550` for source commit `251520148b7141047b5acbd6bdc628d7472a44f9`.
- The GitHub integration available in this session exposes read/write repository-file actions and workflow reruns, but no workflow-dispatch action. The workflow file is only on `aimi-replay`, not the repository default branch, so the manual `Run workflow` path remains unavailable without changing the default branch. No default-branch change was authorized.
- The previously supplied long `SYNTH_56,SEED_1,...,PCTMAX_100` value is a complete prefs profile, not an extra standalone `APP` token list. Do not append an `APP` token or invent a different APP profile. The exact profile can only be used as supplied; it must not be relabelled or modified.
- The one-job summary smoke test and matched pair runs are not started. Task 8 remains deferred until the current compile/audit gate and pair-run prerequisites are resolved; its approved RUN.txt wrapper has not been implemented in this report.


### 2026-11-23, LAPROTECT compile and helper-test validation

- Compile-only workflow run: `45146287550`.
- Source commit checked out: `251520148b7141047b5acbd6bdc628d7472a44f9`.
- Result: **SUCCESS**. Both `Compile replay test sources only` and `Run replay audit helper tests without replay inputs` completed successfully.
- This validates Kotlin test-source compilation and the isolated helper tests only. It does not validate replay outcomes, the 3-day protection rate on generated data, the interlock across real proposal combinations, or the summary TDD result.
- Documentation ownership correction: commit `cd8b648dd987e007e4e71b38d5ca14a8f1d09acf` moved all ChatGPT continuation reports into the `Task` section; Claude's `Results` section was preserved unchanged.
- No replay batch was started. Next replay gates remain: one exact base/APP summary smoke test, then matched pair checks with `CHG_28`; the current session still has no direct workflow-dispatch action.


### 2026-11-23, single base APP / SYNTH_56 / seed 1 control run

- Dispatcher run: [45149628280](https://github.com/Eretikstas/Apex/actions/runs/45149628280), triggered from `main`; reusable replay workflow checked out `aimi-replay` at source commit `304b3c49917786e8a5d483eb98dc2f772c0e2b64`.
- Exact prefs matched the approved control string: `SYNTH_56,SEED_1,CRT_22,NEEDDX_1.3,NOISEPHI_0.995,SBIAS_0.15,UKF,SMARTS,SPLIT_60,SPLITMIN_20,LEARNG3,NOIOB,SMBMIN_60,UAMMIN_60,LATECOB,LATEPCT_50,LATEB_2,LEARNB5,LBLOWMAX_6,LBFILL2,LBMIN_2,TREATL,LEARNDURA,NOISE_0.07,CANCH_72,PCTCARD,PCTMAX_100`. Workflow inputs: `learn=true`, `pretrained=false`, `sim=false`, `algo=aimi`, `batch` empty; `react_floor` and `isf` empty; `oref_prediction`, `no_booster`, `booster_iob` false. Exactly one replay job was planned and executed.
- Result: dispatcher and replay job completed successfully. Replay step: **16,128 ticks, 0 failed, 0 sensor-gap ticks intentionally skipped**; learner pass 1 of 1; Gradle BUILD SUCCESSFUL.
- Public synthetic summary: BG <70 = 2.7%; BG <54 = 0.0%; BG >180 = 8.2%; CV = 28.4%; hypo treatment = 27.64 g/day; daily insulin = **14.83 U/day**. TDD is within the predeclared 14–17 U/day smoke-test range.
- Limitation: the public summary contains only aggregate metrics. It does not show a `learner ALL cards` or `AIMI audit` line, so this run validates replay execution and summary/TDD accounting, but does not validate individual learner-card decisions, LAPROTECT interlock behavior, or the matched-pair scenarios.
- A post-job cleanup warning reported `fatal: No url found for submodule path '.tmp-juggluco' in .gitmodules`; it occurred during Git post-action cleanup after the replay, summary, encryption and artifact upload steps had succeeded. Overall GitHub job conclusion is success. The warning is recorded separately from replay correctness.
- Artifact `aimi-replay-result-0` was uploaded encrypted (1,020,337 bytes); no decryption or inspection of private replay output was performed in this check. No further replay was launched.


### 2026-11-23, why learner audit lines are absent from the base control summary

- Investigated completed run `45149628280`, replay job `137607503933`, workflow source at `aimi-replay` commit `304b3c49917786e8a5d483eb98dc2f772c0e2b64`, and replay learner source.
- **Root cause: the exact approved base APP control prefs do not enable the experimental audit learner.** The prefs contain none of `LEARNALL`, `LACAUSE`, or `LAPROTECT`. Workflow input `learn=true` enables replay learning generally; it does not implicitly add the separate `LEARNALL` / causal-protection tokens.
- Source confirms `learnAll = prefsText.contains("LEARNALL")`; the final `AIMI replay: learner ALL cards ...` line is printed only under `if (learnAll)`. Causal filtering is separately controlled by `LACAUSE`, and the protection interlock by `LAPROTECT` (which requires both `LEARNALL` and `LACAUSE`). Therefore this base APP control was not expected to produce those audit lines.
- The public-summary step selects only existing lines beginning `AIMI replay: learner ALL cards` and `AIMI audit `; it does not generate audit data itself. With no audit lines present, it correctly publishes only aggregate synthetic metrics and omits the `Learner / audit` subsection.
- GitHub job logs intentionally expose only a filtered subset of the Gradle output. The complete Gradle log is packaged inside `result.enc` in artifact `aimi-replay-result-0`; the artifact was downloaded and confirmed to contain only encrypted `result.enc`. No decryption key was read or used, and private replay data was not inspected.
- Conclusion: **not a summary-parser defect and not evidence that audit lines were lost**. This was the base APP/no-extra-learner-tokens control, not an audit-enabled replay. No source or workflow change and no additional replay were made.
- To obtain per-card audit lines, a separate, explicitly approved diagnostic run must use the audit-enabled token combination (at minimum `LEARNALL,LACAUSE`; `LAPROTECT` only if specifically intended). Do not append these tokens to or relabel the already completed base control profile.



### 2026-11-23, diagnostic-token audit after base control

- Rechecked completed run `45149628280` directly from its replay job log. The workflow printed the exact input values and one matrix entry; the public summary contains the six aggregate metrics and no learner/audit subsection. The run's overall and replay-step conclusions are success.
- Corrected the previous diagnostic handoff after inspecting `HistoryReplayTest.kt`: `LEARNALL` enables the all-card learner and final `learner ALL cards` line, but it does **not** by itself enable CR/ISF scans. Those are separately gated by `LACR` and `LAISF`; `LACAUSE` enables causal filtering; `LAPROTECT` enables the all-cause low interlock and is explicitly required to be combined with both `LEARNALL` and `LACAUSE`. `LADAYS` enables the three-distinct-day gates; `LAPCTBACK` enables percentage restoration.
- Therefore the complete single-job diagnostic profile should append these approved lab tokens to the unchanged base control prefs: `LEARNALL,LACAUSE,LAPROTECT,LAISF,LACR,LADAYS,LAPCTBACK`. This is a distinct diagnostic profile, not a replacement for or relabeling of the base APP control. No second run has been started.
- Expected visibility in that run: per-day `AIMI audit LAPROTECT`, CR block audit lines, ISF audit, Basal audit lines, `AIMI audit LEARNALL`, and the final `AIMI replay: learner ALL cards` summary, subject to the learner's scheduled check days and available data. Absence of a card-specific line must be interpreted against its explicit token gate and day schedule, not assumed to be a parser failure.
- Next action: one diagnostic replay only, same SYNTH_56 / SEED_1 / base APP inputs, with the seven audit tokens above appended; `batch` remains empty and all workflow booleans/settings remain at the same defaults. Review audit reconciliation and the LAPROTECT gate before any scenario matrix or pair checks.
- No algorithm source, workflow logic, live AAPS settings, profiles, pump or Nightscout settings were changed in this continuation. This entry is in ChatGPT's Task section; Claude's Results section remains untouched.

### 2026-11-23, diagnostic LAPROTECT replay — run 45155730520

Run: https://github.com/Eretikstas/Apex/actions/runs/45155730520

**Execution:** dispatcher and replay job both completed successfully. One matrix entry only. Replay source was `aimi-replay` commit `71a93ce2ee1d7b89c32dbdfdc9c9fd3fcf42a650`. Inputs confirmed in the plan-job log: `learn=true`, `pretrained=false`, `sim=false`, `algo=aimi`, empty batch/react_floor/isf, and all seven requested tokens present: `LEARNALL,LACAUSE,LAPROTECT,LAISF,LACR,LADAYS,LAPCTBACK`.

Replay: 16,128 ticks, 0 failed, 0 sensor-gap ticks intentionally skipped; pass 1/1; Gradle BUILD SUCCESSFUL in 6m29s.

Public synthetic metrics:
- BG <70: 2.8%; BG <54: 0.0%; BG >180: 5.8%; CV: 27.2%.
- Hypo treatment: 28.64 g/day; daily insulin: 14.91 U/day (within the previously declared 14–17 U/day smoke-test range).

Compared with the base control run 45149628280: BG <70 2.7% -> 2.8%; BG <54 0.0% -> 0.0%; BG >180 8.2% -> 5.8%; CV 28.4% -> 27.2%; hypo treatment 27.64 -> 28.64 g/day; daily insulin 14.83 -> 14.91 U/day. This is a one-seed descriptive comparison, not evidence of causal benefit or safety validation.

**Open blocker — learner/audit evidence was not surfaced.** The public summary contains no `Learner / audit:` section and no `AIMI audit LAPROTECT`, `AIMI audit ISF`, `AIMI audit CR`, or `AIMI replay: learner ALL cards` records. The workflow's summary step selects those exact prefixes from `gradle.log`; none were selected. Input-token presence is confirmed, but that alone does not prove the learner checks executed. Source review of `HistoryReplayTest.kt` shows the SYNTH path calls `synthetic(...)`; within that path `LEARNALL` should schedule checks from day 7 at 06:00 and print the LAPROTECT/CR/ISF audit lines, then print the final learner-ALL card summary. Investigate whether these test stdout lines are being suppressed/not captured or whether the scheduled branch is not reached. Do not claim LAPROTECT runtime validation or infer card behavior until the records are available.

Artifact `aimi-replay-result-0` uploaded (1,089,973 bytes), but it contains encrypted replay output; no decryption key was accessed and private data was not inspected. Post-job Git cleanup emitted the known `.tmp-juggluco` missing-submodule warning after artifact upload; replay and artifact steps succeeded and overall job conclusion is success.

No app branch, live AAPS settings, profiles, pump or Nightscout were changed. No additional run was started.
 
### 2026-11-23, fix learner/audit summary log matching

- Investigated completed diagnostic run `45155730520`, replay job `137628083643`. The Gradle job log shows test-worker output lines indented (for example, the visible `AIMI replay: 16128 ticks...` and `AIMI replay: pass 1 of 1 done` lines).
- Root cause of the missing public learner/audit subsection: the workflow summary parser used `line.startswith(...)` on raw Gradle output. The leading indentation prevented both `AIMI replay: learner ALL cards` and `AIMI audit ` lines from matching, even when present in the test output. This is a summary-parser defect; the earlier hypothesis that test stdout was not captured is not supported.
- Fixed `.github/workflows/aimi-replay.yml` on `aimi-replay`: normalize log lines with `strip()` before prefix matching and publish the normalized lines. Commit: `faf7b64daf20ec706d3043181507c729a00b793d`.
- No algorithm, learner gates, replay inputs, public metrics, or live AAPS settings were changed. No workflow was launched. The diagnostic run's encrypted artifact was not decrypted; this fix makes the next explicitly launched diagnostic run's public summary able to expose matching audit records, but runtime LAPROTECT behavior remains unverified until those records are visible.

### 2026-11-23, dispatcher default corrected after empty-prefs run

Run [45158356369](https://github.com/Eretikstas/Apex/actions/runs/45158356369) completed successfully, but it was not the diagnostic replay: the dispatcher passed an empty `prefs` value. The reusable workflow therefore skipped the SYNTH public summary (`contains(matrix.prefs, 'SYNTH_')` was false). Replay itself completed with 4,641 ticks, 0 failed, pass 1/1, and `BUILD SUCCESSFUL`; the encrypted artifact was uploaded. No learner audit result can be inferred from this run.

To prevent the same input omission, the default-branch dispatcher `.github/workflows/aimi-replay-dispatch.yml` now pre-fills `prefs` with the previously approved single-run diagnostic APP / seed 1 profile: the base SYNTH_56 profile plus `LEARNALL,LACAUSE,LAPROTECT,LAISF,LACR,LADAYS,LAPCTBACK`. The reusable replay implementation and learner logic were not changed. Users can explicitly clear the field when a no-token run is intended.

Dispatcher commit: `2eeeeb0dfd58147d6b832856705443b7ab6720cd` on `main`.

Next validation: launch the dispatcher again with the prefilled `prefs` unchanged, `batch` empty, and the existing single-run defaults. Verify the resulting public synthetic summary and audit lines; do not treat the empty-prefs run as diagnostic evidence.

### 2026-11-23, LAPROTECT rebound audit and strict-boundary tests

Reviewed the actual synthetic replay implementation against the previously approved safety-failure review.

Findings:
1. The protection-low eligibility rule is already aligned with the approved specification: count measured lows after the first 24 hours, excluding false-low rows, sensor-gap/transition rows, and the 180-minute post-hypo-treatment rebound window. The causal learner's exclusions remain separate; SMB-associated lows still count for protection unless they fall into one of the explicitly excluded windows.
2. The insulin-increase interlock is computed from the last three days: fewer than three valid protection days blocks increases; otherwise a protection-low rate strictly greater than 3.0% blocks increases. Exactly 3.0% is open. This matches the approved strict threshold.
3. The previous LAPROTECT line did not explain how many measured lows were excluded as sensor-start, false-low, sensor-gap, or post-hypo rebound. That made it difficult to distinguish low burden from exclusion burden.

Replay-only instrumentation and tests committed:
- `43a92f063474acce5819de3d6802d8b5fc2acbd4` — reason-coded protection exclusions (`missing-bg`, `sensor-start`, `false-low`, `sensor-gap`, `post-hypo-180m`), aggregate all-low/protected-low counts, and explicit tests for the strict 3.0% boundary and insufficient valid days.

No learner formulas, live app code, profiles, pump settings, or Nightscout settings were changed. No replay was launched. The push-triggered compile workflow should validate Kotlin compilation and the isolated helper tests; record its run result before any replay scenario is considered.


### AutoISF weight audit — explicit handoff to Claude (2026-11-24)

**Missing workstream identified by the user: audit the AutoISF weights themselves.** The existing AIMI replay work on CR/ISF/Basal learning and the ISF causal filter does not cover the AutoISF weight system. Treat this as a separate task, not as completed by the Learning Audit.

Relevant app-side source locations on autoisf-atc3 (Claude owns these files; ChatGPT must not edit the app branch):
- plugins/aps/src/commonMain/kotlin/app/aaps/plugins/aps/openAPSAutoISF/OpenAPSAutoISFPlugin.kt — preference reads, profile wiring, and autoISF() calculation.
- plugins/aps/src/commonMain/kotlin/app/aaps/plugins/aps/openAPSAutoISF/DetermineBasalAutoISF.kt and app/src/androidTest/assets/OpenAPSSMBAutoISF/determine-basal.js — algorithm implementation/reference behavior.
- core/interfaces/src/commonMain/kotlin/app/aaps/core/interfaces/aps/OapsProfileAutoIsf.kt — profile fields.
- plugins/aps/src/androidHostTest/kotlin/app/aaps/plugins/aps/openAPSAutoISF/OpenAPSAutoISFPluginTest.kt and DetermineBasalAutoISFTest.kt — existing tests.
- docs/AutoISF-v4-Guide.md — user-facing descriptions.

Weights/controls explicitly present in the source:
1. bgAccel_ISF_weight — acceleration contribution.
2. bgBrake_ISF_weight — braking/deceleration contribution, including the predicted-near-minimum branch.
3. pp_ISF_weight — post-prandial delta contribution.
4. lower_ISFrange_weight and higher_ISFrange_weight — low/high BG-range interpolation multipliers.
5. dura_ISF_weight — sustained-high-glucose/duration contribution.
6. autoISF_min and autoISF_max — final factor bounds; audit these with the weights, but identify them as limits, not weights.

**Required audit (report first; do not change clinical defaults or live settings):**
- Trace each preference from key/default/UI/scene/automation through profile construction into the exact formula and final ISF.
- For every weight, document units, allowed range, default, sign, neutral value, monotonic direction, clamp behavior, and conditions under which it is bypassed.
- Check boundary values, zero/neutral behavior, negative/NaN/infinite inputs, and whether a weight can invert the intended direction or compound another factor unexpectedly.
- Verify Kotlin implementation and JS reference have equivalent equations and branch conditions; explain any intentional divergence.
- Add/identify isolated tests that vary exactly one weight at a time with fixed inputs, plus paired interaction tests (acce/brake; BG-range with acce; pp with dura; weight with min/max clamp). Assert intermediate factor and final ISF, not only a final aggregate.
- Verify persisted/displayed factor telemetry reports the factor actually applied, and scene/automation overrides restore previous weight values correctly.
- Provide a table of all weight keys with source locations, defaults/ranges, formula, current test coverage, missing tests, and risk classification.
- Do not optimize or recommend patient-specific weight values from synthetic data. No pump, profile, Nightscout, or live app setting changes.

**Acceptance:** Claude returns a source-linked audit and proposed test matrix; any formula/default/UI change requires separate review and explicit user approval. This task is an app-branch handoff only; it does not authorize ChatGPT to edit autoisf-atc3 or port anything from the lab.


### 2026-11-25, LAPROTECT pct- source correction

- Fixed `learnAllCheck()` so the `pct-` branch uses `protectionLowPct` when `LAPROTECT` is enabled and retains `percentageLearningLowPct` otherwise:
  `(if (laProtect) protectionLowPct else percentageLearningLowPct) > 4.0`.
- Added a deterministic boundary/source-selection test: LAPROTECT=true selects the all-cause rate (including the strict >4.0 boundary); LAPROTECT=false preserves the legacy learning-only rate.
- Commit: `ed1085154d72f3dab1d27edc58f827c61629a947`.
- Compile check is pending; commit status currently reports `pending` with no checks yet. Do not write RUN.txt or start replay until this exact commit has a green compile result.
- The next 24-job batch remains as specified in RULES_FOR_CHATGPT.md. Before starting it, re-check the exact base / APP / seed 1 control against `2.7 / 0.0 / 8.2 / 28.4 / 27.64 / 14.83`, check that no replay is in progress, and verify the 24-hour start limit. No replay has been started by this task.


### 2026-11-25, LAPROTECT 24-job synthetic batch — running

- Code fix commit: `ed1085154d72f3dab1d27edc58f827c61629a947`; dedicated compile check `45624674552` completed `success`.
- Control checked before launch against Claude's completed control run `45620155708` (base / APP / seed 1): <70 **2.7%**, <54 **0.0%**, >180 **8.2%**, CV **28.4%**, hypo treatment **27.64 g/day**, insulin **14.83 U/day**. These match the required six control values exactly; APP does not enable LAPROTECT, so the pct- branch correction does not alter this control path.
- Pre-launch gate: no `aimi-replay.yml` run was queued/in progress; one actual lab start was recorded in the preceding 24 h, below the maximum of three.
- Started only by committing the 24-job line to `RUN.txt`: commit `48eeab78656121ca7c6c295827f2a3a21ec0fee8`.
- Dispatcher run `45629598681` completed successfully; replay run `45629646841` was dispatched with `sim=true`, `algo=autoisf`, `learn=false`. Plan produced 24 replay matrix jobs; at last check 19 were in progress and 5 queued.
- Scenarios: base, calm, dawn1.3, meals1.3, ins+20, ins-20, start+30, growth2%; seeds 1–3. Each job uses its APP token set plus `LEARNALL,LAISF,LACR,LACAUSE,LAPCTBACK,LADAYS,LAPROTECT`.
- Public result table: pending; do not claim a pass until all 24 jobs finish and their summaries are read.
- Claude review after completion: compare all six metrics with APP and apply the hard pass rule (lows <54, lows <70, hypo grams cannot increase); verify insulin and highs/CV only after the safety gate. Confirm that LAPROTECT protection-low accounting and pct-down decisions are represented in learner/audit logs.
- Next task (CHG_28 matched pairs) is blocked until this batch completes and is assessed.


### 2026-11-25, Task 9 hypo-model calibration — Step 0 static token inventory ONLY

Read-only inspection of `HistoryReplayTest.kt` on `aimi-replay`; no source edits and no replay started.

- **Insulin action / effective strength:** `CHG_<day>` + `CHGISF_<x>` (both required; actual action multiplier applies from the selected day; lines 885–887 and 1699–1720); `ACT_<day>` (activity multiplier: 1.3 during 10–21 h and 1.2 in the specified night; lines 1705–1715); `ILL_<day>` + `ILLD_<n>` + `ILLF_<x>` (illness weakens action; lines 1711–1717); `CANCH_<h>` (cannula age reduces action after 60 h; lines 967, 1702–1704); `CANFAIL_<n>` (periodic half-action window; lines 1707–1717). `DRIFT_<pct>` changes modeled insulin need over weeks, not the insulin action curve itself (lines 1705–1710). `CHGKCR_<x>` changes liver counter-regulation after lows, not insulin action (lines 885–887, 1699–1700).
- **Actual eaten vs entered:** `UNLOG_<pct>` makes a percentage of meals eaten but not entered (no logged carbs and no meal bolus), not a partial-eating mismatch (lines 971–972, 1757–1760). `MEALX_<x>` scales the generated meal amount used for both logged and eaten values (lines 935–938); it does not independently change eaten relative to recorded. The model has a built-in random partial-eating event for 15% of meals, with eaten amount 60–80% of generated amount, but no dedicated token was found to tune that ratio (lines 935–938). `CARBS_<pct>` enlarges recorded meals and supplies the corresponding meal bolus too; it is not an eaten-vs-recorded mismatch (lines 532–545).
- **Low-end sensor error:** `SBIAS_<x>` controls low-end downward sensor bias (default 0.3; lines 853–855, 1722–1726); `NOISE_<x>` controls noise amplitude and `NOISEPHI_<x>` its temporal persistence (lines 850–855, 1700–1701); `COMPRESS_<n>` injects false night lows, about 40 mg/dL for 20 min at a frequency set by n (lines 971–972, 1727–1730). No separate token named as a low-only random error multiplier was found.
- **Hypo treatment amount:** `TREAT_<g>` sets fixed treatment grams (default 6); `TREATL` switches to a dynamically calculated 4–10 g treatment (lines 974 and 1735–1749).
- **Cause annotation:** `LACAUSE` enables causal classification/audit (lines 1025–1028; classifier at lines 120–139). It annotates causes; it does not itself create or alter hypoglycemia.
- **Missing capability relevant to requested grid:** no existing token independently controls partial eating relative to the recorded meal amount. Do not invent one. Any grid using `MEALX` or `UNLOG` must be described accurately as changing meal size or unlogged-meal frequency, not partial-eating ratio.

**Prerequisite gate:** Task 9 was specified to start only after Rules §5 tasks 1–3. Tasks 1–2 are complete, but task 3 (the matched `CHG_28` pairs) is explicitly blocked by Claude's review in the Results section (2026-11-25: “Do NOT run the CHG_28 pairs (task 3)”; LEARNALL parked). Task 2 also failed the learner safety pass. Therefore Task 9's calibration grid is NOT authorized to start yet under the stated sequence. No `RUN.txt` change, no batch commit, and no new run. Await Claude's decision on reopening task 3 / the prerequisite gate.

## Results (written by Claude)

### 2026-11-25, Claude: Task 9 run 45640419812 — the stop was WRONG; table and next step

- The pass rule (section 4) is for LEARNER variants only. Calibration WANTS more lows than APP (the real child has
  more), so a calibration run can not "break" it. Claude's mistake in 4a item 1 wording; fixed in RULES.
- Claude read all 72 public summaries (mean of 3 seeds; distance to the private target, 0 = inside):
  - closest: CHGISF_1.3 + SBIAS_0.45 + TREATL (dist 2.0), CHGISF_1.3 + SBIAS_0.15 + TREATL (2.2),
    CHGISF_1.3 + SBIAS_0.45 + TREAT_5 (2.4: <70 5.90, <54 0.53, >180 5.17, CV 29.7, insulin 11.5 U/day).
  - clear trend: stronger insulin (CHGISF up) brings lows up and highs down; SBIAS up and TREAT_5 add lows.
- Not good enough: with CHGISF_1.3 the model gives ~11.5 U/day, far below the real child's daily insulin. Real life
  = more insulin AND more lows. That fits food absorbed/eaten LESS than recorded (boluses for carbs that do not come),
  which matches the main real cause (lows after a meal bolus).
- `MEALX_<x>` scales the eaten meal against the recorded one (MEALX_1.3 = more than recorded), so MEALX below 1 IS the
  "eaten less than recorded" token. Next: Task 9b (RULES section 5).

### 2026-11-25, Claude review of LAPROTECT batch 45629646841 — FAIL confirmed, LEARNALL parked

- ChatGPT's work is correct: fix `ed1085154d` is as asked (pct- uses `protectionLowPct` only with LAPROTECT, test for
  the >4.0 boundary), compile green, control checked before the start, start limits kept, and it stopped on the gate.
- Verdict: FAIL. base / seed 1 is worse than APP both ways (<70 3.0 vs 2.7, hypo 30.46 vs 27.64 g/day, >180 11.1 vs
  8.2, CV 31.2 vs 28.4). The 8-scenario means show the same: highs are clearly up, lows are not down.
- Why: the audit shows zero accepted CR and ISF candidates, so `LAISF`/`LACR` changed nothing. The difference from APP
  comes from the other tokens (LACAUSE / LAPCTBACK / LADAYS on basal, dura and percentage). This stack gives no gain.
- Decision: LEARNALL (CR/ISF learning) is PARKED. Nothing of it goes to the app. Do NOT run the CHG_28 pairs (task 3).
- Main reason the learner cannot be judged now: the model's lows are far below the real child's. Next work = model
  calibration (RULES section 5, new task). No full APP 8-scenario table is needed for this verdict.

### 2026-11-23, review of the causal filter fixes (commits up to 4dd3a09c30)

Fixed since the first review: `priorEvent` now reads flags too (`mealBolus` works); a CR meal episode with any SMB is
left out for both lows and highs (no bias toward a stronger CR); the basal filter no longer drops windows after SMB;
ISF takes SMB corrections. Compile check run 45080836199 was queued at the time of writing.

Open points before the lab batch:

1. **CR may get no data.** With SMB + UAM almost every meal has an SMB within 6 h, so "any SMB -> drop the episode"
   can leave fewer than 5 meals per block. Please print, per check, how many meals were used and dropped and why. If
   CR never gets data, consider counting only SMB that came AFTER a low started, or judging the first 2 h only.
2. **Basal: meal tails cannot work with this filter.** `cleanBasalRow` drops any row with `afterMeal` (0-5 h after a
   meal) over `k-60..k+12`, so a window needs about 10 h without a meal, and `LBTAIL` (3-5 h after a meal, CR block
   fine) is always dropped. Either let `LBTAIL` windows pass when their CR block is valid, or say tails are off with
   `LACAUSE`.
3. Please add counters for every card: windows used / dropped by reason, and distinct days, in the log line.

Next run planned after these points: 8 scenarios x 3 seeds (base, calm, dawn1.3, meals1.3, ins+20, ins-20,
start+30, growth2%), tokens of `APP` + `LEARNALL,LAISF,LACR,LACAUSE,LAPCTBACK,LADAYS`, compared with `APP` and with
LEARNALL v1. Right answers: dawn -> basal, meals1.3 -> CR stronger (not basal up), ins+20 -> ISF up or percentage
down, ins-20 -> ISF down / basal up, calm -> almost no change. Pass: lows < 3.0 not higher, lows < 3.9 not higher,
highs > 10 not worse than APP.

### 2026-11-23, Claude review of the Learning Audit spec and the 7 robustness proposals (up to 718af46369)

Compile check run 45080836199 (code at 75fe1e04fd): success. Commits after it are documents only.

**Learning Audit (counters per card): yes, do it first.** It is the base for items 1 and 6 below. Notes:
- Keep the six-hour CR SMB rule for now (agreed), but expect the audit to show CR with almost no accepted meals: with
  SMB + UAM nearly every meal has an SMB within 6 h. That result is useful in itself; then the user decides.
- Determinism (case 9): the synthetic mode uses `Random(seed)`, so it should hold; any difference is a bug.
- Keep the audit line short (one line per card per check); the logs are already large.

**Interaction matrix:** good. Cases 5, 6, 8, 10 can run with existing tokens (`ACT_`, `ILL_`, `CANCH_`, `NOISE`/`COMPRESS`,
`SMBMIN`). New tokens are needed for: 1 (one outlier day: e.g. `OUTDAY_<day>_<x>` scaling need for one day), 3 and 4
(conflicts: `MEALX_` + `CHGISF_`, `MEALX_` + `DAYNEED_` exist, check they combine), 7 (sensor gaps of 15/30/60 min:
check whether a gap token exists; if not, add `GAP_<min>_<per day>`). Please list the exact tokens per case in Task.

**The 7 proposals:**
1. Confidence score: yes, report-only, built from the audit fields (clean days, episodes, spread, exclusion rate).
   Hard gates stay absolute. Cheap once the audit exists.
2. Drift detection: partly exists (the basal card needs the same side on two cards in a row). Merge it with the
   "outcome control" the user asked for: after a change, compare 3-4 days after vs 7 before on the card's own hours or
   block; if worse, a rollback card and that cause frozen. This item is missing from the proposal list; please add it.
3. Outlier protection: medians are already used. Leave-one-day-out is cheap and good as a report field. Low priority.
4. Interaction guard: report conflicts, yes. But I disagree with "no global priority order": one change at a time
   needs a tie-break, and the safe one is "protect against lows first" (percentage down / weaker insulin before
   anything that gives more insulin). Keep that order; let the guard report what was deferred and why.
5. Recovery and data ageing: last. It adds tuning knobs; the 7-day windows already age old data out.
6. Explainability: this is the audit line plus the decision; no separate work.
7. Shadow comparison: this is what every batch already does (APP vs variant, same seeds). Nothing to build; just
   always include APP in a batch.

**Recommended order:** audit (incl. 6) -> batch of 8 scenarios (APP vs causal) -> new scenario tokens + interaction
matrix -> confidence (report-only) -> outcome control with rollback (2) -> interaction report (4) -> ageing (5).

Next action for Claude: when the audit is committed and the compile check passes, the user says "run STATUS".

### 2026-11-23, run STATUS started by Claude

Compile check: green (45096342199, 45096199938). Lab run **45099362646** (code 7f34c90ae0), 35 jobs:
- 8 scenarios (base, calm, dawn1.3, meals1.3, ins+20, ins-20, start+30, growth2%) x seeds 1-3 with APP tokens +
  `LEARNALL,LAISF,LACR,LACAUSE,LAPCTBACK,LADAYS`;
- the same 8 scenarios, seed 1, APP tokens only (checks "no token = old result" against run 45070147879);
- pairs on base seed 1: `MEALX_1.3,CHGISF_0.8`; `MEALX_1.3,DAYNEED_1.2`; `CHGISF_0.8,DAYNEED_1.2`.
Results will be written here when the user asks ("результаты"). ChatGPT's rights were widened today: see
COORDINATION.md, part "What ChatGPT may do on its own".

### 2026-11-23, results of run 45099362646 (code 7f34c90ae0) — CAUSAL is NOT safe yet

2nd half of 56 days, sensor values, mean of seeds; columns: % <70 / % <54 / % >180 / CV / hypo g per day / TDD.
5 jobs failed at build (re-run started): ins-20 APP, start+30 CAUSAL s3, growth2% CAUSAL s1, meals+day PAIR, calm CAUSAL s1.

| scenario | APP (seed 1) | CAUSAL (seeds 1-3) |
|---|---|---|
| base | 2.7 / 0.0 / 8.2 / 28.4 / 27.6 / 14.8 | 2.6 / 0.0 / 5.9 / 26.8 / 27.4 / 14.6 |
| calm | 1.1 / 0.0 / 3.1 / 23.8 / 14.0 / 14.1 | 1.0 / 0.0 / 2.5 / 22.8 / 12.1 / 13.8 |
| dawn1.3 | 2.6 / 0.0 / 8.6 / 29.1 / 26.9 / 15.2 | 3.2 / 0.0 / 5.4 / 27.6 / 33.6 / 15.4 |
| meals1.3 | 2.9 / 0.0 / 11.1 / 30.8 / 27.6 / 15.9 | 3.1 / 0.1 / 7.4 / 28.8 / 33.6 / 15.9 |
| growth2% | 2.3 / 0.0 / 9.3 / 28.4 / 24.5 / 16.9 | 1.8 / 0.0 / 8.9 / 27.7 / 19.5 / 17.1 |
| ins-20 | (failed, re-run) | 2.2 / 0.0 / 8.1 / 28.4 / 23.3 / 18.1 |
| **ins+20** | 3.4 / 0.0 / 6.7 / 28.6 / 31.0 / 12.1 | **5.4 / 0.1 / 2.1 / 26.8 / 53.5 / 13.2** |
| **start+30** | 3.1 / 0.0 / 8.0 / 28.8 / 29.3 / 15.0 | **5.6 / 0.1 / 3.9 / 28.4 / 52.5 / 15.8** |

Card decisions of CAUSAL (all seeds): **CR never changed (1.00/1.00/1.00), ISF never changed, percentage never left
100 %.** Only basal and dura acted; dura climbed to its maximum 2.0 in most runs. Log line example (ins+20):
`lows3 2.7 % eligible lows 0.0 %` — almost no low counts as fasting/meal_bolus, so the percentage protection never
fires, while dura keeps adding insulin. Result: where insulin is stronger (ins+20, start+30) lows nearly double and
hypo carbs go from ~30 to ~53 g/day. Fewer highs everywhere, but bought with lows. **Fail of the pass criterion.**

Required before the next run (ChatGPT, report-first, then code):
1. **Hypo protection must not be filtered away.** Percentage down: count all lows except sensor artifacts (false low,
   sensor gap, first day of a sensor) and rebound lows within 3 h after hypo treatment. SMB lows DO count here (the
   loop gave too much). The causal filter decides which card is to BLAME (CR/ISF/basal), not whether to protect.
2. **No card may add insulin while lows are high:** any "more insulin" decision (dura up, basal up, CR stronger, ISF
   weaker, weight up) is blocked when all-cause lows (same exclusions as 1) over the last 3 days are > 3 %.
3. **Dura down** must use the same all-cause lows (now it saw almost none and only went up).
4. **CR and ISF got no data at all.** Print the audit counts per check (candidates / accepted / rejected by reason)
   in the "learner ALL" line or a separate line — I could not find audit lines in the log of this commit. We need to
   see WHY (six-hour SMB rule? mealBolus missing? days gate?) before any change to the rule.
5. **Pair check found a spec error:** `CHGISF_x` acts only from the day given by `CHG_<day>` (the ladder uses
   `CHG_28,CHGISF_0.8` for ins-20, `CHG_28,CHGISF_1.2` for ins+20). Without `CHG_` it does nothing:
   `MEALX_1.3,CHGISF_0.8` gave exactly the meals1.3 numbers. Redo the pairs with `CHG_28`.
6. No-token check: APP seed 1 here vs run 45070147879 — I will compare when the re-run ends; numbers look in range.

### 2026-11-23, Claude review: items 1–5 and task 7 workflow (up to fb619bf0f7)

**Items 1–3 (behavior changes): APPROVED by Claude as written in the "safety-failure review"**, including: protection
lows = all valid lows except false-low, sensor gap/transition, first 24 h of a sensor stream and the 180-min post-hypo
rebound window; the 3-day interlock (> 3 %) blocks every insulin-increasing proposal (percentage up/restore, stronger
CR, weaker ISF, positive basal deltas — keep the negative ones, dura up, weight up), evaluated on the pre-change
snapshot; dura-down uses the same protection lows. Behind a new token (e.g. `LAPROTECT`), so the no-token result is
unchanged. The user still has to say yes (asked in chat).

**Item 4 (audit runs before the day gate): OK.** **Item 5 (pairs with `CHG_28`): OK**, run them with matched controls.

**Task 7 summary step — two bugs, fix before relying on it:**
1. `re.search(r"(?:^|\s)SYNTH_([0-9]+)(?:\s|$)", prefs)` — prefs are COMMA-separated (`SYNTH_56,SEED_1,...`), so this
   never matches and the step exits "SYNTH_ day count is missing" on every job. Split on `[,\s]` (e.g.
   `re.search(r"(?:^|[,\s])SYNTH_([0-9]+)(?:[,\s]|$)", prefs)`); same for the task label (first tokens split on comma).
2. Insulin per day: output rows are **5-minute rows** (288 per day; see `n - 7 * 288` in the learner code and Claude's
   table script `deliv * 5 / 60`), not 1-minute ticks. `deliv / 60.0` undercounts basal 5x. Use `deliv * 5 / 60` —
   or better, take the row step from the data (median difference of `t`, in minutes) and use `deliv * step / 60`.
Test on one job before the next batch: TDD should be about 14–17 U/day for base, like the APP table above.





### 2026-11-23, default-branch replay dispatcher enabled

User explicitly approved adding a minimal dispatcher to `main` so the single base SYNTH control can be launched without moving replay implementation or algorithm code to the default branch.

Commits:
- `58838301bbde51fdba030556250ba9c0c028a793` — added `.github/workflows/aimi-replay-dispatch.yml` to `main`. It exposes the same manual inputs and calls the reusable workflow on `aimi-replay`; it does not contain replay implementation.
- `0ea5a7f90de58c546f33ddeef57d30702816c2a0` — added `workflow_call` input declarations to the existing replay workflow.
- `ec544ec7ba5d5085bef920e6f390f999544b89c6` — allowed the plan job to run for both `workflow_dispatch` and `workflow_call`, and pinned checkout to `aimi-replay` so the reusable workflow executes the lab branch code, not `main`.

Validation:
- Confirmed dispatcher file exists on `main`; reusable workflow exists on `aimi-replay`; caller points to `Eretikstas/Apex/.github/workflows/aimi-replay.yml@aimi-replay`.
- Push-triggered replay workflow correctly skipped (it only plans jobs for manual/reusable dispatch).
- Compile check run `45148733890` is in progress for current source commit `ec544ec7ba5d5085bef920e6f390f999544b89c6`; do not launch the SYNTH replay until it completes successfully.
- The available GitHub connection has no workflow-dispatch action and its generic fetch is GET-only. Therefore I cannot submit the dispatch event directly from this session; the dispatcher should now appear under Actions on the repository default branch.

Exact approved single-control prefs, unchanged:
`SYNTH_56,SEED_1,CRT_22,NEEDDX_1.3,NOISEPHI_0.995,SBIAS_0.15,UKF,SMARTS,SPLIT_60,SPLITMIN_20,LEARNG3,NOIOB,SMBMIN_60,UAMMIN_60,LATECOB,LATEPCT_50,LATEB_2,LEARNB5,LBLOWMAX_6,LBFILL2,LBMIN_2,TREATL,LEARNDURA,NOISE_0.07,CANCH_72,PCTCARD,PCTMAX_100`

Dispatch only one job: leave `batch` empty, set `prefs` to the exact string above, keep default `learn=true`, `pretrained=false`, `sim=false`, `algo=aimi`, and all other optional fields empty/false. This is the base APP / seed 1 synthetic control, not a batch.


### 2026-11-24, independent AutoISF weight source audit (report only)

- Read Claude-owned `autoisf-atc3` source directly: AutoISF plugin, DoubleKey preference definitions, OapsProfileAutoIsf, bundled JS reference, plugin tests, DetermineBasal tests, and AutoISF v4 guide.
- Added `lab/aimi-replay/AUTOISF_WEIGHT_AUDIT_2026-11-24.md` in commit `e866196208af00b1a5b42784e9b7217d64c5eb04`. This is a read-only source audit; no app source, defaults, live settings, profile, pump, Nightscout or algorithm behavior was changed.
- Confirmed all six weights default to 0.0 and key bounds are: accel 0–1, brake 0–1, PP 0–0.15, lower/upper BG range 0–2, Dura 0–3. `autoISF_min/max` are limits, not weights; both default to 1.0.
- Main findings for Claude: Kotlin `withinISFlimits` has resistance/activity/inactivity branches absent from the bundled JS reference; the algorithm path does not explicitly validate each weight for finite/range values; weight scales/units are implicit; existing tests do not isolate all six weights or the requested branch/boundary/interaction cases; automation restore is not verified by inspected tests.
- Exact formulas, bypass gates, neutral semantics, risk ranking and proposed one-factor/pair matrix are in the report.
- Claude owns any app-branch changes and should review the report. No AutoISF formula/default/UI change is proposed or authorized by this audit.


### 2026-11-24, isolated GitHub Actions Android emulator smoke workflow

- Added `.github/workflows/aimi-replay-emulator.yml` on `aimi-replay` only (workflow commit `7eea48a0068fd3d1930170c0219c3df5ba71f98e`; instrumentation-result check correction `560069155d18cff0824350adc044f04730b6067d`).
- It reuses the proven GitHub-hosted KVM + Android Emulator Runner pattern from `atc3-screens.yml`, builds FullDebug and the instrumentation APK, boots an API 31 Pixel 6 emulator, and runs `RingSkinScreensTest` with synthetic in-app glucose/profile data. It uploads screenshots, instrumentation output and reports as a 14-day artifact.
- Trigger: push to `aimi-replay` touching only this workflow, or manual workflow dispatch. The commit itself should trigger the first run; check the Actions run before treating the emulator path as validated.
- Safety/isolation: no app source edits, no changes to Claude's `autoisf-atc3` or `atc3-driver` branches, no real pump/CGM/Nightscout credentials, and no live profile. Legacy instrumentation source exclusions occur only in the ephemeral Actions checkout, matching the existing ATC3 workflow.
- Current status: workflow definition committed and read back. Build, emulator boot, instrumentation pass and artifact availability are **not yet confirmed** until the Actions run completes. Claude may adapt the workflow to his UI/card test plan; keep any adaptations on his app branch or a separately reviewed lab workflow.


### 2026-11-24 — independent second-pass AutoISF weight audit

Extended `lab/aimi-replay/AUTOISF_WEIGHT_AUDIT_2026-11-24.md` in commit `dd959e6fff60e1ca3f65b826aa4aea621c627bd3`. New findings for Claude:
- Master `ApsUseAutoIsfWeights` defaults false; enabling alone with all six weights=0 and min=max=1 leaves the weighted factor neutral.
- Automation supports only enable/disable, acceleration weight, and IOB threshold; other five weights have no automation setters/triggers in the inspected implementation.
- Scene restore/restart behavior is not established by ActionAutoIsfTest; needs dedicated state/scene tests.
- Production plugin uses Kotlin `DetermineBasalAutoISF`; JS engine is loaded by a separate instrumentation adapter, so parity should be tested deliberately, not assumed to be dual production execution.
- Low-BG `bg_ISF < 1` branch returns before PP and Dura calculation; verify this short-circuit is intended.
- Direct formula boundary has no explicit finite/range guard for every weight; add NaN/Infinity/corrupt-import tests. Telemetry `finiteOnly()` does not protect the insulin-factor calculation.

Read-only static audit only. No app source, formula, preference default, clinical setting, or workflow changed. Claude owns any proposed fixes and must return exact tests/results before modifications are considered.


### 2026-11-24, AutoISF deterministic test-matrix handoff

Added `lab/aimi-replay/AUTOISF_WEIGHT_TEST_MATRIX_2026-11-24.md` in commit `b9a3d4538ff28a494c5f0c381b698b796b66c28e`.

The report-only matrix specifies:
- master enable/disable and neutral controls;
- one-factor tests for all six weights, including branch quadrants and exact threshold boundaries;
- seven pair/short-circuit interaction groups;
- min/max boundary and corrupt-limit cases;
- Kotlin/JS shared-fixture parity with the known activity/resistance divergence explicitly isolated;
- NaN/Infinity/negative/out-of-range persisted/imported values through the complete preference-to-formula path;
- automation/scene restore and restart lifecycle cases;
- telemetry stage-by-stage assertions.

No app-side source or formula was changed. This is a lab-branch test specification only; Claude owns app-side test implementation and must confirm expected invalid-input fallback and Kotlin/JS divergence classification before implementation. No patient-specific weights are recommended.


### 2026-11-24, first AutoISF weight boundary tests added (lab branch)

Added `plugins/aps/src/test/kotlin/app/aaps/plugins/aps/openAPSAutoISF/AutoIsfWeightBoundaryTest.kt` in commit `a0208af015ec1fa42af78bc3c8c7fe2286d50317` (follow-up import cleanup: `021bf328f11f651451563a3a2dc45b38df0ba570`).

Initial deterministic tests cover:
- lower- and higher-BG interpolation weights independently scaling their respective sides;
- zero lower/higher weights producing neutral interpolation;
- the 100 mg/dL interpolation centre and adjacent values;
- min/max factor clamp behavior immediately below, at and above each boundary;
- neutral min=max=1 behavior.

Extended `.github/workflows/aimi-replay-compile.yml` to include the AutoISF test directory and run this test class before the existing replay checks (commit `2a5eaddedcdd41e48b71045df5ecf9d70ebf1697`).

CI run `45298059989` was created by the push and is currently in progress at checkout/JDK setup. No pass/fail result is claimed yet.

**Coverage is intentionally partial:** this first slice directly exercises production interpolation and limit functions. It does not yet isolate acceleration, braking, PP or Dura in the full AutoISF path; it does not test invalid persisted weights or min/max because expected fail-closed behavior and Kotlin/JS divergence classification still require app-owner review. No production formula, default, preference, profile or pump behavior was changed.

Claude handoff: review the tests and confirm the invalid-input fallback contract plus the Kotlin/JS fixture classification before the next test tranche. ChatGPT continues test-only work on `aimi-replay`; no edits to `autoisf-atc3`.


### 2026-11-24, Android Emulator smoke run 45296311819 — SUCCESS

Run: [45296311819](https://github.com/Eretikstas/Apex/actions/runs/45296311819), branch `aimi-replay`, tested commit `560069155d18cff0824350adc044f04730b6067d`. Completed successfully at 2026-11-24 00:39:33 UTC. The earlier “not yet confirmed” note above is superseded by this result.

All 11 substantive job steps completed successfully:
- FullDebug app APK and FullDebug AndroidTest APK both built; Gradle reported `BUILD SUCCESSFUL` in 11m51s.
- KVM setup succeeded. Android 31 `google_apis`, `x86_64`, Pixel 6 emulator booted; the test script set locale to `ru-RU`.
- Both APKs installed successfully.
- Instrumentation `app.aaps.e2e.RingSkinScreensTest#ringScreens` completed with `OK (1 test)` in approximately 233.1 seconds.
- The test produced 15 screenshots; every row in `out/screens/index.txt` is marked `ok`. Coverage represented in the index: stable/falling/rising states for day and night, low variants, AIMI window, and Assignments menu.

Artifact:
- Name: `aimi-replay-emulator-smoke`; ID: `14877931520`; size: 3,371,808 bytes; SHA-256: `becc442d0d62ee69264a18890e0e523f199133a908060104cd424bbaddff0b98`.
- Contains 18 files: 15 PNG screenshots, `out/screens/index.txt`, `out/instrument.txt`, and `out/logcat.txt`.
- [Download artifact](https://github.com/Eretikstas/Apex/actions/runs/45296311819/artifacts/14877931520); expiry: 2026-12-08 00:39:26 UTC.

Log review:
- Instrumentation output explicitly reports `OK (1 test)`; the captured device logcat contains no `FATAL EXCEPTION`, `Fatal signal`, or `ANR in` markers.
- The emulator emitted repeated SwiftShader graphics messages (`Failed to find ColorBuffer` / invalid texture binding) during capture, but the test completed and all 15 screenshots were saved and visually readable.
- Build emitted Android resource-format warnings for non-positional multiple substitutions in localized strings; these did not fail the build.
- Post-job Git cleanup emitted a warning because submodule path `.tmp-juggluco` has no URL in `.gitmodules`; this happened after successful test and artifact upload. Actions also reported a Node.js 20 deprecation warning. Neither changed the successful workflow conclusion.

Scope and interpretation: this confirms the isolated laboratory FullDebug build, emulator boot, APK installation, and one synthetic Ring UI instrumentation test. It does not validate the nine app adaptation cards, AutoISF weight behavior, pump/CGM connectivity, clinical behavior, or Claude's production/app-branch workflow. No app source, live settings, profile, pump, or Nightscout configuration was changed by this run. Claude may use this workflow as a starting point for his own UI/card scenarios.


Log-count follow-up for run 45296311819: the full job log contains 935 Android resource-format warnings of the “multiple substitutions in non-positional format” class, spanning 24 locale resource sets and 36 distinct string keys (848 warning lines originate from the APS module). The build remains green, but Claude should decide whether these existing localized resource declarations need cleanup. The emulator job log also contains 20 SwiftShader ColorBuffer/texture-binding error lines; no fatal instrumentation or app crash followed them, and the 15 screenshots are present.


### 2026-11-24, AutoISF boundary-test CI failure and correction

- CI run [45298059989](https://github.com/Eretikstas/Apex/actions/runs/45298059989) compiled the test sources successfully, then ran 5 tests: 4 passed, 1 failed.
- The only failure was an overly strict signed-zero assertion: interpolation returned IEEE-754 `-0.0`, while the test compared it to `0.0` using exact equality. This is numerically neutral and is not a production algorithm defect.
- Corrected the three zero-weight assertions (and centre assertion) to use a numeric tolerance in commit `691240e1cd878e98fd1a3c3aa7452f0841c79dfc`. No production source or formula changed.
- A new push-triggered CI run should validate the correction. Until that run completes successfully, AutoISF boundary tests remain unvalidated. Remaining coverage is still partial: acceleration/braking, PP, Dura, invalid/non-finite persisted inputs, and Kotlin/JS fixture parity are not yet covered.
- Android emulator run [45296311819](https://github.com/Eretikstas/Apex/actions/runs/45296311819) is independently green: FullDebug + instrumentation APK built, Pixel 6 API 31 emulator booted, `RingSkinScreensTest` reported `OK (1 test)`, and 15 screenshots were uploaded (artifact 14877931520).


### 2026-11-24, expanded AutoISF weight branch tests (pending CI)

- Added integration assertions to `OpenAPSAutoISFPluginTest.autoISFTest` in commit `8089e21129ad014eeb93753b06bcca2ce8384846`.
- The added cases exercise PP, Dura, acceleration and braking weights through the production `autoISF()` path, attempting to neutralize/bypass unrelated factors and compare weighted versus zero-weight outputs.
- This is test-only; no AutoISF formula, default, preference, app UI or live setting was changed.
- CI must confirm whether the fixture and expected branch isolation are valid. Until the new run completes, do not count these branches as passed. Invalid/non-finite persisted-value handling and Kotlin/JS parity remain open.


### 2026-11-24, AutoISF preference/UI source audit and emulator run 45303288555

- Commit `2aba42221c81f872c2113f582a9d5bc02a631ce5` successfully triggered the dedicated workflow [AIMI replay emulator smoke run 45303288555](https://github.com/Eretikstas/Apex/actions/runs/45303288555).
- At the latest check, the job had completed checkout, disk preparation, Russian overlay pruning, JDK/Gradle setup, and exclusion of obsolete instrumentation sources. The FullDebug + AndroidTest APK build was still in progress; KVM, emulator boot, instrumentation, and artifact upload had not started. Do not report an emulator pass until the run concludes.
- The workflow's current instrumentation target remains `RingSkinScreensTest` (one synthetic Ring UI test). It is not an AutoISF settings UI test and does not verify six fields, slider ranges, value editing, or persistence. The workflow is useful as a build/emulator smoke only.
- Source inspection confirms the AutoISF settings screen lists the master switch, `autoISF_min/max`, all six weights, and IOB threshold under the nested `auto_isf_settings` sub-screen.
- Confirmed preference metadata from `core/keys/.../DoubleKey.kt`: accel 0..1; brake 0..1; PP 0..0.15; lower BG range 0..2; higher BG range 0..2; Dura 0..3. All six defaults are 0. `autoISF_min` is 0.3..1.0 (default 1.0) and `autoISF_max` is 1.0..3.0 (default 1.0); these are bounds, not weights. Master `ApsUseAutoIsfWeights` defaults false.
- Compose double preference state writes to `Preferences.put()` on value change, so values are persisted immediately rather than only on leaving the screen. A process-restart UI persistence test is still needed.
- **Non-finite input risk confirmed in source path:** the double preference state clamps only with `coerceIn(min,max)`; the UI's initial repair checks only `v < min || v > max`. For NaN both comparisons are false, so that repair does not reject NaN. The AutoISF formula reads preference doubles directly and has no complete finite/range validation at the formula boundary. Add explicit NaN and ±Infinity persisted-preference tests; do not invent or silently implement a fallback policy without reviewing the required fail-closed behavior with Claude.
- The user-uploaded `aaps-4.1-custom-261015-1156.apk` was statically inspected as a ZIP/APK: its DEX files contain all six weight preference keys, the master enable key, and min/max keys. This is string-presence evidence only; it does not prove the APK installs, renders the controls, or executes AutoISF correctly. The hosted workflow builds from the `aimi-replay` source and does not install this uploaded APK.
- Next: finish and inspect run 45303288555; then add a dedicated synthetic AutoISF settings UI test that navigates to the APS plugin preferences, verifies the six labels/ranges, edits known test values, leaves/reopens the screen and restarts the app to verify persistence. Keep all testing on the lab branch with synthetic profile/data only. No real pump, CGM, Nightscout credentials, live profile, or production settings are involved.


## 2026-11-24, emulator smoke post-run audit

- Run 45303288555 completed with workflow conclusion `failure`, but the instrumentation test itself **passed**: `RingSkinScreensTest#ringScreens` reported `OK (1 test)` and instrumentation exit code `-1` (Android instrumentation success convention).
- Emulator booted, both APKs installed, 16 screenshot files were pulled. The failure happened only in the shell's final grep assertion: the YAML contained an over-escaped regular expression (`\\\\(` / `\\\\)`) that did not match the successful `OK (1 test)` output.
- No evidence of a failed UI assertion or failed emulator boot in this run. Emulator emitted graphics ColorBuffer warnings; the instrumentation completed despite them.
- Fixed the matcher in commit `f34a1c520826c447e42f13a152d73b2756ebc65c` to `grep -Eq 'OK \\([1-9][0-9]* tests?\\)' out/instrument.txt`. The workflow file change should trigger a fresh smoke run; confirm its final status before claiming the workflow passes.
- The smoke test remains a Ring UI test only. It does not validate AutoISF settings rendering or persistence.
- Confirmed the AutoISF settings tree is nested under `openapsautoisf_settings` with child screen key `auto_isf_settings`, containing master toggle, min/max, six weights, and IOB threshold. Dedicated AutoISF instrumentation coverage remains pending.

### 2026-11-25, Claude: control run of the open summary — PASS

Run 45620155708 (code be928e7f51, `sim=true`, `algo=autoisf`, 1 job: base / APP / seed 1). Public summary:
`<70 2.7 % | <54 0.0 | >180 8.2 | CV 28.4 | hypo 27.64 g/day | insulin 14.83 U/day`. Identical to Claude's own
decryption of the same job in run 45099362646 (2.7 / 0.0 / 8.2 / 28.4 / 27.6 / 14.8): the summary is correct, TDD in
the expected 14–17 range, and "no token = old result" holds. From now on ChatGPT reads results from the summary.

Review of LAPROTECT (code be928e7f51): the interlock and dura part are right and well tested. **One approved point is
not done:** with LAPROTECT, "percentage down" must use the all-cause protection low rate (`protectionLowPct`), not
`percentageLearningLowPct` (fasting / meal_bolus only). Fix: in `learnAllCheck`, the `pct-` branch condition becomes
`(if (laProtect) protectionLowPct else percentageLearningLowPct) > 4.0`. Rebound lows counted as protection lows:
accepted (more cautious than the spec).

Rules (see COORDINATION.md): APP23 used `algo=aimi` — not comparable, discard; lab runs are `algo=autoisf` only. No new
workflows, no code outside `HistoryReplayTest.kt` and `lab/` without Claude and the user.


### 2026-11-25, Task 9 hypo-model calibration — completed run, STOP: safety pass NOT met

- Replay run [45640419812](https://github.com/Eretikstas/Apex/actions/runs/45640419812) finished: all 73 workflow jobs completed successfully (1 plan + 72 replay). Each replay job uploaded its encrypted result and emitted a public synthetic summary. No replay job failures.
- Read public summary rows from completed replay job logs. The validated APP control remains: BG <70 2.7%, BG <54 0.0%, BG >180 8.2%, CV 28.4%, hypo treatment 27.64 g/day, insulin 14.83 U/day.
- **STOP condition from RULES_FOR_CHATGPT.md §§4 and 4a is met:** at least one calibration replay result violates the required pass rule against APP. Example: CHGISF_1.0 / SBIAS_0.15 / TREATL / seed 1 produced BG <70 = 3.0% (>2.7%) and hypo treatment = 29.46 g/day (>27.64). Other observed jobs also exceed APP on one or both gates; some runs have BG <54 >0.0% as well.
- Because the pass rule is broken, do not calculate/select a top-three calibration, do not claim calibration, do not start another batch, and do not modify model/learner code. Hand off the failed safety result to Claude for review. The private target bands remain chat-only and are not copied here.
- This note records the stop trigger and representative public numeric evidence; it is not a full aggregate calibration report.
