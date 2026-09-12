"""Fail CI when a suspend function can absorb a CancellationException.

Cooperative cancellation arrives as an exception, so a broad `catch` around suspending work turns
"the caller cancelled" into "the operation failed" and the coroutine keeps going. The fix is to
rethrow it first — but *first* is load-bearing in a way that is easy to get wrong:

    kotlinx.coroutines.CancellationException
      = java.util.concurrent.CancellationException
      : IllegalStateException : RuntimeException : Exception

so a `catch (e: IllegalStateException)` ahead of the guard swallows cancellation before the guard
can ever run. This checks both that a guard exists and that nothing catchable precedes it.
"""
import argparse
import json
import os
import re
import sys
from pathlib import Path

PRUNE = {"build", "node_modules", ".git", ".gradle", "_site", ".claude", ".trunk", ".idea"}
POLICY = "config/cancellation-guards.json"
CATCH = re.compile(r"catch\s*\(\s*\w+\s*:\s*(?P<type>[\w.]+)\s*\)\s*\{")
# `catch (e: Throwable) { if (e is CancellationException) throw e; ... }` is equally correct:
# the clause catches cancellation but hands it straight back.
RETHROWS_IN_BODY = re.compile(r"CancellationException[\s\S]{0,160}?\bthrow\b")
# Every type on CancellationException's own hierarchy: catching one of these catches cancellation.
CATCHES_CANCELLATION = {"Exception", "Throwable", "RuntimeException", "IllegalStateException"}
GUARD = "CancellationException"


def balanced_end(text, start):
    depth, i = 1, start
    while i < len(text) and depth:
        if text[i] == "{":
            depth += 1
        elif text[i] == "}":
            depth -= 1
        i += 1
    return i


def enclosing_is_suspend(text, position):
    head = text.rfind("fun ", 0, position)
    if head < 0:
        return False
    return "suspend" in text[max(0, head - 80):head]


def groups(text):
    """Clauses of one try: consecutive catches separated only by whitespace or a closing brace."""
    clauses = []
    for match in CATCH.finditer(text):
        end = balanced_end(text, match.end())
        clauses.append({
            "start": match.start(),
            "end": end,
            "type": match.group("type").split(".")[-1],
            "line": text[:match.start()].count("\n") + 1,
            "rethrows": bool(RETHROWS_IN_BODY.search(text[match.end():end - 1])),
        })
    out, current = [], []
    for clause in clauses:
        if current and text[current[-1]["end"]:clause["start"]].strip() in ("", "}"):
            current.append(clause)
        else:
            if current:
                out.append(current)
            current = [clause]
    if current:
        out.append(current)
    return out


def scan(root):
    root = Path(root)
    findings = []
    for folder, dirs, files in os.walk(root):
        dirs[:] = [d for d in dirs if d not in PRUNE]
        if os.sep + "src" + os.sep + "main" not in folder:
            continue
        for name in files:
            if not name.endswith(".kt"):
                continue
            full = Path(folder) / name
            relative = full.relative_to(root).as_posix()
            text = full.read_text(encoding="utf-8", errors="replace")
            if "suspend" not in text:
                continue
            for group in groups(text):
                if not enclosing_is_suspend(text, group[0]["start"]):
                    continue
                catching = [c for c in group if c["type"] in CATCHES_CANCELLATION]
                if not catching:
                    continue
                guard = next(
                    (i for i, c in enumerate(group) if GUARD in c["type"] or c["rethrows"]),
                    None,
                )
                first = group.index(catching[0])
                if guard is None:
                    findings.append({"file": relative, "line": catching[0]["line"],
                                     "problem": f"catch ({catching[0]['type']}) with no {GUARD} guard"})
                elif guard > first:
                    findings.append({"file": relative, "line": group[guard]["line"],
                                     "problem": f"{GUARD} guard is unreachable: catch "
                                                f"({group[first]['type']}) on line {group[first]['line']} "
                                                f"already catches it"})
    return findings


def check(root):
    root = Path(root)
    policy = json.loads((root / POLICY).read_text(encoding="utf-8-sig"))
    recorded = policy["recorded"]
    allowed = {(entry["file"], entry["line"]) for entry in policy["allowed"]}
    if len(allowed) != recorded:
        raise ValueError(f"{POLICY}: recorded {recorded} does not match its own list of {len(allowed)}")
    findings = scan(root)
    failures = [f"{f['file']}:{f['line']}: {f['problem']}"
                for f in findings if (f["file"], f["line"]) not in allowed]
    if len(findings) > recorded:
        failures.append(f"Unguarded broad catches grew from {recorded} to {len(findings)}")
    return failures, len(findings)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("root", nargs="?", default=".")
    args = parser.parse_args()
    try:
        failures, total = check(args.root)
    except (OSError, ValueError, KeyError, json.JSONDecodeError) as error:
        print(f"Invalid cancellation evidence: {error}", file=sys.stderr)
        sys.exit(1)
    for failure in failures:
        print(failure, file=sys.stderr)
    if failures:
        sys.exit(1)
    print(f"Every broad catch in a suspend function rethrows CancellationException first ({total} recorded)")
