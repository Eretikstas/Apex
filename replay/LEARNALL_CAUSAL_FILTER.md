# LEARNALL causal filter — laboratory notes

Scope: synthetic/replay laboratory only, branch `aimi-replay`. No production AAPS module, phone profile, pump driver, or insulin-delivery path is changed by this work.

## Switches

- `LACAUSE`: attach causal-window labels to each 5-minute replay row.
- `LADAYS`: require evidence spanning at least three distinct replay days for learner windows.
- `LAPCTBACK`: allow the percentage card to return from a reduced value toward 100%; it never raises above 100%.
- Keep `LEARNALL`, `LAISF`, `LACR`, and the desired basal/dura learner switches in the same replay preference string.

## Row labels and time windows

- `afterMeal`: meal logged now or during the previous 5 hours.
- `afterHypo`: hypo-treatment carbohydrates now or during the previous 3 hours.
- `afterSmb`: SMB in the previous 3 hours.
- `afterManual`: manual bolus now or during the previous 3 hours.
- `afterInsulin`: either `afterSmb` or `afterManual`.
- `oldCannula`: cannula age above 72 hours, when cannula-age data is present.
- `sensorGap`: no glucose status available for that loop tick.
- `falseLow`: simulated compression-low interval.
- `activity` and `illness`: synthetic scenario tags when the corresponding scenario is active.
- Each reading below 70 mg/dL receives a `hypoCause`: `smb` (SMB in prior 2h), `meal_bolus` (meal bolus in prior 3h), `rebound` (hypo treatment in prior 3h), `fasting`, `other`, or `ambiguous`. Multiple simultaneous candidate causes are marked `ambiguous` and excluded from causal learning.

## Learner eligibility

- Percentage down: only fasting or meal-bolus-associated lows; exclude sensor/compression lows, gaps, old cannula, activity, and illness. SMB- and rebound-associated lows do not lower the percentage.
- Percentage return: second priority after percentage down; only when all lows in the latest 3-day window are at most 2%; cap is 100%.
- CR: meal blocks need at least five eligible meals spanning three distinct days. Require a logged meal bolus and a complete 6-hour follow-up. Exclude meals preceded by a hypo-treatment chain, contaminated by old cannula/gaps/activity/illness/sensor artifacts, or with SMB in the first 3 hours. SMB-, rebound-, and ambiguous-cause lows are not evidence against CR.
- ISF: only explicit manual correction rows (manual bolus without meal carbs), glucose above 180 mg/dL, rise under 18 mg/dL in the preceding 15 minutes, no meal in the 4 hours before or 3 hours after, no hypo treatment, no SMB, no overlapping correction, and no sensor/activity/illness/cannula contamination. Require at least five non-overlapping corrections across three days.
- Basal: retain quiet-window rules and exclude meal, hypo-rebound, recent SMB/manual-bolus, old-cannula, gap, activity, illness, and false-low rows. Require at least three distinct days.
- Dura: ignore hypo-rebound, old-cannula, gap, activity, illness, and false-low rows; require valid data on at least five of the last seven days. Flat-high runs are evaluated only across contiguous clean rows.

## Deliberate limitations

- The synthetic replay currently has no independent sensor-age field. The first day of a newly started sensor therefore cannot be identified reliably and is not guessed.
- `sensorGap` is based on missing glucose status in this synthetic loop; it does not fabricate gaps in the generated sensor stream.
- Cannula filtering is effective only in scenarios that supply cannula-age data.
- These rules are experimental attribution filters, not clinical dosing recommendations. No numerical CR/ISF profile values are changed outside replay.
- This commit has not been built or run through CI; results must be checked in the encrypted laboratory replay before any further decision.
