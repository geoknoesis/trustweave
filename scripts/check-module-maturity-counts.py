#!/usr/bin/env python3
"""Fail when docs/api-reference/module-maturity.md states a test count that no longer matches the sources.

The maturity tables give each module's test count so readers can calibrate their own review effort. A count
that is far below the real one understates a module (the XAdES row once said 3 for a suite of ~80), and one
above it overstates it. Counts are `@Test` / `@ParameterizedTest` / `@RepeatedTest` annotations under the
module's `src/test`. Parameterised tests expand at run time, so the stated figure is allowed to be lower than the
annotation count by a tolerance, but never higher; exact equality would force a doc edit on every new test.

Usage: python scripts/check-module-maturity-counts.py [--root DIR] [--doc FILE] [--tolerance 0.25]
Module names in the first column are Gradle paths (`signatures:jades`), mapped to directories by `:` -> `/`.
"""
import argparse
import re
import sys
from pathlib import Path

ANNOTATION = re.compile(r"^\s*@(?:Test|ParameterizedTest|RepeatedTest)\b", re.MULTILINE)
ROW = re.compile(r"^\|\s*`([^`]+)`\s*\|\s*(\d+)\s*\|", re.MULTILINE)


def count_tests(module_dir: Path) -> int:
    total = 0
    for source in (module_dir / "src" / "test").rglob("*.kt"):
        total += len(ANNOTATION.findall(source.read_text(encoding="utf-8", errors="replace")))
    return total


def check(root: Path, doc: Path, tolerance: float) -> list:
    problems = []
    for module, stated in ROW.findall(doc.read_text(encoding="utf-8")):
        module_dir = root / module.replace(":", "/")
        if not (module_dir / "src").is_dir():
            problems.append(f"{module}: listed in {doc.name} but {module_dir} has no sources")
            continue
        actual = count_tests(module_dir)
        stated = int(stated)
        if stated > actual:
            problems.append(f"{module}: the doc says {stated} tests but only {actual} are annotated")
        elif stated < actual * (1 - tolerance):
            problems.append(f"{module}: the doc says {stated} tests but {actual} are annotated; update the row")
    return problems


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--doc", type=Path, default=None)
    parser.add_argument("--tolerance", type=float, default=0.25)
    args = parser.parse_args(argv)
    doc = args.doc or args.root / "docs/api-reference/module-maturity.md"
    problems = check(args.root, doc, args.tolerance)
    if problems:
        print("\n".join(problems), file=sys.stderr)
        return 1
    print(f"Checked the test counts in {doc.name}: all within tolerance")
    return 0


if __name__ == "__main__":
    sys.exit(main())
