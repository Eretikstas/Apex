# SOURCES — register of official sources for the emulator and for training (25.11)

RULE (user 25.11): every range used in the emulator or for training must have a row here with a source. No row =
not used, or marked "our estimate". New emulator parameters and training ranges come ONLY from this register.

## Sources read (full text downloaded and searched; quotes below checked as exact substrings, 39/39)

| Short name | Document | Where |
|---|---|---|
| ISPAD 2022 targets | ISPAD Clinical Practice Consensus Guidelines 2022: Glycemic targets and glucose monitoring | PMC10107615 |
| ISPAD 2022 hypo | ISPAD 2022: Assessment and management of hypoglycemia | PMC10107518 |
| ISPAD 2022 exercise | ISPAD 2022: Exercise in children and adolescents with diabetes | PMC10107219 |
| ISPAD 2024 targets | ISPAD 2024: Glycemic Targets | PMC11854972 |
| ISPAD 2024 CGM | ISPAD 2024 Diabetes Technologies: Glucose Monitoring | PMC11854985 |
| ISPAD 2024 AID | ISPAD 2024 Diabetes Technologies: Insulin Delivery | PMC11854989 |
| Fiasp SmPC | EMA Summary of Product Characteristics, Fiasp (EPAR product information) | ema.europa.eu |
| SiBionics peds | Accuracy, safety, and usability of a real-time CGM system in pediatric diabetes management (GS1, 81 children 3-17 y) | PMC13471122 |
| UVA/Padova | Kovatchev 2009 (PMC2681269), Dalla Man 2014 (PMC4454102), Visentin 2015 (PMC4290795): ABSTRACTS only | PMC |
| simglucose | open-source UVA/Padova tables vpatient_params.csv, Quest.csv, sensor_params.csv | github.com/jxx123/simglucose |

NOT reached yet: ISPAD 2022/2024 insulin treatment chapter (Wiley/Karger block downloads) - total daily dose by age,
basal share, ICR/ISF rules; ISPAD sick day chapter (not found in PMC). Until read: illness and dose ranges = NO SOURCE.

## Claude corrections to the table below (these win)

1. The emulator is OUR OWN model, not simglucose: rows that say "emulator uses 10 simglucose children" are wrong.
   simglucose children are 7-12 years, 24-46 kg: OLDER and heavier than our target child. Use them for SHAPE of
   variation between children, not for absolute values.
2. Fiasp: SmPC gives the max glucose-lowering effect 1-3 h after injection (adults); our curve peak 35 min is the
   activity peak chosen for the real loop. Not a direct contradiction, but a GAP: the simulated BODY uses the SAME
   curve as the loop (`Body` -> curve(35, 5)), so the model can never show the harm of a wrong curve. Add a token
   for a different body curve (body peak/DIA != loop peak/DIA), ranges from the SmPC.
3. Glycemic targets "NOT MODELLED" in the table only means the loop does not optimise them; our summaries do report
   <70 / <54 / >180 / CV. The ISPAD 2024 targets are the lab pass marks: TIR >70 %, <70 <4 %, <54 <1 %, CV <=36 %.
4. Real gaps to fix first (all with a source): hypo recheck 15 min (ISPAD) vs our 20 min; CGM day-1 error (MARD
   11.7 % day 1 vs 8.7 % overall, SiBionics peds) not modelled; sensor change every 14 days not modelled; exercise
   with planned carbs 0.3-1.0 g/kg/h and basal/bolus cuts not modelled; CGM lag during exercise about 12 min.
5. Our current illness (insulin 0.7x for 3 days), activity (1.3x / 1.2x night), cannula (to 0.75x at 96 h) and
   compression (-40 mg/dL, 20 min) values have NO SOURCE yet: mark them "our estimate" until a source is read.

---

## Register (built by Gemini Pro from the files above; quotes verified by script)

### Table 1: SOURCES REGISTER

| Parameter | Official value or range for a young child (or "not in sources") | Source file + section heading | VERBATIM quote (copy exactly, max 40 words) | Our emulator now | Verdict |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Fiasp: Onset / First appearance** | ~4 minutes | `fiasp_smpc.txt` (5.2 Pharmacokinetic properties) | "Insulin appeared in the circulation approximately 4 minutes after administration" | Not explicitly defined; relies on mathematical curve starting at 0. | NOT MODELLED |
| **Fiasp: Tmax (peak effect)** | 1-3 hours | `fiasp_smpc.txt` (5.1 Pharmacodynamic properties) | "The maximum glucose-lowering effect of Fiasp occurred between 1 and 3 hours after injection." | Insulin action curve peak 35 min. | MISMATCH |
| **Fiasp: Max effect (Cmax)** | Similar to adults | `fiasp_smpc.txt` (5.2 Pharmacokinetic properties) | "maximum serum insulin aspart concentration was similar between age groups." | Implicit in total insulin action scale. | NOT MODELLED |
| **Fiasp: Duration (DIA)** | 3-5 hours | `fiasp_smpc.txt` (5.1 Pharmacodynamic properties) | "The duration of action was shorter for Fiasp compared to that of NovoRapid and lasted for 3–5 hours." | Exponential curve with DIA 5 h. | MATCH |
| **Fiasp: Children vs adults exposure** | Lower total exposure in children | `fiasp_smpc.txt` (5.2 Pharmacokinetic properties) | "Total exposure of Fiasp was lower in children and adolescents compared to adults when dosed with 0.2 units/kg body weight" | General UVA/Padova parameter scaling, no specific Fiasp pediatric exposure correction. | NOT MODELLED |
| **Fiasp: Pump use** | Approved for basal & bolus (~50/50) | `fiasp_smpc.txt` (4.2 Posology and method of administration) | "Fiasp solution for injection in vial can be used for CSII in pumps suitable for insulin infusion and will cover both the bolus insulin requirement (approximately 50%) and basal insulin." | Used via simulated pump in closed-loop model delivering temp basals and SMBs. | MATCH |
| **Target: TIR** | >70% | `ispad24_targets.txt` (Continuous Glucose Monitoring) | ">70% between 3.9 and 10 mmol/L (70–180 mg/dL)" | Implicit goal of the AID loop; not explicitly tracked as an internal variable. | NOT MODELLED |
| **Target: <70 mg/dL (<3.9 mmol/L)** | <4% | `ispad24_targets.txt` (Continuous Glucose Monitoring) | "<4% <3.9 mmol/L (70 mg/dL)" | Implicit goal; not used as an internal optimization metric. | NOT MODELLED |
| **Target: <54 mg/dL (<3.0 mmol/L)** | <1% | `ispad24_targets.txt` (Continuous Glucose Monitoring) | "<1% <3.0 mmol/L (54 mg/dL)" | Implicit goal; not used as an internal optimization metric. | NOT MODELLED |
| **Target: >180 mg/dL (>10 mmol/L)** | <25% | `ispad24_targets.txt` (Continuous Glucose Monitoring) | "<25% >10 mmol/L (180 mg/dL)" | Implicit goal; not used as an internal optimization metric. | NOT MODELLED |
| **Target: >250 mg/dL (>13.9 mmol/L)** | <5% | `ispad24_targets.txt` (Continuous Glucose Monitoring) | "<5% >13.9 mmol/L (250 mg/dL)" | Implicit goal; not used as an internal optimization metric. | NOT MODELLED |
| **Target: CV** | ≤36% | `ispad24_targets.txt` (Continuous Glucose Monitoring) | "Glycemic variability (coefficient of variation, [%CV]) target ≤36%" | Not internally tracked or optimized by the loop. | NOT MODELLED |
| **Target: HbA1c** | ≤6.5% (with advanced tech) or ≤7.0% | `ispad24_targets.txt` (Glycated Hemoglobin) | "target of ≤6.5% (48 mmol/mol) for those who can safely reach that target with the support of advanced technologies" | Not tracked or calculated in the emulator. | NOT MODELLED |
| **Hypoglycemia: Alert level** | <70 mg/dL (<3.9 mmol/L) | `ispad_hypo.txt` (4.1 Definition) | "A glucose value of <3.9 mmol/L (70 mg/dL) is an alert value that requires attention" | Treats with carbs if sensor is below 3.9 mmol/L. | MATCH |
| **Hypoglycemia: Serious level** | <54 mg/dL (<3.0 mmol/L) | `ispad_hypo.txt` (4.1 Definition) | "A glucose value of <3.0 mmol/L (54 mg/dL) indicates clinically important or serious hypoglycemia." | Not differentiated from alert level in treatment protocol. | NOT MODELLED |
| **Hypoglycemia: Treatment dose** | ~0.3 g/kg (for 5yo ~20kg = 6g) | `ispad_hypo.txt` (9. HYPOGLYCEMIA TREATMENT) | "This can be accomplished by administering ~0.3 g/kg glucose orally" | Administers 5-6 g fast carbs. | MATCH |
| **Hypoglycemia: Recheck time** | 15 minutes | `ispad_hypo.txt` (9. HYPOGLYCEMIA TREATMENT) | "Following initial hypoglycemia treatment, blood glucose should be retested in 15 min." | Rechecks and re-treats "again after 20 min". | MISMATCH |
| **Exercise: Sensitivity rise** | Yes (increased insulin sensitivity) | `ispad_exercise.txt` (2. EXECUTIVE SUMMARY) | "increased risk of hypoglycemia during, shortly after, and up to 24 h after exercise due to increased insulin sensitivity." | Insulin acts 1.3x stronger 10-21h and 1.2x overnight. | MATCH |
| **Exercise: Duration / Nocturnal lows** | 7-11 hours (overnight risk) | `ispad_exercise.txt` (7.5. Overnight following planned activity) | "risk of hypoglycemia lasts for 7–11 h, which increases the risk of overnight hypoglycemia for activity performed after 4 p.m." | Activity day increases insulin sensitivity by 1.2x that night. | MATCH |
| **Exercise: Basal/bolus reductions** | 25-75% bolus reduction, 50-80% basal reduction | `ispad_exercise.txt` (7.2. Prior to planned exercise) | "Insulin pump basal rate reductions of 50% and 80%, reduced the risk of hypoglycemia during aerobic exercise" | Does not automatically trigger preemptive basal/bolus cuts for exercise. | NOT MODELLED |
| **Exercise: Carbs per hour** | 0.3 - 1.0 g/kg/h | `ispad_exercise.txt` (7.3. During the planned activity) | "0.5–1.0 g/kg/h is required in the presence of high circulating bolus insulin" | Does not explicitly add scheduled carbs during exercise periods. | NOT MODELLED |
| **Illness: Effect on insulin need** | Need increases (sensitivity drops) | `fiasp_smpc.txt` (4.4 Special warnings) | "Concomitant illness, especially infections and feverish conditions, usually increases the patient’s insulin requirements." | Illness (ILLF): insulin acts 0.7x weaker for 3 days. | MATCH |
| **CGM: MARD overall** | 8.7% (SiBionics) | `sibionics_peds.txt` (Abstract) | "The MARD value was 8.7%." | Uses normal distribution and generic AR(1) noise, MARD not calibrated. | NOT MODELLED |
| **CGM: MARD hypo range** | MAD 0.46 mmol/L / MARD 10.8% | `sibionics_peds.txt` (Results) | "In the 3.3–4.4 mmol/L range, with 160 paired measurements, the MAD was 0.46 mmol/L." | Adds custom SBIAS so sensor "reads low at the bottom". | MATCH |
| **CGM: MARD day 1** | 11.7% (SiBionics) | `sibionics_peds.txt` (Table 3) | "Early (Day 1) 88 86.4 11.7" | No day 1 degradation modeled. | NOT MODELLED |
| **CGM: Warm-up time** | 30 min (Dexcom G7), 1 h (Libre 3), SiBionics: not in sources | `ispad24_cgm.txt` (Table 2) | "Sensor warm-up time" | Emulator never disconnects or warms up sensors. | NOT MODELLED |
| **CGM: Wear time** | Up to 14 days (SiBionics) | `sibionics_peds.txt` (Procedures) | "Each sensor can be worn for up to 14 days." | Emulator never replaces sensors. | NOT MODELLED |
| **CGM: Lag** | ~12 ± 11 min | `ispad_exercise.txt` (Table 8) | "CGM lags by about 12 ± 11 min during prolonged aerobic exercise." | "5 min lag". | MISMATCH |
| **CGM: Compression lows** | Caused by pressure | `ispad24_cgm.txt` (Accuracy of CGM Devices) | "These discrepancies stem from physiological delays ... and biomechanical factors like motion and pressure" | COMPRESS: nights 0-6 h, reading drops -40 mg/dL for 20 min. | MATCH |
| **UVA/Padova: FDA acceptance** | Substitute to animal trials | `e2681269.txt` (Abstract) | "accepted by the Food and Drug Administration as a substitute to animal trials in the preclinical testing" | N/A (Meta-fact about simulator validity). | MATCH |
| **UVA/Padova: Children cohort** | 100 children | `e2681269.txt` (Abstract) | "a large cohort of n = 300 simulated “subjects” ( n = 100 adults, 100 adolescents, and 100 children)" | Only uses 10 children from the open-source version (child#001 - child#010). | MISMATCH |
| **UVA/Padova: Intra-day variability** | SI varies by time of day | `e4290795.txt` (Abstract) | "postprandial insulin sensitivity ( S I ) in T1DM subjects was lower at breakfast (B) than lunch (L) and dinner (D)" | Applies daytime/dawn insulin multipliers (`stDay`, `stDawn`). | MATCH |
| **Simglucose children: CR** | Min: 7, Mean: 18.7, Max: 25 g/U | `Quest.csv` | "Name,CR,CF,Age,TDI" | Uses standard `Quest.csv` values exactly. | MATCH |
| **Simglucose children: CF** | Min: 24.99, Mean: 34.54, Max: 42.72 mg/dL/U | `Quest.csv` | "Name,CR,CF,Age,TDI" | Uses standard `Quest.csv` values exactly. | MATCH |
| **Simglucose children: TDI** | Min: 16.02, Mean: 22.84, Max: 40.93 U/day | `Quest.csv` | "Name,CR,CF,Age,TDI" | Uses standard `Quest.csv` values exactly. | MATCH |
| **Simglucose children: Age** | Min: 7, Mean: 9.4, Max: 12 yrs | `Quest.csv` | "Name,CR,CF,Age,TDI" | Ages are 7-12 years (Mismatch for a ~5-year-old). | MISMATCH |
| **Simglucose children: BW** | Min: 23.73, Mean: 35.86, Max: 45.54 kg | `vpatient_params.csv` | "Name,i,x0_ 1,x0_ 2,x0_ 3,x0_ 4,x0_ 5,x0_ 6" | Cohort weights are for older children, far too high for a 5-year-old (~18kg). | MISMATCH |


### Table 2: GAPS

| Emulator Behavior NOT Supported by Source Files | Source Facts IGNORED by the Emulator |
| :--- | :--- |
| **Cannula site degradation formula:** The emulator applies a custom mathematical failure rate for cannulas ("from 60 h of wear insulin acts weaker, down to 0.75x at 96 h (CANCH)"). Official files acknowledge infusion set issues but provide no such specific continuous decay curve. | **Fiasp Peak (Tmax) Mismatch:** The emulator peaks Fiasp action at 35 minutes, but the official SmPC explicitly states the maximum effect occurs between 1 and 3 hours. |
| **Sensor Noise Autocorrelation (NOISEPHI):** The emulator generates sensor noise using an autoregressive factor (`NOISEPHI 0.995`) representing slow noise drift. This statistical generation method is not outlined in the clinical texts. | **Age/Weight limits of UVA/Padova open-source cohort:** The target patient is a ~5-year-old child. The provided `child#001` through `child#010` in `Quest.csv` and `vpatient_params.csv` apply strictly to children aged 7-12 weighing 23.7 to 45.5 kg, meaning baseline physiology drastically mismatches a 5-year-old. |
| **Synthetic Learning Algorithms (LEARNG):** The emulator models abstract logic for a "learner" that dynamically shifts guard windows and temp basal caps based on past rebounding. Official clinical guidelines describe commercially available AID behaviors but not this specific heuristic ML model. | **CGM Lag during exercise:** The emulator hardcodes a basic "5 min lag", ignoring the official guidance that CGM lags significantly more (~12 ± 11 min) during physical activity/aerobic exercise. |
| **Arbitrary Hypo Recheck Delay:** The emulator waits 20 minutes before re-checking and treating hypoglycemia again, rather than the universally endorsed 15-minute rule. | **Day 1 CGM Accuracy Degradation:** The SiBionics study notes higher inaccuracy (MARD 11.7%) on Day 1 of wear compared to subsequent days (7.4-10.0%), which the emulator completely bypasses by not simulating sensor insertion/lifespan. |