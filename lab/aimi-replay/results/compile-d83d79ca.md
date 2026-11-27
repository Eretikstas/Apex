# Compile check for `d83d79ca`

- Link: https://github.com/Eretikstas/Apex/actions/runs/46054986377
- Full commit: `d83d79ca214ff03c72deeea7c6602f4a2f1814c0`
- Conclusion: **failure (compile)**

- Set up job: success
- Checkout: success
- Set up JDK 21: success
- Set up Gradle: success
- Compile replay test sources only: failure
- Test AutoISF weight boundaries: skipped
- Run replay audit helper tests without replay inputs: skipped
- Test adaptation status builder and dashboard presenter: skipped
- Test in-memory AIMI status through APSResult wrapper: skipped
- Assemble laboratory FullDebug APK: skipped
- Upload laboratory APK: skipped
- Post Set up Gradle: success
- Post Set up JDK 21: success
- Post Checkout: success
- Complete job: success

Errors (first 40 lines):
```text
> Task :plugins:aps:compileFullDebugUnitTestKotlin FAILED
* What went wrong:
BUILD FAILED in 2m 33s
```
