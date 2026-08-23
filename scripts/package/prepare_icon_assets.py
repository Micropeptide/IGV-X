#!/usr/bin/env python3
"""Create transparent macOS/Java icon assets from the supplied artwork.

The original IGV-X artwork was a JPEG stored under PNG/ICNS names.  Its white
rounded-corner matte therefore survived every packaging path and appeared as a
white square in the Dock.  This script removes only the connected near-white
pixels that touch the image edge, preserving white details enclosed by the
foreground artwork, then emits real RGBA PNGs and a multi-resolution ICNS.
"""

from __future__ import annotations

import argparse
import math
import shutil
import subprocess
import tempfile
from collections import deque
from pathlib import Path

from PIL import Image

WHITE_DISTANCE = 60.0


def _white_distance(rgb: tuple[int, int, int]) -> float:
    return math.sqrt(sum((255 - value) ** 2 for value in rgb))


def transparentize(source: Image.Image) -> Image.Image:
    """Remove the edge-connected white matte while retaining foreground pixels."""
    rgba = source.convert("RGBA")
    width, height = rgba.size
    pixels = rgba.load()
    outside = bytearray(width * height)
    queue: deque[int] = deque()

    def is_candidate(index: int) -> bool:
        x, y = index % width, index // width
        return pixels[x, y][3] > 0 and _white_distance(pixels[x, y][:3]) <= WHITE_DISTANCE

    def seed(index: int) -> None:
        if not outside[index] and is_candidate(index):
            outside[index] = 1
            queue.append(index)

    for x in range(width):
        seed(x)
        seed((height - 1) * width + x)
    for y in range(height):
        seed(y * width)
        seed(y * width + width - 1)

    while queue:
        index = queue.popleft()
        x, y = index % width, index // width
        neighbors = (
            index - 1 if x else -1,
            index + 1 if x + 1 < width else -1,
            index - width if y else -1,
            index + width if y + 1 < height else -1,
        )
        for neighbor in neighbors:
            if neighbor >= 0 and not outside[neighbor] and is_candidate(neighbor):
                outside[neighbor] = 1
                queue.append(neighbor)

    for index, marked in enumerate(outside):
        if not marked:
            continue
        x, y = index % width, index // width
        r, g, b, original_alpha = pixels[x, y]
        # Preserve a soft anti-aliased edge rather than leaving a white halo.
        alpha = round(min(1.0, _white_distance((r, g, b)) / WHITE_DISTANCE) * 255)
        pixels[x, y] = (r, g, b, min(original_alpha, alpha))
    return rgba


def write_png(image: Image.Image, path: Path, size: tuple[int, int] | None = None) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    if size is not None:
        image = image.resize(size, Image.Resampling.LANCZOS)
    image.save(path, format="PNG", optimize=True)


def write_icns(image: Image.Image, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="igvx-iconset-") as temporary:
        iconset = Path(temporary) / "IGV-X.iconset"
        iconset.mkdir()
        for logical_size in (16, 32, 128, 256, 512):
            write_png(
                image,
                iconset / f"icon_{logical_size}x{logical_size}.png",
                (logical_size, logical_size),
            )
            write_png(
                image,
                iconset / f"icon_{logical_size}x{logical_size}@2x.png",
                (logical_size * 2, logical_size * 2),
            )
        subprocess.run(
            ["/usr/bin/iconutil", "-c", "icns", "-o", str(path), str(iconset)],
            check=True,
        )


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--png", type=Path, action="append", default=[])
    parser.add_argument("--size", type=int, help="square pixel size for PNG outputs")
    parser.add_argument("--icns", type=Path, action="append", default=[])
    args = parser.parse_args()
    if not args.png and not args.icns:
        parser.error("at least one --png or --icns output is required")

    image = transparentize(Image.open(args.source))
    png_size = (args.size, args.size) if args.size else None
    for path in args.png:
        write_png(image, path, png_size)
    if args.icns:
        with tempfile.TemporaryDirectory(prefix="igvx-icns-") as temporary:
            generated = Path(temporary) / "igv_icon.icns"
            write_icns(image, generated)
            for path in args.icns:
                path.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(generated, path)


if __name__ == "__main__":
    main()
