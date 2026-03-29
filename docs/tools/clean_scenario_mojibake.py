"""
Normalize list lines that start with corrupted emoji / multi-encoded UTF-8 before English text.
Run from repo root: python docs/tools/clean_scenario_mojibake.py
"""
from __future__ import annotations

import re
from pathlib import Path

DOCS = Path(__file__).resolve().parents[1]
SKIP = {"_site", "tools"}


def _looks_like_mojibake_prefix(rest: str, max_scan: int = 48) -> bool:
    """Only strip when the line clearly starts with corrupted UTF-8 (not normal markdown)."""
    head = rest[:max_scan]
    if "\ufffd" in head:
        return True
    if "Ã" in head or "Å" in head:
        return True
    # Common double-encoded emoji / dash debris
    if "â" in head and any(x in head for x in ("€", "œ", "€", "¦", "”", "•")):
        return True
    return False


def clean_leading_garbage_bullet(line: str) -> str:
    m = re.match(r"^(\s*-\s+)(.*)$", line)
    if not m:
        return line
    prefix, rest = m.group(1), m.group(2)
    # Skip markdown checkboxes / quotes
    if rest.startswith("`") or rest.startswith("*"):
        return line
    if not _looks_like_mojibake_prefix(rest):
        return line
    # Find first basic Latin letter or digit starting a word
    for i, ch in enumerate(rest):
        if ch.isascii() and ch.isalnum():
            return prefix + rest[i:]
    return line


def process_file(path: Path) -> bool:
    text = path.read_text(encoding="utf-8")
    lines = text.splitlines(keepends=True)
    new_lines = []
    changed = False
    for ln in lines:
        stripped = ln.rstrip("\r\n")
        nl = "\r\n" if ln.endswith("\r\n") else "\n" if ln.endswith("\n") else ""
        core = stripped
        new_core = clean_leading_garbage_bullet(core)
        if new_core != core:
            changed = True
        new_lines.append(new_core + nl)
    if changed:
        path.write_text("".join(new_lines), encoding="utf-8")
    return changed


def main() -> None:
    n = 0
    for p in sorted(DOCS.rglob("*.md")):
        if any(part in SKIP for part in p.parts):
            continue
        if process_file(p):
            print("fixed", p.relative_to(DOCS.parent))
            n += 1
    print("files updated:", n)


if __name__ == "__main__":
    main()
