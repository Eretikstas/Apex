# Learning audit and interaction tests

Purpose: make every learning decision explainable before comparing only glucose outcomes.
This is laboratory instrumentation and replay validation only. It must not change live AAPS settings.

## Learning Audit log contract

At each LEARNALL check, emit one compact, deterministic audit line per card. Keep values aggregate-only; never print timestamps, glucose traces, meal amounts, or personal identifiers.

Required fields for each card:
- card name and observation-window length;
- candidate windows/episodes;
- accepted windows/episodes;
- rejected windows grouped by one mutually exclusive primary reason;
- distinct days represented by accepted data;
- minimum-data gate and whether it passed;
- parameter before, parameter after, and delta (or "unchanged");
- decision: changed / no-change / insufficient-data.

### CR audit
For each time block report:
- eligible meal episodes;
- accepted episodes;
- rejected: short/incomplete observation window, sensor gap, old cannula, activity/illness/false-low, prior hypo/rebound, prior SMB, any post-meal SMB, missing meal bolus, ambiguous low;
- accepted distinct days;
- low and high episode counts and verdict.

Preserve the current conservative rule: any SMB in the complete six-hour post-meal observation window rejects the whole meal episode, for both lows and highs. Do not silently narrow this to two hours or only SMB-after-low. If this leaves fewer than five episodes, report insufficient data; any alternative filter needs separate user approval.

### ISF audit
For correction candidates (>180 mg/dL / 10 mmol/L), report:
- accepted corrections and distinct days;
- rejected: sensor gap/old cannula/missing BG, hypo-treatment or rebound chain, overlapping manual/SMB bolus, meal/COB, rising glucose before correction, insufficient net insulin exposure;
- median observed ISF and minimum-data verdict.

The correction event itself may be manual or SMB. Do not reject the event bolus at the event index; reject competing boluses in the surrounding observation window.

### Basal audit
For candidate hourly windows report:
- candidate, accepted, rejected by primary reason;
- distinct days and hours with enough samples;
- rejected: meal contamination, hypo/rebound, old cannula, sensor gap, activity, illness, false low, incomplete BG window, non-hour-aligned start, invalid BG range, insufficient clean days;
- per-hour sample count and resulting basal delta.

Do not let a rejected window silently disappear. Keep the current safety rule that meal-tail windows are not used with LACAUSE until a reliable row-level link to a clean CR block exists. Do not re-enable LBTAIL under causal filtering without a test proving that link.

### Percentage and Dura audit
Report eligible low count/rate versus total low count/rate, exclusions by cause, distinct days, hypo-treatment carbohydrate totals (aggregate only), parameter before/after, and why the card did or did not act.

## Proposed learner robustness mechanisms — for Claude review only

These are design proposals, not approved learner behavior. First assess feasibility, interactions with existing cards, testability, and risk. Do not implement or alter formulas until the user reviews Claude's assessment.

### 1. Confidence score
Calculate a per-card evidence-quality score from independent clean days, episode count, dispersion/consistency, missing-data rate, and exclusion rate. Show the component values and a categorical confidence (low / medium / high); do not let a single opaque score hide a failed minimum-data gate. Confidence must never override hard eligibility or safety gates. Initially report-only in replay.

### 2. Drift detection
Distinguish isolated outliers, temporary shifts, and persistent directional changes. Require repeated evidence across independent days and compare recent and baseline windows. Report detected direction, magnitude, persistence, and evidence count. Do not automatically adapt parameters in the first implementation.

### 3. Outlier protection
Use robust summaries (e.g. median and a documented robust spread measure) and sensitivity tests with/without the most influential day. A single unusual day must not dominate a learning block. Do not discard clinically meaningful repeated events merely because they are extreme.

### 4. Parameter interaction guard
Detect when CR, ISF, Basal, Percentage, or Dura evidence overlaps or could explain the same glucose excursion. Emit a conflict record identifying evidence overlap, competing card proposals, the reason one is deferred, and unresolved ambiguity. Do not invent a global priority order before replay evidence and user review. Never let one parameter silently compensate for another card's contaminated evidence.

### 5. Recovery detection and data ageing
Detect return toward the individual's stable baseline after a temporary disturbance. Compare recent clean evidence with older evidence using a documented time-decay rule, while preserving minimum sample/day gates. Report when old evidence is down-weighted and why. No automatic reset or parameter rollback.

### 6. Learning explainability
For every proposed change, produce a concise deterministic explanation: evidence used, evidence excluded, minimum-data gate, confidence components, interaction conflicts, proposed delta, and reason for change/no-change. Aggregate-only output; no personal traces.

### 7. Shadow comparison
Run APP baseline, current learner, and experimental report-only mechanisms on identical replay inputs and seeds. Keep the new mechanisms observational until separately approved. Compare audit determinism, parameter proposals, and glucose safety outcomes; never apply experimental values to live AAPS.

## Interaction / conflict replay matrix

Run identical inputs and seeds with APP baseline and LEARNALL. For each scenario record all card decisions and audit lines.

1. **Single outlier day:** one isolated high-need or low-need day among otherwise stable days. Expected: no abrupt parameter change based on that day alone.
2. **Persistent change:** same direction across at least 5–7 days. Expected: adaptation only after the required clean-data and distinct-day gates pass.
3. **CR vs ISF conflict:** meal episodes suggest stronger CR while clean correction episodes suggest weaker ISF. Expected: one card cannot silently absorb the other card's evidence; record the selected priority and the deferred card.
4. **CR vs basal conflict:** post-meal highs with valid meal bolus and later basal drift. Expected: CR evidence is isolated from fasting basal evidence; no basal increase solely to compensate for a meal-ratio problem.
5. **SMB low and rebound:** lows after SMB and after hypo treatment. Expected: no CR/ISF/basal learning from contaminated episodes; percentage protection follows only its explicit eligibility rule.
6. **Exercise / illness:** marked activity or illness windows. Expected: excluded from causal learning and counted by reason.
7. **Sensor gaps:** 15, 30, and 60 minute gaps plus recovery. Expected: gap and recovery transition windows excluded; no parameter update from missing or interpolated data.
8. **Cannula degradation:** old cannula and failure windows. Expected: excluded from CR/ISF/basal learning; no compensation learning.
9. **Repeated identical replay:** same input, tokens, and seed twice. Expected: identical audit counts, card decisions, and parameter deltas.
10. **Missing-data floor:** fewer than five clean CR/ISF episodes or fewer than three distinct days where required. Expected: explicit insufficient-data result, no parameter update.

## Acceptance gates

- Audit totals reconcile: candidates = accepted + rejected (for each card and block).
- Rejection reasons are mutually exclusive and deterministic.
- Every card reports distinct days and minimum-data gate.
- Same seed/input produces byte-for-byte identical audit summaries.
- No new learner rule or parameter formula is introduced by this instrumentation.
- No batch is run by ChatGPT. Claude verifies compile and executes the approved replay after the user asks.
- No changes to app branch, live profile, pump settings, or Nightscout.
