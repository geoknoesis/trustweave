"""Hoist indented org.trustweave imports to top of ```kotlin fences; fix bad DidResolutionResult."""
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / "docs"
FENCE = re.compile(r"(```kotlin\n)(.*?)(```)", re.DOTALL)
INDENT_IMPORT = re.compile(r"^[ \t]+import (org\.trustweave\.\S+)\s*$")
BAD_DID_RES = "import org.trustweave.trust.types.DidResolutionResult"
GOOD_DID_RES = "import org.trustweave.did.resolver.DidResolutionResult"
SPI_WALLET = "import org.trustweave.spi.services.WalletCreationOptionsBuilder"
FIX_WALLET = "import org.trustweave.wallet.services.WalletCreationOptionsBuilder"


def process_fence_body(body: str) -> tuple[str, bool]:
    lines = body.splitlines(keepends=False)
    hoisted: list[str] = []
    kept: list[str] = []
    for line in lines:
        m = INDENT_IMPORT.match(line)
        if m:
            stmt = f"import {m.group(1)}"
            if stmt == BAD_DID_RES:
                stmt = GOOD_DID_RES
            if stmt not in hoisted:
                hoisted.append(stmt)
            continue
        kept.append(line)

    if not hoisted:
        new_body = "\n".join(lines)
        if body.endswith("\n"):
            new_body += "\n"
        return new_body, False

    i = 0
    out: list[str] = []
    if kept and kept[0].startswith("package "):
        out.append(kept[0])
        i = 1
    while i < len(kept) and not kept[i].strip():
        out.append(kept[i])
        i += 1

    top_imports: list[str] = []
    while i < len(kept) and kept[i].startswith("import "):
        li = kept[i].strip()
        if li == BAD_DID_RES:
            li = GOOD_DID_RES
        if li == SPI_WALLET:
            li = FIX_WALLET
        if li not in top_imports:
            top_imports.append(li)
        i += 1

    for h in hoisted:
        if h == BAD_DID_RES:
            h = GOOD_DID_RES
        if h == "import org.trustweave.spi.services.WalletCreationOptionsBuilder":
            h = FIX_WALLET
        if h not in top_imports:
            top_imports.append(h)

    out.extend(top_imports)
    if i < len(kept) and kept[i].strip():
        out.append("")
    out.extend(kept[i:])
    new_body = "\n".join(out)
    if body.endswith("\n"):
        new_body += "\n"
    return new_body, True


def fix_orphan_trustweave_imports(body: str) -> str:
    """Move column-0 `import org.trustweave...` lines out of function bodies to the import block."""
    lines = body.splitlines(keepends=False)
    code_start = None
    for idx, line in enumerate(lines):
        s = line.strip()
        if not s or s.startswith("//"):
            continue
        if s.startswith("package "):
            continue
        if line.startswith("import "):
            continue
        code_start = idx
        break
    if code_start is None:
        return body
    head = lines[:code_start]
    tail = lines[code_start:]
    orphans = [ln for ln in tail if ln.startswith("import org.trustweave")]
    tail2 = [ln for ln in tail if not ln.startswith("import org.trustweave")]
    if not orphans:
        return body

    pkg: list[str] = []
    i = 0
    if head and head[0].startswith("package "):
        pkg.append(head[0])
        i = 1
    blanks1: list[str] = []
    while i < len(head) and not head[i].strip():
        blanks1.append(head[i])
        i += 1
    imports: list[str] = []
    while i < len(head) and head[i].startswith("import "):
        imports.append(head[i])
        i += 1
    rest_head = head[i:]
    exist = set(imports)
    for o in orphans:
        if o not in exist:
            imports.append(o)
            exist.add(o)
    out = pkg + blanks1 + imports
    if rest_head:
        if rest_head[0].strip():
            out.append("")
        out.extend(rest_head)
    if tail2:
        if out and out[-1].strip() and tail2[0].strip():
            out.append("")
        out.extend(tail2)
    res = "\n".join(out)
    if body.endswith("\n"):
        res += "\n"
    return res


def maybe_add_testkit(body: str) -> str:
    if "IN_MEMORY" not in body and "KEY)" not in body and "KEY " not in body:
        return body
    if "testkit.services" in body:
        return body
    lines = body.splitlines(keepends=False)
    insert_at = 0
    if lines and lines[0].startswith("package "):
        insert_at = 1
    while insert_at < len(lines) and not lines[insert_at].strip():
        insert_at += 1
    while insert_at < len(lines) and lines[insert_at].startswith("import "):
        insert_at += 1
    imp = "import org.trustweave.testkit.services.*"
    if imp in body:
        return body
    lines.insert(insert_at, imp)
    out = "\n".join(lines)
    if body.endswith("\n"):
        out += "\n"
    return out


def process_file(text: str) -> str:
    changed = False

    def repl(m: re.Match[str]) -> str:
        nonlocal changed
        prefix, body, suffix = m.group(1), m.group(2), m.group(3)
        new_body, c = process_fence_body(body)
        if c:
            changed = True
        new_body = fix_orphan_trustweave_imports(new_body)
        new_body = maybe_add_testkit(new_body)
        return prefix + new_body + suffix

    out = FENCE.sub(repl, text)
    if changed or out != text:
        pass
    return out


def main() -> None:
    for path in sorted(ROOT.rglob("*.md")):
        if "_site" in path.parts:
            continue
        text = path.read_text(encoding="utf-8")
        new = process_file(text)
        new = new.replace(BAD_DID_RES, GOOD_DID_RES)
        new = new.replace(SPI_WALLET, FIX_WALLET)
        if new != text:
            path.write_text(new, encoding="utf-8")
            print(path.relative_to(ROOT))


if __name__ == "__main__":
    main()
