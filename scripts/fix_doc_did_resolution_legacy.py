"""
Remove legacy doc-only DidResolutionResult.getOrThrow() helpers and fix common call patterns.
Only touches docs/*.md, excluding docs/_site and docs/.internal.
"""
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DOCS = ROOT / "docs"

# Block helpers (optional leading whitespace)
HELPER_BLOCK = re.compile(
    r"""
    \n[\t ]*//\s*Helper\s+extension\s+for\s+resolution\s+results\s*\n
    [\t ]*fun\s+DidResolutionResult\.getOrThrow\(\)\s*=\s*when\s*\(this\)\s*\{\s*\n
    [\t ]*is\s+DidResolutionResult\.Success\s*->\s*this\.document\s*\n
    [\t ]*else\s*->\s*throw\s+IllegalStateException\([^)]+\)\s*\n
    [\t ]*\}\s*\n
    """,
    re.VERBOSE,
)

HELPER_BLOCK_ALT = re.compile(
    r"""
    \n[\t ]*//\s*Helper\s*\([^)]*\)\s*\n
    [\t ]*fun\s+DidResolutionResult\.getOrThrow\(\)\s*=\s*when\s*\(this\)\s*\{\s*\n
    [\t ]*is\s+DidResolutionResult\.Success\s*->\s*this\.document\s*\n
    [\t ]*else\s*->\s*throw\s+IllegalStateException\([^)]+\)\s*\n
    [\t ]*\}\s*\n
    """,
    re.VERBOSE,
)

# val x = <tw>.resolveDid(arg).getOrThrow()
ASSIGN_RESOLVE = re.compile(
    r"val\s+(\w+)\s*=\s*(\w+)\.resolveDid\(([^)]+)\)\.getOrThrow\(\)"
)

# <tw>.resolveDid(arg).getOrThrow() as expression (assign to implicit)
EXPR_RESOLVE = re.compile(r"(\w+)\.resolveDid\(([^)]+)\)\.getOrThrow\(\)")


def process(content: str) -> tuple[str, int]:
    n = 0
    orig = content

    for rx in (HELPER_BLOCK, HELPER_BLOCK_ALT):
        new_content, k = rx.subn("\n", content)
        if k:
            n += k
            content = new_content

    def sub_assign(m: re.Match) -> str:
        var, tw, arg = m.group(1), m.group(2), m.group(3)
        return (
            f"val {var} = when (val res = {tw}.resolveDid({arg})) {{\n"
            f"    is DidResolutionResult.Success -> res.document\n"
            f"    else -> throw IllegalStateException(res.errorMessage ?: \"Failed to resolve DID\")\n"
            f"}}"
        )

    content, k = ASSIGN_RESOLVE.subn(sub_assign, content)
    n += k

    def sub_expr(m: re.Match) -> str:
        tw, arg = m.group(1), m.group(2)
        return (
            f"when (val res = {tw}.resolveDid({arg})) {{\n"
            f"    is DidResolutionResult.Success -> res.document\n"
            f"    else -> throw IllegalStateException(res.errorMessage ?: \"Failed to resolve DID\")\n"
            f"}}"
        )

    content, k = EXPR_RESOLVE.subn(sub_expr, content)
    n += k

    # Add errorMessage import hint once per file if we touched resolution and import missing
    if content != orig and "errorMessage" in content and "org.trustweave.did.resolver.errorMessage" not in content:
        # Insert after first DidResolutionResult import if present
        if "import org.trustweave.did.resolver.DidResolutionResult" in content:
            content = content.replace(
                "import org.trustweave.did.resolver.DidResolutionResult",
                "import org.trustweave.did.resolver.DidResolutionResult\nimport org.trustweave.did.resolver.errorMessage",
                1,
            )
            n += 1

    return content, n


def main() -> None:
    total = 0
    for path in sorted(DOCS.rglob("*.md")):
        rel = path.relative_to(DOCS)
        if str(rel).startswith("_site") or str(rel).startswith(".internal"):
            continue
        text = path.read_text(encoding="utf-8")
        new_text, k = process(text)
        if new_text != text:
            path.write_text(new_text, encoding="utf-8")
            total += k
            print(f"{rel}: {k} change(s)")
    print(f"Total change operations: {total}")


if __name__ == "__main__":
    main()
