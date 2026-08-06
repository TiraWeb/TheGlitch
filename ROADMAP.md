# The Glitch — Build Roadmap

A non-Pay-to-Win (EULA-compliant) rogue-lite **extraction hybrid** Minecraft server.

**Target hardware:** Oracle Cloud Always Free — Ampere A1, 2 OCPUs / 12GB RAM, Ubuntu 24.04 (ARM64)
**Stack:** Purpur (Java 25 — required by Minecraft 26.x) · GeyserMC + Floodgate (Bedrock cross-play) · single instance, three zones via coordinate offsetting
**Capacity target:** ~10–20 players comfortable, ~25–30 with tuning

Check items off as they're completed. Each numbered topic is sized to roughly one working session — except the Phase 4 building block, which is flagged as bigger.

**Status as of 2026-08-03:** Phases 0-2 are implemented. Phase 3.1 is configured, but the live Bedrock join test is pending. Phase 4 mechanics are scripted; the default setup creates generated worlds, while imported map saves are external/live-only. Phase 5 has several installed or source-level foundations, but GlitchDungeons, GlitchItems, and GlitchShops are not runtime-verified. GlitchStash extraction/storage is implemented, with the repository Standard timer set to 30 seconds; generated live arena values still require verification. GlitchClasses has a working core but incomplete abilities and progression. Oraxen has 18 item definitions and assets. See [docs/STATUS.md](docs/STATUS.md) for the authoritative status. EssentialsX is incompatible with the current Minecraft target. The immediate priorities are dungeon repair, item/loot integration, death and extraction rules, physical world content, and end-to-end testing.

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
and config. The scripts are present, but live verification and terrain source
depend on the provisioning path. `setup-worlds.sh` creates generated worlds;
`scripts/setup-imported-worlds.sh` expects external uploaded saves. Physical
construction inside these worlds is a separate body of work, split out into its
own checklist below.

- [x] **4.1 Zone layout blueprint** — Concrete coordinate offsets, world borders per zone, teleport routing between zones. _Scripts and coordinates exist; terrain source is provisioning-dependent. See docs/ZONES.md._
- [x] **4.2 Hub City — mechanics** — WorldGuard lockdown, safe-zone flags, borders, gamerules, and spawn configuration are scripted. _Live verification depends on the chosen world provisioning path._
- [x] **4.3 Standard Glitch (PvE) — mechanics** — Keep-inventory, natural-spawn, border, and 8-slot instancing configuration are scripted. _Dungeon shells and runtime dungeon verification remain pending._
- [x] **4.4 The Red Zone (PvPvE) — mechanics** — Full-loot flags, borders, entry coordinates, and extraction coordinates are documented/scripted. _Terrain source and live configuration remain to be verified._

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
- [x] **5.6 Classes** — Vanguard (tank), Warden (support), Specter (stealth), Operator (tech). _Core GlitchClasses source exists with class selection, persistence, ability items, and several abilities. Designed traits, reset costs, upgrade behavior, and live testing remain incomplete._
- [x] **5.7 Scoreboard/HUD** — TAB (sidebar scoreboard: shards/zone/class, tab list header/footer) + PlaceholderAPI. _Done: plugins added to bootstrap.sh, TAB config seeded with Glitch-themed sidebar._
- [ ] **5.8 Extraction mechanic** — VelKoth (KOTH plugin in CAPTURE mode for extraction zones). _Repository config uses a 30s Standard timer. Generated `arenas.yml`, live arena values, and a full extraction test still need verification. Fast/Silent variants are not implemented. Wand fix: click the block at your feet, not the ground below._
- [x] **5.9 Extraction vault** — GlitchStash plugin (custom, built from source). _Core inventory save, YAML persistence, retrieval GUI, overflow preservation, and Multiverse teleport exist. Live extraction testing and remaining low-level cleanup are pending. EssentialsX is incompatible with MC 26.x; teleport uses Multiverse._

## Phase 5.4 — Custom Dungeon Plugin (TheGlitchDungeons)

_Authoritative development plan. See Phase 5.9 for extraction plugins._

_Current reality: source exists, but this phase is not complete. The current
configuration uses list-form mob entries while the wave code expects a
configuration section; dungeon extraction is not fully started or integrated
with GlitchStash. **Deferred by operator decision (2026-08-03)** — the dungeon
PvE world is not the current focus._

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
- [x] **5.9.2 GlitchClasses** — Class selection + abilities system. _Source core exists with four classes, selection GUI, ability items, persistence, and event listeners. Some traits/abilities, reset costs, and live verification remain pending._
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

- [x] **5.10.1 Item base + resource pack** — Oraxen v1.218.0 build/deploy path and resource-pack assets exist. _The jar is built on the server; deployment and live verification are not represented by this repository._
- [x] **5.10.2 Material + key items** — 5 materials (Rune Fragment, Aether Shard, Rift Crystal, Void Essence, Legendary Relic) + 4 keys (Cache/Vault/Rift/Fast Extract). _Oraxen configs, generated 16×16 textures, ESC-menu language override, and deployment script exist. Extended: 10 more item definitions (5 Unstable Rifts, Fast Extract Key, Healing Potion, Corrupted Heal, Rift Reveal Pack, Void Infusion) with sell-price lore — 18 custom items total. Live deployment must be verified on the server._
- [x] **5.10.3 Rarity tiers + stat-roll engine** — Common/Uncommon/Rare/Epic/Legendary; identify-outcome stat rolls (GlitchItems custom plugin). _Source V1 exists: gear generation, 3 archetypes, 4 armor pieces, stat ranges, stars, attributes, `/identify`, Resonance math, and Residual Glitch. Deployed and live-tested (2026-08-03): `/identify` works. Loot and mob integrations are still incomplete._
- [ ] **5.10.4 Unstable Rifts + Identifier NPC** — mob loot tables emit rifts; hub NPC stabilizes for a shard fee. _Mob drops wired (2026-08-03): all 4 drop tables now emit Unstable Rifts + materials via the Oraxen `oraxen` drop type (GlitchStalker/Phantom/Brute: common/uncommon ~8%, GlitchCore: rare guaranteed + epic 50% + legendary 15%). Needs live test. Identifier NPC still pending._
- [ ] **5.10.5 Resonance tags + gear rolls** — 5 frequencies (Aegis/Veil/Bloom/Ward/Hollow) on mobs + gear; weapons +25% damage vs matching mobs, armor +defense. _Mob side done in repo (2026-08-03): all 10 mobs carry ScoreboardTags (res_aegis/res_veil/res_bloom/res_ward/res_hollow). Gear side exists in GlitchItems (CombatListener reads res_* tags). Needs live damage test._
- [ ] **5.10.6 Residual Glitch** — greed stacks (max 8, +1 every **5 min** — big map, searching is the game, not camping), small loot luck +5%/stack, aggro/risk scaling, extraction payout ×(1+0.10×stacks), elite hunts at 5+ stacks. _V1 built in GlitchItems (timer, boss bar HUD, damage multiplier, payout API) + payout hooked into extraction (GlitchStash pays sell-value × (multiplier−1) on win, stacks clear). Remaining: loot-luck/elite-hunt integration with world population._
- [ ] **5.10.7 World population** — spawners, chests, regen emitting rifts from 5.10.4.
- [ ] **5.10.8 Resonance crafting** — recipes via Workbench (no RNG professions).
- [ ] **5.10.9 Rename pass** — GlitchClasses configs, MythicMobs drop tables, DeluxeMenus shop, docs to Arcane Ruins naming.

## Phase 5.11 — Core Gameplay Content

_In-world content that makes the game loops real. Design numbers live in
docs/GAME_DESIGN.md (mobs §2, loot §3, hideout §4, dungeon tiers §5, extraction
§7, economy §8, anti-grief §9) — renames per docs/ITEM_SYSTEM.md §9. GAME_DESIGN
still carries old techy names until 5.10.9._

- [ ] **5.11.1 Mob roster (10/10)** — GAME_DESIGN §2: 4 seeded (Stalker, Brute, Phantom, Core boss — 5.3). _Add:_ Glitch Wisp (Vex), Corrupted Crawler (Silverfish), Glitch Sentinel (Wither Skeleton), Glitch Sniper (enchanted Skeleton), Glitch Warden (Iron Golem), The Glitch King (Ender Dragon, 3-phase). Each gets a `Resonance:` tag per ITEM_SYSTEM §5 mob table. **Zone distribution locked:** glitch_red = T1 fodder everywhere, T2 mid zones, T3 elites guard POIs (Core 0,0, reliquaries, extract sites), T4 bosses = server events; glitch_pve = tier-scaled dungeon waves. _DONE in repo (2026-08-03): all 6 missing mobs added with per-tier drop tables + resonance ScoreboardTags (res_veil/res_hollow/res_ward/res_aegis/res_bloom); the 4 existing mobs also got their tags. Needs live test (`mm reload` + spawn checks). Spawners/zone placement still deferred with world population (5.10.7)._
- [ ] **5.11.2 Loot containers** — GAME_DESIGN §3 + ITEM_SYSTEM §9: Debris Pile (everywhere, free), Loot Cache (mid-tier, Cache Key), Vault (hard areas, Vault Key), Rift Vault (boss areas, Rift Key). Contents per rarity table; placed in-world by 5.10.7.
- [ ] **5.11.3 Material + shard drop tables** — replace COINS-only placeholders with GAME_DESIGN §3 per-mob-tier rates (renamed materials, e.g. Rune Fragment 100% 1-2 on Tier 1, Void Essence 5% on Tier 3, Legendary Relic 10% on bosses). _Done in repo (2026-08-03) for the 4 existing mobs: T2 tables (rune 100% 2-4, aether 10%, crystal 5%) + T4 boss table (rune/aether/crystal/void 100%, relic 10%). Vanilla placeholder drops (diamond/netherite/pearl/star) removed. Needs live test._
- [ ] **5.11.4 Starter kit** — GAME_DESIGN §6: leather armor, wooden sword, 3 bread, 5 Rune Fragments. _EssentialsX kits unusable on MC 26.x — mechanism TBD (small custom plugin or DeluxeMenus button)._
- [ ] **5.11.5 Extraction variants** — GAME_DESIGN §7: Standard (30s, free) / Fast (15s, Fast Extract Key) / Silent (10s, Rift Key). _DECIDED: Standard = 30s. _Repo VelKoth config set to 30s; payout hookup done (GlitchStash pays sell-value × (multiplier−1) on extract). Remaining: change the live per-arena capture time on the box (arenas.yml, generated in-game) + Fast/Silent arenas with key consumption (separate task)._
- [ ] **5.11.6 Anti-grief / fair play** — GAME_DESIGN §9: 30s invulnerability on Red Zone entry points, friendly fire off everywhere, 2-min AFK kick in dungeons, shards account-bound (Coins currently drop on death as items — verify against "not tradeable").
- [ ] **5.11.7 Economy balance pass** — GAME_DESIGN §8 income/expense targets (new ~50 shards/h, mid ~150, endgame ~400; ~15k shard hideout, ~5k class) — tune after 5.10.x + 5.11.x land.
- [ ] **5.11.8 Mob health bars (GlitchHealthBar)** — floating color-coded HP bar (██████░░░░ 32/80) above every mob's head. _V1 DONE + live-tested (2026-08-06): TextDisplay above each hostile mob (spawn-event attach + 2s rescan safety net), updates on hit, smooth 0.25s follow pass with HP-change-only text updates, scales with mob height, /ghb test|count|reload debug tools. MythicMobs ShowHealth (80/80 in nametag) also enabled as baseline._

## Phase 5.12 — Merchant NPCs & item economy (GlitchShops)

_Plan: docs/GLITCH_SHOPS_DESIGN.md; price table in docs/ITEM_SYSTEM.md §11.
Sell price < buy price; sell price on item lore, buy price only in the merchant
GUI. Currency: Glitch Shards (Coins/Vault)._

- [x] **5.12.1 Sellable roster + prices** — _The 18 item configs carry sell-price lore and the shop reads them from config._
- [x] **5.12.2 GlitchShops plugin** — _Deployed and live-tested (2026-08-03): `/shop` buy/sell verified. 54-slot bazaar GUI, buy/sell toggle, five categories, gear restock, super-rare roll, and Vault economy hook._
- [ ] **5.12.3 Hub merchant NPCs** — _NPC listener exists, but the Grand Bazaar NPC must be placed and tested in-game._
- [ ] **5.12.4 Gear vendors** — Armourer/Weaponsmith NPCs: fixed base price + small random variance on rolls each restock; every weapon has a 0.01% super-rare max-roll variant in stock (ITEM_SYSTEM §11). _V1 built: gear tab in bazaar (3 weapon + 2 armor slots, restock every 10 min, super-rare roll)._
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
