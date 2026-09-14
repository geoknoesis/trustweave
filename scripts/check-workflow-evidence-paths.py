#!/usr/bin/env python3
"""Every module evidence path a workflow reads must be a declared Gradle task output.

This defect class has cost three separate CI failures, and each time the symptom was the same:
a workflow read a path the build no longer wrote, on a commit whose tests all passed.

Two distinct ways to get there, and this gate closes both.

1. **Undeclared output.** Gradle's build cache restores a task's *declared* outputs. A file a test
   writes outside them is not part of the cache entry, so a cached test run leaves the JUnit XML
   in place and the evidence absent — and the step that reads it fails on a commit that changed
   nothing. Declaring the directory with `outputs.dir(...)` makes the evidence travel with the
   cache entry that produced it.

2. **Stale path.** The build layout moved and the workflow did not, so the workflow reads a path
   no module writes at all.

So: for each `build/<module>/...` path a workflow mentions, find the module it belongs to and
require that module's build script to declare an output covering it. Root-level `build/reports/`
paths are exempt — those are written by scripts and by Gradle's own reporting, not by a cacheable
module task.
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

WORKFLOWS = ("ci.yml", "release-evidence.yml")
BUILD_PATH = re.compile(r"build/[A-Za-z0-9_./-]+")
# `outputs.dir(evidence)` / `outputs.file(diagnostics)` — the local is defined from
# layout.buildDirectory just above, so the declaration is matched by the directory name it carries.
DECLARES_OUTPUT = re.compile(r"outputs\.(?:dir|file|files)\s*\(")


def modules(root: Path) -> list[str]:
    """Module directories, longest first, so the most specific one wins."""
    found = []
    for script in root.rglob("build.gradle.kts"):
        if "build" in script.parts or "node_modules" in script.parts:
            continue
        relative = script.parent.relative_to(root).as_posix()
        if relative != ".":
            found.append(relative)
    return sorted(found, key=len, reverse=True)


def evidence_paths(root: Path) -> dict[str, set[str]]:
    """Maps each workflow file to the build paths it mentions."""
    referenced = {}
    for name in WORKFLOWS:
        text = (root / ".github" / "workflows" / name).read_text(encoding="utf-8")
        referenced[name] = set(BUILD_PATH.findall(text))
    return referenced


def check(root: Path) -> list[str]:
    root = Path(root)
    known = modules(root)
    failures = []
    for workflow, paths in evidence_paths(root).items():
        for path in sorted(paths):
            remainder = path[len("build/") :]
            module = next((name for name in known if remainder.startswith(name + "/")), None)
            if module is None:
                # Root-level reports; nothing module-scoped to declare.
                continue
            inner = remainder[len(module) + 1 :]
            segment = inner.split("/", 1)[0]
            if not segment:
                continue
            script = root / module / "build.gradle.kts"
            source = script.read_text(encoding="utf-8")
            if not DECLARES_OUTPUT.search(source):
                failures.append(
                    f"{workflow} reads {path}, but {module}/build.gradle.kts declares no task output. "
                    f"A file written outside a task's declared outputs is not restored on a build-cache "
                    f"hit, so this step fails on a commit whose tests all passed."
                )
            elif segment not in source:
                failures.append(
                    f"{workflow} reads {path}, but {module}/build.gradle.kts never mentions "
                    f"'{segment}'. Either the build layout moved and the workflow did not, or the "
                    f"output is declared somewhere this gate cannot see."
                )
    return failures


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parent.parent)
    args = parser.parse_args()
    failures = check(args.root)
    if failures:
        print("Workflow evidence paths are not backed by declared build outputs:")
        for failure in failures:
            print(f"- {failure}")
        return 1
    print("Every module evidence path a workflow reads is a declared Gradle task output")
    return 0


if __name__ == "__main__":
    sys.exit(main())
