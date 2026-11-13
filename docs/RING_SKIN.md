# Ring skin: the main screen with "why"

A separate skin (`SkinRing`). It only draws the home screen; the loop, the algorithm and the other skins are
not changed. Choose it in Preferences → Overview → Skin.

## The screen, top to bottom

1. Status chips: pump link, sensor age, cannula age, reservoir.
2. A warning line, only when there is a reason (link lost, cannula near 3 days, sensor ending).
3. The ring: BG, arrow, delta, prediction in 30 min, minutes ago; the ring itself is time in range for
   3 h / 24 h / 7 days. Left: insulin on board, carbs on board. Right: basal now, sensitivity.
4. Period switch (3 h / 24 h / 7 days) and the stats of that period: below 3.9, in range, above 10, CV.
5. "Next hour" tile: Stable / Rising / Falling, a 3 h graph with 1 h prediction, one line why.
6. "Why": the last loop decision in plain words, guard chips, two earlier decisions.
7. Buttons: Target, Carbs, Insulin, Calculator.

Day and night colors follow the app theme.

## "Next hour" tile rules

| State | When |
|---|---|
| Falling (wins) | lowest prediction in the next 60 min < 3.5 mmol/L (63 mg/dL) and BG < 6 (108 mg/dL) |
| Rising | BG > 8 (144 mg/dL) and rising, or the last SMB was capped |
| Stable | none of the above |

## "Why": every reason AIMI writes, in families

AIMI writes its decision as free text (`rT.reason`, about 200 places: `DetermineBasalAIMI2`,
`BasalDecisionEngine`, `SmbInstructionExecutor`, string resources `reason_*`). The text changes between
versions and mixes languages, so the skin does not translate it line by line. It builds the sentence from:

- the action, from the numbers of the result: SMB units, temp basal rate and duration, zero basal, nothing;
- the predictions (eventual BG, lowest predicted BG);
- the first family below whose markers are in the reason (checked in this order).

| # | Family | Markers in the reason | Plain sentence (example) |
|---|---|---|---|
| 1 | No fresh data | `Data Stale`, `BG data is too old`, `CGM is calibrating`, `CGM data is unchanged`, `no GS`, `No BG history` | No fresh sensor data: the loop waits. |
| 2 | Safety stop | `Safety Halt`, `Safety TBR`, `Auditor Rejected` | A safety check stopped the dose. |
| 3 | Low now / predicted low | `LGS`, `BG < LGS threshold`, `Hypo protection`, `Hard Brake`, `BG is dropping`, `Drop (Δ`, `PredLow`, `Safety cut`, `BG 80–90`, `BG 90–100` | BG is low or falling: basal cut. |
| 4 | After a low | `hypoRecovery`, `POST_HYPO_REBOUND`, `Post-hypo blend dampen`, `[Hypo history]`, `hypo history×` | After a low the loop gives less insulin. |
| 5 | Enough insulin on board | `IOB ... > maxIOB`, `HighIOB`, `STACK_CAP`, `IOB surveillance`, `MaxIob` limit, `Meal mode rising: IOB` | Enough insulin is working already: waits. |
| 6 | Meal mode / prebolus | `Microbolusing ... Mode`, `FCL mode`, `Manual modes meal`, `Early meal`, `Meal/snack`, `Dinner mode`, `anticip`, `FCL`, `Meal Advisor`, `POST_HYPO_MEAL`, `slowCarb` | Meal mode: insulin for the meal. |
| 7 | Rise without carbs (UAM) | `SMB (UAM)`, `Hyper boost`, `Plateau`, `Drift Terminator`, `Strong rise`, `Slow rise`, `BG>180`, `Eventual BG>180`, `HyperKicker`, `PostMealBoost`, `rise ceiling` | BG rises without entered carbs: extra insulin. |
| 8 | Activity | `Morning activity`, `effort×`, `EFFORT×`, `Sport safety`, `Activity SMB Cap`, `Steps` | Activity: less insulin. |
| 9 | Honeymoon | `Honeymoon` | Honeymoon mode: softer correction. |
| 10 | Normal | `Eventual BG ... >=`, `setting current basal`, `Temp basal ... for` | BG on target: normal basal. |
| — | Fallback | anything else | the action and the prediction only, e.g. "Gave 0.15 U, prediction 9.8 in 1 h". |

Limits are added as a second sentence when their markers are present: `Cap:`, `Limits:`, `maxBolus`, `MaxSMB`,
`Thyroid Guard`, `EndoDampen`, `PKPD`, `COFFEE`, `BASAL_TERMINAL`, `ENGINE_BOUND`: "Wanted X U, the limit allowed Y U."

The full reason stays one tap away (the Loop sheet), so nothing is hidden.

The mapper is `RingWhyMapper` with unit tests for every family and the fallback. When AIMI adds a reason the
mapper does not know, the fallback still says what the loop did.
