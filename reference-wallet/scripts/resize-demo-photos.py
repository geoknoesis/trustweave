#!/usr/bin/env python3
"""
Resize demo credential photo samples to practical wallet limits.

Targets (aligned with EUDI PID / mDL guidance for embedded JPEG claims):
  - portrait (3:4): 480 x 640 px, max ~50 KiB JPEG
  - landscape (4:3, drone registry): 480 x 360 px, max ~50 KiB JPEG

Usage:
  python scripts/resize-demo-photos.py
  python scripts/resize-demo-photos.py --kind portrait path/to/input.png
"""
from __future__ import annotations

import argparse
import io
import sys
from pathlib import Path

import cairosvg
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
DRONES_DIR = ROOT / "public" / "drones"
SUBJECTS_DIR = ROOT / "public" / "subjects"

PRESETS = {
    "portrait": {"width": 480, "height": 640, "max_bytes": 50 * 1024},
    "landscape": {"width": 480, "height": 360, "max_bytes": 50 * 1024},
}


def load_image(path: Path) -> Image.Image:
    if path.suffix.lower() == ".svg":
        png_bytes = cairosvg.svg2png(url=str(path))
        return Image.open(io.BytesIO(png_bytes)).convert("RGB")
    img = Image.open(path)
    return img.convert("RGB")


def fit_cover(img: Image.Image, width: int, height: int) -> Image.Image:
    src_w, src_h = img.size
    scale = max(width / src_w, height / src_h)
    resized = img.resize((round(src_w * scale), round(src_h * scale)), Image.Resampling.LANCZOS)
    left = (resized.width - width) // 2
    top = (resized.height - height) // 2
    return resized.crop((left, top, left + width, top + height))


def encode_jpeg(img: Image.Image, quality: int) -> bytes:
    buf = io.BytesIO()
    img.save(buf, format="JPEG", quality=quality, optimize=True, progressive=True)
    return buf.getvalue()


def compress_to_limit(img: Image.Image, max_bytes: int) -> tuple[bytes, int]:
    best: tuple[bytes, int] | None = None
    for quality in range(85, 39, -5):
        data = encode_jpeg(img, quality)
        if best is None or len(data) < len(best[0]):
            best = (data, quality)
        if len(data) <= max_bytes:
            return data, quality
    assert best is not None
    return best


def process_file(input_path: Path, output_path: Path, kind: str) -> None:
    preset = PRESETS[kind]
    img = fit_cover(load_image(input_path), preset["width"], preset["height"])
    data, quality = compress_to_limit(img, preset["max_bytes"])
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_bytes(data)
    print(
        f"{output_path.name}: {preset['width']}x{preset['height']} "
        f"q={quality} {len(data) // 1024} KiB"
    )


def convert_drone_svgs() -> None:
    for svg in sorted(DRONES_DIR.glob("DRONE-*.svg")):
        jpg = svg.with_suffix(".jpg")
        process_file(svg, jpg, "landscape")


def convert_subject_svgs() -> None:
    for svg in sorted(SUBJECTS_DIR.glob("PERSON-*.svg")):
        jpg = svg.with_suffix(".jpg")
        process_file(svg, jpg, "portrait")


def main() -> int:
    parser = argparse.ArgumentParser(description="Resize demo credential photo samples.")
    parser.add_argument("inputs", nargs="*", help="Optional input image paths")
    parser.add_argument(
        "--kind",
        choices=sorted(PRESETS),
        default="landscape",
        help="Preset dimensions (default: landscape for drone photos)",
    )
    parser.add_argument(
        "--output",
        type=Path,
        help="Output JPEG path (required when converting a single input)",
    )
    args = parser.parse_args()

    if args.inputs:
        if len(args.inputs) == 1 and args.output:
            process_file(Path(args.inputs[0]), args.output, args.kind)
            return 0
        print("Provide exactly one input and --output for ad-hoc conversion.", file=sys.stderr)
        return 1

    convert_drone_svgs()
    if SUBJECTS_DIR.is_dir():
        convert_subject_svgs()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
