#!/usr/bin/env python3
"""Fail when an OSV-Scanner JSON report contains an advisory that is not in the checked-in baseline.

The dependency backlog is large and some entries (org.didcommx:didcomm 0.3.2 embeds an old Nimbus and
json-smart) have no upgrade path, so the OSV job cannot simply fail on every finding. Instead it fails
on any finding that is *new* relative to config/osv/baseline.json. A finding is an advisory in a
package: it matches the baseline when the advisory id (or any alias) is listed AND the package name
(group prefix and version ignored, ecosystem compared when the entry records one) is listed for that
entry. A baselined GHSA that appears in a NEW package therefore fails. Entries without a "packages"
list are legacy id-only entries and match any package; --update-baseline rewrites them with packages.

The gate also refuses to pass on a broken scan:
  * a missing or unparseable report is an error (exit 2);
  * an empty report (no results at all) while the baseline is not empty is an error (exit 2), because
    the backlog cannot have disappeared; pass --allow-empty only after deliberately clearing it;
  * with --sbom, the scanned component count must reach a floor: --min-packages (default 200) or half
    of the baseline's recorded "package_count", whichever is larger (exit 2).

Stale baseline entries (nothing in the report matches them any more) are listed; they fail the check
only with --strict-stale.

Usage:
    python scripts/check-osv-baseline.py --report build/reports/osv/osv.json \
        --sbom build/reports/cyclonedx/bom.json
    python scripts/check-osv-baseline.py --report ... --sbom ... --update-baseline

--update-baseline rewrites the baseline from the report (advisory + packages + ecosystems, plus the
SBOM "package_count"), keeping the existing reason for ids that were already listed. Only run it
deliberately, after triaging the new advisories; a new entry needs a reason (edit the file) before it
is committed.
"""
import argparse
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_BASELINE = ROOT / "config/osv/baseline.json"
DEFAULT_MIN_PACKAGES = 200


def short_name(name):
    """'io.netty:netty-common' and 'netty-common' (JAR scan) both become 'netty-common'."""
    return str(name).rpartition(":")[2].lower()


def label_name(label):
    return short_name(label.rpartition("@")[0] or label)


def report_advisories(report):
    """Return {primary id: {"ids": ids+aliases, "packages": {"name@version"}, "pairs": {(short name, ecosystem)}}}."""
    found = {}
    for result in report.get("results", []):
        for package in result.get("packages", []):
            info = package.get("package", {})
            label = f"{info.get('name', '?')}@{info.get('version', '?')}"
            ecosystem = info.get("ecosystem", "")
            for vulnerability in package.get("vulnerabilities", []):
                vid = vulnerability.get("id")
                if not vid:
                    continue
                entry = found.setdefault(vid, {"ids": {vid}, "packages": set(), "pairs": set()})
                entry["ids"].update(vulnerability.get("aliases", []))
                entry["packages"].add(label)
                entry["pairs"].add((short_name(info.get("name", "?")), ecosystem))
    return found


def baseline_ids(baseline):
    ids = set()
    for item in baseline.get("advisories", []):
        ids.add(item["id"])
        ids.update(item.get("aliases", []))
    return ids


def _entry_matches(item, ids, name, ecosystem):
    if not ({item["id"], *item.get("aliases", [])} & ids):
        return False
    packages = item.get("packages")
    if not packages:
        return True  # legacy id-only entry
    if name not in {label_name(p) for p in packages}:
        return False
    ecosystems = item.get("ecosystems")
    return not (ecosystems and ecosystem and ecosystem not in ecosystems)


def new_advisories(found, baseline):
    """Advisories with at least one (package, ecosystem) not covered by the baseline; only those packages are kept."""
    items = baseline.get("advisories", [])
    fresh = {}
    for vid, entry in found.items():
        missing = {pair for pair in entry["pairs"] if not any(_entry_matches(i, entry["ids"], *pair) for i in items)}
        if missing:
            names = {name for name, _ in missing}
            fresh[vid] = {
                "ids": entry["ids"],
                "pairs": missing,
                "packages": {p for p in entry["packages"] if label_name(p) in names},
            }
    return fresh


def stale_entries(found, baseline):
    stale = []
    for item in baseline.get("advisories", []):
        hit = any(
            _entry_matches(item, entry["ids"], *pair) for entry in found.values() for pair in entry["pairs"]
        )
        if not hit:
            stale.append(item["id"])
    return stale


def sbom_component_count(sbom):
    def count(components):
        return sum(1 + count(c.get("components", [])) for c in components)

    return count(sbom.get("components", []))


def updated_baseline(found, baseline, package_count=None):
    previous = {item["id"]: item for item in baseline.get("advisories", [])}
    advisories = []
    for vid in sorted(found):
        old = next((previous[i] for i in found[vid]["ids"] if i in previous), None)
        advisories.append(
            {
                "id": vid,
                "aliases": sorted(found[vid]["ids"] - {vid}),
                "packages": sorted(found[vid]["packages"]),
                "ecosystems": sorted({eco for _, eco in found[vid]["pairs"] if eco}),
                "reason": (old or {}).get("reason", "TODO: triage before committing"),
            }
        )
    result = {"schema": 2, "description": baseline.get("description", "")}
    count = package_count if package_count is not None else baseline.get("package_count")
    if count is not None:
        result["package_count"] = count
    result["advisories"] = advisories
    return result


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--report", type=Path, required=True, help="OSV-Scanner --format=json output")
    parser.add_argument("--baseline", type=Path, default=DEFAULT_BASELINE)
    parser.add_argument("--sbom", type=Path, help="CycloneDX bom.json the scan covered; enables the package-count floor")
    parser.add_argument("--min-packages", type=int, default=DEFAULT_MIN_PACKAGES, help="floor for SBOM components (default %(default)s)")
    parser.add_argument("--allow-empty", action="store_true", help="accept a report with no results although the baseline is not empty")
    parser.add_argument("--strict-stale", action="store_true", help="fail when baseline entries no longer match anything")
    parser.add_argument("--update-baseline", action="store_true")
    args = parser.parse_args(argv)

    if not args.report.is_file():
        print(f"OSV report not found: {args.report}", file=sys.stderr)
        return 2
    try:
        report = json.loads(args.report.read_text(encoding="utf-8"))
    except ValueError as error:
        print(f"OSV report is not valid JSON: {error}", file=sys.stderr)
        return 2
    if not isinstance(report, dict) or "results" not in report:
        print("OSV report has no 'results' key; the scan did not complete.", file=sys.stderr)
        return 2
    found = report_advisories(report)
    baseline = json.loads(args.baseline.read_text(encoding="utf-8")) if args.baseline.is_file() else {"advisories": []}

    package_count = None
    if args.sbom:
        if not args.sbom.is_file():
            print(f"SBOM not found: {args.sbom}", file=sys.stderr)
            return 2
        package_count = sbom_component_count(json.loads(args.sbom.read_text(encoding="utf-8")))

    if args.update_baseline:
        args.baseline.parent.mkdir(parents=True, exist_ok=True)
        args.baseline.write_text(
            json.dumps(updated_baseline(found, baseline, package_count), indent=2) + "\n", encoding="utf-8"
        )
        print(f"Wrote {len(found)} advisories to {args.baseline}")
        return 0

    if package_count is not None:
        floor = max(args.min_packages, int(baseline.get("package_count", 0) * 0.5))
        if package_count < floor:
            print(f"Scan too small: the SBOM has {package_count} components, expected at least {floor}.", file=sys.stderr)
            return 2
    if not found and baseline.get("advisories") and not args.allow_empty:
        print(
            "OSV report contains no advisories although the baseline lists some; treating the scan as broken "
            "(pass --allow-empty after deliberately clearing the baseline).",
            file=sys.stderr,
        )
        return 2

    fresh = new_advisories(found, baseline)
    stale = stale_entries(found, baseline)
    if stale:
        print(f"Note: {len(stale)} baseline entries no longer match anything and can be removed: {', '.join(sorted(stale)[:20])}")
    if fresh:
        print(f"{len(fresh)} advisory(ies) not in {args.baseline.relative_to(ROOT) if args.baseline.is_relative_to(ROOT) else args.baseline}:")
        for vid in sorted(fresh):
            print(f"  {vid} ({', '.join(sorted(fresh[vid]['packages']))})")
        print("Upgrade or exclude the dependency, or triage the advisory and add it to the baseline with a reason.")
        return 1
    if stale and args.strict_stale:
        print("--strict-stale: remove the stale entries from the baseline.", file=sys.stderr)
        return 1
    print(f"OSV: {len(found)} advisories, all in the baseline")
    return 0


if __name__ == "__main__":
    sys.exit(main())
