#!/usr/bin/env python3
"""Draw the 64x64 server-list icon (server/server-icon.png).

A glitched "G" emblem in the server's purple/pink: a thick ring with a bar
(the G), cyan/magenta chromatic split, a few horizontally displaced slices,
on a dark rounded badge. Drawn at 4x then downsampled.

Usage: python scripts/gen-server-icon.py
"""
import os
import random

from PIL import Image, ImageChops, ImageDraw, ImageFilter

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(REPO, "server", "server-icon.png")
S = 256  # working size (4x)


def g_mask(offset=(0, 0)):
    m = Image.new("L", (S, S), 0)
    d = ImageDraw.Draw(m)
    ox, oy = offset
    box = (46 + ox, 46 + oy, 210 + ox, 210 + oy)
    d.ellipse(box, fill=255)
    d.ellipse((82 + ox, 82 + oy, 174 + ox, 174 + oy), fill=0)
    # open the G on the right (upper) side
    d.polygon([(128 + ox, 128 + oy), (230 + ox, 40 + oy), (230 + ox, 118 + oy)], fill=0)
    # the G's bar
    d.rectangle((128 + ox, 116 + oy, 206 + ox, 142 + oy), fill=255)
    d.rectangle((176 + ox, 116 + oy, 206 + ox, 172 + oy), fill=255)
    return m


def main():
    random.seed(7)
    bg = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(bg)
    d.rounded_rectangle((4, 4, S - 5, S - 5), radius=44, fill=(20, 12, 34, 255),
                        outline=(168, 85, 247, 255), width=8)
    # faint scanlines
    for y in range(16, S - 16, 12):
        d.line((16, y, S - 16, y), fill=(34, 22, 56, 255), width=3)

    glyph = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    layers = [((-9, 0), (34, 211, 238)), ((9, 0), (244, 114, 182)), ((0, 0), (237, 233, 254))]
    for off, col in layers:
        solid = Image.new("RGBA", (S, S), col + (255,))
        alpha = g_mask(off)
        if col == (237, 233, 254):
            # main glyph: vertical purple->white gradient
            grad = Image.new("RGBA", (S, S))
            gd = ImageDraw.Draw(grad)
            for y in range(S):
                t = y / S
                gd.line((0, y, S, y), fill=(int(192 + 50 * t), int(132 + 100 * t), 252, 255))
            solid = grad
        else:
            alpha = alpha.point(lambda v: int(v * 0.85))
        glyph.alpha_composite(Image.merge("RGBA", (*solid.convert("RGB").split(), alpha)))

    # glitch: shift a few horizontal slices of the G sideways
    for _ in range(5):
        y0 = random.randint(40, 200)
        h = random.randint(6, 16)
        dx = random.choice([-1, 1]) * random.randint(10, 22)
        strip = glyph.crop((0, y0, S, y0 + h))
        glyph.paste((0, 0, 0, 0), (0, y0, S, y0 + h))
        glyph.alpha_composite(strip, (max(0, dx), y0), (max(0, -dx), 0))
    bg.alpha_composite(glyph)

    icon = bg.resize((64, 64), Image.LANCZOS).filter(ImageFilter.UnsharpMask(radius=1, percent=60))
    icon.save(OUT, optimize=True)
    print("wrote", OUT)


if __name__ == "__main__":
    main()
