#!/usr/bin/env python3
"""
Replace legacy TrustWeave.create / TrustWeave.create { } with quickStart / build / from.

Safe rules:
  - Never touches createDid, createStatusList, createKey, createDidAndIssue, etc.
  - TrustWeave.create()     -> TrustWeave.quickStart()
  - TrustWeave.create(cfg)  -> TrustWeave.from(cfg)  (single identifier or parenthesized expr)
  - TrustWeave.create {     -> TrustWeave.build {

Inside ```kotlin / ```kotlin ... fenced blocks only:
  - Line: this.kms = X  -> customKms(X)
  - Line: kms = X       -> customKms(X)  (assignment at line level)
  - Line: walletFactory = X -> factories(walletFactory = X)

Does not migrate legacy inner DSL automatically. For didMethods / blockchains / blockchain { register },
use did { method(...) } and anchor { chain(...) { provider(...); options { ... } } }; see
docs/integrations/ and docs/scenarios/ for reference snippets.
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]

# Order matters: more specific first
PATTERNS: list[tuple[re.Pattern[str], str]] = [
    # TrustWeave.create(someConfig) — single arg, common name
    (
        re.compile(r"TrustWeave\.create\s*\(\s*([a-zA-Z_][a-zA-Z0-9_]*)\s*\)"),
        r"TrustWeave.from(\1)",
    ),
    # TrustWeave.create() with optional whitespace
    (
        re.compile(r"TrustWeave\.create\s*\(\s*\)"),
        "TrustWeave.quickStart()",
    ),
    # TrustWeave.create {  (builder)
    (
        re.compile(r"TrustWeave\.create\s*\{"),
        "TrustWeave.build {",
    ),
]

# Inside Kotlin fences: line-based KMS / wallet factory
_KMS_THIS = re.compile(r"^(\s*)this\.kms\s*=\s*(.+?)\s*$")
_KMS_ASSIGN = re.compile(r"^(\s*)kms\s*=\s*(.+?)\s*$")
_WALLET = re.compile(r"^(\s*)walletFactory\s*=\s*(.+?)\s*$")


def _paren_balanced(s: str) -> bool:
    depth = 0
    for ch in s:
        if ch == "(":
            depth += 1
        elif ch == ")":
            depth -= 1
            if depth < 0:
                return False
    return depth == 0


def _transform_kotlin_fence(body: str) -> str:
    lines = body.splitlines(keepends=True)
    out: list[str] = []
    for line in lines:
        m = _KMS_THIS.match(line.rstrip("\r\n"))
        if m:
            indent, expr = m.group(1), m.group(2).rstrip()
            if "(" in expr and not _paren_balanced(expr):
                out.append(line)
                continue
            nl = "\n" if line.endswith("\n") else ""
            cr = "\r" if line.endswith("\r\n") else ""
            out.append(f"{indent}customKms({expr}){cr}{nl}" if cr else f"{indent}customKms({expr}){nl}")
            continue
        m = _KMS_ASSIGN.match(line.rstrip("\r\n"))
        if m:
            indent, expr = m.group(1), m.group(2).rstrip()
            if "(" in expr and not _paren_balanced(expr):
                out.append(line)
                continue
            nl = "\n" if line.endswith("\n") else ""
            cr = "\r" if line.endswith("\r\n") else ""
            out.append(f"{indent}customKms({expr}){cr}{nl}" if cr else f"{indent}customKms({expr}){nl}")
            continue
        m = _WALLET.match(line.rstrip("\r\n"))
        if m:
            indent, expr = m.group(1), m.group(2).rstrip()
            nl = "\n" if line.endswith("\n") else ""
            cr = "\r" if line.endswith("\r\n") else ""
            out.append(
                f"{indent}factories(walletFactory = {expr}){cr}{nl}"
                if cr
                else f"{indent}factories(walletFactory = {expr}){nl}"
            )
            continue
        out.append(line)
    return "".join(out)


_FENCE_START = re.compile(r"^```(?:kotlin|kt)\s*$", re.IGNORECASE)


def transform_markdown(text: str) -> str:
    lines = text.splitlines(keepends=True)
    result: list[str] = []
    i = 0
    while i < len(lines):
        line = lines[i]
        if _FENCE_START.match(line.rstrip("\r\n")):
            result.append(line)
            i += 1
            fence_lines: list[str] = []
            while i < len(lines):
                if lines[i].startswith("```"):
                    body = "".join(fence_lines)
                    for pat, repl in PATTERNS:
                        body = pat.sub(repl, body)
                    body = _transform_kotlin_fence(body)
                    result.append(body)
                    result.append(lines[i])
                    i += 1
                    break
                fence_lines.append(lines[i])
                i += 1
            continue
        chunk = line
        for pat, repl in PATTERNS:
            chunk = pat.sub(repl, chunk)
        result.append(chunk)
        i += 1
    return "".join(result)


def transform_plain(text: str) -> str:
    out = text
    for pat, repl in PATTERNS:
        out = pat.sub(repl, out)
    return out


def should_skip_dir(path: Path) -> bool:
    p = path.as_posix()
    if "/_site/" in p or p.endswith("/_site"):
        return True
    if "/build/" in p:
        return True
    if "/.gradle/" in p:
        return True
    return False


def main() -> int:
    roots = [
        REPO_ROOT / "docs",
        REPO_ROOT / "distribution",
        REPO_ROOT / "README.md",
    ]
    changed = 0
    files: list[Path] = []
    for r in roots:
        if r.is_file():
            files.append(r)
        elif r.is_dir():
            for p in r.rglob("*"):
                if should_skip_dir(p):
                    continue
                if p.suffix.lower() == ".md":
                    files.append(p)
                if p.suffix.lower() == ".kt" and "examples" in p.parts:
                    files.append(p)

    seen: set[Path] = set()
    for path in files:
        path = path.resolve()
        if path in seen:
            continue
        seen.add(path)
        if not path.is_file():
            continue
        raw = path.read_text(encoding="utf-8")
        if "TrustWeave.create" not in raw:
            continue
        if path.suffix.lower() == ".kt":
            if "import org.trustweave.trust.TrustWeave" not in raw:
                continue
        if path.suffix.lower() == ".md":
            new = transform_markdown(raw)
        else:
            new = transform_plain(raw)
        if new != raw:
            path.write_text(new, encoding="utf-8", newline="\n")
            print(path.relative_to(REPO_ROOT))
            changed += 1

    print(f"Updated {changed} files.", file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
