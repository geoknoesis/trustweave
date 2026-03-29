"""Replace known UTF-8 mojibake sequences in docs with ASCII markers (code samples)."""
from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / "docs"

# Double-encoded / corrupted check and cross markers (common in scenario println output)
SUCCESS = b"\xc3\x83\xc2\xa2\xc3\x85\xe2\x80\x9c\xe2\x80\xa6"
FAIL = b"\xc3\x83\xc2\xa2\xc3\x82\xc2\x9d\xc3\x85\xe2\x80\x99"
ISC_BAD = b"(ISC)\xc3\x83\xe2\x80\x9a\xc3\x82\xc2\xb2"
ISC_OK = b"(ISC)\xc2\xb2"  # (ISC)²

REPLACEMENTS: list[tuple[bytes, bytes]] = [
    (SUCCESS, b"[OK]"),
    (FAIL, b"[FAIL]"),
    (ISC_BAD, ISC_OK),
]


def main() -> None:
    for path in sorted(ROOT.rglob("*.md")):
        if "_site" in path.parts:
            continue
        data = path.read_bytes()
        new = data
        for old, rep in REPLACEMENTS:
            new = new.replace(old, rep)
        if new != data:
            path.write_bytes(new)
            print(path.relative_to(ROOT))


if __name__ == "__main__":
    main()
