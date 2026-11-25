# Learner: change a setting only when it is proven to be the cause (Claude + user, 25.11)

The learner may propose a change to a setting (an AutoISF weight, CR, ISF, a basal hour) only when the data show that
THIS setting caused the miss, and not food, a hypo treatment, the cannula, the sensor, another setting or chance.
Every proposal must carry its proof. Without proof: no proposal, only a line "not sure, because ...".

## 1. Weight cards (acce, brake, pp, dura, lower range, higher range, min, max) — token `WATTR`

A card for weight W may propose ONE step (the existing step size of W) only if ALL of these hold:

1. **Clean window.** In the 3 h before the miss: no hypo treatment, no manual correction bolus, no temp target or
   profile switch, cannula age < 72 h, no sensor gap, no sensor noise above the scenario normal, no false drop.
   Meal boluses are allowed only for the pp card (pp acts after meals) and never in the hour after a hypo treatment.
2. **W was really acting.** In the 30–90 min before the miss the factor part that W controls moved the AutoISF
   factor by at least 5 %, and the final factor was NOT held at autoISF_min/max (then it is a min/max case).
3. **Counterfactual.** Recompute, with the same inputs, the AutoISF factor and the loop's insulin (SMB + TBR) for
   W one step in the proposed direction. The insulin difference x ISF must explain at least half of the glucose
   miss (above the target for a high, below 3.9 for a low). If not: W is not the cause.
4. **Repeats.** The same direction on at least 3 different days in the last 7, and the opposite direction on at most
   1/3 as many days.
5. **No better explanation.** No pending basal, CR, ISF or percentage proposal for the same hours (those go first),
   and the miss is not explained by a planted / known cause (food, cannula, sensor).
6. **Report first.** The card writes: W, old -> new, the days, the counterfactual insulin and glucose numbers, and
   which checks passed. It changes nothing by itself (report-only until the user approves).

## 2. CR and ISF — token `CRAUDIT`

The CR/ISF learner accepted ZERO changes in run 45629646841. Before any new rule: count every meal (CR) and every
correction (ISF) candidate and its ONE first rejection reason (sensor, cannula, hypo/rebound, overlapping bolus,
SMB within 6 h, rising glucose at the start, too few data). Candidates = accepted + rejected. Then the same proof
idea as above: CR is blamed only when the meal window is clean and the miss scales with the carbs.

## 3. How we know it works: planted causes (the test)

On the calibrated model (CAL), 3 seeds each. A good learner must be RIGHT on the positive cases and SILENT on the
negative ones.

- Positive (one wrong setting planted): loop weight pp too low (`WPP_0.005`), brake too strong (`WBRK_0.6`),
  acce off (`WACC_0`); for CR: true carbs 20 % above the ratio (`CRT` one step); for ISF: `CHGISF_1.2` without CAL.
  Expected: the card for exactly that setting, in the right direction, within 14 days. No other card.
- Negative (cause is NOT a setting): `MEALX_0.7` alone, `CANCH_96` (old cannula), `NOISE_0.12`, `DRIFT_2`, rebound
  days (`TREAT_15`, too much hypo treatment). Expected: NO weight / CR / ISF proposal (basal or % may react).
- Score per card: right proposals / planted cases, and false proposals per 28 days on negative cases.
  Pass: right >= 2 of 3 seeds on its positive case, false proposals <= 1 per 28 days on all negative cases.

## 4. Hours without clean windows (basal)

Basal hours with no clean window may only be filled from neighbour hours that HAVE clean data (LBFILL2), never moved
more than half of the neighbours' change, and the card must say "filled from hh, hh". The app must do the same as
the lab (Claude checks with a unit test).
