# App CI run 46061515901

- Link: https://github.com/Eretikstas/Apex/actions/runs/46061515901
- Branch `learner-split`, commit `7cc11aea`, event `workflow_dispatch`, started 2026-11-27T20:08:08Z
- Result: **FAILURE: driver**

## Job `driver`: failure
- Failed steps: Native Sibionics source tests (and the new SourceSensor entry), Ring overview tests (and the preference key list), Build the app

```text
> Task :plugins:learner:mergeDebugUnitTestResources FAILED
* What went wrong:
BUILD FAILED in 6s
> Task :app:mergeFullDebugResources FAILED
BUILD FAILED in 1m 13s
BUILD FAILED in 15s
```

## Job `emulator`: skipped

## Artifacts (download from the run page)
- `autoisf-atc3-ring-shots` (1340 KB)
- `autoisf-atc3-test-reports` (830 KB)
