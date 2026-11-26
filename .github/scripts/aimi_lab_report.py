#!/usr/bin/env python3
"""Write the public result of a lab run into lab/aimi-replay/results/, so ChatGPT can read it in the repository.

ChatGPT cannot see GitHub Actions. This script reads the run through the API and writes a short Markdown file.
Only public synthetic data is written: the summary table rows and the "AIMI audit" / learner lines that the
replay already prints in its public log. Encrypted results are never touched.

Usage:
  aimi_lab_report.py replay  <run id>   -> results/run-<run id>.md   (+ CONTROL PASS/FAIL for CTRL13A jobs)
  aimi_lab_report.py compile <run id>   -> results/compile-<sha8>.md
"""
import json
import os
import re
import sys
import time
import urllib.request
from pathlib import Path

REPO = os.environ["REPO"]
TOKEN = os.environ.get("GH_TOKEN", "")
API = f"https://api.github.com/repos/{REPO}/actions"
OUT = Path("lab/aimi-replay/results")

# Step 13a / 13b reference for APP(a) + CAL usual, per seed: <70, <54, >180, CV, hypo g/day, insulin U/day.
# A job that carries the token CTRL13A must give exactly these numbers for its seed.
CTRL13A = {
    "SEED_1": ["6.0", "0.5", "5.7", "29.7", "33.21", "11.78"],
    "SEED_2": ["4.0", "0.1", "4.2", "27.7", "24.11", "10.92"],
    "SEED_3": ["4.8", "0.3", "4.5", "28.9", "29.64", "11.63"],
}
KEEP = ("AIMI audit ", "AIMI replay: learner ALL", "AIMI replay: ")


def get(url: str, raw: bool = False):
    headers = {"Accept": "application/vnd.github+json"}
    if TOKEN:
        headers["Authorization"] = f"Bearer {TOKEN}"
    req = urllib.request.Request(url, headers=headers)
    with urllib.request.urlopen(req, timeout=120) as r:
        data = r.read()
    return data.decode("utf-8", "replace") if raw else json.loads(data)


def jobs(run_id: str):
    out, page = [], 1
    while True:
        d = get(f"{API}/runs/{run_id}/jobs?per_page=100&page={page}")
        out += d["jobs"]
        if len(d["jobs"]) < 100:
            return out
        page += 1


def log_lines(job_id: int, marker: str = ""):
    """GitHub makes a job log readable some time after the job ends: ask again for up to about 10 minutes."""
    text, err = "", ""
    for _ in range(20):
        try:
            text = get(f"{API}/jobs/{job_id}/logs", raw=True)
            if text.strip() and (not marker or marker in text):
                break
        except Exception as e:  # a missing log is retried, then reported
            err = str(e)
        time.sleep(30)
    if not text.strip():
        return [f"(LOG NOT READY: {err or 'empty'})"]
    # Each line starts with an ISO time stamp and a space.
    return [re.sub(r"^\S+Z ", "", line).strip() for line in text.splitlines()]


def overall(js) -> str:
    """The run is still going while its report job runs, so its own conclusion is not set yet: judge by the jobs."""
    bad = [j["name"].split(",")[0] for j in js if j["conclusion"] not in ("success", "skipped")]
    return "success" if not bad else "failure (" + ", ".join(bad[:10]) + ")"


def replay(run_id: str) -> Path:
    run = get(f"{API}/runs/{run_id}")
    all_jobs = [j for j in jobs(run_id) if j["name"] != "report"]
    lines = [f"# Lab run {run_id}", "",
             f"- Link: {run['html_url']}",
             f"- Commit: `{run['head_sha'][:8]}`, started {run['created_at']}, result: **{overall(all_jobs)}**", "",
             "| # | Job | Result | <70 | <54 | >180 | CV | Hypo g/day | Insulin U/day | Control |",
             "|---:|---|---|---:|---:|---:|---:|---:|---:|---|"]
    details, controls_failed, missing = [], 0, 0
    rjobs = sorted((j for j in all_jobs if j["name"].startswith("replay")), key=lambda j: int(re.sub(r"\D.*", "", j["name"][8:]) or 0))
    for j in rjobs:
        m = re.match(r"replay \((\d+),", j["name"])
        idx = m.group(1) if m else "?"
        log = log_lines(j["id"], "AIMI_REPLAY_PREFS")
        prefs = next((l.split("AIMI_REPLAY_PREFS:", 1)[1].strip() for l in log if "AIMI_REPLAY_PREFS:" in l), "")
        tokens = [t for t in re.split(r"[,\s]+", prefs) if t]
        row = next((l for l in log if l.startswith("| SYNTH")), None)
        nums = [c.strip() for c in row.strip("|").split("|")[1:7]] if row else []
        seed = next((t for t in tokens if t.startswith("SEED_")), "?")
        control = ""
        if "CTRL13A" in tokens:
            want = CTRL13A.get(seed)
            if want is None or nums != want:
                control = f"**FAIL** (want {'/'.join(want or ['?'])})"
                controls_failed += 1
            else:
                control = "PASS"
        short = ",".join(t for t in tokens if not t.startswith("SYNTH_"))
        if len(nums) != 6:
            missing += 1
        cells = nums if len(nums) == 6 else ["-"] * 6
        lines.append(f"| {idx} | {seed} | {j['conclusion']} | " + " | ".join(cells) + f" | {control} |")
        keep = [l for l in log if l.startswith(KEEP) and "AIMI_REPLAY_PREFS" not in l]
        seen = set()
        keep = [l for l in keep if not (l in seen or seen.add(l))]
        details += ["", f"## Job {idx}", "", f"Tokens: `{short}`", "", "```text"] + keep[:200] + ["```"]
    verdict = "CONTROL FAIL: STOP and ask the user" if controls_failed else "controls: none failed"
    if missing:
        verdict += f"; **INCOMPLETE: {missing} job(s) without numbers (log not ready or job failed) - do not use them**"
    lines[4:4] = [f"- Jobs: {len(rjobs)}; {verdict}"]
    OUT.mkdir(parents=True, exist_ok=True)
    path = OUT / f"run-{run_id}.md"
    path.write_text("\n".join(lines + details) + "\n", encoding="utf-8")
    return path


def compile_check(run_id: str) -> Path:
    run = get(f"{API}/runs/{run_id}")
    lines = [f"# Compile check for `{run['head_sha'][:8]}`", "",
             f"- Link: {run['html_url']}",
             f"- Full commit: `{run['head_sha']}`",
             f"- Conclusion: **{overall([j for j in jobs(run_id) if j['name'] != 'report'])}**", ""]
    for j in jobs(run_id):
        if j["name"] == "report":
            continue
        for s in j["steps"]:
            lines.append(f"- {s['name']}: {s['conclusion']}")
        if j["conclusion"] != "success":
            errs = [l for l in log_lines(j["id"]) if l.startswith("e: ") or "FAILED" in l or "What went wrong" in l]
            lines += ["", "Errors (first 40 lines):", "```text"] + errs[:40] + ["```"]
    OUT.mkdir(parents=True, exist_ok=True)
    path = OUT / f"compile-{run['head_sha'][:8]}.md"
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return path


if __name__ == "__main__":
    mode, run_id = sys.argv[1], sys.argv[2]
    if not run_id.isdigit():
        raise SystemExit("run id must be a number")
    print(replay(run_id) if mode == "replay" else compile_check(run_id))
