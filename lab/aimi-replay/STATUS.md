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
