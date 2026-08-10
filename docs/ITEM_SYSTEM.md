# The Glitch — Item System (Arcane Ruins)

> Design for the unique item/economy/loot loop. Replaces the "techy" naming and flat loot
> tables in `GAME_DESIGN.md` (§2, §3, §4) with the **Arcane Ruins** identity — a magical rift
> leaking chaos, NOT a computer virus.
>
> Differentiators researched against **Wynncraft** (persistent MMO, level-gated gear, elemental
> damage, profession crafting) and **Hypixel Ravengard** (Dark-and-Darker clone; shrinking death
> wall, 3-man parties, weapons with only 2 stats — top complaint: *no loot depth*).

> **Implementation status:** This document is the intended Arcane Ruins design.
> Oraxen item definitions/assets, GlitchItems V1 source, and parts of GlitchShops
> exist. Rift drops, Identifier NPC behavior, mob tags, world population,
> crafting, and death handling are not complete. See [`docs/STATUS.md`](STATUS.md).

---

## 1. Design Pillars

| # | Pillar | How it's unique |
|---|---|---|
| 1 | **Unstable Rifts** | Loot drops unrevealed. You extract crystals and pay to reveal them — a lottery in the hub, not on the floor. (Ravengard/Wynncraft: loot drops finished.) |
| 2 | **Resonance** | 5 arcane frequencies instead of elemental damage. Every weapon/armor rolls one; mobs are tagged with one. Counter-play builds identity without a spreadsheet. |
| 3 | **Residual Glitch** | Greed is *self-imposed*, not a shrinking wall. Stacks scale loot luck AND mob aggression; extraction multiplies saved value. |
| 4 | **Crafting by Resonance** | Combine rune materials to craft a *targeted* item of a chosen Resonance. Not Wynncraft's RNG professions. |
| 5 | **No item levels** | Nothing is level-gated. Gear is defined by rarity + Resonance, not character level. First run gear can stay relevant with good rolls. |

---

## 2. Rarity System

Simple, readable tiers (Common → Legendary, like most games — the server should be
understandable, not a thesaurus):

| Tier | Name | Color | Shard Value | Note |
|---|---|---|---|---|
| 1 | **Common** | White | 1-5 | Fragmented → Common |
| 2 | **Uncommon** | Green | 10-25 | Stabilized → Uncommon |
| 3 | **Rare** | Blue | 50-100 | Resonant → Rare |
| 4 | **Epic** | Purple | 200-500 | Empowered → Epic |
| 5 | **Legendary** | Gold | 1000-2500 | Primordial → Legendary |

Gear base stays the same (wood→stone→iron→diamond→netherite) — only names change.

### Stat roll ranges per rarity (identify results)

| Stat | Common | Uncommon | Rare | Epic | Legendary |
|---|---|---|---|---|---|
| Damage bonus | +0–5% | +5–10% | +10–15% | +15–20% | +20–30% |
| Armor bonus | +0–2 | +2–4 | +4–6 | +6–8 | +8–10 |
| Speed | +0–2% | +2–4% | +4–6% | +6–8% | +8–12% |
| Max HP | +0 | +1–2 | +2–3 | +3–5 | +5–8 |
| Resonance boost | — | +1 | +1 | +2 | +2 |
| Roll stars | 0–1 | 0–2 | 0–3 | 1–4 | 2–5 |

Star system (inspired by Wynncraft IDs, simplified): each stat roll gets 0–5 stars; stars are
shown in lore. A max-roll Legendary ("godroll") is the best item in the game — the chase
item for the endgame economy.

### Gear archetypes & attributes

**Weapons — 3 archetypes** (arcane flavor, no guns):

| Archetype | Vanilla base | Role |
|---|---|---|
| **Blade** | sword | balanced, standard DPS |
| **Greatblade** | axe | slow, heavy, knockback |
| **Arcane Staff** | wand/trident | ranged arcane bolt |

Base material scales with rarity (wood/stone/iron/diamond/netherite). **Attributes** (special
effects — the "boredom killer" vs Ravengard's 2-stat weapons):

| Rarity | Weapon attributes |
|---|---|
| Common | none — raw stat rolls only |
| Uncommon | 1 small stat bump |
| Rare | 1 attribute (e.g. 3% lifesteal, fire aspect I, +1 reach) |
| Epic | 1 strong attribute (6% lifesteal, fire aspect II, resonance boost +2) |
| Legendary | 2 attributes + max roll stars |

**Armor — 4 pieces** (helmet, chestplate, leggings, boots). Kept simple on purpose:
**base stats scale with rarity (defense/HP per piece) + exactly ONE attribute from
Rare up.** No multi-slot complexity; leggings+boots are also the pieces kept on
death (see §12).

**Sources:** Unstable Rifts (random rolls — the identify loop), Workbench crafting
(targeted Resonance with Aether Shards, §7), merchants (fixed base + small roll variance,
super-rare 0.01% max-roll variant, §11), elites/bosses (guaranteed rare+, rare-only
tables so bosses never drop "buns" loot).

---

## 3. Materials (renamed)

| Current | New | Source | Use |
|---|---|---|---|
| Glitch Dust | **Rune Fragment** | T1-T3 mobs (common) | Crafting base |
| Circuit Board | **Aether Shard** | T2+ mobs, chests | Resonance crafting |
| Data Shard | **Rift Crystal** | T2+ mobs, chests | Identify fuel, key crafting |
| Corrupted Core | **Void Essence** | Elites/bosses, Rift Vaults | Top crafting, keys |
| Mythic Fragment | **Legendary Relic** | Bosses (10%) | Legendary-only recipes |

### Keys

Keys are named after what they open — no lore required:

| Current | New | Uses |
|---|---|---|
| Rusty Key | **Cache Key** | Loot Caches (mid zones) |
| Vault Key | **Vault Key** | Vaults (hard zones) |
| Master Key | **Rift Key** | Rift Vaults (boss areas) |

---

## 4. Unstable Rifts (identify loop)

### Drop flow
1. Mobs/chests drop an **Unstable Rift** — an amethyst/end-crystal item tinted by rarity.
2. Rifts are carried out and stored in the stash like any item.
3. At the hub, the **Identifier NPC** stabilizes a rift for a shard fee:

| Rarity | Identify fee (Shards) |
|---|---|
| Common | 5 |
| Uncommon | 20 |
| Rare | 75 |
| Epic | 250 |
| Legendary | 800 |

4. Reveal = rift transforms into a real gear item with **random stat rolls** (see §2 ranges).
   - *Variance:* roll stars are hidden until reveal — you might reveal a godroll or a brick.

### Design intent
- Extracting rifts feels like carrying treasure you don't fully understand — pure arcane.
- Identify fee is a **shard sink** that powers the economy without deleting loot.
- Chests and mobs share ONE drop type, so loot tables stay simple; the RNG lives at reveal.

---

## 5. Resonance System

Five frequencies. Each **weapon** rolls one Resonance (+damage to matching mobs).
Each **armor piece** rolls one Resonance (+defense against matching mobs).

| Resonance | Color | Theme | Mobs (proposed) | Weapon effect | Armor effect |
|---|---|---|---|---|---|
| **Aegis** | Amber/gold | shielding | Brute, Warden | +25% dmg vs Aegis mobs | +15% dmg reduction from Aegis mobs |
| **Veil** | Deep blue | shadow/mobility | Wisp, Phantom | +25% dmg vs Veil mobs | +10% movement speed |
| **Bloom** | Emerald | life | Stalker | +25% dmg vs Bloom mobs | +1 HP regen / 4s out of combat |
| **Ward** | Crimson | raw force | Sentinel, Sniper | +25% dmg vs Ward mobs | +1 armor tier vs Ward mobs |
| **Hollow** | Violet | void | Crawler, Core, King | +25% dmg vs Hollow mobs | +15% dmg reduction from Hollow mobs |

### Mobs table (extend GAME_DESIGN.md §2 — add a `Resonance:` column)

| Mob | Resonance |
|---|---|
| Glitch Wisp | Veil |
| Corrupted Crawler | Hollow |
| Glitch Stalker | Bloom |
| Glitch Brute | Aegis |
| Glitch Phantom | Veil |
| Glitch Sentinel | Ward |
| Glitch Sniper | Ward |
| Glitch Warden | Aegis |
| The Glitch King | Hollow |
| The Corrupted Core | Hollow |

### Why this beats Wynncraft elements
- 5 readable colors vs 5 elemental damage types that all just say "+X elemental damage".
- Mobs are tagged by *zone identity*, so loadouts become knowledge ("south wing is Bloom-heavy → bring a Hollow weapon").
- No resistance/weakness matrix to memorize — one stat, one counter.

---

## 6. Residual Glitch (greed system)

While inside `glitch_red` (and optionally deep PvE dungeon floors):

| Stack | Effect gained per stack (max 8) |
|---|---|
| Loot luck | +5% rare-roll chance / +1 chest tier upgrade chance |
| Aggro | mobs detect you from +2 blocks, prefer targeting you |
| Damage taken | +5% incoming damage |
| Elite events | at stack 5+, elite mobs begin hunting you |

**Timer:** +1 stack every **5 minutes**. **Clears:** on extraction or death.

**Extraction payout:** saved loot value × (1 + 0.10 × stacks). 8 stacks = 1.8× value.

**Design intent:** glitch_red is a huge map (2k × 2k) — the game is about **searching it**
(containers, POIs, mobs, rifts), not standing still. The 5-minute timer means a full
greed-maxi run takes 40 minutes of actual play, and the staying bonus is deliberately
small: loot luck +5% per stack and a modest payout multiplier. Staying in one spot should
never beat *looting more ground* — Residual Glitch only tips the scale for players who
choose to linger near risky POIs.

### Flavor
"Residual Glitch" is residual magical chaos clinging to you the longer you linger in the rift —
it draws more of the anomaly (better loot) while eroding your grip on reality (more damage).

---

## 7. Crafting (replaces GAME_DESIGN.md §4 recipes)

| Recipe | Materials | Output |
|---|---|---|
| Base Weapon (Uncommon) | 3 Rune Fragment + 2 Rift Crystal | Uncommon weapon (random Resonance) |
| Targeted Resonance Weapon | base + 2 Aether Shard of chosen Resonance | Weapon locked to chosen Resonance |
| Rift Reveal Pack | 5 Rift Crystal | Free reveal of 1 Uncommon rift |
| Vault Key | 3 Rift Crystal + 1 Void Essence | Vault Key |
| Rift Key | 3 Void Essence + 1 Legendary Relic | Rift Key |
| Void Infusion | 2 Void Essence + 1 Legendary Relic | +1 Resonance boost on an Epic item |

Crafting requires the hideout **Workbench** (as designed in GAME_DESIGN.md §4) — recipes stay
table-based, only inputs/outputs change.

---

## 8. Class ability renames (GlitchClasses cosmetics only — no logic change)

| Current | New (arcane) |
|---|---|
| Shield Wall | **Aegis Wall** |
| Turret Deploy | **Sentinel Construct** |
| EMP Grenade | **Null Grenade** |
| Revive Beacon | **Resonance Beacon** |
| Overclock (passive) | **Resonance Surge** |
| Overload (ultimate) | **Cataclysm** |

Class names themselves stay (Vanguard/Warden/Specter/Operator). Operator reads fine as an
arcane-engineer archetype once abilities are renamed.

---

## 9. Container renames (GAME_DESIGN.md §3 table)

| Current | New | Contents unchanged |
|---|---|---|
| Scrap Pile | **Debris Pile** | everywhere, no key |
| Supply Crate | **Loot Cache** | mid-tier, needs Cache Key |
| Locked Vault | **Vault** | hard areas, needs Vault Key |
| Core Cache | **Rift Vault** | boss areas, needs Rift Key |

---

## 10. Dependency / order of implementation

**Plugin decision (made):** Oraxen for item bases + auto-generated resource pack; small custom
plugin (`GlitchItems`?) for Rift identify / Resonance / Residual Glitch logic.

**Oraxen install path:** jar is ~$20 on marketplaces, but source is open (license permits personal
use) → built from source on the box via `sudo ./setup-oraxen.sh` (clones
`github.com/oraxen/oraxen`, pins tag v1.218.0, `./gradlew build`, deploys to `server/plugins/Oraxen.jar`).
NOT in `bootstrap.sh` on purpose, and the built jar must never be committed to this repo (license
forbids redistribution).

Rough order:

1. **Item base plugin + resource pack** — ✅ Oraxen built from source + deployed (setup-oraxen.sh)
2. **Material + key + consumable items** — ✅ 18 items as Oraxen configs (server/plugins/Oraxen/): 5 materials, 4 keys, 5 Unstable Rifts, 4 alchemy — deploy via setup-oraxen-items.sh
3. **Source V1:** rarity tiers + stat-roll engine, 3 weapon archetypes, 4 armor pieces, attributes, `/identify`, Resonance math, and Residual Glitch source exist. Build and runtime testing are pending.
4. Rift drops (mob loot tables emit rifts) + Identifier NPC flow — not complete.
5. Resonance tags (MythicMobs metadata) + complete gear integration — DONE in repo (2026-08-03, ten mobs); live test pending.
6. Residual Glitch timer/effects + extraction multiplier — source and payout hook exist; consumers DONE (2026-08-06 → 2026-08-10): identify loot luck (star-luck per roll, rarity surge per stack), elite hunt at 5+ stacks (console `mm spawn`, configurable), container loot luck (per-roll rarity surge + surge drop). Aggro-scaling consumer still open.
7. World population (spawners, chests, regen) — DONE in repo (2026-08-10): glitch_red SpawnAreas seeded + GlitchItems container system (Debris/Cache/Vault/Rift Vault, key consumption, regen, loot luck). In-world marking/placement is operator work.
8. Crafting recipes via Workbench — not complete.
9. Rename pass across runtime configs and menus — not complete.
10. Death rules (§12: keep leggings+boots in glitch_red) — DONE in repo (2026-08-06): GlitchDeathRules plugin (mercy keep + entry invulnerability). Live build/test pending.

---

## 11. Merchant NPCs & pricing (GlitchShops)

Every custom item can be **sold to hub merchant NPCs** for **Glitch Shards** (Coins/Vault).
Players later spend shards at the respective NPCs to **buy** stock (materials, keys, potions,
and — later — armour/weapon vendors).

**Rules:**
1. **Sell price < buy price** for every item.
2. **Only the sell price appears on the item** (last lore line: `Sell price: N Shards`).
   Buy prices are shown **exclusively in the merchant GUI** — never on the item.
3. Lore is cosmetic display only. The **source of truth is the GlitchShops config**
   (keyed by Oraxen item id), so lore cannot be forged to change what the NPC pays.
4. All prices are whole Shards (no decimals — Coins is whole-number).

### Price table (sell shown in lore / buy at NPC)

| Item | Sell | Buy | Basis |
|---|---|---|---|
| Rune Fragment | 2 | 5 | T1 material, common |
| Aether Shard | 10 | 20 | T2 material |
| Rift Crystal | 20 | 40 | T2+ material, identify fuel |
| Void Essence | 100 | 200 | elite material |
| Legendary Relic | 800 | 1500 | boss material |
| Cache Key | 15 | 30 | Loot Caches |
| Vault Key | 60 | 120 | craft: 3 Rift Crystal + 1 Void Essence |
| Rift Key | 400 | 800 | craft: 3 Void Essence + 1 Legendary Relic |
| Unstable Rift (Common) | 3 | 10 | identify fee 5 |
| Unstable Rift (Uncommon) | 12 | 40 | identify fee 20 |
| Unstable Rift (Rare) | 45 | 150 | identify fee 75 |
| Unstable Rift (Epic) | 150 | 500 | identify fee 250 |
| Unstable Rift (Legendary) | 500 | 1500 | identify fee 800 |
| Fast Extract Key | 40 | 75 | GAME_DESIGN §8 cost 75 |
| Healing Potion | 12 | 20 | craft: 5 Rune Fragment + 1 Rift Crystal |
| Corrupted Heal | 150 | 250 | rare consumable |
| Rift Reveal Pack | 75 | 150 | craft: 5 Rift Crystal |
| Void Infusion | 600 | 1000 | craft: 2 Void Essence + 1 Legendary Relic |

Rift sell price ≈ 60% of the identify fee (selling skips the gamble). Buy price ≈ sell × 1.5–3
(greater for low-tier, keeps vendor margins meaningful).

### Lore format

Every custom item ends with the same line:

```
- '<gray>Sell price: <aqua>N Shards</aqua></gray>'
```

No other price info ever goes on the item.

### Shop layout (hub)

| NPC | Sells | Buys |
|---|---|---|
| **Vendor — Materials** | materials | all custom items |
| **Vendor — Keys** | keys | all custom items |
| **Vendor — Alchemy** | potions | all custom items |
| **Vendor — Rifts** | Unstable Rifts | all custom items |
| **Armourer / Weaponsmith** | gear (§2 archetypes) | gear at vendor value |

Every merchant buys *any* custom item at its config sell price (one shared "Sell" tab),
so nothing ever becomes unsellable clutter.

### Vendor gear stock (Armourer / Weaponsmith)

- **Fixed base price** per item (from the rarity shard value) + **small random variance
  on the stat rolls** each restock — buying gear is a mini-gamble, selling stays stable.
- **Super-rare variant:** every weapon has a 0.01% chance to appear in stock with
  **maximum rolls** (the "vendor godroll") — a chase item for players who don't want to
  rely on rift RNG alone.

---

## 12. Death & extraction rules

| Zone | On death |
|---|---|
| **hub** | Nothing lost (safe zone). |
| **glitch_pve** | Keep-inventory: run is lost, items kept. Training floor. |
| **glitch_red** | Full loot with one mercy rule: **player keeps leggings + boots**. Helmet, chestplate, weapons, inventory, and shards drop. |

Why the mercy rule: full-loss is the danger, but a corpse can always be re-geared "from
the legs up" — players keep a defensive floor (2 armor slots + the pieces that hold the
simplest stats) while still feeling the sting of losing their chestpiece, helmet, and
weapon. Keep-inventory PvE stays as the new-player training floor.

**Extraction timing:** Repository VelKoth configuration sets Standard extract to
**30s**. The live timer is stored in generated `arenas.yml` and must be verified.
Fast = 15s and Silent = 10s remain design-only until separate arenas and key
consumption are implemented. See GAME_DESIGN §7.
