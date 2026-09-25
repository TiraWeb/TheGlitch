#!/usr/bin/env python3
"""Generate the MythicDungeons map configs for the 11 boss dungeons.

Inputs (gitignored, see scripts/dungeon_maps.py + dungeon_bosses.py):
  dungeon-private/layout.json      start + arena coordinates per map
  dungeon-private/default-config.yml   MD's maps/default-config.yml (copied
                                       from the live server by setup-dungeons.sh)
Output:
  dungeon-private/md/<id>/config.yml, functions.yml

Each dungeon is boss-arena-only: players spawn a short walk from the arena;
stepping within ARENA_RADIUS of its centre shows the boss title and spawns
the boss once; killing the (final-phase) boss pays the tier reward to every
party member, shows "Cleared" and finishes the dungeon, returning everyone to
the hub after a short delay.

functions.yml uses MD's own ConfigurationSerializable layout ("==" class
keys, @SavedField names) — reverse-engineered from MythicDungeons 2.1.0; see
docs/DUNGEONS.md if a newer MD changes it.
"""
import json
import os
import re
import sys

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PRIV = os.path.join(REPO, "dungeon-private")

# id: (display name, tier, boss to spawn, boss whose death clears it, boss title)
DUNGEONS = {
    "small":   ("Goblin Hollow",       1, "am_goblin_boss", "am_goblin_boss", "Goblin Warlord"),
    "haunted": ("Haunted Crypt",       1, "skeleton_boss",  "skeleton_boss",  "Bone Tyrant"),
    "puzzle":  ("Illusion Vault",      1, "bl_illusionist", "bl_illusionist", "The Illusionist"),
    "desert":  ("Sunken Sands",        1, "bl_earth_spider", "bl_earth_spider", "Earth Spider"),
    "aztec":   ("Temple of Moldar",    2, "hv_moldar_spawner", "hv_moldar",   "Moldar"),
    "crimson": ("Crimson Keep",        2, "Boss-Akaza",     "Boss-Akaza",     "Akaza, Upper Moon"),
    "nether":  ("Ember Depths",        2, "mf_ember_claw",  "mf_ember_claw",  "Ember Claw"),
    # stand-in until the FULL samus2002 archer pack is available (see dungeon_bosses.py)
    "pirate":  ("Wreck of the Tide",   2, "Dungeon_GlitchReaver", "Dungeon_GlitchReaver", "Glitch Reaver"),
    "medium":  ("Moonlit Sanctum",     3, "hv_selenia",     "hv_selenia",     "Selenia"),
    "town":    ("Hollow Town",         3, "Hanashiguro",    "Hanashiguro2",   "The Lovers"),
    "mythic":  ("Mythic Spire",        3, "Mage",           "Mage",           "The Awakened Mage"),
}
TIER_COLOUR = {1: "&a", 2: "&6", 3: "&c"}
# per-player clear reward: shards + one Nexo item
REWARDS = {1: (400, "vault_key"), 2: (900, "void_essence"), 3: (1800, "legendary_relic")}
ARENA_RADIUS = 10.0
EXIT = {"world": "hub", "x": 0.5, "y": -60.0, "z": 0.5, "yaw": 0.0, "pitch": 0.0}

P = "net.playavalon.mythicdungeons"


def q(s):
    return json.dumps(s, ensure_ascii=False)  # JSON strings are valid YAML double-quoted scalars


def loc(x, y, z, indent, world=None):
    pad = " " * indent
    out = [f"{pad}==: org.bukkit.Location"]
    if world:
        out.append(f"{pad}world: {world}")
    out += [f"{pad}x: {float(x)}", f"{pad}y: {float(y)}", f"{pad}z: {float(z)}",
            f"{pad}pitch: 0.0", f"{pad}yaw: 0.0"]
    return "\n".join(out)


def target(indent):
    pad = " " * indent
    return f"{pad}==: {P}.api.parents.FunctionTargetType\n{pad}value: PARTY"


def trigger_block(cls, fields, indent):
    pad = " " * indent
    lines = [f"{pad}==: {P}.dungeons.triggers.{cls}"]
    lines += [f"{pad}{k}: {v}" for k, v in fields.items()]
    lines += [f"{pad}allowRetrigger: false", f"{pad}limitToRoom: false", f"{pad}delayTicks: 0",
              f"{pad}conditions: []"]
    return "\n".join(lines)


def fn_map(cls, fields, at, indent, trig=None):
    """One serialized DungeonFunction as a YAML mapping at `indent`."""
    pad = " " * indent
    lines = [f"{pad}==: {cls}"]
    for k, v in fields.items():
        lines.append(f"{pad}{k}: {v}")
    lines.append(f"{pad}location:\n{loc(*at, indent + 2)}")
    lines.append(f"{pad}targetType:\n{target(indent + 2)}")
    if trig:
        lines.append(f"{pad}trigger:\n{trig}")
    return "\n".join(lines)


def fn(cls, fields, at, indent, trig=None):
    """The same as a YAML list item ("- " at `indent`, body at indent + 2)."""
    body = fn_map(cls, fields, at, indent + 2, trig)
    return " " * indent + "- " + body[indent + 2:]


def multi(children, at, trig):
    body = "\n".join(children)
    return fn(f"{P}.dungeons.functions.meta.FunctionMulti", {"functions": "\n" + body}, at, 0, trig)


def functions_yml(did, layout):
    name, tier, spawn_id, final_id, title = DUNGEONS[did]
    ax, ay, az = layout["arena"]
    arena = (ax, ay, az)
    # MD stores functions keyed by block location — the clear handler sits
    # two blocks off the arena centre so it doesn't replace the fight trigger
    clear_at = (ax + 2, ay, az)
    shards, item = REWARDS[tier]
    col = TIER_COLOUR[tier]
    F = f"{P}.dungeons.functions"
    ci = 4  # child indent inside FunctionMulti.functions

    start_fight = multi([
        fn(f"{F}.FunctionTitle", {"title": q(f"{col}&l{title}"), "subtitle": q("&7Defeat the boss!"),
                                  "fadeIn": 10, "stay": 50, "fadeOut": 15}, arena, ci),
        fn(f"{F}.FunctionPlaySound", {"sound": q("minecraft:entity.wither.spawn"), "soundCategory": q("HOSTILE"),
                                      "volume": 1.0, "pitch": 0.8, "playAtLocation": "false"}, arena, ci),
        fn(f"{F}.FunctionSpawnMythicMob", {"mob": q(spawn_id), "levelString": q("1"), "maxCount": 1,
                                           "delay": 40, "interval": 0, "yaw": 0.0}, arena, ci),
    ], arena, trigger_block("TriggerDistance", {"radius": ARENA_RADIUS, "count": 1, "forEachPlayer": "false"}, 4))

    finish = multi([
        fn(f"{F}.FunctionTitle", {"title": q("&a&lDungeon Cleared"),
                                  "subtitle": q(f"&7+{shards} Shards &8| &7returning to the hub..."),
                                  "fadeIn": 10, "stay": 70, "fadeOut": 20}, arena, ci),
        fn(f"{F}.FunctionPlaySound", {"sound": q("minecraft:ui.toast.challenge_complete"),
                                      "soundCategory": q("MASTER"), "volume": 1.0, "pitch": 1.0,
                                      "playAtLocation": "false"}, arena, ci),
        fn(f"{F}.FunctionCommand", {"command": q(f"eco give %player_name% {shards}"),
                                    "commandType": 1, "forEachPlayer": "true"}, arena, ci),
        fn(f"{F}.FunctionCommand", {"command": q(f"nexo give {item} 1 %player_name%"),
                                    "commandType": 1, "forEachPlayer": "true"}, arena, ci),
        fn(f"{F}.meta.FunctionDelayed", {"delay": 160, "function": "\n" + fn_map(
            f"{F}.FunctionFinishDungeon", {"leave": "true"}, arena, ci + 4)}, arena, ci),
    ], clear_at, trigger_block("TriggerMythicMobDeath", {"mob": q(final_id), "radius": 0.0, "count": 1}, 4))

    return f"Version: 1\nFunctions:\n{start_fight}\n{finish}\n"


def set_key(text, dotted, value):
    """Replace `Key: value` for a dotted path in MD's commented default config."""
    parts = dotted.split(".")
    pos = 0
    for depth, key in enumerate(parts):
        m = re.compile(r"(?m)^" + " " * (2 * depth) + re.escape(key) + r":").search(text, pos)
        if not m:
            raise SystemExit(f"config key not found: {dotted}")
        pos = m.start()
    line_end = text.index("\n", pos)
    indent = " " * (2 * (len(parts) - 1))
    if isinstance(value, dict):
        # block value (Location): replace the key line (+ any nested lines)
        end = line_end
        nxt = re.compile(r"(?m)^ {0,%d}\S" % (2 * (len(parts) - 1))).search(text, line_end + 1)
        end = nxt.start() - 1 if nxt else len(text)
        body = "\n".join(f"{indent}  {k}: {v}" for k, v in value.items())
        return text[:pos] + f"{indent}{parts[-1]}:\n{body}" + text[end:]
    return text[:pos] + f"{indent}{parts[-1]}: {value}" + text[line_end:]


def config_yml(did, layout, template):
    name, tier, *_ = DUNGEONS[did]
    sx, sy, sz = layout["spawn"]
    ax, _, az = layout["arena"]
    import math
    yaw = round(math.degrees(math.atan2(-(ax - sx), (az - sz))), 1)  # face the arena
    t = template
    t = set_key(t, "General.DisplayName", q(f"{TIER_COLOUR[tier]}{name}"))
    t = set_key(t, "General.ShowTitleOnStart", "true")
    t = set_key(t, "General.ExitLocation", {"==": "org.bukkit.Location", **EXIT})
    t = set_key(t, "General.AlwaysUseExit", "true")
    t = set_key(t, "General.PlayerLives", "3")
    t = set_key(t, "General.KeepInventoryOnEnter", "true")
    t = set_key(t, "General.MaxInstances", "2")
    t = set_key(t, "General.TimeLimit", "20")
    t = set_key(t, "Requirements.MaxPartySize", "4")
    t = set_key(t, "Rules.AllowDropItems", "false")
    # StartLocation isn't in the default template: add it under General
    start = {"==": "org.bukkit.Location", "x": sx, "y": float(sy), "z": sz, "yaw": yaw, "pitch": 0.0}
    body = "\n".join(f"    {k}: {v}" for k, v in start.items())
    t = t.replace("\nGeneral:\n", f"\nGeneral:\n  StartLocation:\n{body}\n", 1) if "\nGeneral:\n" in t \
        else t.replace("General:\n", f"General:\n  StartLocation:\n{body}\n", 1)
    return t


# Dungeon worlds: keep inventory on death (lives are the penalty), only the
# boss spawns, nothing grows/burns, fixed time. MD reads one key per GameRule;
# both the legacy camelCase and the 26.x snake_case names are written.
GAMERULES = {
    "keepInventory": "true", "keep_inventory": "true",
    "doMobSpawning": "false", "spawn_mobs": "false",
    "mobGriefing": "false", "mob_griefing": "false",
    "doDaylightCycle": "false", "advance_time": "false",
    "doWeatherCycle": "false", "advance_weather": "false",
    "doFireTick": "false", "fire_spread_radius_around_player": "0",
    "randomTickSpeed": "0", "random_tick_speed": "0",
}


def main(only=None):
    layout = json.load(open(os.path.join(PRIV, "layout.json")))
    template = open(os.path.join(PRIV, "default-config.yml"), encoding="utf-8").read()
    for did in DUNGEONS:
        if only and did not in only:
            continue
        out = os.path.join(PRIV, "md", did)
        os.makedirs(out, exist_ok=True)
        with open(os.path.join(out, "functions.yml"), "w", encoding="utf-8") as f:
            f.write(functions_yml(did, layout[did]))
        with open(os.path.join(out, "config.yml"), "w", encoding="utf-8") as f:
            f.write(config_yml(did, layout[did], template))
        with open(os.path.join(out, "gamerules.yml"), "w", encoding="utf-8") as f:
            f.write("Version: 3\n" + "".join(f"{k}: {v}\n" for k, v in GAMERULES.items()))
        print(f"[{did}] {DUNGEONS[did][0]} (T{DUNGEONS[did][1]}) start={layout[did]['spawn']} "
              f"arena={layout[did]['arena']}")


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:] or None))
