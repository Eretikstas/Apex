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
