#!/usr/bin/env python3
"""Write a short public report of an app CI run, so an assistant that cannot see GitHub Actions can read it.

Usage: ci_report.py <run id> <out dir>  ->  <out dir>/run-<run id>.md and <out dir>/LATEST.md
It lists every job and step, the failed steps, the error lines of failed jobs (Kotlin errors, failed tests,
instrumented test summary) and the artifacts with their download pages. Nothing private is in these logs.
"""
import json
import os
import re
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

REPO = os.environ["REPO"]
TOKEN = os.environ.get("GH_TOKEN", "")
API = f"https://api.github.com/repos/{REPO}/actions"
ERR = re.compile(r"(^e: |FAILED|What went wrong|FAILURES!!!|Tests run:|OK \(\d+ tests?\)|Error in |"
                 r"Test classes that did not pass|expected to be|AssertionError|BUILD FAILED)")


class NoRedirect(urllib.request.HTTPRedirectHandler):
    """The log endpoint answers with a redirect to a signed storage link: follow it ourselves, WITHOUT our token."""

    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def get(url: str, raw: bool = False):
    headers = {"Accept": "application/vnd.github+json"}
    if TOKEN:
        headers["Authorization"] = f"Bearer {TOKEN}"
    opener = urllib.request.build_opener(NoRedirect)
    try:
        with opener.open(urllib.request.Request(url, headers=headers), timeout=120) as r:
            data = r.read()
    except urllib.error.HTTPError as e:
        if e.code not in (301, 302, 303, 307, 308):
            raise
        # The storage link carries its own signature; a token sent there can make it fail.
        with urllib.request.urlopen(urllib.request.Request(e.headers["Location"]), timeout=120) as r:
            data = r.read()
    return data.decode("utf-8", "replace") if raw else json.loads(data)


def log_lines(job_id: int):
    """
    A job log can be not ready for some minutes after the job ends (404): ask again for up to about 5 minutes.
    Any other error will not go away by waiting: stop at once. Never silent: a log that could not be read gives one
    line "LOG NOT READ: <reason>".
    """
    reason = "empty"
    for _ in range(10):
        try:
            text = get(f"{API}/jobs/{job_id}/logs", raw=True)
            if text.strip():
                return [re.sub(r"^\S+Z ", "", l).rstrip() for l in text.splitlines()]
        except urllib.error.HTTPError as e:
            reason = f"HTTP {e.code} {e.read()[:200]!r}"
            if e.code != 404:
                break
        except Exception as e:
            reason = repr(e)[:200]
        time.sleep(30)
    return [f"LOG NOT READ: {reason}"]


def main(run_id: str, out: Path) -> Path:
    run = get(f"{API}/runs/{run_id}")
    jobs = [j for j in get(f"{API}/runs/{run_id}/jobs?per_page=100")["jobs"] if j["name"] != "report"]
    bad = [j for j in jobs if j["conclusion"] not in ("success", "skipped")]
    lines = [f"# App CI run {run_id}", "",
             f"- Link: {run['html_url']}",
             f"- Branch `{run['head_branch']}`, commit `{run['head_sha'][:8]}`, event `{run['event']}`, started {run['created_at']}",
             f"- Result: **{'success' if not bad else 'FAILURE: ' + ', '.join(j['name'] for j in bad)}**", ""]
    for j in jobs:
        lines.append(f"## Job `{j['name']}`: {j['conclusion']}")
        failed = [s["name"] for s in j["steps"] if s["conclusion"] == "failure"]
        if failed:
            lines.append(f"- Failed steps: {', '.join(failed)}")
        if j["conclusion"] not in ("skipped",):
            hits = [l for l in log_lines(j["id"]) if (ERR.search(l) or l.startswith("LOG NOT READ")) and "[36;1m" not in l]
            seen = set()
            hits = [l for l in hits if not (l in seen or seen.add(l))]
            if hits:
                lines += ["", "```text"] + hits[:60] + ["```"]
        lines.append("")
    arts = get(f"{API}/runs/{run_id}/artifacts")["artifacts"]
    if arts:
        lines.append("## Artifacts (download from the run page)")
        lines += [f"- `{a['name']}` ({a['size_in_bytes'] // 1024} KB)" for a in arts]
    out.mkdir(parents=True, exist_ok=True)
    text = "\n".join(lines) + "\n"
    path = out / f"run-{run_id}.md"
    path.write_text(text, encoding="utf-8")
    (out / "LATEST.md").write_text(text, encoding="utf-8")
    return path


if __name__ == "__main__":
    if not sys.argv[1].isdigit():
        raise SystemExit("run id must be a number")
    print(main(sys.argv[1], Path(sys.argv[2])))
