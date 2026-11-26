#!/usr/bin/env python3
"""Compare real and virtual 5-minute CGM/loop CSVs; requires Python 3 and numpy only.

time_min = minutes since local midnight on day 1, continuous across days.
insulin_u = total insulin delivered in that 5-minute bin (basal + bolus + SMB).
BG comparisons use bg_sensor_mgdl on both sides; bg_true_mgdl may be blank.
Meal-bolus context is a documented proxy because insulin_u does not separate bolus from basal.
"""
import argparse
import csv
import math
from pathlib import Path
import numpy as np

# Absolute distance thresholds. W1 is used for distributions; scalar metrics use abs difference.
THRESHOLDS = {
    "hourly mean BG (mg/dL)": 10.0,
    "hourly BG SD (mg/dL)": 10.0,
    "CV (percentage points)": 5.0,
    "6-hour time-in-range (percentage points)": 5.0,
    "night slope (mg/dL/hour)": 2.0,
    "meal rise (mg/dL)": 20.0,
    "meal time-to-peak (min)": 30.0,
    "meal BG at 3 h (mg/dL)": 15.0,
    "rate of change (mg/dL/hour)": 1.5,
    "5-minute BG drop (mg/dL)": 5.0,
    "low episodes/day": 0.5,
    "low duration (min)": 15.0,
    "low nadir (mg/dL)": 10.0,
    "low context (percentage points)": 20.0,
    "BG autocorrelation": 0.15,
}
COLS = ("time_min", "bg_true_mgdl", "bg_sensor_mgdl", "insulin_u", "carbs_g", "hypo_treat_g")


def _num(v):
    if v is None or not str(v).strip():
        return math.nan
    try:
        x = float(v)
        return x if math.isfinite(x) else math.nan
    except ValueError:
        return math.nan


def load_csv(path):
    with open(path, newline="", encoding="utf-8-sig") as f:
        reader = csv.DictReader(f)
        missing = set(COLS) - set(reader.fieldnames or ())
        if missing:
            raise ValueError(f"{path}: missing columns {sorted(missing)}")
        rows = list(reader)
    if not rows:
        raise ValueError(f"{path}: no rows")
    d = {k: np.asarray([_num(r.get(k)) for r in rows], dtype=float) for k in COLS}
    if not np.isfinite(d["time_min"]).all() or np.any(np.diff(d["time_min"]) <= 0):
        raise ValueError(f"{path}: time_min must be finite and strictly increasing")
    if not np.isfinite(d["bg_sensor_mgdl"]).any():
        raise ValueError(f"{path}: no valid bg_sensor_mgdl")
    return d


def _bg(d):
    m = np.isfinite(d["bg_sensor_mgdl"])
    return d["time_min"][m], d["bg_sensor_mgdl"][m]


def _finite(x):
    return np.asarray(x, dtype=float)[np.isfinite(x)]


def w1(a, b):
    x, y = np.sort(_finite(a)), np.sort(_finite(b))
    if not len(x) and not len(y):
        return 0.0
    if not len(x) or not len(y):
        return math.inf
    z = np.sort(np.concatenate((x, y)))
    if len(z) < 2:
        return abs(float(x[0] - y[0]))
    mid = (z[:-1] + z[1:]) / 2
    return float(np.sum(np.abs(np.searchsorted(x, mid, side="right") / len(x) -
                               np.searchsorted(y, mid, side="right") / len(y)) * np.diff(z)))


def _dist(a, b):
    if math.isnan(a) and math.isnan(b):
        return 0.0
    if not math.isfinite(a) or not math.isfinite(b):
        return math.inf
    return abs(a - b)


def _fmt(x, n=1):
    if math.isnan(float(x)):
        return "n/a"
    if math.isinf(float(x)):
        return "inf"
    return f"{float(x):.{n}f}"


def _arr(x, n=1):
    return "[" + ", ".join(_fmt(v, n) for v in x) + "]"


def _line(feature, real, virtual, distance, threshold, kind="abs diff"):
    verdict = "SIMILAR" if math.isfinite(distance) and distance <= threshold else "DIFFERENT"
    return f"| {feature} | {real} | {virtual} | {_fmt(distance)} ({kind}) | {_fmt(threshold)} | **{verdict}** |"


def _hour_stats(d):
    t, bg = _bg(d)
    h = (np.floor(t).astype(int) % 1440) // 60
    mean, sd = np.full(24, np.nan), np.full(24, np.nan)
    for k in range(24):
        v = bg[h == k]
        if len(v):
            mean[k], sd[k] = np.mean(v), np.std(v)
    return mean, sd


def _hourly_mae(a, b):
    """Mean absolute difference at matching clock hours; missing hours cannot be compared."""
    x, y = np.asarray(a, dtype=float), np.asarray(b, dtype=float)
    if x.shape != y.shape or not np.isfinite(x).all() or not np.isfinite(y).all():
        return math.inf
    return float(np.mean(np.abs(x - y)))


def _blocks(d):
    t, bg = _bg(d)
    b = (np.floor(t).astype(int) % 1440) // 360
    out = {}
    for name, fn in (("<70", lambda x: x < 70), ("<54", lambda x: x < 54), (">180", lambda x: x > 180)):
        a = np.full(4, np.nan)
        for k in range(4):
            v = bg[b == k]
            if len(v):
                a[k] = 100 * np.mean(fn(v))
        out[name] = a
    return out


def _night_slope(d):
    t, bg = _bg(d)
    day = np.floor(t / 1440).astype(int)
    hour = (np.floor(t).astype(int) % 1440) / 60
    slopes = []
    for k in np.unique(day):
        m = (day == k) & (hour < 6)
        if np.count_nonzero(m) >= 3:
            slopes.append(float(np.polyfit(t[m] / 60, bg[m], 1)[0]))
    return float(np.mean(slopes)) if slopes else math.nan


def _meals(d):
    t, bg = _bg(d)
    ids = np.flatnonzero(np.isfinite(d["carbs_g"]) & (d["carbs_g"] >= 10))
    rise, peak_min, bg3 = [], [], []
    for i in ids:
        mt = d["time_min"][i]
        base = bg[(t >= mt - 30) & (t <= mt + 5)]
        win = (t >= mt) & (t <= mt + 180)
        if not len(base) or not np.any(win):
            continue
        wt, wb = t[win], bg[win]
        p = int(np.argmax(wb))
        rise.append(float(wb[p] - base[0]))
        peak_min.append(float(wt[p] - mt))
        later = bg[(t >= mt + 175) & (t <= mt + 185)]
        if len(later):
            bg3.append(float(later[0]))
    return {"rise": np.asarray(rise), "peak": np.asarray(peak_min), "bg3": np.asarray(bg3)}


def _rates(d):
    t, bg = _bg(d)
    dt, drop = np.diff(t), np.diff(bg)
    m = np.isclose(dt, 5.0, atol=0.25)
    return drop[m] / dt[m] * 60, drop[m]


def _bolus_proxy(d):
    t, ins = d["time_min"], d["insulin_u"]
    out = np.zeros(len(t), dtype=bool)
    for i, ti in enumerate(t):
        local = ins[(t >= ti - 180) & (t <= ti + 180) & np.isfinite(ins)]
        base = float(np.percentile(local, 20)) if len(local) else 0.0
        out[i] = np.isfinite(ins[i]) and ins[i] > max(0.12, base * 2.5 + 0.04)
    return out


def _lows(d):
    t, bg = _bg(d)
    events, start = [], None
    for i, low in enumerate(bg < 70):
        if low and (start is None or t[i] - t[i - 1] > 5.25):
            if start is not None:
                events.append((start, i - 1))
            start = i
        elif not low and start is not None:
            events.append((start, i - 1))
            start = None
    if start is not None:
        events.append((start, len(bg) - 1))
    bolus, carbs, hypo = _bolus_proxy(d), d["carbs_g"], d["hypo_treat_g"]
    durations, nadirs, causes = [], [], []
    for a, b in events:
        ts = t[a]
        durations.append(float(t[b] - ts + 5))
        nadirs.append(float(np.min(bg[a:b + 1])))
        meal_bolus = False
        for j, tj in enumerate(d["time_min"]):
            if ts - 150 <= tj <= ts and bolus[j]:
                meal_before = np.isfinite(carbs) & (carbs >= 10) & (d["time_min"] >= tj - 150) & (d["time_min"] <= tj)
                if np.any(meal_before):
                    meal_bolus = True
                    break
        hypo_before = np.any((d["time_min"] >= ts - 180) & (d["time_min"] <= ts) &
                             np.isfinite(hypo) & (hypo > 0))
        causes.append(0 if meal_bolus else 1 if hypo_before else 2)
    days = max(1, len(np.unique(np.floor(t / 1440).astype(int))))
    pct = np.asarray([100 * causes.count(k) / len(causes) if causes else math.nan for k in range(3)])
    return {"count": len(events) / days, "duration": np.asarray(durations),
            "nadir": np.asarray(nadirs), "cause_pct": pct}


def _autocorr(d):
    t, bg = _bg(d)
    lookup = {round(float(x), 3): float(y) for x, y in zip(t, bg)}
    out = []
    for lag in (30, 60, 120):
        pairs = [(v, lookup[round(float(x + lag), 3)]) for x, v in zip(t, bg)
                 if round(float(x + lag), 3) in lookup]
        out.append(float(np.corrcoef(np.asarray(pairs).T)[0, 1]) if len(pairs) >= 3 else math.nan)
    return np.asarray(out)


def compare(real, virtual):
    rows = ["| Feature | Real | Virtual | Distance | Threshold | Verdict |",
            "|---|---:|---:|---:|---:|---|"]
    rm, rs = _hour_stats(real)
    vm, vs = _hour_stats(virtual)
    mean_distance = _hourly_mae(rm, vm)
    sd_distance = _hourly_mae(rs, vs)
    rows.append(_line("Hourly mean BG by hour 00–23, mg/dL", _arr(rm), _arr(vm),
                      mean_distance, THRESHOLDS["hourly mean BG (mg/dL)"], "mean absolute hour-by-hour difference"))
    rows.append(_line("Hourly BG SD by hour 00–23, mg/dL", _arr(rs), _arr(vs),
                      sd_distance, THRESHOLDS["hourly BG SD (mg/dL)"], "mean absolute hour-by-hour difference"))
    _, rbg = _bg(real); _, vbg = _bg(virtual)
    rcv = 100 * np.std(rbg) / np.mean(rbg)
    vcv = 100 * np.std(vbg) / np.mean(vbg)
    rows.append(_line("Overall CV, %", _fmt(rcv), _fmt(vcv), _dist(rcv, vcv),
                      THRESHOLDS["CV (percentage points)"]))
    rb, vb = _blocks(real), _blocks(virtual)
    for key in ("<70", "<54", ">180"):
        rows.append(_line(f"Time {key} by 6-hour block 00–06/06–12/12–18/18–24, %",
                          _arr(rb[key]), _arr(vb[key]), w1(rb[key], vb[key]),
                          THRESHOLDS["6-hour time-in-range (percentage points)"], "Wasserstein-1"))
    rn, vn = _night_slope(real), _night_slope(virtual)
    rows.append(_line("Night 00–06 mean BG slope, mg/dL/hour", _fmt(rn), _fmt(vn), _dist(rn, vn),
                      THRESHOLDS["night slope (mg/dL/hour)"]))
    rmeal, vmeal = _meals(real), _meals(virtual)
    for key, label in (("rise", "Meal rise to peak, mg/dL"),
                       ("peak", "Meal minutes to peak"), ("bg3", "Meal BG at 3 h, mg/dL")):
        a, b = _finite(rmeal[key]), _finite(vmeal[key])
        am = float(np.mean(a)) if len(a) else math.nan
        bm = float(np.mean(b)) if len(b) else math.nan
        rows.append(_line(label, f"n={len(a)}, mean={_fmt(am)}", f"n={len(b)}, mean={_fmt(bm)}",
                          w1(a, b), THRESHOLDS[{"rise": "meal rise (mg/dL)", "peak": "meal time-to-peak (min)",
                                                "bg3": "meal BG at 3 h (mg/dL)"}[key]], "Wasserstein-1"))
    rr, vr = _rates(real), _rates(virtual)
    for i, p in enumerate((5, 50, 95)):
        a = float(np.percentile(rr[0], p)) if len(rr[0]) else math.nan
        b = float(np.percentile(vr[0], p)) if len(vr[0]) else math.nan
        rows.append(_line(f"Rate of change P{p}, mg/dL/hour", _fmt(a), _fmt(b), _dist(a, b),
                          THRESHOLDS["rate of change (mg/dL/hour)"]))
    for p in (5, 50, 95):
        a = float(np.percentile(rr[1], p)) if len(rr[1]) else math.nan
        b = float(np.percentile(vr[1], p)) if len(vr[1]) else math.nan
        rows.append(_line(f"5-minute BG drop P{p}, mg/dL", _fmt(a), _fmt(b), _dist(a, b),
                          THRESHOLDS["5-minute BG drop (mg/dL)"]))
    rl, vl = _lows(real), _lows(virtual)
    rows.append(_line("Low episodes per day (<70)", _fmt(rl["count"]), _fmt(vl["count"]),
                      _dist(rl["count"], vl["count"]), THRESHOLDS["low episodes/day"]))
    for key, label, th in (("duration", "Low episode length, min", "low duration (min)"),
                           ("nadir", "Low episode nadir, mg/dL", "low nadir (mg/dL)")):
        a, b = _finite(rl[key]), _finite(vl[key])
        rows.append(_line(label, f"n={len(a)}, mean={_fmt(np.mean(a) if len(a) else math.nan)}",
                          f"n={len(b)}, mean={_fmt(np.mean(b) if len(b) else math.nan)}",
                          w1(a, b), THRESHOLDS[th], "Wasserstein-1"))
    rows.append(_line("Low context (%): meal-bolus proxy / hypo carbs / none",
                      _arr(rl["cause_pct"]), _arr(vl["cause_pct"]),
                      w1(rl["cause_pct"], vl["cause_pct"]),
                      THRESHOLDS["low context (percentage points)"], "Wasserstein-1"))
    ra, va = _autocorr(real), _autocorr(virtual)
    rows.append(_line("BG autocorrelation at 30 / 60 / 120 min", _arr(ra, 3), _arr(va, 3),
                      w1(ra, va), THRESHOLDS["BG autocorrelation"], "Wasserstein-1"))
    return "\n".join(rows)


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("real_csv", type=Path)
    p.add_argument("virtual_csv", type=Path)
    a = p.parse_args()
    print(compare(load_csv(a.real_csv), load_csv(a.virtual_csv)))


if __name__ == "__main__":
    main()
