# APP23 laboratory handoff — 2026-11-23

## Current state
- Repository: `Eretikstas/Apex`
- Branch: `aimi-replay`
- Prepared launcher: `.github/workflows/run-app23.yml`
- Launcher commit previously created: `d413d8fd42b8665911e87667c172f857f8fb043c`
- Launcher file was fetched and verified in this session.
- It defines `workflow_dispatch`, calls `.github/workflows/aimi-replay.yml`, and embeds the prepared 24-row APP23 batch. No user-entered batch is required.

## Execution status
- APP23 has **not been confirmed as started**.
- The prior UI observation was that GitHub marked this workflow disabled. A workflow file existing in the branch does not mean it is enabled.
- The connected GitHub toolset exposes repository reads/writes and workflow-run inspection/retry, but no workflow enable endpoint and no `workflow_dispatch` action. The fetch action is GET-only. Therefore I cannot re-enable or dispatch this workflow through the current connection.
- I will not change the launcher to a push-triggered workflow as a workaround: that does not reliably re-enable a manually disabled workflow and risks unintended execution.

## Safety / scope
- All work remains on `aimi-replay`.
- No changes to `autoisf-atc3`.
- No app build or installation on the child's phone.
- No changes to live pump/profile settings.
- Do not interpret the skipped push-triggered replay run as a successful APP23 run.

## Next technical gate
A repository owner must re-enable the disabled workflow once in GitHub Actions. After that, the prepared launcher can be manually dispatched. Then inspect the actual `workflow_dispatch` run, all jobs/logs/artifacts, and report the 24 scenarios across their seeds. Until a completed run and its artifacts are observed, there are no APP23 performance conclusions.


## Update at 2026-11-23 12:37 CET — push-trigger fallback
To avoid requiring the repository owner to re-enable the manually disabled `run-app23.yml`, a separate one-time push-triggered reusable-workflow caller was added:
- `.github/workflows/app23-auto.yml`
- Trigger: push to `aimi-replay`, restricted to changes of this exact workflow file.
- It calls the existing `aimi-replay.yml` reusable workflow with `learn=true`, `pretrained=false`, `sim=false`, `algo=aimi`, and the prepared batch.
- Latest corrected commit: `6237bffa30331ffe45d08644e976d7b9f2567970`.

### Batch validation correction
An initial manually reconstructed version accidentally contained only 21 rows; one intermediate correction also remained at 21 rows. These were superseded by the latest commit, which embeds the canonical `lab/aimi-replay/app23_batch.txt` verbatim.
Latest batch validation:
- exactly 24 rows;
- all required flags present in every row: `LEARNALL, LAISF, LACR, LACAUSE, LAPCTBACK, LADAYS, LAPROTECT, LEARNB5`;
- expected 8 scenarios × 3 seeds.
Earlier push-triggered runs, if GitHub accepted them, may have used incomplete 21-row versions. Do not use those results. Only evaluate a run tied to commit `6237bffa30331ffe45d08644e976d7b9f2567970` or later.

### Run visibility limitation
The connected GitHub workflow-run listing wrapper filters to pull-request-triggered runs, so it returned no runs for this push-triggered commit. A GET-only fetch is also not exposing the Actions run collection in this connector. Thus execution has been configured to trigger on push, but a matching run ID/status and its artifacts are not yet independently confirmed from this connection. Do not claim completion until the run tied to the latest commit is verified in GitHub Actions.


## Update at 2026-11-23 20:43 CET — reusable workflow secret fix and APP23 started
This update supersedes the earlier statement that push-trigger execution was unconfirmed/unavailable.

### Root cause of previous startup failures
- Runs tied to commits `4522085...`, `e84daca...`, and `6237bff...` ended with `startup_failure` and zero jobs.
- The caller used `secrets: inherit` while the repository is owned by an individual account. The called reusable workflow also did not declare `AIMI_REPLAY_KEY` under `on.workflow_call.secrets`.
- Fixed on `aimi-replay` in two commits:
  - `142385ae79bfc81b0b4aa932da2923510a3d3c81`: declare `AIMI_REPLAY_KEY: required: true` under reusable workflow_call secrets.
  - `2325a131e0eed62e65742ed702808966a9bd09f7`: pass `AIMI_REPLAY_KEY: ${{ secrets.AIMI_REPLAY_KEY }}` explicitly from app23-auto caller.

### Confirmed execution
- APP23 run: `45251769895`
- Commit: `2325a131e0eed62e65742ed702808966a9bd09f7`
- Event: push; workflow: `.github/workflows/app23-auto.yml`
- Plan job completed successfully and generated the matrix.
- Exactly 24 replay matrix jobs were created (indices 0–23), each currently queued at the last inspection.
- This is a real run; it is no longer a startup failure. No replay result is available yet. Do not report any clinical metrics until all jobs finish and summaries/artifacts are inspected.
- Separate compile workflow run `45251757109` was also in progress at last inspection.

### Safety scope unchanged
- Only `aimi-replay` was modified.
- No changes to `autoisf-atc3`, no app installation, and no live pump/profile changes.


## Update at 2026-11-23 22:xx CET — APP23 execution completed; audit findings

### Execution
- APP23 run `45251769895`, commit `2325a131e0eed62e65742ed702808966a9bd09f7`: completed `success`.
- Matrix: plan job + all 24 replay jobs completed successfully (25/25 jobs).
- Separate compile run `45251757109`: completed `success`.
- 24 result artifacts were uploaded, one per replay row. Artifact payloads are encrypted (`out/result.enc)); the GitHub connector exposes metadata but not the binary download/decryption path. Therefore do not claim the clinical summary metrics (TIR/CV/<3.9/<3.0/>10/hypo grams) have been aggregated from artifacts.

### Findings visible in replay logs
- Repeated across inspected jobs: ISF candidate acceptance is 0; gate fails with candidates rejected as meal / hypo-rebound / prior-insulin. Example replay job index 0: 123 candidates, 0 accepted (59 meal, 49 hypo-rebound, 15 prior-insulin).
- CR: all inspected time blocks have 0 accepted candidates; rejection reasons are prior insulin and prior hypo rebound. All CR block values remain 1.00/1.00/1.00.
- Basal: 0/3960 candidates accepted in inspected jobs; all 24 hourly buckets fail (0 eligible hours); dominant exclusions are hypo-rebound and meal-tail, plus causal-filter. This means basal learning is effectively disabled by the current filter combination on these datasets.
- ISF displayed value remains 162 in most inspected jobs; a few move to 180, but candidate acceptance logs still show 0, so the apparent change needs trace-level audit before attributing it to ISF learning.
- Percentage card does execute both directions in logs. Examples: index 0 shows pct- 2 / pct+ 2, final 100%; index 7 shows pct- 5 / pct+ 4, final 90%; index 11 shows pct- 6 / pct+ 5, final 90%. This suggests repeated down/up oscillation and warrants a dedicated transition audit; do not treat counts as successful stable adaptation.
- LAPROTECT audit generally reports gate OPEN in inspected examples, with protected lows lower than all-cause lows because post-hypo 180m rows are excluded from protected lows. Validate this semantic separation against expected policy; do not weaken safety interlocks based on these logs alone.

### Current conclusion / next work
- Infrastructure and compilation are green.
- Algorithmic acceptance is not green: ISF, CR, and especially basal are over-filtered (zero accepted in inspected replay jobs).
- Do not integrate or deploy any learner changes. No changes to `autoisf-atc3`, production app, phone, pump, or live profile.
- Next laboratory task: instrument the exact rejection pipeline and inspect why every basal candidate is classified as meal-tail / hypo-rebound; separately quantify ISF/CR rejection windows and validate whether the 4h/3h prior-insulin guards are unintentionally overlapping nearly all candidates. Then rerun the same 24-row batch and compare against this run.
- Obtain clinical aggregate metrics only through a permitted artifact-decryption path; never print or expose `AIMI_REPLAY_KEY`.


## Update at 2026-11-23 22:xx CET — basal evidence-window correction and rerun

### Root cause identified
The APP23 audit's zero basal acceptance was traced to a contradictory filter in the replay harness:
- Each basal candidate was checked against a 73-sample window spanning 5 h before through 1 h after the measured hour.
- `cleanBasalRow` rejected any `afterMeal` flag in that whole window, even though `afterMeal` remains true for 5 h and the basal estimator separately enforces its 5 h meal exclusion / explicit `LBTAIL` exception.
- This made the LBTAIL path logically unreachable and extended meal contamination rejection beyond the intended candidate interval. The same wide window also rejected candidates for any prior hypo/insulin/activity flag found in the 5 h history, rather than checking the hour whose glucose change is being attributed.

### Laboratory-only correction
On `aimi-replay`, in `HistoryReplayTest.kt`:
- Added `isBasalEvidenceRow`: checks post-hypo, post-insulin, old-cannula, sensor-gap, activity, illness and false-low flags; deliberately does not reject `afterMeal`.
- Both advanced and classic basal audits now evaluate causal/sensor-gap flags only across the 13 samples of the measured hour (hour start through +60 min).
- Meal handling remains separate and conservative: the existing 5 h `since[k]` gate remains, and the 3–5 h `LBTAIL` exception still requires COB <0.5 and an allowed CR block.
- Added unit assertions proving afterMeal alone is not a basal-evidence contaminant while hypo, insulin, activity, illness, false-low and sensor-gap still are.
- Audit reason classification was aligned with the new evidence window.
- Commits: `7f7411b2c6633a9ccb3cad213f6f1fb71cefd826`, `f3461ec90c6b175e1d7aa5d80dc7af9a7d552169`.

### Rerun
- Triggered the canonical APP23 24-row batch again by updating only the scoped launcher comment: commit `4ddb436843b355f7675015723df3a35d9e8ca5dc`.
- Compile workflow is also configured to run automatically on the test-source change.
- Pending verification: confirm compile job and all 24 replay jobs, then compare basal acceptance/hour coverage and all clinical aggregate metrics with run `45251769895`.
- This is a replay-harness/laboratory change only. No production branch, app installation, phone, pump, or live profile was changed.

### Interpretation guard
The narrower evidence interval is a hypothesis about the replay harness's over-filtering, not proof that more basal samples are clinically valid. The 5 h meal rule, LBTAIL COB/CR checks, hypo and insulin exclusions, cannula/sensor/activity/illness guards remain in force. Do not integrate until the rerun demonstrates acceptable data quality and independent safety/clinical review.


### CI run identifiers and live status
- Replay rerun: `45255929153` (commit `4ddb436843b355f7675015723df3a35d9e8ca5dc`): plan succeeded; 24 replay jobs created; at last poll 18 in progress, 6 queued, no failed jobs.
- Compile checks: `45255870246` (first filter patch) and `45255915278` (final filter + audit-reason patch) were both observed in progress at last poll.
- Early log download attempts for the new replay jobs returned BlobNotFound while jobs were still running; no result is inferred from that.
- Next gate: wait for the current jobs to complete, then inspect each job's basal audit line and verify that accepted candidates and eligible hours are nonzero in at least some eligible days, without relaxing the meal/COB or hypo/insulin safeguards.


## Update at 2026-11-23 22:54 CET — APP23 corrected rerun completed

### Execution verification
- Run: `45261150404`
- Run URL: https://github.com/Eretikstas/Apex/actions/runs/45261150404
- Tested commit: `de50ec7a31ff446fa8930b4898bbfedfab168936`
- Plan job: success.
- Replay matrix: all 24/24 jobs completed with conclusion `success`.
- Each replay log reports 16,128 or 24,192 ticks, 0 failed ticks, and 0 sensor-gap ticks intentionally skipped.
- 24 encrypted result artifacts uploaded (one per batch row); artifact names `aimi-replay-result-0` through `aimi-replay-result-23`. Artifacts are present and not expired at inspection.
- Compile check for the preceding ISF audit fix: run `45259513694`, conclusion `success`.

### Findings from the completed logs
- **ISF:** every inspected replay job reports 0 accepted candidates and gate FAIL. Rejections are dominated by hypo-rebound and meal windows, with prior-insulin also present. In some jobs the displayed ISF value moves 162 → 180 despite 0 accepted ISF candidates; do not attribute this change to successful ISF learning without tracing the value writer.
- **CR:** all three blocks in inspected jobs report 0 accepted candidates; values remain 1.00/1.00/1.00. Main rejection reasons remain prior-insulin and prior-hypo-rebound; occasional post-meal SMB rejection is also logged.
- **Basal:** the previous blanket 0/3960 result is no longer universal after the evidence-window correction. Acceptance varies by scenario/seed from 0 to a small number (examples include 1–6/3960); eligible-hour coverage remains below threshold in most inspected rows. A small minority of replay rows reaches gate PASS, but the estimator is still generally starved of eligible evidence. This is a partial improvement in harness behavior, not evidence of safe or useful basal adaptation.
- **Percentage card:** both `pct-` and `pct+` events execute. Example final states include 100% after balanced counts and 90% after more downward than upward events (e.g. 5 down / 4 up). Continue auditing transition order, hysteresis and persistence; counts alone do not establish a stable or beneficial learner.
- **LAPROTECT:** inspected daily audits generally show gate OPEN. The protected-low count is lower than all-cause lows because rows in the 180-minute post-hypo interval are excluded. Keep this separate from the all-cause safety interlock; no safety threshold is relaxed by this run.
- **Clinical aggregate metrics (TIR, CV, time <3.9/<3.0/>10, hypo-treatment grams):** not reported. The artifacts contain encrypted `result.enc` payloads and the available GitHub connection exposes metadata but no supported binary retrieval/decryption path. No decryption key was printed or exposed.

### Decision
- Infrastructure/replay execution: PASS.
- Algorithmic readiness: FAIL / NOT DEMONSTRATED.
- ISF and CR learning are effectively blocked by current eligibility filters in the inspected logs.
- Basal evidence availability improved but remains inadequate in most rows.
- Percentage-card transitions need a dedicated stability/safety audit.
- Do not integrate, deploy, or copy these changes to `autoisf-atc3`, the child's phone, pump, or live profile.
- Next laboratory-only work: trace the ISF 162→180 write path; instrument per-row ISF/CR exclusion timing; inspect basal accepted-hour coverage and LBTAIL eligibility across all 24 jobs; add a deterministic percentage-card transition ledger and assertions; then rerun the same 24-row batch. Obtain clinical aggregate metrics only through a permitted artifact-access path.


## Update — learner filter correction after APP23 audit

### Diagnosis
APP23 run `45261150404` completed its 24 replay jobs technically. The code audit found three over-broad temporal exclusions:
- Basal evidence rejected every row carrying `afterInsulin` (3 h after any SMB/manual bolus), despite delivered insulin already being included in the basal activity convolution. In frequent-SMB data this made almost every 65-minute candidate window ineligible.
- ISF rejected the full 4 h pre / 3 h post candidate window whenever the 5 h `afterMeal` flag was set, and rejected any prior insulin flag in the full 4 h pre-window. This was wider than the modeled overlap.
- CR rejected prior insulin/hypo for 3 h and any post-meal SMB/manual correction across the full 6 h response window, even after the event was no longer materially overlapping.

### Laboratory-only changes
Commit: `564c95bba5f5b26c5096a4748db37df3e251c539`
File: `plugins/aps/src/test/kotlin/app/aaps/plugins/aps/openAPSAIMI/HistoryReplayTest.kt`

Changes:
1. Basal evidence no longer blanket-excludes `afterInsulin`; insulin remains represented in the activity convolution. Hypo, old cannula, sensor gap, activity, illness and false-low exclusions remain.
2. ISF meal exclusion narrowed to explicit meal events in the 3 h pre / 2 h post interval; separate prior bolus exclusion narrowed to 1 h. Existing insulin activity/net calculation remains.
3. CR prior insulin and prior hypo lookback narrowed from 3 h to 2 h; post-meal SMB/manual-correction exclusion narrowed from 6 h to 2 h.
4. LAPROTECT now counts post-treatment/rebound lows as all-cause safety evidence. It still excludes missing BG, sensor-start, false-low and sensor-gap rows. The prior exclusion of `afterHypo` contradicted the all-cause interlock intent.
5. Existing unit assertions updated for the new protection and basal evidence semantics.

### Validation status
- These changes are a hypothesis-driven laboratory correction, not evidence of improved clinical performance.
- Compile + helper tests must pass before starting a fresh 24-scenario replay.
- No clinical metrics are inferred from encrypted result artifacts.
- No changes to `autoisf-atc3`, live profiles, pump settings, or the child's installed app.


## Interim update — APP23 rerun after learner filter correction

- Compile + learner audit helper tests: PASS, run 45264677162.
- APP23 replay run: 45270000264, commit 4b87bf6b12263a986fb8a5fd28a5b2fe402c9c6c.
- Batch: canonical 24 scenarios; at the time of this interim update 20/24 replay jobs completed successfully, 4 still running; no completed replay job failures.
- Basal evidence is no longer zero: completed scenario logs show accepted observations in the hourly evidence audit and `gate=PASS`; typical hourly accepted totals are materially above the prior 0/3960 result. These are audit-row counts, not independent clinical outcomes.
- ISF audit: 0 accepted candidates in every inspected completed scenario. Rejection causes remain meal-window, hypo-rebound and prior-insulin.
- CR audit: 0 accepted episodes in every inspected completed scenario; prior-insulin and prior-hypo-rebound remain dominant rejection causes.
- Apparent ISF profile movement (e.g. 162→180 or 203) is not evidence of an accepted ISF learner proposal: percentage-card normalization in `setPct()` scales `isfP` inversely when the global profile percentage changes. ISF audit remains at 0 accepted.
- LAPROTECT now counts rebound/post-treatment lows as all-cause lows; examples show both OPEN and BLOCK_INCREASES when the all-cause low rate exceeds 3%.
- Percentage card shows repeated down/up events in some scenarios (e.g. six decreases and four restores, ending at 80%). This requires stability/hysteresis review; do not treat as a validated learning outcome.
- Encrypted result artifacts are being uploaded per scenario. Clinical TIR/CV/<3.9/<3.0/>10/hypo-treatment grams have not yet been extracted or aggregated; no clinical efficacy claim is made.
- No changes to production branch `autoisf-atc3`, no app installation/deployment, and no live pump/profile changes.


## Follow-up — ISF causal-window correction (2026-11-23)

- Commit: `7e16831f11f051e250f805f5c16092117bd4b3d2` on `aimi-replay`.
- Finding: the ISF rejection condition scanned `afterHypo` over the entire 7 h candidate window (4 h pre + 3 h post). Since `afterHypo` is itself a rolling 180-minute flag, a hypo anywhere in that broad window could mark many later rows and reject a correction even when the hypo was not temporally close to the correction. This was broader than the intended causal exclusion.
- Change: ISF now rejects an event for an actual hypo in the 3 h immediately before the correction or in the 3 h response window after it. The wider sensor-gap, missing-BG, old-cannula, activity, illness and false-low checks remain unchanged. Meal and separate-bolus filters remain unchanged.
- Safety intent: do not accept corrections contaminated by a recent low or a low during the measured response; remove only the inherited rolling-flag overreach outside the correction's causal window.
- Compile workflow automatically triggered by the source change: run 45271884135; status was in progress when this note was written.
- APP23 run 45270000264: 23/24 replay jobs had completed successfully at this note; scenario 22 remained in progress. The 23 completed jobs had no replay-job failures.
- No claim of clinical improvement. Await compile/helper-test result and then a fresh APP23 replay before evaluating whether ISF obtains valid candidates.
- CR remains at zero accepted episodes in inspected scenarios. Do not loosen its prior-insulin or prior-hypo exclusions without a separate causal analysis.
- Percentage down/up oscillation remains unresolved; no arbitrary cooldown or relaxation has been added yet.
- All work remains confined to `aimi-replay`; no production branch, installed app, live profile, pump or Nightscout changes.


## Follow-up — percentage restoration hysteresis (2026-11-23)

- Commit: `377f75facf2cefdac28bbc99a6b025a9db4d5306` on `aimi-replay`.
- APP23 evidence: several 56/84-day replay scenarios issued multiple percentage decreases and restorations in the same run (e.g. 6 decreases / 4 restores; another 6 / 5). The old rule restored 10 percentage points after only one qualifying 3-day low-rate window, allowing a rapid reversal of the preceding decrease.
- Change: percentage restoration now requires two consecutive eligible learner checks with eligible low rate <=2%, no LAPROTECT insulin-increase block, and pctBack enabled. Checks are separated by the existing LAGAP (default 3 days), so the earliest restoration is after at least 6 days of sustained evidence. The streak resets after a decrease, a failed qualifying window, or a restoration; each +10-point step therefore requires fresh sustained evidence.
- Added pure helper assertions for first-check rejection, second-check acceptance, protection block, low-rate threshold and 100% ceiling.
- Compile + helper-test workflow triggered for this exact source commit: run 45272107823; in progress at report time.
- The preceding compile run 45271884135 targets the earlier ISF-only commit and is not the final validation for this combined source state.
- APP23 run 45270000264: 23/24 replay jobs complete successfully; scenario 22 remains in progress. No completed replay failures.
- Neither change is clinically validated. The next required sequence is: compile + helper tests for commit 377f75f, then a fresh full 24-scenario replay; compare ISF accepted counts and percentage event counts. CR remains unlearned (zero accepted in inspected scenarios) and its exclusions remain unchanged.
- All changes remain on `aimi-replay`; production branch, installed app, live profile and pump are untouched.


## APP23 run 45270000264 — final completion

- All 24/24 replay jobs completed successfully; 0 replay-job failures. Plan job also succeeded.
- Basal audit: every inspected scenario had a nonzero accepted count and `gate=PASS`; accepted rows varied substantially by scenario. This is a data-eligibility audit only, not proof of correct basal estimates or clinical benefit.
- ISF audit: still 0 accepted candidates in all inspected scenarios, including final scenario 22. Rejections in scenario 22 were dominated by meal-window (90–115 per scan), then hypo-rebound and prior-insulin. The first ISF window correction is not yet evaluated in a full replay because APP23 run 45270000264 used the pre-correction commit.
- CR audit: 0 accepted meal blocks in the inspected scenarios; prior-insulin and prior-hypo-rebound remain dominant.
- Percentage card continues to produce multiple decreases/restorations within some runs (scenario 22: 4 decreases, 2 restores, final 80%). The two-consecutive-window restoration hysteresis is intended to reduce immediate reversals; it is not yet tested.
- The encrypted artifacts do not provide accessible clinical metrics in the current workflow surface. No TIR/CV/hypoglycemia efficacy conclusion is possible.
- Next validation target is compile/helper tests for commit `377f75facf2cefdac28bbc99a6b025a9db4d5306`, then a new 24-case replay against that exact commit. Do not reuse APP23 metrics as validation of the newer code.


## ISF low-window precision refinement — commit 2c660b1cff1521737956de92adba6c3b44bb0ff8

- Further audit of the synthetic row schema showed `hypo` records carbohydrate treatment grams, not every measured low. The first correction-window patch therefore still used a treatment-event proxy.
- Refined ISF contamination test to reject an actual BG <70 mg/dL in the 3 h before correction or 3 h after correction. Sensor-gap, missing-BG, old-cannula, activity, illness, false-low, meal-window and separate-bolus filters remain in place.
- Added boundary assertions for lows at the first included pre-window tick, at the correction tick, just outside the post-window, and an invalid short array window.
- Compile + helper tests for this exact commit: run 45272322156 (in progress at time of writing).
- APP23 run 45270000264 is complete (24/24 success) but predates this ISF correction and cannot validate it. A new full replay must follow a passing compile/test.
- Percentage restore hysteresis remains part of the same source branch and is included in this compile run.


## APP23 public synthetic metrics recovered from workflow summaries

The workflow's `Public synthetic summary` step does expose aggregate-only second-half metrics (not raw rows). These were extracted from all 24 successful jobs in run 45270000264. Values below are means across seeds/scenarios grouped by synthetic duration; they are NOT APP-vs-LEARN paired comparisons.

| Synthetic window | n | BG <70 mg/dL | BG <54 mg/dL | BG >180 mg/dL | Derived TIR 70–180 | CV | Hypo treatment | Insulin |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| 56 days | 15 | 2.73% | 0.01% | 7.15% | 90.11% | 28.28% | 29.30 g/day | 15.01 U/day |
| 84 days | 9 | 2.72% | 0.03% | 10.11% | 87.17% | 29.88% | 27.12 g/day | 15.90 U/day |

Per-job ranges:
- 56-day group: BG <70 1.1–4.7%; BG <54 0–0.1%; BG >180 1.2–12.7%; CV 22.0–31.4%; hypo treatment 14.25–47.89 g/day; insulin 13.51–16.46 U/day.
- 84-day group: BG <70 1.8–3.7%; BG <54 0–0.1%; BG >180 5.0–15.6%; CV 28.1–31.0%; hypo treatment 18.19–36.45 g/day; insulin 12.19–18.54 U/day.

Important interpretation:
- The summary covers only the second half of each synthetic replay (28 days for SYNTH_56; 42 days for SYNTH_84), not the full replay.
- TIR is derived as 100% - BG<70% - BG>180%; <54% is a subset of <70% and must not be subtracted twice.
- These aggregates mix different stress profiles (noise, dawn, meal sensitivity, drift, profile/ISF perturbations, etc.). Do not interpret the 56-day and 84-day groups as a controlled duration comparison.
- There is no paired APP baseline for these exact 24 scenarios in run 45270000264. Therefore these values describe this learner-enabled replay only and cannot establish improvement or regression.
- ISF and CR accepted counts remain zero in the audited learner. No ISF/CR learned parameter changes should be claimed from the final summaries.


## CR prior-hypo causal-window correction — 2026-11-23

- Commit: `64f4502df72bf83986e966440014de19100b5cc1`.
- Audit finding: CR rejected a meal if any row in the prior 2 h carried `afterHypo`. That is a rolling 180-minute flag, so it could mark a meal as contaminated even when no actual low or hypo treatment occurred inside the 2 h causal window.
- Change: retain the 2 h lookback, but require direct evidence within that interval: either measured BG <70 mg/dL or hypo-treatment carbohydrates >0. The stale rolling `afterHypo` flag is no longer sufficient by itself. Sensor gaps, missing BG, old cannula, activity/illness/false-low, recent insulin, post-meal SMB/manual correction, and ambiguous low causes remain excluded.
- Added helper tests for measured low, hypo treatment, clean data and missing BG.
- Compile/helper-test run for exact commit: 45273029777 (in progress at time of writing).
- Previous compile run 45272322156 (commit 2c660b1) remained in progress without step progress; it does not validate commit 64f4502.
- No replay against the corrected ISF/CR/percentage logic has been run yet. After compile/helper tests pass, run the full 24-scenario APP23 batch.
- No clinical efficacy claim; all changes remain confined to `aimi-replay`.
