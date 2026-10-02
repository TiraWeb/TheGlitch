#!/usr/bin/env python3
"""Rank square slices of a red map for the tutorial world (read-only, offline).

Reads each chunk's heightmaps straight from the region files (no server load):
  water   = MOTION_BLOCKING_NO_LEAVES above OCEAN_FLOOR  (water/lava columns)
  leaves  = MOTION_BLOCKING above MOTION_BLOCKING_NO_LEAVES (tree canopy)
  slope   = std-dev of OCEAN_FLOOR (how hilly)
Only slices fully inside the imported map footprint (<world>.keep) count.

  python3 tutorial-slice-survey.py [--world glitch_red_eleria] [--size 240] [--step 64] [--top 8]
"""
import argparse
import glob
import io
import math
import os
import re
import struct
import zlib

ROOT = "/opt/theglitch/server/hub/dimensions/minecraft"
KEEP = "/opt/theglitch/server/plugins/GlitchWorldGen"
COORD = re.compile(r"r\.(-?\d+)\.(-?\d+)\.mca$")
MAPS = ("OCEAN_FLOOR", "MOTION_BLOCKING", "MOTION_BLOCKING_NO_LEAVES")


def read_nbt(f, t):
    if t == 1: return f.read(1)[0]
    if t == 2: return struct.unpack(">h", f.read(2))[0]
    if t == 3: return struct.unpack(">i", f.read(4))[0]
    if t == 4: return struct.unpack(">q", f.read(8))[0]
    if t == 5: return struct.unpack(">f", f.read(4))[0]
    if t == 6: return struct.unpack(">d", f.read(8))[0]
    if t == 7: return f.read(struct.unpack(">i", f.read(4))[0])
    if t == 8: return f.read(struct.unpack(">H", f.read(2))[0]).decode("utf-8", "replace")
    if t == 9:
        et = f.read(1)[0]
        return [read_nbt(f, et) for _ in range(struct.unpack(">i", f.read(4))[0])]
    if t == 10:
        out = {}
        while True:
            tt = f.read(1)[0]
            if tt == 0:
                return out
            name = f.read(struct.unpack(">H", f.read(2))[0]).decode("utf-8", "replace")
            out[name] = read_nbt(f, tt)
    if t == 11: return struct.unpack(f">{struct.unpack('>i', f.read(4))[0]}i", f.read(4 * struct.unpack(">i", f.read(4))[0])) if False else [struct.unpack(">i", f.read(4))[0] for _ in range(struct.unpack(">i", f.read(4))[0])]
    if t == 12:
        n = struct.unpack(">i", f.read(4))[0]
        return list(struct.unpack(f">{n}q", f.read(8 * n)))
    raise ValueError(t)


def unpack(longs, bits=9):
    per = 64 // bits
    mask = (1 << bits) - 1
    out = []
    for v in longs:
        v &= (1 << 64) - 1
        for i in range(per):
            out.append((v >> (i * bits)) & mask)
            if len(out) == 256:
                return out
    return out


def chunk_maps(region_dir, keep):
    """(cx, cz) -> {map: [256 heights]} for footprint chunks."""
    res = {}
    for path in glob.glob(os.path.join(region_dir, "*.mca")):
        rx, rz = map(int, COORD.search(os.path.basename(path)).groups())
        data = open(path, "rb").read()
        if len(data) < 8192:
            continue
        for i in range(1024):
            cx, cz = rx * 32 + i % 32, rz * 32 + i // 32
            if (cx, cz) not in keep:
                continue
            loc = data[i * 4:i * 4 + 4]
            if loc == b"\0\0\0\0":
                continue
            off = int.from_bytes(loc[:3], "big") * 4096
            ln = struct.unpack(">I", data[off:off + 4])[0]
            if data[off + 4] != 2:
                continue
            try:
                f = io.BytesIO(zlib.decompress(data[off + 5:off + 4 + ln]))
                f.read(1)
                f.read(struct.unpack(">H", f.read(2))[0])
                root = read_nbt(f, 10)
                hm = root.get("Heightmaps", {})
                if all(k in hm for k in MAPS):
                    res[(cx, cz)] = {k: unpack(hm[k]) for k in MAPS}
            except Exception:
                continue
    return res


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--world", default="glitch_red_eleria")
    ap.add_argument("--size", type=int, default=240)
    ap.add_argument("--step", type=int, default=64)
    ap.add_argument("--top", type=int, default=8)
    a = ap.parse_args()
    keep = set()
    for line in open(os.path.join(KEEP, a.world + ".keep")):
        x, z = line.split()
        keep.add((int(x), int(z)))
    maps = chunk_maps(os.path.join(ROOT, a.world, "region"), keep)
    print(f"{a.world}: {len(maps)} footprint chunks read")
    n = a.size // 16
    xs = [c[0] for c in maps]
    zs = [c[1] for c in maps]
    results = []
    step = max(1, a.step // 16)
    for cx0 in range(min(xs), max(xs) - n + 2, step):
        for cz0 in range(min(zs), max(zs) - n + 2, step):
            cells = [(cx0 + i, cz0 + j) for i in range(n) for j in range(n)]
            if any(c not in maps for c in cells):
                continue
            water = leaves = cols = 0
            floors = []
            for c in cells:
                m = maps[c]
                for k in range(0, 256, 4):  # every 4th column is plenty
                    of, mb, nl = m["OCEAN_FLOOR"][k], m["MOTION_BLOCKING"][k], m["MOTION_BLOCKING_NO_LEAVES"][k]
                    cols += 1
                    if nl > of:
                        water += 1
                    if mb > nl:
                        leaves += 1
                    floors.append(of)
            mean = sum(floors) / len(floors)
            std = math.sqrt(sum((h - mean) ** 2 for h in floors) / len(floors))
            wf, lf = water / cols, leaves / cols
            # want: dry, some cover, gently varied terrain
            score = wf * 10 + abs(lf - 0.2) * 2 + abs(std - 6) / 6
            cx, cz = (cx0 * 16 + a.size // 2), (cz0 * 16 + a.size // 2)
            results.append((score, cx, cz, wf, lf, std, mean - 64))
    results.sort()
    print(f"{len(results)} candidate slices of {a.size}x{a.size} (centre x,z | water | leaves | slope | mean y)")
    for s, cx, cz, wf, lf, std, my in results[:a.top]:
        print(f"  centre {cx:5d},{cz:5d}  water {wf:5.1%}  leaves {lf:5.1%}  slope {std:4.1f}  y~{my:5.0f}  score {s:.2f}")


if __name__ == "__main__":
    main()
