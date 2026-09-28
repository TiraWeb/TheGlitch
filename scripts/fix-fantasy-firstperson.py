#!/usr/bin/env python3
"""Cap the first-person size of the Skulpt fantasy-arsenal weapon models.

The pack's models are 30-60 model units tall and several also use a 2x
first-person scale, so a held weapon (e.g. Moonlight, ~45 units at 1x)
covered a third of the screen. This rescales firstperson_{right,left}hand so
the model's apparent height is at most TARGET units, and shifts the
translation so the grip (near the bottom of the model) stays in the hand.

Minecraft applies display transforms as v' = t + R(S(v - c)) + c with
c = (8, 8, 8) and R = rotationXYZ(rx, ry, rz); keeping the grip point g fixed
under a scale change s0 -> s1 needs t' = t + R((s0 - s1)(g - c)).

Idempotent: models already at or under TARGET are left alone.
Usage: python3 scripts/fix-fantasy-firstperson.py
"""
import json
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MODELS = ROOT / "server/plugins/Nexo/pack/assets/skulpt/models/arsenals/fantasy_arsenal"
TARGET = 28.0  # apparent first-person height in model units (vanilla sword ~11)
GRIP_ABOVE_BOTTOM = 6.0


def rot_xyz(v, deg):
    # Rotate about X, then Y, then Z (JOML rotationXYZ on column vectors = Rx*Ry*Rz*v).
    rx, ry, rz = (math.radians(a) for a in deg)
    x, y, z = v
    # Rz
    x, y = x * math.cos(rz) - y * math.sin(rz), x * math.sin(rz) + y * math.cos(rz)
    # Ry
    x, z = x * math.cos(ry) + z * math.sin(ry), -x * math.sin(ry) + z * math.cos(ry)
    # Rx
    y, z = y * math.cos(rx) - z * math.sin(rx), y * math.sin(rx) + z * math.cos(rx)
    return [x, y, z]


def main():
    changed = 0
    for path in sorted(MODELS.glob("*.json")):
        model = json.loads(path.read_text(encoding="utf-8"))
        elements = model.get("elements") or []
        if not elements:
            continue
        ys = [e[k][1] for e in elements for k in ("from", "to")]
        xs = [e[k][0] for e in elements for k in ("from", "to")]
        zs = [e[k][2] for e in elements for k in ("from", "to")]
        height = max(ys) - min(ys)
        grip = [(min(xs) + max(xs)) / 2, min(ys) + GRIP_ABOVE_BOTTOM, (min(zs) + max(zs)) / 2]
        display = model.get("display") or {}
        touched = False
        for key in ("firstperson_righthand", "firstperson_lefthand"):
            d = display.get(key)
            if d is None:
                continue
            s0 = (d.get("scale") or [1, 1, 1])[0]
            if height * s0 <= TARGET + 0.01:
                continue
            s1 = round(TARGET / height, 3)
            off = [(s0 - s1) * (g - 8.0) for g in grip]
            shift = rot_xyz(off, d.get("rotation") or [0, 0, 0])
            t = d.get("translation") or [0, 0, 0]
            # Vanilla clamps display translation to +-80.
            d["translation"] = [round(max(-80, min(80, t[i] + shift[i])), 3) for i in range(3)]
            d["scale"] = [s1, s1, s1]
            touched = True
        if touched:
            path.write_text(json.dumps(model, separators=(",", ":")), encoding="utf-8")
            changed += 1
            print(f"{path.name}: height {height:.1f} -> first-person scale {display['firstperson_righthand']['scale'][0]}")
    print(f"updated {changed} model(s)")


if __name__ == "__main__":
    main()
