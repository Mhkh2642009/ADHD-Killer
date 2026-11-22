#!/usr/bin/env python3
"""
Turn "Focused Mind Orbit Logo.png" into the ADHD Killer launcher icon set.

Outputs
  * app/src/main/res/drawable-nodpi/ic_launcher_foreground.png   adaptive foreground (108dp canvas)
  * app/src/main/res/drawable-nodpi/ic_launcher_monochrome.png   themed-icon silhouette
  * app/src/main/res/mipmap-<density>/ic_launcher.webp           legacy square icon (48dp)
  * app/src/main/res/mipmap-<density>/ic_launcher_round.webp     legacy round icon (48dp)

Pipeline
  1. Flood fill the white backdrop away so the artwork keeps transparency.
  2. Split the purple artwork into components: the orbit ring vs. the four corner
     brackets. The brackets sit in the corners of the canvas and would be sliced
     off by every launcher mask, so the adaptive layers use brain + orbit only.
  3. Legacy bitmaps keep the full artwork, brackets included.
"""

import re
import subprocess
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / "app" / "src" / "main" / "res"
SOURCE = ROOT / "Focused Mind Orbit Logo.png"
PREVIEW = ROOT / "build" / "icon-preview"

# Adaptive icon canvas is 108dp; only the middle 66dp survives every launcher mask.
CANVAS_DP = 108
# The orbit ring is the widest part of the mark, so it defines the scale.
ORBIT_TARGET_DP = 62

# Deep near-black with a violet cast so the purple orbit and the white brain both read.
BACKDROP = (17, 13, 26)

DENSITIES = {
    "mdpi": 1.0,
    "hdpi": 1.5,
    "xhdpi": 2.0,
    "xxhdpi": 3.0,
    "xxxhdpi": 4.0,
}

LEGACY_DP = 48
ADAPTIVE_PX = 432  # 108dp at xxxhdpi, drawn once in drawable-nodpi

COMPONENT_RE = re.compile(
    r"^\s*(\d+):\s+(\d+)x(\d+)\+(-?\d+)\+(-?\d+)\s+[\d.,]+\s+([\d.eE+-]+)\s+gray\((\d+)\)"
)


def strip_background(src: Image.Image) -> Image.Image:
    """Remove the white backdrop, keeping the white brain art intact."""
    rgb = src.convert("RGB")
    w, h = rgb.size

    work = rgb.copy()
    fill = (255, 0, 255)
    thresh = 8
    for pt in [(0, 0), (w - 1, 0), (0, h - 1), (w - 1, h - 1)]:
        try:
            ImageDraw.floodfill(work, pt, fill, thresh=thresh)
        except ValueError:
            pass

    pixels = work.load()
    alpha = Image.new("L", (w, h), 255)
    alpha_px = alpha.load()
    for y in range(h):
        for x in range(w):
            if pixels[x, y] == fill:
                alpha_px[x, y] = 0

    out = src.convert("RGBA")
    out.putalpha(alpha)
    return out


def purple_components(img: Image.Image, tmp: Path):
    """Label the purple artwork with ImageMagick; return (mask, components)."""
    mask_path = tmp / "purple.png"
    labels_path = tmp / "labels.png"

    # r,g,b are normalised 0..1 in ImageMagick's -fx expression.
    expr = "((b>0.45)&&(b>g+0.12)&&(r>0.20)&&(g<b*0.92))?1:0"
    subprocess.run(
        ["magick", str(SOURCE), "-alpha", "off", "-fx", expr, str(mask_path)],
        check=True,
    )
    subprocess.run(
        [
            "magick", str(mask_path),
            "-define", "connected-components:verbose=true",
            "-connected-components", "8", str(labels_path),
        ],
        check=True,
        capture_output=True,
        text=True,
    )

    components = []
    for line in subprocess.run(
        ["magick", str(mask_path), "-define", "connected-components:verbose=true",
         "-connected-components", "8", "null:"],
        check=True, capture_output=True, text=True,
    ).stdout.splitlines():
        m = COMPONENT_RE.match(line)
        if m:
            components.append({
                "id": int(m.group(1)),
                "w": int(m.group(2)), "h": int(m.group(3)),
                "x": int(m.group(4)), "y": int(m.group(5)),
                "area": float(m.group(6)),
            })
    return Image.open(mask_path).convert("L"), components


def split_brackets(art: Image.Image, tmp: Path):
    """Return (art without corner brackets, art with brackets)."""
    mask, components = purple_components(art, tmp)
    brackets = [        c for c in components
        if 8000 <= c["area"] <= 20000 and 150 <= c["w"] <= 260 and 100 <= c["h"] <= 220
    ]
    if len(brackets) != 4:
        raise SystemExit(f"expected 4 corner brackets, found {len(brackets)}: {brackets}")

    labels = Image.open(tmp / "labels.png").convert("L")
    ids = {c["id"] for c in brackets}

    # Exact bracket pixels, by component label.
    bracket_mask = Image.new("L", art.size, 0)
    bp = bracket_mask.load()
    lp = labels.load()
    for y in range(art.height):
        for x in range(art.width):
            if lp[x, y] in ids:
                bp[x, y] = 255

    # Antialiased fringes survive as tiny neighbouring components: sweep them too.
    fringe = bracket_mask.filter(ImageFilter.MaxFilter(13))
    fp = fringe.load()
    ap = art.getchannel("A").load()
    for y in range(art.height):
        for x in range(art.width):
            if fp[x, y] and ap[x, y]:
                bp[x, y] = 255

    clean = art.copy()
    cp = clean.load()
    for y in range(art.height):
        for x in range(art.width):
            if bp[x, y]:
                cp[x, y] = (0, 0, 0, 0)

    orbit = max((c for c in components if c["id"] != 0), key=lambda c: c["area"])
    return clean, orbit


def trim_alpha(img: Image.Image) -> Image.Image:
    bbox = img.getchannel("A").getbbox()
    return img.crop(bbox) if bbox else img


def fit_into(img: Image.Image, size: int) -> Image.Image:
    scale = size / max(img.width, img.height)
    return img.resize(
        (max(1, int(img.width * scale)), max(1, int(img.height * scale))), Image.LANCZOS
    )


def adaptive_foreground(art: Image.Image, orbit_w_px: int, px: int) -> Image.Image:
    """108dp canvas with the mark inside the guaranteed-visible safe zone."""
    canvas = Image.new("RGBA", (px, px), (0, 0, 0, 0))
    art = trim_alpha(art)
    target_px = int(round(px * ORBIT_TARGET_DP / CANVAS_DP))
    scale = target_px / orbit_w_px
    art = art.resize((max(1, int(art.width * scale)), max(1, int(art.height * scale))), Image.LANCZOS)
    x = (px - art.width) // 2
    y = (px - art.height) // 2
    canvas.alpha_composite(art, (x, y))
    return canvas


def monochrome(art: Image.Image, orbit_w_px: int, px: int) -> Image.Image:
    """Solid silhouette for Android's themed icons (tinted from the alpha channel)."""
    fg = adaptive_foreground(art, orbit_w_px, px)
    alpha = fg.getchannel("A")
    solid = Image.new("RGBA", fg.size, (0, 0, 0, 255))
    solid.putalpha(alpha)
    return solid


def legacy_icon(art: Image.Image, size: int, round_icon: bool) -> Image.Image:
    bg = Image.new("RGBA", (size, size), BACKDROP + (255,))
    inner = int(size * 0.88)
    layer = Image.new("RGBA", (inner, inner), (0, 0, 0, 0))
    fitted = fit_into(trim_alpha(art), inner)
    layer.alpha_composite(fitted, ((inner - fitted.width) // 2, (inner - fitted.height) // 2))
    bg.alpha_composite(layer, ((size - inner) // 2, (size - inner) // 2))

    if round_icon:
        mask = Image.new("L", (size, size), 0)
        ImageDraw.Draw(mask).ellipse([0, 0, size - 1, size - 1], fill=255)
    else:
        mask = Image.new("L", (size, size), 0)
        ImageDraw.Draw(mask).rounded_rectangle([0, 0, size - 1, size - 1], radius=int(size * 0.22), fill=255)

    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.paste(bg, (0, 0), mask)
    return out


def main() -> None:
    PREVIEW.mkdir(parents=True, exist_ok=True)
    tmp = PREVIEW

    src = Image.open(SOURCE)
    art = strip_background(src)
    clean, orbit = split_brackets(art, tmp)
    print(f"orbit component: {orbit['w']}x{orbit['h']} at +{orbit['x']}+{orbit['y']}")

    for name, scale in DENSITIES.items():
        out_dir = RES / f"mipmap-{name}"
        out_dir.mkdir(parents=True, exist_ok=True)

        adaptive_px = int(round(CANVAS_DP * scale))
        adaptive_foreground(clean, orbit["w"], adaptive_px).save(out_dir / "ic_launcher_foreground.png")
        monochrome(clean, orbit["w"], adaptive_px).save(out_dir / "ic_launcher_monochrome.png")

        legacy_px = int(round(LEGACY_DP * scale))
        legacy_icon(art, legacy_px, False).save(out_dir / "ic_launcher.webp", "WEBP", lossless=True)
        legacy_icon(art, legacy_px, True).save(out_dir / "ic_launcher_round.webp", "WEBP", lossless=True)
        print(f"{name}: adaptive {adaptive_px}px, legacy {legacy_px}px")

    # Preview sheet: adaptive foreground under a launcher mask + the legacy shapes.
    fg = Image.open(RES / "mipmap-xxxhdpi" / "ic_launcher_foreground.png")
    sheet = Image.new("RGBA", (1040, 380), (32, 32, 32, 255))

    tile = Image.new("RGBA", (300, 300), BACKDROP + (255,))
    fitted = fit_into(fg, 300)
    tile.alpha_composite(fitted, ((300 - fitted.width) // 2, (300 - fitted.height) // 2))
    mask = Image.new("L", (300, 300), 0)
    ImageDraw.Draw(mask).ellipse([14, 14, 285, 285], fill=255)
    circle = Image.new("RGBA", (300, 300), (0, 0, 0, 0))
    circle.paste(tile, (0, 0), mask)
    sheet.alpha_composite(circle, (20, 40))

    sheet.alpha_composite(legacy_icon(art, 220, True), (360, 80))
    sheet.alpha_composite(legacy_icon(art, 220, False), (600, 80))

    mono = Image.open(RES / "mipmap-xxxhdpi" / "ic_launcher_monochrome.png")
    mono_tile = Image.new("RGBA", (220, 220), (210, 210, 210, 255))
    fitted = fit_into(mono, 220)
    mono_tile.alpha_composite(fitted, ((220 - fitted.width) // 2, (220 - fitted.height) // 2))
    sheet.alpha_composite(mono_tile, (830, 80))
    sheet.save(PREVIEW / "sheet.png")
    print("done")


if __name__ == "__main__":
    main()
