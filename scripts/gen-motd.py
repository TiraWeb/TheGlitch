#!/usr/bin/env python3
"""Build the server-list MOTD (two centred lines, hex gradient title).

Prints the value for `motd=` in server.properties as backslash-u escapes (see the
MC-2215 note in bootstrap.sh). Paper renders legacy hex colours written as
section-x-r-r-g-g-b-b. Centring uses the vanilla font's advance widths; the
server-list text area is about 270px wide.

Usage: python scripts/gen-motd.py
"""
WIDTH = 270
NARROW = {"i": 2, "l": 3, "!": 2, ".": 2, ",": 2, "'": 2, ":": 2, ";": 2, "|": 2, "t": 4,
          "I": 4, " ": 4, "[": 4, "]": 4, "(": 5, ")": 5, "k": 5, "f": 5, "*": 5, "<": 5,
          ">": 5, "•": 4, "✦": 8, "»": 7}
SECTION = "§"


def hexcode(rgb):
    return SECTION + "x" + "".join(SECTION + c for c in "%02x%02x%02x" % rgb)


def width(text, bold=False):
    return sum(NARROW.get(c, 6) + (1 if bold and c != " " else 0) for c in text)


def gradient(text, a, b, bold=True):
    out, n = [], max(1, len(text) - 1)
    for i, c in enumerate(text):
        t = i / n
        rgb = tuple(round(a[k] + (b[k] - a[k]) * t) for k in range(3))
        out.append(hexcode(rgb) + (SECTION + "l" if bold else "") + c)
    return "".join(out)


def centre(plain_width, text):
    pad = max(0, (WIDTH - plain_width) // 2 // 4)
    # a colour code first: java.util.Properties strips leading spaces from a value
    return SECTION + "r" + " " * pad + text


PURPLE, PINK = (192, 132, 252), (244, 114, 182)
title, tag = "THE GLITCH", " [ALPHA]"
line1_plain = width("✦ ") + width(title, bold=True) + width(" ✦") + width(tag)
line1 = (hexcode((168, 85, 247)) + "✦ " + gradient(title, PURPLE, PINK) + SECTION + "r "
         + hexcode((168, 85, 247)) + "✦" + SECTION + "8 [" + hexcode((250, 204, 21)) + SECTION + "lALPHA"
         + SECTION + "r" + SECTION + "8]")
sub = "Rogue-lite extraction • Boss dungeons • Classes"
line2 = (SECTION + "7Rogue-lite extraction " + hexcode((168, 85, 247)) + "• " + SECTION + "7Boss dungeons "
         + hexcode((168, 85, 247)) + "• " + SECTION + "7Classes")
motd = centre(line1_plain, line1) + "\n" + centre(width(sub), line2)
print("".join("\\n" if c == "\n" else c if ord(c) < 128 else "\\u%04X" % ord(c) for c in motd))
