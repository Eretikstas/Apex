# Learning lab baseline

## Frozen source

- Repository: Eretikstas/Apex
- Baseline branch: `autoisf-atc3`
- Baseline commit: `916fe1ff6d1cf360347a67d5971872f6c7b69127`
- App version: `4.0.0-aisf3.2.1`
- Version code: `1500`
- AutoISF version in source: `3.2.1`
- Java: 21
- Kotlin/JVM target: 21
- Compile SDK: 37
- Min SDK: 31
- Target SDK: 35

The version string and version code were read from `buildSrc/src/main/kotlin/Versions.kt` at the frozen commit. The AutoISF plugin source declares `autoIsfVersion = "3.2.1"`.

## Laboratory branch

- Branch: `aimi-learning-lab`
- Created directly from the frozen commit above.
- This branch is the isolated destination for future educational experiments and tests.
- The source branch `autoisf-atc3` is not modified by this baseline record.
- `main` and the previous `aimi-replay` branch are not merged into this branch.

## Why a new branch was created

GitHub reports no common ancestor between the frozen Claude branch and the existing `main` / `aimi-replay` history. A direct merge or cherry-pick sequence would therefore be unsafe without a file-by-file port and compatibility review.

The previous `aimi-replay` branch identifies itself as `4.0.0.0-dev.AIMI.211026`, not `4.0.0-aisf3.2.1`. Its test results must not be represented as validation of this frozen baseline.

## Safety and scope

- Synthetic data and virtual/emulator testing only.
- No real patient profile, pump, CGM, Nightscout credentials, or therapy settings.
- No changes to `main` or `autoisf-atc3`.
- No experimental weight values are clinical recommendations.
- Every port from another branch requires source comparison, tests, and an explicit record of the changed files.

## Initial findings

- The frozen source contains the six AutoISF weight preferences and their calculation paths.
- The frozen source includes an AutoISF 3.2.1 guide and AutoISF plugin tests.
- The previous branch's UI test is not yet ported. Its failure on `aimi-replay` showed an empty AutoISF preference screen; this is not evidence of a failure on the frozen Claude baseline.
