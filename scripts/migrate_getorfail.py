"""
One-shot migration: replace legacy .getOrFail() call sites with typed getOrThrow* APIs.
Run from repo root: python scripts/migrate_getorfail.py
"""
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def find_matching_brace(s: str, open_idx: int) -> int:
    assert s[open_idx] == "{"
    depth = 0
    i = open_idx
    while i < len(s):
        if s[i] == "{":
            depth += 1
        elif s[i] == "}":
            depth -= 1
            if depth == 0:
                return i
        i += 1
    return -1


def replace_method_block(text: str, method: str, replacement: str) -> str:
    """Replace `.method { ... }.getOrFail()` -> `.method { ... }` + replacement (includes leading dot)."""
    pattern = re.compile(r"\." + re.escape(method) + r"\s*\{")
    out: list[str] = []
    last = 0
    i = 0
    while True:
        m = pattern.search(text, i)
        if not m:
            break
        brace_start = m.end() - 1
        close = find_matching_brace(text, brace_start)
        if close < 0:
            i = m.end()
            continue
        tail = text[close + 1 : close + 1 + 13]
        if tail.startswith(".getOrFail()"):
            out.append(text[last : m.start()])
            out.append(text[m.start() : close + 1])
            out.append(replacement)
            last = close + 1 + 12
            i = last
        else:
            i = close + 1
    out.append(text[last:])
    return "".join(out)


def migrate_content(text: str) -> str:
    # Order matters: longer / more specific method names first
    text = re.sub(
        r"([\w.]+)\.createDidWithKey\s*\(\s*\)\s*\.getOrFail\(\)",
        r"\1.createDidWithKey().getOrThrow()",
        text,
    )
    text = replace_method_block(text, "createDidWithKey", ".getOrThrow()")
    text = re.sub(
        r"([\w.]+)\.createDid\s*\(\s*\)\s*\.getOrFail\(\)",
        r"\1.createDid().getOrThrowDid()",
        text,
    )
    text = replace_method_block(text, "presentationResult", ".getOrThrow()")
    text = replace_method_block(text, "issue", ".getOrThrow()")
    text = replace_method_block(text, "wallet", ".getOrThrow()")
    text = replace_method_block(text, "createDid", ".getOrThrowDid()")
    return text


def ensure_imports(text: str) -> str:
    """Strip legacy imports; insert getOrThrow* imports after package when needed."""
    if "package " not in text:
        return text
    lines = text.splitlines(keepends=True)
    pkg_end = 0
    for idx, line in enumerate(lines):
        if line.startswith("package "):
            pkg_end = idx + 1
            break
    insert_at = pkg_end
    while insert_at < len(lines) and lines[insert_at].strip() == "":
        insert_at += 1
    needed: list[str] = []
    if ".getOrThrowDid()" in text and "import org.trustweave.trust.types.getOrThrowDid" not in text:
        needed.append("import org.trustweave.trust.types.getOrThrowDid\n")
    if ".getOrThrow()" in text:
        if "import org.trustweave.credential.results.getOrThrow" not in text:
            needed.append("import org.trustweave.credential.results.getOrThrow\n")
        if "import org.trustweave.trust.types.getOrThrow" not in text:
            needed.append("import org.trustweave.trust.types.getOrThrow\n")
    new_lines = list(lines[:insert_at])
    if needed:
        if new_lines and new_lines[-1].strip() != "":
            new_lines.append("\n")
        new_lines.extend(needed)
        if new_lines and not new_lines[-1].endswith("\n"):
            new_lines[-1] += "\n"
    rest = "".join(lines[insert_at:])
    rest = rest.replace("import org.trustweave.testkit.getOrFail\n", "")
    rest = rest.replace("import org.trustweave.trust.types.getOrFail\n", "")
    return "".join(new_lines) + rest


def main() -> None:
    for path in ROOT.rglob("*.kt"):
        if "build" in path.parts or ".gradle" in path.parts:
            continue
        raw = path.read_text(encoding="utf-8")
        if "getOrFail" not in raw:
            continue
        migrated = migrate_content(raw)
        migrated = ensure_imports(migrated)
        if migrated != raw:
            path.write_text(migrated, encoding="utf-8", newline="\n")
            print("updated", path.relative_to(ROOT))


if __name__ == "__main__":
    main()
