# Learner curriculum (what the learner learns, in this order) — Claude + user, 25.11

Rules come from sources only (tag in brackets). OUR ASSUMPTION = not from a source, the lab must test it.
[AP] AAPS docs "Your AAPS profile", [AA] AAPS Autotune, [AC] AAPS COB calculation, [AE] AAPS eCarbs,
[OA] OpenAPS Autotune docs, [QG] AutoISF 3.2.0 Quick Guide, [BM] BerNie Meals/ISF-weights V5.8.1,
[BI] BerNie "ISF determination" V4.1, [BC] BerNie "Carb ratio" V3.4, [BA] Tim Street/BerNie "Using Autotune" V1.
Weights details: AUTOISF_WEIGHTS_GUIDE.md. Proof rules: LEARNER_ATTRIBUTION_SPEC.md.

Stage 0 - data filter (before any conclusion)
- Skip days with illness, cannula failure, sensor errors, medical procedures.
- Highs: first rule out a blocked cannula (common with Fiasp) [BI 7.2].
- A meal whose COB was mostly computed by the minimum rate (min_5m_carbimpact fallback) is NOT used for CR:
  less was absorbed than entered [AC].
- Hypo treatments must be entered as carbs, else basal conclusions are false [OA].
- ~20 min after a sensor calibration: false acceleration, skip for weights/SMB analysis [QG].

Stage 1 - basal first [AP][BI][OA]
- Learn from stretches with no COB and little insulin activity; for a child: at night with COB = 0 [AP].
- Per hour; a change goes 1–2 h before the rise/fall [AP]; hours without data from neighbours, count those days
  [AA][OA]; small steps (Autotune: 20 % of the computed need, autosens limits) [OA].

Stage 2 - ISF second, per part of the day [BI][AP]
- NOT Autotune while AutoISF changes ISF [QG][BA].
- Roller coaster / TBR cycling 0 <-> high -> ISF too strong; does not reach target (esp. at night) -> too weak;
  change 10–20 % for that part of the day, check the next day [BI 7.1][AP]. Night: estimate from IOB [BI 4].
- OUR ASSUMPTION: Autotune categorization only on stretches with AutoISF factor 1.0.

Stage 3 - CR third [AP][BC][AE]
- High after the meal AND after full absorption -> CR stronger; high while COB > 0 but low after -> CR weaker /
  pre-bolus timing; basal right and target reached after absorption -> eating-soon or pre-bolus, not CR [AP].
- Bolus only for carbs absorbed while the bolus is strongly active; Fiasp: about 60 g per bolus at most [BC 1.1].
  Slow meals: eCarbs [AE]. Autotune days start at 04:00; COB > 0 at 04:00 spoils CR [BA].

Stage 4 - AutoISF weights last [QG][BM]
- Only with basal/ISF/CR right (TIR about 90 %), one weight at a time, small steps, several days [QG][AP].
- One effect wins at a time; the winner is the culprit of that SMB [QG]. Meal phases: start acce, strong rise pp,
  turn/peak brake, stuck high dura, level range [BM]. brake = 30–40 % of acce [QG].
- Counterfactual re-run with another weight = the authors' method (emulator) [BM 4.2.7].
- OUR ASSUMPTIONS: >= 50 % of the miss explained, 3 of 7 days, 5 % "acting" threshold, 3 h window.

Stage 5 - explanations
- Every low gets a cause (meal bolus, rebound, fasting, cannula, sensor, COB minimum-rate). Every proposal lists the
  stage rules it passed, or "not sure, because ...".

Stage 6 - exam: planted wrong setting -> found; food/cannula/noise/rebound -> silent (step 13); bench 7 days reports.
