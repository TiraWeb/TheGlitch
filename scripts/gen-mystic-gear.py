#!/usr/bin/env python3
"""Generate the Mystic arsenal tiers and the Bazaar armour sets.

Rewrites (Nexo item configs + shop stock, all in the repo):
  server/plugins/Nexo/items/oraxen_items/fantasy_weapons.yml  stats/lore of the 25 Mystic weapons
  server/plugins/Nexo/items/oraxen_items/glitch_armor.yml     the fixed armour sets
  plugins/GlitchShops/src/main/resources/shops.yml            mystic + gear prices

The weapons keep their pack model and flavour line; everything else comes from
the tables below. Nexo AttributeModifiers REPLACE the item's vanilla stats, so
ATTACK_DAMAGE here is the full bonus over the 1-damage fist and ATTACK_SPEED
is relative to the base 4.0 attacks/second.

Enchant levels stay <= 10: the client only has names up to X, higher levels
show as a raw "enchantment.level.20" key in the tooltip.

Armour ids end in _helmet/_chestplate/..., which makes Nexo give them a
custom nexo:<set> equipment model with no textures (pink/black armour); each
piece therefore sets an explicit equippable asset_id (vanilla netherite/diamond).

Tiers sit above rolled Legendary gear (netherite sword +rolls) and top out
around the Dream Eater chase item (30-45 dmg). Sell = 55% of buy.

Usage: python scripts/gen-mystic-gear.py
"""
import os
import re

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ITEMS = os.path.join(REPO, "server", "plugins", "Nexo", "items", "oraxen_items")
WEAPONS = os.path.join(ITEMS, "fantasy_weapons.yml")
ARMOR = os.path.join(ITEMS, "glitch_armor.yml")
SHOPS = os.path.join(REPO, "plugins", "GlitchShops", "src", "main", "resources", "shops.yml")
SELL_RATIO = 0.55

TIERS = {
    # name, badge colour (MiniMessage gradient), base damage, extras, enchants
    1: ("MYSTIC", "#a855f7:#f472b6", 11, [],
        {"sharpness": 5, "unbreaking": 3}),
    2: ("RELIC", "#38bdf8:#818cf8", 15, [("MAX_HEALTH", 4, "ADD_NUMBER")],
        {"sharpness": 6, "fire_aspect": 2, "looting": 3, "unbreaking": 5}),
    3: ("MYTHIC", "#f59e0b:#ef4444", 20, [("MAX_HEALTH", 8, "ADD_NUMBER"),
                                         ("MOVEMENT_SPEED", 0.10, "ADD_SCALAR")],
        {"sharpness": 8, "fire_aspect": 3, "looting": 4, "unbreaking": 10, "mending": 1}),
    4: ("GODSLAYER", "#fde047:#f43f5e", 25, [("MAX_HEALTH", 16, "ADD_NUMBER"),
                                            ("MOVEMENT_SPEED", 0.20, "ADD_SCALAR"),
                                            ("KNOCKBACK_RESISTANCE", 0.5, "ADD_NUMBER"),
                                            ("ARMOR_TOUGHNESS", 4, "ADD_NUMBER")],
        {"sharpness": 10, "fire_aspect": 4, "looting": 5, "unbreaking": 10, "mending": 1}),
}

# archetype: damage multiplier, attack speed (relative to 4.0), extra attributes, extra enchants
ARCHETYPES = {
    "sword":   (1.0, -2.2, [], {"sweeping_edge": 3}),
    "heavy":   (1.3, -2.9, [("ATTACK_KNOCKBACK", 1.0, "ADD_NUMBER")], {"knockback": 2}),
    "polearm": (1.1, -2.6, [("ENTITY_INTERACTION_RANGE", 1.5, "ADD_NUMBER"),
                            ("SWEEPING_DAMAGE_RATIO", 0.5, "ADD_NUMBER")], {"sweeping_edge": 3}),
    "light":   (0.7, -1.4, [("MOVEMENT_SPEED", 0.10, "ADD_SCALAR")], {}),
}

# weapon id: (tier, archetype, buy price)
ARSENAL = {
    # Tier 4 — Godslayer: the "crazy" chase purchases
    "void_sword":                  (4, "sword", 1_000_000),
    "singularity_hammer_awakened": (4, "heavy", 850_000),
    "duality":                     (4, "sword", 750_000),
    # Tier 3 — Mythic
    "hell_bringer":                (3, "sword", 250_000),
    "ktanazul":                    (3, "sword", 200_000),
    "royal_greatsword_awakened":   (3, "heavy", 180_000),
    "combustion_awakened":         (3, "sword", 150_000),
    "blood_gladius_awakened":      (3, "sword", 150_000),
    # Tier 2 — Relic
    "colossal_sword":              (2, "heavy", 60_000),
    "resistance_greatsword_awakened": (2, "heavy", 60_000),
    "dark_destroyer":              (2, "heavy", 55_000),
    "moonlight":                   (2, "sword", 55_000),
    "death_hand":                  (2, "sword", 50_000),
    "engine_blade":                (2, "sword", 50_000),
    "great_scythe":                (2, "polearm", 45_000),
    "lantern_scythe":              (2, "polearm", 45_000),
    # Tier 1 — Mystic
    "core_hammer":                 (1, "heavy", 20_000),
    "prismarine_greataxe":         (1, "heavy", 20_000),
    "storm_axe":                   (1, "heavy", 20_000),
    "twisted_greatsword":          (1, "heavy", 20_000),
    "violet_splitter":             (1, "heavy", 18_000),
    "jade_glaive":                 (1, "polearm", 18_000),
    "silver_halberd":              (1, "polearm", 18_000),
    "yin":                         (1, "light", 18_000),
    "yang":                        (1, "light", 18_000),
}

# Armour sets: id prefix -> (name, gradient, material prefix, per-piece stats, enchants, prices, flavour)
PIECES = ["helmet", "chestplate", "leggings", "boots"]
SLOT = {"helmet": "HEAD", "chestplate": "CHEST", "leggings": "LEGS", "boots": "FEET"}
ARMOR_SETS = {
    "warden": ("Warden's Bulwark", "#94a3b8:#e2e8f0", "DIAMOND",
               {"helmet": (3, 2), "chestplate": (8, 2), "leggings": (6, 2), "boots": (3, 2)}, [],
               {"protection": 4, "unbreaking": 3},
               {"helmet": 3000, "chestplate": 5000, "leggings": 4000, "boots": 3000},
               "Forged for the ones who hold the line."),
    "voidforged": ("Voidforged", "#38bdf8:#818cf8", "NETHERITE",
                   {"helmet": (4, 3), "chestplate": (9, 3), "leggings": (7, 3), "boots": (4, 3)},
                   [("MAX_HEALTH", 2, "ADD_NUMBER")],
                   {"protection": 5, "unbreaking": 5, "mending": 1},
                   {"helmet": 15000, "chestplate": 25000, "leggings": 20000, "boots": 15000},
                   "Cold metal pulled from between the worlds."),
    "godslayer": ("Godslayer Aegis", "#fde047:#f43f5e", "NETHERITE",
                  {"helmet": (6, 5), "chestplate": (12, 5), "leggings": (10, 5), "boots": (6, 5)},
                  [("MAX_HEALTH", 6, "ADD_NUMBER"), ("MOVEMENT_SPEED", 0.05, "ADD_SCALAR"),
                   ("KNOCKBACK_RESISTANCE", 0.25, "ADD_NUMBER")],
                  {"protection": 8, "thorns": 4, "unbreaking": 10, "mending": 1},
                  {"helmet": 150000, "chestplate": 250000, "leggings": 200000, "boots": 150000},
                  "Worn by whatever is left standing at the end."),
}


def sell(buy):
    return int(round(buy * SELL_RATIO, -2))


def attr_lines(attrs, slot):
    out = ["  AttributeModifiers:"]
    for name, amount, op in attrs:
        out += [f"    - attribute: {name}", f"      amount: {round(amount, 3)}",
                f"      operation: {op}", f"      slot: {slot}"]
    return out


def ench_lines(ench):
    return ["  Enchantments:"] + [f"    {k}: {v}" for k, v in ench.items()]


def weapon_blocks(text):
    blocks = re.split(r"(?m)^(?=[a-z_]+:\s*$)", text)
    header, items = blocks[0], {}
    for b in blocks[1:]:
        items[b.split(":", 1)[0]] = b
    return header, items


def rebuild_weapon(wid, block):
    tier, arch, buy = ARSENAL[wid]
    tname, grad, base, textra, tench = TIERS[tier]
    mult, speed, aextra, aench = ARCHETYPES[arch]
    keep = []
    for key in ("itemname", "material"):
        m = re.search(rf"(?m)^  {key}:.*$", block)
        keep.append(m.group(0))
    pack = re.search(r"(?m)^  Pack:\n(?:    .*\n)+", block).group(0).rstrip("\n")
    flavour = re.findall(r'(?m)^  - "(<dark_gray><italic>.*)"$', block)
    dmg = round(base * mult, 1)
    attrs = [("ATTACK_DAMAGE", dmg, "ADD_NUMBER"), ("ATTACK_SPEED", speed, "ADD_NUMBER")] + aextra + textra
    ench = {**tench, **aench}
    name = re.sub(r"<gradient:[^>]+>", f"<gradient:{grad}>", keep[0])
    lines = [f"{wid}:", name, keep[1], pack]
    lines += attr_lines(attrs, "MAINHAND") + ench_lines(ench)
    lines += ["  lore:", f'  - "<gradient:{grad}>✦ {tname} ✦</gradient> <dark_gray>·</dark_gray> <gray>{arch}</gray>"']
    lines += [f'  - "{flavour[0]}"'] if flavour else []
    lines += ["  - ''", f'  - " <gray>Sell price: <aqua>{sell(buy):,} Shards</aqua></gray>"', ""]
    return "\n".join(lines) + "\n"


def armor_yaml():
    out = ["# The Glitch — Bazaar armour sets (Gear tab, fixed stock). Generated by",
           "# scripts/gen-mystic-gear.py — edit the tables there, not this file.",
           "# Vanilla diamond/netherite models (no custom armour textures yet); the",
           "# stats come from these AttributeModifiers, which replace the vanilla ones.", ""]
    for sid, (name, grad, mat, stats, extra, ench, prices, flavour) in ARMOR_SETS.items():
        for piece in PIECES:
            armor, tough = stats[piece]
            attrs = [("ARMOR", armor, "ADD_NUMBER"), ("ARMOR_TOUGHNESS", tough, "ADD_NUMBER")] + extra
            iid = f"{sid}_{piece}"
            out += [f"{iid}:", f"  itemname: <gradient:{grad}><bold>{name} {piece.capitalize()}</bold></gradient>",
                    f"  material: {mat}_{piece.upper()}"]
            out += attr_lines(attrs, SLOT[piece]) + ench_lines(ench)
            # Explicit vanilla look: without it Nexo assigns a nexo:<set> equipment
            # model (ids end in _helmet/...) that has no textures -> pink/black armour.
            out += ["  Components:", "    equippable:", f"      slot: {SLOT[piece]}",
                    f"      asset_id: minecraft:{mat.lower()}"]
            out += ["  lore:", f'  - "<gradient:{grad}>✦ {name.upper()} ✦</gradient>"',
                    f'  - "<dark_gray><italic>{flavour}</italic></dark_gray>"', "  - ''",
                    f'  - " <gray>Sell price: <aqua>{sell(prices[piece]):,} Shards</aqua></gray>"', ""]
    return "\n".join(out) + "\n"


def shop_section(key, title, icon, entries):
    lines = [f"  {key}:", f'    title: "{title}"', f'    tab-icon: "{icon}"', "    stock:"]
    lines += [f"      {iid}: {{ buy: {buy}, sell: {sell(buy)} }}" for iid, buy in entries]
    return "\n".join(lines) + "\n"


def main():
    text = open(WEAPONS, encoding="utf-8").read()
    header, items = weapon_blocks(text)
    missing = set(ARSENAL) ^ set(items)
    if missing:
        raise SystemExit(f"weapon list mismatch: {sorted(missing)}")
    header = re.sub(r"(?ms)^# The Glitch — Mystic Arsenal.*?(?=^\S|\Z)", "", header)
    new = ["# The Glitch — Mystic Arsenal: 25 weapons from the operator-supplied",
           "# \"Fantasy Weapons\" pack (skulpt: models), sold in the Bazaar Mystic tab.",
           "# Generated by scripts/gen-mystic-gear.py — four tiers (Mystic / Relic /",
           "# Mythic / Godslayer) x four archetypes (sword / heavy / polearm / light).", ""]
    order = sorted(ARSENAL, key=lambda w: (ARSENAL[w][0], ARSENAL[w][2]))
    body = "".join(rebuild_weapon(w, items[w]) for w in order)
    open(WEAPONS, "w", encoding="utf-8", newline="\n").write("\n".join(new) + "\n" + body)
    open(ARMOR, "w", encoding="utf-8", newline="\n").write(armor_yaml())

    shops = open(SHOPS, encoding="utf-8").read()
    mystic = shop_section("mystic", "Mystic", "void_sword", [(w, ARSENAL[w][2]) for w in order])
    gear_entries = [(f"{sid}_{p}", s[6][p]) for sid, s in ARMOR_SETS.items() for p in PIECES]
    gear = shop_section("gear", "Gear", "voidforged_chestplate", gear_entries)
    shops = re.sub(r"(?ms)^  mystic:\n.*?(?=^  \S|\Z)", mystic, shops)
    shops = re.sub(r"(?ms)^  gear:\n.*?(?=^  \S|\Z)", "", shops)
    shops = shops.rstrip("\n") + "\n" + gear
    open(SHOPS, "w", encoding="utf-8", newline="\n").write(shops)

    for w in order:
        t, a, buy = ARSENAL[w]
        dmg = 1 + round(TIERS[t][2] * ARCHETYPES[a][0], 1)
        print(f"  T{t} {a:7} {w:32} dmg {dmg:5} aps {4 + ARCHETYPES[a][1]:.1f}  buy {buy:>9,}")
    print(f"armour: {len(gear_entries)} pieces")


if __name__ == "__main__":
    main()
