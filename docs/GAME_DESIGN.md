# The Glitch — Game Design Document

> Extraction loop, class system, mob design, loot tiers, hideout progression, dungeon scaling.
> All systems designed for Minecraft (MythicMobs + VelKoth + custom plugins).
> Arcane Ruins aesthetic: corrupted magical anomaly — no guns, no techy/circuit items.
> Item names/rarities per docs/ITEM_SYSTEM.md; merchant economy per ITEM_SYSTEM.md §11.

> **Implementation status:** This is a design specification, not an as-built
> feature list. The tables below describe intended gameplay. Four initial mobs,
> the core class plugin, GlitchStash, and parts of the item/merchant plugins
> exist in source/configuration; most world content, loot integration, dungeon
> content, death rules, and anti-grief rules remain incomplete. See
> [`docs/STATUS.md`](STATUS.md).

---

## 1. Classes

Four classes, each with a unique identity. Classes are chosen once and can be reset at the hideout for a fee.

### Vanguard (Tank / Frontline)

*The wall between your team and death.*

| Slot | Ability | Type | Effect |
|---|---|---|---|
| Prime | **Aegis Wall** | Active (30s cooldown) | Deploy a 3x3 barrier that absorbs 200 damage. Allies behind it take 50% reduced damage from projectiles. |
| Tactical | **Taunt** | Active (15s cooldown) | Force all mobs within 10 blocks to target you for 5 seconds. You gain 20% damage reduction during taunt. |
| Trait 1 | **Ironclad** | Passive | Knockback resistance while holding a shield. +1 armor tier. |
| Trait 2 | **Last Stand** | Passive | When below 30% HP, gain 40% damage resistance for 5 seconds. 60s cooldown. |

**Upgrade path (10 levels):**
1. +5% melee damage
2. +10 HP
3. Aegis Wall cooldown -5s
4. Taunt range +5 blocks
5. +1 armor tier
6. Last Stand duration +2s
7. +10% damage while Last Stand active
8. Aegis Wall absorbs +100 damage
9. Taunt now also slows enemies by 30%
10. **Ultimate: Fortress** — Aegis Wall becomes indestructible for 3 seconds

---

### Warden (Support / Healer)

*The reason your squad walks out alive.*

| Slot | Ability | Type | Effect |
|---|---|---|---|
| Prime | **Healing Pulse** | Active (25s cooldown) | Burst heal all allies within 8 blocks for 40 HP. Applies Regeneration II for 5s. |
| Tactical | **Resonance Beacon** | Active (20s cooldown) | Place a beacon that revives downed allies within 5 blocks after 3 seconds. Single use per death. |
| Trait 1 | **Mend** | Passive | When you eat food, allies within 5 blocks also heal 10 HP. |
| Trait 2 | **Vigilance** | Passive | You can see ally health bars through walls within 20 blocks. |

**Upgrade path (10 levels):**
1. +5% healing potency
2. +10 HP
3. Healing Pulse radius +3 blocks
4. Resonance Beacon placement speed -1s
5. Mend now also gives Absorption I for 10s
6. Healing Pulse cooldown -5s
7. +10% healing potency (total 15%)
8. Resonance Beacon can revive 2 allies
9. Vigilance now also shows enemy health bars
10. **Ultimate: Guardian Angel** — Next time an ally would die, they instead survive with 1 HP and get 3s of invulnerability. 120s cooldown.

---

### Specter (Stealth / Looter)

*The first in, the last out, and the richest.*

| Slot | Ability | Type | Effect |
|---|---|---|---|
| Prime | **Cloak** | Active (30s cooldown) | Turn invisible for 5 seconds. Attacking or taking damage breaks it. Mobs won't target you. |
| Tactical | **Shadow Step** | Active (12s cooldown) | Teleport 10 blocks forward (line of sight required). Generates no noise. |
| Trait 1 | **Lightweight** | Passive | +15% movement speed. -10% fall damage. |
| Trait 2 | **Scavenge** | Passive | +20% loot from containers. +1 inventory slot. |

**Upgrade path (10 levels):**
1. +5% movement speed
2. +5 HP
3. Cloak duration +2s
4. Shadow Step range +5 blocks
5. +10% loot from containers (total 30%)
6. Cloak cooldown -5s
7. Lightweight now +20% movement speed
8. Shadow Step can be used twice before cooldown
9. Scavenge now +30% loot and +2 inventory slots
10. **Ultimate: Ghost Protocol** — For 10 seconds, mobs cannot detect you and you move at 2x speed. 120s cooldown.

---

### Operator (Control / Constructs)

*Controls the battlefield before the fight starts.*

| Slot | Ability | Type | Effect |
|---|---|---|---|
| Prime | **Sentinel Construct** | Active (40s cooldown) | Place an auto-targeting arcane construct that fires at mobs for 15 seconds. 100 HP, 5 damage/shot. |
| Tactical | **Null Grenade** | Active (15s cooldown) | Throw a grenade that disables mob abilities in a 6-block radius for 5 seconds. |
| Trait 1 | **Engineer** | Passive | You can repair constructs and barriers by right-clicking them. +25% construct/barrier HP. |
| Trait 2 | **Resonance Surge** | Passive | Construct fires 25% faster. Null Grenade duration +2s. |

**Upgrade path (10 levels):**
1. +5% construct damage
2. +10 HP
3. Construct duration +5s
4. Null Grenade radius +3 blocks
5. +1 construct charge (can place 2 constructs)
6. Construct damage +25% (total 30%)
7. Null Grenade now also slows enemies by 40% for 3s
8. Construct gains a shield (50 HP) that absorbs damage before the construct itself
9. Resonance Surge now +50% construct fire rate
10. **Ultimate: Cataclysm** — Construct explodes in a 10-block radius dealing 80 damage to all mobs, then spawns a new construct. 120s cooldown.

---

## 2. Custom Mobs (MythicMobs)

10 mobs across 4 tiers. Resonance tags (docs/ITEM_SYSTEM.md §5) are matched by gear:
a Hollow weapon deals +25% damage to Hollow mobs. `Resonance:` column is a MythicMobs
metadata field.

**Zone distribution:**
- **glitch_pve (dungeons):** wave-based spawning scaled per dungeon tier (§5). T1 dungeons = T1 mobs, T2 = T1+T2, ..., T5 = everything incl. bosses.
- **glitch_red (open world):** T1 fodder roams everywhere (common), T2 standard mobs occupy mid zones, T3 elites guard points of interest (the Core at 0,0, Vaults, extraction sites), T4 bosses spawn as scheduled server events.

### Tier 1 — Corrupted Drones (Fodder)

| Mob | Base | HP | Damage | Resonance | Behavior |
|---|---|---|---|---|---|
| **Glitch Wisp** | Vex | 20 | 3 | Veil | Swarm (spawns in groups of 3-5). Fast, low HP. Alert other mobs when they spot a player. |
| **Corrupted Crawler** | Silverfish | 30 | 4 | Hollow | Burrows through walls. Emerges under players. Poisons on hit (2s). |

**Drops:** Rune Fragment (common), nothing special.

---

### Tier 2 — Corrupted Infantry (Standard)

| Mob | Base | HP | Damage | Resonance | Behavior |
|---|---|---|---|---|---|
| **Glitch Stalker** | Zombie | 60 | 6 | Bloom | Sneaks toward players. Attacks from behind for bonus damage (1.5x). Retreats when low HP. |
| **Glitch Brute** | Zombie (large) | 120 | 10 | Aegis | Slow, heavy hitter. Charges in a straight line (knockback 10 blocks). 3s charge-up telegraphed by particles. |
| **Glitch Phantom** | Skeleton | 50 | 8 | Veil | Ranged attacker. Teleports when a player gets within 5 blocks. Shoots spectral arrows that bypass shields. |

**Drops:** Rune Fragment (common), Aether Shard (10%), Rift Crystal (5%), Unstable Rift (small chance, §3 drop table).

---

### Tier 3 — Corrupted Elite (Dangerous)

| Mob | Base | HP | Damage | Resonance | Behavior |
|---|---|---|---|---|---|
| **Glitch Sentinel** | Wither Skeleton | 200 | 14 | Ward | AoE slam (5-block radius, 15 damage, 2s stun). Summons 2 Glitch Wisps every 30s. Immune to knockback. |
| **Glitch Sniper** | Skeleton (enchanted) | 80 | 18 | Ward | Long-range laser. Charges for 2s (red beam telegraph), then fires for massive damage. Weak point: glowing core. |
| **Glitch Warden** | Iron Golem | 300 | 8 | Aegis | Guards a specific area. Pulls players toward it with a vortex (every 20s). Spawns a damage field around itself. |

**Drops:** Rune Fragment (uncommon), Aether Shard (30%), Rift Crystal (15%), Void Essence (5%), Unstable Rift (good chance, higher rarity weighting).

---

### Tier 4 — Boss (Server Event)

| Mob | Base | HP | Damage | Resonance | Behavior |
|---|---|---|---|---|---|
| **The Glitch King** | Ender Dragon | 2000 | 20+ | Hollow | 3-phase fight. Phase 1 (100-75% HP): Summons Glitch Stalkers, ground slam AoE. Phase 2 (75-25% HP): Teleports around arena, fires laser beams, creates corruption zones (damage over time). Phase 3 (<25% HP): Enrage mode — faster attacks, more spawns, but core is exposed (3x damage). |
| **The Corrupted Core** | Wither | 1500 | 15+ | Hollow | Stationary boss. Spawns corruption turrets that fire projectiles. Players must destroy turrets to damage the core. Every 25% HP lost, spawns a wave of Corrupted Crawlers. |

**Drops:** Void Essence (guaranteed), Legendary Relic (10%), Epic/Legendary loot (30%), guaranteed high-rarity Unstable Rifts, Shards (50-100).

---

### Mob Skills (MythicMobs)

```yaml
# Example: Glitch Brute charge attack
BruteCharge:
  Skills:
    - message{msg="<red>The Brute is charging!"} @PlayersInRadius{r=10}
    - effect:particles{p=REDSTONE;d=3;r=2} @self ~onTimer:20
    - damage{a=15} @Target
    - velocity{v=0,0.5,0;type=SET} @Target
    - sound{s=entity.iron_golem.attack;v=1;p=0.5} @self

# Example: Glitch Sniper laser
SniperLaser:
  Skills:
    - effect:particles{p=REDSTONE;d=1;r=1} @target ~onTimer:40
    - damage{a=18} @Target
    - sound{s=block.beacon.activate;v=0.8;p=1.2} @self
```

---

## 3. Loot Tiers

### Rarity System

| Tier | Name | Color | Drop Rate (Glitch PVE) | Drop Rate (Glitch Red) | Shard Value |
|---|---|---|---|---|---|
| 1 | **Common** | White | 60% | 40% | 1-5 |
| 2 | **Uncommon** | Green | 25% | 30% | 10-25 |
| 3 | **Rare** | Blue | 10% | 20% | 50-100 |
| 4 | **Epic** | Purple | 4% | 8% | 200-500 |
| 5 | **Legendary** | Gold | 1% | 2% | 1000-2500 |

### Gear (weapons + armor)

Full design in docs/ITEM_SYSTEM.md §2: 3 weapon archetypes (**Blade**, **Greatblade**,
**Arcane Staff**), 4 armor pieces (helmet, chestplate, leggings, boots). Base material
scales with rarity (wood/leather → netherite). Weapons gain special attributes from
Rare up (lifesteal, fire aspect, ...); armor keeps it simple: **base stats upgraded
by rarity + exactly one attribute** from Rare up.

| Type | Common | Uncommon | Rare | Epic | Legendary |
|---|---|---|---|---|---|
| **Weapons** | Wooden Blade | Stone Blade | Iron Blade | Diamond Blade | Netherite Blade |
| **Armor** | Leather | Chainmail | Iron | Diamond | Netherite (full set) |
| **Consumables** | Bread | Golden carrot | Enchanted golden apple | Totem of undying | Custom: Corrupted Heal (full HP + 10s Regen III) |
| **Materials** | Rune Fragment x1 | Aether Shard x1 | Rift Crystal x1 | Void Essence x1 | Legendary Relic x1 |
| **Keys** | — | Cache Key (Loot Caches) | Vault Key (Vaults) | Rift Key (Rift Vaults) | — |
| **Unstable Rifts** | Common rift | Uncommon rift | Rare rift | Epic rift | Legendary rift |

### Shard Drop Rates (MythicMobs)

| Mob Tier | Rune Fragment | Aether Shard | Rift Crystal | Void Essence | Legendary Relic |
|---|---|---|---|---|---|
| Tier 1 (Fodder) | 100% (1-2) | — | — | — | — |
| Tier 2 (Infantry) | 100% (2-4) | 10% | 5% | — | — |
| Tier 3 (Elite) | 100% (3-6) | 30% | 15% | 5% | — |
| Tier 4 (Boss) | 100% (10-20) | 100% (3-5) | 100% (1-3) | 100% (1) | 10% |

### Loot Containers (in-world)

| Container | Location | Key | Contents |
|---|---|---|---|
| **Debris Pile** | Everywhere | none | Common (60%), Uncommon (25%), nothing (15%) |
| **Loot Cache** | Mid-tier areas | Cache Key | Uncommon (40%), Rare (30%), Common (20%), nothing (10%) |
| **Vault** | Hard areas | Vault Key | Rare (30%), Epic (40%), Uncommon (20%), nothing (10%) |
| **Rift Vault** | Boss areas | Rift Key | Epic (30%), Legendary (50%), Rare (20%) |

---

## 4. Hideout Upgrades

The hideout is a persistent progression system between raids. Located in the hub, accessible via `/hideout`.

### Station List

| # | Station | Function | Upgrade Cost (Shards) | Prerequisites |
|---|---|---|---|---|
| 1 | **Workbench** | Craft weapons, armor, consumables from materials (Resonance crafting — no RNG professions) | 100 / 500 / 2000 | None / Station 2 / Station 4 |
| 2 | **Med Station** | Heal between raids (free), craft healing items | 50 / 300 / 1000 | None / Workbench 1 / Workbench 2 |
| 3 | **Stash** | Expand inventory storage (36 → 54 → 72 → 90 slots) | 200 / 800 / 3000 | None / Station 1 / Station 2 |
| 4 | **Intel Center** | Unlock mob health bars, extraction timers, map info | 150 / 600 / 2500 | None / Station 1 / Station 3 |
| 5 | **Arcane Core** | Enable other stations, powers the anomaly-based stations | 300 / 1000 / 4000 | Workbench 1 / Intel Center 1 / Intel Center 2 |
| 6 | **Skill Trainer** | Reset class, upgrade class abilities (10 levels per class) | 100 per level | None |
| 7 | **Armory** | Store and organize gear between raids, auto-sort | 250 / 750 | Stash 2 / Arcane Core 1 |

### Crafting Recipes (Workbench)

| Recipe | Materials | Output |
|---|---|---|
| Healing Potion | 5 Rune Fragment + 1 Rift Crystal | 3x Healing Potion (Regen II, 5s) |
| Base Weapon (Uncommon) | 3 Rune Fragment + 2 Rift Crystal | Uncommon weapon (random Resonance) |
| Targeted Resonance Weapon | base + 2 Aether Shard of chosen Resonance | Weapon locked to chosen Resonance |
| Rift Reveal Pack | 5 Rift Crystal | Free reveal of 1 Uncommon rift |
| Vault Key | 3 Rift Crystal + 1 Void Essence | Vault Key (1 use) |
| Rift Key | 3 Void Essence + 1 Legendary Relic | Rift Key (1 use) |
| Void Infusion | 2 Void Essence + 1 Legendary Relic | +1 Resonance boost on an Epic item |

---

## 5. Dungeon Tiers (glitch_pve)

Five dungeon tiers, each harder than the last. Tier 1 is for beginners, Tier 5 is endgame.

### Tier Scaling

| Tier | Mob HP Multiplier | Mob Damage Multiplier | Spawn Rate | Min Players | Timer | Shard Reward | Loot Table |
|---|---|---|---|---|---|---|---|
| 1 | 1.0x | 1.0x | Normal | 1 | 10 min | 10-20 | Common/Uncommon |
| 2 | 1.5x | 1.25x | +25% | 2 | 12 min | 20-40 | Uncommon/Rare |
| 3 | 2.0x | 1.5x | +50% | 3 | 15 min | 40-80 | Rare/Epic |
| 4 | 3.0x | 2.0x | +75% | 3 | 18 min | 80-150 | Epic/Legendary |
| 5 | 4.0x | 2.5x | +100% | 4 | 20 min | 150-300 | Legendary only |

### Dungeon Modifiers (Rotating Weekly)

| Modifier | Effect |
|---|---|---|
| **Glass Cannon** | All damage dealt +50%, all HP -25% |
| **Horde Mode** | 2x spawn rate, -25% mob HP |
| **Boss Rush** | Only elites and bosses spawn |
| **Darkness** | Night vision flickers, mobs glow in the dark |
| **Corrupted** | All mobs have poison aura (1 damage/s within 3 blocks) |

---

## 6. New Player Experience (Full Flow)

### Joining the Server

1. **Land in Hub** — safe zone, nothing can hurt you. 20 players max in hub.
2. **Pick a class** — walk to the class selection area, click an NPC:
   - Vanguard (tank) — "Hold the line. Protect your team."
   - Warden (support) — "Keep everyone alive. You're the reason they extract."
   - Specter (stealth) — "Get in, get rich, get out. They never see you."
   - Operator (control) — "Control the fight before it starts."
3. **Get starter kit** — basic leather armor, wooden Blade, 3 bread, 5 Rune Fragments.
4. **Explore hub** — class trainer (upgrade abilities), armory (store gear), merchant NPCs (buy potions/keys/materials, sell any custom item — ITEM_SYSTEM §11).
5. **Learn the extraction loop** — `/tutorial` starts a 5-minute guided run in a safe zone.

### First Dungeon Run (Tier 1)

6. **Join a party** — party system, 1-4 players. Matchmaking or friends.
7. **Enter dungeon** — assigned to slot 1 of 8. Teleported in. Timer starts (10 minutes).
8. **Fight waves** — Corrupted Crawlers and Glitch Wisps spawn. Easy mobs, learn mechanics.
9. **Loot containers** — Debris Piles everywhere. Grab what you can.
10. **Extract** — hold extraction zone for 30 seconds. Inventory saved to stash. Teleported back to hub.
11. **Retrieve loot** — `/stash` in hub. Click items to move to inventory.
12. **Spend shards** — class trainer: upgrade Aegis Wall or Taunt. Merchants: sell junk, buy potions and keys. Identify your first Unstable Rift at the Identifier NPC.

### First Red Zone Run (glitch_red)

13. **Enter Red Zone** — one of 6 entry points. Full loot on death.
14. **Loot cautiously** — Uncommon and Rare items in Loot Caches.
15. **Fight mobs** — Glitch Stalkers and Phantoms. Be careful, they hit harder.
16. **If you die** — everything drops **except your leggings and boots** (kept as a mercy rule — see §7 Death Rules). Back to hub empty-handed otherwise. Class XP still counts.
17. **If you extract** — inventory saved. Spend shards on upgrades. The longer you stayed, the bigger the payout (Residual Glitch, ITEM_SYSTEM §6).

### Mid-Game (Tier 2-3 Dungeons)

18. **Dungeon Tier 2** — 2 players minimum. Mobs have 1.5x HP. Brutes appear.
19. **Craft better gear** — workbench: Base Weapon from Rune Fragments + Rift Crystals.
20. **Hideout upgrades** — Med Station (free heals), Stash expansion (more storage).
21. **Dungeon Tier 3** — 3 players. Mobs have 2x HP. Sentinels and Snipers appear.
22. **Red Zone deeper** — Loot Caches give Rare loot. Vaults need Vault Keys.
23. **Class level 5** — Aegis Wall duration +2s. Taunt range +5 blocks.

### Late-Game (Tier 4-5 Dungeons + Bosses)

24. **Dungeon Tier 4** — 3 players. 3x mob HP. The Glitch King boss spawns.
25. **Craft Epic gear** — Diamond Blade, Diamond Armor from Void Essences.
26. **Dungeon Tier 5** — 4 players. 4x mob HP. The Corrupted Core boss. Weekly modifiers active.
27. **Red Zone endgame** — Rift Vault containers give Epic and Legendary loot.
28. **Class level 10** — Ultimate ability unlocked. Fortress, Guardian Angel, Ghost Protocol, or Cataclysm.
29. **Hideout maxed** — all stations upgraded, full crafting, 90-slot stash.
30. **Seasonal reset** — every 3 months, stash resets but class XP and hideout permanent.

---

## 7. Extraction Types

| Type | Timer | Noise | Requirement | Location |
|---|---|---|---|---|
| **Standard Extract** | 30s | Loud (zone-wide) | None | 3 per map |
| **Fast Extract** | 15s | Moderate | Fast Extract Key (craftable / buy 75 Shards) | 1 per map |
| **Silent Extract** | 10s | Silent | Rift Key (rare drop) | 1 per map (hidden) |

**Key design:** Standard is free but loud — attracts mobs and players. Fast is quicker but costs a key. Silent is the best but the key is extremely rare.
**Repository configuration:** VelKoth is set to **30 seconds** for the Standard
extract. The live arena timer is stored in generated `arenas.yml` and must be
verified on the server. Fast/Silent extraction is **implemented in source
(2026-08-06)** in GlitchStash (key-requiring zones, arming, payout bonus) —
VelKoth arenas with 15s/10s capture times must still be created live and
mirrored into the GlitchStash config.

### Death Rules

| Zone | On death |
|---|---|
| **hub** | Nothing lost (safe zone). |
| **glitch_pve** | Keep-inventory: run is lost, your items are kept. Training floor. |
| **glitch_red** | Full loot — **with one mercy rule: you keep your leggings and boots**. Helmet, chestplate, weapons, inventory, and shards all drop. The point is danger: a corpse can always be re-geared from the legs up. |

The current Coins configuration drops currency as items in game worlds. The
design decision that shards are account-bound is unresolved and must be aligned
with the death/economy implementation before launch. Merchant sell prices are
intended as the safety net for extracted loot.

---

## 8. Economy Balance

### Shard Income Sources

| Source | Shards per Run | Frequency |
|---|---|---|
| Tier 1 dungeon | 10-20 | Every 10 min |
| Tier 2 dungeon | 20-40 | Every 12 min |
| Tier 3 dungeon | 40-80 | Every 15 min |
| Tier 4 dungeon | 80-150 | Every 18 min |
| Tier 5 dungeon | 150-300 | Every 20 min |
| Red Zone extraction | 20-100 | Variable |
| Boss kill | 50-100 | Rare |
| Loot sale (merchant) | 5-500 per item | Any time |

### Shard Expense Sources

| Source | Cost | Notes |
|---|---|---|
| Class upgrade (1-5) | 50-200 per level | Cheap early, expensive later |
| Class upgrade (6-10) | 300-1000 per level | Endgame investment |
| Hideout stations | 50-4000 | One-time cost, permanent |
| Identify fees (Unstable Rifts) | 5-800 per rift | ITEM_SYSTEM §4 |
| Crafting materials | 10-500 per recipe | Consumable |
| Vault Key (buy) | 120 | Single use |
| Fast Extract Key (buy) | 75 | Single use |
| Class reset | 500 | Full reset |

Full merchant price table (sell < buy): docs/ITEM_SYSTEM.md §11.

### Balance Target

- **New player:** ~50 shards/hour from Tier 1 dungeons
- **Mid-game:** ~150 shards/hour from Tier 2-3 dungeons
- **Endgame:** ~400 shards/hour from Tier 4-5 dungeons + Red Zone
- **Full hideout upgrade cost:** ~15,000 shards total (100 hours at new player rate)
- **Full class upgrade cost:** ~5,000 shards per class (50 hours at new player rate)

---

## 9. Anti-Grief / Fair Play

| Rule | Intended implementation | Current status |
|---|---|---|
| No spawn camping | 30-second invulnerability on entry points | Planned |
| No team killing | Friendly fire disabled in all zones | Partially configured; live verification pending |
| No item duplication | Per-player stash, item-safe GUI handling | Core stash fixes implemented; testing pending |
| No AFK farming | Dungeons kick after 2 minutes of inactivity | Not implemented |
| No RMT | Shards are bound to account, not tradeable between players | Conflicts with current item-drop currency configuration |
| No exploit abuse | Weekly audit logs, automatic detection | Planned |

---

## 10. Seasonal Content (Planned)

| Season | Theme | New Content |
|---|---|---|
| Season 1 | **The Awakening** | Base game, 4 classes, 10 mobs across 4 tiers, hideout |
| Season 2 | **Frozen Corruption** | Ice biome dungeon, frost mobs, cold weather mechanic |
| Season 3 | **The Hive** | Underground dungeon, insectoid mobs, swarm mechanic |
| Season 4 | **Void Rift** | End-game dungeon, void mobs, reality-bending mechanics |

Each season: new dungeon tier, new mob types, new loot, seasonal leaderboard, stash reset.

---

## 11. Merchants (GlitchShops)

Hub merchant NPCs handle the item economy (design: docs/GLITCH_SHOPS_DESIGN.md, prices:
docs/ITEM_SYSTEM.md §11):

- **Vendors (Materials / Keys / Alchemy / Rifts)** buy **any** custom item at its sell price and sell stock at higher buy prices (buy price shown only in the GUI, never on the item).
- **Armourer / Weaponsmith** sell gear: fixed base price, **small random variance on the rolls**, and a **super-rare (0.01%) variant** of each weapon in stock with maximum rolls.
- Selling junk is the shard safety net; buying gear is the shard sink that keeps extraction risk meaningful.
