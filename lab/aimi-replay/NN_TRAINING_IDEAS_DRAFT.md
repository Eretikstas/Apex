# Small neural net for the learner — training ideas, DRAFT (25.11)

Brainstorm by two free models (A = Nemotron 3 Ultra, B = Ernie 5.5 Flash), merged by a free model. Claude read the
safety parts only. IDEAS, not tasks. Synthetic only; real patient data never goes into this repository.

## Claude notes (these win over the text below)

1. Order agreed with the user: (1) compare the emulator with the real child (tools: DUMPCSV + compare_days.py, side
   task); (2) only if similar enough: synthetic weeks with known truth; (3) small net; (4) offline check on real
   history vs Autotune and the rule learner; (5) report-only card. Stop at any stage that fails.
2. Section 9 row 2 is mislabelled: a net that "thinks basal is too high" after compression lows LOWERS basal (row 12
   says it right). Read every guard row for direction before use.
3. NOISE ranges below (0.1-0.9) are far too high for this emulator: NOISE_0.12 already gives about 11 % <70.
4. "Calibrate with one MCMC run" is too simple: the calibration is the main work and is done by Claude privately.
5. Good parts to keep: guards OUTSIDE the net (rules after it, fail-safe = no change), trap weeks as a fixed test
   suite (section 4, ties in with STRESS_SCENARIOS_DRAFT.md table 3), a tiny baseline first, "I do not know" output
   (ensemble spread + out-of-distribution check), never train on data the net itself influenced.

---

# Merged Training Variants for Synthetic-to-Real Profile Error Estimator

---

## 1. Domain Randomization — Axes per Synthetic Week

| # | Axis (token-style name) | Range | Why it matters for real child | Source |
|---|-------------------------|-------|-------------------------------|--------|
| 1 | `MEAL_VAR_CV` | 0.15–0.45 (CV of meal carb entry error) | Parents misestimate carbs ±20–40%; log-normal, not Gaussian | A |
| 2 | `MEAL_TIMING_JITTER` | ±0 to ±30 min (bolus-to-eat delay) | Bolus given late/early; changes IOB-glucose alignment | A (NEW) |
| 3 | `CARB_ABSORPTION_A_FACTOR` | 0.6–1.6 (triangle `a` param multiplier) | Fat/protein/fiber slow absorption; varies meal-to-meal | A (NEW) |
| 4 | `CARB_ABSORPTION_B_FACTOR` | 0.6–1.6 (triangle `b` param multiplier) | Same as above; independent variation per meal | A (NEW) |
| 5 | `UNLOGGED_SNACK_PROB` | 0–0.3 per hour (05–22h) | Grazing, toddler snacks; COB never hits zero | A |
| 6 | `UNLOGGED_SNACK_SIZE_G` | 3–15 g (log-uniform) | Small untracked carbs | A (NEW) |
| 7 | `MISSED_BOLUS_PROB` | 0–0.1 per meal | Forgot bolus entirely | A (NEW) |
| 8 | `DOUBLE_BOLUS_PROB` | 0–0.03 per meal | Accidental double-tap | A (NEW) |
| 9 | `LATE_BOLUS_MIN` | 0–60 min (delayed bolus after first bite) | Real life: bolus after eating starts | A (NEW) |
| 10 | `ILLNESS_INSULIN_RESIST_FACTOR` | 0.4–0.9 (multiplier on ISF) | Fever, infection; lasts 2–7 days | A |
| 11 | `ILLNESS_ANOREXIA_FACTOR` | 0.2–0.8 (meal size multiplier during illness) | Eats less, same bolus → lows | A (NEW) |
| 12 | `GROWTH_DRIFT_PCT_PER_WEEK` | 0–25% | Child growing; basal/ISF/CR all shift together | A |
| 13 | `DAWN_AMPLITUDE_FACTOR` | 0.8–2.5 (liver output 04–08h multiplier) | Dawn phenomenon varies by age, puberty | A |
| 14 | `DAYTIME_STRESS_FACTOR` | 0.9–1.4 (basal need 10–18h multiplier) | School, cortisol, excitement | A |
| 15 | `ACTIVITY_DAY_PROB` | 0–0.3 per day | Sport, playground, swim | A |
| 16 | `ACTIVITY_SENS_MULT` | 1.1–2.0 (insulin sensitivity multiplier during activity) | Variable intensity | A (NEW) |
| 17 | `ACTIVITY_DURATION_H` | 0.5–4 h | Short vs long sessions | A (NEW) |
| 18 | `ACTIVITY_TOD_DIST` | Categorical: morning/afternoon/evening | Timing changes glucose pattern | A (NEW) |
| 19 | `CANNULA_LIFE_H` | 48–120 h (change interval) | Parents stretch sites | A |
| 20 | `CANNULA_DEGRADATION_CURVE` | Parametric: onset 48–72h, slope to 50–80% at end | Fiasp occludes faster; not linear | A |
| 21 | `CANNULA_OCCLUSION_PROB_PER_DAY` | 0–0.15 | Sudden blockage | A |
| 22 | `CANNULA_OCCLUSION_ONSET_H` | 0–24 h after change | Early vs late failure | A (NEW) |
| 23 | `SENSOR_NOISE_SD_MGDL` | 1–8 mg/dL (slow noise SD) | Different sensor lots, skin | A |
| 24 | `SENSOR_NOISE_AUTOCORR` | 0.95–0.998 (phi) | Noise timescale: minutes vs hours | A |
| 25 | `SENSOR_COMPRESSION_PROB_PER_NIGHT` | 0–0.8 | Sleep position dependent | A |
| 26 | `SENSOR_COMPRESSION_DEPTH_MGDL` | 20–80 mg/dL drop | Pressure severity | A (NEW) |
| 27 | `SENSOR_COMPRESSION_DURATION_MIN` | 10–60 min | How long pressure lasts | A (NEW) |
| 28 | `SENSOR_FIRST_DAY_BIAS_MGDL` | -30 to +10 mg/dL | Dexcom G6/G7 first 24h reads low | A (NEW) |
| 29 | `SENSOR_CALIBRATION_OFFSET_MGDL` | -20 to +20 mg/dL | Bad fingerstick calibration | A (NEW) |
| 30 | `SENSOR_GAP_PROB_PER_WEEK` | 0–3 | Transmitter off, Bluetooth loss | A |
| 31 | `SENSOR_GAP_DURATION_MIN` | 15–120 min | Variable gap length | A (NEW) |
| 32 | `HYPO_TREATMENT_CARBS_G` | 4–20 g (per event, variable) | Parent grabs different snacks | A |
| 33 | `HYPO_TREATMENT_TIMING_JITTER` | 0–20 min after alert | Delay to treat | A (NEW) |
| 34 | `HYPO_OVER_TREAT_PROB` | 0–0.3 | Second treatment before rise | A (NEW) |
| 35 | `REBOUND_GUARD_ADHERENCE` | 0–1 (fraction of guards respected) | Parents override SMB hold | A |
| 36 | `PUMP_CLOCK_DRIFT_MIN` | -30 to +30 min | Basal schedule misaligned | A (NEW) |
| 37 | `TIMEZONE_SHIFT_H` | -12 to +12 (travel) | Basal schedule shifts abruptly | A (NEW) |
| 38 | `MENSTRUAL_CYCLE_PHASE` | Categorical: follicular/luteal/none | Insulin resistance week before | A (NEW) |
| 39 | `MENSTRUAL_RESIST_FACTOR` | 1.0–1.35 | Magnitude of resistance | A (NEW) |
| 40 | `HOT_WEATHER_PEAK_MULT` | 0.7–1.0 (insulin peak time multiplier) | Heat → faster absorption | A (NEW) |
| 41 | `HOT_WEATHER_DIA_MULT` | 0.8–1.0 (DIA multiplier) | Shorter insulin action | A (NEW) |
| 42 | `DIET_CHANGE_ABSORPTION_MULT` | 0.5–1.5 (carb absorption speed) | Gluten-free, low-carb, new foods | A (NEW) |
| 43 | `ISF_TRUE_MULT` (`CHGISF`) | 0.55–1.5 | Real sensitivity swings with growth/illness/puberty | B |
| 44 | `DAYTIME_BASAL_NEED_MULT` (`DAYNEED`) | 0.7–1.4 | Dawn/school/afternoon shifts; classic Autotune miss | B |
| 45 | `TRUE_CR_G_PER_U` (`CRT`) | 15–60 g/U | Small child, wide range; CR errors drive post-meal hypo | B |
| 46 | `DAY_TO_DAY_BASAL_SD` (`DAYSD`) | 0.05–0.30 | Normal scatter; teaches net not to chase noise | B |
| 47 | `LIVER_REBOUND_STRENGTH` (`KCR`) | 0.02–0.08 | Post-low recovery; misread as basal error | B |
| 48 | `MEAL_SIZE_MULT` (`MEALX`) | 0.7–2.0 | Real intake variance | B |
| 49 | `UNLOGGED_FRACTION` (`UNLOG`) | 0–40% | Huge pediatric confounder; parent misses entries | B |
| 50 | `LATE_TAIL_BOLUS_PCT` | 20–70% | Mimics real family habits | B |
| 51 | `HYPO_TREAT_SIZE_G` | 4–15 g | Rebound source | B |
| 52 | `REBOUND_CORRECTION_CHANCE` | 0–60% | Over-treatment highs | B |
| 53 | `PROFILE_START_ERROR` (`PROFX`) | 0.8–1.4 | How far the algorithm must move | B |
| 54 | `ACTIVITY_PATTERN` | start day/period | Sensitivity swings | B |
| 55 | `CANNULA_CHANGE_INTERVAL` (`CANCH`) | 48–120 h | Degradation before 72h | B |
| 56 | `CANNULA_FAILURE` (`CANFAIL`) | period | Occlusion highs | B |
| 57 | `SENSOR_NOISE_SD_FRAC` (`NOISE`) | 0.1–0.9 | **CONFLICT**: A uses 1–8 mg/dL absolute; B uses fraction of signal | A+B |
| 58 | `SENSOR_NOISE_AUTOCORR` (`NOISEPHI`) | 0.95–0.999 | **CONFLICT**: A uses 0.95–0.998; B uses 0.95–0.999 | A+B |
| 59 | `LOW_END_SENSOR_BIAS` (`SBIAS`) | 0.1–0.6 | False lows at low glucose | B |
| 60 | `COMPRESSION_LOWS_PER_WK` (`COMPRESS`) | 0–6/wk | Night false lows | B |
| 61 | `SENSOR_GAP_DURATION` (`GAP`) | 15/30/60 min | Missing-data handling | B |
| 62 | `ILLNESS_RESISTANCE` (`ILLF`) | 0.5–0.8 | Do-not-act trap | B |
| 63 | `SINGLE_OUTLIER_DAY` (`OUTDAY`) | 0.7–1.4 | One bad day must not move profile | B |
| 64 | `AUTOISF_BASELINE_WEIGHTS` | ±50% of start | Weight targets | B |
| 65 | `VOMIT_PCT` | 0–100% | Carbs eaten but vomited after bolus → false "too little insulin" | B (NEW) |
| 66 | `MISSED_BOLUS` / `DOUBLE_BOLUS` | per day/meal index | Bolus entry errors → CR mis-estimate | B (NEW) |
| 67 | `OCCLUSION` | day, hour, duration | Full no-delivery → must NOT raise basal | B (NEW) |
| 68 | `SPORT` | day, duration, sens 1.3–2.0 | Single activity day sensitivity | B (NEW) |
| 69 | `SENSOR_FIRST_DAY_BIAS` | day, bias | First-24h false low → do-not-act | B (NEW) |
| 70 | `CALIBRATION_OFFSET` | day, offset ±15–25 mg/dL | Step bias the net must not chase | B (NEW) |
| 71 | `CLOCK_DRIFT_MIN` | ±60 min | Basal indexed at wrong hour; must not "fix" wrong hours | B (NEW) |
| 72 | `MENSTRUAL` / `STRESS` | start, len, factor | Cyclic/persistent resistance that must reverse | B (NEW) |
| 73 | `POST_HYPO_RESIST` | day, hour, factor | Transient post-low resistance | B (NEW) |
| 74 | `HOT_WEATHER` / `DIET_CHANGE` | peak/DIA, meal a/b | Absorption speed changes → ISF/CR misread | B (NEW) |
| 75 | `SNACKS` | day, n, grams | COB never zero → basal unlearnable that window | B (NEW) |
| 76 | `PRE_BOLUS_TIMING_JITTER` | -15…+45 min per meal | Timing vs CR confound | B (NEW) |
| 77 | `PUMP_BASAL_QUANTIZATION` | min increment + delivery jitter | Small-child tiny basal | B (NEW) |
| 78 | `CGM_WARMUP_GAP` | on sensor change | Extra missing windows | B (NEW) |

**Rule**: Every synthetic week samples ALL axes independently (except physiologically coupled ones: e.g., `ILLNESS_*` together). This forces the net to learn invariant features, not emulator quirks. (A)

---

## 2. Training Targets / Labels

| # | Target | How label is known in emulator | Real-world utility | Source |
|---|--------|-------------------------------|-------------------|--------|
| 1 | `basal_error_by_block[6]` | True basal need per 4h block vs profile basal → fractional error | Direct Autotune input; parent adjusts 6 basal rates | A |
| 2 | `isf_error_by_block[4]` | True ISF per 6h block (night/morning/afternoon/evening) vs profile ISF → fractional error | AutoISF baseline; parent adjusts 4 ISF values | A |
| 3 | `cr_error_by_block[3]` | True CR per meal block (breakfast/lunch/dinner) vs profile CR → fractional error | Meal bolus accuracy; parent adjusts 3 CRs | A |
| 4 | `autoisf_weight_delta[6]` | Optimal weights (acce, brake, low, high, pp, dura) from counterfactual re-run vs current → delta | Fine-tunes SMB aggressiveness; parent approves 1 weight at a time | A |
| 5 | `cannula_degradation_state` | Current insulin delivery efficiency (0.5–1.0) from `canF` trace | Warns "site likely failing, change soon" before highs | A |
| 6 | `sensor_bias_mgdl` | Mean (reading - true glucose) over last 24h (excl. compression) | Detects calibration drift; parent recalibrates or discounts | A |
| 7 | `unlogged_carb_rate_g_per_hr` | Total unlogged carbs / hours with COB≈0 (night) → g/hr | Explains mysterious highs; suggests snack logging | A |
| 8 | `illness_insulin_resistance_factor` | True ISF multiplier during illness window (from `ILL_` params) | Tells parent "insulin 30% weaker, raise temp targets" | A |
| 9 | `growth_drift_pct_per_week` | Fitted exponential to true basal need trend over 14d | Predicts when next basal review needed | A |
| 10 | `data_quality_score[4]` | (a) CGM completeness, (b) meal logging completeness, (c) cannula age risk, (d) sensor age risk | Gates confidence: "only 60% clean data, don't trust output" | A |
| 11 | `recent_hypo_overtreatment_flag` | True if >1 treatment within 30 min without glucose rise → binary | Prevents learner from lowering basal/ISF after rebound highs | A |
| 12 | `meal_absorption_slow_flag_per_block[3]` | True if median meal `a`/`b` > 1.3x default in block | Suggests eCarbs or split bolus for that meal | A |
| 13 | `change_nothing_probability` | 1 if week is pure confounder (Section 4) | Gate against acting on noise | B |
| 14 | `not_enough_clean_data_score` | f(clean-day count, COB=0 hours, meals/block) | Defer advice until sufficient evidence | B |
| 15 | `confounder_multi_label` | {illness, occlusion, unlogged, compression, calib, vomiting,…} present this week | Explains WHY to abstain | B |
| 16 | `cause_of_last_excursion` | Multi-class: bolus / rebound / fasting / cannula / sensor / min-rate COB | Stage 5 explanations for parent | B |
| 17 | `safe_stage_readiness` | Which of basal/ISF/CR/weights is currently valid (TIR, clean data) | Order enforcement | B |
| 18 | `counterfactual_delta_tir` | Predicted ΔTIR if 10% step applied (replay emulator with step) | Lets net pick step that actually helps | B |
| 19 | `cannula_age_estimate_from_delivery` | From delivery signature | Flags degraded delivery windows | B |
| 20 | `sensor_health_bias_estimate` | From residual patterns | Gates hypo-driven learning | B |
| 21 | `days_until_enough_evidence` | Based on data quality & variability | Tells parent "recheck in N days" instead of acting now | B |
| 22 | `next_2h_hypo_risk_flag` | Safety head from emulator future | Can veto insulin-increasing proposal | B |

**Label generation**: For each synthetic week, run emulator → get ground truth → compute labels. Store as multi-task regression + classification targets. Normalize regression targets to [-1, 1] range (e.g., basal error -50% to +50%). (A)

---

## 3. Curriculum — Easy to Hard Weeks

| Phase | Weeks | Tokens / Description | Rationale | Source |
|-------|-------|---------------------|-----------|--------|
| 0 | 1–50 | `PROFX_1.0 DAWN_1.0 MEALX_1.0 UNLOG_0 NOISE_0.15 COMPRESS_0 CANCH_96` | Perfect world: only profile basal error (`PROFX` varies ±20%) | A |
| 1 | 51–200 | `PROFX_[0.8,1.2] DAWN_[1.0,1.3] MEALX_[0.9,1.1] UNLOG_[0,5] NOISE_[0.15,0.3] COMPRESS_[0,1] CANCH_[72,120]` | Add realistic noise, dawn, meal variance, mild unlogged, sensor compression | A |
| 2 | 201–500 | + `DRIFT_[0,10] ACT_[7,14] DAYNEED_[1.0,1.2] ILL_21 ILLD_3 ILLF_[0.8,0.95]` | Growth drift, weekly activity, daytime stress, late illness (day 21 = outside 7d window → context only) | A |
| 3 | 501–1000 | + `UNLOG_[5,15] MEALX_[0.8,1.3] CANFAIL_[14,21] NOISE_[0.3,0.5] COMPRESS_[1,3] CHGISF_[0.8,1.2] CHG_[3,5]` | ISF change mid-week, cannula failures, more unlogged, sensor noise, ISF shift | A |
| 4 | 1001–2000 | + `ILL_[2,5] ILLD_[2,4] ILLF_[0.6,0.85] ACT_[3,7] DAYNEED_[1.1,1.4] DAWN_[1.2,1.8] PROFX_[0.7,1.4]` | Illness INSIDE window, frequent activity, stronger dawn, larger profile error | A |
| 5 | 2001–4000 | + `MEAL_VAR_CV_[0.2,0.4] MEAL_TIMING_JITTER_[0,20] CARB_ABSORPTION_A_FACTOR_[0.7,1.4] UNLOGGED_SNACK_PROB_[0.05,0.15] SENSOR_FIRST_DAY_BIAS_[-20,0] HOT_WEATHER_PEAK_MULT_[0.8,1.0] MENSTRUAL_RESIST_FACTOR_[1.0,1.2]` | NEW axes: meal entry error, timing, absorption variance, grazing, sensor first-day, heat, menstrual | A |
| 6 | 4001–8000 | Full domain randomization (all 78 axes, independent) + `SEED` varied widely | Stress test: net must generalize across all combinations | A |
| 7 | 8001–10000 | **Adversarial curriculum**: weeks designed to trap naive models (Section 4) | Hard negative mining | A |
| — | Interleaved | ~10% calm weeks throughout all phases | Prevent drift toward always-abstain | B |
| — | Progression rule | Only advance when validation loss on current phase plateaus < 5% improvement over 500 weeks. Freeze earlier phase weights (progressive unfreezing). | Prevents catastrophic forgetting | A |

---

## 4. Negative / Adversarial Examples — Trap Weeks (Correct Answer = "No Change" or "Not Enough Clean Data")

| # | Week name | Key Tokens / Description | Trap for naive model | Correct label | Source |
|---|-----------|--------------------------|----------------------|---------------|--------|
| 1 | `clean_stable` | `PROFX_1.0 DAWN_1.0 MEALX_1.0 UNLOG_0 NOISE_0.15 COMPRESS_0 CANCH_120` | Predicts "change basal" from random noise | All zeros; data_quality=1.0 | A |
| 2 | `compression_nights` | `PROFX_1.0 COMPRESS_5 NOISE_0.2` | Sees night lows → lowers basal/raises ISF | Basal/ISF zero; sensor_bias=-15 (night only); data_quality.cgm=0.7 | A |
| 3 | `sensor_drift_up` | `PROFX_1.0` + `SENSOR_CALIBRATION_OFFSET_+15` day 3 | Sees rising trend → lowers basal/raises ISF | sensor_bias=+15; basal/ISF zero | A |
| 4 | `illness_resistance` | `ILL_2 ILLD_4 ILLF_0.65` | Sees highs → raises basal/lowers ISF/strengthens CR | illness_factor=0.65; basal/ISF/CR zero | A |
| 5 | `vomiting_unlogged` | `ILL_2 ILLD_3 ILLF_0.9 UNLOG_40 MEALX_0.5` | Sees lows → lowers basal/weakens ISF | illness_factor=0.9; unlogged_carb_rate negative; basal zero | A |
| 6 | `cannula_fail_midweek` | `CANFAIL_3` (day 3 failure 14–20h) | Sees afternoon highs → raises basal | cannula_degradation=0.5 (day 3 only); basal zero | A |
| 7 | `old_cannula_gradual` | `CANCH_72` (degraded 60–72h every cycle) | Sees creeping highs → raises basal progressively | cannula_degradation ramp; basal zero | A |
| 8 | `growth_spurt` | `DRIFT_20` | Sees rising trend → raises basal weekly | growth_drift=20%; basal zero (profile tracks if updated) | A |
| 9 | `sport_day_lows` | `ACT_1` (day 1 sport 10–22h, sens=1.5) | Sees lows → lowers basal/weakens ISF | activity day flag; basal/ISF zero | A |
| 10 | `party_unlogged_sweets` | `UNLOG_0` + `PARTY_3_60` (day 3, 60g unlogged at 18h) | Sees evening high → strengthens CR/raises basal | unlogged_carb_rate=8 g/hr (evening); CR/basal zero | A |
| 11 | `missed_bolus_lunch` | `MISSEDBOLUS_2_1` (day 2, lunch) | Sees post-lunch high → strengthens CR | missed_bolus_flag; CR zero | A |
| 12 | `double_bolus_breakfast` | `DOUBLEBOLUS_4_0` (day 4, breakfast) | Sees morning low → weakens CR/lowers basal | double_bolus_flag; CR/basal zero | A |
| 13 | `late_bolus_dinner` | `LATEBOLUS_5_2_45` (day 5, dinner, 45 min late) | Sees early low then late high → weakens CR then raises basal | late_bolus_flag; CR/basal zero | A |
| 14 | `sensor_gap_night` | `GAP_30` (day 4, 02:00, 30 min) + `NOISE_0.4` | Interpolated gap looks like trend → changes basal | data_quality.cgm=0.85; basal zero | A |
| 15 | `hypo_overtreat_rebound` | `TREAT_15 CORR_80 GUARD_0` (large treatment, frequent correction, no guard) | Sees rebound high → raises basal/lowers ISF | recent_hypo_overtreat=1; basal/ISF zero | A |
| 16 | `timezone_shift` | `TIMEZONE_SHIFT_+6` day 3 | Basal schedule misaligned 6h → predicts shifted basal changes | pump_clock_drift=360; basal zero (schedule wrong, not rates) | A |
| 17 | `hot_weather_faster_insulin` | `HOT_WEATHER_2_0.8_0.9` (day 2, peak 0.8x, DIA 0.9x) | Sees early lows → weakens ISF/lowers basal | hot_weather_flag; ISF/basal zero | A |
| 18 | `menstrual_resistance_week` | `MENSTRUAL_1_7_1.25` (days 1–7, 25% resistance) | Sees highs all week → raises basal permanently | menstrual_factor=1.25; basal zero (temporary) | A |
| 19 | `stress_exams` | `STRESS_2_5_1.3` (days 2–6, 30% basal need) | Sees highs → raises basal; doesn't lower after | stress_factor=1.3 (days 2–6); basal zero after day 6 | A |
| 20 | `insufficient_data` | `GAP_60` day 2 + `GAP_60` day 5 + `UNLOG_30` + `COMPRESS_4` | Any prediction is hallucination | data_quality all <0.5; "not enough clean data" flag=1 | A |
| 21 | `gap_60_trap` | `GAP_60` day-4 | Learn from interpolated segment | No-op; data_quality.cgm low | B |
| 22 | `illness_trap` | `ILL_10 ILLD_3 ILLF_0.6` | Raise basal / weaken ISF on resistance highs | illness_factor=0.6; no profile change | B |
| 23 | `cannula_fail_trap` | `CANFAIL_7` / `OCCLUSION` | Raise basal while insulin isn't delivered → crash when resumes | cannula_degradation=0; alert "change site" | B |
| 24 | `gradual_cannula_trap` | `CANCH_72` gradual | Raise basal on degradation highs | cannula_degradation ramp; basal zero | B |
| 25 | `sensor_first_day_trap` | `SENSORFIRSTDAY` false lows | Lower basal on false lows | sensor_bias flag; basal zero | B |
| 26 | `calibration_trap` | `CALIBRATION` +offset | Shift whole profile in offset direction | sensor_bias=offset; profile zero | B |
| 27 | `double_bolus_trap` | `DOUBLEBOLUS` | Weaken CR | double_bolus_flag; CR zero | B |
| 28 | `missed_bolus_trap` | `MISSEDBOLUS` | Strengthen CR / raise basal | missed_bolus_flag; CR/basal zero | B |
| 29 | `unlogged_party_trap` | `UNLOG_30` / `PARTY` | Strengthen CR on unbolused carbs | unlogged_carb_rate high; CR zero | B |
| 30 | `vomit_trap` | `VOMIT_50` | Strengthen CR (carbs "not absorbed") | vomit_flag; CR zero | B |
| 31 | `post_hypo_resist_trap` | `POSTHYPORESIST` | Lower basal on transient post-low highs | post_hypo_resist_flag; basal zero | B |
| 32 | `double_treat_trap` | `DOUBLETREAT`/over-treat rebound | Lower basal on rebound high | recent_hypo_overtreat=1; basal zero | B |
| 33 | `sport_day_trap` | `SPORT` day | Raise basal on activity lows | activity_flag; basal zero | B |
| 34 | `noise_chase_trap` | `NOISE_0.9 NOISEPHI_0.999` | Chase sensor noise into basal | data_quality.cgm low; basal zero | B |
| 35 | `few_clean_days_trap` | Only 1–2 clean days (short synth + heavy gaps) | Act on noise | "not enough data" flag=1 | B |
| 36 | `snacks_all_week_trap` | `SNACKS` all week, COB never zero | Estimate basal from contaminated windows | unlogged_carb_rate high; basal zero | B |
| 37 | `autoisf_on_trap` | AutoISF factor ≠ 1.0 all week | Autotune-style ISF on AutoISF-driven data | autoisf_active_flag; ISF zero | B |
| 38 | `few_meals_trap` | <4 meals in a meal block | CR from too few meals | data_quality.meals low; CR zero | B |
| 39 | `clock_drift_trap` | `CLOCKDRIFT` | Raise basal at shifted (wrong) hours | pump_clock_drift flag; basal zero | B |

**Training**: These weeks appear in Phase 7 with **label = "no change" + data_quality flags**. Loss weights: 3x for adversarial vs normal weeks. (A)

---

## 5. Input Design — Signals, Windows, Missing Data, Leaks

### 5.1 Input Tensor: `[7 days, 288 slots (5-min), 22 channels]` (A) + additional signals (B)

| Ch | Signal | Source | Preprocessing | Missing encoding | Source |
|----|--------|--------|---------------|------------------|--------|
| 0 | CGM glucose (mg/dL) | Raw sensor | `log1p((mg/dL - 70) / 30)` → [-2, 3] | -1.0 (distinct from 70 mg/dL) | A |
| 1 | CGM velocity (mg/dL/5m) | Diff ch0 | Same scale | -1.0 | A |
| 2 | IOB (U) | Pump history | `IOB / max_daily_basal` → [0, 3] | 0.0 (no insulin = 0 IOB) | A |
| 3 | COB (g) | Pump history | `COB / 100` → [0, 2] | 0.0 | A |
| 4 | Basal rate (U/h) | Profile + temp | `rate / max_basal` → [0, 2] | Profile basal (known) | A |
| 5 | Bolus (U/5m) | Pump history | `bolus / max_bolus` → [0, 1] | 0.0 | A |
| 6 | Carbs entered (g/5m) | Pump history | `carbs / 100` → [0, 1] | 0.0 | A |
| 7 | SMB active (bool) | Loop logs | 0/1 | 0 | A |
| 8 | Temp target active (bool) | Loop logs | 0/1 | 0 | A |
| 9 | Autosens ratio | Loop logs | `log(ratio)` → [-0.5, 0.5] | 0.0 (1.0) | A |
| 10 | AutoISF factor | Loop logs | `log(factor)` → [-0.5, 0.5] | 0.0 (1.0) | A |
| 11 | Cannula age (h) | Pump history | `age / 168` → [0, 1] | 0 (new) | A |
| 12 | Sensor age (h) | CGM start time | `age / 168` → [0, 1] | 0 (new) | A |
| 13 | Hour of day (sin) | Timestamp | `sin(2π*h/24)` | N/A | A |
| 14 | Hour of day (cos) | Timestamp | `cos(2π*h/24)` | N/A | A |
| 15 | Day of week (sin) | Timestamp | `sin(2π*d/7)` | N/A | A |
| 16 | Day of week (cos) | Timestamp | `cos(2π*d/7)` | N/A | A |
| 17 | Meal flag (0/1/2) | Carb entry >5g | 0=none, 1=logged, 2=unlogged (emulator only — **NOT fed to net**) | 0 | A |
| 18 | Hypo treatment flag | Carb entry <15g + glucose<70 | 0/1 | 0 | A |
| 19 | Sensor noise estimate | Rolling 30-min MAD | `MAD / 10` → [0, 2] | 0 | A |
| 20 | Data gap flag | Missing CGM slots | 0/1 per slot | 1 if gap | A |
| 21 | Loop suspend flag | Pump suspended | 0/1 | 0 | A |
| 22 | CGM short deltas (5/15/30 min) | Derived | Additional trend channels | -1.0 if gap | B |
| 23 | Trend arrow | Raw sensor | Categorical: rising/falling/stable | 0 if gap | B |
| 24 | Temp basal remaining (min) | Pump | Normalized | 0 | B |
| 25 | `min_5m_carbimpact` flag | Loop | 0/1 | 0 | B |
| 26 | AutoISF state (each `_ISF` factor, winner category) | Loop | One-hot + continuous | 0 | B |
| 27 | Target glucose | Loop | Normalized to profile target | Profile target | B |
| 28 | Sensor warmup flag | CGM session | 0/1 | 0 | B |
| 29 | Meal flags: logged carbs, `lateTail` fired, `split` pending, meal-type tag, pre-bolus timing | Pump/logs | As available | 0 | B |

### 5.2 Windowing
- **Primary**: 7 full days (10080 min = 2016 slots) → downsample to 288 slots/day (5-min) = 2016 steps. Too long for small net.
- **Solution (A)**: Two-branch input:
  - **Global branch**: 7-day summary stats (48 features: medians, IQRs, TIR, hypo/hyper counts, IOB/COB patterns per 4h block, autocorr, cannula/sensor age distribution, meal timing regularity) → MLP.
  - **Local branch**: 3-day sliding window (864 slots) centered on "most informative" segment (detected by attention) → 1D CNN + BiGRU.
  - Fuse → prediction heads.
- **Alternative (B)**: Rolling 5–7 days primary; include longer coarse summary (14 d) for trend/drift; do not feed samples beyond "now".

### 5.3 Missing Data Marking
- **CGM gaps**: Channel 0 = -1.0, Channel 20 = 1.0. Do NOT interpolate. Let net learn gap patterns. (A)
- **Cannula age**: Known from pump history (last site change). If unknown → 168 (max). (A)
- **Sensor age**: Known from CGM session start. If unknown → 168. (A)
- **Meal flags**: Channel 17 = 1 only for **logged** meals. Unlogged meals are **invisible** to net (realistic). (A)
- **Per-channel validity mask + explicit gap/recovery flags** (`GAP`); never impute-then-ignore — mask and let model see "missing." Mark recovery hour post-gap. (B)

### 5.4 Normalization
- Robust per-child scaling; ISF/CR/basal as log-ratios to profile. (B)
- Input normalized per-channel (mean/std from synthetic train set). (A)

### 5.5 What NOT to Feed (Leaks)
| Forbidden | Why it leaks | Source |
|-----------|--------------|--------|
| True glucose (emulator state) | Net would learn identity mapping | A |
| True ISF/basal/CR profiles | Direct label access | A |
| Emulator tokens (`ILL_`, `CANFAIL_`, etc.) | Sim-only, not real | A |
| Counterfactual re-run results | Only in training, not deployment | A |
| Unlogged meal times/sizes | Parent doesn't know these | A |
| Cannula degradation factor (`canF`) | Not observable | A |
| Sensor bias ground truth | Not observable | A |
| Future data (t+1...) | Causality violation | A |
| True ISF/CR/basal/CRT, `CHGISF`/`CHG` day, true cannula factor `canF`, true sensor bias/noise terms, unlogged true grams, `VOMIT`/`MISSEDBOLUS` ground truth, illness flags, the `SEED` and token list, `PROFX`, true (unmeasured) glucose, any future sample, and the label itself | Only what a real phone actually has | B |

---

## 6. Small Model Architectures — Phone/Offline Fit

| # | Name | Architecture | Params | Size (int8) | Latency (Pixel 6) | Pros | Cons | Source |
|---|------|--------------|--------|-------------|-------------------|------|------|--------|
| 1 | **TinyTCN** | 4-layer TemporalConvNet (dilated 1,2,4,8, kernel=3, ch=64) + GlobalAvgPool + 3 FC heads | ~45k | 45 KB | 8 ms | Causal, no recurrence, ONNX-friendly, handles variable length | Limited long-range memory (>2h) | A |
| 2 | **MicroBiGRU** | 2-layer BiGRU (hidden=48) + AttentionPool(16) + 3 FC heads | ~62k | 62 KB | 12 ms | Strong sequence modeling, attention explains "why" | Slightly slower, needs padding | A |
| 3 | **HybridCNN-GRU** | 3-layer CNN (stride 2, ch=32,64,64) → 1-layer GRU (32) → FC heads | ~38k | 38 KB | 6 ms | CNN extracts local patterns (meal spikes), GRU integrates | Two paradigms to tune | A |
| 4 | **Tabular+TS Fusion** | Global stats MLP (48→64→32) + Local 1D-CNN (3-layer, ch=32) → Concat → FC heads | ~30k | 30 KB | 5 ms | Smallest; global stats capture drift, local captures dynamics | Hand-crafted global features | A |
| 5 | **DistilledEnsemble** | Teacher: Large Transformer (256k) → Student: TinyTCN (45k) via logit matching | 45k (student) | 45 KB | 8 ms | Best accuracy via distillation; teacher never deployed | Two-phase training | A |
| 6 | **TinyMLP** | MLP on hand-crafted summary features (per-hour medians, clean-hour counts, TIR, deltas) | ~5–20k | <20 KB | <2 ms | Fast, interpretable, good baseline/gate | Weak on temporal shape | B |
| 7 | **1D Temporal CNN** | Dilated, 2–3 blocks, k=3–5 | ~30–120k | 30–120 KB | 5–15 ms | Captures local meal/insulin dynamics cheaply | Receptive field tuning needed for 7-day context | B |
| 8 | **Small GRU** | 1–2 layers, hidden 32–64 + MLP head | ~50–150k | 50–150 KB | 10–20 ms | Strong on sequences | Slower on phone, watch battery | B |
| 9 | **Feature-Embedding + Attention Pooling** | Per-hour tokens, learned attention | ~80–200k | 80–200 KB | 15–30 ms | Native missing-data masking via attention, highlights influential hours | Heavier | B |
| 10 | **Multi-Head Hybrid** | Shared TCN/GRU encoder → heads {basal, ISF, CR, weights, no-op, OOD/confidence, hypo-risk} | +10–30k for heads | +10–30 KB | +2–5 ms | One model, all targets; needs balanced multi-task sampling | Multi-task balancing complexity | B |
| 11 | **Gradient-Boosted Trees** | ~MBs | — | — | — | Non-NN guard/ensemble member; useful OOD cross-check | Not the primary net | B |

**All (A options)**: Quantized int8 (TFLite/ONNX Runtime Mobile). Input normalized per-channel. Output heads: 6×basal + 4×ISF + 3×CR + 6×AutoISF + 4×data_quality + 4×flags = 27 regression + 4 classification. Shared backbone → task-specific heads (2 FC layers each).

**Recommendation**: Start with **HybridCNN-GRU** (best latency/accuracy tradeoff). Distill from Teacher in Phase 6. (A)

---

## 7. Validation Before Any Real Use

### 7.1 Sim Tests (Held-Out Emulator Versions)
| Test | Method | Pass criterion | Source |
|------|--------|----------------|--------|
| **Version holdout** | Train on emulator v3.2.0, test on v3.2.1 (code changes: brake formula, dura clamp) | MAE on basal error < 8% | A |
| **Seed holdout** | 1000 seeds never seen in training | Median absolute error < 10% on all 13 regression targets | A |
| **Extreme token combos** | Sample 500 weeks from Phase 6 (full randomization) | Zero catastrophic errors (>30% wrong direction) | A |
| **Adversarial suite** | 20 trap weeks (Section 4) | All flagged "no change" or "low confidence" | A |
| **Ablation: missing channels** | Zero out CGM / IOB / COB / meal flags individually | Degradation < 2x baseline MAE | A |
| **Time-shift invariance** | Shift all timestamps ±3h (simulate clock drift) | Predictions invariant (basal blocks shift correctly) | A |
| **Held-out emulator versions** | New seeds AND shifted ranges AND NEW tokens unseen in training | Report per-axis worst-case, not just mean | B |
| **Planted-setting exam** | Inject known `CHGISF`/`NEEDDX`/`CRT`/`DAWN` errors | Measure recovery direction and step-size within 10–20% cap | B |
| **Silence exam** | Table-3 confounder weeks must yield no-op at high rate | Any action on these = fail | B |

### 7.2 Checks on Real History (Offline, No Acting)
| Check | Data | Method | Pass | Source |
|-------|------|--------|------|--------|
| **Retrospective prediction** | Last 90 real days (CGM + pump + carb logs) | Run net weekly; compare proposals to what *actually* happened next week | >70% of "change basal X%" proposals match direction of next-week Autotune | A |
| **Confidence calibration** | Same 90 days | Bin predictions by confidence; plot actual error vs predicted error | Reliability diagram: slope 0.9–1.1, ECE < 0.05 | A |
| **OOD detection** | Same 90 days + 30 days from different child (if available) | Mahalanobis distance in penultimate layer; flag top 10% | Known OOD days flagged; in-distribution not | A |
| **Counterfactual sanity** | Real days with known events (illness, site change, sensor swap) | Verify net outputs correct flags (illness_factor, cannula_degradation, sensor_bias) | >80% recall on known events | A |
| **Parent review simulation** | All proposals from retrospective run | Apply max 10% step; simulate closed-loop in emulator with real noise | No severe hypo (<54 mg/dL) increase vs baseline | A |
| **Real-history replay (shadow)** | Child's logged CGM/insulin/carbs | Run net; compare advice to Autotune/actual clinician decisions | Flag disagreements for review | B |
| **Stress-ladder acceptance** | Held-out sim | Must pass Levels 1–3 before Level 4–5; storm weeks must abstain | Pass all levels | B |
| **Two-key gate** | Held-out real weeks | Shadow-mode agreement + OOD false-trigger rate meet preset bars | Both bars met | B |

### 7.3 Confidence Calibration & OOD Detection
- **Method**: Temperature scaling on validation set (held-out synthetic + real). (A)
- **OOD score**: `max( Mahalanobis(penultimate), entropy(softmax(flags)) )`. (A) + **Ensemble disagreement** (variance across seeds), **feature-space Mahalanobis/density** vs training manifold, **input-reconstruction error**. (B)
- **Threshold**: Set at 95th percentile of in-distribution synthetic validation. (A)
- **Output**: If OOD > threshold → `"insufficient_clean_data": true, "confidence": 0.0, "advice": null`. (A)
- **When triggered**: Output "I don't know," default no-op. (B)

---

## 8. Ways to Reduce Sim-to-Real Gap (≥8)

| # | Method | Description | Source |
|---|--------|-------------|--------|
| 1 | **Emulator fitting to real child** | Run Bayesian calibration (MCMC) on child's last 30 real days → fit emulator params (`DAYSD`, `NOISE`, `NOISEPHI`, `SBIAS`, `KCR`, `DAWN`, `DAYNEED`, `DRIFT`, `CANCH`, meal timing distribution, carb entry error CV). Use posterior samples as synthetic prior. | A |
| 2 | **Real data mixing** | In Phase 5–6, replace 15% of synthetic weeks with **real weeks** (CGM + insulin + carbs from child). Labels from *emulator fitted to that week* (not ground truth — but emulator is now calibrated). Forces net to handle real noise patterns. | A |
| 3 | **Domain-adversarial training** | Add gradient-reversal layer predicting "synthetic vs real" on penultimate features. Net learns domain-invariant representations. | A |
| 4 | **Ensemble of 5 seeds** | Train 5 models (different seeds, data order). Deploy ensemble; output = median prediction. Uncertainty = IQR across ensemble. Reject if IQR > 8%. | A |
| 5 | **Noise injection on real inputs** | At inference, add synthetic sensor noise (`NOISE_0.3`, `COMPRESS_1`) to real CGM 10×, run net 10×, median = prediction. Smooths real-sensor quirks. | A |
| 6 | **Feature normalization per-child** | Compute channel mean/std on child's last 14 days (rolling). Re-normalize inputs online. Removes distributional shift. | A |
| 7 | **Counterfactual augmentation** | For each real week, run emulator *with child's fitted params* but vary tokens (`ILL_`, `ACT_`, `CANFAIL_`) → generate 20 "what-if" variants. Labels known. Adds real-background diversity. | A |
| 8 | **Learner rule distillation** | Train auxiliary head to predict rule-based learner outputs (from `LEARNER_CURRICULUM.md`) on same inputs. Forces net to respect known-safe logic. | A |
| 9 | **Progressive real deployment** | Week 1–2: net runs in **shadow mode** (logs proposals, no parent alert). Week 3–4: alerts only when ensemble confidence > 0.85 AND proposal matches rule-based learner. Week 5+: full advisory. | A |
| 10 | **System-ID the emulator** | Fit `DAYSD`, `NOISE`, `SBIAS`, `CRT`, meal/bolus timing, cannula behavior to real child's logged days; train mostly on the calibrated emulator. | B |
| 11 | **Fine-tune on real weeks with weak labels** | Use Autotune outputs, accepted/rejected past parent decisions, known manual corrections as weak labels. | B |
| 12 | **Keep domain randomization wide** | So no single emulator quirk is exploitable. | B |
| 13 | **Realistic corruption of sim inputs** | Dropouts, quantization, clock skew to match real phone data. | B |
| 14 | **Regularize toward rule-based learner** | Behavior-cloning prior so net deviates only with strong evidence. | B |
| 15 | **Log shadow predictions on real data** | Use later-confirmed outcomes for continual recalibration. | B |
| 16 | **Add deliberately-different emulator variants** | Adversarial sim so net can't overfit one parameter set. | B |
| 17 | **Cross-check with gradient-boosted-tree model** | Sanity guard on each real week. | B |
| 18 | **Require OOD gate to pass on held-out real weeks** | Before enabling any advice. | B |

---

## 9. Top 10 Failure Modes & Guards

| # | Failure mode | How it hurts child | Guard (hard-coded, not learned) | Source |
|---|--------------|-------------------|--------------------------------|--------|
| 1 | **Lowers basal during illness** (sees highs, thinks ISF too strong) | Child gets DKA; insulin resistance needs *more* basal | **Block**: If `illness_factor < 0.9` (detected) → basal delta = 0. Force `illness_factor` head to gate basal head. | A |
| 2 | **Raises basal after compression lows** (sees night lows → thinks basal too high) | Next night: real lows from raised basal + compression | **Block**: If `data_quality.cgm < 0.8` AND lows clustered 0–6h → basal delta = 0. Require ≥3 clean nights. | A |
| 3 | **Strengthens CR for unlogged party sweets** (sees post-meal high) | Next real meal: over-bolus → severe hypo | **Block**: If `unlogged_carb_rate > 5 g/hr` in block → CR delta = 0. Flag "check logging". | A |
| 4 | **Lowers ISF after hypo over-treatment rebound** (sees high → thinks ISF too weak) | Next correction: over-insulin → recurrent hypo | **Block**: If `recent_hypo_overtreat = 1` → ISF delta = 0 for 24h. | A |
| 5 | **Raises basal for sensor calibration drift** (sees trend) | Real glucose drops → hypo | **Block**: If `sensor_bias > 10 mg/dL` → all deltas = 0. Alert "recalibrate sensor". | A |
| 6 | **Changes profile during cannula failure** (sees highs → raises basal) | New site: massive overdose | **Block**: If `cannula_degradation < 0.7` → basal/ISF/CR delta = 0. Alert "change site". | A |
| 7 | **Adjusts AutoISF weights on noisy data** (false acceleration → tunes acce) | SMB becomes unstable | **Block**: If `sensor_noise_estimate > 1.5` OR `NOISEGUARD` active → weight deltas = 0. | A |
| 8 | **Doesn't revert menstrual/stress basal raise** (learns persistent change) | Post-cycle: basal too high → night hypos | **Hard limit**: Any basal raise > 0 requires `growth_drift > 5%/week` OR `data_quality > 0.9` for 14d. Auto-expire raises after 10d if not re-confirmed. | A |
| 9 | **Proposes change with <3 clean days** (overfits noise) | Random walk of settings | **Hard rule**: `data_quality.completeness < 0.7` → `"advice": null, "reason": "insufficient_clean_data"`. | A |
| 10 | **Confidence miscalibrated** (high confidence on OOD) | Parent trusts bad advice | **Hard rule**: If `ensemble_IQR > 0.08` OR `OOD_score > threshold` → `confidence = 0`, advice suppressed. Log for review. | A |
| 11 | **Raises basal during illness/occlusion** → later crash when delivery resumes | DKA risk when delivery restarts | **Confounder classifier + block increases** on unexplained persistent highs < required clean days; **hypo-risk head veto**. | B |
| 12 | **Lowers basal on compression/first-day/calibration lows** → nocturnal hypo | Severe night hypo | **Sensor-health gate + night false-low detector**; require corroborating insulin context. | B |
| 13 | **Strengthens CR from missed/unlogged/vomited meals** → post-meal hypo | Severe post-meal hypo | **Require COB=0 clean-absorption + meal-confusion flags**; default no-op. | B |
| 14 | **Confidently wrong from sim overfit** | Unsafe advice acted upon | **OOD ensemble-disagreement + reconstruction gate** → no-op. | B |
| 15 | **Acts on too little clean data** | Random walk of settings | **Min-clean-days gate + explicit "not enough data" output**. | B |
| 16 | **Under-corrects growth drift** → chronic highs/DKA | Chronic hyperglycemia, DKA risk | **Include drift/trend feature**; allow cautious up-trend steps but hard-cap per step. | B |
| 17 | **Stacks multiple changes at once** → oscillation | Unstable glucose | **Enforce one stage at a time, ≤10–20% step, cooldown between changes**. | B |
| 18 | **Tunes AutoISF weights while basal/ISF wrong** → instability | SMB instability | **Stage-readiness gate** (weights only near TIR ~90%). | B |
| 19 | **Feedback loop: net's own advice contaminates future training data** | Self-reinforcing errors | **Train only on shadow/unaffected data or causally-filtered rows**; never treat own-influenced data as clean truth. | B |
| 20 | **Silent overconfidence in storm/OOD never firing** | No guard activates when needed | **Hard ensemble + OOD threshold that defaults to no-op**; fail-safe is inaction. | B |
| 21 | **Clock/timezone/DST misalignment shifting basal hours** | Basal delivered at wrong times | **CLOCKDRIFT-aware features**; reject profile advice tied to ambiguous hours. | B |
| 22 | **Sensor calibration step causing systematic shift** | Persistent bias | **Skip ±20 min post-calibration windows** for basal/ISF/weights. | B |

**All guards are symbolic, auditable, and unit-tested**. They sit *after* the neural net, not inside it. Net learns to predict *error*; guards decide *whether to act*. (A)

---

## Top 10 to Do First (Most Value for Least Work, Safety First)

1. **Implement hard-coded safety guards (Section 9 items 1–10)** before any model training — these are zero-cost, auditable, and prevent the worst harms immediately.
2. **Build the adversarial trap weeks (Section 4, all 39)** as a fixed test suite — ensures "do no harm" on known confounders before adding complexity.
3. **Calibrate emulator to the real child (Section 8 item 1)** using last 30 days — turns synthetic data from "generic" to "child-specific" with one MCMC run.
4. **Implement input pipeline with missing-data masks (Section 5)** and leak-proof normalization — prevents the most common source of sim-to-real failure.
5. **Train TinyMLP baseline (Section 6 item 6)** on summary features only — gives a fast, interpretable lower bound in hours, not days.
6. **Add confidence calibration + OOD detection (Section 7.3)** with temperature scaling and Mahalanobis distance — enables safe "I don't know" outputs from day one.
7. **Run shadow-mode on 90 real days (Section 7.2)** with the baseline model — validates the whole pipeline end-to-end without risk.
8. **Build the two-branch input (global stats + local window) for HybridCNN-GRU (Section 6 item 3)** — best latency/accuracy tradeoff for phone deployment.
9. **Implement ensemble of 5 seeds + IQR uncertainty (Section 8 item 4)** — cheap robustness boost, catches sim-overfit errors.
10. **Add domain-adversarial head (Section 8 item 3)** during main training — forces domain-invariant features with minimal compute overhead.