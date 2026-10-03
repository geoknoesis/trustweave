#!/usr/bin/env python3
"""Fail when an OSV-Scanner JSON report contains an advisory that is not in the checked-in baseline.

The dependency backlog is large and some entries (org.didcommx:didcomm 0.3.2 embeds an old Nimbus and
json-smart) have no upgrade path, so the OSV job cannot simply fail on every finding. Instead it fails
on any advisory that is *new* relative to config/osv/baseline.json. An advisory matches the baseline
when its id, or any alias, is listed there.

Usage:
    python scripts/check-osv-baseline.py --report build/reports/osv/osv.json
    python scripts/check-osv-baseline.py --report build/reports/osv/osv.json --update-baseline

--update-baseline rewrites the baseline from the report, keeping the existing reason for ids that
were already listed. Only run it deliberately, after triaging the new advisories; a new entry needs
a reason (edit the file) before it is committed.
"""
import argparse
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_BASELINE = ROOT / "config/osv/baseline.json"


def report_advisories(report):
    """Return {primary id: {"ids": set(id + aliases), "packages": set("name@version")}} for a report."""
    found = {}
    for result in report.get("results", []):
        for package in result.get("packages", []):
            info = package.get("package", {})
            label = f"{info.get('name', '?')}@{info.get('version', '?')}"
            for vulnerability in package.get("vulnerabilities", []):
                vid = vulnerability.get("id")
                if not vid:
                    continue
                entry = found.setdefault(vid, {"ids": {vid}, "packages": set()})
                entry["ids"].update(vulnerability.get("aliases", []))
                entry["packages"].add(label)
    return found


def baseline_ids(baseline):
    ids = set()
    for item in baseline.get("advisories", []):
        ids.add(item["id"])
        ids.update(item.get("aliases", []))
    return ids


def new_advisories(found, baseline):
    known = baseline_ids(baseline)
    return {vid: entry for vid, entry in found.items() if not (entry["ids"] & known)}


def stale_entries(found, baseline):
    seen = set().union(*(entry["ids"] for entry in found.values())) if found else set()
    return [item["id"] for item in baseline.get("advisories", []) if not ({item["id"], *item.get("aliases", [])} & seen)]


def updated_baseline(found, baseline):
    previous = {item["id"]: item for item in baseline.get("advisories", [])}
    advisories = []
    for vid in sorted(found):
        old = next((previous[i] for i in found[vid]["ids"] if i in previous), None)
        advisories.append(
            {
                "id": vid,
                "aliases": sorted(found[vid]["ids"] - {vid}),
                "packages": sorted(found[vid]["packages"]),
                "reason": (old or {}).get("reason", "TODO: triage before committing"),
            }
        )
    return {"schema": 1, "description": baseline.get("description", ""), "advisories": advisories}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--report", type=Path, required=True, help="OSV-Scanner --format=json output")
    parser.add_argument("--baseline", type=Path, default=DEFAULT_BASELINE)
    parser.add_argument("--update-baseline", action="store_true")
    args = parser.parse_args(argv)

    if not args.report.is_file():
        print(f"OSV report not found: {args.report}", file=sys.stderr)
        return 2
    found = report_advisories(json.loads(args.report.read_text(encoding="utf-8")))
    baseline = json.loads(args.baseline.read_text(encoding="utf-8")) if args.baseline.is_file() else {"advisories": []}

    if args.update_baseline:
        args.baseline.parent.mkdir(parents=True, exist_ok=True)
        args.baseline.write_text(json.dumps(updated_baseline(found, baseline), indent=2) + "\n", encoding="utf-8")
        print(f"Wrote {len(found)} advisories to {args.baseline}")
        return 0

    fresh = new_advisories(found, baseline)
    stale = stale_entries(found, baseline)
    if stale:
        print(f"Note: {len(stale)} baseline entries no longer appear and can be removed: {', '.join(sorted(stale)[:20])}")
    if fresh:
        print(f"{len(fresh)} advisory(ies) not in {args.baseline.relative_to(ROOT) if args.baseline.is_relative_to(ROOT) else args.baseline}:")
        for vid in sorted(fresh):
            print(f"  {vid} ({', '.join(sorted(fresh[vid]['packages']))})")
        print("Upgrade or exclude the dependency, or triage the advisory and add it to the baseline with a reason.")
        return 1
    print(f"OSV: {len(found)} advisories, all in the baseline")
    return 0


if __name__ == "__main__":
    sys.exit(main())
