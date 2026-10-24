#!/usr/bin/env python3
"""Render the Harmony app icon master at 1024 x 1024 using Pillow."""
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "assets/app-icon-1024.png"
SIZE = 1024
SCALE = 4
BACKGROUND = "#159CEE"
HEAD = "#BFE8FF"


def head_outline() -> list[tuple[float, float]]:
    points = [(244.0, 356.0)]

    def curve(c1, c2, end):
        start = points[-1]
        for step in range(1, 65):
            t = step / 64
            u = 1 - t
            points.append(tuple(u ** 3 * start[i] + 3 * u * u * t * c1[i]
                                + 3 * u * t * t * c2[i] + t ** 3 * end[i]
                                for i in (0, 1)))

    curve((254, 208), (386, 142), (527, 142))
    curve((713, 142), (816, 254), (816, 420))
    curve((816, 543), (761, 599), (706, 657))
    curve((677, 688), (675, 709), (676, 745))
    points.append((678, 817))
    points.append((434, 876))
    curve((403, 884), (386, 869), (386, 840))
    points.append((386, 743))
    points.append((280, 743))
    curve((245, 743), (236, 725), (239, 690))
    points.append((254, 584))
    points.append((208, 584))
    curve((186, 584), (180, 571), (188, 551))
    points.append((244, 418))
    points.append((244, 356))
    return points


def render() -> Image.Image:
    side = SIZE * SCALE
    image = Image.new("RGB", (side, side), BACKGROUND)
    outline = head_outline()
    scaled = [(round(x * SCALE), round(y * SCALE)) for x, y in outline]
    mask = Image.new("L", image.size)
    ImageDraw.Draw(mask).polygon(scaled, fill=255)

    shadow = Image.new("L", image.size)
    shadow_draw = ImageDraw.Draw(shadow)
    offset = side
    for shift in range(0, offset + 1, 8):
        shadow_draw.polygon([(x + shift, y + shift) for x, y in scaled], fill=255)
    image.paste("#0D7FD7", mask=shadow)
    image.paste(HEAD, mask=mask)

    def tile(box, color):
        tile_mask = Image.new("L", image.size)
        ImageDraw.Draw(tile_mask).rectangle(tuple(round(v * SCALE) for v in box), fill=255)
        image.paste(color, mask=ImageChops.multiply(mask, tile_mask))

    tile((616, 0, 1024, 356), "#BDF200")
    tile((394, 374, 598, 598), "#FFD52A")
    tile((394, 616, 598, 1024), "#FF8554")
    for x in (376, 598):
        tile((x, 0, x + 18, 1024), BACKGROUND)
    for y in (356, 598):
        tile((0, y, 1024, y + 18), BACKGROUND)
    return image.resize((SIZE, SIZE), Image.Resampling.LANCZOS)


def main() -> None:
    image = render()
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    image.save(OUTPUT, format="PNG", optimize=True)
    print(f"Rendered {OUTPUT.relative_to(ROOT)}: {image.width}x{image.height} {image.mode}")


if __name__ == "__main__":
    main()
