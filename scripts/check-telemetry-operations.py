#!/usr/bin/env python3
"""Every declared telemetry Operation must have a call site that emits it.

An Operation value nothing ever reports is worse than no value at all. A host charts
`trustweave_library_operations_total` by operation, sees a permanent zero series, and cannot tell
"the SDK is not instrumented here" from "this never happened" — so the enum quietly becomes a list
of intentions rather than a contract about what the library reports.

This gate keeps the two in step. A value may be added to the enum in the same commit that adds an
emitter, and a value whose last emitter is deleted fails the build rather than decaying into a zero
series. Removing the value is always an acceptable fix: an operation the SDK does not perform has
no business being declared.

Emitters are `Telemetry.measure(Operation.X` and `Telemetry.rejected(Operation.X`, found in main
sources only. A value emitted from test sources alone does not count: a host does not run tests.
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

ENUM = "common/src/main/kotlin/org/trustweave/core/telemetry/Telemetry.kt"
ENUM_BLOCK = re.compile(r"public enum class Operation \{(.*?)\n\}", re.DOTALL)
VALUE = re.compile(r"^\s*([A-Z][A-Z0-9_]*)\s*,?\s*$", re.MULTILINE)
# Telemetry.measure(Operation.X / Telemetry.rejected(\n    Operation.X — both spellings occur.
EMITTER = re.compile(r"Telemetry\s*\.\s*(?:measure|rejected)\s*\(\s*Operation\s*\.\s*([A-Z][A-Z0-9_]*)", re.DOTALL)


def declared_operations(root: Path) -> list[str]:
    source = (root / ENUM).read_text(encoding="utf-8-sig")
    block = ENUM_BLOCK.search(source)
    if not block:
        raise ValueError(f"{ENUM}: could not find the Operation enum")
    return VALUE.findall(block.group(1))


def emitted_operations(root: Path) -> dict[str, list[str]]:
    """Maps each emitted operation to the main-source files that emit it."""
    emitted: dict[str, list[str]] = {}
    for path in root.rglob("*.kt"):
        parts = path.parts
        # node_modules carries vendored Kotlin (React Native's ReactAndroid) laid out as
        # src/main/java, which would otherwise be walked as if it were ours.
        if "build" in parts or "node_modules" in parts or "src" not in parts:
            continue
        index = parts.index("src")
        if index + 1 >= len(parts) or parts[index + 1] != "main":
            continue
        for name in EMITTER.findall(path.read_text(encoding="utf-8", errors="replace")):
            emitted.setdefault(name, []).append(str(path.relative_to(root)).replace("\\", "/"))
    return emitted


def check(root: Path) -> list[str]:
    declared = declared_operations(root)
    emitted = emitted_operations(root)
    failures = []
    for name in declared:
        if name not in emitted:
            failures.append(
                f"Operation.{name} is declared but no main source emits it. "
                f"Add a Telemetry.measure or Telemetry.rejected call site, or remove the value: "
                f"a host charting it would see a permanent zero series."
            )
    for name in sorted(set(emitted) - set(declared)):
        failures.append(f"Operation.{name} is emitted but not declared in {ENUM}")
    return failures


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parent.parent)
    args = parser.parse_args()
    failures = check(args.root)
    if failures:
        print("Telemetry operations and their emitters disagree:")
        for failure in failures:
            print(f"- {failure}")
        return 1
    declared = declared_operations(args.root)
    print(f"{len(declared)} telemetry operations declared; every one has a main-source emitter")
    return 0


if __name__ == "__main__":
    sys.exit(main())
