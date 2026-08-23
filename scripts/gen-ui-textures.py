#!/usr/bin/env python3
"""The Glitch — UI texture generator (placeholder art, replace later).

Generates every custom-font glyph texture and the global vanilla-chest
override used by the Oraxen resource pack. Pure Pillow, deterministic
output, safe to re-run (idempotent).

Outputs into server/plugins/Oraxen/pack/textures/:
  glyphs/*.png                           inline font-glyph icons
  gui/sprites/container/generic_54.png   themed 6-row chest window (256x256)
  gui/container/generic_54.png           legacy-path duplicate (pre-1.20.2)

Glyph unicode mapping lives in server/plugins/Oraxen/glyphs/theglitch.yml and
is mirrored in plugins/GlitchItems/.../GlitchUI.java — keep in sync.

Palette matches play.theglitch.gg (void purple / amethyst / aqua).
Usage:  python scripts/gen-ui-textures.py
"""

from pathlib import Path

from PIL import Image, ImageDraw
import math
import random

REPO = Path(__file__).resolve().parents[1]
TEX = REPO / "server" / "plugins" / "Oraxen" / "pack" / "textures"

# ---------------------------------------------------------------- palette ---
VOID_TOP = (13, 6, 22, 255)
VOID_BOT = (24, 11, 38, 255)
AMETHYST = (168, 85, 247, 255)
AMETHYST_DIM = (109, 40, 217, 200)
FUCHSIA = (232, 121, 249, 255)
AQUA = (34, 211, 238, 255)
GOLD = (251, 191, 36, 255)
AMBER = (245, 158, 11, 255)
BLUE = (59, 130, 246, 255)
EMERALD = (16, 185, 129, 255)
CRIMSON = (239, 68, 68, 255)
VIOLET = (139, 92, 246, 255)
GRAY_OUT = (110, 110, 122, 255)


def canvas(w=16, h=16):
    return Image.new("RGBA", (w, h), (0, 0, 0, 0))


def save(img, rel):
    out = TEX / rel
    out.parent.mkdir(parents=True, exist_ok=True)
    img.save(out)
    print(f"wrote {out.relative_to(REPO)}")


def px(d, x, y, c):
    d.point((x, y), fill=c)


def lighter(c, amt=60):
    return tuple(min(255, v + amt) for v in c[:3]) + (255,)


# ------------------------------------------------------------ glyph icons ---
def glyph_shield(color):
    """Aegis — rounded kite shield."""
    img = canvas()
    d = ImageDraw.Draw(img)
    d.polygon([(4, 2), (11, 2), (12, 3), (12, 8), (11, 11), (8, 13), (7, 13),
               (4, 11), (3, 8), (3, 3)], fill=color)
    d.line([(4, 2), (11, 2)], fill=lighter(color))
    d.line([(7, 4), (7, 11)], fill=(20, 10, 30, 160))
    return img


def glyph_crescent(color):
    """Veil — crescent moon."""
    img = canvas()
    d = ImageDraw.Draw(img)
    d.ellipse((3, 2, 13, 13), fill=color)
    cut = canvas()
    ImageDraw.Draw(cut).ellipse((6, 1, 15, 11), fill=(0, 0, 0, 255))
    img.paste((0, 0, 0, 0), (0, 0), cut)
    d = ImageDraw.Draw(img)
    d.arc((3, 2, 13, 13), 110, 330, fill=lighter(color, 70))
    return img


def glyph_flower(color):
    """Bloom — four-petal rune flower."""
    img = canvas()
    d = ImageDraw.Draw(img)
    for cx, cy in [(8, 4), (8, 12), (4, 8), (12, 8)]:
        d.ellipse((cx - 2, cy - 2, cx + 2, cy + 2), fill=color)
    d.rectangle((7, 7, 8, 8), fill=GOLD)
    return img


def glyph_rune(color):
    """Ward — angular warding stave."""
    img = canvas()
    d = ImageDraw.Draw(img)
    d.line([(8, 1), (8, 14)], fill=color, width=2)
    d.line([(4, 3), (8, 6)], fill=color, width=2)
    d.line([(12, 3), (8, 6)], fill=color, width=2)
    d.line([(4, 9), (8, 12)], fill=lighter(color, 50))
    d.line([(12, 9), (8, 12)], fill=lighter(color, 50))
    return img


def glyph_vortex(color):
    """Hollow — dotted vortex ring."""
    img = canvas()
    d = ImageDraw.Draw(img)
    for i in range(10):
        a = i * math.pi / 5
        x = round(8 + 5.5 * math.cos(a))
        y = round(8 + 5.5 * math.sin(a))
        shade = tuple(int(ch * (0.55 + 0.45 * i / 9)) for ch in color[:3]) + (255,)
        px(d, x, y, shade)
        if i % 3 == 0:
            px(d, x + (1 if x < 8 else -1), y, shade)
    px(d, 8, 8, FUCHSIA)
    return img


def glyph_crystal(color):
    """Glitch Shard — faceted echo-shard crystal."""
    img = canvas()
    d = ImageDraw.Draw(img)
    d.polygon([(8, 1), (11, 5), (11, 11), (8, 15), (5, 11), (5, 5)], fill=color)
    d.line([(8, 1), (8, 15)], fill=(240, 253, 255, 190))
    d.line([(5, 5), (11, 5)], fill=lighter(color, 60)[:3] + (220,))
    return img


def glyph_spark(color):
    """Four-point sparkle (filled star pip)."""
    img = canvas()
    d = ImageDraw.Draw(img)
    d.polygon([(8, 1), (9, 7), (15, 8), (9, 9), (8, 15), (7, 9), (1, 8), (7, 7)],
              fill=color)
    px(d, 8, 8, lighter(color, 80))
    return img


def glyph_spark_empty():
    """Outline-only sparkle so unfilled pips stay visible on dark lore."""
    img = canvas()
    d = ImageDraw.Draw(img)
    pts = [(8, 1), (9, 7), (15, 8), (9, 9), (8, 15), (7, 9), (1, 8), (7, 7)]
    d.polygon(pts, outline=GRAY_OUT)
    d.line([(8, 3), (13, 8)], fill=GRAY_OUT)
    d.line([(8, 3), (3, 8)], fill=GRAY_OUT)
    d.line([(8, 13), (3, 8)], fill=GRAY_OUT)
    d.line([(8, 13), (13, 8)], fill=GRAY_OUT)
    return img


def glyph_divider():
    """150x8 ornamental rule for lore pages (~25 chars wide at h=8)."""
    img = canvas(150, 8)
    d = ImageDraw.Draw(img)
    for x in range(4, 146):
        t = abs(x - 75) / 71.0
        c = (
            int(AMETHYST[0] * (1 - t) + FUCHSIA[0] * (t * 0.35)),
            int(AMETHYST[1] * (1 - t) + FUCHSIA[1] * (t * 0.35)),
            int(AMETHYST[2] * (1 - t) + FUCHSIA[2] * (t * 0.35)),
            int(235 * (1 - 0.55 * t)),
        )
        px(d, x, 3, c)
        px(d, x, 4, c if x % 7 else AMETHYST_DIM)
    d.polygon([(75, 0), (79, 4), (75, 8), (71, 4)], fill=FUCHSIA)
    d.polygon([(75, 2), (77, 4), (75, 6), (73, 4)], fill=VOID_BOT)
    return img


def glyph_title_rune():
    """Small glitch-diamond for menu titles."""
    img = canvas()
    d = ImageDraw.Draw(img)
    d.polygon([(8, 1), (14, 8), (8, 15), (2, 8)], outline=FUCHSIA,
              fill=(30, 12, 48, 210))
    d.polygon([(8, 4), (11, 8), (8, 12), (5, 8)], fill=AMETHYST)
    px(d, 8, 8, (250, 250, 255, 255))
    return img


# --------------------------------------------------- vanilla chest window ---
def chest_generic_54():
    """Themed override for container/generic_54.png (256x256).

    Vanilla samples: title band (0,0)-(175,16); six 18px row strips starting
    y=17 (last ends y=124). We paint that whole window uniformly so the
    per-strip blits never show seams, then decorate only fixed-safe zones.
    """
    img = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    rng = random.Random(0xC0FFEE)
    d = ImageDraw.Draw(img)

    W, H = 176, 125  # window content bounds (exclusive)
    for y in range(H):
        t = y / H
        c = tuple(int(a + (b - a) * t) for a, b in zip(VOID_TOP, VOID_BOT))
        d.line([(0, y), (W - 1, y)], fill=c)
        if rng.random() < 0.30:
            x = rng.randrange(W)
            n = rng.randint(-5, 5)
            r, g, b_, _ = img.getpixel((x, y))
            px(d, x, y, (max(0, r + n), max(0, g + n), max(0, b_ + n), 255))

    for x in range(W):
        px(d, x, 0, AMETHYST)
        px(d, x, H - 1, AMETHYST)
    for y in range(H):
        px(d, 0, y, AMETHYST)
        px(d, W - 1, y, AMETHYST)
    d.rectangle((1, 1, W - 2, H - 2), outline=(52, 24, 82, 255))

    d.line([(2, 15), (W - 3, 15)], fill=(59, 29, 94, 255))
    d.line([(2, 16), (W - 3, 16)], fill=(38, 18, 62, 160))

    for cx, cy in [(4, 4), (W - 5, 4)]:
        d.polygon([(cx, cy - 3), (cx + 3, cy), (cx, cy + 3), (cx - 3, cy)],
                  fill=FUCHSIA)
        px(d, cx, cy, (250, 240, 255, 255))

    for i in range(6):
        a = 90 - i * 14
        if a <= 0:
            break
        d.line([(2 + i, H - 2 - i), (W - 3 - i, H - 2 - i)], fill=(168, 85, 247, a))
    return img


def main():
    TEX.mkdir(parents=True, exist_ok=True)
    g = "glyphs"
    save(glyph_shield(AMBER), f"{g}/res_aegis.png")
    save(glyph_crescent(BLUE), f"{g}/res_veil.png")
    save(glyph_flower(EMERALD), f"{g}/res_bloom.png")
    save(glyph_rune(CRIMSON), f"{g}/res_ward.png")
    save(glyph_vortex(VIOLET), f"{g}/res_hollow.png")
    save(glyph_crystal(AQUA), f"{g}/shard.png")
    save(glyph_spark(GOLD), f"{g}/star_full.png")
    save(glyph_spark_empty(), f"{g}/star_empty.png")
    save(glyph_divider(), f"{g}/divider.png")
    save(glyph_title_rune(), f"{g}/title_rune.png")
    chest = chest_generic_54()
    save(chest, "gui/sprites/container/generic_54.png")
    save(chest.copy(), "gui/container/generic_54.png")  # legacy path fallback
    print("done.")


if __name__ == "__main__":
    main()
