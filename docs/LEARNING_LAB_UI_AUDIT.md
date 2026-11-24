# Learning Lab — AutoISF UI audit

Baseline: `4.0.0-aisf3.2.1`, AutoISF `3.2.1`, source snapshot `916fe1ff6d1cf360347a67d5971872f6c7b69127`.
Working branch: `aimi-learning-lab`.

## Verified source facts

- `OpenAPSAutoISFPlugin.getPreferenceScreenContent()` defines the AutoISF settings as a nested `PreferenceSubScreenDef` with key `auto_isf_settings`.
- That nested section contains the master switch, min/max bounds, all six weight preferences, and two related settings.
- The six weight fields are `DoublePreferenceKey` entries in `DoubleKey`.
- Each of the six weight keys has no explicit `dependency`, no `hideParentScreenIfHidden`, and uses the default `ElementVisibility.ALWAYS`.
- Their `calculatedBySM` flag remains the default `false`; the Compose double-preference renderer only suppresses a field for simple mode when `calculatedBySM` is true. Therefore simple mode does not hide these six weight fields.
- `PreferenceScreenView` and `PluginPreferencesScreen` auto-expand only the main section (`<screen.key>_main`). Nested sections are independently collapsed by default.
- `PreferenceContentExtensions.addPreferenceSubScreenDef()` renders nested sections as their own clickable headers; their controls are only composed when the nested section's own state is expanded.

## UI test implication

A reliable UI smoke test must:
1. Navigate to the AutoISF preference screen.
2. Wait for the main section to finish composing.
3. Find and expand the nested AutoISF settings header if it is not already expanded.
4. Assert that all six weight controls are displayed and enabled/disabled as expected.
5. Change a harmless synthetic preference value, read it back from the preference store, recreate the screen, and verify persistence.
6. Restore the test preference store to defaults after the test.

Do not infer that the six controls are missing from the application merely because the outer card is visible while the nested subsection remains collapsed.

## Not yet verified

- The exact cause of the previous old-branch UI screenshot has not been proven from its test source and runtime event trace.
- No UI test has yet passed on `aimi-learning-lab`.
- No emulator execution or preference persistence test is claimed by this audit.
- No algorithm or production preference behavior was modified.
