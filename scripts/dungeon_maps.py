#!/usr/bin/env python3
"""Extract the dungeon map worlds and find each map's entrance + boss arena.

Reads the licensed BreadBuilds bundle (Dungeon_maps.zip in the repo root,
gitignored) and writes, under dungeon-private/ (also gitignored):

  maps/<id>/            the newest Java world of each map, without player data
  layout.json           {id: {spawn: [x,y,z], arena: [x,y,z], ...}}

Arena detection works on the region files directly (small pure-Python NBT +
Anvil reader, numpy for the grid): flood-fill the walkable cells reachable
from the world spawn, then pick the most open spot far from the entrance.
Maps where that guess is wrong get an override in layout-overrides.json
(same shape, merged on top) — see docs/DUNGEONS.md.

Usage: python scripts/dungeon_maps.py [--only id,id] [--no-extract]
"""
import argparse
import gzip
import io
import json
import math
import os
import re
import shutil
import struct
import sys
import zipfile
import zlib
from collections import deque

import numpy as np

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BUNDLE = os.path.join(REPO, "Dungeon_maps.zip")
OUT = os.path.join(REPO, "dungeon-private")

# dungeon id -> inner zip name prefix (Mega Dungeon Bundle/<prefix>... .zip)
MAPS = {
    "small":   "Small dungeon",
    "haunted": "Haunted Dungeon",
    "puzzle":  "Puzzle Dungeon",
    "desert":  "Desert Dungeon",
    "aztec":   "Aztec progressive dungeon",
    "crimson": "Crimson Dungeon",
    "nether":  "Nether Dungeon",
    "pirate":  "Pirate Multiroom dungeon",
    "medium":  "Medium Dungeon",
    "town":    "Dungeon Town",
    "mythic":  "Mythic Dungeon",
}

SKIP_PARTS = ("playerdata/", "stats/", "advancements/", "session.lock", "__MACOSX", "/._")


# --------------------------------------------------------------------------- NBT
def _nbt(buf, tag):
    if tag == 1:  return struct.unpack(">b", buf.read(1))[0]
    if tag == 2:  return struct.unpack(">h", buf.read(2))[0]
    if tag == 3:  return struct.unpack(">i", buf.read(4))[0]
    if tag == 4:  return struct.unpack(">q", buf.read(8))[0]
    if tag == 5:  return struct.unpack(">f", buf.read(4))[0]
    if tag == 6:  return struct.unpack(">d", buf.read(8))[0]
    if tag == 7:
        n = struct.unpack(">i", buf.read(4))[0]; return buf.read(n)
    if tag == 8:
        n = struct.unpack(">H", buf.read(2))[0]; return buf.read(n).decode("utf-8", "replace")
    if tag == 9:
        t, n = struct.unpack(">bi", buf.read(5)); return [_nbt(buf, t) for _ in range(n)]
    if tag == 10:
        d = {}
        while True:
            t = buf.read(1)[0]
            if t == 0: return d
            n = struct.unpack(">H", buf.read(2))[0]
            key = buf.read(n).decode("utf-8", "replace")  # read before the value
            d[key] = _nbt(buf, t)
    if tag == 11:
        n = struct.unpack(">i", buf.read(4))[0]
        return np.frombuffer(buf.read(4 * n), dtype=">i4")
    if tag == 12:
        n = struct.unpack(">i", buf.read(4))[0]
        return np.frombuffer(buf.read(8 * n), dtype=">i8")
    raise ValueError(f"bad tag {tag}")


def read_nbt(data):
    buf = io.BytesIO(data)
    t = buf.read(1)[0]
    n = struct.unpack(">H", buf.read(2))[0]
    buf.read(n)
    return _nbt(buf, t)


# ------------------------------------------------------------------------ Anvil
AIRS = {"minecraft:air", "minecraft:cave_air", "minecraft:void_air"}
# Blocks a player walks through (counted as "open" for standing room).
PASSABLE_RE = re.compile(
    r"air$|(^|_)torch$|_sign$|_banner$|carpet$|button$|lever$|pressure_plate$|"
    r"_door$|fence_gate$|^minecraft:(cobweb|powder_snow)$|"
    r"^minecraft:(short_grass|grass|tall_grass|fern|large_fern|dead_bush|vine|"
    r"cobweb|snow|rail|redstone_wire|light|water|flower_pot|candle|lantern|"
    r"soul_lantern|chain|ladder|scaffolding|string|tripwire)$|flower|sapling|"
    r"mushroom$|_candle$|coral_fan$|glow_lichen|hanging_roots|moss_carpet|"
    r"^minecraft:(poppy|dandelion|azure_bluet|oxeye_daisy|cornflower|allium|"
    r"blue_orchid|lily_of_the_valley|wither_rose)$")
LIQUID_RE = re.compile(r"^minecraft:(water|lava)$")


def classify(name):
    """0 = air, 1 = solid (can stand on), 2 = passable decoration, 3 = liquid."""
    if name in AIRS:
        return 0
    if LIQUID_RE.match(name):
        return 3
    if PASSABLE_RE.search(name):
        return 2
    return 1


def load_region_blocks(world_dir):
    """Return (grid[y,z,x] uint8 class, x0, y0, z0) covering every non-empty section."""
    region_dir = os.path.join(world_dir, "region")
    sections = []  # (cx, sy, cz, 4096 class array)
    for fn in os.listdir(region_dir):
        m = re.match(r"r\.(-?\d+)\.(-?\d+)\.mca$", fn)
        if not m:
            continue
        with open(os.path.join(region_dir, fn), "rb") as f:
            data = f.read()
        if len(data) < 8192:
            continue
        for i in range(1024):
            off = int.from_bytes(data[i * 4:i * 4 + 3], "big") * 4096
            if not off:
                continue
            length = struct.unpack(">i", data[off:off + 4])[0]
            comp = data[off + 4]
            raw = data[off + 5:off + 4 + length]
            try:
                if comp == 2:
                    raw = zlib.decompress(raw)
                elif comp == 1:
                    raw = gzip.decompress(raw)
                elif comp != 3:
                    continue
                chunk = read_nbt(raw)
            except Exception:
                continue
            chunk = chunk.get("Level", chunk)
            cx, cz = chunk.get("xPos"), chunk.get("zPos")
            for sec in chunk.get("sections", chunk.get("Sections", [])) or []:
                bs = sec.get("block_states")
                if not bs:
                    continue
                palette = [p.get("Name", "minecraft:air") for p in bs.get("palette", [])]
                if not palette or (len(palette) == 1 and palette[0] in AIRS):
                    continue
                cls = np.array([classify(n) for n in palette], dtype=np.uint8)
                if len(palette) == 1:
                    arr = np.full(4096, cls[0], dtype=np.uint8)
                else:
                    bits = max(4, (len(palette) - 1).bit_length())
                    per = 64 // bits
                    longs = bs["data"].astype(np.uint64)
                    idx = np.empty(len(longs) * per, dtype=np.uint64)
                    mask = np.uint64((1 << bits) - 1)
                    for k in range(per):
                        idx[k::per] = (longs >> np.uint64(k * bits)) & mask
                    idx = idx[:4096].astype(np.int64)
                    idx[idx >= len(cls)] = 0
                    arr = cls[idx]
                sections.append((cx, sec["Y"], cz, arr))
    if not sections:
        raise RuntimeError(f"no block data in {world_dir}")
    xs = [s[0] for s in sections]; ys = [s[1] for s in sections]; zs = [s[2] for s in sections]
    x0, y0, z0 = min(xs) * 16, min(ys) * 16, min(zs) * 16
    grid = np.zeros(((max(ys) - min(ys) + 1) * 16, (max(zs) - min(zs) + 1) * 16,
                     (max(xs) - min(xs) + 1) * 16), dtype=np.uint8)
    for cx, sy, cz, arr in sections:
        gy, gz, gx = sy * 16 - y0, cz * 16 - z0, cx * 16 - x0
        grid[gy:gy + 16, gz:gz + 16, gx:gx + 16] = arr.reshape(16, 16, 16)  # y, z, x
    return grid, x0, y0, z0


def analyse(world_dir, pick=0):
    """Rank open rooms as arena candidates; start players a short walk away.

    Every dungeon here is boss-arena-only, so the start point is placed
    START_WALK path blocks from the chosen arena inside the same space rather
    than at the map's front door. `pick` selects a candidate (0 = most open);
    set it per map in layout-overrides.json after looking at the preview.
    """
    grid, x0, y0, z0 = load_region_blocks(world_dir)
    H, D, W = grid.shape
    solid = grid == 1
    open_ = (grid == 0) | (grid == 2)
    stand = np.zeros_like(solid)
    stand[1:-1] = solid[:-2] & open_[1:-1] & open_[2:]
    # arena floor: 5 blocks of headroom (boss models are tall)
    tall = stand.copy()
    for k in range(2, 5):
        tall[:-k] &= open_[k:]
    tall[-5:] = False
    # indoors: some solid block 5..40 above. Rooftops and the flat ground
    # around a showcase build have open sky and never make an arena.
    cs = np.concatenate([np.zeros((1, D, W), np.int32), solid.cumsum(0, dtype=np.int32)])
    lo = np.clip(np.arange(H) + 5, 0, H)
    hi = np.clip(np.arange(H) + 41, 0, H)
    tall &= (cs[hi] - cs[lo]) > 0

    # openness = arena-floor cells in a 25x25 window over y-1..y+1
    scores = np.zeros(stand.shape, dtype=np.int32)
    R = 12
    for y in range(1, H - 1):
        if not tall[y].any():
            continue
        layer = (tall[y - 1] | tall[y] | tall[y + 1]).astype(np.int32)
        c = np.pad(layer, ((R + 1, R), (R + 1, R))).cumsum(0).cumsum(1)
        n = 2 * R + 1
        win = c[n:, n:] - c[:-n, n:] - c[n:, :-n] + c[:-n, :-n]
        scores[y] = np.where(tall[y], win, 0)

    order = np.argsort(scores, axis=None)[::-1]
    cands = []
    for flat in order[:200000]:
        sc = int(scores.flat[flat])
        if sc < 80 or len(cands) >= 8:
            break
        y, z, x = np.unravel_index(flat, scores.shape)
        if any(abs(z - cz) < 36 and abs(x - cx) < 36 and abs(y - cy) < 10 for _, cy, cz, cx in cands):
            continue
        cands.append((sc, int(y), int(z), int(x)))
    if not cands:
        raise RuntimeError("no open room found")

    def walk(seed, limit):
        dist = {seed: 0}
        q = deque([seed])
        while q:
            y, z, x = q.popleft()
            d = dist[(y, z, x)] + 1
            if d > limit:
                continue
            for dz, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nz, nx = z + dz, x + dx
                if not (0 <= nz < D and 0 <= nx < W):
                    continue
                for dy in (0, 1, -1, -2):
                    ny = y + dy
                    if 0 < ny < H - 2 and stand[ny, nz, nx] and (ny, nz, nx) not in dist:
                        if dy == 1 and not open_[min(H - 1, y + 2), z, x]:
                            continue
                        dist[(ny, nz, nx)] = d
                        q.append((ny, nz, nx))
                        break
        return dist

    out = []
    for sc, y, z, x in cands:
        dist = walk((y, z, x), START_WALK)
        # farthest reachable cell within the walk budget, preferring the same floor
        start = max(dist, key=lambda c: (dist[c] - 3 * abs(c[0] - y)))
        out.append({
            "arena": [x + x0 + 0.5, y + y0, z + z0 + 0.5],
            "spawn": [start[2] + x0 + 0.5, start[0] + y0, start[1] + z0 + 0.5],
            "open": sc,
            "walk": dist[start],
        })
    pick = min(pick, len(out) - 1)
    if PREVIEW:
        render_preview(world_dir, solid, tall, x0, y0, z0, out, pick)
    chosen = dict(out[pick])
    chosen["pick"] = pick
    chosen["candidates"] = out
    chosen["bounds"] = [x0, y0, z0, x0 + W, y0 + H, z0 + D]
    return chosen


PREVIEW = False
START_WALK = 16  # path blocks between the start point and the arena centre


def render_preview(world_dir, solid, floor, x0, y0, z0, cands, pick, scale=4):
    """Top-down PNG of walkable floors (brighter = higher) with every arena
    candidate numbered; the picked one is red, its start point green.
    Grid lines every 32 blocks, labelled with world X/Z. For eyeballing only."""
    from PIL import Image, ImageDraw
    H, D, W = solid.shape
    ys = np.arange(H)[:, None, None]
    has = floor.any(0)
    top = np.where(has, (floor * ys).max(0), 0).astype(float)
    base = np.where(solid.any(0), 45, 25).astype(float)
    shade = np.where(has, 70 + 170 * top / max(1, top.max()), base).astype(np.uint8)
    img = np.stack([shade, shade, shade], -1)
    im = Image.fromarray(img).resize((W * scale, D * scale), Image.NEAREST)
    dr = ImageDraw.Draw(im)
    for gx in range((x0 // 32 + 1) * 32, x0 + W, 32):
        px = (gx - x0) * scale
        dr.line([(px, 0), (px, D * scale)], fill=(60, 60, 110))
        dr.text((px + 2, 2), str(gx), fill=(150, 150, 255))
    for gz in range((z0 // 32 + 1) * 32, z0 + D, 32):
        pz = (gz - z0) * scale
        dr.line([(0, pz), (W * scale, pz)], fill=(60, 60, 110))
        dr.text((2, pz + 2), str(gz), fill=(150, 150, 255))
    for i, c in enumerate(cands):
        ax, ay, az = c["arena"]
        cx, cz = (ax - x0) * scale, (az - z0) * scale
        col = (255, 50, 50) if i == pick else (255, 200, 0)
        r = 8 * scale
        dr.ellipse([cx - r, cz - r, cx + r, cz + r], outline=col, width=3)
        dr.text((cx - 4, cz - 6), f"{i} y{int(ay)}", fill=col)
        if i == pick:
            sx, sy, sz = c["spawn"]
            px, pz = (sx - x0) * scale, (sz - z0) * scale
            dr.ellipse([px - 6, pz - 6, px + 6, pz + 6], outline=(0, 255, 0), width=3)
    out = os.path.join(OUT, "preview")
    os.makedirs(out, exist_ok=True)
    im.save(os.path.join(out, os.path.basename(world_dir) + ".png"))


# -------------------------------------------------------------------- extraction
def pick_world(inner):
    """Choose the newest-version world/ folder inside one map zip."""
    worlds = {}
    for name in inner.namelist():
        m = re.search(r"^(.*?files for (?:Java )?([\d.]+)\+/world/)level\.dat$", name)
        if m and "__MACOSX" not in name:
            ver = tuple(int(p) for p in m.group(2).split("."))
            worlds[ver] = m.group(1)
    if not worlds:
        raise RuntimeError("no world/level.dat found")
    return worlds[max(worlds)], ".".join(map(str, max(worlds)))


def extract(did, prefix, bundle):
    inner_name = next(n for n in bundle.namelist() if n.split("/")[-1].startswith(prefix) and n.endswith(".zip"))
    inner = zipfile.ZipFile(io.BytesIO(bundle.read(inner_name)))
    root, ver = pick_world(inner)
    dest = os.path.join(OUT, "maps", did)
    if os.path.isdir(dest):
        shutil.rmtree(dest)
    count = 0
    for name in inner.namelist():
        if not name.startswith(root) or name.endswith("/"):
            continue
        rel = name[len(root):]
        if any(s in name for s in SKIP_PARTS):
            continue
        path = os.path.join(dest, *rel.split("/"))
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "wb") as f:
            f.write(inner.read(name))
        count += 1
    return dest, ver, count


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--only", default="")
    ap.add_argument("--no-extract", action="store_true")
    ap.add_argument("--preview", action="store_true", help="write dungeon-private/preview/<id>.png")
    a = ap.parse_args()
    global PREVIEW
    PREVIEW = a.preview
    only = [s for s in a.only.split(",") if s]
    os.makedirs(OUT, exist_ok=True)
    layout_path = os.path.join(OUT, "layout.json")
    layout = json.load(open(layout_path)) if os.path.exists(layout_path) else {}
    bundle = None if a.no_extract else zipfile.ZipFile(BUNDLE)
    ov_path = os.path.join(OUT, "layout-overrides.json")
    overrides = json.load(open(ov_path)) if os.path.exists(ov_path) else {}
    for did, prefix in MAPS.items():
        if only and did not in only:
            continue
        if bundle:
            dest, ver, count = extract(did, prefix, bundle)
            print(f"[{did}] extracted {count} files from the {ver}+ world")
        else:
            dest = os.path.join(OUT, "maps", did)
        o = overrides.get(did, {})
        info = analyse(dest, o.get("pick", 0))
        info.update({k: v for k, v in o.items() if k != "pick"})
        layout[did] = info
        cl = "  ".join(f"#{i}:{c['arena']} open={c['open']}" for i, c in enumerate(info["candidates"]))
        print(f"[{did}] pick #{info['pick']} spawn={info['spawn']} arena={info['arena']}\n    {cl}")
    with open(layout_path, "w") as f:
        json.dump(layout, f, indent=2)
    print(f"wrote {layout_path}")


if __name__ == "__main__":
    sys.exit(main())
