"""Fix double-encoded emoji / mojibake in docs scenario println() and sample output lines."""
from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / "docs"

# Longer / more specific patterns first.
REPLACEMENTS: list[tuple[bytes, bytes]] = [
    (
        b'println("\\n\xc3\x83\xc2\xb0\xc3\x85\xc2\xb8"\xc3\xa2\xe2\x82\xac\xc5\xbe Firmware Rollback',
        b'println("\\n[rollback] Firmware Rollback',
    ),
    (
        b'println("\\n\xc3\x83\xc2\xb0\xc3\x85\xc2\xb8"\xc3\xa2\xe2\x82\xac\xc5\xbe Ticket Transfer',
        b'println("\\n[transfer] Ticket Transfer',
    ),
    (
        b'println("\\n\xc3\x83\xc2\xb0\xc3\x85\xc2\xb8"\xc3\xa2\xe2\x82\xac\xc2\xb9 ',
        b'println("\\n[insurer] ',
    ),
    (
        b'println("\\n\xc3\x83\xc2\xb0\xc3\x85\xc2\xb8\xc3\x85\xc2\xbd\xc3\x82\xc2\xab ',
        b'println("\\n[ticket] ',
    ),
    (
        b'println("\\n\xc3\x83\xc2\xb0\xc3\x85\xc2\xb8\xc3\x82\xc2\x8f\xc3\x82\xc2\xa2 ',
        b'println("\\n[employer] ',
    ),
    (
        b'println("\\n\xc3\x83\xc2\xb0\xc3\x85\xc2\xb8"\' ',
        b'println("\\n[privacy] ',
    ),
    (
        b'println("\\n\xc3\x83\xc2\xa2\xc3\x85\xe2\x80\x9c\xc3\x8b\xe2\x80\xa0\xc3\x83\xc2\xaf\xc3\x82\xc2\xb8\xc3\x82\xc2\x8f ',
        b'println("\\n[airline] ',
    ),
    (
        b'println("   \xc3\x83\xc2\xb0\xc3\x85\xc2\xb8\'\xc3\x82\xc2\xb0 ',
        b'println("   [payout] ',
    ),
    (
        b'println("\xc3\x83\xc2\xa2\xc3\x85\xc2\xa1\xc3\x82\xc2\xa0\xc3\x83\xc2\xaf\xc3\x82\xc2\xb8\xc3\x82\xc2\x8f ',
        b'println("[WARN] ',
    ),
    (
        b'println("   ${cred.id}: \xc3\x83\xc2\xa2\xc3\x85\xc2\xa1\xc3\x82\xc2\xa0\xc3\x83\xc2\xaf\xc3\x82\xc2\xb8\xc3\x82\xc2\x8f ',
        b'println("   ${cred.id}: [WARN] ',
    ),
    # Sample output (line starts)
    (
        b'\n\xc3\x83\xc2\xa2\xc3\x85\xe2\x80\x9c\xc3\x8b\xe2\x80\xa0\xc3\x83\xc2\xaf\xc3\x82\xc2\xb8\xc3\x82\xc2\x8f ',
        b'\n[airline] ',
    ),
    (
        b'\n\xc3\x83\xc2\xb0\xc3\x85\xc2\xb8"\xc3\x85\xc2\xa0 ',
        b'\n[stats] ',
    ),
    (
        b'\n\xc3\x83\xc2\xb0\xc3\x85\xc2\xb8"\xc3\x82\xc2\x8d ',
        b'\n[verify] ',
    ),
    (
        b'\n\xc3\x83\xc2\xb0\xc3\x85\xc2\xb8"\xe2\x80\xa6 ',
        b'\n[expiry] ',
    ),
    (
        b'\n\xc3\x83\xc2\xb0\xc3\x85\xc2\xb8"\xc3\xa2\xe2\x82\xac\xc2\xb9 ',
        b'\n[insurer] ',
    ),
    (
        b'\n\xc3\x83\xc2\xb0\xc3\x85\xc2\xb8"\xc3\xa2\xe2\x82\xac\xc5\xbe ',
        b'\n[transfer] ',
    ),
    (
        b'\n\xc3\x83\xc2\xb0\xc3\x85\xc2\xb8\xc3\x85\xc2\xbd\xc3\x82\xc2\xab ',
        b'\n[ticket] ',
    ),
    (
        b'\n\xc3\x83\xc2\xb0\xc3\x85\xc2\xb8"\xc3\x82\xc2\x90 ',
        b'\n[verify] ',
    ),
    (
        b'\n\xc3\x83\xc2\xb0\xc3\x85\xc2\xb8"\' ',
        b'\n[privacy] ',
    ),
    (
        b'\n\xc3\x83\xc2\xb0\xc3\x85\xc2\xb8\xc3\x82\xc2\x8f\xc3\x82\xc2\xa2 ',
        b'\n[employer] ',
    ),
    (
        b'\n\xc3\x83\xc2\xb0\xc3\x85\xc2\xb8"\xc3\x82\xc2\xa6 ',
        b'\n[firmware] ',
    ),
    (
        b"   \xc3\x83\xc2\xb0\xc3\x85\xc2\xb8'\xc3\x82\xc2\xb0 ",
        b"   [payout] ",
    ),
    (b"\xc3\x82\xc2\xb0C", b" C"),
]

# UTF-8 mis-decoded as Latin-1 then saved again (\xc3\xb0\xc5\xb8… sequences).
_MOJI_PRINTLN: list[tuple[bytes, bytes]] = [
    (
        b'println("\\n\xc3\xb0\xc5\xb8\xe2\x80\x9c\xe2\x80\xb9 Employer ',
        b'println("\\n[employer] ',
    ),
    (
        b'println("\\n\xc3\xb0\xc5\xb8\xe2\x80\x9c\xe2\x80\xb9 Software Bill ',
        b'println("\\n[sbom] ',
    ),
    (b'println("\\n\xc3\xb0\xc5\xb8\xe2\x80\x9c\xc2\xa6 ', b'println("\\n[provenance] '),
    (b'println("\\n\xc3\xb0\xc5\xb8\xe2\x80\x9d\xc2\xa8 ', b'println("\\n[build] '),
    (b'println("\\n\xc3\xb0\xc5\xb8\xe2\x80\x9d\xc2\x8d ', b'println("\\n[verify] '),
    (b'println("\\n\xc3\xb0\xc5\xb8\xe2\x80\x9d\xc2\x90 ', b'println("\\n[auth] '),
    (b'println("\\n\xc3\xb0\xc5\xb8\xe2\x80\x9d\xe2\x80\x99 ', b'println("\\n[privacy] '),
    (b'println("\\n\xc3\xb0\xc5\xb8\xe2\x80\x9c\xc5\x93 ', b'println("\\n[history] '),
    (b'println("\\n\xc3\xb0\xc5\xb8\xe2\x80\x9c\xc5\xa0 ', b'println("\\n[stats] '),
    (b'println("\\n\xc3\xb0\xc5\xb8\xc2\x8f\xc2\xa6 ', b'println("\\n[bank] '),
    (b'println("\\n\xc3\xb0\xc5\xb8\xc2\x8f\xc2\xa2 ', b'println("\\n[building] '),
    (b'println("\xc3\xa2\xc2\x9d\xc5\x92 ', b'println("[FAIL] '),
    # field-data-collection (✓ / ✗ mojibake)
    (b'println("\xc3\xa2\xc5\x93\xe2\x80\x9c ', b'println("[OK] '),
    (b'println("\xc3\xa2\xc5\x93\xe2\x80\x94 ', b'println("[FAIL] '),
    (b'println("  \xc3\xa2\xc5\x93\xe2\x80\x9c ', b'println("  [OK] '),
    (b'println("  \xc3\xa2\xc5\x93\xe2\x80\x94 ', b'println("  [FAIL] '),
]

_MOJI_SAMPLE: list[tuple[bytes, bytes]] = [
    (b'\n\xc3\xb0\xc5\xb8\xe2\x80\x9c\xc2\xa6 ', b'\n[provenance] '),
    (b'\n\xc3\xb0\xc5\xb8\xe2\x80\x9d\xc2\xa8 ', b'\n[build] '),
    (b'\n\xc3\xb0\xc5\xb8\xe2\x80\x9d\xc2\x8d ', b'\n[verify] '),
    (b'\n\xc3\xb0\xc5\xb8\xe2\x80\x9d\xc2\x90 ', b'\n[auth] '),
    (b'\n\xc3\xb0\xc5\xb8\xe2\x80\x9d\xe2\x80\x99 ', b'\n[privacy] '),
    (b'\n\xc3\xb0\xc5\xb8\xe2\x80\x9c\xc5\x93 ', b'\n[history] '),
    (b'\n\xc3\xb0\xc5\xb8\xe2\x80\x9c\xc5\xa0 ', b'\n[stats] '),
    (b'\n\xc3\xb0\xc5\xb8\xc2\x8f\xc2\xa6 ', b'\n[bank] '),
    (b'\n\xc3\xb0\xc5\xb8\xc2\x8f\xc2\xa2 ', b'\n[building] '),
    (
        b'\n\xc3\xb0\xc5\xb8\xe2\x80\x9c\xe2\x80\xb9 Employer ',
        b'\n[employer] ',
    ),
    (
        b'\n\xc3\xb0\xc5\xb8\xe2\x80\x9c\xe2\x80\xb9 Software Bill ',
        b'\n[sbom] ',
    ),
]

REPLACEMENTS = REPLACEMENTS + _MOJI_PRINTLN + _MOJI_SAMPLE


def main() -> None:
    for path in sorted(ROOT.rglob("*.md")):
        if "_site" in path.parts:
            continue
        data = path.read_bytes()
        new_b = data
        for old, rep in REPLACEMENTS:
            new_b = new_b.replace(old, rep)
        out = new_b
        if out != data:
            path.write_bytes(out)
            print(path.relative_to(ROOT))


if __name__ == "__main__":
    main()
