# The Glitch — Item System (Arcane Ruins)

> Design for the unique item/economy/loot loop. Replaces the "techy" naming and flat loot
> tables in `GAME_DESIGN.md` (§2, §3, §4) with the **Arcane Ruins** identity — a magical rift
> leaking chaos, NOT a computer virus.
>
> Differentiators researched against **Wynncraft** (persistent MMO, level-gated gear, elemental
> damage, profession crafting) and **Hypixel Ravengard** (Dark-and-Darker clone; shrinking death
> wall, 3-man parties, weapons with only 2 stats — top complaint: *no loot depth*).

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

## 2. Rarity System (renamed from GAME_DESIGN.md §3)

| Tier | Name | Color | Shard Value | Note |
|---|---|---|---|---|
| 1 | **Fragmented** | White | 1-5 | Scrap → Fragmented |
| 2 | **Stabilized** | Green | 10-25 | Salvaged → Stabilized |
| 3 | **Resonant** | Blue | 50-100 | Reclaimed → Resonant |
| 4 | **Empowered** | Purple | 200-500 | Overclocked → Empowered |
| 5 | **Primordial** | Gold | 1000-2500 | Corrupted → Primordial |

Gear base stays the same (wood→stone→iron→diamond→netherite) — only names change.

### Stat roll ranges per rarity (identify results)

| Stat | Fragmented | Stabilized | Resonant | Empowered | Primordial |
|---|---|---|---|---|---|
| Damage bonus | +0–5% | +5–10% | +10–15% | +15–20% | +20–30% |
| Armor bonus | +0–2 | +2–4 | +4–6 | +6–8 | +8–10 |
| Speed | +0–2% | +2–4% | +4–6% | +6–8% | +8–12% |
| Max HP | +0 | +1–2 | +2–3 | +3–5 | +5–8 |
| Resonance boost | — | +1 | +1 | +2 | +2 |
| Roll stars | 0–1 | 0–2 | 0–3 | 1–4 | 2–5 |

Star system (inspired by Wynncraft IDs, simplified): each stat roll gets 0–5 stars; stars are
shown in lore. High-roll Primordial = chase item for the economy.

---

## 3. Materials (renamed)

| Current | New | Source | Use |
|---|---|---|---|
| Glitch Dust | **Rune Fragment** | T1-T3 mobs (common) | Crafting base |
| Circuit Board | **Aether Shard** | T2+ mobs, chests | Resonance crafting |
| Data Shard | **Rift Crystal** | T2+ mobs, chests | Identify fuel, key crafting |
| Corrupted Core | **Void Essence** | Elites/bosses, Core Caches | Top crafting, keys |
| Mythic Fragment | **Primordial Relic** | Bosses (10%) | Primordial-only recipes |

### Keys

| Current | New | Uses |
|---|---|---|
| Rusty Key | **Fractured Key** | Debris Piles / Explorer's Caches |
| Vault Key | **Sealed Key** | Sealed Reliquaries |
| Master Key | **Primordial Key** | Rift Vaults |

---

## 4. Unstable Rifts (identify loop)

### Drop flow
1. Mobs/chests drop an **Unstable Rift** — an amethyst/end-crystal item tinted by rarity.
2. Rifts are carried out and stored in the stash like any item.
3. At the hub, the **Identifier NPC** stabilizes a rift for a shard fee:

| Rarity | Identify fee (Shards) |
|---|---|
| Fragmented | 5 |
| Stabilized | 20 |
| Resonant | 75 |
| Empowered | 250 |
| Primordial | 800 |

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
| Loot luck | +10% rare-roll chance / +1 chest tier upgrade chance |
| Aggro | mobs detect you from +4 blocks, prefer targeting you |
| Damage taken | +5% incoming damage |
| Elite events | at stack 5+, elite mobs begin hunting you |

**Timer:** +1 stack every 60 seconds. **Clears:** on extraction or death.

**Extraction payout:** saved loot value × (1 + 0.15 × stacks). 8 stacks = 2.2× value, but you're
nearly deafeningly loud, heavily damaged, and hunted. That's the extract-now-vs-greed choice,
self-imposed instead of a wall.

### Flavor
"Residual Glitch" is residual magical chaos clinging to you the longer you linger in the rift —
it draws more of the anomaly (better loot) while eroding your grip on reality (more damage).

---

## 7. Crafting (replaces GAME_DESIGN.md §4 recipes)

| Recipe | Materials | Output |
|---|---|---|
| Stabilized Weapon (base) | 3 Rune Fragment + 2 Rift Crystal | Stabilized weapon (random Resonance) |
| Targeted Resonance Weapon | base + 2 Aether Shard of chosen Resonance | Weapon locked to chosen Resonance |
| Rift Reveal Pack | 5 Rift Crystal | Free reveal of 1 Stabilized rift |
| Sealed Key | 3 Rift Crystal + 1 Void Essence | Sealed Key |
| Primordial Key | 3 Void Essence + 1 Primordial Relic | Primordial Key |
| Void Infusion | 2 Void Essence + 1 Primordial Relic | +1 Resonance boost on an Empowered item |

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
| Overclock (passive) | **Resonant Surge** |
| Overload (ultimate) | **Cataclysm** |

Class names themselves stay (Vanguard/Warden/Specter/Operator). Operator reads fine as an
arcane-engineer archetype once abilities are renamed.

---

## 9. Container renames (GAME_DESIGN.md §3 table)

| Current | New | Contents unchanged |
|---|---|---|
| Scrap Pile | **Debris Pile** | everywhere |
| Supply Crate | **Explorer's Cache** | mid-tier |
| Locked Vault | **Sealed Reliquary** | hard areas, needs Sealed Key |
| Core Cache | **Rift Vault** | boss areas |

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
2. **Material + key items** — ✅ 5 materials + 3 keys as Oraxen configs (server/plugins/Oraxen/), deploy via setup-oraxen-items.sh
3. Rarity tiers + stat-roll engine (identify outcome).
4. Rift drop (mob loot tables emit rifts) + Identifier NPC flow.
5. Resonance tags (MythicMobs metadata) + gear rolls.
6. Residual Glitch timer + effects + extraction multiplier.
7. World population (spawners, chests, regen) — emits rifts from §4.
8. Crafting recipes via Workbench.
9. Rename pass across GlitchClasses configs + MythicMobs drop tables + DeluxeMenus shop.
