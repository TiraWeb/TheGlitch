# The Glitch — Build Roadmap

A non-Pay-to-Win (EULA-compliant) rogue-lite **extraction hybrid** Minecraft server.

**Target hardware:** Oracle Cloud Always Free — Ampere A1, 2 OCPUs / 12GB RAM, Ubuntu 24.04 (ARM64)
**Stack:** Purpur (Java 25 — required by Minecraft 26.x) · GeyserMC + Floodgate (Bedrock cross-play) · single instance, three zones via coordinate offsetting
**Capacity target:** ~10–20 players comfortable, ~25–30 with tuning

Check items off as they're completed. Each numbered topic is sized to roughly one working session — except the Phase 4 building block, which is flagged as bigger.

**Status as of 2026-08-01:** Phases 0–2 done. Phase 3.1 done (Bedrock test pending). Phase 4 mechanics done — all 3 worlds are now **custom imported maps** (hub=TerraSpace, glitch_red=Odyssey 2k, glitch_pve=CaveFree), not generated worlds. Phase 5.1-5.3, 5.5, 5.6-5.9 done (plugins installed, GlitchStash + GlitchClasses + GlitchDungeons built). Phase 5.4 designed (see Phase 5.9). Extraction loop fully working (VelKoth → GlitchStash → Multiverse teleport). Class system fully working (4 classes, ability items, 10 levels). GlitchDungeons plugin built from source (21 files, party system, wave spawning, boss bar, rewards). **Phase 5.10 (Arcane Ruins item system) started:** Oraxen built from source (paid jars avoided — see setup-oraxen.sh), 18 custom items (5 materials, 4 keys, 5 Unstable Rifts, 4 alchemy) with sell-price lore + textures deployed and verified in-game; design doc in docs/ITEM_SYSTEM.md. **Phase 5.11 (core gameplay content) now tracked** — design numbers in docs/GAME_DESIGN.md. **Phase 5.12 (merchant NPCs / GlitchShops) planned** — prices in ITEM_SYSTEM §11, plugin design in docs/GLITCH_SHOPS_DESIGN.md. **Design decisions locked (2026-08-02):** gear = 3 weapon archetypes + 4 armor pieces with attributes; death in glitch_red keeps only leggings+boots (Phase 5.13); Standard extract = 30s; merchant stock has small variance + 0.01% super-rare weapon variant; mob zone distribution fixed; **simplicity pass — rarities are now Common/Uncommon/Rare/Epic/Legendary and keys are Cache/Vault/Rift (no Fragmented/Primordial jargon); Residual Glitch = +1 stack per 5 min with small staying bonus (search-first, no camping rewards)**. EssentialsX INCOMPATIBLE with MC 26.x. Next: item-system steps 3–10 (stat-roll engine + gear line, rifts, Resonance, Residual Glitch, world population, crafting, rename pass, death rules), then physical builds or in-game testing.

---

## Phase 0 — Secure the box

- [x] **0.1 Networking & firewall** — Open `25565/TCP` (Java) and `19132/UDP` (Bedrock) in the OCI VCN Security List **and** the on-box firewall (Ubuntu image ships with restrictive iptables). SSH hardening: key-only auth, fail2ban.
- [x] **0.2 OS preparation** — System updates, dedicated unprivileged `minecraft` user, 4GB swapfile (OOM insurance; Oracle images ship with none), timezone.

## Phase 1 — Server core online

- [x] **1.1 Java runtime** — OpenJDK 25 (ARM64) from Ubuntu 24.04 repos (Minecraft 26.x raised the requirement from Java 21).
- [x] **1.2 Purpur installation** — Latest stable Purpur jar, EULA acceptance, directory layout, `start.sh` with Aikar's flags (**8GB heap** — leaves ~4GB for JVM off-heap + Geyser + OS), systemd unit for boot persistence and clean restarts.
- [x] **1.3 First boot** — Minimal `server.properties`, whitelist on, first vanilla login test.

## Phase 2 — Performance tuning

- [x] **2.1 Config pass** — `server.properties`, `bukkit.yml`, `spigot.yml`, `paper-global.yml`, `paper-world-defaults.yml`, `purpur.yml` tuned for 2-core ARM: view distance ~5, simulation distance ~3–4, entity ticking/activation ranges, pathfinding throttles, network compression threshold for mobile clients.
- [x] **2.2 World pre-generation** — World borders per zone + full pre-gen with Chunky (mandatory on 2 cores; terrain gen mid-game would tank TPS). _Red Zone: 17,689 chunks pre-generated (on original seed-generated world; now replaced by Odyssey 2k map import — no pre-gen needed on imported maps)._
- [x] **2.3 Monitoring baseline** — spark profiler installed, baseline TPS/MSPT recorded so every later phase can be measured against it. _Idle baseline: 20 TPS, 0.6ms median MSPT, ~1% CPU, 3.6GB heap — see docs/PERFORMANCE.md._

## Phase 3 — Bedrock cross-play

- [x] **3.1 GeyserMC + Floodgate** — Install/config, UDP 19132 verified, Floodgate key linkage, username prefix policy. _Installed + configured (new 2.9+ config structure); loads clean. Join-test from a real Bedrock client deferred (user's call)._
- [ ] **3.2 Bedrock UX tuning** — Combat/cooldown translation settings, emulated off-hand behavior, forms support check, join test from a Bedrock client (phone/console). _Cooldown-type=crosshair configured; live verification pending._

## Phase 4 — World architecture (the three zones)

Everything below is *mechanics* — worlds, gamerules, protection flags, borders,
config. All scriptable, all done and verified live. **All three worlds are
custom imported maps** — not vanilla generated terrain. Hub uses **TerraSpace**
(Japanese cyberpunk city build), glitch_pve uses **CaveFree** (cave/underground
map), glitch_red uses **MMORPG_Odyssey** 2k custom terrain. Physical
construction inside these worlds is a separate body of work, split out into
its own checklist below.

- [x] **4.1 Zone layout blueprint** — Concrete coordinate offsets, world borders per zone, teleport routing between zones. _Three custom imported worlds (hub=TerraSpace, glitch_red=Odyssey 2k, glitch_pve=CaveFree); see docs/ZONES.md._
- [x] **4.2 Hub City — mechanics** — WorldGuard total lockdown (PvP/hunger/block-changes off, invincible on, explosion/mob-damage denied, hostile deny-spawn), `spawn_mobs false` / `keep_inventory true`, spawn set to 0,-60,0. _Verified live: worlds registered via `mv import`, correct MC 26.x gamerule names (see docs/ZONES.md)._
- [x] **4.3 Standard Glitch (PvE) — mechanics** — World registered, `keep_inventory true`, natural spawns off (MythicMobs-only design), 8-slot dungeon instancing blueprint. _Verified live._
- [x] **4.4 The Red Zone (PvPvE) — mechanics** — World registered, full-loot PvP flags, 6 entry coordinates + 3 extraction sites documented. World uses **MMORPG_Odyssey 2k** custom imported map (not seed-generated terrain). _Verified live._

### Physical world building — DEFERRED

Physical builds (Sakura Spawn hub, dungeon shells, Red Zone POIs) are deferred.
The world mechanics (gamerules, flags, borders, pre-gen) are all scripted and
work on a fresh install. Physical builds require in-game WorldEdit and are
documented in `docs/DUNGEON_SHELL.md` for when the operator is ready.

- [ ] **4.5 Hub City build** — the actual city: spawn plaza, shop stalls, class-selector area, cosmetic look and feel. _Deferred: requires in-game WorldEdit paste. See docs/DUNGEON_SHELL.md._
- [ ] **4.6 Dungeon room builds (glitch_pve)** — First dungeon shell "The Echoing Vault" at Slot 1 (-1024, -1024). _Deferred: build scripts exist but require in-game execution. See docs/DUNGEON_SHELL.md._
- [ ] **4.7 Red Zone points of interest** — physical structures at the Core (0,0 — Tier 4/5 loot), the 6 entry points, and the 3 extraction beacon sites. Currently just coordinates on paper (docs/ZONES.md), nothing built.

## Phase 5 — Core plugin stack

- [x] **5.1 Foundation plugins** — LuckPerms (groups/tracks, `setup-luckperms.sh`), VaultUnlocked (modern Vault fork, auto-detects LuckPerms). _Done: plugins added to bootstrap.sh, config seeded, setup script created. Run `sudo ./setup-luckperms.sh` after first restart with LuckPerms loaded._
- [x] **5.2 Glitch Shards economy** — Run-currency via Eli's Coins (Echo Shard items, enchanted glow). Disabled in hub, active in glitch_pve/glitch_red. Drop-on-death enabled, MythicMobs handles loot tables via `coins` drop type. _Done: plugin added to bootstrap.sh, config seeded with Glitch Shard naming._
- [x] **5.3 MythicMobs** — Custom mobs with Glitch Shards loot. _Done: plugin added to bootstrap.sh, 4 mob definitions (Glitch Stalker, Brute, Phantom, Core boss) with drop tables using COINS type. Configs seeded once._
- [ ] **5.4 Dungeon/Party management** — _Deferred to custom plugin. Development plan documented in Phase 5.9._
- [x] **5.5 Hub NPCs** — FancyNpcs (packet-based, 0 TPS impact) + DeluxeMenus for GUIs. _Done: plugins added to bootstrap.sh, class selector + shard shop GUIs seeded._
- [x] **5.6 Classes** — Vanguard (tank), Warden (support), Specter (stealth), Operator (tech). _Done: GlitchClasses plugin built from source (replaces premium plugin). 4 classes with prime/tactical abilities, 10 upgrade levels, passive traits, class selection GUI. Ability items (immovable, no-duplicate) auto-given on class select and when entering game worlds. YAML per-player storage, LuckPerms integration._
- [x] **5.7 Scoreboard/HUD** — TAB (sidebar scoreboard: shards/zone/class, tab list header/footer) + PlaceholderAPI. _Done: plugins added to bootstrap.sh, TAB config seeded with Glitch-themed sidebar._
- [x] **5.8 Extraction mechanic** — VelKoth (KOTH plugin in CAPTURE mode for extraction zones). _Done: plugin added to bootstrap.sh, extraction arenas (X1/X2/X3) in glitch_red with 300s hold-to-extract. Wand fix: click block at your feet, not ground below._
- [x] **5.9 Extraction vault** — GlitchStash plugin (custom, built from source). _Done: auto-saves inventory on extraction (accumulates across multiple extractions), auto-teleports to hub via Multiverse-Core mv tp, /stash retrieves items. YAML per-player storage. EssentialsX INCOMPATIBLE with MC 26.x — teleport uses mv tp instead._

## Phase 5.4 — Custom Dungeon Plugin (TheGlitchDungeons)

_Authoritative development plan. See Phase 5.9 for extraction plugins._

- [ ] **5.4.1** Project setup — Maven/Gradle, Paper API + MythicMobs API dependencies
- [ ] **5.4.2** Party system — create/invite/accept/leave/disband, max 4 players
- [ ] **5.4.3** Slot management — 8-slot grid tracking (available/occupied/cooldown)
- [ ] **5.4.4** Dungeon start — assign party to free slot, teleport, start timer
- [ ] **5.4.5** Mob spawning — MythicMobs API integration, wave progression
- [ ] **5.4.6** Timer + win/lose — countdown, auto-fail on expiry, completion rewards
- [ ] **5.4.7** Extraction — region-based channeling, shard banking
- [ ] **5.4.8** Death handling — lives system, respawn at staging
- [ ] **5.4.9** Rewards — shard banking via Vault API, bonus item drops
- [ ] **5.4.10** Polish — messages, sounds, boss bar, action bar progress

## Phase 5.9 — Custom Extraction Plugins

_Seven custom plugins designed for Arc Raiders/Marathon-style extraction gameplay. All Java/Paper API, no premium dependencies._

- [x] **5.9.1 GlitchStash** — Grid-based stash inventory UI. Persistent server-side storage, risk/reward visualization, item provenance tracking. _Done: built from source, YAML storage, /stash GUI, auto-save on extraction (accumulates), teleport via mv tp (EssentialsX broken on MC 26.x)._
- [x] **5.9.2 GlitchClasses** — Class selection + abilities system. _Done: built from source, 4 classes (Vanguard/Warden/Specter/Operator), class selection GUI, prime + tactical ability items (immovable, no-duplicate), 10 upgrade levels, passive traits via event listeners. Items auto-given on class select, on join, and on entering game worlds. /class kit to re-receive. YAML per-player storage, LuckPerms meta integration._
- [ ] **5.9.3 GlitchRaid** — Raid lifecycle manager. Timers, party assignment, post-raid summary screen, death recap, loot accounting.
- [ ] **5.9.4 GlitchInsurance** — Shard-backed item insurance. Pay premium to protect gear on death, cooldowns, claim window.
- [ ] **5.9.5 GlitchHideout** — Between-raid progression. Physical hideout in hub, upgradeable crafting stations, skill trees, stash expansion.
- [ ] **5.9.6 GlitchEvents** — Dynamic world events. Server-wide broadcasts, timed extraction windows, roaming bosses, supply drops.
- [ ] **5.9.7 GlitchLoot** — Smart loot system. Adaptive drop rates, contextual loot, item power budget, anti-funneling.

## Phase 5.10 — Item System (Arcane Ruins)

_Authoritative design: docs/ITEM_SYSTEM.md. Implementation order follows its
§10 list. Custom items run on Oraxen — both Nexo and the prebuilt Oraxen jar
are paid (~$20–22), but Oraxen's GitHub source carries a personal-use license
(no redistribution), so we build our own via setup-oraxen.sh and never commit
the jar. No item levels: power comes from rarity tiers + random stat rolls +
Resonance matching, not number inflation._

- [x] **5.10.1 Item base + resource pack** — Oraxen v1.218.0 built from source. _Done: setup-oraxen.sh (clone, patch Iris JitPack dep, Gradle build with JDK 21+25 toolchains), deployed to server/plugins/Oraxen.jar, resource pack auto-hosts via atlas.oraxen.com._
- [x] **5.10.2 Material + key items** — 5 materials (Rune Fragment, Aether Shard, Rift Crystal, Void Essence, Legendary Relic) + 4 keys (Cache/Vault/Rift/Fast Extract). _Done: Oraxen configs in server/plugins/Oraxen/items/, programmatic 16×16 textures, ESC-menu language override (pack name "The Glitch"), deployed via setup-oraxen-items.sh, verified in-game. Extended: 10 more items (5 Unstable Rifts, Fast Extract Key, Healing Potion, Corrupted Heal, Rift Reveal Pack, Void Infusion) with sell-price lore — 18 custom items total._
- [ ] **5.10.3 Rarity tiers + stat-roll engine** — Common/Uncommon/Rare/Epic/Legendary; identify-outcome stat rolls (GlitchItems custom plugin). _Design locked: 3 weapon archetypes (Blade/Greatblade/Arcane Staff) with attributes from Rare up; armor = base stats by rarity + 1 attribute (ITEM_SYSTEM §2). Simple-name pass done: rarities + keys renamed to plain words (no Fragmented/Primordial etc.)._
- [ ] **5.10.4 Unstable Rifts + Identifier NPC** — mob loot tables emit rifts; hub NPC stabilizes for a shard fee.
- [ ] **5.10.5 Resonance tags + gear rolls** — 5 frequencies (Aegis/Veil/Bloom/Ward/Hollow) on mobs + gear; weapons +25% damage vs matching mobs, armor +defense.
- [ ] **5.10.6 Residual Glitch** — greed stacks (max 8, +1 every **5 min** — big map, searching is the game, not camping), small loot luck +5%/stack, aggro/risk scaling, extraction payout ×(1+0.10×stacks), elite hunts at 5+ stacks.
- [ ] **5.10.7 World population** — spawners, chests, regen emitting rifts from 5.10.4.
- [ ] **5.10.8 Resonance crafting** — recipes via Workbench (no RNG professions).
- [ ] **5.10.9 Rename pass** — GlitchClasses configs, MythicMobs drop tables, DeluxeMenus shop, docs to Arcane Ruins naming.

## Phase 5.11 — Core Gameplay Content

_In-world content that makes the game loops real. Design numbers live in
docs/GAME_DESIGN.md (mobs §2, loot §3, hideout §4, dungeon tiers §5, extraction
§7, economy §8, anti-grief §9) — renames per docs/ITEM_SYSTEM.md §9. GAME_DESIGN
still carries old techy names until 5.10.9._

- [ ] **5.11.1 Mob roster (10/10)** — GAME_DESIGN §2: 4 seeded (Stalker, Brute, Phantom, Core boss — 5.3). _Add:_ Glitch Wisp (Vex), Corrupted Crawler (Silverfish), Glitch Sentinel (Wither Skeleton), Glitch Sniper (enchanted Skeleton), Glitch Warden (Iron Golem), The Glitch King (Ender Dragon, 3-phase). Each gets a `Resonance:` tag per ITEM_SYSTEM §5 mob table. **Zone distribution locked:** glitch_red = T1 fodder everywhere, T2 mid zones, T3 elites guard POIs (Core 0,0, reliquaries, extract sites), T4 bosses = server events; glitch_pve = tier-scaled dungeon waves.
- [ ] **5.11.2 Loot containers** — GAME_DESIGN §3 + ITEM_SYSTEM §9: Debris Pile (everywhere, free), Loot Cache (mid-tier, Cache Key), Vault (hard areas, Vault Key), Rift Vault (boss areas, Rift Key). Contents per rarity table; placed in-world by 5.10.7.
- [ ] **5.11.3 Material + shard drop tables** — replace COINS-only placeholders with GAME_DESIGN §3 per-mob-tier rates (renamed materials, e.g. Rune Fragment 100% 1-2 on Tier 1, Void Essence 5% on Tier 3, Legendary Relic 10% on bosses).
- [ ] **5.11.4 Starter kit** — GAME_DESIGN §6: leather armor, wooden sword, 3 bread, 5 Rune Fragments. _EssentialsX kits unusable on MC 26.x — mechanism TBD (small custom plugin or DeluxeMenus button)._
- [ ] **5.11.5 Extraction variants** — GAME_DESIGN §7: Standard (30s, free) / Fast (15s, Fast Extract Key) / Silent (10s, Rift Key). _DECIDED: Standard = 30s — change VelKoth hold from 300s test value on deploy; keys are the speed-up, not the wall._
- [ ] **5.11.6 Anti-grief / fair play** — GAME_DESIGN §9: 30s invulnerability on Red Zone entry points, friendly fire off everywhere, 2-min AFK kick in dungeons, shards account-bound (Coins currently drop on death as items — verify against "not tradeable").
- [ ] **5.11.7 Economy balance pass** — GAME_DESIGN §8 income/expense targets (new ~50 shards/h, mid ~150, endgame ~400; ~15k shard hideout, ~5k class) — tune after 5.10.x + 5.11.x land.

## Phase 5.12 — Merchant NPCs & item economy (GlitchShops)

_Plan: docs/GLITCH_SHOPS_DESIGN.md; price table in docs/ITEM_SYSTEM.md §11.
Sell price < buy price; sell price on item lore, buy price only in the merchant
GUI. Currency: Glitch Shards (Coins/Vault)._

- [ ] **5.12.1 Sellable roster + prices** — _Item configs done: all 18 custom items carry a `Sell price: N Shards` lore line per the §11 table. Plugin pending._
- [ ] **5.12.2 GlitchShops plugin** — buy/sell GUI (Sell tab = inventory, Buy tab = stock), prices from config only, Vault/Coins deposit+withdraw, Oraxen item-id resolution.
- [ ] **5.12.3 Hub merchant NPCs** — FancyNpcs × 4 (Materials, Keys, Alchemy, Rifts), right-click opens the shop; every merchant buys any custom item.
- [ ] **5.12.4 Gear vendors** — Armourer/Weaponsmith NPCs: fixed base price + small random variance on rolls each restock; every weapon has a 0.01% super-rare max-roll variant in stock (ITEM_SYSTEM §11).
- [ ] **5.12.5 Economy sanity pass** — verify prices against GAME_DESIGN §8 income targets once loot tables (5.11.3) + rifts (5.10.4) land.

## Phase 5.13 — Death Rules (risk tuning)

_DECIDED (ITEM_SYSTEM §12): glitch_red full loot with one mercy rule — on death the player
keeps leggings + boots; helmet, chestplate, weapons, inventory, and shards drop. glitch_pve
stays keep-inventory (training floor)._

- [ ] **5.13.1 Leggings+boots keep plugin** — small custom plugin (or fold into GlitchLoot/GlitchShops): on death in glitch_red, remove leggings+boots from the drop and re-give to player; everything else drops normally.
- [ ] **5.13.2 Shards-on-death verification** — resolve the "bound vs drop-as-item" conflict (GAME_DESIGN §9 / ROADMAP 5.11.6) so shards behave per design.
- [ ] **5.13.3 Tuning pass** — verify the mercy rule doesn't make full-loot too soft (data from early playtests).

## Phase 6 — Game loops

- [ ] **6.1 Dungeon objectives** — Wave-clear and data-core-repair objectives, tier scaling, completion rewards. _Needs 4.6 (a built dungeon room) to actually place objectives in._
- [ ] **6.2 Extraction beacons** — Timed channel mechanic via VelKoth, server-wide/zone broadcast on activation, GlitchStash auto-saves inventory on completion, teleport to hub via Multiverse-Core.
- [ ] **6.3 Gear-score gating** — Item-attribute scoring on Red Zone entry, distribution across rotating drop points to prevent spawn-camping.
- [ ] **6.4 Progression sinks** — Hub skill-point shop via DeluxeMenus: shards → permanent class upgrades (+HP %, cooldown reduction), costs curve.

## Phase 7 — Monetization (EULA-safe, Hypixel model)

- [ ] **7.1 Permission architecture** — LuckPerms ranks/tracks for all purchasables; nothing gameplay-power gated.
- [ ] **7.2 Cosmetics** — Geyser-compatible Java resource pack (weapon skins via custom model data), chat tags, particle trails.
- [ ] **7.3 Store + boosters** — Tebex (free tier) integration; "Glitch Surge" global 2x shard booster (1 hour) with activator announcement.
- [ ] **7.4 Quality of life** — Premium loadout slots in Hub, priority queue for full-capacity periods.

## Phase 8 — Operations & launch

- [ ] **8.1 Backups & restarts** — Automated world backups to OCI Object Storage (free tier), scheduled daily restart, log rotation.
- [ ] **8.2 Protection & moderation** — Anti-cheat, anti-grief/rollback (CoreProtect), moderation commands and staff permissions.
- [ ] **8.3 Launch** — Pre-launch checklist, load test, soft launch with whitelist, then open.

---

## Working model

Each phase is developed in this repo (scripts + config files in their real directory layout), then pulled and executed on the instance:

```bash
git pull && sudo bash <phase-script>.sh
```

The server operator is the only keyholder; this repo is the single source of truth for every config, so the instance can be rebuilt from scratch at any time.
