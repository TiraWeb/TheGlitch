#!/usr/bin/env python3
"""Generate the Arc Raiders-style loot line (docs/ITEM_SYSTEM.md §14).

One table here drives:
  server/plugins/Nexo/items/oraxen_items/arc_loot.yml      Nexo items (vanilla looks)
  plugins/GlitchShops/src/main/resources/shops.yml         materials tab buys + sell-only "loot" section
  plugins/GlitchItems/src/main/resources/config.yml        gadgets.inert-items list

Salvage feeds the Workbench, recyclables break down at the Recycler, trinkets
only sell, blueprints unlock Workbench recipes, gadgets/heals are used with
right-click (GlitchItems GadgetListener), the Secure Pouch keeps hotbar slot 9
on a Red Zone death (GlitchDeathRules).

Usage: python scripts/gen-arc-loot.py
"""
import os
import re

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ITEMS = os.path.join(REPO, "server", "plugins", "Nexo", "items", "oraxen_items", "arc_loot.yml")
SHOPS = os.path.join(REPO, "plugins", "GlitchShops", "src", "main", "resources", "shops.yml")
ITEMS_CFG = os.path.join(REPO, "plugins", "GlitchItems", "src", "main", "resources", "config.yml")

RARITY = {"common": "white", "uncommon": "green", "rare": "blue", "epic": "dark_purple", "legendary": "gold"}

# id: (name, material, rarity, sell, buy (0 = not sold in the Bazaar), lore lines)
SALVAGE = {
    "scrap_plating": ("Scrap Plating", "IRON_NUGGET", "common", 3, 8, ["Bent hull plating from the old world."]),
    "frayed_wiring": ("Frayed Wiring", "COPPER_NUGGET", "common", 4, 10, ["Still carries a faint charge."]),
    "polymer_goo": ("Polymer Goo", "SLIME_BALL", "common", 4, 10, ["Sticky, flexible, useful."]),
    "coolant_cell": ("Coolant Cell", "PRISMARINE_CRYSTALS", "common", 6, 15, ["Cold to the touch."]),
    "glitch_cloth": ("Glitch Cloth", "PURPLE_WOOL", "common", 3, 8, ["Fabric that flickers at the edges."]),
    "circuit_core": ("Circuit Core", "ECHO_SHARD", "uncommon", 30, 0, ["Refined from wiring and plating."]),
    "stabilizer_coil": ("Stabilizer Coil", "BREEZE_ROD", "uncommon", 45, 0, ["Keeps rift energy from wandering."]),
    "rift_capacitor": ("Rift Capacitor", "HEART_OF_THE_SEA", "rare", 250, 0, ["Stores a sliver of the Glitch."]),
}
SALVAGE_NOTE = "Crafting material (Workbench)."

RECYCLABLES = {
    "rusted_gear": ("Rusted Gear", "RAW_IRON", "common", 6),
    "cracked_screen": ("Cracked Screen", "BLACK_STAINED_GLASS_PANE", "common", 10),
    "dead_battery": ("Dead Battery", "COPPER_BULB", "common", 12),
    "shattered_lens": ("Shattered Lens", "TINTED_GLASS", "uncommon", 15),
    "corrupted_drive": ("Corrupted Drive", "OBSERVER", "uncommon", 25),
    "broken_drone": ("Broken Drone", "HEAVY_CORE", "rare", 40),
}

TRINKETS = {
    "pixel_duck": ("Pixel Duck", "YELLOW_DYE", "common", 60, "Squeaks in 8-bit."),
    "lost_floppy": ("Lost Floppy", "MUSIC_DISC_CAT", "common", 150, "Label reads: DO NOT OPEN."),
    "static_radio": ("Static Radio", "JUKEBOX", "uncommon", 250, "Plays a station that no longer exists."),
    "frozen_clock": ("Frozen Clock", "CLOCK", "uncommon", 400, "Stuck at the minute the Glitch began."),
    "glitched_snow_globe": ("Glitched Snow Globe", "LIGHT_BLUE_STAINED_GLASS", "rare", 700, "The snow falls upward."),
    "void_music_box": ("Void Music Box", "NOTE_BLOCK", "rare", 1200, "Its tune makes the rift hum back."),
    "cursed_crown": ("Cursed Crown", "GOLDEN_HELMET", "epic", 2500, "Heavy with someone else's ambition."),
    "rift_idol": ("Rift Idol", "END_CRYSTAL", "legendary", 5000, "Collectors will pay anything for it."),
}

# Right-click tools (GlitchItems GadgetListener). The Workbench recipe id matches the item id.
GADGETS = {
    "frag_grenade": ("Frag Grenade", "FIRE_CHARGE", "uncommon", 40,
                     ["Throw: explodes on impact for heavy", "damage to everything nearby."]),
    "smoke_grenade": ("Smoke Grenade", "FIREWORK_STAR", "uncommon", 30,
                      ["Throw: a thick cloud for 8s that", "blinds players and makes mobs lose you."]),
    "lure_beacon": ("Lure Beacon", "BELL", "uncommon", 35,
                    ["Throw: nearby mobs chase the", "beacon instead of you for 8s."]),
    "snap_hook": ("Snap Hook", "LEAD", "rare", 50,
                  ["Aim at a block within 24m and", "right-click to grapple to it."]),
    "barricade_kit": ("Barricade Kit", "IRON_BARS", "rare", 60,
                      ["Right-click a block: raises a 3x2", "wall in front of you for 20s."]),
    "pulse_mine": ("Pulse Mine", "HEAVY_WEIGHTED_PRESSURE_PLATE", "rare", 60,
                   ["Right-click a block to arm. Stuns and", "reveals the first enemy that steps near."]),
    "bandage": ("Bandage", "WHITE_CARPET", "common", 8,
                ["Right-click: heals 3 hearts over 3s."]),
    "adrenaline_shot": ("Adrenaline Shot", "END_ROD", "uncommon", 40,
                        ["Right-click: Speed III + Jump for 6s,", "shakes off slowness."]),
    "secure_pouch": ("Secure Pouch", "RABBIT_HIDE", "epic", 1500,
                     ["Carry it: on a Red Zone death you keep", "this pouch and your hotbar slot 9."]),
}
GADGET_BUY = {"bandage": 25}  # the rest are Workbench-only (blueprints)

# Workbench recipes that need a blueprint: recipe id -> (display, rarity, sell)
BLUEPRINTS = {
    "adrenaline_shot": ("Adrenaline Shot", "common", 200),
    "frag_grenade": ("Frag Grenade", "common", 200),
    "smoke_grenade": ("Smoke Grenade", "common", 200),
    "lure_beacon": ("Lure Beacon", "common", 200),
    "targeted_weapon_aegis": ("Aegis Blade", "common", 250),
    "targeted_weapon_veil": ("Veil Blade", "common", 250),
    "targeted_weapon_bloom": ("Bloom Blade", "common", 250),
    "targeted_weapon_ward": ("Ward Blade", "common", 250),
    "targeted_weapon_hollow": ("Hollow Blade", "common", 250),
    "snap_hook": ("Snap Hook", "uncommon", 450),
    "barricade_kit": ("Barricade Kit", "uncommon", 450),
    "pulse_mine": ("Pulse Mine", "uncommon", 450),
    "rift_key": ("Rift Key", "uncommon", 600),
    "void_infusion": ("Void Infusion", "rare", 1000),
    "secure_pouch": ("Secure Pouch", "rare", 1200),
    "dream_eater": ("The Glitch That Dreams", "legendary", 4000),
}


def item(iid, name, material, rarity, lore, sell, texture=None):
    c = RARITY[rarity]
    out = [f"{iid}:", "  itemname: '<" + c + ">" + name.replace("'", "''") + "</" + c + ">'", f"  material: {material}",
           "  Pack:", "    parent_model: item/generated", f"    texture: {texture or iid}", "  lore:"]
    out += ["  - '<gray>" + line.replace("'", "''") + "</gray>'" for line in lore]
    out.append(f"  - '<dark_gray>{rarity.capitalize()}</dark_gray>'")
    out.append(f"  - ' <gray>Sell price: <aqua>{sell} Shards</aqua></gray>'")
    return "\n".join(out) + "\n"


def items_yaml():
    parts = ["# The Glitch — Arc Raiders-style loot line (salvage, recyclables, trinkets,",
             "# blueprints, gadgets). Generated by scripts/gen-arc-loot.py — edit the table",
             "# there, not this file. Textures: scripts/gen-arc-loot-textures.py.", ""]
    parts.append("# --- Salvage + refined parts ---\n")
    for iid, (n, m, r, s, _b, lore) in SALVAGE.items():
        parts.append(item(iid, n, m, r, lore + [SALVAGE_NOTE], s))
    parts.append("# --- Recyclables: break down at the Recycler (/recycle) ---\n")
    for iid, (n, m, r, s) in RECYCLABLES.items():
        parts.append(item(iid, n, m, r, ["Junk. Recycle it at the hideout", "(/recycle) for salvage."], s))
    parts.append("# --- Trinkets: sell-only valuables ---\n")
    for iid, (n, m, r, s, flavour) in TRINKETS.items():
        parts.append(item(iid, n, m, r, [flavour, "Valuable — sell it at the Bazaar."], s))
    parts.append("# --- Gadgets, heals, Secure Pouch ---\n")
    for iid, (n, m, r, s, lore) in GADGETS.items():
        parts.append(item(iid, n, m, r, lore, s))
    parts.append("# --- Blueprints: right-click to learn the Workbench recipe ---\n")
    for rid, (n, r, s) in BLUEPRINTS.items():
        parts.append(item(f"blueprint_{rid}", f"Blueprint: {n}", "GLOBE_BANNER_PATTERN", r,
                          ["Right-click to learn this recipe", "at the Workbench permanently."], s,
                          texture=f"blueprint_{r}"))
    return "\n".join(parts)


def all_sell():
    rows = [(i, v[3], v[4]) for i, v in SALVAGE.items()]
    rows += [(i, v[3], 0) for i, v in RECYCLABLES.items()]
    rows += [(i, v[3], 0) for i, v in TRINKETS.items()]
    rows += [(i, v[3], GADGET_BUY.get(i, 0)) for i, v in GADGETS.items()]
    rows += [(f"blueprint_{i}", v[2], 0) for i, v in BLUEPRINTS.items()]
    return rows


def main():
    open(ITEMS, "w", encoding="utf-8", newline="\n").write(items_yaml())

    shops = open(SHOPS, encoding="utf-8").read()
    # Buyable salvage joins the Materials tab; bandages join Alchemy.
    for iid, (_n, _m, _r, sell, buy, _l) in SALVAGE.items():
        if buy and f"      {iid}:" not in shops:
            shops = re.sub(r"(?m)^(      legendary_relic: .*\n)", rf"\1      {iid}: {{ buy: {buy}, sell: {sell} }}\n", shops)
    for iid, buy in GADGET_BUY.items():
        if f"      {iid}:" not in shops:
            shops = re.sub(r"(?m)^(      void_infusion: .*\n)",
                           rf"\1      {iid}: {{ buy: {buy}, sell: {GADGETS[iid][3]} }}\n", shops)
    # Everything new is sellable through the hidden "loot" section (not in tab-order).
    loot = ["  loot:", '    title: "Loot"', '    tab-icon: "rift_idol"',
            "    # Sell-only (buy 0 = never shown for sale). Generated by scripts/gen-arc-loot.py.",
            "    stock:"]
    loot += [f"      {i}: {{ buy: 0, sell: {s} }}" for i, s, b in all_sell() if not b]
    shops = re.sub(r"(?ms)^  loot:\n.*?(?=^  \S|\Z)", "", shops)
    shops = shops.rstrip("\n") + "\n" + "\n".join(loot) + "\n"
    open(SHOPS, "w", encoding="utf-8", newline="\n").write(shops)

    cfg = open(ITEMS_CFG, encoding="utf-8").read()
    inert = list(SALVAGE) + list(RECYCLABLES) + list(TRINKETS)
    line = "  inert-items: [" + ", ".join(inert) + "]"
    cfg, n = re.subn(r"(?m)^  inert-items: \[.*\]$", line, cfg)
    if n != 1:
        raise SystemExit("gadgets.inert-items line not found in GlitchItems config.yml")
    open(ITEMS_CFG, "w", encoding="utf-8", newline="\n").write(cfg)

    print(f"items: {len(SALVAGE)} salvage, {len(RECYCLABLES)} recyclables, {len(TRINKETS)} trinkets, "
          f"{len(GADGETS)} gadgets/heals, {len(BLUEPRINTS)} blueprints")


if __name__ == "__main__":
    main()
