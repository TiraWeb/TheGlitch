#!/usr/bin/env python3
"""Build the full-window menu backgrounds used by GlitchQuests.

Source art: assets/ui-kits/medieval/{rewards,jobs}_template.png (GUI_pack.zip).
Each template is a 256x256 canvas drawn for a 3-row chest: the window's
top-left corner sits at template pixel (40, 83), so slot N's item lands on
template (48 + 18*(N%9), 101 + 18*(N//9)) and the player inventory lines up
with the painted grid at the bottom.

They are shown by putting a single bitmap glyph in the chest title
(font theglitch:menus). The title is drawn at window (8, 6); a space glyph
shifts left 48px and the bitmap's ascent 96 lifts it 89px, which puts
template (40, 83) exactly on the window corner. See GlitchQuests MenuTitles.

Edits vs the stock templates:
  * jobs  -> quests: banner re-lettered "QUESTS" (Q and T drawn in the same
    6x8 two-tone style, since the kit has no Q/T), and the big header box
    gets a 2x scroll icon from assets/icon-kits/document.
"""
from pathlib import Path
import json

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
KIT = ROOT / "assets" / "ui-kits" / "medieval"
DOCS = ROOT / "assets" / "icon-kits" / "document"
OUT = ROOT / "server" / "plugins" / "Nexo" / "pack" / "assets" / "theglitch"

TOP = (147, 111, 90, 255)     # upper half of banner letters
BOTTOM = (127, 93, 75, 255)   # lower half

LETTERS = {
    "Q": ["######.", "######.", "##..##.", "##..##.", "##..##.", "##.###.", "######.", "#######"],
    "U": ["##..##", "##..##", "##..##", "##..##", "##..##", "##..##", "######", "######"],
    "E": ["######", "######", "##....", "####..", "####..", "##....", "######", "######"],
    "S": ["######", "######", "##....", "######", "######", "....##", "######", "######"],
    "T": ["######", "######", "..##..", "..##..", "..##..", "..##..", "..##..", "..##.."],
}

# Glyph codepoints (private use, separate font so they can't collide with Nexo's).
SHIFT_BACK = ""   # space glyph, advance -48
REWARDS_BG = ""
QUESTS_BG = ""


def reletter(img, word, text_top, center_x, erase_box):
    x0, y0, x1, y1 = erase_box
    for y in range(y0, y1 + 1):
        bg = img.getpixel((x0 - 2, y))
        for x in range(x0, x1 + 1):
            if img.getpixel((x, y)) in (TOP, BOTTOM):
                img.putpixel((x, y), bg)
    width = sum(len(LETTERS[c][0]) for c in word) + len(word) - 1
    x = center_x - width // 2
    for c in word:
        rows = LETTERS[c]
        for dy, row in enumerate(rows):
            for dx, ch in enumerate(row):
                if ch == "#":
                    img.putpixel((x + dx, text_top + dy), TOP if dy < 4 else BOTTOM)
        x += len(rows[0]) + 1


def quests_background():
    img = Image.open(KIT / "jobs_template.png").convert("RGBA")
    # "JOBS" occupies x=114..140, y=22..29
    reletter(img, "QUESTS", 22, 127, (112, 22, 142, 29))
    # big header box interior: x=104..150, y=48..93
    icon = Image.open(DOCS / "scroll1.png").convert("RGBA").resize((32, 32), Image.NEAREST)
    img.alpha_composite(icon, (127 - 16, 71 - 16))
    return img


def main():
    tex = OUT / "textures" / "gui"
    tex.mkdir(parents=True, exist_ok=True)
    Image.open(KIT / "rewards_template.png").convert("RGBA").save(tex / "rewards_bg.png")
    quests_background().save(tex / "quests_bg.png")

    font = {
        "providers": [
            {"type": "space", "advances": {SHIFT_BACK: -48}},
            {"type": "bitmap", "file": "theglitch:gui/rewards_bg.png",
             "height": 256, "ascent": 96, "chars": [REWARDS_BG]},
            {"type": "bitmap", "file": "theglitch:gui/quests_bg.png",
             "height": 256, "ascent": 96, "chars": [QUESTS_BG]},
        ]
    }
    (OUT / "font").mkdir(parents=True, exist_ok=True)
    (OUT / "font" / "menus.json").write_text(json.dumps(font, indent=2) + "\n", encoding="utf-8")
    print("wrote", tex / "rewards_bg.png", tex / "quests_bg.png", OUT / "font" / "menus.json")


if __name__ == "__main__":
    main()
