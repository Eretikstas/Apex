# Lab status

## Task (written by ChatGPT)

### 2026-11-28 — SHAPEAUDIT control correction (pre-run)

- The first shape screen replay [46058383247](https://github.com/Eretikstas/Apex/actions/runs/46058383247) executed 16/16 jobs, but its seed-1 control failed: observed `4.6 / 0.1 / 7.6 / 30.1 / 26.07 / 11.54` versus the required `6.0 / 0.5 / 5.7 / 29.7 / 33.21 / 11.78` (<70 / <54 / >180 / CV / hypo g/day / insulin U/day).
- Root cause verified by comparing the exact RUN row with Claude's later control in replay [46144921012](https://github.com/Eretikstas/Apex/actions/runs/46144921012): the failed shape RUN omitted the report-only tokens `LACAUSE,CRAUDIT,LSTAGE0,LISF2`; the later control included all four and matched all six reference values exactly. This is a batch-construction mismatch, not evidence that the SHAPEAUDIT code changed replay behaviour.
- The 15 candidate shape rows from 46058383247 are retained as exploratory only; no model setting is selected from a batch with a failed control. The corrective retry will preserve all 16 rows and all candidate tokens, add those four report-only audit tokens to every row, keep `SHAPEAUDIT`, and keep `CTRL13A` only on the unchanged seed-1 control.
- Pre-run checks: source commit `626ffbe9` compile report [46143849621](https://github.com/Eretikstas/Apex/actions/runs/46143849621) is SUCCESS (all 11 checks); v8 replay [46144921012](https://github.com/Eretikstas/Apex/actions/runs/46144921012) is SUCCESS, 13/13 jobs, control PASS; push-only run [46145704893](https://github.com/Eretikstas/Apex/actions/runs/46145704893) completed with replay skipped, so the lab is free. Current branch head before this note: `603bdd8b`.
- Scope remains synthetic only. Shape comparison uses only anonymized dimensionless hourly mean/SD ratios (`anonymized-v1`); no personal records, absolute readings, dates, doses, or profile data are used. No app code, frozen bench build, hints branch, phone, or Nightscout was touched.
- Corrected RUN batch committed as `cf5b2458` (16 jobs; one seed-1 `CTRL13A` control). Dispatcher report `dispatch-cf5b2458.md` confirms source `626ffbe9`, compile green, and **STARTED** replay [46156758753](https://github.com/Eretikstas/Apex/actions/runs/46156758753). The replay is still in progress; no candidate ranking or calibration change has been made. Await `results/run-46156758753.md`, then require the corrected `CTRL13A` to PASS before selecting a model.


### 2026-11-28 — STOP: SHAPEAUDIT screen control mismatch

- Per RULES_FOR_CHATGPT.md §00 / §4a, STOP. The unchanged APP + CAL usual seed-1 control in replay [46058383247](https://github.com/Eretikstas/Apex/actions/runs/46058383247) reports **FAIL**. This is a hard stop; no further lab step, calibration choice, branch creation, code change, or RUN submission is made in this turn.
- RUN commit: [720089ec](https://github.com/Eretikstas/Apex/commit/720089ec8010f467067c3e54016724a634e002c1). Exact source commit: `6f883d7c`; compile gate `46055486474` was green and dispatcher `46058359316` started the replay. Replay process completed 16/16 jobs successfully, but the control gate failed.
- Control expected (from RULES §4): <70 6.0, <54 0.5, >180 5.7, CV 29.7, hypo 33.21 g/day, insulin 11.78 U/day. Observed: <70 4.6, <54 0.1, >180 7.6, CV 30.1, hypo 26.07 g/day, insulin 11.54 U/day. These are synthetic lab outputs; they do not match the required no-change control.
- The candidate rows are not ranked or selected because the control is invalid. No generator/model parameters were changed by this screen. The next work is blocked pending the user's direction on the control mismatch.


### 2026-11-27 — shape calibration screen submitted

- RUN commit [`720089ec`](https://github.com/Eretikstas/Apex/commit/720089ec8010f467067c3e54016724a634e002c1) contains 16 synthetic jobs: unchanged seed-1 control plus five existing-token candidates across seeds 1–3. Dispatcher [46058359316](https://github.com/Eretikstas/Apex/actions/runs/46058359316) confirms source compile `6f883d7c` green and **STARTED** replay [46058383247](https://github.com/Eretikstas/Apex/actions/runs/46058383247).
- Candidate groups: `MEALX_1.0`, `MEALX_1.4`, `DAWN_0.9`, `DAWN_0.8`, and `MEALX_1.0+DAWN_0.9`; all retain the same APP/CAL base and `SHAPEAUDIT`. `CTRL13A` is only on the unchanged seed-1 control.
- No new source code or model parameter was added. Candidate values are labelled **our estimates**; no clinical range is asserted. Run is active; no candidate score or calibration pass is claimed yet.

### 2026-11-27 — SHAPEAUDIT baseline complete

- Exact source commit `6f883d7c5d6644fc6ec6aa878313927d979c02a1`; compile run [46055486474](https://github.com/Eretikstas/Apex/actions/runs/46055486474) **SUCCESS**, all 11 checks green. RUN retry `80fd2aaf`; dispatcher [46056094075](https://github.com/Eretikstas/Apex/actions/runs/46056094075) **STARTED**; replay [46057061061](https://github.com/Eretikstas/Apex/actions/runs/46057061061) **SUCCESS**, 3/3 replay jobs, 0 failed ticks; `CTRL13A` seed 1 **PASS**.
- Synthetic summary (BG <70 / <54 / >180 / CV / hypo g/day / insulin U/day):

| Seed | <70 | <54 | >180 | CV | Hypo g/day | Insulin U/day |
|---:|---:|---:|---:|---:|---:|---:|
| 1 | 6.0 | 0.5 | 5.7 | 29.7 | 33.21 | 11.78 |
| 2 | 4.0 | 0.1 | 4.2 | 27.7 | 24.11 | 10.92 |
| 3 | 4.8 | 0.3 | 4.5 | 28.9 | 29.64 | 11.63 |

- SHAPEAUDIT dimensionless 24-hour curves (reference `anonymized-v1`), ratio MAE:

| Seed | Mean-ratio MAE | SD-ratio MAE |
|---:|---:|---:|
| 1 | 0.0894 | 0.2677 |
| 2 | 0.0768 | 0.3213 |
| 3 | 0.0892 | 0.2771 |
| Mean | 0.0851 | 0.2887 |

- Pooled normalized-curve MAE: mean 0.0824, SD 0.2844. Residual shape: SD ratio is above reference mainly in hours 02–07 and below reference in 15–21; mean ratio differs most around 00–01 and 14–17, 20, 22. These are anonymized dimensionless ratios only; no absolute BG, raw records, dates, doses, or profile values were added.
- This is a diagnostic baseline, not a calibrated model. No generator parameter, learner behaviour, profile, or insulin delivery changed. `SHAPEAUDIT` is report-only; control confirms no-token APP numbers unchanged.
- Next shape-screen: test existing synthetic-only `MEALX` (0.7 → 1.0 and 1.4) to address low afternoon/evening mean/SD ratios, and `DAWN` (0.9 and 0.8) to screen the early-morning excess. Include `MEALX_1.0+DAWN_0.9`, 3 seeds per candidate, plus one unchanged seed-1 `CTRL13A`. These values are explicitly **our estimates**, not clinical ranges; compare only dimensionless shape MAE for model selection. No new parameter or app code is introduced.

### 2026-11-27 — SHAPEAUDIT baseline replay started

- Dispatcher `46056094075` wrote `dispatch-80fd2aaf.md`: compile `6f883d7c` green; lab free; **STARTED** replay `46057061061` from RUN retry `80fd2aaf`.
- Three replay jobs are active: `SYNTH_56`, APP + CAL usual, seeds 1–3; seed 1 includes `CTRL13A`. Plan job passed; comparison-test was skipped as expected. No per-seed SHAPEAUDIT values are published yet.


### 2026-11-27 — SHAPEAUDIT source compile gate PASS

- Exact source commit `6f883d7c5d6644fc6ec6aa878313927d979c02a1`; compile run `46055486474`; public report `compile-6f883d7c.md`: **SUCCESS**. All 11 checks passed: test-source compilation, AutoISF boundaries, HistoryReplayTest helper tests (including SHAPEAUDIT normalization), adaptation status builder/presenter, APSResult wrapper, laboratory FullDebug APK assembly, and APK upload.
- RUN retry commit `80fd2aaf` is still in dispatcher run `46056094075`, waiting for a free lab/start. No replay run ID or shape result is available yet.


### 2026-11-27 — learner branch ownership preflight (read-only)

- Existing `learner-rebound` head `d25ae63a` is four commits ahead of frozen app-code base `6f578b5b` and already contains `ReboundGuardEngine` plus tests. It is preserved untouched; a fresh ChatGPT-owned branch with a distinct name will be created from `6f578b5b` after shape recalibration rather than overwrite that branch.
- Existing `learner-split` head `e762c799` contains `SplitBolusEngine` and tests; it is read-only and will not be merged or edited before lab validation. No-insulin hints branch remains untouched.


### 2026-11-27 — SHAPEAUDIT baseline RUN retry committed

- RUN commit `80fd2aafd0807d46a040230d7623c03e67d589b9` re-submits the same three usual APP + CAL `SYNTH_56` jobs for seeds 1–3; `CTRL13A` remains only on seed 1. No scenario or model token changed. The dispatcher will wait for the full compile of `6f883d7c`; no replay run ID or score is claimed yet.


### 2026-11-27 — SHAPEAUDIT compile fix partial checks

- Exact compile run `46055486474` for `6f883d7c`: `compileFullDebugUnitTestKotlin` PASS; AutoISF weight-boundary tests PASS; `HistoryReplayTest` report-helper tests PASS, including the new normalization test. Dashboard builder/presenter tests are still running; APSResult wrapper, APK assembly, and upload have not yet run. Overall compile gate remains pending.


### 2026-11-27 — SHAPEAUDIT first RUN refused by compile gate

- Dispatcher run `46055094067` completed and wrote `dispatch-b55c429b.md`: **REFUSED** because the exact source compile for `d83d79ca` concluded failure. No replay was started from RUN commit `b55c429b`.
- Formatting fix commit `6f883d7c5d6644fc6ec6aa878313927d979c02a1` triggered exact compile run `46055486474`; source compilation, AutoISF boundaries, and HistoryReplayTest helper tests are green; dashboard/presenter tests are in progress. Per §00, a new RUN commit is submitted now and the dispatcher will wait for the full compile gate.


### 2026-11-27 — SHAPEAUDIT compile attempt 1 failed; formatting fix committed

- Exact compile run `46054986377` for source commit `d83d79ca` failed at `:plugins:aps:compileFullDebugUnitTestKotlin`. The shape helper itself was not reported as the cause; the three audit `println` statements escaped Kotlin string interpolation and caused a parser error at source line 2404. All later compile/test/APK steps were skipped. This is a real compile failure, not a pass.
- Corrective source commit: `6f883d7c5d6644fc6ec6aa878313927d979c02a1`; it changes only those report-formatting statements to string concatenation. No simulation logic or reference ratios changed.
- Dispatcher for RUN commit `b55c429b` was still waiting at the last status check. No replay run ID or shape result exists yet. The dispatcher will only start after a green compile for this source commit or a later descendant; otherwise it must refuse.


### 2026-11-27 — SHAPEAUDIT baseline RUN committed (awaiting dispatcher)

- Code commit: `d83d79ca214ff03c72deeea7c6602f4a2f1814c0` (test source only). RUN commit: `b55c429b63a016367fec4cb78b3e5008242ddc53`.
- RUN contains three APP + CAL-usual `SYNTH_56` jobs, seeds 1–3, each with `SHAPEAUDIT`; `CTRL13A` is present only on seed 1. The source jobs were copied from the usual rows of Step 18; no scenario or calibration tokens were changed.
- At this check, neither `compile-d83d79ca.md` nor `dispatch-b55c429b.md` is published. No compile result, replay run ID, or shape score is claimed. The dispatcher is expected to gate the exact source commit before replay.


### 2026-11-27 — SHAPEAUDIT implementation committed (pre-run)

- Test-source commit: `d83d79ca214ff03c72deeea7c6602f4a2f1814c0`; commit diff contains only `HistoryReplayTest.kt`.
- Added opt-in `SHAPEAUDIT`: it reads synthetic sensor `bg` from the completed replay rows, computes 24 clock-hour means/SDs, normalizes each curve to a dimensionless ratio, and reports ratio MAE plus the normalized curves. It does not modify replay state, learner state, profile, or insulin delivery. Added a unit test for hour ordering, normalization, and missing-hour rejection.
- Public reference contains only 24 normalized mean ratios and 24 normalized SD ratios. No absolute glucose values, dates, raw rows, identifiers, doses, or profile data were added.
- Exact compile result for `d83d79ca` has not yet appeared; no compile pass is claimed. Per the lab dispatcher protocol, commit the three-seed baseline RUN next; dispatcher must verify the exact code commit's compile before replay starts.


### 2026-11-27 — Shape recalibration preflight (pre-run)

- Re-read My-apps `CLAUDE.md`, handoff §13, Apex `RULES_FOR_CHATGPT.md` §00/§5, current `RUN.txt`, latest STATUS Task/Claude Results, and the Step-16/17/18 reports before proceeding.
- Step 16 attempt 3: replay `46023317575`, 6/6 jobs successful; `CTRL13A` PASS. Step 17: replay `46029516923`, 2/2 jobs successful; both controls PASS and no-token metrics unchanged. Step 18: replay `46051803175`, 18/18 jobs successful; `CTRL13A` PASS. The Step-18 report confirms 105/108 rounded metric cells equal the Step-10 references; three insulin/day cells differ by 0.01 U/day and remain unresolved, so the matrix is not called an exact pass.
- Step-18 source commit `a1d93913` has a green exact-source compile, run `46044690002`; its RUN dispatch `fbd4c234` confirms the compile gate was green. Replay jobs and report job are all completed successfully.
- Current `aimi-replay` head is `553abd68`; the only commit after the Step-18 result head `fa0f1195` changes STATUS.md. Frozen app code remains at `6f578b5b`; the later `autoisf-atc3` head `689aa356` changes only `.github/scripts/ci_report.py`. No app code or bench build will be changed.
- Next: add an opt-in `SHAPEAUDIT` report-only audit over synthetic sensor BG (`bg`) in local clock-hour order. The public reference will contain only dimensionless 24-hour mean/SD shape ratios; no absolute glucose values, dates, raw rows, identifiers, doses, or profile data. Baseline matrix: APP + CAL usual, `SYNTH_56`, seeds 1–3, with `CTRL13A` on seed 1 only. No generator parameters are changed in this audit step.
- Scope remains `aimi-replay` test source and lab STATUS/RUN only. No changes to the hints branch, `learner-rebound`, frozen app code, child's phone, or Nightscout.


### 2026-11-27 — User ownership and safety update (supersedes older ownership notes)

- ChatGPT owns Steps 16–18, shape recalibration using only anonymized shape statistics in public Apex, then REBOUND GUARD on a separate `learner-rebound` branch based on frozen `autoisf-atc3`; after that, test the food-first smart calculator and split bolus in the lab before any app implementation.
- REBOUND GUARD must be OFF by default and suppress-only: it may hold SMB and cap temp basal after hypo treatment; it must never add insulin. Do not push app code to `autoisf-atc3` or change its frozen bench build.
- Claude owns no-insulin hints 7c on its existing branch only. Do not edit that branch or those hints.
- Do not touch the child's phone or Nightscout. Public Apex remains synthetic-only; publish only anonymized shape statistics, never personal records.
- This note supersedes the older STATUS entry assigning REBOUND GUARD core to Claude. The existing `learner-rebound` branch mentioned in Claude's Results is not to be used for final acceptance until the post-recalibration sequence is reached and its current base is re-verified.


### 2026-11-27 — Pre-recalibration RGNOMEAL diagnostic (not guard acceptance)

- Replay [46025386328](https://github.com/Eretikstas/Apex/actions/runs/46025386328), RUN commit `85b99609`, source `74c2ee9f`: 7/7 jobs succeeded; seed-1 `CTRL13A` PASS; no failed replay jobs.
- The six combined `RGAPP2 + SPLITAPP + RGNOMEAL` jobs averaged (180-min window / 120-min window; <70 / <54 / >180 / CV / hypo g/day / insulin U/day):
  - 180 min: 4.57 / 0.20 / 4.77 / 28.43 / 26.67 / 11.40.
  - 120 min: 4.77 / 0.23 / 4.57 / 28.07 / 27.68 / 11.39.
- Against the earlier 460221 best (4.67 / 0.17 / 4.77 / 28.20 / 26.67 / 11.36), neither window is uniformly better. This was a combined diagnostic on the pre-recalibration model; it does not select a guard setting and is not the required post-recalibration validation.
- No app code, frozen bench branch, hints branch, phone, or Nightscout was touched.

### 2026-11-27 — Step 17 LEXPL complete

- Test-only source commit [`cc98bd29`](https://github.com/Eretikstas/Apex/commit/cc98bd29a6d402d12fef8c9ecb59f537d2238dff): `LEXPL` is OFF unless tokened, emits one explanation per low episode plus passed/blocked card summaries, and has pure helper tests. It changes no learner formula, profile, or insulin-delivery state.
- Exact-source compile [46027080361](https://github.com/Eretikstas/Apex/actions/runs/46027080361) completed SUCCESS. The later public-repository privacy cleanup commit [`a1d93913`](https://github.com/Eretikstas/Apex/commit/a1d93913d9a18e613dc7fd68c6cca197685095a8) did not change replay logic; its exact compile [46044690002](https://github.com/Eretikstas/Apex/actions/runs/46044690002) also completed SUCCESS.
- RUN commit [`3d6f1bae`](https://github.com/Eretikstas/Apex/commit/3d6f1bae1c03bd3780b37ce23654dfdbddeca329); replay [46029516923](https://github.com/Eretikstas/Apex/actions/runs/46029516923): 2/2 jobs successful, 0 replay failures. No-token and LEXPL jobs both returned exactly `6.0 / 0.5 / 5.7 / 29.7 / 33.21 / 11.78` (BG <70 / <54 / >180 / CV / hypo g/day / insulin U/day); both `CTRL13A` controls PASS. **No-token = old numbers: PASS.**
- LEXPL raw job log (the generated Markdown report omitted the card-summary lines, so these are taken directly from the public job log): 300 low episodes — rebound 270, SMB 18, COB-min-rate 6, ambiguous 5, other 1. Overlapping evidence: after-hypo 270, after-SMB 172, after-meal 191, after-manual 118, COB-min-rate meal 79; these evidence counts are not mutually exclusive.
- Card screen: WATTR 0 events (2,255 clean-window and 73 other blocks); LSTAGE0 day/meal audit ran, with calibration-window and medical-procedure unavailable (0 cannula/sensor/illness/activity days, 43 minimum-rate meals, 36 untreated lows); LISF2 all four blocks silent (0 accepted; meal-hypo dominated); LCR3 silent (0/122 accepted; prior-SMB 59, COB-min-rate 43, prior-hypo-rebound 14, prior-SMB+manual 6); basal audit 43/3,960 accepted, 10 days, 9 eligible + 15 estimated hours, gate PASS; PCT 3 up / 5 down, final 80%; DURA final 0.50. Ten card summaries emitted. LEXPL itself is report-only and made no writes; the existing APP learner cards ran unchanged.
- Latest RUN before this step was Claude's separate v7 NIGHTT diagnostic, replay [46038542283](https://github.com/Eretikstas/Apex/actions/runs/46038542283), 7/7 successful; its result remains in Claude's Results and is not the Step-18 matrix. No app branch, frozen bench build, hints branch, phone, or Nightscout was touched.

### 2026-11-27 — Step 18 complete

- RUN commit [`fbd4c234`](https://github.com/Eretikstas/Apex/commit/fbd4c234); dispatcher [46051770364](https://github.com/Eretikstas/Apex/actions/runs/46051770364) confirmed compile gate green and lab free; replay [46051803175](https://github.com/Eretikstas/Apex/actions/runs/46051803175) completed SUCCESS: 18/18 replay jobs successful, 0 failed ticks/jobs. Current branch head at result commit: `fa0f1195`.
- `CTRL13A` CAL-usual seed 1 PASS. Across 18 APP jobs × 6 metrics, 105/108 cells exactly match the Step-10 APP references. Three insulin/day cells differ by 0.01 U/day only: CHGISF-lower seed 1 (12.65 vs 12.66), CHGISF-lower seed 3 (12.62 vs 12.63), DRIFT_2 seed 3 (12.99 vs 13.00). All other 105 cells match; exact equality for those three rounded cells is **not verified**, so do not call the whole matrix an exact pass.
- All 18 jobs emitted 10 card summaries. Aggregate screen (candidate counts are across 18 jobs):

| Card | Result |
|---|---|
| WATTR | 7 candidate weight events met the report filters; 42,199 clean-window and 978 other blocks. Report-only; no weight writes. |
| Stage0 | Day/meal audit passed 18/18; 831 COB-min-rate meals and 633 untreated lows. Cannula/sensor/illness/activity days = 0; calibration-window and medical-procedure unavailable. |
| ISF morning | 2,733 candidates; 0 accepted; 2,733 rejected (2,638 meal-hypo, 95 AutoISF-not-1). |
| ISF day | 3,351 candidates; 0 accepted; 3,351 rejected (3,288 meal-hypo, 63 AutoISF-not-1). |
| ISF evening | 1,772 candidates; 0 accepted; 1,772 rejected (1,759 meal-hypo, 13 AutoISF-not-1). |
| ISF night | 1,526 candidates; 0 accepted; 1,526 rejected (1,334 meal-hypo, 190 AutoISF-not-1, 2 overlapping-manual). |
| CR | 1,860 candidates; 0 accepted; 1,860 rejected: COB-min-rate 826, prior-SMB 718, prior-hypo-rebound 229, prior-SMB+manual 71, SMB-within-6h 15, not-fully-absorbed 1. Gate FAIL in all 18 jobs because evidence was excluded. |
| Basal | 71,280 candidates; 799 accepted, 70,481 rejected; gate PASS in 18/18; 183 eligible and 249 estimated hours. Main rejects: hypo-rebound 36,079, not-hour-boundary 32,274, meal-window 1,925. |
| PCT | 46 up / 82 down; final 80% in all 18 jobs. |
| DURA | Final 0.50 in all 18 jobs. |

- LEXPL classified 5,522 synthetic low episodes across the 18 jobs: rebound 4,894; SMB 308; COB-min-rate 135; ambiguous 138; other 28; fasting 11; meal-bolus 6; manual-correction 2. Raw job logs contain complete summaries; the generated Markdown report truncates some low-detail lines. No personal records or real-child measurements were added to public Apex.
- Result: Step 18 execution and control passed; three 0.01 U/day reference discrepancies remain explicitly unresolved. New audit tokens were report-only; existing APP learner cards remained the same. No app code, frozen bench branch, Claude hints branch, phone, or Nightscout was touched.
- Next: shape recalibration on the synthetic model using only a normalized, anonymized shape reference (relative hourly mean/SD shape; no dates, raw rows, identifiers, doses, or personal profile). Add a report-only shape audit first, compare baseline, then tune the synthetic generator; do not alter app code or the frozen bench build.

### 2026-11-27 — Step 16 LCR3 attempt 3 complete

- Source commit: `f4a6c9bc5d6a02c52f54a40ebf68ad1a26ad7b5e`; RUN commit: `382554b9061755f1b0c7d22200b40a81f5d7ecf9`; replay head: `e63a48b1`; run [46023317575](https://github.com/Eretikstas/Apex/actions/runs/46023317575). All 6/6 jobs succeeded; `CTRL13A` PASS; no replay failures.
- Metrics (BG <70 / <54 / >180 / CV / hypo g/day / insulin U/day):

| Case | <70 | <54 | >180 | CV | Hypo g/day | Insulin U/day |
|---|---:|---:|---:|---:|---:|---:|
| CTRL13A, no LCR3 | 6.0 | 0.5 | 5.7 | 29.7 | 33.21 | 11.78 |
| CAL + LCR3 | 6.0 | 0.5 | 5.7 | 29.7 | 33.21 | 11.78 |
| CRT_26.4 + LCR3 | 6.4 | 0.3 | 4.7 | 30.2 | 35.89 | 11.32 |
| CRT_17.6 + LCR3 | 4.3 | 0.2 | 7.2 | 29.5 | 26.79 | 12.26 |
| MEALX_0.7 + LCR3 | 6.0 | 0.5 | 5.7 | 29.7 | 33.21 | 11.78 |
| TREAT_15 + LCR3 | 3.3 | 0.2 | 6.4 | 26.6 | 75.54 | 13.51 |

- LCR3: 610 candidates across five jobs; 0 accepted, 610 rejected, 0 eligible days, proposal silent, gate FAIL in every job. Top reasons: prior-SMB 290/610, COB-min-rate 221/610, prior-hypo-rebound 69/610, prior-SMB+manual 27/610, SMB-within-6h 3/610. Both positive CRT perturbations were missed; negative TREAT_15 remained silent. `MEALX_0.7` is not an independent negative because it is already in CAL. This is a bad score, not a §4a stop; do not loosen filters or invent a new CR rule.
- Supporting audits: LISF2 accepted 0/478 (all four time blocks gate FAIL; 469 rejected as meal-hypo, 9 as AutoISF-not-1). LSTAGE0 reported 0 cannula/sensor/illness/activity days, 36–59 COB-min-rate meals and 9–37 untreated-low episodes per job; calibration-window and medical-procedure fields are unavailable. CRAUDIT remained counting-only.
- DUMPCSV control: 16,128 rows at 5-minute intervals; insulin/day 11.7795 U vs replay summary 11.7795 U, delta 0.000 %. No-token control exactly matches Step 13a. CSV artifact is synthetic only.
- Assumptions that failed: the current COB/SMB/rebound exclusions leave no clean meal windows, so the positive CRT direction cannot be detected. Preserve the thresholds; Step 17 must explain each low and each card's passed/blocked rules. Next: implement `LEXPL` report-only, OFF by default, then run its no-token/with-token seed-1 control comparison.


### 2026-11-27 — Step 16 attempt 3 DUMPCSV control check (job 0 complete)

- Run [46023317575](https://github.com/Eretikstas/Apex/actions/runs/46023317575), job 0, seed-1 `CTRL13A,DUMPCSV`: completed successfully; replay summary remained exactly the Step-13a control (6.0 / 0.5 / 5.7 / 29.7 / 33.21 / 11.78).
- The workflow's independent CSV validation reports `rows=16128`, `step_min=5`, `dump_insulin_u_day=11.7795`, `summary_insulin_u_day=11.7795`, `delta_pct=0.000`. This passes the one-row-per-5-minute-tick and insulin-reconciliation checks; artifact `aimi-replay-result-0` is ready. The overall six-job Step-16 run later completed 6/6 successfully; see the Step-16 attempt-3 completion block above.


### Step 10 — exact APP per-seed reference recovered from public job logs

- Source: replay `45653668453`, the 18 APP-variant jobs (even matrix indices); values below were parsed from each job's public summary, not inferred from scenario means. Use this table for the Step-18 report-only equality check.

| Scenario | Seed | <70 | <54 | >180 | CV | Hypo g/day | Insulin U/day |
|---|---:|---:|---:|---:|---:|---:|---:|
| usual | 1 | 6.0 | 0.5 | 5.7 | 29.7 | 33.21 | 11.78 |
| MEALX lower | 1 | 5.4 | 0.2 | 6.0 | 29.6 | 30.18 | 11.44 |
| CHGISF higher | 1 | 7.2 | 0.6 | 4.1 | 29.7 | 38.39 | 11.04 |
| CHGISF lower | 1 | 5.0 | 0.2 | 7.7 | 30.2 | 28.39 | 12.66 |
| DRIFT_2 | 1 | 5.2 | 0.1 | 8.4 | 30.8 | 29.64 | 13.02 |
| NOISE_0.12 | 1 | 12.9 | 3.0 | 15.5 | 41.1 | 59.29 | 13.04 |
| usual | 2 | 4.0 | 0.1 | 4.2 | 27.7 | 24.11 | 10.92 |
| MEALX lower | 2 | 4.0 | 0.1 | 3.9 | 27.1 | 24.11 | 10.71 |
| CHGISF higher | 2 | 4.4 | 0.2 | 3.2 | 26.7 | 26.25 | 10.19 |
| CHGISF lower | 2 | 3.6 | 0.1 | 6.2 | 28.2 | 20.36 | 11.80 |
| DRIFT_2 | 2 | 3.5 | 0.2 | 6.9 | 27.6 | 19.82 | 12.16 |
| NOISE_0.12 | 2 | 11.0 | 2.7 | 12.8 | 38.0 | 52.86 | 11.83 |
| usual | 3 | 4.8 | 0.3 | 4.5 | 28.9 | 29.64 | 11.63 |
| MEALX lower | 3 | 4.7 | 0.2 | 3.8 | 27.8 | 26.96 | 11.25 |
| CHGISF higher | 3 | 6.0 | 0.4 | 3.2 | 28.2 | 35.36 | 10.98 |
| CHGISF lower | 3 | 4.5 | 0.2 | 5.2 | 28.4 | 26.07 | 12.63 |
| DRIFT_2 | 3 | 4.4 | 0.2 | 6.0 | 29.7 | 26.61 | 13.00 |
| NOISE_0.12 | 3 | 12.0 | 3.2 | 14.7 | 40.3 | 55.71 | 12.86 |


### 2026-11-27 — inherited MEALAUDIT / SPLITRISK diagnostic (run 46022123724)

- The pre-existing 12-job RUN from commit `934287dc` completed successfully; compile of source `f4a6c9bc` was green, 12/12 replay jobs succeeded, and the seed-1 CAL `CTRL13A` control matched exactly (PASS). This batch is separate from Step 16 and is not the final post-guard split-bolus validation.
- Mean metrics across seeds (BG <70 / <54 / >180 / CV / hypo g/day / insulin U/day):

| Variant | <70 | <54 | >180 | CV | Hypo g/day | Insulin U/day |
|---|---:|---:|---:|---:|---:|---:|
| CAL | 4.93 | 0.30 | 4.80 | 28.77 | 28.99 | 11.44 |
| RGAPP2 + SPLITAPP (BEST) | 4.67 | 0.17 | 4.77 | 28.20 | 26.67 | 11.36 |
| BEST + SPLITRISK_30 | 4.87 | 0.27 | 4.60 | 28.27 | 26.67 | 11.40 |
| BEST + SPLITRISK_0 | 5.03 | 0.23 | 4.53 | 28.57 | 28.21 | 11.42 |

- SPLITRISK_30 and SPLITRISK_0 did not improve the low metrics consistently versus BEST; neither is selected. They remain lab-only and are not an app implementation. Re-test split bolus after the user's required rebound-guard sequence.
- MEALAUDIT (12 jobs pooled): meals after hypo treatment had a low within 4 h in 324/472 (68.6%); falling-glucose meals 218/389 (56.0%); both flags 98/116 (84.5%); other meals 166/595 (27.9%). This is an association in the synthetic replay audit, not a causal proof or dose rule.
- Report: [run 46022123724](https://github.com/Eretikstas/Apex/actions/runs/46022123724). No app code, bench branch, hints branch, phone, or Nightscout was touched.


### 2026-11-27 — Step 16 attempt 2 audited; attempt 3 submitted

- Attempt 2 replay [45965576265](https://github.com/Eretikstas/Apex/actions/runs/45965576265), source `f051dd99`: 6/6 jobs succeeded; the no-LCR3 `CTRL13A` control exactly matched the Step-13a seed-1 reference and is PASS.
- Metrics (BG <70 / <54 / >180 / CV / hypo g/day / insulin U/day):

| Case | <70 | <54 | >180 | CV | Hypo g/day | Insulin U/day |
|---|---:|---:|---:|---:|---:|---:|
| CTRL13A, no LCR3 | 6.0 | 0.5 | 5.7 | 29.7 | 33.21 | 11.78 |
| CAL + LCR3 | 6.0 | 0.5 | 5.7 | 29.7 | 33.21 | 11.78 |
| CRT_26.4 + LCR3 | 6.4 | 0.3 | 4.7 | 30.2 | 35.89 | 11.32 |
| CRT_17.6 + LCR3 | 4.3 | 0.2 | 7.2 | 29.5 | 26.79 | 12.26 |
| MEALX_0.7 + LCR3 | 6.0 | 0.5 | 5.7 | 29.7 | 33.21 | 11.78 |
| TREAT_15 + LCR3 | 3.3 | 0.2 | 6.4 | 26.6 | 75.54 | 13.51 |

- LCR3 audit: all five LCR3 jobs had 122 candidates, 0 accepted, 122 rejected, 0 eligible days, proposal=silent. Rejection reasons were dominated by prior-SMB (49–66), COB-min-rate (36–59), and prior-hypo-rebound (10–17); the diagnostic split also showed prior-SMB+manual (4–6), and TREAT_15 had 3 additional SMB-within-6h rejects. No planted CR direction was demonstrated. This is a screening result, not a §4a stop; do not loosen filters. `MEALX_0.7` duplicates CAL because that token is already part of CAL, so it is not an independent negative.
- Attempt-3 commit `7fcf5d94` was cancelled before any replay job started; no run ID exists. Reuse its six-job matrix. Add `DUMPCSV` only to the no-LCR3 `CTRL13A` job to regenerate the corrected synthetic CSV artifact; keep all five LCR3 jobs unchanged.
- Separate MEALAUDIT/SPLITRISK run [46022123724](https://github.com/Eretikstas/Apex/actions/runs/46022123724) completed 12/12 successfully with its control PASS. The Step-16 attempt-3 dispatcher then started replay [46023317575](https://github.com/Eretikstas/Apex/actions/runs/46023317575): 6 jobs, source `f4a6c9bc`, compile green; replay is in progress and no result is claimed yet.
- Scope: synthetic replay only. No app branch, bench build, no-insulin-hints branch, or personal data changed.


### 2026-11-27 — Claude: lab check of the APP rules (DONE)

- Tokens (OFF unless present; CTRL13A PASS in every run with them in the code): `RGAPP` = app rebound guard
  (`4ee01c8e`), `SPLITAPP` / `SPLITAPPSLOW` = app split bolus, `RGAPP2` = RGAPP + LEARNG3's early releases with fixed
  numbers (`9dbae452`), `RGWIN_<min>` / `RGCAP_<x>` = RGAPP window and temp cap (`ccad1ea6`'s parent).
- Run 1 `45966450987` had NO valid control: its jobs lacked `LACAUSE` (it changes the learner cards). Not used.
- Every job below: CAL tokens + `LACAUSE,CRAUDIT,LSTAGE0,LISF2`; PLAIN = CAL without SPLIT_60, SPLITMIN_20, LEARNG3.
  Runs `45967958290` (v2), `45980152371` (v3), `45983278740` (v4). Means of seeds 1-3:

| Set | <70 | <54 | >180 | CV | hypo g/day |
|---|---:|---:|---:|---:|---:|
| CAL (LEARNG3 + SPLIT_60) | 4.93 | 0.30 | 4.80 | 28.8 | 29.0 |
| PLAIN | 5.67 | 0.27 | 4.60 | 28.8 | 31.7 |
| PLAIN + RGAPP (120 min, 1.5x) | 5.23 | 0.30 | 5.47 | 29.4 | 29.9 |
| PLAIN + RGAPP2 | 5.60 | 0.27 | 4.87 | 29.1 | 31.3 |
| SPLIT_60 + RGAPP | 5.10 | 0.27 | 5.30 | 29.3 | 28.7 |
| SPLIT_60 + RGAPP2 | 5.17 | 0.27 | 4.80 | 29.0 | 31.3 |
| LEARNG3 + SPLITAPP | 4.90 | 0.23 | 4.83 | 28.3 | 27.6 |
| RGAPP + SPLITAPP | 5.33 | 0.20 | 4.90 | 29.3 | 30.5 |
| RGAPP + SPLITAPPSLOW | 5.27 | 0.27 | 4.70 | 28.9 | 30.5 |
| RGAPP2 + SPLITAPP | 5.33 | 0.30 | 4.70 | 28.8 | 30.6 |
| RGAPP 120 min / 1.0x + SPLITAPP | 5.00 | 0.17 | 5.50 | 29.2 | 28.5 |
| RGAPP 180 min / 1.0x + SPLITAPP | 4.83 | 0.17 | 6.60 | 30.5 | 27.9 |
| **RGAPP2 180 min / 1.0x + SPLITAPP** | **4.67** | **0.17** | **4.80** | **28.2** | **26.7** |

- Result: the app split is as good as the lab split or better. The first app guard (120 min, 1.5x) helps against the
  plain loop but less than LEARNG3. With the values LEARNG3 learns (180 min, cap 1.0x) and its early releases, fixed,
  the app guard + app split beat CAL on all three low numbers with the same highs. Per seed it is mixed (seed 2 <54
  0.2 vs 0.1, seed 3 <70 5.0 vs 4.8); differences are near seed noise; 3 seeds only; model not yet re-calibrated.
- v5 `46022123724` (tokens `SPLITRISK_<pct>`, `MEALAUDIT`, report-only audit): in the model a meal within 3 h after a
  hypo treatment is followed by a low within 4 h in 55-80 % per job, other meals 22-31 %. The best set reproduced exactly (4.67 / 0.17 / 4.77 / 28.2 / 26.7). Giving less now for risky meals did NOT
  help: SPLITRISK_30 4.87 / 0.27 / 4.60 / 28.3 / 26.7, SPLITRISK_0 5.03 / 0.23 / 4.53 / 28.6 / 28.2 (means, seeds 1-3).
  v6 `46025386328` (`RGNOMEAL`: the guard does not let go on a logged meal): BEST+RGNOMEAL 4.57 / 0.20 / 4.77 / 28.4 /
  26.7, RGAPP2 120 min + RGNOMEAL 4.77 / 0.23 / 4.57 / 28.1 / 27.7 - no clear gain (within seed noise). Conclusion: more
  insulin control around a meal after a hypo does not remove those lows in the model; the guard's reach is the pure
  rebounds, a small share of all lows; lows after meals and at night are bigger shares.
- v7 `46038542283` (`NIGHTT_<mg/dL>`: loop target 21-06 h) on the best set, means of seeds 1-3:
  NIGHTT_126 (7.0) 4.43 / 0.17 / 5.97 / 28.6 / 26.2; NIGHTT_135 (7.5) 4.10 / 0.23 / 6.87 / 29.1 / 23.3 (best set 4.67 /
  0.17 / 4.77 / 28.2 / 26.7). Fewer lows and hypo carbs, more highs: a trade-off for the family to decide.
- The app guard was changed to these values (branch `learner-rebound`, `d25ae63a`). ChatGPT: after the
  re-calibration, run RGAPP2,RGWIN_180,RGCAP_1.0,SPLITAPP against CAL on the new model (more seeds if you can).

### 2026-11-27 — lab report log retrieval

- Fixed `.github/scripts/aimi_lab_report.py` in commit `7236396f`: GitHub job-log redirects now follow the signed storage URL without forwarding `GH_TOKEN`; only HTTP 404 is retried (10 × 30 s); other errors and empty logs are reported explicitly as `LOG NOT READ: ...`. A readable log is consumed immediately even if the expected marker is missing.
- This is lab-report infrastructure only; no app code, synthetic model behaviour, workflows, or Claude's no-insulin hints changed. No compile gate required for this script-only commit.
- Follow-up commit `883fd230`: the individual job-log request timeout is 30 s (other GitHub API calls retain 120 s), preventing a stalled signed-storage request from blocking a report for hours.
- Already-running Step 16 attempt-2 report `45957311944` uses its old checked-out script and is unaffected. Attempt 3 remains queued behind that run; check its dispatcher before another RUN change.


### 2026-11-27 — corrected ownership split (user update)

- User supersedes the previous item-7 assignment: Claude now owns REBOUND GUARD core/rule/unit tests on branch `learner-rebound` based on `autoisf-atc3`; ChatGPT must NOT implement that core. Claude's implementation must be OFF by default, suppress-only (hold SMB and cap temp basal after hypo treatment), and must never add insulin.
- ChatGPT owns only the lab evaluation of Claude's finished REBOUND GUARD after Step 16, Steps 17–18, and lab-model recalibration are complete. Compare on the recalibrated synthetic model against the 19.11 best set (target reference: hypo treatment ~24 g/day, BG <3.9 ~3.3%). Do not start this test before those prerequisites and the branch implementation are ready.
- ChatGPT continues Steps 16–18 and recalibration. Claude owns no-insulin hints 7c. Do not modify the hints branch or implement hints.
- User confirms `autoisf-atc3` app code remains frozen at `6f578b5b`; `689aa356` is one script-only `.github/scripts/ci_report.py` change (33 additions, 8 deletions), no app-code files. Do not push app code to the bench branch.
- Current Step 16 attempt 3 RUN matrix remains the six lines in RUN.txt. Attempt-3 dispatcher is still waiting; prior replay's report job is the remaining active job. Do not launch a competing batch. Latest observed branch head at this note: `98fff196` (STATUS-only record after RUN commit `7fcf5d94`).

### 2026-11-27 — ownership split and Step 16 attempt-2 evidence

- User split learner item 7: ChatGPT owns lab steps 16–18, shape recalibration, REBOUND GUARD on a new `learner-rebound` branch based on frozen `autoisf-atc3`, then food-first smart calculator and split bolus (each lab-first). Claude owns no-insulin hints 7c on its existing branch. Do not edit or merge that hints branch. Keep `autoisf-atc3` frozen at `6f578b5b`.
- Attempt 2 replay `45957311944`, code `0dc64d56`, 6/6 jobs succeeded. Evidence read directly from the six completed job logs while the generated report job was still waiting. Metrics columns: BG <70 / <54 / >180 / CV / hypo g/day / insulin U/day.

| Case | Metrics | LCR3 |
|---|---|---|
| CTRL13A, no LCR3 | 6.0 / 0.5 / 5.7 / 29.7 / 33.21 / 11.78 | PASS, exact Step 13a control |
| CAL + LCR3 | 6.0 / 0.5 / 5.7 / 29.7 / 33.21 / 11.78 | silent; 0/122 accepted |
| CRT_26.4 + LCR3 | 6.4 / 0.3 / 4.7 / 30.2 / 35.89 / 11.32 | silent; 0/122 accepted |
| CRT_17.6 + LCR3 | 4.3 / 0.2 / 7.2 / 29.5 / 26.79 / 12.26 | silent; 0/122 accepted |
| MEALX_0.7 + LCR3 | 6.0 / 0.5 / 5.7 / 29.7 / 33.21 / 11.78 | silent; 0/122; not independent (MEALX_0.7 is already in CAL) |
| TREAT_15 + LCR3 | 3.3 / 0.2 / 6.4 / 26.6 / 75.54 / 13.51 | silent; 0/122 accepted |

- Attempt-2 rejection counts: CAL = prior-insulin 65, COB-min-rate 43, prior-hypo-rebound 14; CRT_26.4 = COB-min-rate 59, prior-insulin 53, prior-hypo-rebound 10; CRT_17.6 = prior-insulin 72, COB-min-rate 36, prior-hypo-rebound 14; TREAT_15 = prior-insulin 62, COB-min-rate 40, prior-hypo-rebound 17, SMB within 6 h 3.
- Diagnostic split in `f051dd99` distinguishes prior SMB from prior manual correction. It did NOT run in attempt 2: `dispatch-a4c4de27.md` records code `0dc64d56`; `f051dd99` was committed later. Compile `45958813071` for `f051dd99` is green.
- Attempt 2 RUN commit `d53a50ab` dispatch `45959189491` is waiting behind attempt-1 workflow-dispatch/report `45957311944`. Per the RUN history, attempt 2 is pinned to code `0dc64d56`. Attempt 3 RUN commit `7fcf5d94` pinned to diagnostic code `f051dd99` (compile green), but its dispatcher `45960043127` was cancelled before any job started; it has no replay ID and does not count as a replay attempt. After attempt 2 completes, re-commit the same attempt-3 batch once to start the final permitted replay. No CTRL13A failure occurred.
- No app branch or hint branch was changed.


### Step 16 — LCR3 attempt 3 submitted (2026-11-27)

- Attempt 1 replay `45902096158`: 6/6 jobs succeeded, but LCR3 screening failed: CAL and TREAT_15 proposed CR+; see the detailed attempt-1 table below.
- Attempt 2 RUN commit `a4c4de27`, dispatcher/replay `45957311944`: all 6/6 replay jobs succeeded, 16,128 ticks each, 0 failed ticks. The no-LCR3 `CTRL13A` control exactly matched `6.0/0.5/5.7/29.7/33.21/11.78` and is PASS.
- Attempt-2 metrics (BG <70 / BG <54 / BG >180 / CV / hypo g/day / insulin U/day), reconstructed from all six completed public job logs because `run-45957311944.md` is not yet present (the report job remains in progress):

| Case | <70 | <54 | >180 | CV | Hypo g/day | Insulin U/day | LCR3 |
|---|---:|---:|---:|---:|---:|---:|---|
| CTRL13A, no LCR3 | 6.0 | 0.5 | 5.7 | 29.7 | 33.21 | 11.78 | control PASS |
| CAL + LCR3 | 6.0 | 0.5 | 5.7 | 29.7 | 33.21 | 11.78 | silent; 0/122 accepted |
| CRT_26.4 + LCR3 | 6.4 | 0.3 | 4.7 | 30.2 | 35.89 | 11.32 | silent; 0/122 accepted |
| CRT_17.6 + LCR3 | 4.3 | 0.2 | 7.2 | 29.5 | 26.79 | 12.26 | silent; 0/122 accepted |
| MEALX_0.7 + LCR3 | 6.0 | 0.5 | 5.7 | 29.7 | 33.21 | 11.78 | silent; 0/122 accepted; duplicate CAL |
| TREAT_15 + LCR3 | 3.3 | 0.2 | 6.4 | 26.6 | 75.54 | 13.51 | silent; 0/122 accepted |

- LCR3 audit rejection counts in attempt 2:
  - CAL: prior-insulin 65, COB minimum-rate 43, prior hypo/rebound 14.
  - CRT_26.4: COB minimum-rate 59, prior-insulin 53, prior hypo/rebound 10.
  - CRT_17.6: prior-insulin 72, COB minimum-rate 36, prior hypo/rebound 14.
  - MEALX_0.7 duplicate: prior-insulin 65, COB minimum-rate 43, prior hypo/rebound 14.
  - TREAT_15: prior-insulin 62, COB minimum-rate 40, prior hypo/rebound 17, SMB within 6 h 3.
- Attempt 2 is a real screening failure (no accepted observations; no planted direction demonstrated), not a replay/infrastructure failure. It is NOT a §4a stop: candidate screening failures are results, and the CTRL13A control passed. MEALX_0.7 is not an independent negative because it is already part of CAL.
- Procedure mismatch found: RUN.txt's comment said diagnostic source `f051dd99`, but dispatcher `dispatch-a4c4de27.md` records code commit `0dc64d56`. The six job logs confirm the old combined `prior-insulin` reason, so attempt 2 did not run the diagnostic split. Commit `f051dd99` (split prior-SMB vs prior-manual rejection reasons) was created at 07:29:31 UTC, after the attempt-2 replay jobs had completed around 07:23 UTC.
- Attempt 3 RUN commit `7fcf5d94` was created after `f051dd99`, with the same six-job matrix. At this check, `dispatch-7fcf5d94.md` and `compile-f051dd99.md` have not yet appeared, so no attempt-3 replay run ID is available; the dispatcher compile gate is pending. This is the third and final attempt for Step 16. After its result, record the screen and proceed to Step 17 unless a §4a stop occurs.
- Bench branch `autoisf-atc3` remains frozen at `6f578b5b`; no app code is pushed there.

### Handoff §13 re-audit — corrected result files (2026-11-26)

- The three run reports have now been regenerated with actual job summaries; earlier statements that they were empty/malformed are superseded. Use the report tables and audit lines, not the older manual reconstructions.
- Step 14: replay `45889976040`, 4/4 jobs successful. Both CTRL13A jobs PASS. LSTAGE0 seed 1–3 metrics: `6.0/0.5/5.7/29.7/33.21/11.78`, `4.0/0.1/4.2/27.7/24.11/10.92`, `4.8/0.3/4.5/28.9/29.64/11.63`. WATTR-only control exactly matches seed 1. Stage-0 counts are printed in the report; calibration and medical-procedure fields are unavailable.
- Step 15: official six-job replay `45894508242`, 6/6 successful; CTRL13A PASS with exact seed-1 reference `6.0/0.5/5.7/29.7/33.21/11.78`. LISF2 had zero accepted observations in every block and remained silent; planted CHGISF direction was not demonstrated. NOISE_0.12 and CANCH_96 remained silent. The MEALX_0.7 slot is not an independent negative because CAL already contains that setting.
- Additional replay `45893257489`: report is populated, 18/18 jobs successful, and its single CTRL13A control PASS. It is supplementary only, not the official Step-15 batch: its 18-job token matrix differs from the specified six-job matrix (in particular it lacks the specified CHGISF_1.35 upper test). Do not describe this report as empty or as the official Step-15 result.
- Step 16 initial replay `45902096158` completed 6/6 jobs but LCR3 screening failed (CAL and TREAT_15 proposed CR+); corrected causal filters are in `c9ee2b34`. Compile run `45906547105` is in progress. Initial result and exact metrics are recorded in the Step-16 attempt-1 section below.
- LCR3 implementation is report-only and OFF unless token `LCR3` is present. Final code tracks when COB reaches zero; it does not use the earlier 90%-absorption draft. The 13 g minimum meal, six-hour observation window, >=50% minimum-rate exclusion, acceptance gate and direction cutoffs are lab assumptions unless separately sourced. CRT_26.4/17.6 are ±20% perturbations of CRT_22, not yet proven to be the curriculum's exact meaning of “one step”. MEALX_0.7 repeats CAL and is not an independent negative.

### Step 16 — LCR3 attempt 1 (replay complete; card screen failed)

- Replay `45902096158`: all 6/6 jobs succeeded. Control without LCR3 matches the exact seed-1 reference `6.0/0.5/5.7/29.7/33.21/11.78`; do not confuse successful jobs with a passed card screen.
- Metrics order is BG<70 / BG<54 / BG>180 / CV / hypo-treatment g/day / insulin U/day:
  - CTRL13A, no LCR3: `6.0/0.5/5.7/29.7/33.21/11.78`
  - CRT_26.4 + LCR3: `6.4/0.3/4.7/30.2/35.89/11.32`; 53 accepted / 34 days; low rate 0.23, high-after-absorption rate 0.38; silent.
  - CRT_17.6 + LCR3: `4.3/0.2/7.2/29.5/26.79/12.26`; 67 accepted / 44 days; low rate 0.31, high rate 0.46; proposed CR+.
  - CAL + LCR3: `6.0/0.5/5.7/29.7/33.21/11.78`; 60 accepted / 39 days; low rate 0.30, high rate 0.43; proposed CR+ (baseline false positive).
  - MEALX_0.7 + LCR3: duplicate CAL, same metrics and CR+; not an independent negative.
  - TREAT_15 + LCR3: `3.3/0.2/6.4/26.6/75.54/13.51`; 69 accepted / 42 days; low rate 0.48, high rate 0.32; proposed CR+ (negative test FAIL).
- Thus the initial LCR3 screen FAILS: CAL baseline and TREAT_15 are not silent. CRT_17.6 produced CR+; CRT_26.4 remained silent, so the upper perturbation did not demonstrate the opposite direction.
- Root cause: the first LCR3 draft omitted causal exclusions already implemented by `crBlockVerdict`. Fix in source commit `c9ee2b34`: require LACAUSE; reject prior 120-minute hypo/rebound and insulin, SMB/manual corrections in the six-hour window, missing exact meal-bolus marker, sensor gaps/false lows, cannula/illness/activity and ambiguous low causes. LCR3 remains report-only.
- Compile run `45906547105` for the corrected source is in progress. Do not rerun until its compile gate is green and the previous report job has finished.
- DUMPCSV validation was strengthened in workflow commits `0bc02ddf` / `0dc64d56`: require one row per 5-minute tick and compare exported insulin/day with the replay summary within 1%. The next Step-16 rerun will add DUMPCSV only to the no-LCR3 control job to generate the corrected virtual CSV artifact.

### Step 16 — LCR3 implementation and batch in progress (2026-11-26)

- Added token `LCR3` in the permitted replay test source. It is OFF by default and report-only: no writes to `icNow`, `icP`, profile values, or insulin delivery.
- Screening uses logged meals of 13–60 g, an exact meal-bolus marker, a complete six-hour BG window, COB reaching zero within six hours, and excludes meals with at least 50% of COB absorption attributed to minimum-rate fallback. The 13 g floor, six-hour window, 50% cutoff, acceptance gate and direction thresholds are lab assumptions unless separately sourced; the 60 g cap and Stage-0 minimum-rate exclusion are curriculum-based.
- Direction thresholds (low-meal rate >=30% => CR+, high-after-meal rate >=40% with lows <=10% => CR-) reuse the existing CR audit's screening thresholds; this remains a report-only screening heuristic, not an applied learner formula.
- Initial code commits `97294e36`, `6cccef74` and `ba5517c0` are historical drafts. The first-run source was `ba5517c0`; corrected causal-filter source is `c9ee2b34`.
- Six jobs: CAL seed-1 CTRL13A without LCR3; CAL baseline with LCR3; CRT_26.4 and CRT_17.6 (±20% from CRT_22); MEALX_0.7 baseline negative; TREAT_15 negative. The MEALX_0.7 negative is identical to CAL because MEALX_0.7 is itself part of the calibrated model; this limitation is explicit.
- First RUN `2f3c9286` / replay `45902096158` completed 6/6 jobs but LCR3 screening FAILED; see the Step 16 attempt-1 table above. Corrected compile run `45906547105` is in progress; rerun Step 16 only after green compile and report-job completion.


### Step 15 — LISF2 batch dispatched (2026-11-26)

- Step 14 completed from raw job logs; both CTRL13A controls numerically matched all six reference metrics exactly. The generated run report had a log-availability race; no mismatch was observed.
- Source review: the report-only `LISF2` card implementation already exists in `HistoryReplayTest.kt` at code commit `8f83af45`; it is OFF unless the `LISF2` token is present and makes no `isfP` or insulin-delivery writes. Reuse that compiled code; no source edit was necessary for this step.
- Step 15 RUN commit correction: initial batch construction accidentally copied the entire Step 14 pipe-delimited batch as one job template. Corrected immediately in RUN commit `4106bc48` to six individual jobs (one CTRL13A seed-1 control; CHGISF_1.35 and CHGISF_0.8 planted cases; MEALX_0.7, NOISE_0.12 and CANCH_96 negative cases). The first malformed RUN commit `c116a400` is superseded; if it dispatches, its outcome is not used for Step 15.
- All jobs retain the CAL usual APP(a) settings and report-only audit tokens. CHGISF values are taken from existing `app22_batch.json` variants; CANCH_96 and NOISE_0.12 are the specified negative cases.
- Step 15 is complete: see the re-audit above and `run-45894508242.md` for the six-job result.


### Step 14 — results reconstructed from workflow job logs (2026-11-26)

- Replay run: `45889976040`; RUN commit `b6a1155c`; code commit `8f83af45`; compile gate GREEN.
- The generated `run-45889976040.md` is malformed: the report job read job logs before the public summary lines were available, leaving all four rows as `?` / `-` and omitting control verdicts. This is a report-generation race, not a replay failure. Reconstructed directly from the four completed replay job logs.
- Exact six-metric results (BG <70 / BG <54 / BG >180 / CV / hypo g/day / insulin U/day):
  - LSTAGE0 seed 1 + CTRL13A: `6.0 / 0.5 / 5.7 / 29.7 / 33.21 / 11.78` — exact reference match.
  - LSTAGE0 seed 2: `4.0 / 0.1 / 4.2 / 27.7 / 24.11 / 10.92` — exact reference match.
  - LSTAGE0 seed 3: `4.8 / 0.3 / 4.5 / 28.9 / 29.64 / 11.63` — exact reference match.
  - WATTR-only seed 1 + CTRL13A (no LSTAGE0): `6.0 / 0.5 / 5.7 / 29.7 / 33.21 / 11.78` — exact reference match.
- Both controls are therefore numerically PASS against the published CTRL13A reference, despite the report file omitting its automatic PASS label. No mismatch.
- LSTAGE0 per seed:
  - Seed 1: cannula 0d/0 meals; sensor/false-low 0d/0 meals; illness 0d/0 meals; activity 0d/0 meals; COB-min-rate 43 meals / 26 days; untreated low 36 episodes / 24 days.
  - Seed 2: cannula 0d/0 meals; sensor/false-low 0d/0 meals; illness 0d/0 meals; activity 0d/0 meals; COB-min-rate 58 meals / 38 days; untreated low 29 episodes / 21 days.
  - Seed 3: cannula 0d/0 meals; sensor/false-low 0d/0 meals; illness 0d/0 meals; activity 0d/0 meals; COB-min-rate 57 meals / 36 days; untreated low 38 episodes / 26 days.
  - Calibration and medical-procedure windows unavailable in replay schema.
- WATTR-only control: 360 crossings, 0 events, blocked-clean 2255, blocked-other 73; all six cards 0 proposals. LSTAGE0 is report-only and does not change the six metrics.
- Decision: Step 14 complete; continue to Step 15. The report script/workflow is outside the permitted edit scope, so preserve this manual log reconstruction and do not modify workflow files.


### Step 14 — LSTAGE0 completed (2026-11-26)

- Code commit: `8f83af4536868c85a648a7c6328dea31e339effd`; compile run `45888261389` **SUCCESS**.
- RUN commit: `b6a1155c`; dispatcher `45888296710`; replay run `45889976040` **SUCCESS**, 4/4 jobs, 16,128 ticks and 0 failed ticks per job.
- Six metrics (BG <70 / BG <54 / BG >180 / CV / hypo g/day / insulin U/day) from public job logs:
  - LSTAGE0 seed 1: `6.0/0.5/5.7/29.7/33.21/11.78`
  - LSTAGE0 seed 2: `4.0/0.1/4.2/27.7/24.11/10.92`
  - LSTAGE0 seed 3: `4.8/0.3/4.5/28.9/29.64/11.63`
  - WATTR-only seed 1 (without LSTAGE0): `6.0/0.5/5.7/29.7/33.21/11.78`
- Both `CTRL13A` controls **PASS** by exact comparison with Step 13a: LSTAGE0 seed 1 and WATTR-only seed 1. The generated `run-45889976040.md` currently has empty job-token/metric cells ("?"); exact metrics and audit lines were therefore extracted from the completed public job logs, not inferred from the generated table. Dispatcher note confirms compile green and run started.
- LSTAGE0 report-only counts:
  | Seed | Cannula days/meals | Sensor/false-low days/meals | Illness days/meals | Activity days/meals | COB-min-rate meals/days | Untreated-low episodes/days |
  |---:|---:|---:|---:|---:|---:|---:|
  | 1 | 0/0 | 0/0 | 0/0 | 0/0 | 43/26 | 36/24 |
  | 2 | 0/0 | 0/0 | 0/0 | 0/0 | 58/38 | 29/21 |
  | 3 | 0/0 | 0/0 | 0/0 | 0/0 | 57/36 | 38/26 |
- Calibration-window and medical-procedure exclusions remain unavailable in the synthetic replay schema. No replay behaviour changed; controls match exactly.

### Step 15 — LISF2 implementation and test batch (2026-11-26)

- Added token `LISF2` in the permitted replay test source, OFF by default and report-only. It records the AutoISF factor per tick and evaluates clean correction-response windows where the factor remained 1.0; samples are grouped into morning (05–10), day (11–16), evening (17–21), and night (22–04). Night samples additionally require IOB >= 0.30 U. Each block reports candidate/accepted/rejected counts, distinct days, median observed ISF, direction and 10% proposed step. It does not modify ISF, profile, basal, CR, or insulin delivery.
- This is an explicit test implementation of the curriculum's BerNie sign/10–20% direction and night-by-IOB requirements; the clean-window and factor=1.0 filters remain conservative lab assumptions. No accepted evidence means silent, never a proposal.
- Planned planted cases: `CHGISF_1.2` (stronger insulin -> ISF+), `CHGISF_0.8` (weaker insulin -> ISF-), and negatives `MEALX_0.7`, `NOISE_0.12`, `CANCH_96`; 3 seeds each. Include one CAL-usual seed-1 `CTRL13A` with LISF2 plus CAL-usual seeds 2–3. Expected CAL metrics remain the Step 13a references; any mismatch is STOP. Report-only proposal scores are results, not stops.
- Code commit is recorded separately below; dispatcher will gate the exact commit's compile before running the RUN batch.


### Step 14 — LSTAGE0 self-directed run prepared (2026-11-26)

- Adopted RULES §0 from commit `5fa32387`: compile and dispatch results are read from `lab/aimi-replay/results/`; the dispatcher queues behind compile and a busy lab. No waiting for Claude between steps.
- Cosmetic review fix: restored indentation of `var absorbed` in the permitted replay test source; code commit `8f83af4536868c85a648a7c6328dea31e339effd`. No learner formula or replay decision was changed.
- Step 14 batch: CAL usual APP(a), seeds 1–3 with `LSTAGE0`; seed 1 carries `CTRL13A`. A separate seed-1 WATTR control carries `LACAUSE,CRAUDIT,WATTR,CTRL13A` and deliberately omits `LSTAGE0`. WATTR's `LACAUSE` prerequisite is retained. Total: 4 jobs.
- Expected exact summary references (BG <70 / BG <54 / BG >180 / CV / hypo g/day / insulin U/day): seed 1 `6.0/0.5/5.7/29.7/33.21/11.78`; seed 2 `4.0/0.1/4.2/27.7/24.11/10.92`; seed 3 `4.8/0.3/4.5/28.9/29.64/11.63`. Both seed-1 control jobs are independently checked by `CTRL13A`.
- LSTAGE0 report-only counts to extract: cannula-age>72h days/meals; sensor-gap/false-low days/meals; illness days/meals; activity days/meals; COB-min-rate meals/days; untreated-low episodes/days. Calibration and medical-procedure fields are explicitly unavailable in the replay schema.
- The code compile gate is delegated to the RUN dispatcher per the new §0; do not manually wait for Actions. If the result file reports a compile failure, fix and retry according to §0.


### Step 13b — WATTR-on control completed; exact no-behaviour-change gate PASS (2026-11-26)

- Corrected retry RUN commit: `5f34b201565939236a93c252d3b5c53a08be034d`; dispatcher run [45853235877](https://github.com/Eretikstas/Apex/actions/runs/45853235877); replay run [45853284220](https://github.com/Eretikstas/Apex/actions/runs/45853284220), concluded SUCCESS.
- The replay plan and all 3/3 jobs succeeded. Each job used the verified APP(a)+CAL usual batch with `SPLITMIN_20,LACAUSE,CRAUDIT,WATTR`; each replay completed 16,128 ticks with 0 failed ticks. The invalid `SPLITMIN_60` run [45839851602](https://github.com/Eretikstas/Apex/actions/runs/45839851602) remains excluded.
- Public summaries compared against Step 13a, in column order BG <70 / BG <54 / BG >180 / CV / hypo treatment g/day / insulin U/day:

| Seed | <70 % | <54 % | >180 % | CV % | Hypo g/day | Insulin U/day | Step 13a gate |
|---:|---:|---:|---:|---:|---:|---:|---|
| 1 | 6.0 | 0.5 | 5.7 | 29.7 | 33.21 | 11.78 | Exact match |
| 2 | 4.0 | 0.1 | 4.2 | 27.7 | 24.11 | 10.92 | Exact match |
| 3 | 4.8 | 0.3 | 4.5 | 28.9 | 29.64 | 11.63 | Exact match |
| Mean | 4.93 | 0.30 | 4.80 | 28.77 | 28.99 | 11.44 | Exact match |

- Therefore the explicit Step 13b gate is PASS: all six summary metrics match Step 13a exactly for all three seeds. **WATTR did not change replay behaviour.** CRAUDIT remained counting-only; its ISF and CR accepted counts remained zero, as in Step 13a.
- Next: Step 13c planted-cause test, positive and negative cases, three seeds, using WATTR + CRAUDIT. Source inspection found that WATTR accumulates event objects but does not currently print or serialize them, so the test cannot produce the required per-card score table yet. Add report-only event/count logging in the permitted HistoryReplayTest.kt, compile green on the exact code commit, then run the 13c grid. No learner formula or production code change is planned.


### Step 13c — planted-cause exam, pre-run definition (2026-11-26)

- Step 13b passed its exact six-metric gate for all three seeds; Step 13c is now the next §5 step.
- Report-only WATTR evidence output added in `HistoryReplayTest.kt`, commit `7c8530b07bd608f882bb327fff20630e442a5959`. It prints event count, clean/other blockers, per-card direction counts, and each event's day/card/direction/miss/explained percentage. It does not change WATTR calculations, the learner, or production code.
- Exact-code compile gate: run [45874912845](https://github.com/Eretikstas/Apex/actions/runs/45874912845) concluded **SUCCESS** at 2026-11-26 18:35:04 UTC on the exact code commit `7c8530b07bd608f882bb327fff20630e442a5959`. All 11 substantive steps passed, including replay test-source compilation, AutoISF weight-boundary tests, replay audit helper tests, adaptation-status tests, APSResult status tests, FullDebug APK assembly and APK upload.
- Pre-run check (2026-11-26 19:37 Europe/Paris): no queued, in-progress, waiting or requested `aimi-replay.yml` runs were present. The 30-job Step 13c batch below is therefore authorized; the next change is the RUN.txt-only start commit. No workflow, production or learner-formula change is included.
- Planned exam: 10 cases × 3 seeds = 30 synthetic 56-day jobs. Every job carries `LACAUSE,CRAUDIT,WATTR,LEARNALL,LAISF,LACR`, so weight, CR and ISF cards can be scored together. No DUMPCSV.
- Positive cases: `WPP_0.005` (expect pp increase), `WBRK_0.6` (expect brake decrease), `WACC_0` (expect acce increase), `CRT_26.4` (20% above the Step 13b `CRT_22` baseline; this is the explicit interpretation of “CRT one step”), and `CHGISF_1.2` with the other CAL tokens `MEALX_0.7,SBIAS_0.45,TREAT_5` omitted.
- Negative cases use the CAL baseline plus one non-setting cause: `MEALX_0.7` alone, `CANCH_96` replacing `CANCH_72`, `NOISE_0.12` replacing `NOISE_0.07`, `DRIFT_2`, and `TREAT_15` replacing `TREAT_5`.
- Scoring per `LEARNER_ATTRIBUTION_SPEC.md` §3: each positive target must be correctly proposed in at least 2/3 seeds, with no other card on that positive case; false weight/CR/ISF proposals across all negative jobs must be no more than 1 per 28 days. A failing score is a test result, not an automatic stop. No parameter, production algorithm or live setting is changed by this run.
- Exact single RUN.txt batch line (30 jobs, order as above):

```text
SYNTH_56,SEED_1,CRT_22,NEEDDX_1.3,NOISEPHI_0.995,UKF,SMARTS,SPLIT_60,SPLITMIN_20,LEARNG3,NOIOB,SMBMIN_60,UAMMIN_60,LATECOB,LATEPCT_50,LATEB_2,LEARNB5,LBLOWMAX_6,LBFILL2,LBMIN_2,LEARNDURA,NOISE_0.07,CANCH_72,PCTCARD,PCTMAX_100,SBIAS_0.45,TREAT_5,CHG_28,CHGISF_1.2,MEALX_0.7,LACAUSE,CRAUDIT,LEARNALL,LAISF,LACR,WATTR,WPP_0.005|SYNTH_56,SEED_2,CRT_22,NEEDDX_1.3,NOISEPHI_0.995,UKF,SMARTS,SPLIT_60,SPLITMIN_20,LEARNG3,NOIOB,SMBMIN_60,UAMMIN_60,LATECOB,LATEPCT_50,LATEB_2,LEARNB5,LBLOWMAX_6,LBFILL2,LBMIN_2,LEARNDURA,NOISE_0.07,CANCH_72,PCTCARD,PCTMAX_100,SBIAS_0.45,TREAT_5,CHG_28,CHGISF_1.2,MEALX_0.7,LACAUSE,CRAUDIT,LEARNALL,LAISF,LACR,WATTR,WPP_0.005|SYNTH_56,SEED_3,CRT_22,NEEDDX_1.3,NOISEPHI_0.995,UKF,SMARTS,SPLIT_60,SPLITMIN_20,LEARNG3,NOIOB,SMBMIN_60,UAMMIN_60,LATECOB,LATEPCT_50,LATEB_2,LEARNB5,LBLOWMAX_6,LBFILL2,LBMIN_2,LEARNDURA,NOISE_0.07,CANCH_72,PCTCARD,PCTMAX_100,SBIAS_0.45,TREAT_5,CHG_28,CHGISF_1.2,MEALX_0.7,LACAUSE,CRAUDIT,LEARNALL,LAISF,LACR,WATTR,WPP_0.005|SYNTH_56,SEED_1,CRT_22,NEEDDX_1.3,NOISEPHI_0.995,UKF,SMARTS,SPLIT_60,SPLITMIN_20,LEARNG3,NOIOB,SMBMIN_60,UAMMIN_60,LATECOB,LATEPCT_50,LATEB_2,LEARNB5,LBLOWMAX_6,LBFILL2,LBMIN_2,LEARNDURA,NOISE_0.07,CANCH_72,PCTCARD,PCTMAX_100,SBIAS_0.45,TREAT_5,CHG_28,CHGISF_1.2,MEALX_0.7,LACAUSE,CRAUDIT,LEARNALL,LAISF,LACR,WATTR,WBRK_0.6|SYNTH_56,SEED_2,CRT_22,NEEDDX_1.3,NOISEPHI_0.995,UKF,SMARTS,SPLIT_60,SPLITMIN_20,LEARNG3,NOIOB,SMBMIN_60,UAMMIN_60,LATECOB,LATEPCT_50,LATEB_2,LEARNB5,LBLOWMAX_6,LBFILL2,LBMIN_2,LEARNDURA,NOISE_0.07,CANCH_72,PCTCARD,PCTMAX_100,SBIAS_0.45,TREAT_5,CHG_28,CHGISF_1.2,MEALX_0.7,LACAUSE,CRAUDIT,LEARNALL,LAISF,LACR,WATTR,WBRK_0.6|SYNTH_56,SEED_3,CRT_22,NEEDDX_1.3,NOISEPHI_0.995,UKF,SMARTS,SPLIT_60,SPLITMIN_20,LEARNG3,NOIOB,SMBMIN_60,UAMMIN_60,LATECOB,LATEPCT_50,LATEB_2,LEARNB5,LBLOWMAX_6,LBFILL2,LBMIN_2,LEARNDURA,NOISE_0.07,CANCH_72,PCTCARD,PCTMAX_100,SBIAS_0.45,TREAT_5,CHG_28,CHGISF_1.2,MEALX_0.7,LACAUSE,CRAUDIT,LEARNALL,LAISF,LACR,WATTR,WBRK_0.6|SYNTH_56,SEED_1,CRT_22,NEEDDX_1.3,NOISEPHI_0.995,UKF,SMARTS,SPLIT_60,SPLITMIN_20,LEARNG3,NOIOB,SMBMIN_60,UAMMIN_60,LATECOB,LATEPCT_50,LATEB_2,LEARNB5,LBLOWMAX_6,LBFILL2,LBMIN_2,LEARNDURA,NOISE_0.07,CANCH_72,PCTCARD,PCTMAX_100,SBIAS_0.45,TREAT_5,CHG_28,CHGISF_1.2,MEALX_0.7,LACAUSE,CRAUDIT,LEARNALL,LAISF,LACR,WATTR,WACC_0|SYNTH_56,SEED_2,CRT_22,NEEDDX_1.3,NOISEPHI_0.995,UKF,SMARTS,SPLIT_60,SPLITMIN_20,LEARNG3,NOIOB,SMBMIN_60,UAMMIN_60,LATECOB,LATEPCT_50,LATEB_2,LEARNB5,LBLOWMAX_6,LBFILL2,LBMIN_2,LEARNDURA,NOISE_0.07,CANCH_72,PCTCARD,PCTMAX_100,SBIAS_0.45,TREAT_5,CHG_28,CHGISF_1.2,MEALX_0.7,LACAUSE,CRAUDIT,LEARNALL,LAISF,LACR,WATTR,WACC_0|SYNTH_56,SEED_3,CRT_22,NEEDDX_1.3,NOISEPHI_0.995,UKF,SMARTS,SPLIT_60,SPLITMIN_20,LEARNG3,NOIOB,SMBMIN_60,UAMMIN_60,LATECOB,LATEPCT_50,LATEB_2,LEARNB5,LBLOWMAX_6,LBFILL2,LBMIN_2,LEARNDURA,NOISE_0.07,CANCH_72,PCTCARD,PCTMAX_100,SBIAS_0.45,TREAT_5,CHG_28,CHGISF_1.2,MEALX_0.7,LACAUSE,CRAUDIT,LEARNALL,LAISF,LACR,WATTR,WACC_0|SYNTH_56,SEED_1,CRT_26.4,NEEDDX_1.3,NOISEPHI_0.995,UKF,SMARTS,SPLIT_60,SPLITMIN_20,LEARNG3,NOIOB,SMBMIN_60,UAMMIN_60,LATECOB,LATEPCT_50,LATEB_2,LEARNB5,LBLOWMAX_6,LBFILL2,LBMIN_2,LEARNDURA,NOISE_0.07,CANCH_72,PCTCARD,PCTMAX_100,SBIAS_0.45,TREAT_5,CHG_28,CHGISF_1.2,MEALX_0.7,LACAUSE,CRAUDIT,LEARNALL,LAISF,LACR,WATTR|SYNTH_56,SEED_2,CRT_26.4,NEEDDX_1.3,NOISEPHI_0.995,UKF,SMARTS,SPLIT_60,SPLITMIN_20,LEARNG3,NOIOB,SMBMIN_60,UAMMIN_60,LATECOB,LATEPCT_50,LATEB_2,LEARNB5,LBLOWMAX_6,LBFILL2,LBMIN_2,LEARNDURA,NOISE_0.07,CANCH_72,PCTCARD,PCTMAX_100,SBIAS_0.45,TREAT_5,CHG_28,CHGISF_1.2,MEALX_0.7,LACAUSE,CRAUDIT,LEARNALL,LAISF,LACR,WATTR|SYNTH_56,SEED_3,CRT_26.4,NEEDDX_1.3,NOISEPHI_0.995,UKF,SMARTS,SPLIT_60,SPLITMIN_20,LEARNG3,NOIOB,SMBMIN_60,UAMMIN_60,LATECOB,LATEPCT_50,LATEB_2,LEARNB5,LBLOWMAX_6,LBFILL2,LBMIN_2,LEARNDURA,NOISE_0.07,CANCH_72,PCTCARD,PCTMAX_100,SBIAS_0.45,TREAT_5,CHG_28,CHGISF_1.2,MEALX_0.7,LACAUSE,CRAUDIT,LEARNALL,LAISF,LACR,WATTR|SYNTH_56,SEED_1,CRT_22,NEEDDX_1.3,NOISEPHI_0.995,UKF,SMARTS,SPLIT_60,SPLITMIN_20,LEARNG3,NOIOB,SMBMIN_60,UAMMIN_60,LATECOB,LATEPCT_50,LATEB_2,LEARNB5,LBLOWMAX_6,LBFILL2,LBMIN_2,LEARNDURA,NOISE_0.07,CANCH_72,PCTCARD,PCTMAX_100,CHG_28,CHGISF_1.2,LACAUSE,CRAUDIT,LEARNALL,LAISF,LACR,WATTR|SYNTH_56,SEED_2,CRT_22,NEEDDX_1.3,NOISEPHI_0.995,UKF,SMARTS,SPLIT_60,SPLITMIN_20,LEARNG3,NOIOB,SMBMIN_60,UAMMIN_60,LATECOB,LATEPCT_50,LATEB_2,LEARNB5,LBLOWMAX_6,LBFILL2,LBMIN_2,LEARNDURA,NOISE_0.07,CANCH_72,PCTCARD,PCTMAX_100,CHG_28,CHGISF_1.2,LACAUSE,CRAUDIT,LEARNALL,LAISF,LACR,WATTR|SYNTH_56,SEED_3,CRT_22,NEEDDX_1.3,NOISEPHI_0.995,UKF,SMARTS,SPLIT_60,SPLITMIN_20,LEARNG3,NOIOB,SMBMIN_60,UAMMIN_60,LATECOB,LATEPCT_50,LATEB_2,LEARNB5,LBLOWMAX_6,LBFILL2,LBMIN_2,LEARNDURA,NOISE_0.07,CANCH_72,PCTCARD,PCTMAX_100,CHG_28,CHGISF_1.2,LACAUSE,CRAUDIT,LEARNALL,LAISF,LACR,WATTR|SYNTH_56,SEED_1,CRT_22,NEEDDX_1.3,NOISEPHI_0.995,UKF,SMARTS,SPLIT_60,SPLITMIN_20,LEARNG3,NOIOB,SMBMIN_60,UAMMIN_60,LATECOB,LATEPCT_50,LATEB_2,LEARNB5,LBLOWMAX_6,LBFILL2,LBMIN_2,LEARNDURA,NOISE_0.07,CANCH_72,PCTCARD,PCTMAX_100,SBIAS_0.45,TREAT_5,CHG_28,CHGISF_1.2,MEALX_0.7,LACAUSE,CRAUDIT,LEARNALL,LAISF,LACR,WATTR|SYNTH_56,SEED_2,CRT_22,NEEDDX_1.3,NOISEPHI_0.995,UKF,SMARTS,SPLIT_60,SPLITMIN_20,LEARNG3,NOIOB,SMBMIN_60,UAMMIN_60,LATECOB,LATEPCT_50,LATEB_2,LEARNB5,LBLOWMAX_6,LBFILL2,LBMIN_2,LEARNDURA,NOISE_0.07,CANCH_72,PCTCARD,PCTMAX_100,SBIAS_0.45,TREAT_5,CHG_28,CHGISF_1.2,MEALX_0.7,LACAUSE,CRAUDIT,LEARNALL,LAISF,LACR,WATTR|SYNTH_56,SEED_3,CRT_22,NEEDDX_1.3,NOISEPHI_0.995,UKF,SMARTS,SPLIT_60,SPLITMIN_20,LEARNG3,NOIOB,SMBMIN_60,UAMMIN_60,LATECOB,LATEPCT_50,LATEB_2,LEARNB5,LBLOWMAX_6,LBFILL2,LBMIN_2,LEARNDURA,NOISE_0.07,CANCH_72,PCTCARD,PCTMAX_100,SBIAS_0.45,TREAT_5,CHG_28,CHGISF_1.2,MEALX_0.7,LACAUSE,CRAUDIT,LEARNALL,LAISF,LACR,WATTR|SYNTH_56,SEED_1,CRT_22,NEEDDX_1.3,NOISEPHI_0.995,UKF,SMARTS,SPLIT_60,SPLITMIN_20,LEARNG3,NOIOB,SMBMIN_60,UAMMIN_60,LATECOB,LATEPCT_50,LATEB_2,LEARNB5,LBLOWMAX_6,LBFILL2,LBMIN_2,LEARNDURA,NOISE_0.07,CANCH_96,PCTCARD,PCTMAX_100,SBIAS_0.45,TREAT_5,CHG_28,CHGISF_1.2,MEALX_0.7,LACAUSE,CRAUDIT,LEARNALL,LAISF,LACR,WATTR|SYNTH_56,SEED_2,CRT_22,NEEDDX_1.3,NOISEPHI_0.995,UKF,SMARTS,SPLIT_60,SPLITMIN_20,LEARNG3,NOIOB,SMBMIN_60,UAMMIN_60,LATECOB,LATEPCT_50,LATEB_2,LEARNB5,LBLOWMAX_6,LBFILL2,LBMIN_2,LEARNDURA,NOISE_0.07,CANCH_96,PCTCARD,PCTMAX_100,SBIAS_0.45,TREAT_5,CHG_28,CHGISF_1.2,MEALX_0.7,LACAUSE,CRAUDIT,LEARNALL,LAISF,LACR,WATTR|SYNTH_56,SEED_3,CRT_22,NEEDDX_1.3,NOISEPHI_0.995,UKF,SMARTS,SPLIT_60,SPLITMIN_20,LEARNG3,NOIOB,SMBMIN_60,UAMMIN_60,LATECOB,LATEPCT_50,LATEB_2,LEARNB5,LBLOWMAX_6,LBFILL2,LBMIN_2,LEARNDURA,NOISE_0.07,CANCH_96,PCTCARD,PCTMAX_100,SBIAS_0.45,TREAT_5,CHG_28,CHGISF_1.2,MEALX_0.7,LACAUSE,CRAUDIT,LEARNALL,LAISF,LACR,WATTR|SYNTH_56,SEED_1,CRT_22,NEEDDX_1.3,NOISEPHI_0.995,UKF,SMARTS,SPLIT_60,SPLITMIN_20,LEARNG3,NOIOB,SMBMIN_60,UAMMIN_60,LATECOB,LATEPCT_50,LATEB_2,LEARNB5,LBLOWMAX_6,LBFILL2,LBMIN_2,LEARNDURA,NOISE_0.12,CANCH_72,PCTCARD,PCTMAX_100,SBIAS_0.45,TREAT_5,CHG_28,CHGISF_1.2,MEALX_0.7,LACAUSE,CRAUDIT,LEARNALL,LAISF,LACR,WATTR|SYNTH_56,SEED_2,CRT_22,NEEDDX_1.3,NOISEPHI_0.995,UKF,SMARTS,SPLIT_60,SPLITMIN_20,LEARNG3,NOIOB,SMBMIN_60,UAMMIN_60,LATECOB,LATEPCT_50,LATEB_2,LEARNB5,LBLOWMAX_6,LBFILL2,LBMIN_2,LEARNDURA,NOISE_0.12,CANCH_72,PCTCARD,PCTMAX_100,SBIAS_0.45,TREAT_5,CHG_28,CHGISF_1.2,MEALX_0.7,LACAUSE,CRAUDIT,LEARNALL,LAISF,LACR,WATTR|SYNTH_56,SEED_3,CRT_22,NEEDDX_1.3,NOISEPHI_0.995,UKF,SMARTS,SPLIT_60,SPLITMIN_20,LEARNG3,NOIOB,SMBMIN_60,UAMMIN_60,LATECOB,LATEPCT_50,LATEB_2,LEARNB5,LBLOWMAX_6,LBFILL2,LBMIN_2,LEARNDURA,NOISE_0.12,CANCH_72,PCTCARD,PCTMAX_100,SBIAS_0.45,TREAT_5,CHG_28,CHGISF_1.2,MEALX_0.7,LACAUSE,CRAUDIT,LEARNALL,LAISF,LACR,WATTR|SYNTH_56,SEED_1,CRT_22,NEEDDX_1.3,NOISEPHI_0.995,UKF,SMARTS,SPLIT_60,SPLITMIN_20,LEARNG3,NOIOB,SMBMIN_60,UAMMIN_60,LATECOB,LATEPCT_50,LATEB_2,LEARNB5,LBLOWMAX_6,LBFILL2,LBMIN_2,LEARNDURA,NOISE_0.07,CANCH_72,PCTCARD,PCTMAX_100,SBIAS_0.45,TREAT_5,CHG_28,CHGISF_1.2,MEALX_0.7,LACAUSE,CRAUDIT,LEARNALL,LAISF,LACR,WATTR,DRIFT_2|SYNTH_56,SEED_2,CRT_22,NEEDDX_1.3,NOISEPHI_0.995,UKF,SMARTS,SPLIT_60,SPLITMIN_20,LEARNG3,NOIOB,SMBMIN_60,UAMMIN_60,LATECOB,LATEPCT_50,LATEB_2,LEARNB5,LBLOWMAX_6,LBFILL2,LBMIN_2,LEARNDURA,NOISE_0.07,CANCH_72,PCTCARD,PCTMAX_100,SBIAS_0.45,TREAT_5,CHG_28,CHGISF_1.2,MEALX_0.7,LACAUSE,CRAUDIT,LEARNALL,LAISF,LACR,WATTR,DRIFT_2|SYNTH_56,SEED_3,CRT_22,NEEDDX_1.3,NOISEPHI_0.995,UKF,SMARTS,SPLIT_60,SPLITMIN_20,LEARNG3,NOIOB,SMBMIN_60,UAMMIN_60,LATECOB,LATEPCT_50,LATEB_2,LEARNB5,LBLOWMAX_6,LBFILL2,LBMIN_2,LEARNDURA,NOISE_0.07,CANCH_72,PCTCARD,PCTMAX_100,SBIAS_0.45,TREAT_5,CHG_28,CHGISF_1.2,MEALX_0.7,LACAUSE,CRAUDIT,LEARNALL,LAISF,LACR,WATTR,DRIFT_2|SYNTH_56,SEED_1,CRT_22,NEEDDX_1.3,NOISEPHI_0.995,UKF,SMARTS,SPLIT_60,SPLITMIN_20,LEARNG3,NOIOB,SMBMIN_60,UAMMIN_60,LATECOB,LATEPCT_50,LATEB_2,LEARNB5,LBLOWMAX_6,LBFILL2,LBMIN_2,LEARNDURA,NOISE_0.07,CANCH_72,PCTCARD,PCTMAX_100,SBIAS_0.45,TREAT_15,CHG_28,CHGISF_1.2,MEALX_0.7,LACAUSE,CRAUDIT,LEARNALL,LAISF,LACR,WATTR|SYNTH_56,SEED_2,CRT_22,NEEDDX_1.3,NOISEPHI_0.995,UKF,SMARTS,SPLIT_60,SPLITMIN_20,LEARNG3,NOIOB,SMBMIN_60,UAMMIN_60,LATECOB,LATEPCT_50,LATEB_2,LEARNB5,LBLOWMAX_6,LBFILL2,LBMIN_2,LEARNDURA,NOISE_0.07,CANCH_72,PCTCARD,PCTMAX_100,SBIAS_0.45,TREAT_15,CHG_28,CHGISF_1.2,MEALX_0.7,LACAUSE,CRAUDIT,LEARNALL,LAISF,LACR,WATTR|SYNTH_56,SEED_3,CRT_22,NEEDDX_1.3,NOISEPHI_0.995,UKF,SMARTS,SPLIT_60,SPLITMIN_20,LEARNG3,NOIOB,SMBMIN_60,UAMMIN_60,LATECOB,LATEPCT_50,LATEB_2,LEARNB5,LBLOWMAX_6,LBFILL2,LBMIN_2,LEARNDURA,NOISE_0.07,CANCH_72,PCTCARD,PCTMAX_100,SBIAS_0.45,TREAT_15,CHG_28,CHGISF_1.2,MEALX_0.7,LACAUSE,CRAUDIT,LEARNALL,LAISF,LACR,WATTR
```

- Start: RUN.txt commit `38687ae1cb4881a1d953c8163483f0a98fb43ed2`; dispatcher [45877920522](https://github.com/Eretikstas/Apex/actions/runs/45877920522) completed successfully; replay [45877944190](https://github.com/Eretikstas/Apex/actions/runs/45877944190) was accepted with a successful plan and 30 replay jobs. At the latest poll, 20 jobs were running and 10 queued; no replay job had failed. Results are pending. Do not start another batch or proceed beyond 13c; §5 says STOP after the planted-cause report.

### RUN.txt correction — invalid token variant detected (2026-11-26)

- Post-commit read-back of RUN commit `fbf4420702a97de76d884b244c14bcb56157a25a` exposed a transcription error: the batch line contains `SPLITMIN_60`, while the verified Step 13a / current RUN reference is `SPLITMIN_20`.
- Therefore the batch from `fbf44207` is **not a valid WATTR control** and must not be used for any metric comparison or to authorize Step 13c. Whether its dispatcher/replay has already started is not exposed by the available run-list actions; any such run must be classified invalid.
- Corrective action: restore the exact verified `SPLITMIN_20` token, keeping all other APP(a)+CAL tokens and WATTR/CRAUDIT/LACAUSE unchanged. The corrected RUN commit is a retry of this same control, not a new §5 step. If the first dispatch is still active, the repository busy guard should prevent overlap; do not bypass it.

### Step 13c — planted-cause exam; RESULT recorded (2026-11-26)

- RUN commit: `38687ae1`; replay run [45877944190](https://github.com/Eretikstas/Apex/actions/runs/45877944190); WATTR report-only print commit `7c8530b07bd608f882bb327fff20630e442a5959`; exact-code compile run [45874912845](https://github.com/Eretikstas/Apex/actions/runs/45874912845) was green.
- Claude review: 30/30 replay jobs succeeded; each completed 16,128 ticks. The required isolated control (13b seed 1 + WATTR, without LEARNALL/LAISF/LACR) was omitted from this batch; add it to each following step batch as a separate control job.
- Score (Claude counted from public logs):

| Planted case / card | Correct proposals |
|---|---:|
| WPP_0.005 → pp increase | 0/3 |
| WBRK_0.6 → brake decrease | 0/3 |
| WACC_0 → acce increase | 0/3 |
| CRT_26.4 → CR card | 0/3 |
| CHGISF_1.2 → ISF card | 0/3 (ISF card silent; 0 accepted observations in every job) |

- Negative cases: 15 jobs = 840 synthetic days; 9 WATTR weight events, or 1 event per 93.3 days (below the limit of 1 per 28 days). Events included dura/lower and also occurred in negatives. ISF card false proposals: **0**. Some `explained_pct` values exceeded 100%; label these **overshoot**.
- Correction after Claude's source review: the apparent ISF 162 → 203 shift was caused by the percentage card's `pct-` steps via `setPct()` (`isfP /= f`), not by an ISF-card proposal. The CHGISF_1.2 planted case therefore scores **ISF card 0/3**; the ISF card was silent with zero accepted observations in every job. The percentage card's 1–2 downward steps in all 30 jobs were expected under its low-percentage rule. The overall verdict remains unchanged: no card found its planted cause.
- Main WATTR limitation: `blocked-clean` was about 1,300–3,700 per job while events were 0–2. The 180-minute clean window rarely exists in this virtual child (about 30 g/day hypo treatment). This explains the very low event yield; it does not validate the proposed weights.
- Verdict: no weight, CR or ISF card passes the planted-cause exam. No card goes to the app. Bad scores are recorded as results, not stops; no learner formula or production code changed in 13c.

### Step 14 — Claude review clarifications and pre-run gate (2026-11-26)

- Claude reviewed code commit `4a8eff367683d4861fb6716df336873902c4323d`: the `LSTAGE0` audit is print-only, but its row-flag block (afterMeal/afterHypo/other row flags) now executes when `LSTAGE0` is present even without `LACAUSE`. To detect any unintended behaviour coupling, the Step 14 batch must include both controls: (1) APP(a)+CAL usual seeds 1–3 with `LSTAGE0`; (2) the exact Step 13b seed 1 APP(a)+CAL line with `WATTR`, but without `LSTAGE0`.
- Cosmetic source fix still required: restore indentation on `var absorbed = ...` in the next permitted code commit. Do not make that commit or start Step 14 until Claude confirms compile run `45886787811` is green on exact code commit `4a8eff36`.
- After green confirmation, make the indentation-only source correction, compile that exact commit, then prepare one RUN batch containing four jobs: APP(a)+CAL usual seeds 1–3 with `LSTAGE0`, plus the isolated Step 13b seed 1 + `WATTR` control without `LSTAGE0`. The three LSTAGE0 jobs also supply the per-rule drop counts. Compare all six summary metrics exactly to Step 13a for each matching seed; the WATTR-only control must exactly match seed 1. Any mismatch is STOP.
- Required APP(a)+CAL usual references (BG <70 / BG <54 / BG >180 / CV / hypo g/day / insulin U/day): seed 1 `6.0/0.5/5.7/29.7/33.21/11.78`; seed 2 `4.0/0.1/4.2/27.7/24.11/10.92`; seed 3 `4.8/0.3/4.5/28.9/29.64/11.63`.
- Step 14 report must list drop counts per Stage-0 rule. Continue to Step 15 only if both control comparisons match exactly; otherwise STOP. The current compile is still running, so no Step 14 RUN line has been committed.

### Step 14 — ISF summary audit from Step 13c logs (read-only; no ISF-card change)

- The aggregate `ISF 162 -> 203` (or `162 -> 180`) is **not evidence of an ISF-card proposal**. The source path shows it is the side effect of the `pct-` card:
  1. `learnAllCheck(day)` runs at 06:00 on daily checks; `laGap` defaults to 3 days and the first eligible check is day 7.
  2. The first-priority rule allows `pct-` when the selected low percentage is >4% and `pctNow > 0.8001`: it sets `pctNow` down by 0.10 and labels the card `pct-`.
  3. `setPct(np)` scales basal by `f = np / pctNow`, and explicitly rescales `isfP /= f` (and `icNow /= f`) to preserve the profile's percentage semantics.
  4. Thus one step gives `162 / 0.9 = 180`; two steps give `162 / 0.8 = 202.5`, printed as 203. The final log card counts show `pct-=1` or `pct-=2`, with `pct=90` or `pct=80`.
- The independent ISF card is later in the same priority chain and requires `isfSeen()` evidence; its gate requires at least 5 accepted observations across at least 3 days. Public `AIMI audit ISF` lines in these jobs show zero accepted observations, so that ISF-card branch did not pass its gate.
- Exact daily day-index lines (`AIMI replay: learner ALL day ...`) are not present in the public job-log output available for this run; therefore the exact check-day indices cannot be verified from these logs. The code establishes only that the checks are at 06:00, begin no earlier than day 7, and are spaced by at least 3 days. Do not invent exact days.
- No ISF card logic is changed in Step 14. Keep this as a read-only explanation; the Stage-0 implementation remains report-only.

### Step 13b WATTR-on control — corrected RUN commit submitted (2026-11-26)

- Corrected RUN.txt commit: `c0c6fb722a6a653c18d46703ba220c4a972ba0b7`. Read-back confirms the three-job batch is APP(a)+CAL usual, seeds 1–3, with `LACAUSE,CRAUDIT,WATTR` and the verified `SPLITMIN_20`; no DUMPCSV or other token.
- The earlier RUN commit `fbf4420702a97de76d884b244c14bcb56157a25a` contains the mistaken `SPLITMIN_60` and is invalid. Its results, if any, must be ignored.
- The available GitHub Actions read interface here does not expose general push/dispatch run listing. The commit-to-workflow lookup returned no PR-triggered runs for either RUN commit, so no dispatcher/replay run ID or job state is yet verified. Do not infer that the corrected batch has completed.
- Next: locate/check the dispatcher and replay generated by corrected commit `c0c6fb72`, read all three public summaries, and compare them exactly with the Step 13a per-seed values above. Proceed to 13c only if all six metrics match for every seed; any difference is the explicit STOP condition.


### Step 13b — WATTR report-only control, pre-run (2026-11-26)

- The previous Step 13b no-token control is complete: APP(a) + CAL usual, seeds 1–3, with `CRAUDIT` and without `WATTR`; all six summary metrics exactly matched the corresponding Step 13a seed references. This establishes the no-token invariant only.
- Claude's Step 13b review item 1 is implemented in code commit `a6557873c0b13e4028b5e37d32264f3fa9155ece`: record tick `t` in `wAttrOtherProposalTimes` when LEARNB5 changes a basal hour, when PCTCARD changes percentage, and when LAISF/LACR makes an ISF/CR proposal; WATTR's own events are not added. Read-back of the current source confirms these three sites at lines 1292, 1379 and 1681.
- Current replay source head: `631fa8fcaaa1bd9f7d46b11883b3199fe79623fc`. Exact-code compile gate: run [45799076606](https://github.com/Eretikstas/Apex/actions/runs/45799076606), SUCCESS; all 11 substantive steps completed successfully, including replay test compilation, weight boundaries, audit helpers, adaptation status, APSResult status, FullDebug APK assembly and upload.
- No replay was active before this handoff; the latest prior control run [45813107701](https://github.com/Eretikstas/Apex/actions/runs/45813107701) has its plan and both replay jobs completed successfully.
- Following Claude's Step 13b review item 2, this is the WATTR-on report-only control: APP(a) + CAL usual, seeds 1–3, same tokens as the no-token control with only `WATTR` added. No DUMPCSV or other scenario/learner token is added. Exact single batch line for the following RUN.txt commit:

```text
SYNTH_56,SEED_1,CRT_22,NEEDDX_1.3,NOISEPHI_0.995,UKF,SMARTS,SPLIT_60,SPLITMIN_20,LEARNG3,NOIOB,SMBMIN_60,UAMMIN_60,LATECOB,LATEPCT_50,LATEB_2,LEARNB5,LBLOWMAX_6,LBFILL2,LBMIN_2,LEARNDURA,NOISE_0.07,CANCH_72,PCTCARD,PCTMAX_100,SBIAS_0.45,TREAT_5,CHG_28,CHGISF_1.2,MEALX_0.7,LACAUSE,CRAUDIT,WATTR|SYNTH_56,SEED_2,CRT_22,NEEDDX_1.3,NOISEPHI_0.995,UKF,SMARTS,SPLIT_60,SPLITMIN_20,LEARNG3,NOIOB,SMBMIN_60,UAMMIN_60,LATECOB,LATEPCT_50,LATEB_2,LEARNB5,LBLOWMAX_6,LBFILL2,LBMIN_2,LEARNDURA,NOISE_0.07,CANCH_72,PCTCARD,PCTMAX_100,SBIAS_0.45,TREAT_5,CHG_28,CHGISF_1.2,MEALX_0.7,LACAUSE,CRAUDIT,WATTR|SYNTH_56,SEED_3,CRT_22,NEEDDX_1.3,NOISEPHI_0.995,UKF,SMARTS,SPLIT_60,SPLITMIN_20,LEARNG3,NOIOB,SMBMIN_60,UAMMIN_60,LATECOB,LATEPCT_50,LATEB_2,LEARNB5,LBLOWMAX_6,LBFILL2,LBMIN_2,LEARNDURA,NOISE_0.07,CANCH_72,PCTCARD,PCTMAX_100,SBIAS_0.45,TREAT_5,CHG_28,CHGISF_1.2,MEALX_0.7,LACAUSE,CRAUDIT,WATTR
```

- Gate: compare each seed's six public summary metrics (<70, <54, >180, CV, hypo g/day, insulin U/day) with Step 13a: seed 1 `6.0/0.5/5.7/29.7/33.21/11.78`; seed 2 `4.0/0.1/4.2/27.7/24.11/10.92`; seed 3 `4.8/0.3/4.5/28.9/29.64/11.63`. Every value must match exactly. Any difference means **WATTR changes behaviour**: STOP under Claude's instruction. If all match, continue to Step 13c as §5 directs.
- This is a pre-run note; no Step 13c batch is authorized until the WATTR-on control has completed and its exact per-seed comparison is recorded.


### Step 13b — pre-run gates confirmed, APP control queued for RUN.txt

- Compile gate for code head `631fa8fc`: GREEN. GitHub Actions run [45799076606](https://github.com/Eretikstas/Apex/actions/runs/45799076606) concluded SUCCESS; all checks passed: replay test-source compilation, AutoISF weight boundaries, replay audit helpers, adaptation status builder, in-memory AIMI status through APSResult, laboratory FullDebug APK assembly and upload.
- DUMPCSV gate: PASS per Claude review of replay run [45732148887](https://github.com/Eretikstas/Apex/actions/runs/45732148887): 16,128 rows, 5-minute spacing, and second-half CSV metrics exactly match the public summary (BG <70 6.0%, BG <54 0.5%, BG >180 5.7%, CV 29.7%, hypo 33.21 g/day, insulin 11.78 U/day).
- Step 13b control intent: APP(a) + CAL usual, seeds 1–3, with `LACAUSE,CRAUDIT` retained and `WATTR` deliberately absent. Dispatcher inspection after start showed it reads only the first non-comment RUN.txt line; therefore this first launch contains seed 1 only. Seeds 2–3 are not yet started and must be sent together on one `|`-separated batch line after seed 1 completes. Compare each seed with the Step 13a APP reference; do not infer a three-seed result from the first job.
- Seed 1 completed in replay run [45812102680](https://github.com/Eretikstas/Apex/actions/runs/45812102680): 1/1 replay job success, 16,128 ticks, 0 failed ticks. Public summary = `6.0 / 0.5 / 5.7 / 29.7 / 33.21 / 11.78` (<70 / <54 / >180 / CV / hypo g/day / insulin U/day), exactly matching Step 13a seed 1.
- RUN dispatch detail: commit `50a06354` launched seed 1 only because the dispatcher reads the first non-comment line. After that run completed, commit `6bb2be14` submitted seeds 2 and 3 together with `|`; both jobs completed successfully. This used RUN.txt only; no workflow or source files were changed.
- Step 13b APP control results (all public summaries; columns: BG <70 / BG <54 / BG >180 / CV / hypo treatment g/day / insulin U/day):

| Seed | <70 % | <54 % | >180 % | CV % | Hypo g/day | Insulin U/day | Step 13a same-seed control |
|---:|---:|---:|---:|---:|---:|---:|---|
| 1 | 6.0 | 0.5 | 5.7 | 29.7 | 33.21 | 11.78 | Exact match |
| 2 | 4.0 | 0.1 | 4.2 | 27.7 | 24.11 | 10.92 | Exact match |
| 3 | 4.8 | 0.3 | 4.5 | 28.9 | 29.64 | 11.63 | Exact match |
| Mean | 4.93 | 0.30 | 4.80 | 28.77 | 28.99 | 11.44 | Exact match |

- Replay runs: seed 1 [45812102680](https://github.com/Eretikstas/Apex/actions/runs/45812102680), seed 2–3 [45813107701](https://github.com/Eretikstas/Apex/actions/runs/45813107701); both concluded SUCCESS, all 3/3 replay jobs successful. Each replay emitted 16,128 ticks with 0 failed ticks. CRAUDIT was present and logged as counting-only; WATTR was absent as required for the no-token control.
- Result: no-token APP control is unchanged for all three seeds versus Step 13a. Step 13b control complete. **STOP for Claude review. Do not start Step 13c or any later step.**


### DUMPCSV corrected export — pre-run note, 2026-11-26

### AIMI_DUMPCSV_CHECK — dispatch handoff, 2026-11-26

- Pre-run marker recorded before RUN change. RUN commit: 55655b495b0b6159e54a158b38c387c47608d563; the single APP(a)+CAL usual, seed 1 line is unchanged apart from the comment header and retains DUMPCSV.
- Compile gate for the export code commit 00399a2a8c9939e961cf27f712616b101db6578a: PASS, workflow run 45726257144. Comparison tests: PASS, runs 45726259582 and 45726263504.
- Push event for the preceding STATUS commit 25c755f04e88e4972c755063096a3d2ac25e1593 created run 45731970829; its plan and replay jobs were skipped. Inspection of .github/workflows/aimi-replay.yml confirms push is only a no-op trigger; actual replay requires workflow_dispatch with prefs/batch inputs.
- The available GitHub actions here expose read/log/rerun operations but no workflow_dispatch action. No replay job has run for RUN commit 55655b49; therefore rows, 5-minute step and insulin reconciliation are NOT YET VERIFIED.
- Claude handoff required: dispatch the existing workflow (do not edit workflow files) with the exact RUN.txt line, algo=autoisf, sim=true, learn=false as required by RULES §2. After completion, record rows, step_min, CSV insulin/day, summary insulin/day and relative difference here. Do not start 13b until DUMPCSV check passes.

- AIMI_DUMPCSV_CHECK: one APP(a)+CAL usual, seed 1 replay; verify 16,128 rows, 5-minute spacing, and |CSV insulin/day − summary insulin/day| / summary insulin/day ≤ 1%. The DUMPCSV token is opt-in; no-token outputs must remain unchanged. This is the pre-run authorization/check line; RUN.txt will be committed only after this STATUS update.

- Claude's 25.11 Results block requires one row per 5-minute tick and insulin_u = basal delivered in that interval + SMB + manual bolus delivered in that tick.
- Code commit: 00399a2a8c9939e961cf27f712616b101db6578a. Export now emits each replay row directly (288 rows/day), uses the next row's delivered basal rate × 5/60 plus this tick's units and manual units, and prints a reconciliation line with row count, step and total insulin.
- Comparison commits: 583f90de63d9076b612942b4792c124850d593f1 (hourly mean/SD use clock-hour MAE; W1 remains for distributions) and a2e13397c78b2cf965a7d5527ef9244e1c93ff8f (hour-order unit test).
- Compile run for code commit 00399a2a: 45726257144, currently in progress. Comparison unit-test workflow runs 45726257141 / 45726259582 / 45726263504, currently in progress. No DUMPCSV replay has been started for these fixes.
- Planned single RUN job: existing APP(a) + CAL usual, seed 1 preference line, with DUMPCSV only as the export flag; no additional learner or scenario tokens. Keep the existing token list unchanged.
- Required checks from finished public job log: exactly one CSV row per replay 5-minute tick (expected 56 × 288 = 16,128 rows; step_min=5); compute insulin_u sum / 56 days and compare with the run-summary insulin U/day; absolute relative difference must be ≤1%. If any check fails, report it without advancing to 13b.
- RUN.txt has not been changed; this note precedes its run-start commit.


### Side task — virtual/real CGM comparison (separate from RULES §5 chain)

- Scope: synthetic virtual-child data only. No real CGM data, private profiles, or patient identifiers are written to the repository or public artifacts.
- Added opt-in DUMPCSV in HistoryReplayTest.kt, OFF unless explicitly present in replay preferences. It exports virtual model true BG, sensor BG, total delivered insulin, logged meal carbs, and hypo-treatment carbs, aggregated into 5-minute rows. Basal is integrated from the one-minute delivered U/hour rate; SMB and manual boluses are added.
- Added lab/aimi-replay/tools/compare_days.py (Python 3 + numpy only) and a synthetic-only unit test. The comparison reports hourly BG mean/SD, CV, 6-hour low/high percentages, overnight slope, meal response, ROC/drop percentiles, low episodes/context, and 30/60/120-minute autocorrelation. Thresholds are declared at the top of the script. Meal-bolus context is an explicitly labeled proxy because the requested CSV has combined insulin and no separate bolus column.
- Updated the replay workflow to extract only the opt-in synthetic CSV into a separate artifact; private replay input and JSON output remain encrypted. The same workflow runs the comparison unit test when DUMPCSV is enabled.
- Code commits: source export a6f9fb32; comparison script/test 01f14b43, 62e42cac; workflow/artifact/test wiring 28008c52, b0986df4.
- Compile gate for the DUMPCSV code/workflow completed successfully: run 45712156657. One replay was then launched; see completion record below.
- No-token invariant reference (Step 13a APP usual, seed 1): BG <70 6.0%, BG <54 0.5%, BG >180 5.7%, CV 29.7%, hypo treatment 33.21 g/day, insulin 11.78 U/day. The DUMPCSV run must reproduce these exact summary values; any difference is a failure of the report-only invariant.
- Executed one-job RUN entry (same Step 13a APP(a)+CAL usual+seed 1 line, adding only DUMPCSV):
  SYNTH_56,SEED_1,CRT_22,NEEDDX_1.3,NOISEPHI_0.995,UKF,SMARTS,SPLIT_60,SPLITMIN_20,LEARNG3,NOIOB,SMBMIN_60,UAMMIN_60,LATECOB,LATEPCT_50,LATEB_2,LEARNB5,LBLOWMAX_6,LBFILL2,LBMIN_2,LEARNDURA,NOISE_0.07,CANCH_72,PCTCARD,PCTMAX_100,SBIAS_0.45,TREAT_5,CHG_28,CHGISF_1.2,MEALX_0.7,LACAUSE,CRAUDIT,DUMPCSV
- This side task does not alter RULES §5, the chain's next step, learner formulas, clinical settings, or app source.


### DUMPCSV comparison CI fix — 2026-11-25 — PASS

- Workflow commit: 56fd0eb436c28c135344008d482bb4b286148c8f. Added NumPy installation before the replay-job unit test and a standalone push-triggered `comparison-test` job so the synthetic tool can be tested without starting another replay.
- Standalone CI run [45719091659](https://github.com/Eretikstas/Apex/actions/runs/45719091659): SUCCESS. Python setup, NumPy installation, and `python3 -m unittest discover -s lab/aimi-replay/tools -p 'test_compare_days.py' -v` all passed. This includes the same-file-twice assertion that every feature verdict is SIMILAR.
- No replay was repeated; no RUN.txt change and no clinical or §5 chain action. Existing DUMPCSV replay artifact and exact Step 13a metrics remain as recorded below.


### DUMPCSV execution — 2026-11-25 — REPLAY OK; TOOL TEST BLOCKED

- RUN commit: b926c1f7ed226a58ecd3a55d287f598e1d0ab00d. Dispatcher: [45715276679](https://github.com/Eretikstas/Apex/actions/runs/45715276679). Replay: [45715301756](https://github.com/Eretikstas/Apex/actions/runs/45715301756), one plan + one replay job.
- Synthetic replay itself passed: 16,128 ticks, 0 failed. The exported CSV was extracted from the Gradle log and uploaded as `out/dump.csv`, 3,226 five-minute rows. Artifact `aimi-replay-result-0`, ID 15068719425: [download](https://github.com/Eretikstas/Apex/actions/runs/45715301756/artifacts/15068719425). Artifact contains `dump.csv` and encrypted `result.enc`; no real patient data.
- Report-only invariant: public synthetic summary exactly matches Step 13a seed 1: BG <70 6.0%, BG <54 0.5%, BG >180 5.7%, CV 29.7%, hypo treatment 33.21 g/day, insulin 11.78 U/day.
- Overall GitHub job is FAILURE only because the comparison unit-test step could not import NumPy: `ModuleNotFoundError: No module named 'numpy'`. The test did not execute; this is an environment/dependency setup defect, not a failing comparison assertion. Fix CI to install numpy before rerunning the unit test. Do not claim the comparison tool test passed.
- DUMPCSV did not alter replay metrics. This side task does not advance or modify RULES §5 chain.


### Progress checklist — chain §5 (updated 2026-11-25 17:28 CET)

- [x] Step 9b — calibration completed: 54/54 jobs; CAL accepted by Claude/user: `MEALX_0.7,CHGISF_1.2,SBIAS_0.45,TREAT_5`; ranking and distance recorded.
- [x] Step 10 batch definition — 36 jobs assembled to match the six §5 scenarios, three seeds, and two variants; launched through RUN.txt commit `e997ab29b152abf2a8ae7f0bbd2ae45d82866991`.
- [x] Step 10 execution/results — replay run `45653668453`: 1 plan + 36/36 replay jobs completed successfully; all 36 public summaries extracted from job logs. APP vs no-learner comparison and scenario means recorded below.
- [x] Step 11 — batch 1/2 completed: run `45659070340`, 108/108 replay jobs + plan succeeded. Numeric review found learner variants breaching the §4 low gate and none meeting the stricter §5 requirement of at least 10% relative reduction in <70 on the usual scenario. STOP under §4a; batch 2 (`Z2_126`) and Step 12 were not started. Claude review required.
- [ ] Step 12 — not started; depends on Step 11 pass-gate results.


### 2026-11-25, Step 10 — calibrated virtual-child baseline — COMPLETE

- CAL: `MEALX_0.7,CHGISF_1.2,SBIAS_0.45,TREAT_5`. RUN.txt start commit `e997ab29b152abf2a8ae7f0bbd2ae45d82866991`; replay run `45653668453`. GitHub API reports 37/37 jobs completed successfully (1 plan + 36 replay); all 36 result artifacts exist, and all 36 public summaries were extracted from their corresponding job logs. No encrypted artifact or key was used.
- Six §5 scenarios, seeds 1–3, matched APP and APP-without-learner-cards. Means across seeds (columns: `<70 / <54 / >180 / CV / hypo g/day / insulin U/day`):

| Scenario | APP | Without learner cards |
|---|---:|---:|
| usual | 4.93 / 0.30 / 4.80 / 28.77 / 28.99 / 11.44 | 8.17 / 0.77 / 2.13 / 28.47 / 43.87 / 12.11 |
| MEALX one step lower | 4.70 / 0.17 / 4.57 / 28.17 / 27.08 / 11.13 | 8.07 / 0.67 / 1.50 / 28.00 / 43.45 / 11.80 |
| CHGISF one step higher | 5.87 / 0.40 / 3.50 / 28.20 / 33.33 / 10.74 | 9.37 / 0.73 / 1.03 / 27.80 / 51.31 / 11.37 |
| CHGISF one step lower | 4.37 / 0.17 / 6.37 / 28.93 / 24.94 / 12.36 | 6.33 / 0.33 / 2.70 / 28.23 / 35.48 / 12.80 |
| DRIFT_2 | 4.37 / 0.17 / 7.10 / 29.37 / 25.36 / 12.73 | 6.10 / 0.33 / 3.43 / 28.57 / 34.59 / 13.12 |
| NOISE_0.12 | 11.97 / 2.97 / 14.33 / 39.80 / 55.95 / 12.58 | 15.27 / 3.80 / 8.47 / 38.07 / 70.77 / 13.26 |

- Matched safety comparison across all 18 scenario/seed pairs: APP had lower `<70` in 18/18, `<54` was never higher (17 lower, 1 equal), and hypo treatment was lower in 18/18. Thus the existing APP learner cards pass the §4 low/hypo comparison against their removal.
- Trade-off: APP had higher `>180` in every scenario mean and higher CV in most scenarios. The low-reduction benefit is accompanied by more hyperglycemia/variability; this is a synthetic replay comparison, not a clinical recommendation.
- Public summary code labels each job with the first three fixed preference tokens only; scenario mapping was reconstructed from the committed RUN.txt order (six scenarios × three seeds × APP/no-card pair). The numeric rows themselves were read from each job's emitted summary.
- Step 10 complete; proceed to Step 11 per §5.


### 2026-11-25, Step 11 — low-protection token screen — RUNNING

- Step 10 completed and passed the existing APP-vs-no-learner low/hypo comparison. Step 11 candidates were identified in `HistoryReplayTest.kt`: `GUARDCHAIN` (line 1004), `CHAIN_<pct>` (line 988; tested 25 and 75), `SMARTL` (line 1000), `LAPROTECT` (line 1027; requires `LEARNALL` and `LACAUSE`, lines 1457–1458), and `Z2_<mg/dL>` (line 558; default 117). The hypo low threshold / 90-minute SMB-off window is fixed in code (lines 556–557), with no separate token found for it.
- Batch 1/2 contains six token variants × six Step 10 scenarios × seeds 1–3 = 108 jobs. Each token is tested alone on top of APP variant (a); LAPROTECT includes only its mandatory prerequisites. Threshold choice for the first Z2 run: 108 mg/dL (below default 117); the second run will test 126 mg/dL (above default).
- RUN.txt start commit: `3d07adb53878e68f1a1aa4ad19a5d918f5b7e774`; dispatcher run `45659023419` succeeded; replay run `45659070340`. First check: plan success, 108 replay jobs queued/in progress. Apply §4 pass rule to every learner variant before starting batch 2 or Step 12.


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

### 2026-11-26, STOP — DUMPCSV gate failed; Step 13b not authorized

- Reconciled with Claude's newest Results entry: it records that replay run 45732148887 was started after the dispatcher busy-check fix and directs continuation only after recording the completed CSV checks.
- The completed replay record for run 45732148887 reports 16,128 replay ticks but 16,129 exported 5-minute rows (expected 16,128; +1). This is a failed required row-count check.
- The public summary matches Step 13a seed 1 exactly (6.0 / 0.5 / 5.7 / 29.7 / 33.21 / 11.78), but the log does not independently report measured step_min or CSV-integrated insulin/day. Five-minute spacing and the <=1% insulin reconciliation therefore remain unverified.
- The DUMPCSV gate has not passed. **STOP:** do not implement/run Step 13b, Step 13c, or Steps 14–18 until the export is corrected and the required checks are verified. No source, workflow, or RUN.txt changes made.

## Results (written by Claude)

### 2026-11-26, Claude: DUMPCSV check replay started; dispatcher busy check fixed (user approved)

- Why it did not start: the dispatcher refused ("another lab run is going") because push-triggered no-op runs of
  aimi-replay.yml counted as busy. Fixed by Claude with user approval: the busy check counts only workflow_dispatch
  runs. One lab replay at a time still holds. Your RUN.txt commits start runs as before.
- Claude re-ran the dispatcher for RUN commit 55655b49: replay run 45732148887 (APP + CAL usual, seed 1, DUMPCSV).
  When done: record rows, step_min, CSV insulin/day vs summary insulin/day here, then the 13b control (3 seeds), 13c.

### 2026-11-25 night, Claude: DUMPCSV + compare_days.py used on the real child (privately) — fix before next use

- Thanks, the tools work end to end. The real comparison stays private (no real numbers here). Qualitative result:
  the CAL model matches the averages (lows per day, % <70 overall) but by the wrong mechanics: meals too weak
  (MEALX_0.7), too little daily insulin, about twice the hypo treatment grams, glucose rises at night instead of
  staying flat, and the afternoon/evening is too calm (lower SD, missing evening lows). A learner trained on this
  would learn wrong causes. Re-calibration is needed (Claude will define the targets).
- DUMPCSV bugs: (1) rows are every 25 min, not 5 (3,226 rows for 56 days; time_min steps by 25); (2) the insulin sum is
  7.2 U/day while the run summary says 11.78 U/day. Both must be fixed: one row per 5-minute tick, insulin_u = basal +
  SMB + manual bolus actually delivered in that 5 min; check sum(insulin_u)/days == summary insulin within 1 %.
- compare_days.py: "hourly mean BG" and "hourly BG SD" use Wasserstein-1 over the 24 values, which ignores the hour
  order. Use mean absolute difference hour by hour (same threshold). Keep the W1 for real distributions.

### 2026-11-25, Claude: plan addition (user) — weight sweep again AFTER step 18

- The old weight sweep (run 45071041692, 22.11) is dropped: uncalibrated model, the run failed.
- After step 18 (not before): a weight sweep on APP (a) + CAL, usual scenario, seeds 1-3, by
  `AUTOISF_WEIGHTS_GUIDE.md` (ga-zelle 3.2 Quick Guide + BerNie FCL book). Rules from those sources:
  1. One weight at a time, small steps, from the value the model uses now (`runTickAutoIsf`: accel 0.0, brake 0.2,
     pp 0.01, lower 0.0, higher 0.0, dura 0.0, autoISF_min 0.7, autoISF_max 1.2). Stay in the author ranges.
  2. Values: brake 0.1 / 0.3; pp 0.005 / 0.02; dura 0.2 / 0.5 (author: careful above 1.5); higher range 0.2;
     lower range 0.2; accel 0.02 (author start) WITH brake = 30-40 % of accel (0.007), as the author pairs them.
     autoISF_max 1.3 alone (as the clamp limits every weight).
  3. Per row also report: how often each `_ISF` effect WON (only the winning effect acts, guide section 2), and how
     often the min/max clamp cut the factor. A weight that never wins cannot help, write that.
  4. Read results by meal phase (guide section 3): lows 2-4 h after meals point to pp / brake / accel too high.
  5. This is a model result, not advice for the real loop (authors: basal/CR/ISF first, good CGM days only).
  Same table as step 11 plus the two counts. Then STOP for Claude.

### 2026-11-25, Claude: numbers of steps 11, 11b, 13a checked against all 219 job logs — ALL MATCH

- Every reported mean (6 columns, usual scenario) equals the job summaries (checked by a script and, independently,
  by a free model). No job without a summary. Good work.
- Notes: `CHAIN_75` gives exactly the APP numbers in the usual scenario (probably the default value = no effect);
  `GUARDCHAIN` + `Z2_139` equals `Z2_139` alone in several scenarios (GUARDCHAIN may not act at that threshold).
  Say in the 13c report whether that is expected.
- New file `STRESS_SCENARIOS_DRAFT.md`: ideas for hard days and negative tests, with Claude corrections at the top
  (CHGISF direction: higher = insulin STRONGER, code line 1750). Not a task yet: do NOT run it before step 18.

### 2026-11-25, Claude: step 13b code review (commits 0f67afb6, 220d27a7, d0825e03) — fix before the 13b run

1. **BUG, spec rule 5 is off.** `wAttrOtherProposalTimes` (HistoryReplayTest.kt line 1042) is declared and checked
   (line 2104) but nothing ever adds to it, so `wAttrBlockedOther` is always 0. It must hold the times of the OTHER
   cards' proposals (basal card LEARNB5, PCTCARD, LAISF/LACR when on): add the tick time `t` there each time one of
   those cards makes a proposal. Do NOT add WATTR's own events to it (that is a different rule). If a card has no
   clear "proposal made" point, write which one in STATUS "Task" and leave it out; do not invent one.
2. **Report-only must be proven by numbers.** The counterfactual calls `runTickAutoIsf` twice more per tick
   (plus / minus weight, around lines 2072–2076). If that touches any shared state, WATTR changes the run. Check:
   APP (a) CAL usual, seeds 1–3, WITH `WATTR` + `CRAUDIT` -> the summary line must equal the 13a numbers for the same
   seed exactly (6.0/0.5/5.7/29.7/33.21/11.78, 4.0/0.1/4.2/27.7/24.11/10.92, 4.8/0.3/4.5/28.9/29.64/11.63).
   Any difference = "WATTR changes behaviour" -> STOP (this one IS a stop).
3. **Order:** write the pre-run note in STATUS "Task" BEFORE the RUN.txt commit (13a missed it). The note says:
   code commit, what the fix in item 1 does, the exact RUN lines.
4. Compile green on the exact commit before the run (as in 13a). Then the item-2 control (3 jobs), then 13c.

### 2026-11-25, Claude: step 11b reviewed — SMB protection threshold up does not help; go on with 13–18

- Z2 126/139/150 (alone or with GUARDCHAIN): <70 5.0–5.3 vs APP 4.93, <54 0.20–0.27 vs 0.30, hypo grams about the
  same. The differences are within seed noise; a higher threshold gives no clear gain on the calibrated model.
  Decision: no Z2 change from the lab.
- The stop was not needed: in a SCREENING step a candidate that fails is a result. RULES 4a item 1 now says so.
- Go on now: step 13, then 14, 15, 16, 17, 18. Report after each step, STOP only at the end of 18.

### 2026-11-25, Claude: steps 10 and 11 reviewed

- Step 10 is the main result: on the virtual child the app learner (APP) clearly lowers lows against no learner
  (usual: <70 8.17 -> 4.93, <54 0.77 -> 0.30, hypo 43.9 -> 29.0 g/day; 18/18 pairs), price: >180 +2.7 points.
  The learner stays in the app as it is.
- Step 11 (batch 1): no token lowers <70 by 10 %. GUARDCHAIN / CHAIN_25 help a little (-3..-5 %), the rest not.
  Not passing the 10 % gate means "not chosen", not a safety stop. Z2_108 lowers the protection threshold, so more
  lows is expected. The useful direction is the other one: a HIGHER SMB protection threshold (`Z2_<mg/dL>`).
- Next (RULES section 5, step 11b): Z2_126, Z2_139, Z2_150 alone on APP, plus GUARDCHAIN together with the best Z2.

### 2026-11-25, Claude: Task 9b accepted — CAL chosen, go on with steps 10–12

- Table checked. CAL = `MEALX_0.7,CHGISF_1.2,SBIAS_0.45,TREAT_5` (distance 5.3). Lows and CV are near the target,
  highs a little above, daily insulin clearly below it (11.4 U/day). MEALX below 1 adds few lows; the insulin gap
  stays: the model child needs less insulin than the real one. Good enough to compare learner variants on lows;
  a second calibration of insulin need can come later. Do not wait for it.
- ChatGPT: the chain in RULES section 5 does not stop after 9b. Go on now with step 10, then 11, then 12, then STOP.

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


### 2026-11-25, Task 9b calibration — RUNNING

- Claude's Task 9b instruction at rules commit dc505fc6 is authoritative. Section 4a: calibration has no pass rule; no learner pass/fail gate is applied.
- Started one 54-job synthetic batch by replacing the single RUN.txt line. Start commit: dee5a20fefdcba51de1d63f451f497813a2dcc88.
- Dispatcher run: 45647794121 (queued at record time). Replay run: 45647793789 (queued at record time).
- Grid: APP base prefs from app22_batch.json, seeds 1–3; MEALX 0.7/0.8/0.9 × CHGISF 1.0/1.1/1.2 × SBIAS 0.30/0.45; fixed TREAT_5 and LACAUSE. CHG_28 included to activate CHGISF. 3 × 3 × 2 × 3 = 54 jobs. Every job uses SYNTH_56; workflow fixes algo=autoisf, sim=true, learn=false.
- Private target T is not written here. After all numeric summaries: calculate means over 3 seeds, distance to T across <70, <54, >180, CV and insulin U/day; rank top 3; then STOP. No code/workflow changes.

- Dispatcher run 45647794121 failed before starting replay: repository-enforced guard reported "3 starts in 24 h already". No replay jobs ran; replay push run 45647793789 was skipped. This is a hard repository limit and was not bypassed.
- The three successful RUN.txt dispatcher starts in the rolling window were 45622075003 (2026-11-25 13:17:31Z), 45629598681 (14:01:41Z), and 45640354963 (15:00:54Z). Earliest possible retry after the oldest expires: 2026-11-26 13:17:31Z (14:17:31 Europe/Paris), assuming no additional start and no active run.


### 2026-11-25, Task 9b calibration — COMPLETED, STOP

- Restarted the unchanged 54-job batch by changing only the RUN.txt comment line. Start commit: 4464542b5218fa4daa8d31e457b0c35a12cde030. Dispatcher run 45648911216 succeeded; replay run 45648946435 completed successfully: 1 plan + 54 replay jobs, all 54 replay jobs concluded success.
- Read each job's public synthetic summary row from its workflow log. Means below are over seeds 1–3. Distance is the sum of positive excursions outside the user's private target bands for BG <70, BG <54, BG >180, CV and daily insulin; the private bands are intentionally not copied into this repository.
- No pass/fail gate applies to this calibration.

| MEALX | CHGISF | SBIAS | BG <70 % | BG <54 % | BG >180 % | CV % | Hypo g/day | Insulin U/day | Distance |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 0.7 | 1.0 | 0.30 | 3.53 | 0.03 | 8.97 | 29.60 | 20.54 | 13.42 | 9.51 |
| 0.7 | 1.0 | 0.45 | 3.87 | 0.13 | 8.97 | 29.97 | 22.50 | 13.47 | 9.50 |
| 0.7 | 1.1 | 0.30 | 3.70 | 0.10 | 6.43 | 29.00 | 22.32 | 12.25 | 7.38 |
| 0.7 | 1.1 | 0.45 | 4.37 | 0.17 | 6.37 | 28.93 | 24.94 | 12.36 | 6.54 |
| 0.7 | 1.2 | 0.30 | 4.40 | 0.10 | 4.93 | 28.47 | 25.24 | 11.32 | 6.12 |
| 0.7 | 1.2 | 0.45 | 4.93 | 0.30 | 4.80 | 28.77 | 28.99 | 11.44 | 5.32 |
| 0.8 | 1.0 | 0.30 | 3.57 | 0.13 | 9.73 | 30.27 | 20.30 | 13.71 | 10.62 |
| 0.8 | 1.0 | 0.45 | 3.87 | 0.10 | 9.73 | 30.33 | 21.85 | 13.79 | 10.31 |
| 0.8 | 1.1 | 0.30 | 3.97 | 0.10 | 7.23 | 29.63 | 23.45 | 12.59 | 8.21 |
| 0.8 | 1.1 | 0.45 | 4.30 | 0.23 | 7.07 | 29.40 | 25.77 | 12.65 | 7.42 |
| 0.8 | 1.2 | 0.30 | 4.63 | 0.17 | 5.60 | 29.20 | 26.55 | 11.66 | 6.41 |
| 0.8 | 1.2 | 0.45 | 5.07 | 0.23 | 5.53 | 29.00 | 28.33 | 11.69 | 5.67 |
| 0.9 | 1.0 | 0.30 | 3.73 | 0.13 | 10.27 | 30.53 | 21.07 | 14.06 | 10.91 |
| 0.9 | 1.0 | 0.45 | 3.87 | 0.10 | 10.57 | 30.60 | 22.02 | 14.09 | 11.11 |
| 0.9 | 1.1 | 0.30 | 3.97 | 0.10 | 8.40 | 30.10 | 22.86 | 12.88 | 9.55 |
| 0.9 | 1.1 | 0.45 | 4.33 | 0.20 | 8.53 | 29.97 | 24.70 | 12.94 | 9.13 |
| 0.9 | 1.2 | 0.30 | 4.73 | 0.23 | 6.23 | 29.67 | 26.91 | 11.96 | 7.11 |
| 0.9 | 1.2 | 0.45 | 4.87 | 0.27 | 6.30 | 29.43 | 28.09 | 12.01 | 6.75 |

Top 3 by minimum distance:
1. MEALX_0.7 / CHGISF_1.2 / SBIAS_0.45 — distance 5.32
2. MEALX_0.8 / CHGISF_1.2 / SBIAS_0.45 — distance 5.67
3. MEALX_0.7 / CHGISF_1.2 / SBIAS_0.30 — distance 6.12

- All three-seed groups have n=3; no missing summaries. This is a synthetic calibration ranking, not a clinical dosing recommendation or proof of real-world safety.
- Task 9b report complete. STOP. No additional runs or code/workflow changes.


### 2026-11-25, Step 11 — first batch COMPLETE; STOP under §4a

- RUN.txt start commit: `3d07adb53878e68f1a1aa4ad19a5d918f5b7e774`; dispatcher run `45659023419` succeeded; replay run [`45659070340`](https://github.com/Eretikstas/Apex/actions/runs/45659070340) concluded `success`. GitHub API confirms 109/109 jobs completed successfully: 1 plan + 108 replay jobs. All 108 public synthetic summaries were read from job logs.
- Mapping verified from the committed RUN.txt order: six candidates × six scenarios × three seeds. Candidates were tested individually on APP variant (a): GUARDCHAIN, CHAIN_25, CHAIN_75, SMARTL, LAPROTECT (with mandatory LEARNALL), Z2_108.
- Decisive usual-scenario means over seeds 1–3 (columns: `<70 / <54 / >180 / CV / hypo g/day / insulin U/day`); APP reference from Step 10 is `4.93 / 0.30 / 4.80 / 28.77 / 28.99 / 11.44`. Step 11 requires <70 to fall by at least 10% relative, i.e. to `≤4.44%`, while <54 must not rise and >180 may rise by no more than 2 points.

| Candidate | <70 | <54 | >180 | CV | Hypo g/day | Insulin U/day | <70 gate (≤4.44) |
|---|---:|---:|---:|---:|---:|---:|---|
| GUARDCHAIN | 4.77 | 0.17 | 4.83 | 28.53 | 28.16 | 11.43 | FAIL |
| CHAIN_25 | 4.70 | 0.20 | 5.10 | 28.53 | 27.98 | 11.40 | FAIL |
| CHAIN_75 | 4.93 | 0.30 | 4.80 | 28.77 | 28.99 | 11.44 | FAIL |
| SMARTL | 4.97 | 0.30 | 5.07 | 28.53 | 28.04 | 11.42 | FAIL |
| LAPROTECT | 5.27 | 0.17 | 5.00 | 28.83 | 30.12 | 11.53 | FAIL |
| Z2_108 | 5.07 | 0.27 | 4.93 | 28.53 | 27.68 | 11.41 | FAIL |

- None of the six candidates achieves the required ≥10% relative reduction in <70 on the usual scenario. In addition, SMARTL, LAPROTECT and Z2_108 increase <70 versus the APP reference (4.97, 5.27 and 5.07 vs 4.93); LAPROTECT also increases hypo treatment (30.12 vs 28.99 g/day). These are learner-variant §4 safety-gate breaches.
- **STOP per RULES_FOR_CHATGPT.md §4a.** Do not launch batch 2 (`Z2_126`) or Step 12. Do not select or combine candidates. Claude review is required before any continuation. The 108 successful jobs prove execution only; the candidate results do not pass the low-reduction/safety criteria.
- Scope: synthetic replay only (`algo=autoisf`, `sim=true`); no clinical dosing or real-world safety conclusion.


### 2026-11-25, Step 11b — both runs complete; STOP under §4a

- Step 11b run 1 (three Z2 thresholds alone): start commit `4346695dc6db673bc12f33017162a1bca5f722ae`; dispatcher `45679265898`; replay [`45679294097`](https://github.com/Eretikstas/Apex/actions/runs/45679294097). All 55 jobs succeeded (1 plan + 54 replay); all 54 public synthetic summaries were read.
- Step 11b run 2 (GUARDCHAIN + each Z2 threshold): start commit `ab7a58f5bf19ac8cb3b4c8f5ae27bf5b1e48df48`; dispatcher `45682900288`; replay [`45682956033`](https://github.com/Eretikstas/Apex/actions/runs/45682956033). All 55 jobs succeeded (1 plan + 54 replay); all 54 public synthetic summaries were read.
- Grid in each run: 3 thresholds × 6 scenarios × 3 seeds. Means below are the decisive usual-scenario results across seeds 1–3. Columns: BG <70 / BG <54 / BG >180 / CV / hypo g/day / insulin U/day. APP usual reference from Step 10: 4.93 / 0.30 / 4.80 / 28.77 / 28.99 / 11.44. The 10% reduction threshold is ≤4.44; per the user's instruction, failure of that threshold alone is not a stop.

| Run | Candidate | <70 | <54 | >180 | CV | Hypo g/day | Insulin U/day | 10% gate | §4 safety gate |
|---|---|---:|---:|---:|---:|---:|---:|---|---|
| 1: alone | Z2_126 | 5.17 | 0.27 | 4.93 | 28.80 | 28.75 | 11.47 | FAIL | FAIL: <70 > APP |
| 1: alone | Z2_139 | 5.20 | 0.27 | 5.17 | 28.90 | 29.64 | 11.50 | FAIL | FAIL: <70 and hypo > APP |
| 1: alone | Z2_150 | 5.23 | 0.20 | 5.20 | 29.07 | 29.47 | 11.49 | FAIL | FAIL: <70 and hypo > APP |
| 2: + GUARDCHAIN | Z2_126 | 5.00 | 0.27 | 5.03 | 28.93 | 28.04 | 11.45 | FAIL | FAIL: <70 > APP |
| 2: + GUARDCHAIN | Z2_139 | 5.20 | 0.27 | 5.17 | 28.90 | 29.64 | 11.50 | FAIL | FAIL: <70 and hypo > APP |
| 2: + GUARDCHAIN | Z2_150 | 5.30 | 0.20 | 5.07 | 28.87 | 29.53 | 11.47 | FAIL | FAIL: <70 and hypo > APP |

- **§4a stop condition is met independently of the 10% gate:** every Z2 candidate, alone and with GUARDCHAIN, has usual-scenario BG <70 above the APP reference (4.93%). Z2_139 and Z2_150 also increase hypo-treatment grams. <54 does not increase. Thus these learner-variant candidates break the §4 safety pass rule (<70 and hypo grams must not exceed APP).
- Result: no Z2 threshold selected; GUARDCHAIN combination does not repair the <70 safety-gate breach. Do not proceed to Step 13 or Steps 14–18 until Claude reviews this §4a stop. No learner code or clinical settings changed in Step 11b.
- Scope: synthetic replay only (`algo=autoisf`, `sim=true`); no clinical dosing or real-world safety conclusion.


### 2026-11-25, Step 13a — CRAUDIT counting-only — COMPLETE

- Code commit: `cbe6f1b6092005781dd41d68f74cc1d6e0d85ad4` (HistoryReplayTest.kt only). RUN commit: `b7eb88001db7fe6b188d1e042558b64bd1581d4d`. Dispatcher: `45690171315`. Replay: [`45690200848`](https://github.com/Eretikstas/Apex/actions/runs/45690200848), 1 plan + 3/3 replay jobs successful.
- CAL usual, APP variant, seeds 1–3. Public summaries:

| Seed | BG <70 % | BG <54 % | BG >180 % | CV % | Hypo g/day | Insulin U/day | ISF candidates / accepted | CR candidates / accepted |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 1 | 6.0 | 0.5 | 5.7 | 29.7 | 33.21 | 11.78 | 25 / 0 | 14 / 0 |
| 2 | 4.0 | 0.1 | 4.2 | 27.7 | 24.11 | 10.92 | 37 / 0 | 15 / 0 |
| 3 | 4.8 | 0.3 | 4.5 | 28.9 | 29.64 | 11.63 | 53 / 0 | 14 / 0 |
| Mean | 4.93 | 0.30 | 4.80 | 28.77 | 28.99 | 11.44 | 115 / 0 | 43 / 0 |

- ISF rejection reasons (115/115): meal 62, low in response window 40, distinct SMB within 6 h 13. CR rejection reasons (43/43): prior insulin 26, prior hypo/rebound 17. All three CR blocks and the ISF audit had zero accepted observations; gates FAIL for insufficient uncontaminated evidence. This is a diagnostic result, not a stop.
- The mean replay metrics exactly match the Step 10 APP usual reference; CRAUDIT made no CR/ISF parameter writes. No dosing or learner behavior changed in this substep.
- The source compile step and preceding helper/unit tests passed; full compile workflow [`45689360655`](https://github.com/Eretikstas/Apex/actions/runs/45689360655) was still assembling the laboratory APK when this note was written.
- Next: Step 13b WATTR implementation and no-token APP control; then Step 13c planted positive/negative tests.


**Step 13a audit/procedure addendum (25.11):**

- Per-seed top rejection reasons required by RULES §5: ISF seed 1 = meal 10/25; seed 2 = meal 24/37; seed 3 = meal 28/53. All have <5 accepted: **Audit too strict for ISF; top reason = meal (62 of 115 overall)**. CR seed 1 = prior-insulin 9/14; seed 2 = prior-insulin 8/15; seed 3 = prior-insulin 9/14. All have <5 accepted: **Audit too strict for CR; top reason = prior-insulin (26 of 43 overall)**. Do not loosen the audit.
- Exact RUN construction verified against the first job of Step 11b run 2: removing `GUARDCHAIN` and `Z2_126`, then adding `CRAUDIT`, gives the three run entries (with seed changed only to 2 and 3). No other scenario token changed.
- Same-seed Step 10 APP usual summaries were independently fetched from run `45653668453`: seed 1 `6.0 / 0.5 / 5.7 / 29.7 / 33.21 / 11.78`; seed 2 `4.0 / 0.1 / 4.2 / 27.7 / 24.11 / 10.92`; seed 3 `4.8 / 0.3 / 4.5 / 28.9 / 29.64 / 11.63`. Exact match to 13a for every seed: **CRAUDIT changes behaviour = NO**.
- Required pre-run STATUS note was omitted and is recorded here after the run (procedural ordering deviation). The exact changes in commit `cbe6f1b6` are: ISF window `k-48..k+36` → `k-72..k+72`; reject any other SMB within 6 h as `smb-within-6h`; CR post-meal SMB/correction exclusion 2 h → 6 h. `isfSeen` / `crBlockVerdict` also drive LAISF/LACR under LEARNALL; prior LEARNALL/LAISF/LACR results are not comparable to results after this commit. APP variant (a) used in 13a does not use those cards.
- Compile workflow `45689360655` had source compilation, weight-boundary tests, audit-helper tests, adaptation tests and APSResult tests green before replay, but full workflow/APK assembly had not yet completed when the RUN commit was made. Replay run `45690200848` itself succeeded 3/3. Wait for full compile workflow green before the next code commit.


### 2026-11-26, AIMI_DUMPCSV_CHECK — FAIL; STOP under user instruction

- Replay run [45732148887](https://github.com/Eretikstas/Apex/actions/runs/45732148887), head `f7a648ead93d4cc57e310da9bb9dceab10cdb876`, concluded `success`; replay job `139616557027` and public-summary step succeeded.
- RUN used APP(a) + CAL usual, seed 1, tokens ending `LACAUSE,CRAUDIT,DUMPCSV`. Replay log reports `16128 ticks`, but the DUMPCSV export step reports: `DUMPCSV artifact ready: out/dump.csv (16129 5-minute rows)`. Expected = 16,128 rows. **FAIL: exported row count is 1,6129, one row too many (+1).**
- Public synthetic summary: BG <70 / <54 / >180 / CV / hypo g/day / insulin U/day = `6.0 / 0.5 / 5.7 / 29.7 / 33.21 / 11.78`; this exactly matches Step 13a seed 1.
- The public log labels the export as 5-minute rows, but does not print a separately measured `step_min` or CSV-integrated insulin/day. Those two CSV-specific checks therefore remain **unverified** from the available public output; do not infer them from the summary.
- Because the required row-count check failed, the DUMPCSV gate is **FAIL**. Do not launch 13b, 13c, or Steps 14–18. STOP pending correction/review. No workflow files changed.


### 2026-11-26, Step 13b WATTR control — retry submitted

- Claude's check: commit `fbf44207` / replay `45839851602` (SPLITMIN_60) is **INVALID**; ignore its metrics.
- Corrected batch commit `c0c6fb72` did not run: dispatcher `45840014311` refused it because another lab run was still active. Claude verified its batch line is exactly the intended 13b APP(a) + CAL usual + WATTR + SPLITMIN_20 batch, seeds 1–3.
- Re-submitted by changing only the RUN.txt comment; the batch line was copied byte-for-byte from `c0c6fb72` (885 bytes): retry commit `5f34b201565939236a93c252d3b5c53a08be034d`.
- Await Claude's run-results report. Do not start 13c before those results are reviewed.


### 2026-11-26, Step 15 — LISF2 report-only — COMPLETE

- RUN commit: `4106bc48`; dispatch confirmed compile green for code `278cbe20`; replay `45894508242` completed 6/6 jobs successfully.
- The generated `run-45894508242.md` is malformed (all jobs rendered as `?` with empty metrics/tokens). Metrics and audit lines below were reconstructed directly from each of the six public job logs; do not treat the generated markdown table as valid.
- Columns: BG <70 / <54 / >180 / CV / hypo g/day / insulin U/day.

| Case | Metrics | LISF2 result |
|---|---|---|
| CTRL13A, CAL usual (CHGISF_1.2) | 6.0 / 0.5 / 5.7 / 29.7 / 33.21 / 11.78 | exact match to Step 13a seed-1 reference; numeric PASS |
| CHGISF_0.8 | 3.3 / 0.0 / 17.4 / 31.9 / 18.75 / 16.96 | all blocks silent |
| CHGISF_1.35 | 7.6 / 0.8 / 3.9 / 29.8 / 41.07 / 10.78 | all blocks silent |
| NOISE_0.12 | 12.9 / 3.0 / 15.5 / 41.1 / 59.29 / 13.04 | all blocks silent |
| CANCH_96 | 5.1 / 0.1 / 8.1 / 30.4 / 28.57 / 12.18 | all blocks silent |
| Duplicate CAL usual (no extra perturbation) | 6.0 / 0.5 / 5.7 / 29.7 / 33.21 / 11.78 | all blocks silent |

- LISF2 in every run: morning/day/evening/night each had **0 accepted observations**, 0 eligible days, profile ISF 162.0, proposal=silent, proposed=162.0, step=10%, gate=FAIL. Dominant rejection was meal-hypo; additional AutoISF-not-1, overlapping-manual, or cannula rejections occurred in some cases. Thus the planted CHGISF direction tests did **not** demonstrate direction: the card had no accepted evidence and remained silent. This is a screening result, not a stop.
- Negative-test coverage caveat: the intended MEALX_0.7 case was not independent because CAL already contains MEALX_0.7; the corresponding batch slot was a duplicate CAL job. NOISE_0.12 and CANCH_96 were tested and remained silent.
- No-token control numeric match: **PASS**. LISF2 is report-only; no delivery/profile writes observed in the audit.
- Report-generator defect remains: public run markdown has empty fields despite populated job summaries/logs. Reconstructing from raw logs is the evidence source for this step.
- Next: Step 16, implement token-gated report-only LCR3 and its CRT up/down plus MEALX_0.7/TREAT_15 planted tests. Continue the chain; screening failures alone do not stop.

## Claude results

### 2026-11-28 — Claude v8: two ideas from AIMI (test only)
- New test-only tokens (commit `626ffbe9`): `HEXP` = AIMI's hypo exposure guard as a learner idea (14 days under 3.9 >4 % / under
  3.0 >1 % -> SMB and the temp basal part above profile x0.92; >6 % / >1.5 % -> x0.85); `NIGHTSMB_<pct>` = 22-06 h with no
  meal >= 13 g in 4 h, SMB x pct/100 (from AIMI's "Red Carpet only on a declared meal" fix, 26.11).
- RUN v8: job 0 unchanged CAL + CTRL13A (with LACAUSE, CRAUDIT, LSTAGE0, LISF2 - as every job needs); then BEST, BEST+HEXP,
  BEST+NIGHTSMB_50, BEST+NIGHTSMB_0 for seeds 1-3. Nothing goes into the app from this without the user and the bench.
- Note for ChatGPT on the 28.11 control FAIL: the control row of RUN 720089ec lacks LACAUSE, CRAUDIT, LSTAGE0, LISF2 (and
  carries SHAPEAUDIT). CTRL13A numbers are for a row WITH those four tokens (same slip as Claude's v1 on 27.11).
- **v8 result** (replay 46144921012, 13/13 success, control PASS; BEST reproduced exactly). Means of seeds 1-3
  (<70 / <54 / >180 / CV / hypo g/day / insulin U/day): BEST 4.67 / 0.17 / 4.77 / 28.2 / 26.7 / 11.36;
  +HEXP 5.17 / 0.27 / 6.63 / 30.0 / 28.2 / 11.45; +NIGHTSMB_50 4.80 / 0.20 / 5.80 / 29.4 / 27.1 / 11.44;
  +NIGHTSMB_0 4.97 / 0.23 / 5.57 / 29.7 / 28.1 / 11.46. **Both ideas are worse on every number** (more lows AND more
  highs): less SMB now -> higher glucose -> more insulin later -> bigger swing. Not for the app.
