#!/usr/bin/env python3
"""Generate demo CAC subject portrait SVG sources (3:4 passport-style busts)."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "public" / "subjects"

SUBJECTS = [
    ("PERSON-001", "Smith, John", "#d4a574", "#1e3a8a", "#cbd5e1"),
    ("PERSON-002", "Garcia, Maria", "#c68642", "#0369a1", "#e2e8f0"),
    ("PERSON-003", "Chen, Wei", "#f1c27d", "#0f766e", "#dbeafe"),
    ("PERSON-004", "Johnson, Aisha", "#8d5524", "#7c2d12", "#f1f5f9"),
    ("PERSON-005", "Mueller, Hans", "#e0ac69", "#334155", "#e2e8f0"),
]

TEMPLATE = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 360 480" role="img" aria-label="{id} {name}">
  <rect width="360" height="480" fill="{bg}"/>
  <ellipse cx="180" cy="168" rx="78" ry="92" fill="{skin}"/>
  <ellipse cx="180" cy="395" rx="118" ry="75" fill="{uniform}"/>
  <rect x="0" y="430" width="360" height="50" fill="#0f172a"/>
  <text x="180" y="460" text-anchor="middle" fill="#f8fafc" font-family="system-ui,sans-serif" font-size="12">{id} - {name}</text>
</svg>
"""


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    for sid, name, skin, uniform, bg in SUBJECTS:
        path = OUT / f"{sid}.svg"
        path.write_text(
            TEMPLATE.format(id=sid, name=name, skin=skin, uniform=uniform, bg=bg),
            encoding="utf-8",
        )
        print(f"wrote {path.name}")


if __name__ == "__main__":
    main()
