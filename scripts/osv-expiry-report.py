#!/usr/bin/env python3
"""Write a Markdown report of OSV baseline triage entries that have expired or expire soon.

Used by the scheduled `osv-expiry-issue` job in .github/workflows/security.yml, which opens or updates one
GitHub issue from the report. The gate itself (scripts/check-osv-baseline.py) still fails on an expired
entry; this only gives maintainers notice before that happens.

    python scripts/osv-expiry-report.py --output build/reports/osv/expiry.md
Exit status is 0 always when the baseline parses; the number of listed entries is printed, and the file is
written only when there is at least one (so a workflow can test for its existence).
"""
import argparse
import datetime
import importlib.util
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
_SPEC = importlib.util.spec_from_file_location("osv_baseline", Path(__file__).with_name("check-osv-baseline.py"))
osv = importlib.util.module_from_spec(_SPEC)
_SPEC.loader.exec_module(osv)

MARKER = "<!-- osv-expiry-report -->"


def build_report(baseline, today, window=osv.EXPIRY_WARNING_DAYS):
    """Return (markdown or None, count). Entries are grouped by expiry date, earliest first."""
    rows = []
    for item in baseline.get("advisories", []):
        expires = osv.parse_date(item.get("expires"))
        if expires is None or (expires - today).days > window:
            continue
        packages = ", ".join(sorted(item.get("packages", []))[:3]) or "(any package)"
        rows.append((expires, item.get("id", "?"), item.get("status", "?"), packages))
    if not rows:
        return None, 0
    rows.sort()
    lines = [
        MARKER,
        f"{len(rows)} entry(ies) in `config/osv/baseline.json` expire within {window} days or already have "
        f"(checked {today}). The OSV gate fails on an expired entry.",
        "",
        "Re-triage each one (new `reviewed` and `expires`, spread out so one date does not fail everything), "
        "or fix the dependency and delete the entry. See SECURITY.md, Dependency Scanning.",
        "",
        "| Expires | Advisory | Status | Packages |",
        "|---|---|---|---|",
    ]
    for expires, vid, status, packages in rows:
        flag = " (EXPIRED)" if expires < today else ""
        lines.append(f"| {expires}{flag} | {vid} | {status} | {packages} |")
    return "\n".join(lines) + "\n", len(rows)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--baseline", type=Path, default=osv.DEFAULT_BASELINE)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--today", type=datetime.date.fromisoformat)
    args = parser.parse_args(argv)
    baseline = json.loads(args.baseline.read_text(encoding="utf-8"))
    today = args.today or datetime.datetime.now(datetime.timezone.utc).date()
    markdown, count = build_report(baseline, today)
    if args.output.exists():
        args.output.unlink()
    if markdown:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(markdown, encoding="utf-8")
    print(f"{count} baseline entry(ies) expired or expiring within {osv.EXPIRY_WARNING_DAYS} days")
    return 0


if __name__ == "__main__":
    sys.exit(main())
