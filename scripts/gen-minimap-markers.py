#!/usr/bin/env python3
"""Draw NMinimap marker icons (server/plugins/NMinimap/markers/*.png).

  crate_<type>  loot crates ready to open (GlitchItems MinimapBridge), tier colours
  extract       open extraction points (GlitchStash MinimapExtractionMarkers)
  hostile       mob radar dot (MinimapBridge sets it for every Mob)
  rogue         Rogue Raider bot (GlitchBots) — purple chevron, MinimapBridge

On-map size comes from NMinimap config markers.sizes (map pixels), so these are
drawn at 16x16 and scaled down by the client.

Usage: python scripts/gen-minimap-markers.py [--preview out.png]
"""
import argparse
import os

from PIL import Image, ImageDraw

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(REPO, "server", "plugins", "NMinimap", "markers")
EDGE = (18, 16, 22, 255)

CRATES = {  # container type -> (body, lid/trim)
    "debris": ((150, 120, 90), (205, 180, 140)),
    "cache": ((60, 160, 80), (140, 230, 150)),
    "vault": ((50, 110, 210), (140, 190, 255)),
    "rift_vault": ((150, 70, 220), (215, 160, 255)),
}


def crate(body, trim):
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rectangle((1, 3, 14, 14), fill=EDGE)
    d.rectangle((2, 4, 13, 13), fill=body + (255,))
    d.rectangle((2, 4, 13, 7), fill=trim + (255,))
    d.line((2, 8, 13, 8), fill=EDGE)
    d.rectangle((7, 7, 8, 10), fill=(250, 220, 120, 255))
    return im


def extract():
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.polygon([(8, 0), (15, 8), (8, 15), (0, 8)], fill=EDGE)
    d.polygon([(8, 2), (13, 8), (8, 13), (2, 8)], fill=(245, 167, 66, 255))
    d.polygon([(8, 5), (10, 8), (8, 11), (6, 8)], fill=(255, 240, 200, 255))
    return im


def hostile():
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.ellipse((1, 1, 14, 14), fill=EDGE)
    d.ellipse((3, 3, 12, 12), fill=(239, 68, 68, 255))
    d.ellipse((5, 5, 7, 7), fill=(255, 170, 170, 255))
    return im


def rogue():
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.polygon([(8, 0), (15, 15), (8, 11), (1, 15)], fill=EDGE)
    d.polygon([(8, 3), (13, 13), (8, 9), (3, 13)], fill=(168, 85, 247, 255))
    d.polygon([(8, 5), (10, 10), (8, 8)], fill=(225, 190, 255, 255))
    return im


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--preview")
    args = ap.parse_args()
    os.makedirs(OUT, exist_ok=True)
    icons = {f"crate_{t}": crate(*c) for t, c in CRATES.items()}
    icons["extract"] = extract()
    icons["hostile"] = hostile()
    icons["rogue"] = rogue()
    for name, im in icons.items():
        im.save(os.path.join(OUT, f"{name}.png"))
    if args.preview:
        sheet = Image.new("RGBA", (len(icons) * 20, 20), (70, 90, 70, 255))
        for i, im in enumerate(icons.values()):
            sheet.alpha_composite(im, (i * 20 + 2, 2))
        sheet.resize((sheet.width * 6, sheet.height * 6), Image.NEAREST).save(args.preview)
    print(f"{len(icons)} markers -> {OUT}")


if __name__ == "__main__":
    main()
