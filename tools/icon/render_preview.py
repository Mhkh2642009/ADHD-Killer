#!/usr/bin/env python3
"""Render a preview of the ADHD Killer launcher icon as a launcher would draw it.

Composites the adaptive icon (gradient background vector + PNG foreground) under a
round and a squircle mask, plus the two legacy bitmaps, into one sheet.
"""

from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / "app" / "src" / "main" / "res"
OUT = ROOT / "tools" / "icon" / "preview.png"

# Matches drawable/ic_launcher_background.xml
GRADIENT = [(42, 30, 69), (20, 15, 38), (10, 7, 19)]
GRADIENT_OFFSETS = [0.0, 0.55, 1.0]
GRADIENT_RADIUS_DP = 76


def gradient_canvas(px: int) -> Image.Image:
    img = Image.new("RGBA", (px, px))
    p = img.load()
    cx = cy = px / 2
    radius = px * GRADIENT_RADIUS_DP / 108
    for y in range(px):
        for x in range(px):
            d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5 / radius
            d = min(1.0, d)
            for i in range(len(GRADIENT) - 1):
                lo, hi = GRADIENT_OFFSETS[i], GRADIENT_OFFSETS[i + 1]
                if lo <= d <= hi:
                    t = 0.0 if hi == lo else (d - lo) / (hi - lo)
                    c = tuple(round(GRADIENT[i][k] + (GRADIENT[i + 1][k] - GRADIENT[i][k]) * t) for k in range(3))
                    break
            else:
                c = GRADIENT[-1]
            p[x, y] = c + (255,)
    return img


def squircle_mask(px: int) -> Image.Image:
    mask = Image.new("L", (px, px), 0)
    r = int(px * 0.28)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, px - 1, px - 1], radius=r, fill=255)
    return mask


def circle_mask(px: int) -> Image.Image:
    mask = Image.new("L", (px, px), 0)
    inset = int(px * (1 - 72 / 108) / 2)
    ImageDraw.Draw(mask).ellipse([inset, inset, px - 1 - inset, px - 1 - inset], fill=255)
    return mask


def main() -> None:
    fg = Image.open(RES / "mipmap-xxxhdpi" / "ic_launcher_foreground.png").convert("RGBA")
    bg = gradient_canvas(fg.width)

    legacy = Image.open(RES / "mipmap-xxxhdpi" / "ic_launcher.webp").convert("RGBA")
    legacy_round = Image.open(RES / "mipmap-xxxhdpi" / "ic_launcher_round.webp").convert("RGBA")

    tile = 300
    sheet = Image.new("RGBA", (1320, 400), (30, 30, 30, 255))

    def adaptive(mask: Image.Image) -> Image.Image:
        out = Image.new("RGBA", (fg.width, fg.width), (0, 0, 0, 0))
        out.paste(bg, (0, 0), mask)
        out.alpha_composite(fg)
        out.putalpha(Image.composite(out.getchannel("A"), Image.new("L", out.size, 0), mask))
        return out.resize((tile, tile), Image.LANCZOS)

    sheet.alpha_composite(adaptive(circle_mask(fg.width)), (20, 50))
    sheet.alpha_composite(adaptive(squircle_mask(fg.width)), (340, 50))
    sheet.alpha_composite(legacy_round.resize((tile, tile), Image.LANCZOS), (660, 50))
    sheet.alpha_composite(legacy.resize((tile, tile), Image.LANCZOS), (980, 50))

    sheet.save(OUT)
    print(f"wrote {OUT}")


if __name__ == "__main__":
    main()
