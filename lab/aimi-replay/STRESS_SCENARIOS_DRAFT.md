# Stress scenarios — DRAFT (25.11)

Made by a free model (Nemotron 3 Ultra) from HistoryReplayTest.kt at d0825e03; Claude checked only parts.
Use as a list of IDEAS. Before any run, check each token against the code. Synthetic only.

## Claude corrections (these win over the text below)

1. **CHGISF direction is WRONG below.** RULES step 10 says `CHGISF` one step HIGHER = insulin STRONGER. The model
   wrote the opposite in its notes. Check `CHGISF` in the code before you use it in a ladder.
2. **Base run is wrong below** (`SYNTH_28 SEED_1 LACAUSE`). Use the real base: APP variant (a) + `CAL`
   (MEALX_0.7, CHGISF_1.2, SBIAS_0.45, TREAT_5), SYNTH_56, seeds 1–3.
3. **NOISE values below are far too high.** `NOISE_0.12` already gives <70 about 11–12 % and <54 about 3 %
   (step 11). Use 0.07 / 0.10 / 0.12 at most. Also check: are <70 / <54 counted on the TRUE glucose or on the noisy
   sensor value? Write the answer in STATUS (if on the sensor value, NOISE days are false lows, not real ones).
4. Drop row 23 (menstrual cycle): not for a young child.
5. Rows marked MISSING are proposals for NEW tokens. New tokens only after step 18, one token per commit, OFF by
   default, "no token = old numbers" check each time.
6. Table 3 (wrong learner reactions) is the most useful part: each row = a negative test for steps 15–16
   ("the card must stay silent").

---

# TABLE 1: Every scenario token in the code

| Token | Value format & allowed range | What it does to the child/day | Code line (applies it) | Default if no value |
|-------|------------------------------|-------------------------------|------------------------|---------------------|
| `SYNTH_<days>` | Integer days, e.g. `SYNTH_28` | Enables synthetic child mode; generates `days` days of simulated life | L1093 (`tok("SYNTH", 28.0).toInt()`) | 28 (but token required to enter synthetic mode) |
| `SEED_<n>` | Long integer, e.g. `SEED_1` | Sets RNG seed for all stochastic elements (meals, noise, cannula, etc.) | L1101 (`Random(tok("SEED", 1.0).toLong())`) | 1 |
| `MEALX_<x>` | Double multiplier, e.g. `MEALX_1.3` | Multiplies every meal's logged grams by x (carbs eaten & bolused for) | L1139 (`tok("MEALX", 1.0)`) | 1.0 |
| `CHGISF_<x>` | Double multiplier, e.g. `CHGISF_1.2` | From `CHG_<day>` onward, insulin acts `x` times as strongly (true ISF multiplier) | L1157 (`tok("CHGISF", 1.0)`), L1245 (`if (changed) chgIsf else 1.0`) | 1.0 |
| `CHGKCR_<x>` | Double multiplier, e.g. `CHGKCR_1.0` | From `CHG_<day>` onward, liver response to low (KCR) multiplied by x | L1158 (`tok("CHGKCR", 1.0)`), L1245 (`if (changed) kcr * chgKcr else kcr`) | 1.0 |
| `CHG_<day>` | Integer day (1-based), e.g. `CHG_14` | Day number when `CHGISF`/`CHGKCR` change takes effect | L1156 (`tok("CHG", 9999.0).toInt()`) | 9999 (never) |
| `PROFX_<x>` | Double multiplier, e.g. `PROFX_1.5` | Phone profile basal starts `x` times off (stress test: far start) | L1165 (`tok("PROFX", 1.0)`), L1166 (`profBasal[it] * profX`) | 1.0 |
| `PROF_<name>` | String, e.g. `PROF_HIGH` | Uses alternate profile from `synth.profiles.<name>` in encrypted input (basal, ISF, IC) | L1161-1163 | Main profile |
| `NEEDDX_<x>` | Double multiplier, e.g. `NEEDDX_1.2` | True basal need during day hours (10-21) multiplied by x | L1169 (`tok("NEEDDX", 1.0)`), L1170 (`if (it in 10..21) needDx else 1.0`) | 1.0 |
| `DAYNEED_<x>` | Double multiplier, e.g. `DAYNEED_1.3` | Body's basal need 10-18h multiplied by x (profile has it wrong; meals still there) | L1188 (`tok("DAYNEED", 1.0)`), L1247 (`if (h in 10..17) stDay else 1.0`) | 1.0 |
| `DAWN_<x>` | Double multiplier, e.g. `DAWN_1.5` | Liver output 04-08h multiplied by x | L1186 (`tok("DAWN", 1.0)`), L1247 (`if (h in 4..7) stDawn else 1.0`) | 1.0 |
| `ACT_<day>` | Integer day (0-based), e.g. `ACT_7` | From that day, every 7th day: insulin 30% stronger 10-22h, 20% stronger night after | L1187 (`tok("ACT", -1.0).toInt()`), L1248-1249 | -1 (disabled) |
| `CANCH_<h>` | Double hours, e.g. `CANCH_96` | Cannula changed every h hours; after 60h insulin action drops to 75% at 96h | L1185 (`tok("CANCH", 0.0)`), L1243-1244 | 0 (no change) |
| `CANFAIL_<n>` | Integer days, e.g. `CANFAIL_14` | Every n days a cannula failure: insulin works at 50% 14-20h | L1188 (`tok("CANFAIL", 0.0).toInt()`), L1250 | 0 (disabled) |
| `COMPRESS_<n>` | Double per week, e.g. `COMPRESS_3` | n false night lows per week (0-6h): reading drops ~40 mg/dL for 20 min | L1188 (`tok("COMPRESS", 0.0)`), L1278-1279 | 0 |
| `UNLOG_<pct>` | Double percent, e.g. `UNLOG_15` | pct% of meals eaten but not entered (no carbs logged, no bolus) | L1189 (`tok("UNLOG", 0.0)`), L1323-1324 | 0 |
| `ILL_<day>` | Integer day (0-based), e.g. `ILL_10` | Illness from that day for `ILLD` days: insulin acts `ILLF` as strongly | L1186 (`tok("ILL", -1.0).toInt()`), L1248 | -1 (disabled) |
| `ILLD_<n>` | Integer days, e.g. `ILLD_3` | Duration of illness in days | L1248 (`tok("ILLD", 3.0).toInt()`) | 3 |
| `ILLF_<x>` | Double multiplier, e.g. `ILLF_0.7` | Insulin effectiveness multiplier during illness | L1248 (`tok("ILLF", 0.7)`) | 0.7 |
| `DRIFT_<pct>` | Double percent/week, e.g. `DRIFT_5` | Insulin need grows pct% per week (child growth) | L1185 (`tok("DRIFT", 0.0)`), L1247 (`1.0 / (1.0 + stDrift/100 * week)`) | 0 |
| `KCR_<x>` | Double, e.g. `KCR_0.05` | Liver response to low (mg/dL per minute per mg/dL below 75) | L1102 (`tok("KCR", 0.03)`), L1245 | 0.03 |
| `NOISE_<x>` | Double SD mg/dL, e.g. `NOISE_0.5` | Slow glucose noise standard deviation (per minute) | L1103 (`tok("NOISE", 0.25)`), L1246 | 0.25 |
| `NOISEPHI_<x>` | Double 0-1, e.g. `NOISEPHI_0.99` | Noise autocorrelation (0.98=minutes, 0.995=hours) | L1104 (`tok("NOISEPHI", 0.98)`), L1246 (`noisePhi * e + ...`) | 0.98 |
| `SBIAS_<x>` | Double mg/dL, e.g. `SBIAS_0.5` | Low-end sensor bias: reading -= bias * (100 - sens) when sens < 100 | L1104 (`tok("SBIAS", 0.3)`), L1277 | 0.3 |
| `DAYSD_<x>` | Double, e.g. `DAYSD_0.2` | Day-to-day basal need variation (log-normal SD) | L1103 (`tok("DAYSD", 0.12)`), L1125 (`Math.exp(rnd.nextGaussian() * daySd)`) | 0.12 |
| `CRT_<x>` | Double, e.g. `CRT_10` | True carb ratio (g/U); true CSF = ISF_true / CRT | L1173 (`tok("CRT", cfg.getDouble("crTrue"))`) | From encrypted input |
| `CORR_<pct>` | Double percent, e.g. `CORR_30` | % chance family gives manual correction on rebound (10-120 min after low) | L1105 (`tok("CORR", 30.0)`), L1405-1412 | 30 |
| `TREAT_<g>` | Double grams, e.g. `TREAT_8` | Fixed hypo treatment size in grams | L1190 (`tok("TREAT", 6.0)`), L1315 | 6.0 |
| `TREATL` | Flag (no value) | Learner treatment size: computes carbs to lift to ~5.5 mmol/L using trend + IOB | L1190 (`prefsText.contains("TREATL")`), L1316-1321 | Fixed 6g |
| `LATE` | Flag | Enables late meal tail bolus (90-240 min after meal, seen absorption) | L1182 (`prefsText.contains("LATE")`) | Off |
| `LATECOB` | Flag | Late bolus also counts COB in wizard calculation | L1182 (`prefsText.contains("LATECOB")`) | Off |
| `LATEPCT_<pct>` | Double percent, e.g. `LATEPCT_50` | Late bolus = (above target - IOB) * pct/100, max 30% of meal dose | L1183 (`tok("LATEPCT", 50.0)/100`), L1378-1385 | 50% |
| `LATEB_<x>` | Double multiplier, e.g. `LATEB_1.2` | In late window, temp basal floor = x * profile basal (max basal respected) | L1183 (`tok("LATEB", 0.0)`), L1480-1488 | 0 (disabled) |
| `Z2_<mg/dL>` | Integer mg/dL, e.g. `Z2_117` | SMB back on above this glucose when rising (after hypo off) | L1182 (`tok("Z2", 117.0)`), L1366 | 117 |
| `GUARD_<min>` | Integer minutes, e.g. `GUARD_60` | Rebound guard: after hypo treatment, SMB held for min minutes unless food/strong rise | L1183 (`tok("GUARD", 0.0).toLong()`), L1368-1376 | 0 (disabled) |
| `GUARDCHAIN` | Flag | Two+ hypo treatments in 4h = swing; guard window 1.5x longer (max 240 min) | L1198 (`prefsText.contains("GUARDCHAIN")`), L1370-1371 | Off |
| `TEMPCAP` | Flag | While guard holds, temp basal above profile basal is capped to profile | L1183 (`prefsText.contains("TEMPCAP")`), L1480 | Off |
| `CAPX_<x>` | Double multiplier, e.g. `CAPX_1.5` | Fixed temp basal cap on rebound = x * profile basal (with TEMPCAP) | L1194 (`tok("CAPX", 1.0)`), L1480 | 1.0 |
| `NOIOB` | Flag | Wizard does not add negative basal IOB to meal bolus when glucose < 4.5 (or 5.5 with NOIOB55) | L1181 (`prefsText.contains("NOIOB")`), L1328-1338 | Off |
| `NOIOB55` | Flag | NOIOB threshold at 5.5 mmol/L (99 mg/dL) instead of 4.5 (81 mg/dL) | L1181 (`prefsText.contains("NOIOB55")`), L1181 (`noIobBg = 99.0 else 81.0`) | Off |
| `SMART` | Flag | Smart calculator: meal bolus at low glucose waits for rise (checks at 10, 15, then every min) | L1179 (`sim && prefsText.contains("SMART")`), L1200-1219 | Off |
| `SMART_ADJ` | Flag | Smart dose: -20% on weak rise (<2 mg/dL/5min), +10% on sharp rise (>6) | L1180 (`prefsText.contains("SMART_ADJ")`), L1213-1214 | Off |
| `SMART_LOW20` | Flag | Smart dose multiplier 0.8 when firing | L1212 (`prefsText.contains("SMART_LOW20")`) | 1.0 |
| `SMART6` | Flag | Smart fires at 108 mg/dL (6 mmol/L) instead of 99 (5.5) | L1210 (`prefsText.contains("SMART6")`) | 99 mg/dL |
| `SMARTS` | Flag | Smart calculator in synthetic mode (same as SMART but for synthetic) | L1196 (`prefsText.contains("SMARTS")`), L1339-1355 | Off |
| `SMARTL` | Flag | Smart "food first" level learns: low after bolus raises threshold, high after delayed lowers | L1197 (`prefsText.contains("SMARTL")`), L1356-1363 | Off |
| `SPLIT_<pct>` | Double percent, e.g. `SPLIT_50` | Split bolus: pct% of meal part now, rest waits for seen rise | L1195 (`tok("SPLIT", 100.0)/100`), L1348-1354 | 100% (no split) |
| `SPLITMIN_<min>` | Integer minutes, e.g. `SPLITMIN_45` | Split second part checked from this minute | L1195 (`tok("SPLITMIN", 45.0).toLong()`), L1346 | 45 |
| `SPLITTYPE` | Flag | Family marks meal fast/slow (85% accurate); only slow meals split | L1196 (`prefsText.contains("SPLITTYPE")`), L1349-1350 | Off |
| `SPLITABS` | Flag | Split waits for SEEN absorption (deviation >6 mg/dL/5min x2) instead of glucose level | L1197 (`prefsText.contains("SPLITABS")`), L1347 | Off |
| `CHAIN_<pct>` | Double percent, e.g. `CHAIN_50` | Meal within 3h after hypo: only pct% meal part now, rest as split (seen rise, drop 120min) | L1198 (`tok("CHAIN", 0.0)/100`), L1342-1347 | 0 (disabled) |
| `LEARNCR_<k>` | Double, e.g. `LEARNCR_0.5` | Meal CR follows basal learner: CR = icP / (1 + k*(r-1)), clamped 0.75-1.25x | L1175 (`tok("LEARNCR", 0.0)`), L1260-1265 | 0 (disabled) |
| `LEARNCR2` | Flag | CR learns from last 7 days meals: lows>=25% -> weaker 7%, highs>=40% & lows<=10% -> stronger 7% | L1267 (`prefsText.contains("LEARNCR2")`), L1268-1284 | Off |
| `LEARNCRM` | Flag | CR learns per meal block (05-11, 11-17, 17-05) from last 7 days (>=4 meals/block) | L1199 (`prefsText.contains("LEARNCRM")`), L1286-1310 | Off |
| `LEARNCRM2` | Flag | LEARNCRM with refined rules: excludes post-hypo meals, high only 2-4h post-meal | L1199 (`prefsText.contains("LEARNCRM2")`), L1297-1300 | Off |
| `LEARNB` | Flag | Basal learner: hourly from clean fasting hours, median, step ±20%, every LBEVERY days | L1224 (`prefsText.contains("LEARNB")`), L1235-1255 | Off |
| `LBEVERY_<d>` | Integer days, e.g. `LBEVERY_4` | Basal card frequency in days | L1235 (`tok("LBEVERY", 4.0).toInt()`), L1493 | 4 |
| `LBDAYS_<d>` | Integer days, e.g. `LBDAYS_14` | Basal learner looks back this many days | L1235 (`tok("LBDAYS", 14.0).toInt()`), L1237 | 14 |
| `LBFILL` | Flag | Hours with no data follow measured hours' median ratio (half per step) | L1236 (`prefsText.contains("LBFILL")`), L1245-1246 | Off |
| `LBFILL2` | Flag | Hour with no data interpolates between nearest measured hours (not one ratio) | L1236 (`prefsText.contains("LBFILL2")`), L1247-1251 | Off |
| `LBMIN_<n>` | Integer, e.g. `LBMIN_2` | Clean hour windows needed to call hour "measured" (user 19.11: 2) | L1236 (`tok("LBMIN", 0.0).toInt()`), L1244 | 3 (or 4 with LEARNB5) |
| `LBTAIL` | Flag | Basal card uses meal tails: hour starts 3h+ after meal, COB=0, only if CR block ok | L1237 (`prefsText.contains("LBTAIL")`), L1238-1239 | Off |
| `LBTAILMIN_<n>` | Integer, e.g. `LBTAILMIN_5` | Tail hours need this many windows (default 5) | L1237 (`tok("LBTAILMIN", 5.0).toInt()`), L1238 | 5 |
| `LBLOWMAX_<n>` | Integer, e.g. `LBLOWMAX_0` | Raise blocked when >n readings <3.9 within ±2h of hour in last 7 days | L1237 (`tok("LBLOWMAX", 0.0).toInt()`), L1254-1259 | 0 |
| `LEARNB2` | Flag | Hour changes only when need >10% away on 2 cards same side; 7-day shift detector | L1236 (`prefsText.contains("LEARNB2")`), L1238-1242 | Off |
| `LEARNB3` | Flag | Asymmetric: lower after 2 cards -10%, raise after 3 cards +15% & no lows near hour | L1236 (`prefsText.contains("LEARNB3")`), L1252-1259 | Off |
| `LEARNB5` | Flag | Symmetric ±10% on 2 cards; raise blocked by lows near hour; half-step to estimate | L1236 (`prefsText.contains("LEARNB5")`), L1252-1259 | Off |
| `LBTREND` | Flag | Weekly trend card: TDD+10% & >10% x1.5 -> basal+10%; TDD-10% & hypo carbs x1.3 -> -10% | L1236 (`prefsText.contains("LBTREND")`), L1228-1233 | Off |
| `LBTREND2` | Flag | Trend base window never reaches back over last trend card; same direction 2 checks | L1234 (`prefsText.contains("LBTREND2")`), L1229-1232 | Off |
| `LBFAST` | Flag | When last 2 basal cards moved same way, next card after 2 days not LBEVERY | L1193 (`prefsText.contains("LBFAST")`), L1494-1496 | Off |
| `LEARNDURA` | Flag | Dura card every LBEVERY days: flat highs>=2% & lows<=3% -> dura+0.25; lows>4% -> -0.25 | L1193 (`prefsText.contains("LEARNDURA")`), L1292-1305 | Off |
| `LEARNALL` | Flag | One card per LAGAP days: ISF/CR/basal/dura/pct with causal filtering | L1192 (`prefsText.contains("LEARNALL")`), L1307-1375 | Off |
| `LAGAP_<d>` | Integer days, e.g. `LAGAP_3` | Days between LEARNALL cards | L1192 (`tok("LAGAP", 3.0).toInt()`), L1307 | 3 |
| `LAISF` | Flag | LEARNALL includes ISF learning | L1311 (`prefsText.contains("LAISF")`), L1312-1361 | Off |
| `LACR` | Flag | LEARNALL includes CR learning | L1311 (`prefsText.contains("LACR")`), L1362-1395 | Off |
| `LAPROTECT` | Flag | All-cause interlock: blocks insulin increases if protection lows >3% or <3 valid days | L1192 (`prefsText.contains("LAPROTECT")`), L1309, L1377-1385 | Off |
| `LACAUSE` | Flag | Causal filtering for learners: excludes ambiguous/contaminated rows | L1191 (`replayTokens.contains("LACAUSE")`), L1220-1227 | Off |
| `LAPCTBACK` | Flag | Percentage restore: after lows<=2% for 2 checks, pct+10% (max 130%) | L1193 (`prefsText.contains("LAPCTBACK")`), L1386-1390 | Off |
| `LADAYS` | Flag | Require 3 distinct days of evidence for learner proposals | L1194 (`prefsText.contains("LADAYS")`), L1222, L1378 | Off |
| `PCTCARD` | Flag | Daily percentage card at 06:00: highs>8% & lows<=3% & (TDD+15% or highs>12%) -> +10%; lows>4% -> -10% | L1194 (`prefsText.contains("PCTCARD")`), L1397-1418 | Off |
| `PCTHYS` | Flag | Pct down only after lows>4% at 2 checks in a row (or >6% once) | L1194 (`prefsText.contains("PCTHYS")`), L1411 | Off |
| `PCTSTRICT` | Flag | Pct up only with lows<=2% & hypo carbs not above 14-day baseline | L1195 (`prefsText.contains("PCTSTRICT")`), L1412-1413 | Off |
| `PCTSTEP_<pct>` | Double percent, e.g. `PCTSTEP_10` | Percentage card step size | L1195 (`tok("PCTSTEP", 10.0)/100`), L1411 | 10% |
| `PCTMAX_<pct>` | Double percent, e.g. `PCTMAX_130` | Highest percentage (default 130); PCTMAX_100 = only lowers, returns to 100% | L1195 (`tok("PCTMAX", 130.0)/100`), L1413 | 130% |
| `NOISEGUARD` | Flag | Noisy sensor (mean jump > NGTHR in 15 min): no SMB, temp basal <= profile | L1196 (`prefsText.contains("NOISEGUARD")`), L1415-1417 | Off |
| `NGTHR_<mg/dL>` | Double, e.g. `NGTHR_4` | Noise guard threshold (mean 2nd derivative in 15 min) | L1196 (`tok("NGTHR", 4.0)`), L1416 | 4 |
| `OUTDAY_<day>_<factor>` | Day (1-based) + factor, e.g. `OUTDAY_4_1.3` | One whole-day multiplier on basal need (stress test) | L94-107 (`parseSyntheticStressTokens`), L1107 (`stressTokens.outlierDay`), L1246 | None |
| `GAP_<min>` | 15, 30, or 60, e.g. `GAP_15` | Removes all sensor samples for duration on day 4 at 02:00; following hour marked recovery | L109-112, L1108-1115 (`gapStart`, `isSensorGapActive`) | None (requires LACAUSE) |
| `WACC_<x>` | Double, e.g. `WACC_0.15` | AutoISF weight: acceleration (acce) | L1078 (`tok("WACC", 0.1)`) | 0.1 |
| `WBRK_<x>` | Double, e.g. `WBRK_0.25` | AutoISF weight: brake | L1078 (`tok("WBRK", 0.20)`) | 0.20 |
| `WLOW_<x>` | Double, e.g. `WLOW_0.2` | AutoISF weight: lower range | L1078 (`tok("WLOW", 0.15)`) | 0.15 |
| `WHIGH_<x>` | Double, e.g. `WHIGH_0.15` | AutoISF weight: higher range | L1078 (`tok("WHIGH", 0.1)`) | 0.1 |
| `WPP_<x>` | Double, e.g. `WPP_0.02` | AutoISF weight: post-prandial | L1078 (`tok("WPP", 0.01)`) | 0.01 |
| `WDUR_<x>` | Double, e.g. `WDUR_0.7` | AutoISF weight: duration | L1078 (`tok("WDUR", 0.5)`) | 0.5 |
| `WMIN_<x>` | Double, e.g. `WMIN_0.4` | AutoISF min factor | L1078 (`tok("WMIN", 0.3)`) | 0.3 |
| `WMAX_<x>` | Double, e.g. `WMAX_1.3` | AutoISF max factor | L1078 (`tok("WMAX", 1.2)`) | 1.2 |
| `NEED_<pct>` | Integer percent, e.g. `NEED_20` | Body needs pct% more basal insulin (lower sensitivity); glucose drifts up | L1176 (`tok("NEED", 0.0)`), L1224-1225 | 0 |
| `CARBS_<pct>` | Integer percent, e.g. `CARBS_30` | Every meal >12g is pct% bigger; parent enters it and boluses for it with profile CR | L1177 (`tok("CARBS", 0.0)`), L1226-1237 | 0 |
| `LOOP_MIN_<n>` | Integer minutes, e.g. `LOOP_MIN_5` | Loop runs every n minutes (AAPS: 5); extra cycles copy previous inputs | L1073 (`Regex("LOOP_MIN_([0-9]+)")`), L1074 | 5 (from input) |
| `AISF_LIVE` | Flag | AutoISF 3.2.1 works out ISF factor from model glucose (else: factor AAPS logged) | L1075 (`prefsText.contains("AISF_LIVE")`), L1080 | Off |
| `UKF` | Flag | Loop sees UKF-smoothed glucose on 5-min data (family sees raw) | L1109 (`prefsText.contains("UKF")`) | Off |
| `LEARNG` | Flag | Learner learns guard window (from 0) and temp basal cap (from 2x) from rebound outcomes | L1112 (`prefsText.contains("LEARNG")`), L1113-1123 | Off |
| `LEARNG2` | Flag | Learner "what-if": low counts only if without extra insulin lowest would stay >=75 | L1115 (`prefsText.contains("LEARNG2")`), L1116-1123 | Off |
| `LEARNG3` | Flag | LEARNG2 + guard releases early when glucose > treatment+expected+margin & rising | L1116 (`prefsText.contains("LEARNG3")`), L1117-1123 | Off |
| `CRAUDIT` | Flag | Counting-only CR/ISF audit; no parameter writes | L1191 (`replayTokens.contains("CRAUDIT")`), L1520-1522 | Off |
| `WATTR` | Flag | Weight attribution logging (requires LACAUSE) | L1191 (`replayTokens.contains("WATTR")`), L1192 | Off |

---

# TABLE 2: Hard days a real child has — mapping to tokens or MISSING

| # | Hard day | Existing tokens (with values) that model it | Missing? New token spec |
|---|----------|---------------------------------------------|--------------------------|
| 1 | **Illness: fever = insulin resistance** | `ILL_10 ILLD_3 ILLF_0.7` (insulin acts 0.7x for 3 days from day 10) | — |
| 2 | **Illness: stomach bug = low eating / vomiting** | `UNLOG_50 MEALX_0.5` (50% meals unlogged, logged meals half size) + `CARBS_-30` (meals 30% smaller) | MISSING: `VOMIT_<pct>` — at meal time, with probability pct%, the eaten carbs become 0 (vomited) but bolus already given. Effect: `eaten = 0` for that meal, `logged` unchanged. Variable: `Meal.eaten`. When: at meal timestamp. |
| 3 | **Growth spurt (insulin need rises ~10-20%/week)** | `DRIFT_15` (15%/week increase in insulin need) | — |
| 4 | **Sport day (high activity, insulin sensitivity up)** | `ACT_7` (from day 7, every 7th day: insulin 1.3x 10-22h, 1.2x night) | MISSING: `SPORT_<day>_<dur>_<sens>` — single sport day: on `day`, for `dur` hours, insulin sensitivity multiplier `sens` (e.g. 1.5). Effect: multiply `actF` by `sens` for those hours. Variable: `actF` in synthetic loop. When: specific day + hour range. |
| 5 | **School trip (irregular meals, more activity, stress)** | `ACT_7 UNLOG_20 MEALX_1.2 DAYNEED_1.2` (activity day + 20% unlogged + meals 20% bigger + day basal need +20%) | MISSING: `SCHOOLTRIP_<day>_<days>` — for `days` days from `day`: `UNLOG_30 MEALX_1.3 DAYNEED_1.3 ACT_<day>` combined. Single token for convenience. |
| 6 | **Party with unlogged sweets (extra carbs, no bolus)** | `UNLOG_100 MEALX_2.0` on one day (but UNLOG is global %) | MISSING: `PARTY_<day>_<extraCarbs>` — on `day`, one extra meal at ~18h with `extraCarbs` grams, unlogged (no bolus). Effect: add `Meal` with `logged=0, eaten=extraCarbs`. Variable: `meals` list. When: specific day, hour 18. |
| 7 | **Missed meal bolus (ate, forgot bolus)** | `UNLOG_100` for one meal (but UNLOG is global %) | MISSING: `MISSEDBOLUS_<day>_<mealIdx>` — on `day`, meal index `mealIdx` (0-based in day's meals): `logged=0` but `eaten` unchanged. Effect: modify that `Meal.logged = 0`. When: at that meal's timestamp. |
| 8 | **Double meal bolus (bolused twice)** | None | MISSING: `DOUBLEBOLUS_<day>_<mealIdx>` — on `day`, meal `mealIdx`: wizard bolus given twice (two `giveBolus` calls). Effect: duplicate bolus in `chain.manual` and `doses`. When: at meal timestamp. |
| 9 | **Late meal bolus (bolused 30-60 min after eating)** | `LATE_BOLUS_30` in replay (moves bolus later); in synthetic: `SMARTS` with low glucose delays | MISSING: `LATEBOLUS_<day>_<mealIdx>_<min>` — on `day`, meal `mealIdx`: bolus delayed by `min` minutes. Effect: `Pending` with `next = t + min*60000`. When: at meal timestamp. |
| 10 | **Cannula failure / occlusion (insulin not delivered)** | `CANFAIL_7` (every 7 days, 14-20h insulin 0.5x) | MISSING: `OCCLUSION_<day>_<startH>_<durH>` — on `day`, from `startH` for `durH` hours: insulin action multiplier 0.0 (no delivery). Effect: `canF = 0.0` for those hours. Variable: `canF` in synthetic loop. When: specific day + hour range. |
| 11 | **Old cannula after 72h (insulin action degrades)** | `CANCH_96` (cannula changed every 96h; after 60h action drops to 75% at 96h) | — |
| 12 | **Sensor first-day false lows (first 24h reads low)** | None (sensor model has no first-day behavior) | MISSING: `SENSORFIRSTDAY_<day>_<bias>` — on `day` (first sensor day), all readings `- bias` mg/dL (e.g. -20). Effect: `reading -= bias` for that day. Variable: `reading` in synthetic loop. When: day 0 of sensor (every `CANCH` hours? unclear in code). |
| 13 | **Sensor compression lows at night (pressure on sensor)** | `COMPRESS_3` (3 false night lows/week, 0-6h, -40 mg/dL for 20 min) | — |
| 14 | **Sensor gap (no data for 15-60 min)** | `GAP_15` / `GAP_30` / `GAP_60` (day 4 at 02:00, removes samples for duration) | — |
| 15 | **Calibration error (sensor offset ±20 mg/dL)** | None | MISSING: `CALIBRATION_<day>_<offset>` — on `day`, after calibration event (modeled as fixed time), all readings `+ offset` mg/dL. Effect: `reading += offset` from that timestamp. Variable: `reading`. When: specific timestamp. |
| 16 | **Hypo treated twice (rebound high from over-treatment)** | `TREAT_12` (12g treatment) + `CORR_50` (50% chance correction on rebound) + `GUARD_0` (no guard) | MISSING: `DOUBLETREAT_<day>_<time>` — at `time` on `day`, two treatments 15 min apart (e.g. 6g + 6g). Effect: two `eatenList.add(Meal(...))` calls 15 min apart. When: specific timestamp. |
| 17 | **Hot weather (faster insulin absorption)** | None | MISSING: `HOTWEATHER_<day>_<peakMult>_<diaMult>` — on `day`, insulin peak time * `peakMult`, DIA * `diaMult` (e.g. 0.8, 0.9). Effect: modify `peak` and `dia` for `iobCurve` and `Body.curve`. Variables: `peak`, `dia` in `Body` and `Chain`. When: whole day. |
| 18 | **Gluten-free diet start (changed carb absorption — slower)** | None | MISSING: `DIETCHANGE_<day>_<aMult>_<bMult>` — from `day` onward, meal absorption `a` and `b` (triangle params) multiplied. Effect: `Meal.a *= aMult, Meal.b *= bMult` for meals on/after day. Variables: `Meal.a`, `Meal.b`. When: from day onward. |
| 19 | **Dawn phenomenon (liver output 4-8am)** | `DAWN_1.5` (liver output 1.5x 04-08h) | — |
| 20 | **Pump clock wrong (basal schedule shifted)** | None | MISSING: `CLOCKDRIFT_<min>` — all basal schedule times shifted by `min` minutes (profile basal array indexed by wrong hour). Effect: `hourOf(t)` returns `(t + min*60000) / 3600000 % 24`. Variable: `hourOf` function / `profBasal[hourOf(t)]`. When: whole run. |
| 21 | **Many small snacks (grazing, COB never zero)** | `MEALX_0.5` + high meal frequency (but slots fixed in encrypted input) | MISSING: `SNACKS_<day>_<perHour>_<g>` — on `day`, add `perHour` snacks of `g` grams each hour (logged & bolused). Effect: insert extra `Meal` entries every 60/perHour minutes. Variable: `meals` list. When: that day. |
| 22 | **Post-hypo insulin resistance (transient)** | None | MISSING: `POSTHYPORESIST_<day>_<hours>_<factor>` — after each hypo treatment on `day`, for `hours` hours, insulin sensitivity * `factor` (e.g. 0.8). Effect: multiply `isfT` by `factor` for that window. Variable: `isfT` in synthetic loop. When: after each hypo treatment. |
| 23 | **Menstrual cycle (insulin resistance week before)** | None | MISSING: `MENSTRUAL_<startDay>_<lenDays>_<factor>` — from `startDay` for `lenDays`, basal need * `factor` (e.g. 1.2). Effect: multiply `needBasal[h]` by `factor` for those days. Variable: `needBasal` / `needNow`. When: day range. |
| 24 | **Stress/exams (cortisol-driven highs)** | `DAYNEED_1.3` + `DAWN_1.3` (but not cyclic) | MISSING: `STRESS_<day>_<days>_<factor>` — for `days` from `day`, basal need * `factor` (e.g. 1.25) all day. Effect: multiply `needNow` by `factor`. Variable: `needNow`. When: day range. |

---

# TABLE 3: Wrong learner reaction per hard day (negative test)

| # | Hard day | Wrong learner reaction (negative test) |
|---|----------|----------------------------------------|
| 1 | Fever illness | Lowers basal / raises ISF / strengthens CR because highs are from resistance, not too little insulin |
| 2 | Stomach bug (vomiting) | Strengthens CR (thinks carbs weren't absorbed) or lowers basal (thinks lows from too much basal) |
| 3 | Growth spurt | Lowers ISF / raises CR / lowers basal because rising trend looks like settings too strong |
| 4 | Sport day | Raises basal / weakens ISF / strengthens CR because lows during activity look like too much insulin |
| 5 | School trip | Raises basal (for stress highs) then crashes at night; or strengthens CR for unlogged snacks |
| 6 | Party with unlogged sweets | Strengthens CR (sees highs after meal) or raises basal (sees sustained highs) |
| 7 | Missed meal bolus | Strengthens CR (high after meal) or raises basal (prolonged high) |
| 8 | Double meal bolus | Weakens CR (low after meal) or lowers basal (rebound low) |
| 9 | Late meal bolus | Weakens CR (low then high) or raises basal (late high) |
| 10 | Cannula occlusion | Raises basal / weakens ISF / strengthens CR (persistent highs from no delivery) |
| 11 | Old cannula >72h | Raises basal / weakens ISF (gradual highs from degrading delivery) |
| 12 | Sensor first-day false lows | Lowers basal / raises ISF / weakens CR (false lows look like too much insulin) |
| 13 | Sensor compression lows | Lowers basal / raises ISF (night lows from pressure, not insulin) |
| 14 | Sensor gap | Any change based on interpolated/missing data (should skip learning) |
| 15 | Calibration error (offset) | Systematic basal/ISF/CR shift matching the offset direction |
| 16 | Hypo treated twice | Lowers basal / raises ISF / weakens CR (rebound high from over-treatment looks like settings too strong) |
| 17 | Hot weather (faster absorption) | Weakens ISF / strengthens CR / lowers basal (lows from faster peak) |
| 18 | Gluten-free diet (slower absorption) | Strengthens CR / raises basal (late highs from slow carbs) |
| 19 | Dawn phenomenon | Raises basal 3-5am (correct) but may over-raise if not time-limited |
| 20 | Pump clock wrong | Shifts basal schedule wrong direction (e.g. raises basal at wrong hours) |
| 21 | Many small snacks | Strengthens CR (COB never zero, highs attributed to CR) or raises basal |
| 22 | Post-hypo resistance | Lowers basal / raises ISF (transient highs after low) |
| 23 | Menstrual cycle | Raises basal for whole week (correct) but may not lower back after |
| 24 | Stress/exams | Raises basal persistently (correct) but may not lower after stress ends |

---

# TABLE 4: Stress ladder — 5 levels, ready token lists (EXISTING tokens only)

Base run: `SYNTH_28 SEED_1 LACAUSE` (28 days, fixed seed, causal filtering for learners)

| Level | Name | Token list to append to base |
|-------|------|------------------------------|
| 1 | **Calm / Usual** | `PROFX_1.0 DAWN_1.1 ACT_14 CANCH_96 COMPRESS_1` |
| 2 | **Usual variability** | `PROFX_1.1 DAWN_1.2 ACT_10 CANCH_96 COMPRESS_2 MEALX_1.1 UNLOG_5 DRIFT_5` |
| 3 | **Hard** | `ILL_14 ILLD_3 ILLF_0.7 DRIFT_10 MEALX_1.3 UNLOG_15 CHGISF_1.2 CHG_21` |
| 4 | **Aggressive** | `CHGISF_0.65 CHG_14 MEALX_1.6 UNLOG_30 ILL_10 ILLD_4 ILLF_0.6 NOISE_0.5 COMPRESS_4 CANFAIL_10` |
| 5 | **Storm (all at once)** | `CHGISF_0.65 CHG_7 MEALX_1.6 UNLOG_30 ILL_7 ILLD_5 ILLF_0.6 NOISE_0.7 COMPRESS_6 CANFAIL_7 DRIFT_15 ACT_5 DAWN_1.5 DAYNEED_1.3 PROFX_1.3 CANCH_72` |

**Notes:**
- All tokens exist in the code (Table 1).
- `CHG_<day>` sets when `CHGISF`/`CHGKCR` take effect (day 1-based).
- `ACT_<day>` starts recurring activity days from that day (0-based).
- `ILL_<day>` starts illness from that day (0-based).
- `CANCH_<h>`: lower = more frequent changes; 72h means degradation starts at 60h (12h of degraded action per cycle).
- `PROFX_>1.0` makes profile basal higher than true need (algorithm must learn down).
- `CHGISF_<1.0` means insulin becomes stronger (algorithm must lower basal/raise ISF).
- `CHGISF_>1.0` means insulin becomes weaker (algorithm must raise basal/lower ISF).
- Level 5 combines: growth spurt (`DRIFT_15`), illness (`ILL_7`), sensor noise (`NOISE_0.7`), compression lows (`COMPRESS_6`), cannula failures (`CANFAIL_7`), activity (`ACT_5`), dawn (`DAWN_1.5`), day need (`DAYNEED_1.3`), profile off (`PROFX_1.3`), ISF change (`CHGISF_0.65` at day 7), meals bigger (`MEALX_1.6`), unlogged (`UNLOG_30`), old cannula (`CANCH_72`).