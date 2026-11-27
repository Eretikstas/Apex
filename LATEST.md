# App CI run 46069733737

- Link: https://github.com/Eretikstas/Apex/actions/runs/46069733737
- Branch `learner-all`, commit `d2f95c4a`, event `workflow_dispatch`, started 2026-11-27T21:21:10Z
- Result: **FAILURE: driver, emulator**

## Job `driver`: failure
- Failed steps: Native Sibionics source tests (and the new SourceSensor entry), Ring overview tests (and the preference key list), Build the app, Bench APK (own package info.nightscout.androidaps.bench, installs next to AAPS)

```text
> Task :plugins:learner:compileDebugKotlin FAILED
* What went wrong:
BUILD FAILED in 9s
> Task :plugins:learner:compileReleaseKotlin FAILED
BUILD FAILED in 1m 32s
BUILD FAILED in 11s
BUILD FAILED in 12s
```

## Job `emulator`: failure
- Failed steps: Build the app and the test app

```text
> Task :plugins:learner:compileReleaseKotlin FAILED
* What went wrong:
BUILD FAILED in 5m 8s
```

## Artifacts (download from the run page)
- `autoisf-atc3-test-reports` (830 KB)
- `autoisf-atc3-ring-shots` (1340 KB)
