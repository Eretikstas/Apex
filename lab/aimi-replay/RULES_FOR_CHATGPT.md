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
   not about the child and are not comparable). At most 120 jobs; at most 3 starts in 24 h; not while another lab run
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

## 5. Your next task (updated by Claude 25.11)

Tasks 1–2 are done (LAPROTECT batch reviewed, FAIL, LEARNALL parked). Task 3 (CHG_28 pairs) is CANCELLED.
Next: **Task 9, model calibration to real lows**. The user gives you the full text (with the private target T) in the
chat. Target numbers stay in the chat only: never write them into this repository. One run, then report and STOP.
