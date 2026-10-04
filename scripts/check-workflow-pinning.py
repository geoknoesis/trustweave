"""Fail CI when a workflow action is not pinned, or a job lacks a permissions scope or a timeout.

A mutable tag (``@v4``) lets whoever controls the tag change what runs with the
repository's token. A workflow with no ``permissions:`` block inherits the
repository default scope, which is usually broader than the job needs. A job without
``timeout-minutes`` can hang for the 6-hour default. A ``run:`` block that pipes into ``tee`` without
``set -o pipefail`` reports ``tee``'s exit status and hides a failing command in front of it.
"""
import argparse
import re
import sys
from pathlib import Path

# `uses:` values that reference another workflow or a local action in this repository.
LOCAL = ("./", ".github/")
DOCKER = "docker://"
SHA = re.compile(r"^[0-9a-f]{40}$")
USES = re.compile(r"^\s*(?:-\s*)?uses:\s*(\S+)")
# Top-level keys sit at column zero; job keys are indented two spaces under `jobs:`.
TOP_LEVEL = re.compile(r"^([A-Za-z_][\w-]*):")
JOB = re.compile(r"^  ([A-Za-z_][\w-]*):\s*$")
JOB_KEY = re.compile(r"^    ([A-Za-z_][\w-]*):")
RUN = re.compile(r"^(\s*)(?:-\s*)?run:\s*(.*)$")
PIPE_TEE = re.compile(r"\|\s*tee\b")
PIPEFAIL = re.compile(r"set\s+(?:-[A-Za-z]*\s+)*-o\s+pipefail|set\s+-[A-Za-z]*o\s+pipefail|set\s+-[A-Za-z]*e[A-Za-z]*o\s+pipefail")


def check_pipefail(name, lines):
    """Flag `run:` blocks that pipe into tee without enabling pipefail."""
    failures = []
    index = 0
    while index < len(lines):
        match = RUN.match(lines[index])
        if not match:
            index += 1
            continue
        indent = len(match.group(1))
        start = index
        block = [match.group(2)]
        index += 1
        while index < len(lines) and (not lines[index].strip() or len(lines[index]) - len(lines[index].lstrip()) > indent):
            block.append(lines[index])
            index += 1
        text = "\n".join(block)
        if PIPE_TEE.search(text) and not PIPEFAIL.search(text):
            failures.append(f"{name}:{start + 1}: run block pipes into tee without 'set -o pipefail'")
    return failures


def check_text(name, text):
    """Return a list of human-readable violations for one workflow document."""
    failures = []
    lines = text.splitlines()

    for number, line in enumerate(lines, start=1):
        match = USES.match(line)
        if not match:
            continue
        reference = match.group(1).strip("'\"")
        if reference.startswith(LOCAL) or reference.startswith(DOCKER):
            continue
        if "@" not in reference:
            failures.append(f"{name}:{number}: {reference} has no version reference")
            continue
        action, _, version = reference.rpartition("@")
        if not SHA.match(version):
            failures.append(f"{name}:{number}: {action} is pinned to '{version}', not a 40-character commit SHA")

    top_level = {TOP_LEVEL.match(line).group(1) for line in lines if TOP_LEVEL.match(line)}
    if "jobs" not in top_level:
        raise ValueError(f"{name}: no jobs block")
    workflow_scoped = "permissions" in top_level
    failures.extend(check_pipefail(name, lines))

    in_jobs = False
    job = None
    scoped = False
    timed = False

    def close_job():
        if job is not None and not scoped and not workflow_scoped:
            failures.append(f"{name}: job '{job}' has no permissions scope and the workflow declares none")
        if job is not None and not timed:
            failures.append(f"{name}: job '{job}' has no timeout-minutes")

    for line in lines:
        top = TOP_LEVEL.match(line)
        if top:
            close_job()
            in_jobs, job, scoped, timed = top.group(1) == "jobs", None, False, False
            continue
        if not in_jobs:
            continue
        started = JOB.match(line)
        if started:
            close_job()
            job, scoped, timed = started.group(1), False, False
            continue
        key = JOB_KEY.match(line)
        if key and key.group(1) == "permissions":
            scoped = True
        if key and key.group(1) == "timeout-minutes":
            timed = True
    close_job()
    return failures


def check(folder):
    documents = sorted(Path(folder).glob("*.yml")) + sorted(Path(folder).glob("*.yaml"))
    if not documents:
        raise ValueError(f"No workflows found under {folder}")
    failures = []
    for document in documents:
        failures.extend(check_text(document.name, document.read_text(encoding="utf-8-sig")))
    return failures, len(documents)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("folder", nargs="?", default=".github/workflows")
    args = parser.parse_args()
    try:
        failures, count = check(args.folder)
    except (OSError, ValueError) as error:
        print(f"Invalid workflow evidence: {error}", file=sys.stderr)
        sys.exit(1)
    for failure in failures:
        print(failure, file=sys.stderr)
    if failures:
        sys.exit(1)
    print(f"Checked {count} workflows; every action is SHA-pinned, every job is permission-scoped and time-limited, and no tee pipe lacks pipefail")
