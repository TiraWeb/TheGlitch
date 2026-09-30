#!/usr/bin/env python3
"""Write each red world's imported-map footprint (chunk list) for GlitchWorldGen.

A chunk that came from the imported map save was upgraded by the server and
carries a `blending_data` tag; chunks the server generated itself (Chunky
pregens, minimap renders, extraction scans) never do. Verified 2026-09-30:
glitch_red's blending set is exactly the 27,467 chunks of the original
MMORPG_Odyssey download.

Writes <out>/<world>.keep ("x z" per line) and prints each footprint's
bounding box + a suggested square world border.

  python3 map-footprint.py [--root /opt/theglitch/server/hub/dimensions/minecraft]
                           [--out /opt/theglitch/server/plugins/GlitchWorldGen]

Read-only on the world; run it BEFORE pruning (pruning keeps these chunks).
"""
import argparse
import glob
import io
import os
import re
import struct
import zlib

WORLDS = ("glitch_red", "glitch_red_eleria", "glitch_red_horizons")
COORD = re.compile(r"r\.(-?\d+)\.(-?\d+)\.mca$")


def has_blending(raw):
    """Walk the root compound's direct children looking for blending_data (no full NBT decode)."""
    f = io.BytesIO(raw)
    if f.read(1) != b"\x0a":
        return False
    f.read(struct.unpack(">H", f.read(2))[0])

    def skip(t):
        if t in (1,): f.read(1)
        elif t == 2: f.read(2)
        elif t in (3, 5): f.read(4)
        elif t in (4, 6): f.read(8)
        elif t == 7: f.read(struct.unpack(">i", f.read(4))[0])
        elif t == 8: f.read(struct.unpack(">H", f.read(2))[0])
        elif t == 9:
            et = f.read(1)[0]
            for _ in range(struct.unpack(">i", f.read(4))[0]):
                skip(et)
        elif t == 10:
            while True:
                tt = f.read(1)[0]
                if tt == 0:
                    return
                f.read(struct.unpack(">H", f.read(2))[0])
                skip(tt)
        elif t == 11: f.read(4 * struct.unpack(">i", f.read(4))[0])
        elif t == 12: f.read(8 * struct.unpack(">i", f.read(4))[0])
        else: raise ValueError(t)

    while True:
        t = f.read(1)
        if not t or t[0] == 0:
            return False
        name = f.read(struct.unpack(">H", f.read(2))[0])
        if name == b"blending_data":
            return True
        skip(t[0])


def footprint(region_dir):
    keep = set()
    for path in glob.glob(os.path.join(region_dir, "*.mca")):
        rx, rz = map(int, COORD.search(os.path.basename(path)).groups())
        data = open(path, "rb").read()
        if len(data) < 8192:
            continue
        for i in range(1024):
            loc = data[i * 4:i * 4 + 4]
            if loc == b"\0\0\0\0":
                continue
            off = int.from_bytes(loc[:3], "big") * 4096
            ln = struct.unpack(">I", data[off:off + 4])[0]
            if data[off + 4] != 2:
                continue
            try:
                if has_blending(zlib.decompress(data[off + 5:off + 4 + ln])):
                    keep.add((rx * 32 + i % 32, rz * 32 + i // 32))
            except Exception:
                continue
    return keep


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--root", default="/opt/theglitch/server/hub/dimensions/minecraft")
    ap.add_argument("--out", default="/opt/theglitch/server/plugins/GlitchWorldGen")
    args = ap.parse_args()
    os.makedirs(args.out, exist_ok=True)
    for world in WORLDS:
        keep = footprint(os.path.join(args.root, world, "region"))
        if not keep:
            print(f"{world}: no blending_data chunks found — skipped")
            continue
        with open(os.path.join(args.out, world + ".keep"), "w") as fh:
            fh.writelines(f"{x} {z}\n" for x, z in sorted(keep))
        xs = [k[0] for k in keep]
        zs = [k[1] for k in keep]
        bx0, bx1, bz0, bz1 = min(xs) * 16, max(xs) * 16 + 16, min(zs) * 16, max(zs) * 16 + 16
        size = max(bx1 - bx0, bz1 - bz0) + 32
        print(f"{world}: {len(keep)} map chunks, blocks x {bx0}..{bx1} z {bz0}..{bz1}; "
              f"border center ({(bx0 + bx1) / 2}, {(bz0 + bz1) / 2}) size {size}")


if __name__ == "__main__":
    main()
