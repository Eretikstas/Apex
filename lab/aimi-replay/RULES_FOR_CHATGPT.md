# Hard rules for ChatGPT (set by the user and Claude, 25.11). These win over any other document.

Breaking a rule = stop, write in STATUS.md "Task" what you wanted to do and why, wait for Claude.

## 1. Where you may write

- Repository `Eretikstas/Apex`, branch `aimi-replay` ONLY. Never `main`, never `autoisf-atc3`, never `Eretikstas/My-apps`.
- Files you may change: `plugins/aps/src/test/kotlin/app/aaps/plugins/aps/openAPSAIMI/HistoryReplayTest.kt`,
  anything in `lab/aimi-replay/` (except the "Results" part of STATUS.md and this file).
- NOT allowed (stop and ask): any file in `plugins/*/src/main`, `app/src/main`, `implementation/`, `core/`, `ui/`,
  `pump/`, device tests (`app/src/androidTest`), and ANY file in `.github/workflows/` (no new workflows, no edits).
  Your 4 auto-start workflows (app23-auto, app23-lab-run, app24-baseline-auto, aimi-replay-emulator) were turned off
  by Claude on 25.11; do not turn them on.

## 2. How you start a lab run (the only way)

1. The code is at a commit with a green compile check (`aimi-replay-compile.yml`).
2. Write ONE batch line into `lab/aimi-replay/RUN.txt` (under the comment line) and commit. That commit starts it.
3. Every job has `SYNTH_<days>`; the run is always `algo=autoisf`, `sim=true` (fixed by the workflow — AIMI runs are
   not about the child and are not comparable). At most 120 jobs; not while another lab run
   goes (the workflow refuses, it does not queue).
4. Token lists come from `lab/aimi-replay/app22_batch.json` (APP per scenario and seed) plus your test tokens. Never
   guess a token; never drop the scenario tokens (`CHG_28,CHGISF_x`, `MEALX_x`, `DAYNEED_x`, ...).
5. Read the results on the run page of `aimi-replay.yml` (public summary). Do not ask for the encryption key.
   You do not need an API key: never ask the user for one.

## 3. How much

- One task at a time. At most 10 commits per task. Each commit small, with a clear message.
- After each task: report in STATUS.md "Task": commits, run id, the summary table you read, what Claude must check.
  Never write "passed" without the numbers from a finished run.

## 4. What must stay true

- Without a new token the result is exactly the old one (check: base / APP / seed 1 gives
  `<70 2.7 | <54 0.0 | >180 8.2 | CV 28.4 | hypo 27.64 | insulin 14.83`).
- Everything new is OFF by default and switched on by its own token.
- A change that makes the learner give MORE insulin, or that weakens a protection, needs Claude and the user first.
- The pass rule for learner variants: lows < 54 not higher, lows < 70 not higher, hypo grams not higher than APP;
  highs may only then be compared.
- No personal data (name, Nightscout, real numbers of the child) in this public repository.

## 4a. Work on your own: when you may STOP, and when you must NOT (user 25.11)

The user wants you to finish a task without asking. Stops cost the user's time and Claude's limits.

STOP and wait for Claude ONLY when:
1. a control does not match its reference numbers, or a learner change MEANT FOR THE APP breaks the pass rule.
   In screening / test steps (11, 11b, 13–18) a candidate that fails the pass rule is a RESULT: report it, go on.
   Calibration runs have NO pass rule (they are meant to have more lows): report the table and the ranking;
2. the task needs something section 1 forbids (a workflow, the app branch, src/main code, a new learner behaviour
   that gives more insulin or weakens a protection);
3. compile fails twice with the same error;
4. the task needs a token or data that does not exist and the task text gives no way around it.

Do NOT stop for anything else. In particular:
- The newest message from the user (with Claude's text) wins over older text in this file or in old task texts.
  If an old line says "after task N" and the new message says task N is cancelled, go on.
- A small choice the task leaves open (which values of a grid, which of two equal tokens, how to name a column):
  choose yourself, write the choice and the reason in STATUS.md "Task", and go on.
- Waiting for a run is not a reason to ask: check it again later and go on.
- At the end of a task: write the report, then STOP (that is the normal end, not a question).

## 5. Your next tasks: ONE CHAIN, go from step to step without asking (Claude + user 25.11)

Goal: a learner that really lowers the lows of the real child, proven on a model that looks like the child, before
the phone bench. Work through the chain. After each step write its table to STATUS "Task" and go on. Stop only by
RULES 4a, or at the end of the chain. One run per step (no daily limit any more).

**Step 9b — calibration** (as in the user's chat text). Then pick the calibration yourself: the combination with the
smallest distance to T whose <54 is at most the T band; on a tie take the one with insulin closer to T. Call its
model tokens `CAL` (the MEALX / CHGISF / SBIAS / TREAT tokens of that combination). Write which one you took.
If the best distance is still large (more than 3), still go on, but say so in big letters.

**Step 10 — baseline on the calibrated model ("virtual child").** 6 scenarios x seeds 1–3 x 2 variants = 36 jobs.
Scenarios = APP base prefs + `CAL` + one of: nothing (usual), `MEALX` one step lower than CAL (eats even less),
`CHGISF` one step higher (insulin stronger), `CHGISF` one step lower (weaker), `DRIFT_2`, `NOISE_0.12`.
Variants: (a) APP as is; (b) APP without the learner cards (remove LEARNB5, LEARNDURA, PCTCARD and their helper
tokens LBLOWMAX_*, LBFILL2, LBMIN_*, PCTMAX_*; keep everything else). Table: <70, <54, >180, CV, hypo g/day, insulin.
This answers: does today's learner help this child or not.

**Step 11 — what lowers the lows.** Take the existing tokens that act on lows (find them in HistoryReplayTest.kt
and write their line numbers): at least `GUARDCHAIN`, `CHAIN_<pct>` (try 2 values), `SMARTL`, `LAPROTECT`, and, if
a token exists for the SMB protection threshold or the hypo temp target, 2 values of it. Each one ALONE on top of
variant (a), the 6 scenarios x 3 seeds. Max 120 jobs per run; split into 2 runs if needed.
Pass rule here: <54 not higher than (a), <70 lower than (a) by at least 10 % relative, >180 at most +2 points.

**Step 11b (added by Claude 25.11 evening; do it now, then STOP).** On variant (a) with `CAL`, 6 scenarios x seeds 1–3:
`Z2_126`, `Z2_139`, `Z2_150` alone (54 jobs), then in a second run `GUARDCHAIN` + each of the three Z2 values
(54 jobs). Table as in step 11 with the gate result per row. Failing the 10 % gate is NOT a stop: it only means
"not chosen". Report both runs, then STOP (step 12 waits for Claude).

**Step 13 (after 11b, go on without asking): learner attribution, see `LEARNER_ATTRIBUTION_SPEC.md`.**
13a. Implement token `CRAUDIT` (counting only, no behaviour change) and run the CR/ISF audit on CAL usual, 3 seeds.
     **13a details (Claude 25.11 night, after commit cbe6f1b6):**
     1. Wait for compile run 45689360655. If red: fix ONLY the compile error, push, wait for green. No replay before
        a green compile on the exact commit you run.
     2. Before the run, write in STATUS.md (block "Step 13a"): cbe6f1b6 is NOT only counting, it changed audit rules
        (ISF window k-48..k+36 -> k-72..k+72; ISF any other SMB in 6 h -> reject `smb-within-6h`; CR post-meal SMB /
        correction exclusion 2 h -> 6 h). `isfSeen` / `crBlockVerdict` also drive LAISF / LACR cards, so old
        LEARNALL / LAISF / LACR results are not comparable with new ones. APP variant (a) does not use them.
     3. Start by RUN.txt only, ONE start, 3 jobs: take the first job line of the 11b run 2 RUN.txt (SEED_1, base CAL),
        remove `GUARDCHAIN` and `Z2_126`, add `CRAUDIT`; copies for SEED_2 and SEED_3. Nothing else changes.
     4. From public job logs only, per seed copy exactly: the `AIMI audit CRAUDIT` line, the `AIMI audit ISF` line,
        the CR audit line(s), the summary line.
     5. Table, one row per seed: (a) candidates == accepted + rejected for ISF AND CR (if CR has no such check or it
        does not add up: write "CR reconcile: MISSING/FAIL", do not fix silently); (b) accepted count — if < 5 for
        ISF or CR in all seeds write "Audit too strict: top reason = <reason> (<n> of <total>)", do NOT loosen rules;
        (c) summary metrics must equal APP (a) usual of step 10 for the same seed; any difference = FAIL
        "CRAUDIT changes behaviour" -> STOP and wait for Claude (this one IS a stop).
     6. Then go on to 13b. Do not change audit windows or rules. "not run" + reason for any check that did not run.
13b. Implement token `WATTR` (weight cards, report-only, rules 1–6 of the spec) in HistoryReplayTest.kt, OFF by
     default; compile green; check that without the token the APP control numbers are unchanged.
13c. Run the planted-cause test (spec section 3) with `WATTR` and `CRAUDIT`: positive and negative cases, 3 seeds.
     Report the score table per card. This is a test of the learner's judgement: a bad score is a RESULT, not a stop.
Then STOP. Do not apply any weight change in the model; WATTR only reports.

**Steps 14–18 (after step 13; one chain, go on without asking; Claude reviews only at the END).** Build the
learner of `LEARNER_CURRICULUM.md` in HistoryReplayTest.kt, each part behind its own token, OFF by default, compile
green after each part, "no token = old numbers" checked (base / APP / seed 1 control) after each part.
14. Token `LSTAGE0`: the stage-0 data filter (cannula, COB minimum-rate meals, untreated-hypo check, calibration
    window). Report how many days / meals each rule drops on CAL usual, 3 seeds. No behaviour change otherwise.
15. Token `LISF2`: stage-2 ISF card by part of the day (BerNie signs, 10–20 % step, night by IOB), report-only.
    Planted test: CHGISF one step up and down -> right direction; MEALX_0.7 / NOISE_0.12 / CANCH_96 -> silent.
16. Token `LCR3`: stage-3 CR card (AAPS post-meal rule, 60 g, COB minimum-rate meals excluded), report-only.
    Planted test: CRT one step up and down -> right direction; MEALX_0.7 / TREAT_15 -> silent.
17. Token `LEXPL`: stage-5 explanations: per low its cause, per card the rules it passed; output a short table.
18. One final run: APP + LSTAGE0 + LISF2 + LCR3 + WATTR + LEXPL (all report-only) on 6 scenarios x 3 seeds:
    numbers must equal APP (report-only changes nothing) + the score table of every card. Then report and STOP.
Report format for every step: commit, run id, table, which assumptions failed. Never "passed" without numbers.

**Step 12 — the best together.** Combine the tokens that passed step 11 (all of them, and the best 2). Same table and
pass rule. Then write a short final list: which tokens pass, how much they lower <70 on each scenario. STOP.

Never in this chain: new learner formulas, src/main code, the app branch, real numbers of the child in the repo.
