# The Glitch — Build Roadmap

A non-Pay-to-Win (EULA-compliant) rogue-lite **extraction hybrid** Minecraft server.

**Target hardware:** Oracle Cloud Always Free — Ampere A1, 2 OCPUs / 12GB RAM, Ubuntu 24.04 (ARM64)
**Stack:** Purpur (Java 25 — required by Minecraft 26.x) · GeyserMC + Floodgate (Bedrock cross-play) · single instance, three zones via coordinate offsetting
**Capacity target:** ~10–20 players comfortable, ~25–30 with tuning

Check items off as they're completed. Each numbered topic is sized to roughly one working session — except the Phase 4 building block, which is flagged as bigger.

**Status as of 2026-09-01:** Phases 0-2 implemented; 3.1 configured (Bedrock join pending); Phase 4 mechanics scripted (generated vs imported saves external). **Phase 5.9 complete in source** — all seven extraction plugins + **Phase 5.7/5.8 complete**: **GlitchHUD** (per-world sidebar `NumberFormat.blank`, `BELOW_NAME` stacks, `NOTCHED_10` residual bar, TAB takeover) and **dynamic extraction** (`DynamicExtractionManager` + `SpotPicker` + `WaypointBridge`/`ExtractionMarkers` + `AutoExtractScheduler` 30m cycle, 3 validated random `extraction_dyn*` arenas, universal keys, force-loaded chunks) are built via the **14-module** Maven reactor and deployed live. All **12** deployable plugins + shared `GlitchCommon` load clean (`2026-09-01` `BUILD SUCCESS` — `f312262` GlitchHUD, `c1634b3`/`9d8f05a` dynamic extraction + fixes, `c9a229e` ping/TPS + `ByteTag` PDC crash fix — live cycle `3/3 at (670,299),(69,569),(299,942)`). In-game testing per [docs/TESTING.md](docs/TESTING.md) remains for newer plugins; `GlitchItems`/`GlitchShops`/`GlitchHealthBar` have full live tests, GlitchHUD visuals + dynamic capture/particles verified in-game. `GlitchDungeons` deferred. Oraxen 18 items + UI glyphs (`E040-E049` + `negative_space.json`). Root `pom.xml` resolves `me.clip:placeholderapi:2.12.3` via `https://repo.extendedclip.com/content/repositories/placeholderapi/`. Coins account-bound (`player-drop:false` …). EssentialsX incompatible. See [docs/STATUS.md](docs/STATUS.md).

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
- [ ] **4.7 Red Zone points of interest** — physical structures at the Core (0,0 — Tier 4/5 loot), the 6 entry points, and the extraction sites. Entry coordinates documented (docs/ZONES.md); extraction sites are operator-placed in-game (VelKoth arenas).

## Phase 5 — Core plugin stack

- [x] **5.1 Foundation plugins** — LuckPerms (groups/tracks, `setup-luckperms.sh`), VaultUnlocked (modern Vault fork, auto-detects LuckPerms). _Done: plugins added to bootstrap.sh, config seeded, setup script created. Run `sudo ./setup-luckperms.sh` after first restart with LuckPerms loaded._
- [x] **5.2 Glitch Shards economy** — Run-currency via Eli's Coins (Echo Shard items, enchanted glow). Disabled in hub, active in glitch_pve/glitch_red. **Account-bound** (`player-drop:false` `lose-on-death:false` `drop-on-death:false` in `server/plugins/Coins/config.yml:85,114` — shards do NOT drop on death; live reloaded 2026-08-23 via `coins reload`), MythicMobs handles loot tables via `coins` drop type. _Done: plugin added to bootstrap.sh, config seeded with Glitch Shard naming._
- [x] **5.3 MythicMobs** — Custom mobs with Glitch Shards loot. _Done: plugin added to bootstrap.sh, 4 mob definitions (Glitch Stalker, Brute, Phantom, Core boss) with drop tables using COINS type. Configs seeded once._
- [ ] **5.4 Dungeon/Party management** — _Deferred to custom plugin. Development plan documented in Phase 5.9._
- [x] **5.5 Hub NPCs** — FancyNpcs (packet-based, 0 TPS impact) + DeluxeMenus for GUIs. _Done: plugins added to bootstrap.sh, class selector + shard shop GUIs seeded._
- [x] **5.6 Classes** — Vanguard (tank), Warden (support), Specter (stealth), Operator (tech). _Core GlitchClasses source exists with class selection, persistence, and keybind-activated abilities (F prime / Sneak+F tactical / Sneak+Q ultimate — ability items removed). Abilities completed in repo (2026-08-10): all four ultimates (Fortress, Guardian Angel, Ghost Protocol, Cataclysm), missing traits (Vigilance, Scavenge via specter_scavenge tag → GlitchItems containers, Resonance Surge, Engineer turret repair), EMP impact, cloak break, Ironclad knockback fix, Revive Beacon healing, real reset costs (GUI + /class reset). Live verification remains pending._
- [x] **5.7 Scoreboard/HUD** — **GlitchHUD** owns the sidebar (`NumberFormat.blank` no red numbers, per-world layouts hub/pve/red, `◆ EXTRACTION ◆` pulse, rune `E049`/divider `E048`/stars `E040-E043`, `StashCycleProbe` next-cycle, `BelowNameManager` `BELOW_NAME` stacks, `ResidualGlitchManager` `NOTCHED_10` bar) + **PlaceholderResolver** ping/TPS fallback (`Player.getPing()`/`Bukkit.getTPS()` over PAPI) + TAB header/footer+teams only (`scoreboard.enabled: false` synced by `build-all.sh`) + Oraxen `negative_space.json`. _Deployed 2026-09-01 `f312262`+`c9a229e`+`9d8f05a` (divider trimmed, ping/TPS fixed, `ByteTag` PDC crash fixed)._ + PlaceholderAPI `2.12.3`.
- [x] **5.8 Extraction mechanic** — VelKoth CAPTURE arenas. _Standard 30s timer. **Dynamic extraction (2026-09-01 `c1634b3`+`9d8f05a`+`a0edffa`):** `AutoExtractScheduler` 31m cycle (`36000` ticks open + `5s` scatter) fires `DynamicExtractionManager` → `SpotPicker` 250 tries / 12-deep scan / 9-point flatness tol 2 / `isOccluding` 2-deep solid / barrier/bedrock/shulker rejection / WorldGuard / 30-block separation → 3 `extraction_dyn*` arenas via `ArenaManager.addArena/saveArenas` + `CuboidRegion p.y()-1..p.y()+4` (6 high) + force-loaded chunks + `WaypointBridge` locator-bar beacons + `ExtractionMarkers` ring particles. Live verified `Cycle #1 3/3`. Fast/Silent variants (GlitchStash, 2026-08-06) key-requiring + arming + bonus still live (`/extractadmin`). Manual wand fix: click block at feet._
- [x] **5.9 Extraction vault** — GlitchStash plugin (custom, built from source). _Core inventory save, YAML persistence, retrieval GUI, overflow preservation, and Multiverse teleport exist. Dynamic + variant extraction integrated; container-key `ByteTag` crash fixed (`c9a229e` — `pdc.has` guard on all `OraxenUtil`/`HideoutManager`/`ShopManager`/`IdentifyManager`/`ExtractionVariantManager`); `scatter center (1000,1000) r 1000` corrected for `MMORPG_Odyssey [0..2000]²` land (`cd74932`). EssentialsX incompatible; teleport uses Multiverse._

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
- [x] **5.9.2 GlitchClasses** — Class selection + abilities system. _Source core exists with four classes, selection GUI, keybind-activated abilities (F prime / Sneak+F tactical / Sneak+Q ultimate — ability items removed), persistence, and event listeners. Abilities + ultimates + reset costs completed in repo (2026-08-10); live verification remains pending._
- [x] **5.9.3 GlitchRaid** — Raid lifecycle manager. Timers, party assignment, post-raid summary screen, death recap, loot accounting. _DONE in repo (2026-08-21): `/raid start|end|status` + `/raidadmin reload|list|end`, BossBar timer (default 1800s), party leader/members (max 4), loot/death accounting with end-of-raid summary, auto-timeout, PlaceholderAPI `%glitchraid_*%` expansion, no hard deps. Built via reactor and deployed; loads clean on live (2026-08-21). In-game playtest pending._
- [x] **5.9.4 GlitchInsurance** — Shard-backed item insurance. Pay premium to protect gear on death, cooldowns, claim window. _DONE in repo (2026-08-21): premium 100 shards/item, max 3 policies, 300s claim window after death, 60s cooldown, Vault withdraw, per-player Base64 YAML persistence (async atomic saves), PlayerDeathEvent moves insured items to keep-slot. Built via reactor (auto-seeds `lib/VaultUnlocked.jar`) and deployed; loads clean on live (2026-08-21). In-game playtest pending._
- [x] **5.9.5 GlitchHideout** — Between-raid progression. Physical hideout in hub, upgradeable crafting stations, skill trees, stash expansion. _DONE in repo (2026-08-10): GlitchHideout plugin — 7 stations (Arcane Core, Workbench, Med Station, Stash, Intel Center, Skill Trainer, Armory) with shard costs + prerequisites per GAME_DESIGN §4, per-player YAML persistence, workbench crafting (ITEM_SYSTEM §7 recipes via /o give + /glitchitems give), extended stash (27/45/54) + armory storage with auto-sort, med heal, intel hostile-glow. Physical hideout building in hub remains in-game work._
- [x] **5.9.6 GlitchEvents** — Dynamic world events. Server-wide broadcasts, timed extraction windows, roaming bosses, supply drops. _DONE in repo (2026-08-22): auto-scheduler fires a random event every 20–45 min in enabled worlds; supply drops place a filled BARREL near a random player with coordinate broadcast + timed removal; roaming bosses spawn via MythicMobs console command (`mm mobs spawn`, warn-once if absent); `/glitchevents start supply_drop|start roaming_boss|stop|reload|status` (`glitchevents.admin`). Extraction windows are stubbed/disabled by default. Deployed; loads clean on live (2026-08-22). In-game playtest pending._
- [x] **5.9.7 GlitchLoot** — Smart loot system. Adaptive drop rates, contextual loot, item power budget, anti-funneling. _DONE in repo (2026-08-22): adaptive dry-streak bonus (+2%/roll, max 25%, 50% decay on loot), hourly power budget (rare 20 / epic 60 / legendary 150 of 400/h), per-player anti-funnel cooldown (120s); EntityDeathEvent bonus drops for monster kills by players (weighted rare/epic/legendary → named EMERALD/AMETHYST_SHARD/DIAMOND), fully guarded so it can never break death drops; `/glitchloot status|reload` (`glitchloot.admin`). Deployed; loads clean on live (2026-08-22). In-game playtest pending._

## Phase 5.10 — Item System (Arcane Ruins)

_Authoritative design: docs/ITEM_SYSTEM.md. Implementation order follows its
§10 list. Custom items run on Oraxen — both Nexo and the prebuilt Oraxen jar
are paid (~$20–22), but Oraxen's GitHub source carries a personal-use license
(no redistribution), so we build our own via setup-oraxen.sh and never commit
the jar. No item levels: power comes from rarity tiers + random stat rolls +
Resonance matching, not number inflation._

- [x] **5.10.1 Item base + resource pack** — Oraxen v1.218.0 build/deploy path and resource-pack assets exist. _The jar is built on the server; deployment and live verification are not represented by this repository._
- [x] **5.10.2 Material + key items** — 5 materials (Rune Fragment, Aether Shard, Rift Crystal, Void Essence, Legendary Relic) + 4 keys (Cache/Vault/Rift/Fast Extract). _Oraxen configs, generated 16×16 textures, ESC-menu language override, and deployment script exist. Extended: 10 more item definitions (5 Unstable Rifts, Fast Extract Key, Healing Potion, Corrupted Heal, Rift Reveal Pack, Void Infusion) with sell-price lore — 18 custom items total. Live deployment must be verified on the server._
- [x] **5.10.3 Rarity tiers + stat-roll engine** — Common/Uncommon/Rare/Epic/Legendary; identify-outcome stat rolls (GlitchItems custom plugin). _Source V1 exists: gear generation, 3 archetypes, 4 armor pieces, stat ranges, stars, attributes, `/identify`, Resonance math, and Residual Glitch. Deployed and live-tested (2026-08-03): `/identify` works. Mob integration complete (tags + drops, 2026-08-03); container integration complete (2026-08-10)._
- [ ] **5.10.4 Unstable Rifts + Identifier NPC** — mob loot tables emit rifts; hub NPC stabilizes for a shard fee. _Mob drops wired (2026-08-03): all T2-T4 drop tables emit Unstable Rifts + materials via the Oraxen `oraxen` drop type (Stalker/Phantom/Brute: common/uncommon ~8%, elites: uncommon 30% + rare 15%, bosses: guaranteed high-rarity). Needs live test. Identifier NPC still pending._
- [ ] **5.10.5 Resonance tags + gear rolls** — 5 frequencies (Aegis/Veil/Bloom/Ward/Hollow) on mobs + gear; weapons +25% damage vs matching mobs, armor +defense. _Mob side done in repo (2026-08-03): all 10 mobs carry ScoreboardTags (res_aegis/res_veil/res_bloom/res_ward/res_hollow). Gear side exists in GlitchItems (CombatListener reads res_* tags). Needs live damage test._
- [ ] **5.10.6 Residual Glitch** — greed stacks (max 8, +1 every **5 min** — big map, searching is the game, not camping), small loot luck +5%/stack, aggro/risk scaling, extraction payout ×(1+0.10×stacks), elite hunts at 5+ stacks. _V1 built in GlitchItems (timer, boss bar HUD, damage multiplier, payout API) + payout hooked into extraction (GlitchStash pays sell-value × (multiplier−1) on win, stacks clear). Consumers DONE in repo (2026-08-06→08-10): loot luck applies at identify (star-luck per roll + rarity-upgrade chance per stack), elite hunt spawns a MythicMobs elite (console mm spawn) at 5+ stacks, and containers apply the loot-luck consumer (per-roll rarity surge + surge drop, 2026-08-10). Aggro-scaling (mob detection range) still open._
- [ ] **5.10.7 World population** — spawners, chests, regen emitting rifts from 5.10.4. _DONE in repo (2026-08-10): glitch_red SpawnAreas seeded (T1 fodder everywhere, T2 mid cross-ring, T3 elite guards at Core 0,0 + extraction sites; bootstrap.sh now seeds Spawners/SpawnAreas) + GlitchItems container system (Debris/Cache/Vault/Rift Vault marked per-block, key consumption, regen cooldown, loot-luck consumer). Remaining: mark containers + extraction arenas in-world (operator) + live test._
- [ ] **5.10.8 Resonance crafting** — recipes via Workbench (no RNG professions).
- [ ] **5.10.9 Rename pass** — GlitchClasses configs, MythicMobs drop tables, DeluxeMenus shop, docs to Arcane Ruins naming.

## Phase 5.11 — Core Gameplay Content

_In-world content that makes the game loops real. Design numbers live in
docs/GAME_DESIGN.md (mobs §2, loot §3, hideout §4, dungeon tiers §5, extraction
§7, economy §8, anti-grief §9) — renames per docs/ITEM_SYSTEM.md §9. GAME_DESIGN
still carries old techy names until 5.10.9._

- [ ] **5.11.1 Mob roster (10/10)** — GAME_DESIGN §2: 4 seeded (Stalker, Brute, Phantom, Core boss — 5.3). _Add:_ Glitch Wisp (Vex), Corrupted Crawler (Silverfish), Glitch Sentinel (Wither Skeleton), Glitch Sniper (enchanted Skeleton), Glitch Warden (Iron Golem), The Glitch King (Ender Dragon, 3-phase). Each gets a `Resonance:` tag per ITEM_SYSTEM §5 mob table. **Zone distribution locked:** glitch_red = T1 fodder everywhere, T2 mid zones, T3 elites guard POIs (Core 0,0, reliquaries, extract sites), T4 bosses = server events; glitch_pve = tier-scaled dungeon waves. _DONE in repo (2026-08-03): all 6 missing mobs added with per-tier drop tables + resonance ScoreboardTags (res_veil/res_hollow/res_ward/res_aegis/res_bloom); the 4 existing mobs also got their tags. Needs live test (`mm reload` + spawn checks). Spawners/zone placement still deferred with world population (5.10.7)._
- [x] **5.11.2 Loot containers** — GAME_DESIGN §3 + ITEM_SYSTEM §9: Debris Pile (everywhere, free), Loot Cache (mid-tier, Cache Key), Vault (hard areas, Vault Key), Rift Vault (boss areas, Rift Key). _DONE in repo (2026-08-10): GlitchItems containers — per-type rarity tables, key consumption (Oraxen id or material/name fallback), per-block regen cooldown via block PDC, Residual loot-luck consumer, /glitchcontainers set|clear|info|types|reload. In-world placement is operator work (mark blocks). Contents per rarity table; placed in-world by 5.10.7._
- [ ] **5.11.3 Material + shard drop tables** — replace COINS-only placeholders with GAME_DESIGN §3 per-mob-tier rates (renamed materials, e.g. Rune Fragment 100% 1-2 on Tier 1, Void Essence 5% on Tier 3, Legendary Relic 10% on bosses). _Done in repo (2026-08-03) for the 4 existing mobs: T2 tables (rune 100% 2-4, aether 10%, crystal 5%) + T4 boss table (rune/aether/crystal/void 100%, relic 10%). Vanilla placeholder drops (diamond/netherite/pearl/star) removed. Needs live test._
- [x] **5.11.4 Starter kit** — GAME_DESIGN §6: leather armor, wooden sword, 3 bread, 5 Rune Fragments. _DONE in repo (2026-08-06): GlitchClasses starter-kit section (vanilla material or Oraxen id entries), granted once per player on very first class select, items drop at feet if inventory full. Needs live build/test._
- [x] **5.11.5 Extraction variants** — GAME_DESIGN §7: Standard (30s, free) / Fast (15s, Fast Extract Key) / Silent (10s, Rift Key). _DONE in repo (2026-08-06): GlitchStash extraction-variants zones (config rectangle bounds, no extra deps), right-click key in zone to consume + arm, payout bonus on win (fast +5%, silent +10%), keyless-win warning with enforce-key flag, /extractadmin zones|reload|armed. Remaining (live, ops): create extract_fast (15s) and extract_silent (10s) VelKoth arenas with /koth set time and mirror bounds into GlitchStash config; Standard arena live capture time still 30s in generated arenas.yml._
- [ ] **5.11.6 Anti-grief / fair play** — GAME_DESIGN §9: friendly fire off everywhere, 2-min AFK kick in dungeons, shards account-bound (`server/plugins/Coins/config.yml:85` `player-drop:false` `lose-on-death:false` `drop-on-death:false` — repo + live reloaded 2026-08-23). _30s invulnerability on Red Zone entry points is DONE in GlitchDeathRules (2026-08-06): applies on world entry/join/respawn, optional entry-point zones, cancel-on-action, glow indicator. Remaining: friendly-fire/AFK verification._
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

- [x] **5.13.1 Leggings+boots keep plugin** — _DONE in repo (2026-08-06): GlitchDeathRules plugin — mercy rule keeps leggings+boots on death in glitch_red (drop-filter, no keepInventory), plus Red Zone entry invulnerability (30s, configurable). Needs live build/test._
- [x] **5.13.2 Shards-on-death verification** — _DONE in config 2026-08-23:_ `server/plugins/Coins/config.yml:114` `lose-on-death:false` `drop-on-death:false` + `player-drop:false:85` (account-bound, no item drop); live verified via `coins reload` in 10ms on 2026-08-22 host. _Remaining: playtest confirm no shard loss on death in `glitch_red`._
- [ ] **5.13.3 Tuning pass** — verify the mercy rule doesn't make full-loot too soft (data from early playtests).

## Phase 6 — Game loops

- [ ] **6.1 Dungeon objectives** — Wave-clear and data-core-repair objectives, tier scaling, completion rewards. _Needs 4.6 (a built dungeon room) to actually place objectives in._
- [x] **6.2 Extraction beacons** — Timed channel via VelKoth, zone broadcast, GlitchStash auto-save + hub teleport. _Implemented as **dynamic cycle**: `AutoExtractScheduler` 31m loop → `DynamicExtractionManager` 3 random validated arenas (`extraction_dyn*`) for 30m, then scatter; markers/waypoints/chunk-loading included. Static extraction variants (Fast/Silent) also live. Remaining: playtest gear-score weighting on scatter and tuning of `SpotPicker` (2-deep solid, stepped-roof reject)._
- [ ] **6.3 Gear-score gating** — Item-attribute scoring on Red Zone entry, distribution across rotating drop points to prevent spawn-camping. _Scatter now land-aware `[0..2000]²` (`cd74932`); weighting still open._
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
