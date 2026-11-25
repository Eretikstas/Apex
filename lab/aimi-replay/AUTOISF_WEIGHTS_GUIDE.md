# AutoISF weights: how they work and how to tell which one is at fault

Sources: ga-zelle autoISF 3.2.0 Quick Guide (status 14/02/26, github.com/ga-zelle/autoISF), BerNie
"04_Meals.ISF-weights_FCL-book V5.8.1" (github.com/bernie4375/FCL-potential-autoISF). Our code (3.2.1) is the final
truth; check every formula against `HistoryReplayTest.kt` / the AutoISF plugin before using it.

## 1. The four effects (factor > 1 = stronger ISF = more insulin)

| Effect | Formula | Active when | Weight (range / author start) |
|---|---|---|---|
| acce_ISF | `1 + acce_weight * fit_share * cap_weight * acceleration`; cap_weight 0.5 below target, 1.0 above; fit_share 0..1 (parabola fit quality; bad fit -> acce_ISF = 1) | always, sign of acceleration picks the weight | bgAccel_ISF_weight for positive acc. (0–1, start 0.02); bgBrake_ISF_weight for negative acc. (0–1, start 0.01; author: brake = 30–40 % of accel) |
| bg_ISF | `1 + range_weight * polygon(bg)` (fixed polygon) | below target: lower_ISFrange_weight (weakens ISF); above target: higher_ISFrange_weight (strengthens) | 0–2, start 0.2 each |
| pp_ISF | `1 + delta * pp_ISF_weight` | short_avgdelta > 0 and bg > target + 10 mg/dL | 0–0.15, start 0.005 |
| dura_ISF | `1 + (avg05 - target)/target * dura05/60 * dura_ISF_weight` | bg stays within ±5 % for >= 10 min (dura05) and that average (avg05) is above target | 0–3, start 0.2; careful above 1.5 |

Weight 0 switches the effect off. Note: negative acceleration starts while bg still rises (the slope gets smaller),
so the brake weakens ISF before the peak.

## 2. How the factors are combined (precedence)

1. bg_ISF < 1 (below target): ISF is weakened; if acce_ISF > 1 the two are multiplied; then the WEAKER of that and
   autosens is used. Other effects are not checked.
2. acce_ISF < 1 (decelerating) while others want stronger: the strongest positive factor x acce_ISF; then the
   STRONGER of that and autosens.
3. Normal case: the strongest single factor (with autosens in the comparison) is the only one applied.
4. Then autoISF_min / autoISF_max clamp the result.
So at any moment ONE effect (or one product) "wins". Only the winning effect's weight can change the insulin then.

## 3. Meal phases (BerNie): which weight drives the SMBs when

- Start of a meal (first 3–4 SMBs): acceleration -> **acce** (bgAccel). Too high: big early SMBs, IOB too high for
  small meals -> late low. Too low: slow start -> high peak.
- Strong near-linear rise: **pp**. Too high: SMBs go on when the rise ends -> low 2–4 h later. Too low: high peak.
- Rise slows / peak / fall: **brake** (bgBrake). Too low: SMBs continue into the turn -> overshoot into a low.
  Too high: insulin cut too early -> second rise.
- Stuck high for a long time (fatty meal, resistance): **dura**. Too high: insulin stacks -> low later.
- Level of bg above / below target: **higher / lower range**.
- The author's way to find the culprit: re-run the same day in the AutoISF emulator and read which `_ISF` category
  set the effective ISF, and what a different weight would change. Our learner does exactly that (counterfactual).

## 4. Before tuning weights (authors)

Basal, CR and ISF must be right first (TIR around 90 %); one weight at a time, small steps, several days; tune on
"good CGM days" only; a calibration step needs weights and SMB off for ~20 min (false acceleration).
