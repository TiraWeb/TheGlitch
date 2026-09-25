#!/usr/bin/env python3
"""Stage the licensed dungeon boss packs for the live server.

Reads Dungeon_bosses/ (repo root, gitignored — purchased ModelEngine +
MythicMobs packs) and writes dungeon-private/ (also gitignored):

  meg/<pack>/...                       ModelEngine blueprints (+ their pngs)
  mm/Packs/GlitchDungeonBosses/...     every mob + skill file, adjusted below
  nexo/pack/assets/<ns>/...            sounds + bossbar/VFX textures
  nexo/glyphs/dungeon_bosses/glyphs.yml

Every skill, animation, model{} line and VFX mob is copied as shipped. The
only edits are the ones needed to live alongside the rest of the server:

* Sounds the packs put in minecraft:sounds.json move to the `glitchbosses:`
  namespace (the repo owns minecraft:sounds.json and overwrites it on every
  Nexo sync); the skills' sound{s=...} references are rewritten to match.
* Bossbar / VFX font glyphs the packs map onto real characters in the
  default font (the illusionist takes over "¿", the earth spider "💢", the
  mage a run of Khmer letters) become Nexo glyphs on private-use codepoints
  U+E9A0.., so those characters keep rendering normally in chat.
* Boss HP is set per dungeon tier (packs ship 30-500 HP, tuned for solo
  showcase fights); bosses never despawn mid-fight.
* Duplicate skill definitions shared by the two samus2002 packs are kept once.

Usage: python scripts/dungeon_bosses.py
"""
import io
import json
import os
import re
import shutil
import sys
import zipfile

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(REPO, "Dungeon_bosses")
OUT = os.path.join(REPO, "dungeon-private")
MM_OUT = os.path.join(OUT, "mm", "Packs", "GlitchDungeonBosses")
NEXO_ASSETS = os.path.join(OUT, "nexo", "pack", "assets")
GLYPH_FILE = os.path.join(OUT, "nexo", "glyphs", "dungeon_bosses", "glyphs.yml")
NS = "glitchbosses"

# pack id -> (source root, {mob id: health}, skill blocks to drop as duplicates)
PACKS = {
    "goblin":    ("goblin_boss-amonde/goblin_boss-amonde", {"am_goblin_boss": 900}, []),
    "skeleton":  ("skeleton_boss-amonde", {"skeleton_boss": 900}, []),
    "illusionist": ("bl_illusionist", {"bl_illusionist": 900}, []),
    "earth_spider": ("bl_earth_spider", {"bl_earth_spider": 1000}, []),
    "moldar":    ("hv_moldar", {"hv_moldar": 1400}, []),
    "akaza":     ("Akaza/Akaza v1.0/For Nexo/plugins", {"Boss-Akaza": 1400}, []),
    "ember_claw": ("ModelFoundry's Ember Claw Sub-hitbox 1.0.0/plugins", {"mf_ember_claw": 1500}, []),
    # The archer pack is the BOSS_ONLY edition: its skills summon ~25
    # VFX_AwakenedArcher_* mobs, meta-skills and awakened_archer_sounds that
    # only ship in the FULL edition, so it is left out until that is supplied
    # (the Pirate dungeon uses STAND_INS meanwhile). Re-enable with:
    # "archer": ("samus2002_RPG_CLASS_BOSS_ARCHER_BOSS_ONLY/samus2002_RPG_CLASS_BOSS_ARCHER_BOSS_ONLY",
    #            {"Archer": 1500}, ["Changetarget", "Cancelattack", "BOSS_BLOCK"]),
    "selenia":   ("hv_selenia/hv_selenia", {"hv_selenia": 2200}, []),
    "lovers":    ("TheLoversBossfight/The Lovers, Samurai Bossfight/plugins",
                  {"Hanashiguro": 700, "The_Lovers": 700, "Hanashiguro2": 900}, []),
    "mage":      ("samus2002_RPG_CLASS_BOSS_MAGE_FULL/samus2002_RPG_CLASS_BOSS_MAGE_FULL", {"Mage": 2200}, []),
}
# Dungeon versions of existing server mobs, used where a pack is unavailable.
STAND_INS = """Dungeon_GlitchReaver:
  Template: GlitchReaver
  Display: '&4&lGlitch Reaver'
  Health: 1500
  Options:
    Despawn: false
    PreventOtherDrops: true
"""
# Obvious vendor typos that stop a skill line from loading (old -> new).
FIXES = {"ember_claw": [("<random.float-.30to-50>", "<random.float.-30to-50>")]}
SKIP_FILES = {"mf_ember_claw_pet.yml", "packinfo.yml"}  # pet needs MCPets; packinfo rewritten

_next_char = [0xE9A0]


def alloc_char():
    c = chr(_next_char[0])
    _next_char[0] += 1
    return c


glyphs = {}          # glyph id -> config dict
sounds = {}          # glitchbosses sounds.json


def copy_file(data, dest):
    os.makedirs(os.path.dirname(dest), exist_ok=True)
    with open(dest, "wb") as f:
        f.write(data)


class Source:
    """Uniform read access to a directory or a zip inside a pack."""

    def __init__(self, zpath=None, root=None):
        self.z = zipfile.ZipFile(zpath) if zpath else None
        self.root = root

    def names(self):
        if self.z:
            return [n for n in self.z.namelist() if not n.endswith("/")]
        out = []
        for dp, _, fn in os.walk(self.root):
            for f in fn:
                out.append(os.path.relpath(os.path.join(dp, f), self.root).replace("\\", "/"))
        return out

    def read(self, name):
        if self.z:
            return self.z.read(name)
        with open(os.path.join(self.root, name), "rb") as f:
            return f.read()


def import_minecraft_sounds(src, prefix=""):
    """Move a pack's minecraft:sounds.json entries into glitchbosses:.
    Returns the set of sound event keys (for rewriting skill references)."""
    sj = json.loads(src.read(prefix + "assets/minecraft/sounds.json").decode("utf-8-sig"))
    for key, ev in sj.items():
        new = dict(ev)
        lst = []
        for s in ev.get("sounds", []):
            entry = dict(s) if isinstance(s, dict) else {"name": s}
            name = entry["name"]
            if entry.get("type", "file") == "file":
                path = name.split(":", 1)[1] if ":" in name else name
                ogg = f"{prefix}assets/minecraft/sounds/{path}.ogg"
                copy_file(src.read(ogg), os.path.join(NEXO_ASSETS, NS, "sounds", *path.split("/")) + ".ogg")
                entry["name"] = f"{NS}:{path}"
            lst.append(entry if len(entry) > 1 else entry["name"])
        new["sounds"] = lst
        if not key.startswith("mcmodels."):  # purchase watermark entries, no audio
            sounds[key] = new
    return set(sj)


def import_font_glyphs(src, font_json, tex_prefix, gid_prefix):
    """Turn a pack's minecraft:default bitmap providers into Nexo glyphs on
    fresh private-use codepoints. Returns {old char: new char}."""
    font = json.loads(src.read(font_json).decode("utf-8-sig"))
    remap = {}
    for prov in font.get("providers", []):
        if prov.get("type") != "bitmap":
            continue  # e.g. the illusionist's negative_spaces.ttf — not needed
        file = prov["file"]
        path = file.split(":", 1)[1] if ":" in file else file
        png = src.read(f"{tex_prefix}assets/minecraft/textures/{path}")
        stem = os.path.splitext(os.path.basename(path))[0]
        copy_file(png, os.path.join(NEXO_ASSETS, NS, "textures", "bossfx", stem + ".png"))
        for row in prov["chars"]:
            for old in row:
                new = alloc_char()
                remap[old] = new
                glyphs[f"{gid_prefix}_{stem}"] = {
                    "texture": f"{NS}:bossfx/{stem}",
                    "ascent": prov.get("ascent", 8),
                    "height": prov.get("height", 8),
                    "font": "minecraft:default",
                    "char": new,
                }
    return remap


def rewrite_sound_refs(text, keys):
    for k in sorted(keys, key=len, reverse=True):
        text = re.sub(r"((?:\bs|\bsound)=)(?:minecraft:)?" + re.escape(k) + r"(?=[;}\s\"'])",
                      lambda m: m.group(1) + f"{NS}:{k}", text)
    return text


def top_blocks(text):
    """Split a MythicMobs file into (key, block text) for top-level entries."""
    parts = re.split(r"(?m)^(?=[A-Za-z0-9_\-.]+:\s*(?:#.*)?$)", text)
    out = []
    for p in parts:
        m = re.match(r"([A-Za-z0-9_\-.]+):", p)
        out.append((m.group(1) if m else None, p))
    return out


def adjust_mobs(text, health):
    out = []
    for key, block in top_blocks(text):
        if key in health:
            block, n = re.subn(r"(?m)^(\s+Health:\s*)\S+", lambda m: m.group(1) + str(health[key]), block, count=1)
            if not n:
                raise SystemExit(f"{key}: no Health line to set")
            block = re.sub(r"(?m)^(\s+Despawn:\s*)\S+", r"\1false", block)
            # No vanilla loot (Akaza is a zombie -> rotten flesh); rewards come from the dungeon.
            if re.search(r"(?m)^\s+PreventOtherDrops:", block):
                block = re.sub(r"(?m)^(\s+PreventOtherDrops:\s*)\S+", r"\1true", block)
            else:
                m = re.search(r"(?m)^(\s+)Options:\s*$", block)
                if m:
                    block = block[:m.end()] + "\n" + m.group(1) + "  PreventOtherDrops: true" + block[m.end():]
                else:
                    block = block.rstrip("\n") + "\n  Options:\n    PreventOtherDrops: true\n\n"
        out.append(block)
    return "".join(out)


def drop_blocks(text, names):
    return "".join(b for k, b in top_blocks(text) if k not in names)


def main():
    if not os.path.isdir(SRC):
        raise SystemExit(f"missing {SRC}")
    for d in ("meg", "mm", "nexo"):
        shutil.rmtree(os.path.join(OUT, d), ignore_errors=True)

    for pid, (root, health, dupes) in PACKS.items():
        base = os.path.join(SRC, *root.split("/"))
        src = Source(root=base)
        names = src.names()
        sound_keys, char_map = set(), {}

        # --- resource-pack pieces
        if pid in ("earth_spider", "illusionist"):
            rp = os.path.join(base, "Resourcepack")
            s = Source(os.path.join(rp, f"bl_{pid if pid == 'illusionist' else 'earth_spider'}_sounds.zip"))
            sound_keys = import_minecraft_sounds(s)
            b = Source(os.path.join(rp, f"bl_{pid if pid == 'illusionist' else 'earth_spider'}_bossbar.zip"))
            char_map = import_font_glyphs(b, "assets/minecraft/font/default.json", "", pid)
        elif pid == "selenia":
            sound_keys = import_minecraft_sounds(Source(os.path.join(base, "Resoursepack", "hv_selenia.zip")))
        elif pid == "akaza":
            for n in names:
                if n.startswith("Nexo/pack/assets/"):
                    copy_file(src.read(n), os.path.join(NEXO_ASSETS, *n[len("Nexo/pack/assets/"):].split("/")))
            c = alloc_char()
            char_map = {"\U00010148": c}
            glyphs["bossbar-akaza"] = {"texture": "boss-akaza:boss_bar/akaza_bossbar", "ascent": 15,
                                       "height": 225, "font": "minecraft:default", "char": c}
        elif pid == "mage":
            raw = "Raw Resource Pack/"
            for n in names:
                if n.startswith(raw + "assets/") and n.endswith(".ogg"):
                    copy_file(src.read(n), os.path.join(NEXO_ASSETS, *n[len(raw + "assets/"):].split("/")))
                if n.startswith(raw + "assets/") and n.endswith("sounds.json") and "/minecraft/" not in n:
                    copy_file(src.read(n), os.path.join(NEXO_ASSETS, *n[len(raw + "assets/"):].split("/")))
            char_map = import_font_glyphs(src, raw + "assets/minecraft/font/default.json", raw, "mage")

        # --- ModelEngine blueprints (keep the pack's folder layout)
        megs = 0
        for n in names:
            low = n.lower()
            if "modelengine/" not in low or "/.data/" in low or low.endswith("config.yml"):
                continue
            if low.endswith(".bbmodel") or (low.endswith(".png") and "blueprints/" in low):
                rel = n[low.index("modelengine/") + len("modelengine/"):]
                rel = rel[len("blueprints/"):] if rel.lower().startswith("blueprints/") else rel
                copy_file(src.read(n), os.path.join(OUT, "meg", pid, *rel.split("/")))
                megs += low.endswith(".bbmodel")

        # --- MythicMobs configs
        files = 0
        for n in names:
            low = n.lower()
            if "mythicmobs/" not in low or not low.endswith(".yml") or os.path.basename(n) in SKIP_FILES:
                continue
            text = src.read(n).decode("utf-8-sig")
            kind = "Skills" if re.search(r"skill", low) else "Mobs"
            if sound_keys:
                text = rewrite_sound_refs(text, sound_keys)
            for old, new in char_map.items():
                text = text.replace(old, new)
            for old, new in FIXES.get(pid, []):
                text = text.replace(old, new)
            if pid == "akaza":
                text = text.replace("%nexo_bossbar-akaza%", char_map["\U00010148"])
            if kind == "Mobs":
                text = adjust_mobs(text, health)
            elif dupes:
                text = drop_blocks(text, dupes)
            fname = re.sub(r"[^A-Za-z0-9_.-]", "_", os.path.basename(n))
            os.makedirs(os.path.join(MM_OUT, kind), exist_ok=True)
            with open(os.path.join(MM_OUT, kind, f"{pid}__{fname}"), "w", encoding="utf-8") as f:
                f.write(text)
            files += 1
        missing = [m for m in health if not any(
            re.search(r"(?m)^" + re.escape(m) + r":", open(os.path.join(MM_OUT, "Mobs", f), encoding="utf-8").read())
            for f in os.listdir(os.path.join(MM_OUT, "Mobs")) if f.startswith(pid + "__"))]
        if missing:
            raise SystemExit(f"{pid}: boss mob(s) not found: {missing}")
        print(f"[{pid}] {megs} blueprints, {files} MM files, {len(sound_keys)} sounds moved, "
              f"{len(char_map)} glyphs remapped")

    with open(os.path.join(MM_OUT, "Mobs", "glitch__stand_ins.yml"), "w", encoding="utf-8") as f:
        f.write(STAND_INS)
    with open(os.path.join(MM_OUT, "packinfo.yml"), "w", encoding="utf-8") as f:
        f.write("Name: GlitchDungeonBosses\nVersion: 1.0.0\nAuthor: licensed packs (see docs/DUNGEONS.md)\n"
                "Description: Dungeon bosses for The Glitch MythicDungeons\n")
    # Dungeon key icons (licensed key1.zip icon pack, nexo-type textures);
    # the key items themselves are in the repo and reference key1:items/<name>.
    kz = zipfile.ZipFile(os.path.join(REPO, "key1.zip"))
    for n in kz.namelist():
        m = re.match(r"en/nexo-type/plugins/Nexo/pack/assets/key1/(textures/items/(iron|golden|mythic)_key1\.png)$", n)
        if m:
            copy_file(kz.read(n), os.path.join(NEXO_ASSETS, "key1", *m.group(1).split("/")))
    with open(os.path.join(NEXO_ASSETS, NS, "sounds.json"), "w", encoding="utf-8") as f:
        json.dump(sounds, f, indent=1)
    os.makedirs(os.path.dirname(GLYPH_FILE), exist_ok=True)
    with open(GLYPH_FILE, "w", encoding="utf-8") as f:
        f.write("# Generated by scripts/dungeon_bosses.py — boss bossbar/VFX glyphs on U+E9A0..\n")
        for gid, g in glyphs.items():
            f.write(f"\n{gid}:\n")
            f.write(f"  texture: {g['texture']}\n  ascent: {g['ascent']}\n  height: {g['height']}\n")
            f.write(f"  font: {g['font']}\n  char: \"\\u{ord(g['char']):04X}\"\n")
    print(f"glyphs: {len(glyphs)}  glitchbosses sounds: {len(sounds)}")


if __name__ == "__main__":
    sys.exit(main())
