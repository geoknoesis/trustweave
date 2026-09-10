"""Fail CI when required Kover scopes/counters disappear or fall below reviewed floors."""
import argparse
import json
import math
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


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
    parser.add_argument("report", nargs="?", default="build/reports/kover/report.xml")
    parser.add_argument("--policy", default="config/coverage-policy.json")
    args = parser.parse_args()
    try:
        failures = check(args.report, json.loads(Path(args.policy).read_text(encoding="utf-8")))
    except (OSError, ValueError, KeyError, ET.ParseError) as error:
        print(f"Invalid coverage evidence: {error}", file=sys.stderr)
        sys.exit(1)
    for failure in failures:
        print(failure, file=sys.stderr)
    if failures:
        sys.exit(1)
    print("Coverage policy passed")
