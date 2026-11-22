# Apex driver v4 (vendored)

Source: https://github.com/russoturisto2026-create/aaps-apex-driver, branch `main`, commit
89d91d8b739b4e213214fe5264275227eecca6ec ("Apex driver v4 2026.11.18.3", 19.11.2026), folder `pump/apex`. It was
copied unchanged in commit 151db18439; every file not listed below is still exactly the author's. Its registration lines
(`aaps-4.0.0-registration.patch`) are added by hand next to the old ATC3 ones in core (see docs/OUR_CHANGES.md).

## Our changes to this module

To see them all: `git diff 151db18439 HEAD -- pump/apex`.

| File | Change | Why |
|---|---|---|
| `link/ApexBLE.kt` | the class and the properties the radio swap needs are `open` (`linkUpAtMs` with a `protected` setter); it is no longer made by DI itself | so a device test can put our software pump in place of the Bluetooth radio |
| `link/ApexRadioBindings.kt` (new) | the DI provider of `ApexBLE`, through `ApexRadioSwap.make` (null in the app: the real radio) | the same |
| `manager/ApexManager.kt` | `writeBasalProfile` and `writeSettings`: with NO answer the values are read back from the pump, and only a refusal or a read-back that does not match fails; the alarm duration is kept only once confirmed | a lost answer is not a failed write (our ATC3 review findings 3 and 4, still open in v4); checked by `ApexScenarioTest` |

The same list, with the files we changed outside this module, is in docs/OUR_CHANGES.md.
The repository has no license file, only DISCLAIMER.md ("as is", no liability).
