# Lab coordination: two assistants, one repository

Two assistants work on this repository for the same user: **ChatGPT** (lab code) and **Claude** (app, tests, runs).
This file says who changes what, so the two never edit the same file at the same time. Simple English; no personal
data here (the repository is public).

## Who does what

| | ChatGPT | Claude |
|---|---|---|
| Branch | `aimi-replay` (the lab) | `autoisf-atc3` (the app, the Apex v4 driver, device tests, CI) |
| Files it changes | `plugins/aps/src/test/kotlin/app/aaps/plugins/aps/openAPSAIMI/HistoryReplayTest.kt`, `lab/aimi-replay/*.md` (except the "Results" part of STATUS.md), `replay/*.md`, `.github/workflows/aimi-replay-compile.yml` | everything in `autoisf-atc3`; in `aimi-replay` only the "Results" part of `lab/aimi-replay/STATUS.md` |
| Runs | the compile check only (it runs on push) | the lab batches (`aimi-replay.yml`), the emulator runs, decrypting the results |
| Port into the app | no | yes, only after the lab check passed and the user agreed |

## How work moves

1. ChatGPT makes a change in `aimi-replay` as small commits with clear messages, then writes in `STATUS.md`, part
   "Task": the commit, the tokens and scenarios to run, and the expected right answers.
2. The user tells Claude "run STATUS". Claude checks the compile run, starts the batch, and writes the table and the
   card decisions in `STATUS.md`, part "Results" (and a short note to the user).
3. ChatGPT reads "Results" and makes the next change.

## Rules

- `git pull` before any change. Plain push only: no `--force`, no history rewrite.
- In `STATUS.md` each writes only its own part: ChatGPT "Task", Claude "Results".
- ChatGPT does not write to `autoisf-atc3`. Something needed there goes into "Task"; Claude moves it.
- No personal data in this repository. Real numbers of the child stay in the user's private repository.
- While a batch runs, `HistoryReplayTest.kt` is not changed (a run uses the commit it started from, but results
  are easier to tie to a version).
- Nothing in the lab changes the child's settings. Lab results are evidence for the user, not instructions.

## Shared facts

- Baseline to compare with: the learner as in the app now (`APP` = basal card + dura card + percentage card that
  only lowers and comes back to 100 %), lab run 45070147879, 22 scenarios. Its token list per scenario is in the
  user's handoff file (`app22_batch.json`).
- LEARNALL v1 (run 45072107080) was worse than APP (highs x2): the CR card weakened CR almost everywhere and the
  percentage got stuck at 80 %. The causal filter is the fix being built.
- Batch format: `aimi-replay.yml`, input `batch` = token lists separated by `|` (each one a job, at most 256, the
  whole input under 65 KB). Results are encrypted; Claude decrypts them.
