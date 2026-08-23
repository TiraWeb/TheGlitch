#!/usr/bin/env python3
"""Bulk recolor all vanilla container + HUD textures to Arcane theme."""
import pathlib
from PIL import Image

REPO = pathlib.Path(__file__).resolve().parents[1]
TEMP = pathlib.Path(r"C:\Users\tirob\AppData\Local\Temp\opencode")
TEX = REPO / "server" / "plugins" / "Oraxen" / "pack" / "textures"

VOID_TOP = (13, 6, 22)
VOID_BOT = (24, 11, 38)
SLOT_BG = (42, 31, 61)
HIGHLIGHT = (233, 213, 255)
SHADOW = (26, 14, 46)
MID_SHADOW = (74, 46, 106)
AMETHYST = (168, 85, 247)

def recolor_generic(in_path, out_rel):
    im = Image.open(in_path).convert("RGBA")
    w,h = im.size
    out = Image.new("RGBA", (w,h), (0,0,0,0))
    for y in range(h):
        t = y / max(1, h-1)
        void_c = tuple(int(VOID_TOP[i] + (VOID_BOT[i]-VOID_TOP[i])*t) for i in range(3))
        for x in range(w):
            r,g,b,a = im.getpixel((x,y))
            if a==0: continue
            if (r,g,b)==(198,198,198):
                out.putpixel((x,y), void_c + (a,))
            elif (r,g,b)==(139,139,139):
                out.putpixel((x,y), SLOT_BG + (a,))
            elif (r,g,b)==(255,255,255):
                out.putpixel((x,y), HIGHLIGHT + (a,))
            elif (r,g,b)==(55,55,55):
                out.putpixel((x,y), SHADOW + (a,))
            elif (r,g,b)==(85,85,85):
                out.putpixel((x,y), MID_SHADOW + (a,))
            elif (r,g,b)==(0,0,0):
                out.putpixel((x,y), (0,0,0,a))
            else:
                lum=(r+g+b)//3
                if r==g==b:
                    if lum>200: out.putpixel((x,y), HIGHLIGHT+(a,))
                    elif lum>150: out.putpixel((x,y), void_c+(a,))
                    elif lum>100: out.putpixel((x,y), SLOT_BG+(a,))
                    else: out.putpixel((x,y), SHADOW+(a,))
                else:
                    out.putpixel((x,y), (r,g,b,a))
    # amethyst outer edge
    for x in range(w):
        for y in (0,h-1):
            if out.getpixel((x,y))[3]!=0:
                out.putpixel((x,y), AMETHYST + (out.getpixel((x,y))[3],))
    for y in range(h):
        for x in (0,w-1):
            if out.getpixel((x,y))[3]!=0:
                out.putpixel((x,y), AMETHYST + (out.getpixel((x,y))[3],))
    out_path = TEX / out_rel
    out_path.parent.mkdir(parents=True, exist_ok=True)
    out.save(out_path)
    print(f"recolored {in_path.name} -> {out_rel}")

# List of vanilla container textures to try (1.21.1)
containers = [
    "anvil.png", "beacon.png", "blast_furnace.png", "brewing_stand.png",
    "cartography_table.png", "crafting_table.png", "dispenser.png", "dropper.png",
    "enchantment_table.png", "furnace.png", "grindstone.png", "hopper.png",
    "loom.png", "merchant.png", "shulker_box.png", "smithing.png", "smoker.png",
    "stonecutter.png", "villager2.png", "horse.png", "generic_54.png", "inventory.png"
]
# Try both legacy and sprites paths; we already have generic_54 and inventory, but re-do for consistency
import subprocess, os
for name in containers:
    for base in [f"https://assets.mcasset.cloud/1.21.1/assets/minecraft/textures/gui/container/{name}",
                 f"https://assets.mcasset.cloud/1.21.1/assets/minecraft/textures/gui/sprites/container/{name}"]:
        out_name = name
        # map to both legacy and sprites outputs if found
        tmp = TEMP / f"vanilla_{name}"
        # Use curl.exe
        ret = os.system(f'curl.exe -s -L -o "{tmp}" "{base}"')
        if tmp.exists() and tmp.stat().st_size > 200:
            # Check if it's actually PNG (starts with 89 50 4E 47)
            data = tmp.read_bytes()[:8]
            if data.startswith(b'\x89PNG'):
                print(f"downloaded {name} from {base} ({tmp.stat().st_size} bytes)")
                # Recolor to both destinations
                for out_rel in [f"gui/container/{name}", f"gui/sprites/container/{name}"]:
                    try:
                        recolor_generic(tmp, out_rel)
                    except Exception as e:
                        print(f" failed recolor {name} -> {out_rel}: {e}")
                break
        # else try next base
    else:
        print(f"NOT FOUND {name}")

# HUD hotbar family
hud_files = [
    ("hud/hotbar.png", "https://assets.mcasset.cloud/1.21.1/assets/minecraft/textures/gui/sprites/hud/hotbar.png"),
    ("hud/hotbar_selection.png", "https://assets.mcasset.cloud/1.21.1/assets/minecraft/textures/gui/sprites/hud/hotbar_selection.png"),
    ("hud/hotbar_background.png", "https://assets.mcasset.cloud/1.21.1/assets/minecraft/textures/gui/sprites/hud/hotbar_background.png"),
    ("hud/hotbar_offhand_left.png", "https://assets.mcasset.cloud/1.21.1/assets/minecraft/textures/gui/sprites/hud/hotbar_offhand_left.png"),
    ("hud/hotbar_offhand_right.png", "https://assets.mcasset.cloud/1.21.1/assets/minecraft/textures/gui/sprites/hud/hotbar_offhand_right.png"),
]
for out_rel, url in hud_files:
    tmp = TEMP / f"vanilla_{out_rel.replace('/','_')}"
    os.system(f'curl.exe -s -L -o "{tmp}" "{url}"')
    if tmp.exists() and tmp.stat().st_size > 200 and tmp.read_bytes().startswith(b'\x89PNG'):
        print(f"downloaded HUD {out_rel}")
        # For HUD, use hotbar recolor logic (semi-transparent)
        # Reuse recolor_generic but keep alpha
        try:
            # Use generic recolor for HUD too (handles transparency)
            from PIL import Image as _Image
            im = _Image.open(tmp).convert("RGBA")
            w,h = im.size
            out = _Image.new("RGBA", (w,h), (0,0,0,0))
            for y in range(h):
                for x in range(w):
                    r,g,b,a = im.getpixel((x,y))
                    if a==0: continue
                    if a<200:
                        t=x/max(1,w-1)
                        void_c=tuple(int(VOID_TOP[i]+(VOID_BOT[i]-VOID_TOP[i])*t) for i in range(3))
                        void_c=tuple(max(0,c-10) for c in void_c)
                        out.putpixel((x,y), void_c+(a,))
                    else:
                        if (r,g,b) in [(93,93,93),(126,126,126),(147,147,147),(198,198,198)]:
                            out.putpixel((x,y), (168,85,247,a))
                        elif (r,g,b)==(0,0,0):
                            out.putpixel((x,y),(0,0,0,a))
                        else:
                            out.putpixel((x,y),(r,g,b,a))
            out_path = TEX / f"gui/sprites/{out_rel}"
            out_path.parent.mkdir(parents=True, exist_ok=True)
            out.save(out_path)
            out_path2 = TEX / f"gui/{out_rel}"
            out_path2.parent.mkdir(parents=True, exist_ok=True)
            out.save(out_path2)
            print(f"  -> themed {out_rel}")
        except Exception as e:
            print(f" failed HUD {out_rel}: {e}")
    else:
        print(f"HUD NOT FOUND {out_rel}")

print("bulk done")
