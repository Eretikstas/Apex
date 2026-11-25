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
1. a LEARNER-variant run breaks the pass rule (section 4), or a control does not match its reference numbers.
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

**Step 12 — the best together.** Combine the tokens that passed step 11 (all of them, and the best 2). Same table and
pass rule. Then write a short final list: which tokens pass, how much they lower <70 on each scenario. STOP.

Never in this chain: new learner formulas, src/main code, the app branch, real numbers of the child in the repo.
