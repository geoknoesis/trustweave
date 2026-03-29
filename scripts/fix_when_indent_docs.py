import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / "docs"

# Fix blocks where inner branches lost indentation (4 spaces instead of parent+4)
BAD = re.compile(
    r"""
    (?P<prefix>\n[\t ]+)
    (?P<head>val\s+\w+\s+=\s+when\s+\(val\s+res\s+=\s+[^\n]+\)\s+\{\n)
    [\t ]{4}(?P<succ>is\s+DidResolutionResult\.Success\s+->\s+res\.document\n)
    [\t ]{4}(?P<els>else\s+->\s+throw\s+IllegalStateException\([^\n]+\)\n)
    \}
    """,
    re.VERBOSE,
)


def repl(m: re.Match) -> str:
    prefix = m.group("prefix")
    head = m.group("head")
    succ = m.group("succ").rstrip("\n")
    els = m.group("els").rstrip("\n")
    inner_indent = prefix + "    "
    return f"{prefix}{head}{inner_indent}{succ}\n{inner_indent}{els}\n{prefix}}}"


def main() -> None:
    for path in sorted(ROOT.rglob("*.md")):
        rel = path.relative_to(ROOT)
        if str(rel).startswith("_site") or str(rel).startswith(".internal"):
            continue
        text = path.read_text(encoding="utf-8")
        new_text, n = BAD.subn(repl, text)
        if n:
            path.write_text(new_text, encoding="utf-8")
            print(f"{rel}: {n}")


if __name__ == "__main__":
    main()
