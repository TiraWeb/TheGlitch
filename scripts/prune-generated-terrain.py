#!/usr/bin/env python3
"""Remove terrain that vanilla generated OUTSIDE a red world's imported map.

Background (2026-09-29): NMinimap's render-new-chunks kept loading the chunk
north of every chunk it drew, generating ~160 new region files of vanilla
terrain in each red world (there was no world border). This puts the world back
to the chunk set it had in a known-good backup: any chunk present now but absent
from the backup is dropped from region/, entities/ and poi/ (header entries are
zeroed; a .mca left with no chunks is deleted).

Run with the server STOPPED (region files are cached while it runs).

  python3 prune-generated-terrain.py --reference-root /tmp/old/server/hub/dimensions/minecraft \
      [--live-root /opt/theglitch/server/hub/dimensions/minecraft] [--apply]

Without --apply it only reports. The reference root must contain
<world>/region/*.mca from a backup taken BEFORE the unwanted generation.
"""
import argparse
import glob
import os
import re
import sys

WORLDS = ("glitch_red", "glitch_red_eleria", "glitch_red_horizons")
KINDS = ("region", "entities", "poi")
COORD = re.compile(r"r\.(-?\d+)\.(-?\d+)\.mca$")


def reference_chunks(root, world):
    keep = set()
    for path in glob.glob(os.path.join(root, world, "region", "*.mca")):
        rx, rz = map(int, COORD.search(os.path.basename(path)).groups())
        with open(path, "rb") as fh:
            header = fh.read(4096)
        for i in range(1024):
            if header[i * 4:i * 4 + 4] != b"\0\0\0\0":
                keep.add((rx * 32 + i % 32, rz * 32 + i // 32))
    return keep


def keep_file_chunks(keep_dir, world):
    keep = set()
    path = os.path.join(keep_dir, world + ".keep")
    if os.path.isfile(path):
        for line in open(path):
            p = line.split()
            if len(p) == 2:
                keep.add((int(p[0]), int(p[1])))
    return keep


def prune_file(path, keep, apply):
    rx, rz = map(int, COORD.search(os.path.basename(path)).groups())
    with open(path, "rb" if not apply else "r+b") as fh:
        header = bytearray(fh.read(8192))
        if len(header) < 8192:
            return 0, 0, False
        removed = kept = 0
        for i in range(1024):
            if header[i * 4:i * 4 + 4] == b"\0\0\0\0":
                continue
            if (rx * 32 + i % 32, rz * 32 + i // 32) in keep:
                kept += 1
                continue
            removed += 1
            header[i * 4:i * 4 + 4] = b"\0\0\0\0"            # location
            header[4096 + i * 4:4096 + i * 4 + 4] = b"\0\0\0\0"  # timestamp
        if apply and removed:
            fh.seek(0)
            fh.write(header)
    return removed, kept, kept == 0


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--reference-root")
    ap.add_argument("--keep-dir")
    ap.add_argument("--live-root", default="/opt/theglitch/server/hub/dimensions/minecraft")
    ap.add_argument("--apply", action="store_true")
    args = ap.parse_args()
    if not (args.reference_root or args.keep_dir):
        ap.error("need --reference-root or --keep-dir")

    for world in WORLDS:
        keep = (keep_file_chunks(args.keep_dir, world) if args.keep_dir
                else reference_chunks(args.reference_root, world))
        if not keep:
            sys.exit(f"no reference chunks for {world} — refusing to continue")
        for kind in KINDS:
            removed = kept = deleted = 0
            for path in sorted(glob.glob(os.path.join(args.live_root, world, kind, "*.mca"))):
                r, k, empty = prune_file(path, keep, args.apply)
                removed += r
                kept += k
                if empty:
                    deleted += 1
                    if args.apply:
                        os.remove(path)
            print(f"{world:22} {kind:9} chunks removed {removed:7} kept {kept:7} files {'deleted' if args.apply else 'to delete'}: {deleted}")
    if not args.apply:
        print("dry run — nothing changed (pass --apply with the server stopped)")


if __name__ == "__main__":
    main()
