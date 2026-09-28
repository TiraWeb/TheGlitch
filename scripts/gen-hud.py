#!/usr/bin/env python3
"""Generate The Glitch top-left HUD for MythicHUD: an objective-tracker look
(icon tile + title bar + tick-box lines), fed by PlaceholderAPI.

  Extraction card (red worlds): %glitchstash_hud_*% (GlitchStash ExtractionHud)
  Daily contracts:              %glitchquests_hud_*% (GlitchQuests QuestExpansion)

Writes (all under server/plugins/MythicHUD/):
  source-pack/assets/mythichud/textures/assets/glitch/*.png   pixel-art pieces
  hud_assets/hud/glitch-hud.yml                               assets + conditions
  layouts/glitch-layouts.yml                                  the "glitch-hud" layout

Positions: MythicHUD element position is a screen % (x 0 = left, y 100 = top),
layer offsets are GUI pixels (+x right, +y up). Tune TOP/LEFT/ROW below.

Usage: python scripts/gen-hud.py [--preview out.png]
"""
import argparse
import math
import os

from PIL import Image, ImageDraw

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
HUD = os.path.join(REPO, "server", "plugins", "MythicHUD")
TEX = os.path.join(HUD, "source-pack", "assets", "mythichud", "textures", "assets", "glitch")

ORANGE, ORANGE_D, ORANGE_L = (245, 167, 66), (176, 102, 28), (255, 214, 150)
GREEN, GREEN_D = (74, 222, 128), (22, 120, 60)
EDGE = (22, 21, 27)
TILE_A, TILE_B = (58, 57, 66), (40, 39, 46)
BAR_A, BAR_B, BAR_HI = (64, 63, 72), (50, 49, 57), (104, 102, 114)
GREY, GREY_D = (156, 163, 175), (75, 75, 84)

# ---------------------------------------------------------------- drawing


def tile(border=ORANGE):
    """24x24 icon tile: dark edge, coloured ring, vertical-gradient slate fill."""
    im = Image.new("RGBA", (24, 24), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rounded_rectangle((0, 0, 23, 23), radius=3, fill=EDGE + (255,))
    d.rounded_rectangle((1, 1, 22, 22), radius=2, fill=border + (255,))
    for y in range(3, 21):
        t = (y - 3) / 17
        c = tuple(round(TILE_A[i] + (TILE_B[i] - TILE_A[i]) * t) for i in range(3))
        d.line((3, y, 20, y), fill=c + (255,))
    d.line((3, 3, 20, 3), fill=(84, 82, 94, 255))
    return im


def outline(glyph, colour=EDGE):
    """1px dark outline around a glyph's silhouette."""
    out = Image.new("RGBA", glyph.size, (0, 0, 0, 0))
    src, dst = glyph.load(), out.load()
    w, h = glyph.size
    for y in range(h):
        for x in range(w):
            if src[x, y][3]:
                continue
            if any(0 <= x + dx < w and 0 <= y + dy < h and src[x + dx, y + dy][3] > 0
                   for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                dst[x, y] = colour + (255,)
    out.alpha_composite(glyph)
    return out


def arrow(step):
    """Turn arrow rotated step*45 degrees clockwise (0 = straight ahead)."""
    S = 8  # supersample
    size = 16 * S
    im = Image.new("L", (size, size), 0)
    d = ImageDraw.Draw(im)
    c = size / 2
    pts = [(0, -7.2), (6.2, -0.8), (2.3, -0.8), (2.3, 7.0), (-2.3, 7.0), (-2.3, -0.8), (-6.2, -0.8)]
    a = math.radians(step * 45)
    rot = [(c + (x * math.cos(a) - y * math.sin(a)) * S, c + (x * math.sin(a) + y * math.cos(a)) * S)
           for x, y in pts]
    d.polygon(rot, fill=255)
    mask = im.resize((16, 16), Image.BOX).point(lambda v: 255 if v >= 110 else 0)
    glyph = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    gp, mp = glyph.load(), mask.load()
    for y in range(16):
        for x in range(16):
            if mp[x, y]:
                # light from the top-left: lighter pixels toward the upper-left
                shade = ORANGE_L if (x + y) < 13 else ORANGE if (x + y) < 19 else ORANGE_D
                gp[x, y] = shade + (255,)
    return outline(glyph)


def target():
    g = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(g)
    d.ellipse((1, 1, 14, 14), outline=GREEN + (255,), width=2)
    d.ellipse((5, 5, 10, 10), fill=GREEN + (255,))
    d.point((6, 6), fill=(200, 255, 220, 255))
    return outline(g)


def clock():
    g = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(g)
    d.ellipse((1, 1, 14, 14), fill=(200, 204, 212, 255), outline=GREY + (255,))
    d.line((7, 7, 7, 3), fill=EDGE + (255,))
    d.line((7, 7, 10, 9), fill=EDGE + (255,))
    return outline(g)


def scroll():
    g = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(g)
    d.rectangle((3, 2, 12, 13), fill=(233, 214, 170, 255))
    d.rectangle((2, 1, 13, 2), fill=(196, 160, 104, 255))
    d.rectangle((2, 13, 13, 14), fill=(196, 160, 104, 255))
    for y in (5, 7, 9):
        d.line((5, y, 10, y), fill=(140, 112, 70, 255))
    d.line((5, 11, 8, 11), fill=(140, 112, 70, 255))
    return outline(g)


def with_glyph(glyph, border=ORANGE):
    t = tile(border)
    t.alpha_composite(glyph, (4, 4))
    return t


def title_bar():
    """3-slice text background (MythicHUD 'background', cap 5): 14 px tall."""
    im = Image.new("RGBA", (12, 14), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rounded_rectangle((0, 0, 11, 13), radius=2, fill=EDGE + (235,))
    for y in range(1, 13):
        t = (y - 1) / 11
        c = tuple(round(BAR_A[i] + (BAR_B[i] - BAR_A[i]) * t) for i in range(3))
        d.line((1, y, 10, y), fill=c + (235,))
    d.line((2, 1, 9, 1), fill=BAR_HI + (235,))
    return im


def checkbox(kind):
    """9x9 tick boxes: todo (grey), active (orange, filled centre), done (green tick)."""
    im = Image.new("RGBA", (9, 9), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    ring = {"todo": GREY, "active": ORANGE, "done": GREEN}[kind]
    d.rectangle((0, 0, 8, 8), fill=EDGE + (255,))
    d.rectangle((1, 1, 7, 7), fill=ring + (255,))
    d.rectangle((2, 2, 6, 6), fill=(34, 33, 40, 255))
    if kind == "active":
        d.rectangle((3, 3, 5, 5), fill=ORANGE + (255,))
    if kind == "done":
        for x, y in ((2, 4), (3, 5), (4, 6), (5, 5), (6, 4), (6, 3), (6, 2)):
            d.point((x, y), fill=(190, 255, 210, 255))
    return im


def dot():
    im = Image.new("RGBA", (9, 9), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.ellipse((2, 2, 6, 6), fill=GREY_D + (255,), outline=EDGE + (255,))
    return im


def textures():
    out = {f"icon_a{i}": with_glyph(arrow(i)) for i in range(8)}
    out["icon_zone"] = with_glyph(target(), GREEN)
    out["icon_closed"] = with_glyph(clock(), GREY)
    out["icon_contracts"] = with_glyph(scroll())
    out["title_bar"] = title_bar()
    for k in ("todo", "active", "done"):
        out[f"box_{k}"] = checkbox(k)
    out["dot"] = dot()
    out["line_bg"] = line_bg()
    return out


# ----------------------------------------------------------------- layout

TOP, LEFT = -6, 8          # card origin (GUI px from the top-left corner)
BAR_X, BAR_Y = LEFT + 29, TOP - 8   # title text, right of the tile
LINE_X, ROW = LEFT + 4, 12          # tick-box column and line pitch
QUEST_RED_TOP = TOP - 58            # contracts sit under the extraction card
BAR_BG_Y, LINE_BG_Y = 6, 2          # nudge the text backgrounds up onto the text

# MythicHUD condition lines read "<condition> <required result> <action>": the
# action fires when the condition does NOT give the required result, so
# "... true hide" means "show only while true" (its wiki example reads it the
# other way round — the bundled vanilla-armor asset confirms this reading).
SHOW_RED = "placeholder{ph=%glitchstash_hud_show%;v=true} true hide"


def only_when(placeholder, value):
    return f"placeholder{{ph={placeholder};v={value}}} true hide"


def line_bg():
    """3-slice translucent strip behind tracker lines (readable on bright terrain)."""
    im = Image.new("RGBA", (8, 11), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rounded_rectangle((0, 0, 7, 10), radius=2, fill=(16, 16, 20, 150))
    return im


def tex_layer(path, x, y):
    return {"texture": {"path": f"assets/glitch/{path}.png"}, "align": "right", "offset": {"x": x, "y": y}}


def text_layer(text, x, y, bar=False):
    layer = {"text": text, "font": "default", "outlined": False, "align": "right", "offset": {"x": x, "y": y}}
    if bar:
        layer["background"] = {"texture": "assets/glitch/title_bar.png", "cap": 5, "padding": 6,
                               "offset": {"y": BAR_BG_Y}}
    else:
        layer["background"] = {"texture": "assets/glitch/line_bg.png", "cap": 3, "padding": 3,
                               "offset": {"y": LINE_BG_Y}}
    return layer


def hud_assets():
    assets = {}
    for icon in [f"a{i}" for i in range(8)] + ["zone", "closed"]:
        assets[f"glitch-ext-icon-{icon}"] = {
            "conditions": [only_when("%glitchstash_hud_icon%", icon)],
            "layers": {"tile": tex_layer(f"icon_{icon}", LEFT, TOP)},
        }
    assets["glitch-ext-body"] = {
        "conditions": [SHOW_RED],
        "layers": {
            "title": text_layer("%glitchstash_hud_title%", BAR_X, BAR_Y, bar=True),
            "box1": tex_layer("box_active", LINE_X, TOP - 28),
            "line1": text_layer("%glitchstash_hud_line1%", LINE_X + 13, TOP - 28),
            "box2": tex_layer("dot", LINE_X, TOP - 28 - ROW),
            "line2": text_layer("%glitchstash_hud_line2%", LINE_X + 13, TOP - 28 - ROW),
        },
    }
    for where, top, cond in (("red", QUEST_RED_TOP, SHOW_RED),):
        assets[f"glitch-quest-head-{where}"] = {
            "conditions": [cond],
            "layers": {
                "tile": tex_layer("icon_contracts", LEFT, top),
                "title": text_layer("%glitchquests_hud_title%", BAR_X, top - 5, bar=True),
            },
        }
        for n in range(1, 5):
            y = top - 28 - (n - 1) * ROW
            for state, box in (("todo", "box_todo"), ("done", "box_done")):
                assets[f"glitch-quest-{where}-{n}-{state}"] = {
                    "conditions": [cond, only_when(f"%glitchquests_hud_{n}_state%", state)],
                    "layers": {
                        "box": tex_layer(box, LINE_X, y),
                        "text": text_layer(f"%glitchquests_hud_{n}_text%", LINE_X + 13, y),
                    },
                }
    return assets


def layouts(assets):
    elements = {name: {"asset": name, "position": {"x": 0.0, "y": 100.0}, "offset": {"x": 0, "y": 0}}
                for name in assets}
    return {"glitch-hud": {"elements": elements}}


def dump_yaml(data, indent=0):
    """Tiny YAML writer (quotes every string) so the output needs no PyYAML."""
    lines = []
    pad = "  " * indent
    for key, value in data.items():
        if isinstance(value, dict):
            lines.append(f"{pad}{key}:")
            lines.append(dump_yaml(value, indent + 1))
        elif isinstance(value, list):
            lines.append(f"{pad}{key}:")
            lines += [f"{pad}  - '{v}'" for v in value]
        elif isinstance(value, str):
            lines.append(f"{pad}{key}: '" + value.replace("'", "''") + "'")
        else:
            lines.append(f"{pad}{key}: {str(value).lower() if isinstance(value, bool) else value}")
    return "\n".join(lines)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--preview")
    args = ap.parse_args()
    os.makedirs(TEX, exist_ok=True)
    tex = textures()
    for name, im in tex.items():
        im.save(os.path.join(TEX, f"{name}.png"))
    assets = hud_assets()
    os.makedirs(os.path.join(HUD, "hud_assets", "hud"), exist_ok=True)
    header = "# The Glitch top-left HUD — generated by scripts/gen-hud.py, edit that instead.\n"
    with open(os.path.join(HUD, "hud_assets", "hud", "glitch-hud.yml"), "w", encoding="utf-8", newline="\n") as f:
        f.write(header + dump_yaml(assets) + "\n")
    os.makedirs(os.path.join(HUD, "layouts"), exist_ok=True)
    with open(os.path.join(HUD, "layouts", "glitch-layouts.yml"), "w", encoding="utf-8", newline="\n") as f:
        f.write(header + dump_yaml(layouts(assets)) + "\n")
    if args.preview:
        sheet = Image.new("RGBA", (len(tex) * 30, 30), (90, 110, 90, 255))
        for i, im in enumerate(tex.values()):
            sheet.alpha_composite(im, (i * 30 + 3, 3))
        sheet.resize((sheet.width * 4, sheet.height * 4), Image.NEAREST).save(args.preview)
    print(f"{len(tex)} textures, {len(assets)} assets")


if __name__ == "__main__":
    main()
