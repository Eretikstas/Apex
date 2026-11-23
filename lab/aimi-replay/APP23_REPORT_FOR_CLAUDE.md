# APP23 laboratory replay — report for Claude

Date: 2026-11-23
Branch: `aimi-replay`

## Scope
- APP23 batch: 24 synthetic replay cases, 8 scenario families × 3 seeds.
- Flags on every row: `LEARNALL, LAISF, LACR, LACAUSE, LAPCTBACK, LADAYS, LAPROTECT`.
- Replay mode: `learn=true`, `pretrained=false`, `sim=false`, `algo=aimi`.
- No changes to `autoisf-atc3`; no deployment to the child's phone.

## Changes made
1. Added a one-shot workflow wrapper at `.github/workflows/app23-lab-run.yml`.
2. The first automatic run was created: [45165462182](https://github.com/Eretikstas/Apex/actions/runs/45165462182).
3. Run 45165462182 completed with both reusable-workflow jobs **skipped**. No replay scenario ran and no artifacts were produced.
4. Root cause found in `.github/workflows/aimi-replay.yml`: the reusable workflow's `plan` job guard accepted only `workflow_dispatch` / `workflow_call`; for the wrapper's push-triggered invocation, the guard evaluated false.
5. Updated the guard to allow non-empty `inputs.batch` or `inputs.prefs` as well:
   `if: ${{ github.event_name == 'workflow_dispatch' || inputs.batch != '' || inputs.prefs != '' }}`
   Commit: `1b2c682ac2f4477e9a76599193b4800e971c0780`.
6. Updated the wrapper to retrigger after the guard fix (commit returned by GitHub connector: `26358c79511d9821f54bfeaab187e6573034b012`).

## Current verified status
- The first run is **not a successful replay**: 2 jobs skipped, 0 artifacts.
- The subsequent push-triggered run of the base workflow was skipped as expected.
- The compile check for commit `1b2c682ac2f4477e9a76599193b4800e971c0780` was still in progress at last inspection.
- Corrected wrapper run [45165525600](https://github.com/Eretikstas/Apex/actions/runs/45165525600) is now active. The plan job succeeded and produced 24 replay matrix jobs; at the latest check, 20 were in progress and 4 queued. This is an active replay, not a completed or passed result.

## Next required technical steps
1. Verify whether the corrected wrapper commit `26358c79511d9821f54bfeaab187e6573034b012` produced an Actions run. If absent, use a supported GitHub Actions dispatch mechanism or enable the original workflow through repository Actions permissions.
2. Confirm the called workflow's `plan` job runs and emits a matrix with 24 entries.
3. Confirm 24 replay matrix jobs complete, inspect all job conclusions and artifacts, and extract only approved aggregate synthetic summaries.
4. Compare all 8 scenario families across seeds 1–3. Report failures and uncertainty; do not treat compile success as replay success.
5. Keep all work on `aimi-replay`. Do not merge to `autoisf-atc3` or deploy to the child's phone.

## Links
- [APP23 batch](https://github.com/Eretikstas/Apex/blob/aimi-replay/lab/aimi-replay/app23_batch.txt)
- [Wrapper workflow](https://github.com/Eretikstas/Apex/blob/aimi-replay/.github/workflows/app23-lab-run.yml)
- [Reusable replay workflow](https://github.com/Eretikstas/Apex/blob/aimi-replay/.github/workflows/aimi-replay.yml)
- [First skipped APP23 run](https://github.com/Eretikstas/Apex/actions/runs/45165462182)
