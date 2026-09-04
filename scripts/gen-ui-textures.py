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


# ------------------------------------------------------- rank badge icons ---
# 16x16, used as font glyphs in front of LuckPerms rank prefixes (E050-E058).
PALE_ICE = (190, 242, 255, 255)
DARK_VOID = (20, 10, 30, 255)
GRAY_BADGE = (105, 105, 116, 255)


def rank_member():
    """Gray rounded badge (default rank, no frills)."""
    img = canvas()
    d = ImageDraw.Draw(img)
    d.rounded_rectangle((3, 4, 12, 13), radius=3, fill=GRAY_BADGE)
    d.line([(5, 5), (10, 5)], fill=lighter(GRAY_BADGE, 60))
    return img


def rank_wisp():
    """Pale wisp-flame."""
    img = canvas()
    d = ImageDraw.Draw(img)
    d.polygon([(8, 1), (11, 6), (12, 9), (11, 12), (8, 14), (5, 12),
               (4, 9), (5, 6)], fill=AQUA)
    d.polygon([(8, 5), (10, 8), (9, 11), (8, 12), (7, 11), (6, 8)],
              fill=PALE_ICE)
    return img


def rank_stalker():
    """Violet watcher eye."""
    img = canvas()
    d = ImageDraw.Draw(img)
    d.ellipse((2, 5, 13, 10), fill=VIOLET)
    d.ellipse((5, 5, 10, 10), fill=DARK_VOID)
    d.rectangle((7, 6, 8, 9), fill=FUCHSIA)
    px(d, 6, 6, (255, 255, 255, 255))
    return img


def rank_sentinel():
    """Amber radiant diamond."""
    img = canvas()
    d = ImageDraw.Draw(img)
    d.polygon([(8, 1), (13, 6), (8, 15), (3, 6)], fill=AMBER)
    d.line([(8, 1), (8, 15)], fill=lighter(AMBER, 70))
    d.line([(3, 6), (13, 6)], fill=lighter(AMBER, 40))
    for sx, sy in [(4, 3), (12, 3)]:
        px(d, sx, sy, (255, 250, 235, 255))
    return img


def rank_helper():
    """Blue kite shield with a pale cross."""
    img = canvas()
    d = ImageDraw.Draw(img)
    d.polygon([(4, 2), (11, 2), (12, 3), (12, 8), (11, 11), (8, 13), (7, 13),
               (4, 11), (3, 8), (3, 3)], fill=BLUE)
    d.line([(4, 2), (11, 2)], fill=lighter(BLUE, 70))
    d.line([(7, 4), (7, 11)], fill=(225, 240, 255, 255))
    d.line([(5, 7), (10, 7)], fill=(225, 240, 255, 255))
    return img


def rank_dev():
    """Magenta cog."""
    img = canvas()
    d = ImageDraw.Draw(img)
    d.rectangle((5, 5, 10, 10), fill=FUCHSIA)
    for x0, y0, x1, y1 in [(6, 2, 9, 4), (6, 11, 9, 13), (2, 6, 4, 9),
                           (11, 6, 13, 9)]:
        d.rectangle((x0, y0, x1, y1), fill=FUCHSIA)
    d.rectangle((6, 6, 9, 9), fill=DARK_VOID)
    px(d, 7, 7, (255, 255, 255, 255))
    return img


def rank_moderator():
    """Emerald shield with a check."""
    img = canvas()
    d = ImageDraw.Draw(img)
    d.polygon([(4, 2), (11, 2), (12, 3), (12, 8), (11, 11), (8, 13), (7, 13),
               (4, 11), (3, 8), (3, 3)], fill=EMERALD)
    d.line([(4, 2), (11, 2)], fill=lighter(EMERALD, 70))
    d.line([(5, 8), (7, 10), (11, 4)], fill=(240, 255, 245, 255), width=2)
    return img


def rank_admin():
    """Crimson five-point star."""
    img = canvas()
    d = ImageDraw.Draw(img)
    pts = []
    for i in range(10):
        r = 7 if i % 2 == 0 else 3
        a = -math.pi / 2 + i * math.pi / 5
        pts.append((8 + r * math.cos(a), 8 + r * math.sin(a)))
    d.polygon(pts, fill=CRIMSON)
    px(d, 8, 8, lighter(CRIMSON, 90))
    return img


def rank_owner():
    """Gold crown with crimson gems."""
    img = canvas()
    d = ImageDraw.Draw(img)
    d.polygon([(2, 12), (2, 5), (5, 8), (8, 3), (11, 8), (14, 5), (14, 12)],
              fill=GOLD)
    d.rectangle((2, 12, 14, 14), fill=AMBER)
    for gx in (4, 8, 12):
        px(d, gx, 13, CRIMSON)
    px(d, 8, 6, (255, 250, 235, 255))
    return img


# --------------------------------------------------- vanilla chest window ---
def chest_generic_54():
    """Themed override for container/generic_54.png (256x256).

    Supports ALL chest sizes (27/36/45/54) by painting borders at every
    possible bottom edge (17 + rows*18). Vanilla blits only header + N*18
    rows, so a single texture with seams at each boundary works for any N.
    """
    img = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    rng = random.Random(0xC0FFEE)
    d = ImageDraw.Draw(img)

    W = 176
    MAX_H = 125  # 6 rows: 17 + 6*18
    for y in range(MAX_H):
        t = y / MAX_H
        c = tuple(int(a + (b - a) * t) for a, b in zip(VOID_TOP, VOID_BOT))
        d.line([(0, y), (W - 1, y)], fill=c)
        if rng.random() < 0.30:
            x = rng.randrange(W)
            n = rng.randint(-5, 5)
            r, g, b_, _ = img.getpixel((x, y))
            px(d, x, y, (max(0, r + n), max(0, g + n), max(0, b_ + n), 255))

    # outer vertical borders (full height)
    for y in range(MAX_H):
        px(d, 0, y, AMETHYST)
        px(d, W - 1, y, AMETHYST)
    # top border
    for x in range(W):
        px(d, x, 0, AMETHYST)
    # bottom borders at EVERY valid chest height so any size shows a clean edge
    for rows in (3, 4, 5, 6):
        by = 17 + rows * 18 - 1
        for x in range(W):
            px(d, x, by, AMETHYST)
        # inner hairline just above each bottom
        for x in range(1, W - 1):
            px(d, x, by - 1, (52, 24, 82, 255))
    # top inner hairline
    d.rectangle((1, 1, W - 2, MAX_H - 2), outline=(52, 24, 82, 80))

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
        d.line([(2 + i, MAX_H - 2 - i), (W - 3 - i, MAX_H - 2 - i)], fill=(168, 85, 247, a))
    return img


def inventory_background():
    """Themed player inventory (E) — 176x166 window used by survival_inventory."""
    W, H = 176, 166
    img = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    rng = random.Random(0xC0FFEE + 1)
    d = ImageDraw.Draw(img)
    for y in range(H):
        t = y / H
        c = tuple(int(a + (b - a) * t) for a, b in zip(VOID_TOP, VOID_BOT))
        d.line([(0, y), (W - 1, y)], fill=c)
        if rng.random() < 0.20:
            x = rng.randrange(W)
            n = rng.randint(-5, 5)
            r, g, b_, _ = img.getpixel((x, y))
            px(d, x, y, (max(0, r + n), max(0, g + n), max(0, b_ + n), 255))
    # outer frame
    for x in range(W):
        px(d, x, 0, AMETHYST)
        px(d, x, H - 1, AMETHYST)
    for y in range(H):
        px(d, 0, y, AMETHYST)
        px(d, W - 1, y, AMETHYST)
    d.rectangle((1, 1, W - 2, H - 2), outline=(52, 24, 82, 255))
    # title underline where "Inventory" / crafting labels sit
    d.line([(2, 15), (W - 3, 15)], fill=(59, 29, 94, 180))
    # slot area hints: subtle inner lines around crafting grid + armor column
    # crafting 2x2 at approx (88,28) in vanilla — hint border
    for x in range(88, 124):
        px(d, x, 27, (68, 32, 112, 120))
        px(d, x, 64, (68, 32, 112, 120))
    for y in range(27, 65):
        px(d, 88, y, (68, 32, 112, 120))
        px(d, 123, y, (68, 32, 112, 120))
    # corner runes like chest
    for cx, cy in [(4, 4), (W - 5, 4)]:
        d.polygon([(cx, cy - 3), (cx + 3, cy), (cx, cy + 3), (cx - 3, cy)], fill=FUCHSIA)
        px(d, cx, cy, (250, 240, 255, 255))
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
    save(rank_member(), f"{g}/rank_member.png")
    save(rank_wisp(), f"{g}/rank_wisp.png")
    save(rank_stalker(), f"{g}/rank_stalker.png")
    save(rank_sentinel(), f"{g}/rank_sentinel.png")
    save(rank_helper(), f"{g}/rank_helper.png")
    save(rank_dev(), f"{g}/rank_dev.png")
    save(rank_moderator(), f"{g}/rank_moderator.png")
    save(rank_admin(), f"{g}/rank_admin.png")
    save(rank_owner(), f"{g}/rank_owner.png")
    chest = chest_generic_54()
    save(chest, "gui/sprites/container/generic_54.png")
    save(chest.copy(), "gui/container/generic_54.png")  # legacy path fallback
    inv = inventory_background()
    save(inv, "gui/sprites/container/inventory.png")
    save(inv.copy(), "gui/container/inventory.png")
    print("done.")


if __name__ == "__main__":
    main()
