#!/usr/bin/env python3
"""Fail when an OSV-Scanner JSON report contains an advisory that is not in the checked-in baseline.

The dependency backlog is large and some entries (org.didcommx:didcomm 0.3.2 embeds an old Nimbus and
json-smart) have no upgrade path, so the OSV job cannot simply fail on every finding. Instead it fails
on any finding that is *new* relative to config/osv/baseline.json. A finding is an advisory in a
package: it matches the baseline when the advisory id (or any alias) is listed AND the package name
(group prefix ignored, ecosystem compared when the entry records one) is listed for that entry at the
finding's version. A baselined GHSA that appears in a NEW package therefore fails. Entries without a
"packages" list are legacy id-only entries and match any package; --update-baseline rewrites them with
packages.

Matching is version-aware where the baseline records a version ("name@version"): the finding's version
must be one of the versions recorded for that package. A bump that still leaves the advisory in the
report (the scanner says the new version is affected) therefore re-surfaces it for a fresh look; a bump
to a fixed version simply drops out of the report and the recorded version is listed as stale. A label
without a version ("name" or "name@?") matches any version of that package.

Triage (baseline "schema" 3 and later). Every entry records a decision, so the baseline is a reviewed
list rather than a dump:
  status    affected | not-reachable | false-positive | accepted-risk | needs-review
  reason    why: where the package comes from, what uses it, the fixed version, any mitigation
  reviewed  YYYY-MM-DD the decision was made (not in the future)
  expires   YYYY-MM-DD the decision lapses; required for affected, accepted-risk and needs-review,
            optional for not-reachable and false-positive, at most 366 days after "reviewed"
An entry whose "expires" date has passed fails the check (exit 1) until it is re-triaged or the
dependency is fixed and the entry removed. A malformed triage (unknown status, missing reason or date,
a "TODO" reason, a missing expiry where one is required) is an error (exit 2). "needs-review" is the
honest value for an entry nobody has analysed yet; it is time-boxed like the others. Baselines older
than schema 3 are accepted without triage fields.

The gate also refuses to pass on a broken scan:
  * a missing or unparseable report is an error (exit 2);
  * an empty report (no results at all) while the baseline is not empty is an error (exit 2), because
    the backlog cannot have disappeared; pass --allow-empty only after deliberately clearing it;
  * with --sbom, the scanned component count must reach a floor: --min-packages (default 200) or half
    of the baseline's recorded "package_count", whichever is larger (exit 2).

Stale baseline entries (nothing in the report matches them any more) are listed; they fail the check
only with --strict-stale. Recorded versions that no longer appear in an otherwise live entry are listed
too, but never fail the check.

Usage:
    python scripts/check-osv-baseline.py --report build/reports/osv/osv.json \
        --sbom build/reports/cyclonedx/bom.json
    python scripts/check-osv-baseline.py --report ... --sbom ... --update-baseline

--update-baseline rewrites the baseline from the report (advisory + packages + ecosystems, plus the
SBOM "package_count"). An entry that was already listed keeps its triage as long as the report adds no
package version the entry did not record; otherwise (and for brand-new advisories) it is written as
"needs-review" with a TODO reason that quotes the earlier decision, and the check rejects it until a
person replaces it with a real triage. Only run it deliberately, after triaging the new advisories.
"""
import argparse
import datetime
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_BASELINE = ROOT / "config/osv/baseline.json"
DEFAULT_MIN_PACKAGES = 200
TRIAGE_SCHEMA = 3
STATUSES = ("affected", "not-reachable", "false-positive", "accepted-risk", "needs-review")
EXPIRY_REQUIRED = frozenset({"affected", "accepted-risk", "needs-review"})
MAX_TRIAGE_DAYS = 366
EXPIRY_WARNING_DAYS = 30


def short_name(name):
    """'io.netty:netty-common' and 'netty-common' (JAR scan) both become 'netty-common'."""
    return str(name).rpartition(":")[2].lower()


def label_name(label):
    return short_name(label.rpartition("@")[0] or label)


def label_version(label):
    """The version of a 'name@version' label; '' when the label has none or records '?'."""
    version = label.rpartition("@")[2] if "@" in label else ""
    return "" if version == "?" else version


def report_advisories(report):
    """Return {primary id: {"ids": ids+aliases, "packages": {"name@version"}, "pairs": {(short name, ecosystem, version)}}}."""
    found = {}
    for result in report.get("results", []):
        for package in result.get("packages", []):
            info = package.get("package", {})
            label = f"{info.get('name', '?')}@{info.get('version', '?')}"
            ecosystem = info.get("ecosystem", "")
            version = label_version(label)
            for vulnerability in package.get("vulnerabilities", []):
                vid = vulnerability.get("id")
                if not vid:
                    continue
                entry = found.setdefault(vid, {"ids": {vid}, "packages": set(), "pairs": set()})
                entry["ids"].update(vulnerability.get("aliases", []))
                entry["packages"].add(label)
                entry["pairs"].add((short_name(info.get("name", "?")), ecosystem, version))
    return found


def baseline_ids(baseline):
    ids = set()
    for item in baseline.get("advisories", []):
        ids.add(item["id"])
        ids.update(item.get("aliases", []))
    return ids


def _entry_matches(item, ids, name, ecosystem, version=""):
    if not ({item["id"], *item.get("aliases", [])} & ids):
        return False
    packages = item.get("packages")
    if not packages:
        return True  # legacy id-only entry
    recorded = [label_version(p) for p in packages if label_name(p) == name]
    if not recorded:
        return False
    if version and "" not in recorded and version not in recorded:
        return False  # same package, a version the entry never recorded
    ecosystems = item.get("ecosystems")
    return not (ecosystems and ecosystem and ecosystem not in ecosystems)


def recorded_versions(items, ids, name):
    """Versions of `name` the baseline records for entries that carry one of `ids`."""
    versions = set()
    for item in items:
        if {item["id"], *item.get("aliases", [])} & ids:
            versions.update(label_version(p) for p in item.get("packages", []) if label_name(p) == name)
    return {v for v in versions if v}


def new_advisories(found, baseline):
    """Advisories with at least one (package, ecosystem, version) not covered by the baseline; only those packages are kept."""
    items = baseline.get("advisories", [])
    fresh = {}
    for vid, entry in found.items():
        missing = {triple for triple in entry["pairs"] if not any(_entry_matches(i, entry["ids"], *triple) for i in items)}
        if missing:
            names = {(name, version) for name, _, version in missing}
            fresh[vid] = {
                "ids": entry["ids"],
                "pairs": missing,
                "packages": {p for p in entry["packages"] if (label_name(p), label_version(p)) in names},
            }
    return fresh


def stale_entries(found, baseline):
    stale = []
    for item in baseline.get("advisories", []):
        hit = any(
            _entry_matches(item, entry["ids"], *triple) for entry in found.values() for triple in entry["pairs"]
        )
        if not hit:
            stale.append(item["id"])
    return stale


def stale_versions(found, baseline):
    """Recorded 'name@version' labels of live entries that the report no longer shows (never fatal)."""
    seen = {(name, version) for entry in found.values() for name, _, version in entry["pairs"]}
    dead = set(stale_entries(found, baseline))
    lines = set()
    for item in baseline.get("advisories", []):
        if item["id"] in dead:
            continue
        for p in item.get("packages", []):
            version = label_version(p)
            if version and (label_name(p), version) not in seen:
                lines.add(f"{item['id']} {p}")
    return sorted(lines)


def parse_date(value):
    try:
        return datetime.date.fromisoformat(value) if isinstance(value, str) and len(value) == 10 else None
    except ValueError:
        return None


def validate_triage(baseline, today):
    """Return (errors, expired, expiring): malformed triage, lapsed entries, entries lapsing within 30 days.

    Baselines older than TRIAGE_SCHEMA carry no triage and are not validated.
    """
    errors, expired, expiring = [], [], []
    if baseline.get("schema", 1) < TRIAGE_SCHEMA:
        return errors, expired, expiring
    for item in baseline.get("advisories", []):
        vid = item.get("id", "?")
        status = item.get("status")
        if status not in STATUSES:
            errors.append(f"{vid}: status must be one of {', '.join(STATUSES)} (got {status!r})")
            continue
        reason = str(item.get("reason", "")).strip()
        if not reason or reason.upper().startswith("TODO"):
            errors.append(f"{vid}: write a real triage reason (empty or TODO)")
        reviewed = parse_date(item.get("reviewed"))
        if reviewed is None:
            errors.append(f"{vid}: 'reviewed' must be a YYYY-MM-DD date")
        elif reviewed > today:
            errors.append(f"{vid}: 'reviewed' ({reviewed}) is in the future")
        expires_raw = item.get("expires")
        if expires_raw is None:
            if status in EXPIRY_REQUIRED:
                errors.append(f"{vid}: status '{status}' requires an 'expires' date")
            continue
        expires = parse_date(expires_raw)
        if expires is None:
            errors.append(f"{vid}: 'expires' must be a YYYY-MM-DD date")
            continue
        if reviewed is not None and (expires - reviewed).days > MAX_TRIAGE_DAYS:
            errors.append(f"{vid}: 'expires' is more than {MAX_TRIAGE_DAYS} days after 'reviewed'")
        if expires < today:
            expired.append(f"{vid} ({status}, expired {expires})")
        elif (expires - today).days <= EXPIRY_WARNING_DAYS:
            expiring.append(f"{vid} ({status}, expires {expires})")
    return errors, expired, expiring


def sbom_component_count(sbom):
    def count(components):
        return sum(1 + count(c.get("components", [])) for c in components)

    return count(sbom.get("components", []))


def updated_baseline(found, baseline, package_count=None):
    previous = {item["id"]: item for item in baseline.get("advisories", [])}
    advisories = []
    for vid in sorted(found):
        old = next((previous[i] for i in found[vid]["ids"] if i in previous), None) or {}
        packages = sorted(found[vid]["packages"])
        entry = {
            "id": vid,
            "aliases": sorted(found[vid]["ids"] - {vid}),
            "packages": packages,
            "ecosystems": sorted({eco for _, eco, _ in found[vid]["pairs"] if eco}),
        }
        old_packages = old.get("packages")
        recorded = {(label_name(p), label_version(p)) for p in old_packages or []}
        added = sorted(
            p
            for p in packages
            if old_packages and (label_name(p), label_version(p)) not in recorded and (label_name(p), "") not in recorded
        )
        if old and not added:
            # Unchanged (or a legacy id-only entry being migrated): keep the earlier decision as it was.
            entry["status"] = old.get("status", "needs-review")
            entry["reason"] = old.get("reason", "TODO: triage before committing")
            for key in ("reviewed", "expires"):
                if key in old:
                    entry[key] = old[key]
        else:
            before = f" Earlier decision: {old['status']}: {old.get('reason', '')}" if old.get("status") else ""
            why = f"new versions {', '.join(added)}" if added else "new advisory"
            entry["status"] = "needs-review"
            entry["reason"] = f"TODO: triage before committing ({why}).{before}"
        advisories.append(entry)
    result = {"schema": TRIAGE_SCHEMA, "description": baseline.get("description", "")}
    count = package_count if package_count is not None else baseline.get("package_count")
    if count is not None:
        result["package_count"] = count
    result["advisories"] = advisories
    return result


def _describe_fresh(entry, baseline):
    items = baseline.get("advisories", [])
    parts = []
    for label in sorted(entry["packages"]):
        known = recorded_versions(items, entry["ids"], label_name(label))
        note = f" (baselined at {', '.join(sorted(known))}; this version is new)" if known else ""
        parts.append(f"{label}{note}")
    return ", ".join(parts)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--report", type=Path, required=True, help="OSV-Scanner --format=json output")
    parser.add_argument("--baseline", type=Path, default=DEFAULT_BASELINE)
    parser.add_argument("--sbom", type=Path, help="CycloneDX bom.json the scan covered; enables the package-count floor")
    parser.add_argument("--min-packages", type=int, default=DEFAULT_MIN_PACKAGES, help="floor for SBOM components (default %(default)s)")
    parser.add_argument("--allow-empty", action="store_true", help="accept a report with no results although the baseline is not empty")
    parser.add_argument("--strict-stale", action="store_true", help="fail when baseline entries no longer match anything")
    parser.add_argument("--today", type=datetime.date.fromisoformat, help="override today's date (YYYY-MM-DD) for triage expiry; for tests")
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
        print(f"Wrote {len(found)} advisories to {args.baseline}; replace every TODO reason with a triage before committing.")
        return 0

    today = args.today or datetime.datetime.now(datetime.timezone.utc).date()
    errors, expired, expiring = validate_triage(baseline, today)
    if errors:
        print(f"{len(errors)} baseline entry(ies) with an invalid triage:", file=sys.stderr)
        for line in errors:
            print(f"  {line}", file=sys.stderr)
        return 2

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

    shown = args.baseline.relative_to(ROOT) if args.baseline.is_relative_to(ROOT) else args.baseline
    fresh = new_advisories(found, baseline)
    stale = stale_entries(found, baseline)
    if stale:
        print(f"Note: {len(stale)} baseline entries no longer match anything and can be removed: {', '.join(sorted(stale)[:20])}")
    old_versions = stale_versions(found, baseline)
    if old_versions:
        print(f"Note: {len(old_versions)} recorded package versions no longer appear in the report and can be dropped: {'; '.join(old_versions[:10])}")
    if expiring:
        print(f"Note: {len(expiring)} triage entry(ies) expire within {EXPIRY_WARNING_DAYS} days: {', '.join(sorted(expiring)[:20])}")
    failed = False
    if expired:
        print(f"{len(expired)} triage entry(ies) in {shown} have expired; re-triage them or fix the dependency:")
        for line in sorted(expired):
            print(f"  {line}")
        failed = True
    if fresh:
        print(f"{len(fresh)} advisory(ies) not in {shown}:")
        for vid in sorted(fresh):
            print(f"  {vid} ({_describe_fresh(fresh[vid], baseline)})")
        print("Upgrade or exclude the dependency, or triage the advisory and add it to the baseline with a reason.")
        failed = True
    if failed:
        return 1
    if stale and args.strict_stale:
        print("--strict-stale: remove the stale entries from the baseline.", file=sys.stderr)
        return 1
    counts = {}
    for item in baseline.get("advisories", []):
        if item.get("status"):
            counts[item["status"]] = counts.get(item["status"], 0) + 1
    summary = f" ({', '.join(f'{s}: {counts[s]}' for s in STATUSES if s in counts)})" if counts else ""
    print(f"OSV: {len(found)} advisories, all in the baseline{summary}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
