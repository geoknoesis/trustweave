"""
Normalize `is org.trustweave.credential.results.IssuanceResult.Success` -> `is IssuanceResult.Success`
and ensure `import org.trustweave.credential.results.IssuanceResult` exists in the first Kotlin fence
that contains executable TrustWeave code (not Gradle `dependencies {` blocks).

Safe heuristic: insert import only in ```kotlin fences whose body contains `TrustWeave.build` or
`trustWeave.issue` (avoids matching dependency snippets that only mention TrustWeave in comments).
"""
from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / "docs"
FQCN_SUCCESS = "is org.trustweave.credential.results.IssuanceResult.Success"
SHORT_SUCCESS = "is IssuanceResult.Success"
IMPORT_LINE = "import org.trustweave.credential.results.IssuanceResult"


def first_safe_kotlin_block(content: str) -> int | None:
    """Return index (0-based) of first char inside a safe ```kotlin block, or None."""
    pos = 0
    while True:
        start = content.find("```kotlin", pos)
        if start == -1:
            return None
        nl = content.find("\n", start)
        if nl == -1:
            return None
        body_start = nl + 1
        end = content.find("```", body_start)
        if end == -1:
            return None
        body = content[body_start:end]
        first_nonempty = next((ln.strip() for ln in body.splitlines() if ln.strip()), "")
        if first_nonempty.startswith("dependencies {"):
            pos = end + 3
            continue
        if "TrustWeave.build" in body or "trustWeave.issue" in body or "fun main()" in body:
            return body_start
        pos = end + 3


def ensure_import(content: str) -> str:
    if IMPORT_LINE in content:
        return content
    idx = first_safe_kotlin_block(content)
    if idx is None:
        return content
    return content[:idx] + IMPORT_LINE + "\n" + content[idx:]


def main() -> None:
    for path in sorted(ROOT.rglob("*.md")):
        text = path.read_text(encoding="utf-8")
        if FQCN_SUCCESS not in text:
            continue
        new = text.replace(FQCN_SUCCESS, SHORT_SUCCESS)
        new = ensure_import(new)
        if new != text:
            path.write_text(new, encoding="utf-8")
            print(path.relative_to(ROOT))


if __name__ == "__main__":
    main()
