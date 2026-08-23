#!/usr/bin/env python3
"""Recolor vanilla container/hud textures to Arcane Ruins theme while preserving layout.

Input: vanilla PNGs downloaded to C:/Users/tirob/AppData/Local/Temp/opencode/
Output: themed textures into server/plugins/Oraxen/pack/textures/

This ensures slot positions are pixel-perfect (fixes E inventory misalignment).
"""
from pathlib import Path
from PIL import Image

REPO = Path(__file__).resolve().parents[1]
TEMP = Path(r"C:\Users\tirob\AppData\Local\Temp\opencode")
TEX = REPO / "server" / "plugins" / "Oraxen" / "pack" / "textures"

VOID_TOP = (13, 6, 22)
VOID_BOT = (24, 11, 38)
AMETHYST = (168, 85, 247)
FUCHSIA = (232, 121, 249)
SLOT_BG = (42, 31, 61)  # dark purple-gray for slot interior
HIGHLIGHT = (233, 213, 255)  # light lavender for highlights
SHADOW = (26, 14, 46)
MID_SHADOW = (74, 46, 106)

def recolor_inventory(in_path, out_rel):
    im = Image.open(in_path).convert("RGBA")
    w, h = im.size
    out = Image.new("RGBA", (w, h), (0,0,0,0))
    # For gradient, use Y of each pixel relative to window height
    # Inventory window is 176x166 at (0,0) in the 256x256 sheet, but file itself is 176x166? Actually vanilla inventory.png we downloaded is 176x166? Check.
    # The downloaded vanilla_inventory2.png is likely 176x166 cropped. We'll just gradient by y.
    for y in range(h):
        t = y / max(1, h-1)
        void_c = tuple(int(VOID_TOP[i] + (VOID_BOT[i]-VOID_TOP[i])*t) for i in range(3))
        for x in range(w):
            r,g,b,a = im.getpixel((x,y))
            if a == 0:
                continue
            # Map vanilla grays to themed
            if (r,g,b) == (198,198,198):
                # background light gray -> void gradient
                out.putpixel((x,y), void_c + (a,))
            elif (r,g,b) == (139,139,139):
                out.putpixel((x,y), SLOT_BG + (a,))
            elif (r,g,b) == (255,255,255):
                out.putpixel((x,y), HIGHLIGHT + (a,))
            elif (r,g,b) == (55,55,55):
                out.putpixel((x,y), SHADOW + (a,))
            elif (r,g,b) == (85,85,85):
                out.putpixel((x,y), MID_SHADOW + (a,))
            elif (r,g,b) == (0,0,0):
                out.putpixel((x,y), (0,0,0,a))
            else:
                # Keep close grays with tint
                # For any other gray (e.g., hotbar uses different grays), map by luminance
                lum = (r+g+b)//3
                if r==g==b:
                    if lum > 200:
                        out.putpixel((x,y), HIGHLIGHT + (a,))
                    elif lum > 150:
                        out.putpixel((x,y), void_c + (a,))
                    elif lum > 100:
                        out.putpixel((x,y), SLOT_BG + (a,))
                    else:
                        out.putpixel((x,y), SHADOW + (a,))
                else:
                    out.putpixel((x,y), (r,g,b,a))
    # Add subtle amethyst border at window edges (1px)
    # Detect window bounds: for inventory (176x166), border is at outermost pixels where original had dark outline
    # Our recolor already has SHADOW at those positions, but add amethyst top edge
    for x in range(w):
        # top edge highlight
        if out.getpixel((x,0))[3] != 0:
            # blend highlight with void
            out.putpixel((x,0), AMETHYST + (out.getpixel((x,0))[3],))
        if out.getpixel((x,h-1))[3] != 0:
            out.putpixel((x,h-1), AMETHYST + (out.getpixel((x,h-1))[3],))
    for y in range(h):
        if out.getpixel((0,y))[3] != 0:
            out.putpixel((0,y), AMETHYST + (out.getpixel((0,y))[3],))
        if out.getpixel((w-1,y))[3] != 0:
            out.putpixel((w-1,y), AMETHYST + (out.getpixel((w-1,y))[3],))
    out_path = TEX / out_rel
    out_path.parent.mkdir(parents=True, exist_ok=True)
    out.save(out_path)
    print(f"recolored {in_path.name} -> {out_rel} ({w}x{h})")

def recolor_hotbar(in_path, out_rel):
    im = Image.open(in_path).convert("RGBA")
    w,h = im.size
    out = Image.new("RGBA", (w,h), (0,0,0,0))
    for y in range(h):
        for x in range(w):
            r,g,b,a = im.getpixel((x,y))
            if a == 0:
                continue
            # Hotbar uses semi-transparent dark bg (39,37,5,186 etc) and gray borders
            # Map by luminance and alpha
            if a < 200:
                # semi-transparent background -> void with same alpha
                # Keep alpha, map RGB to void
                t = x / max(1, w-1)
                void_c = tuple(int(VOID_TOP[i] + (VOID_BOT[i]-VOID_TOP[i])*t) for i in range(3))
                # darken slightly for hotbar
                void_c = tuple(max(0, c-10) for c in void_c)
                out.putpixel((x,y), void_c + (a,))
            else:
                if (r,g,b) in [(93,93,93),(126,126,126),(147,147,147)]:
                    out.putpixel((x,y), AMETHYST + (a,))
                elif (r,g,b) in [(0,0,0)]:
                    out.putpixel((x,y), (0,0,0,a))
                else:
                    # keep grays as slot
                    out.putpixel((x,y), (r,g,b,a))
    # Add amethyst border on hotbar frame
    out_path = TEX / out_rel
    out_path.parent.mkdir(parents=True, exist_ok=True)
    out.save(out_path)
    print(f"recolored {in_path.name} -> {out_rel} ({w}x{h})")

# Inventory - use vanilla 176x166 (actually downloaded file is that size)
recolor_inventory(TEMP / "vanilla_inventory2.png", "gui/container/inventory.png")
recolor_inventory(TEMP / "vanilla_inventory2.png", "gui/sprites/container/inventory.png")
# Generic 54 - recolor vanilla_generic54.png (should be 256x256 with window)
recolor_inventory(TEMP / "vanilla_generic54.png", "gui/container/generic_54.png")
recolor_inventory(TEMP / "vanilla_generic54.png", "gui/sprites/container/generic_54.png")
# Hotbar
recolor_hotbar(TEMP / "hotbar.png", "gui/sprites/hud/hotbar.png")
recolor_hotbar(TEMP / "hotbar.png", "gui/hud/hotbar.png")  # legacy
# Hotbar selection - bright amethyst border (24x23)
def recolor_selection(in_path, out_rel):
    im = Image.open(in_path).convert("RGBA")
    w,h = im.size
    out = Image.new("RGBA", (w,h), (0,0,0,0))
    for y in range(h):
        for x in range(w):
            r,g,b,a = im.getpixel((x,y))
            if a == 0:
                continue
            # Selection border should be bright amethyst/fuchsia
            lum = (r+g+b)//3
            if lum > 150:
                out.putpixel((x,y), FUCHSIA + (a,))
            elif lum > 80:
                out.putpixel((x,y), AMETHYST + (a,))
            else:
                out.putpixel((x,y), (0,0,0,a))
    out_path = TEX / out_rel
    out_path.parent.mkdir(parents=True, exist_ok=True)
    out.save(out_path)
    print(f"recolored {in_path.name} -> {out_rel} ({w}x{h})")
recolor_selection(TEMP / "vanilla_hotbar_selection.png", "gui/sprites/hud/hotbar_selection.png")
recolor_selection(TEMP / "vanilla_hotbar_selection.png", "gui/hud/hotbar_selection.png")

print("done")
