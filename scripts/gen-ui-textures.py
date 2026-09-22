#!/usr/bin/env python3
"""The Glitch — UI texture generator.

Generates every custom-font glyph texture and copies the chest-window
overrides used by the Nexo resource pack (migrated from Oraxen 2026-09-20).
Pure Pillow, deterministic output, safe to re-run (idempotent).

Outputs into server/plugins/Nexo/pack/external_packs/Oraxen/assets/minecraft/textures/:
  glyphs/*.png                                 inline font-glyph icons (procedural, Arcane Ruins palette)
  gui/sprites/container/generic_{9,18,27,36,45,54}.png   themed chest windows (256x256 each, one per row count)
  gui/container/generic_{9,18,27,36,45,54}.png           legacy-path duplicates (pre-1.20.2)

The chest-window art is procedural (chest_window()), pinned to vanilla's real
UV coordinates (176px wide, flush at x=0, 17px header, 18px/row) — round 9
tried copying the operator-supplied "Medieval" kit's generic_N.png files
byte-for-byte instead, and it looked fine standalone but was actually drawn
CENTERED in its 256x256 canvas (content at x=29..226, not x=0..176), which
vanilla doesn't account for; every item icon rendered scattered relative to
the drawn grid in-game. Reverted to procedural generation and recolored it to
the kit's wood/parchment palette (sampled, not copied) instead of the
original void-purple one, so alignment stays correct by construction.
assets/ui-kits/medieval/ (tracked in git) still holds the raw kit — the
generic_N.png files there are NOT usable as direct vanilla overrides for the
reason above, but menu_template.png/jobs_template.png/profile_template.png/
rewards_template.png/buttons.png/typography_*.png are staged for a future
custom GUI (e.g. the planned Menu hub) built via its own layout system
(DeluxeMenus or a Nexo font-image glyph) rather than a generic_N swap.

Glyph unicode mapping lives in server/plugins/Nexo/glyphs/oraxen_glyphs/theglitch.yml
and is mirrored in plugins/GlitchItems/.../GlitchUI.java — keep in sync.
Glyphs/rank badges/inventory.png stay on the original void-purple/amethyst/
aqua palette — only the chest window itself changed.

Usage:  python scripts/gen-ui-textures.py
"""

from pathlib import Path

from PIL import Image, ImageDraw
import math
import random

REPO = Path(__file__).resolve().parents[1]
TEX = REPO / "server" / "plugins" / "Nexo" / "pack" / "external_packs" / "Oraxen" / "assets" / "minecraft" / "textures"
UI_KIT = REPO / "assets" / "ui-kits" / "medieval"

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
# Font glyphs in front of LuckPerms rank prefixes (E050-E058). Were procedural
# 16x16 icons; round 10 (2026-09-23) swapped them for pre-made Hypixel-style
# badges (operator-supplied "Mythic Ranks" kit) that bake the rank NAME into
# the badge art itself (80x16, wide) — so the glyph config's ascent/height
# changed too (8/9 -> 9/13, matching the kit author's own values). Source
# PNGs tracked at assets/icon-kits/mythicranks/ (29 total; 9 used here, the
# rest — builder/media/famous/legend/manager/master/mvp/mvp+/srmod/staff/
# titan/hero + 7 social-platform badges — staged for future ranks/features).
# LuckPerms prefixes are UNCHANGED (still glyph + "&color&l[TAG]" text) so
# Bedrock clients, which can't render the glyph, keep their plain-text tag.
RANK_ICON_MAP = {
    "rank_member": "member",
    "rank_wisp": "vip",
    "rank_stalker": "mvpplus",
    "rank_sentinel": "mvpplusplus",
    "rank_helper": "helper",
    "rank_dev": "dev",
    "rank_moderator": "mod",
    "rank_admin": "admin",
    "rank_owner": "owner",
}
RANKS_KIT = REPO / "assets" / "icon-kits" / "mythicranks"


# --------------------------------------------------- vanilla chest window ---
CHEST_SIZES = (9, 18, 27, 36, 45, 54)

# Wood/parchment palette sampled from assets/ui-kits/medieval/generic_54.png
# (see docs/STATUS.md round 9 for why it's sampled, not copied wholesale).
WOOD_DARK = (97, 51, 37, 255)
WOOD_MID = (131, 74, 53, 255)
PARCHMENT_LIGHT = (222, 197, 160, 255)
PARCHMENT_DARK = (188, 158, 122, 255)
WOOD_GOLD = (251, 185, 84, 255)
# Darker reddish tone for the player's-own-inventory section (always present
# below the container part of every chest-style GUI, at a fixed vanilla
# offset) — kept visually distinct from the container slots above it, same
# 2-tone split the Medieval kit itself used.
PLAYERINV_LIGHT = (168, 106, 84, 255)
PLAYERINV_DARK = (140, 83, 66, 255)


def chest_window(rows):
    """Themed override for container/generic_{rows*9}.png (256x256).

    Vanilla blits this texture starting at pixel (0,0) with a fixed 176px
    width, a 17px header, and 18px-tall slot rows — it does NOT auto-detect
    or center content, so those exact coordinates are load-bearing, not a
    style choice. (Round 9: the operator-supplied "Medieval" kit PNGs looked
    fine standalone but drew their panel centered in the 256x256 canvas
    (content x=29..226) instead of flush at x=0 — vanilla then sampled a
    misaligned crop, scattering every item icon relative to the drawn grid.
    Reverted to procedural generation, which is pinned to vanilla's real
    coordinates by construction, just recolored to the kit's wood/parchment
    palette instead of the original void-purple one.)
    """
    img = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    rng = random.Random(0xC0FFEE)
    d = ImageDraw.Draw(img)

    W = 176
    H = 17 + rows * 18
    for y in range(H):
        t = y / H
        c = tuple(int(a + (b - a) * t) for a, b in zip(PARCHMENT_LIGHT, PARCHMENT_DARK))
        d.line([(0, y), (W - 1, y)], fill=c)
        if rng.random() < 0.35:
            x = rng.randrange(W)
            n = rng.randint(-8, 8)
            r, g, b_, _ = img.getpixel((x, y))
            px(d, x, y, (max(0, r + n), max(0, g + n), max(0, b_ + n), 255))

    # per-cell grid — 18px squares starting at (7,17), matching vanilla's real slot pitch
    grid_line = (WOOD_MID[0], WOOD_MID[1], WOOD_MID[2], 140)
    for row in range(rows + 1):
        gy = 17 + row * 18
        if gy < H:
            d.line([(7, gy), (W - 8, gy)], fill=grid_line)
    for col in range(10):
        gx = 7 + col * 18
        if gx < W - 7:
            d.line([(gx, 17), (gx, H - 2)], fill=grid_line)

    # outer border
    for y in range(H):
        px(d, 0, y, WOOD_DARK)
        px(d, W - 1, y, WOOD_DARK)
    for x in range(W):
        px(d, x, 0, WOOD_DARK)
    by = H - 1
    for x in range(W):
        px(d, x, by, WOOD_DARK)
    for x in range(1, W - 1):
        px(d, x, by - 1, WOOD_MID)
    d.line([(2, 15), (W - 3, 15)], fill=WOOD_MID)
    d.line([(2, 16), (W - 3, 16)], fill=(WOOD_MID[0], WOOD_MID[1], WOOD_MID[2], 150))
    for row in range(1, rows):
        gy = 17 + row * 18 - 1
        d.line([(2, gy), (W - 3, gy)], fill=(WOOD_MID[0], WOOD_MID[1], WOOD_MID[2], 110))

    for cx, cy in [(4, 4), (W - 5, 4)]:
        d.polygon([(cx, cy - 3), (cx + 3, cy), (cx, cy + 3), (cx - 3, cy)], fill=WOOD_GOLD)
        px(d, cx, cy, (255, 250, 235, 255))

    # Player's own inventory (always shown below the container, at a fixed
    # vanilla offset). Round 9 drew a precise 176px-wide grid here (matching
    # the container's own coordinates) and it came out visibly WIDER and a
    # different shape in-game than the container above it, so that follow-up
    # dropped the grid entirely (flat fill, no edges to look "wrong shape").
    # Round 11: the operator wants the boxes back. Compromise — draw the grid
    # at the same 18px pitch (starting x=7, matching real slot spacing) but
    # tile it across the FULL 0..256 width instead of stopping at column 9.
    # A repeating pattern has no "this is where it should end" edge to be
    # wrong about, so wherever vanilla actually crops this region, the real
    # slots still land on grid-lined cells instead of a hard mismatched border.
    for y in range(H, 256):
        t = (y - H) / (256 - H)
        c = tuple(int(a + (b - a) * t) for a, b in zip(PLAYERINV_LIGHT, PLAYERINV_DARK))
        d.line([(0, y), (255, y)], fill=c)
        if rng.random() < 0.35:
            x = rng.randrange(256)
            n = rng.randint(-8, 8)
            r, g, b_, _ = img.getpixel((x, y))
            px(d, x, y, (max(0, r + n), max(0, g + n), max(0, b_ + n), 255))

    playerinv_grid = (PLAYERINV_DARK[0] - 30, PLAYERINV_DARK[1] - 20, PLAYERINV_DARK[2] - 15, 140)
    row = 0
    gy = H
    while gy < 256:
        d.line([(0, gy), (255, gy)], fill=playerinv_grid)
        gy += 18
        row += 1
    gx = 7
    while gx < 256:
        d.line([(gx, H), (gx, 255)], fill=playerinv_grid)
        gx += 18

    return img


def gen_chest_windows():
    """Generate the procedural wood/parchment chest window at every size."""
    for n in CHEST_SIZES:
        img = chest_window(n // 9)
        save(img, f"gui/sprites/container/generic_{n}.png")
        save(img.copy(), f"gui/container/generic_{n}.png")  # legacy path fallback


def inventory_background():
    """Themed player inventory (E) — 176x166 window used by survival_inventory.

    Same wood/parchment palette as chest_window(). No title underline (it
    cut through the armor column); every slot gets a box at vanilla coords.
    """
    W, H = 176, 166
    img = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    rng = random.Random(0xC0FFEE + 1)
    d = ImageDraw.Draw(img)
    for y in range(H):
        t = y / H
        c = tuple(int(a + (b - a) * t) for a, b in zip(PARCHMENT_LIGHT, PARCHMENT_DARK))
        d.line([(0, y), (W - 1, y)], fill=c)
        if rng.random() < 0.20:
            x = rng.randrange(W)
            n = rng.randint(-5, 5)
            r, g, b_, _ = img.getpixel((x, y))
            px(d, x, y, (max(0, r + n), max(0, g + n), max(0, b_ + n), 255))
    # outer frame
    for x in range(W):
        px(d, x, 0, WOOD_DARK)
        px(d, x, H - 1, WOOD_DARK)
    for y in range(H):
        px(d, 0, y, WOOD_DARK)
        px(d, W - 1, y, WOOD_DARK)
    d.rectangle((1, 1, W - 2, H - 2), outline=(WOOD_MID[0], WOOD_MID[1], WOOD_MID[2], 255))
    # Per-slot boxes at the exact vanilla survival_inventory coordinates:
    # a box spans (x, y)..(x+18, y+18) and the item draws at (x+1, y+1).
    inv_grid_line = (WOOD_DARK[0], WOOD_DARK[1], WOOD_DARK[2], 110)

    def slot_box(x, y, size=18):
        d.rectangle((x, y, x + size, y + size), outline=inv_grid_line)

    for row in range(4):                      # armor column
        slot_box(7, 7 + row * 18)
    slot_box(76, 61)                          # offhand
    for row in range(2):                      # 2x2 crafting grid
        for col in range(2):
            slot_box(97 + col * 18, 17 + row * 18)
    slot_box(153, 27)                         # crafting output
    # crafting arrow between grid and output
    arrow = (WOOD_DARK[0], WOOD_DARK[1], WOOD_DARK[2], 150)
    d.line([(137, 36), (147, 36)], fill=arrow)
    d.polygon([(147, 33), (150, 36), (147, 39)], fill=arrow)

    inv_top = 83                              # main inventory, 3 rows
    hotbar_top = inv_top + 3 * 18 + 4         # hotbar, 1 row
    for top, rows in ((inv_top, 3), (hotbar_top, 1)):
        for row in range(rows + 1):
            gy = top + row * 18
            d.line([(7, gy), (7 + 9 * 18, gy)], fill=inv_grid_line)
        for col in range(10):
            gx = 7 + col * 18
            d.line([(gx, top), (gx, top + rows * 18)], fill=inv_grid_line)

    # corner runes like chest
    for cx, cy in [(4, 4), (W - 5, 4)]:
        d.polygon([(cx, cy - 3), (cx + 3, cy), (cx, cy + 3), (cx - 3, cy)], fill=WOOD_GOLD)
        px(d, cx, cy, (255, 250, 235, 255))
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
    for glyph_name, badge_name in RANK_ICON_MAP.items():
        img = Image.open(RANKS_KIT / f"{badge_name}.png").convert("RGBA")
        save(img, f"{g}/{glyph_name}.png")
    gen_chest_windows()
    inv = inventory_background()
    save(inv, "gui/sprites/container/inventory.png")
    save(inv.copy(), "gui/container/inventory.png")
    print("done.")


if __name__ == "__main__":
    main()
