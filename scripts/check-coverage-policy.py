"""Fail CI when required Kover scopes/counters disappear or fall below reviewed floors."""
import argparse
import importlib.util
import json
import math
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

_spec = importlib.util.spec_from_file_location("build_root", Path(__file__).with_name("build_root.py"))
_build_root = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(_build_root)


METRICS = {"INSTRUCTION", "BRANCH", "LINE", "COMPLEXITY", "METHOD", "CLASS"}


def validate_policy(policy):
    if not isinstance(policy, dict) or not isinstance(policy.get("scopes"), dict) or not policy["scopes"]:
        raise ValueError("Policy requires nonempty scopes")
    for scope, limits in policy["scopes"].items():
        if not isinstance(scope, str) or not scope.strip() or not isinstance(limits, dict) or not limits:
            raise ValueError("Every scope requires a name and nonempty limits")
        for metric, floor in limits.items():
            if metric not in METRICS:
                raise ValueError(f"Unknown coverage metric: {metric}")
            if type(floor) not in (int, float) or not math.isfinite(floor) or not 0 <= floor <= 100:
                raise ValueError(f"Invalid floor: {scope} {metric}")


def merged_report():
    """Locate the merged Kover report for whichever build layout is in use.

    The root project's build directory is `build/` normally, but this repository redirects it to
    `<LOCALAPPDATA>/TrustWeave/gradle-build/<root>/_root` on Windows so IDE file locks stay out of
    the workspace. Checking both beats assuming one and silently reading a stale report.
    """
    root = _build_root.resolve()
    candidates = [root / "_root" / "reports" / "kover" / "report.xml", root / "reports" / "kover" / "report.xml"]
    for candidate in candidates:
        if candidate.is_file():
            return candidate
    raise ValueError(
        "No merged coverage report; run koverXmlReport first. Looked in: "
        + ", ".join(str(c) for c in candidates)
    )


def check(report, policy):
    validate_policy(policy)
    root = ET.parse(report).getroot()
    if root.tag != "report":
        raise ValueError("Expected a coverage report root")
    packages = {}
    for item in root.findall("package"):
        name = item.attrib["name"]
        if name in packages:
            raise ValueError(f"Duplicate coverage scope: {name}")
        packages[name] = item
    failures = []
    for scope, limits in policy["scopes"].items():
        node = root if scope == "*" else packages.get(scope)
        if node is None:
            failures.append(f"Missing coverage scope: {scope}")
            continue
        counters = {}
        for item in node.findall("counter"):
            metric = item.attrib["type"]
            if metric in counters:
                raise ValueError(f"Duplicate counter: {scope} {metric}")
            counters[metric] = item
        for metric, floor in limits.items():
            counter = counters.get(metric)
            if counter is None:
                failures.append(f"Missing counter: {scope} {metric}")
                continue
            values = [counter.attrib[name] for name in ("covered", "missed")]
            if any(not value.isascii() or not value.isdecimal() for value in values):
                raise ValueError(f"Invalid counter: {scope} {metric}")
            covered, missed = map(int, values)
            if covered + missed == 0:
                failures.append(f"Invalid or empty counter: {scope} {metric}")
                continue
            actual = 100 * covered / (covered + missed)
            if actual < floor:
                failures.append(f"{scope} {metric}: {actual:.2f}% below {floor:.2f}%")
    return failures


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    # Resolved the way the build resolves it: this repository centralizes module output and
    # redirects it out of the workspace on Windows, so a hardcoded in-repo path reads whatever
    # stale report happens to be lying there.
    parser.add_argument("report", nargs="?", default=None)
    parser.add_argument("--policy", default="config/coverage-policy.json")
    args = parser.parse_args()
    try:
        report = args.report or merged_report()
        failures = check(report, json.loads(Path(args.policy).read_text(encoding="utf-8")))
    except (OSError, ValueError, KeyError, ET.ParseError) as error:
        print(f"Invalid coverage evidence: {error}", file=sys.stderr)
        sys.exit(1)
    for failure in failures:
        print(failure, file=sys.stderr)
    if failures:
        sys.exit(1)
    print("Coverage policy passed")
