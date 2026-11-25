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
**With carbs (meals):** a meal window is NOT excluded for weight cards. Instead the card works per meal phase (see
`AUTOISF_WEIGHTS_GUIDE.md` section 3): for every SMB/TBR in the window it logs WHICH effect won (acce / brake / pp /
dura / range / autosens / clamp). Only the weight of the winning effect in the phase that led to the miss can be
blamed: a low 2–4 h after a meal -> the effects that won during the SMBs given in the 3 h before the low (too much
insulin); a high peak -> the effect that won during the rise (too little insulin). CR is blamed instead when the
miss scales with the carbs of the meal on 3 days and the counterfactual of the weight does not explain >= 50 %.
Weights come after basal/CR/ISF: no weight proposal in a week with a pending basal/CR/ISF proposal.
6. **Report first.** The card writes: W, old -> new, the days, the counterfactual insulin and glucose numbers, and
   which checks passed. It changes nothing by itself (report-only until the user approves).

## 0. What is from sources and what is OUR assumption (user 25.11: nothing invented without saying so)

FROM SOURCES (OpenAPS / AAPS Autotune docs and our AAPS Autotune code, see section 2b): point categories CSF / UAM /
ISF / basal, deviation = avgDelta - BGI, ISF from the median of ISF-point ratios (>= 10 points), CR from meal periods
(>= 60 min), 20 % step blending, limits by autosens min/max, basal 20 % into the 3 hours before, empty hours from
neighbours, basal first then ISF/CR, the AAPS rules for post-meal highs. AutoISF weight formulas, precedence and meal
phases: from the AutoISF Quick Guide and BerNie (AUTOISF_WEIGHTS_GUIDE.md).
OUR ASSUMPTIONS (not in any source; the lab must test them, not trust them): the 5 % "really acting" threshold, the
counterfactual >= 50 % rule, "3 of 7 days", the 3 h window before a low, "opposite <= 1/3". The planted-cause test
(section 3) decides if they work; change them if the score is bad.

## 2b. CR / ISF / basal: use the Autotune method (verified), not a new one

- WARNING (sources): Autotune is NOT valid while AutoISF changes ISF. AutoISF Quick Guide 3.2.0: fast ISF changes
  "render the use of Autosense and Autotune useless"; use Autotune only with the ISF-changing AutoISF features off.
  Tim Street / BerNie "Using Autotune V.1": with dynamic ISF, basal and ISF results are "likely incorrect".
- So: (a) ISF / CR per part of the day by BerNie's signs ("ISF determination V.4.1"): roller coaster or TBR cycling
  0 <-> high -> ISF too strong; does not reach target -> too weak; change 10–20 % for that part of the day; night by
  IOB; CR by the AAPS post-meal rule; first rule out a blocked cannula for highs. (b) Autotune categorization only on
  stretches where the AutoISF factor was 1.0 (OUR assumption; the authors say "switch the features off" - test it).
- Known limits from the docs: one ISF and one CR per day only (no circadian tuning); carbs must be entered exactly
  (else basal is raised at meal times); hypo treatments must be entered (else UAM-as-basal makes an amplifying cycle).
- Post-meal rule (AAPS docs): high after the meal AND still high after full absorption -> CR stronger; high while COB
  > 0 but low after absorption -> CR weaker / pre-bolus timing; check basal first with no-carb periods.

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
