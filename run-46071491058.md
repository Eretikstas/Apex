# App CI run 46071491058

- Link: https://github.com/Eretikstas/Apex/actions/runs/46071491058
- Branch `learner-all`, commit `87cfc44e`, event `workflow_dispatch`, started 2026-11-27T21:37:16Z
- Result: **FAILURE: driver**

## Job `driver`: failure
- Failed steps: Native Sibionics source tests (and the new SourceSensor entry)

```text
> Task :plugins:learner:compileDebugUnitTestKotlin FAILED
* What went wrong:
BUILD FAILED in 11s
```

## Job `emulator`: success

```text
app.aaps.e2e.VirtualBenchTest,app.aaps.e2e.ApexScenarioTest: OK (22 tests)
```

## Artifacts (download from the run page)
- `autoisf-atc3-emulator` (1495 KB)
- `autoisf-atc3-bench-apk` (135511 KB)
- `autoisf-atc3-test-reports` (838 KB)
- `autoisf-atc3-ring-shots` (1340 KB)
