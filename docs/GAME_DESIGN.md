# The Glitch — Game Design Document

> Extraction loop, class system, mob design, loot tiers, hideout progression, dungeon scaling.
> All systems designed for Minecraft (MythicMobs + VelKoth + custom plugins).

---

## 1. Classes

Four classes, each with a unique identity. Classes are chosen once and can be reset at the hideout for a fee.

### Vanguard (Tank / Frontline)

*The wall between your team and death.*

| Slot | Ability | Type | Effect |
|---|---|---|---|
| Prime | **Shield Wall** | Active (30s cooldown) | Deploy a 3x3 barrier that absorbs 200 damage. Allies behind it take 50% reduced damage from projectiles. |
| Tactical | **Taunt** | Active (15s cooldown) | Force all mobs within 10 blocks to target you for 5 seconds. You gain 20% damage reduction during taunt. |
| Trait 1 | **Ironclad** | Passive | Knockback resistance while holding a shield. +1 armor tier. |
| Trait 2 | **Last Stand** | Passive | When below 30% HP, gain 40% damage resistance for 5 seconds. 60s cooldown. |

**Upgrade path (10 levels):**
1. +5% melee damage
2. +10 HP
3. Shield Wall cooldown -5s
4. Taunt range +5 blocks
5. +1 armor tier
6. Last Stand duration +2s
7. +10% damage while Last Stand active
8. Shield Wall absorbs +100 damage
9. Taunt now also slows enemies by 30%
10. **Ultimate: Fortress** — Shield Wall becomes indestructible for 3 seconds

---

### Warden (Support / Healer)

*The reason your squad walks out alive.*

| Slot | Ability | Type | Effect |
|---|---|---|---|
| Prime | **Healing Pulse** | Active (25s cooldown) | Burst heal all allies within 8 blocks for 40 HP. Applies Regeneration II for 5s. |
| Tactical | **Revive Beacon** | Active (20s cooldown) | Place a beacon that revives downed allies within 5 blocks after 3 seconds. Single use per death. |
| Trait 1 | **Mend** | Passive | When you eat food, allies within 5 blocks also heal 10 HP. |
| Trait 2 | **Vigilance** | Passive | You can see ally health bars through walls within 20 blocks. |

**Upgrade path (10 levels):**
1. +5% healing potency
2. +10 HP
3. Healing Pulse radius +3 blocks
4. Revive Beacon placement speed -1s
5. Mend now also gives Absorption I for 10s
6. Healing Pulse cooldown -5s
7. +10% healing potency (total 15%)
8. Revive Beacon can revive 2 allies
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

### Operator (Tech / Control)

*Controls the battlefield before the fight starts.*

| Slot | Ability | Type | Effect |
|---|---|---|---|
| Prime | **Turret Deploy** | Active (40s cooldown) | Place an auto-targeting turret that fires at mobs for 15 seconds. 100 HP, 5 damage/shot. |
| Tactical | **EMP Grenade** | Active (15s cooldown) | Throw a grenade that disables mob abilities in a 6-block radius for 5 seconds. |
| Trait 1 | **Engineer** | Passive | You can repair turrets and barriers by right-clicking them. +25% turret/Barrier HP. |
| Trait 2 | **Overclock** | Passive | Turret fires 25% faster. EMP Grenade duration +2s. |

**Upgrade path (10 levels):**
1. +5% turret damage
2. +10 HP
3. Turret duration +5s
4. EMP Grenade radius +3 blocks
5. +1 turret charge (can place 2 turrets)
6. Turret damage +25% (total 30%)
7. EMP Grenade now also slows enemies by 40% for 3s
8. Turret gains a shield (50 HP) that absorbs damage before the turret itself
9. Overclock now +50% turret fire rate
10. **Ultimate: Overload** — Turret explodes in a 10-block radius dealing 80 damage to all mobs, then spawns a new turret. 120s cooldown.

---

## 2. Custom Mobs (MythicMobs)

### Mob Categories

#### Tier 1 — Corrupted Drones (Fodder)

| Mob | Base | HP | Damage | Behavior |
|---|---|---|---|---|
| **Glitch Wisp** | Vex | 20 | 3 | Swarm (spawns in groups of 3-5). Fast, low HP. Alert other mobs when they spot a player. |
| **Corrupted Crawler** | Silverfish | 30 | 4 | Burrows through walls. Emerges under players. Poisons on hit (2s). |

**Drops:** Glitch Dust (common), nothing special.

---

#### Tier 2 — Corrupted Infantry (Standard)

| Mob | Base | HP | Damage | Behavior |
|---|---|---|---|---|
| **Glitch Stalker** | Zombie | 60 | 6 | Sneaks toward players. Attacks from behind for bonus damage (1.5x). Retreats when low HP. |
| **Glitch Brute** | Zombie (large) | 120 | 10 | Slow, heavy hitter. Charges in a straight line (knockback 10 blocks). 3s charge-up telegraphed by particles. |
| **Glitch Phantom** | Skeleton | 50 | 8 | Ranged attacker. Teleports when a player gets within 5 blocks. Shoots spectral arrows that bypass shields. |

**Drops:** Glitch Dust (common), Circuit Board (10%), Data Shard (5%).

---

#### Tier 3 — Corrupted Elite (Dangerous)

| Mob | Base | HP | Damage | Behavior |
|---|---|---|---|---|
| **Glitch Sentinel** | Wither Skeleton | 200 | 14 | AoE slam (5-block radius, 15 damage, 2s stun). Summons 2 Glitch Wisps every 30s. Immune to knockback. |
| **Glitch Sniper** | Skeleton (enchanted) | 80 | 18 | Long-range laser. Charges for 2s (red beam telegraph), then fires for massive damage. Weak point: glowing core. |
| **Glitch Warden** | Iron Golem | 300 | 8 | Guards a specific area. Pulls players toward it with a vortex (every 20s). Spawns a damage field around itself. |

**Drops:** Glitch Dust (uncommon), Circuit Board (30%), Data Shard (15%), Corrupted Core (5%).

---

#### Tier 4 — Boss (Server Event)

| Mob | Base | HP | Damage | Behavior |
|---|---|---|---|---|
| **The Glitch King** | Ender Dragon | 2000 | 20+ | 3-phase fight. Phase 1 (100-75% HP): Summons Glitch Stalkers, ground slam AoE. Phase 2 (75-25% HP): Teleports around arena, fires laser beams, creates corruption zones (damage over time). Phase 3 (<25% HP): Enrage mode — faster attacks, more spawns, but core is exposed (3x damage). |
| **The Corrupted Core** | Wither | 1500 | 15+ | Stationary boss. Spawns corruption turrets that fire projectiles. Players must destroy turrets to damage the core. Every 25% HP lost, spawns a wave of Corrupted Crawlers. |

**Drops:** Corrupted Core (guaranteed), Mythic loot (10%), Legendary loot (30%), Shards (50-100).

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
| 1 | **Scrap** | White | 60% | 40% | 1-5 |
| 2 | **Salvaged** | Green | 25% | 30% | 10-25 |
| 3 | **Reclaimed** | Blue | 10% | 20% | 50-100 |
| 4 | **Overclocked** | Purple | 4% | 8% | 200-500 |
| 5 | **Corrupted** | Gold | 1% | 2% | 1000-2500 |

### Item Types by Rarity

| Type | Scrap | Salvaged | Reclaimed | Overclocked | Corrupted |
|---|---|---|---|---|---|
| **Weapons** | Wooden sword | Stone sword | Iron sword | Diamond sword | Netherite sword (+enchants) |
| **Armor** | Leather | Chainmail | Iron | Diamond | Netherite (full set) |
| **Consumables** | Bread | Golden carrot | Enchanted golden apple | Totem of undying | Custom: Corrupted Heal (full HP + 10s Regen III) |
| **Materials** | Glitch Dust x1 | Circuit Board x1 | Data Shard x1 | Corrupted Core x1 | Mythic Fragment x1 |
| **Keys** | — | Rusty Key (common chest) | Vault Key (rare chest) | Master Key (locked vault) | — |

### Shard Drop Rates (MythicMobs)

| Mob Tier | Glitch Dust | Circuit Board | Data Shard | Corrupted Core | Mythic Fragment |
|---|---|---|---|---|---|
| Tier 1 (Fodder) | 100% (1-2) | — | — | — | — |
| Tier 2 (Infantry) | 100% (2-4) | 10% | 5% | — | — |
| Tier 3 (Elite) | 100% (3-6) | 30% | 15% | 5% | — |
| Tier 4 (Boss) | 100% (10-20) | 100% (3-5) | 100% (1-3) | 100% (1) | 10% |

### Loot Containers (in-world)

| Container | Location | Contents |
|---|---|---|
| **Scrap Pile** | Everywhere | Scrap (60%), Salvaged (25%), nothing (15%) |
| **Supply Crate** | Mid-tier areas | Salvaged (40%), Reclaimed (30%), Scrap (20%), nothing (10%) |
| **Locked Vault** | Hard areas | Reclaimed (30%), Overclocked (40%), Salvaged (20%), nothing (10%). Requires Vault Key. |
| **Core Cache** | Boss areas | Overclocked (30%), Corrupted (50%), Reclaimed (20%) |

---

## 4. Hideout Upgrades

The hideout is a persistent progression system between raids. Located in the hub, accessible via `/hideout`.

### Station List

| # | Station | Function | Upgrade Cost (Shards) | Prerequisites |
|---|---|---|---|---|
| 1 | **Workbench** | Craft weapons, armor, consumables from materials | 100 / 500 / 2000 | None / Station 2 / Station 4 |
| 2 | **Med Station** | Heal between raids (free), craft healing items | 50 / 300 / 1000 | None / Workbench 1 / Workbench 2 |
| 3 | **Stash** | Expand inventory storage (36 → 54 → 72 → 90 slots) | 200 / 800 / 3000 | None / Station 1 / Station 2 |
| 4 | **Intel Center** | Unlock mob health bars, extraction timers, map info | 150 / 600 / 2500 | None / Station 1 / Station 3 |
| 5 | **Power Core** | Enable other stations, craft batteries, power turrets | 300 / 1000 / 4000 | Workbench 1 / Intel Center 1 / Intel Center 2 |
| 6 | **Skill Trainer** | Reset class, upgrade class abilities (10 levels per class) | 100 per level | None |
| 7 | **Armory** | Store and organize gear between raids, auto-sort | 250 / 750 | Stash 2 / Power Core 1 |

### Crafting Recipes (Workbench)

| Recipe | Materials | Output |
|---|---|---|
| Iron Sword | 3 Circuit Board + 2 Data Shard | Iron Sword (Reclaimed) |
| Diamond Armor | 5 Circuit Board + 3 Data Shard + 1 Corrupted Core | Diamond Armor (Overclocked) |
| Healing Potion | 5 Glitch Dust + 1 Data Shard | 3x Healing Potion (Regen II, 5s) |
| Vault Key | 3 Data Shard + 1 Corrupted Core | Vault Key (1 use) |
| Turret Battery | 2 Circuit Board + 2 Glitch Dust | Turret Battery (15s charge) |

---

## 5. Dungeon Tiers (glitch_pve)

Five dungeon tiers, each harder than the last. Tier 1 is for beginners, Tier 5 is endgame.

### Tier Scaling

| Tier | Mob HP Multiplier | Mob Damage Multiplier | Spawn Rate | Min Players | Timer | Shard Reward | Loot Table |
|---|---|---|---|---|---|---|---|
| 1 | 1.0x | 1.0x | Normal | 1 | 10 min | 10-20 | Scrap/Salvaged |
| 2 | 1.5x | 1.25x | +25% | 2 | 12 min | 20-40 | Salvaged/Reclaimed |
| 3 | 2.0x | 1.5x | +50% | 3 | 15 min | 40-80 | Reclaimed/Overclocked |
| 4 | 3.0x | 2.0x | +75% | 3 | 18 min | 80-150 | Overclocked/Corrupted |
| 5 | 4.0x | 2.5x | +100% | 4 | 20 min | 150-300 | Corrupted only |

### Dungeon Modifiers (Rotating Weekly)

| Modifier | Effect |
|---|---|
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
   - Operator (tech) — "Control the fight before it starts."
3. **Get starter kit** — basic leather armor, wooden sword, 3 bread, 5 Glitch Dust.
4. **Explore hub** — class trainer (upgrade abilities), armory (store gear), shop stalls.
5. **Learn the extraction loop** — `/tutorial` starts a 5-minute guided run in a safe zone.

### First Dungeon Run (Tier 1)

6. **Join a party** — party system, 1-4 players. Matchmaking or friends.
7. **Enter dungeon** — assigned to slot 1 of 8. Teleported in. Timer starts (10 minutes).
8. **Fight waves** — Corrupted Crawlers and Glitch Wisps spawn. Easy mobs, learn mechanics.
9. **Loot containers** — Scrap Piles everywhere. Grab what you can.
10. **Extract** — hold extraction zone for 30 seconds. Inventory saved to stash. Teleported back to hub.
11. **Retrieve loot** — `/stash` in hub. Click items to move to inventory.
12. **Spend shards** — class trainer: upgrade Shield Wall or Taunt. Workbench: craft an Iron Sword.

### First Red Zone Run (glitch_red)

13. **Enter Red Zone** — one of 6 entry points. Full loot on death.
14. **Loot cautiously** — Salvaged and Reclaimed items in Supply Crates.
15. **Fight mobs** — Glitch Stalkers and Phantoms. Be careful, they hit harder.
16. **If you die** — everything drops. Back to hub empty-handed. Class XP still counts.
17. **If you extract** — inventory saved. Spend shards on upgrades.

### Mid-Game (Tier 2-3 Dungeons)

18. **Dungeon Tier 2** — 2 players minimum. Mobs have 1.5x HP. Brutes appear.
19. **Craft better gear** — workbench: Iron Sword from Circuit Boards + Data Shards.
20. **Hideout upgrades** — Med Station (free heals), Stash expansion (more storage).
21. **Dungeon Tier 3** — 3 players. Mobs have 2x HP. Sentinels and Snipers appear.
22. **Red Zone deeper** — Supply Crates give Reclaimed loot. Locked Vaults need Vault Keys.
23. **Class level 5** — Shield Wall duration +2s. Taunt range +5 blocks.

### Late-Game (Tier 4-5 Dungeons + Bosses)

24. **Dungeon Tier 4** — 3 players. 3x mob HP. The Glitch King boss spawns.
25. **Craft Overclocked gear** — Diamond Sword, Diamond Armor from Corrupted Cores.
26. **Dungeon Tier 5** — 4 players. 4x mob HP. The Corrupted Core boss. Weekly modifiers active.
27. **Red Zone endgame** — Core Cache containers give Overclocked and Corrupted loot.
28. **Class level 10** — Ultimate ability unlocked. Fortress, Guardian Angel, Ghost Protocol, or Overload.
29. **Hideout maxed** — all stations upgraded, full crafting, 90-slot stash.
30. **Seasonal reset** — every 3 months, stash resets but class XP and hideout permanent.

---

## 7. Extraction Types

| Type | Timer | Noise | Requirement | Location |
|---|---|---|---|---|
| **Standard Extract** | 30s | Loud (zone-wide) | None | 3 per map |
| **Fast Extract** | 15s | Moderate | Requires Fast Extract Key (craftable) | 1 per map |
| **Silent Extract** | 10s | Silent | Requires Master Key (rare drop) | 1 per map (hidden) |

**Key design:** Standard is free but loud — attracts mobs and players. Fast is quicker but costs a key. Silent is the best but the key is extremely rare.

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
| Loot sale (vendor) | 5-500 per item | Any time |

### Shard Expense Sources

| Source | Cost | Notes |
|---|---|---|
| Class upgrade (1-5) | 50-200 per level | Cheap early, expensive later |
| Class upgrade (6-10) | 300-1000 per level | Endgame investment |
| Hideout stations | 50-4000 | One-time cost, permanent |
| Crafting materials | 10-500 per recipe | Consumable |
| Vault Key | 100 | Single use |
| Fast Extract Key | 75 | Single use |
| Class reset | 500 | Full reset |

### Balance Target

- **New player:** ~50 shards/hour from Tier 1 dungeons
- **Mid-game:** ~150 shards/hour from Tier 2-3 dungeons
- **Endgame:** ~400 shards/hour from Tier 4-5 dungeons + Red Zone
- **Full hideout upgrade cost:** ~15,000 shards total (100 hours at new player rate)
- **Full class upgrade cost:** ~5,000 shards per class (50 hours at new player rate)

---

## 9. Anti-Grief / Fair Play

| Rule | Implementation |
|---|---|
| No spawn camping | 30-second invulnerability on entry points |
| No team killing | Friendly fire disabled in all zones |
| No item duplication | Per-player stash, items tracked by UUID |
| No AFK farming | Dungeons kick after 2 minutes of inactivity |
| No RMT | Shards are bound to account, not tradeable between players |
| No exploit abuse | Weekly audit logs, automatic detection |

---

## 10. Seasonal Content (Planned)

| Season | Theme | New Content |
|---|---|---|
| Season 1 | **The Awakening** | Base game, 4 classes, 5 mob tiers, hideout |
| Season 2 | **Frozen Corruption** | Ice biome dungeon, frost mobs, cold weather mechanic |
| Season 3 | **The Hive** | Underground dungeon, insectoid mobs, swarm mechanic |
| Season 4 | **Void Rift** | End-game dungeon, void mobs, reality-bending mechanics |

Each season: new dungeon tier, new mob types, new loot, seasonal leaderboard, stash reset.
