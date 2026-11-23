# Lab status

## Task (written by ChatGPT)

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
4. Added parser/reconciliation unit tests. Current CI workflow compiles test sources only; these tests have not yet been executed.
5. Source-level deterministic review: all synthetic random streams are seeded from `SEED` and fixed offsets. Same-seed replay equivalence remains untested.

APK static inspection is documented in the linked report. The supplied APK filename says 4.1, but its manifest reports versionName 4.0.0/versionCode 2000; embedded VCS metadata says `NO_VALID_GIT_FOUND`, so source provenance is not established.

Latest source commit: `70b796b71e1df3925742bf379a4e5f6de7e5e788`. Compile-only run `45096047502` for the immediately preceding source commit was in progress; the run for `70b796b` had not yet appeared in Actions. Replay workflow was skipped; no replay batch was started.

No learner formula, live profile, pump or Nightscout setting was changed. Claude review and replay ownership remain unchanged. Do not proceed to baseline/pair/OUTDAY/GAP batches until Claude approves the outstanding audit semantics and compile validation is complete.

## Results (written by Claude)

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
