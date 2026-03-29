"""
Fix list lines where an opening ** was stripped, leaving '- Foo**' or '- Foo** rest'.
Only touches lines that end with ** (optional whitespace) and contain exactly two '*' chars total.
"""
from __future__ import annotations

import re
from pathlib import Path

DOCS = Path(__file__).resolve().parents[1]
SKIP = {"_site"}


def fix_line(line: str) -> str:
    stripped = line.rstrip("\r\n")
    if "*" not in stripped:
        return line
    if stripped.count("*") != 2:
        return line
    if not stripped.endswith("**"):
        return line
    m = re.match(r"^(\s*-\s+)(.+)\*\*\s*$", stripped)
    if not m:
        return line
    prefix, inner = m.group(1), m.group(2)
    if inner.startswith("**"):
        return line
    nl = "\r\n" if line.endswith("\r\n") else "\n" if line.endswith("\n") else ""
    return f"{prefix}**{inner}**{nl}"


def main() -> None:
    n = 0
    for p in sorted(DOCS.rglob("*.md")):
        if any(part in SKIP for part in p.parts):
            continue
        text = p.read_text(encoding="utf-8")
        lines = text.splitlines(keepends=True)
        out = []
        changed = False
        for ln in lines:
            new_ln = fix_line(ln)
            if new_ln != ln:
                changed = True
            out.append(new_ln)
        if changed:
            p.write_text("".join(out), encoding="utf-8")
            print("fixed", p.relative_to(DOCS.parent))
            n += 1
    print("files:", n)


if __name__ == "__main__":
    main()
